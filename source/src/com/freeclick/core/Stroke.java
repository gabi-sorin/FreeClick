package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One finger's path inside a gesture. Times are in ms relative to the start
 * of the gesture the stroke belongs to (so multi-finger gestures keep the
 * relative timing between fingers).
 */
public final class Stroke {
    public final List<float[]> points = new ArrayList<float[]>(); // {x, y}
    public final List<Long> times = new ArrayList<Long>();

    public Stroke() {}

    public Stroke add(float x, float y, long t) {
        points.add(new float[]{x, y});
        times.add(t);
        return this;
    }

    public int size() { return points.size(); }
    public long startTime() { return times.isEmpty() ? 0 : times.get(0); }
    public long endTime() { return times.isEmpty() ? 0 : times.get(times.size() - 1); }
    public long duration() { return endTime() - startTime(); }
    public float x(int i) { return points.get(i)[0]; }
    public float y(int i) { return points.get(i)[1]; }

    /** Largest distance of any point from the first point (used for tap detection). */
    public double maxDisplacement() {
        if (points.isEmpty()) return 0;
        double max = 0;
        float x0 = x(0), y0 = y(0);
        for (int i = 1; i < points.size(); i++) {
            double dx = x(i) - x0, dy = y(i) - y0;
            max = Math.max(max, Math.sqrt(dx * dx + dy * dy));
        }
        return max;
    }

    public Stroke copy() {
        Stroke s = new Stroke();
        for (int i = 0; i < points.size(); i++) s.add(x(i), y(i), times.get(i));
        return s;
    }

    /** Shift all timestamps by -offset. */
    public void shiftTime(long offset) {
        for (int i = 0; i < times.size(); i++) times.set(i, times.get(i) - offset);
    }

    /**
     * Ramer–Douglas–Peucker simplification: keeps the shape of the path while
     * dropping redundant points. First and last points are always kept.
     */
    public Stroke simplified(double epsilon, int maxPoints) {
        if (points.size() <= 2) return copy();
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        rdp(0, points.size() - 1, epsilon, keep);
        Stroke out = new Stroke();
        for (int i = 0; i < points.size(); i++) if (keep[i]) out.add(x(i), y(i), times.get(i));
        // Hard cap: evenly sample if still too many points.
        if (maxPoints > 2 && out.size() > maxPoints) {
            Stroke capped = new Stroke();
            for (int k = 0; k < maxPoints; k++) {
                int idx = (int) Math.round((double) k * (out.size() - 1) / (maxPoints - 1));
                capped.add(out.x(idx), out.y(idx), out.times.get(idx));
            }
            return capped;
        }
        return out;
    }

    private void rdp(int a, int b, double eps, boolean[] keep) {
        if (b <= a + 1) return;
        double maxD = -1;
        int idx = -1;
        for (int i = a + 1; i < b; i++) {
            double d = perpendicular(x(i), y(i), x(a), y(a), x(b), y(b));
            if (d > maxD) { maxD = d; idx = i; }
        }
        if (maxD > eps) {
            keep[idx] = true;
            rdp(a, idx, eps, keep);
            rdp(idx, b, eps, keep);
        }
    }

    static double perpendicular(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) {
            double ex = px - ax, ey = py - ay;
            return Math.sqrt(ex * ex + ey * ey);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx, cy = ay + t * dy;
        double ex = px - cx, ey = py - cy;
        return Math.sqrt(ex * ex + ey * ey);
    }
}
