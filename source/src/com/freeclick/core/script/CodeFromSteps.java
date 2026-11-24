package com.freeclick.core.script;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;
import com.freeclick.core.GlobalActions;
import com.freeclick.core.Macro;

import java.util.List;
import java.util.Locale;

/**
 * 💻 A recording or a list of targets, as code (2.4): every tap, swipe, press and pause becomes its own line
 * (tap(x, y, durata, 0) with the exact press time, then wait(...) for the pause before the next one), so
 * you can edit it like any script. Steps that are more than a touch (pictures, colours, blocks, free
 * gestures with several fingers) run exactly as before with ruleaza(pasi[k]): the new script keeps the
 * same steps in its list.
 */
public final class CodeFromSteps {
    private CodeFromSteps() {}

    public static String code(Macro m) {
        StringBuilder b = new StringBuilder();
        List<Action> steps = m.actions;
        double speed = Macro.clampSpeed(m.settings.speed);
        b.append("// Din „").append(m.name).append("” (").append(steps.size()).append(steps.size() == 1 ? " pas" : " pași") // tree-ok: top-level list
                .append("). Fiecare linie e o atingere sau o pauză; schimbă-le cum vrei.\n");
        if (speed != 1.0) b.append("// Viteza ").append(fmt(speed)).append("x e deja socotită în pauze și durate.\n");
        boolean complex = false;
        for (Action a : steps) if (!simple(a)) complex = true; // tree-ok: top-level list
        if (complex) b.append("// Pașii cu imagini, culori, blocuri sau gesturi libere rulează din lista „pasi” (ruleaza(pasi[k]))"
                + (speed != 1.0 ? ", la viteza 1x" : "") + ".\n");
        b.append('\n');
        int loops = m.settings.loops;
        String indent = "";
        if (loops != 1) {
            b.append(loops == 0 ? "while (true) {\n" : "for (int runda = 0; runda < " + loops + "; runda++) {\n");
            indent = "    ";
        }
        for (int k = 0; k < steps.size(); k++) step(b, indent, steps.get(k), k, speed); // tree-ok: index = pasi[k]
        if (loops != 1) {
            if (m.settings.loopDelay > 0) b.append(indent).append("wait(").append(ms(m.settings.loopDelay, speed)).append(");   // pauza dintre runde\n");
            b.append("}\n");
        }
        return b.toString();
    }

    /** Steps written as plain calls; everything else goes through ruleaza(pasi[k]). */
    static boolean simple(Action a) {
        switch (a.type) {
            case TAP: case LONG_PRESS: case DOUBLE_TAP: case SWIPE: case WAIT: return true;
            case GLOBAL: return global(a.globalAction) != null;
            default: return false;
        }
    }

    private static String global(int g) {
        if (g == GlobalActions.BACK) return "back()";
        if (g == GlobalActions.HOME) return "home()";
        if (g == GlobalActions.RECENTS) return "recente()";
        if (g == GlobalActions.NOTIFICATIONS) return "notificari()";
        return null;
    }

    private static void step(StringBuilder b, String in, Action a, int k, double speed) {
        if (!a.enabled) {
            b.append(in).append("// (oprit) ").append(ActionFormatter.describe(a, null)).append('\n');
            return;
        }
        if (!simple(a)) {
            b.append(in).append("ruleaza(pasi[").append(k).append("]);   // ").append(ActionFormatter.describe(a, null)).append('\n');
            return;
        }
        String call = call(a, speed);
        if (a.delay > 0) b.append(in).append("wait(").append(ms(a.delay, speed)).append(");\n");
        if (call != null) b.append(in).append(call).append(";\n");
        if (a.repeat > 1 && call != null) { // the step again, with its own pause between repeats
            b.append(in).append("for (int i = 1; i < ").append(a.repeat).append("; i++) {\n");
            if (a.interval > 0) b.append(in).append("    wait(").append(ms(a.interval, speed)).append(");\n");
            b.append(in).append("    ").append(call).append(";\n");
            b.append(in).append("}\n");
        }
    }

    private static String call(Action a, double speed) {
        int x = Math.round(a.x), y = Math.round(a.y), x2 = Math.round(a.x2), y2 = Math.round(a.y2);
        switch (a.type) {
            case TAP: return "tap(" + x + ", " + y + ", " + Math.max(1, a.duration) + ", 0)";
            case LONG_PRESS: return "apasaLung(" + x + ", " + y + ", " + Math.max(1, a.duration) + ", 0)";
            case DOUBLE_TAP: return "dubluTap(" + x + ", " + y + ", 0)";
            case SWIPE: return "swipe(" + x + ", " + y + ", " + x2 + ", " + y2 + ", " + Math.max(1, Math.round(a.duration / speed)) + ", 0)";
            case WAIT:
                if (a.durationMax > a.duration) return "waitRandom(" + ms(a.duration, speed) + ", " + ms(a.durationMax, speed) + ")";
                return a.duration > 0 ? "wait(" + ms(a.duration, speed) + ")" : null;
            case GLOBAL: return global(a.globalAction);
            default: return null;
        }
    }

    private static long ms(long v, double speed) { return Math.max(0, Math.round(v / speed)); }

    private static String fmt(double d) { return d == Math.floor(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.2f", d); }
}
