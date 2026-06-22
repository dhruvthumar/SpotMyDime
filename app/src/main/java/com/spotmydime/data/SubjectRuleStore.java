package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SubjectRuleStore {

    private static final String PREFS_NAME = "subject_rules";
    private static final String KEY_RULES = "rules";

    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "a", "an", "the", "for", "of", "to", "in", "on", "at", "by",
            "with", "without", "and", "or", "but", "is", "are", "was", "were",
            "be", "been", "have", "has", "had", "do", "does", "did", "will",
            "would", "shall", "should", "may", "might", "must", "can", "could",
            "your", "our", "my", "its", "his", "her", "their", "this", "that",
            "these", "those", "from", "about", "into", "through", "during",
            "before", "after", "above", "below", "between", "out", "off",
            "over", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "each", "every",
            "both", "few", "more", "most", "other", "some", "such", "no",
            "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "just", "because", "as", "until", "up", "down", "s", "t", "re",
            "subject", "regarding"
    )));

    private final SharedPreferences prefs;

    public SubjectRuleStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<String> extractKeywords(String subject) {
        if (subject == null || subject.isEmpty()) return Collections.emptyList();
        String cleaned = subject.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String[] words = cleaned.split(" ");
        Set<String> unique = new HashSet<>();
        for (String w : words) {
            w = w.trim();
            if (w.length() > 1 && !STOP_WORDS.contains(w)) {
                unique.add(w);
            }
        }
        List<String> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }

    public SubjectRule findMatch(String senderEmail, String subject) {
        if (senderEmail == null || subject == null) return null;
        List<String> subjectKeywords = extractKeywords(subject);
        if (subjectKeywords.isEmpty()) return null;

        Map<String, Map<String, SubjectRule>> all = load();
        Map<String, SubjectRule> senderRules = all.get(senderEmail);
        if (senderRules == null) return null;

        SubjectRule best = null;
        int bestMatchCount = 0;

        for (Map.Entry<String, SubjectRule> entry : senderRules.entrySet()) {
            String[] ruleKeywords = entry.getKey().split(",");
            boolean allMatch = true;
            for (String rk : ruleKeywords) {
                if (!subjectKeywords.contains(rk)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch && ruleKeywords.length > bestMatchCount) {
                best = entry.getValue();
                bestMatchCount = ruleKeywords.length;
            }
        }

        return best;
    }

    public void setRule(String senderEmail, String subject, String alias, String category) {
        if (senderEmail == null || subject == null) return;
        List<String> keywords = extractKeywords(subject);
        if (keywords.isEmpty()) return;
        String key = String.join(",", keywords);

        Map<String, Map<String, SubjectRule>> all = load();
        Map<String, SubjectRule> senderRules = all.computeIfAbsent(senderEmail, k -> new HashMap<>());
        senderRules.put(key, new SubjectRule(senderEmail, key, alias, category));
        save(all);
    }

    public void removeRule(String senderEmail, String keywordsKey) {
        if (senderEmail == null || keywordsKey == null) return;
        Map<String, Map<String, SubjectRule>> all = load();
        Map<String, SubjectRule> senderRules = all.get(senderEmail);
        if (senderRules != null) {
            senderRules.remove(keywordsKey);
            if (senderRules.isEmpty()) {
                all.remove(senderEmail);
            }
            save(all);
        }
    }

    public Map<String, Map<String, SubjectRule>> getAll() {
        return load();
    }

    private Map<String, Map<String, SubjectRule>> load() {
        String json = prefs.getString(KEY_RULES, "{}");
        try {
            JSONObject root = new JSONObject(new JSONTokener(json));
            Map<String, Map<String, SubjectRule>> result = new HashMap<>();
            Iterator<String> senders = root.keys();
            while (senders.hasNext()) {
                String sender = senders.next();
                JSONObject senderObj = root.getJSONObject(sender);
                Map<String, SubjectRule> rules = new HashMap<>();
                Iterator<String> keys = senderObj.keys();
                while (keys.hasNext()) {
                    String keywordsKey = keys.next();
                    JSONObject ruleObj = senderObj.getJSONObject(keywordsKey);
                    String alias = ruleObj.optString("alias", null);
                    String category = ruleObj.optString("category", null);
                    if (alias != null || category != null) {
                        rules.put(keywordsKey, new SubjectRule(sender, keywordsKey, alias, category));
                    }
                }
                if (!rules.isEmpty()) {
                    result.put(sender, rules);
                }
            }
            return result;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void save(Map<String, Map<String, SubjectRule>> data) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, Map<String, SubjectRule>> senderEntry : data.entrySet()) {
                JSONObject senderObj = new JSONObject();
                for (Map.Entry<String, SubjectRule> ruleEntry : senderEntry.getValue().entrySet()) {
                    JSONObject ruleObj = new JSONObject();
                    if (ruleEntry.getValue().alias != null) {
                        ruleObj.put("alias", ruleEntry.getValue().alias);
                    }
                    if (ruleEntry.getValue().category != null) {
                        ruleObj.put("category", ruleEntry.getValue().category);
                    }
                    senderObj.put(ruleEntry.getKey(), ruleObj);
                }
                root.put(senderEntry.getKey(), senderObj);
            }
            prefs.edit().putString(KEY_RULES, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static class SubjectRule {
        public final String senderEmail;
        public final String keywordsKey;
        public final String alias;
        public final String category;

        public SubjectRule(String senderEmail, String keywordsKey, String alias, String category) {
            this.senderEmail = senderEmail;
            this.keywordsKey = keywordsKey;
            this.alias = alias;
            this.category = category;
        }

        public List<String> getKeywords() {
            if (keywordsKey == null || keywordsKey.isEmpty()) return Collections.emptyList();
            return Arrays.asList(keywordsKey.split(","));
        }
    }
}
