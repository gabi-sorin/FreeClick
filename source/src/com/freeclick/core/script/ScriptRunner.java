package com.freeclick.core.script;

import com.freeclick.core.Action;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.Json;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.script.Ast.Event;
import com.freeclick.core.script.Ast.Program;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Runs a script on its own thread (so a loop without waits never freezes the phone, and Stop is
 * always instant), with pause, step-by-step and breakpoints, events (cand / la_fiecare / la_ora)
 * served while the script waits, and variables kept between runs ("pastreaza").
 */
public final class ScriptRunner {
    public interface Listener {
        /** RUNNING / PAUSED (with the line and why) / FINISHED; on the script thread. */
        void onState(int state, int line, String why);
        void onFinished(String reason, ScriptError error);
    }

    public static final int RUNNING = 1, PAUSED = 2, FINISHED = 3;
    public static final String DONE = "done", STOPPED = "stopped", SCRIPT_STOP = "stop", ERROR = "error";
    public static final String WHY_USER = "user", WHY_TOUCH = "touch", WHY_STEP = "step", WHY_BREAKPOINT = "breakpoint", WHY_APP = "app", WHY_AFK = "afk";

    /** Statements in a row without any wait, touch or read before the loop is slowed down a little. */
    static final int BUSY_STEPS = 200000;
    static final long EVENT_CHECK_MS = 300;

    private final Program prog;
    private final ScriptHost host;
    private final Listener listener;
    private final List<Action> steps;
    final List<Object> images = new ArrayList<Object>();
    final Random random = new Random();
    final Map<String, Object> kept = new LinkedHashMap<String, Object>();
    private boolean keptDirty;
    private long keptSavedAt;
    private Interp interp;
    private Thread thread;

    private final Object lock = new Object();
    private volatile boolean stopped, scriptStopped, paused, stepMode, pauseOnTouch = true;
    private volatile String pauseWhy = WHY_USER;
    private volatile int line;
    private final Set<Integer> breakpoints = Collections.synchronizedSet(new HashSet<Integer>());
    private int armedBreak = -1;
    private int busy;
    private boolean busyWarned;
    private boolean inEvent;
    long startedAt;
    private long[] eventDue;
    private int[] eventDay;
    private int[] eventAt;

    /** For the editor's colours. */
    public static final String[] KEYWORDS = {"if", "else", "while", "for", "do", "return", "break", "continue", "struct", "const",
            "switch", "case", "default", "timeout", "limita", "try", "catch", "incearca", "prinde",
            "true", "false", "cout", "endl", "cand", "la_fiecare", "la_ora", "pastreaza", "when", "every", "at", "auto", "std"};
    public static final String[] TYPES = {"int", "long", "double", "float", "bool", "string", "color", "point", "rect", "void",
            "vector", "pair", "Pas", "unsigned"};

    public static java.util.Set<String> functionNames() { return Builtins.allNames(); }

    /** The script's #defines, name → what it stands for (the editor colours p like point). */
    public static java.util.Map<String, String> defines(String source) { return Lexer.defines(source); }

    private static final java.util.Set<String> SCREEN = new java.util.HashSet<String>(java.util.Arrays.asList(
            "pixel", "culoare", "pipeta", "citeste", "citesteText", "apare", "gaseste", "gasesteToate", "asteaptaImagine",
            "asteaptaCuloare", "exista", "procentCuloare", "colorPercent", "percentColor", "procent_culoare", "gasesteCuloare",
            "findColor", "find_color", "gaseste_culoare", "pipetaAtingere", "get_touch_pipeta", "getTouchPipeta", "pickColorByTouch",
            "pick_touch_color", "model", "pattern", "modelPixeli", "asteaptaLiniste", "waitStill", "asteapta_liniste",
            "asteaptaSchimbare", "waitChange", "asteapta_schimbare"));

    /** Whether the script looks at the screen (so the screen is sampled all along while it runs). */
    public static boolean usesScreen(String source) {
        try {
            for (Lexer.Tok t : Lexer.lex(source == null ? "" : source)) if (t.k == Lexer.K.IDENT && SCREEN.contains(t.s)) return true;
        } catch (RuntimeException e) {
            return true; // cannot tell: sample
        }
        return false;
    }

    /** Check a script without running it: null if it is fine, otherwise the first mistake. */
    public static ScriptError check(String source) {
        try {
            compile(source);
            return null;
        } catch (ScriptError e) {
            return e;
        }
    }

    static Program compile(String source) {
        Program p = Parser.parse(source == null ? "" : source);
        Checker.check(p);
        return p;
    }

    /**
     * @param steps the script's own list ('pasi'); image steps in it are the pictures gaseste() knows
     * @throws ScriptError if the script has a mistake
     */
    public ScriptRunner(String source, List<Action> steps, ScriptHost host, Listener listener) {
        this.prog = compile(source);
        this.host = host;
        this.listener = listener;
        this.steps = steps == null ? new ArrayList<Action>() : steps;
        for (Action a : com.freeclick.core.ActionTree.all(this.steps)) if (a.type == Action.Type.IMAGE && a.img != null) images.add(a.img);
    }

    public void setRandom(Random r) { random.setSeed(r.nextLong()); }
    public void setPauseOnTouch(boolean on) { pauseOnTouch = on; }

    // ------------------------------------------------------------------ 2.3: listening to your fingers
    private int listenWaits;
    private boolean listenKeep, listenOn, forward = true;
    com.freeclick.core.TouchTracker.Gesture lastGesture;
    long tapsMark = -1, gesturesMark = -1;

    /** A wait for your touch begins / ends. */
    void listenBegin() { listenWaits++; syncListen(); }
    void listenEnd() { listenWaits = Math.max(0, listenWaits - 1); syncListen(); }
    /** asculta(true/false); degete(), atins()… turn it on by themselves. */
    void setListening(boolean on) { listenKeep = on; syncListen(); }
    boolean listeningKept() { return listenKeep; }
    void setForward(boolean on) {
        if (forward == on) return;
        forward = on;
        if (listenOn) host.listen(true, forward);
    }

    private void syncListen() {
        boolean want = listenWaits > 0 || listenKeep;
        if (want == listenOn) return;
        listenOn = want;
        host.listen(want, forward);
    }

    /** Names that listen to your fingers: such a script does not pause when you touch the screen. */
    private static final java.util.Set<String> TOUCH_INPUT = new java.util.HashSet<String>(java.util.Arrays.asList(
            "asteaptaAtingere", "waitForTouch", "wait_for_touch", "asteapta_atingere", "asteaptaGest", "waitGesture", "wait_gesture",
            "asteapta_gest", "asteaptaSwipe", "waitSwipe", "wait_swipe", "asteapta_swipe", "asteaptaDubluTap", "waitDoubleTap",
            "wait_double_tap", "asteaptaApasareLunga", "waitLongPress", "wait_long_press", "asteaptaDegete", "waitFingers",
            "wait_fingers", "asteapta_degete", "asteaptaFaraDegete", "asteaptaRidicare", "waitNoFingers", "wait_release", "degete",
            "fingers", "atins", "touching", "ecranAtins", "pozitieDeget", "fingerPos", "finger", "ultimaAtingere", "lastTouch",
            "last_touch", "atingeriNoi", "newTaps", "new_taps", "asteaptaAtingereInZona", "waitTouchIn", "wait_touch_in",
            "alegeZona", "pickZone", "pick_zone", "pipetaAtingere", "get_touch_pipeta", "getTouchPipeta", "pickColorByTouch",
            "pick_touch_color", "asculta", "listen", "inregistreaza", "record", "inregistrare",
            "asteaptaTapuri", "waitTaps", "wait_taps", "gesturiNoi", "newGestures", "new_gestures"));

    /** Whether the script listens to your fingers (then touching the screen is input, not "pause"). */
    public static boolean usesTouchInput(String source) {
        try {
            List<Lexer.Tok> t = Lexer.lex(source == null ? "" : source);
            for (int i = 0; i < t.size(); i++) {
                Lexer.Tok k = t.get(i);
                if (k.k != Lexer.K.IDENT) continue;
                if (TOUCH_INPUT.contains(k.s)) return true;
            }
        } catch (ScriptError ignored) { }
        return false;
    }
    public void setBreakpoints(Set<Integer> lines) { breakpoints.clear(); if (lines != null) breakpoints.addAll(lines); }
    public void setStepMode(boolean on) { stepMode = on; }
    public boolean isStepMode() { return stepMode; }
    public int line() { return line; }
    public boolean isPaused() { return paused; }
    public boolean isRunning() { return thread != null && thread.isAlive(); }
    public String pauseReason() { return pauseWhy; }

    /** Run on a new thread. */
    public void start() {
        thread = new Thread(null, new Runnable() { public void run() { runHere(); } }, "freeclick-script", 32L * 1024 * 1024);
        thread.start();
    }

    /** Run on the calling thread (the tests do this, on a virtual clock). */
    public void runHere() {
        String reason = DONE;
        ScriptError error = null;
        startedAt = host.now();
        loadKept();
        interp = new Interp(prog, host, this);
        interp.initGlobals(steps);
        listener.onState(RUNNING, 0, null);
        try {
            interp.runTop();
            if (!prog.events.isEmpty() && !stopped && onlyEvents()) {
                host.print("Scriptul așteaptă evenimentele (cand / la_fiecare / la_ora). ■ îl oprește.", PlaybackEngine.PRINT_INFO);
                while (true) sleep(1000);
            }
        } catch (Interp.StopSignal s) {
            reason = scriptStopped ? SCRIPT_STOP : STOPPED;
        } catch (ScriptError e) {
            reason = ERROR;
            error = e;
            e.runtime = true;
        } catch (StackOverflowError e) {
            reason = ERROR;
            error = new ScriptError("Prea multe apeluri unul în altul: o funcție se cheamă la nesfârșit?", line, 0);
            error.runtime = true;
        } catch (RuntimeException e) {
            reason = ERROR;
            error = new ScriptError("Eroare internă: " + e, line, 0);
            error.runtime = true;
        }
        saveKept(true);
        stopped = true;
        if (listenOn) { listenOn = false; host.listen(false, forward); }
        listener.onState(FINISHED, line, null);
        listener.onFinished(reason, error);
    }

    /**
     * A script that is only events (plus variable declarations) keeps serving them until Stop;
     * one with code of its own ends when that code ends.
     */
    private boolean onlyEvents() {
        if (prog.main != null) return false;
        for (Ast.Stmt s : prog.top) if (!(s instanceof Ast.VarDecl) && !(s instanceof Ast.Empty)) return false;
        return true;
    }

    /** Stop now (from any thread): blocked waits and reads give up at once. */
    public void stop() {
        stopped = true;
        synchronized (lock) { paused = false; lock.notifyAll(); }
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) t.interrupt();
    }

    public void pause(String why) {
        synchronized (lock) {
            if (stopped || paused) return;
            paused = true;
            pauseWhy = why == null ? WHY_USER : why;
        }
        listener.onState(PAUSED, line, pauseWhy);
    }

    public void resume() {
        synchronized (lock) {
            stepMode = false;
            paused = false;
            lock.notifyAll();
        }
        listener.onState(RUNNING, line, null);
    }

    /** Step mode: run until the next line, then pause again. */
    public void step() {
        synchronized (lock) {
            stepMode = true;
            paused = false;
            lock.notifyAll();
        }
    }

    /** The script's global variables as text (for the debugger), in declaration order. */
    public Map<String, String> variables() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Interp in = interp;
        if (in == null) return out;
        for (com.freeclick.core.script.Ast.Stmt s : prog.top) {
            if (!(s instanceof Ast.VarDecl)) continue;
            for (Ast.Sym sym : ((Ast.VarDecl) s).syms) {
                Object v = in.globals[sym.slot];
                if (v instanceof Interp.Ref) v = ((Interp.Ref) v).get();
                try { out.put(sym.name, in.show(v, sym.type)); } catch (RuntimeException ignored) { out.put(sym.name, "?"); }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ called by the interpreter (script thread)
    RuntimeException stopNow() {
        stopped = true;
        return Interp.STOP;
    }

    void scriptStop() {
        scriptStopped = true;
        throw stopNow();
    }

    /** Before every statement: Stop, pause, step mode, breakpoints, and no busy loop hogging the phone. */
    // ------------------------------------------------------------------ 2.5: timeout (ms) { … }
    /** Thrown when the time of a timeout block is up; the block with that level catches it. */
    static final class TimeoutSignal extends RuntimeException {
        final int level;
        TimeoutSignal(int level) { super(null, null, false, false); this.level = level; }
    }

    private final long[] deadlines = new long[64];
    private int deadlineDepth;

    int pushDeadline(long until) {
        if (deadlineDepth >= deadlines.length) {
            ScriptError e = new ScriptError("Prea multe timeout-uri unul în altul (cel mult 64)", line, 1);
            e.runtime = true;
            throw e;
        }
        deadlines[deadlineDepth] = until;
        return deadlineDepth++;
    }

    void popDeadline(int level) { deadlineDepth = Math.min(deadlineDepth, level); }

    /** The outermost timeout whose time is up leaves its block (inner ones go with it). */
    private void checkDeadlines() {
        if (deadlineDepth == 0 || inEvent) return; // an event handler finishes first; the main flow leaves after
        long now = host.now();
        for (int i = 0; i < deadlineDepth; i++) if (now >= deadlines[i]) throw new TimeoutSignal(i);
    }

    private long nearestDeadline() {
        long d = Long.MAX_VALUE;
        for (int i = 0; i < deadlineDepth; i++) d = Math.min(d, deadlines[i]);
        return d;
    }

    void checkpoint(int ln) {
        if (stopped) throw Interp.STOP;
        checkDeadlines();
        line = ln;
        boolean bp = breakpoints.contains(ln);
        if (ln != armedBreak) armedBreak = -1;
        if (stepMode || (bp && armedBreak != ln)) {
            if (bp) armedBreak = ln;
            synchronized (lock) { paused = true; pauseWhy = stepMode ? WHY_STEP : WHY_BREAKPOINT; }
            listener.onState(PAUSED, ln, pauseWhy);
        }
        waitIfPaused();
        if (++busy > BUSY_STEPS) {
            busy = 0;
            if (!busyWarned) {
                busyWarned = true;
                host.print("⚠ linia " + ln + ": o buclă rulează fără wait(); o încetinesc puțin ca să nu încălzească telefonul", PlaybackEngine.PRINT_WARN);
            }
            try { host.sleep(5); } catch (InterruptedException e) { throw stopNow(); }
        }
    }

    private void waitIfPaused() {
        if (!paused) return;
        boolean wasPaused = false;
        synchronized (lock) {
            while (paused && !stopped) {
                wasPaused = true;
                try { lock.wait(); } catch (InterruptedException e) { throw stopNow(); }
            }
        }
        if (stopped) throw Interp.STOP;
        if (wasPaused && !stepMode) listener.onState(RUNNING, line, null);
    }

    /** A pause that can be paused, stopped, and that serves the events meanwhile. */
    void sleep(long ms) {
        long left = Math.max(0, ms);
        busy = 0;
        while (true) {
            if (stopped) throw Interp.STOP;
            waitIfPaused();
            if (!inEvent) runEvents();
            saveKept(false);
            checkDeadlines();
            if (left <= 0) return;
            long slice = Math.min(left, 100);
            if (deadlineDepth > 0) slice = Math.max(1, Math.min(slice, nearestDeadline() - host.now()));
            long t0 = host.now();
            try { host.sleep(slice); } catch (InterruptedException e) { throw stopNow(); }
            left -= Math.max(slice, host.now() - t0);
        }
    }

    void gesture(Action a, Ast.Expr where) {
        busy = 0;
        while (true) {
            if (stopped) throw Interp.STOP;
            waitIfPaused();
            boolean ok;
            try { ok = host.gesture(a); } catch (InterruptedException e) { throw stopNow(); }
            if (ok || !pauseOnTouch) return;
            pause(WHY_TOUCH); // the user touched the screen: wait, then do the same touch again
        }
    }

    void global(int g) {
        busy = 0;
        waitIfPaused();
        try { host.global(g); } catch (InterruptedException e) { throw stopNow(); }
    }

    boolean openApp(String pkg) {
        busy = 0;
        waitIfPaused();
        try { return host.openApp(pkg); } catch (InterruptedException e) { throw stopNow(); }
    }

    int pixel(int x, int y, Ast.Expr where) {
        busy = 0;
        waitIfPaused();
        int c;
        try { c = host.pixel(x, y); } catch (InterruptedException e) { throw stopNow(); }
        if (c < 0) throw interp.rt(where, "Nu pot citi ecranul (are nevoie de Android 11+; ecranele protejate nu se pot citi)");
        return c;
    }

    /** A question to you: the script waits (its events too), stop still works. */
    String ask(String q, String preset, boolean number) {
        busy = 0;
        waitIfPaused();
        try { return host.ask(q, preset, number); } catch (InterruptedException e) { throw stopNow(); }
    }

    int choose(String title, List<String> options) {
        busy = 0;
        waitIfPaused();
        try { return host.choose(title, options); } catch (InterruptedException e) { throw stopNow(); }
    }

    /** Colour samples over a zone (ColorZones); the zone may be written either way round. */
    int[] grid(int x1, int y1, int x2, int y2, int cols, int rows, Ast.Expr where) {
        busy = 0;
        waitIfPaused();
        int[] g;
        try { g = host.grid(x1, y1, x2, y2, cols, rows); } catch (InterruptedException e) { throw stopNow(); }
        if (g == null) throw interp.rt(where, "Nu pot citi ecranul (are nevoie de Android 11+; ecranele protejate nu se pot citi)");
        return g;
    }

    long zoneHash(Interp.Obj zone, Ast.Expr where) {
        busy = 0;
        waitIfPaused();
        long h;
        try {
            h = host.zoneHash((int) (long) (Long) zone.f[0], (int) (long) (Long) zone.f[1], (int) (long) (Long) zone.f[2], (int) (long) (Long) zone.f[3]);
        } catch (InterruptedException e) {
            throw stopNow();
        }
        if (h == -1) throw interp.rt(where, "Nu pot citi ecranul (are nevoie de Android 11+; ecranele protejate nu se pot citi)");
        return h;
    }

    boolean saveScreenshot(String name) {
        busy = 0;
        waitIfPaused();
        try { return host.saveScreenshot(name); } catch (InterruptedException e) { throw stopNow(); }
    }

    boolean openUrl(String url) {
        busy = 0;
        waitIfPaused();
        try { return host.openUrl(url); } catch (InterruptedException e) { throw stopNow(); }
    }

    String read(Interp.Obj zone, boolean number, Ast.Expr where) {
        busy = 0;
        waitIfPaused();
        try {
            String t = host.readText((int) (long) (Long) zone.f[0], (int) (long) (Long) zone.f[1], (int) (long) (Long) zone.f[2], (int) (long) (Long) zone.f[3], number);
            if (t == null) throw interp.rt(where, "Nu pot citi ecranul (are nevoie de Android 11+; ecranele protejate nu se pot citi)");
            return t;
        } catch (InterruptedException e) {
            throw stopNow();
        }
    }

    List<int[]> find(ImageSpec spec, boolean all, Ast.Expr where) {
        busy = 0;
        waitIfPaused();
        try {
            List<int[]> r = host.findImage(spec, all);
            if (r == null) throw interp.rt(where, "Nu pot căuta pe ecran (are nevoie de Android 11+; ecranele protejate nu se pot citi; poza poate lipsi)");
            return r;
        } catch (InterruptedException e) {
            throw stopNow();
        }
    }

    void runSteps(List<Action> list, int times, Ast.Expr where) {
        busy = 0;
        waitIfPaused();
        try { host.runSteps(list, times); } catch (InterruptedException e) { throw stopNow(); }
        if (stopped) throw Interp.STOP;
    }

    // ------------------------------------------------------------------ events
    private void runEvents() {
        List<Event> ev = prog.events;
        if (ev.isEmpty()) return;
        long now = host.now();
        if (eventDue == null) {
            eventDue = new long[ev.size()];
            eventDay = new int[ev.size()];
            eventAt = new int[ev.size()];
            inEvent = true;
            try {
                for (int i = 0; i < ev.size(); i++) {
                    Event e = ev.get(i);
                    if (e.kind == Event.EVERY) {
                        long every = Math.max(100, (Long) interp.eventArg(e));
                        eventAt[i] = (int) Math.min(Integer.MAX_VALUE, every);
                        eventDue[i] = now + every;
                    } else if (e.kind == Event.AT) {
                        String t = ((String) interp.eventArg(e)).trim();
                        int colon = t.indexOf(':');
                        try {
                            int h = Integer.parseInt(t.substring(0, colon)), m = Integer.parseInt(t.substring(colon + 1, Math.min(t.length(), colon + 3)));
                            eventAt[i] = h * 60 + m;
                        } catch (RuntimeException bad) {
                            throw interp.rt(e.arg, "la_ora cere o oră ca \"22:00\", nu „" + t + "”");
                        }
                        eventDay[i] = -1;
                    }
                }
            } finally {
                inEvent = false;
            }
        }
        for (int i = 0; i < ev.size(); i++) {
            Event e = ev.get(i);
            boolean go = false;
            inEvent = true;
            try {
                if (e.kind == Event.EVERY) {
                    if (now >= eventDue[i]) { go = true; eventDue[i] = Math.max(eventDue[i] + eventAt[i], now + 1); }
                } else if (e.kind == Event.AT) {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.setTimeInMillis(host.wallTime());
                    int minute = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
                    int day = c.get(java.util.Calendar.DAY_OF_YEAR) + 1000 * c.get(java.util.Calendar.YEAR);
                    if (minute == eventAt[i] && eventDay[i] != day) { eventDay[i] = day; go = true; }
                } else if (now >= eventDue[i]) {
                    eventDue[i] = now + EVENT_CHECK_MS;
                    go = interp.eventCondition(e);
                }
                if (go) {
                    interp.runEvent(e);
                    if (e.kind == Event.WHEN) eventDue[i] = host.now() + EVENT_CHECK_MS;
                }
            } finally {
                inEvent = false;
            }
        }
    }

    // ------------------------------------------------------------------ kept variables
    void keep(String name, Object v) {
        kept.put(name, v);
        keptDirty = true;
    }

    private void loadKept() {
        String json = host.loadKept();
        kept.putAll(keptFromJson(json));
    }

    private void saveKept(boolean force) {
        if (!keptDirty) return;
        long now = host.now();
        if (!force && now - keptSavedAt < 5000) return;
        keptSavedAt = now;
        keptDirty = false;
        host.saveKept(keptToJson(kept));
    }

    static String keptToJson(Map<String, Object> m) {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            Map<String, Object> w = new LinkedHashMap<String, Object>();
            if (v instanceof Long) { w.put("t", "int"); w.put("v", v); }
            else if (v instanceof Double) { w.put("t", "double"); w.put("v", v); }
            else if (v instanceof Boolean) { w.put("t", "bool"); w.put("v", v); }
            else if (v instanceof Integer) { w.put("t", "color"); w.put("v", (long) (Integer) v); }
            else if (v instanceof String) { w.put("t", "string"); w.put("v", v); }
            else continue;
            o.put(e.getKey(), w);
        }
        return Json.stringify(o, false);
    }

    static Map<String, Object> keptFromJson(String json) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            Object p = Json.parse(json);
            if (!(p instanceof Map)) return out;
            @SuppressWarnings("unchecked") Map<String, Object> o = (Map<String, Object>) p;
            for (Map.Entry<String, Object> e : o.entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> w = (Map<String, Object>) e.getValue();
                String t = Json.getString(w, "t", "");
                Object v = w.get("v");
                if (t.equals("int") && v instanceof Number) out.put(e.getKey(), ((Number) v).longValue());
                else if (t.equals("double") && v instanceof Number) out.put(e.getKey(), ((Number) v).doubleValue());
                else if (t.equals("bool") && v instanceof Boolean) out.put(e.getKey(), v);
                else if (t.equals("color") && v instanceof Number) out.put(e.getKey(), (int) (((Number) v).longValue() & 0xFFFFFF));
                else if (t.equals("string") && v instanceof String) out.put(e.getKey(), v);
            }
        } catch (RuntimeException broken) {
            // a broken file only loses the kept values
        }
        return out;
    }
}
