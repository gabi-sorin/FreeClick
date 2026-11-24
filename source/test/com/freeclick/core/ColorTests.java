package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;

/** Colour condition ("wait for colour") tests. */
public final class ColorTests {
    static final int RED = 0xE53935, WHITE = 0xFFFFFF;

    static final class Rec implements PlaybackEngine.Listener {
        final List<String> finished = new ArrayList<String>();
        public void onStateChanged(PlaybackEngine.State s) { }
        public void onProgress(int l, int t, int s, int n, String name, Action a) { }
        public void onFinished(String r) { finished.add(r); }
    }

    static Macro waitThenTap(Action cond) {
        Macro m = new Macro("c");
        cond.delay = 0;
        m.actions.add(cond);
        Action t = Action.tap(1, 1); t.delay = 0;
        m.actions.add(t);
        return m;
    }

    static void testColorHelpers() {
        T.eq("#E53935", ColorMatch.toHex(RED), "hex");
        T.eq(RED, ColorMatch.parseHex("#e53935"), "parse");
        T.eq(0xFF0000, ColorMatch.parseHex("F00"), "short form");
        T.eq(0x123456, ColorMatch.parseHex("#FF123456"), "ARGB drops alpha");
        T.eq(0, ColorMatch.parseHex("nonsense"), "invalid -> black");
        T.eq(10, ColorMatch.distance(0x000000, 0x0A0505), "max channel diff");
        T.ok(ColorMatch.matches(0xE0393A, RED, 24), "close colours match");
        T.ok(!ColorMatch.matches(WHITE, RED, 24), "different colours");
    }

    static void testWaitsUntilColourAppears() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        h.colorTimeline.put(0L, WHITE);
        h.colorTimeline.put(3000L, RED);
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(waitThenTap(Action.waitColor(10, 20, RED, 0)), null);
        h.runAll(10000);
        T.eq(1, h.gestures().size(), "tap after colour");
        long t = h.gestures().get(0).time;
        T.ok(t >= 3000 && t < 3000 + PlaybackEngine.POLL_MS + 1, "tap shortly after the colour appeared: " + t);
        T.eq(1, e.conditionsMet(), "met");
        T.eq("10,20", h.samples.get(0), "sampled the right pixel");
        T.eq("[done]", r.finished.toString(), "done");
    }

    static void testWaitsUntilColourDisappears() {
        FakeHost h = new FakeHost();
        h.colorTimeline.put(0L, RED);
        h.colorTimeline.put(2000L, WHITE);
        Action c = Action.waitColor(1, 1, RED, 0);
        c.invert = true;
        new PlaybackEngine(h, null).start(waitThenTap(c), null);
        h.runAll(10000);
        T.ok(h.gestures().get(0).time >= 2000, "tap only after red is gone");
    }

    static void testTimeoutContinues() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        h.colorTimeline.put(0L, WHITE);
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(waitThenTap(Action.waitColor(1, 1, RED, 1000)), null);
        h.runAll(10000);
        T.eq(1, h.gestures().size(), "continued after timeout");
        long t = h.gestures().get(0).time - 1000;
        T.ok(t >= 1000 && t <= 1000 + PlaybackEngine.POLL_MS, "about 1 s later: " + t);
        T.eq(1, e.conditionsTimedOut(), "timed out counted");
        T.eq("[done]", r.finished.toString(), "done");
    }

    static void testTimeoutStops() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        h.colorTimeline.put(0L, WHITE);
        Action c = Action.waitColor(1, 1, RED, 500);
        c.stopOnTimeout = true;
        new PlaybackEngine(h, r).start(waitThenTap(c), null);
        h.runAll(10000);
        T.eq(0, h.gestures().size(), "no tap");
        T.eq("[condition-timeout]", r.finished.toString(), "reason");
    }

    static void testNoCaptureGivesUp() {
        FakeHost h = new FakeHost(); Rec r = new Rec(); // empty timeline = capture unavailable
        new PlaybackEngine(h, r).start(waitThenTap(Action.waitColor(1, 1, RED, 0)), null);
        h.runAll(10000);
        T.eq("[no-capture]", r.finished.toString(), "reason");
        T.ok(h.now - 1000 >= PlaybackEngine.CAPTURE_GRACE_MS, "waited the grace period");
    }

    static void testToleranceAndSpeedIndependence() {
        FakeHost h = new FakeHost();
        h.colorTimeline.put(0L, WHITE);
        h.colorTimeline.put(1500L, 0xEE3F3A); // close to RED (distance 9)
        Macro m = waitThenTap(Action.waitColor(1, 1, RED, 0));
        m.settings.speed = 10; // speed must not change real-time polling of the screen
        new PlaybackEngine(h, null).start(m, null);
        h.runAll(10000);
        long t = h.gestures().get(0).time;
        T.ok(t >= 1500 && t <= 1500 + PlaybackEngine.POLL_MS, "matched within tolerance at real time: " + t);
    }

    static void testPauseDoesNotEatTimeout() {
        FakeHost h = new FakeHost();
        h.colorTimeline.put(0L, WHITE);
        PlaybackEngine e = new PlaybackEngine(h, null);
        e.start(waitThenTap(Action.waitColor(1, 1, RED, 2000)), null);
        h.runUntil(1000 + 500);
        e.pause();
        h.runUntil(1000 + 60000);
        T.eq(0, h.gestures().size(), "still waiting while paused");
        e.resume();
        h.runAll(10000);
        long afterResume = h.gestures().get(0).time - 61000;
        T.ok(afterResume >= 1400 && afterResume <= 1500 + PlaybackEngine.POLL_MS,
                "remaining ~1.5 s of timeout after resume, got " + afterResume);
    }

    static void testCoordinatesScaledAndCodec() {
        FakeHost h = new FakeHost();
        h.colorTimeline.put(0L, RED);
        Macro m = waitThenTap(Action.waitColor(100, 200, RED, 0));
        m.screenWidth = 540; m.screenHeight = 1200;
        new PlaybackEngine(h, null).start(m, null);
        h.runAll(1000);
        T.eq("200,400", h.samples.get(0), "scaled pixel");

        Action c = Action.waitColor(5, 6, RED, 700);
        c.tolerance = 40; c.invert = true; c.stopOnTimeout = true;
        Macro mm = new Macro("x");
        mm.actions.add(c);
        Action r = MacroCodec.fromJson(MacroCodec.toJson(mm)).actions.get(0);
        T.eq(Action.Type.WAIT_COLOR, r.type, "type");
        T.eq(RED, r.color, "colour");
        T.eq(40, r.tolerance, "tolerance");
        T.ok(r.invert && r.stopOnTimeout, "flags");
        T.eq(700L, r.duration, "timeout");
    }

    static void testUsesScreenCaptureThroughSubMacro() {
        Macro child = new Macro("child");
        child.actions.add(Action.waitColor(1, 1, RED, 0));
        Macro parent = new Macro("parent");
        parent.actions.add(Action.macro(child.id, 1));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(child).add(parent);
        T.ok(MacroGraph.usesScreenCapture(parent, lib), "detected via sub-macro");
        child.actions.get(0).enabled = false;
        T.ok(!MacroGraph.usesScreenCapture(parent, lib), "disabled condition ignored");
    }
}
