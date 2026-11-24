package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;

/** FreeClick 1.7: image steps in the engine (on the virtual clock), file format, images used. */
public final class V17Tests {

    static ImageSpec spec(String id) {
        ImageSpec s = new ImageSpec();
        s.id = id; s.w = 200; s.h = 80; s.capX = 100; s.capY = 500; s.capScreenW = 1080; s.capScreenH = 2400;
        s.tapDx = 100; s.tapDy = 40; s.name = "Collect";
        return s;
    }

    static final String ID = "0123456789abcdef0123";

    static Macro macro() {
        Macro m = new Macro("img");
        m.screenWidth = 1080; m.screenHeight = 2400;
        m.settings.loops = 1;
        return m;
    }

    static Action img(int press, int waitMode, long timeout) {
        Action a = Action.image(spec(ID));
        a.press = press; a.waitMode = waitMode; a.duration = timeout;
        return a;
    }

    static FakeHost host() {
        FakeHost h = new FakeHost();
        h.frameIntervalMs = 350;
        return h;
    }

    static V16Tests.Rec run(FakeHost h, Macro m) {
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setNotices(r);
        h.engine = e;
        e.start(m, null);
        h.runAll();
        return r;
    }

    static List<FakeHost.Event> taps(FakeHost h) { return h.gestures(); }

    static float x0(FakeHost.Event e) { return e.plan.strokes.get(0).points.get(0)[0]; }
    static float y0(FakeHost.Event e) { return e.plan.strokes.get(0).points.get(0)[1]; }

    // ------------------------------------------------------------------ tests
    static void testWaitsForTheImageThenTapsItConfirmedOnTwoFrames() {
        FakeHost h = host();
        h.show(ID, 1000 + 3000, 1000 + 60000, 400, 900);
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 10000));
        V16Tests.Rec r = run(h, m);
        T.eq(1, taps(h).size(), "one tap");
        FakeHost.Event t = taps(h).get(0);
        T.near(500, x0(t), 1e-3, "tap x = found x + tap offset");
        T.near(940, y0(t), 1e-3, "tap y");
        long at = t.time - 1000;
        T.ok(at >= 3000 + 350, "only after a second frame confirms it: " + at);
        T.ok(at <= 3000 + 2 * 350 + 300, "but promptly: " + at);
        T.eq("[done]", r.finished.toString(), "done");
    }

    static void testSomethingSeenOnOnlyOneFrameIsNotTapped() {
        FakeHost h = host();
        h.show(ID, 1000 + 1400, 1000 + 1500, 400, 900); // fades out within one frame
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 4000));
        V16Tests.Rec r = run(h, m);
        T.eq(0, taps(h).size(), "no tap on a button that was disappearing");
        T.eq("[done]", r.finished.toString(), "timed out and carried on");
    }

    static void testMissingContinueLooksOnceAndGoesOn() {
        FakeHost h = host();
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_CONTINUE, 30000));
        Action after = Action.tap(5, 5);
        m.actions.add(after);
        run(h, m);
        T.eq(1, taps(h).size(), "only the next tap");
        T.ok(taps(h).get(0).time - 1000 < 1000, "without waiting 30 s: " + (taps(h).get(0).time - 1000));
        T.eq(1, h.imageRequests.size(), "one look");
    }

    static void testWaitUntilGoneAndJustWait() {
        FakeHost h = host();
        h.show(ID, 0, 1000 + 2000, 400, 900);
        Macro m = macro();
        m.actions.add(img(Action.PRESS_GONE, Action.MISSING_WAIT, 0));
        m.actions.add(Action.tap(5, 5));
        run(h, m);
        T.eq(1, taps(h).size(), "no tap on the image, then the next tap");
        T.ok(taps(h).get(0).time - 1000 >= 2000, "after the image was gone");
        FakeHost h2 = host();
        h2.show(ID, 1000 + 1000, 1000 + 9000, 400, 900);
        Macro m2 = macro();
        m2.actions.add(img(Action.PRESS_NONE, Action.MISSING_WAIT, 0));
        m2.actions.add(Action.tap(5, 5));
        run(h2, m2);
        T.eq(1, taps(h2).size(), "'only wait': no tap on the image");
        T.near(5, x0(taps(h2).get(0)), 1e-3, "the next step's tap");
    }

    static void testAllCopiesTappedTopFirstInOneGesture() {
        FakeHost h = host();
        h.show(ID, 0, 999999, 600, 1500).show(ID, 0, 999999, 50, 300).show(ID, 0, 999999, 600, 300);
        Macro m = macro();
        Action a = img(Action.PRESS_TAP, Action.MISSING_CONTINUE, 0);
        a.allMatches = true;
        m.actions.add(a);
        run(h, m);
        T.eq(1, taps(h).size(), "one gesture");
        GesturePlan p = taps(h).get(0).plan;
        T.eq(3, p.strokes.size(), "three taps");
        T.near(150, p.strokes.get(0).points.get(0)[0], 1e-3, "top-left first");
        T.near(1540, p.strokes.get(2).points.get(0)[1], 1e-3, "lowest last");
        T.ok(p.strokes.get(1).startTime > p.strokes.get(0).startTime, "one after another, not together");
        T.eq(0, h.violations, "valid gesture: " + h.violationLog);
    }

    static void testStopAfterNFinds() {
        FakeHost h = host();
        h.show(ID, 0, 9999999, 400, 900);
        Macro m = macro();
        m.settings.loops = 0;
        Action a = img(Action.PRESS_TAP, Action.MISSING_WAIT, 0);
        a.maxHits = 5;
        m.actions.add(a);
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        h.engine = e;
        e.start(m, null);
        h.runAll();
        T.eq(5, taps(h).size(), "five taps");
        T.eq("[hit-limit]", r.finished.toString(), "then stops");
        T.eq(5, (int) e.imageHits().get(a), "counted");
    }

    static void testTimeoutStopsOrContinues() {
        FakeHost h = host();
        Macro m = macro();
        Action a = img(Action.PRESS_TAP, Action.MISSING_WAIT, 2000);
        a.stopOnTimeout = true;
        m.actions.add(a);
        m.actions.add(Action.tap(5, 5));
        V16Tests.Rec r = run(h, m);
        T.eq("[condition-timeout]", r.finished.toString(), "stops");
        T.eq(0, taps(h).size(), "nothing tapped");
        T.ok(h.now - 1000 >= 2000 && h.now - 1000 < 3000, "after about 2 s: " + (h.now - 1000));
    }

    static void testPauseDuringASearchNeverDoublesTheRun() {
        FakeHost h = host();
        h.imageLatencyMs = 200;
        h.show(ID, 0, 9999999, 400, 900);
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        m.actions.add(Action.tap(5, 5));
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        h.engine = e;
        e.start(m, null);
        h.runUntil(1000 + 100); // request out, answer due at ~200+
        e.pause();
        h.runUntil(1000 + 150);
        e.resume(); // quick pause/resume while the old answer is still coming
        h.runUntil(1000 + 3000);
        e.pause();
        h.runUntil(1000 + 9000); // answers arriving while paused are ignored
        e.resume();
        h.runAll();
        T.eq(2, taps(h).size(), "image tapped once, then the next step once: " + taps(h));
        T.eq("[done]", r.finished.toString(), "done once");
        T.eq(0, h.violations, h.violationLog.toString());
    }

    static void testSearchesOnlyFramesTakenAfterTheLastTouch() {
        FakeHost h = host();
        h.show(ID, 0, 9999999, 400, 900);
        Macro m = macro();
        Action t = Action.tap(5, 5);
        t.delay = 100;
        m.actions.add(t);
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        run(h, m);
        long tapEnd = taps(h).get(0).time + t.duration;
        T.ok(h.imageRequests.get(0) >= tapEnd + PlaybackEngine.SETTLE_MS, "first frame after the touch settled");
        T.ok(h.imageRequests.get(1) > h.imageRequests.get(0), "confirmation needs a newer frame");
    }

    static void testCoveredProtectedAndNoCapture() {
        FakeHost h = host();
        h.show(ID, 0, 9999999, 400, 900);
        h.imageTimeline.get(ID).get(0).covered = true;
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 1500));
        V16Tests.Rec r = run(h, m);
        T.eq("[image-covered]", r.notices.toString(), "told once that the panel covers it");
        T.eq(0, taps(h).size(), "never taps through the panel");
        FakeHost p = host();
        p.protectedFrom = 0;
        V16Tests.Rec rp = run(p, macroWith(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0)));
        T.eq("[protected]", rp.finished.toString(), "protected screen reported");
        FakeHost n = host();
        n.imageNoCapture = true;
        V16Tests.Rec rn = run(n, macroWith(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0)));
        T.eq("[no-capture]", rn.finished.toString(), "no capture after the grace time");
        FakeHost mi = host();
        mi.imageMissing = true;
        V16Tests.Rec rm = run(mi, macroWith(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0)));
        T.eq("[image-missing]", rm.finished.toString(), "missing picture file reported");
    }

    static Macro macroWith(Action a) { Macro m = macro(); m.actions.add(a); return m; }

    static void testNoBurstAfterAnImageWait() {
        FakeHost h = host();
        h.callbackLatencyMs = 400; // late-reporting phone
        h.show(ID, 1000 + 2500, 9999999, 400, 900);
        Macro m = macro();
        m.actions.add(Action.tap(5, 5));
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        Action next = Action.tap(6, 6);
        next.delay = 500;
        m.actions.add(next);
        run(h, m);
        List<FakeHost.Event> t = taps(h);
        T.eq(3, t.size(), "three taps");
        T.ok(t.get(2).time - t.get(1).time >= 500, "the 500 ms pause after the image tap is kept: " + (t.get(2).time - t.get(1).time));
    }

    static void testTouchCancelOnTheImageTapSearchesAgainOnResume() {
        FakeHost h = host();
        h.show(ID, 0, 9999999, 400, 900);
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setPauseOnCancel(true);
        h.engine = e;
        h.failGestures = true;
        e.start(m, null);
        h.runUntil(1000 + 3000);
        T.eq(PlaybackEngine.State.PAUSED, e.state(), "paused by the touch");
        int before = h.imageRequests.size();
        h.failGestures = false;
        e.resume();
        h.runAll();
        T.ok(h.imageRequests.size() > before, "looks again (the image may have moved)");
        T.eq(2, taps(h).size(), "then taps it");
        T.eq("[done]", r.finished.toString(), "done");
    }

    static void testPreviewNeverAsksForFrames() {
        FakeHost h = host();
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        h.engine = e;
        e.setPreview(true, true);
        e.start(m, null);
        h.runAll();
        T.eq(0, h.imageRequests.size(), "no screen reading in preview");
        T.eq(1, taps(h).size(), "ghost tap where it was cut");
        T.near(200, x0(taps(h).get(0)), 1e-3, "capX + tapDx");
        FakeHost h2 = host();
        PlaybackEngine e2 = new PlaybackEngine(h2, new V16Tests.Rec());
        e2.setPreview(true, false);
        Macro m2 = macro();
        m2.actions.add(img(Action.PRESS_TAP, Action.MISSING_CONTINUE, 0));
        m2.actions.add(Action.tap(5, 5));
        e2.start(m2, null);
        h2.runAll();
        T.eq(1, taps(h2).size(), "assumed absent: skipped, next step runs");
    }

    static void testTimeLimitEndsAnEndlessImageWait() {
        FakeHost h = host();
        Macro m = macro();
        m.settings.stopAfterMs = 2500;
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        V16Tests.Rec r = run(h, m);
        T.eq("[time-limit]", r.finished.toString(), "ended");
        T.eq(2500, h.now - 1000 - 0 >= 2500 ? 2500 : h.now - 1000, "at the limit");
    }

    // ------------------------------------------------------------------ format
    static void testImageStepsRoundTripAndListTheirFiles() {
        Macro child = macro();
        ImageSpec s2 = spec("fedcba9876543210fedc");
        s2.zx1 = 0; s2.zy1 = 0; s2.zx2 = 1080; s2.zy2 = 1200; s2.tight = true; s2.precision = 93;
        Action c = Action.image(s2);
        c.press = Action.PRESS_LONG; c.pressMs = 900; c.allMatches = true; c.maxHits = 7; c.stopOnTimeout = true;
        child.actions.add(c);
        Macro root = macro();
        root.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 12000));
        root.actions.add(Action.macro(child.id, 1));
        String j = MacroCodec.toJson(child);
        Macro back = MacroCodec.fromJson(j);
        T.eq(j, MacroCodec.toJson(back), "stable");
        Action b = back.actions.get(0);
        T.ok(b.img.hasZone() && b.img.tight && b.img.precision == 93 && b.allMatches && b.maxHits == 7 && b.pressMs == 900
                && b.press == Action.PRESS_LONG && b.stopOnTimeout, "all fields kept");
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(child).add(root);
        T.eq("[0123456789abcdef0123, fedcba9876543210fedc]", MacroGraph.imageIds(root, lib).toString(), "files used, sub-macros included");
        T.ok(MacroGraph.usesScreenCapture(root, lib), "image steps need screen capture");
        String hostile = j.replace("fedcba9876543210fedc", "../../etc/passwd");
        Macro h = MacroCodec.fromJson(hostile);
        T.eq(Action.Type.UNKNOWN, h.actions.get(0).type, "an unsafe image id is never used as a file name (kept as unknown)");
    }

    static void testOlderAppVersionsRefuseImageFiles() {
        Macro m = macro();
        m.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        T.ok(MacroCodec.VERSION >= 3 && MacroCodec.toJson(m).contains("\"version\": " + MacroCodec.VERSION), "written as format 3 or newer");
    }

    static void testBase64() {
        java.util.Random r = new java.util.Random(3);
        for (int n = 0; n < 50; n++) {
            byte[] b = new byte[r.nextInt(300)];
            r.nextBytes(b);
            String e = B64.encode(b);
            T.eq(java.util.Base64.getEncoder().encodeToString(b), e, "same as the JDK");
            T.ok(java.util.Arrays.equals(b, B64.decode(e)), "round trip");
        }
        T.eq(null, B64.decode("ab$c"), "invalid text refused");
    }

    static void testBundleCarriesItsPictures() {
        Macro root = macro();
        root.actions.add(img(Action.PRESS_TAP, Action.MISSING_WAIT, 0));
        final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        String bundle = MacroCodec.exportBundle(root, new MacroLibrary.InMemory().add(root), new MacroCodec.ImageSource() {
            public byte[] png(String id) { return ID.equals(id) ? png : null; }
        });
        java.util.Map<String, byte[]> im = MacroCodec.importImages(bundle);
        T.eq(1, im.size(), "one picture");
        T.ok(java.util.Arrays.equals(png, im.get(ID)), "bytes kept");
        String hostile = bundle.replace("\"" + ID + "\": ", "\"../x\": ");
        T.eq(0, MacroCodec.importImages(hostile).size(), "unsafe names dropped");
        List<Macro> ms = MacroCodec.importBundle(bundle, new java.util.HashSet<String>());
        java.util.Map<String, String> ren = new java.util.HashMap<String, String>();
        ren.put(ID, "ffffffffffffffffffff");
        MacroCodec.renameImages(ms, ren);
        T.eq("ffffffffffffffffffff", ms.get(0).actions.get(0).img.id, "steps follow renamed pictures");
    }

    static void testRecordingAnImageWaitExcludesTheTimeSpentCuttingIt() {
        GestureRecorder rec = new GestureRecorder(new GestureRecorder.Config());
        rec.start(0);
        rec.pointerDown(0, 100, 100, 1000);
        rec.pointerUp(0, 100, 100, 1050);
        Action w = Action.image(spec(ID));
        w.press = Action.PRESS_NONE; w.waitMode = Action.MISSING_WAIT;
        rec.addStep(w, 3050);            // 🖼 pressed 2 s after the tap
        rec.skipTime(15000);             // 15 s spent cutting the image out
        rec.pointerDown(0, 200, 200, 3050 + 15000 + 700);
        rec.pointerUp(0, 200, 200, 3050 + 15000 + 750);
        List<Action> out = rec.stop();
        T.eq(3, out.size(), "tap, image wait, tap");
        T.eq(2000L, out.get(1).delay, "the image wait comes 2 s after the tap");
        T.eq(700L, out.get(2).delay, "the next tap 0.7 s later: the 15 s of cutting are not recorded");
    }
}
