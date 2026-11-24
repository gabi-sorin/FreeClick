package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** FreeClick 1.6: step tree, file version gate, engine scheduling, new steps, preview, undo. */
public final class V16Tests {

    // ------------------------------------------------------------------ helpers
    static Macro macro(String name) {
        Macro m = new Macro(name);
        m.screenWidth = 1080; m.screenHeight = 2400;
        m.settings.loops = 1;
        return m;
    }

    static Action tapAt(float x, long delay) { Action a = Action.tap(x, 100); a.delay = delay; return a; }

    /** A block-like step holding others (the If/Repeat types arrive later; the tree code must not care). */
    static Action block(Action... inside) {
        Action b = new Action(Action.Type.WAIT);
        b.children = new ArrayList<Action>();
        for (Action a : inside) b.children.add(a);
        return b;
    }

    static final class Rec implements PlaybackEngine.Listener, PlaybackEngine.Notices {
        final List<String> finished = new ArrayList<String>();
        final List<String> notices = new ArrayList<String>();
        final List<PlaybackEngine.State> states = new ArrayList<PlaybackEngine.State>();
        public void onStateChanged(PlaybackEngine.State s) { states.add(s); }
        public void onProgress(int loop, int total, int step, int steps, String name, Action a) { }
        public void onFinished(String reason) { finished.add(reason); }
        public void onNotice(String code, Action a) { notices.add(code); }
    }

    static List<Long> times(FakeHost h, String kind) {
        List<Long> out = new ArrayList<Long>();
        for (FakeHost.Event e : h.events) if (e.kind.startsWith(kind)) out.add(e.time - 1000);
        return out;
    }

    // ------------------------------------------------------------------ tree
    static void testTreeWalkCountRemove() {
        Action a = tapAt(1, 0), b = tapAt(2, 0), c = tapAt(3, 0), d = tapAt(4, 0);
        Action inner = block(c);
        Action outer = block(b, inner);
        outer.elseChildren = new ArrayList<Action>();
        outer.elseChildren.add(d);
        List<Action> list = new ArrayList<Action>();
        list.add(a); list.add(outer);
        T.eq(4, ActionTree.count(list), "leaves counted at every depth");
        T.eq(6, ActionTree.all(list).size(), "all steps incl. blocks");
        T.eq(2, ActionTree.depth(list), "two levels of blocks");
        T.eq(4, ActionTree.positionOf(list, d), "else step is the 4th");
        T.ok(ActionTree.remove(list, c), "deep remove");
        T.eq(3, ActionTree.count(list), "one less");
        T.eq(d, ActionTree.removeLastLeaf(list), "'−' takes the last step (from else)");
        T.eq(b, ActionTree.removeLastLeaf(list), "then goes into the block, never deletes the block");
        T.eq(a, ActionTree.removeLastLeaf(list), "empty blocks are skipped");
        T.eq(null, ActionTree.removeLastLeaf(list), "nothing left");
        T.eq(1, list.size(), "the (empty) block itself stays");
    }

    static void testCodecRoundTripsBlocksAndNewSteps() {
        Macro m = macro("tree");
        Action w = Action.randomWait(2000, 5000);
        Action app = Action.launchApp("com.game", 8000);
        Action outer = block(tapAt(5, 10), block(w));
        outer.elseChildren = new ArrayList<Action>();
        outer.elseChildren.add(app);
        m.actions.add(outer);
        String json = MacroCodec.toJson(m);
        Macro back = MacroCodec.fromJson(json);
        T.eq(json, MacroCodec.toJson(back), "stable");
        Action w2 = back.actions.get(0).children.get(1).children.get(0);
        T.ok(w2.isRandomWait(), "random wait kept");
        T.eq(5000L, w2.durationMax, "max kept");
        T.eq("com.game", back.actions.get(0).elseChildren.get(0).packageName, "package kept");
        Macro dup = back.duplicate("d");
        dup.actions.get(0).children.get(0).x = 999;
        T.near(5, back.actions.get(0).children.get(0).x, 1e-6, "copies are deep");
        T.ok(!MacroCodec.toJson(macro("plain")).contains("children"), "no new fields for plain macros");
    }

    static void testExportImportFollowReferencesInsideBlocks() {
        Macro child = macro("child"); child.actions.add(tapAt(1, 0));
        Macro root = macro("root");
        root.actions.add(block(Action.macro(child.id, 1)));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(child).add(root);
        String bundle = MacroCodec.exportBundle(root, lib);
        T.ok(bundle.contains("\"child\""), "sub-macro inside a block is exported");
        HashSet<String> existing = new HashSet<String>();
        existing.add(child.id); existing.add(root.id);
        List<Macro> imported = MacroCodec.importBundle(bundle, existing);
        Macro newRoot = null, newChild = null;
        for (Macro m : imported) { if (m.name.equals("root")) newRoot = m; else newChild = m; }
        T.ok(!newChild.id.equals(child.id), "child got a fresh id");
        T.eq(newChild.id, newRoot.actions.get(0).children.get(0).macroId,
                "reference inside the block follows the new id (never the user's own macro)");
    }

    static void testGraphSeesInsideBlocks() {
        Macro a = macro("A"), b = macro("B");
        a.actions.add(block(Action.macro(b.id, 1)));
        b.actions.add(block(block(Action.macro(a.id, 1))));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory().add(a).add(b);
        T.ok(MacroGraph.findCycle(a, lib) != null, "cycle through blocks found");
        Macro c = macro("C");
        c.actions.add(block(Action.waitColor(1, 1, 0, 100)));
        T.ok(MacroGraph.usesScreenCapture(c, lib), "colour condition inside a block needs capture");
        Macro scaled = macro("S");
        scaled.screenWidth = 540; scaled.screenHeight = 1200;
        scaled.actions.add(block(tapAt(100, 0)));
        MacroTransform.rescaleToScreen(scaled, 1080, 2400);
        T.near(200, scaled.actions.get(0).children.get(0).x, 1e-3, "rescaling reaches inside blocks");
        Macro onlyDisabled = macro("D");
        Action off = tapAt(1, 0); off.enabled = false;
        onlyDisabled.actions.add(off);
        onlyDisabled.actions.add(Action.macro("missing", 1));
        T.ok(!MacroGraph.hasRunnableSteps(onlyDisabled, lib), "nothing runnable");
        T.ok(MacroGraph.hasRunnableSteps(c, lib), "steps inside blocks are runnable");
    }

    static void testNewerFilesAreRefusedNotHalfRead() {
        String newer = "{\"version\":99,\"name\":\"x\",\"actions\":[{\"type\":\"TAP\"}]}";
        try {
            MacroCodec.fromJson(newer);
            throw new AssertionError("should refuse");
        } catch (MacroCodec.NewerVersionException expected) {
            T.ok(expected.getMessage().contains("Actualizează"), "tells the user to update");
        }
        String bundle = "{\"format\":\"freeclick-bundle\",\"version\":99,\"macros\":[]}";
        T.throwsAny(new Runnable() { public void run() { MacroCodec.importBundle("{\"format\":\"freeclick-bundle\",\"version\":99,\"macros\":[]}", new HashSet<String>()); } }, "newer bundle refused");
        T.ok(bundle.length() > 0, "");
        MacroCodec.fromJson("{\"version\":1,\"name\":\"old\",\"actions\":[]}"); // older is fine
    }

    static void testHostileNestingIsRefused() {
        StringBuilder sb = new StringBuilder("{\"name\":\"x\",\"actions\":");
        for (int i = 0; i < 200; i++) sb.append("[{\"type\":\"WAIT\",\"children\":");
        sb.append("[]");
        for (int i = 0; i < 200; i++) sb.append("}]");
        sb.append("}");
        final String deep = sb.toString();
        T.throwsAny(new Runnable() { public void run() { MacroCodec.fromJson(deep); } }, "200 levels refused cleanly");
    }

    // ------------------------------------------------------------------ engine
    static void testTimeLimitEndsAForeverColourWait() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        h.colorTimeline.put(0L, 0x000000);
        Macro m = macro("wait");
        m.actions.add(Action.waitColor(10, 10, 0xFFFFFF, 0)); // forever
        m.settings.stopAfterMs = 2500;
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(m, null);
        h.runUntil(1000 + 1200);
        e.pause();
        h.runUntil(1000 + 6000); // paused time does not count
        e.resume();
        h.runAll();
        T.eq("[time-limit]", r.finished.toString(), "limit reached during the wait");
        T.eq(6000 + 1300, h.now - 1000, "1.2 s + 1.3 s of playing, 4.8 s paused");
    }

    static void testRandomWaitStaysInRangeAndFollowsSpeed() {
        long min = Long.MAX_VALUE, max = 0;
        Random master = new Random(7);
        for (int seed = 0; seed < 200; seed++) {
            FakeHost h = new FakeHost();
            Macro m = macro("rw");
            m.actions.add(Action.randomWait(2000, 5000));
            m.actions.add(tapAt(1, 0));
            m.settings.speed = 2.0;
            PlaybackEngine e = new PlaybackEngine(h, null);
            e.setRandom(new Random(master.nextLong()));
            e.start(m, null);
            h.runAll();
            long t = times(h, "gesture").get(0);
            min = Math.min(min, t); max = Math.max(max, t);
        }
        T.ok(min >= 1000 && max <= 2500, "between 2 and 5 s at 2x: " + min + ".." + max);
        T.ok(max - min > 1000, "really random: " + min + ".." + max);
    }

    static void testLaunchAppWaitsForItThenContinuesOnTime() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        h.installed.add("com.game");
        h.callbackLatencyMs = 40; // phone reports gestures late; must not shorten the pause after the wait
        Macro m = macro("app");
        m.actions.add(tapAt(1, 100));
        m.actions.add(Action.launchApp("com.game", 10000));
        m.actions.add(tapAt(2, 300));
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setNotices(r);
        e.start(m, null);
        h.runAll();
        List<Long> launch = times(h, "launch:com.game");
        List<Long> taps = times(h, "gesture");
        T.eq(1, launch.size(), "launched once");
        long inFront = launch.get(0) + h.appLaunchDelayMs;
        T.ok(taps.get(1) >= inFront + 300, "next tap only after the app is in front + its full pause: " + taps + " front " + inFront);
        T.ok(taps.get(1) <= inFront + 300 + PlaybackEngine.APP_POLL_MS, "but not much later");
        T.eq("[]", r.notices.toString(), "no problems");
    }

    static void testLaunchMissingOrSlowAppIsReportedAndSkipped() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        h.installed.add("com.slow");
        h.appLaunchDelayMs = 60000;
        Macro m = macro("apps");
        m.actions.add(Action.launchApp("com.missing", 5000));
        m.actions.add(Action.launchApp("com.slow", 3000));
        m.actions.add(tapAt(1, 0));
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setNotices(r);
        e.start(m, null);
        h.runAll();
        T.eq("[app-missing, app-slow]", r.notices.toString(), "both reported");
        T.eq(1, times(h, "gesture").size(), "the macro still continues");
        T.ok(times(h, "gesture").get(0) >= 3000, "waited the 3 s for the slow app");
    }

    static void testNoBurstAfterAColourWait() {
        // A late-reporting phone builds up "lateness"; a colour wait must not let it eat the next pause.
        FakeHost h = new FakeHost();
        h.callbackLatencyMs = 400;
        h.colorTimeline.put(0L, 0x000000);
        h.colorTimeline.put(1000L + 3000, 0x00FF00);
        Macro m = macro("burst");
        m.actions.add(tapAt(1, 0));
        m.actions.add(Action.waitColor(5, 5, 0x00FF00, 0));
        m.actions.add(tapAt(2, 300));
        new PlaybackEngine(h, null).start(m, null);
        h.runAll();
        List<Long> taps = times(h, "gesture");
        T.ok(taps.get(1) >= 3000 + 300, "full 300 ms after the colour appeared: " + taps);
    }

    static void testColourIsReadOnlyFromFramesTakenAfterTheTouch() {
        FakeHost h = new FakeHost();
        h.frameIntervalMs = 350;
        h.colorTimeline.put(0L, 0x00FF00); // already green before the tap
        Macro m = macro("fresh");
        Action t = tapAt(1, 0);
        m.actions.add(t);
        m.actions.add(Action.waitColor(5, 5, 0x00FF00, 0));
        m.actions.add(tapAt(2, 0));
        new PlaybackEngine(h, null).start(m, null);
        h.runAll();
        List<Long> taps = times(h, "gesture");
        long tapEnd = taps.get(0) + t.duration;
        T.ok(taps.get(1) >= tapEnd + PlaybackEngine.SETTLE_MS, "the green seen before the tap does not count: " + taps);
        T.ok(h.notBefores.get(0) >= 1000 + tapEnd + PlaybackEngine.SETTLE_MS, "asked for a fresh frame");
    }

    static void testTouchCancelPausesAndResumeRetriesTheGesture() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro m = macro("touch");
        m.actions.add(tapAt(1, 100));
        m.actions.add(tapAt(2, 100));
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setPauseOnCancel(true);
        h.failGestures = true;
        e.start(m, null);
        h.runUntil(1000 + 500);
        T.eq(PlaybackEngine.State.PAUSED, e.state(), "paused after the cancelled tap");
        T.eq(PlaybackEngine.PAUSE_TOUCH, e.pauseReason(), "because of a touch");
        h.failGestures = false;
        e.resume();
        h.runAll();
        List<FakeHost.Event> g = h.gestures();
        T.eq(3, g.size(), "cancelled tap played again, then the second");
        T.near(1, g.get(1).plan.strokes.get(0).points.get(0)[0], 1e-3, "retry is the same tap");
        T.eq("[done]", r.finished.toString(), "finished normally");
        T.eq(1, e.failedGestures(), "one failure counted");
    }

    static void testWithoutPauseOnCancelTheRunCarriesOn() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        h.failGestures = true;
        Macro m = macro("carry");
        m.actions.add(tapAt(1, 100));
        m.actions.add(tapAt(2, 100));
        new PlaybackEngine(h, r).start(m, null);
        h.runAll();
        T.eq(2, h.gestures().size(), "both attempted");
        T.eq("[done]", r.finished.toString(), "1.5 behaviour kept when the setting is off");
    }

    static void testPreviewLimitsLoopsAndNeverReadsTheScreen() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro m = macro("prev");
        m.settings.loops = 0; // forever
        m.actions.add(tapAt(1, 100));
        m.actions.add(Action.waitColor(5, 5, 0x00FF00, 0));
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setPreview(true, true);
        e.start(m, null);
        h.runAll();
        T.eq(2, h.gestures().size(), "forever shows 2 turns");
        T.eq(0, h.samples.size(), "no screen reads in preview");
        T.eq("[done]", r.finished.toString(), "ends by itself");
        T.eq(2, e.conditionsMet(), "conditions assumed found");
    }

    static void testPreviewAssumingAbsentShortensWaits() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro m = macro("absent");
        m.actions.add(Action.waitColor(5, 5, 0x00FF00, 30000));
        m.actions.add(tapAt(1, 0));
        m.actions.add(Action.waitColor(5, 5, 0x00FF00, 0));
        m.actions.add(tapAt(2, 0));
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setPreview(true, false);
        e.start(m, null);
        h.runAll();
        List<Long> taps = times(h, "gesture");
        T.eq(1, taps.size(), "stops where it would wait forever");
        T.ok(taps.get(0) <= PlaybackEngine.PREVIEW_MAX_WAIT_MS + 200, "30 s timeout shortened: " + taps);
        T.eq("[condition-timeout]", r.finished.toString(), "reported");
    }

    static void testSkipWaitJumpsAPause() {
        FakeHost h = new FakeHost();
        Macro m = macro("skip");
        m.actions.add(Action.waitFor(60000));
        m.actions.add(tapAt(1, 0));
        PlaybackEngine e = new PlaybackEngine(h, null);
        e.start(m, null);
        h.runUntil(1000 + 500);
        e.skipWait();
        h.runAll();
        T.eq("[500]", times(h, "gesture").toString(), "tap right after ⏭");
    }

    static void testStartFromAStep() {
        FakeHost h = new FakeHost();
        Macro m = macro("from");
        for (int i = 0; i < 4; i++) m.actions.add(tapAt(i + 1, 100));
        new PlaybackEngine(h, null).start(m, null, 2);
        h.runAll();
        T.eq(2, h.gestures().size(), "steps 3 and 4 only");
        T.near(3, h.gestures().get(0).plan.strokes.get(0).points.get(0)[0], 1e-3, "starts at step 3");
    }

    static void testPassesThatTouchNothingNeverSpin() {
        FakeHost h = new FakeHost();
        Macro m = macro("spin");
        m.settings.loops = 0; m.settings.loopDelay = 0;
        m.settings.stopAfterMs = 1000;
        m.actions.add(Action.global(GlobalActions.BACK));
        new PlaybackEngine(h, null).start(m, null);
        h.runAll(100000);
        int n = times(h, "global").size();
        T.ok(n >= 9 && n <= 11, "about 10 passes per second, not thousands: " + n);
    }

    static void testEmptyAndOnlyMissingSubMacrosAreRefusedAtStart() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro m = macro("refs");
        m.actions.add(Action.macro("gone", 2));
        PlaybackEngine e = new PlaybackEngine(h, r);
        T.ok(!e.start(m, null), "refused");
        T.eq("[empty]", r.finished.toString(), "reason");
        T.eq(0, h.pendingTimers(), "nothing scheduled");
    }

    static void testStaleTimersCannotFireAfterRestart() {
        FakeHost h = new FakeHost(); Rec r = new Rec();
        Macro a = macro("a"); a.actions.add(tapAt(1, 5000));
        Macro b = macro("b"); b.actions.add(tapAt(2, 100));
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.start(a, null);
        h.runUntil(1000 + 50);
        e.start(b, null); // restart while a's 5 s pause is pending
        h.runAll();
        T.eq(1, h.gestures().size(), "only b's tap");
        T.near(2, h.gestures().get(0).plan.strokes.get(0).points.get(0)[0], 1e-3, "b");
    }

    // ------------------------------------------------------------------ undo
    static void testUndoRedoAcrossPanelEdits() {
        WorkspaceTests.MemStore s = new WorkspaceTests.MemStore();
        Workspace w = WorkspaceTests.ws(s, new WorkspaceTests.MemRepo());
        w.ensureMode();
        T.ok(!w.canUndo(), "fresh work has no history");
        w.addTarget(Workspace.Target.TAP);
        Action t2 = w.addTarget(Workspace.Target.TAP);
        t2.x = 10; w.markDirty();              // dragged
        w.removeLast();
        w.addTarget(Workspace.Target.SWIPE);
        w.clearAll();
        T.eq(0, ActionTree.count(w.current().actions), "cleared");
        T.eq("ștergerea tuturor", w.undoLabel(), "label");
        T.eq("ștergerea tuturor", w.undo(), "undo clear");
        T.eq(2, w.current().actions.size(), "target + swipe back");
        w.undo(); // swipe
        w.undo(); // remove last
        T.eq(2, w.current().actions.size(), "removed target is back");
        T.near(10, w.current().actions.get(1).x, 1e-3, "with its dragged position");
        w.undo(); // drag
        T.ok(w.current().actions.get(1).x != 10, "drag undone");
        T.eq("modificarea", w.redo(), "redo drag");
        T.near(10, w.current().actions.get(1).x, 1e-3, "drag redone");
        T.eq(s.drafts.get(Macro.KIND_MANUAL), MacroCodec.toJson(w.current()), "draft saved after undo/redo (survives restart)");
        w.addTarget(Workspace.Target.LONG_PRESS);
        T.ok(!w.canRedo(), "a new change clears redo");
        w.setSpeed(3);
        T.eq("adăugarea țintei", w.undoLabel(), "speed is not an undo step");
    }

    static void testUndoHistoryIsPerWorkAndBounded() {
        Workspace w = WorkspaceTests.ws(new WorkspaceTests.MemStore(), new WorkspaceTests.MemRepo());
        w.ensureMode();
        for (int i = 0; i < 50; i++) w.addTarget(Workspace.Target.TAP);
        int n = 0;
        while (w.undo() != null) n++;
        T.eq(Workspace.UNDO_MAX_STEPS, n, "at most 30 steps back");
        w.addTarget(Workspace.Target.TAP);
        w.switchMode(Macro.KIND_RECORD);
        T.ok(!w.canUndo(), "switching mode starts a new history");
        w.switchMode(Macro.KIND_MANUAL);
        T.ok(!w.canUndo(), "and coming back does not mix histories");
        // a huge recording: history stays under the character budget
        w.switchMode(Macro.KIND_RECORD);
        w.beginRecording();
        List<Action> big = new ArrayList<Action>();
        for (int i = 0; i < 4000; i++) big.add(Action.tap(i % 1000, i % 2000));
        for (int k = 0; k < 10; k++) w.addRecorded(big);
        T.ok(w.canUndo(), "still undoable");
        int steps = 0;
        while (w.undo() != null) steps++;
        T.ok(steps < 10, "old snapshots dropped to stay small: " + steps);
    }

    // ------------------------------------------------------------------ editor history & recording tools
    static void testEditHistory() {
        EditHistory h = new EditHistory(3, 1000000);
        T.ok(!h.canUndo(), "empty");
        h.record("A", "x"); h.record("B", "y"); h.record("C", "z"); h.record("D", "w"); // E is current
        T.eq("w", h.undoLabel(), "last label");
        T.eq("D", h.undo("E"), "back to D");
        T.eq("C", h.undo("D"), "back to C");
        T.eq("B", h.undo("C"), "back to B");
        T.eq(null, h.undo("B"), "only 3 steps kept");
        T.eq("C", h.redo("B"), "redo");
        h.record("C", "new");
        T.ok(!h.canRedo(), "new change clears redo");
        EditHistory small = new EditHistory(30, 10);
        small.record("12345678", "a"); small.record("abcdefgh", "b");
        T.eq("abcdefgh", small.undo("now"), "newest kept");
        small.clear();
        small.record("same", "noop");
        small.dropIfUnchanged("same");
        T.ok(!small.canUndo(), "a change that did nothing is forgotten");
        T.eq(null, small.undo("x"), "oldest dropped by size");
    }

    static void testRecordingTools() {
        List<Action> l = new ArrayList<Action>();
        Action first = tapAt(1, 7300);
        Action sw = Action.swipe(0, 0, 100, 100, 400); sw.delay = 9000;
        Action w = Action.waitFor(12000);
        Action rw = Action.randomWait(2000, 20000);
        Action lp = Action.longPress(5, 5, 900); lp.delay = 200;
        l.add(first); l.add(sw); l.add(w); l.add(rw); l.add(lp);
        T.eq(7300L, MacroTransform.shortenFirstPause(l, 500), "old first pause returned");
        T.eq(500L, first.delay, "first pause shortened");
        T.eq(-1L, MacroTransform.shortenFirstPause(l, 500), "already short");
        int n = MacroTransform.capPauses(l, 3000);
        T.eq(3, n, "swipe delay, pause, random max");
        T.eq(3000L, sw.delay, "capped delay");
        T.eq(3000L, w.duration, "capped pause");
        T.ok(rw.isRandomWait() && rw.durationMax == 3000 && rw.duration == 2000, "random keeps its spread");
        T.eq(200L, lp.delay, "short pauses untouched");
        List<Action> sel = new ArrayList<Action>();
        sel.add(sw); sel.add(lp);
        MacroTransform.speedUp(sel, 2);
        T.eq(1500L, sw.delay, "pause halved");
        T.eq(200L, sw.duration, "swipe twice as fast");
        T.eq(100L, lp.delay, "selected only");
        T.eq(900L, lp.duration, "long press keeps its press time");
        T.eq(3000L, w.duration, "unselected untouched");
        List<Stroke> strokes = new ArrayList<Stroke>();
        Stroke st = new Stroke(); st.add(0, 0, 0); st.add(10, 10, 300); st.add(20, 20, 801);
        strokes.add(st);
        Action g = Action.gesture(strokes);
        List<Action> gl = new ArrayList<Action>(); gl.add(g);
        MacroTransform.speedUp(gl, 2);
        T.eq(401L, g.duration, "recorded path twice as fast");
        T.eq(150L, (long) g.strokes.get(0).times.get(1), "every point's time scaled");
    }
}
