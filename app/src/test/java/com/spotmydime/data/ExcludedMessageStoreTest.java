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
public class ExcludedMessageStoreTest {

    @Mock private Context mockContext;
    @Mock private SharedPreferences mockPreferences;
    @Mock private SharedPreferences.Editor mockEditor;
    private ExcludedMessageStore store;

    @Before
    public void setUp() {
        when(mockContext.getSharedPreferences("excluded_messages", Context.MODE_PRIVATE))
                .thenReturn(mockPreferences);
        when(mockPreferences.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        store = new ExcludedMessageStore(mockContext);
    }

    @Test
    public void testExcludeAndIsExcluded() {
        when(mockPreferences.getString("ids", "[]"))
                .thenReturn("[]")
                .thenReturn("[\"msg1\"]");

        assertFalse(store.isExcluded("msg1"));
        store.exclude("msg1");
        assertTrue(store.isExcluded("msg1"));
    }

    @Test
    public void testExcludeDuplicateDoesNotThrow() {
        when(mockPreferences.getString("ids", "[]"))
                .thenReturn("[]")
                .thenReturn("[\"msg1\"]")
                .thenReturn("[\"msg1\"]");

        store.exclude("msg1");
        store.exclude("msg1");
        assertTrue(store.isExcluded("msg1"));
    }

    @Test
    public void testMultipleExcludedMessages() {
        when(mockPreferences.getString("ids", "[]"))
                .thenReturn("[]")
                .thenReturn("[\"msg1\"]")
                .thenReturn("[\"msg1\",\"msg2\"]");

        store.exclude("msg1");
        store.exclude("msg2");
        assertTrue(store.isExcluded("msg1"));
        assertTrue(store.isExcluded("msg2"));
    }

    @Test
    public void testNotExcludedMessages() {
        when(mockPreferences.getString("ids", "[]"))
                .thenReturn("[]")
                .thenReturn("[\"msg1\"]");

        store.exclude("msg1");
        assertFalse(store.isExcluded("msg3"));
        assertFalse(store.isExcluded("other"));
    }

    @Test
    public void testNullHandling() {
        assertFalse(store.isExcluded(null));
        store.exclude(null);
    }

    @Test
    public void testEmptyStore() {
        when(mockPreferences.getString("ids", "[]")).thenReturn("[]");
        assertFalse(store.isExcluded("anything"));
    }

    @Test
    public void testLoadMalformedJson() {
        when(mockPreferences.getString("ids", "[]")).thenReturn("{invalid}");
        assertFalse(store.isExcluded("msg1"));
    }
}
