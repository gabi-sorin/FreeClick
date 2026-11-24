package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A saved automation: ordered actions plus how to play them back. */
public final class Macro {

    public static final class Settings {
        /** Number of full runs; 0 = repeat forever. */
        public int loops = 1;
        /** Pause between runs (ms, at 1x). */
        public long loopDelay = 500;
        /** Playback speed multiplier (0.1 .. 10). */
        public double speed = 1.0;
        /** Random offset applied to tap/swipe positions, in pixels (0 = off). */
        public int jitterPx = 0;
        /** Random +/- percentage applied to delays (0 = off). */
        public int jitterTimePct = 0;
        /** Stop automatically after this many ms of playback (0 = never). */
        public long stopAfterMs = 0;
        /** Scale coordinates if the current screen size differs from the recording. */
        public boolean scaleToScreen = true;
        /** Hide all overlays while gestures are injected (for apps with tapjacking filters). */
        public boolean safeMode = false;
        /** Record a screen video while this macro plays. */
        public boolean recordVideo = false;
        /** Pause playback if the foreground app changes (0/empty = off). */
        public String lockToPackage = "";
        /** Open this app (package name) before playing; empty = play over whatever is open. */
        public String launchPackage = "";
        /** Manual mode: pause before each target (ms), used for new targets and "apply to all". */
        public long tapInterval = 200;
        /** Manual mode: press time of taps (ms). */
        public long tapDuration = 50;
        /** Manual mode: duration of swipes (ms). */
        public long swipeDuration = 300;
        /** 2.3: taps one after another are done at the same moment, like several fingers ("Atinge deodată"). */
        public boolean syncTaps = false;

        public Settings copy() {
            Settings s = new Settings();
            s.loops = loops; s.loopDelay = loopDelay; s.speed = speed;
            s.jitterPx = jitterPx; s.jitterTimePct = jitterTimePct;
            s.stopAfterMs = stopAfterMs; s.scaleToScreen = scaleToScreen;
            s.safeMode = safeMode; s.recordVideo = recordVideo;
            s.lockToPackage = lockToPackage;
            s.launchPackage = launchPackage;
            s.tapInterval = tapInterval; s.tapDuration = tapDuration; s.swipeDuration = swipeDuration;
            s.syncTaps = syncTaps;
            return s;
        }
    }

    /** Which app mode a macro belongs to (decides which list/panel it opens in). */
    public static final String KIND_MANUAL = "manual";
    public static final String KIND_RECORD = "record";
    public static final String KIND_COMBO = "combo";
    /** A script (1.9): its code is in `script`, its own steps ('pasi' in the code) in `actions`. */
    public static final String KIND_SCRIPT = "script";

    public static final double MIN_SPEED = 0.1;
    public static final double MAX_SPEED = 10.0;

    public String id;
    public String name;
    /** KIND_MANUAL, KIND_RECORD or KIND_COMBO. */
    public String kind = KIND_MANUAL;
    public long createdAt;
    public long updatedAt;
    /** Screen size (px) when the macro was created; used for scaling. */
    public int screenWidth;
    public int screenHeight;
    public final List<Action> actions = new ArrayList<Action>();
    public Settings settings = new Settings();
    /** KIND_SCRIPT: the code. */
    public String script = "";

    public Macro(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = createdAt;
    }

    public static double clampSpeed(double s) {
        if (Double.isNaN(s)) return 1.0;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, s));
    }

    /** Deep copy with a new id (used by "Duplicate"). */
    public Macro duplicate(String newName) {
        Macro m = new Macro(newName);
        m.kind = kind;
        m.screenWidth = screenWidth;
        m.screenHeight = screenHeight;
        for (Action a : actions) m.actions.add(a.copy()); // tree-ok: copy() is deep
        m.settings = settings.copy();
        m.script = script;
        return m;
    }

    /** Kind for files written before kinds existed: sub-macros = combo, recorded paths = record. */
    public static String inferKind(Macro m) {
        boolean gesture = false;
        for (Action a : ActionTree.all(m.actions)) {
            if (a.type == Action.Type.MACRO) return KIND_COMBO;
            if (a.type == Action.Type.GESTURE) gesture = true;
        }
        return gesture ? KIND_RECORD : KIND_MANUAL;
    }

    public static boolean isValidKind(String k) {
        return KIND_MANUAL.equals(k) || KIND_RECORD.equals(k) || KIND_COMBO.equals(k) || KIND_SCRIPT.equals(k);
    }

    public int countEnabled() {
        int n = 0;
        for (Action a : ActionTree.leaves(actions)) if (a.enabled) n++;
        return n;
    }
}
