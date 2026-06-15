package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TransactionOverrideStore {

    private static final String PREFS_NAME = "tx_overrides";
    private static final String KEY_DATA = "overrides";

    private final SharedPreferences prefs;

    public TransactionOverrideStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getType(String messageId) {
        return getField(messageId, "type");
    }

    public Double getAmount(String messageId) {
        String val = getField(messageId, "amount");
        if (val == null) return null;
        try { return Double.parseDouble(val); } catch (Exception e) { return null; }
    }

    public void setType(String messageId, String type) {
        setField(messageId, "type", type);
    }

    public void setAmount(String messageId, double amount) {
        setField(messageId, "amount", String.valueOf(amount));
    }

    private String getField(String messageId, String field) {
        if (messageId == null) return null;
        Map<String, Map<String, String>> all = load();
        Map<String, String> rec = all.get(messageId);
        return rec != null ? rec.get(field) : null;
    }

    private void setField(String messageId, String field, String value) {
        if (messageId == null) return;
        Map<String, Map<String, String>> all = load();
        Map<String, String> rec = all.get(messageId);
        if (rec == null) rec = new HashMap<>();
        rec.put(field, value);
        all.put(messageId, rec);
        save(all);
    }

    private Map<String, Map<String, String>> load() {
        String json = prefs.getString(KEY_DATA, "{}");
        try {
            JSONObject root = new JSONObject(new JSONTokener(json));
            Map<String, Map<String, String>> result = new HashMap<>();
            Iterator<String> msgIds = root.keys();
            while (msgIds.hasNext()) {
                String mid = msgIds.next();
                JSONObject rec = root.getJSONObject(mid);
                Map<String, String> fields = new HashMap<>();
                Iterator<String> fkeys = rec.keys();
                while (fkeys.hasNext()) {
                    String fk = fkeys.next();
                    fields.put(fk, rec.getString(fk));
                }
                result.put(mid, fields);
            }
            return result;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void save(Map<String, Map<String, String>> all) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, Map<String, String>> entry : all.entrySet()) {
                JSONObject rec = new JSONObject();
                for (Map.Entry<String, String> f : entry.getValue().entrySet()) {
                    rec.put(f.getKey(), f.getValue());
                }
                root.put(entry.getKey(), rec);
            }
            prefs.edit().putString(KEY_DATA, root.toString()).apply();
        } catch (Exception ignored) {}
    }
}
