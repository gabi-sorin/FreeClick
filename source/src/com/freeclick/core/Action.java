package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A single step of a macro. Coordinates are in screen pixels of the recording screen. */
public final class Action {

    public enum Type {
        TAP,         // x,y, duration = press time
        LONG_PRESS,  // x,y, duration = press time
        DOUBLE_TAP,  // x,y
        SWIPE,       // x,y -> x2,y2 over duration (straight line)
        GESTURE,     // recorded free-form path(s), one Stroke per finger
        WAIT,        // do nothing for duration
        GLOBAL,      // system action (back, home, ...), see GlobalActions
        MACRO,       // run another macro (combination mode)
        WAIT_COLOR,  // wait until the pixel at x,y has (or loses) a colour; duration = timeout (0 = forever)
        LAUNCH_APP,  // open packageName; duration = how long to wait for it to come to the front (0 = don't wait)
        IMAGE,       // look for img on the screen, then tap it / wait for it / wait until it is gone (see press, waitMode)
        PRINT,       // write text (with {expressions}) to the console
        SET_VAR,     // varName = expression (text)
        READ,        // read a number / text from the zone x,y-x2,y2, or the colour at x,y, into varName
        UNKNOWN      // a step written by a newer FreeClick: kept unchanged in the file, never run
    }

    public static final long DEFAULT_TAP_MS = 50;
    public static final long DEFAULT_LONG_PRESS_MS = 800;
    public static final long DEFAULT_SWIPE_MS = 300;
    public static final long DOUBLE_TAP_GAP_MS = 90;

    public Type type;
    /** Pause before this action runs (ms, at 1x speed). */
    public long delay;
    /** Press / swipe / wait duration (ms). */
    public long duration;
    public float x, y, x2, y2;
    /** Free-form strokes (GESTURE only). */
    public List<Stroke> strokes = new ArrayList<Stroke>();
    /** GLOBAL only. */
    public int globalAction;
    /** MACRO only: id of the macro to run. */
    public String macroId;
    /** How many times to run this action (>= 1). */
    public int repeat = 1;
    /** Pause between repeats (ms, at 1x speed). */
    public long interval = 100;
    public boolean enabled = true;
    /** Optional note shown in the editor. */
    public String label = "";
    /** WAIT_COLOR: target colour (0xRRGGBB, alpha ignored). */
    public int color;
    /** WAIT_COLOR: max difference per channel (0..255) still counted as a match. */
    public int tolerance = 24;
    /** WAIT_COLOR: wait until the colour is NOT there any more. */
    public boolean invert;
    /** WAIT_COLOR: if the timeout expires, stop the macro (otherwise just continue). */
    public boolean stopOnTimeout;
    /** WAIT: if larger than duration, the pause is random between duration and durationMax. */
    public long durationMax;
    /** LAUNCH_APP: the app to open. */
    public String packageName = "";
    /**
     * Blocks (If / Repeat, added in a later version): the steps inside, and the "else" steps.
     * Null for ordinary steps. Code that visits steps must go through ActionTree, never only
     * the top-level list, or steps inside blocks would be missed.
     */
    public List<Action> children;
    public List<Action> elseChildren;
    /** UNKNOWN: the original JSON object, written back unchanged. */
    public Map<String, Object> raw;

    // ---- IMAGE
    public static final int PRESS_TAP = 0, PRESS_LONG = 1, PRESS_NONE = 2, PRESS_GONE = 3;
    public static final int MISSING_CONTINUE = 0, MISSING_WAIT = 1;
    public ImageSpec img;
    /** What to do when it is there: tap, long press, nothing (just wait for it), wait until it is gone. */
    public int press = PRESS_TAP;
    /** If it is not there: carry on (MISSING_CONTINUE) or wait up to `duration` (0 = forever). */
    public int waitMode = MISSING_CONTINUE;
    /** Tap every copy on screen (top-most first), not only the top-most. */
    public boolean allMatches;
    /** Stop the macro after it was found this many times (0 = never). */
    public int maxHits;
    /** Press time of the tap / long press on the image. */
    public long pressMs = DEFAULT_TAP_MS;

    // ---- PRINT / SET_VAR / READ (1.8)
    public static final int READ_NUMBER = 0, READ_TEXT = 1, READ_COLOR = 2;
    /** PRINT: the text with {expressions}; SET_VAR: the expression. */
    public String text = "";
    /** SET_VAR / READ: the variable written. */
    public String varName = "";
    /** READ: what is read (READ_NUMBER / READ_TEXT from the zone, READ_COLOR at x,y). */
    public int readMode = READ_NUMBER;

    public Action(Type type) { this.type = type; }

    public static Action print(String text) {
        Action a = new Action(Type.PRINT);
        a.text = text == null ? "" : text;
        return a;
    }

    public static Action setVar(String name, String expression) {
        Action a = new Action(Type.SET_VAR);
        a.varName = name == null ? "" : name;
        a.text = expression == null ? "" : expression;
        return a;
    }

    /** Read from the zone x1,y1 - x2,y2 (READ_NUMBER or READ_TEXT). */
    public static Action read(float x1, float y1, float x2, float y2, int mode, String var) {
        Action a = new Action(Type.READ);
        a.x = Math.min(x1, x2); a.y = Math.min(y1, y2);
        a.x2 = Math.max(x1, x2); a.y2 = Math.max(y1, y2);
        a.readMode = mode == READ_TEXT ? READ_TEXT : READ_NUMBER;
        a.varName = var == null ? "" : var;
        return a;
    }

    public static Action readColor(float x, float y, String var) {
        Action a = new Action(Type.READ);
        a.x = x; a.y = y; a.x2 = x; a.y2 = y;
        a.readMode = READ_COLOR;
        a.varName = var == null ? "" : var;
        return a;
    }

    public static Action tap(float x, float y) {
        Action a = new Action(Type.TAP);
        a.x = x; a.y = y; a.duration = DEFAULT_TAP_MS;
        return a;
    }

    public static Action longPress(float x, float y, long ms) {
        Action a = new Action(Type.LONG_PRESS);
        a.x = x; a.y = y; a.duration = ms;
        return a;
    }

    public static Action doubleTap(float x, float y) {
        Action a = new Action(Type.DOUBLE_TAP);
        a.x = x; a.y = y; a.duration = DEFAULT_TAP_MS;
        return a;
    }

    public static Action swipe(float x, float y, float x2, float y2, long ms) {
        Action a = new Action(Type.SWIPE);
        a.x = x; a.y = y; a.x2 = x2; a.y2 = y2; a.duration = ms;
        return a;
    }

    /**
     * Zoom with two fingers around (cx, cy): side by side, moving apart (zoomIn) or together.
     * {@code zoomSpan} keeps it editable (the fingers follow the marker); the strokes are also
     * stored, so a version without zoom targets still plays it.
     */
    public static Action zoom(float cx, float cy, int span, long ms, boolean in) {
        Action a = new Action(Type.GESTURE);
        a.x = cx;
        a.y = cy;
        a.zoomSpan = in ? Math.abs(span) : -Math.abs(span);
        a.duration = Math.max(1, ms);
        a.rebuildZoom();
        return a;
    }

    /** Two-finger zoom: + = apart (zoom in), − = together (zoom out); 0 = a recorded gesture. */
    public int zoomSpan;

    public boolean isZoom() { return type == Type.GESTURE && zoomSpan != 0; }

    /** The two strokes of a zoom, from its centre, span and duration. */
    public void rebuildZoom() {
        if (!isZoom()) return;
        int span = Math.max(80, Math.abs(zoomSpan));
        boolean in = zoomSpan > 0;
        int near = Math.max(20, span / 6), far = Math.max(near + 40, span / 2);
        int from = in ? near : far, to = in ? far : near;
        strokes = new ArrayList<Stroke>();
        for (int side = -1; side <= 1; side += 2) {
            Stroke s = new Stroke();
            s.add(x + side * from, y, 0);
            s.add(x + side * to, y, Math.max(1, duration));
            strokes.add(s);
        }
    }

    public static Action gesture(List<Stroke> strokes) {
        Action a = new Action(Type.GESTURE);
        a.strokes = new ArrayList<Stroke>(strokes);
        long end = 0;
        for (Stroke s : strokes) end = Math.max(end, s.endTime());
        a.duration = end;
        if (!strokes.isEmpty() && strokes.get(0).size() > 0) {
            a.x = strokes.get(0).x(0);
            a.y = strokes.get(0).y(0);
        }
        return a;
    }

    public static Action waitFor(long ms) {
        Action a = new Action(Type.WAIT);
        a.duration = ms;
        return a;
    }

    public static Action global(int globalAction) {
        Action a = new Action(Type.GLOBAL);
        a.globalAction = globalAction;
        return a;
    }

    public static Action waitColor(float x, float y, int rgb, long timeoutMs) {
        Action a = new Action(Type.WAIT_COLOR);
        a.x = x; a.y = y; a.color = rgb & 0xFFFFFF; a.duration = timeoutMs;
        return a;
    }

    public static Action randomWait(long minMs, long maxMs) {
        Action a = new Action(Type.WAIT);
        a.duration = Math.max(0, Math.min(minMs, maxMs));
        a.durationMax = Math.max(minMs, maxMs);
        return a;
    }

    public static Action image(ImageSpec spec) {
        Action a = new Action(Type.IMAGE);
        a.img = spec;
        a.duration = 30000;
        a.x = spec.capX + spec.tapDx;
        a.y = spec.capY + spec.tapDy;
        return a;
    }

    public static Action launchApp(String pkg, long waitMs) {
        Action a = new Action(Type.LAUNCH_APP);
        a.packageName = pkg == null ? "" : pkg;
        a.duration = Math.max(0, waitMs);
        return a;
    }

    /** True for a WAIT whose length is picked at random each time. */
    public boolean isRandomWait() { return type == Type.WAIT && durationMax > duration; }

    /** True if this step contains other steps. */
    public boolean isBlock() { return children != null || elseChildren != null; }

    public static Action macro(String macroId, int times) {
        Action a = new Action(Type.MACRO);
        a.macroId = macroId;
        a.repeat = Math.max(1, times);
        return a;
    }

    /** True if this action is executed with dispatchGesture. */
    public boolean isGesture() {
        return type == Type.TAP || type == Type.LONG_PRESS || type == Type.DOUBLE_TAP
                || type == Type.SWIPE || type == Type.GESTURE;
    }

    /** True if the action can be shown/edited as an on-screen marker (manual mode). */
    public boolean isMarkerEditable() {
        return type == Type.TAP || type == Type.LONG_PRESS || type == Type.DOUBLE_TAP || type == Type.SWIPE
                || type == Type.WAIT_COLOR || type == Type.IMAGE || type == Type.READ || isZoom();
    }

    public Action copy() {
        Action a = new Action(type);
        a.delay = delay; a.duration = duration;
        a.x = x; a.y = y; a.x2 = x2; a.y2 = y2;
        for (Stroke s : strokes) a.strokes.add(s.copy());
        a.globalAction = globalAction;
        a.macroId = macroId;
        a.repeat = repeat; a.interval = interval;
        a.enabled = enabled; a.label = label;
        a.color = color; a.tolerance = tolerance; a.invert = invert; a.stopOnTimeout = stopOnTimeout;
        a.durationMax = durationMax;
        a.packageName = packageName;
        if (children != null) { a.children = new ArrayList<Action>(); for (Action c : children) a.children.add(c.copy()); }
        if (elseChildren != null) { a.elseChildren = new ArrayList<Action>(); for (Action c : elseChildren) a.elseChildren.add(c.copy()); }
        if (raw != null) a.raw = Json.deepCopy(raw);
        if (img != null) a.img = img.copy();
        a.press = press; a.waitMode = waitMode; a.allMatches = allMatches; a.maxHits = maxHits; a.pressMs = pressMs;
        a.text = text; a.varName = varName; a.readMode = readMode;
        a.zoomSpan = zoomSpan;
        return a;
    }

    /** Duration of the action itself at 1x speed, excluding delay and repeats. */
    public long ownDuration() {
        switch (type) {
            case DOUBLE_TAP: return duration * 2 + DOUBLE_TAP_GAP_MS;
            case GLOBAL: return 0;
            case MACRO: return 0;
            case WAIT_COLOR: return 0; // unknown: depends on the screen
            case LAUNCH_APP: return 0; // depends on the app
            case IMAGE: return press == PRESS_TAP || press == PRESS_LONG ? pressMs : 0; // plus the search
            case UNKNOWN: return 0;
            case PRINT: case SET_VAR: case READ: return 0;
            case WAIT: return isRandomWait() ? (duration + durationMax) / 2 : Math.max(0, duration);
            default: return Math.max(0, duration);
        }
    }
}
