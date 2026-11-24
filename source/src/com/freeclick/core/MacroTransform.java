package com.freeclick.core;

/** In-place geometric edits of a macro. */
public final class MacroTransform {
    private MacroTransform() {}

    /** At most this many fingers at once (Android allows more, hands do not). */
    public static final int MAX_SYNC_FINGERS = 10;

    /**
     * 2.3 "Atinge toate deodată": taps that follow one another (on, repeated once) become one gesture
     * with a finger on each, all down at the same moment; everything else stays as it was, in order.
     * Returns a new list (the original is not changed); blocks are handled inside too.
     */
    public static java.util.List<Action> syncTaps(java.util.List<Action> in) {
        java.util.List<Action> out = new java.util.ArrayList<Action>();
        java.util.List<Action> group = new java.util.ArrayList<Action>();
        for (Action a : in) { // tree-ok: blocks are handled by the recursion below
            if (a.type == Action.Type.TAP && a.enabled && a.repeat <= 1 && group.size() < MAX_SYNC_FINGERS) {
                group.add(a);
                continue;
            }
            flushSync(group, out);
            if (a.type == Action.Type.TAP && a.enabled && a.repeat <= 1) { group.add(a); continue; } // the 11th starts a new group
            Action c = a;
            if (a.children != null || a.elseChildren != null) {
                c = a.copy();
                if (c.children != null) c.children = syncTaps(c.children);
                if (c.elseChildren != null) c.elseChildren = syncTaps(c.elseChildren);
            }
            out.add(c);
        }
        flushSync(group, out);
        return out;
    }

    private static void flushSync(java.util.List<Action> group, java.util.List<Action> out) {
        if (group.size() == 1) out.add(group.get(0));
        if (group.size() >= 2) {
            java.util.List<Stroke> fingers = new java.util.ArrayList<Stroke>();
            for (Action t : group) fingers.add(new Stroke().add(t.x, t.y, 0).add(t.x, t.y, Math.max(1, t.duration)));
            Action g = Action.gesture(fingers);
            g.delay = group.get(0).delay;
            g.label = group.size() + " atingeri deodată";
            out.add(g);
        }
        group.clear();
    }

    /**
     * Manual mode "apply to all": every target gets the same pause before it,
     * every tap the same press time and every swipe the same duration
     * (long presses keep their own press time). Settings remember the values
     * for targets added later.
     */
    public static void applyManualTiming(Macro m, long interval, long tapMs, long swipeMs) {
        m.settings.tapInterval = Math.max(0, interval);
        m.settings.tapDuration = Math.max(1, tapMs);
        m.settings.swipeDuration = Math.max(1, swipeMs);
        for (Action a : ActionTree.all(m.actions)) {
            switch (a.type) {
                case TAP:
                case DOUBLE_TAP:
                    a.delay = m.settings.tapInterval;
                    a.duration = m.settings.tapDuration;
                    break;
                case LONG_PRESS:
                    a.delay = m.settings.tapInterval;
                    break;
                case SWIPE:
                    a.delay = m.settings.tapInterval;
                    a.duration = m.settings.swipeDuration;
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Convert every coordinate from the macro's recorded screen size to the
     * given one (used when a macro is opened in the panel on a different
     * screen/orientation, so markers appear where taps will really land).
     * @return true if anything changed.
     */
    public static boolean rescaleToScreen(Macro m, int width, int height) {
        if (width <= 0 || height <= 0) return false;
        if (m.screenWidth <= 0 || m.screenHeight <= 0) {
            m.screenWidth = width;
            m.screenHeight = height;
            return false;
        }
        CoordinateScaler s = CoordinateScaler.of(m.screenWidth, m.screenHeight, width, height, true);
        m.screenWidth = width;
        m.screenHeight = height;
        if (s.isIdentity()) return false;
        for (Action a : ActionTree.all(m.actions)) {
            a.x = s.x(a.x); a.y = s.y(a.y);
            a.x2 = s.x(a.x2); a.y2 = s.y(a.y2);
            for (Stroke st : a.strokes) {
                for (float[] p : st.points) { p[0] = s.x(p[0]); p[1] = s.y(p[1]); }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ recording tools
    /**
     * "Scurtează pauza de început": the wait before the first enabled step becomes at most maxMs.
     * @return the old pause, or -1 if nothing changed.
     */
    public static long shortenFirstPause(java.util.List<Action> list, long maxMs) {
        for (Action a : ActionTree.leaves(list)) {
            if (!a.enabled) continue;
            if (a.delay <= maxMs) return -1;
            long old = a.delay;
            a.delay = Math.max(0, maxMs);
            return old;
        }
        return -1;
    }

    /**
     * "Scurtează pauzele lungi": every pause longer than maxMs (before a step, between repeats,
     * or a Pause step) becomes maxMs. Random pauses keep their spread inside the limit.
     * @return how many pauses were shortened.
     */
    public static int capPauses(java.util.List<Action> list, long maxMs) {
        maxMs = Math.max(0, maxMs);
        int n = 0;
        for (Action a : ActionTree.all(list)) {
            if (a.delay > maxMs) { a.delay = maxMs; n++; }
            if (a.repeat > 1 && a.interval > maxMs) { a.interval = maxMs; n++; }
            if (a.type == Action.Type.WAIT) {
                if (a.isRandomWait() && a.durationMax > maxMs) {
                    a.durationMax = maxMs;
                    if (a.duration >= a.durationMax) { a.duration = maxMs; a.durationMax = 0; }
                    n++;
                } else if (!a.isRandomWait() && a.duration > maxMs) {
                    a.duration = maxMs;
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * "Accelerează": the given steps play `factor` times faster, like the speed setting does:
     * pauses, swipes and recorded paths get shorter; taps and long presses keep their press time
     * (so they still register as the same kind of touch).
     */
    public static void speedUp(java.util.List<Action> steps, double factor) {
        if (factor <= 0 || factor == 1) return;
        for (Action a : ActionTree.all(steps)) {
            a.delay = Math.round(a.delay / factor);
            a.interval = Math.round(a.interval / factor);
            switch (a.type) {
                case SWIPE:
                    a.duration = Math.max(1, Math.round(a.duration / factor));
                    break;
                case WAIT:
                    a.duration = Math.round(a.duration / factor);
                    if (a.durationMax > 0) a.durationMax = Math.max(a.duration + 1, Math.round(a.durationMax / factor));
                    break;
                case GESTURE: {
                    long end = 0;
                    for (Stroke s : a.strokes) {
                        for (int i = 0; i < s.times.size(); i++) s.times.set(i, Math.round(s.times.get(i) / factor));
                        end = Math.max(end, s.endTime());
                    }
                    a.duration = Math.max(1, end);
                    break;
                }
                default:
                    break;
            }
        }
    }
}
