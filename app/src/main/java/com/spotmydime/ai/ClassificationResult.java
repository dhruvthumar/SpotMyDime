package com.spotmydime.ai;

public class ClassificationResult {
    public final String category;
    public final String vendor;
    public final Double amount;
    public final String type;

    public ClassificationResult(String category, String vendor, Double amount) {
        this(category, vendor, amount, null);
    }

    public ClassificationResult(String category, String vendor, Double amount, String type) {
        this.category = category == null ? "Other" : category;
        this.vendor = vendor == null ? "" : vendor;
        this.amount = amount;
        this.type = type;
    }
}
