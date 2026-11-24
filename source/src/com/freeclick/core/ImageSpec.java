package com.freeclick.core;

/**
 * Which image an image step looks for and how: the cut-out (stored as a PNG file named by
 * its content hash), where it was cut on which screen, where to tap on it, where to search
 * and how strictly.
 */
public final class ImageSpec {
    public static final int PRECISION_STRICT = 93, PRECISION_NORMAL = 85, PRECISION_TOLERANT = 78;

    /** Content hash of the PNG (also its file name). */
    public String id = "";
    /** Size of the cut-out (px). */
    public int w, h;
    /** Where it was cut (top-left, px of the capture screen) and that screen's size. */
    public float capX, capY;
    public int capScreenW, capScreenH;
    /** Tap point relative to the cut-out's top-left (px of the cut-out). Default: the centre. */
    public float tapDx, tapDy;
    /** Search zone (capture-screen px); all 0 = whole screen. */
    public float zx1, zy1, zx2, zy2;
    /** 70..98; 78 tolerant (also other sizes), 85 normal, 93 strict. */
    public int precision = PRECISION_NORMAL;
    /** The cut-out was snapped to the object's edges (then its middle is not searched alone). */
    public boolean tight;
    /** Name shown in messages ("Collect"); empty = "imaginea". */
    public String name = "";

    public boolean hasZone() { return zx2 > zx1 && zy2 > zy1; }
    public boolean multiScale() { return precision <= PRECISION_TOLERANT; }

    public String displayName() { return name == null || name.trim().isEmpty() ? "imaginea" : "„" + name.trim() + "”"; }

    public ImageSpec copy() {
        ImageSpec s = new ImageSpec();
        s.id = id; s.w = w; s.h = h; s.capX = capX; s.capY = capY; s.capScreenW = capScreenW; s.capScreenH = capScreenH;
        s.tapDx = tapDx; s.tapDy = tapDy; s.zx1 = zx1; s.zy1 = zy1; s.zx2 = zx2; s.zy2 = zy2;
        s.precision = precision; s.tight = tight; s.name = name;
        return s;
    }

    /** True if an id can safely be a file name (hex content hash). */
    public static boolean isSafeId(String id) {
        return id != null && id.matches("[0-9a-f]{16,64}");
    }
}
