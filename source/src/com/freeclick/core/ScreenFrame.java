package com.freeclick.core;

/**
 * One screenshot, immutable: ARGB pixels plus lazily computed grey levels (a pyramid) that
 * every image search on this frame shares. Level f holds the average brightness of f×f
 * blocks (f = 2, 4, 8, 16); level 1 (full resolution) is read straight from the pixels.
 */
public final class ScreenFrame {
    public final int[] argb;
    public final int width, height;
    /** When the frame was requested (uptime ms): the capture happened at or after this. */
    public final long time;

    private final Gray[] levels = new Gray[5]; // index = log2(f)

    /** Grey image of one pyramid level, with integral images for fast window statistics. */
    static final class Gray {
        final int w, h;
        final int[] p;
        long[] sum, sq; // (w+1)*(h+1), built on demand
        Gray(int w, int h) { this.w = w; this.h = h; p = new int[w * h]; }

        void integrals() {
            if (sum != null) return;
            int W = w + 1;
            sum = new long[W * (h + 1)];
            sq = new long[W * (h + 1)];
            for (int y = 0; y < h; y++) {
                long rs = 0, rq = 0;
                for (int x = 0; x < w; x++) {
                    int v = p[y * w + x];
                    rs += v; rq += (long) v * v;
                    sum[(y + 1) * W + x + 1] = sum[y * W + x + 1] + rs;
                    sq[(y + 1) * W + x + 1] = sq[y * W + x + 1] + rq;
                }
            }
            ImageMatcher.ops += (long) w * h;
        }

        long windowSum(int x, int y, int ww, int hh) {
            int W = w + 1;
            return sum[(y + hh) * W + x + ww] - sum[y * W + x + ww] - sum[(y + hh) * W + x] + sum[y * W + x];
        }

        long windowSq(int x, int y, int ww, int hh) {
            int W = w + 1;
            return sq[(y + hh) * W + x + ww] - sq[y * W + x + ww] - sq[(y + hh) * W + x] + sq[y * W + x];
        }
    }

    public ScreenFrame(int[] argb, int width, int height, long time) {
        if (argb.length < width * height) throw new IllegalArgumentException("pixels");
        this.argb = argb;
        this.width = width;
        this.height = height;
        this.time = time;
    }

    static int luma(int c) {
        return (((c >> 16) & 255) * 77 + ((c >> 8) & 255) * 150 + (c & 255) * 29) >> 8;
    }

    int lumaAt(int x, int y) { return luma(argb[y * width + x]); }

    /** Colour 0xRRGGBB at (x, y), or -1 outside the frame. */
    public int rgb(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return -1;
        return argb[y * width + x] & 0xFFFFFF;
    }

    /** True if (almost) every pixel of the app area is black: a protected (FLAG_SECURE) screen. */
    public boolean looksProtected(int top, int bottom) {
        top = Math.max(0, top); bottom = Math.min(height, bottom);
        if (bottom - top < 10) return false;
        int dark = 0, n = 0;
        for (int y = top; y < bottom; y += 7) {
            for (int x = 0; x < width; x += 7) {
                n++;
                if ((argb[y * width + x] & 0xFFFFFF) == 0) dark++;
            }
        }
        return n > 0 && dark >= n * 0.995;
    }

    /** Grey level f (2, 4, 8 or 16). */
    synchronized Gray level(int f) {
        int k = Integer.numberOfTrailingZeros(f);
        if (k < 1 || k > 4 || (1 << k) != f) throw new IllegalArgumentException("level " + f);
        if (levels[k] != null) return levels[k];
        Gray g;
        if (k == 1) {
            g = new Gray(width / 2, height / 2);
            for (int y = 0; y < g.h; y++) {
                int r0 = 2 * y * width, r1 = r0 + width;
                for (int x = 0; x < g.w; x++) {
                    int i = 2 * x;
                    g.p[y * g.w + x] = (luma(argb[r0 + i]) + luma(argb[r0 + i + 1]) + luma(argb[r1 + i]) + luma(argb[r1 + i + 1]) + 2) >> 2;
                }
            }
            ImageMatcher.ops += (long) width * height;
        } else {
            Gray s = level(f / 2);
            g = new Gray(s.w / 2, s.h / 2);
            for (int y = 0; y < g.h; y++) {
                int r0 = 2 * y * s.w, r1 = r0 + s.w;
                for (int x = 0; x < g.w; x++) {
                    int i = 2 * x;
                    g.p[y * g.w + x] = (s.p[r0 + i] + s.p[r0 + i + 1] + s.p[r1 + i] + s.p[r1 + i + 1] + 2) >> 2;
                }
            }
            ImageMatcher.ops += (long) s.w * s.h;
        }
        levels[k] = g;
        return g;
    }
}
