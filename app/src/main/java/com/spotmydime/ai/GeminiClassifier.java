package com.spotmydime.ai;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Calls the Gemini API to classify a transaction email.
 *
 * Input:  sender display name, subject line, snippet, full body
 * Output: ClassificationResult with category, merchant nickname,
 *         amount, transaction type (incoming/outgoing), and optional date.
 *
 * Vendor memory lives in VendorStore (GmailFetcher layer) — this class
 * is stateless and only makes the API call + parses the response.
 */
public class GeminiClassifier {

    private static final String TAG = "GeminiClassifier";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

    /** Set from HomeActivity.onCreate via BuildConfig or strings.xml. */
    public static String apiKey = "";

    /** Minimum ms between API calls to avoid hitting rate limits. */
    private static final long MIN_INTERVAL_MS = 1200; // ~50 RPM
    private static long lastCallTime = 0;

    private static void throttle() {
        long now = System.currentTimeMillis();
        long wait = MIN_INTERVAL_MS - (now - lastCallTime);
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
        }
        lastCallTime = System.currentTimeMillis();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Convenience: returns only category string. */
    public static String classify(String sender, String subject, String snippet) {
        ClassificationResult r = classifyFull(sender, subject, snippet, null);
        return r == null ? null : r.category;
    }

    /**
     * Full classification. Uses subject line as primary signal; body provides
     * amount and date confirmation. Returns null on network/parse failure —
     * caller should fall back to local heuristics.
     */
    public static ClassificationResult classifyFull(String sender, String subject,
                                                     String snippet, String fullBody) {
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "No API key — Gemini disabled");
            return null;
        }

        // Truncate body to keep token cost low (~750 words)
        String body = fullBody != null
                ? fullBody.substring(0, Math.min(fullBody.length(), 2000))
                : "";

        String prompt = buildPrompt(sender, subject, snippet, body);

        try {
            Log.d(TAG, "Gemini call — subject: " + subject);
            throttle();
            String requestJson = buildRequest(prompt);
            String responseJson = postJson(API_URL + apiKey, requestJson);

            if (responseJson == null) {
                Log.w(TAG, "Gemini null response");
                return null;
            }

            Log.d(TAG, "Gemini response: " + responseJson);
            return parseResponse(responseJson);

        } catch (Exception e) {
            Log.e(TAG, "Gemini call failed", e);
            return null;
        }
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private static String buildPrompt(String sender, String subject,
                                       String snippet, String body) {
        return "You are a financial transaction email classifier for a personal finance app.\n"
             + "Your job: read the email fields below and return a JSON object.\n\n"
             + "EMAIL FIELDS:\n"
             + "  sender:  \"" + safe(sender)  + "\"\n"
             + "  subject: \"" + safe(subject) + "\"\n"
             + "  snippet: \"" + safe(snippet) + "\"\n"
             + "  body:    \"" + safe(body)    + "\"\n\n"
             + "OUTPUT FORMAT — respond with ONLY this JSON, no markdown, no commentary:\n"
             + "{\n"
             + "  \"is_transaction\": true or false,\n"
             + "  \"is_suspicious\": true or false,\n"
             + "  \"merchant_confidence\": \"<'high', 'low', or 'none'>\",\n"
             + "  \"category\": \"<one of the categories below>\",\n"
             + "  \"merchant\": \"<short friendly merchant name, e.g. 'Netflix', 'Tim Hortons', 'Amazon', or empty string if you cannot identify one>\",\n"
             + "  \"amount\": \"<numeric only, e.g. 12.99, or empty string if unknown>\",\n"
             + "  \"type\": \"<'incoming' or 'outgoing'>\",\n"
             + "  \"date\": \"<date found in email as YYYY-MM-DD, or empty string if none>\"\n"
             + "}\n\n"
             + "CATEGORIES (pick exactly one):\n"
             + "  Food & Dining, Shopping, Subscriptions, Transportation,\n"
             + "  Bills & Utilities, Entertainment, Health, Travel,\n"
             + "  Transfers, Interac Sent, Interac Received, Other\n\n"
              + "RULES FOR is_transaction:\n"
              + "1. is_transaction = true only if the email confirms a specific payment, purchase,\n"
              + "   charge, refund, receipt, or money transfer that HAS ALREADY HAPPENED, with a dollar amount.\n"
              + "2. is_transaction = false if the sender is from a personal email domain (gmail.com,\n"
              + "   yahoo.com, outlook.com, hotmail.com, icloud.com, etc.) AND the sender display name\n"
              + "   is a generic single word like 'returns', 'support', 'noreply', 'info', 'hello',\n"
              + "   'team', 'billing', 'alerts' AND no real business/merchant name appears anywhere in\n"
              + "   the email body or subject — there must be an actual identifiable company for this\n"
              + "   to be a valid transaction.\n"
              + "3. is_transaction = false for: promotional emails, newsletters, account summaries,\n"
              + "   future/scheduled charges that have not happened yet (e.g. 'will be charged on the 1st',\n"
              + "   'renews on July 15', 'no payment has been processed yet'), pledges/reminders about\n"
              + "   money that has not moved, requests for money (someone asking the user to pay, not\n"
              + "   a confirmation that the user already paid), and balance/quote/estimate notices where\n"
              + "   no charge occurred.\n\n"
              + "RULES FOR is_suspicious — set true if the email shows signs of being spam, phishing,\n"
              + "or a scam, REGARDLESS of whether is_transaction is true or false. Specifically:\n"
              + "4. is_suspicious = true if the sender is a generic/unidentifiable address (e.g. a personal-\n"
              + "   looking Gmail/Yahoo/Hotmail address, or a generic label like 'returns', 'support',\n"
              + "   'noreply', 'billing', 'team', 'alerts') AND the email body/subject does NOT clearly\n"
              + "   name a real, identifiable business anywhere — i.e. there is genuinely no company,\n"
              + "   brand, or merchant the user could recognize.\n"
              + "5. is_suspicious = true if the email uses urgency, fear, or pressure language to get the\n"
              + "   user to click a link or 'verify' something (e.g. 'act now', 'your account will be\n"
              + "   locked', 'claim your prize', 'unusual activity — click here'), especially combined\n"
              + "   with vague or generic sender/business identity.\n"
              + "6. is_suspicious = true if amounts, order numbers, or claims seem internally inconsistent\n"
              + "   or designed to bait a response (e.g. a large unexplained refund/prize with no purchase\n"
              + "   history context, or a request framed as a system test).\n"
              + "7. is_suspicious = false for ordinary legitimate transactions even if the merchant name is\n"
              + "   hard to pin down — e.g. a real but small/unfamiliar local business, a person's name on\n"
              + "   a peer-to-peer transfer, or a utility/biller you don't recognize. Not knowing a brand is\n"
              + "   NOT the same as the email being suspicious — only flag it when the signals above are\n"
              + "   actually present. Most legitimate receipts from small or unfamiliar vendors should NOT\n"
              + "   be marked suspicious just because you don't recognize the name.\n\n"
              + "RULES FOR merchant_confidence:\n"
              + "8. 'high' = you can clearly name the specific business (e.g. 'Netflix', 'Tim Hortons').\n"
              + "9. 'low' = there's a real business but you're inferring/guessing the name from limited info.\n"
              + "10. 'none' = you cannot identify any business or person this transaction is with.\n"
              + "    merchant_confidence = 'none' does NOT by itself mean is_suspicious = true — a real\n"
              + "    transaction can still have no identifiable merchant (e.g. a generic bank transfer\n"
              + "    notice). Only use the is_suspicious criteria above to decide that field.\n\n"
              + "OTHER RULES:\n"
              + "11. type = 'outgoing' when money LEAVES the user (purchases, bills, payments, fees).\n"
              + "    type = 'incoming' when money COMES TO the user (refunds, deposits, cashback,\n"
              + "    Interac received, salary, reimbursements).\n"
              + "12. Credit card payment confirmation → outgoing (user paying their card bill).\n"
              + "13. merchant = the business name the user would recognise, NOT the email sender name.\n"
              + "    E.g. sender 'Uber Receipts <uber.com>' → merchant 'Uber'. Use empty string if\n"
              + "    merchant_confidence is 'none'.\n"
              + "14. If is_transaction = false, still return all fields but set category = 'Other',\n"
              + "    amount = '', type = 'outgoing'.\n"
              + "15. amount must be the transaction total only (no currency symbols, no commas).\n";
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    /**
     * Generic text generation. Sends an arbitrary prompt and returns the plain-text response.
     * Returns null on any failure.
     */
    public static String generateText(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) return null;
        try {
            throttle();
            String requestJson = buildRequest(prompt);
            String responseJson = postJson(API_URL + apiKey, requestJson);
            if (responseJson == null) return null;
            // Extract text from response
            JSONObject root = new JSONObject(responseJson);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null && parts.length() > 0) {
                        return parts.getJSONObject(0).optString("text", null);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Gemini generateText failed", e);
            return null;
        }
    }

    // ── Request / Response ────────────────────────────────────────────────────

    private static String buildRequest(String promptText) throws Exception {
        JSONObject part = new JSONObject();
        part.put("text", promptText);

        JSONArray parts = new JSONArray();
        parts.put(part);

        JSONObject content = new JSONObject();
        content.put("parts", parts);

        JSONArray contents = new JSONArray();
        contents.put(content);

        // Ask Gemini for deterministic, structured output
        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.1);
        genConfig.put("topP", 0.9);

        JSONObject body = new JSONObject();
        body.put("contents", contents);
        body.put("generationConfig", genConfig);

        return body.toString();
    }

    private static String postJson(String urlStr, String json) throws Exception {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP " + code + " (attempt " + (attempt + 1) + "/" + maxRetries + ")");

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            if (code == 429 && attempt < maxRetries - 1) {
                long backoff = (long) Math.pow(2, attempt) * 1000 + (long) (Math.random() * 500);
                Log.w(TAG, "429 rate limited — retrying in " + backoff + "ms");
                Thread.sleep(backoff);
                continue;
            }

            if (code >= 400) {
                Log.w(TAG, "Gemini HTTP " + code + ": " + sb);
                return null;
            }
            return sb.toString();
        }
        return null;
    }

    private static ClassificationResult parseResponse(String response) {
        try {
            // Extract model text from candidates array
            String text = null;
            try {
                JSONObject root = new JSONObject(response);
                JSONArray candidates = root.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                    if (content != null) {
                        JSONArray parts = content.optJSONArray("parts");
                        if (parts != null && parts.length() > 0) {
                            text = parts.getJSONObject(0).optString("text", null);
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (text == null) text = response;

            // Strip markdown code fences if model wraps in ```json ... ```
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

            // Extract first JSON object from the text
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                Log.w(TAG, "No JSON found in response");
                return null;
            }

            JSONObject out = new JSONObject(text.substring(start, end + 1));

            boolean isTxn        = out.optBoolean("is_transaction", true);
            boolean isSuspicious = out.optBoolean("is_suspicious", false);
            String category      = out.optString("category", "Other");
            String merchant      = out.optString("merchant", "");
            String amountStr     = out.optString("amount", "");
            String type          = out.optString("type", "outgoing");
            String date          = out.optString("date", "");
            // merchant_confidence is read for logging/future use but doesn't
            // gate anything here directly — the is_suspicious criteria in the
            // prompt already account for "no identifiable merchant" cases that
            // genuinely warrant suspicion vs. those that don't.
            String merchantConfidence = out.optString("merchant_confidence", "low");
            Log.d(TAG, "merchant_confidence=" + merchantConfidence + " is_suspicious=" + isSuspicious);

            // If model says not a transaction, return a result that clearly
            // signals that — caller (GmailFetcher) checks isTransaction and
            // discards the email instead of creating a Transaction for it.
            // isSuspicious is still propagated even here, since GmailFetcher
            // logs/handles suspicious-and-not-a-transaction the same as
            // suspicious-and-is-a-transaction: discard either way.
            if (!isTxn) {
                return new ClassificationResult("Other", merchant, null, "outgoing", null, false, isSuspicious);
            }

            // Parse amount
            Double amount = null;
            if (!amountStr.isEmpty()) {
                try {
                    amount = Double.parseDouble(amountStr.replaceAll("[^0-9.\\-]", ""));
                } catch (NumberFormatException ignored) {}
            }

            String dateResult = (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) ? date : null;

            return new ClassificationResult(category, merchant, amount, type, dateResult, true, isSuspicious);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse Gemini response", e);
            return null;
        }
    }
}
