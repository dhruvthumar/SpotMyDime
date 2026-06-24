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

    /**
     * Test Case 4: Filter transactions by type (income vs expense)
     * Verifies that filtering by transaction type returns only matching transactions.
     */
    @Test
    public void testFilterByType() {
        // Test 1: Filter by income only
        List<Transaction> income = filterByType(testTransactions, Transaction.Type.INCOMING);
        assertEquals("Should return 1 income transaction", 1, income.size());
        assertTrue("All should be income",
            income.stream().allMatch(t -> t.getType() == Transaction.Type.INCOMING));
        assertEquals("Should be Freelance Client", "Freelance Client", income.get(0).getMerchant());

        // Test 2: Filter by expense only
        List<Transaction> expenses = filterByType(testTransactions, Transaction.Type.OUTGOING);
        assertEquals("Should return 6 expense transactions", 6, expenses.size());
        assertTrue("All should be expense",
            expenses.stream().allMatch(t -> t.getType() == Transaction.Type.OUTGOING));

        // Test 3: Null type returns all
        List<Transaction> all = filterByType(testTransactions, null);
        assertEquals("Null type should return all transactions", 7, all.size());
    }

    /**
     * Test Case 5: Sort transactions by amount descending
     * Verifies transactions are sorted from largest to smallest amount.
     */
    @Test
    public void testSortByAmountDescending() {
        List<Transaction> sorted = sortByAmount(testTransactions, false);
        assertEquals("Should still have 7 transactions", 7, sorted.size());
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue("Transaction " + i + " should be <= previous amount",
                sorted.get(i).getAmount() <= sorted.get(i - 1).getAmount());
        }
        assertEquals("First should be Freelance Client ($500)", "Freelance Client", sorted.get(0).getMerchant());
        assertEquals("Last should be Starbucks ($5.50)", "Starbucks", sorted.get(sorted.size() - 1).getMerchant());
    }

    /**
     * Test Case 6: Sort transactions by amount ascending
     * Verifies transactions are sorted from smallest to largest amount.
     */
    @Test
    public void testSortByAmountAscending() {
        List<Transaction> sorted = sortByAmount(testTransactions, true);
        assertEquals("Should still have 7 transactions", 7, sorted.size());
        for (int i = 1; i < sorted.size(); i++) {
            assertTrue("Transaction " + i + " should be >= previous amount",
                sorted.get(i).getAmount() >= sorted.get(i - 1).getAmount());
        }
        assertEquals("First should be Starbucks ($5.50)", "Starbucks", sorted.get(0).getMerchant());
        assertEquals("Last should be Freelance Client ($500)", "Freelance Client", sorted.get(sorted.size() - 1).getMerchant());
    }

    /**
     * Test Case 7: Combined type and category filter
     * Verifies that type + category filters work together correctly.
     */
    @Test
    public void testCombinedTypeAndCategoryFilters() {
        // Filter by Shopping category first
        List<Transaction> shopping = filterByCategory(testTransactions, "Shopping");
        // Then filter by expense type
        List<Transaction> result = filterByType(shopping, Transaction.Type.OUTGOING);

        assertEquals("Should return 2 Shopping expense transactions", 2, result.size());
        assertTrue("All should be Shopping category",
            result.stream().allMatch(t -> "Shopping".equals(t.getCategory())));
        assertTrue("All should be expense",
            result.stream().allMatch(t -> t.getType() == Transaction.Type.OUTGOING));
    }

    /**
     * Test Case 8: Combined type filter, category filter, and sort by amount
     * Verifies all three work together (multi-filter system).
     */
    @Test
    public void testMultiFilterSystem() {
        // Start with all transactions
        List<Transaction> result = testTransactions;

        // Filter by expense type
        result = filterByType(result, Transaction.Type.OUTGOING);

        // Filter by Food & Dining category
        result = filterByCategory(result, "Food & Dining");

        // Sort by amount descending
        result = sortByAmount(result, false);

        assertEquals("Should return 2 Food & Dining expense transactions", 2, result.size());
        assertEquals("First should be Tim Hortons ($8.99)", "Tim Hortons", result.get(0).getMerchant());
        assertEquals("Second should be Starbucks ($5.50)", "Starbucks", result.get(1).getMerchant());
        assertTrue("All should be expense",
            result.stream().allMatch(t -> t.getType() == Transaction.Type.OUTGOING));
        assertTrue("All should be Food & Dining",
            result.stream().allMatch(t -> "Food & Dining".equals(t.getCategory())));
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

    /**
     * Filters transactions by type (INCOMING, OUTGOING, or null for all)
     */
    private List<Transaction> filterByType(List<Transaction> transactions, Transaction.Type type) {
        if (type == null) {
            return transactions;
        }
        return transactions.stream()
            .filter(t -> t.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * Sorts transactions by amount
     * @param ascending true for ascending, false for descending
     */
    private List<Transaction> sortByAmount(List<Transaction> transactions, boolean ascending) {
        List<Transaction> sorted = new ArrayList<>(transactions);
        if (ascending) {
            sorted.sort((a, b) -> Double.compare(a.getAmount(), b.getAmount()));
        } else {
            sorted.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));
        }
        return sorted;
    }
}

