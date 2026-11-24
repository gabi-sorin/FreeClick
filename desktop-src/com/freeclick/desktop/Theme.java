/*
 * Decompiled with CFR 0.152.
 */
package com.freeclick.desktop;

import com.freeclick.core.Action;
import java.awt.Color;

final class Theme {
    static final Color INDIGO = new Color(0x11171E);
    static final Color INDIGO_DARK = new Color(0xEEF3F8);
    static final Color TEAL = new Color(0x38D6B0);
    static final Color RED = new Color(14427686);
    static final Color AMBER = new Color(16096779);
    static final Color GREY = new Color(0xA1AEBB);
    static final Color LIGHT = new Color(0x1B242E);
    static final Color LINE = new Color(0x303A46);
    static final Color INK = new Color(0xEEF3F8);
    static final Color BAR_BG = new Color(20, 15, 69, 236);
    static final Color BAR_BUTTON = new Color(255, 255, 255, 26);
    static final Color BAR_BUTTON_HOVER = new Color(255, 255, 255, 56);
    static final Color BAR_ICON = new Color(0xF8F7FF);
    static final Color BAR_TEXT = new Color(13618160);
    static final Color BAR_PLAY = TEAL;
    static final Color BAR_RECORD = new Color(0xEF4444);
    static final int BAR_BUTTON_SIZE = 40;
    static final int BAR_GAP = 4;
    static final int BAR_PAD = 6;
    static final int BAR_RADIUS = 16;
    static final int BAR_GRIP = 14;
    static final int MARKER_SIZE = 40;
    static final int MARKER_RING = 3;
    static final Color MARKER_TAP = new Color(0x38D6B0);
    static final Color MARKER_LONG = new Color(16347926);
    static final Color MARKER_DOUBLE = new Color(9133302);
    static final Color MARKER_SWIPE = new Color(3900150);
    static final Color MARKER_COLOR = new Color(15381256);
    static final Color MARKER_IMAGE = new Color(15485081);
    static final Color MARKER_READ = new Color(2278750);
    static final Color MARKER_MOUSE = new Color(6583435);
    static final Color MARKER_FILL_ALPHA = new Color(0, 0, 0, 150);
    static final Color MARKER_TEXT = Color.WHITE;
    static final Color MARKER_SELECTED = new Color(16638023);
    static final Color MARKER_ACTIVE = new Color(0xFFFFFF);
    static final Color MARKER_OFF = new Color(10265519);
    static final float LINE_WIDTH = 4.0f;
    static final Color ZONE_FILL = new Color(34, 197, 94, 40);
    static final Color WINDOW_BG = new Color(0x11171E);
    static final Color LIST_SELECTED = new Color(0x29403F);
    static final Color LIST_PLAYING = new Color(0x253A32);
    static final int RADIUS = 10;
    static final int GAP = 8;
    static final float TEXT = 14.0f;
    static final float TEXT_SMALL = 12.5f;
    static final float TITLE = 17.0f;

    private Theme() {
    }

    static Color marker(Action action) {
        switch (action.type) {
            case TAP: {
                return MARKER_TAP;
            }
            case LONG_PRESS: {
                return MARKER_LONG;
            }
            case DOUBLE_TAP: {
                return MARKER_DOUBLE;
            }
            case SWIPE: 
            case GESTURE: {
                return MARKER_SWIPE;
            }
            case WAIT_COLOR: {
                return MARKER_COLOR;
            }
            case IMAGE: {
                return MARKER_IMAGE;
            }
            case READ: {
                return MARKER_READ;
            }
        }
        return MARKER_MOUSE;
    }

    static Color alpha(Color color, int n) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, n)));
    }
}

