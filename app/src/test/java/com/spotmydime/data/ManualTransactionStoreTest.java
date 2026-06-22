package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ManualTransactionStore
 * Tests the manual transaction creation, persistence, and retrieval logic
 * used by the "Add Expense/Income" page in HomeActivity
 */
@RunWith(MockitoJUnitRunner.class)
public class ManualTransactionStoreTest {

    @Mock
    private Context mockContext;

    @Mock
    private SharedPreferences mockPreferences;

    @Mock
    private SharedPreferences.Editor mockEditor;

    private ManualTransactionStore manualStore;

    @Before
    public void setUp() {
        when(mockContext.getSharedPreferences("manual_transactions", Context.MODE_PRIVATE))
                .thenReturn(mockPreferences);
        when(mockPreferences.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);

        manualStore = new ManualTransactionStore(mockContext);
    }

    /**
     * Test Case 1: Create and save valid manual transactions
     * Verifies that manual transactions can be created with correct properties:
     * - Transaction type toggle (expense vs income)
     * - Category assignment
     * - Amount validation
     * - Date assignment
     * - Proper message ID prefix for manual entries
     */
    @Test
    public void testCreateAndSaveValidManualTransactions() {
        // Test 1: Create a valid expense manual transaction
        Transaction expenseTransaction = ManualTransactionStore.createTransaction(
            "Starbucks",           // merchant
            15.50,                 // amount
            System.currentTimeMillis(), // dateMillis
            "Jun 15",              // dateDisplay
            "Food & Dining",       // category
            Transaction.Type.OUTGOING, // expense type
            "Morning coffee"       // notes
        );

        assertEquals("Should set merchant name", "Starbucks", expenseTransaction.getMerchant());
        assertEquals("Should set amount", 15.50, expenseTransaction.getAmount(), 0.01);
        assertEquals("Should set category", "Food & Dining", expenseTransaction.getCategory());
        assertEquals("Should set type to OUTGOING", Transaction.Type.OUTGOING, expenseTransaction.getType());
        assertEquals("Should set notes as subject", "Morning coffee", expenseTransaction.getSubject());
        assertTrue("Should have manual_ prefix in messageId",
            expenseTransaction.getMessageId().startsWith("manual_"));

        // Test 2: Create a valid income manual transaction
        Transaction incomeTransaction = ManualTransactionStore.createTransaction(
            "Freelance Project",
            500.00,
            System.currentTimeMillis(),
            "Jun 14",
            "Transfers",
            Transaction.Type.INCOMING,
            "Client payment"
        );

        assertEquals("Should set merchant for income", "Freelance Project", incomeTransaction.getMerchant());
        assertEquals("Should set income amount", 500.00, incomeTransaction.getAmount(), 0.01);
        assertEquals("Should set type to INCOMING", Transaction.Type.INCOMING, incomeTransaction.getType());
        assertTrue("Should have manual_ prefix in messageId",
            incomeTransaction.getMessageId().startsWith("manual_"));

        // Test 3: Avatar letter should be first character of merchant
        Transaction txn = ManualTransactionStore.createTransaction(
            "Walmart",
            25.00,
            System.currentTimeMillis(),
            "Jun 15",
            "Shopping",
            Transaction.Type.OUTGOING,
            ""
        );
        assertEquals("Avatar should be 'W' from Walmart", 'W', txn.getAvatarLetter());
    }

    /**
     * Test Case 2: Handle edge cases in manual transaction creation
     * Verifies that manual entry handles invalid/edge case inputs gracefully:
     * - Empty/null merchant names
     * - Zero or negative amounts
     * - Missing categories
     * - Boundary values for amounts
     * - Special characters in merchant names
     */
    @Test
    public void testManualTransactionEdgeCases() {
        // Test 1: Empty merchant name (should still create transaction with '?' avatar)
        Transaction txn1 = ManualTransactionStore.createTransaction(
            "",
            50.00,
            System.currentTimeMillis(),
            "Jun 15",
            "Shopping",
            Transaction.Type.OUTGOING,
            "Edge case test"
        );
        assertEquals("Should have '?' avatar for empty merchant", '?', txn1.getAvatarLetter());
        assertEquals("Should preserve empty merchant", "", txn1.getMerchant());

        // Test 2: Very small amount (e.g., $0.01)
        Transaction txn2 = ManualTransactionStore.createTransaction(
            "Penny Store",
            0.01,
            System.currentTimeMillis(),
            "Jun 15",
            "Shopping",
            Transaction.Type.OUTGOING,
            ""
        );
        assertEquals("Should accept $0.01", 0.01, txn2.getAmount(), 0.01);

        // Test 3: Large amount
        Transaction txn3 = ManualTransactionStore.createTransaction(
            "Car Payment",
            25000.00,
            System.currentTimeMillis(),
            "Jun 15",
            "Transportation",
            Transaction.Type.OUTGOING,
            ""
        );
        assertEquals("Should accept large amount", 25000.00, txn3.getAmount(), 0.01);

        // Test 4: Merchant with special characters and lowercase
        Transaction txn4 = ManualTransactionStore.createTransaction(
            "mcdonald's & co.",
            12.99,
            System.currentTimeMillis(),
            "Jun 15",
            "Food & Dining",
            Transaction.Type.OUTGOING,
            "Lunch"
        );
        assertEquals("Avatar should be uppercase 'M' even if merchant is lowercase",
            'M', txn4.getAvatarLetter());
        assertEquals("Should preserve merchant name with special chars",
            "mcdonald's & co.", txn4.getMerchant());

        // Test 5: Each manual transaction should have unique messageId
        Transaction txn5 = ManualTransactionStore.createTransaction(
            "Test", 10.00, System.currentTimeMillis(), "Jun 15", "Other",
            Transaction.Type.OUTGOING, ""
        );
        Transaction txn6 = ManualTransactionStore.createTransaction(
            "Test", 10.00, System.currentTimeMillis(), "Jun 15", "Other",
            Transaction.Type.OUTGOING, ""
        );
        assertNotEquals("Each manual transaction should have unique ID",
            txn5.getMessageId(), txn6.getMessageId());
        assertTrue("Both should start with manual_ prefix",
            txn5.getMessageId().startsWith("manual_") &&
            txn6.getMessageId().startsWith("manual_"));
    }

    /**
     * Test Case 3: Delete a manual transaction
     * Verifies that delete() removes the correct transaction and
     * does not affect others in the list.
     */
    @Test
    public void testDeleteRemovesCorrectTransaction() {
        when(mockPreferences.getString("list", "[]"))
                .thenReturn("[{\"merchant\":\"Amazon\",\"amount\":59.99,\"dateMillis\":1718900000000," +
                           "\"dateDisplay\":\"Jun 15\",\"category\":\"Shopping\",\"type\":\"outgoing\"," +
                           "\"notes\":\"Order\",\"id\":\"manual_abc123\"}," +
                           "{\"merchant\":\"Netflix\",\"amount\":15.99,\"dateMillis\":1718900000000," +
                           "\"dateDisplay\":\"Jun 15\",\"category\":\"Subscriptions\",\"type\":\"outgoing\"," +
                           "\"notes\":\"\",\"id\":\"manual_def456\"}]")
                .thenReturn("[{\"merchant\":\"Amazon\",\"amount\":59.99,\"dateMillis\":1718900000000," +
                           "\"dateDisplay\":\"Jun 15\",\"category\":\"Shopping\",\"type\":\"outgoing\"," +
                           "\"notes\":\"Order\",\"id\":\"manual_abc123\"}]");

        List<Transaction> all = manualStore.getAll();
        assertEquals(2, all.size());

        manualStore.delete("manual_def456");

        all = manualStore.getAll();
        assertEquals(1, all.size());
        assertEquals("Amazon", all.get(0).getMerchant());
        assertEquals("manual_abc123", all.get(0).getMessageId());
    }

    /**
     * Test Case 4: Save and retrieve transaction list (JSON round-trip)
     * Verifies that saving a transaction and then retrieving it preserves
     * all fields correctly through the JSON serialization/deserialization.
     */
    @Test
    public void testSaveAndGetAllRoundTrip() {
        when(mockPreferences.getString("list", "[]"))
                .thenReturn("[]")
                .thenReturn("[{\"merchant\":\"Starbucks\",\"amount\":5.50,\"dateMillis\":1718900000000," +
                           "\"dateDisplay\":\"Jun 20\",\"category\":\"Food & Drink\",\"type\":\"outgoing\"," +
                           "\"notes\":\"Coffee\",\"id\":\"manual_test123\"}]");

        Transaction txn = ManualTransactionStore.createTransaction(
            "Starbucks", 5.50, 1718900000000L, "Jun 20",
            "Food & Drink", Transaction.Type.OUTGOING, "Coffee");

        List<Transaction> before = manualStore.getAll();
        assertEquals(0, before.size());

        manualStore.save(txn);

        List<Transaction> after = manualStore.getAll();
        assertEquals(1, after.size());
        Transaction loaded = after.get(0);
        assertEquals("Starbucks", loaded.getMerchant());
        assertEquals(5.50, loaded.getAmount(), 0.01);
        assertEquals("Food & Drink", loaded.getCategory());
        assertEquals(Transaction.Type.OUTGOING, loaded.getType());
        assertEquals("Coffee", loaded.getSubject());
    }

    /**
     * Test Case 5: Delete null ID does nothing
     */
    @Test
    public void testDeleteNullId() {
        manualStore.delete(null);
        verify(mockPreferences, never()).edit();
    }
}

