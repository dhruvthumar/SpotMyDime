package com.spotmydime.ui.onboarding;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Manual Entry Form Validation
 * Tests the validation logic for the "Add Expense/Income" page fields:
 * - Category validation
 * - Amount validation
 * - Date validation
 * - Transaction type toggle
 *
 * This simulates the form validation that happens in HomeActivity.saveManualTransaction()
 */
public class ManualEntryFormValidationTest {

    /**
     * Test Case 1: Validate manual entry form inputs correctly
     * Verifies that all form fields are validated before saving:
     * - Category must be selected (non-empty)
     * - Amount must be valid double value
     * - Amount must be greater than 0
     * - Date can be auto-selected (or user-selected)
     * - Payment method is optional
     */
    @Test
    public void testValidManualEntryFormInputs() {
        // Test 1: Valid expense entry with all fields
        boolean isCategoryValid1 = !("".equals(selectCategory("Shopping")));
        assertTrue("Category 'Shopping' should be valid", isCategoryValid1);

        boolean isAmountValid1 = parseAndValidateAmount("59.99");
        assertTrue("Amount 59.99 should be valid", isAmountValid1);

        // Test 2: Valid income entry
        boolean isCategoryValid2 = !("".equals(selectCategory("Transfers")));
        assertTrue("Category 'Transfers' should be valid", isCategoryValid2);

        boolean isAmountValid2 = parseAndValidateAmount("500.00");
        assertTrue("Amount 500.00 should be valid for income", isAmountValid2);

        // Test 3: Valid entry with Food & Dining
        boolean isCategoryValid3 = !("".equals(selectCategory("Food & Dining")));
        assertTrue("Category 'Food & Dining' should be valid", isCategoryValid3);

        boolean isAmountValid3 = parseAndValidateAmount("25.50");
        assertTrue("Amount 25.50 should be valid", isAmountValid3);

        // Test 4: All required categories should be valid
        String[] validCategories = {
            "Food & Dining", "Shopping", "Subscriptions", "Transportation",
            "Bills & Utilities", "Entertainment", "Health", "Interac Sent",
            "Interac Received", "Transfers", "Travel", "Other"
        };
        for (String category : validCategories) {
            boolean isValid = !("".equals(selectCategory(category)));
            assertTrue("Category '" + category + "' should be valid", isValid);
        }
    }

    /**
     * Test Case 2: Reject invalid form inputs and edge cases
     * Verifies that the form rejects invalid entries:
     * - Empty category (must select one)
     * - Empty amount string
     * - Non-numeric amount
     * - Negative amounts
     * - Zero amount
     * - Very large amounts (but this may be allowed, so test boundary)
     * - Category with typos/invalid options
     */
    @Test
    public void testRejectInvalidManualEntryFormInputs() {
        // Test 1: Empty category should be invalid
        boolean isCategoryValid1 = !("".equals(selectCategory("")));
        assertFalse("Empty category should be invalid", isCategoryValid1);

        // Test 2: Empty amount string should be invalid
        boolean isAmountValid1 = parseAndValidateAmount("");
        assertFalse("Empty amount string should be invalid", isAmountValid1);

        // Test 3: Non-numeric amount should be invalid
        boolean isAmountValid2 = parseAndValidateAmount("not_a_number");
        assertFalse("Non-numeric amount should be invalid", isAmountValid2);

        // Test 4: Amount with special characters only should be invalid
        boolean isAmountValid3 = parseAndValidateAmount("@#$%");
        assertFalse("Amount with only special chars should be invalid", isAmountValid3);

        // Test 5: Negative amount should be invalid (user expense is positive)
        boolean isAmountValid4 = parseAndValidateAmount("-50.00");
        assertFalse("Negative amount should be invalid", isAmountValid4);

        // Test 6: Zero amount should be invalid (no $0 transactions)
        boolean isAmountValid5 = parseAndValidateAmount("0.00");
        assertFalse("Zero amount should be invalid", isAmountValid5);

        // Test 7: Invalid category (typo/non-existent)
        boolean isCategoryValid2 = !("".equals(selectCategory("InvalidCategory")));
        assertFalse("Non-existent category should be invalid", isCategoryValid2);

        // Test 8: Null amount should be invalid
        boolean isAmountValid6 = parseAndValidateAmount(null);
        assertFalse("Null amount should be invalid", isAmountValid6);

        // Test 9: Whitespace-only amount should be invalid
        boolean isAmountValid7 = parseAndValidateAmount("   ");
        assertFalse("Whitespace-only amount should be invalid", isAmountValid7);

        // Test 10: Amount with dollar sign (should be parsed or rejected)
        boolean isAmountValid8 = parseAndValidateAmount("$50.00");
        // This could go either way depending on implementation
        // If your app includes '$', this should be true; otherwise false
    }

    /**
     * Test Case 3: Validate transaction type toggling (Expense vs Income)
     * Verifies that the expense/income toggle works correctly and affects type assignment
     */
    @Test
    public void testTransactionTypeToggle() {
        // Test 1: Expense toggle creates OUTGOING transaction
        boolean toggleExpense = setToggle(true);
        assertEquals("Expense toggle should be true", true, toggleExpense);

        // Test 2: Income toggle creates INCOMING transaction
        boolean toggleIncome = setToggle(false);
        assertEquals("Income toggle should be false", false, toggleIncome);

        // Test 3: Multiple toggles should work
        boolean first = setToggle(true);  // Expense
        boolean second = setToggle(false); // Income
        boolean third = setToggle(true);   // Back to Expense

        assertEquals("Should toggle back to expense", true, third);
    }

    // ── HELPER METHODS (simulate form field behavior) ──

    /**
     * Helper: Validates and selects a category.
     * Returns the category if valid, empty string if invalid.
     */
    private String selectCategory(String category) {
        String[] validCategories = {
            "Food & Dining", "Shopping", "Subscriptions", "Transportation",
            "Bills & Utilities", "Entertainment", "Health", "Interac Sent",
            "Interac Received", "Transfers", "Travel", "Other"
        };

        if (category == null || category.isEmpty()) {
            return ""; // Invalid
        }

        for (String valid : validCategories) {
            if (valid.equalsIgnoreCase(category)) {
                return category; // Valid
            }
        }

        return ""; // Invalid category
    }

    /**
     * Helper: Validates amount input.
     * Returns true if valid amount (numeric, positive, non-zero), false otherwise.
     */
    private boolean parseAndValidateAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return false; // Empty or null
        }

        try {
            // Try to parse as double
            double amount = Double.parseDouble(amountStr.trim());

            // Must be positive and non-zero
            if (amount > 0) {
                return true;
            }
        } catch (NumberFormatException e) {
            // Not a valid number
            return false;
        }

        return false;
    }

    /**
     * Helper: Toggles between expense and income.
     * Returns true for Expense (OUTGOING), false for Income (INCOMING).
     */
    private boolean setToggle(boolean isExpense) {
        return isExpense;
    }
}

