package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw touch events (from the full-screen capture overlay) into macro
 * actions. One "gesture" lasts from the first finger down until the last
 * finger up; it becomes one Action:
 *   - one finger, no movement, short  -> TAP
 *   - one finger, no movement, long   -> LONG_PRESS
 *   - one finger, straight movement   -> SWIPE
 *   - anything else (curves, several fingers) -> GESTURE with every stroke
 * The pause since the previous action is stored as the action delay, so
 * playback at 1x reproduces the original rhythm.
 */
public final class GestureRecorder {

    public static final class Config {
        public float touchSlopPx = 24f;
        public long longPressMs = 400;
        public double simplifyEpsilonPx = 1.5;
        public int maxPointsPerStroke = 150;
        /**
         * Optional cap for the very first delay (time from pressing record to the first
         * touch). No cap by default: the initial pause is part of the recording.
         */
        public long firstDelayCapMs = Long.MAX_VALUE;
        /** A straight-ish single stroke whose points stay within this distance of the line becomes a SWIPE. */
        public double straightTolerancePx = 6.0;
    }

    private final Config cfg;
    private final List<Action> actions = new ArrayList<Action>();
    private final Map<Integer, Stroke> active = new HashMap<Integer, Stroke>();
    private final List<Stroke> finished = new ArrayList<Stroke>();
    private long recordingStart;
    private long lastEnd;
    private long gestureStart = -1;
    private boolean recording;

    public GestureRecorder(Config cfg) { this.cfg = cfg == null ? new Config() : cfg; }

    public void start(long now) {
        actions.clear();
        active.clear();
        finished.clear();
        recordingStart = now;
        lastEnd = now;
        gestureStart = -1;
        recording = true;
    }

    public boolean isRecording() { return recording; }
    public boolean isInGesture() { return gestureStart >= 0; }
    public List<Action> actions() { return actions; }

    /** Finish recording; a gesture still in progress is discarded. */
    public List<Action> stop() {
        recording = false;
        active.clear();
        finished.clear();
        gestureStart = -1;
        return new ArrayList<Action>(actions);
    }

    public void pointerDown(int id, float x, float y, long t) {
        if (!recording) return;
        if (gestureStart < 0) {
            gestureStart = t;
            finished.clear();
        }
        Stroke old = active.remove(id);
        if (old != null) finished.add(old);
        active.put(id, new Stroke().add(x, y, t));
    }

    public void pointerMove(int id, float x, float y, long t) {
        if (!recording) return;
        Stroke s = active.get(id);
        if (s == null) return;
        // Ignore samples that do not move and do not advance time (duplicates).
        int last = s.size() - 1;
        if (s.x(last) == x && s.y(last) == y) return;
        s.add(x, y, Math.max(t, s.endTime()));
    }

    /** @return the finished Action when the last finger lifts, otherwise null. */
    public Action pointerUp(int id, float x, float y, long t) {
        if (!recording) return null;
        Stroke s = active.remove(id);
        if (s != null) {
            int last = s.size() - 1;
            if (s.x(last) != x || s.y(last) != y || t > s.endTime()) s.add(x, y, Math.max(t, s.endTime()));
            finished.add(s);
        }
        if (!active.isEmpty() || gestureStart < 0) return null;
        return completeGesture();
    }

    /** Touch stream cancelled by the system: drop the gesture in progress. */
    public void cancelGesture() {
        active.clear();
        finished.clear();
        gestureStart = -1;
    }

    /** Insert a system action (Back/Home/...) pressed from the panel while recording. */
    public Action addGlobal(int globalAction, long now) {
        if (!recording) return null;
        Action a = Action.global(globalAction);
        a.delay = nextDelay(now);
        lastEnd = now;
        actions.add(a);
        return a;
    }

    /**
     * Insert a step that happens "now" (e.g. "wait for this image", cut out from the panel while
     * recording). The time the user then spends cutting it out is not recorded: call
     * {@link #skipTime} with it afterwards.
     */
    public Action addStep(Action a, long now) {
        if (!recording || a == null) return null;
        a.delay = nextDelay(now);
        lastEnd = now;
        actions.add(a);
        return a;
    }

    /** Leave out a stretch of time (the user was busy with FreeClick, not with the app). */
    public void skipTime(long ms) {
        if (recording && ms > 0 && gestureStart < 0) lastEnd += ms;
    }

    private long nextDelay(long start) {
        long d = Math.max(0, start - lastEnd);
        if (actions.isEmpty()) d = Math.min(d, cfg.firstDelayCapMs);
        return d;
    }

    private Action completeGesture() {
        List<Stroke> strokes = new ArrayList<Stroke>(finished);
        long start = gestureStart;
        long end = start;
        for (Stroke s : strokes) end = Math.max(end, s.endTime());
        finished.clear();
        gestureStart = -1;
        if (strokes.isEmpty()) return null;

        Action a = classify(strokes, start);
        a.delay = nextDelay(start);
        lastEnd = end;
        actions.add(a);
        return a;
    }

    private Action classify(List<Stroke> strokes, long start) {
        for (Stroke s : strokes) s.shiftTime(start);
        if (strokes.size() == 1) {
            Stroke s = strokes.get(0);
            long dur = Math.max(1, s.duration());
            if (s.maxDisplacement() <= cfg.touchSlopPx) {
                float cx = s.x(0), cy = s.y(0);
                if (dur >= cfg.longPressMs) return Action.longPress(cx, cy, dur);
                Action tap = Action.tap(cx, cy);
                tap.duration = dur;
                return tap;
            }
            Stroke simple = s.simplified(cfg.straightTolerancePx, cfg.maxPointsPerStroke);
            if (simple.size() == 2) {
                return Action.swipe(s.x(0), s.y(0), s.x(s.size() - 1), s.y(s.size() - 1), dur);
            }
        }
        List<Stroke> out = new ArrayList<Stroke>();
        for (Stroke s : strokes) {
            Stroke simple = s.simplified(cfg.simplifyEpsilonPx, cfg.maxPointsPerStroke);
            // A stroke must last at least 1 ms to be injectable.
            if (simple.size() == 1) simple.add(simple.x(0), simple.y(0), simple.startTime() + Math.max(1, s.duration()));
            out.add(simple);
        }
        return Action.gesture(out);
    }
}
