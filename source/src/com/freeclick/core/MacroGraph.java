package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Analysis of combination (macro-calls-macro) structure. */
public final class MacroGraph {
    private MacroGraph() {}

    /**
     * Returns the names along a reference cycle reachable from root
     * (e.g. [A, B, A]) or null if there is none.
     */
    public static List<String> findCycle(Macro root, MacroLibrary lib) {
        List<Macro> stack = new ArrayList<Macro>();
        return dfs(root, lib, stack, new HashSet<String>());
    }

    private static List<String> dfs(Macro m, MacroLibrary lib, List<Macro> stack, Set<String> done) {
        if (m == null) return null;
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).id.equals(m.id)) {
                List<String> cycle = new ArrayList<String>();
                for (int j = i; j < stack.size(); j++) cycle.add(stack.get(j).name);
                cycle.add(m.name);
                return cycle;
            }
        }
        if (done.contains(m.id)) return null;
        stack.add(m);
        for (Action a : ActionTree.all(m.actions)) {
            if (a.type == Action.Type.MACRO && a.enabled) {
                Macro child = lib.find(a.macroId);
                List<String> c = dfs(child, lib, stack, done);
                if (c != null) return c;
            }
        }
        stack.remove(stack.size() - 1);
        done.add(m.id);
        return null;
    }

    /** True if adding a call parent -> child would create a loop. */
    public static boolean wouldCreateCycle(Macro parent, String childId, MacroLibrary lib) {
        if (childId == null) return false;
        if (childId.equals(parent.id)) return true;
        return reaches(lib.find(childId), parent.id, lib, new HashSet<String>());
    }

    private static boolean reaches(Macro from, String targetId, MacroLibrary lib, Set<String> seen) {
        if (from == null || !seen.add(from.id)) return false;
        if (from.id.equals(targetId)) return true;
        for (Action a : ActionTree.all(from.actions)) {
            if (a.type == Action.Type.MACRO && a.macroId != null) {
                if (a.macroId.equals(targetId)) return true;
                if (reaches(lib.find(a.macroId), targetId, lib, seen)) return true;
            }
        }
        return false;
    }

    /** True if the macro (or any sub-macro) has a colour condition, i.e. needs screen capture. */
    public static boolean usesScreenCapture(Macro m, MacroLibrary lib) {
        return usesCapture(m, lib, new HashSet<String>());
    }

    private static boolean usesCapture(Macro m, MacroLibrary lib, Set<String> seen) {
        if (m == null || !seen.add(m.id)) return false;
        for (Action a : ActionTree.all(m.actions)) {
            if (!a.enabled) continue;
            if (a.type == Action.Type.WAIT_COLOR || a.type == Action.Type.IMAGE || a.type == Action.Type.READ) return true;
            if (a.type == Action.Type.MACRO && usesCapture(lib.find(a.macroId), lib, seen)) return true;
        }
        return false;
    }

    /** True if a macro (or one it runs) reads numbers or text from the screen (needs whole frames: Android 11+). */
    public static boolean readsText(Macro m, MacroLibrary lib) {
        return readsText(m, lib, new HashSet<String>());
    }

    private static boolean readsText(Macro m, MacroLibrary lib, Set<String> seen) {
        if (m == null || !seen.add(m.id)) return false;
        for (Action a : ActionTree.all(m.actions)) {
            if (!a.enabled) continue;
            if (a.type == Action.Type.READ && a.readMode != Action.READ_COLOR) return true;
            if (a.type == Action.Type.MACRO && readsText(lib.find(a.macroId), lib, seen)) return true;
        }
        return false;
    }

    /** Every image file a macro needs (its own steps and those of the macros it runs). */
    public static Set<String> imageIds(Macro m, MacroLibrary lib) {
        Set<String> out = new java.util.TreeSet<String>();
        collectImages(m, lib, out, new HashSet<String>());
        return out;
    }

    private static void collectImages(Macro m, MacroLibrary lib, Set<String> out, Set<String> seen) {
        if (m == null || !seen.add(m.id)) return;
        for (Action a : ActionTree.all(m.actions)) {
            if (a.type == Action.Type.IMAGE && a.img != null && a.img.id != null) out.add(a.img.id);
            if (a.type == Action.Type.MACRO && lib != null) collectImages(lib.find(a.macroId), lib, out, seen);
        }
    }

    /** Ids referenced by root (directly) that are not in the library. */
    public static List<String> missingReferences(Macro root, MacroLibrary lib) {
        List<String> out = new ArrayList<String>();
        for (Action a : ActionTree.all(root.actions)) {
            if (a.type == Action.Type.MACRO && a.enabled && lib.find(a.macroId) == null) out.add(a.macroId);
        }
        return out;
    }

    /** Estimated time of one run at 1x speed (sub-macros included, without jitter). */
    public static long estimateDurationMs(Macro m, MacroLibrary lib) {
        return estimate(m, lib, new HashSet<String>());
    }

    private static long estimate(Macro m, MacroLibrary lib, Set<String> path) {
        if (m == null || !path.add(m.id)) return 0;
        long total = estimateList(m.actions, lib, path);
        path.remove(m.id);
        return total;
    }

    private static long estimateList(List<Action> list, MacroLibrary lib, Set<String> path) {
        if (list == null) return 0;
        long total = 0;
        for (Action a : list) { // tree-ok: recurses into blocks below
            if (!a.enabled) continue;
            long one;
            if (a.type == Action.Type.MACRO) one = estimate(lib.find(a.macroId), lib, path);
            else if (a.isBlock()) one = Math.max(estimateList(a.children, lib, path), estimateList(a.elseChildren, lib, path));
            else one = a.ownDuration();
            int reps = Math.max(1, a.repeat);
            total += a.delay + one * reps + a.interval * (reps - 1);
        }
        return total;
    }

    /**
     * True if playing the macro would execute at least one step (enabled, known, and not only
     * calls to empty or missing sub-macros). Checked before playing instead of discovering an
     * empty pass at run time.
     */
    public static boolean hasRunnableSteps(Macro m, MacroLibrary lib) {
        return runnable(m, lib, new HashSet<String>());
    }

    private static boolean runnable(Macro m, MacroLibrary lib, Set<String> seen) {
        if (m == null || !seen.add(m.id)) return false;
        return runnableList(m.actions, lib, seen);
    }

    private static boolean runnableList(List<Action> list, MacroLibrary lib, Set<String> seen) {
        if (list == null) return false;
        for (Action a : list) { // tree-ok: recurses into blocks below
            if (!a.enabled || a.type == Action.Type.UNKNOWN) continue;
            if (a.type == Action.Type.MACRO) {
                Macro child = lib.find(a.macroId);
                if (child != null && Macro.KIND_SCRIPT.equals(child.kind)) return true; // a script step always runs
                if (runnable(child, lib, seen)) return true;
            } else if (a.isBlock()) {
                if (runnableList(a.children, lib, seen) || runnableList(a.elseChildren, lib, seen)) return true;
            } else {
                return true;
            }
        }
        return false;
    }
}
