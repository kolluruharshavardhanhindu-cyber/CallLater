package com.example.inmeetingsms;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

/**
 * Runs BEFORE the phone rings (Android 10+, once CallLater is chosen as the call-screening app).
 *  - Replied (or already replied) + "end call" on  -> call is rejected, so it never rings.
 *  - Could not reply (private number, contacts-only, no permission) + "silence" on -> rings silently.
 * A call is only ever rejected after the SMS has been submitted.
 */
public class MeetingCallScreeningService extends CallScreeningService {
    private static final String TAG = "CallLater";

    @Override
    public void onScreenCall(Call.Details details) {
        CallResponse.Builder response = new CallResponse.Builder();
        try {
            if (Prefs.isActive(this)
                    && details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
                int status = Replier.SKIPPED;
                if (details.getHandle() != null && "tel".equals(details.getHandle().getScheme())) {
                    status = Replier.reply(this, details.getHandle().getSchemeSpecificPart(), subIdOf(details));
                }
                boolean handled = status != Replier.SKIPPED;
                if (handled && Prefs.get(this).getBoolean(Prefs.END_CALL, true)) {
                    response.setDisallowCall(true).setRejectCall(true);
                } else if (Prefs.get(this).getBoolean(Prefs.SILENT, true)) {
                    response.setSilenceCall(true);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Screening failed; letting the call through.", e);
            response = new CallResponse.Builder();
        } finally {
            respondToCall(details, response.build());
        }
    }

    private static int subIdOf(Call.Details details) {
        try {
            PhoneAccountHandle h = details.getAccountHandle();
            if (h != null && h.getId() != null) return Integer.parseInt(h.getId());
        } catch (Exception ignored) {
            // non-numeric id on some phones; the default SIM is used then
        }
        return -1;
    }
}
