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
 * Calls the backend proxy to classify a transaction email via Gemini.
 *
 * The Gemini API key lives on the server — the app never sees it.
 * Requests are authenticated with a Google ID token (Bearer auth).
 */
public class GeminiClassifier {

    private static final String TAG = "GeminiClassifier";

    /** Set from HomeActivity.onCreate via BuildConfig. */
    public static String backendUrl = "";

    /** Debug mode — captures full prompt / raw response / parsed result for AiDebugConsole. */
    public static boolean debugMode = false;
    public static com.spotmydime.data.AiDebugStore debugStore;
    public static String lastPrompt;
    public static String lastRawResponse;
    public static int lastHttpCode;
    public static ClassificationResult lastResult;

    /**
     * Minimum ms between API calls.
     * Free tier for gemini-1.5-flash: 15 RPM → 4000ms spacing.
     * Use 7000ms to stay safe if switching to a 10-RPM model.
     */
    private static final long MIN_INTERVAL_MS = 7000;
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

    public static String classify(String sender, String subject, String snippet) {
        ClassificationResult r = classifyFull(sender, subject, snippet, null);
        return r == null ? null : r.category;
    }

    public static ClassificationResult classifyFull(String sender, String subject,
                                                     String snippet, String fullBody) {
        if (backendUrl.isEmpty()) {
            Log.w(TAG, "Backend URL not set");
            return null;
        }

        String body = fullBody != null
                ? fullBody.substring(0, Math.min(fullBody.length(), 2000))
                : "";

        String prompt = buildPrompt(sender, subject, snippet, body);

        try {
            throttle();
            String requestJson = buildRequest(prompt);
            if (debugMode) lastPrompt = prompt;
            lastHttpCode = 0;
            String responseJson = postToBackend(requestJson);

            if (responseJson == null) {
                Log.w(TAG, "Backend returned null");
                if (debugMode) { lastRawResponse = null; lastResult = null; }
                return null;
            }

            if (debugMode) lastRawResponse = responseJson;
            ClassificationResult result = parseResponse(responseJson);
            if (debugMode) lastResult = result;
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Classification failed", e);
            if (debugMode) { lastRawResponse = null; lastResult = null; }
            return null;
        }
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private static String buildPrompt(String sender, String subject,
                                        String snippet, String body) {
        return "You are a financial transaction email classifier for a personal finance app.\n"
             + "Your job: read the email fields below and return ONLY a JSON object — no markdown, no commentary.\n\n"
             + "EMAIL FIELDS:\n"
             + "  sender:  \"" + safe(sender)  + "\"\n"
             + "  subject: \"" + safe(subject) + "\"\n"
             + "  snippet: \"" + safe(snippet) + "\"\n"
             + "  body:    \"" + safe(body)    + "\"\n\n"
             + "OUTPUT FORMAT:\n"
             + "{\n"
             + "  \"is_transaction\": true or false,\n"
             + "  \"is_suspicious\": true or false,\n"
             + "  \"merchant_confidence\": \"<'high', 'low', or 'none'>\",\n"
             + "  \"category\": \"<one of the categories below>\",\n"
             + "  \"merchant\": \"<short friendly merchant name, or empty string>\",\n"
             + "  \"amount\": \"<numeric only, e.g. 12.99, or empty string if unknown>\",\n"
             + "  \"type\": \"<'incoming' or 'outgoing'>\",\n"
             + "  \"date\": \"<YYYY-MM-DD, or empty string if none>\"\n"
             + "}\n\n"
             + "CATEGORIES (pick exactly one):\n"
             + "  Food & Dining, Shopping, Subscriptions, Transportation,\n"
             + "  Bills & Utilities, Entertainment, Health, Travel,\n"
             + "  Transfers, Interac Sent, Interac Received, Other\n\n"
             + "DECISION ORDER — evaluate in this exact sequence and stop at the first rule that applies:\n\n"
             + "STEP 1 — Did money actually move, in the past tense, right now?\n"
             + "Set is_transaction = true ONLY if the email confirms a SPECIFIC payment, purchase,\n"
             + "charge, refund, or transfer that HAS ALREADY HAPPENED, with a dollar amount attached\n"
             + "to that specific event.\n\n"
             + "Set is_transaction = false for ANY of the following, even if a dollar amount appears\n"
             + "somewhere in the email:\n"
             + "  - A charge that hasn't happened yet (\"will be charged on the 1st\", \"renews on\n"
             + "    July 15 for $99.99\", \"your card will be charged\")\n"
             + "  - A pledge, reminder, or recurring-commitment notice (\"you pledged to donate\n"
             + "    $20/month\", \"no payment has been processed yet\")\n"
             + "  - A cancellation, estimate, or quote notice, even one that mentions a refund\n"
             + "    that hasn't completed (\"a refund WILL BE issued within 3-5 business days\" —\n"
             + "    that's a future event, not a confirmed transaction)\n"
             + "  - A request asking the USER to pay someone else (not a confirmation that the\n"
             + "    user already paid)\n"
             + "  - Promotional emails, newsletters, balance summaries, account notices\n"
             + "  - A REFUND CONFIRMATION ONLY counts as is_transaction = true if it explicitly\n"
             + "    says the refund has been completed/processed/issued AND ties back to an\n"
             + "    identifiable order, purchase, or business. A vague refund claim with no\n"
             + "    real business identity should instead be evaluated under STEP 2 below.\n\n"
             + "STEP 2 — Is there a real, identifiable business behind this?\n"
             + "Before trusting any transaction-like language, check: can you name a specific,\n"
             + "real business or merchant this is from — using the sender address, sender display\n"
             + "name, AND the email body/subject together?\n\n"
             + "A real business is something like \"Netflix\", \"Tim Hortons\", \"Amazon\", \"Air Canada\",\n"
             + "a named local restaurant/shop, or a person's full name on a peer-to-peer transfer.\n"
             + "A real business is NOT just a category label or role description — \"returns\",\n"
             + "\"billing\", \"support\", \"the bank\", \"your subscription provider\" do not count as a\n"
             + "business name on their own.\n\n"
             + "If the sender is a generic/personal-looking address (a plain Gmail/Yahoo/Outlook/\n"
             + "Hotmail/iCloud address, or a generic role-based local part like returns@, support@,\n"
             + "noreply@, billing@, alerts@, team@, info@, hello@) AND you cannot find any specific\n"
             + "named business anywhere in the subject or body either — then:\n"
             + "  - is_suspicious = true\n"
             + "  - is_transaction = false\n"
             + "  - category = \"Other\", amount = \"\", type = \"outgoing\", merchant = \"\"\n"
             + "  regardless of how confident or transaction-like the language sounds. A\n"
             + "  well-written refund confirmation from \"returns@gmail.com\" with no business name\n"
             + "  anywhere is a bigger red flag than a clumsy one — scams imitate confidence.\n\n"
             + "STEP 3 — Other suspicious signals (check even if Step 2 passed).\n"
             + "Set is_suspicious = true if you see:\n"
             + "  - Urgency, fear, or pressure language (\"act now\", \"your account will be locked\",\n"
             + "    \"verify immediately\", \"claim your prize\") — especially paired with a vague or\n"
             + "    generic sender identity\n"
             + "  - Amounts, order numbers, or claims that feel inconsistent, unprompted, or\n"
             + "    designed to bait a click/reply (e.g. a large unexplained refund/prize with\n"
             + "    no purchase context, a \"test\"/\"simulation\" framing)\n"
             + "  - A request to click a link or \"log in to verify\" tied to a financial claim\n\n"
             + "Set is_suspicious = false for ordinary legitimate transactions even when the\n"
             + "merchant is small, unfamiliar, or hard to name — not recognizing a brand is NOT\n"
             + "itself suspicious. Only flag based on the concrete signals above.\n\n"
             + "If is_suspicious = true at ANY point, it overrides everything else: treat the\n"
             + "email as not worth including (is_transaction = false, category = \"Other\",\n"
             + "amount = \"\", type = \"outgoing\", merchant = \"\").\n\n"
             + "STEP 4 — merchant_confidence (only relevant when is_transaction = true and\n"
             + "is_suspicious = false):\n"
             + "  'high' = you can clearly name the specific business\n"
             + "  'low'  = a real business exists but you're inferring the name from limited info\n"
             + "  'none' = no identifiable business, but the transaction is still legitimate\n"
             + "           (e.g. a generic internal bank transfer notice with no merchant at all)\n"
             + "  merchant_confidence = 'none' does NOT by itself mean is_suspicious = true —\n"
             + "  only the Step 2/3 criteria decide that.\n\n"
             + "OTHER RULES:\n"
             + "- type = 'outgoing' when money LEAVES the user (purchases, bills, payments, fees,\n"
             + "  pledges once charged). type = 'incoming' when money COMES TO the user (refunds,\n"
             + "  deposits, cashback, Interac received, salary, reimbursements).\n"
             + "- Credit card bill payment confirmation → outgoing.\n"
             + "- merchant = the business name a user would recognize, NOT the raw sender address.\n"
             + "  E.g. sender \"Uber Receipts <uber.com>\" → merchant \"Uber\".\n"
             + "- amount must be the transaction total only — no currency symbols, no commas.\n\n"
             + "WORKED EXAMPLES:\n\n"
             + "\"Your order #44213 for $58.00 was cancelled per your request. A refund will be\n"
             + "issued within 3-5 business days.\"\n"
             + "→ is_transaction: false (refund hasn't happened yet)\n\n"
             + "\"You pledged to donate $20.00 monthly to Save the Children. No payment has been\n"
             + "processed yet, your card will be charged on the 1st.\"\n"
             + "→ is_transaction: false (no charge yet), is_suspicious: false (real named charity)\n\n"
             + "\"Hi Gru, your annual plan will renew on July 15, 2026 for $99.99. No action\n"
             + "needed if you'd like to continue.\"\n"
             + "→ is_transaction: false (future renewal)\n\n"
             + "From \"returns <justbingemovies@gmail.com>\": \"We've processed your refund of\n"
             + "$45.00 for order #998213 back to your original payment method.\"\n"
             + "→ is_transaction: false, is_suspicious: true (generic Gmail sender, role-based\n"
             + "  display name \"returns\", NO actual business named anywhere)\n\n"
             + "\"Your payment of $11.99 to Spotify was successful. Charged to Visa ending 4421.\"\n"
             + "→ is_transaction: true, is_suspicious: false, merchant: \"Spotify\",\n"
             + "  merchant_confidence: \"high\"\n";
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    public static String generateText(String prompt) {
        if (backendUrl.isEmpty()) return null;
        try {
            throttle();
            String requestJson = buildRequest(prompt);
            String responseJson = postToBackend(requestJson);
            if (responseJson == null) return null;
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
            Log.e(TAG, "generateText failed", e);
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

        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.1);
        genConfig.put("topP", 0.9);

        JSONObject body = new JSONObject();
        body.put("contents", contents);
        body.put("generationConfig", genConfig);

        return body.toString();
    }

    private static String postToBackend(String json) throws Exception {
        String url = backendUrl + "/api/gemini/classify";
        int maxRetries = 3;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Backend HTTP " + code + " (attempt " + (attempt + 1) + "/" + maxRetries + ")");

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

            lastHttpCode = code;

            if (code >= 400) {
                String errBody = sb.toString();
                Log.w(TAG, "Backend HTTP " + code + " — " + (errBody.length() > 200 ? errBody.substring(0, 200) : errBody));
                if (debugMode) lastRawResponse = errBody.length() > 1000 ? errBody.substring(0, 1000) : errBody;
                lastHttpCode = code;
                return null;
            }
            return sb.toString();
        }
        return null;
    }

    private static ClassificationResult parseResponse(String response) {
        try {
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

            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

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

            if (!isTxn) {
                return new ClassificationResult("Other", merchant, null, "outgoing", null, false, isSuspicious);
            }

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
