package com.freeclick.core;

/**
 * Maps coordinates recorded on one screen size to the current one, so a macro
 * recorded on a 1080x2400 phone still hits the same buttons on 720x1600, or
 * after the resolution setting changes.
 */
public final class CoordinateScaler {
    public final float sx, sy;

    public static final CoordinateScaler IDENTITY = new CoordinateScaler(1f, 1f);

    private CoordinateScaler(float sx, float sy) { this.sx = sx; this.sy = sy; }

    public static CoordinateScaler of(int recordedW, int recordedH, int currentW, int currentH, boolean enabled) {
        if (!enabled || recordedW <= 0 || recordedH <= 0 || currentW <= 0 || currentH <= 0) return IDENTITY;
        if (recordedW == currentW && recordedH == currentH) return IDENTITY;
        return new CoordinateScaler((float) currentW / recordedW, (float) currentH / recordedH);
    }

    public float x(float x) { return x * sx; }
    public float y(float y) { return y * sy; }
    public boolean isIdentity() { return sx == 1f && sy == 1f; }
}
