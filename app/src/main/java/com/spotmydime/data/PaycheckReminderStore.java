package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.spotmydime.util.SecurePrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaycheckReminderStore {

    private static final String PREFS_NAME = "paycheck_reminders";
    private static final String KEY_LIST = "list";

    private final SharedPreferences prefs;

    public PaycheckReminderStore(Context context) {
        prefs = SecurePrefs.get(context, PREFS_NAME);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public void save(PaycheckReminder r) {
        List<PaycheckReminder> all = getAll();
        all.add(0, r);
        saveAll(all);
    }

    public void update(PaycheckReminder r) {
        List<PaycheckReminder> all = getAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(r.id)) {
                all.set(i, r);
                break;
            }
        }
        saveAll(all);
    }

    public List<PaycheckReminder> getAll() {
        String json = prefs.getString(KEY_LIST, "[]");
        List<PaycheckReminder> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                PaycheckReminder r = new PaycheckReminder();
                r.id = o.optString("id", UUID.randomUUID().toString());
                r.amount = o.optDouble("amount", 0);
                r.entryDateMillis = o.optLong("entryDateMillis", System.currentTimeMillis());
                r.intervalDays = o.optInt("intervalDays", 30);
                r.nextReminderDateMillis = o.optLong("nextReminderDateMillis", System.currentTimeMillis());
                r.category = o.optString("category", "Other");
                r.notes = o.optString("notes", "");
                r.merchant = o.optString("merchant", "Paycheck");
                list.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<PaycheckReminder> getDueReminders() {
        List<PaycheckReminder> all = getAll();
        List<PaycheckReminder> due = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (PaycheckReminder r : all) {
            if (r.nextReminderDateMillis <= now) {
                due.add(r);
            }
        }
        return due;
    }

    public void delete(String id) {
        if (id == null) return;
        List<PaycheckReminder> all = getAll();
        all.removeIf(r -> id.equals(r.id));
        saveAll(all);
    }

    private void saveAll(List<PaycheckReminder> list) {
        try {
            JSONArray arr = new JSONArray();
            for (PaycheckReminder r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("amount", r.amount);
                o.put("entryDateMillis", r.entryDateMillis);
                o.put("intervalDays", r.intervalDays);
                o.put("nextReminderDateMillis", r.nextReminderDateMillis);
                o.put("category", r.category);
                o.put("notes", r.notes);
                o.put("merchant", r.merchant);
                arr.put(o);
            }
            prefs.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class PaycheckReminder {
        public String id;
        public double amount;
        public long entryDateMillis;
        public int intervalDays;
        public long nextReminderDateMillis;
        public String category;
        public String notes;
        public String merchant;

        public PaycheckReminder() {}

        public static PaycheckReminder create(String merchant, double amount, long entryDateMillis,
                                              int intervalDays, String category, String notes) {
            PaycheckReminder r = new PaycheckReminder();
            r.id = UUID.randomUUID().toString();
            r.merchant = merchant;
            r.amount = amount;
            r.entryDateMillis = entryDateMillis;
            r.intervalDays = intervalDays;
            r.category = category;
            r.notes = notes;
            // Calculate next reminder: intervalDays from entryDate
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(entryDateMillis);
            cal.add(java.util.Calendar.DAY_OF_YEAR, intervalDays);
            r.nextReminderDateMillis = cal.getTimeInMillis();
            return r;
        }
    }
}
