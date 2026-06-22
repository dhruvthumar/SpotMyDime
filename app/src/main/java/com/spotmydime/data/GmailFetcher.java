package com.spotmydime.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.spotmydime.ai.ClassificationResult;
import com.spotmydime.ai.GeminiClassifier;
import com.spotmydime.ai.TransactionClassifier;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GmailFetcher {

    private static final String TAG = "GmailFetcher";
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.readonly";

    // Pre-filter: skip emails with no money signal at all
    private static final Pattern SNIPPET_AMOUNT =
            Pattern.compile(
                    "\\$\\s*[0-9]+(?:[,.][0-9]+)*|"
                            + "(?:total|amount|paid|charged|due|cost|price|spent|payment|"
                            +    "sale|balance|fee|subtotal|grand\\s+total|sum)"
                            + "\\s*[:\\s]*\\$?\\s*[0-9]+(?:[,.][0-9]+)*",
                    Pattern.CASE_INSENSITIVE);

    // Known personal/free email domains. Emails from these addresses with no
    // identifiable vendor should be discarded — they are likely spam, phishing,
    // or generic notifications that do not represent real transactions.
    private static final Set<String> PERSONAL_DOMAINS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "live.com",
                    "aol.com", "icloud.com", "mail.com", "protonmail.com", "zoho.com",
                    "yandex.com", "gmx.com"
            )));

    public static final int REQUEST_AUTH = 1001;

    public interface Callback {
        void onResult(List<Transaction> transactions);
        void onError(String message);
    }

    // ── Fetch entry point ─────────────────────────────────────────────────────

    public static void fetchTransactions(Context context, Callback callback) {
        // No explicit afterDate given — use incremental sync: only ask Gmail
        // for messages newer than the last successful sync (with a small
        // overlap window). Falls back to the wide default on first-ever sync.
        SyncStateStore syncState = new SyncStateStore(context);
        String incrementalAfter = syncState.getQueryAfterDate("2026/01/01");
        fetchTransactionsInternal(context, callback, incrementalAfter, true);
    }

    /**
     * Explicit afterDate overload — bypasses incremental sync and does NOT
     * update the sync watermark (so it's safe to use for one-off date-range
     * queries, e.g. a settings "view a specific month" action, without
     * corrupting the incremental sync state used by the no-arg overload).
     * Use the no-arg overload for normal app-open/refresh flows.
     */
    public static void fetchTransactions(Context context, Callback callback, String afterDate) {
        fetchTransactionsInternal(context, callback, afterDate, false);
    }

    private static void fetchTransactionsInternal(Context context, Callback callback,
                                                   String afterDate, boolean updateSyncState) {
        final String effectiveAfter = (afterDate != null) ? afterDate : "2026/01/01";
        final SyncStateStore syncState = updateSyncState ? new SyncStateStore(context) : null;
        new Thread(() -> {
            try {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
                if (account == null) {
                    callback.onError("Not signed in. Please sign in again.");
                    return;
                }

                String token;
                try {
                    token = GoogleAuthUtil.getToken(context, account.getAccount(), SCOPE);
                } catch (com.google.android.gms.auth.UserRecoverableAuthException e) {
                    Log.e(TAG, "Auth requires user action", e);
                    if (context instanceof Activity) {
                        ((Activity) context).startActivityForResult(e.getIntent(), REQUEST_AUTH);
                    } else {
                        Intent i = e.getIntent();
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                    }
                    callback.onError("Authorization required.");
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Auth token error", e);
                    callback.onError("Failed to get Gmail access token: " + e.getClass().getSimpleName());
                    return;
                }

                String query = "after:" + effectiveAfter
                        + " ($ OR total OR amount OR paid OR charged OR receipt OR invoice"
                        + " OR \"order confirmation\" OR \"your order\" OR \"payment received\""
                        + " OR \"you've received\" OR \"e-transfer\" OR refund OR subscription)";

                String listUrl = GMAIL_API + "/messages?q="
                        + java.net.URLEncoder.encode(query, "UTF-8") + "&maxResults=100";

                JSONObject listObj = new JSONObject(executeGet(listUrl, token));
                JSONArray messages = listObj.optJSONArray("messages");

                if (messages == null || messages.length() == 0) {
                    Log.d(TAG, "No messages matched query");
                    callback.onResult(Collections.emptyList());
                    return;
                }

                Log.d(TAG, "Matched " + messages.length() + " messages");

                VendorStore vendorStore           = new VendorStore(context);
                VendorAliasStore aliasStore       = new VendorAliasStore(context);
                SubjectRuleStore subjectRuleStore = new SubjectRuleStore(context);
                TransactionOverrideStore overrides = new TransactionOverrideStore(context);
                ExcludedMessageStore excluded     = new ExcludedMessageStore(context);
                AiResultCache aiCache             = new AiResultCache(context);

                List<Transaction> results = new ArrayList<>();
                int max = Math.min(messages.length(), 60);
                long newestSeenMillis = 0;

                for (int i = 0; i < max; i++) {
                    String msgId = messages.getJSONObject(i).getString("id");
                    if (excluded.isExcluded(msgId)) continue;

                    String msgJson = executeGet(
                            GMAIL_API + "/messages/" + msgId + "?format=full", token);

                    // Track newest internalDate across ALL fetched messages, even ones
                    // that don't parse into a transaction — otherwise a burst of
                    // non-transactional mail would never advance the sync watermark
                    // and we'd keep re-fetching them every time.
                    try {
                        long msgDate = new JSONObject(msgJson).optLong("internalDate", 0);
                        if (msgDate > newestSeenMillis) newestSeenMillis = msgDate;
                    } catch (Exception ignored) {}

                    Transaction t = parseMessage(msgJson, vendorStore, aliasStore, subjectRuleStore, overrides, aiCache, msgId);
                    if (t != null) {
                        results.add(t);
                        Log.d(TAG, "OK  " + t.getMerchant()
                                + "  $" + t.getAmount()
                                + "  [" + t.getCategory() + "]"
                                + "  " + t.getType());
                    }
                }

                // Advance the sync watermark so the next fetch only asks Gmail for
                // messages newer than this (minus the overlap window in SyncStateStore).
                // syncState is null when this call came from the explicit-afterDate
                // overload (updateSyncState=false) — that path intentionally never
                // touches the watermark.
                if (newestSeenMillis > 0 && syncState != null) {
                    syncState.updateLastSyncedMillis(newestSeenMillis);
                }

                Collections.sort(results, (a, b) -> Long.compare(b.getDateMillis(), a.getDateMillis()));

                // ── Deduplication ─────────────────────────────────────────────
                // Key: vendor (lowercase) + amount (rounded to 2dp) + date (day bucket, ±12h window).
                // When two entries share the same key, keep the one with the longer subject
                // (longer = more informative: "Your Amazon order has shipped" beats "Amazon").
                List<Transaction> deduped = new ArrayList<>();
                Set<String> seen = new HashSet<>();

                for (Transaction t : results) {
                    // Day bucket: truncate to day boundary (midnight local)
                    long dayBucket = t.getDateMillis() / (24L * 60 * 60 * 1000);
                    String vendor  = t.getMerchant() != null
                            ? t.getMerchant().toLowerCase(Locale.US).trim() : "";
                    String amtKey  = String.format(Locale.US, "%.2f", t.getAmount());
                    String key     = vendor + "|" + amtKey + "|" + dayBucket;

                    if (!seen.contains(key)) {
                        seen.add(key);
                        deduped.add(t);
                    } else {
                        // Duplicate found — replace if this one has a longer (more informative) subject
                        for (int di = 0; di < deduped.size(); di++) {
                            Transaction existing = deduped.get(di);
                            long existingDay  = existing.getDateMillis() / (24L * 60 * 60 * 1000);
                            String existingV  = existing.getMerchant() != null
                                    ? existing.getMerchant().toLowerCase(Locale.US).trim() : "";
                            String existingAmt = String.format(Locale.US, "%.2f", existing.getAmount());
                            String existingKey = existingV + "|" + existingAmt + "|" + existingDay;

                            if (existingKey.equals(key)) {
                                int existingLen = existing.getSubject() != null ? existing.getSubject().length() : 0;
                                int newLen      = t.getSubject()        != null ? t.getSubject().length()        : 0;
                                if (newLen > existingLen) {
                                    deduped.set(di, t);
                                    Log.d(TAG, "Dedup: replaced with longer subject for key=" + key);
                                } else {
                                    Log.d(TAG, "Dedup: dropped duplicate for key=" + key);
                                }
                                break;
                            }
                        }
                    }
                }

                Log.d(TAG, "After dedup: " + deduped.size() + " (was " + results.size() + ")");
                callback.onResult(deduped);

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed", e);
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("Unable to resolve host") || msg.contains("UnknownHost")
                        || msg.contains("no address associated")) {
                    callback.onError("No internet connection. Check your Wi-Fi or mobile data and pull to refresh.");
                } else {
                    callback.onError("Error: " + e.getClass().getSimpleName() + " — " + msg);
                }
            }
        }).start();
    }

    // ── Parse a single message ────────────────────────────────────────────────

    private static Transaction parseMessage(String msgJson,
                                            VendorStore vendorStore,
                                            VendorAliasStore aliasStore,
                                            SubjectRuleStore subjectRuleStore,
                                            TransactionOverrideStore overrides,
                                            AiResultCache aiCache,
                                            String messageId) throws Exception {
        JSONObject obj     = new JSONObject(msgJson);
        JSONObject payload = obj.getJSONObject("payload");

        // ── Extract headers ──
        String subject = "";
        String from    = "";
        JSONArray headers = payload.optJSONArray("headers");
        if (headers != null) {
            for (int i = 0; i < headers.length(); i++) {
                JSONObject h = headers.getJSONObject(i);
                String name  = h.getString("name");
                if ("Subject".equalsIgnoreCase(name)) subject = h.optString("value", "");
                else if ("From".equalsIgnoreCase(name)) from  = h.optString("value", "");
            }
        }

        long   internalDate = obj.optLong("internalDate", System.currentTimeMillis());
        String snippet      = obj.optString("snippet", "");

        // ── Pre-filter: must have a money signal ──
        if (!SNIPPET_AMOUNT.matcher(snippet + " " + subject).find()) {
            Log.d(TAG, "SKIP (no money signal): " + subject);
            return null;
        }

        // ── Extract vendor from From header ──
        String rawVendor  = extractVendorName(from);
        String vendor     = rawVendor;
        String senderEmail = extractEmailFromHeader(from);

        // ── Check subject-based rules FIRST (user-defined per-subject overrides) ──
        SubjectRuleStore.SubjectRule matchedRule = null;
        if (senderEmail != null && subjectRuleStore != null) {
            matchedRule = subjectRuleStore.findMatch(senderEmail, subject);
            if (matchedRule != null) {
                Log.d(TAG, "Subject rule matched for " + senderEmail
                        + ": " + matchedRule.keywordsKey
                        + " -> alias=" + matchedRule.alias
                        + ", category=" + matchedRule.category);
            }
        }

        if (matchedRule == null) {
            // No subject rule — fall back to vendor-wide alias
            if (vendor != null && aliasStore != null) {
                String alias = aliasStore.getAlias(vendor);
                if (alias != null) vendor = alias;
            }
        }

        // ── Extract body ──
        String fullBody = extractBodyText(payload);

        // ── Interac fast-path ──
        String lcSubject = subject.toLowerCase(Locale.US);
        if (lcSubject.contains("interac e-transfer") || lcSubject.contains("interac e transfer")) {
            return buildInteracTransaction(subject, from, snippet, fullBody,
                    internalDate, vendor, rawVendor, messageId, vendorStore);
        }

        // ── If a subject rule matched, use its values directly (skip AI) ──
        if (matchedRule != null) {
            // Subject rule matched — use user-defined values for both merchant and category
            // No Gemini call, no vendor-wide alias/category learning
        }

        // ── Check per-message AI cache (skip Gemini for already-processed messages) ──
        AiResultCache.CachedResult cachedAi = (aiCache != null && messageId != null && matchedRule == null)
                ? aiCache.get(messageId) : null;

        // ── Check VendorStore cache — skip "Other" so AI gets another chance ──
        String cachedCategory = null;
        if (matchedRule != null && matchedRule.category != null) {
            cachedCategory = matchedRule.category;
        } else if (cachedAi != null && cachedAi.category != null && !cachedAi.category.equals("Other")) {
            cachedCategory = cachedAi.category;
        }
        if (cachedCategory == null && vendor != null && matchedRule == null) {
            String cached = vendorStore.getCategory(vendor);
            if (cached != null && !cached.equals("Other")) {
                cachedCategory = cached;
            }
        }

        // ── Run AI classification (only if not cached per-message and no subject rule) ──
        ClassificationResult res        = null;
        String               category   = cachedCategory;
        Double               modelAmount = null;
        String               modelDate   = null;
        String               aiMerchant  = (matchedRule != null) ? matchedRule.alias : null;

        if (cachedAi != null) {
            // Cached verdict from a previous sync says this message is NOT a
            // real transaction, or was flagged suspicious — discard again
            // without calling Gemini.
            if ("__NOT_A_TRANSACTION__".equals(cachedAi.category)) {
                Log.d(TAG, "DISCARD (cached: not a transaction): " + subject);
                return null;
            }
            if ("__SUSPICIOUS__".equals(cachedAi.category)) {
                Log.d(TAG, "DISCARD (cached: flagged suspicious): " + subject);
                return null;
            }
            // Use cached AI result — no Gemini call needed
            category   = cachedAi.category;
            modelAmount = cachedAi.amount;
            modelDate   = cachedAi.date;
            aiMerchant  = cachedAi.merchant;
            Log.d(TAG, "Using cached AI result for message " + messageId);
        } else if (cachedCategory == null) {
            boolean likelyTxn = TransactionClassifier.isTransactional(subject, snippet, fullBody);

            if (likelyTxn) {
                // PRIMARY: call Gemini with subject + body
                res = GeminiClassifier.classifyFull(vendor, subject, snippet, fullBody);

                if (res != null && res.isSuspicious) {
                    // AI flagged this as spam/phishing-like — discard regardless
                    // of what is_transaction says, since a scam dressed up as a
                    // transaction is exactly the security risk we're guarding
                    // against. Caching this separately from
                    // __NOT_A_TRANSACTION__ so future debugging/logging can
                    // tell the two discard reasons apart if needed.
                    if (aiCache != null && messageId != null) {
                        aiCache.put(messageId, null, "__SUSPICIOUS__", null, null, "outgoing");
                    }
                    Log.d(TAG, "DISCARD (AI flagged suspicious): " + subject);
                    return null;

                } else if (res != null && !res.isTransaction) {
                    // AI explicitly says this is NOT a real transaction (promo,
                    // newsletter, balance check, "you owe" reminder, etc.) —
                    // discard it instead of guessing a category and keeping it.
                    // Caching the verdict (even though category is a sentinel,
                    // not a real category) means the next sync sees cachedAi != null
                    // and skips straight past TransactionClassifier/Gemini entirely
                    // for this exact message — no re-call, no re-guess.
                    if (aiCache != null && messageId != null) {
                        aiCache.put(messageId, null, "__NOT_A_TRANSACTION__", null, null, "outgoing");
                    }
                    Log.d(TAG, "DISCARD (AI says not a transaction): " + subject);
                    return null;

                } else if (res != null && !res.category.equals("Other")) {
                    category   = res.category;
                    modelAmount = res.amount;
                    modelDate   = res.dateStr;

                    // Prefer AI merchant name (more user-friendly than From header)
                    if (res.vendor != null && !res.vendor.isEmpty()) {
                        aiMerchant = res.vendor;
                    }

                    // Persist AI result per-message so we never call Gemini for this message again
                    if (aiCache != null && messageId != null) {
                        aiCache.put(messageId, aiMerchant, category, modelAmount, modelDate,
                                res.type != null ? res.type : "outgoing");
                    }

                } else if (res != null && res.category.equals("Other")) {
                    // AI says it IS a transaction, just couldn't pin a specific
                    // category. If the sender is from a personal domain with no
                    // identifiable vendor, discard it to avoid surfacing spam.
                    if (messageId != null) {
                        String emailAddr = extractEmailFromHeader(from);
                        if (emailAddr != null && isPersonalEmailDomain(emailAddr)) {
                            boolean hasUserOverride = (overrides != null && overrides.getType(messageId) != null);
                            if (!hasUserOverride) {
                                Log.d(TAG, "DISCARD (Other + personal domain): " + subject);
                                if (aiCache != null) {
                                    aiCache.put(messageId, null, "__NOT_A_TRANSACTION__", null, null, "outgoing");
                                }
                                return null;
                            }
                        }
                    }
                    modelAmount = res.amount;
                    if (res.vendor != null && !res.vendor.isEmpty()) aiMerchant = res.vendor;
                    category = guessCategoryFallback(vendor, subject);

                    if (aiCache != null && messageId != null) {
                        aiCache.put(messageId, aiMerchant, category, modelAmount, modelDate,
                                res.type != null ? res.type : "outgoing");
                    }
                } else {
                    // AI failed (network/parse error, null response).
                    // If the sender is from a personal domain, discard permanently
                    // rather than falling through to keyword guess which may
                    // assign a real-sounding category to spam.
                    String emailAddr = extractEmailFromHeader(from);
                    if (emailAddr != null && isPersonalEmailDomain(emailAddr)) {
                        boolean hasUserOverride = (messageId != null && overrides != null
                                && overrides.getType(messageId) != null);
                        if (!hasUserOverride) {
                            Log.d(TAG, "DISCARD (Gemini failed + personal domain): " + subject);
                            if (aiCache != null && messageId != null) {
                                aiCache.put(messageId, null, "__NOT_A_TRANSACTION__", null, null, "outgoing");
                            }
                            return null;
                        }
                    }
                    // Deliberately NOT cached otherwise, so a transient Gemini
                    // failure gets retried on the next sync.
                    category = guessCategoryFallback(vendor, subject);
                }
            } else {
                // Not transactional by heuristic — if the sender is from a
                // personal domain, discard rather than guessing categories.
                String emailAddr = extractEmailFromHeader(from);
                if (emailAddr != null && isPersonalEmailDomain(emailAddr)) {
                    boolean hasUserOverride = (messageId != null && overrides != null
                            && overrides.getType(messageId) != null);
                    if (!hasUserOverride) {
                        Log.d(TAG, "DISCARD (heuristic fail + personal domain): " + subject);
                        if (aiCache != null && messageId != null) {
                            aiCache.put(messageId, null, "__NOT_A_TRANSACTION__", null, null, "outgoing");
                        }
                        return null;
                    }
                }
                category = guessCategoryFallback(vendor, subject);
            }
        }

        // ── Personal-sender security guard ─────────────────────────────
        // If the AI and heuristic both failed to assign a meaningful
        // category AND the sender is from a personal email domain
        // (gmail.com, yahoo.com, etc.), this email has no identifiable
        // business/vendor — discard it to avoid surfacing spam or
        // generic notifications as transactions.
        if ((category == null || "Other".equals(category))) {
            String emailAddr = extractEmailFromHeader(from);
            if (emailAddr != null && isPersonalEmailDomain(emailAddr)) {
                boolean hasUserOverride = (messageId != null && overrides != null
                        && overrides.getType(messageId) != null);
                if (!hasUserOverride) {
                    Log.d(TAG, "DISCARD (personal domain, no vendor found): " + subject);
                    if (aiCache != null && messageId != null) {
                        aiCache.put(messageId, null, "__NOT_A_TRANSACTION__", null, null, "outgoing");
                    }
                    return null;
                }
            }
        }

        // Persist alias — skip when a subject rule matched to avoid overwriting
        // vendor-wide alias with a subject-specific value
        if (matchedRule == null && aiMerchant != null && !aiMerchant.isEmpty()
                && rawVendor != null && !rawVendor.equals(aiMerchant)
                && aliasStore != null) {
            aliasStore.setAlias(rawVendor, aiMerchant);
        }

        // Learn: persist vendor → category — skip when a subject rule matched
        if (matchedRule == null && category != null && !"Other".equals(category) && vendor != null) {
            String keyToLearn = (aiMerchant != null) ? aiMerchant : vendor;
            if (keyToLearn != null && cachedCategory == null) {
                vendorStore.setCategory(keyToLearn, category);
            }
        }

        // ── Resolve final merchant display name ──
        // Priority: AI nickname > TransactionParser fallback > alias store > From header > subject
        String merchant;
        if (aiMerchant != null && !aiMerchant.isEmpty()) {
            merchant = aiMerchant;
        } else {
            String parsedMerchant = TransactionParser.extractMerchantName(subject, snippet, fullBody);
            if (parsedMerchant != null) {
                merchant = parsedMerchant;
            } else if (vendor != null && !vendor.isEmpty()) {
                merchant = vendor;
            } else {
                merchant = subject;
            }
        }

        // ── Resolve amount ──
        // Priority: user override > AI amount > regex from subject+body
        String searchText = subject + "\n" + (fullBody.isEmpty() ? snippet : fullBody);
        double amount = (modelAmount != null)
                ? modelAmount
                : TransactionParser.extractAmount(searchText);

        // ── Resolve date ──
        // Priority: AI date > regex fallback from email text > Gmail internalDate.
        // The regex fallback matters specifically when Gemini was skipped (rate
        // limited, not transactional by heuristic) or returned no date — without
        // it we'd silently use the email's RECEIVED date instead of the actual
        // transaction/billing date mentioned in the body.
        long finalDateMillis = internalDate;
        if (modelDate != null) {
            try {
                Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(modelDate);
                if (parsed != null) finalDateMillis = parsed.getTime();
            } catch (ParseException ignored) {}
        } else {
            Long regexDate = TransactionParser.extractDateMillis(searchText);
            if (regexDate != null) finalDateMillis = regexDate;
        }
        String dateDisplay = new SimpleDateFormat("MMM dd", Locale.US).format(new Date(finalDateMillis));

        // ── Resolve transaction type ──
        // Priority: user override > AI type > heuristic
        Transaction.Type type;
        if (res != null && res.type != null) {
            type = "incoming".equalsIgnoreCase(res.type)
                    ? Transaction.Type.INCOMING : Transaction.Type.OUTGOING;
        } else {
            type = classifyType(subject, snippet, fullBody, vendor);
        }

        // ── Apply user overrides ──
        if (messageId != null && overrides != null) {
            String ot = overrides.getType(messageId);
            if (ot != null) type = "incoming".equalsIgnoreCase(ot)
                    ? Transaction.Type.INCOMING : Transaction.Type.OUTGOING;
            Double oa = overrides.getAmount(messageId);
            if (oa != null) amount = oa;
        }

        char avatar = from.isEmpty() ? '?' : Character.toUpperCase(from.trim().charAt(0));

        Log.d(TAG, "RESULT | merchant=" + merchant + " | category=" + category
                + " | amount=" + amount + " | type=" + type);

        return new Transaction(merchant, amount, finalDateMillis, dateDisplay, category,
                avatar, type, extractEmailFromHeader(from), subject, messageId, rawVendor);
    }

    // ── Interac fast-path ─────────────────────────────────────────────────────

    private static Transaction buildInteracTransaction(String subject, String from,
                                                       String snippet, String fullBody,
                                                       long internalDate, String vendor,
                                                       String rawVendor, String messageId,
                                                       VendorStore vendorStore) {
        String cat;
        Transaction.Type txnType;
        String lcSubject = subject.toLowerCase(Locale.US);

        if (lcSubject.contains("transfer to") || lcSubject.contains("sent")) {
            cat     = "Interac Sent";
            txnType = Transaction.Type.OUTGOING;
        } else {
            cat     = "Interac Received";
            txnType = Transaction.Type.INCOMING;
        }

        // Learn: persist Interac vendor → category
        if (vendor != null && vendorStore != null) {
            vendorStore.setCategory(vendor, cat);
        }

        String searchText = subject + "\n" + (fullBody.isEmpty() ? snippet : fullBody);
        double amount     = TransactionParser.extractAmount(searchText);
        String dateDisplay = new SimpleDateFormat("MMM dd", Locale.US).format(new Date(internalDate));
        char   avatar     = from.isEmpty() ? '?' : Character.toUpperCase(from.trim().charAt(0));
        String merchant   = (vendor != null && !vendor.isEmpty()) ? vendor : subject;

        return new Transaction(merchant, amount, internalDate, dateDisplay, cat,
                avatar, txnType, extractEmailFromHeader(from), subject, messageId, rawVendor);
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private static String executeGet(String urlStr, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.connect();

        int code = conn.getResponseCode();
        Log.d(TAG, "GET " + urlStr.substring(Math.min(urlStr.length(), GMAIL_API.length())) + " → " + code);

        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        if (code >= 400) throw new Exception("API " + code + ": " + sb);
        return sb.toString();
    }

    // ── Body extraction ───────────────────────────────────────────────────────

    private static String extractBodyText(JSONObject payload) throws Exception {
        String mimeType = payload.optString("mimeType", "");

        if ("text/plain".equals(mimeType)) {
            JSONObject body = payload.optJSONObject("body");
            if (body != null) {
                String data = body.optString("data", "");
                if (!data.isEmpty()) return new String(Base64.decode(data, Base64.URL_SAFE));
            }
        }

        JSONArray parts = payload.optJSONArray("parts");
        if (parts != null) {
            // Pass 1: prefer text/plain
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                String pm = part.optString("mimeType", "");
                if ("text/plain".equals(pm)) {
                    JSONObject b = part.optJSONObject("body");
                    if (b != null) {
                        String data = b.optString("data", "");
                        if (!data.isEmpty()) return new String(Base64.decode(data, Base64.URL_SAFE));
                    }
                }
                // Recurse into multipart children
                JSONArray sub = part.optJSONArray("parts");
                if (sub != null) {
                    for (int j = 0; j < sub.length(); j++) {
                        JSONObject s = sub.getJSONObject(j);
                        if ("text/plain".equals(s.optString("mimeType", ""))) {
                            JSONObject b = s.optJSONObject("body");
                            if (b != null) {
                                String data = b.optString("data", "");
                                if (!data.isEmpty()) return new String(Base64.decode(data, Base64.URL_SAFE));
                            }
                        }
                    }
                }
            }
            // Pass 2: fall back to HTML → strip tags
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if ("text/html".equals(part.optString("mimeType", ""))) {
                    JSONObject b = part.optJSONObject("body");
                    if (b != null) {
                        String data = b.optString("data", "");
                        if (!data.isEmpty()) {
                            String html = new String(Base64.decode(data, Base64.URL_SAFE));
                            return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                        }
                    }
                }
            }
            // Last resort: recurse into first part
            return extractBodyText(parts.getJSONObject(0));
        }
        return "";
    }

    // ── Category fallback (keyword rules) ─────────────────────────────────────

    private static String guessCategoryFallback(String vendor, String subject) {
        String text = ((vendor != null ? vendor : "") + " " + (subject != null ? subject : ""))
                .toLowerCase(Locale.US);

        String[][] rules = {
                // Food
                {"ubereats","Food & Dining"},{"doordash","Food & Dining"},{"skip the dishes","Food & Dining"},
                {"starbucks","Food & Dining"},{"mcdonald","Food & Dining"},{"tim horton","Food & Dining"},
                {"chipotle","Food & Dining"},{"subway","Food & Dining"},{"pizza hut","Food & Dining"},
                {"domino","Food & Dining"},{"kfc","Food & Dining"},{"taco bell","Food & Dining"},
                {"burger king","Food & Dining"},{"wendy","Food & Dining"},{"dunkin","Food & Dining"},
                {"popeyes","Food & Dining"},{"restaurant","Food & Dining"},{"food","Food & Dining"},
                {"dining","Food & Dining"},{"grubhub","Food & Dining"},{"instacart","Food & Dining"},

                // Shopping
                {"amazon","Shopping"},{"walmart","Shopping"},{"target","Shopping"},
                {"costco","Shopping"},{"best buy","Shopping"},{"ebay","Shopping"},
                {"etsy","Shopping"},{"ikea","Shopping"},{"home depot","Shopping"},
                {"lowes","Shopping"},{"shopify","Shopping"},{"adidas","Shopping"},
                {"nike","Shopping"},{"h&m","Shopping"},{"zara","Shopping"},
                {"gap","Shopping"},{"old navy","Shopping"},{"cvs","Shopping"},
                {"walgreens","Shopping"},{"wish","Shopping"},{"shein","Shopping"},

                // Subscriptions
                {"netflix","Subscriptions"},{"spotify","Subscriptions"},{"hbo","Subscriptions"},
                {"disney+","Subscriptions"},{"disney plus","Subscriptions"},{"hulu","Subscriptions"},
                {"patreon","Subscriptions"},{"discord","Subscriptions"},{"twitch","Subscriptions"},
                {"apple one","Subscriptions"},{"youtube premium","Subscriptions"},
                {"subscription","Subscriptions"},{"renewal","Subscriptions"},

                // Transportation
                {"uber","Transportation"},{"lyft","Transportation"},{"shell","Transportation"},
                {"esso","Transportation"},{"petro","Transportation"},{"chevron","Transportation"},
                {"transit","Transportation"},{"via rail","Transportation"},{"greyhound","Transportation"},
                {"parking","Transportation"},{"presto","Transportation"},

                // Travel
                {"airbnb","Travel"},{"expedia","Travel"},{"booking.com","Travel"},
                {"hotels.com","Travel"},{"flight","Travel"},{"airline","Travel"},
                {"air canada","Travel"},{"westjet","Travel"},{"southwest","Travel"},
                {"delta","Travel"},{"united","Travel"},

                // Entertainment
                {"steam","Entertainment"},{"apple","Entertainment"},{"google play","Entertainment"},
                {"xbox","Entertainment"},{"playstation","Entertainment"},{"nintendo","Entertainment"},
                {"ticketmaster","Entertainment"},{"eventbrite","Entertainment"},
                {"cineplex","Entertainment"},{"amc","Entertainment"},

                // Health
                {"pharmacy","Health"},{"shoppers drug","Health"},{"rexall","Health"},
                {"doctor","Health"},{"clinic","Health"},{"dental","Health"},
                {"optometrist","Health"},{"hospital","Health"},{"health","Health"},

                // Bills & Utilities
                {"bell","Bills & Utilities"},{"rogers","Bills & Utilities"},{"telus","Bills & Utilities"},
                {"shaw","Bills & Utilities"},{"hydro","Bills & Utilities"},{"electric","Bills & Utilities"},
                {"internet","Bills & Utilities"},{"phone bill","Bills & Utilities"},
                {"enbridge","Bills & Utilities"},{"water bill","Bills & Utilities"},

                // Transfers
                {"paypal","Transfers"},{"venmo","Transfers"},{"stripe","Transfers"},
                {"wise","Transfers"},{"transferwise","Transfers"},{"revolut","Transfers"},
        };

        for (String[] r : rules) {
            if (text.contains(r[0])) return r[1];
        }
        return "Other";
    }

    // ── Transaction type heuristics ───────────────────────────────────────────

    private static Transaction.Type classifyType(String subject, String snippet,
                                                 String fullBody, String vendor) {
        String text = ((subject != null ? subject : "") + " "
                + (snippet != null ? snippet : "") + " "
                + (fullBody != null ? fullBody : "")).toLowerCase(Locale.US);

        // Credit card / loan payments = outgoing
        if ((text.contains("credit card") || text.contains("card payment")
                || text.contains("mortgage") || text.contains("loan payment"))
                && (text.contains("payment received") || text.contains("payment of")
                || text.contains("you made a payment") || text.contains("your payment")
                || text.contains("statement") || text.contains("autopay")
                || text.contains("scheduled payment") || text.contains("minimum payment"))) {
            return Transaction.Type.OUTGOING;
        }

        // Incoming signals
        if (text.contains("refund") || text.contains("cashback") || text.contains("cash back")
                || text.contains("deposit") || text.contains("you received")
                || text.contains("money received") || text.contains("sent you")
                || text.contains("paid you") || text.contains("reimbursement")
                || text.contains("direct deposit") || text.contains("payroll")
                || text.contains("income") || text.contains("interest earned")
                || (text.contains("interac") && (text.contains("you've received")
                || (text.contains("received") && text.contains("from"))))) {
            return Transaction.Type.INCOMING;
        }

        return Transaction.Type.OUTGOING;
    }

    // ── Header helpers ────────────────────────────────────────────────────────

    private static String extractVendorName(String from) {
        if (from == null || from.isEmpty()) return null;
        int idx = from.indexOf('<');
        if (idx > 0) {
            String name = from.substring(0, idx).trim().replace("\"", "");
            if (!name.isEmpty()) return name;
        }
        String email = from.trim();
        int at = email.indexOf('@');
        if (at > 0) {
            String domain = email.substring(at + 1);
            // Use the registrable domain name (e.g. "gmail" from "gmail.com")
            String[] parts = domain.split("\\.");
            if (parts.length >= 2) {
                String name = parts[parts.length - 2];
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
            int dot = domain.lastIndexOf('.');
            if (dot > 0) domain = domain.substring(0, dot);
            return Character.toUpperCase(domain.charAt(0)) + domain.substring(1);
        }
        return email.isEmpty() ? null : email;
    }

    private static String extractEmailFromHeader(String from) {
        if (from == null || from.isEmpty()) return null;
        int s = from.indexOf('<'), e = from.indexOf('>');
        if (s >= 0 && e > s) return from.substring(s + 1, e).trim();
        String t = from.trim();
        return t.contains("@") ? t : null;
    }

    private static boolean isPersonalEmailDomain(String email) {
        if (email == null) return false;
        int at = email.indexOf('@');
        if (at < 0) return false;
        String domain = email.substring(at + 1).toLowerCase(Locale.US);
        return PERSONAL_DOMAINS.contains(domain);
    }
}