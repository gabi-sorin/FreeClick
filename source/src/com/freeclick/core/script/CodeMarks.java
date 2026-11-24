package com.freeclick.core.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The places a script names with numbers written in its text: point(x, y), tap(x, y), rect(…),
 * swipe(a, b), pixel(point) + #colour, gaseste("imagine"). The editor draws them on a screenshot
 * and, when one is dragged there, rewrites exactly those numbers in the code.
 * Works on any text, also half-written or wrong code (comments and strings are skipped).
 */
public final class CodeMarks {
    private CodeMarks() {}

    /** Categories (also the bits of the "what to show" setting). */
    public static final int TOUCH = 0, SWIPE = 1, ZONE = 2, COLOR = 3, IMAGE = 4;

    public static final class Mark {
        public final int kind;
        /** 1-based line of the mark's first character. */
        public int line;
        /** Variable name ("butonul") or "linia N". */
        public String label;
        /**
         * TOUCH: x, y · SWIPE: x1, y1, x2, y2 · ZONE: x1, y1, x2, y2 ·
         * COLOR: x, y, rgb (x = y = -1: a colour with no place; rgb = -1: a place with no colour) · IMAGE: none.
         */
        public int[] values;
        /** Text positions [start, end) of the numbers in values that can be rewritten (in order). */
        public int[] starts, ends;
        /** COLOR: position of the #RRGGBB text (-1 if none) and the tolerance written after it (-1 if none). */
        public int hexStart = -1, hexEnd = -1, tolerance = -1;
        /** IMAGE: the picture's name. */
        public String image;
        /** Start and end of the whole mark in the text. */
        public int start, end;

        Mark(int kind) { this.kind = kind; }

        /** The numbers that can be moved (x/y pairs): 2 for a point, 4 for a swipe or a zone. */
        public int movable() { return starts == null ? 0 : starts.length; }

        public boolean hasPlace() { return kind != IMAGE && !(kind == COLOR && values[0] < 0); }
    }

    // ------------------------------------------------------------------ a forgiving tokenizer
    private static final int ID = 0, NUM = 1, STR = 2, HEX = 3, P = 4;

    private static final class T {
        final int k, s, e;
        final String text;
        T(int k, int s, int e, String text) { this.k = k; this.s = s; this.e = e; this.text = text; }
        boolean is(String p) { return text.equals(p) && (k == P || k == ID); }
    }

    /** Tokens, with "#define p point"-style short names read as what they stand for (p(1, 2) is a point). */
    private static List<T> tokens(String src) {
        List<T> raw = rawTokens(src);
        if (src.indexOf("#define") < 0) return raw;
        java.util.Map<String, String> alias = new java.util.HashMap<String, String>();
        List<T> out = new ArrayList<T>();
        for (int i = 0; i < raw.size(); i++) {
            T a = raw.get(i);
            if (a.is("#") && i + 3 < raw.size() && raw.get(i + 1).is("define") && raw.get(i + 2).k == ID && raw.get(i + 3).k == ID
                    && !hasNewline(src, a.s, raw.get(i + 3).e)
                    && (i + 4 >= raw.size() || hasNewline(src, raw.get(i + 3).e, raw.get(i + 4).s))) {
                alias.put(raw.get(i + 2).text, raw.get(i + 3).text);
                i += 3;
                continue;
            }
            if (a.k == ID && alias.containsKey(a.text)) a = new T(ID, a.s, a.e, alias.get(a.text));
            out.add(a);
        }
        return out;
    }

    private static boolean hasNewline(String src, int from, int to) {
        int k = src.indexOf('\n', from);
        return k >= 0 && k < to;
    }

    private static List<T> rawTokens(String src) {
        List<T> out = new ArrayList<T>();
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') { while (i < n && src.charAt(i) != '\n') i++; continue; }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }
            int s = i;
            if (c == '"' || c == '\'') {
                i++;
                StringBuilder b = new StringBuilder();
                while (i < n && src.charAt(i) != c && src.charAt(i) != '\n') {
                    if (src.charAt(i) == '\\' && i + 1 < n) { i++; }
                    b.append(src.charAt(i));
                    i++;
                }
                if (i < n && src.charAt(i) == c) i++;
                out.add(new T(STR, s, i, b.toString()));
            } else if (c == '#' && i + 7 <= n && isHex(src, i + 1, 6) && (i + 7 == n || !Character.isLetterOrDigit(src.charAt(i + 7)))) {
                i += 7;
                out.add(new T(HEX, s, i, src.substring(s, i)));
            } else if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '.' || src.charAt(i) == '_')) i++;
                out.add(new T(NUM, s, i, src.substring(s, i)));
            } else if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                out.add(new T(ID, s, i, src.substring(s, i)));
            } else {
                i++;
                out.add(new T(P, s, i, String.valueOf(c)));
            }
        }
        return out;
    }

    private static boolean isHex(String s, int from, int len) {
        for (int i = from; i < from + len; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    // ------------------------------------------------------------------ scanning
    private static final Set<String> TAPS = set("tap", "atinge", "click", "dubluTap", "doubleTap", "dublu_tap",
            "apasaLung", "longPress", "apasa_lung");
    private static final Set<String> SWIPES = set("swipe", "gliseaza", "trage", "drag", "pasSwipe", "stepSwipe");
    private static final Set<String> PIXELS = set("pixel", "culoare", "pipeta");
    private static final Set<String> WAIT_COLOR = set("asteaptaCuloare", "waitColor", "asteapta_culoare");
    private static final Set<String> PATTERNS = set("model", "pattern", "modelPixeli");
    private static final Set<String> IMAGES = set("apare", "visible", "vizibil", "gaseste", "find", "gasesteToate", "findAll",
            "gaseste_toate", "asteaptaImagine", "waitImage", "asteapta_imagine");

    private static Set<String> set(String... a) { return new HashSet<String>(java.util.Arrays.asList(a)); }

    public static List<Mark> scan(String src) {
        if (src == null) src = "";
        List<T> t = tokens(src);
        List<Mark> out = new ArrayList<Mark>();
        int[] lineStarts = lineStarts(src);
        // call frames: name of the call and the index of the argument being read
        List<String> frameName = new ArrayList<String>();
        List<Integer> frameArg = new ArrayList<Integer>();
        Set<Integer> consumed = new HashSet<Integer>(); // first tokens of point(…) already part of a swipe
        Mark pendingColor = null; // a place read with pixel(…) that the next #colour of the statement belongs to
        List<Mark> patternPoints = new ArrayList<Mark>(); // model({p1, p2…}, {#c1, #c2…}): the k-th colour → the k-th point
        int patternColor = 0;
        for (int i = 0; i < t.size(); i++) {
            T k = t.get(i);
            if (k.k == P) {
                if (k.is(";")) { pendingColor = null; patternPoints.clear(); patternColor = 0; }
                if ((k.is("{") || k.is("}")) && frameName.isEmpty()) { pendingColor = null; } // a block, not a list
                if (k.is("{") && !frameName.isEmpty()) { // an initializer list inside a call: its own commas
                    frameName.add("{");
                    frameArg.add(0);
                } else if (k.is("}") && !frameName.isEmpty() && "{".equals(frameName.get(frameName.size() - 1))) {
                    frameName.remove(frameName.size() - 1);
                    frameArg.remove(frameArg.size() - 1);
                } else if (k.is("(")) {
                    String name = i > 0 && t.get(i - 1).k == ID ? t.get(i - 1).text : "";
                    frameName.add(name);
                    frameArg.add(0);
                } else if (k.is(")") && !frameName.isEmpty()) {
                    frameName.remove(frameName.size() - 1);
                    frameArg.remove(frameArg.size() - 1);
                } else if (k.is(",") && !frameArg.isEmpty()) {
                    frameArg.set(frameArg.size() - 1, frameArg.get(frameArg.size() - 1) + 1);
                }
                continue;
            }
            if (k.k == HEX) {
                int rgb = Integer.parseInt(k.text.substring(1), 16);
                int tol = -1;
                if (i + 2 < t.size() && t.get(i + 1).is(",") && t.get(i + 2).k == NUM && isInt(t.get(i + 2).text)) tol = Integer.parseInt(t.get(i + 2).text);
                if (inPattern(frameName, frameArg, 1) && patternColor < patternPoints.size()) {
                    Mark pm = patternPoints.get(patternColor++);
                    pm.values[2] = rgb;
                    pm.hexStart = k.s;
                    pm.hexEnd = k.e;
                    continue;
                }
                if (pendingColor != null && pendingColor.values[2] < 0) {
                    pendingColor.values[2] = rgb;
                    pendingColor.hexStart = k.s;
                    pendingColor.hexEnd = k.e;
                    pendingColor.tolerance = tol;
                    pendingColor.end = Math.max(pendingColor.end, k.e);
                    pendingColor = null;
                } else {
                    Mark m = new Mark(COLOR);
                    m.values = new int[]{-1, -1, rgb};
                    m.hexStart = k.s;
                    m.hexEnd = k.e;
                    m.tolerance = tol;
                    m.start = k.s;
                    m.end = k.e;
                    m.label = assignedName(t, i);
                    out.add(m);
                }
                continue;
            }
            if (k.k != ID || i + 1 >= t.size() || !t.get(i + 1).is("(")) continue;
            String name = k.text;
            String outer = frameName.isEmpty() ? "" : frameName.get(frameName.size() - 1);
            int outerArg = frameArg.isEmpty() ? -1 : frameArg.get(frameArg.size() - 1);
            if (name.equals("point") && !consumed.contains(i)) {
                int[] p = ints(t, i + 2, 2);
                if (p == null) continue;
                Mark m;
                if (inPattern(frameName, frameArg, 0)) {
                    m = new Mark(COLOR);
                    m.values = new int[]{num(src, t, p[0]), num(src, t, p[1]), -1};
                    patternPoints.add(m);
                } else if (PIXELS.contains(outer) || (WAIT_COLOR.contains(outer) && outerArg == 0)) {
                    m = new Mark(COLOR);
                    m.values = new int[]{num(src, t, p[0]), num(src, t, p[1]), -1};
                    pendingColor = m;
                } else {
                    m = new Mark(TOUCH);
                    m.values = new int[]{num(src, t, p[0]), num(src, t, p[1])};
                }
                spans(m, src, t, p);
                m.start = k.s;
                m.end = t.get(p[1]).e + 1;
                m.label = assignedName(t, i);
                out.add(m);
            } else if (name.equals("rect")) {
                int[] p = ints(t, i + 2, 4);
                if (p == null) continue;
                Mark m = new Mark(ZONE);
                m.values = new int[]{num(src, t, p[0]), num(src, t, p[1]), num(src, t, p[2]), num(src, t, p[3])};
                spans(m, src, t, p);
                m.start = k.s;
                m.end = t.get(p[3]).e + 1;
                m.label = assignedName(t, i);
                out.add(m);
            } else if (SWIPES.contains(name)) {
                // swipe(point(a, b), point(c, d)…) or swipe(x1, y1, x2, y2…)
                int[] four = ints(t, i + 2, 4);
                int[] p1 = null, p2 = null;
                if (four == null && i + 2 < t.size() && t.get(i + 2).is("point")) {
                    p1 = ints(t, i + 4, 2);
                    int after = p1 == null ? -1 : p1[1] + 1; // the ")" of the first point
                    if (after > 0 && after + 2 < t.size() && t.get(after).is(")") && t.get(after + 1).is(",") && t.get(after + 2).is("point")
                            && after + 3 < t.size() && t.get(after + 3).is("(")) {
                        p2 = ints(t, after + 4, 2);
                        if (p2 != null) { consumed.add(i + 2); consumed.add(after + 2); }
                    }
                }
                int[] p;
                if (four != null) p = four;
                else if (p1 != null && p2 != null) p = new int[]{p1[0], p1[1], p2[0], p2[1]};
                else continue;
                Mark m = new Mark(SWIPE);
                m.values = new int[]{num(src, t, p[0]), num(src, t, p[1]), num(src, t, p[2]), num(src, t, p[3])};
                spans(m, src, t, p);
                m.start = k.s;
                m.end = t.get(p[3]).e + 1;
                m.label = assignedName(t, i);
                out.add(m);
            } else if (TAPS.contains(name) || PIXELS.contains(name)) {
                // tap(x, y) / pixel(x, y): the two numbers right in the call
                int[] p = ints(t, i + 2, 2);
                if (p == null) continue;
                int next = p[1] + 1;
                if (next >= t.size() || !(t.get(next).is(")") || t.get(next).is(","))) continue;
                boolean pixel = PIXELS.contains(name);
                Mark m = new Mark(pixel ? COLOR : TOUCH);
                m.values = pixel ? new int[]{num(src, t, p[0]), num(src, t, p[1]), -1} : new int[]{num(src, t, p[0]), num(src, t, p[1])};
                spans(m, src, t, p);
                m.start = k.s;
                m.end = t.get(p[1]).e;
                m.label = assignedName(t, i);
                out.add(m);
                if (pixel) pendingColor = m;
            } else if (IMAGES.contains(name) && i + 2 < t.size() && t.get(i + 2).k == STR) {
                Mark m = new Mark(IMAGE);
                m.values = new int[0];
                m.image = t.get(i + 2).text;
                m.start = k.s;
                m.end = t.get(i + 2).e;
                m.label = m.image;
                out.add(m);
            }
        }
        for (Mark m : out) {
            m.line = lineOf(lineStarts, m.start);
            if (m.label == null) m.label = "linia " + m.line;
        }
        return out;
    }

    /** Inside {…} that is argument 'arg' of model(…). */
    private static boolean inPattern(List<String> names, List<Integer> args, int arg) {
        int n = names.size();
        return n >= 2 && "{".equals(names.get(n - 1)) && PATTERNS.contains(names.get(n - 2)) && args.get(n - 2) == arg;
    }

    /** Token indexes of n integers separated by commas, starting at index 'from' (each may have a minus). */
    private static int[] ints(List<T> t, int from, int n) {
        int[] idx = new int[n];
        int i = from;
        for (int k = 0; k < n; k++) {
            if (k > 0) {
                if (i >= t.size() || !t.get(i).is(",")) return null;
                i++;
            }
            if (i < t.size() && t.get(i).is("-")) i++;
            if (i >= t.size() || t.get(i).k != NUM || !isInt(t.get(i).text)) return null;
            idx[k] = i;
            i++;
        }
        return idx;
    }

    private static boolean isInt(String s) {
        if (s.isEmpty() || s.length() > 9) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /** The number at token index i, with its minus if there is one right before it. */
    private static int num(String src, List<T> t, int i) {
        int v = Integer.parseInt(t.get(i).text);
        return i > 0 && t.get(i - 1).is("-") ? -v : v; // ints() only lets a minus stand right before a number
    }

    private static void spans(Mark m, String src, List<T> t, int[] idx) {
        m.starts = new int[idx.length];
        m.ends = new int[idx.length];
        for (int k = 0; k < idx.length; k++) {
            int i = idx[k];
            boolean minus = i > 0 && t.get(i - 1).is("-");
            m.starts[k] = minus ? t.get(i - 1).s : t.get(i).s;
            m.ends[k] = t.get(i).e;
        }
    }

    /** "point butonul = point(…)" / "butonul = point(…)" → "butonul". */
    private static String assignedName(List<T> t, int i) {
        if (i >= 2 && t.get(i - 1).is("=") && t.get(i - 2).k == ID) return t.get(i - 2).text;
        return null;
    }

    private static int[] lineStarts(String s) {
        List<Integer> l = new ArrayList<Integer>();
        l.add(0);
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') l.add(i + 1);
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    private static int lineOf(int[] starts, int pos) {
        int lo = 0, hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= pos) lo = mid; else hi = mid - 1;
        }
        return lo + 1;
    }

    // ------------------------------------------------------------------ rewriting
    /** The text with the mark's movable numbers replaced by {@code values} (same count as {@link Mark#movable()}). */
    public static String replace(String src, Mark m, int[] values) {
        StringBuilder b = new StringBuilder(src);
        for (int k = m.movable() - 1; k >= 0; k--) b.replace(m.starts[k], m.ends[k], String.valueOf(values[k]));
        return b.toString();
    }

    /** A new #RRGGBB for a colour mark. */
    public static String replaceColor(String src, Mark m, int rgb) {
        if (m.hexStart < 0) return src;
        return src.substring(0, m.hexStart) + String.format(java.util.Locale.US, "#%06X", rgb & 0xFFFFFF) + src.substring(m.hexEnd);
    }

    /** The mark on a line (the first one with a place on the screen), or null. */
    public static Mark onLine(List<Mark> marks, int line) {
        Mark any = null;
        for (Mark m : marks) {
            if (m.line != line) continue;
            if (m.hasPlace()) return m;
            if (any == null) any = m;
        }
        return any;
    }
}
