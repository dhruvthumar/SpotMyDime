package com.spotmydime.ai;

public class ClassificationResult {
    public final String category;
    public final String vendor;      // AI-suggested merchant nickname
    public final Double amount;
    public final String type;        // "incoming" or "outgoing"
    public final String dateStr;     // date extracted from email body, may be null

    public ClassificationResult(String category, String vendor, Double amount) {
        this(category, vendor, amount, null, null);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type) {
        this(category, vendor, amount, type, null);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type, String dateStr) {
        this.category = category == null ? "Other" : category;
        this.vendor   = vendor == null ? "" : vendor;
        this.amount   = amount;
        this.type     = type;
        this.dateStr  = dateStr;
    }
}
