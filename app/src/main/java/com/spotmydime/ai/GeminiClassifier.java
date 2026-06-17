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
    private static final String MODEL = "gemini-2.0-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

    /** Set from HomeActivity.onCreate via BuildConfig or strings.xml. */
    public static String apiKey = "";

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
             + "  \"category\": \"<one of the categories below>\",\n"
             + "  \"merchant\": \"<short friendly merchant name, e.g. 'Netflix', 'Tim Hortons', 'Amazon'>\",\n"
             + "  \"amount\": \"<numeric only, e.g. 12.99, or empty string if unknown>\",\n"
             + "  \"type\": \"<'incoming' or 'outgoing'>\",\n"
             + "  \"date\": \"<date found in email as YYYY-MM-DD, or empty string if none>\"\n"
             + "}\n\n"
             + "CATEGORIES (pick exactly one):\n"
             + "  Food & Dining, Shopping, Subscriptions, Transportation,\n"
             + "  Bills & Utilities, Entertainment, Health, Travel,\n"
             + "  Transfers, Interac Sent, Interac Received, Other\n\n"
             + "RULES:\n"
             + "1. is_transaction = true only if the email confirms a specific payment, purchase,\n"
             + "   charge, refund, receipt, or money transfer with a dollar amount.\n"
             + "   Promotional emails, account summaries, and newsletters = false.\n"
             + "2. type = 'outgoing' when money LEAVES the user (purchases, bills, payments, fees).\n"
             + "   type = 'incoming' when money COMES TO the user (refunds, deposits, cashback,\n"
             + "   Interac received, salary, reimbursements).\n"
             + "3. Credit card payment confirmation → outgoing (user paying their card bill).\n"
             + "4. merchant = the business name the user would recognise, NOT the email sender name.\n"
             + "   E.g. sender 'Uber Receipts <uber.com>' → merchant 'Uber'.\n"
             + "5. If is_transaction = false, still return all fields but set category = 'Other',\n"
             + "   amount = '', type = 'outgoing'.\n"
             + "6. amount must be the transaction total only (no currency symbols, no commas).\n";
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
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
        Log.d(TAG, "HTTP " + code);

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        if (code >= 400) {
            Log.w(TAG, "Gemini HTTP " + code + ": " + sb);
            return null;
        }
        return sb.toString();
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

            boolean isTxn    = out.optBoolean("is_transaction", true);
            String category  = out.optString("category", "Other");
            String merchant  = out.optString("merchant", "");
            String amountStr = out.optString("amount", "");
            String type      = out.optString("type", "outgoing");
            String date      = out.optString("date", "");

            // If model says not a transaction, still return result so caller
            // can decide to skip or mark as Other
            if (!isTxn) {
                return new ClassificationResult("Other", merchant, null, "outgoing", null);
            }

            // Parse amount
            Double amount = null;
            if (!amountStr.isEmpty()) {
                try {
                    amount = Double.parseDouble(amountStr.replaceAll("[^0-9.\\-]", ""));
                } catch (NumberFormatException ignored) {}
            }

            String dateResult = (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) ? date : null;

            return new ClassificationResult(category, merchant, amount, type, dateResult);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse Gemini response", e);
            return null;
        }
    }
}
