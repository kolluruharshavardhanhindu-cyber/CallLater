package com.example.inmeetingsms;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects ringing calls from the phone-state broadcast.
 * If Android hides the caller number, the number is recovered from the call log once the call ends
 * (missed / rejected calls only, so a call you answered never gets an SMS).
 */
public class IncomingCallReceiver extends BroadcastReceiver {
    private static final String TAG = "CallLater";
    private static final long PENDING_MAX_AGE_MS = 3L * 60L * 1000L;

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        final Context app = context.getApplicationContext();
        if (!Prefs.isActive(app)) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;

        int subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
                intent.getIntExtra("subscription", -1));
        SharedPreferences prefs = Prefs.get(app);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (Replier.cleanNumber(number) != null) {
                prefs.edit().remove(Prefs.PENDING_UNKNOWN).apply();
                int status = Replier.reply(app, number, subId);
                if (status != Replier.SKIPPED && prefs.getBoolean(Prefs.END_CALL, true)) {
                    Replier.endCall(app);   // SMS is already submitted; now hang up
                }
            } else {
                // Number hidden by Android/OEM (or private caller). Remember to look in the call log later.
                Log.w(TAG, "Caller number not provided; will check call log after the call.");
                if (prefs.getLong(Prefs.PENDING_UNKNOWN, 0L) == 0L) {
                    prefs.edit()
                            .putLong(Prefs.PENDING_UNKNOWN, System.currentTimeMillis())
                            .putInt(Prefs.PENDING_SUB, subId)
                            .apply();
                }
            }
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            // You answered or dialled: never auto-reply for this ring.
            prefs.edit().remove(Prefs.PENDING_UNKNOWN).apply();
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            long since = prefs.getLong(Prefs.PENDING_UNKNOWN, 0L);
            if (since == 0L) return;
            final int pendingSub = prefs.getInt(Prefs.PENDING_SUB, -1);
            prefs.edit().remove(Prefs.PENDING_UNKNOWN).apply();
            if (System.currentTimeMillis() - since > PENDING_MAX_AGE_MS) return;
            if (!Replier.has(app, Manifest.permission.READ_CALL_LOG)) return;

            final BroadcastReceiver.PendingResult result = goAsync();
            final long from = since - 15000L;
            new Thread(() -> {
                try {
                    Thread.sleep(2000L); // the call log is written slightly after the call ends
                    replyFromCallLog(app, from, pendingSub);
                } catch (Exception e) {
                    Log.w(TAG, "Call log fallback failed.", e);
                } finally {
                    result.finish();
                }
            }).start();
        }
    }

    private static void replyFromCallLog(Context app, long fromTime, int subId) {
        Set<String> done = new HashSet<>();
        // types: 3 = missed, 5 = rejected, 6 = blocked
        try (Cursor c = app.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[] { CallLog.Calls.NUMBER },
                CallLog.Calls.DATE + ">=? AND " + CallLog.Calls.TYPE + " IN (3,5,6)",
                new String[] { String.valueOf(fromTime) },
                CallLog.Calls.DATE + " DESC")) {
            if (c == null) return;
            int count = 0;
            while (c.moveToNext() && count++ < 5) {
                String n = c.getString(0);
                if (n != null && done.add(n)) Replier.reply(app, n, subId);
            }
        }
    }
}
