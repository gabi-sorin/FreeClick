package com.freeclick.core.script;

import com.freeclick.core.Action;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.Stroke;

import java.util.ArrayList;
import java.util.List;

/**
 * "▶ Simulează pe captură": runs a script without touching anything, on a virtual clock, with a
 * still picture as its screen. Records where and when it would touch, and what it would print.
 * The picture never changes, so it shows the first pass; it stops after a number of touches,
 * of virtual seconds, or of real milliseconds (a loop that only computes).
 */
public final class ScriptSimulator {
    private ScriptSimulator() {}

    /** The still screen, in the script's own coordinates. */
    public interface Screen {
        int width();
        int height();
        /** 0xRRGGBB, or -1 outside the picture. */
        int pixel(int x, int y);
        /** The text read in the zone (null if it cannot be read). */
        String read(int x1, int y1, int x2, int y2, boolean number);
        /** Tap points of the image on the picture, top-most first (null if it cannot be searched). */
        List<int[]> find(ImageSpec spec, boolean all);
    }

    public static final class Touch {
        /** Virtual ms from the start. */
        public final long time;
        /** Script line that asked for it (0 if unknown). */
        public final int line;
        public final Action.Type type;
        public final int x, y, x2, y2;
        /** Done by 'ruleaza(pasi…)' (a step of the list), not by a line of code directly. */
        public final boolean fromSteps;

        Touch(long time, int line, Action.Type type, int x, int y, int x2, int y2, boolean fromSteps) {
            this.time = time; this.line = line; this.type = type;
            this.x = x; this.y = y; this.x2 = x2; this.y2 = y2; this.fromSteps = fromSteps;
        }

        public boolean isSwipe() { return type == Action.Type.SWIPE || type == Action.Type.GESTURE; }
    }

    public static final class Printed {
        public final long time;
        public final String text;
        Printed(long time, String text) { this.time = time; this.text = text; }
    }

    public static final String END_DONE = "done", END_TOUCHES = "touches", END_TIME = "time", END_REAL = "real", END_STOP = "stop",
            END_ERROR = "error";

    public static final class Result {
        public final List<Touch> touches = new ArrayList<Touch>();
        public final List<Printed> prints = new ArrayList<Printed>();
        public String end = END_DONE;
        public ScriptError error;
        public long virtualMs;

        /** In Romanian, for the bar under the picture. */
        public String describeEnd() {
            if (END_ERROR.equals(end) && error != null) return "eroare: " + error.describe();
            if (END_TOUCHES.equals(end)) return "m-am oprit după " + touches.size() + " atingeri";
            if (END_TIME.equals(end)) return "m-am oprit după " + virtualMs / 1000 + " s simulate";
            if (END_REAL.equals(end)) return "m-am oprit: scriptul calculează fără să atingă sau să aștepte";
            if (END_STOP.equals(end)) return "scriptul se oprește singur (stop())";
            return "scriptul se termină";
        }
    }

    /**
     * A coarse fingerprint of a zone: a grid of up to 24 × 24 samples, colours rounded to 5 bits per
     * channel (tiny noise does not count as a change). Also used by the phone (same rule everywhere).
     */
    public static long hashOf(Screen s, int x1, int y1, int x2, int y2) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), ay = Math.min(y1, y2), by = Math.max(y1, y2);
        int nx = Math.max(1, Math.min(24, bx - ax)), ny = Math.max(1, Math.min(24, by - ay));
        long h = 0xcbf29ce484222325L;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                int x = ax + (int) ((bx - ax) * (i + 0.5) / nx), y = ay + (int) ((by - ay) * (j + 0.5) / ny);
                int c = s.pixel(x, y);
                int q = c < 0 ? -1 : (((c >> 19) & 31) << 10) | (((c >> 11) & 31) << 5) | ((c >> 3) & 31);
                h = (h ^ q) * 0x100000001b3L;
            }
        }
        return h == -1 ? -2 : h;
    }

    /**
     * @param steps     the script's list of steps ('pasi')
     * @param maxTouches stop after this many touches
     * @param maxVirtualMs stop after this much simulated time
     * @param maxRealMs stop after this much real time (busy loops)
     */
    public static Result run(String source, List<Action> steps, final Screen screen, final int maxTouches,
                             final long maxVirtualMs, final long maxRealMs) {
        final Result res = new Result();
        final long realStart = System.nanoTime();
        final ScriptRunner[] runner = new ScriptRunner[1];
        final long wallStart = System.currentTimeMillis();
        final long[] now = {0};
        final String[] limit = {null};

        ScriptHost host = new ScriptHost() {
            private void check() throws InterruptedException {
                if (limit[0] == null) {
                    if (res.touches.size() >= maxTouches) limit[0] = END_TOUCHES;
                    else if (now[0] >= maxVirtualMs) limit[0] = END_TIME;
                    else if ((System.nanoTime() - realStart) / 1000000L >= maxRealMs) limit[0] = END_REAL;
                }
                if (limit[0] != null) {
                    runner[0].stop();
                    throw new InterruptedException();
                }
            }

            private int line() { return runner[0] == null ? 0 : runner[0].line(); }

            private void touch(Action a, boolean fromSteps) {
                int x = Math.round(a.x), y = Math.round(a.y), x2 = Math.round(a.x2), y2 = Math.round(a.y2);
                if (a.type == Action.Type.GESTURE && !a.strokes.isEmpty() && !a.strokes.get(0).points.isEmpty()) {
                    Stroke s = a.strokes.get(0);
                    float[] p0 = s.points.get(0), p1 = s.points.get(s.points.size() - 1);
                    x = Math.round(p0[0]); y = Math.round(p0[1]); x2 = Math.round(p1[0]); y2 = Math.round(p1[1]);
                }
                res.touches.add(new Touch(now[0], line(), a.type, x, y, x2, y2, fromSteps));
                now[0] += Math.max(50, a.ownDuration());
            }

            @Override public long now() { return now[0]; }
            @Override public long wallTime() { return wallStart + now[0]; }

            @Override public void sleep(long ms) throws InterruptedException {
                check();
                now[0] += Math.max(0, ms);
                check();
            }

            @Override public boolean gesture(Action a) throws InterruptedException {
                check();
                touch(a, false);
                return true;
            }

            @Override public boolean global(int g) throws InterruptedException {
                check();
                res.prints.add(new Printed(now[0], "→ " + com.freeclick.core.GlobalActions.name(g)));
                now[0] += 300;
                return true;
            }

            @Override public boolean openApp(String pkg) throws InterruptedException {
                check();
                res.prints.add(new Printed(now[0], "→ deschide " + pkg));
                now[0] += 1000;
                return true;
            }

            @Override public int pixel(int x, int y) throws InterruptedException {
                check();
                return screen.pixel(x, y);
            }

            @Override public long zoneHash(int x1, int y1, int x2, int y2) throws InterruptedException {
                check();
                return hashOf(screen, x1, y1, x2, y2);
            }

            @Override public boolean saveScreenshot(String name) throws InterruptedException {
                check();
                res.prints.add(new Printed(now[0], "📷 captură salvată (în simulare, nu)"));
                return true;
            }

            @Override public void copyText(String text) { res.prints.add(new Printed(now[0], "📋 copiat: " + text)); }

            @Override public boolean openUrl(String url) throws InterruptedException {
                check();
                res.prints.add(new Printed(now[0], "→ deschide " + url));
                now[0] += 1000;
                return true;
            }

            @Override public String readText(int x1, int y1, int x2, int y2, boolean number) throws InterruptedException {
                check();
                now[0] += 100;
                return screen.read(x1, y1, x2, y2, number);
            }

            @Override public List<int[]> findImage(ImageSpec spec, boolean all) throws InterruptedException {
                check();
                now[0] += 100;
                return screen.find(spec, all);
            }

            @Override public void print(String text, int kind) {
                if (kind == PlaybackEngine.PRINT_INFO && text.startsWith("Scriptul așteaptă")) return;
                res.prints.add(new Printed(now[0], text));
            }

            @Override public void toast(String text) { res.prints.add(new Printed(now[0], "💬 " + text)); }
            @Override public void vibrate(long ms) { }
            @Override public int screenWidth() { return screen.width(); }
            @Override public int screenHeight() { return screen.height(); }

            @Override public void runSteps(List<Action> list, int times) throws InterruptedException {
                for (int t = 0; t < Math.max(1, times); t++) {
                    for (Action a : list) { // tree-ok: top-level list handed over by the script
                        check();
                        if (!a.enabled) continue;
                        now[0] += Math.max(0, a.delay);
                        switch (a.type) {
                            case TAP: case LONG_PRESS: case DOUBLE_TAP: case SWIPE: case GESTURE:
                                for (int r = 0; r < Math.max(1, a.repeat); r++) touch(a, true);
                                break;
                            case WAIT:
                                now[0] += a.duration;
                                break;
                            case IMAGE: {
                                List<int[]> at = a.img == null ? null : screen.find(a.img, false);
                                if (at != null && !at.isEmpty()) {
                                    Action tap = Action.tap(at.get(0)[0], at.get(0)[1]);
                                    touch(tap, true);
                                }
                                break;
                            }
                            default:
                                now[0] += 50;
                        }
                    }
                }
            }

            @Override public List<Action> stepsOf(String name) { return null; }
            @Override public String loadKept() { return null; }
            @Override public void saveKept(String json) { }
            private final com.freeclick.core.TouchTracker none = new com.freeclick.core.TouchTracker();
            private boolean saidTouch;
            @Override public com.freeclick.core.TouchTracker touches() { return none; }
            @Override public void listen(boolean on, boolean forward) {
                if (on && !saidTouch) {
                    saidTouch = true;
                    res.prints.add(new Printed(now[0], "(simulare: aici scriptul ar aștepta degetul tău; în simulare nu atinge nimeni)"));
                }
            }
            @Override public int[] toScript(int x, int y) { return new int[]{x, y}; }
            @Override public void hud(String text, int x, int y) {
                if (text != null && !text.isEmpty()) res.prints.add(new Printed(now[0], "(pe ecran) " + text));
            }
            private int buttons;
            @Override public int addButton(String text, int x1, int y1, int x2, int y2) {
                res.prints.add(new Printed(now[0], "(buton „" + text + "”; în simulare nu-l apasă nimeni)"));
                return ++buttons;
            }
            @Override public void removeButton(int id) { }
            @Override public boolean takePress(int id) { return false; }
            @Override public int takeAnyPress() { return -1; }
            @Override public void listenZone(int x1, int y1, int x2, int y2) { }
            private final java.util.Map<String, String> files = new java.util.HashMap<String, String>();
            @Override public String readFile(String name) { return files.get(name); }
            @Override public boolean writeFile(String name, String text, boolean append) {
                files.put(name, append && files.containsKey(name) ? files.get(name) + text : text);
                return true;
            }
            @Override public String ask(String q, String preset, boolean number) {
                res.prints.add(new Printed(now[0], "(întrebare: „" + q + "”; în simulare răspunsul e „" + preset + "”)"));
                return preset;
            }
            @Override public int choose(String title, List<String> options) {
                res.prints.add(new Printed(now[0], "(alege: „" + title + "”; în simulare: „" + options.get(0) + "”)"));
                return 0;
            }
            @Override public int battery() { return 100; }
            @Override public boolean charging() { return false; }
            @Override public boolean landscape() { return screen.width() > screen.height(); }
            @Override public void notifyUser(String title, String text) { res.prints.add(new Printed(now[0], "(notificare) " + title + ": " + text)); }
            @Override public int[] grid(int x1, int y1, int x2, int y2, int cols, int rows) {
                return com.freeclick.core.ColorZones.grid(new com.freeclick.core.ColorZones.Pixels() {
                    public int at(int x, int y) { return screen.pixel(x, y); }
                }, x1, y1, x2, y2, cols, rows);
            }
        };

        ScriptRunner.Listener listener = new ScriptRunner.Listener() {
            @Override public void onState(int state, int line, String why) { }
            @Override public void onFinished(String reason, ScriptError error) {
                if (error != null) { res.end = END_ERROR; res.error = error; }
                else if (limit[0] != null) res.end = limit[0];
                else if (ScriptRunner.SCRIPT_STOP.equals(reason)) res.end = END_STOP;
                else res.end = END_DONE;
            }
        };
        try {
            runner[0] = new ScriptRunner(source, steps, host, listener);
        } catch (ScriptError e) {
            res.end = END_ERROR;
            res.error = e;
            return res;
        }
        runner[0].setPauseOnTouch(false);
        runner[0].runHere();
        res.virtualMs = now[0];
        return res;
    }
}
