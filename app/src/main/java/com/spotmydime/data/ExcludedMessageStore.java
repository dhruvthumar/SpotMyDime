package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.spotmydime.util.SecurePrefs;

import org.json.JSONArray;
import org.json.JSONTokener;

import java.util.HashSet;
import java.util.Set;

public class ExcludedMessageStore {

    private static final String PREFS_NAME = "excluded_messages";
    private static final String KEY_IDS = "ids";

    private final SharedPreferences prefs;

    public ExcludedMessageStore(Context context) {
        prefs = SecurePrefs.get(context, PREFS_NAME);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public boolean isExcluded(String messageId) {
        if (messageId == null) return false;
        return load().contains(messageId);
    }

    public void exclude(String messageId) {
        if (messageId == null) return;
        Set<String> set = load();
        set.add(messageId);
        save(set);
    }

    public Set<String> load() {
        String json = prefs.getString(KEY_IDS, "[]");
        try {
            JSONArray arr = new JSONArray(new JSONTokener(json));
            Set<String> set = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                set.add(arr.getString(i));
            }
            return set;
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private void save(Set<String> set) {
        try {
            JSONArray arr = new JSONArray();
            for (String id : set) {
                arr.put(id);
            }
            prefs.edit().putString(KEY_IDS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
