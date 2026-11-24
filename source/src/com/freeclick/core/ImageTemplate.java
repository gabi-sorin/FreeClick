package com.freeclick.core;

import java.util.HashMap;
import java.util.Map;

/**
 * A cut-out image to look for (a button, an icon...). Immutable; per-level data used by
 * the search is computed once and kept.
 */
public final class ImageTemplate {
    public static final int MIN_SIDE = 16;
    public static final int MAX_SIDE = 400;
    /** Below this brightness spread (0..255) a cut-out is "a plain patch": it would match anywhere. */
    public static final double MIN_DETAIL = 6.0;

    public final int[] argb;
    public final int width, height;
    private final ScreenFrame asFrame;
    private final Map<Integer, Level> levels = new HashMap<Integer, Level>();
    private Double detail;
    private ImageTemplate core;
    private final Map<Integer, ImageTemplate> scaled = new HashMap<Integer, ImageTemplate>();
    private int[] gradient;

    /** Zero-mean grey values of one level, for normalised correlation. */
    static final class Level {
        final int w, h;
        final double[] zero;
        final double norm;
        final double mean;
        Level(int w, int h, double[] zero, double norm, double mean) { this.w = w; this.h = h; this.zero = zero; this.norm = norm; this.mean = mean; }
    }

    public ImageTemplate(int[] argb, int width, int height) {
        if (width < 1 || height < 1 || argb.length < width * height) throw new IllegalArgumentException("template size");
        this.argb = argb;
        this.width = width;
        this.height = height;
        this.asFrame = new ScreenFrame(argb, width, height, 0);
    }

    /** Cut a rectangle out of a frame. */
    public static ImageTemplate cut(ScreenFrame f, int x, int y, int w, int h) {
        x = Math.max(0, x); y = Math.max(0, y);
        w = Math.min(w, f.width - x); h = Math.min(h, f.height - y);
        if (w < 1 || h < 1) throw new IllegalArgumentException("empty cut");
        int[] out = new int[w * h];
        for (int j = 0; j < h; j++) System.arraycopy(f.argb, (y + j) * f.width + x, out, j * w, w);
        return new ImageTemplate(out, w, h);
    }

    /** Standard deviation of brightness: how much there is to recognise. */
    public synchronized double detail() {
        if (detail == null) {
            double s = 0, q = 0;
            int n = width * height;
            for (int i = 0; i < n; i++) { int v = ScreenFrame.luma(argb[i]); s += v; q += (double) v * v; }
            double mean = s / n;
            detail = Math.sqrt(Math.max(0, q / n - mean * mean));
        }
        return detail;
    }

    /** The middle of the cut-out (15% trimmed on every side): survives a background that changed. */
    public synchronized ImageTemplate core() {
        if (core == null) {
            int mx = Math.round(width * 0.15f), my = Math.round(height * 0.15f);
            int w = width - 2 * mx, h = height - 2 * my;
            if (w < MIN_SIDE || h < MIN_SIDE) return null;
            int[] out = new int[w * h];
            for (int j = 0; j < h; j++) System.arraycopy(argb, (j + my) * width + mx, out, j * w, w);
            core = new ImageTemplate(out, w, h);
        }
        return core;
    }

    public int coreOffsetX() { return Math.round(width * 0.15f); }
    public int coreOffsetY() { return Math.round(height * 0.15f); }

    /** Resized copy (bilinear). */
    public synchronized ImageTemplate scaled(double sx, double sy) {
        int key = (int) Math.round(sx * 1000) * 100000 + (int) Math.round(sy * 1000);
        if (Math.abs(sx - 1) < 1e-3 && Math.abs(sy - 1) < 1e-3) return this;
        ImageTemplate t = scaled.get(key);
        if (t != null) return t;
        int w = Math.max(1, (int) Math.round(width * sx)), h = Math.max(1, (int) Math.round(height * sy));
        int[] out = new int[w * h];
        for (int j = 0; j < h; j++) { // bilinear: resized screens are smooth, so must be the template
            double fy = Math.max(0, Math.min(height - 1.001, (j + 0.5) / sy - 0.5));
            int y0 = (int) fy;
            double ty = fy - y0;
            for (int i = 0; i < w; i++) {
                double fx = Math.max(0, Math.min(width - 1.001, (i + 0.5) / sx - 0.5));
                int x0 = (int) fx;
                double tx = fx - x0;
                int c00 = argb[y0 * width + x0], c10 = argb[y0 * width + x0 + 1];
                int c01 = argb[(y0 + 1) * width + x0], c11 = argb[(y0 + 1) * width + x0 + 1];
                int px = 0xFF000000;
                for (int sh = 0; sh <= 16; sh += 8) {
                    double v = ((c00 >> sh) & 255) * (1 - tx) * (1 - ty) + ((c10 >> sh) & 255) * tx * (1 - ty)
                            + ((c01 >> sh) & 255) * (1 - tx) * ty + ((c11 >> sh) & 255) * tx * ty;
                    px |= ((int) Math.round(v) & 255) << sh;
                }
                out[j * w + i] = px;
            }
        }
        t = new ImageTemplate(out, w, h);
        scaled.put(key, t);
        return t;
    }

    /** Edge strength (|dx|+|dy| of brightness) per pixel, 0 on the 1-px border. */
    synchronized int[] gradient() {
        if (gradient == null) gradient = gradientOf(argb, width, height, 0, 0, width, height);
        return gradient;
    }

    /** Edge strength of a window of an ARGB image (0 on the window's 1-px border). */
    static int[] gradientOf(int[] argb, int w, int h, int x0, int y0, int ww, int hh) {
        int[] g = new int[ww * hh];
        for (int j = 1; j < hh - 1; j++) {
            int y = y0 + j;
            if (y < 1 || y >= h - 1) continue;
            for (int i = 1; i < ww - 1; i++) {
                int x = x0 + i;
                if (x < 1 || x >= w - 1) continue;
                int gx = ScreenFrame.luma(argb[y * w + x + 1]) - ScreenFrame.luma(argb[y * w + x - 1]);
                int gy = ScreenFrame.luma(argb[(y + 1) * w + x]) - ScreenFrame.luma(argb[(y - 1) * w + x]);
                g[j * ww + i] = Math.abs(gx) + Math.abs(gy);
            }
        }
        return g;
    }

    /** Grey data at pyramid level f (1 = full size). */
    synchronized Level level(int f) {
        Level l = levels.get(f);
        if (l != null) return l;
        int w, h;
        int[] g;
        if (f == 1) {
            w = width; h = height;
            g = new int[w * h];
            for (int i = 0; i < g.length; i++) g[i] = ScreenFrame.luma(argb[i]);
        } else {
            ScreenFrame.Gray gr = asFrame.level(f);
            w = gr.w; h = gr.h; g = gr.p;
        }
        double mean = 0;
        for (int v : g) mean += v;
        mean /= Math.max(1, g.length);
        double[] z = new double[g.length];
        double nn = 0;
        for (int i = 0; i < g.length; i++) { z[i] = g[i] - mean; nn += z[i] * z[i]; }
        l = new Level(w, h, z, Math.sqrt(nn), mean);
        levels.put(f, l);
        return l;
    }
}
