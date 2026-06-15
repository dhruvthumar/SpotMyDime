package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class VendorAliasStore {

    private static final String PREFS_NAME = "vendor_aliases";
    private static final String KEY_MAPPINGS = "aliases";

    private final SharedPreferences prefs;

    public VendorAliasStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAlias(String original) {
        if (original == null) return null;
        return load().get(original);
    }

    public void setAlias(String original, String displayName) {
        if (original == null || displayName == null) return;
        Map<String, String> all = load();
        all.put(original, displayName);
        save(all);
    }

    public Map<String, String> getAll() {
        return load();
    }

    private Map<String, String> load() {
        String json = prefs.getString(KEY_MAPPINGS, "{}");
        try {
            JSONObject obj = new JSONObject(new JSONTokener(json));
            Map<String, String> map = new HashMap<>();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, obj.getString(k));
            }
            return map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void save(Map<String, String> map) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : map.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            prefs.edit().putString(KEY_MAPPINGS, obj.toString()).apply();
        } catch (Exception ignored) {}
    }
}
