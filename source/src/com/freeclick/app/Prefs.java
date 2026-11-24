package com.freeclick.app;

import android.content.Context;
import android.content.SharedPreferences;

/** App-wide settings (everything is free; there is no "pro" flag anywhere). */
public final class Prefs {
    private final SharedPreferences sp;

    private final com.freeclick.core.DraftFiles drafts;

    Prefs(Context c) {
        sp = c.getSharedPreferences("freeclick", Context.MODE_PRIVATE);
        drafts = new com.freeclick.core.DraftFiles(new java.io.File(c.getFilesDir(), "drafts"),
                new com.freeclick.core.DraftFiles.Legacy() {
                    public String get(String kind) { return sp.getString("draft_" + kind, null); }
                    public void remove(String kind) { sp.edit().remove("draft_" + kind).apply(); }
                });
    }

    /** Which drop-down groups of settings were left open (Section keys). */
    public boolean sectionOpen(String key) { return sp.getBoolean("sec_" + key, false); }
    public void setSectionOpen(String key, boolean open) { sp.edit().putBoolean("sec_" + key, open).apply(); }

    // ---- 🔥 Turbo (2.2): taps where you touch, fast
    // 👂 Android's copies of your touches (2.3), only after the test in Settings said it works
    public boolean touchDirect() { return sp.getBoolean("touch_direct", false); }
    public void setTouchDirect(boolean v) { sp.edit().putBoolean("touch_direct", v).apply(); }

    // 🌙 AFK mode (2.2.1)
    public boolean afkOn() { return sp.getBoolean("afk_on", true); }
    public void setAfkOn(boolean v) { sp.edit().putBoolean("afk_on", v).apply(); }

    public boolean afkRecover() { return sp.getBoolean("afk_recover", true); }
    public void setAfkRecover(boolean v) { sp.edit().putBoolean("afk_recover", v).apply(); }

    public int afkMinBattery() { return Math.max(0, Math.min(50, sp.getInt("afk_min_battery", 15))); }
    public void setAfkMinBattery(int v) { sp.edit().putInt("afk_min_battery", v).apply(); }

    public boolean afkDim() { return sp.getBoolean("afk_dim", false); }
    public void setAfkDim(boolean v) { sp.edit().putBoolean("afk_dim", v).apply(); }

    public boolean turboButton() { return sp.getBoolean("turbo_button", true); }
    public void setTurboButton(boolean v) { sp.edit().putBoolean("turbo_button", v).apply(); }
    /** Taps per second (5 … 60). */
    public int turboRate() { return Math.max(5, Math.min(60, sp.getInt("turbo_rate", 20))); }
    public void setTurboRate(int v) { sp.edit().putInt("turbo_rate", v).apply(); }
    /** A short tap: a burst of this many taps (0 = the tap just goes through, normal). */
    public int turboBurst() { return Math.max(0, Math.min(500, sp.getInt("turbo_burst", 10))); }
    public void setTurboBurst(int v) { sp.edit().putInt("turbo_burst", v).apply(); }
    /** Hold the finger: taps there non-stop until you touch the screen again. */
    public boolean turboHold() { return sp.getBoolean("turbo_hold", true); }
    public void setTurboHold(boolean v) { sp.edit().putBoolean("turbo_hold", v).apply(); }
    /** Non-stop taps end by themselves after this many seconds (safety). */
    public int turboMaxSeconds() { return Math.max(5, Math.min(3600, sp.getInt("turbo_max_s", 120))); }
    public void setTurboMaxSeconds(int v) { sp.edit().putInt("turbo_max_s", v).apply(); }
    /** Each tap lands up to this many px away (0 = exactly the same spot). */
    public int turboSpread() { return Math.max(0, Math.min(100, sp.getInt("turbo_spread", 0))); }
    public void setTurboSpread(int v) { sp.edit().putInt("turbo_spread", v).apply(); }

    /** ⚡ Auto-start (2.1): app package → "macroId|panel" or "macroId|run". One macro per app. */
    public String autostart(String pkg) { return pkg == null ? null : sp.getString("autostart_" + pkg, null); }

    public void setAutostart(String pkg, String value) {
        if (value == null) sp.edit().remove("autostart_" + pkg).apply();
        else sp.edit().putString("autostart_" + pkg, value).apply();
    }

    public java.util.Map<String, String> autostarts() {
        java.util.Map<String, String> m = new java.util.TreeMap<String, String>();
        for (java.util.Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (e.getKey().startsWith("autostart_") && e.getValue() instanceof String) m.put(e.getKey().substring(10), (String) e.getValue());
        }
        return m;
    }

    /** Code editor (2.0): screenshot pane shown, its height (px, 0 = default), which marks are drawn. */
    public boolean codeShotPane() { return sp.getBoolean("code_shot_pane", true); }
    public void setCodeShotPane(boolean v) { sp.edit().putBoolean("code_shot_pane", v).apply(); }
    public int codeShotHeight() { return sp.getInt("code_shot_h", 0); }
    public void setCodeShotHeight(int px) { sp.edit().putInt("code_shot_h", px).apply(); }
    public boolean codeMarksAll() { return sp.getBoolean("code_marks_all", true); }
    public void setCodeMarksAll(boolean v) { sp.edit().putBoolean("code_marks_all", v).apply(); }
    /** One bit per category (CodeMarks.TOUCH … + steps + simulation); all on by default. */
    public int codeMarksMask() { return sp.getInt("code_marks_mask", 0xFF); }
    public void setCodeMarksMask(int m) { sp.edit().putInt("code_marks_mask", m).apply(); }
    /** The screenshot last looked at, per script. */
    public String codeShot(String scriptId) { return sp.getString("code_shot_" + scriptId, null); }
    public void setCodeShot(String scriptId, String shotId) { sp.edit().putString("code_shot_" + scriptId, shotId).apply(); }

    /** Floating console: open, and where it was left (top-left corner in px; -1 = default place). */
    public boolean consoleOpen() { return sp.getBoolean("console_open", false); }
    public void setConsoleOpen(boolean v) { sp.edit().putBoolean("console_open", v).apply(); }
    public int consoleX() { return sp.getInt("console_x", -1); }
    public int consoleY() { return sp.getInt("console_y", -1); }
    public void setConsolePos(int x, int y) { sp.edit().putInt("console_x", x).putInt("console_y", y).apply(); }
    /** The console also lists every step as it runs. */
    public boolean consoleSteps() { return sp.getBoolean("console_steps", false); }
    public void setConsoleSteps(boolean v) { sp.edit().putBoolean("console_steps", v).apply(); }

    /** Press volume-down twice quickly to stop everything. */
    public boolean volumeEmergencyStop() { return sp.getBoolean("volume_stop", true); }
    public void setVolumeEmergencyStop(boolean v) { sp.edit().putBoolean("volume_stop", v).apply(); }

    /** Allow other apps (Tasker, MacroDroid) to start macros by broadcast. Off by default. */
    public boolean allowExternalTriggers() { return sp.getBoolean("external", false); }
    public void setAllowExternalTriggers(boolean v) { sp.edit().putBoolean("external", v).apply(); }

    /** Draw a short ripple where each injected touch lands. */
    public boolean showTouches() { return sp.getBoolean("show_touches", true); }
    public void setShowTouches(boolean v) { sp.edit().putBoolean("show_touches", v).apply(); }

    /** Panel opacity in percent (30..100). */
    public int panelOpacity() { return sp.getInt("panel_opacity", 90); }
    public void setPanelOpacity(int v) { sp.edit().putInt("panel_opacity", Math.max(30, Math.min(100, v))).apply(); }

    /** Panel button size in dp. */
    public int panelButtonDp() { return sp.getInt("panel_button_dp", 44); }
    public void setPanelButtonDp(int v) { sp.edit().putInt("panel_button_dp", Math.max(32, Math.min(64, v))).apply(); }

    /** 0 = low (854p), 1 = medium (1280p), 2 = high (1920p). */
    public int videoQuality() { return sp.getInt("video_quality", 1); }
    public void setVideoQuality(int v) { sp.edit().putInt("video_quality", v).apply(); }

    /** Countdown (s) after pressing play in the panel, so you can let go of the screen. */
    public int startCountdown() { return sp.getInt("countdown", 0); }
    public void setStartCountdown(int v) { sp.edit().putInt("countdown", Math.max(0, Math.min(10, v))).apply(); }

    /** Selected app mode on the home screen (Macro.KIND_*). */
    public String mode() { return sp.getString("mode", com.freeclick.core.Macro.KIND_MANUAL); }
    public void setMode(String k) { sp.edit().putString("mode", k).apply(); }

    /** Unsaved work of each mode ("Nesalvat"), kept when the panel is turned off. */
    /**
     * Unsaved work lives in files/drafts/<mode>.json (atomic writes), not in the preferences
     * file, so long recordings never bloat or slow down settings. Drafts kept by versions
     * 1.1–1.4 in the preferences are moved over automatically the first time they are read.
     */
    public String draft(String kind) { return drafts.read(kind); }

    public boolean draftDirty(String kind) { return sp.getBoolean("draft_dirty_" + kind, false); }
    public synchronized void setDraft(String kind, String json, boolean dirty) {
        SharedPreferences.Editor e = sp.edit().putBoolean("draft_dirty_" + kind, dirty);
        if (drafts.write(kind, json)) e.remove("draft_" + kind);
        else e.putString("draft_" + kind, json); // fallback: never lose the work
        e.apply();
    }

    /**
     * Show the numbered points/targets of a mode on screen. Manual targets: shown by default
     * (you place them). Record mode: hidden by default, like Clickmate (the recording still plays).
     */
    public boolean pointsVisible(String kind) {
        return sp.getBoolean("points_" + kind, com.freeclick.core.Macro.KIND_MANUAL.equals(kind));
    }
    public void setPointsVisible(String kind, boolean v) { sp.edit().putBoolean("points_" + kind, v).apply(); }

    public String lastMacroId() { return sp.getString("last_macro", null); }
    public void setLastMacroId(String id) { sp.edit().putString("last_macro", id).apply(); }

    public int panelX() { return sp.getInt("panel_x", 0); }
    public int panelY() { return sp.getInt("panel_y", 300); }
    public void setPanelPos(int x, int y) { sp.edit().putInt("panel_x", x).putInt("panel_y", y).apply(); }

    /** Random per-install secret embedded in our home-screen shortcuts, so other apps cannot fake them. */
    public synchronized String shortcutToken() {
        String t = sp.getString("shortcut_token", null);
        if (t == null) {
            byte[] b = new byte[24];
            new java.security.SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
            t = sb.toString();
            sp.edit().putString("shortcut_token", t).apply();
        }
        return t;
    }

    /**
     * Pause playback when the user touches the screen (a gesture gets cancelled) or pulls down
     * the notification shade, so taps never land on system buttons. On by default.
     */
    public boolean pauseOnTouch() { return sp.getBoolean("pause_on_touch", true); }
    public void setPauseOnTouch(boolean v) { sp.edit().putBoolean("pause_on_touch", v).apply(); }

    /** Play / pause / stop buttons in a notification while the panel is shown. On by default. */
    public boolean notificationControls() { return sp.getBoolean("notif_controls", true); }
    public void setNotificationControls(boolean v) { sp.edit().putBoolean("notif_controls", v).apply(); }

    public SharedPreferences raw() { return sp; }
}
