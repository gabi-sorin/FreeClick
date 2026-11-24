package com.freeclick.core;

import com.freeclick.core.script.CodeMarks;
import com.freeclick.core.script.Completions;
import com.freeclick.core.script.ScreenSpace;
import com.freeclick.core.script.ScriptError;
import com.freeclick.core.script.ScriptSimulator;
import com.freeclick.core.script.Signatures;
import com.freeclick.core.script.ScriptExamples;
import com.freeclick.core.script.ScriptHost;
import com.freeclick.core.script.ScriptRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** FreeClick 1.9: the C++-like script language (checked before running, run on a virtual clock). */
public final class ScriptTests {

    /** Virtual-clock host: records what the script does. */
    static class Host implements ScriptHost {
        long now = 1000;
        final List<String> out = new ArrayList<String>();
        final List<String> warn = new ArrayList<String>();
        final List<String> did = new ArrayList<String>();
        final Map<String, Integer> pixels = new HashMap<String, Integer>();
        int pixelDefault = 0x000000;
        String text = "";
        /** image name → visible from..to (virtual ms) at x,y */
        final Map<String, long[]> images = new HashMap<String, long[]>();
        final Map<String, List<Action>> saved = new HashMap<String, List<Action>>();
        String kept;
        int cancelNext;
        ScriptRunner runner;
        int stopAfterGestures = -1;
        long sleptTotal;

        public long now() { return now; }
        public long wallTime() { return 1795000000000L + now; }
        public void sleep(long ms) { now += ms; sleptTotal += ms; feedTouches(); }
        // 2.3: fingers on a virtual clock: {time, action, id, x, y}
        final TouchTracker touches = new TouchTracker();
        final List<long[]> fingerScript = new ArrayList<long[]>();
        final List<String> listening = new ArrayList<String>();
        void feedTouches() {
            while (!fingerScript.isEmpty() && fingerScript.get(0)[0] <= now) {
                long[] e = fingerScript.remove(0);
                touches.feed((int) e[1], (int) e[2], e[3], e[4], e[0]);
            }
        }
        /** A finger: down at t at (x, y), moving to (x2, y2), up after ms. */
        void finger(long t, int id, int x, int y, int x2, int y2, long ms) {
            fingerScript.add(new long[]{t, TouchTracker.DOWN, id, x, y});
            fingerScript.add(new long[]{t + ms / 2, TouchTracker.MOVE, id, (x + x2) / 2, (y + y2) / 2});
            fingerScript.add(new long[]{t + ms, TouchTracker.UP, id, x2, y2});
            java.util.Collections.sort(fingerScript, new java.util.Comparator<long[]>() {
                public int compare(long[] a, long[] b) { return Long.compare(a[0], b[0]); }
            });
        }
        public TouchTracker touches() { return touches; }
        public void listen(boolean on, boolean forward) { listening.add(on + "/" + forward); }
        public int[] toScript(int x, int y) { return new int[]{x, y}; }
        // 2.5: buttons; presses come at {time, id}
        final List<long[]> pressScript = new ArrayList<long[]>();
        final List<String> buttons = new ArrayList<String>();
        private final List<Integer> pressed = new ArrayList<Integer>();
        private void feedPresses() {
            while (!pressScript.isEmpty() && pressScript.get(0)[0] <= now) pressed.add((int) pressScript.remove(0)[1]);
        }
        public int addButton(String text, int x1, int y1, int x2, int y2) { buttons.add(text + " " + x1 + "," + y1 + "," + x2 + "," + y2); return buttons.size(); }
        public void removeButton(int id) { did.add("remove button " + id); }
        public boolean takePress(int id) { feedPresses(); return pressed.remove(Integer.valueOf(id)); }
        public int takeAnyPress() { feedPresses(); return pressed.isEmpty() ? -1 : pressed.remove(0); }
        public void listenZone(int x1, int y1, int x2, int y2) {
            touches.setZone(x1 < 0 ? null : new int[]{x1, y1, x2, y2});
            did.add("zone " + x1 + "," + y1 + "," + x2 + "," + y2);
        }
        final java.util.Map<String, String> files = new java.util.HashMap<String, String>();
        public String readFile(String name) { return files.get(name); }
        public boolean writeFile(String name, String text, boolean append) {
            files.put(name, append && files.containsKey(name) ? files.get(name) + text : text);
            return true;
        }
        /** Answers to intreaba/alege, in order (null = Anulează). */
        final List<String> answers = new ArrayList<String>();
        public String ask(String q, String preset, boolean number) { did.add("ask " + q); now += 2000; return answers.isEmpty() ? null : answers.remove(0); }
        public int choose(String title, List<String> options) {
            did.add("choose " + title + " " + options); now += 2000;
            if (answers.isEmpty()) return -1;
            String a = answers.remove(0);
            return a == null ? -1 : options.indexOf(a);
        }
        int batteryLevel = 80;
        boolean plugged;
        String lastHud = "";
        public void hud(String text, int x, int y) { lastHud = text; did.add("hud " + text + (x >= 0 ? " @" + x + "," + y : "")); }
        public int battery() { return batteryLevel; }
        public boolean charging() { return plugged; }
        public boolean landscape() { return false; }
        public void notifyUser(String title, String text) { did.add("notify " + title + ": " + text); }
        /** 2.4: a picture for zone reads (null = use pixels / pixelDefault). */
        ColorZones.Pixels picture;
        public int[] grid(int x1, int y1, int x2, int y2, int cols, int rows) {
            now += 40;
            ColorZones.Pixels p = picture != null ? picture : new ColorZones.Pixels() {
                public int at(int x, int y) { Integer c = pixels.get(x + "," + y); return c != null ? c : pixelDefault; }
            };
            return ColorZones.grid(p, x1, y1, x2, y2, cols, rows);
        }
        public boolean gesture(Action a) {
            String d = a.type + " " + Math.round(a.x) + "," + Math.round(a.y)
                    + (a.type == Action.Type.SWIPE ? "→" + Math.round(a.x2) + "," + Math.round(a.y2) : "") + " " + a.duration + "ms";
            if (cancelNext > 0) { cancelNext--; did.add("cancelled " + d); return false; }
            did.add(d);
            now += Math.max(1, a.ownDuration()) + 20;
            if (stopAfterGestures >= 0 && did.size() >= stopAfterGestures) runner.stop();
            return true;
        }
        public boolean global(int g) { did.add("global " + g); return true; }
        public boolean openApp(String p) { did.add("open " + p); return true; }
        public int pixel(int x, int y) { now += 30; Integer c = pixels.get(x + "," + y); return c != null ? c : pixelDefault; }
        int zoneChanges; long zoneChangeAt = -1;
        public long zoneHash(int x1, int y1, int x2, int y2) { now += 40; return zoneChangeAt >= 0 && now >= zoneChangeAt ? 2 : 1; }
        public boolean saveScreenshot(String name) { did.add("shot " + name); return true; }
        public void copyText(String t) { did.add("copy " + t); }
        public boolean openUrl(String u) { did.add("url " + u); return true; }
        public String readText(int x1, int y1, int x2, int y2, boolean number) { now += 50; did.add("read " + x1 + "," + y1 + "," + x2 + "," + y2); return text; }
        public List<int[]> findImage(ImageSpec spec, boolean all) {
            now += 80;
            List<int[]> l = new ArrayList<int[]>();
            long[] v = images.get(spec.name);
            if (v != null && now >= v[0] && now < v[1]) l.add(new int[]{(int) v[2], (int) v[3]});
            return l;
        }
        public void print(String t, int kind) { (kind == PlaybackEngine.PRINT_WARN ? warn : out).add(t); }
        public void toast(String t) { did.add("toast " + t); }
        public void vibrate(long ms) { did.add("vibrate"); }
        public int screenWidth() { return 1080; }
        public int screenHeight() { return 2400; }
        public void runSteps(List<Action> steps, int times) {
            for (int k = 0; k < times; k++) for (Action a : steps) { did.add("step " + ActionFormatter.describe(a, null)); now += 100; }
        }
        public List<Action> stepsOf(String name) { return saved.get(name); }
        public String loadKept() { return kept; }
        public void saveKept(String json) { kept = json; }
    }

    static final class Rec implements ScriptRunner.Listener {
        String reason; ScriptError error;
        final List<Integer> pausedAt = new ArrayList<Integer>();
        final List<String> why = new ArrayList<String>();
        ScriptRunner runner;
        boolean resumeOnPause = true, stepOnPause;
        public void onState(int state, int line, String w) {
            if (state == ScriptRunner.PAUSED) {
                pausedAt.add(line);
                why.add(w);
                if (stepOnPause) runner.step(); else if (resumeOnPause) runner.resume();
            }
        }
        public void onFinished(String r, ScriptError e) { reason = r; error = e; }
    }

    static Rec run(String src, Host h) { return run(src, h, new ArrayList<Action>()); }

    static Rec run(String src, Host h, List<Action> steps) {
        Rec r = new Rec();
        ScriptRunner sr = new ScriptRunner(src, steps, h, r);
        sr.setRandom(new java.util.Random(1));
        r.runner = sr;
        h.runner = sr;
        sr.runHere();
        if (r.error != null && !ScriptRunner.ERROR.equals(r.reason)) throw new AssertionError("error without reason");
        return r;
    }

    static String outOf(String src) {
        Host h = new Host();
        Rec r = run(src, h);
        if (r.error != null) throw new AssertionError("script failed: " + r.error.describe());
        StringBuilder b = new StringBuilder();
        for (String s : h.out) { if (b.length() > 0) b.append('|'); b.append(s); }
        return b.toString();
    }

    static ScriptError errorOf(String src) {
        ScriptError e = ScriptRunner.check(src);
        if (e == null) throw new AssertionError("expected a mistake in: " + src);
        return e;
    }

    // ------------------------------------------------------------------ language
    static void testArithmeticLikeCpp() {
        T.eq("7|3|2.5|1|-1|true|false|ab12|6", outOf(
                "print(1 + 2 * 3); print(7 / 2); print(5 / 2.0); print(7 % 3); print(-7 / 7);\n"
                        + "print(3 > 2 && 2 >= 2); print(!(1 == 1)); print(\"ab\" + 1 + 2); int x = 5; x += 1.9; print(x);"));
        T.eq("10|11|11|12|0|1", outOf("int i = 10; print(i++); print(i); print(++i - 1); print(i); bool b = 0; print(b ? 1 : 0); print(true ? 1 : 0);"));
        T.eq("3.3333|2|4", outOf("double d = 10 / 3.0; print(d); int k = 2.9; print(k); print(int(4.7));"));
    }

    static void testControlFlow() {
        T.eq("0|1|2|3|4|sum=10|even 2|even 4|done 3", outOf(
                "int s = 0; for (int i = 0; i < 5; i++) { print(i); s += i; } print(\"sum=\" + s);\n"
                        + "int n = 0; while (true) { n++; if (n > 5) break; if (n % 2 == 1) continue; print(\"even \" + n); }\n"
                        + "int k = 0; do { k++; } while (k < 3); print(\"done \", k);"));
    }

    static void testFunctionsRecursionReferences() {
        T.eq("120|55|swapped 2 1|7", outOf(
                "int fact(int n) { if (n <= 1) return 1; return n * fact(n - 1); }\n"
                        + "int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }\n"
                        + "void sw(int& a, int& b) { int t = a; a = b; b = t; }\n"
                        + "void noRef(int a) { a = 99; }\n"
                        + "int main() { print(fact(5)); print(fib(10)); int x = 1, y = 2; sw(x, y); print(\"swapped \", x, \" \", y);\n"
                        + "  int z = 7; noRef(z); print(z); return 0; }"));
    }

    static void testVectorsPairsStructs() {
        T.eq("[3, 1, 2]|[1, 2, 3]|3|2|[10, 20, 30]|copy [1, 2]|orig [1, 2, 9]|(a, 5)|a5|{(1, 2), x, 3}|4 6", outOf(
                "vector<int> v = {3, 1, 2}; print(v); sort(v); print(v); print(v.size()); print(v[1]);\n"
                        + "vector<int> w; for (int i = 1; i <= 3; i++) w.push_back(i * 10); print(w);\n"
                        + "vector<int> a = {1, 2}; vector<int> c = a; a.push_back(9); print(\"copy \", c); print(\"orig \", a);\n"
                        + "pair<string, int> p = make_pair(\"a\", 5); print(p); print(p.first + p.second);\n"
                        + "struct S { point p; string n; int k; };\n"
                        + "S s = S{point(1, 2), \"x\", 3}; print(s);\n"
                        + "vector<S> all; all.push_back(s); all[0].p.x += 3; all[0].p = all[0].p + point(0, 4); print(all[0].p.x, \" \", all[0].p.y);"));
        T.eq("6|[2, 4, 6]", outOf("vector<int> v = {1, 2, 3}; int t = 0; for (int x : v) t += x; print(t); for (auto& x : v) x *= 2; print(v);"));
        T.eq("3|[[1, 2], [3]]|0", outOf("vector<vector<int>> g = {{1, 2}, {3}}; print(g[1][0]); print(g); vector<int> e(3); print(e[2]);"));
    }

    static void testStringsAndColours() {
        T.eq("5|ell|1|-1|true|HELLO|#34A853|52 168 83|true|false|#FF0000", outOf(
                "string s = \"hello\"; print(s.size()); print(s.substr(1, 3)); print(s.find(\"e\")); print(s.find(\"z\"));\n"
                        + "print(s.contine(\"LL\")); print(s.upper());\n"
                        + "color c = #34A853; print(c); print(c.r, \" \", c.g, \" \", c.b); print(aproape(c, #30A050, 10)); print(c == #000000);\n"
                        + "print(color(255, 0, 0).hex());"));
        T.eq("ab|3|1250|12", outOf("string t = \"a\"; t += \"b\"; print(t); print(to_string(3)); print(to_int(\"Gold 1,250\")); print(stoi(\"12\"));"));
    }

    static void testCoutAndPrint() {
        T.eq("a = 5 b = 2.5 true|second line", outOf("int a = 5; double b = 2.5; cout << \"a = \" << a << \" b = \" << b << \" \" << true << endl; cout << \"second\"; cout << \" line\";"));
    }

    // ------------------------------------------------------------------ mistakes, in Romanian, with the line
    static void testMistakesAreFoundBeforeRunning() {
        ScriptError e = errorOf("int x = 1;\nint y = \"doi\";\n");
        T.eq(2, e.line, "line of the type mistake");
        T.ok(e.getMessage().contains("int") && e.getMessage().contains("string"), e.getMessage());
        e = errorOf("int aur = 5;\nprint(aru);");
        T.ok(e.line == 2 && e.getMessage().contains("aur"), "suggests the right name: " + e.getMessage());
        e = errorOf("int x = 1\nprint(x);");
        T.eq(1, e.line, "a missing ; is reported at the end of its line");
        e = errorOf("tap(\"aici\");");
        T.ok(e.getMessage().contains("tap(point") && e.getMessage().contains("tap(int, int"), "shows how to call it: " + e.getMessage());
        e = errorOf("break;");
        T.ok(e.getMessage().contains("buclă"), e.getMessage());
        e = errorOf("void f() { return 3; }");
        T.ok(e.getMessage().contains("void"), e.getMessage());
        e = errorOf("const int k = 1; k = 2;");
        T.ok(e.getMessage().contains("const"), e.getMessage());
        e = errorOf("int f(int a) { return a; }\nint g() { return f(1, 2); }");
        T.eq(2, e.line, "wrong number of arguments");
        e = errorOf("vector<int> v; v.push_back(\"x\");");
        T.ok(e.getMessage().contains("int"), e.getMessage());
        e = errorOf("struct A { int x; };\nA a; a.y = 1;");
        T.ok(e.getMessage().contains("x"), "lists the fields: " + e.getMessage());
        e = errorOf("/* never closed");
        T.ok(e.getMessage().contains("*/"), e.getMessage());
        e = errorOf("int tap = 1;\nint tap = 2;");
        T.ok(e.line == 2, "declared twice");
        e = errorOf("void tap(int x) { }");
        T.ok(e.getMessage().contains("aplicației"), "cannot redefine the app's functions");
        T.eq(null, ScriptRunner.check(""), "an empty script is fine");
    }

    static void testRuntimeMistakesStopWithTheLine() {
        Host h = new Host();
        Rec r = run("vector<int> v = {1, 2, 3};\nprint(v[0]);\nprint(v[3]);", h);
        T.eq(ScriptRunner.ERROR, r.reason, "error");
        T.eq(3, r.error.line, "line of the bad index");
        T.ok(r.error.getMessage().contains("3 elemente"), r.error.getMessage());
        T.eq("[1]", h.out.toString(), "ran until then");
        r = run("int a = 0;\nint b = 5 / a;", new Host());
        T.ok(r.error != null && r.error.line == 2 && r.error.getMessage().contains("0"), "division by 0");
        r = run("int f(int x) { return f(x + 1); }\nf(1);", new Host());
        T.ok(r.error != null && r.error.getMessage().contains("apeluri"), "endless recursion: " + (r.error == null ? null : r.error.getMessage()));
        r = run("int f(int x) { if (x > 0) return 1; }\nprint(f(-1));", new Host());
        T.ok(r.error != null && r.error.getMessage().contains("return"), "no return");
    }

    // ------------------------------------------------------------------ the phone
    static void testTouchesAndWaits() {
        Host h = new Host();
        Rec r = run("tap(540, 1200);\nwait(1s);\nswipe(point(100, 2000), point(100, 500), 250);\napasaLung(point(5, 6));\n"
                + "dubluTap(7, 8);\nback();\ntrage(point(1, 1), point(500, 500));\nwaitRandom(100, 200);", h);
        T.eq(ScriptRunner.DONE, r.reason, "done");
        T.eq("TAP 540,1200 50ms", h.did.get(0), "tap");
        T.eq("SWIPE 100,2000→100,500 250ms", h.did.get(1), "swipe");
        T.eq("LONG_PRESS 5,6 800ms", h.did.get(2), "long press");
        T.eq("DOUBLE_TAP 7,8 50ms", h.did.get(3), "double tap");
        T.eq("global " + GlobalActions.BACK, h.did.get(4), "back");
        T.ok(h.did.get(5).startsWith("GESTURE 1,1"), "drag: " + h.did.get(5));
        T.ok(h.sleptTotal >= 1100 && h.sleptTotal <= 1200, "waits: " + h.sleptTotal);
    }

    static void testScreenColoursReadingImages() {
        Host h = new Host();
        h.pixels.put("10,10", 0x34A853);
        h.text = "Gold 1,250";
        List<Action> steps = new ArrayList<Action>();
        ImageSpec sp = new ImageSpec();
        sp.id = "0123456789abcdef"; sp.name = "Collect"; sp.w = 100; sp.h = 40;
        Action img = Action.image(sp);
        img.enabled = false;
        steps.add(img);
        h.images.put("Collect", new long[]{0, 1000000, 300, 400});
        Rec r = run("if (aproape(pixel(10, 10), #34A853, 20)) print(\"verde\");\n"
                + "int aur = citeste(rect(0, 0, 100, 50)); print(aur);\n"
                + "string t = citesteText(rect(0, 0, 100, 50)); print(t);\n"
                + "if (apare(\"Collect\")) tap(gaseste(\"Collect\"));\n"
                + "point p = gaseste(\"collect\"); print(exista(p));\n"
                + "print(asteaptaImagine(\"Collect\", 1000));", h, steps);
        T.eq(ScriptRunner.DONE, r.reason, "done " + (r.error == null ? "" : r.error.describe()));
        T.eq("[verde, 1250, Gold 1,250, true, true]", h.out.toString(), "results");
        T.ok(h.did.contains("TAP 300,400 50ms"), "tapped the image: " + h.did);
        r = run("print(apare(\"Nu exista\"));", new Host(), steps);
        T.ok(r.error != null && r.error.getMessage().contains("Nu exista"), "unknown image name");
        h = new Host();
        h.text = "abc";
        run("print(citeste(rect(0, 0, 10, 10)));", h);
        T.eq("[-1]", h.out.toString(), "no number: -1");
        T.eq(1, h.warn.size(), "and a warning");
    }

    static void testStepsListFromCode() {
        Host h = new Host();
        List<Action> steps = new ArrayList<Action>();
        steps.add(Action.tap(100, 200));
        steps.add(Action.waitFor(500));
        h.saved.put("Farm", new ArrayList<Action>(java.util.Arrays.asList(Action.tap(9, 9))));
        Rec r = run("print(pasi.size(), \" \", pasi[0].tip, \" \", pasi[0].x);\n"
                + "pasi[0].x = 300;\n"
                + "pasi.push_back(pasTap(point(1, 2)));\n"
                + "ruleaza(pasi);\n"
                + "pasi[1].activ = false;\n"
                + "ruleaza(pasi[0], 2);\n"
                + "vector<Pas> f = pasiDin(\"Farm\"); print(f.size());\n"
                + "ruleazaMacro(\"Farm\");", h, steps);
        T.eq(ScriptRunner.DONE, r.reason, "done " + (r.error == null ? "" : r.error.describe()));
        T.eq("[2 tap 100, 1]", h.out.toString(), "read the list");
        T.eq("step Atingere (300, 200)", h.did.get(0), "changed x is used");
        T.eq("step Pauză 500 ms", h.did.get(1), "wait step");
        T.eq("step Atingere (1, 2)", h.did.get(2), "step added from code");
        T.eq("step Atingere (300, 200)", h.did.get(3), "one step, twice");
        T.eq("step Atingere (300, 200)", h.did.get(4), "…the second time");
        T.eq("step Atingere (9, 9)", h.did.get(5), "another saved item");
        T.eq(100f, steps.get(0).x, "the original list is not changed by the script");
    }

    static void testEvents() {
        Host h = new Host();
        Rec r = run("int n = 0;\nla_fiecare(1s) { n++; }\nwait(5s);\nprint(n);", h);
        T.eq(ScriptRunner.DONE, r.reason, "done");
        T.eq("[5]", h.out.toString(), "every second during a 5 s wait");
        h = new Host();
        List<Action> steps = new ArrayList<Action>();
        ImageSpec sp = new ImageSpec();
        sp.id = "0123456789abcdef"; sp.name = "Ad"; sp.w = 50; sp.h = 50;
        Action ad = Action.image(sp);
        ad.enabled = false;
        steps.add(ad);
        h.images.put("Ad", new long[]{3000, 3600, 900, 100});
        r = run("cand (apare(\"Ad\")) { tap(gaseste(\"Ad\")); print(\"închis\"); }\nfor (int i = 0; i < 6; i++) { tap(1, 1); wait(1s); }", h, steps);
        T.eq(ScriptRunner.DONE, r.reason, "done " + (r.error == null ? "" : r.error.describe()));
        T.ok(h.did.contains("TAP 900,100 50ms") && h.out.contains("închis"), "the pop-up was closed while waiting: " + h.did);
        // a script made only of events keeps running until stopped
        h = new Host();
        h.stopAfterGestures = 3;
        r = run("la_fiecare(2s) { tap(5, 5); }", h);
        T.eq(ScriptRunner.STOPPED, r.reason, "served events until Stop");
        T.eq(3, h.did.size(), "three taps");
    }

    static void testStopPauseStepBreakpoints() {
        Host h = new Host();
        h.stopAfterGestures = 4;
        Rec r = run("while (true) { tap(1, 1); wait(100); }", h);
        T.eq(ScriptRunner.STOPPED, r.reason, "Stop ends an endless loop");
        T.eq(4, h.did.size(), "nothing after Stop");
        r = run("tap(1, 1);\nstop();\ntap(2, 2);", h = new Host());
        T.eq(ScriptRunner.SCRIPT_STOP, r.reason, "stop() in the script");
        T.eq(1, h.did.size(), "nothing after stop()");
        // step by step: pauses before every statement, in order
        h = new Host();
        Rec rec = new Rec();
        rec.stepOnPause = true;
        ScriptRunner sr = new ScriptRunner("int a = 1;\nif (a == 1) {\n  a = 2;\n}\nprint(a);", new ArrayList<Action>(), h, rec);
        rec.runner = sr; h.runner = sr;
        sr.setStepMode(true);
        sr.runHere();
        T.eq("[1, 2, 3, 5]", rec.pausedAt.toString(), "lines visited step by step");
        // breakpoints: pause on that line every time it runs
        h = new Host();
        rec = new Rec();
        sr = new ScriptRunner("int s = 0;\nfor (int i = 0; i < 3; i++) {\n  s += i;\n}\nprint(s);", new ArrayList<Action>(), h, rec);
        rec.runner = sr; h.runner = sr;
        java.util.Set<Integer> bp = new java.util.HashSet<Integer>();
        bp.add(3);
        sr.setBreakpoints(bp);
        sr.runHere();
        T.eq("[3, 3, 3]", rec.pausedAt.toString(), "breakpoint hit on every turn");
        T.eq("[3]", h.out.toString(), "result unchanged");
        T.eq("{s=3}", sr.variables().toString(), "variables for the debugger");
    }

    static void testTouchingTheScreenPausesAndRepeats() {
        Host h = new Host();
        h.cancelNext = 1;
        Rec r = run("tap(1, 1); tap(2, 2);", h);
        T.eq(ScriptRunner.DONE, r.reason, "done");
        T.eq("[cancelled TAP 1,1 50ms, TAP 1,1 50ms, TAP 2,2 50ms]", h.did.toString(), "repeated after the pause");
        T.eq("[touch]", r.why.toString(), "paused because of the touch");
    }

    static void testBusyLoopIsSlowedNotFrozen() {
        Host h = new Host();
        Rec r = run("int i = 0; while (true) { i++; if (i > 450000) break; } print(i);", h);
        T.eq(ScriptRunner.DONE, r.reason, "done");
        T.eq("[450001]", h.out.toString(), "result");
        T.eq(1, h.warn.size(), "one warning about the loop without wait");
        T.ok(h.sleptTotal >= 5, "and it was slowed down");
    }

    static void testKeptBetweenRuns() {
        Host h = new Host();
        String src = "pastreaza int total = 0;\npastreaza string ultima = \"\";\ntotal += 5;\nprint(total, \" \", ultima);\nultima = ora();";
        run(src, h);
        run(src, h);
        T.eq(2, h.out.size(), "two runs");
        T.eq("5 ", h.out.get(0), "first run");
        T.ok(h.out.get(1).startsWith("10 "), "second run remembers: " + h.out.get(1));
        T.ok(ScriptRunner.check("void f() { pastreaza int x = 1; }") != null, "only at the top");
        T.ok(ScriptRunner.check("pastreaza vector<int> v;") != null, "only simple types");
    }

    static void testExamplesAreValid() {
        for (int i = 0; i < ScriptExamples.CODE.length; i++) {
            ScriptError e = ScriptRunner.check(ScriptExamples.CODE[i]);
            T.eq(null, e == null ? null : e.describe(), "example " + ScriptExamples.NAMES[i]);
        }
        T.eq(null, ScriptRunner.check(ScriptExamples.NEW_SCRIPT), "new script");
        T.eq(ScriptExamples.NAMES.length, ScriptExamples.CODE.length, "names");
        // the struct example runs
        Host h = new Host();
        Rec r = run(ScriptExamples.CODE[5], h);
        T.eq(ScriptRunner.DONE, r.reason, "struct example runs " + (r.error == null ? "" : r.error.describe()));
        T.eq("[stânga a fost apăsată de 3 ori]", h.out.toString(), "struct example output");
        T.eq(6, h.did.size(), "six taps");
    }

    static void testGarbageNeverCrashes() {
        java.util.Random rnd = new java.util.Random(FuzzTests.SEED + 9);
        String base = ScriptExamples.CODE[5] + ScriptExamples.CODE[2];
        for (int i = 0; i < 400; i++) {
            StringBuilder b = new StringBuilder(base);
            for (int k = 0; k < 1 + rnd.nextInt(6); k++) {
                int pos = rnd.nextInt(b.length());
                switch (rnd.nextInt(3)) {
                    case 0: b.deleteCharAt(pos); break;
                    case 1: b.insert(pos, "{}()[];,<>=+-*/&|!\"'#.:0aZ \n".charAt(rnd.nextInt(27))); break;
                    default: b.setCharAt(pos, (char) (32 + rnd.nextInt(95)));
                }
            }
            try {
                ScriptRunner.check(b.toString());
            } catch (RuntimeException e) {
                throw new AssertionError("check crashed (case " + i + "): " + e + "\n" + b);
            }
        }
        StringBuilder junk = new StringBuilder();
        for (int i = 0; i < 2000; i++) junk.append((char) (32 + rnd.nextInt(95)));
        ScriptRunner.check(junk.toString());
    }

    static void testScreenUseIsDetected() {
        T.ok(!ScriptRunner.usesScreen("tap(1, 2); wait(1s); // pixel(p) only in a comment\nprint(\"gaseste\");"), "no screen");
        T.ok(ScriptRunner.usesScreen("if (aproape(pixel(point(1, 2)), #FF0000)) tap(1, 2);"), "pixel");
        T.ok(ScriptRunner.usesScreen("int a = citeste(rect(0, 0, 10, 10));"), "read");
        T.ok(ScriptRunner.usesScreen("cand (apare(\"x\")) { }"), "image");
        T.ok(ScriptRunner.usesScreen("\"unterminated"), "unknown: sample to be safe");
    }

    // ------------------------------------------------------------------ 2.0: marks, screen space, simulation
    static void testCodeMarksFindPlacesInCode() {
        String src = "point butonul = point(540, 1210);   // point(1, 2) in a comment\n"
                + "rect zonaAur = rect(10, 12, 95, -58);\n"
                + "tap(100, 200);\n"
                + "swipe(point(1, 2), point(3, 4), 300);\n"
                + "if (aproape(pixel(point(7, 8)), #34A853, 30)) tap(butonul);\n"
                + "color verde = #00FF00;\n"
                + "cand (apare(\"Reclama X\")) { back(); }\n"
                + "print(\"tap(5, 6)\");\n"
                + "swipe(10, 20, 30, 40, 200);\n"
                + "tap(point(1.5, 2));\n";
        java.util.List<CodeMarks.Mark> m = CodeMarks.scan(src);
        T.eq(8, m.size(), "marks: " + m.size());
        T.eq(CodeMarks.TOUCH, m.get(0).kind, "point");
        T.eq("butonul", m.get(0).label, "named");
        T.ok(m.get(0).values[0] == 540 && m.get(0).values[1] == 1210, "values");
        T.eq(1, m.get(0).line, "line");
        T.eq(CodeMarks.ZONE, m.get(1).kind, "rect");
        T.eq(-58, m.get(1).values[3], "minus");
        T.eq(CodeMarks.TOUCH, m.get(2).kind, "tap(x, y)");
        T.eq("linia 3", m.get(2).label, "unnamed");
        T.eq(CodeMarks.SWIPE, m.get(3).kind, "swipe of points");
        T.ok(m.get(3).values[2] == 3 && m.get(3).values[3] == 4, "swipe end");
        CodeMarks.Mark c = m.get(4);
        T.eq(CodeMarks.COLOR, c.kind, "pixel colour");
        T.ok(c.values[0] == 7 && c.values[1] == 8 && c.values[2] == 0x34A853 && c.tolerance == 30, "colour place, value, tolerance");
        T.eq(CodeMarks.COLOR, m.get(5).kind, "a colour alone");
        T.ok(m.get(5).values[0] < 0 && !m.get(5).hasPlace() && "verde".equals(m.get(5).label), "no place");
        T.eq(CodeMarks.IMAGE, m.get(6).kind, "image");
        T.eq("Reclama X", m.get(6).image, "image name");
        T.eq(CodeMarks.SWIPE, m.get(7).kind, "swipe(x1, y1, x2, y2)");
        T.eq(9, m.get(7).line, "swipe line");
        T.eq(CodeMarks.ZONE, CodeMarks.onLine(m, 2).kind, "on line");
        T.ok(CodeMarks.onLine(m, 10) == null, "1.5 is no pixel");
        // garbage never breaks it
        java.util.Random rnd = new java.util.Random(7);
        for (int k = 0; k < 300; k++) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 200; i++) b.append("point(,)-12 #\"/*rect34swipe".charAt(rnd.nextInt(27)));
            CodeMarks.scan(b.toString());
        }
    }

    static void testDraggingAMarkRewritesOnlyItsNumbers() {
        String src = "point p = point(540, 1210);\nswipe(point(1, 2), point(3, 4));\nrect z = rect(1, 2, 3, 4);\n"
                + "if (aproape(pixel(point(7, 8)), #34A853)) tap(p);\n";
        java.util.List<CodeMarks.Mark> m = CodeMarks.scan(src);
        String a = CodeMarks.replace(src, m.get(0), new int[]{-5, 77});
        T.ok(a.startsWith("point p = point(-5, 77);\n"), a);
        java.util.List<CodeMarks.Mark> m2 = CodeMarks.scan(a);
        T.ok(m2.get(0).values[0] == -5 && m2.get(0).values[1] == 77, "scanned again");
        String b = CodeMarks.replace(a, m2.get(1), new int[]{10, 20, 1000, 2000});
        T.ok(b.contains("swipe(point(10, 20), point(1000, 2000));"), b);
        java.util.List<CodeMarks.Mark> m3 = CodeMarks.scan(b);
        String c = CodeMarks.replaceColor(b, m3.get(3), 0xABCDEF);
        T.ok(c.contains("#ABCDEF") && !c.contains("#34A853"), c);
        T.eq(4, CodeMarks.scan(c).size(), "same marks");
    }

    static void testScreenSpaceFollowsOrientation() {
        int[] d = ScreenSpace.designFor(1080, 2400, 2400, 1080);
        T.ok(d[0] == 2400 && d[1] == 1080, "landscape picture of the same phone: 1:1");
        d = ScreenSpace.designFor(1080, 2400, 720, 1600);
        T.ok(d[0] == 1080 && d[1] == 2400, "portrait");
        T.ok(ScreenSpace.sameShape(1080, 2400, 720, 1600), "same shape");
        T.ok(!ScreenSpace.sameShape(1080, 2400, 1080, 1920), "16:9 vs 20:9");
        d = ScreenSpace.designFor(0, 0, 500, 900);
        T.ok(d[0] == 500 && d[1] == 900, "no design: the picture");
    }

    static void testSimulationOnAStillScreen() {
        final int green = 0x34A853;
        ScriptSimulator.Screen screen = new ScriptSimulator.Screen() {
            public int width() { return 1080; }
            public int height() { return 2400; }
            public int pixel(int x, int y) { return x == 7 && y == 8 ? green : 0; }
            public String read(int x1, int y1, int x2, int y2, boolean number) { return "1250"; }
            public java.util.List<int[]> find(com.freeclick.core.ImageSpec s, boolean all) { return new java.util.ArrayList<int[]>(); }
        };
        String src = "int aur = citeste(rect(0, 0, 100, 50));\n"
                + "print(\"aur \", aur);\n"
                + "if (aproape(pixel(point(7, 8)), #34A853)) tap(1, 2);\n"
                + "for (int i = 0; i < 3; i++) {\n"
                + "    tap(point(100 + i, 200));\n"
                + "    wait(1s);\n"
                + "}\n"
                + "swipe(point(1, 2), point(3, 4), 300);\n"
                + "ruleaza(pasi);\n";
        java.util.List<Action> steps = new java.util.ArrayList<Action>();
        steps.add(Action.tap(50, 60));
        ScriptSimulator.Result r = ScriptSimulator.run(src, steps, screen, 100, 60000, 3000);
        T.eq(ScriptSimulator.END_DONE, r.end, r.describeEnd());
        T.eq(6, r.touches.size(), "touches");
        T.ok(r.touches.get(0).x == 1 && r.touches.get(0).line == 3, "green → tap on line 3");
        T.ok(r.touches.get(3).x == 102 && r.touches.get(3).line == 5, "loop");
        T.ok(r.touches.get(3).time >= 2000, "virtual time goes on: " + r.touches.get(3).time);
        T.ok(r.touches.get(4).isSwipe() && r.touches.get(4).x2 == 3, "swipe");
        T.ok(r.touches.get(5).fromSteps && r.touches.get(5).x == 50, "list step");
        T.eq("aur 1250", r.prints.get(0).text, "print");
        // limits: endless tapping, endless waiting, endless computing
        r = ScriptSimulator.run("while (true) { tap(1, 1); wait(100); }", steps, screen, 20, 600000, 3000);
        T.eq(ScriptSimulator.END_TOUCHES, r.end, "touch limit");
        T.eq(20, r.touches.size(), "20");
        r = ScriptSimulator.run("while (true) { wait(1min); }", steps, screen, 20, 120000, 3000);
        T.eq(ScriptSimulator.END_TIME, r.end, "time limit");
        long t0 = System.currentTimeMillis();
        r = ScriptSimulator.run("int n = 0; while (true) { n++; }", steps, screen, 20, 120000, 300);
        T.eq(ScriptSimulator.END_REAL, r.end, "busy loop");
        T.ok(System.currentTimeMillis() - t0 < 5000, "stopped in time");
        r = ScriptSimulator.run("tap(1, 2);\nint x = 1 / 0;", steps, screen, 20, 120000, 3000);
        T.eq(ScriptSimulator.END_ERROR, r.end, "error");
        T.eq(2, r.error.line, "error line");
        r = ScriptSimulator.run("tpa(1, 2);", steps, screen, 20, 120000, 3000);
        T.eq(ScriptSimulator.END_ERROR, r.end, "mistake in the code");
    }

    // ------------------------------------------------------------------ 2.0.1: touch settings, parameter hints
    static String hintAt(String src) {
        int cur = src.indexOf('|');
        Signatures.Hint h = Signatures.at(src.replace("|", ""), cur);
        if (h == null) return null;
        return h.paramStart < 0 ? h.text + " []" : h.text + " [" + h.text.substring(h.paramStart, h.paramEnd) + "]";
    }

    static void testParameterHints() {
        T.ok(hintAt("tap(|").contains("[point p]"), hintAt("tap(|"));
        T.ok(hintAt("tap(p, |").contains("[int durata = DURATA_CLICK]"), hintAt("tap(p, |"));
        T.ok(hintAt("tap(p, 50, |").contains("[int pauza = INTERVAL_REPETARE]"), "pause");
        T.ok(hintAt("tap(1, 2, 3, |").contains("tap(int x, int y") && hintAt("tap(1, 2, 3, |").contains("[int pauza"), hintAt("tap(1, 2, 3, |"));
        T.ok(hintAt("if (aproape(pixel(|").contains("color pixel(point p)"), "innermost call");
        T.ok(hintAt("if (aproape(pixel(p), |").contains("[color b]"), "outer call again");
        T.ok(hintAt("if (x > |") == null, "if is no call");
        T.ok(hintAt("for (int i = 0; i < |") == null, "for");
        String own = "int aduna(int a, const int& b) {\n    return a + b;\n}\nstruct Tinta {\n    point p;\n    int ori = 3;\n};\n";
        T.ok(hintAt(own + "aduna(1, |").contains("int aduna(int a, const int& b)") && hintAt(own + "aduna(1, |").contains("[const int& b]"), hintAt(own + "aduna(1, |"));
        T.ok(hintAt(own + "Tinta t = Tinta{point(1, 2), |").contains("[int ori]"), hintAt(own + "Tinta t = Tinta{point(1, 2), |"));
        T.ok(hintAt(own.replace("int ori = 3;", "int ori = 3; |")) == null, "inside the struct itself: nothing");
        T.ok(hintAt("vector<int> v; v.push_back(|").contains("vector.push_back(T element)"), "method");
        T.ok(hintAt("print(\"a(b, \", |").contains("print("), "strings do not count");
        T.ok(hintAt("print(1, 2, 3, |").contains("[...]"), "any number");
        T.ok(hintAt("tap(p);\nwait(|").contains("[int ms]"), "after a finished call");
        T.ok(hintAt("nimic(|") == null, "unknown");
        T.ok(hintAt("// tap(|") == null, "comment");
    }

    static void testTouchSettingsAtTheTop() {
        FakeScriptHostLike h = new FakeScriptHostLike();
        String src = "const int DURATA_CLICK = 120;\nconst int DURATA_SWIPE = 450;\nconst int INTERVAL_REPETARE = 300;\n"
                + "tap(1, 2);\ntap(point(3, 4), 60, 0);\nswipe(point(1, 1), point(9, 9));\napasaLung(5, 5);\n";
        ScriptSimulator.Result r = ScriptSimulator.run(src, new java.util.ArrayList<Action>(), h, 100, 60000, 3000);
        T.eq(ScriptSimulator.END_DONE, r.end, r.describeEnd());
        T.eq(4, r.touches.size(), "touches");
        // tap 1: 120 ms press + 300 ms pause → the next starts at ≥ 420
        T.ok(r.touches.get(1).time >= 420, "pause after the first tap: " + r.touches.get(1).time);
        // tap 2: 60 ms, no pause → the swipe starts right after
        T.ok(r.touches.get(2).time - r.touches.get(1).time < 100, "explicit 0 pause: " + (r.touches.get(2).time - r.touches.get(1).time));
        T.ok(r.touches.get(3).time - r.touches.get(2).time >= 450 + 300, "swipe duration + pause");
        // without the constants: as before (no pause)
        r = ScriptSimulator.run("tap(1, 2);\ntap(3, 4);", new java.util.ArrayList<Action>(), h, 100, 60000, 3000);
        T.ok(r.touches.get(1).time - r.touches.get(0).time <= 100, "old scripts unchanged");
        T.ok(ScriptRunner.check("tap(1, 2, 0);\ntap(point(1, 2), 30, 10);\ndubluTap(1, 2, 100);\nswipe(1, 2, 3, 4, 200, 50);") == null, "new forms check");
    }

    static final class FakeScriptHostLike implements ScriptSimulator.Screen {
        public int width() { return 1080; }
        public int height() { return 2400; }
        public int pixel(int x, int y) { return 0; }
        public String read(int x1, int y1, int x2, int y2, boolean number) { return ""; }
        public java.util.List<int[]> find(com.freeclick.core.ImageSpec s, boolean all) { return new java.util.ArrayList<int[]>(); }
    }

    static void testNewScriptWithSettingsIsValid() {
        String src = ScriptExamples.settingsHeader(50, 300, 200) + ScriptExamples.NEW_SCRIPT;
        T.ok(ScriptRunner.check(src) == null, "valid: " + ScriptRunner.check(src));
        T.ok(ScriptExamples.hasSettingsHeader(src) && !ScriptExamples.hasSettingsHeader(ScriptExamples.NEW_SCRIPT), "detected");
    }

    static void testSwitchLikeCpp() {
        String src = "string nume(int z) {\n"
                + "    switch (z) {\n"
                + "        case 1: return \"luni\";\n"
                + "        case 6:\n"
                + "        case 7: return \"weekend\";\n"
                + "        default: return \"zi de lucru\";\n"
                + "    }\n"
                + "    return \"?\";\n"
                + "}\n"
                + "print(nume(1), \" \", nume(7), \" \", nume(3));\n"
                + "int n = 0;\n"
                + "switch (2) { case 1: n += 1; case 2: n += 10; case 3: n += 100; break; case 4: n += 1000; }\n"
                + "print(n);\n"
                + "string s = \"aur\";\n"
                + "switch (s) { case \"aur\": print(\"galben\"); break; default: print(\"altceva\"); }\n"
                + "for (int i = 0; i < 4; i++) { switch (i) { case 1: continue; case 2: break; } print(i); }\n";
        T.eq("luni weekend zi de lucru|110|galben|0|2|3", outOf(src), "switch");
        T.ok(errorOf("switch (1) { case 1: break; case 1: break; }").getMessage().contains("de două ori"), "duplicate case");
        T.ok(errorOf("case 1: print(1);").getMessage().contains("doar în switch"), "case outside");
        T.ok(errorOf("switch (point(1, 2)) { default: break; }").getMessage().contains("switch merge pe"), "type");
        T.ok(errorOf("int x = 1; continue;").getMessage().contains("continue"), "continue still needs a loop");
    }

    static java.util.List<String> completeAt(String src) {
        int cur = src.indexOf('|');
        Completions.Result r = Completions.at(src.replace("|", ""), cur, 10);
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (r != null) for (Completions.Item it : r.items) out.add(it.insert);
        return out;
    }

    static void testCompletionsSuggestNames() {
        java.util.List<String> c = completeAt("int aurTotal = 0;\nvoid ataca(point p) { }\nstruct Tinta { point p; int ori; };\nat|");
        T.ok(c.size() > 0 && c.get(0).equals("ataca("), "own function first: " + c);
        T.ok(c.contains("atinge("), "app function: " + c);
        c = completeAt("int aurTotal = 0;\nprint(au|");
        T.eq("aurTotal", c.get(0), "own variable");
        T.ok(completeAt("ta|").contains("tap("), "tap");
        T.ok(completeAt("wh|").contains("while ("), "keyword");
        T.ok(completeAt("vec|").contains("vector "), "type");
        T.ok(completeAt("Zq|").isEmpty(), "nothing");
        T.ok(completeAt("struct Tinta { point p; int ori; };\nTinta t;\nTi|").contains("Tinta{"), "struct builds with {");
        c = completeAt("struct Tinta { point p; int ori; };\nTinta t;\nt.|");
        T.ok(c.contains("ori") && c.contains("x"), "fields after a dot: " + c);
        T.ok(completeAt("vector<int> v;\nv.pu|").contains("push_back("), "method");
        T.ok(completeAt("// ta|").isEmpty(), "comment");
        T.ok(completeAt("print(\"ta|\");").isEmpty(), "string");
        T.ok(completeAt("int x = 12|").isEmpty(), "number");
        T.ok(completeAt("color c = #A|").isEmpty(), "colour");
    }

    static void testNewGameFunctions() {
        Host h = new Host();
        h.zoneChangeAt = 3000; // the zone changes at 3 s
        Rec r = run("bool s = asteaptaSchimbare(rect(0, 0, 100, 100), 10000);\nprint(s, \" \", timp());\n"
                + "bool t = asteaptaSchimbare(rect(0, 0, 100, 100), 500);\nprint(t);\n"
                + "print(asteaptaLiniste(rect(0, 0, 10, 10), 600, 5000));\n", h);
        T.ok(r.error == null, "ran: " + (r.error == null ? "" : r.error.describe()));
        T.ok(h.out.get(0).startsWith("true"), "a change: " + h.out);
        T.eq("false", h.out.get(1), "no further change within 0.5 s");
        T.eq("true", h.out.get(2), "quiet");
        h = new Host();
        h.pixels.put("1,2", 0xFF0000);
        h.pixels.put("3,4", 0x00FF00);
        r = run("print(model({point(1, 2), point(3, 4)}, {#FF0000, #00FF00}));\n"
                + "print(model({point(1, 2), point(3, 4)}, {#FF0000, #0000FF}, 10));\n"
                + "tapInZona(rect(100, 200, 300, 400));\n"
                + "zoomIn(point(500, 500));\nzoomOut(point(500, 500), 600, 300);\n"
                + "copiaza(\"cod 123\");\nsalveazaCaptura(\"boss\");\ndeschideLink(\"https://example.com\");\n", h);
        T.ok(r.error == null, "ran 2: " + (r.error == null ? "" : r.error.describe()));
        T.eq("true", h.out.get(0), "pattern ok");
        T.eq("false", h.out.get(1), "pattern no");
        String tap = h.did.get(0);
        String[] xy = tap.split(" ")[1].split(",");
        int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
        T.ok(x >= 120 && x <= 280 && y >= 220 && y <= 380, "inside the zone, off its edges: " + tap);
        T.ok(h.did.get(1).startsWith("GESTURE") && h.did.get(2).startsWith("GESTURE"), "two-finger gestures: " + h.did);
        T.ok(h.did.contains("copy cod 123") && h.did.contains("shot boss") && h.did.contains("url https://example.com"), "the rest: " + h.did);
        h = new Host();
        r = run("model({point(1, 2)}, {#FF0000, #00FF00});", h);
        T.ok(r.error != null && r.error.describe().contains("câte una"), "sizes differ");
        // zoom: two fingers moving apart
        ScriptSimulator.Result sr = ScriptSimulator.run("zoomIn(point(500, 500));\nzoomOut(point(500, 500));",
                new ArrayList<Action>(), new FakeScriptHostLike(), 10, 60000, 3000);
        T.ok(sr.touches.get(0).x2 < sr.touches.get(0).x, "zoom in: the left finger moves left (apart)");
        T.ok(sr.touches.get(1).x2 > sr.touches.get(1).x, "zoom out: it moves right (together)");
    }

    static void testPatternMarksPairPointsWithColours() {
        String src = "if (model({point(10, 20), point(30, 40)}, {#FF0000, #00FF00}, 24)) {\n    tapInZona(rect(1, 2, 3, 4));\n}\n";
        java.util.List<CodeMarks.Mark> m = CodeMarks.scan(src);
        T.eq(3, m.size(), "two pattern pixels + a zone: " + m.size());
        T.eq(CodeMarks.COLOR, m.get(0).kind, "a pixel of the pattern");
        T.ok(m.get(0).values[0] == 10 && m.get(0).values[2] == 0xFF0000, "first point, first colour");
        T.ok(m.get(1).values[0] == 30 && m.get(1).values[2] == 0x00FF00, "second point, second colour");
        T.eq(CodeMarks.ZONE, m.get(2).kind, "zone");
        T.eq(2, m.get(2).line, "line");
        String moved = CodeMarks.replace(src, m.get(1), new int[]{31, 41});
        T.ok(moved.contains("point(31, 41)}, {#FF0000, #00FF00}"), moved);
    }
}
