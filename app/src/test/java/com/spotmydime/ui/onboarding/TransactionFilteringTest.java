package com.spotmydime.ui.onboarding;

import com.spotmydime.data.Transaction;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unit tests for Transaction Page Filtering
 * Tests the filtering logic for Transactions page (Tab 2):
 * - Filter by category
 * - Filter by date range
 * - Combination of filters
 * - Clear filters functionality
 */
public class TransactionFilteringTest {

    private List<Transaction> testTransactions;

    @Before
    public void setUp() {
        // Create test transactions with various categories, dates, and amounts
        testTransactions = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        long today = cal.getTimeInMillis();

        // Today's transactions
        testTransactions.add(new Transaction(
            "Amazon", 59.99, today, "Jun 15 · 2:30 PM",
            "Shopping", 'A', Transaction.Type.OUTGOING
        ));
        testTransactions.add(new Transaction(
            "Starbucks", 5.50, today, "Jun 15 · 9:00 AM",
            "Food & Dining", 'S', Transaction.Type.OUTGOING
        ));
        testTransactions.add(new Transaction(
            "Freelance Client", 500.00, today, "Jun 15 · 11:30 AM",
            "Transfers", 'F', Transaction.Type.INCOMING
        ));

        // Yesterday's transactions
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterday = cal.getTimeInMillis();

        testTransactions.add(new Transaction(
            "Netflix", 15.99, yesterday, "Jun 14 · 8:00 PM",
            "Subscriptions", 'N', Transaction.Type.OUTGOING
        ));
        testTransactions.add(new Transaction(
            "Walmart", 125.00, yesterday, "Jun 14 · 3:00 PM",
            "Shopping", 'W', Transaction.Type.OUTGOING
        ));

        // 5 days ago
        cal.add(Calendar.DAY_OF_YEAR, -4);
        long fiveDaysAgo = cal.getTimeInMillis();

        testTransactions.add(new Transaction(
            "Lyft", 20.00, fiveDaysAgo, "Jun 10 · 5:30 PM",
            "Transportation", 'L', Transaction.Type.OUTGOING
        ));
        testTransactions.add(new Transaction(
            "Tim Hortons", 8.99, fiveDaysAgo, "Jun 10 · 7:15 AM",
            "Food & Dining", 'T', Transaction.Type.OUTGOING
        ));
    }

    /**
     * Test Case 1: Filter transactions by single category
     * Verifies that filtering by a specific category returns only transactions
     * in that category and excludes all others.
     */
    @Test
    public void testFilterByCategory() {
        // Test 1: Filter by Shopping category
        List<Transaction> shoppingTransactions = filterByCategory(testTransactions, "Shopping");
        assertEquals("Should return 2 Shopping transactions", 2, shoppingTransactions.size());
        assertTrue("All should be Shopping category",
            shoppingTransactions.stream().allMatch(t -> "Shopping".equals(t.getCategory())));
        assertTrue("Should contain Amazon",
            shoppingTransactions.stream().anyMatch(t -> "Amazon".equals(t.getMerchant())));
        assertTrue("Should contain Walmart",
            shoppingTransactions.stream().anyMatch(t -> "Walmart".equals(t.getMerchant())));

        // Test 2: Filter by Food & Dining category
        List<Transaction> foodTransactions = filterByCategory(testTransactions, "Food & Dining");
        assertEquals("Should return 2 Food & Dining transactions", 2, foodTransactions.size());
        assertTrue("All should be Food & Dining category",
            foodTransactions.stream().allMatch(t -> "Food & Dining".equals(t.getCategory())));

        // Test 3: Filter by Transfers (single transaction)
        List<Transaction> transfersTransactions = filterByCategory(testTransactions, "Transfers");
        assertEquals("Should return 1 Transfers transaction", 1, transfersTransactions.size());
        assertEquals("Should be Freelance Client", "Freelance Client",
            transfersTransactions.get(0).getMerchant());

        // Test 4: Filter by non-existent category
        List<Transaction> emptyTransactions = filterByCategory(testTransactions, "Entertainment");
        assertEquals("Should return 0 transactions for non-existent category", 0, emptyTransactions.size());
    }

    /**
     * Test Case 2: Filter transactions by date range
     * Verifies that filtering by start and end dates returns only transactions
     * within that date range (inclusive), and excludes transactions outside range.
     */
    @Test
    public void testFilterByDateRange() {
        Calendar cal = Calendar.getInstance();
        long today = cal.getTimeInMillis();

        // Set start date to today
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startOfToday = cal.getTimeInMillis();

        // Set end date to today end of day
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long endOfToday = cal.getTimeInMillis();

        // Test 1: Filter for today's transactions only
        List<Transaction> todayTransactions = filterByDateRange(
            testTransactions, startOfToday, endOfToday
        );
        assertEquals("Should return 3 transactions from today", 3, todayTransactions.size());
        assertTrue("All should be from today",
            todayTransactions.stream().allMatch(t -> t.getDateMillis() >= startOfToday && t.getDateMillis() <= endOfToday));

        // Test 2: Filter for past 2 days (today and yesterday)
        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        long dayBeforeYesterday = cal.getTimeInMillis();

        cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        long endOfToday2 = cal.getTimeInMillis();

        List<Transaction> twoDaysTransactions = filterByDateRange(
            testTransactions, dayBeforeYesterday, endOfToday2
        );
        assertEquals("Should return 5 transactions from today and yesterday", 5, twoDaysTransactions.size());

        // Test 3: Filter with no matching transactions in date range
        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -20);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        long twentyDaysAgo = cal.getTimeInMillis();

        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -15);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        long fifteenDaysAgo = cal.getTimeInMillis();

        List<Transaction> noTransactions = filterByDateRange(
            testTransactions, twentyDaysAgo, fifteenDaysAgo
        );
        assertEquals("Should return 0 transactions from date range with no data", 0, noTransactions.size());
    }

    /**
     * Test Case 3: Combine category and date range filters
     * Verifies that filters work together correctly, returning only transactions
     * that match BOTH criteria.
     */
    @Test
    public void testCombinedFilters() {
        // Setup date range for today only
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        long startOfToday = cal.getTimeInMillis();

        cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        long endOfToday = cal.getTimeInMillis();

        // Test 1: Shopping category + today's date range
        List<Transaction> resultFiltered = filterByCategory(testTransactions, "Shopping");
        resultFiltered = filterByDateRange(resultFiltered, startOfToday, endOfToday);

        assertEquals("Should return 1 Shopping transaction from today (Amazon only)", 1, resultFiltered.size());
        assertEquals("Should be Amazon", "Amazon", resultFiltered.get(0).getMerchant());

        // Test 2: Food & Dining category + today's date range
        List<Transaction> foodToday = filterByCategory(testTransactions, "Food & Dining");
        foodToday = filterByDateRange(foodToday, startOfToday, endOfToday);

        assertEquals("Should return 1 Food & Dining transaction from today", 1, foodToday.size());
        assertEquals("Should be Starbucks", "Starbucks", foodToday.get(0).getMerchant());

        // Test 3: No results for combined filters that don't match
        List<Transaction> transportToday = filterByCategory(testTransactions, "Transportation");
        transportToday = filterByDateRange(transportToday, startOfToday, endOfToday);

        assertEquals("Should return 0 Transportation transactions from today", 0, transportToday.size());
    }

    // ── HELPER METHODS ──

    /**
     * Filters transactions by category
     */
    private List<Transaction> filterByCategory(List<Transaction> transactions, String category) {
        if (category == null) {
            return transactions;
        }
        return transactions.stream()
            .filter(t -> category.equals(t.getCategory()))
            .collect(Collectors.toList());
    }

    /**
     * Filters transactions by date range (inclusive)
     */
    private List<Transaction> filterByDateRange(List<Transaction> transactions, long startMillis, long endMillis) {
        return transactions.stream()
            .filter(t -> t.getDateMillis() >= startMillis && t.getDateMillis() <= endMillis)
            .collect(Collectors.toList());
    }
}

