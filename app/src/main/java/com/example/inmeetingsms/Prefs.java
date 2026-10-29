package com.example.inmeetingsms;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "in_meeting_settings";
    static final String ENABLED = "meeting_enabled";
    static final String END_TIME = "end_time";            // 0 = until turned off manually
    static final String MESSAGE = "message";
    static final String CONTACTS_ONLY = "contacts_only";
    static final String PENDING_UNKNOWN = "pending_unknown_time";
    static final String PENDING_SUB = "pending_unknown_sub";
    static final String LAST_NUMBER = "last_sent_number";
    static final String LAST_TIME = "last_sent_time";
    static final String SENT_COUNT = "sent_count";
    static final String END_CALL = "end_call_after_reply";   // default true
    static final String SILENT = "silence_ringtone";          // default true

    static final String DEFAULT_MESSAGE = "I am in a meeting. Call you later.";
    static final int MAX_MESSAGE = 300;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** True if In Meeting is on. Also switches it off automatically once the timer has expired. */
    static boolean isActive(Context c) {
        SharedPreferences p = get(c);
        if (!p.getBoolean(ENABLED, false)) return false;
        long end = p.getLong(END_TIME, 0L);
        if (end > 0L && System.currentTimeMillis() >= end) {
            p.edit().putBoolean(ENABLED, false).putLong(END_TIME, 0L).apply();
            return false;
        }
        return true;
    }

    static String message(Context c) {
        String m = get(c).getString(MESSAGE, DEFAULT_MESSAGE);
        if (m == null || m.trim().isEmpty()) return DEFAULT_MESSAGE;
        m = m.trim();
        return m.length() > MAX_MESSAGE ? m.substring(0, MAX_MESSAGE) : m;
    }
}
