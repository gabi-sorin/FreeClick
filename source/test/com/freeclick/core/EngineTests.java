package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** PlaybackEngine tests on a virtual clock. */
public final class EngineTests {

    static final class Rec implements PlaybackEngine.Listener {
        final List<String> finished = new ArrayList<String>();
        final List<PlaybackEngine.State> states = new ArrayList<PlaybackEngine.State>();
        final List<String> progress = new ArrayList<String>();
        public void onStateChanged(PlaybackEngine.State s) { states.add(s); }
        public void onProgress(int loop, int total, int step, int steps, String name, Action a) {
            progress.add(loop + "/" + total + ":" + step + "/" + steps + ":" + name);
        }
        public void onFinished(String reason) { finished.add(reason); }
    }

    static Macro threeTaps() {
        Macro m = new Macro("m");
        m.screenWidth = 1080; m.screenHeight = 2400;
        for (int i = 0; i < 3; i++) {
            Action t = Action.tap(100 * (i + 1), 100);
            t.delay = 1000;
            m.actions.add(t);
        }
        m.settings.loops = 1;
        return m;
    }

    static List<Long> gestureTimes(FakeHost h) {
        List<Long> out = new ArrayList<Long>();
        for (FakeHost.Event e : h.gestures()) out.add(e.time - 1000);
        return out;
    }

    static void testPlaysInOrderWithDelays() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        T.ok(e.start(threeTaps(), null), "started");
        h.runAll();
        // delay 1000, tap 50, delay 1000, tap 50, delay 1000
        T.eq("[1000, 2050, 3100]", gestureTimes(h).toString(), "timing at 1x");
        T.eq("[done]", r.finished.toString(), "finished done");
        T.eq(PlaybackEngine.State.FINISHED, e.state(), "state");
        T.near(200, h.gestures().get(1).plan.strokes.get(0).points.get(0)[0], 1e-3, "second tap x");
        T.eq("1/1:3/3:m", r.progress.get(2), "progress");
    }

    static void testSpeedScalesDelays() {
        FakeHost h = new FakeHost();
        Macro m = threeTaps(); m.settings.speed = 2.0;
        new PlaybackEngine(h, null).start(m, null);
        h.runAll();
        T.eq("[500, 1050, 1600]", gestureTimes(h).toString(), "2x: delays halved, tap press unchanged");
    }

    static void testLoopsAndLoopDelay() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro m = new Macro("m");
        Action t = Action.tap(1, 1); t.delay = 0; m.actions.add(t);
        m.settings.loops = 3; m.settings.loopDelay = 200;
        new PlaybackEngine(h, r).start(m, null);
        h.runAll();
        T.eq("[0, 250, 500]", gestureTimes(h).toString(), "3 loops with 200ms between");
        T.eq("3/3:1/1:m", r.progress.get(2), "loop counter");
    }

    static void testInfiniteLoopWithTimeLimit() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro m = new Macro("m");
        Action t = Action.tap(1, 1); t.delay = 50; m.actions.add(t);
        m.settings.loops = 0; m.settings.loopDelay = 0; m.settings.stopAfterMs = 1000;
        new PlaybackEngine(h, r).start(m, null);
        h.runAll();
        T.eq("[time-limit]", r.finished.toString(), "stopped by time limit");
        T.eq(10, h.gestures().size(), "1000ms / 100ms per tap");
    }

    static void testRepeatAndInterval() {
        FakeHost h = new FakeHost();
        Macro m = new Macro("m");
        Action t = Action.tap(1, 1); t.delay = 100; t.repeat = 3; t.interval = 30;
        m.actions.add(t);
        new PlaybackEngine(h, null).start(m, null);
        h.runAll();
        T.eq("[100, 180, 260]", gestureTimes(h).toString(), "first uses delay, repeats use interval");
    }

    static void testPauseResumeKeepsRemainingDelay() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(threeTaps(), null);
        h.runUntil(1000 + 400);     // 400ms into the first 1000ms delay
        e.pause();
        T.eq(PlaybackEngine.State.PAUSED, e.state(), "paused");
        h.runUntil(1000 + 10000);   // long pause: nothing happens
        T.eq(0, h.gestures().size(), "no gesture while paused");
        e.resume();
        h.runAll();
        // resumed at 10000 with 600ms remaining -> first tap at 10600
        T.eq(10600L, (long) gestureTimes(h).get(0), "remaining delay kept");
        T.ok(e.elapsed() < 4000, "elapsed excludes pause, was " + e.elapsed());
    }

    static void testPauseDuringGestureThenResume() {
        FakeHost h = new FakeHost();
        PlaybackEngine e = new PlaybackEngine(h, null);
        e.start(threeTaps(), null);
        h.runUntil(1000 + 1010);    // tap 1 in flight (1000..1050)
        e.pause();
        h.runUntil(1000 + 5000);    // callback arrives while paused
        T.eq(1, h.gestures().size(), "no new gesture while paused");
        e.resume();
        h.runAll();
        T.eq(3, h.gestures().size(), "continues after resume");
        T.eq(5000L + 1000, (long) gestureTimes(h).get(1), "next delay starts at resume");
    }

    static void testLiveSpeedChangeRescalesCurrentWait() {
        FakeHost h = new FakeHost();
        PlaybackEngine e = new PlaybackEngine(h, null);
        e.start(threeTaps(), null);
        h.runUntil(1000 + 500);     // 500 of 1000 elapsed
        e.setSpeed(5.0);            // remaining 500 -> 100
        h.runUntil(1000 + 600);
        T.eq(1, h.gestures().size(), "first tap fired early");
        T.eq(600L, (long) gestureTimes(h).get(0), "rescaled remaining");
        T.near(5.0, e.speed(), 1e-9, "speed");
        e.setSpeed(1000);           // clamped
        T.near(Macro.MAX_SPEED, e.speed(), 1e-9, "clamped");
    }

    static void testStopCancelsEverything() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(threeTaps(), null);
        h.runUntil(1000 + 1020);    // first gesture in flight
        e.stop();
        h.runAll();
        T.eq(1, h.gestures().size(), "nothing after stop (late callback ignored)");
        T.eq("[stopped]", r.finished.toString(), "reason");
        e.stop();
        T.eq(1, r.finished.size(), "second stop is a no-op");
    }

    static void testSubMacrosCombination() {
        FakeHost h = new FakeHost();
        Macro child = new Macro("child");
        Action a = Action.tap(1, 1); a.delay = 10; child.actions.add(a);
        Action b = Action.tap(2, 2); b.delay = 10; child.actions.add(b);
        child.settings.loops = 99; // ignored when used as a sub-macro
        Macro parent = new Macro("parent");
        Action call = Action.macro(child.id, 2); call.delay = 100; parent.actions.add(call);
        Action end = Action.global(GlobalActions.HOME); end.delay = 0; parent.actions.add(end);
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(child).add(parent);
        Rec r = new Rec();
        new PlaybackEngine(h, r).start(parent, lib);
        h.runAll();
        T.eq(4, h.gestures().size(), "child played twice");
        T.eq(110L, (long) gestureTimes(h).get(0), "sub-macro delay + first action delay");
        T.eq("global", h.events.get(4).kind, "then the global action");
        T.eq(GlobalActions.HOME, h.events.get(4).global, "home");
        T.eq("[done]", r.finished.toString(), "done");
        T.ok(r.progress.get(0).endsWith(":child"), "progress names the sub-macro");
    }

    static void testCycleRefused() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro a = new Macro("A"), b = new Macro("B");
        a.actions.add(Action.macro(b.id, 1));
        b.actions.add(Action.macro(a.id, 1));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(a).add(b);
        T.ok(!new PlaybackEngine(h, r).start(a, lib), "refused");
        T.eq("[cycle]", r.finished.toString(), "reason cycle");
        T.eq(0, h.pendingTimers(), "nothing scheduled");
    }

    static void testEmptyMacroDoesNotSpin() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro m = new Macro("empty");
        m.settings.loops = 0; // infinite
        Action disabled = Action.tap(1, 1); disabled.enabled = false; m.actions.add(disabled);
        m.actions.add(Action.macro("missing", 3));
        new PlaybackEngine(h, r).start(m, new MacroLibrary.InMemory());
        h.runAll(100);
        T.eq("[empty]", r.finished.toString(), "finishes as empty instead of looping forever");
        Rec r2 = new Rec();
        new PlaybackEngine(new FakeHost(), r2).start(new Macro("none"), null);
        T.eq("[empty]", r2.finished.toString(), "no actions");
    }

    static void testWaitAndGlobalTiming() {
        FakeHost h = new FakeHost();
        Macro m = new Macro("m");
        Action w = Action.waitFor(2000); w.delay = 0; m.actions.add(w);
        Action g = Action.global(GlobalActions.BACK); g.delay = 0; m.actions.add(g);
        Action t = Action.tap(1, 1); t.delay = 0; m.actions.add(t);
        m.settings.speed = 4;
        new PlaybackEngine(h, null).start(m, null);
        h.runAll();
        T.eq(1500L, h.events.get(0).time, "wait 2000 at 4x = 500ms");
        T.eq(1500L, h.events.get(1).time, "tap right after back");
    }

    static void testFailedGesturesCountedAndPlaybackContinues() {
        FakeHost h = new FakeHost(); h.failGestures = true;
        PlaybackEngine e = new PlaybackEngine(h, null);
        e.start(threeTaps(), null);
        h.runAll();
        T.eq(3, h.gestures().size(), "all attempted");
        T.eq(3, e.failedGestures(), "failures counted");
        T.eq(3, e.executedSteps(), "steps");
    }

    static void testTimeJitterBoundsAndScaling() {
        FakeHost h = new FakeHost();
        Macro m = new Macro("m");
        Action t = Action.tap(1, 1); t.delay = 1000; m.actions.add(t);
        m.settings.loops = 200; m.settings.loopDelay = 0; m.settings.jitterTimePct = 20;
        m.screenWidth = 540; m.screenHeight = 1200; // half of the fake screen
        PlaybackEngine e = new PlaybackEngine(h, null);
        e.setRandom(new Random(7));
        e.start(m, null);
        h.runAll();
        List<Long> times = gestureTimes(h);
        long prevEnd = 0;
        boolean varied = false;
        for (int i = 0; i < times.size(); i++) {
            long gap = times.get(i) - prevEnd;
            T.ok(gap >= 800 && gap <= 1200, "gap within +/-20%: " + gap);
            if (gap != 1000) varied = true;
            prevEnd = times.get(i) + 50;
        }
        T.ok(varied, "jitter actually varies timing");
        T.near(2, h.gestures().get(0).plan.strokes.get(0).points.get(0)[0], 1e-3, "coordinates scaled x2");
    }

    static void testRestartWhileRunning() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(threeTaps(), null);
        h.runUntil(1000 + 1020);
        e.start(threeTaps(), null); // restart
        h.runAll();
        T.eq(4, h.gestures().size(), "1 from the first run + 3 from the restart");
        T.eq("[done]", r.finished.toString(), "only the second run reports");
    }

    /** Record taps with known pauses, play them back: each touch must start at its recorded time. */
    static Macro recordedTaps(long[] downs, long press) {
        GestureRecorder r = new GestureRecorder(new GestureRecorder.Config());
        r.start(0);
        for (long d : downs) { r.pointerDown(0, 100, 100, d); r.pointerUp(0, 100, 100, d + press); }
        Macro m = new Macro("rec");
        m.actions.addAll(r.stop());
        return m;
    }

    static void testRecordingReplaysOnItsOriginalTimeline() {
        long[] downs = {3000, 3500, 3700, 6700, 6900, 12000};
        for (long latency : new long[]{0, 15, 40, 120}) {
            FakeHost h = new FakeHost();
            h.callbackLatencyMs = latency;
            new PlaybackEngine(h, null).start(recordedTaps(downs, 80), null);
            h.runAll();
            T.eq(downs.length, h.gestures().size(), "all taps played");
            for (int i = 0; i < downs.length; i++) {
                T.eq(downs[i], h.gestures().get(i).time - 1000,
                        "tap " + (i + 1) + " starts at its recorded time with " + latency + " ms phone latency");
            }
        }
    }

    static void testTimelineAtDoubleSpeed() {
        long[] downs = {2000, 3000, 5000};
        FakeHost h = new FakeHost();
        h.callbackLatencyMs = 30;
        Macro m = recordedTaps(downs, 80);
        m.settings.speed = 2;
        new PlaybackEngine(h, null).start(m, null);
        h.runAll();
        // pauses halve, presses keep their length: 1000, 1000+80+460=1540, 1540+80+960=2580
        T.eq("[1000, 1540, 2580]", gestureTimes(h).toString(), "2x timeline with latency compensated");
    }

    static void testLatencyCatchUpIsBoundedAndResetByPause() {
        // gaps (20 ms) smaller than the latency (40 ms): can't start before the previous touch ends,
        // so it runs as tight as possible but never piles up more than the cap.
        long[] downs = new long[50];
        for (int i = 0; i < downs.length; i++) downs[i] = 1000 + i * 100;
        FakeHost h = new FakeHost();
        h.callbackLatencyMs = 40;
        PlaybackEngine e = new PlaybackEngine(h, null);
        e.start(recordedTaps(downs, 80), null);
        h.runAll();
        long last = h.gestures().get(downs.length - 1).time - 1000;
        T.ok(last >= downs[downs.length - 1] && last <= downs[downs.length - 1] + 49 * 20 + 1,
                "drift limited to the unavoidable part: " + last);

        FakeHost h2 = new FakeHost();
        h2.callbackLatencyMs = 40;
        PlaybackEngine e2 = new PlaybackEngine(h2, null);
        e2.start(recordedTaps(new long[]{1000, 2000}, 80), null);
        h2.runUntil(1000 + 1100); // first tap done (callback at 1120)
        h2.runUntil(1000 + 1130);
        e2.pause();
        h2.runUntil(1000 + 5000);
        e2.resume();
        h2.runAll();
        // after a pause the remaining wait restarts from the resume, no catch-up applied
        T.eq(1000L + 5000 + (2000 - 1080 - 50), h2.gestures().get(1).time, "resume keeps the remaining pause");
    }

    /** Regression: a refused start after an earlier run must still report FINISHED (UI would stay "playing"). */
    static void testRefusedStartAfterAnEarlierRunNotifiesFinished() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(threeTaps(), null);
        h.runAll();
        T.eq(PlaybackEngine.State.FINISHED, e.state(), "first run finished");
        int finishedEvents = 0;
        for (PlaybackEngine.State s : r.states) if (s == PlaybackEngine.State.FINISHED) finishedEvents++;
        Macro a = new Macro("A"), b = new Macro("B");
        a.actions.add(Action.macro(b.id, 1));
        b.actions.add(Action.macro(a.id, 1));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(a).add(b);
        T.ok(!e.start(a, lib), "cyclic macro refused");
        int after = 0;
        for (PlaybackEngine.State s : r.states) if (s == PlaybackEngine.State.FINISHED) after++;
        T.eq(finishedEvents + 1, after, "FINISHED reported again so the panel leaves 'playing'");
        T.eq("cycle", r.finished.get(r.finished.size() - 1), "reason");
    }

    static void testScriptStepWaitsForTheScript() {
        FakeHost h = new FakeHost();
        h.scriptMs = 2000;
        Macro code = new Macro("cod");
        code.kind = Macro.KIND_SCRIPT;
        code.script = "tap(1, 1);";
        code.actions.add(Action.tap(9, 9)); // its 'pasi' are NOT run by the engine
        Macro m = new Macro("m");
        Action t1 = Action.tap(1, 1); t1.delay = 0; m.actions.add(t1);
        Action call = Action.macro(code.id, 2); call.delay = 100; m.actions.add(call);
        Action t2 = Action.tap(2, 2); t2.delay = 0; m.actions.add(t2);
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(code).add(m);
        Rec r = new Rec();
        new PlaybackEngine(h, r).start(m, lib);
        h.runAll();
        T.eq("[cod x2]", h.scripts.toString(), "the script ran (twice)");
        T.eq(2, h.gestures().size(), "only the two taps of m");
        long gap = gestureTimes(h).get(1) - gestureTimes(h).get(0);
        T.ok(gap >= 100 + 4000, "the second tap waits for the script: " + gap);
        T.eq("[done]", r.finished.toString(), "done");
        // stopped while the script runs: the script is stopped too, nothing continues
        h = new FakeHost();
        h.scriptMs = 5000;
        r = new Rec();
        final PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(m, lib);
        h.runUntil(h.now + 1000);
        e.stop();
        h.runAll();
        T.ok(h.scripts.contains("stop"), "script stopped: " + h.scripts);
        T.eq(1, h.gestures().size(), "no tap after the stop");
    }

    static void testZoomTargetFollowsItsCentre() {
        Action z = Action.zoom(500, 800, 400, 300, true);
        T.ok(z.isZoom() && z.strokes.size() == 2, "two fingers");
        Macro m = new Macro("z");
        m.actions.add(z);
        Macro back = MacroCodec.fromJson(MacroCodec.toJson(m));
        Action b = back.actions.get(0);
        T.eq(400, b.zoomSpan, "saved and read back");
        b.x = 100; // the marker was dragged
        GesturePlanner.Limits lim = new GesturePlanner.Limits();
        lim.screenWidth = 1080; lim.screenHeight = 2400; lim.maxStrokes = 10;
        GesturePlan p = GesturePlanner.plan(b, 1.0, 0, new java.util.Random(1), CoordinateScaler.IDENTITY, lim);
        T.eq(2, p.strokes.size(), "two strokes");
        float l0 = p.strokes.get(0).points.get(0)[0], l1 = p.strokes.get(0).points.get(p.strokes.get(0).points.size() - 1)[0];
        float r0 = p.strokes.get(1).points.get(0)[0], r1 = p.strokes.get(1).points.get(p.strokes.get(1).points.size() - 1)[0];
        T.ok(l0 < 100 && r0 > 100, "around the new centre: " + l0 + " " + r0);
        T.ok(r1 - l1 > r0 - l0, "apart");
        Action out = Action.zoom(500, 800, 400, 300, false);
        out.rebuildZoom();
        T.ok(out.strokes.get(1).x(1) - out.strokes.get(0).x(1) < out.strokes.get(1).x(0) - out.strokes.get(0).x(0), "together");
        T.ok(ActionFormatter.describe(out, null).startsWith("Zoom out"), ActionFormatter.describe(out, null));
    }
}
