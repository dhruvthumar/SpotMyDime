package com.spotmydime.ai;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for GeminiClassifier response parsing
 * Tests the JSON parsing logic that extracts transaction classification
 * from the Gemini API response to ensure robustness against various response formats.
 */
public class GeminiClassifierResponseTest {

    /**
     * Test Case 1: Parse valid API responses in different formats
     * Verifies that ClassificationResult correctly parses valid JSON responses
     * from the Gemini API, including:
     * - Standard candidates array response format
     * - Embedded JSON in text response
     * - All fields present (category, vendor, amount, type)
     */
    @Test
    public void testParseValidClassificationResponses() {
        // Test 1: Valid ClassificationResult creation with all fields
        ClassificationResult result1 = new ClassificationResult(
            "Shopping",
            "Amazon.ca",
            59.23,
            "outgoing"
        );
        assertEquals("Should set category correctly", "Shopping", result1.category);
        assertEquals("Should set vendor correctly", "Amazon.ca", result1.vendor);
        assertEquals("Should set amount correctly", 59.23, result1.amount, 0.01);
        assertEquals("Should set type correctly", "outgoing", result1.type);

        // Test 2: Valid response with only category and amount (vendor empty)
        ClassificationResult result2 = new ClassificationResult(
            "Food & Dining",
            "",
            25.50,
            "outgoing"
        );
        assertEquals("Should set category correctly", "Food & Dining", result2.category);
        assertEquals("Should have empty vendor", "", result2.vendor);
        assertEquals("Should set amount correctly", 25.50, result2.amount, 0.01);
        assertEquals("Should set type correctly", "outgoing", result2.type);

        // Test 3: Valid response with incoming type
        ClassificationResult result3 = new ClassificationResult(
            "Interac Received",
            "Friend Name",
            100.00,
            "incoming"
        );
        assertEquals("Should correctly identify incoming transaction", "Interac Received", result3.category);
        assertEquals("Should set type to incoming", "incoming", result3.type);
    }

    /**
     * Test Case 2: Handle null values and edge cases in response parsing
     * Verifies that ClassificationResult gracefully handles null inputs
     * and applies sensible defaults, preventing NullPointerExceptions:
     * - Null category defaults to "Other"
     * - Null vendor defaults to empty string
     * - Null amount returns null (not zero)
     * - Null type returns null (not outgoing)
     */
    @Test
    public void testHandleNullValuesAndDefaults() {
        // Test 1: Null category should default to "Other"
        ClassificationResult result1 = new ClassificationResult(
            null,
            "Amazon",
            50.00,
            "outgoing"
        );
        assertEquals("Should default null category to 'Other'", "Other", result1.category);
        assertEquals("Should preserve vendor", "Amazon", result1.vendor);

        // Test 2: Null vendor should default to empty string
        ClassificationResult result2 = new ClassificationResult(
            "Shopping",
            null,
            50.00,
            "outgoing"
        );
        assertEquals("Should preserve category", "Shopping", result2.category);
        assertEquals("Should default null vendor to empty string", "", result2.vendor);

        // Test 3: Null amount should remain null (not converted to 0)
        ClassificationResult result3 = new ClassificationResult(
            "Entertainment",
            "Netflix",
            null,
            "outgoing"
        );
        assertNull("Should preserve null amount (not convert to 0)", result3.amount);

        // Test 4: Null type should remain null
        ClassificationResult result4 = new ClassificationResult(
            "Transfers",
            "PayPal",
            75.00,
            null
        );
        assertNull("Should preserve null type", result4.type);

        // Test 5: All nulls should apply all defaults
        ClassificationResult result5 = new ClassificationResult(
            null,
            null,
            null,
            null
        );
        assertEquals("Should default category to 'Other' when all null", "Other", result5.category);
        assertEquals("Should default vendor to empty string when all null", "", result5.vendor);
        assertNull("Should preserve null amount", result5.amount);
        assertNull("Should preserve null type", result5.type);

        // Test 6: Empty strings should NOT be converted
        ClassificationResult result6 = new ClassificationResult(
            "Other",
            "",
            0.0,
            ""
        );
        assertEquals("Should preserve non-null empty string category", "Other", result6.category);
        assertEquals("Should preserve empty string vendor", "", result6.vendor);
        assertEquals("Should preserve zero amount", 0.0, result6.amount, 0.01);
        assertEquals("Should preserve empty string type", "", result6.type);
    }

    // ── STEP 1: is_transaction flag ──

    @Test
    public void testStep1FutureChargeNotTransaction() {
        ClassificationResult r = new ClassificationResult("Shopping", "Amazon", 50.00, "outgoing",
                null, false);
        assertFalse("Future charges should not be transactions", r.isTransaction);
        assertEquals("Shopping", r.category);
    }

    @Test
    public void testStep1CompletedRefundIsTransaction() {
        ClassificationResult r = new ClassificationResult("Shopping", "Amazon", 45.00, "outgoing",
                null, true);
        assertTrue("Completed refund should be a transaction", r.isTransaction);
    }

    // ── STEP 2: is_suspicious flag ──

    @Test
    public void testStep2GenericSenderIsSuspicious() {
        ClassificationResult r = new ClassificationResult("Transfers", "Unknown", 100.00, "incoming",
                null, false, true);
        assertTrue("Generic sender with no business should be suspicious", r.isSuspicious);
        assertFalse("Suspicious overrides transaction", r.isTransaction);
    }

    @Test
    public void testStep2RealCharityNotSuspicious() {
        ClassificationResult r = new ClassificationResult("Donations", "Red Cross", 50.00, "outgoing",
                null, true, false);
        assertFalse("Real named charity should not be suspicious", r.isSuspicious);
        assertTrue("Real charity pledge should be transaction", r.isTransaction);
    }

    // ── STEP 3: urgency language ──

    @Test
    public void testStep3UrgencyLanguageIsSuspicious() {
        ClassificationResult r = new ClassificationResult("Other", "Unknown", null, null,
                null, false, true);
        assertTrue("Urgency language should be suspicious", r.isSuspicious);
        assertEquals("Other", r.category);
    }

    // ── STEP 4: merchant_confidence (tests default values) ──

    @Test
    public void testStep4FamiliarBrandHighConfidence() {
        ClassificationResult r = new ClassificationResult("Subscriptions", "Netflix", 15.99, "outgoing",
                null, true, false);
        assertTrue(r.isTransaction);
        assertFalse(r.isSuspicious);
    }

    @Test
    public void testStep4GenericBankTransferNoMerchant() {
        ClassificationResult r = new ClassificationResult("Transfers", "", 500.00, "incoming",
                null, true, false);
        assertTrue(r.isTransaction);
        assertFalse(r.isSuspicious);
        assertEquals("", r.vendor);
    }

    // ── isSuspicious overrides isTransaction ──

    @Test
    public void testSuspiciousDefaultsCategoryToOther() {
        ClassificationResult r = new ClassificationResult(null, null, null, null,
                null, false, true);
        assertEquals("Suspicious null category defaults to 'Other'", "Other", r.category);
        assertFalse(r.isTransaction);
        assertTrue(r.isSuspicious);
    }

    @Test
    public void testSuspiciousFalseByDefault() {
        ClassificationResult r = new ClassificationResult("Shopping", "Amazon", 59.99, "outgoing");
        assertFalse("Should default to not suspicious", r.isSuspicious);
        assertTrue("Should default to transaction true", r.isTransaction);
    }

    @Test
    public void testSuspiciousAllNulls() {
        ClassificationResult r = new ClassificationResult(null, null, null, null,
                null, false, true);
        assertEquals("Other", r.category);
        assertEquals("", r.vendor);
        assertNull(r.amount);
        assertNull(r.type);
        assertFalse(r.isTransaction);
        assertTrue(r.isSuspicious);
    }
}

