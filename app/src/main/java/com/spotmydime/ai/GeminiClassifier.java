package com.spotmydime.ai;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeminiClassifier {

    private static final String TAG = "GeminiClassifier";
    private static final String MODEL = "gemini-2.0-flash";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

    public static String apiKey = "";

    public static String classify(String sender, String subject, String snippet) {
        ClassificationResult r = classifyFull(sender, subject, snippet, null);
        return r == null ? null : r.category;
    }

    /**
     * Classifies the email using the remote model (when API key configured).
     * Returns a ClassificationResult containing category, vendor and amount when
     * available. Returns null on unexpected failures.
     */
    public static ClassificationResult classifyFull(String sender, String subject, String snippet, String fullBody) {
        // If no API key is configured, do not call remote model (user requested no hardcoded rules).
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "No API key configured for Gemini — remote classification disabled");
            return null;
        }

        // Truncate fullBody to avoid excessive token usage (first 1500 chars)
        String body = fullBody != null ? fullBody.substring(0, Math.min(fullBody.length(), 1500)) : "";

        String prompt = "You are a transaction classifier.\n"
                + "Input fields:\n"
                + "  subject: the email subject line\n"
                + "  body: the full email body content\n"
                + "Instructions:\n"
                + "  1) If the email is a transactional/receipt/payment notification, output a JSON object EXACTLY in this format (only JSON, no extra commentary):\n"
                + "     {\"category\":\"<one of: Food & Dining, Shopping, Subscriptions, Transportation, Bills & Utilities, Entertainment, Health, Interac Sent, Interac Received, Other>\",\"vendor\":\"<vendor name or empty>\",\"amount\":\"<numeric amount like 59.23 or empty>\",\"type\":\"<incoming or outgoing>\"}\n"
                + "  2) If the email is NOT transactional, set category to \"Other\", vendor/amount to empty strings, and type to \"outgoing\".\n"
                + "  3) type must be \"outgoing\" when money leaves the user's account (purchases, bills, payments), or \"incoming\" when money comes in (refunds, deposits, cashback, reimbursements).\n"
                + "  4) CRITICAL: Emails about credit card payments, loan payments, or mortgage payments (e.g. \"payment received for your credit card\", \"credit card payment confirmation\") are OUTGOING — the user is sending money to pay their bill. Do NOT classify these as incoming.\n"
                + "  5) Emails about refunds, cashback, deposits, or money being sent TO the user are INCOMING.\n"
                + "  6) Only classify an email as a transaction if it contains a specific dollar amount and merchant name. Ignore informational/digest emails.\n"
                + "Now classify the following email:\n"
                + "subject: \"" + (subject != null ? subject.replace("\"", "\\\"") : "") + "\"\n"
                + "body: \"" + body.replace("\"", "\\\"") + "\"\n";

        try {
            Log.d(TAG, "Calling Gemini for subject: " + subject);

            String requestJson = buildRequest(prompt);
            String urlStr = API_URL + apiKey;
            String responseJson = postJson(urlStr, requestJson);

            if (responseJson == null) {
                Log.w(TAG, "Gemini returned null response");
                return null;
            }

            Log.d(TAG, "Gemini raw response: " + responseJson);

            return parseResponseToResult(responseJson);

        } catch (Exception e) {
            Log.e(TAG, "Gemini call failed", e);
            return null;
        }
    }

    // Local fallback classifier: simple keyword matching across sender, subject, snippet.
    private static String localClassify(String sender, String subject, String snippet) {
        StringBuilder sb = new StringBuilder();
        if (sender != null) sb.append(sender).append(' ');
        if (subject != null) sb.append(subject).append(' ');
        if (snippet != null) sb.append(snippet);
        String text = sb.toString().toLowerCase();

        String[][] checks = {
            {"food & dining", "Food & Dining"}, {"food", "Food & Dining"}, {"dining", "Food & Dining"},
            {"ubereats", "Food & Dining"}, {"doordash", "Food & Dining"},
            {"shopping", "Shopping"}, {"amazon", "Shopping"},
            {"subscriptions", "Subscriptions"}, {"subscription", "Subscriptions"},
            {"uber", "Transportation"}, {"lyft", "Transportation"}, {"transport", "Transportation"},
            {"bill", "Bills & Utilities"}, {"utilities", "Bills & Utilities"}, {"bills", "Bills & Utilities"},
            {"netflix", "Entertainment"}, {"spotify", "Entertainment"}, {"entertain", "Entertainment"},
            {"health", "Health"}, {"doctor", "Health"},
            {"interac e-transfer", "Other"},
        };

        for (String[] c : checks) {
            if (text.contains(c[0])) return c[1];
        }

        return "Other";
    }

    private static String buildRequest(String text) throws Exception {
        // Keep the same minimal request shape used before but ensure the
        // prompt text is included as the single part.
        JSONObject part = new JSONObject();
        part.put("text", text);

        JSONArray parts = new JSONArray();
        parts.put(part);

        JSONObject content = new JSONObject();
        content.put("parts", parts);

        JSONArray contents = new JSONArray();
        contents.put(content);

        JSONObject body = new JSONObject();
        body.put("contents", contents);

        // Optionally include safetySettings or model parameters here in future
        return body.toString();
    }

    private static String postJson(String urlStr, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        Log.d(TAG, "HTTP " + code + " for " + urlStr.replace(apiKey, "REDACTED"));

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        if (code >= 400) {
            Log.w(TAG, "Gemini error " + code + ": " + sb.toString());
            return null;
        }

        return sb.toString();
    }

    private static ClassificationResult parseResponseToResult(String response) {
        try {
            // The model response historically included a `candidates` array.
            // Extract the model-generated text from that structure if present,
            // otherwise work with the raw response. Then attempt to parse a
            // JSON object from the returned text.
            String text = null;
            try {
                JSONObject obj = new JSONObject(response);
                JSONArray candidates = obj.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                    if (content != null) {
                        JSONArray parts = content.optJSONArray("parts");
                        if (parts != null && parts.length() > 0) {
                            text = parts.getJSONObject(0).optString("text", null);
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            if (text == null) text = response;

            // Try to extract a JSON object from the model text.
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonPart = text.substring(start, end + 1);
                try {
                    JSONObject out = new JSONObject(jsonPart);
                    String category = out.optString("category", "Other");
                    String vendor = out.optString("vendor", "");
                    String amountStr = out.optString("amount", "");
                    String type = out.optString("type", null);
                    Double amount = null;
                    if (!amountStr.isEmpty()) {
                        try {
                            amount = Double.parseDouble(amountStr.replaceAll("[^0-9\\.\\-]", ""));
                        } catch (Exception ignore) {
                        }
                    }
                    return new ClassificationResult(category, vendor, amount, type);
                } catch (Exception je) {
                    Log.w(TAG, "Failed to parse JSON from model text", je);
                }
            }

            // If we couldn't parse JSON, return null so caller can decide fallback.
            return null;

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse Gemini response", e);
            return null;
        }
    }
}
