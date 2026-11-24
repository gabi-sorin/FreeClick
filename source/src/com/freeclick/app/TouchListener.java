package com.freeclick.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.freeclick.core.GesturePlan;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.TouchTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How FreeClick hears your fingers (2.3), for scripts (asteaptaAtingere(), degete()…).
 * <ul>
 *   <li><b>Direct</b> (Android 15+, if the phone allows it, checked once in Settings): Android sends
 *       FreeClick a copy of every touch while the game gets it as usual. Nothing is in the way.</li>
 *   <li><b>Layer</b> (everywhere else): only while a script listens, a see-through layer catches your
 *       gesture and, when your fingers lift, FreeClick plays it on to the game (taps, swipes, several
 *       fingers). The game gets it a moment later, and a finger held down is only passed on when lifted.</li>
 * </ul>
 * Safety for the direct way: if a touch on FreeClick's own panel is heard but never reaches the panel,
 * touches are being held back, so the direct way turns itself off at once.
 */
final class TouchListener {
    interface Host {
        void inject(GesturePlan plan, PlaybackEngine.GestureCallback cb);
        int maxStrokes();
        int[] panelRect();
        long panelTouchedAt();
        void raisePanel();
        /** A script started / stopped listening (the panel shows 👂). */
        void listeningChanged(boolean on);
        void toast(String s);
        void log(String s);
    }

    final TouchTracker tracker = new TouchTracker();
    private final Context ctx;
    private final WindowManager wm;
    private final Handler main;
    private final Host host;
    private boolean direct;            // Android sends us copies right now
    private boolean listening, forward = true;
    private CatchView layer;

    TouchListener(Context ctx, WindowManager wm, Handler main, Host host) {
        this.ctx = ctx;
        this.wm = wm;
        this.main = main;
        this.host = host;
        tracker.slopPx = Ui.dp(ctx, 12);
        tracker.doubleDistPx = Ui.dp(ctx, 40);
    }

    // ------------------------------------------------------------------ the direct way (Android 15+)
    /** Whether this Android has the "observe touches" switch at all. */
    static boolean directAvailable() {
        if (Build.VERSION.SDK_INT < 35) return false;
        try {
            AccessibilityServiceInfo.class.getMethod("setObservedMotionEventSources", int.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean isDirect() { return directAllowed; }

    private AccessibilityService svc;
    private boolean directAllowed;     // the test in Settings said it works (or it is being tested)
    private boolean testing;

    /**
     * Allows (or forbids) Android's copies. They are only asked for while a script listens (or during the
     * test in Settings): with nothing running, FreeClick hears nothing. False if this phone cannot.
     */
    boolean allowDirect(AccessibilityService s, boolean on) {
        svc = s;
        directAllowed = on && directAvailable();
        apply();
        return directAllowed || !on;
    }

    /** Settings → Testează: copies on right now, without a script. */
    boolean testDirect(AccessibilityService s, boolean on) {
        svc = s;
        testing = on;
        if (on) directAllowed = directAvailable();
        apply();
        return !on || direct;
    }

    private void apply() {
        boolean want = directAllowed && (listening || testing);
        if (want != direct && svc != null && !observe(svc, want)) direct = false;
        syncLayer();
    }

    private boolean lab;

    /** 🧪 The Laboratory measures the layer itself: it goes up even with direct listening allowed. */
    void labLayer(boolean on) {
        lab = on;
        syncLayer();
    }

    /** The layer is only needed while a script listens and Android does not send copies (or the lab measures it). */
    private void syncLayer() {
        if (lab || (listening && !direct)) addLayer(); else removeLayer();
    }

    private boolean observe(AccessibilityService s, boolean on) {
        try {
            AccessibilityServiceInfo info = s.getServiceInfo();
            if (info == null) return false;
            int src = on ? InputDevice.SOURCE_TOUCHSCREEN : 0;
            if (on) { // observed first: touch events must never be taken away from the apps
                AccessibilityServiceInfo.class.getMethod("setMotionEventSources", int.class).invoke(info, src);
                AccessibilityServiceInfo.class.getMethod("setObservedMotionEventSources", int.class).invoke(info, src);
            } else {
                if (directAvailable()) AccessibilityServiceInfo.class.getMethod("setObservedMotionEventSources", int.class).invoke(info, 0);
                if (Build.VERSION.SDK_INT >= 34) AccessibilityServiceInfo.class.getMethod("setMotionEventSources", int.class).invoke(info, 0);
            }
            s.setServiceInfo(info);
            direct = on;
            return true;
        } catch (Exception e) {
            direct = false;
            return false;
        }
    }

    /** A copy of a touch from Android (direct way). */
    void onMotionEvent(MotionEvent e) {
        if (!direct || e == null) return;
        if (e.getDeviceId() == 0) return; // taps made by FreeClick itself (Android injects them as device 0), not your fingers
        feed(e, 0, 0);
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) safetyCheck(e.getX(), e.getY(), e.getEventTime());
    }

    /** A touch on our panel that never reaches it means Android is holding touches back: stop at once. */
    private void safetyCheck(float x, float y, final long downAt) {
        int[] r = host.panelRect();
        if (r == null || x < r[0] || x > r[2] || y < r[1] || y > r[3]) return;
        main.postDelayed(new Runnable() { public void run() {
            if (!direct || host.panelTouchedAt() >= downAt - 50) return;
            onBlocked();
        } }, 700);
    }

    private Runnable onBlocked;

    void setOnBlocked(Runnable r) { onBlocked = r; }

    private void onBlocked() {
        if (onBlocked != null) onBlocked.run();
        host.toast("Ascultarea directă a atingerilor nu merge bine pe telefonul ăsta: am oprit-o.");
        host.log("Ascultarea directă oprită (atingerile nu ajungeau la ecran)");
    }

    // ------------------------------------------------------------------ what scripts ask
    /** A script listens (or stops). {@code pass}: gestures caught by the layer are played on to the game. */
    void setListening(boolean on, boolean pass) {
        forward = pass;
        if (on == listening) return;
        listening = on;
        apply(); // copies from Android if allowed, else the layer; nothing at all when no script listens
        host.listeningChanged(on);
    }

    boolean isListening() { return listening; }

    // ------------------------------------------------------------------ the layer (everywhere else)
    private int[] zone;

    /** 2.5 ascultaInZona: only gestures starting there are heard, and the layer covers only that zone (null = all). */
    void setZone(int[] z) {
        zone = z;
        tracker.setZone(z);
        if (layer != null) Overlays.safeUpdate(wm, layer, layerParams());
    }

    private WindowManager.LayoutParams layerParams() {
        if (zone == null) return Overlays.params(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true, false);
        WindowManager.LayoutParams lp = Overlays.params(Math.max(1, zone[2] - zone[0]), Math.max(1, zone[3] - zone[1]), true, false);
        lp.x = zone[0];
        lp.y = zone[1];
        return lp;
    }

    private void addLayer() {
        if (layer != null) return;
        layer = new CatchView(ctx);
        Overlays.safeAdd(wm, layer, layerParams());
        host.raisePanel(); // ■ on the panel stays reachable
    }

    private void removeLayer() {
        if (layer != null) Overlays.safeRemove(wm, layer);
        layer = null;
    }

    void shutdown() {
        listening = false;
        testing = false;
        apply();
        removeLayer();
    }

    private void feed(MotionEvent e, float dx, float dy) {
        int a = e.getActionMasked();
        long t = e.getEventTime();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                tracker.feed(TouchTracker.DOWN, e.getPointerId(i), e.getX(i) + dx, e.getY(i) + dy, t);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) tracker.feed(TouchTracker.MOVE, e.getPointerId(i), e.getX(i) + dx, e.getY(i) + dy, t);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                tracker.feed(TouchTracker.UP, e.getPointerId(i), e.getX(i) + dx, e.getY(i) + dy, t);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                tracker.feed(TouchTracker.CANCEL, 0, 0, 0, t);
                break;
            default:
                break;
        }
    }

    /** Catches a whole gesture (all fingers), then plays it on to the game. */
    private final class CatchView extends View {
        private final Map<Integer, List<float[]>> paths = new HashMap<Integer, List<float[]>>();
        private long start;
        private final int[] loc = new int[2];

        CatchView(Context c) { super(c); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            getLocationOnScreen(loc);
            feed(e, loc[0], loc[1]);
            int a = e.getActionMasked();
            long t = e.getEventTime();
            if (a == MotionEvent.ACTION_DOWN) { paths.clear(); start = t; }
            if (a == MotionEvent.ACTION_CANCEL) { paths.clear(); return true; }
            for (int i = 0; i < e.getPointerCount(); i++) {
                boolean thisOne = (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN
                        || a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) ? i == e.getActionIndex() : a == MotionEvent.ACTION_MOVE;
                if (!thisOne) continue;
                int id = e.getPointerId(i);
                List<float[]> p = paths.get(id);
                if (p == null) { p = new ArrayList<float[]>(); paths.put(id, p); }
                p.add(new float[]{e.getX(i) + loc[0], e.getY(i) + loc[1], t - start});
            }
            if (a == MotionEvent.ACTION_UP) passOn(new ArrayList<List<float[]>>(paths.values()));
            return true;
        }
    }

    private void passOn(List<List<float[]>> fingers) {
        if (!forward || fingers.isEmpty()) return;
        GesturePlan plan = new GesturePlan();
        for (List<float[]> p : fingers) {
            if (p.isEmpty() || plan.strokes.size() >= Math.max(1, Math.min(10, host.maxStrokes()))) continue;
            float[] first = p.get(0), last = p.get(p.size() - 1);
            long begin = (long) first[2], dur = Math.max(1, Math.min(9000, (long) (last[2] - first[2])));
            GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(Math.max(0, begin), dur);
            int step = Math.max(1, p.size() / 30);
            for (int i = 0; i < p.size() - 1; i += step) s.points.add(new float[]{p.get(i)[0], p.get(i)[1]});
            s.points.add(new float[]{last[0], last[1]});
            plan.strokes.add(s);
        }
        if (plan.strokes.isEmpty()) return;
        final CatchView mine = layer;
        if (mine == null) return;
        Overlays.setTouchable(wm, mine, false); // the replay must land on the game, not on us
        final GesturePlan go = plan;
        main.postDelayed(new Runnable() { public void run() {
            host.inject(go, new PlaybackEngine.GestureCallback() {
                @Override public void onResult(boolean completed) {
                    if (layer == mine && ownGestures == 0) Overlays.setTouchable(wm, mine, true);
                }
            });
        } }, 50);
    }

    private int ownGestures;

    /** The script taps while the layer listens: the layer lets it through. False if there is no layer. */
    boolean lowerForOwnGesture() {
        if (layer == null) return false;
        ownGestures++;
        Overlays.setTouchable(wm, layer, false);
        return true;
    }

    void ownGestureDone() {
        ownGestures = Math.max(0, ownGestures - 1);
        if (ownGestures == 0 && layer != null) Overlays.setTouchable(wm, layer, true);
    }

    /** Settings → test: whether Android's copies arrive (events counted since). */
    long heard() { return tracker.events(); }

    static long now() { return SystemClock.uptimeMillis(); }
}
