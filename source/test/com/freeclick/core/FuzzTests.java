package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Randomised tests: thousands of generated macros, touch streams and playback sessions.
 * Seed comes from -Dfuzz.seed (test.sh runs several seeds); failures print the seed.
 */
public final class FuzzTests {
    static final long SEED = Long.getLong("fuzz.seed", 12345L);
    static final int N = Integer.getInteger("fuzz.n", 300);

    static float f(Random r, float lo, float hi) { return lo + r.nextFloat() * (hi - lo); }

    static final String[] IMAGE_IDS = {"aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb", "cccccccccccccccc"};
    static final String[] RANDOM_TEXTS = {"", "Aur: {aur}", "{n} / {aur * 2}", "{", "}{", "{{x}}", "{1/0}", "{random(1,6)} ({tura}, {timp}s, {ora})",
            "{\"a\" + n}", "{max(}", "ăîșț {nume}", "{aproape(c, #34A853, 20)}"};
    static final String[] RANDOM_EXPRS = {"n + 1", "aur - 100", "random(1, 6)", "1/0", "(", "max(aur, 5) * 2 % 7", "\"x\" + n",
            "aur >= 1000 && n < 3", "#FF0000", "sqrt(-1)", "", "round(aur / 3, 2)", "contine(nume, \"ana\")", "!n", "-(-n)", "1e400"};

    static Action randomAction(Random r, List<String> macroIds) {
        Action.Type[] types = Action.Type.values();
        Action.Type t = types[r.nextInt(types.length)];
        Action a;
        switch (t) {
            case TAP: a = Action.tap(f(r, -200, 3000), f(r, -200, 3000)); a.duration = r.nextInt(3) == 0 ? 0 : r.nextInt(2000); break;
            case LONG_PRESS: a = Action.longPress(f(r, 0, 1080), f(r, 0, 2400), r.nextInt(70000)); break;
            case DOUBLE_TAP: a = Action.doubleTap(f(r, 0, 1080), f(r, 0, 2400)); break;
            case SWIPE: a = Action.swipe(f(r, -50, 1200), f(r, -50, 2500), f(r, -50, 1200), f(r, -50, 2500), r.nextInt(90000)); break;
            case GESTURE: {
                List<Stroke> strokes = new ArrayList<Stroke>();
                int n = 1 + r.nextInt(25);
                for (int i = 0; i < n; i++) {
                    Stroke s = new Stroke();
                    long t0 = r.nextInt(3000);
                    int pts = 1 + r.nextInt(30);
                    for (int k = 0; k < pts; k++) s.add(f(r, -100, 1300), f(r, -100, 2600), t0 + k * r.nextInt(500));
                    strokes.add(s);
                }
                a = Action.gesture(strokes);
                break;
            }
            case WAIT: a = r.nextInt(3) == 0 ? Action.randomWait(r.nextInt(3000), r.nextInt(3000)) : Action.waitFor(r.nextInt(3000)); break;
            case LAUNCH_APP: a = Action.launchApp(new String[]{"com.a", "com.b", "", "com.missing"}[r.nextInt(4)], r.nextInt(5000)); break;
            case IMAGE: {
                ImageSpec sp = new ImageSpec();
                sp.id = IMAGE_IDS[r.nextInt(IMAGE_IDS.length)];
                sp.w = 16 + r.nextInt(300); sp.h = 16 + r.nextInt(300);
                sp.capX = f(r, -50, 1100); sp.capY = f(r, -50, 2400);
                sp.capScreenW = 1080; sp.capScreenH = 2400;
                sp.tapDx = f(r, -20, sp.w + 20); sp.tapDy = f(r, -20, sp.h + 20);
                sp.precision = 60 + r.nextInt(45);
                if (r.nextBoolean()) { sp.zx1 = f(r, 0, 500); sp.zy1 = f(r, 0, 1000); sp.zx2 = sp.zx1 + f(r, 0, 800); sp.zy2 = sp.zy1 + f(r, 0, 1500); }
                a = Action.image(sp);
                a.press = r.nextInt(4); a.waitMode = r.nextInt(2); a.duration = r.nextInt(3) == 0 ? 0 : r.nextInt(6000);
                a.allMatches = r.nextBoolean(); a.maxHits = r.nextInt(3) == 0 ? 1 + r.nextInt(4) : 0;
                a.stopOnTimeout = r.nextInt(4) == 0; a.pressMs = 1 + r.nextInt(2000);
                break;
            }
            case GLOBAL: a = Action.global(1 + r.nextInt(12)); break;
            case PRINT: a = Action.print(RANDOM_TEXTS[r.nextInt(RANDOM_TEXTS.length)]); break;
            case SET_VAR: a = Action.setVar(new String[]{"n", "aur", "Ăla", "", "tura", "9x", "x y"}[r.nextInt(7)],
                    RANDOM_EXPRS[r.nextInt(RANDOM_EXPRS.length)]); break;
            case READ: {
                int mode = r.nextInt(3);
                String var = new String[]{"aur", "nume", "", "timp", "c"}[r.nextInt(5)];
                a = mode == Action.READ_COLOR ? Action.readColor(f(r, -50, 1200), f(r, -50, 2500), var)
                        : Action.read(f(r, -50, 1200), f(r, -50, 2500), f(r, -50, 1200), f(r, -50, 2500), mode, var);
                break;
            }
            case MACRO: a = Action.macro(macroIds.isEmpty() || r.nextInt(5) == 0 ? "missing" : macroIds.get(r.nextInt(macroIds.size())), 1 + r.nextInt(3)); break;
            default: a = Action.waitColor(f(r, 0, 1080), f(r, 0, 2400), r.nextInt(0x1000000), r.nextInt(4000));
                a.invert = r.nextBoolean(); a.stopOnTimeout = r.nextInt(4) == 0; a.tolerance = r.nextInt(256);
        }
        a.delay = r.nextInt(4) == 0 ? 0 : r.nextInt(1500);
        a.repeat = 1 + (r.nextInt(5) == 0 ? r.nextInt(4) : 0);
        a.interval = r.nextInt(300);
        a.enabled = r.nextInt(8) != 0;
        if (r.nextBoolean()) a.label = "ăîș \"x\" \n" + r.nextInt();
        return a;
    }

    /** Codec-only: steps nested in blocks (the engine does not play blocks before 1.8). */
    static void nestRandomly(Random r, List<Action> list, List<String> ids, int depth) {
        if (depth > 4) return;
        for (Action a : new ArrayList<Action>(list)) {
            if (r.nextInt(5) == 0) {
                a.children = new ArrayList<Action>();
                for (int k = 0; k < r.nextInt(4); k++) a.children.add(randomAction(r, ids));
                nestRandomly(r, a.children, ids, depth + 1);
                if (r.nextBoolean()) { a.elseChildren = new ArrayList<Action>(); a.elseChildren.add(randomAction(r, ids)); }
            }
        }
    }

    static Macro randomMacro(Random r, List<String> ids) {
        Macro m = new Macro("m" + r.nextInt(1000) + "ș");
        m.kind = new String[]{Macro.KIND_MANUAL, Macro.KIND_RECORD, Macro.KIND_COMBO}[r.nextInt(3)];
        m.screenWidth = r.nextInt(4) == 0 ? 0 : 400 + r.nextInt(1200);
        m.screenHeight = r.nextInt(4) == 0 ? 0 : 800 + r.nextInt(2000);
        int n = r.nextInt(12);
        for (int i = 0; i < n; i++) m.actions.add(randomAction(r, ids));
        Macro.Settings s = m.settings;
        s.loops = r.nextInt(4);
        s.loopDelay = r.nextInt(800);
        s.speed = Macro.clampSpeed(0.05 + r.nextDouble() * 12);
        s.jitterPx = r.nextInt(3) == 0 ? r.nextInt(40) : 0;
        s.jitterTimePct = r.nextInt(3) == 0 ? r.nextInt(60) : 0;
        s.stopAfterMs = r.nextInt(3) == 0 ? 1000 + r.nextInt(20000) : 0;
        s.scaleToScreen = r.nextBoolean();
        s.safeMode = r.nextBoolean();
        return m;
    }

    // ---------------------------------------------------------------- tests
    static void testJsonAndCodecFuzz() {
        Random r = new Random(SEED);
        for (int i = 0; i < N; i++) {
            Macro m = randomMacro(r, new ArrayList<String>());
            nestRandomly(r, m.actions, new ArrayList<String>(), 0);
            String a = MacroCodec.toJson(m);
            T.eq(ActionTree.count(m.actions), ActionTree.count(MacroCodec.fromJson(a).actions), "no step lost in blocks (case " + i + ")");
            String b = MacroCodec.toJson(MacroCodec.fromJson(a));
            String c = MacroCodec.toJson(MacroCodec.fromJson(b));
            T.eq(b, c, "codec is stable after one round trip (seed " + SEED + ", case " + i + ")");
            // corrupt the text: must fail cleanly or parse, never crash with another exception type
            StringBuilder sb = new StringBuilder(a);
            for (int k = 0; k < 3; k++) {
                int pos = r.nextInt(sb.length());
                switch (r.nextInt(3)) {
                    case 0: sb.deleteCharAt(pos); break;
                    case 1: sb.insert(pos, "{[\",:}]".charAt(r.nextInt(7))); break;
                    default: sb.setCharAt(pos, (char) (32 + r.nextInt(95)));
                }
            }
            try {
                MacroCodec.fromJson(sb.toString());
            } catch (Json.ParseException | IllegalArgumentException expected) {
                // fine
            }
            try {
                MacroCodec.importBundle(sb.toString(), new HashSet<String>());
            } catch (Json.ParseException | IllegalArgumentException expected) {
                // fine
            }
        }
    }

    static void testPlannerFuzz() {
        Random r = new Random(SEED + 1);
        FakeHost h = new FakeHost();
        for (int i = 0; i < N * 5; i++) {
            Action a = randomAction(r, new ArrayList<String>());
            if (!a.isGesture()) continue;
            h.width = 300 + r.nextInt(2000);
            h.height = 300 + r.nextInt(3000);
            h.limits.maxStrokes = r.nextBoolean() ? 20 : 10;
            CoordinateScaler sc = CoordinateScaler.of(r.nextInt(2000), r.nextInt(3000), h.width, h.height, r.nextBoolean());
            GesturePlan p = GesturePlanner.plan(a, 0.05 + r.nextDouble() * 15, r.nextInt(50), r, sc, h.limits());
            T.ok(p != null, "gesture actions always produce a plan");
            h.checkPlan(p);
        }
        T.eq(0, h.violations, "all plans valid for Android: " + h.violationLog);
    }

    static void testRecorderFuzz() {
        Random r = new Random(SEED + 2);
        FakeHost h = new FakeHost();
        for (int i = 0; i < N; i++) {
            GestureRecorder rec = new GestureRecorder(new GestureRecorder.Config());
            long t = r.nextInt(1000);
            rec.start(t);
            List<Integer> down = new ArrayList<Integer>();
            for (int e = 0; e < 200; e++) {
                t += r.nextInt(60);
                float x = f(r, -20, 1100), y = f(r, -20, 2420);
                int op = r.nextInt(10);
                if (op < 2 && down.size() < 12) {
                    int id = r.nextInt(16);
                    if (!down.contains(id)) down.add(id);
                    rec.pointerDown(id, x, y, t);
                } else if (op < 7 && !down.isEmpty()) {
                    rec.pointerMove(down.get(r.nextInt(down.size())), x, y, t);
                } else if (op < 9 && !down.isEmpty()) {
                    Integer id = down.remove(r.nextInt(down.size()));
                    rec.pointerUp(id, x, y, t);
                } else if (op == 9 && r.nextInt(5) == 0) {
                    rec.cancelGesture();
                    down.clear();
                } else if (r.nextInt(20) == 0) {
                    rec.addGlobal(GlobalActions.BACK, t);
                }
            }
            List<Action> out = rec.stop();
            for (Action a : out) {
                T.ok(a.delay >= 0, "non-negative delays");
                if (a.isGesture()) h.checkPlan(GesturePlanner.plan(a, 1, 0, null, null, h.limits()));
            }
        }
        T.eq(0, h.violations, "recorded gestures replay validly: " + h.violationLog);
    }

    /** Random sessions with sub-macros (maybe cyclic), pauses, speed changes, stops, failures, colours. */
    static void testEngineSessionFuzz() {
        Random r = new Random(SEED + 3);
        int finished = 0;
        long gestures = 0;
        java.util.Map<String, Integer> why = new java.util.TreeMap<String, Integer>();
        for (int i = 0; i < N; i++) {
            MacroLibrary.InMemory lib = new MacroLibrary.InMemory();
            List<String> ids = new ArrayList<String>();
            List<Macro> all = new ArrayList<Macro>();
            for (int k = 0; k < 3; k++) { Macro m = randomMacro(r, ids); ids.add(m.id); all.add(m); lib.add(m); }
            // allow references to any (including later ones -> possible cycles)
            for (Macro m : all) for (Action a : m.actions) if (a.type == Action.Type.MACRO && r.nextBoolean()) a.macroId = ids.get(r.nextInt(ids.size()));
            Macro root = all.get(r.nextInt(all.size()));
            if (root.settings.loops == 0 && root.settings.stopAfterMs == 0) root.settings.stopAfterMs = 15000;
            if (root.settings.stopAfterMs == 0) root.settings.stopAfterMs = 40000; // image/colour waits may be endless

            FakeHost h = new FakeHost();
            h.random = r;
            if (r.nextBoolean()) { h.colorTimeline.put(0L, r.nextInt(0x1000000)); h.colorTimeline.put((long) r.nextInt(5000), r.nextInt(0x1000000)); }
            if (r.nextBoolean()) {
                String[] seen = {"1,234", "12 345", "Gold 1.5k", "", "abc", "-7", "99%"};
                for (int k = 0; k < 4; k++) h.textTimeline.put((long) r.nextInt(8000), seen[r.nextInt(seen.length)]);
            }
            h.readNoCapture = r.nextInt(15) == 0;
            if (r.nextBoolean()) h.frameIntervalMs = 100 + r.nextInt(900);
            h.installed.add("com.a");
            if (r.nextBoolean()) h.installed.add("com.b");
            h.appLaunchDelayMs = r.nextInt(6000);
            for (String id : IMAGE_IDS) {
                for (int k = 0; k < r.nextInt(4); k++) {
                    long from = 1000 + r.nextInt(8000);
                    h.show(id, from, from + r.nextInt(6000), f(r, -100, 1200), f(r, -100, 2500));
                    if (r.nextInt(6) == 0) h.imageTimeline.get(id).get(h.imageTimeline.get(id).size() - 1).covered = true;
                }
            }
            h.imageLatencyMs = r.nextInt(300);
            if (r.nextInt(20) == 0) h.imageNoCapture = true;
            if (r.nextInt(20) == 0) h.protectedFrom = 1000 + r.nextInt(10000);
            final List<String> reasons = new ArrayList<String>();
            PlaybackEngine e = new PlaybackEngine(h, new PlaybackEngine.Listener() {
                public void onStateChanged(PlaybackEngine.State s) { }
                public void onProgress(int l, int t, int s, int n, String name, Action a) { }
                public void onFinished(String reason) { reasons.add(reason); }
            });
            h.engine = e;
            e.setRandom(new Random(r.nextLong()));
            e.setPauseOnCancel(r.nextBoolean());
            if (r.nextInt(4) == 0) e.setPreview(true, r.nextBoolean());
            e.start(root, lib, r.nextInt(3) == 0 ? r.nextInt(5) : 0);
            // random user actions while it runs
            for (int step = 0; step < 30 && e.state() != PlaybackEngine.State.FINISHED; step++) {
                h.runUntil(h.now + r.nextInt(1500));
                switch (r.nextInt(8)) {
                    case 0: e.pause(); break;
                    case 1: e.resume(); break;
                    case 2: e.setSpeed(0.05 + r.nextDouble() * 15); break;
                    case 3: if (r.nextInt(4) == 0) e.stop(); break;
                    case 4: e.skipWait(); break;
                    case 5: if (e.isPreview()) e.setPreviewAssumeFound(r.nextBoolean()); else e.pause(PlaybackEngine.PAUSE_APP); break;
                    default: break;
                }
                if (e.isPreview()) T.eq(0, h.samples.size() + h.readRequests.size(), "preview never reads the screen");
            }
            for (int k = 0; k < 5 && e.state() == PlaybackEngine.State.PAUSED; k++) { e.resume(); h.runAll(200000); }
            h.runAll(200000);
            if (e.state() != PlaybackEngine.State.FINISHED) {
                e.stop();
                h.runAll(1000);
            }
            T.eq(PlaybackEngine.State.FINISHED, e.state(), "always ends (seed " + SEED + ", case " + i + ")");
            T.eq(1, reasons.size(), "finish reported exactly once (seed " + SEED + ", case " + i + ") " + reasons);
            T.eq(0, h.violations, "engine invariants (seed " + SEED + ", case " + i + "): " + h.violationLog);
            T.eq(0, h.pendingTimers(), "nothing left scheduled (case " + i + "): " + h.pendingDescription() + " state " + e.state() + " " + reasons);
            finished++;
            gestures += h.gestures().size();
            Integer c = why.get(reasons.get(0));
            why.put(reasons.get(0), c == null ? 1 : c + 1);
        }
        System.out.println("    engine fuzz: " + N + " sessions, " + gestures + " gestures injected, endings " + why);
        T.eq(N, finished, "all sessions completed");
    }

    /** Random sequences of panel operations with "restarts": no crash, drafts always loadable. */
    static void testWorkspaceFuzz() throws Exception {
        Random r = new Random(SEED + 4);
        for (int i = 0; i < N / 3; i++) {
            WorkspaceTests.MemStore store = new WorkspaceTests.MemStore();
            WorkspaceTests.MemRepo repo = new WorkspaceTests.MemRepo();
            Workspace w = WorkspaceTests.ws(store, repo);
            w.ensureMode();
            for (int op = 0; op < 60; op++) {
                if (r.nextInt(4) == 0) {
                    String before = MacroCodec.toJson(w.current());
                    if (w.undo() != null) {
                        T.ok(w.canRedo(), "undo makes redo possible");
                        w.redo();
                        T.eq(before, MacroCodec.toJson(w.current()), "undo + redo is a no-op (case " + i + ")");
                        if (r.nextBoolean()) w.undo();
                    }
                }
                switch (r.nextInt(12)) {
                    case 0: w.switchMode(new String[]{Macro.KIND_MANUAL, Macro.KIND_RECORD, Macro.KIND_COMBO}[r.nextInt(3)]); break;
                    case 1: w.addTarget(Workspace.Target.values()[r.nextInt(3)]); break;
                    case 2: w.removeLast(); break;
                    case 3: if (r.nextInt(4) == 0) w.clearAll(); break;
                    case 4: if (w.current().actions.size() > 0) w.saveAs("s" + op); break;
                    case 5: if (w.isSaved()) w.save(); break;
                    case 6: if (!repo.lib.all().isEmpty()) w.load(repo.lib.all().get(r.nextInt(repo.lib.all().size()))); break;
                    case 7: w.beginRecording(); List<Action> rec = new ArrayList<Action>();
                        for (int k = 0; k < r.nextInt(4); k++) rec.add(Action.tap(r.nextInt(1000), r.nextInt(2000)));
                        w.addRecorded(rec); break;
                    case 8: w = WorkspaceTests.ws(store, repo); w.ensureMode(); break; // restart / update
                    case 9: w.setScreen(400 + r.nextInt(1500), 800 + r.nextInt(2000)); w.rescaleToScreen(); break;
                    case 10: w.setSpeed(r.nextDouble() * 20); break;
                    default: w.reset();
                }
                T.ok(w.current() != null, "always has current work");
                T.eq(store.mode, w.current().kind, "work matches the selected mode");
                String draft = store.drafts.get(w.current().kind);
                if (draft != null) MacroCodec.fromJson(draft); // drafts always loadable
            }
        }
    }
}
