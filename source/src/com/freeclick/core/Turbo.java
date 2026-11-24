package com.freeclick.core;

import java.util.List;
import java.util.Random;

/**
 * 🔥 Turbo (2.2), the part that does not need Android: what a touch means and the taps to send.
 * <ul>
 *   <li>moved more than the touch slop → {@link #SWIPE}: the swipe goes through unchanged;</li>
 *   <li>held at least {@link #HOLD_MS} (and "hold = non-stop" is on) → {@link #HOLD}: taps until the next touch;</li>
 *   <li>short tap → {@link #BURST} taps, or {@link #PASS} (one ordinary tap) when the burst is 0.</li>
 * </ul>
 */
public final class Turbo {
    private Turbo() {}

    public static final int SWIPE = 0, HOLD = 1, BURST = 2, PASS = 3;
    public static final long HOLD_MS = 450;
    public static final int MIN_RATE = 5, MAX_RATE = 60;

    public static int classify(double movedPx, double slopPx, long heldMs, boolean holdEnabled, int burst) {
        if (movedPx > slopPx) return SWIPE;
        if (heldMs >= HOLD_MS && holdEnabled) return HOLD;
        return burst > 0 ? BURST : PASS;
    }

    /** Milliseconds between two taps at {@code rate} taps per second (clamped to 5..60/s). */
    public static long period(int rate) {
        return 1000L / Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }

    /** How long each finger stays down: half the period, between 8 and 40 ms (games miss shorter taps). */
    public static long press(long period) {
        return Math.max(8, Math.min(40, period / 2));
    }

    /**
     * One gesture with {@code n} taps on (x, y), {@code period} ms apart, each moved by up to ±spread px,
     * all kept on the screen (w × h; 0 = unknown). Android takes at most {@code maxStrokes} fingers per
     * gesture, so n is capped there; the caller sends the next batch when this one completes.
     */
    public static GesturePlan taps(float x, float y, int n, int rate, int spread, int maxStrokes, int w, int h, Random r) {
        GesturePlan plan = new GesturePlan();
        long period = period(rate), press = press(period);
        int count = Math.max(0, Math.min(n, Math.max(1, maxStrokes)));
        for (int i = 0; i < count; i++) {
            GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(i * period, press);
            float dx = 0, dy = 0;
            if (spread > 0) {
                dx = (r.nextFloat() * 2 - 1) * spread;
                dy = (r.nextFloat() * 2 - 1) * spread;
            }
            s.points.add(new float[]{clamp(x + dx, w), clamp(y + dy, h)});
            plan.strokes.add(s);
        }
        return plan;
    }

    /**
     * Your swipe as one stroke: at most ~30 points, the real duration (60 ms .. 3 s), the last point kept.
     * Each point is {x, y, ms since the finger went down}.
     */
    public static GesturePlan swipe(List<float[]> pts, int w, int h) {
        GesturePlan plan = new GesturePlan();
        if (pts == null || pts.size() < 2) return plan;
        float[] first = pts.get(0), last = pts.get(pts.size() - 1);
        long ms = Math.max(60, Math.min(3000, (long) (last[2] - first[2])));
        GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(0, ms);
        int step = Math.max(1, pts.size() / 30);
        for (int i = 0; i < pts.size() - 1; i += step) s.points.add(new float[]{clamp(pts.get(i)[0], w), clamp(pts.get(i)[1], h)});
        s.points.add(new float[]{clamp(last[0], w), clamp(last[1], h)});
        plan.strokes.add(s);
        return plan;
    }

    /** Real taps per second so far (what the bar shows). */
    public static int measuredRate(int done, long elapsedMs) {
        return elapsedMs <= 0 ? 0 : (int) Math.round(done * 1000.0 / elapsedMs);
    }

    private static float clamp(float v, int size) {
        if (v < 0) return 0;
        return size > 0 && v > size - 1 ? size - 1 : v;
    }
}
