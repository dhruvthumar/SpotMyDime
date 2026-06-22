package com.spotmydime.ai;

public class ClassificationResult {
    public final String category;
    public final String vendor;      // AI-suggested merchant nickname
    public final Double amount;
    public final String type;        // "incoming" or "outgoing"
    public final String dateStr;     // date extracted from email body, may be null

    // The AI's actual is_transaction verdict. true unless the model explicitly
    // said this email is NOT a real transaction (promo, newsletter, account
    // summary, etc.) — see GeminiClassifier.parseResponse(). Defaults to true
    // for the older constructors so existing call sites (and any manual/
    // fallback ClassificationResult construction) keep their prior behavior.
    public final boolean isTransaction;

    public ClassificationResult(String category, String vendor, Double amount) {
        this(category, vendor, amount, null, null, true);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type) {
        this(category, vendor, amount, type, null, true);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type, String dateStr) {
        this(category, vendor, amount, type, dateStr, true);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type,
                                 String dateStr, boolean isTransaction) {
        this.category = category == null ? "Other" : category;
        this.vendor   = vendor == null ? "" : vendor;
        this.amount   = amount;
        this.type     = type;
        this.dateStr  = dateStr;
        this.isTransaction = isTransaction;
    }
}
