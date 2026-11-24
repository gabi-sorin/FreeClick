package com.freeclick.core;

import java.util.Locale;

/** Human readable (Romanian) one-line descriptions used by the editor and lists. */
public final class ActionFormatter {
    private ActionFormatter() {}

    public static String typeName(Action.Type t) {
        switch (t) {
            case TAP: return "Atingere";
            case LONG_PRESS: return "Apăsare lungă";
            case DOUBLE_TAP: return "Dublu tap";
            case SWIPE: return "Swipe";
            case GESTURE: return "Gest înregistrat";
            case WAIT: return "Pauză";
            case GLOBAL: return "Acțiune sistem";
            case MACRO: return "Sub-macro";
            case WAIT_COLOR: return "Așteaptă culoare";
            case LAUNCH_APP: return "Deschide aplicația";
            case IMAGE: return "Imagine";
            case PRINT: return "Afișează";
            case SET_VAR: return "Variabilă";
            case READ: return "Citește";
            case UNKNOWN: return "Pas dintr-o versiune mai nouă";
            default: return t.name();
        }
    }

    public static String ms(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60000) return trim(String.format(Locale.US, "%.2f", ms / 1000.0)) + " s";
        long s = ms / 1000;
        return (s / 60) + " min " + (s % 60) + " s";
    }

    private static String trim(String n) {
        if (n.indexOf('.') < 0) return n;
        while (n.endsWith("0")) n = n.substring(0, n.length() - 1);
        if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
        return n;
    }

    private static String shorten(String s, int max) {
        if (s == null || s.length() == 0) return "(gol)";
        s = s.replace('\n', ' ');
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String blank(String v) { return v == null || v.trim().length() == 0 ? "(fără nume)" : v.trim(); }

    public static String describe(Action a, MacroLibrary lib) {
        StringBuilder sb = new StringBuilder(typeName(a.type));
        switch (a.type) {
            case TAP:
            case DOUBLE_TAP:
                sb.append(" (").append(Math.round(a.x)).append(", ").append(Math.round(a.y)).append(")");
                break;
            case LONG_PRESS:
                sb.append(" (").append(Math.round(a.x)).append(", ").append(Math.round(a.y)).append(") ")
                        .append(ms(a.duration));
                break;
            case SWIPE:
                sb.append(" (").append(Math.round(a.x)).append(", ").append(Math.round(a.y)).append(") → (")
                        .append(Math.round(a.x2)).append(", ").append(Math.round(a.y2)).append(") ")
                        .append(ms(a.duration));
                break;
            case GESTURE: {
                if (a.isZoom()) {
                    sb.setLength(0);
                    sb.append(a.zoomSpan > 0 ? "Zoom în" : "Zoom out").append(" (2 degete): ").append(Math.abs(a.zoomSpan)).append(" px, ").append(ms(a.duration));
                    break;
                }
                int pts = 0;
                for (Stroke s : a.strokes) pts += s.size();
                sb.append(": ").append(a.strokes.size()).append(a.strokes.size() == 1 ? " deget, " : " degete, ")
                        .append(pts).append(" puncte, ").append(ms(a.duration));
                break;
            }
            case WAIT:
                if (a.isRandomWait()) sb.append(" aleatoare ").append(ms(a.duration)).append(" – ").append(ms(a.durationMax));
                else sb.append(" ").append(ms(a.duration));
                break;
            case IMAGE: {
                if (a.img != null) sb.append(" ").append(a.img.displayName());
                String what = a.press == Action.PRESS_TAP ? (a.allMatches ? ": apasă pe toate" : ": apasă")
                        : a.press == Action.PRESS_LONG ? ": apasă lung" : a.press == Action.PRESS_NONE ? ": așteaptă să apară" : ": așteaptă să dispară";
                sb.append(what);
                if (a.waitMode == Action.MISSING_WAIT) sb.append(a.duration > 0 ? " · max " + ms(a.duration) + (a.stopOnTimeout ? " apoi stop" : "") : " · oricât");
                else if (a.press != Action.PRESS_GONE) sb.append(" · dacă nu e, mergi mai departe");
                if (a.maxHits > 0) sb.append(" · stop după ").append(a.maxHits).append(" găsiri");
                break;
            }
            case PRINT:
                sb.append(": ").append(shorten(a.text, 40));
                break;
            case SET_VAR:
                sb.append(": ").append(blank(a.varName)).append(" = ").append(shorten(a.text, 36));
                break;
            case READ:
                if (a.readMode == Action.READ_COLOR) {
                    sb.append(" culoarea (").append(Math.round(a.x)).append(", ").append(Math.round(a.y)).append(")");
                } else {
                    sb.append(a.readMode == Action.READ_TEXT ? " textul" : " numărul").append(" din (")
                            .append(Math.round(a.x)).append(", ").append(Math.round(a.y)).append(") – (")
                            .append(Math.round(a.x2)).append(", ").append(Math.round(a.y2)).append(")");
                }
                sb.append(" → ").append(blank(a.varName));
                break;
            case LAUNCH_APP:
                sb.append(" ").append(a.packageName == null || a.packageName.length() == 0 ? "(nealeasă)" : a.packageName);
                if (a.duration > 0) sb.append(", așteaptă max ").append(ms(a.duration));
                break;
            case GLOBAL:
                sb.append(": ").append(GlobalActions.name(a.globalAction));
                break;
            case WAIT_COLOR:
                sb.append(a.invert ? " să dispară " : " ").append(ColorMatch.toHex(a.color))
                        .append(" la (").append(Math.round(a.x)).append(", ").append(Math.round(a.y)).append(")")
                        .append(a.duration > 0 ? ", max " + ms(a.duration) + (a.stopOnTimeout ? " apoi stop" : "") : "");
                break;
            case MACRO: {
                Macro m = lib == null ? null : lib.find(a.macroId);
                if (m != null && Macro.KIND_SCRIPT.equals(m.kind)) { // a script step: say so
                    sb.setLength(0);
                    sb.append("📜 Script: ").append(m.name);
                } else {
                    sb.append(": ").append(m == null ? "(lipsă)" : m.name);
                }
                break;
            }
            default: break;
        }
        if (a.repeat > 1) sb.append(" ×").append(a.repeat);
        if (a.delay > 0) sb.append(" · după ").append(ms(a.delay));
        if (!a.enabled) sb.append(" · dezactivat");
        return sb.toString();
    }
}
