package com.spotmydime.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.spotmydime.util.SecurePrefs;

/**
 * Tracks the most recent Gmail message date we've successfully processed,
 * so subsequent fetches only ask Gmail for messages newer than that —
 * instead of re-querying and re-walking the same ~60 messages every refresh.
 *
 * Without this, every app open / pull-to-refresh re-fetches the full
 * "after:2026/01/01" window. AiResultCache/VendorStore make re-classification
 * cheap (no Gemini call), but Gmail still does a full GET per message every
 * time, which is slow and pointless once a message has already been seen.
 *
 * Safety margin: we store the latest message's internalDate minus a small
 * overlap window, so a message that arrives with a slightly earlier
 * internalDate than one already processed (clock skew, batched delivery)
 * still gets picked up on the next sync rather than silently skipped.
 */
public class SyncStateStore {

    private static final String PREFS_NAME = "sync_state";
    private static final String KEY_LAST_SYNCED_MILLIS = "last_synced_millis";

    // Gmail's "after:" search operator is DAY precision only (it means
    // "at or after 00:00:00 on this date" — there's no documented hour/minute
    // granularity). So this overlap can only ever push the query back by
    // whole days, not hours — it exists so a sync running late in the day
    // doesn't get a same-day message excluded on tomorrow's incremental
    // fetch due to local rounding. Reprocessing the whole overlap day is
    // cheap: AiResultCache/ExcludedMessageStore make it a no-op for anything
    // already seen.
    private static final long OVERLAP_MS = 2L * 60 * 60 * 1000;

    private final SharedPreferences prefs;

    public SyncStateStore(Context context) {
        prefs = SecurePrefs.get(context, PREFS_NAME);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    /** Returns the millis of the newest message we've processed, or 0 if never synced. */
    public long getLastSyncedMillis() {
        return prefs.getLong(KEY_LAST_SYNCED_MILLIS, 0L);
    }

    /** Call after a successful fetch with the newest message's internalDate seen this run. */
    public void updateLastSyncedMillis(long newestMessageMillis) {
        if (newestMessageMillis > getLastSyncedMillis()) {
            prefs.edit().putLong(KEY_LAST_SYNCED_MILLIS, newestMessageMillis).apply();
        }
    }

    /**
     * Returns the "after:" date string to use for the Gmail query, in
     * yyyy/MM/dd format as Gmail search expects. Falls back to a wide
     * default on first-ever sync.
     */
    public String getQueryAfterDate(String defaultAfterDate) {
        long last = getLastSyncedMillis();
        if (last <= 0) return defaultAfterDate;

        long withOverlap = last - OVERLAP_MS;
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US);
        return fmt.format(new java.util.Date(withOverlap));
    }

    /** For manual cache-reset flows (e.g. settings "rescan everything"). */
    public void reset() {
        prefs.edit().remove(KEY_LAST_SYNCED_MILLIS).apply();
    }
}
