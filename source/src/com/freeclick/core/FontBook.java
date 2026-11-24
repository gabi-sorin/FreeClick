package com.freeclick.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The letters TextReader compares against: reference letters drawn from the phone's fonts
 * (added by the app at first use, never saved) and letters the user taught from real screens
 * ("Învață", saved as JSON and kept in backups). Thread-safe: reads happen on the matching thread.
 */
public final class FontBook {

    /** One known letter. */
    public static final class Sample {
        public final char c;
        public final boolean learned;
        /** How many separate pieces the letter had when taught (a "%" is 3). */
        int pieces = 1;
        final TextReader.Features f;
        /** Blurred coverage grid, 0..1 (what is saved for learned letters). */
        final float[] cov;
        Sample(char c, boolean learned, float[] cov, float aspect, float top, float bottom, float px) {
            this.c = c;
            this.learned = learned;
            this.cov = cov;
            this.f = TextReader.Features.of(cov, aspect, top, bottom, px);
        }
    }

    /** Most learned letters kept (the oldest go first). */
    public static final int MAX_LEARNED = 1500;

    private final List<Sample> refs = new ArrayList<Sample>();
    private final List<Sample> learned = new ArrayList<Sample>();
    private int version;

    public synchronized int referenceCount() { return refs.size(); }
    public synchronized int learnedCount() { return learned.size(); }
    /** Changes whenever letters are added or removed (to know when to save). */
    public synchronized int version() { return version; }

    synchronized List<Sample> snapshot() {
        List<Sample> all = new ArrayList<Sample>(learned.size() + refs.size());
        all.addAll(learned);
        all.addAll(refs);
        return all;
    }

    /**
     * Add a reference letter drawn dark on light (or light on dark): the whole picture is one
     * letter; baseline = y of the baseline (the bottom edge of "H"), capHeight = height of "H".
     */
    public boolean addReference(char c, int[] argb, int w, int h, int baseline, int capHeight) {
        if (capHeight <= 0) return false;
        TextReader.Glyph g = TextReader.wholeGlyph(argb, w, h);
        if (g == null) return false;
        float top = (baseline - g.y0) / (float) capHeight;
        float bottom = (baseline - g.y1) / (float) capHeight;
        Sample s = new Sample(c, false, g.cov, (g.x1 - g.x0) / (float) (g.y1 - g.y0), top, bottom, g.maxSide());
        synchronized (this) { refs.add(s); version++; }
        return true;
    }

    public synchronized void clearReferences() { refs.clear(); version++; }

    /**
     * Teach the letters of a read: the characters of `text` (spaces ignored) are matched to the
     * letters found, in order (a character may take 2-3 touching pieces, like "%").
     * Returns how many were learned, or -(letters found) if they cannot be matched.
     */
    public int learn(TextReader.Result r, String text) {
        if (r == null || text == null) return 0;
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < text.length(); i++) if (!Character.isWhitespace(text.charAt(i))) chars.append(text.charAt(i));
        List<TextReader.Glyph> glyphs = TextReader.alignForTeaching(r, chars.toString());
        if (glyphs == null || glyphs.size() != chars.length()) return -r.glyphs.size();
        synchronized (this) {
            for (int i = 0; i < chars.length(); i++) {
                TextReader.Glyph g = glyphs.get(i);
                // replace an older sample of the same letter that looks the same (no endless duplicates)
                for (int k = learned.size() - 1; k >= 0; k--) {
                    Sample old = learned.get(k);
                    if (old.c == chars.charAt(i) && TextReader.distance(g.f, old.f, true) < 0.05f) learned.remove(k);
                }
                Sample ns = new Sample(chars.charAt(i), true, g.cov.clone(), g.f.aspect, g.f.top, g.f.bottom, g.f.px);
                ns.pieces = g.comps == null ? 1 : g.comps.length;
                learned.add(ns);
            }
            while (learned.size() > MAX_LEARNED) learned.remove(0);
            version++;
        }
        return chars.length();
    }

    /** Forget every taught letter (or only one character if c != 0). */
    public synchronized int forget(char c) {
        int n = 0;
        for (int k = learned.size() - 1; k >= 0; k--) {
            if (c == 0 || learned.get(k).c == c) { learned.remove(k); n++; }
        }
        if (n > 0) version++;
        return n;
    }

    /** Which characters were taught, with how many samples each. */
    public synchronized Map<Character, Integer> learnedChars() {
        Map<Character, Integer> m = new java.util.TreeMap<Character, Integer>();
        for (Sample s : learned) { Integer n = m.get(s.c); m.put(s.c, n == null ? 1 : n + 1); }
        return m;
    }

    // ------------------------------------------------------------------ file
    public synchronized String toJson() {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("format", "freeclick-letters");
        o.put("version", 1L);
        List<Object> list = new ArrayList<Object>();
        for (Sample s : learned) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("c", String.valueOf(s.c));
            byte[] b = new byte[s.cov.length];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Math.round(Math.max(0, Math.min(1, s.cov[i])) * 255);
            m.put("g", B64.encode(b));
            m.put("a", Math.round(s.f.aspect * 1000) / 1000.0);
            m.put("t", Math.round(s.f.top * 1000) / 1000.0);
            m.put("b", Math.round(s.f.bottom * 1000) / 1000.0);
            m.put("p", Math.round(s.f.px * 10) / 10.0);
            if (s.pieces > 1) m.put("n", (long) s.pieces);
            list.add(m);
        }
        o.put("learned", list);
        return Json.stringify(o, false);
    }

    /** Add the taught letters of another file (a restored backup) to ours, skipping ones we already have. */
    public int mergeLearned(String json) {
        FontBook other = new FontBook();
        other.loadLearned(json);
        int added = 0;
        synchronized (this) {
            for (Sample s : other.learned) {
                boolean dup = false;
                for (Sample t : learned) if (t.c == s.c && TextReader.distance(t.f, s.f, true) < 0.05f) { dup = true; break; }
                if (dup) continue;
                learned.add(s);
                added++;
            }
            while (learned.size() > MAX_LEARNED) learned.remove(0);
            if (added > 0) version++;
        }
        return added;
    }

    /** Replace the learned letters with those in the file (references are kept). Bad entries are skipped. */
    public void loadLearned(String json) {
        List<Sample> out = new ArrayList<Sample>();
        if (json != null && json.trim().length() > 0) {
            Object p = Json.parse(json);
            if (p instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> o = (Map<String, Object>) p;
                List<Object> list = Json.getArray(o, "learned");
                if (list != null) {
                    for (Object e : list) {
                        if (!(e instanceof Map)) continue;
                        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) e;
                        String c = Json.getString(m, "c", "");
                        byte[] g = B64.decode(Json.getString(m, "g", ""));
                        if (c.length() != 1 || g == null || g.length != TextReader.N) continue;
                        float[] cov = new float[TextReader.N];
                        for (int i = 0; i < cov.length; i++) cov[i] = (g[i] & 255) / 255f;
                        float a = (float) Json.getDouble(m, "a", 0.6);
                        if (!(a > 0.01f && a < 50f)) continue;
                        Sample ns = new Sample(c.charAt(0), true, cov, a, (float) Json.getDouble(m, "t", 1), (float) Json.getDouble(m, "b", 0),
                                (float) Math.max(1, Math.min(999, Json.getDouble(m, "p", 20))));
                        ns.pieces = Math.max(1, Math.min(9, Json.getInt(m, "n", 1)));
                        out.add(ns);
                        if (out.size() >= MAX_LEARNED) break;
                    }
                }
            }
        }
        synchronized (this) { learned.clear(); learned.addAll(out); version++; }
    }
}
