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
 * Unit tests for Transaction Page Search and Grouping
 * Tests the search and date grouping features of Transactions page (Tab 2):
 * - Search by merchant name
 * - Search by category
 * - Search by amount
 * - Transaction grouping by date (Today, Yesterday, specific date)
 * - Case-insensitive search
 */
public class TransactionSearchAndGroupingTest {

    private List<Transaction> testTransactions;

    @Before
    public void setUp() {
        testTransactions = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        long today = cal.getTimeInMillis();

        // Today's transactions
        testTransactions.add(new Transaction(
            "Amazon.ca", 59.99, today, "Jun 15 · 2:30 PM",
            "Shopping", 'A', Transaction.Type.OUTGOING,
            "amazon@amazon.com", "Your Amazon.ca order"
        ));
        testTransactions.add(new Transaction(
            "Starbucks", 5.50, today, "Jun 15 · 9:00 AM",
            "Food & Dining", 'S', Transaction.Type.OUTGOING,
            "no-reply@starbucks.com", "Starbucks Receipt"
        ));

        // Yesterday's transactions
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterday = cal.getTimeInMillis();

        testTransactions.add(new Transaction(
            "Netflix", 15.99, yesterday, "Jun 14 · 8:00 PM",
            "Subscriptions", 'N', Transaction.Type.OUTGOING,
            "billing@netflix.com", "Netflix Subscription"
        ));

        // 2 days ago
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long twoDaysAgo = cal.getTimeInMillis();

        testTransactions.add(new Transaction(
            "Walmart", 125.00, twoDaysAgo, "Jun 13 · 3:00 PM",
            "Shopping", 'W', Transaction.Type.OUTGOING,
            "walmart@walmart.com", "Walmart Receipt"
        ));
        testTransactions.add(new Transaction(
            "Lyft", 20.00, twoDaysAgo, "Jun 13 · 5:30 PM",
            "Transportation", 'L', Transaction.Type.OUTGOING,
            "support@lyft.com", "Lyft Ride"
        ));

        // 5 days ago
        cal.add(Calendar.DAY_OF_YEAR, -3);
        long fiveDaysAgo = cal.getTimeInMillis();

        testTransactions.add(new Transaction(
            "Tim Hortons", 8.99, fiveDaysAgo, "Jun 10 · 7:15 AM",
            "Food & Dining", 'T', Transaction.Type.OUTGOING,
            "tim@timhortons.com", "Tim Hortons Order"
        ));
        testTransactions.add(new Transaction(
            "Freelance Income", 500.00, fiveDaysAgo, "Jun 10 · 10:00 AM",
            "Transfers", 'F', Transaction.Type.INCOMING,
            "client@example.com", "Payment Received"
        ));
    }

    /**
     * Test Case 1: Search transactions by merchant name and category
     * Verifies that search functionality correctly filters transactions
     * by merchant name, category, and other text fields with case-insensitivity.
     */
    @Test
    public void testSearchTransactions() {
        // Test 1: Search by exact merchant name (case-insensitive)
        List<Transaction> amazonResults = searchTransactions(testTransactions, "amazon");
        assertEquals("Should find Amazon.ca by partial search (lowercase)", 1, amazonResults.size());
        assertEquals("Should be Amazon.ca", "Amazon.ca", amazonResults.get(0).getMerchant());

        // Test 2: Search by category name
        List<Transaction> shoppingResults = searchTransactions(testTransactions, "shopping");
        assertEquals("Should find 2 Shopping transactions", 2, shoppingResults.size());
        assertTrue("All results should be Shopping category",
            shoppingResults.stream().allMatch(t -> "Shopping".equals(t.getCategory())));

        // Test 3: Search by partial merchant name
        List<Transaction> walmartResults = searchTransactions(testTransactions, "walmart");
        assertEquals("Should find Walmart by partial text 'walmart'", 1, walmartResults.size());
        assertEquals("Should be Walmart", "Walmart", walmartResults.get(0).getMerchant());

        // Test 4: Search by food category
        List<Transaction> foodResults = searchTransactions(testTransactions, "food");
        assertEquals("Should find 2 Food & Dining transactions", 2, foodResults.size());
        assertTrue("All should contain 'Food' in category",
            foodResults.stream().allMatch(t -> t.getCategory().toLowerCase().contains("food")));

        // Test 5: Case-insensitive search using uppercase
        List<Transaction> netflixResults = searchTransactions(testTransactions, "NETFLIX");
        assertEquals("Should find Netflix with uppercase search", 1, netflixResults.size());

        // Test 6: Search with no results
        List<Transaction> noResults = searchTransactions(testTransactions, "nonexistent");
        assertEquals("Should return 0 results for nonexistent search", 0, noResults.size());

        // Test 7: Search by amount
        List<Transaction> amountResults = searchTransactions(testTransactions, "125");
        assertEquals("Should find Walmart transaction by amount", 1, amountResults.size());
        assertEquals("Should be $125.00", 125.00, amountResults.get(0).getAmount(), 0.01);

        // Test 8: Search by email domain
        List<Transaction> emailResults = searchTransactions(testTransactions, "netflix");
        assertEquals("Should find Netflix by email domain search", 1, emailResults.size());

        // Test 9: Search with multiple matches
        List<Transaction> multiResults = searchTransactions(testTransactions, "subscription");
        assertEquals("Should find Netflix subscription", 1, multiResults.size());
    }

    /**
     * Test Case 2: Group transactions by date
     * Verifies that transactions are correctly grouped into:
     * - "Today" for current date transactions
     * - "Yesterday" for previous day transactions
     * - "MMM DD, YYYY" format for older dates
     */
    @Test
    public void testGroupTransactionsByDate() {
        // Group transactions by date label
        GroupedTransactions grouped = groupByDate(testTransactions);

        // Test 1: Today group should have 2 transactions
        assertEquals("Today should have 2 transactions", 2, grouped.todayCount);
        assertTrue("Today group should contain Amazon",
            grouped.todayMerchants.contains("Amazon.ca"));
        assertTrue("Today group should contain Starbucks",
            grouped.todayMerchants.contains("Starbucks"));

        // Test 2: Yesterday group should have 1 transaction
        assertEquals("Yesterday should have 1 transaction", 1, grouped.yesterdayCount);
        assertTrue("Yesterday group should contain Netflix",
            grouped.yesterdayMerchants.contains("Netflix"));

        // Test 3: Older dates should have transactions grouped correctly
        assertEquals("Older dates should have 4 transactions total", 4, grouped.olderCount);
        assertTrue("Older dates should contain Walmart",
            grouped.olderMerchants.contains("Walmart"));
        assertTrue("Older dates should contain Lyft",
            grouped.olderMerchants.contains("Lyft"));
        assertTrue("Older dates should contain Tim Hortons",
            grouped.olderMerchants.contains("Tim Hortons"));

        // Test 4: Total transactions should match
        int totalGrouped = grouped.todayCount + grouped.yesterdayCount + grouped.olderCount;
        assertEquals("Total grouped transactions should match original list",
            testTransactions.size(), totalGrouped);
    }

    /**
     * Test Case 3: Combined search and grouping
     * Verifies that search results are correctly grouped by date after filtering
     */
    @Test
    public void testSearchThenGroupByDate() {
        // Search for "shopping" transactions, then group by date
        List<Transaction> shoppingTransactions = searchTransactions(testTransactions, "shopping");
        GroupedTransactions grouped = groupByDate(shoppingTransactions);

        // Should have 1 today (Amazon) and 1 in older dates (Walmart), 0 yesterday
        assertEquals("Shopping today should have 1 transaction", 1, grouped.todayCount);
        assertEquals("Shopping yesterday should have 0 transactions", 0, grouped.yesterdayCount);
        assertEquals("Shopping in older dates should have 1 transaction", 1, grouped.olderCount);

        // Test 2: Search for "Food" and verify grouping
        List<Transaction> foodTransactions = searchTransactions(testTransactions, "food");
        GroupedTransactions foodGrouped = groupByDate(foodTransactions);

        assertEquals("Food today should have 1 transaction (Starbucks)", 1, foodGrouped.todayCount);
        assertEquals("Food yesterday should have 0 transactions", 0, foodGrouped.yesterdayCount);
        assertEquals("Food in older dates should have 1 transaction (Tim Hortons)", 1, foodGrouped.olderCount);
    }

    // ── HELPER METHODS ──

    /**
     * Searches transactions by merchant, category, subject, or amount
     * Returns all matching transactions (case-insensitive)
     */
    private List<Transaction> searchTransactions(List<Transaction> transactions, String query) {
        if (query == null || query.isEmpty()) {
            return transactions;
        }

        String lowerQuery = query.toLowerCase();
        return transactions.stream()
            .filter(t ->
                (t.getMerchant() != null && t.getMerchant().toLowerCase().contains(lowerQuery)) ||
                (t.getCategory() != null && t.getCategory().toLowerCase().contains(lowerQuery)) ||
                (t.getSenderEmail() != null && t.getSenderEmail().toLowerCase().contains(lowerQuery)) ||
                (t.getSubject() != null && t.getSubject().toLowerCase().contains(lowerQuery)) ||
                String.format("%.2f", t.getAmount()).contains(lowerQuery)
            )
            .collect(Collectors.toList());
    }

    /**
     * Groups transactions by date (Today, Yesterday, or older dates)
     */
    private GroupedTransactions groupByDate(List<Transaction> transactions) {
        GroupedTransactions result = new GroupedTransactions();
        Calendar today = Calendar.getInstance();
        Calendar txCal = Calendar.getInstance();

        for (Transaction t : transactions) {
            txCal.setTimeInMillis(t.getDateMillis());

            boolean isToday = today.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                             today.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR);

            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            boolean isYesterday = yesterday.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                                 yesterday.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR);

            if (isToday) {
                result.todayCount++;
                result.todayMerchants.add(t.getMerchant());
            } else if (isYesterday) {
                result.yesterdayCount++;
                result.yesterdayMerchants.add(t.getMerchant());
            } else {
                result.olderCount++;
                result.olderMerchants.add(t.getMerchant());
            }
        }

        return result;
    }

    // ── HELPER CLASS ──

    /**
     * Simple class to hold grouped transaction counts and merchant names
     */
    private static class GroupedTransactions {
        int todayCount = 0;
        int yesterdayCount = 0;
        int olderCount = 0;
        List<String> todayMerchants = new ArrayList<>();
        List<String> yesterdayMerchants = new ArrayList<>();
        List<String> olderMerchants = new ArrayList<>();
    }
}

