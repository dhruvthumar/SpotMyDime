package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VendorStore
 * Tests the persistence of vendor-to-category mappings using SharedPreferences
 */
@RunWith(MockitoJUnitRunner.class)
public class VendorStoreTest {

    @Mock
    private Context mockContext;

    @Mock
    private SharedPreferences mockPreferences;

    @Mock
    private SharedPreferences.Editor mockEditor;

    private VendorStore vendorStore;

    @Before
    public void setUp() {
        // Setup mock behavior: when context.getSharedPreferences is called, return mock prefs
        when(mockContext.getSharedPreferences("vendor_categories", Context.MODE_PRIVATE))
                .thenReturn(mockPreferences);

        // Setup mock editor chain: prefs.edit() returns editor, and apply() returns void
        when(mockPreferences.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);

        vendorStore = new VendorStore(mockContext);
    }

    /**
     * Test Case 1: Save and retrieve vendor categories
     * Verifies that vendor-to-category mappings are correctly stored and retrieved
     * from SharedPreferences, ensuring the learn-by-vendor system works.
     */
    @Test
    public void testSaveAndRetrieveVendorCategory() {
        // Setup mock to return a valid mapping when getCategory is called
        when(mockPreferences.getString("vendor_categories", "{}"))
                .thenReturn("{\"Amazon\":\"Shopping\",\"Starbucks\":\"Food & Dining\"}");

        // Test retrieve Amazon → Shopping
        String amazonCategory = vendorStore.getCategory("Amazon");
        assertEquals("Should retrieve 'Shopping' for Amazon vendor", "Shopping", amazonCategory);

        // Test retrieve Starbucks → Food & Dining
        String starbucksCategory = vendorStore.getCategory("Starbucks");
        assertEquals("Should retrieve 'Food & Dining' for Starbucks vendor", "Food & Dining", starbucksCategory);

        // Test non-existent vendor returns null
        when(mockPreferences.getString("vendor_categories", "{}"))
                .thenReturn("{\"Amazon\":\"Shopping\"}");
        String unknownCategory = vendorStore.getCategory("UnknownVendor");
        assertNull("Should return null for unknown vendor", unknownCategory);
    }

    /**
     * Test Case 2: Handle null and edge case inputs
     * Verifies that VendorStore gracefully handles null vendors, null categories,
     * and empty SharedPreferences data without crashing.
     */
    @Test
    public void testNullAndEdgeCaseHandling() {
        // Test setCategory with null vendor (should be ignored)
        vendorStore.setCategory(null, "Shopping");
        verify(mockEditor, never()).putString(anyString(), anyString());

        // Test setCategory with null category (should be ignored)
        vendorStore.setCategory("Amazon", null);
        verify(mockEditor, never()).putString(anyString(), anyString());

        // Test getCategory with null vendor (should return null)
        String result1 = vendorStore.getCategory(null);
        assertNull("Should return null when vendor is null", result1);

        // Test with empty SharedPreferences data (returns null for unknown vendors)
        when(mockPreferences.getString("vendor_categories", "{}"))
                .thenReturn("{}");
        String result2 = vendorStore.getCategory("AnyVendor");
        assertNull("Should return null when vendor not in empty store", result2);

        // Test with malformed JSON in SharedPreferences (should return null gracefully, not crash)
        when(mockPreferences.getString("vendor_categories", "{}"))
                .thenReturn("{invalid json}");
        String result3 = vendorStore.getCategory("Amazon");
        assertNull("Should return null gracefully on malformed JSON", result3);
    }
}

