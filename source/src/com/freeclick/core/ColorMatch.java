package com.freeclick.core;

import java.util.Locale;

/** Colour helpers for the "wait for colour" condition. Colours are 0xRRGGBB. */
public final class ColorMatch {
    private ColorMatch() {}

    /** Largest per-channel difference (0 = identical, 255 = opposite). */
    public static int distance(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    public static boolean matches(int actual, int target, int tolerance) {
        return distance(actual, target) <= tolerance;
    }

    public static String toHex(int rgb) {
        return String.format(Locale.US, "#%06X", rgb & 0xFFFFFF);
    }

    /** Accepts "#RRGGBB", "RRGGBB" or "#RGB"; invalid input gives black. */
    public static int parseHex(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() == 8) s = s.substring(2); // AARRGGBB -> RRGGBB
        if (s.length() != 6) return 0;
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
