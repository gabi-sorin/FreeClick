package com.freeclick.app;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.freeclick.core.AfkWatch;
import com.freeclick.core.Macro;
import com.freeclick.core.MacroCodec;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * 🌙 AFK mode (2.2.1): what keeps a long run going while nobody watches.
 * <ul>
 *   <li>every real run: the screen stays on (a 1 px window), also with the panel hidden;</li>
 *   <li>AFK on: a foreground service with its own notification (Android does not stop FreeClick),
 *       a pause when the screen is locked, when the battery is low (until charging) or the phone is hot;</li>
 *   <li>"se reface singur": the game leaves the screen → pause; not back in 20 s → "I reopen it in 10 s ·
 *       Cancel" → the game is opened again and the run starts over (at most 5 times an hour); if Android
 *       stopped FreeClick itself, the session is picked up again when the service comes back;</li>
 *   <li>optional: the screen at minimum brightness while it taps.</li>
 * </ul>
 * The decisions are in {@link AfkWatch} (tested); this class only listens and acts.
 */
final class AfkGuard {
    interface Host {
        boolean afkRunning();
        void afkPause();
        void afkResume();
        void afkStopForRestart();
        void afkRestart(Macro m, boolean script, boolean workspace);
        void afkEnd();
        String afkForeground();
        void afkSessionChanged();
        void toast(String s);
        void log(String s);
    }

    private static final String FILE = "afk_session.json";

    private final Context ctx;
    private final WindowManager wm;
    private final Handler main;
    private final Prefs prefs;
    private final Host host;
    private final AfkWatch watch = new AfkWatch();
    private final Set<String> homes = new HashSet<String>();

    // the session
    private Macro macro;
    private boolean script, workspace, afk;
    private String game;
    private long startedWall;
    private int loops, restartsBefore;

    private boolean restarting;
    private boolean pausedByAfk;
    private boolean runRunning, runUserPaused;
    private int battery = AfkWatch.BATTERY_OK;
    private int level = -1;
    private float temp;
    private boolean screenHold;
    private long lastBeat, lastNote;
    private String lastNoteText = "";

    private View awake;
    private float awakeBrightness = Float.NaN;
    private LinearLayout bar;
    private TextView barText;
    private BroadcastReceiver receiver;

    AfkGuard(Context ctx, WindowManager wm, Handler main, Prefs prefs, Host host) {
        this.ctx = ctx;
        this.wm = wm;
        this.main = main;
        this.prefs = prefs;
        this.host = host;
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : ctx.getPackageManager().queryIntentActivities(home, 0)) homes.add(r.activityInfo.packageName);
        } catch (RuntimeException ignored) { }
    }

    boolean active() { return macro != null; }

    /** The 🌙 notification is up (then it carries the panel controls too). */
    boolean ownsNotification() { return active() && afk && SessionService.isRunning(); }

    // ------------------------------------------------------------------ run events from ClickService
    void onRunStarted(Macro m, boolean isScript, boolean isWorkspace, String foreground) {
        boolean again = restarting && active();
        restarting = false;
        main.removeCallbacks(restartCheck);
        runRunning = true;
        runUserPaused = false;
        pausedByAfk = false;
        if (again) {
            host.log("🌙 Reluat de la capăt (" + watch.restartCount() + ")");
            note(true);
            updateAwake();
            return;
        }
        if (active()) endSession();
        macro = m;
        script = isScript;
        workspace = isWorkspace;
        game = pickGame(m, foreground);
        afk = prefs.afkOn();
        startedWall = System.currentTimeMillis();
        loops = 0;
        restartsBefore = 0;
        watch.start(afk && prefs.afkRecover() ? game : null);
        if (afk) beginAfk();
        updateAwake();
    }

    void onRunEnded() {
        runRunning = false;
        runUserPaused = false;
        if (restarting) { updateAwake(); return; }
        if (active()) endSession();
    }

    void onRunState(boolean running, boolean userPaused) {
        runRunning = running;
        runUserPaused = userPaused;
        if (running) pausedByAfk = false;
        updateAwake();
        note(false);
    }

    /** ▶ / ⏸ / ■ by the user: whatever AFK mode was waiting for is over. */
    void userTookOver() {
        watch.userTookOver();
        pausedByAfk = false;
        hideBar();
    }

    void onLoop(int n) { loops = n; }

    void runOpened(String pkg) { if (active()) watch.runOpened(pkg, now()); }

    void runSwitched() { if (active()) watch.runSwitched(now()); }

    void onForeground(String pkg) {
        if (!active() || !afk || !screenOn()) return;
        act(watch.onForeground(pkg, now()));
    }

    /** The accessibility service is going away (turned off): no session survives that. */
    void shutdown() {
        restarting = false;
        endSession();
        hideBar();
        removeAwake();
    }

    // ------------------------------------------------------------------ the session
    private void beginAfk() {
        register();
        SessionService.start(ctx, title(), text());
        save();
        lastBeat = SystemClock.uptimeMillis();
        main.removeCallbacks(tick);
        main.postDelayed(tick, 1000);
        if (!prefs.sectionOpen("afk.hint.v1")) {
            prefs.setSectionOpen("afk.hint.v1", true);
            host.toast("🌙 Mod AFK: ecranul rămâne aprins, iar dacă jocul se închide îl redeschid. Setări → 🌙 Mod AFK.");
        }
    }

    private void endSession() {
        main.removeCallbacks(tick);
        main.removeCallbacks(restartCheck);
        hideBar();
        unregister();
        if (afk) SessionService.stop(ctx);
        deleteFile();
        macro = null;
        afk = false;
        game = null;
        watch.start(null);
        battery = AfkWatch.BATTERY_OK;
        screenHold = false;
        pausedByAfk = false;
        updateAwake();
        host.afkSessionChanged();
    }

    private String pickGame(Macro m, String foreground) {
        String lock = m.settings.lockToPackage == null ? "" : m.settings.lockToPackage.trim();
        if (lock.length() > 0) return lock;
        String launch = m.settings.launchPackage == null ? "" : m.settings.launchPackage.trim();
        if (launch.length() > 0) return launch;
        if (foreground == null || foreground.isEmpty() || foreground.equals(ctx.getPackageName()) || homes.contains(foreground)
                || ClickService.isSystemUiPackage(foreground)) return null;
        return foreground;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active() || !afk) return;
            long t = now();
            if (screenOn()) {
                if (watch.phase() == AfkWatch.REOPENING) { // the game may already be up (no window event then)
                    String fg = host.afkForeground();
                    if ((fg == null || fg.isEmpty()) && t - launchedAt > 3000) fg = game; // FreeClick just restarted: launched it, nothing else came up
                    if (fg != null && fg.length() > 0) act(watch.onForeground(fg, t));
                }
                act(watch.tick(t));
            }
            if (t - lastBeat >= 60000) { lastBeat = t; save(); }
            note(false);
            if (active()) main.postDelayed(this, 1000);
        }
    };

    private void act(int what) {
        if (!active()) return;
        switch (what) {
            case AfkWatch.PAUSE:
                if (host.afkRunning()) {
                    pauseForAfk();
                    host.toast("🌙 Pauză: jocul nu mai e pe ecran. Reiau când revii.");
                    host.log("🌙 Pauză: jocul nu mai e pe ecran");
                } else {
                    watch.hold(); // you had paused it yourself: AFK mode does not take over
                }
                break;
            case AfkWatch.RESUME:
                hideBar();
                maybeResume();
                break;
            case AfkWatch.ASK:
                if (!pausedByAfk) { watch.hold(); break; }
                showBar("🌙 Jocul nu mai e deschis. Îl redeschid în %d s", (int) (watch.askMs / 1000), new Runnable() {
                    public void run() {
                        watch.hold();
                        host.toast("Bine, aștept. ▶ pe panou ca să continui.");
                    }
                }, null);
                break;
            case AfkWatch.REOPEN:
                hideBar();
                restarting = true;
                pausedByAfk = false;
                host.afkStopForRestart();
                launch(game);
                host.log("🌙 Redeschid " + game);
                break;
            case AfkWatch.RESTART:
                restarting = true;
                host.afkRestart(macro, script, workspace);
                main.removeCallbacks(restartCheck);
                main.postDelayed(restartCheck, 12000);
                break;
            case AfkWatch.GIVE_UP: {
                hideBar();
                String why = "Jocul nu a mai pornit sau s-a închis de prea multe ori într-o oră, așa că m-am oprit.";
                host.log("🌙 " + why);
                SessionService.notifyEnded(ctx, why);
                restarting = false;
                if (host.afkRunning() || pausedByAfk) host.afkEnd();
                if (active()) endSession();
                break;
            }
            default:
                break;
        }
        note(false);
    }

    /** The run did not start again (refused, cancelled...): the session ends instead of hanging. */
    private final Runnable restartCheck = new Runnable() {
        @Override public void run() {
            if (!restarting) return;
            restarting = false;
            SessionService.notifyEnded(ctx, "Am redeschis jocul, dar rularea nu a putut porni din nou.");
            endSession();
        }
    };

    private boolean holding() { return watch.pausedByUs() || battery != AfkWatch.BATTERY_OK || screenHold; }

    private void pauseForAfk() {
        if (host.afkRunning()) {
            host.afkPause();
            pausedByAfk = true;
        }
        updateAwake();
        note(true);
    }

    private void maybeResume() {
        if (pausedByAfk && !holding()) {
            pausedByAfk = false;
            host.afkResume();
        }
        updateAwake();
        note(true);
    }

    private long launchedAt;

    private void launch(String pkg) {
        if (pkg == null) return;
        launchedAt = now();
        Intent li = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { ctx.startActivity(li); } catch (RuntimeException ignored) { }
    }

    // ------------------------------------------------------------------ battery, heat, screen lock
    private void register() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_BATTERY_CHANGED.equals(a)) onBattery(i);
                else if (Intent.ACTION_SCREEN_OFF.equals(a)) onScreen(false);
                else if (Intent.ACTION_USER_PRESENT.equals(a)) onScreen(true);
                else if (Intent.ACTION_SCREEN_ON.equals(a) && !locked()) onScreen(true);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            else ctx.registerReceiver(receiver, f);
        } catch (RuntimeException e) {
            receiver = null;
        }
    }

    private void unregister() {
        if (receiver == null) return;
        try { ctx.unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
        receiver = null;
    }

    private void onBattery(Intent i) {
        int l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        level = l < 0 || scale <= 0 ? -1 : Math.round(l * 100f / scale);
        boolean plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        int b = AfkWatch.battery(level, plugged, temp, prefs.afkMinBattery(), battery);
        if (b == battery) return;
        battery = b;
        if (b == AfkWatch.BATTERY_LOW) {
            pauseForAfk();
            host.toast("🌙 Pauză: bateria e la " + level + "%. Continui când e pusă la încărcat.");
            host.log("🌙 Pauză: baterie " + level + "%");
        } else if (b == AfkWatch.BATTERY_HOT) {
            pauseForAfk();
            host.toast("🌙 Pauză: telefonul are " + Math.round(temp) + " °C. Continui când se răcește.");
            host.log("🌙 Pauză: " + Math.round(temp) + " °C");
        } else {
            host.log("🌙 Bateria e în regulă (" + level + "%, " + Math.round(temp) + " °C)");
            maybeResume();
        }
    }

    private void onScreen(boolean usable) {
        if (!usable) {
            screenHold = true;
            pauseForAfk();
            host.log("🌙 Pauză: ecranul s-a închis");
        } else if (screenHold) {
            screenHold = false;
            maybeResume();
        }
    }

    private boolean locked() {
        KeyguardManager k = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        return k != null && k.isKeyguardLocked();
    }

    private boolean screenOn() {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    // ------------------------------------------------------------------ the screen stays on (and maybe dim)
    private void updateAwake() {
        boolean want = active() && !runUserPaused && battery == AfkWatch.BATTERY_OK;
        if (!want) { removeAwake(); return; }
        float b = afk && prefs.afkDim() && runRunning && !pausedByAfk ? 0.02f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if (awake == null) {
            awake = new View(ctx);
            WindowManager.LayoutParams lp = Overlays.params(1, 1, false, false);
            lp.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            lp.screenBrightness = b;
            Overlays.safeAdd(wm, awake, lp);
            awakeBrightness = b;
        } else if (b != awakeBrightness) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) awake.getLayoutParams();
            if (lp != null) {
                lp.screenBrightness = b;
                Overlays.safeUpdate(wm, awake, lp);
            }
            awakeBrightness = b;
        }
    }

    private void removeAwake() {
        if (awake != null) Overlays.safeRemove(wm, awake);
        awake = null;
        awakeBrightness = Float.NaN;
    }

    /** The dim setting changed in Settings. */
    void settingsChanged() { updateAwake(); }

    // ------------------------------------------------------------------ the 🌙 notification
    private String title() { return "🌙 AFK · „" + (macro == null ? "" : macro.name) + "”"; }

    private String text() {
        StringBuilder s = new StringBuilder(AfkWatch.duration(System.currentTimeMillis() - startedWall));
        if (loops > 0) s.append(" · ciclul ").append(loops);
        int r = restartsBefore + watch.restartCount();
        if (r > 0) s.append(" · ").append(r).append(r == 1 ? " repornire" : " reporniri");
        if (level >= 0) s.append(" · 🔋 ").append(level).append('%');
        if (battery == AfkWatch.BATTERY_LOW) s.append(" · pauză: baterie");
        else if (battery == AfkWatch.BATTERY_HOT) s.append(" · pauză: cald");
        else if (screenHold) s.append(" · pauză: ecran închis");
        else if (watch.phase() == AfkWatch.REOPENING || watch.phase() == AfkWatch.SETTLING) s.append(" · redeschid jocul");
        else if (pausedByAfk) s.append(" · pauză: jocul nu e pe ecran");
        else if (runUserPaused) s.append(" · pauză");
        return s.toString();
    }

    /** At most every 30 s (Android limits updates), or now when the state changed. */
    private void note(boolean now) {
        if (!active() || !afk) return;
        String t = text();
        long u = SystemClock.uptimeMillis();
        boolean stateChanged = !stateOf(t).equals(stateOf(lastNoteText));
        if (!now && !stateChanged && u - lastNote < 30000) return;
        lastNote = u;
        lastNoteText = t;
        SessionService.update(ctx, title(), t, pausedByAfk || runUserPaused || !runRunning);
        host.afkSessionChanged();
    }

    private static String stateOf(String t) {
        int i = t.indexOf("pauză");
        if (i < 0) i = t.indexOf("redeschid");
        return i < 0 ? "" : t.substring(i);
    }

    // ------------------------------------------------------------------ "I reopen it in 10 s · Cancel"
    private void showBar(final String fmt, final int seconds, final Runnable onCancel, final Runnable onDone) {
        hideBar();
        bar = Ui.horizontal(ctx);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8));
        android.graphics.drawable.GradientDrawable bg = Ui.rounded(0xF0161040, Ui.dp(ctx, 18));
        bg.setStroke(Ui.dp(ctx, 2), 0xFFA78BFA);
        bar.setBackground(bg);
        barText = Ui.text(ctx, String.format(fmt, seconds), 14, 0xFFFFFFFF, false);
        bar.addView(barText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView cancel = Ui.text(ctx, "Anulează", 15, 0xFFA78BFA, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0);
        final LinearLayout mine = bar;
        Ui.clickable(cancel, new View.OnClickListener() { public void onClick(View v) {
            if (bar != mine) return;
            hideBar();
            if (onCancel != null) onCancel.run();
        } }, Ui.dp(ctx, 14));
        bar.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(ctx, 44)));
        android.graphics.Point sz = Overlays.screenSize(ctx);
        WindowManager.LayoutParams lp = Overlays.params(sz.x - 2 * Ui.dp(ctx, 10), ViewGroup.LayoutParams.WRAP_CONTENT, true, false);
        lp.x = Ui.dp(ctx, 10);
        int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        lp.y = (id > 0 ? ctx.getResources().getDimensionPixelSize(id) : Ui.dp(ctx, 24)) + Ui.dp(ctx, 6);
        Overlays.safeAdd(wm, bar, lp);
        final int[] left = {seconds};
        main.postDelayed(new Runnable() { public void run() {
            if (bar != mine) return;
            left[0]--;
            if (left[0] <= 0) {
                if (onDone != null) { hideBar(); onDone.run(); }
                return; // ASK: AfkWatch decides when (it hides the bar then)
            }
            barText.setText(String.format(fmt, left[0]));
            main.postDelayed(this, 1000);
        } }, 1000);
    }

    private void hideBar() {
        if (bar != null) Overlays.safeRemove(wm, bar);
        bar = null;
        barText = null;
    }

    // ------------------------------------------------------------------ after Android stopped FreeClick
    private File file() { return new File(ctx.getFilesDir(), FILE); }

    private void save() {
        if (!active() || !afk || !prefs.afkRecover()) return;
        try {
            JSONObject j = new JSONObject();
            j.put("macro", MacroCodec.toJson(macro));
            j.put("script", script);
            j.put("workspace", workspace);
            j.put("game", game == null ? "" : game);
            j.put("started", startedWall);
            j.put("beat", System.currentTimeMillis());
            j.put("loops", loops);
            j.put("restarts", restartsBefore + watch.restartCount());
            File tmp = new File(ctx.getFilesDir(), FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try { out.write(j.toString().getBytes("UTF-8")); } finally { out.close(); }
            if (!tmp.renameTo(file())) tmp.delete();
        } catch (Exception ignored) { } // best effort
    }

    private void deleteFile() { file().delete(); }

    /**
     * Called when the accessibility service (re)connects. If the last AFK session did not end
     * (Android stopped FreeClick less than 20 minutes ago), it is picked up again after a 10 s "Cancel".
     */
    void maybeResumeAfterKill() {
        if (active() || !file().exists()) return;
        if (!prefs.afkOn() || !prefs.afkRecover()) { deleteFile(); return; }
        final JSONObject j;
        final Macro m;
        try {
            FileInputStream in = new FileInputStream(file());
            byte[] buf;
            try {
                buf = new byte[(int) Math.min(8 << 20, file().length())];
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            } finally { in.close(); }
            j = new JSONObject(new String(buf, "UTF-8"));
            m = MacroCodec.fromJson(j.getString("macro"));
        } catch (Exception e) {
            deleteFile();
            return;
        }
        if (System.currentTimeMillis() - j.optLong("beat") > 20 * 60000L) { deleteFile(); return; }
        host.log("🌙 Sesiunea AFK a fost întreruptă de Android");
        showBar("🌙 Android a oprit FreeClick. Reiau „" + m.name + "” în %d s", 10, new Runnable() {
            public void run() { deleteFile(); host.toast("Bine, nu o reiau."); }
        }, new Runnable() {
            public void run() { resumeKilled(m, j); }
        });
    }

    private void resumeKilled(Macro m, JSONObject j) {
        if (active()) return;
        macro = m;
        script = j.optBoolean("script");
        workspace = j.optBoolean("workspace");
        String g = j.optString("game", "");
        game = g.isEmpty() ? null : g;
        afk = true;
        startedWall = j.optLong("started", System.currentTimeMillis());
        loops = j.optInt("loops");
        restartsBefore = j.optInt("restarts") + 1;
        watch.start(game);
        beginAfk();
        restarting = true;
        if (game != null) {
            launch(game);
            watch.expectReopen(now());
        } else {
            host.afkRestart(macro, script, workspace);
            main.postDelayed(restartCheck, 12000);
        }
        updateAwake();
    }

    private static long now() { return SystemClock.uptimeMillis(); }
}
