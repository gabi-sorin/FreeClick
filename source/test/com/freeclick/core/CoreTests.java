package com.freeclick.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** JSON, model, codec, stroke, recorder, planner and graph tests. */
public final class CoreTests {

    // ------------------------------------------------------------- JSON
    static void testJsonRoundTrip() {
        String src = "{\"a\":1,\"b\":[true,false,null,2.5,-3],\"c\":{\"d\":\"x\\\"y\\n\\u0103\"}}";
        Object o = Json.parse(src);
        String again = Json.stringify(o);
        T.eq(src.replace("\\u0103", "ă"), again, "compact round trip");
        T.eq(o, Json.parse(Json.stringify(o, true)), "pretty round trip");
    }

    static void testJsonNumbers() {
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) Json.parse("{\"i\":42,\"f\":1.5e2,\"n\":-0.25,\"big\":123456789012}");
        T.eq(42L, m.get("i"), "int as Long");
        T.eq(150.0, m.get("f"), "exponent as Double");
        T.eq(-0.25, m.get("n"), "negative fraction");
        T.eq(123456789012L, m.get("big"), "big long");
    }

    static void testJsonErrors() {
        T.throwsAny(new Runnable() { public void run() { Json.parse("{\"a\":}"); } }, "missing value");
        T.throwsAny(new Runnable() { public void run() { Json.parse("[1,2"); } }, "unterminated array");
        T.throwsAny(new Runnable() { public void run() { Json.parse("{} x"); } }, "trailing garbage");
        T.throwsAny(new Runnable() { public void run() { Json.parse("\"abc"); } }, "unterminated string");
        T.throwsAny(new Runnable() { public void run() { Json.parse(""); } }, "empty");
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 1000; i++) deep.append('[');
        final String d = deep.toString();
        T.throwsAny(new Runnable() { public void run() { Json.parse(d); } }, "too deep must fail, not stack overflow");
    }

    static void testJsonUnicodeAndControl() {
        String s = "ăîșțâ \t tab \u0001 ctl 😀";
        T.eq(s, Json.parse(Json.stringify(s)), "unicode string");
    }

    // ------------------------------------------------------------- codec
    static Macro sampleMacro() {
        Macro m = new Macro("Test ăș \"quoted\"");
        m.screenWidth = 1080; m.screenHeight = 2400;
        m.settings.loops = 5; m.settings.speed = 2.5; m.settings.jitterPx = 7; m.settings.jitterTimePct = 20;
        m.settings.stopAfterMs = 60000; m.settings.safeMode = true; m.settings.recordVideo = true;
        m.settings.lockToPackage = "com.game";
        m.settings.launchPackage = "com.game.launch";
        Action t = Action.tap(100.25f, 200.75f); t.delay = 300; t.repeat = 3; t.interval = 150; t.label = "buton";
        m.actions.add(t);
        m.actions.add(Action.longPress(5, 6, 900));
        m.actions.add(Action.doubleTap(7, 8));
        m.actions.add(Action.swipe(10, 20, 30, 40, 250));
        List<Stroke> strokes = new ArrayList<Stroke>();
        strokes.add(new Stroke().add(1, 2, 0).add(3, 4, 50).add(5, 6, 100));
        strokes.add(new Stroke().add(9, 9, 20).add(19, 29, 120));
        m.actions.add(Action.gesture(strokes));
        m.actions.add(Action.waitFor(1234));
        m.actions.add(Action.global(GlobalActions.BACK));
        Action sub = Action.macro("other-id", 4); sub.enabled = false;
        m.actions.add(sub);
        return m;
    }

    static void testCodecRoundTrip() {
        Macro m = sampleMacro();
        Macro r = MacroCodec.fromJson(MacroCodec.toJson(m));
        T.eq(m.id, r.id, "id");
        T.eq(m.name, r.name, "name");
        T.eq(1080, r.screenWidth, "screenWidth");
        T.eq(5, r.settings.loops, "loops");
        T.near(2.5, r.settings.speed, 1e-9, "speed");
        T.eq(7, r.settings.jitterPx, "jitterPx");
        T.eq(20, r.settings.jitterTimePct, "jitterTimePct");
        T.eq(60000L, r.settings.stopAfterMs, "stopAfter");
        T.ok(r.settings.safeMode && r.settings.recordVideo, "flags");
        T.eq("com.game", r.settings.lockToPackage, "lockToPackage");
        T.eq("com.game.launch", r.settings.launchPackage, "launchPackage");
        T.eq(m.actions.size(), r.actions.size(), "action count");
        Action t = r.actions.get(0);
        T.eq(Action.Type.TAP, t.type, "tap type");
        T.near(100.3, t.x, 0.051, "x rounded to 0.1");
        T.eq(300L, t.delay, "delay");
        T.eq(3, t.repeat, "repeat");
        T.eq(150L, t.interval, "interval");
        T.eq("buton", t.label, "label");
        Action sw = r.actions.get(3);
        T.near(30, sw.x2, 1e-6, "x2"); T.near(40, sw.y2, 1e-6, "y2"); T.eq(250L, sw.duration, "swipe dur");
        Action g = r.actions.get(4);
        T.eq(2, g.strokes.size(), "strokes");
        T.eq(3, g.strokes.get(0).size(), "points");
        T.eq(20L, g.strokes.get(1).startTime(), "second finger start");
        T.eq(GlobalActions.BACK, r.actions.get(6).globalAction, "global");
        T.eq("other-id", r.actions.get(7).macroId, "macroId");
        T.ok(!r.actions.get(7).enabled, "disabled flag");
        T.eq(4, r.actions.get(7).repeat, "sub repeat");
    }

    static void testCodecToleratesBadData() {
        String json = "{\"name\":\"x\",\"settings\":{\"speed\":999,\"loops\":-4,\"jitterTimePct\":500},"
                + "\"actions\":[{\"type\":\"TELEPORT\"},{\"type\":\"TAP\",\"x\":1,\"y\":2,\"repeat\":0,\"delay\":-5},"
                + "{\"type\":\"GESTURE\",\"strokes\":[]},42]}";
        Macro m = MacroCodec.fromJson(json);
        T.near(Macro.MAX_SPEED, m.settings.speed, 1e-9, "speed clamped");
        T.eq(0, m.settings.loops, "negative loops -> 0");
        T.eq(100, m.settings.jitterTimePct, "jitter clamped");
        T.eq(2, m.actions.size(), "unknown type kept (opaque), empty gesture and garbage skipped");
        T.eq(Action.Type.UNKNOWN, m.actions.get(0).type, "TELEPORT kept as an unknown step");
        T.ok(!m.actions.get(0).enabled, "unknown steps never run");
        T.ok(MacroCodec.toJson(m).contains("\"TELEPORT\""), "unknown step written back unchanged");
        T.eq(1, m.actions.get(1).repeat, "repeat >= 1");
        T.eq(0L, m.actions.get(1).delay, "delay >= 0");
        T.ok(m.id != null && m.id.length() > 0, "missing id gets generated");
    }

    static void testBundleExportImportRemapsIds() {
        Macro child = new Macro("child");
        child.actions.add(Action.tap(1, 1));
        Macro parent = new Macro("parent");
        parent.actions.add(Action.macro(child.id, 2));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(child).add(parent);
        String bundle = MacroCodec.exportBundle(parent, lib);

        // Importing into a library that already has both ids -> fresh ids, references rewritten.
        List<Macro> imported = MacroCodec.importBundle(bundle, new HashSet<String>(Arrays.asList(child.id, parent.id)));
        T.eq(2, imported.size(), "both macros exported");
        Macro p2 = imported.get(0).name.equals("parent") ? imported.get(0) : imported.get(1);
        Macro c2 = imported.get(0).name.equals("child") ? imported.get(0) : imported.get(1);
        T.ok(!p2.id.equals(parent.id) && !c2.id.equals(child.id), "ids remapped");
        T.eq(c2.id, p2.actions.get(0).macroId, "reference follows the remap");

        // Into an empty library -> ids kept.
        List<Macro> kept = MacroCodec.importBundle(bundle, new HashSet<String>());
        T.ok(kept.get(0).id.equals(parent.id) || kept.get(0).id.equals(child.id), "ids kept when free");

        // Hostile ids (path traversal) are replaced on import.
        Macro evil = new Macro("evil");
        evil.id = "../../shared_prefs/freeclick";
        List<Macro> fixed = MacroCodec.importBundle(MacroCodec.toJson(evil), new HashSet<String>());
        T.ok(MacroCodec.isSafeId(fixed.get(0).id), "unsafe id replaced: " + fixed.get(0).id);

        // A single macro file is importable too.
        List<Macro> single = MacroCodec.importBundle(MacroCodec.toJson(child), new HashSet<String>());
        T.eq(1, single.size(), "single macro import");
        T.throwsAny(new Runnable() { public void run() {
            MacroCodec.importBundle("{\"hello\":1}", new HashSet<String>()); } }, "not a macro");
    }

    static void testDuplicateIsDeep() {
        Macro m = sampleMacro();
        Macro d = m.duplicate("copy");
        T.ok(!d.id.equals(m.id), "new id");
        d.actions.get(4).strokes.get(0).points.get(0)[0] = 999;
        d.settings.loops = 77;
        T.near(1, m.actions.get(4).strokes.get(0).x(0), 1e-6, "strokes deep-copied");
        T.eq(5, m.settings.loops, "settings copied");
    }

    // ------------------------------------------------------------- stroke
    static void testSimplifyStraightLine() {
        Stroke s = new Stroke();
        for (int i = 0; i <= 100; i++) s.add(i * 5, i * 2, i * 10);
        Stroke r = s.simplified(1.0, 150);
        T.eq(2, r.size(), "straight line -> 2 points");
        T.eq(1000L, r.endTime(), "end time kept");
    }

    static void testSimplifyKeepsCorner() {
        Stroke s = new Stroke();
        for (int i = 0; i <= 50; i++) s.add(i * 4, 0, i);
        for (int i = 1; i <= 50; i++) s.add(200, i * 4, 50 + i);
        Stroke r = s.simplified(1.0, 150);
        T.eq(3, r.size(), "L shape -> 3 points");
        T.near(200, r.x(1), 1e-6, "corner x");
        T.near(0, r.y(1), 1e-6, "corner y");
    }

    static void testSimplifyCap() {
        Stroke s = new Stroke();
        Random rnd = new Random(1);
        for (int i = 0; i < 1000; i++) s.add(rnd.nextFloat() * 1000, rnd.nextFloat() * 1000, i);
        Stroke r = s.simplified(0.5, 100);
        T.eq(100, r.size(), "capped");
        T.eq(0L, r.startTime(), "first kept");
        T.eq(999L, r.endTime(), "last kept");
    }

    // ------------------------------------------------------------- recorder
    static GestureRecorder rec() {
        GestureRecorder r = new GestureRecorder(new GestureRecorder.Config());
        r.start(0);
        return r;
    }

    static void testRecorderTapAndLongPress() {
        GestureRecorder r = rec();
        r.pointerDown(0, 100, 100, 200);
        r.pointerMove(0, 103, 102, 230);
        Action tap = r.pointerUp(0, 103, 102, 280);
        T.eq(Action.Type.TAP, tap.type, "small move + short = tap");
        T.eq(200L, tap.delay, "delay since start");
        T.eq(80L, tap.duration, "real press time kept");
        r.pointerDown(0, 50, 50, 1280);
        Action lp = r.pointerUp(0, 50, 50, 2000);
        T.eq(Action.Type.LONG_PRESS, lp.type, "long press");
        T.eq(1000L, lp.delay, "delay between gestures measured from previous lift");
        T.eq(720L, lp.duration, "long press duration");
    }

    static void testRecorderKeepsInitialPause() {
        GestureRecorder r = rec();
        r.pointerDown(0, 1, 1, 9000);
        Action a = r.pointerUp(0, 1, 1, 9050);
        T.eq(9000L, a.delay, "initial pause (record pressed -> first touch) kept exactly");
        GestureRecorder.Config c = new GestureRecorder.Config();
        c.firstDelayCapMs = 1000;
        GestureRecorder capped = new GestureRecorder(c);
        capped.start(0);
        capped.pointerDown(0, 1, 1, 9000);
        T.eq(1000L, capped.pointerUp(0, 1, 1, 9050).delay, "cap still available as an option");
    }

    static void testRecorderSwipeAndCurve() {
        GestureRecorder r = rec();
        r.pointerDown(0, 100, 1000, 0);
        for (int i = 1; i <= 20; i++) r.pointerMove(0, 100, 1000 - i * 40, i * 10);
        Action sw = r.pointerUp(0, 100, 200, 210);
        T.eq(Action.Type.SWIPE, sw.type, "straight drag = swipe");
        T.near(200, sw.y2, 1e-6, "end y");
        T.eq(210L, sw.duration, "swipe duration");

        r.pointerDown(0, 0, 0, 1000);
        for (int i = 1; i <= 36; i++) {
            double ang = Math.toRadians(i * 10);
            r.pointerMove(0, (float) (300 + 200 * Math.cos(ang)), (float) (300 + 200 * Math.sin(ang)), 1000 + i * 10);
        }
        Action circle = r.pointerUp(0, 500, 300, 1400);
        T.eq(Action.Type.GESTURE, circle.type, "curve = gesture");
        T.ok(circle.strokes.get(0).size() > 5, "curve keeps several points");
        T.eq(0L, circle.strokes.get(0).startTime(), "times relative to gesture start");
    }

    static void testRecorderMultiTouch() {
        GestureRecorder r = rec();
        r.pointerDown(0, 100, 100, 100);
        r.pointerDown(1, 500, 500, 150);
        r.pointerMove(0, 50, 50, 200);
        r.pointerMove(1, 600, 600, 200);
        T.ok(r.pointerUp(0, 40, 40, 300) == null, "gesture not finished while a finger is down");
        Action pinch = r.pointerUp(1, 650, 650, 320);
        T.eq(Action.Type.GESTURE, pinch.type, "two fingers = gesture");
        T.eq(2, pinch.strokes.size(), "two strokes");
        T.eq(50L, pinch.strokes.get(1).startTime(), "second finger offset kept");
        T.eq(220L, pinch.duration, "duration until last lift");
        T.eq(1, r.actions().size(), "one action recorded");
    }

    static void testRecorderStationaryFingerInMultiTouchIsInjectable() {
        GestureRecorder r = rec();
        r.pointerDown(0, 100, 100, 0);
        r.pointerDown(1, 300, 300, 10);
        r.pointerMove(1, 400, 380, 60);
        r.pointerMove(1, 500, 300, 110);
        r.pointerUp(1, 500, 300, 110);
        Action a = r.pointerUp(0, 100, 100, 200);
        T.eq(Action.Type.GESTURE, a.type, "gesture");
        Stroke still = a.strokes.get(1).startTime() == 0 ? a.strokes.get(1) : a.strokes.get(0);
        T.ok(still.duration() >= 1, "stationary finger stroke has positive duration");
    }

    static void testRecorderGlobalAndCancel() {
        GestureRecorder r = rec();
        r.pointerDown(0, 1, 1, 100);
        r.pointerUp(0, 1, 1, 150);
        Action g = r.addGlobal(GlobalActions.HOME, 650);
        T.eq(500L, g.delay, "global delay");
        r.pointerDown(0, 1, 1, 700);
        r.cancelGesture();
        T.ok(!r.isInGesture(), "cancelled");
        T.eq(2, r.actions().size(), "cancelled gesture not recorded");
        List<Action> out = r.stop();
        T.eq(2, out.size(), "stop returns actions");
        T.ok(r.pointerUp(0, 1, 1, 800) == null, "ignored after stop");
    }

    // ------------------------------------------------------------- planner
    static GesturePlanner.Limits lim() {
        GesturePlanner.Limits l = new GesturePlanner.Limits();
        l.screenWidth = 1080; l.screenHeight = 2400;
        return l;
    }

    static void testPlannerTapNotSlowedDown() {
        Action tap = Action.tap(10, 20);
        GesturePlan p = GesturePlanner.plan(tap, 0.1, 0, null, null, lim());
        T.eq(Action.DEFAULT_TAP_MS, p.totalDuration(), "tap press time independent of speed");
        GesturePlan dt = GesturePlanner.plan(Action.doubleTap(5, 5), 10, 0, null, null, lim());
        T.eq(2, dt.strokes.size(), "double tap = 2 strokes");
        T.eq(Action.DEFAULT_TAP_MS + Action.DOUBLE_TAP_GAP_MS, dt.strokes.get(1).startTime, "second tap start");
    }

    static void testPlannerSwipeAndGestureScaledBySpeed() {
        Action sw = Action.swipe(0, 0, 100, 100, 400);
        T.eq(200L, GesturePlanner.plan(sw, 2.0, 0, null, null, lim()).totalDuration(), "2x");
        T.eq(800L, GesturePlanner.plan(sw, 0.5, 0, null, null, lim()).totalDuration(), "0.5x");
        List<Stroke> st = new ArrayList<Stroke>();
        st.add(new Stroke().add(0, 0, 0).add(10, 10, 100));
        st.add(new Stroke().add(5, 5, 100).add(50, 50, 300));
        GesturePlan g = GesturePlanner.plan(Action.gesture(st), 2.0, 0, null, null, lim());
        T.eq(50L, g.strokes.get(1).startTime, "stroke offset scaled");
        T.eq(100L, g.strokes.get(1).duration, "stroke duration scaled");
    }

    static void testPlannerClampsAndScales() {
        Action t = Action.tap(-50, 5000);
        GesturePlan p = GesturePlanner.plan(t, 1, 0, null, null, lim());
        T.near(0, p.strokes.get(0).points.get(0)[0], 1e-6, "negative x clamped to 0");
        T.near(2399, p.strokes.get(0).points.get(0)[1], 1e-6, "y clamped to screen");
        CoordinateScaler sc = CoordinateScaler.of(1080, 2400, 720, 1600, true);
        GesturePlan q = GesturePlanner.plan(Action.tap(540, 1200), 1, 0, null, sc, lim());
        T.near(360, q.strokes.get(0).points.get(0)[0], 1e-3, "scaled x");
        T.near(800, q.strokes.get(0).points.get(0)[1], 1e-3, "scaled y");
        T.ok(CoordinateScaler.of(1080, 2400, 720, 1600, false).isIdentity(), "scaling disabled");
        T.ok(CoordinateScaler.of(0, 0, 720, 1600, true).isIdentity(), "unknown recording size");
        T.ok(GesturePlanner.plan(Action.waitFor(5), 1, 0, null, null, lim()) == null, "wait is not a gesture");
    }

    static void testPlannerJitterBounds() {
        Random rnd = new Random(42);
        for (int i = 0; i < 500; i++) {
            GesturePlan p = GesturePlanner.plan(Action.tap(500, 500), 1, 10, rnd, null, lim());
            float[] pt = p.strokes.get(0).points.get(0);
            T.ok(Math.abs(pt[0] - 500) <= 10 && Math.abs(pt[1] - 500) <= 10, "jitter within 10px");
        }
        GesturePlan sw = GesturePlanner.plan(Action.swipe(100, 100, 300, 100, 100), 1, 20, new Random(3), null, lim());
        float dx = sw.strokes.get(0).points.get(1)[0] - sw.strokes.get(0).points.get(0)[0];
        float dy = sw.strokes.get(0).points.get(1)[1] - sw.strokes.get(0).points.get(0)[1];
        T.near(200, dx, 1e-3, "swipe moved as a whole (direction preserved)");
        T.near(0, dy, 1e-3, "swipe moved as a whole");
    }

    static void testPlannerRespectsAndroidLimits() {
        GesturePlanner.Limits l = lim();
        l.maxStrokes = 10;
        l.maxDuration = 60000;
        List<Stroke> st = new ArrayList<Stroke>();
        for (int i = 0; i < 15; i++) st.add(new Stroke().add(i, i, i * 10).add(i + 5, i + 5, 90000));
        GesturePlan p = GesturePlanner.plan(Action.gesture(st), 1, 0, null, null, l);
        T.eq(10, p.strokes.size(), "strokes capped");
        T.ok(p.totalDuration() <= 60000, "duration compressed to 60s, was " + p.totalDuration());
        for (GesturePlan.PlannedStroke s : p.strokes) T.ok(s.duration >= 1 && s.startTime >= 0, "valid stroke times");
        // slow motion of a long gesture must also fit
        Action longSwipe = Action.swipe(0, 0, 10, 10, 30000);
        T.ok(GesturePlanner.plan(longSwipe, 0.1, 0, null, null, l).totalDuration() <= 60000, "slow motion clamped");
    }

    // ------------------------------------------------------------- graph
    static void testCycleDetection() {
        Macro a = new Macro("A"), b = new Macro("B"), c = new Macro("C");
        a.actions.add(Action.macro(b.id, 1));
        b.actions.add(Action.macro(c.id, 1));
        c.actions.add(Action.tap(1, 1));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(a).add(b).add(c);
        T.ok(MacroGraph.findCycle(a, lib) == null, "no cycle");
        T.ok(MacroGraph.wouldCreateCycle(c, a.id, lib), "C -> A would loop");
        T.ok(MacroGraph.wouldCreateCycle(a, a.id, lib), "self reference");
        T.ok(!MacroGraph.wouldCreateCycle(a, c.id, lib), "A -> C is fine");
        c.actions.add(Action.macro(a.id, 1));
        T.eq(Arrays.asList("A", "B", "C", "A"), MacroGraph.findCycle(a, lib), "cycle path");
    }

    static void testEstimateDuration() {
        Macro child = new Macro("child");
        Action t = Action.tap(1, 1); t.delay = 100; // 100 + 50
        child.actions.add(t);
        Macro m = new Macro("m");
        Action w = Action.waitFor(1000); w.delay = 0;
        m.actions.add(w);
        Action call = Action.macro(child.id, 3); call.interval = 10; call.delay = 5;
        m.actions.add(call);
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(child).add(m);
        T.eq(1000 + 5 + 150 * 3 + 10 * 2, MacroGraph.estimateDurationMs(m, lib), "estimate");
        Macro x = new Macro("x");
        x.actions.add(Action.macro("nope", 1));
        T.eq(1, MacroGraph.missingReferences(x, lib).size(), "missing ref");
    }

    static void testRescaleToScreen() {
        Macro m = sampleMacro(); // 1080x2400
        T.ok(MacroTransform.rescaleToScreen(m, 540, 1200), "changed");
        T.near(50.125, m.actions.get(0).x, 1e-3, "tap x halved");
        T.near(15, m.actions.get(3).x2, 1e-3, "swipe end halved");
        T.near(0.5, m.actions.get(4).strokes.get(0).x(0), 1e-3, "stroke halved");
        T.eq(540, m.screenWidth, "size updated");
        T.ok(!MacroTransform.rescaleToScreen(m, 540, 1200), "no-op second time");
        Macro fresh = new Macro("f");
        T.ok(!MacroTransform.rescaleToScreen(fresh, 100, 200) && fresh.screenWidth == 100, "unknown size adopts current");
    }

    static void testKindsAndManualTiming() {
        Macro m = sampleMacro();
        m.kind = Macro.KIND_RECORD;
        T.eq(Macro.KIND_RECORD, MacroCodec.fromJson(MacroCodec.toJson(m)).kind, "kind round trip");
        // Old files without "kind" get one inferred from their content.
        String oldManual = "{\"name\":\"a\",\"actions\":[{\"type\":\"TAP\",\"x\":1,\"y\":1}]}";
        String oldRecord = "{\"name\":\"b\",\"actions\":[{\"type\":\"GESTURE\",\"strokes\":[[1,1,0,2,2,50]]}]}";
        String oldCombo = "{\"name\":\"c\",\"kind\":\"weird\",\"actions\":[{\"type\":\"MACRO\",\"macroId\":\"x\"}]}";
        T.eq(Macro.KIND_MANUAL, MacroCodec.fromJson(oldManual).kind, "infer manual");
        T.eq(Macro.KIND_RECORD, MacroCodec.fromJson(oldRecord).kind, "infer record");
        T.eq(Macro.KIND_COMBO, MacroCodec.fromJson(oldCombo).kind, "invalid kind -> inferred combo");

        Macro man = new Macro("manual");
        man.actions.add(Action.tap(1, 1));
        man.actions.add(Action.longPress(1, 1, 900));
        man.actions.add(Action.swipe(1, 1, 5, 5, 300));
        man.actions.add(Action.waitFor(5000));
        MacroTransform.applyManualTiming(man, 250, 80, 400);
        T.eq(250L, man.actions.get(0).delay, "tap interval");
        T.eq(80L, man.actions.get(0).duration, "tap duration");
        T.eq(900L, man.actions.get(1).duration, "long press keeps its press time");
        T.eq(400L, man.actions.get(2).duration, "swipe duration");
        T.eq(5000L, man.actions.get(3).duration, "wait untouched");
        Macro back = MacroCodec.fromJson(MacroCodec.toJson(man));
        T.eq(250L, back.settings.tapInterval, "interval saved");
        T.eq(80L, back.settings.tapDuration, "tap duration saved");
        T.eq(400L, back.settings.swipeDuration, "swipe duration saved");
        T.eq(Macro.KIND_MANUAL, man.duplicate("d").kind, "duplicate keeps kind");
    }

    static void testBackupRestoreKeepsIdsAndNoDuplicates() {
        Macro child = new Macro("child");
        child.actions.add(Action.tap(1, 1));
        Macro parent = new Macro("parent");
        parent.kind = Macro.KIND_COMBO;
        parent.actions.add(Action.macro(child.id, 2));
        String backup = MacroCodec.exportAll(Arrays.asList(child, parent));
        T.ok(MacroCodec.isBackup(backup), "recognised as backup");
        T.ok(!MacroCodec.isBackup(MacroCodec.toJson(child)), "single file is not a backup");
        T.ok(!MacroCodec.isBackup("not json"), "garbage is not a backup");
        // Restoring on a phone that still has the same items: same ids (they get replaced, not duplicated).
        List<Macro> restored = MacroCodec.importBundle(backup, new HashSet<String>(Arrays.asList(child.id, parent.id)));
        T.eq(2, restored.size(), "both restored");
        T.ok(restored.get(0).id.equals(child.id) && restored.get(1).id.equals(parent.id), "ids kept");
        T.eq(child.id, restored.get(1).actions.get(0).macroId, "references intact");
        T.eq(Macro.KIND_COMBO, restored.get(1).kind, "kind kept");
    }

    /** Files written by version 1.0 (before kinds, manual timings and colour conditions) must still load. */
    static void testReadsVersion10Files() {
        String v10 = "{\n  \"version\": 1,\n  \"id\": \"0b5f9c1e-1111-4a4a-9c9c-123456789abc\",\n"
                + "  \"name\": \"Joc vechi\",\n  \"createdAt\": 1763680000000,\n  \"updatedAt\": 1763680000000,\n"
                + "  \"screenWidth\": 1080,\n  \"screenHeight\": 2340,\n"
                + "  \"settings\": {\"loops\": 0, \"loopDelay\": 500, \"speed\": 2.0, \"jitterPx\": 0, \"jitterTimePct\": 0,"
                + " \"stopAfterMs\": 0, \"scaleToScreen\": true, \"safeMode\": false, \"recordVideo\": false, \"lockToPackage\": \"\"},\n"
                + "  \"actions\": [\n"
                + "    {\"type\": \"TAP\", \"delay\": 500, \"duration\": 50, \"x\": 540.0, \"y\": 1200.0, \"repeat\": 1, \"interval\": 100, \"enabled\": true},\n"
                + "    {\"type\": \"SWIPE\", \"delay\": 500, \"duration\": 300, \"x\": 540.0, \"y\": 1500.0, \"x2\": 540.0, \"y2\": 800.0, \"repeat\": 1, \"interval\": 100, \"enabled\": true}\n"
                + "  ]\n}";
        Macro m = MacroCodec.fromJson(v10);
        T.eq("0b5f9c1e-1111-4a4a-9c9c-123456789abc", m.id, "id kept");
        T.eq("Joc vechi", m.name, "name");
        T.eq(Macro.KIND_MANUAL, m.kind, "kind inferred");
        T.eq(2, m.actions.size(), "actions");
        T.near(2.0, m.settings.speed, 1e-9, "speed");
        T.eq(0, m.settings.loops, "infinite loops kept");
        T.eq("", m.settings.launchPackage, "new setting defaults");
        T.eq(200L, m.settings.tapInterval, "manual timing defaults");
        // and it is written back in the current format without losing anything
        Macro again = MacroCodec.fromJson(MacroCodec.toJson(m));
        T.eq(m.actions.get(1).y2, again.actions.get(1).y2, "swipe end kept");
    }

    static void testFormatter() {
        Action t = Action.tap(10, 20);
        t.repeat = 3; t.delay = 1500;
        T.eq("Atingere (10, 20) ×3 · după 1.5 s", ActionFormatter.describe(t, null), "describe tap");
        T.eq("2 min 5 s", ActionFormatter.ms(125000), "minutes");
        T.eq("Acțiune sistem: Înapoi", ActionFormatter.describe(Action.global(GlobalActions.BACK), null), "global");
    }
}
