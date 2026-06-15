package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class ManualTransactionStore {

    private static final String PREFS_NAME = "manual_transactions";
    private static final String KEY_LIST = "list";
    private static final String MANUAL_PREFIX = "manual_";

    private final SharedPreferences prefs;

    public ManualTransactionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(Transaction t) {
        List<Transaction> all = getAll();
        all.add(0, t);
        saveAll(all);
    }

    public List<Transaction> getAll() {
        String json = prefs.getString(KEY_LIST, "[]");
        List<Transaction> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", MANUAL_PREFIX + UUID.randomUUID());
                list.add(new Transaction(
                        o.optString("merchant", ""),
                        o.optDouble("amount", 0),
                        o.optLong("dateMillis", System.currentTimeMillis()),
                        o.optString("dateDisplay", ""),
                        o.optString("category", "Other"),
                        o.optString("merchant", "?").charAt(0),
                        "incoming".equals(o.optString("type"))
                                ? Transaction.Type.INCOMING : Transaction.Type.OUTGOING,
                        null,
                        o.optString("notes", ""),
                        id,
                        null
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void delete(String id) {
        if (id == null) return;
        List<Transaction> all = getAll();
        all.removeIf(t -> id.equals(t.getMessageId()));
        saveAll(all);
    }

    private void saveAll(List<Transaction> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Transaction t : list) {
                JSONObject o = new JSONObject();
                o.put("merchant", t.getMerchant());
                o.put("amount", t.getAmount());
                o.put("dateMillis", t.getDateMillis());
                o.put("dateDisplay", t.getDateDisplay());
                o.put("category", t.getCategory());
                o.put("type", t.getType() == Transaction.Type.INCOMING ? "incoming" : "outgoing");
                o.put("notes", t.getSubject() != null ? t.getSubject() : "");
                o.put("id", t.getMessageId() != null ? t.getMessageId() : MANUAL_PREFIX + UUID.randomUUID());
                arr.put(o);
            }
            prefs.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Transaction createTransaction(String merchant, double amount,
                                                  long dateMillis, String dateDisplay,
                                                  String category, Transaction.Type type,
                                                  String notes) {
        char avatar = merchant.isEmpty() ? '?' : merchant.charAt(0);
        if (avatar >= 'a' && avatar <= 'z') avatar = (char) (avatar - 32);
        String id = MANUAL_PREFIX + UUID.randomUUID().toString();
        return new Transaction(merchant, amount, dateMillis, dateDisplay, category, avatar, type,
                null, notes, id, null);
    }
}
