package com.spotmydime.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransactionClassifierTest {

    // ── Keyword matching ──

    @Test
    public void testReceiptKeyword() {
        assertTrue(TransactionClassifier.isTransactional("Your Receipt from Amazon", "", null));
    }

    @Test
    public void testOrderKeyword() {
        assertTrue(TransactionClassifier.isTransactional("Order Confirmation", "", null));
    }

    @Test
    public void testInvoiceKeyword() {
        assertTrue(TransactionClassifier.isTransactional("Invoice #12345", "", null));
    }

    @Test
    public void testPaymentKeyword() {
        assertTrue(TransactionClassifier.isTransactional("Payment Received", "", null));
    }

    @Test
    public void testPaidKeyword() {
        assertTrue(TransactionClassifier.isTransactional("You paid $50.00", "", null));
    }

    @Test
    public void testTransactionKeyword() {
        assertTrue(TransactionClassifier.isTransactional("Transaction Alert", "", null));
    }

    @Test
    public void testChargedKeyword() {
        assertTrue(TransactionClassifier.isTransactional("Charged $29.99", "", null));
    }

    // ── Currency pattern matching ──

    @Test
    public void testCurrencyPatternDollarAmount() {
        assertTrue(TransactionClassifier.isTransactional("Your total was $12.34", null, null));
    }

    @Test
    public void testCurrencyPatternCommaAmount() {
        assertTrue(TransactionClassifier.isTransactional("Amount: $1,234.56", null, null));
    }

    @Test
    public void testCurrencyPatternCadPrefix() {
        assertTrue(TransactionClassifier.isTransactional("CAD 99.99", null, null));
    }

    @Test
    public void testCurrencyPatternUsdSuffix() {
        assertTrue(TransactionClassifier.isTransactional("Total: 49.99 USD", null, null));
    }

    // ── Combined field matching ──

    @Test
    public void testKeywordInSnippet() {
        assertTrue(TransactionClassifier.isTransactional("Re: your order", "Invoice attached", null));
    }

    @Test
    public void testKeywordInBody() {
        assertTrue(TransactionClassifier.isTransactional("Your Amazon order", null, "payment has been processed"));
    }

    @Test
    public void testCurrencyInSnippet() {
        assertTrue(TransactionClassifier.isTransactional("Newsletter", "$15.99 charge", null));
    }

    // ── Non-transaction rejection ──

    @Test
    public void testPlainGreetingReturnsFalse() {
        assertFalse(TransactionClassifier.isTransactional("Hello", "How are you?", null));
    }

    @Test
    public void testEmptyStringsReturnsFalse() {
        assertFalse(TransactionClassifier.isTransactional("", "", ""));
    }

    @Test
    public void testNullInputsReturnsFalse() {
        assertFalse(TransactionClassifier.isTransactional(null, null, null));
    }

    @Test
    public void testNonFinancialTextReturnsFalse() {
        assertFalse(TransactionClassifier.isTransactional(
            "Weekend plans", "Let's meet at 5pm", "See you there"));
    }

    @Test
    public void testNoMoneySignalReturnsFalse() {
        assertFalse(TransactionClassifier.isTransactional(
            "Meeting notes", "Agenda attached", "Please review"));
    }
}
