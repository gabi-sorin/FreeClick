package com.freeclick.core;

import java.util.Random;

/**
 * Turns an Action into a GesturePlan, applying speed, coordinate scaling,
 * position jitter and Android's gesture limits.
 *
 * Speed rules (chosen so slow motion / fast forward never changes what kind of
 * touch the app sees):
 *  - TAP, LONG_PRESS and DOUBLE_TAP keep their press times (a tap slowed down
 *    10x would otherwise turn into a long press);
 *  - SWIPE and recorded GESTURE movement is scaled by 1/speed.
 */
public final class GesturePlanner {

    public static final class Limits {
        /** GestureDescription.getMaxStrokeCount() on the device (20 on most phones). */
        public int maxStrokes = 20;
        /** GestureDescription.getMaxGestureDuration() (60 s). */
        public long maxDuration = 60000;
        /** Current screen size, used to keep points on-screen (0 = unknown). */
        public int screenWidth;
        public int screenHeight;
    }

    private GesturePlanner() {}

    /** @return the plan, or null if the action is not a gesture. */
    public static GesturePlan plan(Action a, double speed, int jitterPx, Random rnd,
                                   CoordinateScaler scaler, Limits lim) {
        if (!a.isGesture()) return null;
        speed = Macro.clampSpeed(speed);
        if (scaler == null) scaler = CoordinateScaler.IDENTITY;
        if (lim == null) lim = new Limits();
        float jx = 0, jy = 0;
        if (jitterPx > 0 && rnd != null) {
            jx = (rnd.nextFloat() * 2f - 1f) * jitterPx;
            jy = (rnd.nextFloat() * 2f - 1f) * jitterPx;
        }

        GesturePlan plan = new GesturePlan();
        switch (a.type) {
            case TAP:
            case LONG_PRESS: {
                long d = Math.max(1, a.duration);
                GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(0, d);
                s.points.add(pt(scaler.x(a.x) + jx, scaler.y(a.y) + jy, lim));
                plan.strokes.add(s);
                break;
            }
            case DOUBLE_TAP: {
                long d = Math.max(1, a.duration);
                float[] p = pt(scaler.x(a.x) + jx, scaler.y(a.y) + jy, lim);
                GesturePlan.PlannedStroke s1 = new GesturePlan.PlannedStroke(0, d);
                s1.points.add(p);
                GesturePlan.PlannedStroke s2 = new GesturePlan.PlannedStroke(d + Action.DOUBLE_TAP_GAP_MS, d);
                s2.points.add(new float[]{p[0], p[1]});
                plan.strokes.add(s1);
                plan.strokes.add(s2);
                break;
            }
            case SWIPE: {
                long d = scaled(Math.max(1, a.duration), speed);
                GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(0, d);
                s.points.add(pt(scaler.x(a.x) + jx, scaler.y(a.y) + jy, lim));
                s.points.add(pt(scaler.x(a.x2) + jx, scaler.y(a.y2) + jy, lim));
                plan.strokes.add(s);
                break;
            }
            case GESTURE: {
                if (a.isZoom()) a.rebuildZoom(); // the fingers follow the centre (it may have been dragged)
                int n = Math.min(a.strokes.size(), Math.max(1, lim.maxStrokes));
                for (int i = 0; i < n; i++) {
                    Stroke st = a.strokes.get(i);
                    if (st.size() == 0) continue;
                    long start = Math.max(0, Math.round(st.startTime() / speed));
                    long dur = scaled(Math.max(1, st.duration()), speed);
                    GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(start, dur);
                    for (int k = 0; k < st.size(); k++) {
                        s.points.add(pt(scaler.x(st.x(k)) + jx, scaler.y(st.y(k)) + jy, lim));
                    }
                    plan.strokes.add(s);
                }
                break;
            }
            default:
                return null;
        }
        if (plan.strokes.isEmpty()) return null;
        fitDuration(plan, lim.maxDuration);
        return plan;
    }

    static long scaled(long ms, double speed) {
        return Math.max(1, Math.round(ms / speed));
    }

    private static float[] pt(float x, float y, Limits lim) {
        // dispatchGesture rejects negative coordinates; also keep inside the screen.
        float maxX = lim.screenWidth > 0 ? lim.screenWidth - 1 : Float.MAX_VALUE;
        float maxY = lim.screenHeight > 0 ? lim.screenHeight - 1 : Float.MAX_VALUE;
        return new float[]{Math.max(0f, Math.min(maxX, x)), Math.max(0f, Math.min(maxY, y))};
    }

    /** Compress timing proportionally if the gesture is longer than Android allows. */
    private static void fitDuration(GesturePlan plan, long max) {
        long total = plan.totalDuration();
        if (max <= 0 || total <= max) return;
        double f = (double) max / total;
        for (GesturePlan.PlannedStroke s : plan.strokes) {
            s.startTime = Math.min(max - 1, (long) Math.floor(s.startTime * f));
            s.duration = Math.max(1, (long) Math.floor(s.duration * f));
            if (s.startTime + s.duration > max) s.duration = max - s.startTime; // >= 1
        }
    }
}
