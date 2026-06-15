package com.spotmydime.ai;

/**
 * Simple container for classification results returned by the model.
 */
public class ClassificationResult {
    public final String category;
    public final String vendor;
    public final Double amount;

    public ClassificationResult(String category, String vendor, Double amount) {
        this.category = category == null ? "Other" : category;
        this.vendor = vendor == null ? "" : vendor;
        this.amount = amount;
    }
}

