package com.freeclick.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds a cut-out image on a screenshot.
 *
 * Brightness pattern first: normalised cross-correlation (NCC, insensitive to brightness and
 * contrast changes) on a small grey version of the screen, then refined level by level up to
 * full resolution around the best candidates. Then colour: the average colour difference must
 * also be small, so a grey (disabled) button never passes for the same green button.
 *
 * Measured on synthetic 1080×2400 screens: exact, noisy, brighter and resampled copies are
 * found; absent images score ≈0.44; the grey twin of a green button scores 0.97 on brightness
 * but is rejected by colour. A cut-out that includes some background fails when the button
 * moves onto another background, its middle does not, so the middle (core) is searched too.
 *
 * Last check, on edges (outlines and letters): "Collect" inside a "Collected" button has the
 * same brightness pattern (0.91) but different edges (≤0.79 vs ≥0.96 for the real button on
 * 24 test screens, JPEG included), so look-alikes are rejected.
 */
public final class ImageMatcher {
    private ImageMatcher() {}

    /** Multiply-adds done so far (tests put budgets on this instead of on milliseconds). */
    public static long ops;

    static final int CANDIDATES = 12;
    static final double STRONG_EDGES = 0.93;
    static final double[] TOLERANT_SCALES = {0.92, 0.96, 1.04, 1.08};

    public static final class Query {
        /** 70 (tolerant) .. 98 (strict). */
        public int precision = 85;
        /** Also try slightly smaller and bigger sizes (±8%), e.g. for pulsing buttons. */
        public boolean multiScale;
        /** Return every place it appears (else only the top-most). */
        public boolean all;
        /** Search only inside this rectangle (frame px). All zero = whole frame. */
        public int zx1, zy1, zx2, zy2;
        /**
         * Also search the middle of the cut-out if the whole fails. Right for cut-outs with some
         * background around the button (that background may differ elsewhere); wrong for tight
         * ones (snapped to the button's edges), whose middle is often just the label, which also
         * appears inside look-alikes ("Collect" in "Collected").
         */
        public boolean useCore = true;
        /** Ignore matches overlapping this rectangle (our panel). All zero = none. */
        public int ex1, ey1, ex2, ey2;
        /** A second one (our console). All zero = none. */
        public int fx1, fy1, fx2, fy2;
        public int maxResults = 10;
    }

    public static final class Match {
        public int x, y, w, h;
        /** Brightness-pattern similarity 0..1 (shown as %). */
        public double score;
        /** Average colour difference per channel 0..255. */
        public double colorDiff;
        /** Similarity of outlines and letters 0..1. */
        public double edge;
        public boolean ok;
        /** Looks right but lies under the excluded rectangle (our panel). */
        public boolean covered;
        /** What the user sees as "similarity": the weaker of brightness pattern and edges. */
        public double similarity() { return Math.min(score, edge); }
        public int centerX() { return x + w / 2; }
        public int centerY() { return y + h / 2; }
        @Override public String toString() {
            return (ok ? "ok" : covered ? "covered" : "no") + "@" + x + "," + y + " " + w + "x" + h
                    + String.format(java.util.Locale.US, " s=%.3f e=%.3f c=%.1f", score, edge, colorDiff);
        }
    }

    public static final class Result {
        /** Matches that pass the thresholds, top-most first. */
        public final List<Match> matches = new ArrayList<Match>();
        /** The closest candidate even if it failed (for "Testează": "cel mai apropiat 71%"). */
        public Match best;
        public boolean found() { return !matches.isEmpty(); }
    }

    public static double minScore(int precision) { return clampPrecision(precision) / 100.0; }

    public static double maxColorDiff(int precision) { return 6 + (100 - clampPrecision(precision)) * 1.6; }

    /**
     * Edge similarity needed: 0.50 (70) .. 0.69 (78, tolerant) .. 0.85 (85) .. 0.98 (98).
     * Real matches score ≥0.95 at the same size, look-alikes ≤0.79; resized screens lower.
     */
    public static double minEdge(int precision) {
        int p = clampPrecision(precision);
        return p <= 85 ? 0.50 + 0.35 * (p - 70) / 15.0 : 0.85 + 0.13 * (p - 85) / 13.0;
    }

    public static int clampPrecision(int p) { return Math.max(70, Math.min(98, p)); }

    public static Result find(ScreenFrame frame, ImageTemplate t, Query q) {
        Result r = new Result();
        List<Match> all = new ArrayList<Match>();
        double minS = minScore(q.precision), maxC = maxColorDiff(q.precision), minE = minEdge(q.precision);

        // 1. the whole cut-out
        all.addAll(search(frame, t, 0, 0, t.width, t.height, q, minS, maxC, minE));
        boolean any = anyOk(all);
        // 2. its middle, if the whole did not match (the background around the button changed)
        ImageTemplate core = q.useCore && Math.min(t.width, t.height) >= 48 ? t.core() : null; // small icons are cut tightly anyway
        if (!any && core != null) {
            all.addAll(search(frame, core, t.coreOffsetX(), t.coreOffsetY(), t.width, t.height, q, minS, maxC, minE));
            any = anyOk(all);
        }
        // 3. slightly other sizes (tolerant precision only)
        if (!any && q.multiScale) {
            for (double s : TOLERANT_SCALES) {
                ImageTemplate ts = t.scaled(s, s);
                all.addAll(search(frame, ts, 0, 0, ts.width, ts.height, q, minS, maxC, minE));
                ImageTemplate cs = q.useCore && Math.min(ts.width, ts.height) >= 48 ? ts.core() : null;
                if (!anyOk(all) && cs != null) all.addAll(search(frame, cs, ts.coreOffsetX(), ts.coreOffsetY(), ts.width, ts.height, q, minS, maxC, minE));
                if (anyOk(all)) break;
            }
        }
        // best candidate overall, then the ones that pass without overlaps
        for (Match m : all) if (r.best == null || better(m, r.best)) r.best = m;
        List<Match> ok = new ArrayList<Match>();
        for (Match m : all) if (m.ok) ok.add(m);
        Collections.sort(ok, new Comparator<Match>() {
            @Override public int compare(Match a, Match b) { return Double.compare(b.score, a.score); }
        });
        List<Match> kept = new ArrayList<Match>();
        for (Match m : ok) {
            boolean overlaps = false;
            for (Match k : kept) if (overlap(m, k) > 0.3) { overlaps = true; break; }
            if (!overlaps) kept.add(m);
        }
        Collections.sort(kept, new Comparator<Match>() { // reading order: top-most first
            @Override public int compare(Match a, Match b) {
                if (Math.abs(a.y - b.y) > Math.min(a.h, b.h) / 2) return a.y - b.y;
                return a.x - b.x;
            }
        });
        int limit = q.all ? Math.max(1, q.maxResults) : 1;
        for (int i = 0; i < kept.size() && i < limit; i++) r.matches.add(kept.get(i));
        return r;
    }

    private static boolean better(Match a, Match b) {
        if (a.ok != b.ok) return a.ok;
        return a.similarity() > b.similarity();
    }

    /** Normalised correlation of edge strength between the template and the frame window. */
    static double edgeNcc(ScreenFrame fr, ImageTemplate t, int x, int y) {
        if (x < 0 || y < 0 || x + t.width > fr.width || y + t.height > fr.height) return 0;
        int[] a = t.gradient();
        int[] b = ImageTemplate.gradientOf(fr.argb, fr.width, fr.height, x, y, t.width, t.height);
        double sa = 0, sb = 0, qa = 0, qb = 0, d = 0;
        int n = a.length;
        for (int i = 0; i < n; i++) {
            sa += a[i]; sb += b[i];
            qa += (double) a[i] * a[i]; qb += (double) b[i] * b[i];
            d += (double) a[i] * b[i];
        }
        ops += 6L * n;
        double va = qa - sa * sa / n, vb = qb - sb * sb / n;
        if (va < 1e-6 || vb < 1e-6) return 0;
        return (d - sa * sb / n) / Math.sqrt(va * vb);
    }

    private static boolean anyOk(List<Match> l) {
        for (Match m : l) if (m.ok) return true;
        return false;
    }

    static double overlap(Match a, Match b) {
        int ix = Math.max(0, Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x));
        int iy = Math.max(0, Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y));
        double inter = (double) ix * iy;
        double small = Math.min((double) a.w * a.h, (double) b.w * b.h);
        return small <= 0 ? 0 : inter / small;
    }

    /**
     * Pyramid step for the full-screen scan: the template should stay ≥8 px there (fewer
     * misses), but small icons use step 4 at ≥5 px (otherwise the scan costs 5× more).
     */
    static int coarseFactor(int w, int h) {
        int m = Math.min(w, h);
        if (m >= 192) return 16;
        if (m >= 64) return 8;
        if (m >= 20) return 4;
        return 2;
    }

    /**
     * Search one template; returned matches are reported for the whole cut-out
     * (offX/offY: where this template sits inside it, fullW/fullH: its size).
     */
    private static List<Match> search(ScreenFrame fr, ImageTemplate t, int offX, int offY, int fullW, int fullH,
                                      Query q, double minS, double maxC, double minE) {
        List<Match> out = new ArrayList<Match>();
        if (t.width < ImageTemplate.MIN_SIDE || t.height < ImageTemplate.MIN_SIDE) return out;
        if (t.width > fr.width || t.height > fr.height) return out;
        int zx1 = 0, zy1 = 0, zx2 = fr.width, zy2 = fr.height;
        if (q.zx2 > q.zx1 && q.zy2 > q.zy1) {
            zx1 = Math.max(0, q.zx1); zy1 = Math.max(0, q.zy1);
            zx2 = Math.min(fr.width, q.zx2); zy2 = Math.min(fr.height, q.zy2);
        }
        if (zx2 - zx1 < t.width || zy2 - zy1 < t.height) return out;

        List<int[]> cand;
        boolean plain = t.detail() < ImageTemplate.MIN_DETAIL;
        int F = Math.max(2, coarseFactor(t.width, t.height));
        if (plain) cand = plainCandidates(fr, t, F, zx1, zy1, zx2, zy2);
        else cand = coarseCandidates(fr, t, F, zx1, zy1, zx2, zy2);

        // Refine level by level; after each level drop candidates far behind the best one,
        // so the expensive full-resolution steps run only for a few places.
        int keep = q.all ? Math.max(3, Math.min(CANDIDATES, q.maxResults)) : 3;
        int finest = Math.min(t.width, t.height) >= 160 ? 2 : 1; // big cut-outs: 2 px precision is plenty
        double[] scores = new double[cand.size()];
        if (!plain) {
            for (int f = F / 2; f >= finest; f /= 2) {
                double bestNow = -2;
                for (int i = 0; i < cand.size(); i++) {
                    int[] c = cand.get(i);
                    double[] sc = {0};
                    int[] p = refine(fr, t, f, c[0] * 2, c[1] * 2, zx1, zy1, zx2, zy2, sc);
                    c[0] = p[0]; c[1] = p[1];
                    scores[i] = sc[0];
                    bestNow = Math.max(bestNow, sc[0]);
                }
                List<int[]> next = new ArrayList<int[]>();
                List<Double> nextScores = new ArrayList<Double>();
                for (int i = 0; i < cand.size(); i++) {
                    if (scores[i] >= 0.5 && scores[i] >= bestNow - 0.15) { next.add(cand.get(i)); nextScores.add(scores[i]); }
                }
                // strongest first, at most `keep`
                List<Integer> order = new ArrayList<Integer>();
                for (int i = 0; i < next.size(); i++) order.add(i);
                final List<Double> ns = nextScores;
                Collections.sort(order, new Comparator<Integer>() {
                    @Override public int compare(Integer a, Integer b) { return Double.compare(ns.get(b), ns.get(a)); }
                });
                cand = new ArrayList<int[]>();
                scores = new double[Math.min(keep, order.size())];
                for (int i = 0; i < order.size() && i < keep; i++) { cand.add(next.get(order.get(i))); scores[i] = ns.get(order.get(i)); }
                if (cand.isEmpty()) break;
            }
        }
        for (int i = 0; i < cand.size(); i++) {
            int[] c = cand.get(i);
            int x = c[0] * (plain ? 1 : finest), y = c[1] * (plain ? 1 : finest);
            double score = plain ? 0 : scores[i];
            Match m = new Match();
            m.x = x - offX; m.y = y - offY; m.w = fullW; m.h = fullH;
            m.score = score;
            m.colorDiff = colorDiff(fr, t, x, y);
            if (plain) { m.score = Math.max(0, 1 - m.colorDiff / 64.0); m.edge = m.score; }
            else m.edge = m.score >= minS - 0.12 ? edgeNcc(fr, t, x, y) : 0;
            // Brightness pattern and edges must both agree. A real button on another background
            // can lose some brightness similarity in its corners and shadow but keeps very similar
            // edges, so strong edges (≥0.93) make up for up to 0.1 less brightness similarity.
            boolean shape = m.score >= minS || (m.score >= minS - 0.1 && m.edge >= Math.max(minE, STRONG_EDGES));
            boolean pass = (plain ? m.colorDiff <= Math.min(maxC, 12) : shape && m.edge >= minE) && m.colorDiff <= maxC;
            if ((q.ex2 > q.ex1 && q.ey2 > q.ey1 && intersects(m, q.ex1, q.ey1, q.ex2, q.ey2))
                    || (q.fx2 > q.fx1 && q.fy2 > q.fy1 && intersects(m, q.fx1, q.fy1, q.fx2, q.fy2))) {
                m.covered = pass;
                pass = false;
            }
            m.ok = pass;
            out.add(m);
        }
        return out;
    }

    private static boolean intersects(Match m, int x1, int y1, int x2, int y2) {
        return m.x < x2 && m.x + m.w > x1 && m.y < y2 && m.y + m.h > y1;
    }

    /** Full scan at level F; best positions (level-F coordinates), far enough apart. */
    private static List<int[]> coarseCandidates(ScreenFrame fr, ImageTemplate t, int F,
                                                int zx1, int zy1, int zx2, int zy2) {
        ScreenFrame.Gray g = fr.level(F);
        g.integrals();
        ImageTemplate.Level tl = t.level(F);
        int tw = tl.w, th = tl.h, n = tw * th;
        int x0 = (zx1 + F - 1) / F, y0 = (zy1 + F - 1) / F;
        int x1 = Math.min(g.w - tw, zx2 / F - tw), y1 = Math.min(g.h - th, zy2 / F - th);
        int[] cx = new int[CANDIDATES], cy = new int[CANDIDATES];
        double[] cs = new double[CANDIDATES];
        java.util.Arrays.fill(cs, -2);
        int found = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double v = ncc(g, tl, x, y, n);
                if (found == CANDIDATES && v <= cs[CANDIDATES - 1]) continue;
                // replace a nearby weaker candidate instead of adding a duplicate
                int near = -1;
                for (int k = 0; k < found; k++) {
                    if (Math.abs(cx[k] - x) <= tw / 2 && Math.abs(cy[k] - y) <= th / 2) { near = k; break; }
                }
                if (near >= 0) {
                    if (v <= cs[near]) continue;
                    cs[near] = v; cx[near] = x; cy[near] = y;
                } else if (found < CANDIDATES) {
                    cs[found] = v; cx[found] = x; cy[found] = y; found++;
                } else {
                    cs[CANDIDATES - 1] = v; cx[CANDIDATES - 1] = x; cy[CANDIDATES - 1] = y;
                }
                for (int a = found - 1; a > 0 && cs[a] > cs[a - 1]; a--) { // keep sorted (insertion)
                    double ts = cs[a]; cs[a] = cs[a - 1]; cs[a - 1] = ts;
                    int tx = cx[a]; cx[a] = cx[a - 1]; cx[a - 1] = tx;
                    int ty = cy[a]; cy[a] = cy[a - 1]; cy[a - 1] = ty;
                }
            }
        }
        List<int[]> out = new ArrayList<int[]>();
        for (int k = 0; k < found; k++) if (cs[k] > 0.3) out.add(new int[]{cx[k], cy[k]});
        return out;
    }

    private static double ncc(ScreenFrame.Gray g, ImageTemplate.Level tl, int x, int y, int n) {
        long sum = g.windowSum(x, y, tl.w, tl.h);
        long sq = g.windowSq(x, y, tl.w, tl.h);
        double var = sq - (double) sum * sum / n;
        if (var < 1e-6 || tl.norm < 1e-6) return 0;
        double dot = 0;
        int k = 0;
        for (int j = 0; j < tl.h; j++) {
            int row = (y + j) * g.w + x;
            for (int i = 0; i < tl.w; i++) dot += tl.zero[k++] * g.p[row + i];
        }
        ops += n;
        return dot / (Math.sqrt(var) * tl.norm);
    }

    /** Best NCC in a ±2 neighbourhood at level f (1 = full resolution, read from the pixels). */
    private static int[] refine(ScreenFrame fr, ImageTemplate t, int f, int px, int py,
                                int zx1, int zy1, int zx2, int zy2, double[] bestOut) {
        ImageTemplate.Level tl = t.level(f);
        int lw = f == 1 ? fr.width : fr.level(f).w, lh = f == 1 ? fr.height : fr.level(f).h;
        int bx = px, by = py;
        double bv = -2;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = px + dx, y = py + dy;
                if (x < 0 || y < 0 || x + tl.w > lw || y + tl.h > lh) continue;
                if (x * f < zx1 || y * f < zy1 || (x + tl.w) * f > zx2 || (y + tl.h) * f > zy2) continue;
                double v = f == 1 ? nccFull(fr, tl, x, y) : nccDirect(fr.level(f), tl, x, y);
                if (v > bv) { bv = v; bx = x; by = y; }
            }
        }
        bestOut[0] = Math.max(bv, -1);
        return new int[]{bx, by};
    }

    private static double nccDirect(ScreenFrame.Gray g, ImageTemplate.Level tl, int x, int y) {
        long sum = 0, sq = 0;
        double dot = 0;
        int k = 0;
        for (int j = 0; j < tl.h; j++) {
            int row = (y + j) * g.w + x;
            for (int i = 0; i < tl.w; i++) {
                int v = g.p[row + i];
                sum += v; sq += (long) v * v;
                dot += tl.zero[k++] * v;
            }
        }
        int n = tl.w * tl.h;
        ops += n;
        double var = sq - (double) sum * sum / n;
        if (var < 1e-6 || tl.norm < 1e-6) return 0;
        return dot / (Math.sqrt(var) * tl.norm);
    }

    private static double nccFull(ScreenFrame fr, ImageTemplate.Level tl, int x, int y) {
        long sum = 0, sq = 0;
        double dot = 0;
        int k = 0;
        for (int j = 0; j < tl.h; j++) {
            int row = (y + j) * fr.width + x;
            for (int i = 0; i < tl.w; i++) {
                int v = ScreenFrame.luma(fr.argb[row + i]);
                sum += v; sq += (long) v * v;
                dot += tl.zero[k++] * v;
            }
        }
        int n = tl.w * tl.h;
        ops += n;
        double var = sq - (double) sum * sum / n;
        if (var < 1e-6 || tl.norm < 1e-6) return 0;
        return dot / (Math.sqrt(var) * tl.norm);
    }

    /** Average |difference| per colour channel, on at most 48×48 sample points. */
    static double colorDiff(ScreenFrame fr, ImageTemplate t, int x, int y) {
        if (x < 0 || y < 0 || x + t.width > fr.width || y + t.height > fr.height) return 255;
        int sx = Math.max(1, t.width / 48), sy = Math.max(1, t.height / 48);
        long d = 0;
        int n = 0;
        for (int j = 0; j < t.height; j += sy) {
            for (int i = 0; i < t.width; i += sx) {
                int a = fr.argb[(y + j) * fr.width + x + i], b = t.argb[j * t.width + i];
                d += Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                        + Math.abs((a & 255) - (b & 255));
                n += 3;
            }
        }
        ops += n;
        return n == 0 ? 255 : (double) d / n;
    }

    /** Plain patches: positions whose block has the template's brightness and is itself plain. */
    private static List<int[]> plainCandidates(ScreenFrame fr, ImageTemplate t, int F,
                                               int zx1, int zy1, int zx2, int zy2) {
        ScreenFrame.Gray g = fr.level(F);
        g.integrals();
        ImageTemplate.Level tl = t.level(F);
        int tw = tl.w, th = tl.h, n = tw * th;
        int x0 = (zx1 + F - 1) / F, y0 = (zy1 + F - 1) / F;
        int x1 = Math.min(g.w - tw, zx2 / F - tw), y1 = Math.min(g.h - th, zy2 / F - th);
        List<int[]> out = new ArrayList<int[]>();
        double bestD = Double.MAX_VALUE;
        int bx = -1, by = -1;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double mean = g.windowSum(x, y, tw, th) / (double) n;
                double var = g.windowSq(x, y, tw, th) / (double) n - mean * mean;
                double d = Math.abs(mean - tl.mean) + Math.sqrt(Math.max(0, var));
                if (d < bestD) { bestD = d; bx = x; by = y; }
            }
        }
        ops += (long) Math.max(0, x1 - x0 + 1) * Math.max(0, y1 - y0 + 1);
        if (bx >= 0) out.add(new int[]{bx * F, by * F});
        return out;
    }
}
