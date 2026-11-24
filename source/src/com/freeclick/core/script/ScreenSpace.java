package com.freeclick.core.script;

/**
 * The coordinates written in a script belong to the screen it was made for (its "design" screen,
 * saved with it). On a screenshot of another size, or on another phone, they are scaled.
 * Portrait/landscape follows the screen at hand: the same phone held sideways keeps 1:1.
 */
public final class ScreenSpace {
    private ScreenSpace() {}

    /** The design size in the orientation of a screen (or picture) of size w × h. */
    public static int[] designFor(int designW, int designH, int w, int h) {
        if (designW <= 0 || designH <= 0) return new int[]{w, h};
        int big = Math.max(designW, designH), small = Math.min(designW, designH);
        return w > h ? new int[]{big, small} : new int[]{small, big};
    }

    /** True if a w × h picture has (about) the proportions of the design screen. */
    public static boolean sameShape(int designW, int designH, int w, int h) {
        int[] d = designFor(designW, designH, w, h);
        double a = d[0] / (double) d[1], b = w / (double) h;
        return Math.abs(a - b) / a < 0.02;
    }
}
