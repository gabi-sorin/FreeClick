package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place that knows steps can contain other steps (If / Repeat blocks).
 * Everything that looks at "all the steps" of a macro (export, import, cycle checks,
 * scaling, counting, markers, ...) goes through here, so a step inside a block is
 * never missed. tools/check.py rejects direct loops over Macro.actions elsewhere.
 */
public final class ActionTree {
    private ActionTree() {}

    public interface Visitor {
        /** Called for every step, blocks before their contents. Return false to stop the walk. */
        boolean visit(Action a, int depth);
    }

    /** Visit every step (pre-order). Returns false if the visitor stopped early. */
    public static boolean walk(List<Action> list, Visitor v) {
        return walk(list, v, 0);
    }

    private static boolean walk(List<Action> list, Visitor v, int depth) {
        if (list == null) return true;
        for (int i = 0; i < list.size(); i++) {
            Action a = list.get(i);
            if (!v.visit(a, depth)) return false;
            if (!walk(a.children, v, depth + 1)) return false;
            if (!walk(a.elseChildren, v, depth + 1)) return false;
        }
        return true;
    }

    /** Every step in order, flattened (blocks included). */
    public static List<Action> all(List<Action> list) {
        final List<Action> out = new ArrayList<Action>();
        walk(list, new Visitor() {
            public boolean visit(Action a, int depth) { out.add(a); return true; }
        });
        return out;
    }

    /** Every non-block step in order: what the user sees as "the steps". */
    public static List<Action> leaves(List<Action> list) {
        final List<Action> out = new ArrayList<Action>();
        walk(list, new Visitor() {
            public boolean visit(Action a, int depth) { if (!a.isBlock()) out.add(a); return true; }
        });
        return out;
    }

    /** Number of non-block steps. */
    public static int count(List<Action> list) {
        final int[] n = {0};
        walk(list, new Visitor() {
            public boolean visit(Action a, int depth) { if (!a.isBlock()) n[0]++; return true; }
        });
        return n[0];
    }

    public static boolean isEmpty(List<Action> list) { return count(list) == 0; }

    /** True if any step (at any depth) has the given type. */
    public static boolean anyOfType(List<Action> list, final Action.Type type) {
        return !walk(list, new Visitor() {
            public boolean visit(Action a, int depth) { return a.type != type; }
        });
    }

    /** 1-based position of a step among the non-block steps, or 0 if it is not there. */
    public static int positionOf(List<Action> list, Action target) {
        List<Action> l = leaves(list);
        for (int i = 0; i < l.size(); i++) if (l.get(i) == target) return i + 1;
        return 0;
    }

    /** Remove one step (wherever it is). True if found. */
    public static boolean remove(List<Action> list, Action target) {
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            Action a = list.get(i);
            if (a == target) { list.remove(i); return true; }
            if (remove(a.children, target) || remove(a.elseChildren, target)) return true;
        }
        return false;
    }

    /**
     * Remove the last ordinary step (the "−" button). Never removes a whole block: it goes
     * inside the last block instead; a block left empty stays (the user can delete it).
     */
    public static Action removeLastLeaf(List<Action> list) {
        if (list == null) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Action a = list.get(i);
            if (a.isBlock()) {
                Action inner = removeLastLeaf(a.elseChildren != null && !a.elseChildren.isEmpty() ? a.elseChildren : a.children);
                if (inner == null && a.elseChildren != null && !a.elseChildren.isEmpty()) inner = removeLastLeaf(a.children);
                if (inner != null) return inner;
                continue;
            }
            return list.remove(i);
        }
        return null;
    }

    /** Deepest nesting level (0 = no blocks). */
    public static int depth(List<Action> list) {
        final int[] d = {0};
        walk(list, new Visitor() {
            public boolean visit(Action a, int depth) { if (a.isBlock()) d[0] = Math.max(d[0], depth + 1); return true; }
        });
        return d[0];
    }
}
