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

    // The AI's spam/phishing verdict — independent of isTransaction. A
    // transaction can be real but still suspicious (e.g. a generic sender
    // with urgency language), and a non-transaction is not automatically
    // suspicious (e.g. an ordinary newsletter). Defaults to false on the
    // older constructors so nothing already in the codebase is treated as
    // suspicious unless explicitly marked so by the new Gemini prompt logic.
    public final boolean isSuspicious;

    public ClassificationResult(String category, String vendor, Double amount) {
        this(category, vendor, amount, null, null, true, false);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type) {
        this(category, vendor, amount, type, null, true, false);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type, String dateStr) {
        this(category, vendor, amount, type, dateStr, true, false);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type,
                                 String dateStr, boolean isTransaction) {
        this(category, vendor, amount, type, dateStr, isTransaction, false);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type,
                                 String dateStr, boolean isTransaction, boolean isSuspicious) {
        this.category = category == null ? "Other" : category;
        this.vendor   = vendor == null ? "" : vendor;
        this.amount   = amount;
        this.type     = type;
        this.dateStr  = dateStr;
        this.isTransaction = isTransaction;
        this.isSuspicious  = isSuspicious;
    }
}
