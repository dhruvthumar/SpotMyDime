package com.spotmydime.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.spotmydime.ai.GeminiClassifier;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GmailFetcher {

    private static final String TAG = "GmailFetcher";
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.readonly";

    // Matches dollar amounts like $12.34, $1,234.56, or amounts near money keywords
    private static final Pattern SNIPPET_AMOUNT =
            Pattern.compile("\\$\\s*[0-9]+(?:[,.][0-9]+)*|" +
                    "(?:total|amount|paid|charged|due|cost|price|spent|payment|sale|balance|fee|subtotal|grand total|sum)\\s*[:\\s]*\\$?\\s*[0-9]+(?:[,.][0-9]+)*",
                    Pattern.CASE_INSENSITIVE);

    public static final int REQUEST_AUTH = 1001;

    public interface Callback {
        void onResult(List<Transaction> transactions);
        void onError(String message);
    }

    public static void fetchTransactions(Context context, Callback callback) {
        new Thread(() -> {
            try {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
                if (account == null) {
                    callback.onError("Not signed in. Please sign in again.");
                    return;
                }

                String token;
                try {
                    token = GoogleAuthUtil.getToken(
                            context,
                            account.getAccount(),
                            SCOPE
                    );
                } catch (com.google.android.gms.auth.UserRecoverableAuthException e) {
                    Log.e(TAG, "Auth requires user action", e);
                    if (context instanceof Activity) {
                        ((Activity) context).startActivityForResult(e.getIntent(), REQUEST_AUTH);
                    } else {
                        Intent recoverIntent = e.getIntent();
                        recoverIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(recoverIntent);
                    }
                    callback.onError("Authorization required.");
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Auth token error", e);
                    callback.onError("Failed to get Gmail access token: " + e.getClass().getSimpleName());
                    return;
                }

                String dateStr = "2026/01/01";

                // Gmail search query: only get emails that contain monetary signals
                String query = "after:" + dateStr
                        + " ($ OR total OR amount OR paid OR charged OR receipt OR invoice"
                        + " OR \"order confirmation\" OR \"your order\" OR \"payment received\")";

                String listUrl = GMAIL_API + "/messages?q="
                        + java.net.URLEncoder.encode(query, "UTF-8") + "&maxResults=500";

                String listJson = executeGet(listUrl, token);

                JSONObject listObj = new JSONObject(listJson);
                JSONArray messages = listObj.optJSONArray("messages");

                if (messages == null || messages.length() == 0) {
                    Log.d(TAG, "No matching messages found. Response: " + listJson);
                    callback.onResult(Collections.emptyList());
                    return;
                }

                Log.d(TAG, "Found " + messages.length() + " matching messages");

                List<Transaction> results = new ArrayList<>();
                int max = Math.min(messages.length(), 200);
                VendorStore vendorStore = new VendorStore(context);
                ExcludedMessageStore excludedStore = new ExcludedMessageStore(context);

                for (int i = 0; i < max; i++) {
                    String msgId = messages.getJSONObject(i).getString("id");

                    if (excludedStore.isExcluded(msgId)) {
                        Log.d(TAG, "Skipping excluded message: " + msgId);
                        continue;
                    }

                    String msgJson = executeGet(
                            GMAIL_API + "/messages/" + msgId + "?format=full", token);

                    Transaction t = parseMessage(msgJson, vendorStore, msgId);
                    if (t != null) {
                        results.add(t);
                        Log.d(TAG, "Parsed: " + t.getMerchant() + " $" + t.getAmount() + " [" + t.getCategory() + "]");
                    }
                }

                Collections.sort(results, (a, b) ->
                        Long.compare(b.getDateMillis(), a.getDateMillis()));

                Log.d(TAG, "Total valid transactions parsed: " + results.size());
                callback.onResult(results);

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed", e);
                callback.onError("Error: " + e.getClass().getSimpleName()
                        + " — " + e.getMessage());
            }
        }).start();
    }

    private static String executeGet(String urlStr, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.connect();

        int code = conn.getResponseCode();
        Log.d(TAG, "API " + urlStr.substring(GMAIL_API.length()) + " → " + code);

        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        if (code >= 400) {
            throw new Exception("API returned " + code + ": " + sb.toString());
        }

        return sb.toString();
    }

    private static Transaction parseMessage(String msgJson, VendorStore vendorStore, String messageId) throws Exception {
        JSONObject obj = new JSONObject(msgJson);
        JSONObject payload = obj.getJSONObject("payload");

        String subject = "";
        String from = "";
        JSONArray headers = payload.optJSONArray("headers");
        if (headers != null) {
            for (int i = 0; i < headers.length(); i++) {
                JSONObject h = headers.getJSONObject(i);
                String name = h.getString("name");
                if ("Subject".equalsIgnoreCase(name)) {
                    subject = h.optString("value", "");
                } else if ("From".equalsIgnoreCase(name)) {
                    from = h.optString("value", "");
                }
            }
        }

        long internalDate = obj.optLong("internalDate", System.currentTimeMillis());

        String snippet = obj.optString("snippet", "");

        // Quick pre-filter: skip if neither snippet nor subject contains a
        // money-amount pattern. This avoids fetching the full body + running
        // the classifier on clearly non-transactional messages.
        String preCheck = snippet + " " + subject;
        Matcher preMatch = SNIPPET_AMOUNT.matcher(preCheck);
        if (!preMatch.find()) {
            Log.d(TAG, "Skipping — no money pattern in snippet: " + subject);
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
        String dateDisplay = sdf.format(new Date(internalDate));

        String vendor = extractVendorName(from);
        String merchant = (vendor != null && !vendor.isEmpty()) ? vendor : subject;

        // Extract the full body early so the classifier can inspect it when
        // deciding whether this is a transactional message.
        String fullBody = extractBodyText(payload);

        // ── Interac e-Transfer detection ──
        if (subject != null && subject.contains("Interac e-Transfer")) {
            String cat;
            Transaction.Type txnType;
            if (subject.contains("transfer to")) {
                cat = "Interac Sent";
                txnType = Transaction.Type.OUTGOING;
            } else {
                cat = "Interac Received";
                txnType = Transaction.Type.INCOMING;
            }

            String searchText = subject + "\n" + (fullBody.isEmpty() ? snippet : fullBody);
            double amount = TransactionParser.extractAmount(searchText);

            char avatar = from.isEmpty() ? '?' : from.trim().charAt(0);
            if (avatar >= 'a' && avatar <= 'z') avatar = (char) (avatar - 32);

            Log.d(TAG, "Interac: " + cat + " | $" + amount + " | " + txnType);
            return new Transaction(merchant, amount, internalDate, dateDisplay, cat, avatar, txnType,
                    extractEmailFromHeader(from), subject, messageId);
        }

        // Determine if this looks like a transactional email first. If not, skip
        // calling the remote classifier and mark as Other. If vendor already has
        // a stored category, prefer that mapping.
        String category = null;
        if (vendor != null) {
            category = vendorStore.getCategory(vendor);
        }

        boolean isTxn = com.spotmydime.ai.TransactionClassifier.isTransactional(subject, snippet, fullBody);

        Double modelAmount = null;
        com.spotmydime.ai.ClassificationResult res = null;

        if (category == null) {
            if (!isTxn) {
                // Not a transactional message — guess from vendor name
                category = guessCategoryFallback(vendor, subject);
            } else {
                // Call the AI classifier and accept its structured output
                res = GeminiClassifier.classifyFull(vendor, subject, snippet);
                if (res != null) {
                    category = res.category == null ? "Other" : res.category;
                    // If the model extracted a vendor, prefer it (useful when subject
                    // contains a more specific merchant name than the From header).
                    if (res.vendor != null && !res.vendor.isEmpty()) {
                        vendor = res.vendor;
                    }
                    // If the model returned an amount, remember it and override
                    // the extracted amount later.
                    if (res.amount != null) {
                        modelAmount = res.amount;
                    }

                    if (vendor != null) {
                        vendorStore.setCategory(vendor, category);
                        Log.d(TAG, "Learned: " + vendor + " → " + category);
                    }
                } else {
                    // If the model failed, guess category from vendor name
                    category = guessCategoryFallback(vendor, subject);
                }
            }
        }

        String searchText = subject + "\n" + (fullBody.isEmpty() ? snippet : fullBody);
        double amount = TransactionParser.extractAmount(searchText);
        if (modelAmount != null) {
            amount = modelAmount;
        }

        char avatar = from.isEmpty() ? '?' : from.trim().charAt(0);
        if (avatar >= 'a' && avatar <= 'z') avatar = (char) (avatar - 32);

        Transaction.Type type = classifyType(subject, snippet, fullBody, vendor);
        if (res != null && res.type != null) {
            type = "incoming".equalsIgnoreCase(res.type)
                    ? Transaction.Type.INCOMING : Transaction.Type.OUTGOING;
        }

        Log.d(TAG, "From: " + from + " | Vendor: " + vendor + " | Category: " + category
                + " | $" + amount + " | " + type);

        return new Transaction(merchant, amount, internalDate, dateDisplay, category, avatar, type,
                extractEmailFromHeader(from), subject, messageId);
    }

    private static String guessCategoryFallback(String vendor, String subject) {
        String text = ((vendor != null ? vendor : "") + " " + subject).toLowerCase(Locale.US);

        String[][] rules = {
            {"food", "Food & Dining"}, {"dining", "Food & Dining"}, {"restaurant", "Food & Dining"},
            {"ubereats", "Food & Dining"}, {"doordash", "Food & Dining"}, {"starbucks", "Food & Dining"},
            {"mcdonald", "Food & Dining"}, {"chipotle", "Food & Dining"}, {"subway", "Food & Dining"},
            {"pizza hut", "Food & Dining"}, {"domino", "Food & Dining"}, {"kfc", "Food & Dining"},
            {"taco bell", "Food & Dining"}, {"burger king", "Food & Dining"}, {"wendy", "Food & Dining"},
            {"dunkin", "Food & Dining"}, {"popeyes", "Food & Dining"}, {"tim horton", "Food & Dining"},

            {"amazon", "Shopping"}, {"walmart", "Shopping"}, {"target", "Shopping"},
            {"costco", "Shopping"}, {"best buy", "Shopping"}, {"nike", "Shopping"},
            {"ebay", "Shopping"}, {"etsy", "Shopping"}, {"ikea", "Shopping"},
            {"home depot", "Shopping"}, {"lowes", "Shopping"}, {"shopify", "Shopping"},
            {"adidas", "Shopping"}, {"h&m", "Shopping"}, {"zara", "Shopping"},
            {"gap", "Shopping"}, {"old navy", "Shopping"}, {"cvs", "Shopping"},
            {"walgreens", "Shopping"}, {"wish", "Shopping"},

            {"netflix", "Subscriptions"}, {"spotify", "Subscriptions"}, {"hbo", "Subscriptions"},
            {"disney", "Subscriptions"}, {"hulu", "Subscriptions"}, {"patreon", "Subscriptions"},
            {"discord", "Subscriptions"}, {"twitch", "Subscriptions"},

            {"uber", "Transportation"}, {"lyft", "Transportation"}, {"shell", "Transportation"},
            {"esso", "Transportation"}, {"petro", "Transportation"}, {"bp", "Transportation"},
            {"chevron", "Transportation"}, {"exxon", "Transportation"}, {"mobil", "Transportation"},
            {"southwest", "Transportation"}, {"delta", "Transportation"}, {"united", "Transportation"},

            {"airbnb", "Travel"}, {"expedia", "Travel"}, {"booking", "Travel"},

            {"apple", "Entertainment"}, {"steam", "Entertainment"},
            {"microsoft", "Entertainment"}, {"google", "Entertainment"},

            {"paypal", "Transfers"}, {"venmo", "Transfers"}, {"stripe", "Transfers"},

            {"bell", "Bills & Utilities"}, {"rogers", "Bills & Utilities"}, {"telus", "Bills & Utilities"},
            {"hydro", "Bills & Utilities"}, {"electric", "Bills & Utilities"},
            {"internet", "Bills & Utilities"}, {"phone", "Bills & Utilities"},
        };

        for (String[] r : rules) {
            if (text.contains(r[0])) return r[1];
        }

        return "Other";
    }

    private static Transaction.Type classifyType(String subject, String snippet,
                                                   String fullBody, String vendor) {
        String text = (subject + " " + snippet + " " + fullBody).toLowerCase(Locale.US);

        if (text.contains("refund") || text.contains("cashback")
                || text.contains("deposit") || text.contains("payment received")
                || text.contains("you received") || text.contains("credit")
                || text.contains("money received") || text.contains("sent you")
                || text.contains("paid you") || text.contains("reimbursement")
                || text.contains("return") || text.contains("income")
                || text.contains("paypal deposit") || text.contains("direct deposit")
                || text.contains("interest") || text.contains("cash back")
                || text.contains("credit card payment received")
                || (text.contains("interac e-transfer")
                        && (text.contains("you've received")
                        || (text.contains("received") && text.contains("from"))))) {
            return Transaction.Type.INCOMING;
        }

        if (text.contains("interac e-transfer") && text.contains("transfer to")) {
            return Transaction.Type.OUTGOING;
        }

        return Transaction.Type.OUTGOING;
    }

    private static String extractVendorName(String from) {
        if (from == null || from.isEmpty()) return null;
        int idx = from.indexOf('<');
        if (idx > 0) {
            String name = from.substring(0, idx).trim();
            name = name.replace("\"", "");
            if (!name.isEmpty()) return name;
        }
        String email = from.trim();
        int at = email.indexOf('@');
        if (at > 0) {
            String domain = email.substring(at + 1);
            int dot = domain.lastIndexOf('.');
            if (dot > 0) domain = domain.substring(0, dot);
            return domain.substring(0, 1).toUpperCase(Locale.US) + domain.substring(1);
        }
        return email.isEmpty() ? null : email;
    }

    private static String extractEmailFromHeader(String from) {
        if (from == null || from.isEmpty()) return null;
        int start = from.indexOf('<');
        int end = from.indexOf('>');
        if (start >= 0 && end > start) {
            return from.substring(start + 1, end).trim();
        }
        String trimmed = from.trim();
        if (trimmed.contains("@")) return trimmed;
        return null;
    }

    private static String extractBodyText(JSONObject payload) throws Exception {
        String mimeType = payload.optString("mimeType", "");

        if ("text/plain".equals(mimeType)) {
            String data = payload.optJSONObject("body").optString("data", "");
            if (!data.isEmpty()) {
                return new String(Base64.decode(data, Base64.URL_SAFE));
            }
        }

        JSONArray parts = payload.optJSONArray("parts");
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                String partMime = part.optString("mimeType", "");

                if ("text/plain".equals(partMime)) {
                    String data = part.optJSONObject("body").optString("data", "");
                    if (!data.isEmpty()) {
                        return new String(Base64.decode(data, Base64.URL_SAFE));
                    }
                }

                JSONArray subParts = part.optJSONArray("parts");
                if (subParts != null) {
                    for (int j = 0; j < subParts.length(); j++) {
                        JSONObject sub = subParts.getJSONObject(j);
                        if ("text/plain".equals(sub.optString("mimeType", ""))) {
                            String data = sub.optJSONObject("body").optString("data", "");
                            if (!data.isEmpty()) {
                                return new String(Base64.decode(data, Base64.URL_SAFE));
                            }
                        }
                    }
                }

                if ("text/html".equals(partMime)) {
                    String data = part.optJSONObject("body").optString("data", "");
                    if (!data.isEmpty()) {
                        String html = new String(Base64.decode(data, Base64.URL_SAFE));
                        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
                    }
                }
            }

            JSONObject first = parts.getJSONObject(0);
            return extractBodyText(first);
        }

        return "";
    }
}
