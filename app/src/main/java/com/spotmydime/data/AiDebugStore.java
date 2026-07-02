package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.spotmydime.util.SecurePrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AiDebugStore {

    private static final String PREFS_NAME = "ai_debug_store";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 100;
    private final SharedPreferences prefs;

    public AiDebugStore(Context context) {
        prefs = SecurePrefs.get(context, PREFS_NAME);
    }

    public void add(AiDebugEntry entry) {
        try {
            List<AiDebugEntry> all = getAll();
            all.add(0, entry);
            if (all.size() > MAX_ENTRIES) {
                all = all.subList(0, MAX_ENTRIES);
            }
            saveAll(all);
        } catch (Exception ignored) {}
    }

    public List<AiDebugEntry> getAll() {
        List<AiDebugEntry> list = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY_ENTRIES, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void clear() {
        prefs.edit().remove(KEY_ENTRIES).commit();
    }

    public void markAsDuplicate(String messageId) {
        try {
            List<AiDebugEntry> all = getAll();
            boolean changed = false;
            for (AiDebugEntry e : all) {
                if (messageId.equals(e.messageId) && !e.wasDiscarded) {
                    e.wasDiscarded = true;
                    e.discardReason = "Duplicate — same transaction already recorded";
                    e.finalCategory = null;
                    e.finalMerchant = null;
                    e.finalAmount = 0;
                    e.finalDate = null;
                    e.finalType = null;
                    changed = true;
                    break;
                }
            }
            if (changed) saveAll(all);
        } catch (Exception ignored) {}
    }

    private void saveAll(List<AiDebugEntry> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (AiDebugEntry e : entries) {
                arr.put(toJson(e));
            }
            prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static JSONObject toJson(AiDebugEntry e) throws Exception {
        JSONObject o = new JSONObject();
        o.put("messageId", v(e.messageId));
        o.put("timestamp", e.timestamp);
        o.put("from", v(e.from));
        o.put("subject", v(e.subject));
        o.put("snippet", v(e.snippet));
        o.put("body", v(e.body));
        o.put("snippetAmountPassed", e.snippetAmountPassed);
        if (e.isTransactionalResult != null) o.put("isTransactionalResult", e.isTransactionalResult);
        o.put("geminiInput", v(e.geminiInput));
        o.put("geminiOutput", v(e.geminiOutput));
        o.put("geminiHttpCode", e.geminiHttpCode);
        o.put("parsedCategory", v(e.parsedCategory));
        o.put("parsedMerchant", v(e.parsedMerchant));
        if (e.parsedAmount != null) o.put("parsedAmount", e.parsedAmount);
        o.put("parsedDate", v(e.parsedDate));
        o.put("parsedType", v(e.parsedType));
        o.put("parsedIsTransaction", e.parsedIsTransaction);
        o.put("parsedIsSuspicious", e.parsedIsSuspicious);
        o.put("finalCategory", v(e.finalCategory));
        o.put("finalMerchant", v(e.finalMerchant));
        o.put("finalAmount", e.finalAmount);
        o.put("finalDate", v(e.finalDate));
        o.put("finalType", v(e.finalType));
        o.put("wasDiscarded", e.wasDiscarded);
        o.put("discardReason", v(e.discardReason));
        o.put("cachedHit", e.cachedHit);
        o.put("cacheSource", v(e.cacheSource));
        return o;
    }

    private static AiDebugEntry fromJson(JSONObject o) {
        AiDebugEntry e = new AiDebugEntry();
        e.messageId = o.optString("messageId", null);
        e.timestamp = o.optLong("timestamp", 0);
        e.from = o.optString("from", null);
        e.subject = o.optString("subject", null);
        e.snippet = o.optString("snippet", null);
        e.body = o.optString("body", null);
        e.snippetAmountPassed = o.optBoolean("snippetAmountPassed", true);
        if (o.has("isTransactionalResult")) e.isTransactionalResult = o.optBoolean("isTransactionalResult", false);
        e.geminiInput = o.optString("geminiInput", null);
        e.geminiOutput = o.optString("geminiOutput", null);
        e.geminiHttpCode = o.optInt("geminiHttpCode", 0);
        e.parsedCategory = o.optString("parsedCategory", null);
        e.parsedMerchant = o.optString("parsedMerchant", null);
        if (o.has("parsedAmount")) e.parsedAmount = o.optDouble("parsedAmount", -1);
        e.parsedDate = o.optString("parsedDate", null);
        e.parsedType = o.optString("parsedType", null);
        e.parsedIsTransaction = o.optBoolean("parsedIsTransaction", true);
        e.parsedIsSuspicious = o.optBoolean("parsedIsSuspicious", false);
        e.finalCategory = o.optString("finalCategory", null);
        e.finalMerchant = o.optString("finalMerchant", null);
        e.finalAmount = o.optDouble("finalAmount", 0);
        e.finalDate = o.optString("finalDate", null);
        e.finalType = o.optString("finalType", null);
        e.wasDiscarded = o.optBoolean("wasDiscarded", false);
        e.discardReason = o.optString("discardReason", null);
        e.cachedHit = o.optBoolean("cachedHit", false);
        e.cacheSource = o.optString("cacheSource", null);
        return e;
    }

    private static String v(String s) {
        return s != null ? s : "";
    }
}
