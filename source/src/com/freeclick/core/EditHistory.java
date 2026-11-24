package com.freeclick.core;

import java.util.ArrayDeque;

/**
 * Undo / redo for an editor: snapshots (JSON) of the thing being edited, with a label each.
 * Bounded in steps and in characters (long recordings make big snapshots).
 */
public final class EditHistory {
    private static final class Snap {
        final String json; final String label;
        Snap(String json, String label) { this.json = json; this.label = label; }
    }

    private final int maxSteps;
    private final long maxChars;
    private final ArrayDeque<Snap> undo = new ArrayDeque<Snap>();
    private final ArrayDeque<Snap> redo = new ArrayDeque<Snap>();

    public EditHistory(int maxSteps, long maxChars) {
        this.maxSteps = Math.max(1, maxSteps);
        this.maxChars = Math.max(1, maxChars);
    }

    /** Call BEFORE a change, with the state as it was and what the change is called. */
    public void record(String before, String label) {
        if (before == null) return;
        if (!undo.isEmpty() && undo.peekLast().json.equals(before) && label.equals(undo.peekLast().label)) return;
        push(undo, new Snap(before, label));
        redo.clear();
    }

    /** Returns the state to go back to (and remembers `current` for redo), or null. */
    public String undo(String current) { return travel(undo, redo, current); }

    public String redo(String current) { return travel(redo, undo, current); }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public String undoLabel() { return undo.isEmpty() ? null : undo.peekLast().label; }
    public String redoLabel() { return redo.isEmpty() ? null : redo.peekLast().label; }

    public void clear() { undo.clear(); redo.clear(); }

    /** A change was recorded but did nothing: forget it (so ↶ never "undoes" nothing). */
    public void dropIfUnchanged(String current) {
        if (!undo.isEmpty() && undo.peekLast().json.equals(current)) undo.removeLast();
    }

    private String travel(ArrayDeque<Snap> from, ArrayDeque<Snap> to, String current) {
        if (from.isEmpty()) return null;
        Snap s = from.removeLast();
        push(to, new Snap(current, s.label));
        return s.json;
    }

    private void push(ArrayDeque<Snap> stack, Snap s) {
        stack.addLast(s);
        long chars = 0;
        for (Snap x : stack) chars += x.json.length();
        while (stack.size() > maxSteps || (chars > maxChars && stack.size() > 1)) chars -= stack.removeFirst().json.length();
    }
}
