package com.freeclick.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;
import com.freeclick.core.ActionTree;
import com.freeclick.core.CoordinateScaler;
import com.freeclick.core.GesturePlan;
import com.freeclick.core.GesturePlanner;
import com.freeclick.core.GlobalActions;
import com.freeclick.core.ImageMatcher;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.ImageTemplate;
import com.freeclick.core.Macro;
import com.freeclick.core.MacroCodec;
import com.freeclick.core.MacroGraph;
import com.freeclick.core.MacroLibrary;
import com.freeclick.core.MacroTransform;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.ScreenFrame;
import com.freeclick.core.TextReader;
import com.freeclick.core.TouchTracker;
import com.freeclick.core.Workspace;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The accessibility service: owns the floating panel, the targets, the
 * recording layer and the playback engine, and injects gestures with
 * dispatchGesture (no root needed).
 *
 * Like Clickmate there are three modes (manual targets, record, combine);
 * each keeps its current work (saved or "Nesalvat") while the panel is off.
 */
public final class ClickService extends AccessibilityService implements PlaybackEngine.Host,
        PlaybackEngine.Listener, PlaybackEngine.Notices, FloatingPanel.Callbacks, MarkerLayer.Callbacks,
        RecordingLayer.Callbacks {

    private static ClickService instance;

    /** The running service, or null if the user has not enabled it. */
    public static ClickService get() { return instance; }

    static final double[] SPEEDS = {0.1, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3, 5, 10};
    private static final long VOLUME_DOUBLE_PRESS_MS = 600;
    public static final String UNSAVED = Workspace.UNSAVED;

    private Handler main;
    private WindowManager wm;
    private Prefs prefs;
    private MacroRepository repo;
    private PlaybackEngine engine;
    private FloatingPanel panel;
    private MarkerLayer markers;
    private RecordingLayer recording;
    private TouchFeedbackLayer feedback;
    private ScreenSampler sampler;
    private ConsoleLayer console;
    /** The script running now (1.9), or null. */
    private ScriptSession script;
    private boolean scriptShowedPanel;
    /** The console was opened by a "Afișează" step in this run (only once per run). */
    private boolean consoleAutoOpened;

    private Workspace ws;            // current work per mode (pure logic, unit-tested)
    private String foregroundPkg = "";
    private long lastVolumeDown;
    private boolean videoForThisRun;
    private boolean awaitingStart;   // waiting for capture consent / app launch / countdown before starting
    private boolean colorRun;        // current run reads screen colours
    private boolean hiddenForRun;    // targets hidden for this run (safe mode or colour conditions)
    private int optionsScreens;      // our settings screens on top: hide targets meanwhile
    private int countdownLeft;
    private android.app.AlertDialog openDialog;
    private android.widget.PopupWindow openPopup;
    private View picker;
    private boolean picking;         // colour picker open or reading the colour: points hidden
    private final java.util.Set<String> imePackages = new java.util.HashSet<String>();
    private final java.util.LinkedList<String> log = new java.util.LinkedList<String>();
    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private UndoBar undoBar;
    private ControlNotification notif;
    private ImageStore images;
    private java.util.concurrent.ExecutorService matchExec;
    // What the next/current run plays: the panel's work, or a copy handed over by the editor.
    private Macro runMacro;          // null = the panel's current work
    private boolean runPreview;      // draw the touches instead of doing them
    private boolean runPreviewFound = true;
    private int runFromIndex;
    private boolean returnToEditor;  // bring the editor back when the run ends
    private String previewCaption = "";
    private boolean panelHiddenByUser; // notification → "Ascunde panoul"
    private TurboLayer turbo;          // 🔥 taps where you touch (2.2)
    private AfkGuard afk;              // 🌙 long sessions (2.2.1)
    private TouchListener ears;        // 👂 your fingers, for scripts (2.3)
    private boolean systemUiOpenedByMacro;  // a step opened the shade / recents itself: don't pause for it
    private long ignoreSystemUiUntil;

    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            if (panel.state() != FloatingPanel.State.WAITING || !awaitingStart) return;
            if (countdownLeft <= 0) { awaitingStart = false; beginPlayback(); return; }
            panel.setInfo(String.valueOf(countdownLeft));
            countdownLeft--;
            main.postDelayed(this, 1000);
        }
    };

    // ================================================================ lifecycle
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        main = new Handler(Looper.getMainLooper());
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = App.get().prefs();
        repo = App.get().repo();
        engine = new PlaybackEngine(this, this);
        engine.setNotices(this);
        undoBar = new UndoBar(this, wm, main);
        notif = new ControlNotification(this);
        images = App.get().images();
        panel = new FloatingPanel(this, wm, prefs, this);
        markers = new MarkerLayer(this, wm, this);
        recording = new RecordingLayer(this, wm, this);
        feedback = new TouchFeedbackLayer(this, wm);
        console = new ConsoleLayer(this, wm, prefs);
        turbo = new TurboLayer(this, wm, main, prefs, new TurboLayer.Host() {
            public void injectTurbo(GesturePlan plan, PlaybackEngine.GestureCallback cb) { inject(clampToScreen(plan), cb); }
            public int maxStrokes() { return limits().maxStrokes; }
            public void onTurboChanged(boolean on) { turboChanged(on); }
        });
        afk = new AfkGuard(this, wm, main, prefs, afkHost());
        ears = new TouchListener(this, wm, main, new TouchListener.Host() {
            public void inject(GesturePlan plan, PlaybackEngine.GestureCallback cb) { ClickService.this.inject(clampToScreen(plan), cb); }
            public int maxStrokes() { return limits().maxStrokes; }
            public int[] panelRect() { return panel == null ? null : panel.screenRect(); }
            public long panelTouchedAt() { return panel == null ? -1 : panel.touchedAt(); }
            public void raisePanel() { if (panel != null) panel.bringToFront(); }
            public void listeningChanged(boolean on) { if (panel != null) panel.setEar(on); }
            public void toast(String s) { ClickService.this.toast(s); }
            public void log(String s) { addLog(s); }
        });
        ears.setOnBlocked(new Runnable() { public void run() { ears.allowDirect(ClickService.this, false); prefs.setTouchDirect(false); } });
        ears.allowDirect(this, prefs.touchDirect()); // only used while a script listens
        sampler = new ScreenSampler(this, main);
        ws = new Workspace(new Workspace.Store() {
            public String mode() { return prefs.mode(); }
            public void setMode(String k) { prefs.setMode(k); }
            public String draft(String k) { return prefs.draft(k); }
            public boolean draftDirty(String k) { return prefs.draftDirty(k); }
            public void setDraft(String k, String json, boolean d) { prefs.setDraft(k, json, d); }
        }, repo, new Workspace.Saver() {
            public void save(Macro m) throws Exception { repo.save(m); }
        });
        applyServiceFlags();
        refreshImePackages();
        // draw the reference letters of the text reader in the background, so the first read is quick
        main.postDelayed(new Runnable() { public void run() {
            try {
                matcher().execute(new Runnable() { public void run() { App.get().letters().book(); } });
            } catch (java.util.concurrent.RejectedExecutionException ignored) { }
        } }, 8000);
        QuickTileService.requestUpdate(this);
        notifyListeners();
        // 🌙 a long session that Android interrupted (it stopped FreeClick) is picked up again
        main.postDelayed(new Runnable() { public void run() { if (afk != null) afk.maybeResumeAfterKill(); } }, 2500);
    }

    /** Key filtering is only requested while the emergency stop is enabled. */
    void applyServiceFlags() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        if (prefs.volumeEmergencyStop()) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }

    /** Keyboards open their own windows; they must not count as "another app" for lock-to-app. */
    private void refreshImePackages() {
        imePackages.clear();
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            for (android.view.inputmethod.InputMethodInfo i : imm.getEnabledInputMethodList()) imePackages.add(i.getPackageName());
        } catch (RuntimeException ignored) { }
    }

    @Override public boolean onUnbind(Intent intent) {
        teardown();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    private void teardown() {
        if (instance != this) return;
        if (afk != null) afk.shutdown(); // turned off: no session is picked up again
        if (ears != null) ears.shutdown();
        if (script != null) script.runner.stop();
        if (engine != null) engine.stop();
        if (ws != null) ws.persist();
        closeEverything();
        if (sampler != null) sampler.release();
        if (notif != null) notif.cancel();
        if (matchExec != null) { matchExec.shutdownNow(); matchExec = null; }
        instance = null;
        QuickTileService.requestUpdate(this);
        notifyListeners();
    }

    @Override public void onInterrupt() {
        if (engine != null) engine.stop();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (isSystemUiPackage(p)) {
            onSystemUiWindow(event);
            return;
        }
        systemUiOpenedByMacro = false; // back in an app
        if (p.equals(getPackageName()) || p.equals("android") || imePackages.contains(p)) return;
        boolean changedApp = !p.equals(foregroundPkg);
        foregroundPkg = p;
        if (afk != null) afk.onForeground(p); // 🌙 first: AFK mode pauses outside the game and reopens it
        Macro playing = engine == null ? null : engine.macro();
        if (playing != null && engine.state() == PlaybackEngine.State.RUNNING && !engine.isPreview()) {
            String lock = playing.settings.lockToPackage;
            if (lock != null && lock.length() > 0 && !lock.equals(p)) {
                engine.pause(PlaybackEngine.PAUSE_APP);
                toast("Pauză: s-a deschis altă aplicație (" + appLabel(p) + "). Reia singur când revii.");
            }
        } else if (playing != null && engine.state() == PlaybackEngine.State.PAUSED
                && PlaybackEngine.PAUSE_APP.equals(engine.pauseReason())) {
            String lock = playing.settings.lockToPackage;
            if (lock != null && lock.equals(p)) { // back in the macro's app: go on by itself
                engine.resume();
                toast("Reiau: ești înapoi în " + appLabel(p));
            }
        }
        if (changedApp) maybeAutostart(p);
    }

    // ---- ⚡ start when an app opens (2.1)
    private Runnable autoRun;

    private void maybeAutostart(final String pkg) {
        String v = prefs.autostart(pkg);
        if (v == null || panel == null) return;
        int bar = v.indexOf('|');
        final Macro m = repo.find(bar < 0 ? v : v.substring(0, bar));
        if (m == null) { prefs.setAutostart(pkg, null); return; } // deleted
        final boolean run = v.endsWith("|" + AutoStart.RUN);
        if (isPlaying() || recording.isActive() || script != null || capture != null || picking) return; // busy: never interrupt
        if (Macro.KIND_SCRIPT.equals(m.kind)) {
            if (!armScript(m, false, null)) return;
        } else {
            boolean same = workspace() != null && m.id.equals(workspace().id);
            if (!same && workspace() != null && !isWorkspaceSaved() && ActionTree.count(workspace().actions) > 0) {
                toast("⚡ „" + m.name + "”: ai ceva nesalvat în panou, nu îl înlocuiesc. Salvează-l sau golește panoul.");
                return;
            }
            if (!same) load(m);
            else if (!panel.isShown()) presentWorkspace();
            panelHiddenByUser = false;
            panel.setHidden(false);
        }
        if (!run) { toast("⚡ „" + m.name + "” e pe panou: ▶ când ești gata"); return; }
        toast("⚡ Pornesc „" + m.name + "” în 3 s (■ pe panou: nu)");
        if (autoRun != null) main.removeCallbacks(autoRun);
        autoRun = new Runnable() { public void run() {
            autoRun = null;
            if (!pkg.equals(foregroundPkg) || isPlaying() || script != null || recording.isActive()) return;
            onPlay();
        } };
        main.postDelayed(autoRun, 3000);
    }

    /** Phone makers ship System UI under different names (com.android.systemui, MIUI's plugin...). */
    static boolean isSystemUiPackage(String p) {
        return p.contains("systemui") || p.equals("com.android.systemui");
    }

    /**
     * A System UI window appeared while taps run (notification shade, quick settings, power menu...):
     * the next taps would land on system buttons (Wi-Fi, airplane mode...), so pause.
     * Ignored: toasts, the volume dialog, and System UI that the macro itself opened (a "Notificări"
     * or "Recente" step). Every such event is written to the activity log, so a phone whose shade
     * is not recognised can be diagnosed from ≡ → Jurnal.
     */
    private void onSystemUiWindow(AccessibilityEvent event) {
        boolean scriptRunning = script != null && !script.runner.isPaused();
        if (!scriptRunning && (engine == null || engine.state() != PlaybackEngine.State.RUNNING || engine.isPreview())) return;
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        StringBuilder t = new StringBuilder();
        if (event.getText() != null) for (CharSequence c : event.getText()) t.append(c).append(' ');
        String text = t.toString().trim();
        addLog("  ↪ fereastră sistem: " + cls.substring(cls.lastIndexOf('.') + 1) + (text.isEmpty() ? "" : " „" + text + "”"));
        if (!prefs.pauseOnTouch()) return;
        if (systemUiOpenedByMacro || SystemClock.uptimeMillis() < ignoreSystemUiUntil) return;
        if (!isShadeOrSystemPanel(cls, text)) return;
        if (script != null) script.runner.pause(com.freeclick.core.script.ScriptRunner.WHY_APP);
        if (isPlaying()) engine.pause(PlaybackEngine.PAUSE_APP);
        toast("Pauză: s-a deschis un panou de sistem (ex. bara de notificări). Apasă ▶ ca să continui.");
    }

    /** Anything from System UI except toasts, the volume dialog and heads-up banners counts (unit-tested). */
    static boolean isShadeOrSystemPanel(String cls, String text) {
        return !(cls.contains("Toast") || cls.contains("olume") || cls.contains("HeadsUp"));
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (!prefs.volumeEmergencyStop()) return false;
        if (e.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && e.getAction() == KeyEvent.ACTION_DOWN
                && e.getRepeatCount() == 0) {
            long now = SystemClock.uptimeMillis();
            boolean busy = isPlaying() || recording.isActive() || awaitingStart || capture != null || script != null || turboOn();
            if (busy && now - lastVolumeDown < VOLUME_DOUBLE_PRESS_MS) {
                emergencyStop();
                lastVolumeDown = 0;
                return false;
            }
            lastVolumeDown = now;
        }
        return false; // never swallow keys: volume keeps working
    }

    // ================================================================ listeners (home screen state)
    public void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    static void notifyListenersStatic() { if (instance != null) instance.notifyListeners(); }

    private void notifyListeners() {
        for (Runnable r : new ArrayList<Runnable>(listeners)) r.run();
    }

    // ================================================================ public API
    public boolean isPanelShown() { return panel != null && panel.isShown(); }

    public boolean isPlaying() {
        return engine != null && (engine.state() == PlaybackEngine.State.RUNNING
                || engine.state() == PlaybackEngine.State.PAUSED);
    }

    public String foregroundPackage() { return foregroundPkg; }

    /** Current work of the panel (may be unsaved). Edit it, then call markDirty(). */
    public Macro workspace() { return ws == null ? null : ws.current(); }
    public boolean isDirty() { return ws != null && ws.isDirty(); }
    public boolean isWorkspaceSaved() { return ws != null && ws.isSaved(); }

    public void markDirty() {
        if (workspace() == null) return;
        ws.markDirty();
        syncMarkers();
    }

    static FloatingPanel.Mode modeOf(String kind) {
        if (Macro.KIND_RECORD.equals(kind)) return FloatingPanel.Mode.RECORD;
        if (Macro.KIND_COMBO.equals(kind)) return FloatingPanel.Mode.COMBO;
        return FloatingPanel.Mode.MANUAL;
    }

    /** "State" switch ON: show the panel for the selected mode with its current work. */
    public void showPanel() {
        updateScreen();
        ws.ensureMode();
        presentWorkspace();
        if (!prefs.sectionOpen("tutorial.panel.v1")) main.postDelayed(new Runnable() { public void run() { panelTutorial(); } }, 700);
    }

    /** "Atinge ca să continui" over the floating panel: what each button does. */
    public void panelTutorial() {
        if (panel == null || !panel.isShown() || isPlaying() || script != null || recording.isActive()) return;
        boolean rec = Macro.KIND_RECORD.equals(workspace() == null ? "" : workspace().kind);
        List<TutorialOverlay.Step> steps = new ArrayList<TutorialOverlay.Step>();
        steps.add(new TutorialOverlay.Step(panel.rootView(), "Acesta e panoul FreeClick. Stă peste orice aplicație; îl muți "
                + "trăgându-l. Atinge ca să vezi ce face fiecare buton."));
        steps.add(new TutorialOverlay.Step(panel.button(PanelIcon.Kind.PLAY), "▶ pornește. În timpul rulării: ■ oprește, ⏸ pune pauză. "
                + "Dacă atingi ecranul cât rulează, se pune singur pe pauză."));
        if (rec) steps.add(new TutorialOverlay.Step(panel.button(PanelIcon.Kind.RECORD), "● înregistrează ce atingi tu; ▶ repetă exact la fel."));
        else steps.add(new TutorialOverlay.Step(panel.button(PanelIcon.Kind.PLUS), "+ adaugă o țintă: tap, swipe, apăsare lungă, zoom cu două degete, "
                + "o imagine de căutat… Ținta apare pe ecran: o tragi unde trebuie, ții apăsat pe ea pentru setări."));
        steps.add(new TutorialOverlay.Step(panel.button(PanelIcon.Kind.MINUS), "− șterge ultima țintă. Ținut apăsat: le șterge pe toate (cu Anulează)."));
        steps.add(new TutorialOverlay.Step(panel.button(PanelIcon.Kind.TURBO), "🔥 Turbo: cât e pornit, unde atingi primești o rafală de atingeri; "
                + "ții degetul o clipă și ridici = atinge acolo non-stop până atingi din nou. × sus îl oprește. Ritmul: Setări → 🔥 Turbo."));
        steps.add(new TutorialOverlay.Step(panel.button(PanelIcon.Kind.MENU), "≡: Opțiuni (Încarcă / Salvează, interval, durată, repetări), "
                + "Arată aplicația, Oprește, iar în Avansat restul (consolă, pipetă, filmare, jurnal…)."));
        steps.add(new TutorialOverlay.Step(null, "Oprire de urgență oricând: apasă de două ori repede volum jos. "
                + "Tutorialul îl găsești în aplicație: ☰ → Tutorial."));
        List<TutorialOverlay.Step> ok = new ArrayList<TutorialOverlay.Step>();
        for (TutorialOverlay.Step s : steps) if (s.target != null || s.text.startsWith("Oprire")) ok.add(s);
        TutorialOverlay.showOverScreen(this, wm, ok.toArray(new TutorialOverlay.Step[0]), new TutorialOverlay.OnClosed() {
            @Override public void onClosed(boolean neverAgain) { if (neverAgain) prefs.setSectionOpen("tutorial.panel.v1", true); }
        });
    }

    /** "State" switch OFF / "Oprește" in the panel menu. Unsaved work is kept as a draft. */
    public void hidePanel() {
        stopScript();
        if (engine != null) engine.stop();
        if (recording.isActive()) finishRecording();
        ws.persist();
        closeEverything();
        QuickTileService.requestUpdate(this);
        notifyListeners();
    }

    /** Switch mode (home screen); if the panel is visible it switches too. */
    public void switchMode(String kind) {
        if (isPanelShown()) {
            if (isPlaying()) engine.stop();
            if (recording.isActive()) finishRecording();
            updateScreen();
            ws.switchMode(kind);
            presentWorkspace();
        } else {
            prefs.setMode(kind);
        }
    }

    /** Load a saved macro as the current work of its mode and show the panel. */
    public void load(Macro m) {
        if (Macro.KIND_SCRIPT.equals(m.kind)) { // a script is not loaded in the panel: it runs
            startActivity(new Intent(this, CodeEditorActivity.class).putExtra(CodeEditorActivity.EXTRA_ID, m.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (isPlaying()) engine.stop();
        if (recording.isActive()) finishRecording();
        disarmScript(false);
        updateScreen();
        ws.load(m);
        presentWorkspace();
    }

    /** Start a fresh, empty "Nesalvat" in the current mode. */
    public void newWorkspace() {
        if (isPlaying()) engine.stop();
        disarmScript(false);
        updateScreen();
        ws.reset();
        if (isPanelShown()) presentWorkspace();
        notifyListeners();
    }

    /** Load a macro and start it right away (tile, shortcut, Tasker, schedule). */
    public void playNow(Macro m) {
        if (Macro.KIND_SCRIPT.equals(m.kind)) { runScript(m, false, null); return; }
        load(m);
        resetRun();
        beginPlayback();
    }

    public boolean saveWorkspace() {
        if (workspace() == null) return false;
        try {
            ws.save();
            afterSave();
            return true;
        } catch (Exception e) {
            toast("Eroare la salvare: " + e.getMessage());
            return false;
        }
    }

    public boolean saveWorkspaceAs(String name) {
        if (workspace() == null) return false;
        try {
            ws.saveAs(name);
            afterSave();
            return true;
        } catch (Exception e) {
            toast("Eroare la salvare: " + e.getMessage());
            return false;
        }
    }

    private void afterSave() {
        prefs.setLastMacroId(workspace().id);
        QuickTileService.requestUpdate(this);
        notifyListeners();
    }

    /** The step-by-step editor hands back an edited copy of the current work. */
    public void replaceWorkspace(Macro edited) {
        if (workspace() == null) return;
        if (isPlaying()) engine.stop();
        ws.replace(edited);
        syncMarkers();
    }

    /** A saved item was edited elsewhere: if it is the current (unchanged) work, reload it. */
    public void refreshFromRepo(String id) {
        if (ws == null || isPlaying()) return;
        if (ws.refreshFromRepo(id) && panel.isShown()) presentWorkspace();
    }

    /** Settings changed which points are shown. */
    public void onPointsSettingChanged() { syncMarkers(); }

    public void onPanelButtonsChanged() { if (panel != null) panel.refresh(); }

    public void stopPlayback() {
        cancelWaiting();
        if (engine != null) engine.stop();
    }

    public void emergencyStop() {
        boolean was = isPlaying() || recording.isActive() || awaitingStart || capture != null || script != null || turboOn();
        if (turboOn()) turbo.stop();
        if (ears != null && ears.isDirect()) { // the way out if hearing touches ever got in the way
            ears.allowDirect(this, false);
            toast("Ascultarea directă a atingerilor e oprită până repornești FreeClick.");
        }
        stopScript();
        if (capture != null) endCapture(); // never leave the user under a full-screen layer
        removeReadyBar();
        cancelWaiting();
        if (engine != null) engine.stop();
        if (recording.isActive()) finishRecording();
        if (VideoRecorderService.isRecording()) VideoRecorderService.stop(this);
        if (was) toast("Oprire de urgență");
    }

    /** Our settings screens call this so targets do not cover them. */
    private boolean codeEditorFront;

    /** The code editor is on screen: the floating panel hides (it covered the keyboard), and comes back after. */
    public void codeEditorInFront(boolean front) {
        if (front == codeEditorFront || panel == null) return;
        codeEditorFront = front;
        if (script != null || picking || capture != null) return;
        if (!panel.isShown()) return;
        panel.setHidden(front || panelHiddenByUser);
        if (!front && prefs.consoleOpen() && !console.isShown()) console.show();
        else if (front && console.isShown()) console.detach();
    }

    public void onOptionsScreen(boolean visible) {
        optionsScreens = Math.max(0, optionsScreens + (visible ? 1 : -1));
        updateMarkerVisibility();
    }

    // ================================================================ workspace helpers
    private void presentWorkspace() {
        Macro w = workspace();
        if (w == null) return;
        cancelWaiting();
        dismissDialog();
        updateScreen();
        ws.rescaleToScreen();
        if (!panel.isShown()) panel.show();
        panel.setMode(modeOf(w.kind));
        panel.setState(FloatingPanel.State.IDLE);
        panel.setSpeed(w.settings.speed);
        markers.setInteractive(true);
        panelHiddenByUser = false;
        panel.setHidden(false);
        if (prefs.consoleOpen()) console.show();
        syncMarkers();
        QuickTileService.requestUpdate(this);
        notifyListeners();
        updateNotification();
    }

    private void updateScreen() {
        Point sz = Overlays.screenSize(this);
        ws.setScreen(sz.x, sz.y);
    }

    /** Rebuild the on-screen points of the current work (none for combinations or if hidden in settings). */
    private void syncMarkers() {
        Macro w = workspace();
        boolean show = w != null && !Macro.KIND_COMBO.equals(w.kind) && prefs.pointsVisible(w.kind);
        markers.sync(show ? ActionTree.leaves(w.actions) : new ArrayList<Action>());
        updateMarkerVisibility();
    }

    /** Points are hidden while recording, picking a colour, during safe/colour runs and under our settings screens. */
    private void updateMarkerVisibility() {
        markers.setVisible(optionsScreens == 0 && !hiddenForRun && !recording.isActive() && !picking && !panelHiddenByUser && armed == null && !turboOn());
    }

    // ================================================================ 🌙 AFK mode (2.2.1)
    private AfkGuard.Host afkHost() {
        return new AfkGuard.Host() {
            public boolean afkRunning() {
                return (engine != null && engine.state() == PlaybackEngine.State.RUNNING && !engine.isPreview())
                        || (script != null && !script.runner.isPaused());
            }
            public void afkPause() {
                if (engine != null && engine.state() == PlaybackEngine.State.RUNNING) engine.pause(PlaybackEngine.PAUSE_AFK);
                else if (script != null && !script.runner.isPaused()) script.runner.pause(com.freeclick.core.script.ScriptRunner.WHY_AFK);
            }
            public void afkResume() {
                if (engine != null && engine.state() == PlaybackEngine.State.PAUSED && PlaybackEngine.PAUSE_AFK.equals(engine.pauseReason())) engine.resume();
                if (script != null && script.runner.isPaused() && com.freeclick.core.script.ScriptRunner.WHY_AFK.equals(script.runner.pauseReason())) script.runner.resume();
            }
            public void afkStopForRestart() {
                if (script != null) stopScript();
                if (isPlaying()) engine.stop();
                cancelWaiting();
            }
            public void afkRestart(Macro m, boolean isScript, boolean isWorkspace) {
                if (recording.isActive() || capture != null || picking || isPlaying() || script != null) return;
                if (isScript) { runScript(m, false, null, true); return; }
                if (!panel.isShown()) { updateScreen(); ws.ensureMode(); presentWorkspace(); }
                boolean same = isWorkspace && workspace() != null && m.id.equals(workspace().id);
                runMacro = same ? null : deepCopy(m);
                runPreview = false;
                runFromIndex = 0;
                returnToEditor = false;
                beginPlayback();
            }
            public void afkEnd() {
                if (script != null) stopScript();
                if (isPlaying()) engine.stop();
                cancelWaiting();
            }
            public String afkForeground() { return foregroundPkg; }
            public void afkSessionChanged() { updateNotification(); }
            public void toast(String s) { ClickService.this.toast(s); }
            public void log(String s) { addLog(s); }
        };
    }

    /** Settings → 🌙 changed (e.g. the dim screen). */
    public void onAfkSettingChanged() { if (afk != null) afk.settingsChanged(); }

    // ================================================================ 🔥 Turbo (2.2)
    private boolean turboOn() { return turbo != null && turbo.isOn(); }

    @Override public void onTurbo() {
        if (turboOn()) { turbo.stop(); return; }
        if (isPlaying() || script != null || recording.isActive() || capture != null || picking) {
            toast("Oprește mai întâi ce rulează");
            return;
        }
        turbo.start();
    }

    /** While Turbo is on the panel and the points step aside (they would catch the taps). */
    private void turboChanged(boolean on) {
        if (panel != null && panel.isShown()) panel.setHidden(on || panelHiddenByUser);
        updateMarkerVisibility();
        if (on && !prefs.sectionOpen("turbo.hint.v1")) {
            prefs.setSectionOpen("turbo.hint.v1", true);
            toast("Turbo: atinge ca să dai rafala acolo. × sus îl oprește.");
        }
    }

    /** Gestures with points off-screen are refused by Android: keep every point on the display. */
    private GesturePlan clampToScreen(GesturePlan plan) {
        Point sz = Overlays.screenSize(this);
        for (GesturePlan.PlannedStroke s : plan.strokes) {
            for (float[] p : s.points) {
                p[0] = Math.max(0, Math.min(sz.x - 1, p[0]));
                p[1] = Math.max(0, Math.min(sz.y - 1, p[1]));
            }
        }
        return plan;
    }

    private void closeEverything() {
        if (turboOn()) turbo.stop();
        cancelWaiting();
        dismissDialog();
        removePicker();
        picking = false;
        if (sampler != null) sampler.stop();
        if (VideoRecorderService.isCapturingPixels()) VideoRecorderService.stop(this);
        if (recording != null && recording.isActive()) recording.stop();
        if (markers != null) markers.removeAll();
        if (console != null) console.detach();
        if (feedback != null) feedback.hide();
        if (panel != null) panel.hide();
        if (undoBar != null) undoBar.hide();
        if (notif != null) notif.cancel();
        removeReadyBar();
        if (capture != null) endCapture();
    }

    private static Macro deepCopy(Macro m) { return MacroCodec.fromJson(MacroCodec.toJson(m)); }

    // ================================================================ panel callbacks
    @Override public void onPlay() {
        if (afk != null) afk.userTookOver();
        if (script != null) { toggleScriptPause(); return; }
        if (armed != null) {
            Macro fresh = repo.find(armed.id); // the latest saved code
            runScript(fresh != null ? fresh : armed, armedStep, armedBreakpoints, true);
            return;
        }
        if (workspace() == null) return;
        runMacro = null;
        runPreview = false;
        runFromIndex = 0;
        returnToEditor = false;
        if (Macro.KIND_COMBO.equals(workspace().kind) && ActionTree.isEmpty(workspace().actions)) {
            toast("Nu ai selectat nicio combinație. Folosește „Combinații (Încarcă/Salvează)” din meniu.");
            return;
        }
        if (workspace().countEnabled() == 0) {
            toast(Macro.KIND_RECORD.equals(workspace().kind) ? "Înregistrează mai întâi ceva cu ●."
                    : "Adaugă mai întâi ținte cu +.");
            return;
        }
        int cd = prefs.startCountdown();
        markers.setInteractive(false);
        if (cd > 0) {
            countdownLeft = cd;
            awaitingStart = true;
            panel.setState(FloatingPanel.State.WAITING);
            main.post(countdownTick);
        } else {
            beginPlayback();
        }
    }

    /** Forget the options of a run that never started (refused, cancelled while waiting...). */
    private void resetRun() {
        runMacro = null;
        runPreview = false;
        runFromIndex = 0;
        returnToEditor = false;
    }

    private void cancelWaiting() {
        boolean was = awaitingStart;
        awaitingStart = false;
        if (was && !isPlaying()) resetRun();
        if (main != null) main.removeCallbacks(countdownTick);
        if (was && panel != null && panel.state() == FloatingPanel.State.WAITING) {
            panel.setState(FloatingPanel.State.IDLE);
            markers.setInteractive(true);
        }
    }

    /** What the run plays: a copy handed over by the editor, or the panel's work. */
    private Macro target() { return runMacro != null ? runMacro : workspace(); }

    /** ≡ → "Previzualizează": draw the touches of the panel's work without doing them. */
    public void previewWorkspace() {
        if (workspace() == null || isPlaying() || recording.isActive()) return;
        if (workspace().countEnabled() == 0) { toast("Nu e nimic de previzualizat."); return; }
        askPreviewAssumption(null, false);
    }

    /** Editor 👁: preview its (maybe unsaved) copy over the app below, then go back to the editor. */
    public void previewFromEditor(Macro edited) {
        if (edited == null || recording.isActive()) return;
        if (isPlaying()) engine.stop();
        if (!isPanelShown()) showPanel();
        askPreviewAssumption(edited, true);
    }

    /** Editor "Rulează de aici": play its copy from a step, then go back to the editor. */
    public void playFromEditor(Macro edited, int fromIndex) {
        if (edited == null || recording.isActive()) return;
        if (isPlaying()) engine.stop();
        if (!isPanelShown()) showPanel();
        runMacro = edited;
        runPreview = false;
        runFromIndex = Math.max(0, fromIndex);
        returnToEditor = true;
        beginPlayback();
    }

    private void askPreviewAssumption(final Macro m, final boolean fromEditor) {
        final Runnable go = new Runnable() { public void run() {
            runMacro = m;
            runPreview = true;
            runFromIndex = 0;
            returnToEditor = fromEditor;
            beginPlayback();
        } };
        MacroLibrary.InMemory lib = repo.snapshot();
        Macro t = m != null ? m : workspace();
        lib.add(t);
        if (!MacroGraph.usesScreenCapture(t, lib)) { runPreviewFound = true; go.run(); return; }
        // Without touching anything the screen never changes, so colour conditions cannot be
        // checked for real: the user says how to treat them.
        showDialog(new android.app.AlertDialog.Builder(Overlays.dialogContext(this))
                .setTitle("Previzualizare")
                .setMessage("Nimic nu se atinge de-adevăratelea, deci ecranul nu se schimbă. Cum tratez condițiile de culoare?")
                .setPositiveButton("CA GĂSITE", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { runPreviewFound = true; go.run(); }
                })
                .setNegativeButton("CA NEGĂSITE", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { runPreviewFound = false; go.run(); }
                })
                .create());
    }

    private void beginPlayback() {
        final Macro target = target();
        if (target == null) return;
        markers.setInteractive(false);
        if (runPreview) { startEngine(); return; } // nothing to capture, film or open for real
        MacroLibrary.InMemory lib = repo.snapshot();
        lib.add(target);
        if (!ScreenSampler.supported() && !MacroGraph.imageIds(target, lib).isEmpty()) {
            toast("Pașii cu imagini au nevoie de Android 11 sau mai nou.");
            backToIdle();
            return;
        }
        if (!ScreenSampler.supported() && MacroGraph.readsText(target, lib)) {
            toast("Citirea numerelor și a textului de pe ecran are nevoie de Android 11 sau mai nou.");
            backToIdle();
            return;
        }
        final boolean needsPixels = MacroGraph.usesScreenCapture(target, lib);
        final String launch = target.settings.launchPackage == null ? "" : target.settings.launchPackage.trim();
        final Runnable start = new Runnable() {
            @Override public void run() {
                if (launch.length() > 0) launchThenStart(launch); else startEngine();
            }
        };
        boolean wantVideo = target.settings.recordVideo && !VideoRecorderService.isActive();
        if (needsPixels && !ScreenSampler.supported() && !VideoRecorderService.isCapturingPixels()) {
            // Android 10 and older: colours can only be read through screen capture (asks permission).
            if (VideoRecorderService.isRecording()) {
                toast("Condițiile de culoare nu pot rula cât timp filmezi ecranul. Oprește filmarea.");
                backToIdle();
                return;
            }
            if (target.settings.recordVideo) toast("Filmarea e sărită: ecranul e folosit pentru condițiile de culoare.");
            waitForCapture(true, start);
        } else if (wantVideo) {
            videoForThisRun = true;
            waitForCapture(false, start);
        } else {
            start.run();
        }
    }

    /** Ask Android for screen capture, then continue (unless the user pressed stop meanwhile). */
    private void waitForCapture(boolean pixels, final Runnable then) {
        awaitingStart = true;
        panel.setState(FloatingPanel.State.WAITING);
        panel.setInfo(pixels ? "captură…" : "filmare…");
        Runnable cont = new Runnable() {
            @Override public void run() {
                main.postDelayed(new Runnable() { public void run() {
                    if (!awaitingStart) { // user pressed stop meanwhile: don't leave a video running
                        if (videoForThisRun) { VideoRecorderService.stop(ClickService.this); videoForThisRun = false; }
                        return;
                    }
                    awaitingStart = false;
                    then.run();
                } }, 400);
            }
        };
        if (pixels) VideoRecorderService.requestPixels(this, cont); else VideoRecorderService.requestStart(this, cont);
    }

    /** Open the target app first, give it time to come up, then play. */
    private void launchThenStart(String pkg) {
        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) {
            toast("Aplicația „" + pkg + "” nu e instalată sau nu poate fi deschisă.");
            backToIdle();
            return;
        }
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(li);
        } catch (RuntimeException e) {
            toast("Nu pot deschide " + pkg);
            backToIdle();
            return;
        }
        awaitingStart = true;
        panel.setState(FloatingPanel.State.WAITING);
        panel.setInfo("deschid…");
        main.postDelayed(new Runnable() { public void run() {
            if (!awaitingStart) return;
            awaitingStart = false;
            startEngine();
        } }, 2500);
    }

    private void backToIdle() {
        awaitingStart = false;
        resetRun();
        if (videoForThisRun) { VideoRecorderService.stop(this); videoForThisRun = false; }
        panel.setState(FloatingPanel.State.IDLE);
        markers.setInteractive(true);
    }

    private void startEngine() {
        Macro target = target();
        if (target == null) return;
        panel.setPreview(runPreview);
        panel.setState(FloatingPanel.State.PLAYING);
        panel.setSpeed(target.settings.speed);
        MacroLibrary.InMemory lib = repo.snapshot();
        lib.add(target);
        colorRun = !runPreview && MacroGraph.usesScreenCapture(target, lib);
        // Targets and touch ripples must not cover what colour conditions look at.
        hiddenForRun = !runPreview && (target.settings.safeMode || colorRun);
        if (hiddenForRun) feedback.hide();
        updateMarkerVisibility();
        panel.setCompact(colorRun);
        if (colorRun) {
            if (ScreenSampler.supported()) sampler.start();
            warnIfPanelCoversConditions();
            main.removeCallbacks(statusTick);
            main.postDelayed(statusTick, 1000);
        }
        if (runMacro == null && !runPreview && isWorkspaceSaved()) prefs.setLastMacroId(target.id);
        log.clear();
        addLog((runPreview ? "Previzualizare „" : "Start „") + target.name + "” · viteză " + FloatingPanel.formatSpeed(target.settings.speed)
                + (runFromIndex > 0 ? " · de la pasul " + (runFromIndex + 1) : ""));
        if (runPreview) {
            panel.setInfo("👁");
            String launch = target.settings.launchPackage == null ? "" : target.settings.launchPackage.trim();
            if (launch.length() > 0) feedback.showLabel("Ar deschide întâi „" + appLabel(launch) + "”");
        }
        engine.setPauseOnCancel(prefs.pauseOnTouch());
        engine.setPreview(runPreview, runPreviewFound);
        consoleAutoOpened = false;
        consoleOverlapWarned = false;
        if (prefs.consoleSteps()) console.add((runPreview ? "👁 Previzualizare „" : "▶ Start „") + target.name + "”", PlaybackEngine.PRINT_INFO);
        if (!runPreview) afk.onRunStarted(deepCopy(target), false, runMacro == null, foregroundPkg);
        Macro play = target;
        if (target.settings.syncTaps && runFromIndex == 0) { // 🖐 taps one after another, all at the same moment
            play = deepCopy(target);
            List<Action> merged = MacroTransform.syncTaps(play.actions);
            play.actions.clear(); // tree-ok: replaced by the merged list (blocks merged inside)
            play.actions.addAll(merged);
        }
        tapRate.reset();
        engine.start(play, lib, runFromIndex);
    }

    /** While an image is being looked for, the panel says so: "🔍 Collect · 3 s" (never looks stuck). */
    private final Runnable statusTick = new Runnable() {
        @Override public void run() {
            if (engine == null || engine.state() == PlaybackEngine.State.FINISHED || !colorRun) return;
            Action a = engine.imageBeingSearched();
            if (a != null && engine.state() == PlaybackEngine.State.RUNNING && a.img != null) {
                String n = a.img.name == null || a.img.name.trim().isEmpty() ? "imagine" : a.img.name.trim();
                panel.setInfo("🔍 " + n + " · " + (engine.imageSearchElapsed() / 1000) + " s");
            }
            Action r = engine.readBeingDone();
            if (r != null && engine.state() == PlaybackEngine.State.RUNNING) {
                panel.setInfo("📖 " + (r.varName == null || r.varName.isEmpty() ? "citesc" : r.varName));
            }
            main.postDelayed(this, 1000);
        }
    };

    /** Name of an installed app (or the package if unknown). */
    String appLabel(String pkg) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    @Override public void onPauseToggle() {
        if (afk != null) afk.userTookOver();
        if (script != null) { toggleScriptPause(); return; }
        engine.togglePause();
    }

    @Override public void onSkip() { engine.skipWait(); }

    @Override public void onStop() {
        if (afk != null) afk.userTookOver();
        if (autoRun != null) { main.removeCallbacks(autoRun); autoRun = null; toast("Pornirea automată anulată"); return; }
        if (script != null) { stopScript(); return; }
        if (awaitingStart) {
            cancelWaiting();
            if (videoForThisRun) { VideoRecorderService.stop(this); videoForThisRun = false; }
            return;
        }
        engine.stop();
    }

    @Override public void onSpeedMenu(View anchor) {
        final List<String> labels = new ArrayList<String>();
        for (double s : SPEEDS) labels.add(FloatingPanel.formatSpeed(s));
        showPopup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int i) { setSpeed(SPEEDS[i]); }
        });
    }

    private void setSpeed(double s) {
        engine.setSpeed(s);
        if (workspace() != null) ws.setSpeed(s);
        panel.setSpeed(s);
        addLog("Viteză " + FloatingPanel.formatSpeed(s));
    }

    /** Next preset above/below the current speed (works for custom speeds between presets too). */
    static double nextSpeed(double cur, int direction) {
        if (direction > 0) {
            for (double s : SPEEDS) if (s > cur + 1e-9) return s;
            return SPEEDS[SPEEDS.length - 1];
        }
        for (int i = SPEEDS.length - 1; i >= 0; i--) if (SPEEDS[i] < cur - 1e-9) return SPEEDS[i];
        return SPEEDS[0];
    }

    // ---------------------------------------------------------------- manual mode
    @Override public void onAddMenu(View anchor) {
        disarmScript(true); // "+" works on the panel's own targets again
        final List<String> labels = new ArrayList<String>();
        final List<Integer> icons = new ArrayList<Integer>();
        final List<Runnable> actions = new ArrayList<Runnable>();
        add(labels, icons, actions, "Adaugă tap", android.R.drawable.ic_menu_add, new Runnable() { public void run() { addTarget(0); } });
        add(labels, icons, actions, "Adaugă swipe", android.R.drawable.ic_menu_directions, new Runnable() { public void run() { addTarget(1); } });
        add(labels, icons, actions, "Adaugă zoom (două degete)", android.R.drawable.ic_menu_zoom, new Runnable() { public void run() { addZoomTarget(); } });
        add(labels, icons, actions, "Adaugă apăsare lungă", android.R.drawable.ic_menu_recent_history, new Runnable() { public void run() { addTarget(2); } });
        add(labels, icons, actions, "Așteaptă o culoare (pipetă)", android.R.drawable.ic_menu_view, new Runnable() { public void run() { addColorWait(); } });
        if (ScreenSampler.supported()) {
            add(labels, icons, actions, "Imagine: apasă când apare", android.R.drawable.ic_menu_gallery, new Runnable() { public void run() { addImageTarget(); } });
            add(labels, icons, actions, "Citește număr / text din zonă", android.R.drawable.ic_menu_crop, new Runnable() { public void run() { addReadZone(); } });
        }
        add(labels, icons, actions, "Citește culoarea unui punct", android.R.drawable.ic_menu_search, new Runnable() { public void run() { addReadColor(); } });
        add(labels, icons, actions, "Afișează în consolă", android.R.drawable.ic_menu_edit, new Runnable() { public void run() { addTextStep(true); } });
        add(labels, icons, actions, "Setează o variabilă (calcule)", android.R.drawable.ic_menu_manage, new Runnable() { public void run() { addTextStep(false); } });
        showPopup(anchor, labels, icons, new Ui.OnPick() {
            @Override public void onPick(int i) { actions.get(i).run(); }
        });
    }

    private void addTarget(int which) {
        if (workspace() == null) return;
        updateScreen();
        ws.addTarget(which == 1 ? Workspace.Target.SWIPE : which == 2 ? Workspace.Target.LONG_PRESS : Workspace.Target.TAP);
        syncMarkers();
        if (!prefs.pointsVisible(workspace().kind)) toast("Ținta a fost adăugată (punctele sunt ascunse din setări).");
    }

    /** "+ → Zoom": two fingers at the centre of the screen (drag the marker to move them). */
    private void addZoomTarget() {
        if (workspace() == null) return;
        updateScreen();
        String[] items = {"Zoom în (degetele se depărtează)", "Zoom out (degetele se apropie)"};
        showDialog(new android.app.AlertDialog.Builder(Overlays.dialogContext(this)).setTitle("Zoom cu două degete")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        android.graphics.Point sz = Overlays.screenSize(ClickService.this);
                        Action z = Action.zoom(sz.x / 2f, sz.y / 2f, Math.round(Math.min(sz.x, sz.y) * 0.45f), 400, which == 0);
                        z.delay = workspace().settings.tapInterval;
                        ws.add(z);
                        syncMarkers();
                        toast("Mută marcajul în centrul zonei de mărit; ține apăsat pe el pentru distanță și durată");
                    }
                }).create());
    }

    /** "−": remove the last target / recorded action. */
    @Override public void onRemoveLast() {
        if (workspace() == null) return;
        Action a = ws.removeLast();
        if (a == null) { toast("Nu e nimic de șters."); return; }
        syncMarkers();
        int left = ActionTree.count(workspace().actions);
        panel.setInfo(left == 0 ? "" : String.valueOf(left));
        offerUndo("Șters: " + ActionFormatter.typeName(a.type).toLowerCase(Locale.ROOT));
        main.postDelayed(new Runnable() { public void run() {
            if (panel.state() == FloatingPanel.State.IDLE) panel.setInfo("");
        } }, 1200);
    }

    /**
     * Long-press on "−" or ≡ → "Șterge tot": remove every point / action. No "are you sure?":
     * a bar offers ANULEAZĂ for 5 s (and ≡ → Anulează works later too). Saved items are untouched.
     */
    @Override public void onClearAll() {
        final Macro w = workspace();
        if (w == null) return;
        if (ActionTree.isEmpty(w.actions)) { toast("Nu e nimic de șters."); return; }
        int n = ws.clearAll();
        syncMarkers();
        panel.setInfo("");
        offerUndo((Macro.KIND_MANUAL.equals(w.kind) ? "Șterse " + n + " ținte" : "Șterse " + n + " acțiuni")
                + " (salvările rămân)");
    }

    private void offerUndo(String message) {
        undoBar.show(message, new Runnable() { public void run() { undoLast(); } });
    }

    private void undoLast() {
        if (isPlaying() || recording.isActive()) return;
        String what = ws.undo();
        if (what == null) { toast("Nu e nimic de anulat."); return; }
        syncMarkers();
        toast("Anulat: " + what);
    }

    private void redoLast() {
        if (isPlaying() || recording.isActive()) return;
        String what = ws.redo();
        if (what == null) return;
        syncMarkers();
        toast("Refăcut: " + what);
    }

    /** ≡ → "Ascunde / Arată punctele" for the current mode (remembered). */
    private void togglePoints() {
        Macro w = workspace();
        if (w == null) return;
        boolean now = !prefs.pointsVisible(w.kind);
        prefs.setPointsVisible(w.kind, now);
        syncMarkers();
        toast(now ? "Punctele sunt vizibile" : "Punctele sunt ascunse (macro-ul rulează la fel)");
    }

    // ---------------------------------------------------------------- record mode
    @Override public void onRecordToggle() {
        if (recording.isActive()) { finishRecording(); return; }
        if (workspace() == null) return;
        disarmScript(true);
        if (isPlaying()) engine.stop();
        if (ws.recordingWouldReplaceUnsaved()) {
            showDialog(new android.app.AlertDialog.Builder(Overlays.dialogContext(this))
                    .setTitle("Înregistrare nouă?")
                    .setMessage("Înregistrarea curentă nu e salvată și va fi înlocuită. "
                            + "(Ca s-o păstrezi: ≡ → Opțiuni → Salvează ca.)")
                    .setPositiveButton("ÎNREGISTREAZĂ", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int w) { startRecording(); }
                    })
                    .setNegativeButton("ANULEAZĂ", null)
                    .create());
            return;
        }
        startRecording();
    }

    private void startRecording() {
        if (workspace() == null) return;
        // A new recording starts a new "Nesalvat" (a saved one is never overwritten by accident).
        updateScreen();
        ws.beginRecording();
        syncMarkers();
        notifyListeners();
        markers.setInteractive(false);
        recording.start();
        updateMarkerVisibility();
        panel.bringToFront();
        panel.setState(FloatingPanel.State.RECORDING);
        panel.setInfo("0");
        updateNotification();
    }

    private void finishRecording() {
        List<Action> recorded = recording.stop();
        if (workspace() != null && ws.addRecorded(recorded) > 0) {
            toast(recorded.size() + " acțiuni înregistrate. Salvează din ≡ → Opțiuni.");
        }
        syncMarkers();
        markers.setInteractive(true);
        if (panel.isShown()) {
            panel.setState(FloatingPanel.State.IDLE);
        }
        updateNotification();
    }

    @Override public void onGestureRecorded(Action a) {
        panel.setInfo(String.valueOf(recording.count()));
        GesturePlanner.Limits lim = limits();
        GesturePlan plan = GesturePlanner.plan(a, 1.0, 0, null, CoordinateScaler.IDENTITY, lim);
        if (plan == null) return;
        recording.setForwarding(true);
        final boolean pass = panel.intersects(plan);
        if (pass) panel.setTouchable(false);
        final GesturePlan p = plan;
        // Give the window manager a moment to apply "not touchable" before injecting.
        main.postDelayed(new Runnable() {
            @Override public void run() {
                inject(p, new PlaybackEngine.GestureCallback() {
                    @Override public void onResult(boolean completed) {
                        recording.setForwarding(false);
                        if (pass) panel.setTouchable(true);
                    }
                });
            }
        }, 50);
    }

    @Override public void onGlobalDuringRecording(int globalAction) {
        if (recording.addGlobal(globalAction) != null) panel.setInfo(String.valueOf(recording.count()));
        performGlobal(globalAction);
    }

    @Override public void onPanelMoved() { }

    // ---------------------------------------------------------------- ≡ menu
    @Override public void onMenu(final View anchor) {
        if (workspace() == null) return;
        final List<String> labels = new ArrayList<String>();
        final List<Integer> icons = new ArrayList<Integer>();
        final List<Runnable> actions = new ArrayList<Runnable>();
        boolean combo = Macro.KIND_COMBO.equals(workspace().kind);
        // the 3 that matter most; everything else under "Avansat"
        if (armed != null) {
            final String id = armed.id;
            add(labels, icons, actions, "Codul „" + armed.name + "”", android.R.drawable.ic_menu_edit, new Runnable() { public void run() {
                startActivity(new Intent(ClickService.this, CodeEditorActivity.class).putExtra(CodeEditorActivity.EXTRA_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } });
        } else if (combo) {
            add(labels, icons, actions, "Combinații (Încarcă/Salvează)", android.R.drawable.ic_menu_preferences,
                    new Runnable() { public void run() { openLibrary(Macro.KIND_COMBO); } });
        } else {
            add(labels, icons, actions, "Opțiuni (Încarcă/Salvează)", android.R.drawable.ic_menu_preferences,
                    new Runnable() { public void run() { openOptions(); } });
        }
        add(labels, icons, actions, "Arată aplicația", android.R.drawable.ic_menu_info_details,
                new Runnable() { public void run() { showApp(); } });
        add(labels, icons, actions, "Oprește", android.R.drawable.ic_lock_power_off,
                new Runnable() { public void run() { hidePanel(); } });
        add(labels, icons, actions, "Avansat  ›", android.R.drawable.ic_menu_more,
                new Runnable() { public void run() {
                    main.post(new Runnable() { public void run() { advancedMenu(anchor); } });
                } });
        showPopup(anchor, labels, icons, new Ui.OnPick() {
            @Override public void onPick(int i) { actions.get(i).run(); }
        });
    }

    /** ≡ → Avansat: the rest of the panel's menu. */
    private void advancedMenu(View anchor) {
        if (workspace() == null) return;
        final List<String> labels = new ArrayList<String>();
        final List<Integer> icons = new ArrayList<Integer>();
        final List<Runnable> actions = new ArrayList<Runnable>();
        boolean combo = Macro.KIND_COMBO.equals(workspace().kind);
        if (ws.canUndo()) {
            add(labels, icons, actions, "Anulează: " + ws.undoLabel(), android.R.drawable.ic_menu_revert,
                    new Runnable() { public void run() { undoLast(); } });
        }
        if (ws.canRedo()) {
            add(labels, icons, actions, "Refă: " + ws.redoLabel(), android.R.drawable.ic_menu_rotate,
                    new Runnable() { public void run() { redoLast(); } });
        }
        if (armed != null) {
            add(labels, icons, actions, "Scoate scriptul de pe panou", android.R.drawable.ic_menu_close_clear_cancel,
                    new Runnable() { public void run() { disarmScript(true); } });
            add(labels, icons, actions, combo ? "Combinații (Încarcă/Salvează)" : "Opțiuni (Încarcă/Salvează)", android.R.drawable.ic_menu_preferences,
                    new Runnable() { public void run() { disarmScript(false); openOptions(); } });
        }
        if (combo) {
            add(labels, icons, actions, "Editează combinația curentă", android.R.drawable.ic_menu_edit,
                    new Runnable() { public void run() { openCurrentInEditor(); } });
        }
        add(labels, icons, actions, "Previzualizează (fără atingeri)", android.R.drawable.ic_menu_view,
                new Runnable() { public void run() { previewWorkspace(); } });
        if (!combo) {
            add(labels, icons, actions, prefs.pointsVisible(workspace().kind) ? "Ascunde punctele" : "Arată punctele",
                    android.R.drawable.ic_menu_mylocation, new Runnable() { public void run() { togglePoints(); } });
        }
        add(labels, icons, actions, console.isShown() ? "Ascunde consola" : "Consolă", android.R.drawable.ic_menu_info_details,
                new Runnable() { public void run() { console.toggle(); console.setRunning(isPlaying()); } });
        add(labels, icons, actions, "Pipetă: află o culoare", android.R.drawable.ic_menu_search,
                new Runnable() { public void run() { colorTool(); } });
        final Macro codeScript = ScreenSampler.supported() ? repo.find(CodeEditorActivity.lastScriptId()) : null;
        if (codeScript != null && Macro.KIND_SCRIPT.equals(codeScript.kind)) {
            add(labels, icons, actions, "Captură pentru codul „" + codeScript.name + "”", android.R.drawable.ic_menu_camera,
                    new Runnable() { public void run() { shotForScript(codeScript); } });
        }
        add(labels, icons, actions, VideoRecorderService.isRecording() ? "Oprește filmarea" : "Filmează ecranul",
                android.R.drawable.ic_menu_camera, new Runnable() { public void run() {
                    if (VideoRecorderService.isRecording()) VideoRecorderService.stop(ClickService.this);
                    else VideoRecorderService.requestStart(ClickService.this, null);
                } });
        add(labels, icons, actions, "Jurnal activitate", android.R.drawable.ic_menu_agenda,
                new Runnable() { public void run() { showLog(); } });
        if (prefs.notificationControls() && notif.isDismissed()) {
            add(labels, icons, actions, "Arată notificarea", android.R.drawable.ic_popup_reminder,
                    new Runnable() { public void run() { notif.undismiss(); updateNotification(); } });
        }
        add(labels, icons, actions, "Șterge tot", android.R.drawable.ic_menu_delete,
                new Runnable() { public void run() { onClearAll(); } });
        showPopup(anchor, labels, icons, new Ui.OnPick() {
            @Override public void onPick(int i) { actions.get(i).run(); }
        });
    }

    private static void add(List<String> l, List<Integer> i, List<Runnable> a, String label, int icon, Runnable r) {
        l.add(label);
        i.add(icon);
        a.add(r);
    }

    private void openOptions() {
        ws.persist();
        startActivity(new Intent(this, ModeSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    private void openLibrary(String kind) {
        startActivity(new Intent(this, LibraryActivity.class).putExtra(LibraryActivity.EXTRA_KIND, kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    private void openCurrentInEditor() {
        if (!isWorkspaceSaved()) {
            toast("Nu ai selectat nicio combinație. Folosește „Combinații (Încarcă/Salvează)”.");
            return;
        }
        startActivity(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_ID, workspace().id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    private void showApp() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    // ================================================================ targets
    @Override public void onMarkerTapped(final Action a) {
        if (workspace() == null) return;
        MacroLibrary.InMemory lib = repo.snapshot();
        lib.add(workspace());
        final Runnable reopen = new Runnable() { public void run() { syncMarkers(); onMarkerTapped(a); } };
        final ActionEditForm form = new ActionEditForm(Overlays.dialogContext(this), a, workspace(), repo.list(), lib,
                new ActionEditForm.ScreenTools() {
                    @Override public void testImage(final Action draft) {
                        markDirty();
                        dismissDialog();
                        ClickService.this.testImage(draft, reopen);
                    }

                    @Override public void recaptureImage(final Action draft) {
                        markDirty();
                        dismissDialog();
                        ClickService.this.recaptureImage(draft, reopen);
                    }

                    @Override public void pickColor(final Action draft) {
                        markDirty();
                        dismissDialog();
                        startPick(ImageCaptureView.MODE_COLOR, new Picked() {
                            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                                draft.x = r.left; draft.y = r.top;
                                if (draft.type == Action.Type.WAIT_COLOR) draft.color = rgb;
                                else { draft.x2 = draft.x; draft.y2 = draft.y; }
                                markDirty();
                            }
                        }, reopen);
                    }

                    @Override public void pickZone(final Action draft) {
                        markDirty();
                        dismissDialog();
                        startPick(ImageCaptureView.MODE_ZONE, new Picked() {
                            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                                draft.x = r.left; draft.y = r.top; draft.x2 = r.right; draft.y2 = r.bottom;
                                markDirty();
                            }
                        }, reopen);
                    }

                    @Override public void testRead(final Action draft) {
                        markDirty();
                        dismissDialog();
                        ClickService.this.testRead(draft, workspace(), reopen);
                    }
                }, true);
        final int idx = ActionTree.positionOf(workspace().actions, a);
        showDialog(new android.app.AlertDialog.Builder(Overlays.dialogContext(this))
                .setTitle("Ținta " + idx + " · " + ActionFormatter.typeName(a.type))
                .setView(form.view())
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        form.apply();
                        markDirty();
                    }
                })
                .setNeutralButton("Șterge", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        ws.remove(a);
                        syncMarkers();
                        offerUndo("Ținta " + idx + " ștearsă");
                    }
                })
                .setNegativeButton("Anulează", null)
                .create());
    }

    @Override public void onMarkerMoved(Action a) { ws.markDirty(); }

    // ================================================================ engine listener
    @Override public void onStateChanged(PlaybackEngine.State state) {
        if (panel == null) return;
        if (script != null && script.stepsDone != null) { // 'ruleaza(pasi)' inside a script: the script owns the panel
            if (state == PlaybackEngine.State.PAUSED) panel.setState(FloatingPanel.State.PAUSED);
            else if (state == PlaybackEngine.State.RUNNING) panel.setState(FloatingPanel.State.PLAYING);
            return;
        }
        if (console != null) console.setRunning(state == PlaybackEngine.State.RUNNING);
        if (afk != null && !engine.isPreview() && state != PlaybackEngine.State.FINISHED) {
            String why = engine.pauseReason();
            afk.onRunState(state == PlaybackEngine.State.RUNNING, state == PlaybackEngine.State.PAUSED
                    && (PlaybackEngine.PAUSE_USER.equals(why) || PlaybackEngine.PAUSE_TOUCH.equals(why)));
        }
        switch (state) {
            case RUNNING: panel.setState(FloatingPanel.State.PLAYING); break;
            case PAUSED:
                panel.setState(FloatingPanel.State.PAUSED);
                if (PlaybackEngine.PAUSE_TOUCH.equals(engine.pauseReason())) {
                    toast("Pauză: ai atins ecranul. Apasă ▶ ca să continui (setarea e în Setări → Siguranță).");
                    addLog("Pauză: atingere pe ecran");
                }
                break;
            case FINISHED:
                main.removeCallbacks(statusTick);
                if (panel.isShown()) {
                    panel.setHidden(panelHiddenByUser);
                    panel.setTouchable(true);
                    panel.setPreview(false);
                    panel.setCompact(false);
                    panel.setState(FloatingPanel.State.IDLE);
                }
                hiddenForRun = false;
                updateMarkerVisibility();
                markers.setInteractive(true);
                break;
            default: break;
        }
        QuickTileService.requestUpdate(this);
        notifyListeners();
        updateNotification();
    }

    @Override public void onProgress(int loop, int totalLoops, int step, int totalSteps, String macroName, Action action) {
        if (afk != null && !engine.isPreview()) afk.onLoop(loop);
        addLog("[" + loop + "] " + macroName + " " + step + "/" + totalSteps + ": " + ActionFormatter.describe(action, null));
        if (prefs.consoleSteps()) {
            console.add("[" + loop + "] " + step + "/" + totalSteps + " " + ActionFormatter.describe(action, null), PlaybackEngine.PRINT_INFO);
        }
        if (engine.isPreview()) {
            previewCaption = step + "/" + totalSteps + " · " + ActionFormatter.typeName(action.type);
            panel.setInfo("👁 " + step + "/" + totalSteps);
        } else {
            panel.setInfo(loop + "/" + (totalLoops == 0 ? "∞" : String.valueOf(totalLoops)) + rateSuffix());
        }
    }

    @Override public void onNotice(String code, Action action) {
        String pkg = action == null ? "" : action.packageName;
        if (PlaybackEngine.NOTICE_APP_MISSING.equals(code)) {
            toast("Nu pot deschide „" + pkg + "” (nu e instalată?). Continui cu pasul următor.");
            addLog("  ↪ aplicația " + pkg + " nu poate fi deschisă");
        } else if (PlaybackEngine.NOTICE_APP_SLOW.equals(code)) {
            addLog("  ↪ " + appLabel(pkg) + " nu a apărut la timp; continui");
        } else if (PlaybackEngine.NOTICE_IMAGE_COVERED.equals(code) && action != null && action.img != null) {
            toast("🔍 " + action.img.displayName() + " pare acoperită de panou. Mută panoul în altă parte.");
            addLog("  ↪ imaginea pare acoperită de panou");
        }
    }

    @Override public void onFinished(String reason) {
        if (script != null && script.stepsDone != null) {
            java.util.concurrent.CountDownLatch d = script.stepsDone;
            if (!PlaybackEngine.REASON_DONE.equals(reason) && !PlaybackEngine.REASON_STOPPED.equals(reason)) {
                print("⚠ ruleaza(pași) s-a oprit: " + reason, PlaybackEngine.PRINT_WARN);
            }
            d.countDown();
            return;
        }
        boolean wasPreview = runPreview;
        boolean backToEditor = returnToEditor;
        if (!wasPreview && afk != null) afk.onRunEnded();
        resetRun();
        engine.setPreview(false, true);
        updateNotification();
        if (backToEditor) {
            main.postDelayed(new Runnable() { public void run() {
                if (!isPlaying()) startActivity(new Intent(ClickService.this, EditorActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } }, 900);
        }
        if (wasPreview) {
            String m = PlaybackEngine.REASON_DONE.equals(reason) ? "Previzualizare gata ✅ (" + engine.executedSteps() + " pași; nimic nu a fost atins)"
                    : PlaybackEngine.REASON_CONDITION_TIMEOUT.equals(reason) ? "Previzualizare oprită: aici macro-ul ar aștepta culoarea (la nesfârșit sau până la limită)."
                    : PlaybackEngine.REASON_STOPPED.equals(reason) ? null : "Previzualizare oprită (" + reason + ")";
            addLog("Sfârșit previzualizare (" + reason + ")");
            if (m != null) toast(m);
            return;
        }
        if (videoForThisRun) {
            videoForThisRun = false;
            VideoRecorderService.stop(this);
        }
        if (colorRun) sampler.stop();
        int failed = engine.failedGestures();
        String msg = null;
        if (PlaybackEngine.REASON_DONE.equals(reason)) msg = "Gata ✅ (" + engine.executedSteps() + " pași)";
        else if (PlaybackEngine.REASON_TIME_LIMIT.equals(reason)) msg = "Oprit: s-a atins limita de timp";
        else if (PlaybackEngine.REASON_CYCLE.equals(reason)) msg = "Combinația se apelează pe sine (buclă). Verifică ce conține.";
        else if (PlaybackEngine.REASON_EMPTY.equals(reason)) msg = "Nu există acțiuni active de rulat.";
        else if (PlaybackEngine.REASON_TOO_DEEP.equals(reason)) msg = "Prea multe combinații una în alta.";
        else if (PlaybackEngine.REASON_CONDITION_TIMEOUT.equals(reason)) msg = "Oprit: culoarea așteptată nu a apărut la timp.";
        else if (PlaybackEngine.REASON_NO_CAPTURE.equals(reason)) msg = "Oprit: nu pot citi ecranul (pentru culori sau imagini).";
        else if (PlaybackEngine.REASON_PROTECTED.equals(reason)) msg = "Oprit: aplicația asta nu lasă ecranul să fie citit (e protejată), deci imaginile nu pot fi căutate aici.";
        else if (PlaybackEngine.REASON_IMAGE_MISSING.equals(reason)) msg = "Oprit: lipsește poza unui pas cu imagine. Deschide pasul și recapturează imaginea.";
        else if (PlaybackEngine.REASON_HIT_LIMIT.equals(reason)) msg = "Gata ✅: s-a atins numărul de găsiri cerut.";
        if (failed > 0) msg = (msg == null ? "" : msg + "\n") + failed + " gesturi au fost anulate de sistem (ex.: ai atins ecranul în timpul redării).";
        if (!engine.imageHits().isEmpty()) {
            StringBuilder hits = new StringBuilder();
            for (java.util.Map.Entry<Action, Integer> e : engine.imageHits().entrySet()) {
                if (hits.length() > 0) hits.append(", ");
                hits.append(e.getKey().img == null ? "imaginea" : e.getKey().img.displayName()).append(" găsită de ").append(e.getValue())
                        .append(e.getValue() == 1 ? " dată" : " ori");
            }
            msg = (msg == null ? "" : msg + "\n") + hits;
        } else if (engine.conditionsMet() + engine.conditionsTimedOut() > 0) {
            msg = (msg == null ? "" : msg + "\n") + "Condiții: " + engine.conditionsMet()
                    + " îndeplinite, " + engine.conditionsTimedOut() + " expirate.";
        }
        addLog("Sfârșit (" + reason + ")" + (msg == null ? "" : ": " + msg.replace("\n", " · ")));
        colorRun = false;
        if (msg != null) toast(msg);
    }

    // ================================================================ Host
    /** 2.4: real taps per second (what Android actually did), shown on the panel during fast runs. */
    private final TapRate tapRate = new TapRate();

    static final class TapRate {
        private final long[] at = new long[512];
        private int head, count;
        void reset() { head = 0; count = 0; }
        void add(int n, long t) {
            for (int i = 0; i < n; i++) { at[head] = t; head = (head + 1) % at.length; count = Math.min(at.length, count + 1); }
        }
        /** Taps in the last 2 s, per second. */
        int perSecond(long now) {
            int n = 0;
            for (int i = 0; i < count; i++) if (now - at[(head - 1 - i + at.length) % at.length] <= 2000) n++; else break;
            return Math.round(n / 2f);
        }
    }

    private String rateSuffix() {
        int r = tapRate.perSecond(SystemClock.uptimeMillis());
        return r >= 2 ? " · " + r + "/s" : "";
    }

    @Override public void dispatchGesture(final GesturePlan plan, final PlaybackEngine.GestureCallback cb) {
        if (engine.isPreview()) { // draw a ghost finger instead of touching
            feedback.showGhost(plan, previewCaption);
            main.postDelayed(new Runnable() { public void run() { cb.onResult(true); } }, plan.totalDuration() + 60);
            return;
        }
        Macro m = engine.macro();
        final boolean safe = m != null && m.settings.safeMode;
        final boolean pass = !safe && panel.isTouchable() && panel.intersects(plan);
        if (safe) panel.setHidden(true);
        if (pass) panel.setTouchable(false);
        Runnable go = new Runnable() {
            @Override public void run() {
                if (!isPlaying()) { // stopped during the settle delay: do not inject a stale gesture
                    if (pass) panel.setTouchable(true);
                    cb.onResult(false);
                    return;
                }
                if (!safe && !colorRun && prefs.showTouches()) feedback.show(plan);
                inject(plan, new PlaybackEngine.GestureCallback() {
                    @Override public void onResult(boolean completed) {
                        if (safe && isPlaying()) panel.setHidden(panelHiddenByUser);
                        if (pass) panel.setTouchable(true);
                        if (!completed && isPlaying()) addLog("  ↪ gest anulat de sistem");
                        if (completed) tapRate.add(plan.strokes.size(), SystemClock.uptimeMillis());
                        cb.onResult(completed);
                    }
                });
            }
        };
        if (safe || pass) main.postDelayed(go, safe ? 80 : 40); else go.run();
    }

    /** Convert a plan into a GestureDescription and inject it. The callback always fires once. */
    private void inject(GesturePlan plan, final PlaybackEngine.GestureCallback cb) {
        try {
            GestureDescription.Builder b = new GestureDescription.Builder();
            for (GesturePlan.PlannedStroke s : plan.strokes) {
                Path path = new Path();
                float[] first = s.points.get(0);
                path.moveTo(first[0], first[1]);
                for (int i = 1; i < s.points.size(); i++) path.lineTo(s.points.get(i)[0], s.points.get(i)[1]);
                b.addStroke(new GestureDescription.StrokeDescription(path, s.startTime, Math.max(1, s.duration)));
            }
            boolean accepted = dispatchGesture(b.build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) { cb.onResult(true); }
                @Override public void onCancelled(GestureDescription d) { cb.onResult(false); }
            }, main);
            if (!accepted) postResult(cb, false);
        } catch (RuntimeException e) { // IllegalArgument/IllegalState from a malformed gesture
            postResult(cb, false);
        }
    }

    private void postResult(final PlaybackEngine.GestureCallback cb, final boolean ok) {
        main.post(new Runnable() { public void run() { cb.onResult(ok); } });
    }

    @Override public boolean performGlobal(int action) {
        if (engine != null && engine.isPreview()) {
            feedback.showLabel(GlobalActions.name(action) + " (simulat)");
            return true;
        }
        if (Build.VERSION.SDK_INT < GlobalActions.minApi(action)) return false;
        // System UI opened by the macro itself must not trigger the "shade opened" pause.
        ignoreSystemUiUntil = SystemClock.uptimeMillis() + 1500;
        if (action == GlobalActions.NOTIFICATIONS || action == GlobalActions.QUICK_SETTINGS
                || action == GlobalActions.RECENTS || action == GlobalActions.POWER_DIALOG) systemUiOpenedByMacro = true;
        if (afk != null && (action == GlobalActions.HOME || action == GlobalActions.RECENTS || action == GlobalActions.BACK)) afk.runSwitched();
        return performGlobalAction(action);
    }

    @Override public boolean launchApp(String pkg) {
        if (engine != null && engine.isPreview()) {
            feedback.showLabel("Deschide „" + appLabel(pkg) + "” (simulat)");
            return true;
        }
        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return false;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (afk != null) afk.runOpened(pkg); // 🌙 an app the run opens itself is not "the game went away"
        try {
            startActivity(li);
            addLog("  ↪ deschid " + appLabel(pkg));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override public void postDelayed(Runnable r, long delayMs) { main.postDelayed(r, delayMs); }
    @Override public void removeCallbacks(Runnable r) { main.removeCallbacks(r); }
    @Override public long uptimeMillis() { return SystemClock.uptimeMillis(); }
    @Override public int screenWidth() { return Overlays.screenSize(this).x; }
    @Override public int screenHeight() { return Overlays.screenSize(this).y; }

    // ================================================================ image search (Host)
    private synchronized java.util.concurrent.ExecutorService matcher() {
        if (matchExec == null) {
            matchExec = java.util.concurrent.Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "freeclick-match");
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });
        }
        return matchExec;
    }

    /** Last search result (for the test overlay). */
    private volatile ImageMatcher.Result lastSearch;

    @Override public void findImage(final PlaybackEngine.ImageRequest req, final PlaybackEngine.ImageCallback cb) {
        final ImageSpec spec = req.spec;
        if (!ScreenSampler.supported()) { answerLater(cb, PlaybackEngine.IMG_NO_CAPTURE, 0); return; }
        if (spec == null || !images.has(spec.id)) { answerLater(cb, PlaybackEngine.IMG_MISSING, 0); return; }
        sampler.whenFrame(req.notBefore, new ScreenSampler.FrameCallback() {
            @Override public void onFrame(ScreenFrame f) {
                if (f == null) { answerLater(cb, PlaybackEngine.IMG_NO_CAPTURE, 0); return; }
                search(sampler.acquire(f), spec, req.all, cb);
            }
        });
    }

    private void answerLater(final PlaybackEngine.ImageCallback cb, int status, long frameTime) {
        final PlaybackEngine.ImageResult r = new PlaybackEngine.ImageResult();
        r.status = status;
        r.frameTime = frameTime;
        main.post(new Runnable() { public void run() { cb.onImage(r); } });
    }

    /** Search on the matcher thread; answer on the main thread. The frame is released afterwards. */
    private void search(final ScreenFrame fr, final ImageSpec spec, boolean all, final PlaybackEngine.ImageCallback cb) {
        final ImageMatcher.Query q = queryFor(spec, all, fr);
        final int top = statusBarHeight(), bottom = fr.height - navigationBarHeight();
        final double sx = spec.capScreenW > 0 ? fr.width / (double) spec.capScreenW : 1;
        final double sy = spec.capScreenH > 0 ? fr.height / (double) spec.capScreenH : 1;
        try {
            matcher().execute(new Runnable() {
                @Override public void run() {
                    final PlaybackEngine.ImageResult r = new PlaybackEngine.ImageResult();
                    r.frameTime = fr.time;
                    try {
                        ImageTemplate t0 = images.template(spec.id);
                        if (t0 == null) r.status = PlaybackEngine.IMG_MISSING;
                        else if (fr.looksProtected(top, bottom)) r.status = PlaybackEngine.IMG_PROTECTED;
                        else {
                            ImageTemplate t = t0.scaled(sx, sy);
                            ImageMatcher.Result res = ImageMatcher.find(fr, t, q);
                            lastSearch = res;
                            r.status = res.found() ? PlaybackEngine.IMG_FOUND : PlaybackEngine.IMG_NOT_FOUND;
                            for (ImageMatcher.Match m : res.matches) {
                                r.points.add(new float[]{(float) (m.x + spec.tapDx * sx), (float) (m.y + spec.tapDy * sy)});
                            }
                            r.best = res.best == null ? 0 : res.best.similarity();
                            r.covered = !res.found() && res.best != null && res.best.covered;
                        }
                    } catch (RuntimeException e) {
                        r.status = PlaybackEngine.IMG_NOT_FOUND;
                    } catch (OutOfMemoryError e) {
                        r.status = PlaybackEngine.IMG_NO_CAPTURE;
                    }
                    main.post(new Runnable() { public void run() {
                        sampler.releaseFrame(fr);
                        cb.onImage(r);
                    } });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            sampler.releaseFrame(fr);
        }
    }

    /** Search options for a spec on the current screen (zone scaled; our panel excluded). */
    ImageMatcher.Query queryFor(ImageSpec spec, boolean all, ScreenFrame fr) {
        ImageMatcher.Query q = new ImageMatcher.Query();
        q.precision = spec.precision;
        q.multiScale = spec.multiScale();
        q.all = all;
        q.useCore = !spec.tight;
        double sx = spec.capScreenW > 0 ? fr.width / (double) spec.capScreenW : 1;
        double sy = spec.capScreenH > 0 ? fr.height / (double) spec.capScreenH : 1;
        if (spec.hasZone()) {
            q.zx1 = (int) Math.floor(spec.zx1 * sx); q.zy1 = (int) Math.floor(spec.zy1 * sy);
            q.zx2 = (int) Math.ceil(spec.zx2 * sx); q.zy2 = (int) Math.ceil(spec.zy2 * sy);
        }
        int[] r = panel == null ? null : panel.screenRect();
        if (r != null) { q.ex1 = r[0]; q.ey1 = r[1]; q.ex2 = r[2]; q.ey2 = r[3]; }
        int[] cr = console == null ? null : console.screenRect();
        if (cr != null) { q.fx1 = cr[0]; q.fy1 = cr[1]; q.fx2 = cr[2]; q.fy2 = cr[3]; }
        return q;
    }

    int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }

    int navigationBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    @Override public int sampleColor(int x, int y, long notBefore) {
        if (ScreenSampler.supported()) return sampler.sample(x, y, notBefore);
        return VideoRecorderService.samplePixel(x, y);
    }

    // ================================================================ scripts (1.9)
    ScreenSampler sampler() { return sampler; }
    WindowManager windowManager() { return wm; }
    boolean prefsPauseOnTouch() { return prefs.pauseOnTouch(); }

    // ================================================================ 2.5: 🧪 the Laboratory
    /** The lab screen is in front: panel and points step aside so nothing catches the test taps. */
    public void labMode(boolean on) {
        onOptionsScreen(on);
        if (panel != null && panel.isShown()) panel.setHidden(on || panelHiddenByUser);
        if (!on && ears != null) ears.labLayer(false);
    }

    /** A test gesture, sent exactly like a run would send it. */
    public void labInject(GesturePlan plan, PlaybackEngine.GestureCallback cb) { inject(clampToScreen(plan), cb); }

    /** The forwarding layer (as for a listening script without direct listening), to measure it. */
    public void labLayer(boolean on) { if (ears != null) ears.labLayer(on); }

    // ================================================================ 2.5: a script's buttons and listening zone
    private ScriptButtons buttons;

    ScriptButtons scriptButtons() {
        if (buttons == null) buttons = new ScriptButtons(this, wm);
        return buttons;
    }

    void scriptListenZone(int[] z) { if (ears != null) ears.setZone(z); }

    interface Answer { void done(String text, int index); }

    /** intreaba(…): a question over the game; the answer (or null for Anulează) is given exactly once. */
    void scriptAsk(String q, String preset, boolean number, final Answer a) {
        final boolean[] once = {false};
        final android.widget.EditText e = new android.widget.EditText(Overlays.dialogContext(this));
        e.setText(preset == null ? "" : preset);
        e.setSelectAllOnFocus(true);
        e.setSingleLine(true);
        if (number) e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        android.widget.FrameLayout box = new android.widget.FrameLayout(Overlays.dialogContext(this));
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(e);
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(Overlays.dialogContext(this)).setTitle(q).setView(box)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface di, int w) { if (!once[0]) { once[0] = true; a.done(e.getText().toString(), 0); } }
                })
                .setNegativeButton("ANULEAZĂ", null).create();
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface di) { if (!once[0]) { once[0] = true; a.done(null, -1); } }
        });
        showDialog(d);
    }

    /** alege(…): a list over the game; the index (or -1) is given exactly once. */
    void scriptChoose(String title, List<String> options, final Answer a) {
        final boolean[] once = {false};
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(Overlays.dialogContext(this)).setTitle(title)
                .setItems(options.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface di, int w) { if (!once[0]) { once[0] = true; a.done(null, w); } }
                })
                .setNegativeButton("ANULEAZĂ", null).create();
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface di) { if (!once[0]) { once[0] = true; a.done(null, -1); } }
        });
        showDialog(d);
    }

    // ================================================================ 2.4: what a script shows and tells
    private android.widget.TextView hud;

    /** Text over the game; it cannot be touched (taps go through it). x < 0: top, in the middle; "" hides it. */
    void showHud(String text, int x, int y) {
        if (text == null || text.isEmpty()) { hideHud(); return; }
        if (hud == null) {
            hud = Ui.text(this, "", 15, 0xFFFFFFFF, true);
            hud.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            hud.setBackground(Ui.rounded(0xC0140F45, Ui.dp(this, 14)));
            hud.setMaxWidth(Overlays.screenSize(this).x - Ui.dp(this, 24));
        }
        hud.setText(text);
        WindowManager.LayoutParams lp = Overlays.params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false, false);
        if (x < 0) {
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            lp.y = statusBarHeight() + Ui.dp(this, 8);
        } else {
            lp.x = x;
            lp.y = y;
        }
        if (Overlays.isAdded(hud)) Overlays.safeUpdate(wm, hud, lp); else Overlays.safeAdd(wm, hud, lp);
    }

    void hideHud() {
        if (hud != null) Overlays.safeRemove(wm, hud);
    }

    /** notifica(…) from a script: a normal notification (needs the notification permission on Android 13+). */
    void scriptNotification(String title, String text) {
        if (!ControlNotification.scriptMessage(this, title, text)) {
            print("⚠ notifica(): telefonul nu lasă FreeClick să trimită notificări (Setări → Aplicații → FreeClick → Notificări)", PlaybackEngine.PRINT_WARN);
        }
    }

    // ================================================================ 👂 your fingers (2.3)
    TouchTracker touchTracker() { return ears.tracker; }

    /** A script listens to your fingers (or stops): the layer goes up only where Android cannot send copies. */
    void scriptListen(boolean on, boolean forward) { if (ears != null) ears.setListening(on, forward); }

    /** Android 14+ sends copies of touches here when asked (no @Override: older Androids do not know it). */
    public void onMotionEvent(MotionEvent e) {
        if (ears != null) ears.onMotionEvent(e);
    }

    /** Settings: allow Android's copies of touches for listening scripts (false if this phone cannot). */
    public boolean setTouchDirect(boolean on) { return ears != null && ears.allowDirect(this, on); }

    /** Settings → Testează: copies on now (true) and off again after (false); true if they could be turned on. */
    public boolean testTouchDirect(boolean on) { return ears != null && ears.testDirect(this, on); }

    public boolean touchDirect() { return ears != null && ears.isDirect(); }

    public long touchesHeard() { return ears == null ? 0 : ears.heard(); }
    public boolean isScriptRunning() { return script != null; }

    /**
     * Run a script (from the code editor, the list, a shortcut or a schedule). Returns false if it
     * cannot start (a mistake in the code is reported in the editor before this is called).
     */
    public boolean runScript(Macro m, boolean step, java.util.Set<Integer> breakpoints) { return runScript(m, step, breakpoints, false); }

    // ---- a script made ready from the code editor / library: ▶ on the panel starts it, in the game
    private Macro armed;
    private boolean armedStep;
    private java.util.Set<Integer> armedBreakpoints;

    /** The script ▶ on the panel will run (null: the panel's own targets / recording). */
    Macro armedScript() { return armed; }

    /**
     * "▶ Rulează" in the code editor: the script is put on the panel, not started. The user opens
     * the game and presses ▶ there (so it never starts over the editor or the home screen).
     */
    public boolean armScript(Macro m, boolean step, java.util.Set<Integer> breakpoints) {
        if (m == null) return false;
        com.freeclick.core.script.ScriptError err = com.freeclick.core.script.ScriptRunner.check(m.script);
        if (err != null) { toast("Scriptul are o greșeală: " + err.describe()); return false; }
        if (recording.isActive()) { toast("Oprește întâi înregistrarea."); return false; }
        if (script != null) stopScript();
        if (isPlaying()) engine.stop();
        armed = deepCopy(m);
        armedStep = step;
        armedBreakpoints = breakpoints;
        panelHiddenByUser = false;
        if (!panel.isShown()) presentWorkspace();
        panel.setHidden(false);
        panel.setInfo("📜");
        updateMarkerVisibility();
        toast("📜 „" + m.name + "” e pe panou" + (step ? " (pas cu pas)" : "") + ": deschide jocul și apasă ▶");
        notifyListeners();
        return true;
    }

    /** The panel goes back to its own targets / recording. */
    void disarmScript(boolean say) {
        if (armed == null) return;
        String name = armed.name;
        armed = null;
        if (panel != null) panel.setInfo("");
        updateMarkerVisibility();
        if (say) toast("Scriptul „" + name + "” nu mai e pe panou");
        notifyListeners();
    }

    public boolean runScript(Macro m, boolean step, java.util.Set<Integer> breakpoints, boolean fromEditor) {
        if (m == null) return false;
        if (recording.isActive()) { toast("Oprește întâi înregistrarea."); return false; }
        if (script != null) stopScript();
        if (isPlaying()) engine.stop();
        com.freeclick.core.script.ScriptError err = com.freeclick.core.script.ScriptRunner.check(m.script);
        if (err != null) {
            toast("Scriptul are o greșeală: " + err.describe());
            return false;
        }
        ScriptSession s;
        try {
            s = new ScriptSession(this, main, deepCopy(m), fromEditor, step, breakpoints);
        } catch (com.freeclick.core.script.ScriptError e) {
            toast("Scriptul are o greșeală: " + e.describe());
            return false;
        }
        script = s;
        dismissDialog();
        cancelWaiting();
        scriptShowedPanel = !panel.isShown();
        if (scriptShowedPanel) panel.show();
        panel.setHidden(false);
        panel.setPreview(false);
        panel.setCompact(true);
        panel.setState(FloatingPanel.State.PLAYING);
        panel.setInfo("📜");
        hiddenForRun = true;
        updateMarkerVisibility();
        markers.setInteractive(false);
        feedback.hide();
        if (ScreenSampler.supported() && com.freeclick.core.script.ScriptRunner.usesScreen(m.script)) sampler.start();
        consoleAutoOpened = false;
        consoleOverlapWarned = false;
        if (prefs.consoleOpen()) { console.show(); console.setRunning(true); }
        console.add("📜 Pornesc scriptul „" + m.name + "”" + (step ? " pas cu pas" : ""), PlaybackEngine.PRINT_INFO);
        addLog("Script „" + m.name + "”" + (step ? " (pas cu pas)" : ""));
        if (!step) afk.onRunStarted(deepCopy(m), true, false, foregroundPkg);
        s.start();
        QuickTileService.requestUpdate(this);
        notifyListeners();
        return true;
    }

    // ---- "run a script" steps of macros (2.1): the engine waits for the script, then goes on
    private ScriptSession nested;
    private Macro nestedMacro;
    private Runnable nestedDone;
    private int nestedLeft;
    private boolean nestedSampler;

    @Override public void runScriptStep(Macro m, int times, Runnable done) {
        com.freeclick.core.script.ScriptError err = com.freeclick.core.script.ScriptRunner.check(m.script);
        if (err != null) {
            print("⚠ Scriptul „" + m.name + "” are o greșeală: " + err.describe() + " (îl sar)", PlaybackEngine.PRINT_WARN);
            done.run();
            return;
        }
        nestedMacro = deepCopy(m);
        nestedLeft = Math.max(1, times);
        nestedDone = done;
        startNested();
    }

    private void startNested() {
        ScriptSession s;
        try {
            s = new ScriptSession(this, main, deepCopy(nestedMacro), false, false, null);
        } catch (com.freeclick.core.script.ScriptError e) {
            finishNested();
            return;
        }
        s.nested = true;
        s.runner.setPauseOnTouch(false); // the macro's own pause on touch holds it
        nested = s;
        if (ScreenSampler.supported() && !nestedSampler && com.freeclick.core.script.ScriptRunner.usesScreen(nestedMacro.script)) {
            nestedSampler = true;
            sampler.start();
        }
        console.add("📜 „" + nestedMacro.name + "”", PlaybackEngine.PRINT_INFO);
        s.start();
    }

    void onNestedScriptFinished(ScriptSession s, String reason, com.freeclick.core.script.ScriptError error) {
        if (s != nested) return;
        nested = null;
        if (error != null) {
            console.add("❌ „" + nestedMacro.name + "”: " + error.describe(), PlaybackEngine.PRINT_WARN);
            addLog("Script „" + nestedMacro.name + "”: " + error.describe());
        }
        nestedLeft--;
        if (nestedLeft > 0 && error == null && nestedDone != null) { startNested(); return; }
        finishNested();
    }

    private void finishNested() {
        if (script == null) hideHud();
        if (nestedSampler) { nestedSampler = false; if (!colorRun) sampler.stop(); }
        Runnable d = nestedDone;
        nestedDone = null;
        if (d != null) d.run();
    }

    @Override public void stopScriptStep() {
        nestedDone = null; // the engine stopped: nothing continues
        ScriptSession s = nested;
        nested = null;
        if (s != null) s.runner.stop();
        if (nestedSampler) { nestedSampler = false; if (!colorRun) sampler.stop(); }
    }

    @Override public void pauseScriptStep(boolean paused) {
        ScriptSession s = nested;
        if (s == null) return;
        if (paused) s.runner.pause(com.freeclick.core.script.ScriptRunner.WHY_APP); else s.runner.resume();
    }

    public void stopScript() {
        ScriptSession s = script;
        if (s == null) return;
        s.hideDebugBar();
        s.runner.stop();
        if (s.stepsDone != null && isPlaying()) engine.stop();
    }

    private void toggleScriptPause() {
        ScriptSession s = script;
        if (s == null) return;
        if (isPlaying()) { // inside ruleaza(pasi): the engine pauses / resumes too
            engine.togglePause();
            if (engine.state() == PlaybackEngine.State.RUNNING && s.runner.isPaused()) s.runner.resume();
            return;
        }
        s.hideDebugBar();
        if (s.runner.isPaused()) s.runner.resume(); else s.runner.pause(com.freeclick.core.script.ScriptRunner.WHY_USER);
    }

    /** A touch asked by a script: the panel lets it through if it is in the way. */
    void scriptGesture(final GesturePlan plan, final PlaybackEngine.GestureCallback cb) {
        if (script == null && nested == null) { cb.onResult(false); return; }
        final boolean pass = panel.isTouchable() && panel.intersects(plan);
        if (pass) panel.setTouchable(false);
        final boolean lowered = ears != null && ears.lowerForOwnGesture(); // the listening layer must not catch the script's own taps
        Runnable go = new Runnable() {
            @Override public void run() {
                if (script == null && nested == null) {
                    if (pass) panel.setTouchable(true);
                    if (lowered) ears.ownGestureDone();
                    cb.onResult(false);
                    return;
                }
                if (prefs.showTouches()) feedback.show(plan);
                inject(plan, new PlaybackEngine.GestureCallback() {
                    @Override public void onResult(boolean completed) {
                        if (pass) panel.setTouchable(true);
                        if (lowered) ears.ownGestureDone();
                        if (!completed) addLog("  ↪ gest anulat de sistem");
                        if (completed) {
                            tapRate.add(plan.strokes.size(), SystemClock.uptimeMillis());
                            if (script != null) panel.setInfo("📜" + rateSuffix());
                        }
                        cb.onResult(completed);
                    }
                });
            }
        };
        if (pass || lowered) main.postDelayed(go, 40); else go.run();
    }

    void performGlobalForScript(int g) { performGlobal(g); }

    /** 'ruleaza(pasi)': play steps with the normal engine; the script waits until onFinished. */
    void runStepsForScript(ScriptSession s, Macro m) {
        if (s.nested) { // the engine is busy with the macro that started this script
            print("⚠ ruleaza(pasi…) nu merge într-un script pornit dintr-un macro: pune pașii direct în macro", PlaybackEngine.PRINT_WARN);
            if (s.stepsDone != null) s.stepsDone.countDown();
            return;
        }
        if (script != s || s.stepsDone == null) return;
        MacroLibrary.InMemory lib = repo.snapshot();
        lib.add(m);
        engine.setPauseOnCancel(prefs.pauseOnTouch());
        engine.setPreview(false, true);
        if (!engine.start(m, lib)) {
            // refused (nothing to run): onFinished already counted down
            if (s.stepsDone != null) s.stepsDone.countDown();
        }
    }

    void stopStepsForScript() { if (isPlaying()) engine.stop(); }

    void onScriptState(ScriptSession s, int state, int line, String why) {
        if (script != s) return;
        if (afk != null) {
            boolean user = com.freeclick.core.script.ScriptRunner.WHY_USER.equals(why) || com.freeclick.core.script.ScriptRunner.WHY_TOUCH.equals(why)
                    || com.freeclick.core.script.ScriptRunner.WHY_STEP.equals(why) || com.freeclick.core.script.ScriptRunner.WHY_BREAKPOINT.equals(why);
            afk.onRunState(state == com.freeclick.core.script.ScriptRunner.RUNNING, state == com.freeclick.core.script.ScriptRunner.PAUSED && user);
        }
        if (state == com.freeclick.core.script.ScriptRunner.PAUSED) {
            panel.setState(FloatingPanel.State.PAUSED);
            console.setRunning(false);
            if (com.freeclick.core.script.ScriptRunner.WHY_STEP.equals(why) || com.freeclick.core.script.ScriptRunner.WHY_BREAKPOINT.equals(why)) {
                s.showDebugBar(line, why);
            } else if (com.freeclick.core.script.ScriptRunner.WHY_TOUCH.equals(why)) {
                toast("Pauză: ai atins ecranul. Apasă ▶ ca să continui (atingerea se repetă).");
            } else if (com.freeclick.core.script.ScriptRunner.WHY_APP.equals(why)) {
                toast("Pauză: s-a deschis un panou de sistem. Apasă ▶ ca să continui.");
            }
        } else if (state == com.freeclick.core.script.ScriptRunner.RUNNING) {
            if (!s.runner.isStepMode()) s.hideDebugBar();
            panel.setState(FloatingPanel.State.PLAYING);
            console.setRunning(true);
        }
    }

    void onScriptFinished(ScriptSession s, String reason, com.freeclick.core.script.ScriptError error) {
        if (script != s) return;
        script = null;
        hideHud();
        if (buttons != null) buttons.remove(0);
        scriptListenZone(null);
        if (afk != null) afk.onRunEnded();
        s.hideDebugBar();
        if (ScreenSampler.supported()) sampler.stop();
        hiddenForRun = false;
        console.setRunning(false);
        if (scriptShowedPanel) panel.hide();
        else {
            panel.setCompact(false);
            panel.setState(FloatingPanel.State.IDLE);
            markers.setInteractive(true);
        }
        updateMarkerVisibility();
        String msg;
        if (error != null) {
            msg = "❌ " + error.describe();
            console.add(msg, PlaybackEngine.PRINT_WARN);
            if (!console.isShown() && panel.isShown()) console.show();
        } else if (com.freeclick.core.script.ScriptRunner.DONE.equals(reason)) msg = "✅ Scriptul s-a terminat";
        else if (com.freeclick.core.script.ScriptRunner.SCRIPT_STOP.equals(reason)) msg = "■ Scriptul s-a oprit (stop())";
        else msg = "■ Script oprit";
        if (error == null) console.add(msg, PlaybackEngine.PRINT_INFO);
        addLog(msg);
        toast(msg);
        if (armed != null && panel != null) panel.setInfo("📜"); // ▶ runs it again; ≡ → Codul to edit it
        if (error != null) {
            final Intent back = new Intent(this, CodeEditorActivity.class).putExtra(CodeEditorActivity.EXTRA_ID, s.macro.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(CodeEditorActivity.EXTRA_ERROR_LINE, error.line).putExtra(CodeEditorActivity.EXTRA_ERROR, error.describe());
            main.postDelayed(new Runnable() { public void run() { if (script == null) startActivity(back); } }, 600);
        }
        QuickTileService.requestUpdate(this);
        notifyListeners();
    }

    // ================================================================ console and reading (1.8)
    @Override public void print(String text, int kind) {
        if (console == null) return;
        console.add(text, kind);
        // the first message of a run opens the console if it is closed (once: closing it again sticks)
        if (kind == PlaybackEngine.PRINT_USER && !console.isShown() && !consoleAutoOpened && panel != null && panel.isShown()) {
            consoleAutoOpened = true;
            console.show();
            console.setRunning(isPlaying());
        }
        if (kind == PlaybackEngine.PRINT_WARN) addLog(text);
    }

    private boolean consoleOverlapWarned;

    @Override public void readText(final PlaybackEngine.ReadRequest req, final PlaybackEngine.ReadCallback cb) {
        if (!ScreenSampler.supported()) { answerRead(cb, PlaybackEngine.IMG_NO_CAPTURE, 0); return; }
        int[] cr = console == null ? null : console.screenRect();
        if (cr != null && req.x1 < cr[2] && req.x2 > cr[0] && req.y1 < cr[3] && req.y2 > cr[1] && !consoleOverlapWarned) {
            consoleOverlapWarned = true;
            console.add("⚠ consola stă peste zona de citit: mut-o (trage de titlu) sau strânge-o —", PlaybackEngine.PRINT_WARN);
        }
        sampler.whenFrame(req.notBefore, new ScreenSampler.FrameCallback() {
            @Override public void onFrame(ScreenFrame f) {
                if (f == null) { answerRead(cb, PlaybackEngine.IMG_NO_CAPTURE, 0); return; }
                final ScreenFrame fr = sampler.acquire(f);
                final int top = statusBarHeight(), bottom = fr.height - navigationBarHeight();
                try {
                    matcher().execute(new Runnable() {
                        @Override public void run() {
                            final PlaybackEngine.ReadResult r = new PlaybackEngine.ReadResult();
                            r.frameTime = fr.time;
                            try {
                                if (fr.looksProtected(top, bottom)) r.status = PlaybackEngine.IMG_PROTECTED;
                                else {
                                    TextReader.Result res = TextReader.read(fr, req.x1, req.y1, req.x2, req.y2, req.mode, App.get().letters().book());
                                    r.text = res.text;
                                    r.number = res.number;
                                    r.confidence = res.confidence;
                                }
                            } catch (RuntimeException e) {
                                r.text = "";
                            } catch (OutOfMemoryError e) {
                                r.status = PlaybackEngine.IMG_NO_CAPTURE;
                            }
                            main.post(new Runnable() { public void run() {
                                sampler.releaseFrame(fr);
                                cb.onRead(r);
                            } });
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
                    sampler.releaseFrame(fr);
                }
            }
        });
    }

    private void answerRead(final PlaybackEngine.ReadCallback cb, int status, long frameTime) {
        final PlaybackEngine.ReadResult r = new PlaybackEngine.ReadResult();
        r.status = status;
        r.frameTime = frameTime;
        main.post(new Runnable() { public void run() { cb.onRead(r); } });
    }

    /** One line about a read, for the zone picker and the test: „Gold 1,250” = 1250 · sigur 93%. */
    static String describeRead(TextReader.Result res, int mode) {
        if (res.problem != null) return "⚠ " + res.problem;
        String t = "„" + res.text.replace('\n', ' ') + "”";
        if (mode == com.freeclick.core.Action.READ_NUMBER) {
            t += res.number == null ? " · nu e un număr" : " = " + com.freeclick.core.Expr.format(res.number);
        }
        return t + " · sigur " + Math.round(res.confidence * 100) + "%";
    }

    // ================================================================ notification controls
    void updateNotification() {
        if (notif == null || engine == null) return;
        if (afk != null && afk.ownsNotification()) { notif.cancel(); return; } // the 🌙 notification has the controls
        if (panel == null || !panel.isShown()) { notif.cancel(); return; }
        String st;
        if (recording.isActive()) st = "recording";
        else if (engine.state() == PlaybackEngine.State.RUNNING) st = engine.isPreview() ? "preview" : "playing";
        else if (engine.state() == PlaybackEngine.State.PAUSED) st = "paused";
        else st = "idle";
        Macro t = target();
        notif.update(prefs.notificationControls(), t == null ? "" : t.name, st, panelHiddenByUser);
    }

    /** Settings toggled the notification controls. */
    public void onNotificationSettingChanged() {
        if (notif != null) notif.undismiss();
        updateNotification();
    }

    void onNotificationAction(String action) {
        if (ControlNotification.ACTION_DISMISSED.equals(action)) { notif.markDismissed(); return; }
        if (panel == null || !panel.isShown()) return;
        if (afk != null && !ControlNotification.ACTION_PANEL.equals(action)) afk.userTookOver();
        if (ControlNotification.ACTION_TOGGLE.equals(action)) {
            if (script != null) toggleScriptPause();
            else if (isPlaying()) engine.togglePause();
            else if (!recording.isActive() && !awaitingStart) onPlay();
        } else if (ControlNotification.ACTION_STOP.equals(action)) {
            if (script != null) stopScript();
            else if (recording.isActive()) finishRecording(); else stopPlayback();
        } else if (ControlNotification.ACTION_PANEL.equals(action)) {
            panelHiddenByUser = !panelHiddenByUser;
            panel.setHidden(panelHiddenByUser);
            updateMarkerVisibility();
        }
        updateNotification();
    }

    @Override public GesturePlanner.Limits limits() {
        GesturePlanner.Limits l = new GesturePlanner.Limits();
        l.maxStrokes = GestureDescription.getMaxStrokeCount();
        l.maxDuration = GestureDescription.getMaxGestureDuration();
        Point sz = Overlays.screenSize(this);
        l.screenWidth = sz.x;
        l.screenHeight = sz.y;
        return l;
    }

    // ================================================================ choosing a zone or a colour (1.8)
    /** What was chosen on the frozen screen: a zone, or a 1×1 rectangle and the colour there. */
    interface Picked { void onPicked(android.graphics.Rect r, int rgb); }

    /**
     * Freeze the screen and let the user choose a zone to read (ImageCaptureView.MODE_ZONE, with a
     * live preview of what would be read) or a point and its colour (MODE_COLOR, with a magnifier).
     * On Android 10 and older the colour is picked with the simple crosshair layer instead.
     */
    void startPick(final int mode, final Picked then, final Runnable onEnd) {
        if (!ScreenSampler.supported()) {
            if (mode == ImageCaptureView.MODE_COLOR) { legacyColorPick(then, onEnd); return; }
            toast("Citirea din zonă are nevoie de Android 11 sau mai nou.");
            if (onEnd != null) onEnd.run();
            return;
        }
        if (capture != null || panel == null) return;
        captureEnded = onEnd;
        dismissDialog();
        undoBar.hide();
        picking = true;
        updateMarkerVisibility();
        panel.setHidden(true);
        feedback.hide();
        console.detach(); // not part of the frozen picture; comes back afterwards
        main.postDelayed(new Runnable() { public void run() {
            sampler.refreshThen(new Runnable() { public void run() {
                ScreenFrame f = sampler.latest();
                if (f == null) {
                    endCapture();
                    toast("Nu am putut citi ecranul. Încearcă din nou.");
                    return;
                }
                captureFrame = sampler.acquire(f);
                if (!colorRun) sampler.stop();
                showPick(mode, then);
            } });
        } }, 250);
    }

    private void showPick(final int mode, final Picked then) {
        capture = new ImageCaptureView(this, captureFrame, statusBarHeight(), mode, new ImageCaptureView.Callbacks() {
            @Override public void onCaptureDone(android.graphics.Rect r, boolean tight) {
                if (mode == ImageCaptureView.MODE_ZONE && (r.width() < 6 || r.height() < 6)) {
                    capture.showProblem("Zona e prea mică: prinde tot numărul sau textul.");
                    return;
                }
                if (mode == ImageCaptureView.MODE_SWIPE && Math.hypot(r.right - r.left, r.bottom - r.top) < 20) {
                    capture.showProblem("Trage mai mult: de unde începe swipe-ul până unde se termină.");
                    return;
                }
                int x = Math.max(0, Math.min(captureFrame.width - 1, r.left)), y = Math.max(0, Math.min(captureFrame.height - 1, r.top));
                int rgb = captureFrame.argb[y * captureFrame.width + x] & 0xFFFFFF;
                then.onPicked(new android.graphics.Rect(r), rgb);
                endCapture();
            }
            @Override public void onCaptureCancel() { endCapture(); }
        });
        if (mode == ImageCaptureView.MODE_ZONE) {
            capture.setZonePreview(new ImageCaptureView.ZonePreview() {
                @Override public void preview(android.graphics.Rect r, final ImageCaptureView v) {
                    final int[] crop = crop(captureFrame, r);
                    if (crop == null) return;
                    final int w = Math.min(r.right, captureFrame.width) - Math.max(0, r.left);
                    final int h = Math.min(r.bottom, captureFrame.height) - Math.max(0, r.top);
                    runOnMatcher(new Runnable() { public void run() {
                        TextReader.Result res = TextReader.read(crop, w, h, 0, 0, w, h, TextReader.MODE_TEXT, App.get().letters().book());
                        final String msg = describeRead(res, com.freeclick.core.Action.READ_NUMBER);
                        main.post(new Runnable() { public void run() { if (capture == v) v.showReading(msg); } });
                    } });
                }
            });
        }
        Overlays.safeAdd(wm, capture, Overlays.params(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, true, false));
    }

    /** Work on the matcher thread; dropped quietly if the service is shutting down. */
    private void runOnMatcher(Runnable r) {
        try {
            matcher().execute(r);
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) { /* nothing to show any more */ }
    }

    /** A copy of a rectangle of a frame (the frame's buffer is reused once released). */
    static int[] crop(ScreenFrame f, android.graphics.Rect r) {
        int x1 = Math.max(0, r.left), y1 = Math.max(0, r.top), x2 = Math.min(f.width, r.right), y2 = Math.min(f.height, r.bottom);
        if (x2 - x1 < 3 || y2 - y1 < 3 || (long) (x2 - x1) * (y2 - y1) > TextReader.MAX_PIXELS) return null;
        int w = x2 - x1;
        int[] out = new int[w * (y2 - y1)];
        for (int y = y1; y < y2; y++) System.arraycopy(f.argb, y * f.width + x1, out, (y - y1) * w, w);
        return out;
    }

    // ---- Android 10 and older: the crosshair layer, reading the colour through screen capture
    private Picked legacyThen;
    private Runnable legacyEnd;

    private void legacyColorPick(Picked then, Runnable onEnd) {
        legacyThen = then;
        legacyEnd = onEnd;
        if (VideoRecorderService.isRecording()) { toast("Oprește întâi filmarea ecranului."); endLegacyPick(); return; }
        if (!VideoRecorderService.isCapturingPixels()) {
            VideoRecorderService.requestPixels(this, new Runnable() {
                @Override public void run() {
                    main.postDelayed(new Runnable() { public void run() {
                        if (VideoRecorderService.isCapturingPixels() && panel != null && panel.isShown()) showPicker();
                        else endLegacyPick();
                    } }, 500);
                }
            });
            return;
        }
        showPicker();
    }

    private void endLegacyPick() {
        legacyThen = null;
        Runnable r = legacyEnd;
        legacyEnd = null;
        if (r != null) r.run();
    }

    /** "+ → Condiție de culoare": choose the point and its colour, then a step waits for it. */
    private void addColorWait() {
        if (workspace() == null) return;
        startPick(ImageCaptureView.MODE_COLOR, new Picked() {
            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                if (workspace() == null) return;
                Action a = Action.waitColor(r.left, r.top, rgb, 30000);
                a.delay = 0;
                ws.add(a);
                syncMarkers();
                warnIfPanelCoversConditions();
                onMarkerTapped(a);
            }
        }, null);
    }

    /** "+ → Citește din zonă": choose the zone, then a step reads it into a variable. */
    private void addReadZone() {
        if (workspace() == null) return;
        startPick(ImageCaptureView.MODE_ZONE, new Picked() {
            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                if (workspace() == null) return;
                Action a = Action.read(r.left, r.top, r.right, r.bottom, Action.READ_NUMBER, freeVarName("numar"));
                a.delay = workspace().settings.tapInterval;
                ws.add(a);
                syncMarkers();
                onMarkerTapped(a); // number or text, variable name
            }
        }, null);
    }

    /** "+ → Citește culoarea": choose the point, then a step reads its colour into a variable. */
    private void addReadColor() {
        if (workspace() == null) return;
        startPick(ImageCaptureView.MODE_COLOR, new Picked() {
            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                if (workspace() == null) return;
                Action a = Action.readColor(r.left, r.top, freeVarName("culoare"));
                ws.add(a);
                syncMarkers();
                onMarkerTapped(a);
            }
        }, null);
    }

    /** "+ → Afișează în consolă" / "Setează o variabilă": steps without a place on the screen. */
    private void addTextStep(boolean print) {
        if (workspace() == null) return;
        Action a;
        if (print) {
            String last = lastReadVar();
            a = Action.print(last != null ? last + " = {" + last + "}" : "Tura {tura}, timp {timp} s");
        } else {
            a = Action.setVar("n", "n + 1");
        }
        ws.add(a);
        syncMarkers();
        toast(print ? "Pasul „Afișează” e adăugat (îl găsești și în ≡ → Opțiuni → Editează acțiunile)."
                : "Pasul „Variabilă” e adăugat (îl găsești și în ≡ → Opțiuni → Editează acțiunile).");
        onMarkerTapped(a);
    }

    /** The variable of the last read step of the current work, if any. */
    private String lastReadVar() {
        if (workspace() == null) return null;
        String v = null;
        for (Action a : ActionTree.all(workspace().actions)) {
            if (a.type == Action.Type.READ && a.varName != null && !a.varName.trim().isEmpty()) v = a.varName.trim();
        }
        return v;
    }

    /** numar1, numar2... a name no step of the current work uses yet. */
    private String freeVarName(String base) {
        java.util.Set<String> used = new java.util.HashSet<String>();
        if (workspace() != null) {
            for (Action a : ActionTree.all(workspace().actions)) if (a.varName != null) used.add(a.varName.trim().toLowerCase(Locale.ROOT));
        }
        for (int i = 1; ; i++) if (!used.contains(base + i)) return base + i;
    }

    /** ≡ → "Pipetă de culoare": only tells the colour (console + clipboard), adds nothing. */
    private void colorTool() {
        startPick(ImageCaptureView.MODE_COLOR, new Picked() {
            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                String hex = com.freeclick.core.ColorMatch.toHex(rgb);
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("culoare", hex));
                String line = "🎨 (" + r.left + ", " + r.top + ") " + ImageCaptureView.describeColor(rgb);
                console.add(line, PlaybackEngine.PRINT_USER);
                toast(hex + " copiat · " + line.substring(3));
            }
        }, new Runnable() { public void run() { if (prefs.consoleOpen()) console.show(); } });
    }

    // ---- "🔍 Testează citirea": read the zone now, show what was read, teach the letters
    void testRead(final Action a, final Macro owner, final Runnable after) {
        if (!ScreenSampler.supported()) { toast("Citirea din zonă are nevoie de Android 11 sau mai nou."); if (after != null) after.run(); return; }
        picking = true;
        updateMarkerVisibility();
        panel.setHidden(true);
        undoBar.hide();
        console.detach();
        main.postDelayed(new Runnable() { public void run() {
            sampler.refreshThen(new Runnable() { public void run() {
                ScreenFrame f = sampler.latest();
                if (f == null) { endTest(null, after); toast("Nu am putut citi ecranul."); return; }
                final ScreenFrame fr = sampler.acquire(f);
                if (!colorRun) sampler.stop();
                // the step's zone on this screen (recorded on another resolution: scaled like at playback)
                CoordinateScaler sc = owner == null ? CoordinateScaler.IDENTITY : CoordinateScaler.of(owner.screenWidth, owner.screenHeight,
                        fr.width, fr.height, owner.settings.scaleToScreen);
                final android.graphics.Rect zr = new android.graphics.Rect(Math.round(sc.x(a.x)), Math.round(sc.y(a.y)),
                        Math.round(sc.x(a.x2)), Math.round(sc.y(a.y2)));
                final int[] px = crop(fr, zr);
                sampler.releaseFrame(fr);
                if (px == null) { endTest(null, after); toast("Zona e în afara ecranului sau prea mică. Alege zona din nou."); return; }
                final int w = Math.min(zr.right, fr.width) - Math.max(0, zr.left), h = Math.min(zr.bottom, fr.height) - Math.max(0, zr.top);
                final int mode = a.readMode == Action.READ_TEXT ? TextReader.MODE_TEXT : TextReader.MODE_NUMBER;
                runOnMatcher(new Runnable() { public void run() {
                    final TextReader.Result res = TextReader.read(px, w, h, 0, 0, w, h, mode, App.get().letters().book());
                    main.post(new Runnable() { public void run() {
                        endTest(null, null);
                        showReadTest(px, w, h, res, mode, after);
                    } });
                } });
            } });
        } }, 250);
    }

    private void showReadTest(final int[] px, final int w, final int h, final TextReader.Result res, final int mode, final Runnable after) {
        Context dc = Overlays.dialogContext(this);
        android.widget.LinearLayout box = Ui.vertical(dc);
        int p = Ui.dp(dc, 20);
        box.setPadding(p, Ui.dp(dc, 6), p, 0);
        box.addView(new ReadPreviewView(dc, px, w, h, res), new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(dc, 110)));
        box.addView(Ui.text(dc, describeRead(res, mode), 16, Ui.TEXT, true));
        box.addView(Ui.muted(dc, "Sub fiecare literă găsită scrie ce am citit. Dacă e greșit, scrie mai jos ce apare de fapt și apasă "
                + "ÎNVAȚĂ: țin minte literele fontului acestuia (merge și pentru cifre de joc ciudate)."));
        final android.widget.EditText fix = new android.widget.EditText(dc);
        fix.setSingleLine(true);
        fix.setText(res.text.replace('\n', ' '));
        fix.setSelectAllOnFocus(true);
        box.addView(fix, Ui.matchWrap());
        addLog("Test citire: " + describeRead(res, mode));
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(dc).setTitle("Test citire")
                .setView(box)
                .setPositiveButton("ÎNVAȚĂ", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dlg, int which) {
                        final String typed = fix.getText().toString();
                        if (typed.trim().isEmpty()) return;
                        final Letters letters = App.get().letters();
                        runOnMatcher(new Runnable() { public void run() {
                            final int n = letters.bookNoDraw().learn(res, typed);
                            if (n > 0) letters.saveIfChanged();
                            main.post(new Runnable() { public void run() {
                                if (n > 0) toast("Am învățat " + n + " litere. Testează din nou ca să vezi.");
                                else toast("Am găsit " + (-n) + " litere, dar textul scris nu se potrivește cu ele "
                                        + "(sau o literă are altă mărime). Scrie exact ce se vede, fără litere în plus.");
                            } });
                        } });
                    }
                })
                .setNegativeButton("GATA", null)
                .create();
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface dlg) {
                if (prefs.consoleOpen()) console.show();
                if (after != null) after.run();
            }
        });
        showDialog(d);
    }

    /** The zone that was read, enlarged, with a box around each letter and what it was read as under it. */
    private static final class ReadPreviewView extends View {
        final android.graphics.Bitmap bm;
        final TextReader.Result res;
        final int zx, zy;
        final android.graphics.Paint box = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Paint label = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        ReadPreviewView(Context c, int[] px, int w, int h, TextReader.Result res) {
            super(c);
            bm = android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888);
            this.res = res;
            zx = 0; zy = 0;
            box.setStyle(android.graphics.Paint.Style.STROKE);
            box.setStrokeWidth(Ui.dp(c, 1.5f));
            box.setColor(Ui.ACCENT);
            label.setColor(0xFFFFFFFF);
            label.setTextSize(Ui.dp(c, 13));
            label.setFakeBoldText(true);
            label.setTextAlign(android.graphics.Paint.Align.CENTER);
            label.setShadowLayer(Ui.dp(c, 2), 0, 0, 0xFF000000);
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            float lab = Ui.dp(getContext(), 18);
            float s = Math.min(getWidth() / (float) bm.getWidth(), (getHeight() - lab) / (float) bm.getHeight());
            float ox = (getWidth() - bm.getWidth() * s) / 2f;
            android.graphics.Paint fil = new android.graphics.Paint();
            fil.setFilterBitmap(false);
            c.save();
            c.translate(ox, 0);
            c.scale(s, s);
            c.drawBitmap(bm, 0, 0, fil);
            c.restore();
            for (TextReader.Glyph g : res.glyphs) {
                float l = ox + (g.x0 - zx) * s, t = (g.y0 - zy) * s, r = ox + (g.x1 - zx) * s, b = (g.y1 - zy) * s;
                box.setColor(g.dist < 0.3f ? Ui.ACCENT : g.dist < 0.5f ? 0xFFF5C542 : Ui.DANGER);
                c.drawRect(l, t, r, b, box);
                c.drawText(String.valueOf(g.c), (l + r) / 2f, Math.min(getHeight() - 2, bm.getHeight() * s + lab - 3), label);
            }
        }
    }

    // ================================================================ cutting out an image
    interface ImageCaptured { void onImage(ImageSpec spec); }

    private ImageCaptureView capture;
    private ScreenFrame captureFrame;
    private Runnable captureEnded;

    /** Freeze the screen and let the user cut out a button; `then` gets the saved image. */
    void startImageCapture(final ImageCaptured then, final Runnable onEnd) {
        if (!ScreenSampler.supported()) { toast("Pașii cu imagini au nevoie de Android 11 sau mai nou."); if (onEnd != null) onEnd.run(); return; }
        if (capture != null || workspace() == null) return;
        captureEnded = onEnd;
        dismissDialog();
        undoBar.hide();
        picking = true;
        updateMarkerVisibility();
        panel.setHidden(true);
        feedback.hide();
        // a frame without our panel and points: let them disappear first
        main.postDelayed(new Runnable() { public void run() {
            sampler.refreshThen(new Runnable() { public void run() {
                ScreenFrame f = sampler.latest();
                if (f == null) {
                    endCapture();
                    toast("Nu am putut citi ecranul. Încearcă din nou.");
                    return;
                }
                captureFrame = sampler.acquire(f);
                if (!colorRun) sampler.stop();
                showCapture(then);
            } });
        } }, 250);
    }

    private void showCapture(final ImageCaptured then) {
        capture = new ImageCaptureView(this, captureFrame, statusBarHeight(), new ImageCaptureView.Callbacks() {
            @Override public void onCaptureDone(android.graphics.Rect r, boolean tight) {
                String problem = checkCut(r);
                if (problem != null) { capture.showProblem(problem); return; }
                ImageTemplate t = ImageTemplate.cut(captureFrame, r.left, r.top, r.width(), r.height());
                if (t.detail() < ImageTemplate.MIN_DETAIL) {
                    capture.showProblem("Zona e aproape de o singură culoare. Alege ceva cu text sau un desen.");
                    return;
                }
                ImageSpec spec = new ImageSpec();
                try {
                    spec.id = images.save(t.argb, t.width, t.height);
                } catch (Exception e) {
                    capture.showProblem("Nu pot salva imaginea: " + e.getMessage());
                    return;
                }
                spec.w = t.width; spec.h = t.height;
                spec.capX = r.left; spec.capY = r.top;
                spec.capScreenW = captureFrame.width; spec.capScreenH = captureFrame.height;
                spec.tapDx = t.width / 2f; spec.tapDy = t.height / 2f;
                spec.tight = tight;
                then.onImage(spec); // before endCapture(): a recording inserts it before skipping the cutting time
                endCapture();
            }

            @Override public void onCaptureCancel() { endCapture(); }
        });
        Overlays.safeAdd(wm, capture, Overlays.params(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, true, false));
    }

    /** Why a rectangle cannot be used (null if it can). */
    private String checkCut(android.graphics.Rect r) {
        int sb = statusBarHeight();
        if (r.top < sb) {
            if (r.bottom - sb < ImageTemplate.MIN_SIDE) return "Bara de sus (ceasul) se schimbă mereu: alege ceva de sub ea.";
            r.top = sb; // the clock changes every minute: never part of a cut-out
        }
        if (r.width() < ImageTemplate.MIN_SIDE || r.height() < ImageTemplate.MIN_SIDE) return "Prea mic: prinde tot butonul (minim " + ImageTemplate.MIN_SIDE + " px).";
        if (r.width() > ImageTemplate.MAX_SIDE || r.height() > ImageTemplate.MAX_SIDE) return "Prea mare: prinde doar butonul, nu o bucată mare de ecran (maxim " + ImageTemplate.MAX_SIDE + " px).";
        return null;
    }

    private void endCapture() {
        if (capture != null) { Overlays.safeRemove(wm, capture); capture.dispose(); capture = null; }
        if (captureFrame != null) { sampler.releaseFrame(captureFrame); captureFrame = null; }
        picking = false;
        panel.setHidden(panelHiddenByUser);
        if (prefs.consoleOpen() && panel.isShown()) console.show();
        updateMarkerVisibility();
        Runnable r = captureEnded;
        captureEnded = null;
        if (r != null) r.run();
    }

    /** Cut the picture of an image step again (keeps its name and options). */
    void recaptureImage(final Action a, final Runnable after) {
        startImageCapture(new ImageCaptured() {
            @Override public void onImage(ImageSpec spec) {
                ImageSpec old = a.img;
                spec.name = old == null ? "" : old.name;
                spec.precision = old == null ? spec.precision : old.precision;
                a.img = spec;
                a.x = spec.capX + spec.tapDx;
                a.y = spec.capY + spec.tapDy;
                ws.markDirty();
            }
        }, after);
    }

    /** "Testează acum": look for the image on the screen as it is now and show the result on it. */
    void testImage(final Action a, final Runnable after) {
        if (!ScreenSampler.supported()) { toast("Pașii cu imagini au nevoie de Android 11 sau mai nou."); if (after != null) after.run(); return; }
        if (a.img == null || !images.has(a.img.id)) { toast("Poza acestui pas lipsește. Recapturează imaginea."); if (after != null) after.run(); return; }
        picking = true;
        updateMarkerVisibility();
        panel.setHidden(true);
        undoBar.hide();
        main.postDelayed(new Runnable() { public void run() {
            sampler.refreshThen(new Runnable() { public void run() {
                ScreenFrame f = sampler.latest();
                if (f == null) { endTest(null, after); toast("Nu am putut citi ecranul."); return; }
                final ScreenFrame fr = sampler.acquire(f);
                if (!colorRun) sampler.stop();
                final ImageSpec spec = a.img;
                final ImageMatcher.Query q = queryFor(spec, a.allMatches, fr);
                q.ex1 = q.ey1 = q.ex2 = q.ey2 = 0; // the panel is hidden during the test
                final int top = statusBarHeight(), bottom = fr.height - navigationBarHeight();
                final double sx = spec.capScreenW > 0 ? fr.width / (double) spec.capScreenW : 1;
                final double sy = spec.capScreenH > 0 ? fr.height / (double) spec.capScreenH : 1;
                matcher().execute(new Runnable() { public void run() {
                    ImageTemplate t0 = images.template(spec.id);
                    final boolean prot = fr.looksProtected(top, bottom);
                    final ImageMatcher.Result res = t0 == null || prot ? null : ImageMatcher.find(fr, t0.scaled(sx, sy), q);
                    main.post(new Runnable() { public void run() {
                        sampler.releaseFrame(fr);
                        showTestResult(res, prot, after);
                    } });
                } });
            } });
        } }, 250);
    }

    private void showTestResult(ImageMatcher.Result res, boolean prot, final Runnable after) {
        List<android.graphics.RectF> found = new ArrayList<android.graphics.RectF>();
        android.graphics.RectF closest = null;
        String msg;
        if (prot) msg = "Ecran protejat: aplicația asta nu poate fi citită";
        else if (res == null) msg = "Nu am putut căuta";
        else {
            for (ImageMatcher.Match m : res.matches) found.add(new android.graphics.RectF(m.x, m.y, m.x + m.w, m.y + m.h));
            if (res.best != null) closest = new android.graphics.RectF(res.best.x, res.best.y, res.best.x + res.best.w, res.best.y + res.best.h);
            int pct = res.best == null ? 0 : (int) Math.round(res.best.similarity() * 100);
            if (!found.isEmpty()) msg = "Găsit ✅ " + pct + "%" + (found.size() > 1 ? " · de " + found.size() + " ori" : "");
            else if (res.best == null) msg = "Nu apare acum";
            else if (res.best.colorDiff > ImageMatcher.maxColorDiff(50)) msg = "Nu apare acum (alt colorit)";
            else msg = "Nu apare acum · cel mai apropiat " + pct + "%";
        }
        final ImageTestLayer layer = new ImageTestLayer(this, found, closest, msg);
        Overlays.safeAdd(wm, layer, Overlays.params(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, false, false));
        addLog("Test imagine: " + msg);
        main.postDelayed(new Runnable() { public void run() { endTest(layer, after); } }, 2800);
    }

    private void endTest(View layer, Runnable after) {
        if (layer != null) Overlays.safeRemove(wm, layer);
        picking = false;
        panel.setHidden(panelHiddenByUser);
        updateMarkerVisibility();
        if (after != null) after.run();
    }

    // ---- the editor's image buttons: the editor steps aside, we work over the app, then bring it back
    private ImageSpec editorImage;

    private View readyBar;

    /**
     * Editor "Adaugă → Imagine": first a small bar "✂ Decupează acum" so the user can open the
     * right screen of the app; then the screen is frozen for cutting out.
     */
    public void captureForEditor() { editorBack = EditorActivity.class; captureForEditor(EDITOR_IMAGE); }

    /** What the editor asked to choose on the real screen. */
    static final int EDITOR_IMAGE = 0, EDITOR_ZONE = 1, EDITOR_COLOR_WAIT = 2, EDITOR_COLOR_READ = 3, EDITOR_POINT = 4, EDITOR_SWIPE = 5,
            EDITOR_SHOT = 6;

    /** The screenshot taken for the code editor (taken once when it comes back). */
    private android.graphics.Bitmap editorShot;

    public android.graphics.Bitmap takeEditorShot() { android.graphics.Bitmap b = editorShot; editorShot = null; return b; }

    /**
     * The whole screen as a picture, without FreeClick's own windows (panel, targets, console),
     * for the code editor's picture pane. {@code then} gets null if it could not be taken.
     */
    void captureScreenshot(final ShotTaken then) {
        if (!ScreenSampler.supported()) {
            toast("Capturile din aplicație cer Android 11+. Fă un screenshot normal și ia-l din galerie.");
            then.onShot(null);
            return;
        }
        if (capture != null || picking || panel == null) { then.onShot(null); return; }
        dismissDialog();
        undoBar.hide();
        picking = true;
        updateMarkerVisibility();
        panel.setHidden(true);
        feedback.hide();
        console.detach();
        main.postDelayed(new Runnable() { public void run() {
            sampler.refreshThen(new Runnable() { public void run() {
                ScreenFrame f = sampler.latest();
                android.graphics.Bitmap b = null;
                if (f != null) {
                    try {
                        b = android.graphics.Bitmap.createBitmap(f.argb, f.width, f.height, android.graphics.Bitmap.Config.ARGB_8888);
                    } catch (OutOfMemoryError e) {
                        b = null;
                    }
                }
                if (!colorRun) sampler.stop();
                picking = false;
                panel.setHidden(panelHiddenByUser);
                if (prefs.consoleOpen() && panel.isShown()) console.show();
                updateMarkerVisibility();
                if (b == null) toast("Nu am putut lua captura. Încearcă din nou.");
                then.onShot(b);
            } });
        } }, 250);
    }

    interface ShotTaken { void onShot(android.graphics.Bitmap b); }

    /** ≡ → "📷 Captură pentru codul …": saved with the script, the game stays on screen (several in a row). */
    private void shotForScript(final Macro script) {
        captureScreenshot(new ShotTaken() {
            @Override public void onShot(final android.graphics.Bitmap b) {
                if (b == null) return;
                new Thread(new Runnable() { public void run() {
                    String msg;
                    try {
                        ShotStore st = App.get().shots();
                        int n = st.list(script.id).size() + 1;
                        st.add(script.id, b, "captură " + n);
                        msg = "📷 Salvată în „" + script.name + "” (" + n + "/" + ShotStore.MAX_PER_SCRIPT + ")";
                    } catch (Exception e) {
                        msg = "Nu am putut salva captura: " + e.getMessage();
                    }
                    final String m = msg;
                    main.post(new Runnable() { public void run() { toast(m); } });
                } }, "freeclick-shot").start();
            }
        });
    }

    /** Which screen comes back after choosing (the step editor or the code editor). */
    private Class<?> editorBack = EditorActivity.class;

    public void captureForEditor(int what, Class<?> back) {
        editorBack = back == null ? EditorActivity.class : back;
        captureForEditor(what);
    }

    /** A new step chosen on the screen for the editor (taken once when it comes back). */
    private Action editorAction;

    /**
     * Editor "Adaugă → Imagine / Citește / Culoare": first a small bar so the user can open the
     * right screen of the app; then the screen is frozen for choosing.
     */
    /** The editor came back (any way): the "open the right screen" bar is not needed any more. */
    public void cancelReadyBar() { removeReadyBar(); }

    public void captureForEditor(final int what) {
        removeReadyBar();
        android.widget.LinearLayout bar = Ui.horizontal(this);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 10);
        bar.setPadding(p, p / 2, p / 2, p / 2);
        bar.setBackground(Ui.rounded(0xF0140F45, Ui.dp(this, 14)));
        android.widget.TextView msg = Ui.text(this, what == EDITOR_IMAGE ? "Deschide ecranul cu butonul, apoi:" : "Deschide ecranul potrivit, apoi:", 14, 0xFFFFFFFF, false);
        bar.addView(msg, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String goText = what == EDITOR_IMAGE ? "✂ DECUPEAZĂ" : what == EDITOR_ZONE ? "▭ ALEGE ZONA" : what == EDITOR_SWIPE ? "↗ ARATĂ SWIPE-UL"
                : what == EDITOR_SHOT ? "📷 CAPTUREAZĂ" : "🎯 ALEGE PUNCTUL";
        android.widget.TextView go = Ui.text(this, goText, 14, Ui.ACCENT, true);
        go.setPadding(p, p / 2, p, p / 2);
        Ui.clickable(go, new View.OnClickListener() { public void onClick(View v) {
            removeReadyBar();
            final Runnable back = new Runnable() { public void run() { bringEditorBack(); } };
            if (what == EDITOR_SHOT) {
                captureScreenshot(new ShotTaken() {
                    @Override public void onShot(android.graphics.Bitmap b) { editorShot = b; back.run(); }
                });
                return;
            }
            if (what == EDITOR_IMAGE) {
                startImageCapture(new ImageCaptured() {
                    @Override public void onImage(ImageSpec spec) { editorImage = spec; }
                }, back);
                return;
            }
            int mode = what == EDITOR_ZONE ? ImageCaptureView.MODE_ZONE : what == EDITOR_SWIPE ? ImageCaptureView.MODE_SWIPE : ImageCaptureView.MODE_COLOR;
            startPick(mode, new Picked() {
                @Override public void onPicked(android.graphics.Rect r, int rgb) {
                    if (what == EDITOR_ZONE) editorAction = Action.read(r.left, r.top, r.right, r.bottom, Action.READ_NUMBER, "numar");
                    else if (what == EDITOR_COLOR_WAIT) editorAction = Action.waitColor(r.left, r.top, rgb, 30000);
                    else if (what == EDITOR_POINT) editorAction = Action.tap(r.left, r.top);
                    else if (what == EDITOR_SWIPE) editorAction = Action.swipe(r.left, r.top, r.right, r.bottom, Action.DEFAULT_SWIPE_MS);
                    else editorAction = Action.readColor(r.left, r.top, "culoare");
                }
            }, back);
        } }, Ui.dp(this, 8));
        bar.addView(go);
        android.widget.TextView no = Ui.text(this, "×", 26, 0xFFFFFFFF, true);
        no.setGravity(android.view.Gravity.CENTER);
        no.setContentDescription("Renunță");
        Ui.clickable(no, new View.OnClickListener() { public void onClick(View v) { removeReadyBar(); bringEditorBack(); } }, Ui.dp(this, 20));
        bar.addView(no, new android.widget.LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        // as wide as the screen (minus margins): with big system fonts nothing is pushed off-screen
        android.graphics.Point sz = Overlays.screenSize(this);
        android.view.WindowManager.LayoutParams lp = Overlays.params(sz.x - 2 * Ui.dp(this, 8),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true, false);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        lp.x = Ui.dp(this, 8);
        lp.y = statusBarHeight() + Ui.dp(this, 8);
        readyBar = bar;
        Overlays.safeAdd(wm, bar, lp);
    }

    /** The step chosen on the screen for the editor (once). */
    public Action takeEditorAction() { Action a = editorAction; editorAction = null; return a; }

    /** The editor's "🎯" in a colour step: choose the point (and colour) again on the real screen. */
    public void pickColorForEditor(final Action draft) {
        startPick(ImageCaptureView.MODE_COLOR, new Picked() {
            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                draft.x = r.left; draft.y = r.top;
                if (draft.type == Action.Type.WAIT_COLOR) draft.color = rgb; else { draft.x2 = draft.x; draft.y2 = draft.y; }
            }
        }, new Runnable() { public void run() { bringEditorBack(); } });
    }

    /** The editor's "▭ Alege zona" in a read step. */
    public void pickZoneForEditor(final Action draft) {
        startPick(ImageCaptureView.MODE_ZONE, new Picked() {
            @Override public void onPicked(android.graphics.Rect r, int rgb) {
                draft.x = r.left; draft.y = r.top; draft.x2 = r.right; draft.y2 = r.bottom;
            }
        }, new Runnable() { public void run() { bringEditorBack(); } });
    }

    /** The editor's "🔍 Testează citirea". */
    public void testReadForEditor(Action draft, Macro owner) {
        testRead(draft, owner, new Runnable() { public void run() { bringEditorBack(); } });
    }

    private void removeReadyBar() {
        if (readyBar != null) Overlays.safeRemove(wm, readyBar);
        readyBar = null;
    }

    /** The picture cut for the editor (once). */
    public ImageSpec takeEditorImage() { ImageSpec s = editorImage; editorImage = null; return s; }

    public void testForEditor(Action draft) { testImage(draft, new Runnable() { public void run() { bringEditorBack(); } }); }

    public void recaptureForEditor(Action draft) { recaptureImage(draft, new Runnable() { public void run() { bringEditorBack(); } }); }

    private void bringEditorBack() {
        final Class<?> target = editorBack;
        editorBack = EditorActivity.class;
        main.postDelayed(new Runnable() { public void run() {
            startActivity(new Intent(ClickService.this, target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } }, 300);
    }

    /** "+ → Imagine": a step that taps the image when it is on screen (otherwise goes on). */
    private void addImageTarget() {
        startImageCapture(new ImageCaptured() {
            @Override public void onImage(ImageSpec spec) {
                if (workspace() == null) return;
                Action a = Action.image(spec);
                a.delay = workspace().settings.tapInterval;
                a.waitMode = Action.MISSING_CONTINUE;
                ws.add(a);
                syncMarkers();
                onMarkerTapped(a); // name it, choose what it does
            }
        }, null);
    }

    /** 🖼 while recording: "wait here until this image appears" (cutting time is not recorded). */
    @Override public void onImageDuringRecording() {
        if (!recording.isActive()) return;
        final long started = SystemClock.uptimeMillis();
        recording.setVisible(false);
        startImageCapture(new ImageCaptured() {
            @Override public void onImage(ImageSpec spec) {
                Action a = Action.image(spec);
                a.press = Action.PRESS_NONE;
                a.waitMode = Action.MISSING_WAIT;
                a.duration = 30000;
                if (recording.isActive() && recording.addStep(a, started) != null) {
                    panel.setInfo(String.valueOf(recording.count()));
                    toast("Adăugat: așteaptă imaginea (max 30 s). Continuă înregistrarea.");
                }
            }
        }, new Runnable() { public void run() {
            if (recording.isActive()) {
                // the cut-out is inserted at the moment 🖼 was pressed; the time spent cutting is not recorded
                recording.skipTime(SystemClock.uptimeMillis() - started);
                recording.setVisible(true);
                panel.bringToFront();
            }
        } });
    }

    // ================================================================ colour picker
    private void startColorPicker() { addColorWait(); }

    private void showPicker() {
        removePicker();
        picking = true;
        updateMarkerVisibility();
        panel.setHidden(true);
        picker = new PickerView(this);
        Overlays.safeAdd(wm, picker, Overlays.params(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, true, false));
    }

    private void removePicker() {
        if (picker != null) { Overlays.safeRemove(wm, picker); picker = null; }
    }

    private void cancelPicker() {
        removePicker();
        picking = false;
        panel.setHidden(false);
        updateMarkerVisibility();
        endLegacyPick();
    }

    /** Called with the screen point the user touched in the picker. */
    private void onPicked(final float x, final float y) {
        removePicker();
        // Let a frame without our overlays reach the screen before reading the colour.
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (ScreenSampler.supported()) {
                    sampler.refreshThen(new Runnable() {
                        @Override public void run() {
                            int c = sampler.sample(Math.round(x), Math.round(y));
                            if (!colorRun) sampler.stop(); // free the frame
                            finishPick(x, y, c);
                        }
                    });
                } else {
                    finishPick(x, y, VideoRecorderService.samplePixel(Math.round(x), Math.round(y)));
                }
            }
        }, 250);
    }

    private void finishPick(float x, float y, int color) {
        picking = false;
        panel.setHidden(false);
        updateMarkerVisibility();
        Picked then = legacyThen;
        legacyThen = null;
        if (color < 0) toast("Nu am putut citi culoarea (ecran protejat?). Încearcă din nou.");
        else if (then != null) then.onPicked(new android.graphics.Rect(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y) + 1), color);
        endLegacyPick();
    }

    private void warnIfPanelCoversConditions() {
        if (workspace() == null) return;
        for (Action a : ActionTree.all(workspace().actions)) {
            if (a.type != Action.Type.WAIT_COLOR || !a.enabled) continue;
            GesturePlan probe = new GesturePlan();
            GesturePlan.PlannedStroke st = new GesturePlan.PlannedStroke(0, 1);
            st.points.add(new float[]{a.x, a.y});
            probe.strokes.add(st);
            if (panel.intersects(probe)) {
                toast("Atenție: panoul acoperă punctul unei condiții de culoare. Mută panoul.");
                return;
            }
        }
    }

    /** Full-screen touch catcher with a crosshair; never part of the sampled frame. */
    private final class PickerView extends View {
        final android.graphics.Paint text = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Paint bg = new android.graphics.Paint();
        final android.graphics.Paint cross = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float fx = -1, fy = -1;
        final int[] loc = new int[2];

        PickerView(Context c) {
            super(c);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(Ui.dp(c, 15));
            text.setFakeBoldText(true);
            bg.setColor(0xEE16124A);
            cross.setColor(Ui.ACCENT);
            cross.setStrokeWidth(Ui.dp(c, 2));
            cross.setStyle(android.graphics.Paint.Style.STROKE);
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            int h = Ui.dp(getContext(), 72);
            c.drawRect(0, 0, getWidth(), h, bg);
            c.drawText("Atinge punctul a cărui culoare o vrei", Ui.dp(getContext(), 16), h * 0.42f, text);
            c.drawText("(atinge această bară ca să anulezi)", Ui.dp(getContext(), 16), h * 0.78f, text);
            if (fx >= 0) {
                float r = Ui.dp(getContext(), 28);
                c.drawCircle(fx, fy, r, cross);
                c.drawLine(fx - r * 1.5f, fy, fx - r * 0.3f, fy, cross);
                c.drawLine(fx + r * 0.3f, fy, fx + r * 1.5f, fy, cross);
                c.drawLine(fx, fy - r * 1.5f, fx, fy - r * 0.3f, cross);
                c.drawLine(fx, fy + r * 0.3f, fx, fy + r * 1.5f, cross);
            }
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            fx = e.getX(); fy = e.getY();
            invalidate();
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                if (e.getY() < Ui.dp(getContext(), 72)) { cancelPicker(); return true; }
                getLocationOnScreen(loc);
                onPicked(e.getX() + loc[0], e.getY() + loc[1]);
            }
            return true;
        }
    }

    // ================================================================ activity log
    private void addLog(String line) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        log.addLast(ts + "  " + line);
        while (log.size() > 300) log.removeFirst();
    }

    private void showLog() {
        StringBuilder sb = new StringBuilder();
        if (log.isEmpty()) sb.append("Nimic încă. Rulează și revino aici.");
        for (String l : log) sb.append(l).append('\n');
        Context dc = Overlays.dialogContext(this);
        android.widget.TextView t = new android.widget.TextView(dc);
        t.setText(sb);
        t.setTextSize(12);
        t.setTextColor(Ui.TEXT);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        int p = Ui.dp(this, 16);
        t.setPadding(p, p, p, p);
        android.widget.ScrollView sv = new android.widget.ScrollView(dc);
        sv.addView(t);
        showDialog(new android.app.AlertDialog.Builder(dc).setTitle("Jurnal activitate")
                .setView(sv).setPositiveButton("OK", null).create());
    }

    // ================================================================ helpers
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void showDialog(android.app.AlertDialog d) {
        dismissDialog();
        openDialog = Overlays.showOverlayDialog(d);
    }

    private void showPopup(View anchor, List<String> labels, List<Integer> icons, Ui.OnPick cb) {
        if (openPopup != null) { try { openPopup.dismiss(); } catch (RuntimeException ignored) { } }
        openPopup = Ui.popup(anchor, labels, icons, cb);
    }

    private void dismissDialog() {
        if (openDialog != null) {
            try { openDialog.dismiss(); } catch (RuntimeException ignored) { }
            openDialog = null;
        }
        if (openPopup != null) {
            try { openPopup.dismiss(); } catch (RuntimeException ignored) { }
            openPopup = null;
        }
    }
}
