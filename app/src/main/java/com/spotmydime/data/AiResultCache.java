package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.spotmydime.util.SecurePrefs;

import org.json.JSONObject;

public class AiResultCache {

    private static final String PREFS_NAME = "ai_result_cache";
    private final SharedPreferences prefs;

    public AiResultCache(Context context) {
        prefs = SecurePrefs.get(context, PREFS_NAME);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public void put(String messageId, String merchant, String category,
                    Double amount, String dateStr, String type) {
        if (messageId == null) return;
        try {
            JSONObject o = new JSONObject();
            if (merchant != null) o.put("merchant", merchant);
            if (category != null) o.put("category", category);
            if (amount != null) o.put("amount", amount);
            if (dateStr != null) o.put("date", dateStr);
            if (type != null) o.put("type", type);
            prefs.edit().putString(messageId, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public CachedResult get(String messageId) {
        if (messageId == null) return null;
        String json = prefs.getString(messageId, null);
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            String merchant = o.optString("merchant", null);
            String category = o.optString("category", null);
            Double amount = o.has("amount") ? o.optDouble("amount", -1) : null;
            String date = o.optString("date", null);
            String type = o.optString("type", null);
            if (amount != null && amount == -1) amount = null;
            return new CachedResult(merchant, category, amount, date, type);
        } catch (Exception e) {
            return null;
        }
    }

    public void remove(String messageId) {
        if (messageId == null) return;
        prefs.edit().remove(messageId).apply();
    }

    public static class CachedResult {
        public final String merchant;
        public final String category;
        public final Double amount;
        public final String date;
        public final String type;

        public CachedResult(String merchant, String category, Double amount, String date, String type) {
            this.merchant = merchant;
            this.category = category;
            this.amount = amount;
            this.date = date;
            this.type = type;
        }
    }
}
