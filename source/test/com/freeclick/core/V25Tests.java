package com.freeclick.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** FreeClick 2.5: gestures read from human-like fingers (the flexible part, measured in detail). */
public final class V25Tests {

    /** Events {time, action, id, x, y} of one gesture, made the way real fingers move (shaky, uneven timing). */
    static final class Hand {
        final Random r;
        final List<long[]> ev = new ArrayList<long[]>();
        Hand(Random r) { this.r = r; }

        /** One finger from (x, y) along a path (dx, dy over time), with tremor and touchscreen timing (8–17 ms). */
        void finger(int id, long t0, long dur, double x, double y, double dx, double dy, double curve, double tremor) {
            ev.add(new long[]{t0, TouchTracker.DOWN, id, Math.round(x), Math.round(y)});
            long t = t0;
            while (true) {
                t += 8 + r.nextInt(10);
                boolean last = t >= t0 + dur;
                if (last) t = t0 + dur;
                double f = (t - t0) / (double) Math.max(1, dur);
                double e = f * f * (3 - 2 * f); // fingers speed up, then slow down
                double px = x + dx * e - dy * curve * Math.sin(Math.PI * e) + (r.nextDouble() * 2 - 1) * tremor;
                double py = y + dy * e + dx * curve * Math.sin(Math.PI * e) + (r.nextDouble() * 2 - 1) * tremor;
                ev.add(new long[]{t, last ? TouchTracker.UP : TouchTracker.MOVE, id, Math.round(px), Math.round(py)});
                if (last) break;
            }
        }

        void feed(TouchTracker tr) {
            Collections.sort(ev, new Comparator<long[]>() {
                public int compare(long[] a, long[] b) { return a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]); }
            });
            for (long[] e : ev) tr.feed((int) e[1], (int) e[2], e[3], e[4], e[0]);
        }
    }

    private static double between(Random r, double a, double b) { return a + r.nextDouble() * (b - a); }

    /** Harsher hands: twice the tremor, swipes further off their axis, fingers landing further apart in time. */
    static boolean harsh;
    private static double tremor(double t) { return harsh ? t * 2.2 : t; }

    /** Makes one gesture of the given kind on the tracker; returns what asteaptaGest() should say. */
    static String make(String kind, Random r, TouchTracker tr, long t) {
        Hand h = new Hand(r);
        double x = between(r, 200, 880), y = between(r, 400, tremor(2000));
        String want;
        if (kind.equals("tap")) {
            h.finger(0, t, 40 + r.nextInt(220), x, y, between(r, -6, 6), between(r, -6, 6), 0, tremor(2.5));
            want = "tap";
        } else if (kind.equals("lung")) {
            h.finger(0, t, 600 + r.nextInt(1600), x, y, between(r, -8, 8), between(r, -8, 8), 0, tremor(3));
            want = "lung";
        } else if (kind.equals("swipe")) {
            int d = r.nextInt(4);
            double len = between(r, 130, 800), off = Math.toRadians(between(r, harsh ? -35 : -28, harsh ? 35 : 28));
            double ang = (d == 0 ? 0 : d == 1 ? Math.PI : d == 2 ? -Math.PI / 2 : Math.PI / 2) + off;
            h.finger(0, t, 70 + r.nextInt(650), x, y, Math.cos(ang) * len, Math.sin(ang) * len, between(r, -0.12, 0.12), tremor(2));
            want = "swipe " + (d == 0 ? "dreapta" : d == 1 ? "stanga" : d == 2 ? "sus" : "jos");
        } else if (kind.equals("2 degete")) {
            long d0 = 70 + r.nextInt(200), lag = r.nextInt((int) Math.min(harsh ? 130 : 90, d0 - 30)); // both fingers down together at some point
            h.finger(0, t, d0, x, y, between(r, -8, 8), between(r, -8, 8), 0, tremor(2.5));
            h.finger(1, t + lag, 60 + r.nextInt(200), x + between(r, 120, 350), y + between(r, -80, 80), between(r, -8, 8), between(r, -8, 8), 0, tremor(2.5));
            want = "2 degete";
        } else if (kind.equals("zoom")) {
            boolean out = r.nextBoolean();
            double d0 = between(r, 160, 420), k = out ? between(r, 1.45, 3.0) : between(r, 0.3, 0.68), a = between(r, 0, Math.PI);
            double turn = Math.toRadians(between(r, -10, 10)), d1 = d0 * k;
            long dur = 200 + r.nextInt(600), lag = r.nextInt(60);
            double cx = 540, cy = 1200;
            h.finger(0, t, dur, cx - Math.cos(a) * d0 / 2, cy - Math.sin(a) * d0 / 2,
                    -Math.cos(a + turn) * d1 / 2 + Math.cos(a) * d0 / 2, -Math.sin(a + turn) * d1 / 2 + Math.sin(a) * d0 / 2, 0, tremor(2));
            h.finger(1, t + lag, dur - lag, cx + Math.cos(a) * d0 / 2, cy + Math.sin(a) * d0 / 2,
                    Math.cos(a + turn) * d1 / 2 - Math.cos(a) * d0 / 2, Math.sin(a + turn) * d1 / 2 - Math.sin(a) * d0 / 2, 0, tremor(2));
            want = "zoom " + (out ? "mareste" : "micsoreaza");
        } else if (kind.equals("rotire")) {
            boolean cw = r.nextBoolean();
            double d0 = between(r, 200, 450), a = between(r, 0, Math.PI), turn = Math.toRadians(between(r, 40, 120)) * (cw ? 1 : -1);
            double d1 = d0 * between(r, 0.92, tremor(1.08));
            long dur = 300 + r.nextInt(600), lag = r.nextInt(50);
            double cx = 540, cy = 1200;
            // the fingers turn around their middle (straight-line interpolation is close enough for a measured end)
            h.finger(0, t, dur, cx - Math.cos(a) * d0 / 2, cy - Math.sin(a) * d0 / 2,
                    -Math.cos(a + turn) * d1 / 2 + Math.cos(a) * d0 / 2, -Math.sin(a + turn) * d1 / 2 + Math.sin(a) * d0 / 2, 0, tremor(2));
            h.finger(1, t + lag, dur - lag, cx + Math.cos(a) * d0 / 2, cy + Math.sin(a) * d0 / 2,
                    Math.cos(a + turn) * d1 / 2 - Math.cos(a) * d0 / 2, Math.sin(a + turn) * d1 / 2 - Math.sin(a) * d0 / 2, 0, tremor(2));
            want = "rotire " + (cw ? "dreapta" : "stanga");
        } else { // "2 degete swipe"
            int d = r.nextInt(4);
            double len = between(r, 160, 700), ang = (d == 0 ? 0 : d == 1 ? Math.PI : d == 2 ? -Math.PI / 2 : Math.PI / 2) + Math.toRadians(between(r, -20, 20));
            long dur = 150 + r.nextInt(500), lag = r.nextInt(50);
            double gap = between(r, 150, tremor(300));
            h.finger(0, t, dur, x, y, Math.cos(ang) * len, Math.sin(ang) * len, 0, tremor(2));
            h.finger(1, t + lag, dur - lag, x + gap, y, Math.cos(ang) * len, Math.sin(ang) * len, 0, tremor(2));
            want = "2 degete swipe " + (d == 0 ? "dreapta" : d == 1 ? "stanga" : d == 2 ? "sus" : "jos");
        }
        h.feed(tr);
        return want;
    }

    static void testGesturesFromHumanLikeFingers() {
        harsh = false;
        measure(2025, "normale", true);
        harsh = true;
        measure(2026, "tremurate (tremur ×2,2, swipe-uri la ±35°, degete la 130 ms unul de altul)", false);
        harsh = false;
    }

    private static void measure(long seed, String what, boolean strict) {
        String[] kinds = {"tap", "lung", "swipe", "2 degete", "zoom", "rotire", "2 degete swipe"};
        Random r = new Random(seed);
        Map<String, int[]> score = new LinkedHashMap<String, int[]>();
        StringBuilder wrong = new StringBuilder();
        int total = 0, ok = 0;
        for (int k = 0; k < 7000; k++) {
            String kind = kinds[k % kinds.length];
            TouchTracker tr = new TouchTracker();
            tr.slopPx = 36; // 12 dp on a 3× screen, as in the app
            String want = make(kind, r, tr, 1000);
            TouchTracker.Gesture g = tr.next(0, null);
            String got = g == null ? "(nimic)" : g.describe();
            int[] s = score.get(kind);
            if (s == null) { s = new int[2]; score.put(kind, s); }
            s[1]++;
            total++;
            if (got.equals(want)) { s[0]++; ok++; } else if (wrong.length() < 600) wrong.append(want).append(" → ").append(got).append("; ");
        }
        StringBuilder rep = new StringBuilder("    gesturi, degete " + what + ": ");
        for (Map.Entry<String, int[]> e : score.entrySet()) {
            rep.append(e.getKey()).append(' ').append(e.getValue()[0]).append('/').append(e.getValue()[1]).append(" · ");
        }
        rep.append(String.format("total %.2f%%", ok * 100.0 / total));
        System.out.println(rep);
        if (!strict) { if (wrong.length() > 0) System.out.println("      greșite (exemple): " + wrong); return; }
        for (Map.Entry<String, int[]> e : score.entrySet()) {
            T.ok(e.getValue()[0] >= e.getValue()[1] * 0.99, e.getKey() + ": " + e.getValue()[0] + "/" + e.getValue()[1] + " · " + wrong);
        }
    }

    static void testTapRunsDoubleAndTriple() {
        Random r = new Random(9);
        int n = 2000, ok = 0;
        String first = null;
        for (int k = 0; k < n; k++) {
            TouchTracker tr = new TouchTracker();
            tr.slopPx = 36;
            tr.doubleDistPx = 120;
            int taps = 1 + r.nextInt(4);
            long t = 1000;
            double x = between(r, 200, 880), y = between(r, 400, 2000);
            for (int i = 0; i < taps; i++) {
                Hand h = new Hand(r);
                long d = 40 + r.nextInt(120);
                h.finger(0, t, d, x + between(r, -25, 25), y + between(r, -25, 25), between(r, -5, 5), between(r, -5, 5), 0, 2);
                h.feed(tr);
                t += d + 60 + r.nextInt(220); // a quick run: 60–280 ms between taps
            }
            boolean good = tr.nthTap(0, taps) != null && tr.nthTap(0, taps + 1) == null;
            if (taps >= 2) good &= tr.next(0, TouchTracker.DOUBLE) != null;
            if (good) ok++; else if (first == null) first = taps + " taps";
        }
        System.out.println(String.format("    tap-uri la rând (1–4): %d/%d numărate corect", ok, n));
        T.ok(ok >= n * 0.99, "runs of taps: " + ok + "/" + n + " " + first);
    }

    static void testZoomFactorAndRotationAngle() {
        Random r = new Random(4);
        int n = 1000, ok = 0;
        for (int k = 0; k < n; k++) {
            TouchTracker tr = new TouchTracker();
            tr.slopPx = 36;
            String want = make(r.nextBoolean() ? "zoom" : "rotire", r, tr, 1000);
            TouchTracker.Gesture g = tr.next(0, null);
            if (g == null) continue;
            if (want.startsWith("zoom") && g.type.equals(TouchTracker.ZOOM) && (want.endsWith("mareste") ? g.scale > 1.3 : g.scale < 0.75)) ok++;
            if (want.startsWith("rotire") && g.type.equals(TouchTracker.ROTATE) && Math.abs(g.rotation) >= 30 && (g.rotation > 0) == want.endsWith("dreapta")) ok++;
        }
        System.out.println(String.format("    factor zoom / unghi rotire în intervalul așteptat: %d/%d", ok, n));
        T.ok(ok >= n * 0.99, "zoom factor and angle: " + ok + "/" + n);
    }

    static void testScriptsSeeTheNewGestures() {
        ScriptTests.Host h = new ScriptTests.Host();
        Random r = new Random(1);
        // a pinch out at 2 s, then three quick taps at 5 s
        TouchTracker tmp = new TouchTracker();
        Hand pinch = new Hand(r);
        pinch.finger(0, 2000, 400, 440, 1200, -150, 0, 0, 1);
        pinch.finger(1, 2010, 390, 640, 1200, 150, 0, 0, 1);
        h.fingerScript.addAll(pinch.ev);
        for (int i = 0; i < 3; i++) {
            Hand tap = new Hand(r);
            tap.finger(0, 5000 + i * 180, 60, 300, 300, 0, 0, 0, 1);
            h.fingerScript.addAll(tap.ev);
        }
        Collections.sort(h.fingerScript, new Comparator<long[]>() {
            public int compare(long[] a, long[] b) { return a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]); }
        });
        ScriptTests.Rec rec = ScriptTests.run("print(asteaptaGest());\nprint(factorGest() > 2);\npoint p3 = asteaptaTapuri(3);\nprint(p3.x);\n"
                + "asculta(true);\nprint(gesturiNoi());\n", h);
        T.ok(rec.error == null, rec.error == null ? "" : rec.error.describe());
        T.eq("zoom mareste|true|300|0", String.join("|", h.out), "zoom, factor, triple tap, nothing new yet");
    }

    static void testScriptButtonsAndListeningZone() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.pressScript.add(new long[]{3000, 2}); // "Stop" at 3 s
        ScriptTests.Rec rec = ScriptTests.run("int start = buton(\"▶\", rect(0, 0, 200, 120));\nint stopB = buton(\"■\", rect(220, 0, 420, 120));\n"
                + "int n = 0;\nwhile (!apasat(stopB)) { n++; wait(500); }\nprint(n >= 4 && n <= 6);\nprint(asteaptaButon(1s));\nstergeButoanele();\n", h);
        T.ok(rec.error == null, rec.error == null ? "" : rec.error.describe());
        T.eq("[▶ 0,0,200,120, ■ 220,0,420,120]", h.buttons.toString(), "two buttons where asked");
        T.eq("true|-1", String.join("|", h.out), "the loop ran until ■; nothing else pressed after");
        T.ok(h.did.contains("remove button 0"), "all removed");
        ScriptTests.Rec bad = ScriptTests.run("buton(\"x\", rect(0, 0, 5, 5));", new ScriptTests.Host());
        T.ok(bad.error != null && bad.error.getMessage().contains("prea mică"), "a too small button is explained");

        // only gestures starting in the zone are heard
        ScriptTests.Host z = new ScriptTests.Host();
        z.finger(1500, 0, 900, 2000, 900, 2000, 60);   // outside: the game gets it, the script does not
        z.finger(2500, 0, 100, 100, 100, 100, 60);     // inside
        ScriptTests.Rec zr = ScriptTests.run("ascultaInZona(rect(0, 0, 300, 300));\npoint p2 = asteaptaAtingere();\nprint(p2.x);\nascultaPeTotEcranul();\n", z);
        T.ok(zr.error == null, zr.error == null ? "" : zr.error.describe());
        T.eq("100", z.out.get(0), "the touch outside the zone was not heard");
    }

    static void testAskingAndWaitingForAnyPicture() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.answers.add("dragon");
        h.answers.add("12");
        h.answers.add("Boss");
        h.answers.add(null);
        ScriptTests.Rec rec = ScriptTests.run("print(intreaba(\"Boss?\"));\nprint(intreabaNumar(\"Câte?\", 3) + 1);\n"
                + "print(alege(\"Ce?\", {\"Farm\", \"Boss\"}));\nprint(alege(\"Iar?\", {\"a\"}));\nprint(intreaba(\"x\", \"implicit\"));\n", h);
        T.ok(rec.error == null, rec.error == null ? "" : rec.error.describe());
        T.eq("dragon|13|1|-1|implicit", String.join("|", h.out), "answers, a number, a choice, Anulează, the preset");
    }

    static void testTimeoutTryFilesAndCurvedSwipe() {
        ScriptTests.Host h = new ScriptTests.Host();
        ScriptTests.Rec rec = ScriptTests.run(String.join("\n",
                "int n = 0;",
                "timeout (2s) { while (true) { n++; wait(300); } } else { print(\"expirat\"); }",
                "print(n >= 6 && n <= 8);",
                "timeout (5s) { wait(100); } else { print(\"nu trebuie\"); }",
                "int iesit = 0;",
                "timeout (1s) { timeout (10s) { wait(5s); } else { print(\"interior\"); } } else { iesit = 1; }",
                "print(iesit);",
                "for (int i = 0; i < 3; i++) { timeout (1s) { if (i == 1) break; } }",
                "try { vector<int> v = {1}; print(v[5]); } catch (string e) { print(e.contine(\"5\")); }",
                "try { print(\"ok\"); } catch (string e) { print(\"nu\"); }",
                "adaugaInFisier(\"j.txt\", \"a\");",
                "adaugaInFisier(\"j.txt\", \"b\");",
                "print(citesteFisier(\"j.txt\"), existaFisier(\"j.txt\"), existaFisier(\"x.txt\"));",
                "swipeCurb(point(100, 1000), point(900, 1000), 400, 0.3);"), h);
        T.ok(rec.error == null, rec.error == null ? "" : rec.error.describe());
        T.eq("expirat|true|1|true|ok|abtruefalse", String.join("|", h.out), "timeout, nested timeouts (outer wins), try/catch, files");
        String swipe = h.did.get(h.did.size() - 1);
        T.ok(swipe.startsWith("GESTURE"), "a curved swipe is a free gesture: " + swipe);
        ScriptTests.Rec bad = ScriptTests.run("scrieFisier(\"../x\", \"a\");", new ScriptTests.Host());
        T.ok(bad.error != null && bad.error.getMessage().contains("fără foldere"), "no paths out of the folder");
        com.freeclick.core.script.ScriptError e = com.freeclick.core.script.ScriptRunner.check("try { print(1); }");
        T.ok(e != null && e.getMessage().contains("catch"), "try without catch is explained");
        T.ok(com.freeclick.core.script.ScriptRunner.check("catch (string e) { }") != null, "catch without try");
        ScriptTests.Rec stop = ScriptTests.run("try { stop(); } catch (string e) { print(\"prins\"); }\nprint(\"după\");", new ScriptTests.Host());
        T.ok(stop.error == null, "stop is not a mistake");
    }
}
