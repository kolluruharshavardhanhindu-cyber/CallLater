package com.example.inmeetingsms;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;

/** Single place that decides whether to reply and actually sends the SMS. Thread-safe. */
final class Replier {
    private static final String TAG = "CallLater";
    private static final String SENT_LOG = "sent_log";
    /** Do not text the same number again within this window (people redial). */
   static final long COOLDOWN_MS = 20L * 1000L;

    static final int SKIPPED = 0;  // nothing sent (not handled)
    static final int SENT = 1;     // SMS submitted now
    static final int RECENT = 2;   // already replied to this number recently

    private Replier() {}

    static boolean has(Context c, String permission) {
        return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns a clean "+1234567890"-style string, or null for private/withheld/alphanumeric/short-code senders. */
    static String cleanNumber(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (!s.matches("^\\+?[0-9\\s\\-().]+$")) return null;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if ((ch >= '0' && ch <= '9') || (ch == '+' && sb.length() == 0)) sb.append(ch);
        }
        int digits = sb.length() - ((sb.length() > 0 && sb.charAt(0) == '+') ? 1 : 0);
        if (digits < 6 || digits > 15) return null;
        return sb.toString();
    }

    /** Ends the ringing call (Android 9+ with the "Phone" permission). Used when call screening is not set up. */
    @SuppressWarnings("deprecation")
    static boolean endCall(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        if (!has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) return false;
        try {
            TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
            return tm != null && tm.endCall();
        } catch (Exception e) {
            Log.w(TAG, "Could not end the call.", e);
            return false;
        }
    }

    private static String key(String number) {
        String d = number.replaceAll("[^0-9]", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    /** Called from every detection path. Returns SENT, RECENT (already replied) or SKIPPED. */
    static synchronized int reply(Context ctx, String rawNumber, int subId) {
        Context app = ctx.getApplicationContext();
        if (!Prefs.isActive(app)) return SKIPPED;
        if (!has(app, Manifest.permission.SEND_SMS)) {
            Log.w(TAG, "SEND_SMS permission missing.");
            return SKIPPED;
        }
        String number = cleanNumber(rawNumber);
        if (number == null) return SKIPPED;

        SharedPreferences log = app.getSharedPreferences(SENT_LOG, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String k = key(number);
        long last = log.getLong(k, 0L);
        if (last > 0L && now >= last && now - last < COOLDOWN_MS) return RECENT;

        if (Prefs.get(app).getBoolean(Prefs.CONTACTS_ONLY, false) && !isContact(app, number)) return SKIPPED;

        try {
            send(app, number, Prefs.message(app), subId);
        } catch (Exception e) {
            Log.e(TAG, "Could not send automatic SMS.", e);
            return SKIPPED;
        }

        // prune old entries so the file stays small
        SharedPreferences.Editor ed = log.edit();
        Map<String, ?> all = log.getAll();
        if (all.size() > 60) {
            for (Map.Entry<String, ?> en : all.entrySet()) {
                Object v = en.getValue();
                if (v instanceof Long && now - (Long) v > COOLDOWN_MS) ed.remove(en.getKey());
            }
        }
        ed.putLong(k, now).apply();

        SharedPreferences p = Prefs.get(app);
        p.edit()
                .putString(Prefs.LAST_NUMBER, number)
                .putLong(Prefs.LAST_TIME, now)
                .putInt(Prefs.SENT_COUNT, p.getInt(Prefs.SENT_COUNT, 0) + 1)
                .apply();
        Log.i(TAG, "Meeting reply submitted.");
        return SENT;
    }

    /** Test button: sends immediately, ignores cooldown/contacts filter, returns a human-readable result. */
    static String sendTest(Context ctx, String rawNumber, String message) {
        Context app = ctx.getApplicationContext();
        if (!has(app, Manifest.permission.SEND_SMS)) return "SMS permission is not granted.";
        String number = cleanNumber(rawNumber);
        if (number == null) return "Enter a valid phone number (with country code, e.g. +91...).";
        try {
            send(app, number, message, -1);
            return "Test SMS submitted to " + number + ". Check that it arrives.";
        } catch (Exception e) {
            return "Sending failed: " + e.getMessage();
        }
    }

    private static void send(Context app, String number, String message, int subId) throws Exception {
        Exception first = null;
        if (subId >= 0) {
            try {
                submit(smsManagerFor(app, subId), number, message);
                return;
            } catch (Exception e) {
                first = e;
                Log.w(TAG, "Sending on the call's SIM failed, falling back to default SIM.", e);
            }
        }
        try {
            submit(defaultSmsManager(app), number, message);
        } catch (Exception e) {
            throw (first != null && e.getMessage() == null) ? first : e;
        }
    }

    private static void submit(SmsManager sm, String number, String message) {
        if (sm == null) throw new IllegalStateException("SMS service unavailable");
        ArrayList<String> parts = sm.divideMessage(message);
        if (parts.size() > 1) {
            sm.sendMultipartTextMessage(number, null, parts, null, null);
        } else {
            sm.sendTextMessage(number, null, message, null, null);
        }
    }

    @SuppressWarnings("deprecation")
    private static SmsManager defaultSmsManager(Context app) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SmsManager sm = app.getSystemService(SmsManager.class);
            if (sm != null) return sm;
        }
        return SmsManager.getDefault();
    }

    /** Use the SIM the call arrived on (dual-SIM phones). Falls back to the default SMS SIM. */
    @SuppressWarnings("deprecation")
    private static SmsManager smsManagerFor(Context app, int subId) {
        try {
            if (has(app, Manifest.permission.READ_PHONE_STATE)) {
                SubscriptionManager subs =
                        (SubscriptionManager) app.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (subs != null && subs.getActiveSubscriptionInfo(subId) != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SmsManager base = app.getSystemService(SmsManager.class);
                        if (base != null) return base.createForSubscriptionId(subId);
                    }
                    return SmsManager.getSmsManagerForSubscriptionId(subId);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "SIM lookup failed, using default SIM.", e);
        }
        return defaultSmsManager(app);
    }

    private static boolean isContact(Context app, String number) {
        if (!has(app, Manifest.permission.READ_CONTACTS)) return false;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            try (Cursor c = app.getContentResolver().query(
                    uri, new String[] { ContactsContract.PhoneLookup._ID }, null, null, null)) {
                return c != null && c.moveToFirst();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
