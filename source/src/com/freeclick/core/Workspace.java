package com.freeclick.core;

import java.util.List;

/**
 * The panel's current work for each mode (manual targets, recording, combination),
 * with Clickmate-like rules:
 *  - every mode keeps its own work ("Nesalvat" until saved) as a draft that survives
 *    turning the panel off, mode switches, app restarts and app updates;
 *  - loading a saved item copies it (the saved file only changes on Save);
 *  - a new recording never overwrites a saved recording.
 * Pure Java so the whole flow is unit-tested; the service only adds windows around it.
 */
public final class Workspace {
    public static final String UNSAVED = "Nesalvat";

    /** Persistent small state (Android: SharedPreferences). */
    public interface Store {
        String mode();
        void setMode(String kind);
        String draft(String kind);
        boolean draftDirty(String kind);
        void setDraft(String kind, String json, boolean dirty);
    }

    /** Writes a saved item (Android: MacroRepository). */
    public interface Saver { void save(Macro m) throws Exception; }

    public enum Target { TAP, SWIPE, LONG_PRESS }

    private final Store store;
    private final MacroLibrary lib;
    private final Saver saver;
    private Macro current;
    private boolean dirty;
    private int screenW = 1080, screenH = 2400;

    // ---- undo / redo: snapshots of the current work (JSON), newest last
    static final int UNDO_MAX_STEPS = 30;
    /** Long recordings make big snapshots: keep the history under ~8 MB of text. */
    static final long UNDO_MAX_CHARS = 4000000L;
    private static final class Snap {
        final String json; final String label; final boolean dirty;
        Snap(String json, String label, boolean dirty) { this.json = json; this.label = label; this.dirty = dirty; }
    }
    private final java.util.ArrayDeque<Snap> undo = new java.util.ArrayDeque<Snap>();
    private final java.util.ArrayDeque<Snap> redo = new java.util.ArrayDeque<Snap>();
    /** The current work as of the last change (what an undo of the next change returns to). */
    private String committed;
    private boolean committedDirty;

    public Workspace(Store store, MacroLibrary lib, Saver saver) {
        this.store = store;
        this.lib = lib;
        this.saver = saver;
    }

    public void setScreen(int w, int h) {
        if (w > 0 && h > 0) { screenW = w; screenH = h; }
    }

    public Macro current() { return current; }
    public boolean isDirty() { return dirty; }
    public boolean isSaved() { return current != null && lib.find(current.id) != null; }
    public String kind() { return current != null ? current.kind : store.mode(); }

    // ------------------------------------------------------------------ modes & drafts
    /** Make the current work belong to the selected mode, restoring that mode's draft. True if it changed. */
    public boolean ensureMode() {
        String k = store.mode();
        if (current != null && k.equals(current.kind)) return false;
        persist();
        current = restore(k);
        resetHistory();
        return true;
    }

    public void switchMode(String kind) {
        store.setMode(kind);
        ensureMode();
    }

    /** Load a saved item as the work of its mode (a copy: the saved file changes only on Save). */
    public void load(Macro m) {
        persist();
        store.setMode(m.kind);
        current = copy(m);
        dirty = false;
        persist();
        resetHistory();
    }

    /** Start an empty "Nesalvat" in the current mode. */
    public void reset() {
        current = newMacro(store.mode());
        dirty = false;
        persist();
        resetHistory();
    }

    /** Something was edited in place (marker dragged, dialog OK...): remember it, undoably. */
    public void markDirty() { commit("modificarea"); }

    /** Record a change of the current work under a name shown as "Anulează: <label>". */
    public void commit(String label) {
        boolean before = committedDirty;
        dirty = true;
        if (current == null) return;
        String json = MacroCodec.toJson(current);
        if (committed != null && !committed.equals(json)) {
            push(undo, new Snap(committed, label, before));
            redo.clear();
        }
        committed = json;
        committedDirty = dirty;
        store.setDraft(current.kind, json, dirty);
    }

    private void push(java.util.ArrayDeque<Snap> stack, Snap s) {
        stack.addLast(s);
        long chars = 0;
        for (Snap x : stack) chars += x.json.length();
        while (stack.size() > UNDO_MAX_STEPS || (chars > UNDO_MAX_CHARS && stack.size() > 1)) {
            chars -= stack.removeFirst().json.length();
        }
    }

    private void resetHistory() {
        undo.clear();
        redo.clear();
        committed = current == null ? null : MacroCodec.toJson(current);
        committedDirty = dirty;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    /** What the next undo would revert, e.g. "adăugarea țintei" (null if nothing). */
    public String undoLabel() { return undo.isEmpty() ? null : undo.peekLast().label; }
    public String redoLabel() { return redo.isEmpty() ? null : redo.peekLast().label; }

    /** Go back one change. Returns its label, or null if there was nothing to undo. */
    public String undo() { return travel(undo, redo); }

    /** Re-apply the last undone change. */
    public String redo() { return travel(redo, undo); }

    private String travel(java.util.ArrayDeque<Snap> from, java.util.ArrayDeque<Snap> to) {
        if (from.isEmpty() || current == null) return null;
        Snap s = from.removeLast();
        Macro m;
        try {
            m = MacroCodec.fromJson(s.json);
        } catch (RuntimeException e) {
            return null;
        }
        push(to, new Snap(committed, s.label, committedDirty));
        m.id = current.id;
        m.kind = current.kind;
        current = m;
        dirty = s.dirty;
        committed = s.json;
        committedDirty = dirty;
        store.setDraft(current.kind, s.json, dirty);
        return s.label;
    }

    /** Write the current work as this mode's draft. */
    public void persist() {
        if (current != null) store.setDraft(current.kind, MacroCodec.toJson(current), dirty);
    }

    private Macro restore(String kind) {
        String json = store.draft(kind);
        if (json != null) {
            try {
                Macro m = MacroCodec.fromJson(json);
                m.kind = kind;
                dirty = store.draftDirty(kind);
                return m;
            } catch (RuntimeException corrupt) {
                // fall through to a fresh one
            }
        }
        dirty = false;
        return newMacro(kind);
    }

    Macro newMacro(String kind) {
        Macro m = new Macro(UNSAVED);
        m.kind = kind;
        m.screenWidth = screenW;
        m.screenHeight = screenH;
        // manual targets repeat until stopped (Clickmate's "Infinity"); recordings/combinations play once
        m.settings.loops = Macro.KIND_MANUAL.equals(kind) ? 0 : 1;
        return m;
    }

    /** Coordinates follow the current screen (rotation / resolution change). */
    public boolean rescaleToScreen() {
        if (current != null && MacroTransform.rescaleToScreen(current, screenW, screenH)) {
            persist();
            resetHistory(); // older snapshots have the old screen's coordinates
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ save
    public void save() throws Exception {
        saver.save(copy(current));
        dirty = false;
        persist();
    }

    public void saveAs(String name) throws Exception {
        Macro previous = current;
        current = current.duplicate(name);
        try {
            save();
        } catch (Exception e) {
            current = previous;
            throw e;
        }
        resetHistory();
    }

    /** The step-by-step editor hands back an edited copy. */
    public void replace(Macro edited) {
        edited.id = current.id;
        edited.kind = current.kind;
        current = edited;
        commit("editarea");
    }

    /** A saved item changed elsewhere: reload it if it is the current, unmodified work. */
    public boolean refreshFromRepo(String id) {
        if (current == null || !current.id.equals(id) || dirty) return false;
        Macro m = lib.find(id);
        if (m == null) return false;
        current = copy(m);
        persist();
        resetHistory();
        return true;
    }

    // ------------------------------------------------------------------ editing
    /** "+" in manual mode: a new target near the centre, using the mode's interval/durations. */
    public Action addTarget(Target t) {
        int n = ActionTree.count(current.actions);
        float off = (n % 5) * (screenW / 38f);
        Macro.Settings st = current.settings;
        Action a;
        if (t == Target.SWIPE) {
            a = Action.swipe(screenW / 2f, screenH * 0.62f + off, screenW / 2f, screenH * 0.38f + off, st.swipeDuration);
        } else if (t == Target.LONG_PRESS) {
            a = Action.longPress(screenW / 2f + off, screenH / 2f + off, Action.DEFAULT_LONG_PRESS_MS);
        } else {
            a = Action.tap(screenW / 2f + off, screenH / 2f + off);
            a.duration = st.tapDuration;
        }
        a.delay = st.tapInterval;
        current.actions.add(a);
        commit(t == Target.SWIPE ? "adăugarea glisării" : "adăugarea țintei");
        return a;
    }

    public Action add(Action a) {
        current.actions.add(a);
        commit("adăugarea");
        return a;
    }

    public void remove(Action a) {
        if (ActionTree.remove(current.actions, a)) commit("ștergerea");
    }

    /** "−": remove the last step (target, recorded gesture, pause...), never a whole block. Null if empty. */
    public Action removeLast() {
        if (current == null) return null;
        Action a = ActionTree.removeLastLeaf(current.actions);
        if (a != null) commit("ștergerea ultimei acțiuni");
        return a;
    }

    /** Long-press on "−" / "Șterge tot": remove every step. Returns how many were removed. */
    public int clearAll() {
        if (current == null || current.actions.isEmpty()) return 0;
        int n = ActionTree.count(current.actions);
        current.actions.clear();
        commit("ștergerea tuturor");
        return n;
    }

    /** Speed is a setting, not an edit: saved, but not an undo step. */
    public void setSpeed(double s) {
        current.settings.speed = Macro.clampSpeed(s);
        dirty = true;
        persist();
        committed = MacroCodec.toJson(current);
        committedDirty = dirty;
    }

    // ------------------------------------------------------------------ recording
    /** True if pressing ● would throw away an unsaved recording (ask first). */
    public boolean recordingWouldReplaceUnsaved() {
        return current != null && !ActionTree.isEmpty(current.actions) && !isSaved();
    }

    /** ●: a new recording starts a fresh "Nesalvat" (a saved recording is never overwritten). */
    public void beginRecording() {
        // A recording always belongs to record mode.
        if (!Macro.KIND_RECORD.equals(store.mode())) switchMode(Macro.KIND_RECORD);
        else ensureMode();
        if (current == null || !ActionTree.isEmpty(current.actions)) {
            current = newMacro(Macro.KIND_RECORD);
            dirty = false;
            persist();
            resetHistory();
        }
    }

    /** ■: append what was recorded. */
    public int addRecorded(List<Action> recorded) {
        if (recorded == null || recorded.isEmpty()) return 0;
        current.actions.addAll(recorded);
        commit("înregistrarea");
        return recorded.size();
    }

    private static Macro copy(Macro m) { return MacroCodec.fromJson(MacroCodec.toJson(m)); }
}
