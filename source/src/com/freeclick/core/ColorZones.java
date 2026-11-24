package com.freeclick.core;

/**
 * Colours over a whole zone (2.4): how full a bar is (procentCuloare) and where a colour is (gasesteCuloare).
 * Both work on a grid of samples, so a big zone costs the same as a small one and a few odd pixels
 * (anti-aliasing, noise, a number written over a bar) do not change the answer.
 */
public final class ColorZones {
    private ColorZones() {}

    public interface Pixels {
        /** 0xRRGGBB, or -1 outside the picture. */
        int at(int x, int y);
    }

    /** Most samples taken from one zone: at most 240 across a bar, 160 × 160 for a search. */
    public static final int BAR_LONG = 240, BAR_SHORT = 24, FIND = 160;

    /** cols × rows samples at the centres of equal cells of [x1, x2) × [y1, y2), row by row. */
    public static int[] grid(Pixels p, int x1, int y1, int x2, int y2, int cols, int rows) {
        int[] g = new int[cols * rows];
        double cw = (x2 - x1) / (double) cols, ch = (y2 - y1) / (double) rows;
        for (int r = 0; r < rows; r++) {
            int y = (int) Math.floor(y1 + (r + 0.5) * ch);
            for (int c = 0; c < cols; c++) g[r * cols + c] = p.at((int) Math.floor(x1 + (c + 0.5) * cw), y);
        }
        return g;
    }

    /** Grid size for a bar: long side along the bar (a zone taller than wide is a vertical bar). */
    public static int[] barGrid(int w, int h) {
        boolean vertical = h > w;
        int along = Math.max(1, Math.min(BAR_LONG, vertical ? h : w)), across = Math.max(1, Math.min(BAR_SHORT, vertical ? w : h));
        return vertical ? new int[]{across, along} : new int[]{along, across};
    }

    public static int[] findGrid(int w, int h) {
        return new int[]{Math.max(1, Math.min(FIND, w)), Math.max(1, Math.min(FIND, h))};
    }

    /** Below this share of matching samples, a slice across the bar counts as empty (text over the bar is fine). */
    static final double SLICE = 0.25;

    /**
     * How full the bar is, 0…100: the share of slices across it (columns for a horizontal bar, rows for a
     * vertical one) in which at least a quarter of the samples have the colour. -1 if nothing could be read.
     */
    public static double percent(int[] g, int cols, int rows, int color, int tol) {
        boolean vertical = rows > cols;
        int slices = vertical ? rows : cols, len = vertical ? cols : rows;
        boolean[] m = new boolean[g.length];
        boolean any = false;
        for (int i = 0; i < g.length; i++) { m[i] = g[i] >= 0 && ColorMatch.matches(g[i], color, tol); any |= g[i] >= 0; }
        if (!any) return -1;
        // lines along the bar that never show the colour (its border, a shadow) do not count
        boolean[] used = new boolean[len];
        int usedCount = 0;
        for (int k = 0; k < len; k++) {
            int match = 0;
            for (int s = 0; s < slices; s++) if (m[vertical ? s * cols + k : k * cols + s]) match++;
            used[k] = match >= Math.max(1, slices * 0.02);
            if (used[k]) usedCount++;
        }
        if (usedCount == 0) return 0;
        int full = 0, readable = 0;
        for (int s = 0; s < slices; s++) {
            int match = 0, seen = 0;
            for (int k = 0; k < len; k++) {
                if (!used[k]) continue;
                int i = vertical ? s * cols + k : k * cols + s;
                if (g[i] < 0) continue;
                seen++;
                if (m[i]) match++;
            }
            if (seen == 0) continue;
            readable++;
            if (match >= Math.max(1, Math.ceil(seen * SLICE))) full++;
        }
        return readable == 0 ? -1 : full * 100.0 / readable;
    }

    /**
     * Where the colour is: the first patch of it (top to bottom, left to right), its middle, as a grid cell
     * {col, row}; null if the colour is not in the zone. A patch must cover at least two neighbouring samples
     * (about 15 px on a full-width zone), so a stray pixel of the same shade is never taken for a thing.
     */
    public static int[] find(int[] g, int cols, int rows, int color, int tol) { return find(g, cols, rows, color, tol, false); }

    /** {@code perPixel}: the grid has a sample for every pixel, so even a single one is real. */
    public static int[] find(int[] g, int cols, int rows, int color, int tol, boolean perPixel) {
        boolean[] m = new boolean[g.length];
        for (int i = 0; i < g.length; i++) m[i] = g[i] >= 0 && ColorMatch.matches(g[i], color, tol);
        boolean[] seen = new boolean[g.length];
        int[] queue = new int[g.length];
        int[] lone = null;
        for (int start = 0; start < g.length; start++) {
            if (!m[start] || seen[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            long sx = 0, sy = 0;
            while (head < tail) {
                int i = queue[head++];
                int c = i % cols, r = i / cols;
                sx += c;
                sy += r;
                int[] nb = {c > 0 ? i - 1 : -1, c < cols - 1 ? i + 1 : -1, r > 0 ? i - cols : -1, r < rows - 1 ? i + cols : -1};
                for (int n : nb) if (n >= 0 && m[n] && !seen[n]) { seen[n] = true; queue[tail++] = n; }
            }
            double cx = sx / (double) tail, cy = sy / (double) tail;
            int best = queue[0];
            double bestD = Double.MAX_VALUE;
            for (int k = 0; k < tail; k++) { // the patch's own cell nearest its middle (a ring's middle is not on it)
                int i = queue[k];
                double d = Math.hypot(i % cols - cx, i / cols - cy);
                if (d < bestD) { bestD = d; best = i; }
            }
            int[] cell = {best % cols, best / cols};
            if (tail > 1) return cell;
            if (lone == null) lone = cell;
        }
        return perPixel ? lone : null; // one sample alone in a big zone is a stray pixel, not a thing to tap
    }

    /** The screen point in the middle of a grid cell. */
    public static int[] cellCentre(int x1, int y1, int x2, int y2, int cols, int rows, int[] cell) {
        return new int[]{(int) Math.floor(x1 + (cell[0] + 0.5) * (x2 - x1) / (double) cols),
                (int) Math.floor(y1 + (cell[1] + 0.5) * (y2 - y1) / (double) rows)};
    }
}
