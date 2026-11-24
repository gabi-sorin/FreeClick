package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** FreeClick 2.4: bars and colours over a zone, recordings as code, recording from a script, HUD and phone state. */
public final class V24Tests {

    // ------------------------------------------------------------------ helpers: synthetic game screens
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static int rgb(int r, int g, int b) { return clamp(r) << 16 | clamp(g) << 8 | clamp(b); }

    private static int shade(int c, double f, int noise, Random rnd) {
        return rgb((int) (((c >> 16) & 255) * f) + rnd.nextInt(2 * noise + 1) - noise,
                (int) (((c >> 8) & 255) * f) + rnd.nextInt(2 * noise + 1) - noise,
                (int) ((c & 255) * f) + rnd.nextInt(2 * noise + 1) - noise);
    }

    private static int farColour(int from, int minDist, Random rnd) {
        while (true) {
            int c = rnd.nextInt(0x1000000);
            int d = Math.max(Math.abs(((c >> 16) & 255) - ((from >> 16) & 255)),
                    Math.max(Math.abs(((c >> 8) & 255) - ((from >> 8) & 255)), Math.abs((c & 255) - (from & 255))));
            if (d >= minDist) return c;
        }
    }

    /** A game bar: border, a filled part with a vertical gradient and noise, the empty part, maybe "75/100" over it. */
    static final class Bar implements ColorZones.Pixels {
        final int w, h, border, fillPx, fill, empty;
        final boolean text, vertical;
        final int[] px;
        Bar(Random rnd) {
            vertical = rnd.nextInt(5) == 0;
            int along = 150 + rnd.nextInt(750), across = 10 + rnd.nextInt(50);
            w = vertical ? across : along;
            h = vertical ? along : across;
            border = rnd.nextInt(4);
            fill = rgb(40 + rnd.nextInt(216), 40 + rnd.nextInt(216), 40 + rnd.nextInt(216));
            empty = farColour(fill, 90, rnd);
            int inner = along - 2 * border;
            fillPx = Math.round(inner * rnd.nextFloat());
            text = rnd.nextBoolean();
            int lum = (((fill >> 16) & 255) * 299 + ((fill >> 8) & 255) * 587 + (fill & 255) * 114) / 1000;
            int textColour = lum < 150 ? 0xFFFFFF : 0x101010;
            px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = vertical ? h - 1 - y : x, c = vertical ? x : y; // vertical bars fill from the bottom
                    int acrossLen = vertical ? w : h;
                    int v;
                    if (a < border || a >= along - border || c < border || c >= acrossLen - border) v = shade(0x202020, 1, 4, rnd);
                    else if (a - border < fillPx) v = shade(fill, 1 + 0.15 * ((c / (double) acrossLen) - 0.5), 8, rnd);
                    else v = shade(empty, 1, 8, rnd);
                    boolean inText = text && a > along * 0.4 && a < along * 0.6 && c > acrossLen * 0.2 && c < acrossLen * 0.8;
                    if (inText && rnd.nextBoolean()) v = textColour; // readable over the bar
                    px[y * w + x] = v;
                }
            }
        }
        public int at(int x, int y) { return x < 0 || y < 0 || x >= w || y >= h ? -1 : px[y * w + x]; }
        double truth() { return fillPx * 100.0 / ((vertical ? h : w) - 2 * border); }
    }

    static void testBarPercentOnThousandsOfBars() {
        Random rnd = new Random(2024);
        int n = 3000, ok = 0;
        double worst = 0, sum = 0;
        List<Double> errs = new ArrayList<Double>();
        for (int k = 0; k < n; k++) {
            Bar b = new Bar(rnd);
            int[] d = ColorZones.barGrid(b.w, b.h);
            double p = ColorZones.percent(ColorZones.grid(b, 0, 0, b.w, b.h, d[0], d[1]), d[0], d[1], b.fill, 30);
            double e = Math.abs(p - b.truth());
            errs.add(e);
            sum += e;
            worst = Math.max(worst, e);
            if (e <= 4) ok++;
        }
        java.util.Collections.sort(errs);
        System.out.println(String.format("    procentCuloare: %d/%d bare la ±4%% (%.2f%%), eroare medie %.2f, p99 %.2f, maximă %.2f",
                ok, n, ok * 100.0 / n, sum / n, errs.get((int) (n * 0.99)), worst));
        T.ok(ok >= n * 0.99, "≥ 99% of bars within 4 points: " + ok + "/" + n);
    }

    /** A screen with noise, distractor shapes and (maybe) one patch of the target colour. */
    static final class Scene implements ColorZones.Pixels {
        final int w, h, target;
        final int tx, ty, tr; // centre and radius (a disc or a square)
        final boolean square, present;
        final int[] px;
        Scene(Random rnd, boolean present) {
            this.present = present;
            w = 200 + rnd.nextInt(880);
            h = 200 + rnd.nextInt(1300);
            target = rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
            tr = 12 + rnd.nextInt(Math.max(1, Math.min(w, h) / 6));
            tx = tr + rnd.nextInt(Math.max(1, w - 2 * tr));
            ty = tr + rnd.nextInt(Math.max(1, h - 2 * tr));
            square = rnd.nextBoolean();
            int bg = farColour(target, 70, rnd);
            px = new int[w * h];
            for (int i = 0; i < px.length; i++) px[i] = shade(bg, 1, 10, rnd);
            for (int s = 0; s < 6; s++) { // other things on screen, never the target colour
                int c = farColour(target, 70, rnd), r = 10 + rnd.nextInt(60), cx = rnd.nextInt(w), cy = rnd.nextInt(h);
                for (int y = Math.max(0, cy - r); y < Math.min(h, cy + r); y++)
                    for (int x = Math.max(0, cx - r); x < Math.min(w, cx + r); x++) px[y * w + x] = shade(c, 1, 6, rnd);
            }
            for (int s = 0; s < 3; s++) px[rnd.nextInt(px.length)] = target; // stray pixels of the same shade
            if (present) {
                for (int y = ty - tr; y <= ty + tr; y++)
                    for (int x = tx - tr; x <= tx + tr; x++)
                        if (x >= 0 && y >= 0 && x < w && y < h && inside(x, y)) px[y * w + x] = shade(target, 1, 6, rnd);
            }
        }
        boolean inside(int x, int y) { return square ? Math.abs(x - tx) <= tr && Math.abs(y - ty) <= tr : Math.hypot(x - tx, y - ty) <= tr; }
        public int at(int x, int y) { return x < 0 || y < 0 || x >= w || y >= h ? -1 : px[y * w + x]; }
    }

    static void testFindColourOnThousandsOfScreens() {
        Random rnd = new Random(77);
        int n = 1500, hit = 0, quiet = 0;
        for (int k = 0; k < n; k++) {
            Scene s = new Scene(rnd, true);
            int[] d = ColorZones.findGrid(s.w, s.h);
            int[] cell = ColorZones.find(ColorZones.grid(s, 0, 0, s.w, s.h, d[0], d[1]), d[0], d[1], s.target, 30);
            if (cell != null) {
                int[] p = ColorZones.cellCentre(0, 0, s.w, s.h, d[0], d[1], cell);
                if (s.inside(p[0], p[1])) hit++;
            }
            Scene none = new Scene(rnd, false);
            int[] e = ColorZones.findGrid(none.w, none.h);
            int[] c2 = ColorZones.find(ColorZones.grid(none, 0, 0, none.w, none.h, e[0], e[1]), e[0], e[1], none.target, 30);
            if (c2 == null) quiet++;
        }
        System.out.println(String.format("    gasesteCuloare: %d/%d găsite în formă (%.2f%%); fără culoare: %d/%d nimic găsit (%.2f%%)",
                hit, n, hit * 100.0 / n, quiet, n, quiet * 100.0 / n));
        T.ok(hit >= n * 0.99, "≥ 99% inside the patch: " + hit + "/" + n);
        T.ok(quiet >= n * 0.99, "≥ 99% nothing found when the colour is not there: " + quiet + "/" + n);
    }

    static void testColourZonesFromScripts() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.picture = new ColorZones.Pixels() { // a 400 px bar, 3/4 green; a gold disc at (700, 900)
            public int at(int x, int y) {
                if (y >= 100 && y < 120 && x >= 0 && x < 400) return x < 300 ? 0x43A047 : 0x303030;
                if (Math.hypot(x - 700, y - 900) < 40) return 0xFFD54F;
                return 0x101828;
            }
        };
        ScriptTests.Rec r = ScriptTests.run("print(procentCuloare(rect(0, 100, 400, 120), #43A047));\n"
                + "point c = gasesteCuloare(ecran(), #FFD54F);\nprint(c.x, \",\", c.y);\n"
                + "print(exista(gasesteCuloare(rect(0, 0, 300, 300), #FFD54F)));\n", h);
        T.ok(r.error == null, r.error == null ? "" : r.error.describe());
        T.eq("75", h.out.get(0), "75% full");
        String[] xy = h.out.get(1).split(",");
        T.ok(Math.hypot(Integer.parseInt(xy[0]) - 700, Integer.parseInt(xy[1]) - 900) < 40, "in the disc: " + h.out.get(1));
        T.eq("false", h.out.get(2), "not in that zone");
        ScriptTests.Rec bad = ScriptTests.run("print(procentCuloare(rect(5, 5, 5, 50), #FFFFFF));", new ScriptTests.Host());
        T.ok(bad.error != null && bad.error.getMessage().contains("goală"), "an empty zone is explained");
    }

    // ------------------------------------------------------------------ 💻 a recording as code
    /** A script host that plans each touch exactly like the engine and keeps the time line. */
    static final class PlanHost extends ScriptTests.Host {
        final List<String> line = new ArrayList<String>();
        final GesturePlanner.Limits lim = new GesturePlanner.Limits();
        @Override public boolean gesture(Action a) {
            GesturePlan p = GesturePlanner.plan(a, 1.0, 0, new Random(1), CoordinateScaler.IDENTITY, lim);
            line.add((now - 1000) + ":" + describe(p));
            now += p.totalDuration();
            return true;
        }
        @Override public boolean global(int g) { line.add((now - 1000) + ":global " + g); return true; }
    }

    static String describe(GesturePlan p) {
        StringBuilder b = new StringBuilder();
        for (GesturePlan.PlannedStroke s : p.strokes) {
            b.append('[').append(s.startTime).append('+').append(s.duration);
            for (float[] pt : s.points) b.append(' ').append(Math.round(pt[0])).append(',').append(Math.round(pt[1]));
            b.append(']');
        }
        return b.toString();
    }

    /** Same touches, places and durations; each moment within ms (rounding of pauses at other speeds). */
    static boolean sameWithin(List<String> a, List<String> b, long ms) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            String x = a.get(i), y = b.get(i);
            int cx = x.indexOf(':'), cy = y.indexOf(':');
            if (!x.substring(cx).equals(y.substring(cy))) return false;
            if (Math.abs(Long.parseLong(x.substring(0, cx)) - Long.parseLong(y.substring(0, cy))) > ms) return false;
        }
        return true;
    }

    static Macro randomRecording(Random rnd) {
        Macro m = new Macro("rec");
        m.screenWidth = 1080; m.screenHeight = 2400;
        m.settings.loops = 1;
        int n = 1 + rnd.nextInt(25);
        for (int i = 0; i < n; i++) {
            Action a;
            switch (rnd.nextInt(7)) {
                case 0: case 1: a = Action.tap(rnd.nextInt(1080), rnd.nextInt(2400)); a.duration = 20 + rnd.nextInt(200); break;
                case 2: a = Action.longPress(rnd.nextInt(1080), rnd.nextInt(2400), 400 + rnd.nextInt(2000)); break;
                case 3: a = Action.doubleTap(rnd.nextInt(1080), rnd.nextInt(2400)); break;
                case 4: a = Action.swipe(rnd.nextInt(1080), rnd.nextInt(2400), rnd.nextInt(1080), rnd.nextInt(2400), 80 + rnd.nextInt(900)); break;
                case 5: a = Action.waitFor(rnd.nextInt(3000)); break;
                default: a = Action.global(rnd.nextBoolean() ? GlobalActions.BACK : GlobalActions.HOME); break;
            }
            a.delay = rnd.nextInt(4) == 0 ? 0 : rnd.nextInt(2500);
            if (rnd.nextInt(8) == 0 && a.isGesture()) { a.repeat = 2 + rnd.nextInt(4); a.interval = rnd.nextInt(600); }
            m.actions.add(a);
        }
        return m;
    }

    static void testRecordingAsCodeDoesTheSameTouchesAtTheSameTimes() {
        check(new Random(11), 1000, false, "viteză 1, o rundă");
        check(new Random(12), 1000, true, "viteze 0,5–2, 1–3 runde");
    }

    private static void check(Random rnd, int n, boolean varied, String what) {
        int same = 0;
        String firstDiff = null;
        for (int k = 0; k < n; k++) {
            Macro m = randomRecording(rnd);
            if (varied) {
                m.settings.speed = new double[]{0.5, 1, 1.5, 2}[rnd.nextInt(4)];
                m.settings.loops = 1 + rnd.nextInt(3);
                m.settings.loopDelay = rnd.nextInt(2000);
            }
            FakeHost eh = new FakeHost();
            new PlaybackEngine(eh, null).start(m, null);
            eh.runAll();
            List<String> engine = new ArrayList<String>();
            for (FakeHost.Event e : eh.events) {
                if (e.kind.equals("gesture")) engine.add((e.time - 1000) + ":" + describe(e.plan));
                else if (e.kind.startsWith("global")) engine.add((e.time - 1000) + ":global " + e.global);
            }
            String code = com.freeclick.core.script.CodeFromSteps.code(m);
            PlanHost sh = new PlanHost();
            ScriptTests.Rec r = ScriptTests.run(code, sh, m.actions);
            if (r.error != null) { if (firstDiff == null) firstDiff = "error " + r.error.describe() + "\n" + code; continue; }
            if (sameWithin(engine, sh.line, 2)) same++;
            else if (firstDiff == null) firstDiff = "engine " + engine + "\ncode   " + sh.line + "\n" + code;
        }
        System.out.println(String.format("    înregistrare → cod (%s): %d/%d identice cu motorul (atingeri, locuri, durate; momente la ±2 ms)", what, same, n));
        T.ok(same == n, "every recording as code does exactly what the engine does: " + firstDiff);
    }

    // ------------------------------------------------------------------ 🎥 inregistreaza()
    static void testRecordWhatYouDoAndReplayIt() {
        Random rnd = new Random(5);
        int n = 300, exact = 0;
        String first = null;
        for (int k = 0; k < n; k++) {
            ScriptTests.Host h = new ScriptTests.Host();
            List<String> expect = new ArrayList<String>();
            long t = 1500, prevEnd = -1;
            int count = 1 + rnd.nextInt(8);
            for (int i = 0; i < count; i++) {
                int kind = rnd.nextInt(4), x = 50 + rnd.nextInt(980), y = 50 + rnd.nextInt(2300);
                long delay = prevEnd < 0 ? 0 : t - prevEnd;
                if (kind == 0) { // tap
                    long d = 30 + rnd.nextInt(200);
                    h.fingerScript.add(new long[]{t, TouchTracker.DOWN, 0, x, y});
                    h.fingerScript.add(new long[]{t + d, TouchTracker.UP, 0, x + rnd.nextInt(3), y + rnd.nextInt(3)});
                    expect.add("tap " + x + "," + y + " " + d + " after " + delay);
                    prevEnd = t + d;
                } else if (kind == 1) { // long press
                    long d = 600 + rnd.nextInt(1500);
                    h.fingerScript.add(new long[]{t, TouchTracker.DOWN, 0, x, y});
                    h.fingerScript.add(new long[]{t + d, TouchTracker.UP, 0, x, y});
                    expect.add("apasare_lunga " + x + "," + y + " " + d + " after " + delay);
                    prevEnd = t + d;
                } else { // a curved swipe (1 finger) or two fingers moving
                    int fingers = kind == 2 ? 1 : 2;
                    long d = 150 + rnd.nextInt(900);
                    int steps = 5 + rnd.nextInt(20);
                    StringBuilder e = new StringBuilder("GESTURE " + fingers + " fingers " + d + " after " + delay);
                    if (fingers == 1) e = new StringBuilder("GESTURE 1 finger " + d + " after " + delay);
                    for (int f = 0; f < fingers; f++) {
                        int fx = x + f * 200, fy = y;
                        h.fingerScript.add(new long[]{t, TouchTracker.DOWN, f, fx, fy});
                        for (int s2 = 1; s2 <= steps; s2++) {
                            long ts = t + d * s2 / steps;
                            int px = fx + (int) (300 * Math.sin(s2 / (double) steps * 3)), py = fy - s2 * 40;
                            h.fingerScript.add(new long[]{ts, s2 == steps ? TouchTracker.UP : TouchTracker.MOVE, f, px, py});
                            if (s2 == steps) e.append(" | ").append(px).append(",").append(py);
                        }
                    }
                    expect.add(e.toString());
                    prevEnd = t + d;
                }
                t = prevEnd + 100 + rnd.nextInt(1500);
            }
            java.util.Collections.sort(h.fingerScript, new java.util.Comparator<long[]>() {
                public int compare(long[] a, long[] b) { return a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]); }
            });
            ScriptTests.Rec r = ScriptTests.run("vector<Pas> m = inregistreaza(3000);\nfor (auto p2 : m) print(p2.tip, \" \", p2.x, \",\", p2.y, \" \", p2.durata, \" after \", p2.intarziere);\nreda(m, 2);\n", h);
            if (r.error != null) { if (first == null) first = r.error.describe(); continue; }
            boolean ok = h.out.size() == expect.size();
            for (int i = 0; ok && i < expect.size(); i++) {
                String want = expect.get(i), got = h.out.get(i);
                if (want.startsWith("GESTURE")) {
                    String[] w = want.split(" ");
                    ok = got.startsWith("gest ") && got.contains(" " + w[3] + " after " + want.split(" after ")[1].split(" ")[0]) // duration + pause
                            && h.did.get(i).contains(w[1] + " deget"); // every finger kept
                } else ok = got.equals(want);
            }
            ok = ok && h.did.size() == 2 * expect.size();
            if (ok) exact++; else if (first == null) first = "want " + expect + "\ngot  " + h.out + "\ndid " + h.did;
        }
        System.out.println(String.format("    inregistreaza(): %d/%d înregistrări cu aceleași atingeri, locuri, durate și pauze", exact, n));
        T.ok(exact == n, "recordings match what was done: " + first);
    }

    // ------------------------------------------------------------------ show, tell, the phone
    static void testShowTellAndPhoneState() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.batteryLevel = 15;
        ScriptTests.Rec r = ScriptTests.run("afiseazaPeEcran(\"Runda 1\");\nafiseazaPeEcran(\"aici\", point(10, 20));\n"
                + "if (baterie() < 20 && !seIncarca()) notifica(\"Baterie mică\");\nnotifica(\"Titlu\", \"text\");\n"
                + "print(orientare());\nascundeTextul();\n", h);
        T.ok(r.error == null, r.error == null ? "" : r.error.describe());
        T.eq("[hud Runda 1, hud aici @10,20, notify FreeClick: Baterie mică, notify Titlu: text, hud ]", h.did.toString(), "what the phone was asked");
        T.eq("portret", h.out.get(0), "orientation");
    }
}
