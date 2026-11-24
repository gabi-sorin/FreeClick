package com.freeclick.core;

/**
 * "Magnet" for cutting out images: after the user drags a rectangle around a button, its
 * sides move in until they touch the button, if the rectangle's border is plain background.
 * A tight cut-out is found more reliably (no background that may differ elsewhere) and can be
 * told apart from look-alikes by its outline.
 */
public final class Magnet {
    private Magnet() {}

    static final int TOL = 26;

    /** Returns {x, y, w, h, tight(1/0)}. On a busy background the rectangle is left as it is. */
    public static int[] snap(ScreenFrame f, int x, int y, int w, int h) {
        x = Math.max(0, x); y = Math.max(0, y);
        w = Math.min(w, f.width - x); h = Math.min(h, f.height - y);
        int[] same = {x, y, w, h, 0};
        if (w < 4 || h < 4) return same;
        int n = 0;
        int[] rs = new int[2 * (w + h)], gs = new int[rs.length], bs = new int[rs.length];
        for (int i = 0; i < w; i++) { n = put(f, x + i, y, rs, gs, bs, n); n = put(f, x + i, y + h - 1, rs, gs, bs, n); }
        for (int j = 1; j < h - 1; j++) { n = put(f, x, y + j, rs, gs, bs, n); n = put(f, x + w - 1, y + j, rs, gs, bs, n); }
        int bg = (median(rs, n) << 16) | (median(gs, n) << 8) | median(bs, n);
        int near = 0;
        for (int i = 0; i < n; i++) if (close((rs[i] << 16) | (gs[i] << 8) | bs[i], bg)) near++;
        if (near < n * 0.85) return same; // busy background: no snapping
        int top = y, bottom = y + h - 1, left = x, right = x + w - 1;
        while (top < bottom && rowIsBg(f, top, left, right, bg)) top++;
        while (bottom > top && rowIsBg(f, bottom, left, right, bg)) bottom--;
        while (left < right && colIsBg(f, left, top, bottom, bg)) left++;
        while (right > left && colIsBg(f, right, top, bottom, bg)) right--;
        int nw = right - left + 1, nh = bottom - top + 1;
        if (nw < ImageTemplate.MIN_SIDE || nh < ImageTemplate.MIN_SIDE) return same; // nothing (or almost) inside
        return new int[]{left, top, nw, nh, 1};
    }

    private static int put(ScreenFrame f, int x, int y, int[] r, int[] g, int[] b, int n) {
        int c = f.argb[y * f.width + x];
        r[n] = (c >> 16) & 255; g[n] = (c >> 8) & 255; b[n] = c & 255;
        return n + 1;
    }

    private static int median(int[] v, int n) {
        int[] hist = new int[256];
        for (int i = 0; i < n; i++) hist[v[i]]++;
        int half = n / 2, acc = 0;
        for (int i = 0; i < 256; i++) { acc += hist[i]; if (acc > half) return i; }
        return 255;
    }

    private static boolean close(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) <= TOL && Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) <= TOL
                && Math.abs((a & 255) - (b & 255)) <= TOL;
    }

    private static boolean rowIsBg(ScreenFrame f, int y, int x1, int x2, int bg) {
        int bad = 0, n = x2 - x1 + 1;
        for (int x = x1; x <= x2; x++) if (!close(f.argb[y * f.width + x], bg) && ++bad > n * 0.03) return false;
        return true;
    }

    private static boolean colIsBg(ScreenFrame f, int x, int y1, int y2, int bg) {
        int bad = 0, n = y2 - y1 + 1;
        for (int y = y1; y <= y2; y++) if (!close(f.argb[y * f.width + x], bg) && ++bad > n * 0.03) return false;
        return true;
    }
}
