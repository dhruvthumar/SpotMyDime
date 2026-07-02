package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.spotmydime.util.SecurePrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ExcludedPatternStore {

    private static final String PREFS_NAME = "excluded_patterns";
    private static final String KEY = "patterns";
    private static final int MAX_PATTERNS = 200;
    private final SharedPreferences prefs;

    public ExcludedPatternStore(Context context) {
        prefs = SecurePrefs.get(context, PREFS_NAME);
    }

    public void addExclusion(String merchant, String subject) {
        if (merchant == null || subject == null) return;
        try {
            String key = buildKey(merchant, subject);
            List<String> all = loadAll();
            if (all.contains(key)) return;
            all.add(0, key);
            if (all.size() > MAX_PATTERNS) {
                all = all.subList(0, MAX_PATTERNS);
            }
            saveAll(all);
        } catch (Exception ignored) {}
    }

    public boolean isExcluded(String merchant, String subject) {
        if (merchant == null || subject == null) return false;
        String key = buildKey(merchant, subject);
        return loadAll().contains(key);
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }

    public List<ExcludedPattern> getPatterns() {
        List<ExcludedPattern> patterns = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                patterns.add(new ExcludedPattern(
                        o.optString("merchant", ""),
                        o.optString("subject", ""),
                        o.optString("normalized", "")
                ));
            }
        } catch (Exception ignored) {}
        return patterns;
    }

    private List<String> loadAll() {
        List<String> list = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i).optString("normalized", ""));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveAll(List<String> normalizedKeys) {
        try {
            JSONArray arr = new JSONArray();
            for (String key : normalizedKeys) {
                String[] parts = key.split("\\|", 2);
                JSONObject o = new JSONObject();
                o.put("merchant", parts.length > 0 ? parts[0] : "");
                o.put("subject", parts.length > 1 ? parts[1] : "");
                o.put("normalized", key);
                arr.put(o);
            }
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    static String buildKey(String merchant, String subject) {
        return merchant.toLowerCase().trim() + "|" + normalizeSubject(subject);
    }

    static String normalizeSubject(String subject) {
        if (subject == null) return "";
        return subject.toLowerCase()
                .replaceAll("\\d+", "#")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static class ExcludedPattern {
        public final String merchant;
        public final String subject;
        public final String normalized;
        public ExcludedPattern(String merchant, String subject, String normalized) {
            this.merchant = merchant;
            this.subject = subject;
            this.normalized = normalized;
        }
    }
}
