package com.freeclick.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reads a number or a short text from a zone of a screenshot, offline and without any library.
 *
 * 1. Separation: the background colour is the median of the zone's border; every pixel gets its
 *    distance from it, and an automatic (Otsu) threshold splits ink from background. This works
 *    for any colours (white on blue, red on green, outlined game text...), not only dark on light.
 * 2. Pieces of ink that touch form components; frame lines crossing the zone are dropped.
 *    Components are grouped into lines, and pieces stacked on top of each other (i + dot, ă,
 *    ș, :, %, =) into letters. Wide blobs that match badly are tried split into several letters.
 * 3. Each letter becomes a 16×16 grid keeping its proportions (blurred, so stroke width matters less) plus its shape
 *    (width/height) and where it sits relative to the line (baseline, capital height), and is
 *    compared with the letters of the FontBook (the phone's fonts + letters the user taught).
 *    Line metrics come from a first pass on shape alone, so "o" and "O", "," and "'" differ.
 * In MODE_NUMBER only digits and number signs are considered, which makes numbers very reliable.
 */
public final class TextReader {
    private TextReader() {}

    public static final int MODE_NUMBER = Action.READ_NUMBER, MODE_TEXT = Action.READ_TEXT;
    public static final String NUMBER_CHARS = "0123456789.,-+%:/kKMB";
    public static final String TEXT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            + "ĂÂÎȘȚăâîșț.,:;!?-+%/()'&@#$*=";
    static final int GW = 16, GH = 16, N = GW * GH;
    /** Zones larger than this are refused (a zone is for a number or a name, not a whole page). */
    public static final int MAX_PIXELS = 1200000;

    // characters by where they sit, for the line metrics
    static final String CAPS = "0123456789ABDEFGHKLMNQRTUYbdhk";
    static final String XHEIGHT = "acemnorsuvwxz";
    static final String NOT_ON_BASELINE = "gjpqy,;'-*\"=+~^`";

    /** What a letter looks like, normalised. */
    static final class Features {
        float[] v;      // zero-mean, unit-length blurred grid (null if the grid is flat, e.g. "-", "l", ".")
        float[] grad;   // stroke directions: 4×4 zones × 8 directions, unit length (null if no edges)
        float aspect;   // width / height
        float px = 99;  // longer side in pixels (tiny letters: the shape says little, the position a lot)
        float top, bottom; // (baseline - edge) / capital height: top ≈ 1 for capitals, bottom < 0 below the baseline

        static Features of(float[] cov, float aspect, float top, float bottom, float px) {
            Features f = new Features();
            f.px = px;
            f.aspect = Math.max(0.03f, Math.min(30f, aspect));
            f.top = top;
            f.bottom = bottom;
            f.grad = gradients(cov);
            cov = blur(cov);
            double mean = 0;
            for (float c : cov) mean += c;
            mean /= cov.length;
            double ss = 0;
            for (float c : cov) ss += (c - mean) * (c - mean);
            double sd = Math.sqrt(ss / cov.length);
            if (sd < 0.06) return f; // flat: a filled box
            float[] v = new float[cov.length];
            double norm = Math.sqrt(ss);
            for (int i = 0; i < v.length; i++) v[i] = (float) ((cov[i] - mean) / norm);
            f.v = v;
            return f;
        }
    }

    /** One letter found in the zone (frame pixels; x1/y1 exclusive). */
    public static final class Glyph {
        public int x0, y0, x1, y1;
        public int line;
        public boolean spaceBefore;
        /** What it was read as, and how far it was from the closest known letter (0 = identical). */
        public char c = '?';
        public float dist = 9f;
        /** The closest few different characters and their distances (for context fixes: l/I/1, O/0...). */
        char[] alt = new char[0];
        float[] altDist = new float[0];
        float[] cov;
        Features f;
        int[] comps;
        // raw position, before line metrics
        int top, bottom;
        float maxSide() { return Math.max(x1 - x0, y1 - y0); }
        int baseline;
        float capH;
    }

    public static final class Result {
        public String text = "";
        public Double number;
        public final List<Glyph> glyphs = new ArrayList<Glyph>();
        /** 0..1: average closeness of the letters to known ones. */
        public double confidence;
        /** 0..1: the worst letter. */
        public double worst = 1;
        public int background;
        public int threshold;
        public int lines;
        /** Why nothing could be read (null if something was). */
        public String problem;
        // for teaching: the segmentation and each line's baseline / capital height
        Seg seg;
        final List<float[]> metrics = new ArrayList<float[]>();
    }

    // ------------------------------------------------------------------ public entry
    public static Result read(ScreenFrame f, int x1, int y1, int x2, int y2, int mode, FontBook book) {
        return read(f.argb, f.width, f.height, x1, y1, x2, y2, mode, book);
    }

    public static Result read(int[] argb, int fw, int fh, int x1, int y1, int x2, int y2, int mode, FontBook book) {
        Result r = new Result();
        Seg s = segment(argb, fw, fh, x1, y1, x2, y2);
        r.background = s.bg;
        r.threshold = s.threshold;
        if (s.problem != null) { r.problem = s.problem; return r; }
        List<FontBook.Sample> samples = book == null ? new ArrayList<FontBook.Sample>() : book.snapshot();
        if (samples.isEmpty()) { r.problem = "nu am litere de comparat"; return r; }
        StringBuilder text = new StringBuilder();
        double sumQ = 0;
        int lineNo = 0;
        for (List<Glyph> line : s.lines) {
            float[] m = new float[2];
            List<Glyph> done = classifyLine(s, line, samples, mode, m);
            r.seg = s;
            if (done.isEmpty()) continue;
            if (text.length() > 0) text.append('\n');
            for (Glyph g : done) {
                g.line = lineNo;
                g.baseline = (int) m[0];
                g.capH = m[1];
                if (g.spaceBefore) text.append(' ');
                text.append(g.c);
                r.glyphs.add(g);
                double q = quality(g.dist);
                sumQ += q;
                r.worst = Math.min(r.worst, q);
            }
            lineNo++;
        }
        r.lines = lineNo;
        r.text = text.toString();
        if (r.glyphs.isEmpty()) { r.problem = "nu văd litere în zonă"; r.worst = 0; return r; }
        r.confidence = sumQ / r.glyphs.size();
        r.number = Numbers.parse(r.text.replace('\n', ' '));
        return r;
    }

    static double quality(float dist) { return Math.max(0, Math.min(1, 1 - dist / 0.9)); }

    // ------------------------------------------------------------------ teaching
    /**
     * Match the characters the user typed (spaces ignored) to the letters found: one character can
     * take 1-3 neighbouring pieces that touch (a "%" read as "o/o", a broken stroke). Null if they
     * cannot be matched (fewer pieces than characters: letters glued together).
     */
    static List<Glyph> alignForTeaching(Result r, String chars) {
        int n = r.glyphs.size(), m = chars.length();
        if (m == 0 || n < m || r.seg == null) return null;
        float INF = Float.MAX_VALUE;
        float[][] dp = new float[n + 1][m + 1];
        int[][] take = new int[n + 1][m + 1];
        for (float[] row : dp) Arrays.fill(row, INF);
        dp[0][0] = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                for (int k = 1; k <= 3 && k <= i; k++) {
                    if (dp[i - k][j - 1] == INF) continue;
                    float cost = 0;
                    boolean ok = true;
                    for (int q = i - k + 1; q < i; q++) {
                        Glyph a = r.glyphs.get(q - 1), b = r.glyphs.get(q);
                        if (a.line != b.line || b.spaceBefore) { ok = false; break; }
                        float gap = (b.x0 - a.x1) / Math.max(1f, b.capH);
                        if (gap > 0.3f) { ok = false; break; }
                        cost += 0.5f + Math.max(0, gap);
                    }
                    if (!ok) continue;
                    if (dp[i - k][j - 1] + cost < dp[i][j]) { dp[i][j] = dp[i - k][j - 1] + cost; take[i][j] = k; }
                }
            }
        }
        if (dp[n][m] == INF) return null;
        List<Glyph> out = alignedGlyphs(r, take, n, m);
        if (out == null) return null;
        for (int q = 0; q < m; q++) if (!plausible(chars.charAt(q), out.get(q))) return null;
        return out;
    }

    /** A taught character must at least have the right size: a "." is small, a "B" is not. */
    static boolean plausible(char c, Glyph g) {
        float h = (g.y1 - g.y0) / Math.max(1f, g.capH);
        if (".,'-_`\u00B7".indexOf(c) >= 0) return h < 0.55f;
        if (Character.isUpperCase(c) || Character.isDigit(c)) return h > 0.55f;
        if (Character.isLetter(c)) return h > 0.3f;
        return true;
    }

    private static List<Glyph> alignedGlyphs(Result r, int[][] take, int n, int m) {
        List<Glyph> out = new ArrayList<Glyph>();
        int i = n, j = m;
        while (j > 0) {
            int k = take[i][j];
            if (k == 1) out.add(0, r.glyphs.get(i - 1));
            else {
                List<Integer> ids = new ArrayList<Integer>();
                for (int q = i - k; q < i; q++) for (int c : r.glyphs.get(q).comps) ids.add(c);
                int[] arr = new int[ids.size()];
                for (int q = 0; q < arr.length; q++) arr[q] = ids.get(q);
                Glyph first = r.glyphs.get(i - k);
                Glyph g = build(r.seg, arr, 0, r.seg.w);
                if (g == null) return null;
                g.line = first.line; g.baseline = first.baseline; g.capH = first.capH;
                g.f = Features.of(g.cov, (g.x1 - g.x0) / (float) (g.y1 - g.y0), (g.baseline - g.top) / g.capH, (g.baseline - g.bottom) / g.capH, g.maxSide());
                out.add(0, g);
            }
            i -= k;
            j--;
        }
        return out;
    }

    // ------------------------------------------------------------------ segmentation
    static final class Seg {
        int zx, zy, w, h;
        float[] cov;
        int[] label;
        int bg, threshold;
        String problem;
        final List<List<Glyph>> lines = new ArrayList<List<Glyph>>();
        // components: bounding boxes in zone coordinates
        int[] cx0, cy0, cx1, cy1, count;
        boolean[] member; // scratch: which components belong to the glyph being built
    }

    static int dist(int c, int bg) {
        int dr = Math.abs(((c >> 16) & 255) - ((bg >> 16) & 255));
        int dg = Math.abs(((c >> 8) & 255) - ((bg >> 8) & 255));
        int db = Math.abs((c & 255) - (bg & 255));
        return Math.max(dr, Math.max(dg, db));
    }

    static Seg segment(int[] argb, int fw, int fh, int x1, int y1, int x2, int y2) {
        Seg s = new Seg();
        int ax = Math.max(0, Math.min(x1, x2)), ay = Math.max(0, Math.min(y1, y2));
        int bx = Math.min(fw, Math.max(x1, x2)), by = Math.min(fh, Math.max(y1, y2));
        s.zx = ax; s.zy = ay; s.w = bx - ax; s.h = by - ay;
        if (s.w < 3 || s.h < 3) { s.problem = "zona e prea mică"; return s; }
        if ((long) s.w * s.h > MAX_PIXELS) { s.problem = "zona e prea mare"; return s; }
        int w = s.w, h = s.h;
        // background = median colour of the border
        int nb = 2 * w + 2 * (h - 2);
        int[] rr = new int[nb], gg = new int[nb], bb = new int[nb];
        int k = 0;
        for (int x = 0; x < w; x++) {
            k = addRgb(argb[(ay) * fw + ax + x], rr, gg, bb, k);
            k = addRgb(argb[(by - 1) * fw + ax + x], rr, gg, bb, k);
        }
        for (int y = 1; y < h - 1; y++) {
            k = addRgb(argb[(ay + y) * fw + ax], rr, gg, bb, k);
            k = addRgb(argb[(ay + y) * fw + bx - 1], rr, gg, bb, k);
        }
        s.bg = (median(rr, k) << 16) | (median(gg, k) << 8) | median(bb, k);
        int[] d = new int[w * h];
        int[] hist = new int[256];
        int maxD = 0;
        for (int y = 0; y < h; y++) {
            int row = (ay + y) * fw + ax;
            for (int x = 0; x < w; x++) {
                int v = dist(argb[row + x], s.bg);
                d[y * w + x] = v;
                hist[v]++;
                if (v > maxD) maxD = v;
            }
        }
        if (maxD < 40) { s.problem = "zona are o singură culoare"; return s; }
        int t = Math.max(20, otsu(hist, w * h));
        s.threshold = t;
        s.cov = new float[w * h];
        boolean[] ink = new boolean[w * h];
        for (int i = 0; i < d.length; i++) {
            ink[i] = d[i] >= t;
            s.cov[i] = Math.max(0f, Math.min(1f, (d[i] - 0.5f * t) / t));
        }
        label(s, ink);
        buildLines(s);
        if (s.lines.isEmpty()) s.problem = "nu văd litere în zonă";
        return s;
    }

    private static int addRgb(int c, int[] r, int[] g, int[] b, int k) {
        r[k] = (c >> 16) & 255; g[k] = (c >> 8) & 255; b[k] = c & 255;
        return k + 1;
    }

    private static int median(int[] a, int n) {
        int[] c = Arrays.copyOf(a, n);
        Arrays.sort(c);
        return c[n / 2];
    }

    static int otsu(int[] hist, int total) {
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * (double) hist[i];
        double sumB = 0, best = -1;
        int wB = 0, th = 0;
        for (int i = 0; i < 256; i++) {
            wB += hist[i];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += i * (double) hist[i];
            double mB = sumB / wB, mF = (sum - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) { best = between; th = i + 1; }
        }
        return th;
    }

    /** 8-connected components; frame lines and specks are dropped (label -1). */
    private static void label(Seg s, boolean[] ink) {
        int w = s.w, h = s.h;
        int[] lab = new int[w * h];
        int[] stack = new int[w * h];
        List<int[]> boxes = new ArrayList<int[]>();
        boxes.add(null); // ids start at 1
        int id = 0;
        for (int start = 0; start < lab.length; start++) {
            if (!ink[start] || lab[start] != 0) continue;
            id++;
            int x0 = w, y0 = h, x1 = 0, y1 = 0, n = 0, sp = 0;
            stack[sp++] = start;
            lab[start] = id;
            while (sp > 0) {
                int p = stack[--sp];
                int px = p % w, py = p / w;
                n++;
                if (px < x0) x0 = px; if (px + 1 > x1) x1 = px + 1;
                if (py < y0) y0 = py; if (py + 1 > y1) y1 = py + 1;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = py + dy;
                    if (ny < 0 || ny >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = px + dx;
                        if (nx < 0 || nx >= w) continue;
                        int q = ny * w + nx;
                        if (ink[q] && lab[q] == 0) { lab[q] = id; stack[sp++] = q; }
                    }
                }
            }
            boxes.add(new int[]{x0, y0, x1, y1, n});
        }
        s.cx0 = new int[id + 1]; s.cy0 = new int[id + 1]; s.cx1 = new int[id + 1]; s.cy1 = new int[id + 1]; s.count = new int[id + 1];
        boolean[] drop = new boolean[id + 1];
        for (int i = 1; i <= id; i++) {
            int[] b = boxes.get(i);
            s.cx0[i] = b[0]; s.cy0[i] = b[1]; s.cx1[i] = b[2]; s.cy1[i] = b[3]; s.count[i] = b[4];
            int bw = b[2] - b[0], bh = b[3] - b[1];
            // (single pixels are kept for now: the dot of a small "i" is often one pixel; lone specks go later)
            // a line of a frame / button edge crossing the whole zone
            if (b[0] == 0 && b[2] == w && bh < Math.max(3, h * 0.25)) drop[i] = true;
            if (b[1] == 0 && b[3] == h && bw <= Math.max(2, w * 0.03) && (b[0] == 0 || b[2] == w)) drop[i] = true;
            // ink touching the border on 3 or 4 sides is background of another colour, not a letter
            int sides = (b[0] == 0 ? 1 : 0) + (b[2] == w ? 1 : 0) + (b[1] == 0 ? 1 : 0) + (b[3] == h ? 1 : 0);
            if (sides >= 3) drop[i] = true;
        }
        for (int p = 0; p < lab.length; p++) if (lab[p] > 0 && drop[lab[p]]) lab[p] = -1;
        for (int i = 1; i <= id; i++) if (drop[i]) s.count[i] = 0;
        s.label = lab;
        s.member = new boolean[id + 1];
    }

    private static void buildLines(Seg s) {
        int n = s.count.length - 1;
        List<Integer> comps = new ArrayList<Integer>();
        int maxH = 0;
        for (int i = 1; i <= n; i++) if (s.count[i] > 0) { comps.add(i); maxH = Math.max(maxH, s.cy1[i] - s.cy0[i]); }
        if (comps.isEmpty()) return;
        // lines from the taller components, top to bottom
        final Seg fs = s;
        Collections.sort(comps, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) { return (fs.cy0[a] + fs.cy1[a]) - (fs.cy0[b] + fs.cy1[b]); }
        });
        List<int[]> ranges = new ArrayList<int[]>(); // {top, bottom}
        List<List<Integer>> members = new ArrayList<List<Integer>>();
        List<Integer> small = new ArrayList<Integer>();
        for (int c : comps) {
            int ch = s.cy1[c] - s.cy0[c];
            if (ch < 0.55 * maxH) { small.add(c); continue; }
            int best = -1;
            float mid = (s.cy0[c] + s.cy1[c]) / 2f;
            for (int l = 0; l < ranges.size(); l++) {
                int[] r = ranges.get(l);
                int ov = Math.min(r[1], s.cy1[c]) - Math.max(r[0], s.cy0[c]);
                if (ov >= 0.5 * Math.min(ch, r[1] - r[0]) || (mid > r[0] && mid < r[1])) { best = l; break; }
            }
            if (best < 0) { ranges.add(new int[]{s.cy0[c], s.cy1[c]}); members.add(new ArrayList<Integer>()); best = ranges.size() - 1; }
            int[] r = ranges.get(best);
            r[0] = Math.min(r[0], s.cy0[c]); r[1] = Math.max(r[1], s.cy1[c]);
            members.get(best).add(c);
        }
        if (ranges.isEmpty()) { // only small things (e.g. "- . -"): one line
            ranges.add(new int[]{0, s.h});
            members.add(new ArrayList<Integer>());
        }
        for (int c : small) {
            float cy = (s.cy0[c] + s.cy1[c]) / 2f;
            int best = 0;
            float bestD = Float.MAX_VALUE;
            for (int l = 0; l < ranges.size(); l++) {
                int[] r = ranges.get(l);
                float lh = r[1] - r[0];
                float dd = cy < r[0] ? r[0] - cy : cy > r[1] ? cy - r[1] : 0;
                if (cy < r[0]) dd *= 0.7f; // accents above a line belong to it more than to the line above
                if (dd < bestD && dd <= lh) { bestD = dd; best = l; }
            }
            members.get(best).add(c);
        }
        // sort the lines top to bottom
        Integer[] order = new Integer[ranges.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final List<int[]> fr = ranges;
        Arrays.sort(order, new Comparator<Integer>() { public int compare(Integer a, Integer b) { return fr.get(a)[0] - fr.get(b)[0]; } });
        for (Integer li : order) {
            List<Glyph> glyphs = groupLetters(s, members.get(li));
            if (!glyphs.isEmpty()) s.lines.add(glyphs);
        }
    }

    /** Components stacked on each other (or nearly inside each other horizontally) are one letter. */
    private static List<Glyph> groupLetters(final Seg s, List<Integer> comps) {
        Collections.sort(comps, new Comparator<Integer>() { public int compare(Integer a, Integer b) { return s.cx0[a] - s.cx0[b]; } });
        List<List<Integer>> groups = new ArrayList<List<Integer>>();
        List<int[]> boxes = new ArrayList<int[]>();
        for (int c : comps) {
            int cw = s.cx1[c] - s.cx0[c], ch = s.cy1[c] - s.cy0[c];
            int target = -1;
            for (int gi = Math.max(0, groups.size() - 3); gi < groups.size(); gi++) {
                int[] b = boxes.get(gi);
                int gw = b[2] - b[0], gh = b[3] - b[1];
                int xo = Math.min(b[2], s.cx1[c]) - Math.max(b[0], s.cx0[c]);
                int yo = Math.min(b[3], s.cy1[c]) - Math.max(b[1], s.cy0[c]);
                int mw = Math.min(cw, gw);
                if (xo <= 0) continue;
                // fully inside the other's box (the circles of a "%" inside its slash), not just overlapping it
                boolean inside = s.cx0[c] >= b[0] - 1 && s.cx1[c] <= b[2] + 1 && s.cy0[c] >= b[1] - 1 && s.cy1[c] <= b[3] + 1
                        || b[0] >= s.cx0[c] - 1 && b[2] <= s.cx1[c] + 1 && b[1] >= s.cy0[c] - 1 && b[3] <= s.cy1[c] + 1;
                inside = inside && xo >= 0.85 * mw;
                boolean stacked = xo >= 0.4 * mw && yo <= 0.2 * Math.min(ch, gh);
                if (inside || stacked) target = gi;
            }
            if (target < 0) {
                List<Integer> g = new ArrayList<Integer>();
                g.add(c);
                groups.add(g);
                boxes.add(new int[]{s.cx0[c], s.cy0[c], s.cx1[c], s.cy1[c]});
            } else {
                groups.get(target).add(c);
                int[] b = boxes.get(target);
                b[0] = Math.min(b[0], s.cx0[c]); b[1] = Math.min(b[1], s.cy0[c]);
                b[2] = Math.max(b[2], s.cx1[c]); b[3] = Math.max(b[3], s.cy1[c]);
            }
        }
        List<Glyph> out = new ArrayList<Glyph>();
        for (List<Integer> g : groups) {
            int pixels = 0;
            for (int c : g) pixels += s.count[c];
            if (pixels < 2) continue; // a lone speck
            int[] ids = new int[g.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = g.get(i);
            Glyph gl = build(s, ids, 0, s.w);
            if (gl != null) out.add(gl);
        }
        Collections.sort(out, new Comparator<Glyph>() { public int compare(Glyph a, Glyph b) { return a.x0 - b.x0; } });
        return out;
    }

    /** The letter made of these components, restricted to zone columns [xa, xb). */
    static Glyph build(Seg s, int[] comps, int xa, int xb) {
        for (int c : comps) s.member[c] = true;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        int bx0 = s.w, by0 = s.h, bx1 = 0, by1 = 0;
        for (int c : comps) { bx0 = Math.min(bx0, s.cx0[c]); by0 = Math.min(by0, s.cy0[c]); bx1 = Math.max(bx1, s.cx1[c]); by1 = Math.max(by1, s.cy1[c]); }
        bx0 = Math.max(bx0, xa); bx1 = Math.min(bx1, xb);
        for (int y = by0; y < by1; y++) {
            for (int x = bx0; x < bx1; x++) {
                int l = s.label[y * s.w + x];
                if (l > 0 && s.member[l]) {
                    if (x < x0) x0 = x; if (x + 1 > x1) x1 = x + 1;
                    if (y < y0) y0 = y; if (y + 1 > y1) y1 = y + 1;
                }
            }
        }
        Glyph g = null;
        if (x1 > x0 && y1 > y0) {
            g = new Glyph();
            g.comps = comps;
            g.cov = grid(s, x0, y0, x1, y1);
            g.x0 = s.zx + x0; g.y0 = s.zy + y0; g.x1 = s.zx + x1; g.y1 = s.zy + y1;
            g.top = y0; g.bottom = y1;
        }
        for (int c : comps) s.member[c] = false;
        return g;
    }

    /**
     * Coverage of the letter on a 16×16 grid that keeps its proportions (the longer side fills the
     * grid, the letter is centred), area-averaged, then blurred. Outside the letter's box counts as 0.
     */
    private static float[] grid(Seg s, int x0, int y0, int x1, int y1) {
        int bw = x1 - x0, bh = y1 - y0;
        float side = Math.max(bw, bh);
        float ox = x0 - (side - bw) / 2f, oy = y0 - (side - bh) / 2f;
        float cell = side / GW;
        float[] g = new float[N];
        for (int gy = 0; gy < GH; gy++) {
            float sy0 = oy + gy * cell, sy1 = sy0 + cell;
            for (int gx = 0; gx < GW; gx++) {
                float sx0 = ox + gx * cell, sx1 = sx0 + cell;
                float acc = 0;
                for (int py = Math.max(y0, (int) Math.floor(sy0)); py < sy1 && py < y1; py++) {
                    float wy = Math.min(sy1, py + 1) - Math.max(sy0, py);
                    if (wy <= 0) continue;
                    for (int px = Math.max(x0, (int) Math.floor(sx0)); px < sx1 && px < x1; px++) {
                        float wx = Math.min(sx1, px + 1) - Math.max(sx0, px);
                        if (wx <= 0) continue;
                        int p = py * s.w + px;
                        int l = s.label[p];
                        float v = (l == 0 || (l > 0 && s.member[l])) ? s.cov[p] : 0f;
                        acc += v * wx * wy;
                    }
                }
                g[gy * GW + gx] = acc / (cell * cell);
            }
        }
        return g;
    }

    /**
     * Direction of the strokes' edges, per zone: much less sensitive to the font (thin/bold,
     * with/without serifs) than the pixels themselves.
     */
    static float[] gradients(float[] g) {
        float[] h = new float[128];
        for (int y = 0; y < GH; y++) {
            for (int x = 0; x < GW; x++) {
                float gx = at(g, x + 1, y - 1) + 2 * at(g, x + 1, y) + at(g, x + 1, y + 1) - at(g, x - 1, y - 1) - 2 * at(g, x - 1, y) - at(g, x - 1, y + 1);
                float gy = at(g, x - 1, y + 1) + 2 * at(g, x, y + 1) + at(g, x + 1, y + 1) - at(g, x - 1, y - 1) - 2 * at(g, x, y - 1) - at(g, x + 1, y - 1);
                float m = (float) Math.sqrt(gx * gx + gy * gy);
                if (m < 1e-3f) continue;
                double ang = Math.atan2(gy, gx);
                if (ang < 0) ang += 2 * Math.PI;
                double b = ang / (2 * Math.PI) * 8;
                int b0 = (int) Math.floor(b) % 8, b1 = (b0 + 1) % 8;
                float fb = (float) (b - Math.floor(b));
                // spread over the 4 nearest zones (zone centres at 1.5, 5.5, 9.5, 13.5)
                float zx = (x - 1.5f) / 4f, zy = (y - 1.5f) / 4f;
                int zx0 = (int) Math.floor(zx), zy0 = (int) Math.floor(zy);
                float fx = zx - zx0, fy = zy - zy0;
                for (int dy = 0; dy <= 1; dy++) {
                    int zyy = zy0 + dy;
                    if (zyy < 0 || zyy > 3) continue;
                    float wy = dy == 0 ? 1 - fy : fy;
                    for (int dx = 0; dx <= 1; dx++) {
                        int zxx = zx0 + dx;
                        if (zxx < 0 || zxx > 3) continue;
                        float wz = wy * (dx == 0 ? 1 - fx : fx) * m;
                        int base = (zyy * 4 + zxx) * 8;
                        h[base + b0] += wz * (1 - fb);
                        h[base + b1] += wz * fb;
                    }
                }
            }
        }
        double n = 0;
        for (int i = 0; i < h.length; i++) { h[i] = (float) Math.sqrt(h[i]); n += h[i] * h[i]; }
        if (n < 1e-9) return null;
        float inv = (float) (1 / Math.sqrt(n));
        for (int i = 0; i < h.length; i++) h[i] *= inv;
        return h;
    }

    private static float at(float[] g, int x, int y) {
        if (x < 0 || y < 0 || x >= GW || y >= GH) return 0f;
        return g[y * GW + x];
    }

    static float[] blur(float[] g) {
        float[] o = new float[N];
        for (int y = 0; y < GH; y++) {
            for (int x = 0; x < GW; x++) {
                float acc = 0, wsum = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= GH) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= GW) continue;
                        float wgt = (dx == 0 ? 2 : 1) * (dy == 0 ? 2 : 1);
                        acc += g[yy * GW + xx] * wgt;
                        wsum += wgt;
                    }
                }
                o[y * GW + x] = acc / wsum;
            }
        }
        return o;
    }

    /** A picture holding exactly one letter (reference fonts): all its ink is that letter. */
    static Glyph wholeGlyph(int[] argb, int w, int h) {
        Seg s = segment(argb, w, h, 0, 0, w, h);
        if (s.problem != null && s.lines.isEmpty()) return null;
        int n = s.count.length - 1;
        int k = 0;
        for (int i = 1; i <= n; i++) if (s.count[i] > 0) k++;
        if (k == 0) return null;
        int[] ids = new int[k];
        k = 0;
        for (int i = 1; i <= n; i++) if (s.count[i] > 0) ids[k++] = i;
        return build(s, ids, 0, s.w);
    }

    // ------------------------------------------------------------------ classification
    static float W_PIX = Float.parseFloat(System.getProperty("ocr.wpix", "0.5"));
    static float W_DIR = Float.parseFloat(System.getProperty("ocr.wdir", "1.0"));
    static float W_ASP = Float.parseFloat(System.getProperty("ocr.wasp", "0.3"));
    static float W_POS = Float.parseFloat(System.getProperty("ocr.wpos", "0.8"));
    static float distance(Features a, Features b, boolean pos) {
        float pix;
        if (a.v == null || b.v == null) pix = (a.v == null && b.v == null) ? 0f : 1f;
        else {
            float dot = 0;
            for (int i = 0; i < N; i++) dot += a.v[i] * b.v[i];
            pix = 1f - dot;
        }
        float dir;
        if (a.grad == null || b.grad == null) dir = (a.grad == null && b.grad == null) ? 0f : 1f;
        else {
            float dot = 0;
            for (int i = 0; i < a.grad.length; i++) dot += a.grad[i] * b.grad[i];
            dir = 1f - dot;
        }
        float shape = W_PIX * pix + W_DIR * dir;
        // a letter a few pixels small: its grid is mostly blur, trust its size and place instead
        float trust = Math.max(0.3f, Math.min(1f, Math.min(a.px, b.px) / 9f));
        float asp = (float) Math.abs(Math.log(a.aspect / b.aspect)) * W_ASP;
        float d = shape * trust + asp;
        if (pos) d += (Math.abs(a.top - b.top) + Math.abs(a.bottom - b.bottom)) * W_POS;
        return d;
    }

    static boolean allowed(FontBook.Sample s, int mode) {
        if (mode == MODE_NUMBER) return NUMBER_CHARS.indexOf(s.c) >= 0;
        return s.learned || TEXT_CHARS.indexOf(s.c) >= 0;
    }

    /** Best known letter for these features. */
    static FontBook.Sample best(Features f, List<FontBook.Sample> samples, int mode, boolean pos, float[] outDist) {
        FontBook.Sample best = null;
        float bd = Float.MAX_VALUE;
        for (FontBook.Sample s : samples) {
            if (!allowed(s, mode)) continue;
            float d = distance(f, s.f, pos);
            if (s.learned) d *= 0.85f; // what the user taught wins a near tie
            if (d < bd) { bd = d; best = s; }
        }
        outDist[0] = bd;
        return best;
    }

    private static List<Glyph> classifyLine(Seg s, List<Glyph> line, List<FontBook.Sample> samples, int mode, float[] metricsOut) {
        if (line.isEmpty()) return line;
        float[] d = new float[1];
        // pass 1: shape only, to find the baseline and the capital height
        List<Integer> bottoms = new ArrayList<Integer>(), caps = new ArrayList<Integer>(), xs = new ArrayList<Integer>();
        int maxH = 0;
        for (Glyph g : line) {
            g.f = Features.of(g.cov, (g.x1 - g.x0) / (float) (g.y1 - g.y0), 0, 0, g.maxSide());
            FontBook.Sample b = best(g.f, samples, mode, false, d);
            char c = b == null ? '?' : b.c;
            int gh = g.bottom - g.top;
            maxH = Math.max(maxH, gh);
            if (NOT_ON_BASELINE.indexOf(c) < 0) bottoms.add(g.bottom);
            g.c = c;
        }
        int baseline = bottoms.isEmpty() ? 0 : medianInt(bottoms);
        if (bottoms.isEmpty()) for (Glyph g : line) baseline = Math.max(baseline, g.bottom);
        for (Glyph g : line) {
            int gh = g.bottom - g.top;
            if (gh < 0.5 * maxH) continue;
            if (CAPS.indexOf(g.c) >= 0) caps.add(baseline - g.top);
            else if ("Il1|!fl".indexOf(g.c) >= 0) caps.add(Math.round((baseline - g.top) / 1.03f)); // capital or ascender: about the same
            else if (XHEIGHT.indexOf(g.c) >= 0) xs.add(baseline - g.top);
        }
        float capH;
        if (!caps.isEmpty()) capH = medianInt(caps);
        else if (!xs.isEmpty()) capH = medianInt(xs) / 0.72f;
        else { capH = 0; for (Glyph g : line) capH = Math.max(capH, baseline - g.top); }
        if (capH < 2) capH = Math.max(2, maxH);
        metricsOut[0] = baseline;
        metricsOut[1] = capH;

        // pass 2: with the position on the line; badly matching wide blobs are tried split
        List<Glyph> out = new ArrayList<Glyph>();
        for (Glyph g : line) {
            classify(g, baseline, capH, samples, mode, s);
            float aspectToLine = (g.x1 - g.x0) / capH;
            if (g.dist > 0.2f && aspectToLine > 0.7f) {
                List<Glyph> parts = trySplit(s, g, baseline, capH, samples, mode);
                if (parts != null) { out.addAll(parts); continue; }
            }
            out.add(g);
        }
        out = mergePieces(s, out, baseline, capH, samples, mode);
        // spaces
        List<Integer> gaps = new ArrayList<Integer>();
        for (int i = 1; i < out.size(); i++) gaps.add(out.get(i).x0 - out.get(i - 1).x1);
        int medGap = gaps.isEmpty() ? 0 : medianInt(gaps);
        for (int i = 1; i < out.size(); i++) {
            int gap = out.get(i).x0 - out.get(i - 1).x1;
            out.get(i).spaceBefore = gap > 0.42f * capH || (gap > 0.25f * capH && gap > 2.5f * Math.max(1, medGap));
        }
        if (mode == MODE_TEXT) { fixCase(out); fixByContext(out); }
        return out;
    }

    private static void classify(Glyph g, int baseline, float capH, List<FontBook.Sample> samples, int mode, Seg s) {
        g.f = Features.of(g.cov, (g.x1 - g.x0) / (float) (g.y1 - g.y0), (baseline - g.top) / capH, (baseline - g.bottom) / capH, g.maxSide());
        // best distance per character, keep the closest 4
        java.util.Map<Character, Float> per = new java.util.HashMap<Character, Float>();
        for (FontBook.Sample sm : samples) {
            if (!allowed(sm, mode)) continue;
            float d = distance(g.f, sm.f, true);
            if (sm.learned) d *= 0.85f;
            Float old = per.get(sm.c);
            if (old == null || d < old) per.put(sm.c, d);
        }
        List<java.util.Map.Entry<Character, Float>> es = new ArrayList<java.util.Map.Entry<Character, Float>>(per.entrySet());
        Collections.sort(es, new Comparator<java.util.Map.Entry<Character, Float>>() {
            public int compare(java.util.Map.Entry<Character, Float> a, java.util.Map.Entry<Character, Float> b) { return Float.compare(a.getValue(), b.getValue()); }
        });
        int k = Math.min(8, es.size());
        g.alt = new char[k];
        g.altDist = new float[k];
        for (int i = 0; i < k; i++) { g.alt[i] = es.get(i).getKey(); g.altDist[i] = es.get(i).getValue(); }
        g.c = k == 0 ? '?' : g.alt[0];
        g.dist = k == 0 ? 9f : g.altDist[0];
    }

    /** Letters whose small and capital forms have the same shape. */
    static final String SAME_SHAPE = "cCoOsSvVwWxXzZuUkK";

    /**
     * c/C, o/O, s/S...: decided by height compared with the line's sure small letters (a, e, n...)
     * and sure capitals/digits, which is much steadier than the absolute position on small text.
     */
    static void fixCase(List<Glyph> line) {
        List<Integer> xh = new ArrayList<Integer>(), cap = new ArrayList<Integer>();
        for (Glyph g : line) {
            int h = g.bottom - g.top;
            if ("aemnr".indexOf(g.c) >= 0) xh.add(h);
            else if ("ABDEFGHLMNQRTY0123456789".indexOf(g.c) >= 0) cap.add(h);
        }
        if (xh.isEmpty() && cap.isEmpty()) return;
        float x = xh.isEmpty() ? 0 : medianInt(xh), c = cap.isEmpty() ? 0 : medianInt(cap);
        for (Glyph g : line) {
            int i = SAME_SHAPE.indexOf(g.c);
            if (i < 0) continue;
            int h = g.bottom - g.top;
            boolean upper;
            if (x > 0 && c > 0) upper = Math.abs(h - c) < Math.abs(h - x);
            else if (x > 0) upper = h > 1.17f * x;
            else upper = h > 0.86f * c;
            char want = upper ? Character.toUpperCase(g.c) : Character.toLowerCase(g.c);
            if (want == g.c) continue;
            for (int k = 0; k < g.alt.length; k++) {
                if (g.alt[k] == want && g.altDist[k] <= g.dist + 0.35f) { g.c = want; g.dist = g.altDist[k]; break; }
            }
        }
    }

    /** Characters that look alike and are told apart by their neighbours. */
    static final String[] LOOK_ALIKE = {"Il1|", "O0", "S5", "B8", "Z2"};

    /**
     * In words, a bar between lower-case letters is "l", between digits "1"; a round thing among
     * digits is "0"... Only switches to a look-alike that matched almost as well.
     */
    static void fixByContext(List<Glyph> line) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < line.size(); i++) {
                Glyph g = line.get(i);
                String group = null;
                for (String gr : LOOK_ALIKE) if (gr.indexOf(g.c) >= 0) group = gr;
                if (group == null) continue;
                Glyph prev = i > 0 && !g.spaceBefore ? line.get(i - 1) : null;
                Glyph next = i + 1 < line.size() && !line.get(i + 1).spaceBefore ? line.get(i + 1) : null;
                int digits = kind(prev, 'd') + kind(next, 'd'), lower = kind(prev, 'l') + kind(next, 'l'), upper = kind(prev, 'u') + kind(next, 'u');
                char want = 0;
                boolean wordStart = prev == null && next != null && kindOf(next.c) == 'l';
                if (group.equals("Il1|") && kindOf(g.c) != 'd' && g.f != null && g.f.top < 0.85f) {
                    g.c = 'i'; // a bar only as tall as the small letters: an "i" whose dot was lost
                    continue;
                }
                if (wordStart && group.equals("Il1|")) want = 'u'; // "Inventory": capital first letter
                else if (digits > 0 && lower + upper == 0) want = 'd';
                else if (lower > 0 && (prev != null && kind(prev, 'l') + kind(prev, 'u') > 0 || upper == 0 && digits == 0)) want = 'l';
                else if (upper > 0 && lower == 0 && digits == 0) want = 'u';
                if (want == 0 || kindOf(g.c) == want) continue;
                for (int k = 0; k < g.alt.length; k++) {
                    char c = g.alt[k];
                    if (group.indexOf(c) >= 0 && kindOf(c) == want && g.altDist[k] <= g.dist + 0.3f) {
                        g.c = c;
                        g.dist = g.altDist[k];
                        break;
                    }
                }
            }
        }
    }

    private static char kindOf(char c) {
        if (Character.isDigit(c)) return 'd';
        if (Character.isLowerCase(c)) return 'l';
        if (Character.isUpperCase(c)) return 'u';
        return 'o';
    }

    private static int kind(Glyph g, char k) {
        if (g == null) return 0;
        // a look-alike neighbour does not decide anything on its own
        for (String gr : LOOK_ALIKE) if (gr.indexOf(g.c) >= 0) return 0;
        return kindOf(g.c) == k ? 1 : 0;
    }

    /** Characters normally drawn in several pieces side by side. */
    static final String MULTIPART = "%\u2030\"=:;!?\u00F7";

    /** A taught character that the user taught from several pieces (their own "%"...). */
    private static boolean isLearnedMulti(Glyph m, List<FontBook.Sample> samples) {
        if (m.comps == null || m.comps.length < 2) return false;
        for (FontBook.Sample s : samples) if (s.learned && s.c == m.c && s.pieces >= 2) return true;
        return false;
    }

    /**
     * Letters drawn in separate pieces side by side (%, ‰, sometimes " or a broken stroke) are put
     * back together when the whole matches better. Only pieces that touch or overlap, at least one
     * of them small, so "rn" never becomes "m".
     */
    private static List<Glyph> mergePieces(Seg s, List<Glyph> line, int baseline, float capH, List<FontBook.Sample> samples, int mode) {
        int n = line.size();
        if (n < 2) return line;
        float[] best = new float[n + 1];
        int[] prev = new int[n + 1];
        Glyph[] merged = new Glyph[n + 1];
        Arrays.fill(best, Float.MAX_VALUE);
        best[0] = 0;
        for (int j = 1; j <= n; j++) {
            Glyph single = line.get(j - 1);
            if (best[j - 1] + single.dist + 0.12f < best[j]) { best[j] = best[j - 1] + single.dist + 0.12f; prev[j] = j - 1; merged[j] = single; }
            for (int i = Math.max(0, j - 3); i < j - 1; i++) {
                if (best[i] == Float.MAX_VALUE) continue;
                boolean close = true, anySmall = false;
                for (int k = i; k < j; k++) {
                    Glyph g = line.get(k);
                    if (g.bottom - g.top < 0.6f * capH) anySmall = true;
                    if (k > i && g.x0 - line.get(k - 1).x1 > Math.max(1, 0.12f * capH)) close = false;
                }
                if (!close || !anySmall) continue;
                List<Integer> ids = new ArrayList<Integer>();
                for (int k = i; k < j; k++) for (int c : line.get(k).comps) ids.add(c);
                int[] arr = new int[ids.size()];
                for (int k = 0; k < arr.length; k++) arr[k] = ids.get(k);
                Glyph m = build(s, arr, 0, s.w);
                if (m == null) continue;
                classify(m, baseline, capH, samples, mode, s);
                float cost = best[i] + m.dist + 0.12f;
                // merging must be clearly better than the pieces on their own
                float piecesCost = 0;
                for (int k = i; k < j; k++) piecesCost += line.get(k).dist + 0.12f;
                boolean multi = MULTIPART.indexOf(m.c) >= 0 || isLearnedMulti(m, samples);
                if (multi && m.dist < 0.3f && cost + 0.1f < best[i] + piecesCost && cost < best[j]) { best[j] = cost; prev[j] = i; merged[j] = m; }
            }
        }
        List<Glyph> out = new ArrayList<Glyph>();
        for (int j = n; j > 0; j = prev[j]) out.add(0, merged[j]);
        return out;
    }

    /** Split a blob of touching letters at its thinnest columns if the pieces match better. */
    private static List<Glyph> trySplit(Seg s, Glyph whole, int baseline, float capH, List<FontBook.Sample> samples, int mode) {
        int zx0 = whole.x0 - s.zx, zx1 = whole.x1 - s.zx;
        int bw = zx1 - zx0;
        for (int c : whole.comps) s.member[c] = true;
        int[] proj = new int[bw];
        for (int y = whole.y0 - s.zy; y < whole.y1 - s.zy; y++) {
            for (int x = zx0; x < zx1; x++) {
                int l = s.label[y * s.w + x];
                if (l > 0 && s.member[l]) proj[x - zx0]++;
            }
        }
        for (int c : whole.comps) s.member[c] = false;
        int minW = Math.max(2, Math.round(capH * 0.18f));
        List<Integer> cuts = new ArrayList<Integer>();
        int maxP = 0;
        for (int p : proj) maxP = Math.max(maxP, p);
        for (int x = minW; x < bw - minW; x++) {
            boolean localMin = proj[x] <= proj[x - 1] && proj[x] <= proj[x + 1];
            if (localMin && proj[x] <= 0.45f * maxP) {
                if (!cuts.isEmpty() && x - cuts.get(cuts.size() - 1) < minW) {
                    if (proj[x] < proj[cuts.get(cuts.size() - 1)]) cuts.set(cuts.size() - 1, x);
                    continue;
                }
                cuts.add(x);
            }
        }
        if (cuts.isEmpty() || cuts.size() > 10) return null;
        // dynamic programming over the cut points: best sum of distances (+ a cost per extra letter)
        int m = cuts.size() + 2;
        int[] pts = new int[m];
        pts[0] = 0;
        for (int i = 0; i < cuts.size(); i++) pts[i + 1] = cuts.get(i);
        pts[m - 1] = bw;
        float[] best = new float[m];
        int[] prev = new int[m];
        Glyph[][] piece = new Glyph[m][m];
        Arrays.fill(best, Float.MAX_VALUE);
        best[0] = 0;
        for (int j = 1; j < m; j++) {
            for (int i = Math.max(0, j - 4); i < j; i++) {
                if (best[i] == Float.MAX_VALUE) continue;
                Glyph p = build(s, whole.comps, zx0 + pts[i], zx0 + pts[j]);
                if (p == null) continue;
                classify(p, baseline, capH, samples, mode, s);
                piece[i][j] = p;
                float cost = best[i] + p.dist + 0.12f;
                if (cost < best[j]) { best[j] = cost; prev[j] = i; }
            }
        }
        if (best[m - 1] == Float.MAX_VALUE || prev[m - 1] == 0 && piece[0][m - 1] != null) return null;
        if (best[m - 1] >= whole.dist + 0.12f) return null;
        List<Glyph> parts = new ArrayList<Glyph>();
        for (int j = m - 1; j > 0; j = prev[j]) parts.add(0, piece[prev[j]][j]);
        return parts.size() >= 2 ? parts : null;
    }

    private static int medianInt(List<Integer> a) {
        List<Integer> c = new ArrayList<Integer>(a);
        Collections.sort(c);
        return c.get(c.size() / 2);
    }
}
