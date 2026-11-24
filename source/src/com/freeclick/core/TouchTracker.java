package com.freeclick.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Your fingers on the screen, as FreeClick hears them (2.3): how many are down now and where, and the
 * gestures you made (tap, double tap, long press, swipe with its direction, a tap with several fingers).
 * Scripts read it with asteaptaAtingere(), asteaptaGest(), degete()… The app feeds it either from
 * Android's touch observing (the game gets the touch too) or from FreeClick's own listening layer
 * (which passes the gesture on to the game when your fingers lift). Thread-safe: fed on the main
 * thread, read from the script thread.
 */
public final class TouchTracker {
    public static final int DOWN = 0, MOVE = 1, UP = 2, CANCEL = 3;
    public static final String TAP = "tap", DOUBLE = "dublu", LONG = "lung", SWIPE = "swipe", MULTI = "degete",
            ZOOM = "zoom", ROTATE = "rotire";

    /** One finished gesture. Coordinates are screen pixels. */
    public static final class Gesture {
        public final long seq;
        public final String type;
        /** "sus", "jos", "stanga", "dreapta" for a swipe, "" otherwise. */
        public final String direction;
        public final int fingers;
        public final int x1, y1, x2, y2;
        public final long start, end;
        /** 2.4: every finger's path, {x, y, ms since the gesture began}, in the order the fingers went down. */
        public final List<List<float[]>> paths;
        /** 2.5: a tap's place in a run of quick taps (1, 2, 3…); two fingers: how much they spread (×) and turned (°). */
        public int count = 1;
        public double scale = 1, rotation = 0;

        Gesture(long seq, String type, String direction, int fingers, int x1, int y1, int x2, int y2, long start, long end) {
            this(seq, type, direction, fingers, x1, y1, x2, y2, start, end, new ArrayList<List<float[]>>());
        }

        Gesture(long seq, String type, String direction, int fingers, int x1, int y1, int x2, int y2, long start, long end,
                List<List<float[]>> paths) {
            this.seq = seq; this.type = type; this.direction = direction; this.fingers = fingers;
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.start = start; this.end = end; this.paths = paths;
        }

        /** What asteaptaGest() returns: "tap", "dublu", "lung", "swipe sus", "2 degete"… */
        public String describe() {
            if (SWIPE.equals(type)) return (fingers > 1 ? fingers + " degete " : "") + SWIPE + " " + direction;
            if (MULTI.equals(type)) return fingers + " " + MULTI;
            if (ZOOM.equals(type) || ROTATE.equals(type)) return type + " " + direction;
            return type;
        }

        public long duration() { return end - start; }
    }

    /** How far a finger may move and still be a tap (px), how long a long press lasts, a double tap's gap. */
    public float slopPx = 30;
    public long longMs = 500, doubleMs = 350;
    public float doubleDistPx = 120;
    /** Two fingers: turned at least this much = rotire; spread / pinched by this factor = zoom. */
    public double rotateDeg = 25, zoomFactor = 1.25;
    private int tapRun;
    private long tapRunEnd = -1;
    private int tapRunX, tapRunY;

    private final Map<Integer, float[]> down = new LinkedHashMap<Integer, float[]>(); // id → {x, y, startX, startY, downTime}
    /** The paths of the gesture being made, finger by finger (kept for inregistreaza()). */
    private final Map<Integer, List<float[]>> paths = new LinkedHashMap<Integer, List<float[]>>();
    private final List<List<float[]>> donePaths = new ArrayList<List<float[]>>();
    /** At most this many points per finger (a long hold is thinned, never cut). */
    static final int MAX_POINTS = 400;
    private int maxFingers;
    private long gestureStart;
    private float startX, startY, lastX, lastY, maxMove;
    private int firstId = -1;
    private final List<Gesture> recent = new ArrayList<Gesture>();
    private long seq;
    private final long[] lastAtLeast = new long[11];
    { java.util.Arrays.fill(lastAtLeast, -1); }
    private long lastZero = -1;
    private Gesture lastTap;
    private int lastX0 = -1, lastY0 = -1;
    private long events;

    /** 2.5: only gestures that start in this zone are heard (null = the whole screen). */
    private int[] zone;
    private final java.util.Set<Integer> ignored = new java.util.HashSet<Integer>();

    public synchronized void setZone(int[] z) { zone = z; }

    /** A finger event (screen pixels, ms on any monotonic clock). */
    public synchronized void feed(int action, int id, float x, float y, long t) {
        events++;
        if (action != CANCEL && zone != null) { // a gesture that starts outside the zone is not heard at all
            if (action == DOWN && down.isEmpty() && ignored.isEmpty()
                    && (x < zone[0] || x > zone[2] || y < zone[1] || y > zone[3])) ignored.add(id);
            else if (action == DOWN && !ignored.isEmpty()) ignored.add(id);
            if (ignored.contains(id)) {
                if (action == UP) ignored.remove(id);
                return;
            }
        }
        if (action == CANCEL) ignored.clear();
        if (action == CANCEL) {
            down.clear();
            paths.clear();
            donePaths.clear();
            maxFingers = 0;
            firstId = -1;
            lastZero = t;
            notifyAll();
            return;
        }
        if (action == DOWN) {
            if (down.isEmpty()) {
                gestureStart = t;
                paths.clear();
                donePaths.clear();
                startX = lastX = x;
                startY = lastY = y;
                maxMove = 0;
                maxFingers = 0;
                firstId = id;
            }
            down.put(id, new float[]{x, y, x, y, t});
            List<float[]> p = new ArrayList<float[]>();
            p.add(new float[]{x, y, t - gestureStart});
            paths.put(id, p);
            maxFingers = Math.max(maxFingers, down.size());
            lastX0 = Math.round(x);
            lastY0 = Math.round(y);
        } else {
            float[] f = down.get(id);
            if (f == null) return;
            f[0] = x;
            f[1] = y;
            maxMove = Math.max(maxMove, (float) Math.hypot(x - f[2], y - f[3]));
            if (id == firstId) { lastX = x; lastY = y; }
            addPoint(paths.get(id), x, y, t - gestureStart, action == UP);
            if (action == UP) {
                List<float[]> done = paths.remove(id);
                if (done != null) donePaths.add(done);
                down.remove(id);
                if (down.isEmpty()) finish(t);
            }
        }
        int n = Math.min(down.size(), lastAtLeast.length - 1);
        for (int k = 1; k <= n; k++) lastAtLeast[k] = t;
        if (down.isEmpty()) lastZero = t;
        notifyAll();
    }

    private static void addPoint(List<float[]> p, float x, float y, long t, boolean last) {
        if (p == null) return;
        float[] prev = p.get(p.size() - 1);
        if (!last && t - prev[2] < 8 && Math.hypot(x - prev[0], y - prev[1]) < 2) return; // nothing new
        p.add(new float[]{x, y, t});
        if (p.size() > MAX_POINTS) { // thin every other point, keeping the first and the last
            List<float[]> thin = new ArrayList<float[]>();
            for (int i = 0; i < p.size(); i++) if (i % 2 == 0 || i == p.size() - 1) thin.add(p.get(i));
            p.clear();
            p.addAll(thin);
        }
    }

    private void finish(long t) {
        long dur = t - gestureStart;
        List<List<float[]>> fingersPaths = new ArrayList<List<float[]>>(donePaths);
        donePaths.clear();
        int sx = Math.round(startX), sy = Math.round(startY), ex = Math.round(lastX), ey = Math.round(lastY);
        Gesture g;
        if (maxFingers >= 2 && fingersPaths.size() >= 2) {
            g = twoFingers(fingersPaths, sx, sy, ex, ey, t);
        } else if (maxFingers >= 2) {
            g = new Gesture(++seq, MULTI, "", maxFingers, sx, sy, ex, ey, gestureStart, t, fingersPaths);
        } else if (maxMove > slopPx) {
            g = new Gesture(++seq, SWIPE, direction(lastX - startX, lastY - startY), 1, sx, sy, ex, ey, gestureStart, t, fingersPaths);
        } else if (dur >= longMs) {
            g = new Gesture(++seq, LONG, "", 1, sx, sy, ex, ey, gestureStart, t, fingersPaths);
        } else {
            g = new Gesture(++seq, TAP, "", 1, sx, sy, ex, ey, gestureStart, t, fingersPaths);
            boolean more = tapRunEnd >= 0 && gestureStart - tapRunEnd <= doubleMs && Math.hypot(sx - tapRunX, sy - tapRunY) <= doubleDistPx;
            tapRun = more ? tapRun + 1 : 1;
            tapRunEnd = t;
            tapRunX = sx;
            tapRunY = sy;
            g.count = tapRun;
            add(g);
            if (tapRun == 2) add(new Gesture(++seq, DOUBLE, "", 1, sx, sy, ex, ey, lastTap == null ? gestureStart : lastTap.start, t));
            lastTap = g;
            maxFingers = 0;
            firstId = -1;
            return;
        }
        add(g);
        lastTap = null;
        tapRunEnd = -1;
        maxFingers = 0;
        firstId = -1;
    }

    private static String direction(double dx, double dy) {
        return Math.abs(dx) >= Math.abs(dy) ? (dx >= 0 ? "dreapta" : "stanga") : (dy >= 0 ? "jos" : "sus");
    }

    /**
     * Two (or more) fingers: turned → rotire; spread or pinched → zoom; moved together → a swipe with fingers;
     * otherwise a tap with fingers. Measured on the first two fingers, from when both were down to when they left.
     */
    private Gesture twoFingers(List<List<float[]>> paths, int sx, int sy, int ex, int ey, long t) {
        List<float[]> a = paths.get(0), b = paths.get(1);
        float[] a0 = a.get(0), b0 = b.get(0), a1 = a.get(a.size() - 1), b1 = b.get(b.size() - 1);
        double vax = a1[0] - a0[0], vay = a1[1] - a0[1], vbx = b1[0] - b0[0], vby = b1[1] - b0[1];
        double together = Math.hypot((vax + vbx) / 2, (vay + vby) / 2); // both fingers the same way: a swipe
        double apart = Math.hypot(vbx - vax, vby - vay);                 // against each other: zoom or rotire
        double d0 = Math.max(1, Math.hypot(b0[0] - a0[0], b0[1] - a0[1])), d1 = Math.max(1, Math.hypot(b1[0] - a1[0], b1[1] - a1[1]));
        double ang = Math.toDegrees(Math.atan2(b1[1] - a1[1], b1[0] - a1[0]) - Math.atan2(b0[1] - a0[1], b0[0] - a0[0]));
        while (ang > 180) ang -= 360;
        while (ang <= -180) ang += 360;
        double scale = d1 / d0, arc = Math.abs(ang) * Math.PI / 180 * (d0 + d1) / 2;
        String type = MULTI, dir = "";
        if (Math.max(Math.hypot(vax, vay), Math.hypot(vbx, vby)) <= slopPx && Math.abs(d1 - d0) <= slopPx && arc <= slopPx) {
            type = MULTI; // they stayed put (and did not spread or turn): a tap with fingers
        } else if (together >= apart) {
            type = SWIPE;
            dir = direction((vax + vbx) / 2, (vay + vby) / 2);
        } else if (arc > Math.abs(d1 - d0) && Math.abs(ang) >= rotateDeg) {
            type = ROTATE;
            dir = ang > 0 ? "dreapta" : "stanga";
        } else if ((scale >= zoomFactor || scale <= 1 / zoomFactor) && Math.abs(d1 - d0) > slopPx) {
            type = ZOOM;
            dir = scale > 1 ? "mareste" : "micsoreaza";
        } else if (together > slopPx) {
            type = SWIPE;
            dir = direction((vax + vbx) / 2, (vay + vby) / 2);
        }
        Gesture g = new Gesture(++seq, type, dir, maxFingers, sx, sy, ex, ey, gestureStart, t, paths);
        g.scale = scale;
        g.rotation = ang;
        return g;
    }

    /** Where a finger was at a moment (its first point at or after it). */
    private static float[] at(List<float[]> p, float ms) {
        for (float[] q : p) if (q[2] >= ms) return q;
        return p.get(p.size() - 1);
    }

    /** Gestures newer than {@code after}, "dublu" summaries left out (for gesturiNoi()). */
    public synchronized int gesturesSince(long after) {
        int n = 0;
        for (Gesture g : recent) if (g.seq > after && !DOUBLE.equals(g.type)) n++;
        return n;
    }

    /** The first tap newer than {@code after} that is the n-th of a quick run (n = 3: a triple tap), or null. */
    public synchronized Gesture nthTap(long after, int n) {
        for (Gesture g : recent) if (g.seq > after && TAP.equals(g.type) && g.count == n) return g;
        return null;
    }

    private void add(Gesture g) {
        recent.add(g);
        while (recent.size() > 512) recent.remove(0);
    }

    // ------------------------------------------------------------------ reading (any thread)
    /** The number of the last gesture (a wait remembers it, then looks only at newer ones). */
    public synchronized long seq() { return seq; }

    /** Every gesture newer than {@code after}, in order, without the "dublu" summaries (its two taps are there). */
    public synchronized List<Gesture> since(long after) {
        List<Gesture> out = new ArrayList<Gesture>();
        for (Gesture g : recent) if (g.seq > after && !DOUBLE.equals(g.type)) out.add(g);
        return out;
    }

    /** The first gesture newer than {@code after} of this type (null = any type except "dublu"), or null. */
    public synchronized Gesture next(long after, String type) {
        for (Gesture g : recent) {
            if (g.seq <= after) continue;
            if (type == null ? !DOUBLE.equals(g.type) : type.equals(g.type)) return g;
        }
        return null;
    }

    /** Taps made after {@code after}. */
    public synchronized int tapsSince(long after) {
        int n = 0;
        for (Gesture g : recent) if (g.seq > after && TAP.equals(g.type)) n++;
        return n;
    }

    public synchronized int fingers() { return down.size(); }

    /** Where finger i (0 = the first one down) is now, or null. */
    public synchronized int[] finger(int i) {
        int k = 0;
        for (float[] f : down.values()) {
            if (k++ == i) return new int[]{Math.round(f[0]), Math.round(f[1])};
        }
        return null;
    }

    /** Where the last finger went down ({-1, -1} if never). */
    public synchronized int[] lastDown() { return new int[]{lastX0, lastY0}; }

    /** When at least n fingers were last on the screen at once (-1: not yet). */
    public synchronized long lastAtLeast(int n) {
        if (n <= 0) return Long.MAX_VALUE;
        return n >= lastAtLeast.length ? -1 : lastAtLeast[n];
    }

    /** When the screen was last without fingers (-1: not yet). */
    public synchronized long lastZero() { return lastZero; }

    /** How many finger events arrived so far (to know whether listening works at all). */
    public synchronized long events() { return events; }

    /** Forget everything (a new script starts). Fingers still down stay down. */
    public synchronized void clearHistory() {
        recent.clear();
        lastTap = null;
    }
}
