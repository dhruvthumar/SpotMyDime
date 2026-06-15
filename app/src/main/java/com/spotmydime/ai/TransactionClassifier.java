package com.spotmydime.ai;

import java.util.regex.Pattern;

public class TransactionClassifier {

    // simple keywords that often appear in transactional emails
    private static final String[] TRANSACTION_KEYWORDS = new String[] {
            "receipt", "order", "invoice", "payment", "paid", "confirmation",
            "order #", "order ", "shipped", "delivered", "transaction", "charged",
            "amount paid", "total", "invoice #", "your order"
    };

    // simple currency/amount pattern (matches $12.34, CAD 12.34, 12.34 USD, etc.)
    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "(?i)(\\$\\s?\\d{1,3}(?:[,\\.]\\d{3})*(?:[.,]\\d{2})?|\\b(?:cad|usd|c\\$)\\s?\\d+(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})\\s?(?:cad|usd)\\b)"
    );

    // Returns true when the subject/snippet/body looks like a transactional email
    public static boolean isTransactional(String subject, String snippet, String body) {
        StringBuilder sb = new StringBuilder();
        if (subject != null) sb.append(subject).append(" ");
        if (snippet != null) sb.append(snippet).append(" ");
        if (body != null) sb.append(body);

        String text = sb.toString().toLowerCase();
        if (text.isEmpty()) return false;

        for (String k : TRANSACTION_KEYWORDS) {
            if (text.contains(k)) return true;
        }

        if (CURRENCY_PATTERN.matcher(text).find()) return true;

        return false;
    }
}

