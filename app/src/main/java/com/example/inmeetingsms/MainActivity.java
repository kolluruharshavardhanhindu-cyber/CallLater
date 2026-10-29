package com.example.inmeetingsms;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 101;
    private static final int REQ_ROLE = 102;

    private static final String[] DURATION_LABELS = { "Manual", "30 min", "1 hr", "2 hr", "4 hr" };
    private static final long[] DURATION_MS = {
        0L, 30L * 60000L, 60L * 60000L, 120L * 60000L, 240L * 60000L
    };

    // iOS-style palette (light / dark)
    private boolean dark;
    private int cBg, cCard, cLabel, cSecondary, cSep, cBlue, cGreen, cRed, cOrange, cSegBg, cSegSel, cOffTrack;

    private SharedPreferences prefs;
    private int selectedDuration = 0;

    private View statusDot;
    private TextView statusTitle;
    private TextView statusSub;
    private TextView statusNote;
    private TextView mainButton;
    private LinearLayout segmented;
    private LinearLayout setupCard;
    private TextView lastReplyText;
    private EditText messageEdit;
    private EditText testNumberEdit;

    // ---------- small helpers ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView txt(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return t;
    }

    private TextView semibold(TextView t) {
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(0, dp(topDp), 0, 0);
        return p;
    }

    private void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) view.setAlpha(0.55f);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) view.setAlpha(1f);
            return false;
        });
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(cCard, 12));
        return l;
    }

    private void sectionHeader(LinearLayout parent, String title) {
        TextView t = txt(title.toUpperCase(), 13, cSecondary);
        t.setPadding(dp(32), 0, dp(16), dp(6));
        parent.addView(t, lp(-1, -2, 26));
    }

    private void sectionFooter(LinearLayout parent, String text) {
        TextView t = txt(text, 13, cSecondary);
        t.setPadding(dp(32), dp(7), dp(32), 0);
        parent.addView(t, lp(-1, -2, 0));
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(cSep);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
        p.setMargins(dp(16), 0, 0, 0);
        v.setLayoutParams(p);
        return v;
    }

    /** One row of a grouped list: title (+ optional subtitle) on the left, any view on the right. */
    private void addRow(LinearLayout card, String title, String sub, View right,
                        View.OnClickListener click, boolean withDivider) {
        if (withDivider) card.addView(divider());
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(11), dp(16), dp(11));
        r.setMinimumHeight(dp(48));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(txt(title, 17, cLabel));
        if (sub != null) {
            TextView s = txt(sub, 13, cSecondary);
            s.setPadding(0, dp(2), 0, 0);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (right != null) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
            rp.setMargins(dp(12), 0, 0, 0);
            r.addView(right, rp);
        }
        if (click != null) {
            r.setOnClickListener(click);
            pressable(r);
        }
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private Switch iosSwitch(boolean checked, CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(this);
        sw.setShowText(false);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Color.WHITE);
        thumb.setSize(dp(27), dp(27));
        sw.setThumbDrawable(new InsetDrawable(thumb, dp(2)));
        StateListDrawable track = new StateListDrawable();
        GradientDrawable on = round(cGreen, 16);
        on.setSize(dp(51), dp(31));
        GradientDrawable off = round(cOffTrack, 16);
        off.setSize(dp(51), dp(31));
        track.addState(new int[] { android.R.attr.state_checked }, on);
        track.addState(new int[] {}, off);
        sw.setTrackDrawable((Drawable) track);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        return sw;
    }

    private TextView statusRight(boolean ok, String offLabel) {
        TextView t;
        if (ok) {
            t = txt("✓  On", 15, cGreen);
        } else {
            t = txt(offLabel + "  ›", 15, cBlue);
        }
        return semibold(t);
    }

    private boolean has(String p) {
        return Replier.has(this, p);
    }

    private String[] phonePerms() {
        List<String> l = new ArrayList<>();
        l.add(Manifest.permission.READ_PHONE_STATE);
        l.add(Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) l.add(Manifest.permission.ANSWER_PHONE_CALLS);
        return l.toArray(new String[0]);
    }

    private boolean hasAll(String[] perms) {
        for (String p : perms) if (!has(p)) return false;
        return true;
    }

    private boolean hasCore() {
        return has(Manifest.permission.SEND_SMS)
                && has(Manifest.permission.READ_PHONE_STATE)
                && has(Manifest.permission.READ_CALL_LOG);
    }

    private void requestMissing(String[] group) {
        List<String> need = new ArrayList<>();
        for (String p : group) if (!has(p)) need.add(p);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    private void requestEverything() {
        List<String> all = new ArrayList<>();
        all.add(Manifest.permission.SEND_SMS);
        for (String p : phonePerms()) all.add(p);
        all.add(Manifest.permission.READ_CONTACTS);
        requestMissing(all.toArray(new String[0]));
    }

    // ---------- UI ----------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        cBg = dark ? 0xFF000000 : 0xFFF2F2F7;
        cCard = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
        cLabel = dark ? 0xFFFFFFFF : 0xFF000000;
        cSecondary = 0xFF8E8E93;
        cSep = dark ? 0xFF38383A : 0xFFD1D1D6;
        cBlue = dark ? 0xFF0A84FF : 0xFF007AFF;
        cGreen = dark ? 0xFF30D158 : 0xFF34C759;
        cRed = dark ? 0xFFFF453A : 0xFFFF3B30;
        cOrange = dark ? 0xFFFF9F0A : 0xFFFF9500;
        cSegBg = dark ? 0xFF1C1C1E : 0xFFE3E3E8;
        cSegSel = dark ? 0xFF636366 : 0xFFFFFFFF;
        cOffTrack = dark ? 0xFF39393D : 0xFFE9E9EA;

        getWindow().setBackgroundDrawable(new ColorDrawable(cBg));
        getWindow().setStatusBarColor(cBg);
        getWindow().setNavigationBarColor(cBg);
        if (!dark) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        // Large title + logo
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(4), 0);
        TextView title = txt("CallLater", 34, cLabel);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, lp(-1, -2, 4));

        // Hero status card
        LinearLayout hero = card();
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        statusRow.addView(statusDot, new LinearLayout.LayoutParams(dp(12), dp(12)));
        statusTitle = txt("", 22, cLabel);
        semibold(statusTitle);
        statusTitle.setPadding(dp(10), 0, 0, 0);
        statusRow.addView(statusTitle);
        hero.addView(statusRow, lp(-1, -2, 0));
        statusSub = txt("", 15, cSecondary);
        hero.addView(statusSub, lp(-1, -2, 6));
        statusNote = txt("", 13, cOrange);
        hero.addView(statusNote, lp(-1, -2, 8));
        mainButton = new TextView(this);
        mainButton.setGravity(Gravity.CENTER);
        mainButton.setTextSize(17);
        semibold(mainButton);
        mainButton.setClickable(true);
        mainButton.setOnClickListener(v -> onToggle());
        pressable(mainButton);
        hero.addView(mainButton, lp(-1, dp(50), 16));
        root.addView(hero, lp(-1, -2, 16));

        // Duration (segmented control)
        sectionHeader(root, "Turn off automatically");
        segmented = new LinearLayout(this);
        segmented.setOrientation(LinearLayout.HORIZONTAL);
        segmented.setBackground(round(cSegBg, 10));
        segmented.setPadding(dp(2), dp(2), dp(2), dp(2));
        for (int i = 0; i < DURATION_LABELS.length; i++) {
            final int idx = i;
            TextView seg = new TextView(this);
            seg.setText(DURATION_LABELS[i]);
            seg.setTextSize(13);
            seg.setGravity(Gravity.CENTER);
            seg.setPadding(0, dp(8), 0, dp(8));
            seg.setClickable(true);
            seg.setOnClickListener(v -> {
                selectedDuration = idx;
                refreshSegments();
            });
            segmented.addView(seg, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        root.addView(segmented, lp(-1, -2, 0));
        refreshSegments();

        // Message
        sectionHeader(root, "Message");
        LinearLayout msgCard = card();
        messageEdit = new EditText(this);
        messageEdit.setText(Prefs.message(this));
        messageEdit.setTextSize(17);
        messageEdit.setTextColor(cLabel);
        messageEdit.setHintTextColor(cSecondary);
        messageEdit.setBackground(null);
        messageEdit.setMinLines(3);
        messageEdit.setGravity(Gravity.TOP | Gravity.START);
        messageEdit.setPadding(dp(16), dp(14), dp(16), dp(14));
        messageEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageEdit.setFilters(new InputFilter[] { new InputFilter.LengthFilter(Prefs.MAX_MESSAGE) });
        msgCard.addView(messageEdit, new LinearLayout.LayoutParams(-1, -2));
        root.addView(msgCard, lp(-1, -2, 0));
        sectionFooter(root, "Sent as a text to the caller. Up to " + Prefs.MAX_MESSAGE + " characters.");

        // Call handling
        sectionHeader(root, "When someone calls");
        LinearLayout behave = card();
        addRow(behave, "End the call after replying", "The call is cut as soon as the text is sent",
                iosSwitch(prefs.getBoolean(Prefs.END_CALL, true),
                        (b, c) -> prefs.edit().putBoolean(Prefs.END_CALL, c).apply()), null, false);
        addRow(behave, "Silence the ringtone", "Calls we can't text ring silently",
                iosSwitch(prefs.getBoolean(Prefs.SILENT, true),
                        (b, c) -> prefs.edit().putBoolean(Prefs.SILENT, c).apply()), null, true);
        addRow(behave, "Only reply to contacts", "Everyone else is silenced, not texted",
                iosSwitch(prefs.getBoolean(Prefs.CONTACTS_ONLY, false), (b, c) -> {
                    prefs.edit().putBoolean(Prefs.CONTACTS_ONLY, c).apply();
                    if (c && !has(Manifest.permission.READ_CONTACTS)) {
                        requestMissing(new String[] { Manifest.permission.READ_CONTACTS });
                    }
                }), null, true);
        root.addView(behave, lp(-1, -2, 0));
        sectionFooter(root, "A text is sent every time someone calls.");

        // Setup
        sectionHeader(root, "Setup");
        setupCard = card();
        root.addView(setupCard, lp(-1, -2, 0));
        sectionFooter(root, "Call screening is what stops the phone from ringing at all. Without it, "
                + "the call is ended a moment after it starts ringing.");

        // Test
        sectionHeader(root, "Test");
        LinearLayout testCard = card();
        testNumberEdit = new EditText(this);
        testNumberEdit.setHint("Another number, e.g. +91…");
        testNumberEdit.setTextSize(17);
        testNumberEdit.setTextColor(cLabel);
        testNumberEdit.setHintTextColor(cSecondary);
        testNumberEdit.setBackground(null);
        testNumberEdit.setSingleLine(true);
        testNumberEdit.setInputType(InputType.TYPE_CLASS_PHONE);
        testNumberEdit.setPadding(0, 0, 0, 0);
        TextView sendTest = semibold(txt("Send", 17, cBlue));
        sendTest.setClickable(true);
        sendTest.setOnClickListener(v -> {
            saveMessage();
            Toast.makeText(this, Replier.sendTest(this, testNumberEdit.getText().toString(),
                    Prefs.message(this)), Toast.LENGTH_LONG).show();
        });
        pressable(sendTest);
        LinearLayout testRow = new LinearLayout(this);
        testRow.setOrientation(LinearLayout.HORIZONTAL);
        testRow.setGravity(Gravity.CENTER_VERTICAL);
        testRow.setPadding(dp(16), dp(6), dp(16), dp(6));
        testRow.setMinimumHeight(dp(48));
        testRow.addView(testNumberEdit, new LinearLayout.LayoutParams(0, -2, 1f));
        testRow.addView(sendTest);
        testCard.addView(testRow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(testCard, lp(-1, -2, 0));

        // Activity
        sectionHeader(root, "Activity");
        LinearLayout actCard = card();
        lastReplyText = txt("", 15, cLabel);
        lastReplyText.setPadding(dp(16), dp(14), dp(16), dp(14));
        actCard.addView(lastReplyText);
        root.addView(actCard, lp(-1, -2, 0));

        TextView note = txt("Works for normal phone calls (not WhatsApp or other internet calls). "
                + "Withheld numbers can't be texted. Standard SMS charges may apply.", 12, cSecondary);
        note.setPadding(dp(32), dp(20), dp(32), 0);
        root.addView(note, lp(-1, -2, 0));

        setContentView(scroll);
        if (!hasCore()) requestEverything();
    }

    private void refreshSegments() {
        for (int i = 0; i < segmented.getChildCount(); i++) {
            TextView t = (TextView) segmented.getChildAt(i);
            boolean sel = i == selectedDuration;
            t.setBackground(sel ? round(cSegSel, 8) : null);
            t.setTextColor(cLabel);
            t.setTypeface(Typeface.create(sel ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveMessage();
    }

    private void saveMessage() {
        String m = messageEdit.getText().toString().trim();
        prefs.edit().putString(Prefs.MESSAGE, m.isEmpty() ? Prefs.DEFAULT_MESSAGE : m).apply();
    }

    private void onToggle() {
        if (!hasCore()) {
            requestEverything();
            Toast.makeText(this, "Allow the permissions first (see Setup below).", Toast.LENGTH_LONG).show();
            return;
        }
        saveMessage();
        boolean turnOn = !Prefs.isActive(this);
        SharedPreferences.Editor ed = prefs.edit();
        if (turnOn) {
            long dur = DURATION_MS[selectedDuration];
            ed.putBoolean(Prefs.ENABLED, true)
              .putLong(Prefs.END_TIME, dur == 0L ? 0L : System.currentTimeMillis() + dur)
              .remove(Prefs.PENDING_UNKNOWN);
        } else {
            ed.putBoolean(Prefs.ENABLED, false).putLong(Prefs.END_TIME, 0L);
        }
        ed.apply();
        updateUI();
    }

    private void updateUI() {
        boolean enabled = Prefs.isActive(this);
        boolean role = holdsScreeningRole();
        boolean wantsSilent = prefs.getBoolean(Prefs.END_CALL, true) || prefs.getBoolean(Prefs.SILENT, true);

        statusDot.setBackground(ovalDrawable(enabled ? cGreen : cSecondary));
        if (enabled) {
            long end = prefs.getLong(Prefs.END_TIME, 0L);
            statusTitle.setText("CallLater is on");
            statusSub.setText(end > 0L
                    ? "Turns off automatically at "
                        + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(end))
                    : "Callers get your text until you turn it off.");
            mainButton.setText("Turn Off");
            mainButton.setTextColor(cRed);
            mainButton.setBackground(round(dark ? 0xFF2C1A19 : 0xFFFDE8E7, 14));
        } else {
            statusTitle.setText("CallLater is off");
            statusSub.setText("Calls ring normally.");
            mainButton.setText("Turn On");
            mainButton.setTextColor(Color.WHITE);
            mainButton.setBackground(round(cBlue, 14));
        }
        if (wantsSilent && !role) {
            statusNote.setVisibility(View.VISIBLE);
            statusNote.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? "To stop the ringtone completely, set up Call screening below."
                    : "This Android version ends the call just after it starts ringing.");
        } else {
            statusNote.setVisibility(View.GONE);
        }

        long last = prefs.getLong(Prefs.LAST_TIME, 0L);
        int count = prefs.getInt(Prefs.SENT_COUNT, 0);
        lastReplyText.setText(last == 0L ? "No replies sent yet."
                : count + (count == 1 ? " reply sent" : " replies sent") + "\nLast: "
                + prefs.getString(Prefs.LAST_NUMBER, "") + " · "
                + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(last)));

        refreshSetup(role);
    }

    private Drawable ovalDrawable(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private void refreshSetup(boolean role) {
        setupCard.removeAllViews();
        boolean sms = has(Manifest.permission.SEND_SMS);
        boolean phone = hasAll(phonePerms());
        boolean contacts = has(Manifest.permission.READ_CONTACTS);

        addRow(setupCard, "Send text messages", null, statusRight(sms, "Allow"),
                sms ? null : v -> requestMissing(new String[] { Manifest.permission.SEND_SMS }), false);
        addRow(setupCard, "Phone & call log", "To see who is calling and end calls",
                statusRight(phone, "Allow"), phone ? null : v -> requestMissing(phonePerms()), true);
        addRow(setupCard, "Contacts", "Only for “Only reply to contacts”",
                statusRight(contacts, "Allow"),
                contacts ? null : v -> requestMissing(new String[] { Manifest.permission.READ_CONTACTS }), true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addRow(setupCard, "Call screening", "Stops the ringtone completely",
                    statusRight(role, "Set up"), role ? null : v -> requestScreeningRole(), true);
        }
        addRow(setupCard, "Allow restricted settings", "If Allow is greyed out, open App info → ⋮",
                semibold(txt("Open  ›", 15, cBlue)), v -> openAppSettings(), true);
        addRow(setupCard, "Battery", "Set to Unrestricted so calls are never missed",
                semibold(txt("Open  ›", 15, cBlue)), v -> openBatterySettings(), true);
    }

    private boolean holdsScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        } catch (Exception e) {
            return false;
        }
    }

    private void requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
                return;
            }
        } catch (Exception ignored) {
            // fall through
        }
        Toast.makeText(this, "This phone does not offer the call-screening option.", Toast.LENGTH_LONG).show();
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open settings.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            openAppSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            if (!hasCore()) {
                Toast.makeText(this, "Permission blocked? Tap “Allow restricted settings” below, "
                        + "then allow Phone, SMS and Call logs.", Toast.LENGTH_LONG).show();
            }
            updateUI();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        updateUI();
    }
}
