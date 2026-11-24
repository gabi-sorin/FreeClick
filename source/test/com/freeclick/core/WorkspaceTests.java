package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Whole-session scenarios: modes, drafts, targets, recording, saving, loading, playing. */
public final class WorkspaceTests {

    /** In-memory stand-in for SharedPreferences: survives "restarts" (new Workspace objects). */
    static final class MemStore implements Workspace.Store {
        String mode = Macro.KIND_MANUAL;
        final Map<String, String> drafts = new HashMap<String, String>();
        final Map<String, Boolean> dirty = new HashMap<String, Boolean>();
        public String mode() { return mode; }
        public void setMode(String k) { mode = k; }
        public String draft(String k) { return drafts.get(k); }
        public boolean draftDirty(String k) { return Boolean.TRUE.equals(dirty.get(k)); }
        public void setDraft(String k, String json, boolean d) { drafts.put(k, json); dirty.put(k, d); }
    }

    static final class MemRepo implements Workspace.Saver {
        final MacroLibrary.InMemory lib = new MacroLibrary.InMemory();
        int saves;
        boolean fail;
        public void save(Macro m) throws Exception {
            if (fail) throw new java.io.IOException("disk full");
            saves++;
            lib.add(m);
        }
    }

    static Workspace ws(MemStore s, MemRepo r) {
        Workspace w = new Workspace(s, r.lib, r);
        w.setScreen(1080, 2400);
        return w;
    }

    static void testFreshManualWork() {
        Workspace w = ws(new MemStore(), new MemRepo());
        T.ok(w.ensureMode(), "created");
        T.eq(Workspace.UNSAVED, w.current().name, "unsaved name");
        T.eq(Macro.KIND_MANUAL, w.current().kind, "manual");
        T.eq(0, w.current().settings.loops, "manual repeats until stopped");
        T.ok(!w.isSaved() && !w.isDirty(), "clean and unsaved");
        T.ok(!w.ensureMode(), "second ensure is a no-op");
    }

    static void testTargetsUseModeTimingsAndDraftsSurviveEverything() {
        MemStore store = new MemStore();
        MemRepo repo = new MemRepo();
        Workspace w = ws(store, repo);
        w.ensureMode();
        MacroTransform.applyManualTiming(w.current(), 333, 44, 555);
        Action t = w.addTarget(Workspace.Target.TAP);
        Action s = w.addTarget(Workspace.Target.SWIPE);
        Action l = w.addTarget(Workspace.Target.LONG_PRESS);
        T.eq(333L, t.delay, "tap interval");
        T.eq(44L, t.duration, "tap duration");
        T.eq(555L, s.duration, "swipe duration");
        T.eq(Action.DEFAULT_LONG_PRESS_MS, l.duration, "long press");
        T.ok(w.isDirty(), "dirty");
        T.ok(t.x != s.x || t.y != s.y, "targets do not stack exactly");

        // switch to record mode and back: the manual draft is restored
        w.switchMode(Macro.KIND_RECORD);
        T.eq(Macro.KIND_RECORD, w.current().kind, "record work");
        T.eq(0, w.current().actions.size(), "record starts empty");
        w.switchMode(Macro.KIND_MANUAL);
        T.eq(3, w.current().actions.size(), "manual draft restored");
        T.ok(w.isDirty(), "still marked modified");

        // "app restart / update": a brand new Workspace on the same stored data
        Workspace after = ws(store, repo);
        after.ensureMode();
        T.eq(3, after.current().actions.size(), "draft survives restarts and updates");
        T.eq(333L, after.current().settings.tapInterval, "settings survive");
    }

    static void testRemoveLastAndClearAll() {
        Workspace w = ws(new MemStore(), new MemRepo());
        w.ensureMode();
        T.ok(w.removeLast() == null, "nothing to remove");
        T.eq(0, w.clearAll(), "nothing to clear");
        Action a = w.addTarget(Workspace.Target.TAP);
        Action b = w.addTarget(Workspace.Target.SWIPE);
        T.ok(w.removeLast() == b, "removes the last one");
        T.eq(1, w.current().actions.size(), "one left");
        w.addTarget(Workspace.Target.TAP);
        w.add(Action.waitFor(1000));
        T.eq(3, w.clearAll(), "cleared all three");
        T.eq(0, w.current().actions.size(), "empty");
        T.ok(w.isDirty(), "clearing is a change");
        w.remove(a); // removing something not there is harmless
    }

    static void testSaveSaveAsLoadAreCopies() throws Exception {
        MemStore store = new MemStore();
        MemRepo repo = new MemRepo();
        Workspace w = ws(store, repo);
        w.ensureMode();
        w.addTarget(Workspace.Target.TAP);
        w.saveAs("Idle slayer");
        T.ok(w.isSaved() && !w.isDirty(), "saved and clean");
        T.eq("Idle slayer", w.current().name, "named");
        String id = w.current().id;
        Macro stored = repo.lib.find(id);
        // editing the work does not touch the saved file until Save
        w.addTarget(Workspace.Target.TAP);
        T.eq(1, repo.lib.find(id).actions.size(), "saved copy unchanged before Save");
        T.ok(stored != w.current(), "work is a copy");
        w.save();
        T.eq(2, repo.lib.find(id).actions.size(), "Save writes the changes");
        // Save as makes a new item and keeps the old one
        w.saveAs("Idle slayer 2");
        T.ok(!w.current().id.equals(id), "new id");
        T.eq(2, repo.lib.all().size(), "two saved items");
        // load the first one back
        w.load(repo.lib.find(id));
        T.eq("Idle slayer", w.current().name, "loaded");
        w.current().actions.clear();
        T.eq(2, repo.lib.find(id).actions.size(), "loaded work is a copy");
    }

    static void testSaveFailureKeepsWork() {
        MemRepo repo = new MemRepo();
        Workspace w = ws(new MemStore(), repo);
        w.ensureMode();
        w.addTarget(Workspace.Target.TAP);
        String before = w.current().id;
        repo.fail = true;
        try {
            w.saveAs("x");
            throw new AssertionError("should fail");
        } catch (Exception expected) { }
        T.eq(before, w.current().id, "previous work restored after a failed Save as");
        T.ok(w.isDirty() && !w.isSaved(), "still unsaved and modified");
    }

    static void testRecordingRules() throws Exception {
        MemStore store = new MemStore();
        MemRepo repo = new MemRepo();
        Workspace w = ws(store, repo);
        w.switchMode(Macro.KIND_RECORD);
        T.ok(!w.recordingWouldReplaceUnsaved(), "empty: no question");
        String firstId = w.current().id;
        w.beginRecording();
        T.eq(firstId, w.current().id, "empty work is reused");
        List<Action> rec = new ArrayList<Action>();
        rec.add(Action.tap(1, 1));
        rec.add(Action.global(GlobalActions.BACK));
        T.eq(2, w.addRecorded(rec), "two recorded");
        T.eq(0, w.addRecorded(new ArrayList<Action>()), "empty stop adds nothing");
        T.ok(w.recordingWouldReplaceUnsaved(), "unsaved recording: ask before replacing");
        w.saveAs("Rec A");
        T.ok(!w.recordingWouldReplaceUnsaved(), "saved: no question needed");
        String savedId = w.current().id;
        w.beginRecording();
        T.ok(!w.current().id.equals(savedId), "new recording gets a new Nesalvat");
        T.eq(Workspace.UNSAVED, w.current().name, "named Nesalvat");
        T.eq(2, repo.lib.find(savedId).actions.size(), "saved recording untouched");
        T.ok(w.removeLast() == null, "new recording is empty");
    }

    static void testReplaceAndRefresh() throws Exception {
        MemRepo repo = new MemRepo();
        Workspace w = ws(new MemStore(), repo);
        w.ensureMode();
        w.addTarget(Workspace.Target.TAP);
        w.saveAs("A");
        String id = w.current().id;
        // edited elsewhere (list editor) while the work is clean -> reloaded
        Macro edited = MacroCodec.fromJson(MacroCodec.toJson(repo.lib.find(id)));
        edited.actions.add(Action.waitFor(5));
        repo.save(edited);
        T.ok(w.refreshFromRepo(id), "clean work reloads");
        T.eq(2, w.current().actions.size(), "sees the edit");
        // but never over local changes
        w.addTarget(Workspace.Target.TAP);
        T.ok(!w.refreshFromRepo(id), "dirty work is not overwritten");
        // replace (step editor on the current work) keeps id and kind
        Macro copy = MacroCodec.fromJson(MacroCodec.toJson(w.current()));
        copy.id = "other";
        copy.kind = Macro.KIND_COMBO;
        copy.actions.clear();
        w.replace(copy);
        T.eq(id, w.current().id, "id kept");
        T.eq(Macro.KIND_MANUAL, w.current().kind, "kind kept");
        T.ok(w.isDirty(), "dirty");
    }

    static void testCorruptDraftStartsFresh() {
        MemStore store = new MemStore();
        store.drafts.put(Macro.KIND_MANUAL, "{broken json");
        Workspace w = ws(store, new MemRepo());
        w.ensureMode();
        T.eq(0, w.current().actions.size(), "fresh work instead of a crash");
    }

    static void testRescaleFollowsScreen() {
        Workspace w = ws(new MemStore(), new MemRepo());
        w.ensureMode();
        Action t = w.addTarget(Workspace.Target.TAP);
        float x = t.x;
        w.setScreen(540, 1200);
        T.ok(w.rescaleToScreen(), "rescaled");
        T.near(x / 2, w.current().actions.get(0).x, 1e-3, "half size");
        T.ok(!w.rescaleToScreen(), "second time no-op");
    }

    /** Record -> save -> "update/restart" -> load -> play with phone latency: exact timeline. */
    static void testFullRecordSaveRestartPlay() throws Exception {
        MemStore store = new MemStore();
        MemRepo repo = new MemRepo();
        Workspace w = ws(store, repo);
        w.switchMode(Macro.KIND_RECORD);
        w.beginRecording();
        GestureRecorder r = new GestureRecorder(new GestureRecorder.Config());
        r.start(0);
        long[] downs = {2500, 2900, 7000};
        for (long d : downs) { r.pointerDown(0, 300, 600, d); r.pointerUp(0, 300, 600, d + 70); }
        r.pointerDown(0, 100, 2000, 8000);
        for (int i = 1; i <= 10; i++) r.pointerMove(0, 100, 2000 - i * 100, 8000 + i * 20);
        r.pointerUp(0, 100, 1000, 8200);
        w.addRecorded(r.stop());
        w.saveAs("Farm");
        String id = w.current().id;

        Workspace restarted = ws(store, repo);
        restarted.ensureMode();
        T.eq(id, restarted.current().id, "same work after restart");
        restarted.load(repo.lib.find(id));

        FakeHost h = new FakeHost();
        h.callbackLatencyMs = 35;
        new PlaybackEngine(h, null).start(restarted.current(), repo.lib);
        h.runAll();
        T.eq(4, h.gestures().size(), "3 taps + 1 swipe");
        T.eq(2500L, h.gestures().get(0).time - 1000, "initial pause exact");
        T.eq(2900L, h.gestures().get(1).time - 1000, "gap exact");
        T.eq(7000L, h.gestures().get(2).time - 1000, "long gap exact");
        T.eq(8000L, h.gestures().get(3).time - 1000, "swipe on time");
        T.eq(200L, h.gestures().get(3).plan.totalDuration(), "swipe duration kept");
    }

    /** Manual targets, infinite loop, stopped by time limit; then a combination of two recordings. */
    static void testManualAndCombinationSessions() throws Exception {
        MemStore store = new MemStore();
        MemRepo repo = new MemRepo();
        Workspace w = ws(store, repo);
        w.ensureMode();
        w.addTarget(Workspace.Target.TAP);
        w.addTarget(Workspace.Target.SWIPE);
        MacroTransform.applyManualTiming(w.current(), 100, 50, 150);
        w.current().settings.stopAfterMs = 2000;
        FakeHost h = new FakeHost();
        new PlaybackEngine(h, null).start(w.current(), repo.lib);
        h.runAll();
        // one cycle = 100 + 50 + 100 + 150 = 400 ms (+ loop delay 500) -> stops at 2 s
        T.ok(h.gestures().size() >= 4 && h.gestures().size() <= 6, "looped until the limit: " + h.gestures().size());

        // two saved recordings combined: A twice, then B
        Macro a = new Macro("A"); a.kind = Macro.KIND_RECORD;
        Action ta = Action.tap(1, 1); ta.delay = 10; a.actions.add(ta);
        Macro b = new Macro("B"); b.kind = Macro.KIND_RECORD;
        Action tb = Action.tap(2, 2); tb.delay = 10; b.actions.add(tb);
        repo.save(a);
        repo.save(b);
        w.switchMode(Macro.KIND_COMBO);
        w.add(Action.macro(a.id, 2));
        w.add(Action.macro(b.id, 1));
        w.saveAs("Combo");
        FakeHost h2 = new FakeHost();
        new PlaybackEngine(h2, null).start(w.current(), repo.lib);
        h2.runAll();
        T.eq(3, h2.gestures().size(), "A, A, B");
        T.near(2, h2.gestures().get(2).plan.strokes.get(0).points.get(0)[0], 1e-3, "B last");
        T.eq(Macro.KIND_COMBO, repo.lib.find(w.current().id).kind, "saved as a combination");
    }
}
