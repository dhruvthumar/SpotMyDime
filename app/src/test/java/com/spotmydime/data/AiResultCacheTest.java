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

@RunWith(MockitoJUnitRunner.class)
public class AiResultCacheTest {

    @Mock private Context mockContext;
    @Mock private SharedPreferences mockPreferences;
    @Mock private SharedPreferences.Editor mockEditor;
    private AiResultCache cache;

    @Before
    public void setUp() {
        when(mockContext.getSharedPreferences("ai_result_cache", Context.MODE_PRIVATE))
                .thenReturn(mockPreferences);
        when(mockPreferences.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
        when(mockEditor.clear()).thenReturn(mockEditor);
        cache = new AiResultCache(mockContext);
    }

    @Test
    public void testPutAndGetRoundTrip() {
        when(mockPreferences.getString("msg1", null))
                .thenReturn(null)
                .thenReturn("{\"merchant\":\"Netflix\",\"category\":\"Subscriptions\",\"amount\":15.99,\"date\":\"Jun 20\",\"type\":\"outgoing\"}");

        assertNull(cache.get("msg1"));
        cache.put("msg1", "Netflix", "Subscriptions", 15.99, "Jun 20", "outgoing");
        AiResultCache.CachedResult result = cache.get("msg1");
        assertNotNull(result);
        assertEquals("Netflix", result.merchant);
        assertEquals("Subscriptions", result.category);
        assertEquals(15.99, result.amount, 0.01);
        assertEquals("Jun 20", result.date);
        assertEquals("outgoing", result.type);
    }

    @Test
    public void testGetNonexistentKey() {
        when(mockPreferences.getString("unknown", null)).thenReturn(null);
        assertNull(cache.get("unknown"));
    }

    @Test
    public void testOverwriteExistingEntry() {
        when(mockPreferences.getString("msg1", null))
                .thenReturn("{\"merchant\":\"Netflix\",\"category\":\"Subscriptions\",\"amount\":15.99,\"date\":\"Jun 20\",\"type\":\"outgoing\"}")
                .thenReturn("{\"merchant\":\"Netflix\",\"category\":\"Entertainment\",\"amount\":19.99,\"date\":\"Jun 21\",\"type\":\"outgoing\"}");

        cache.put("msg1", "Netflix", "Subscriptions", 15.99, "Jun 20", "outgoing");
        AiResultCache.CachedResult first = cache.get("msg1");
        assertEquals("Subscriptions", first.category);

        cache.put("msg1", "Netflix", "Entertainment", 19.99, "Jun 21", "outgoing");
        AiResultCache.CachedResult second = cache.get("msg1");
        assertEquals("Entertainment", second.category);
        assertEquals(19.99, second.amount, 0.01);
    }

    @Test
    public void testPutNullMessageIdDoesNothing() {
        cache.put(null, "Netflix", "Subscriptions", 15.99, null, null);
        verify(mockPreferences, never()).edit();
    }

    @Test
    public void testGetNullMessageIdReturnsNull() {
        assertNull(cache.get(null));
    }

    @Test
    public void testPutWithNullOptionalFields() {
        when(mockPreferences.getString("msg2", null))
                .thenReturn("{\"merchant\":\"Amazon\",\"category\":\"Shopping\"}");

        cache.put("msg2", "Amazon", "Shopping", null, null, null);
        AiResultCache.CachedResult result = cache.get("msg2");
        assertNotNull(result);
        assertEquals("Amazon", result.merchant);
        assertEquals("Shopping", result.category);
        assertNull(result.amount);
        assertNull(result.date);
        assertNull(result.type);
    }

    @Test
    public void testRemove() {
        cache.remove("msg1");
        verify(mockEditor).remove("msg1");
        verify(mockEditor).apply();
    }

    @Test
    public void testRemoveNullDoesNothing() {
        cache.remove(null);
        verify(mockPreferences, never()).edit();
    }

    @Test
    public void testClear() {
        cache.clear();
        verify(mockEditor).clear();
        verify(mockEditor).apply();
    }

    @Test
    public void testLoadMalformedJsonReturnsNull() {
        when(mockPreferences.getString("bad", null)).thenReturn("{not valid json}");
        assertNull(cache.get("bad"));
    }
}
