package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Plays a macro: walks the actions (and sub-macros), applies speed, loops,
 * jitter and time limits, and asks the Host to inject each gesture.
 *
 * The engine is single-threaded: every public method and every Host callback
 * must happen on the same thread (the Android main thread in the app, a
 * virtual clock in the unit tests). It never blocks; all waiting is done with
 * Host.postDelayed so the UI stays responsive and pause/stop act instantly.
 *
 * Scheduling rules (1.6):
 *  - at most one step timer is pending; every timer is its own object, so a timer
 *    that was replaced or cancelled can never fire into a later state;
 *  - gesture callbacks carry the run's generation (start/stop), so a late callback
 *    from an earlier run is ignored;
 *  - the time limit has its own timer (it also ends long waits);
 *  - lateness compensation is reset after anything that is not on the recorded
 *    timeline (pauses, waits for a colour or an app), so it never fires taps in bursts.
 */
public final class PlaybackEngine {

    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    public interface GestureCallback { void onResult(boolean completed); }

    public interface Host {
        /** Inject a gesture; call cb exactly once, on the engine thread. */
        void dispatchGesture(GesturePlan plan, GestureCallback cb);
        /** Perform a system action (Back, Home, ...). */
        boolean performGlobal(int action);
        void postDelayed(Runnable r, long delayMs);
        void removeCallbacks(Runnable r);
        long uptimeMillis();
        /** Current screen size in px, for scaling and clamping. */
        int screenWidth();
        int screenHeight();
        GesturePlanner.Limits limits();
        /**
         * Colour (0xRRGGBB) of the screen pixel at x,y in a frame captured at or after
         * notBefore (uptime ms). -1 = screen capture not available, -2 = no frame that
         * recent yet (ask again a little later).
         */
        int sampleColor(int x, int y, long notBefore);
        /** Open an app. False if it is not installed / cannot be opened. */
        boolean launchApp(String packageName);
        /** Package of the app in front, or null if unknown. */
        String foregroundPackage();
        /**
         * Look for an image on a frame captured at or after req.notBefore; answer once with
         * cb.onImage, on the engine thread, later (never from inside this call).
         */
        void findImage(ImageRequest req, ImageCallback cb);
        /** Write a line to the console (kind = PRINT_USER / PRINT_INFO / PRINT_WARN). */
        void print(String text, int kind);
        /**
         * Read a number or text in a zone (current-screen px) of a frame captured at or after
         * req.notBefore; answer once with cb.onRead, on the engine thread, later.
         */
        void readText(ReadRequest req, ReadCallback cb);
        /**
         * A "run a script" step (2.1): run the saved script {@code times} times, then call {@code done}
         * once, on the engine thread. {@link #stopScriptStep} ends it early, {@link #pauseScriptStep} holds it.
         */
        void runScriptStep(Macro script, int times, Runnable done);
        void stopScriptStep();
        void pauseScriptStep(boolean paused);
    }

    public static final int PRINT_USER = 0, PRINT_INFO = 1, PRINT_WARN = 2;

    /** A zone to read (current-screen pixels, inclusive-exclusive). */
    public static final class ReadRequest {
        public int x1, y1, x2, y2;
        /** Action.READ_NUMBER or Action.READ_TEXT. */
        public int mode;
        public long notBefore;
    }

    /** What was read. status: IMG_FOUND = read something (maybe nothing useful), IMG_NO_CAPTURE, IMG_PROTECTED. */
    public static final class ReadResult {
        public int status = IMG_FOUND;
        public String text = "";
        /** The number in the text (READ_NUMBER), null if there is none. */
        public Double number;
        /** 0..1, how sure the reader is. */
        public double confidence;
        public long frameTime;
    }

    public interface ReadCallback { void onRead(ReadResult r); }

    /** Variables filled in by the app itself (cannot be set by a step). */
    public static final String[] BUILTIN_VARS = {"tura", "timp", "ora"};

    public static boolean isBuiltinVar(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String b : BUILTIN_VARS) if (b.equals(n)) return true;
        return false;
    }

    /** What to look for. */
    public static final class ImageRequest {
        public ImageSpec spec;
        public long notBefore;
        public boolean all;
        public ImageRequest(ImageSpec spec, long notBefore, boolean all) { this.spec = spec; this.notBefore = notBefore; this.all = all; }
    }

    public static final int IMG_FOUND = 0, IMG_NOT_FOUND = 1, IMG_NO_CAPTURE = 2, IMG_PROTECTED = 3, IMG_MISSING = 4;

    /** What was seen. Rectangles are in current-screen pixels; tap points already include the spec's tap offset. */
    public static final class ImageResult {
        public int status = IMG_NOT_FOUND;
        /** Tap points {x, y} of every match, top-most first. */
        public final List<float[]> points = new ArrayList<float[]>();
        /** Best similarity 0..1 (also when not found: "closest 71%"). */
        public double best;
        /** It looks present but lies under our panel. */
        public boolean covered;
        /** When the frame was requested (uptime ms). */
        public long frameTime;
    }

    public interface ImageCallback { void onImage(ImageResult r); }

    public interface Listener {
        void onStateChanged(State state);
        /** loop is 1-based; totalLoops 0 = infinite. */
        void onProgress(int loop, int totalLoops, int step, int totalSteps, String macroName, Action action);
        void onFinished(String reason);
    }

    /** Optional: things worth telling the user that do not stop the run. */
    public interface Notices {
        void onNotice(String code, Action action);
    }

    public static final String REASON_DONE = "done";
    public static final String REASON_STOPPED = "stopped";
    public static final String REASON_TIME_LIMIT = "time-limit";
    public static final String REASON_EMPTY = "empty";
    public static final String REASON_CYCLE = "cycle";
    public static final String REASON_TOO_DEEP = "too-deep";
    public static final String REASON_CONDITION_TIMEOUT = "condition-timeout";
    public static final String REASON_NO_CAPTURE = "no-capture";
    /** An image step reached its "stop after N finds". */
    public static final String REASON_HIT_LIMIT = "hit-limit";
    /** The screen cannot be captured (banking apps, some games): images cannot be seen. */
    public static final String REASON_PROTECTED = "protected";
    /** The picture file of an image step is missing (e.g. a file imported without it). */
    public static final String REASON_IMAGE_MISSING = "image-missing";

    public static final String PAUSE_USER = "user";
    /** A gesture was cancelled because the user touched the screen. */
    public static final String PAUSE_TOUCH = "touch";
    /** The service paused (another app came to the front, a system panel opened...). */
    public static final String PAUSE_APP = "app";
    /** 🌙 AFK mode waits: the game left the screen, the battery is low or hot, the screen was locked. */
    public static final String PAUSE_AFK = "afk";

    public static final String NOTICE_APP_MISSING = "app-missing";
    public static final String NOTICE_APP_SLOW = "app-slow";
    /** The image looks present but our panel covers it (reported once per step). */
    public static final String NOTICE_IMAGE_COVERED = "image-covered";

    /** How often a colour condition is checked (real time, not affected by speed). */
    static final long POLL_MS = 150;
    /** How often "is the app in front yet?" is checked. */
    static final long APP_POLL_MS = 250;
    /** How long a colour condition waits for screen capture to deliver frames before giving up. */
    static final long CAPTURE_GRACE_MS = 3000;
    /** A frame read for a condition must be taken this long after the last touch (the app reacts). */
    static final long SETTLE_MS = 150;
    /** A pass of the macro that touched nothing lasts at least this long (no busy loop). */
    static final long MIN_IDLE_PASS_MS = 100;
    /** Preview: waits for conditions assumed absent are shortened to this. */
    static final long PREVIEW_MAX_WAIT_MS = 2000;
    /** An image that is not there yet is looked for again at most this often (frames come every ~350 ms anyway). */
    static final long IMAGE_RETRY_MS = 100;
    /** A read is confirmed when two frames in a row give the same value; at most this many frames are read. */
    static final int READ_MAX_TRIES = 5;
    /** Preview: loops show at most this many turns. */
    static final int PREVIEW_MAX_TURNS = 2;

    static final int MAX_DEPTH = 32;
    /** Most lateness that is caught up on (protects against a long system hiccup). */
    static final long MAX_LATENESS_DEBT = 3000;

    /** One list of steps being played: the root macro or a sub-macro call. */
    private static final class Frame {
        final Macro owner;
        final List<Action> list;
        int index;
        int rep;
        int loopsDone;
        int runsLeft;
        Frame(Macro m, int runs) { owner = m; list = m.actions; runsLeft = runs; }
    }

    private static final class Step {
        final Action action;
        final long delay;
        final Frame frame;
        final int index;
        Step(Action a, long d, Frame f, int index) { action = a; delay = d; frame = f; this.index = index; }
    }

    // What a step timer does when it fires.
    private static final int RUN_PENDING = 1, ADVANCE = 2, POLL = 3, APP_WAIT = 4, IMAGE_RETRY = 5, READ_RETRY = 6;

    private final class Timer implements Runnable {
        final int kind;
        final long due;
        Timer(int kind, long due) { this.kind = kind; this.due = due; }
        @Override public void run() {
            if (timer != this) return; // replaced or cancelled
            timer = null;
            if (state != State.RUNNING) return;
            if (kind == RUN_PENDING) { runDue = due; runPending(); }
            else if (kind == ADVANCE) advance();
            else if (kind == POLL) pollColor();
            else if (kind == APP_WAIT) pollApp();
            else if (kind == IMAGE_RETRY) requestImage();
            else if (kind == READ_RETRY) requestRead();
        }
    }

    private final Host host;
    private final Listener listener;
    private Notices notices;
    private Random random = new Random();

    private Macro root;
    private MacroLibrary lib;
    private final List<Frame> stack = new ArrayList<Frame>();
    private State state = State.IDLE;
    private double speed = 1.0;
    private long startedAt;
    private long pausedTotal;
    private long pausedAt;
    private long carryDelay;
    private int stepsThisPass;
    private int gesturesThisPass;
    private long passStartedAt;
    private int generation;
    private CoordinateScaler scaler = CoordinateScaler.IDENTITY;
    private boolean pauseOnCancel;
    private String pauseReason = PAUSE_USER;
    private boolean preview;
    private boolean previewAssumeFound = true;

    private Timer timer;
    private int kindOnPause;
    private long remainingOnPause = -1;
    private Step pending;
    /** A gesture cancelled by a touch, played again on resume. */
    private Step retryOnResume;
    private boolean gestureInFlight;
    /**
     * Timing accuracy: phones report a finished gesture a little late (tens of ms).
     * That lateness is subtracted from the following pause(s), so a recording plays
     * back on its original timeline instead of drifting later with every touch.
     */
    private long lateness;
    private long runDue;
    /** Frames read for conditions must be newer than this (uptime ms). */
    private long screenSettledAt;
    private Action colorWait;
    private long colorWaitStart;
    private Action appWait;
    private long appWaitStart;
    private int conditionsMet;
    private int conditionsTimedOut;
    // image step being searched
    private Action imageWait;
    private Step imageStep;
    private long imageWaitStart;
    private int imageToken;
    /** First frame that showed the wanted state; the second consecutive one confirms it. */
    private ImageResult imageFirst;
    private long lastImageFrame;
    private boolean coveredReported;
    private final java.util.IdentityHashMap<Action, Integer> imageHits = new java.util.IdentityHashMap<Action, Integer>();
    // read step in progress
    private Action readWait;
    private long readWaitStart;
    private int readToken;
    private int readTries;
    private Object readPrev;
    private long lastReadFrame;
    /** Variables of this run (lower-case names → Double or String). */
    private final java.util.LinkedHashMap<String, Object> vars = new java.util.LinkedHashMap<String, Object>();
    private final Expr.Env env = new Expr.Env() {
        @Override public Object get(String name) {
            if (name.equals("tura")) return (double) (stack.isEmpty() ? 1 : stack.get(0).loopsDone + 1);
            if (name.equals("timp")) return Math.round(elapsed() / 100.0) / 10.0;
            if (name.equals("ora")) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                return String.format(java.util.Locale.US, "%02d:%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                        c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND));
            }
            return vars.get(name);
        }
        @Override public double random() { return random.nextDouble(); }
        @Override public void warn(String message) { host.print("⚠ " + message, PRINT_WARN); }
    };

    private final Runnable deadline = new Runnable() {
        @Override public void run() {
            if (state != State.RUNNING) return;
            long limit = root.settings.stopAfterMs;
            long left = limit - elapsed();
            if (left > 0) { host.postDelayed(this, left); return; }
            finish(REASON_TIME_LIMIT);
        }
    };

    // Stats
    private int executedSteps;
    private int failedGestures;

    public PlaybackEngine(Host host, Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    public void setRandom(Random r) { this.random = r; }
    public void setNotices(Notices n) { this.notices = n; }
    /** Pause (instead of carrying on) when a gesture is cancelled because the screen was touched. */
    public void setPauseOnCancel(boolean on) { this.pauseOnCancel = on; }
    /**
     * Preview: the Host only draws gestures. Conditions are not read from the screen but
     * assumed found (or not), waits for absent things are shortened, loops show 2 turns.
     */
    public void setPreview(boolean on, boolean assumeFound) { preview = on; previewAssumeFound = assumeFound; }
    public boolean isPreview() { return preview; }
    public void setPreviewAssumeFound(boolean found) { previewAssumeFound = found; }
    public State state() { return state; }
    public double speed() { return speed; }
    public int executedSteps() { return executedSteps; }
    public int failedGestures() { return failedGestures; }
    public Macro macro() { return root; }
    public int conditionsMet() { return conditionsMet; }
    public int conditionsTimedOut() { return conditionsTimedOut; }
    /** Why the run is paused (PAUSE_USER / PAUSE_TOUCH / PAUSE_APP). */
    public String pauseReason() { return pauseReason; }
    /** True while a colour condition is being checked. */
    public boolean isWaitingForColor() { return colorWait != null; }
    /** The image step being searched right now (null if none). */
    public Action imageBeingSearched() { return imageWait; }
    /** How long the current image search has been going (ms of play time). */
    public long imageSearchElapsed() { return imageWait == null ? 0 : elapsed() - imageWaitStart; }
    /** How many times each image step was found in this run. */
    public java.util.Map<Action, Integer> imageHits() { return imageHits; }
    /** The variables of the current (or last) run, in the order they were first set. */
    public java.util.Map<String, Object> variables() { return java.util.Collections.unmodifiableMap(vars); }
    /** True while a read step is reading the screen. */
    public Action readBeingDone() { return readWait; }
    /** Evaluate a {template} with the current variables (the console's "show variable"). */
    public String expand(String template) { return Expr.expand(template, env); }

    /** Start playing. Returns false (and reports why) if the macro cannot run. */
    public boolean start(Macro macro, MacroLibrary library) { return start(macro, library, 0); }

    /** Start playing from a top-level step (editor "Rulează de aici"). */
    public boolean start(Macro macro, MacroLibrary library, int fromIndex) {
        if (state == State.RUNNING || state == State.PAUSED) stopInternal(REASON_STOPPED, false);
        // Back to IDLE without notifying: whatever happens next (start or refusal) is then
        // always reported as a state change, even if the previous run had already FINISHED.
        state = State.IDLE;
        generation++;
        root = macro;
        lib = library != null ? library : new MacroLibrary.InMemory();
        stack.clear();
        cancelTimer();
        host.removeCallbacks(deadline);
        pending = null;
        retryOnResume = null;
        gestureInFlight = false;
        kindOnPause = 0;
        carryDelay = 0;
        stepsThisPass = 0;
        gesturesThisPass = 0;
        executedSteps = 0;
        failedGestures = 0;
        colorWait = null;
        appWait = null;
        imageWait = null;
        imageFirst = null;
        imageToken++;
        imageHits.clear();
        readWait = null;
        readToken++;
        vars.clear();
        lateness = 0;
        conditionsMet = 0;
        conditionsTimedOut = 0;
        pausedTotal = 0;
        pauseReason = PAUSE_USER;
        screenSettledAt = 0;
        speed = Macro.clampSpeed(macro.settings.speed);
        scaler = CoordinateScaler.of(macro.screenWidth, macro.screenHeight,
                host.screenWidth(), host.screenHeight(), macro.settings.scaleToScreen);

        if (MacroGraph.findCycle(macro, lib) != null) {
            finish(REASON_CYCLE);
            return false;
        }
        if (!MacroGraph.hasRunnableSteps(macro, lib)) {
            finish(REASON_EMPTY);
            return false;
        }
        Frame f = new Frame(macro, 1);
        f.index = Math.max(0, Math.min(fromIndex, macro.actions.size())); // tree-ok: a top-level step index
        stack.add(f);
        startedAt = host.uptimeMillis();
        passStartedAt = startedAt;
        setState(State.RUNNING);
        if (macro.settings.stopAfterMs > 0) host.postDelayed(deadline, macro.settings.stopAfterMs);
        advance();
        return state == State.RUNNING;
    }

    public void pause() { pause(PAUSE_USER); }

    public void pause(String reason) {
        if (state != State.RUNNING) return;
        if (scriptWait != null) host.pauseScriptStep(true);
        if (timer != null) {
            remainingOnPause = Math.max(0, timer.due - host.uptimeMillis());
            kindOnPause = timer.kind;
            cancelTimer();
        } else {
            remainingOnPause = -1;
            kindOnPause = 0;
        }
        host.removeCallbacks(deadline);
        imageToken++;        // a search answered during the pause is ignored...
        imageFirst = null;   // ...and resume searches again from a fresh frame
        readToken++;
        readPrev = null;
        readTries = 0;
        pausedAt = host.uptimeMillis();
        pauseReason = reason == null ? PAUSE_USER : reason;
        lateness = 0; // timing restarts from the resume, nothing to catch up
        setState(State.PAUSED);
    }

    public void resume() {
        if (state != State.PAUSED) return;
        pausedTotal += host.uptimeMillis() - pausedAt;
        setState(State.RUNNING);
        long limit = root.settings.stopAfterMs;
        if (limit > 0) host.postDelayed(deadline, Math.max(0, limit - elapsed()));
        if (retryOnResume != null) {
            pending = retryOnResume;
            retryOnResume = null;
            schedule(RUN_PENDING, 0);
        } else if (kindOnPause != 0) {
            schedule(kindOnPause, remainingOnPause);
            kindOnPause = 0;
        } else if (imageWait != null) {
            requestImage();
        } else if (readWait != null) {
            requestRead();
        } else if (scriptWait != null) {
            host.pauseScriptStep(false); // its end continues the run
        } else if (scriptDoneWhilePaused) {
            scriptDoneWhilePaused = false;
            advance();
        } else if (!gestureInFlight) {
            advance();
        } // else: the gesture callback continues
    }

    public void togglePause() {
        if (state == State.RUNNING) pause(); else if (state == State.PAUSED) resume();
    }

    public void stop() {
        if (state == State.RUNNING || state == State.PAUSED) stopInternal(REASON_STOPPED, true);
    }

    /** Skip what is being waited for now (a pause or a condition); used by the preview's ⏭. */
    public void skipWait() {
        if (state != State.RUNNING || timer == null) return;
        int kind = timer.kind;
        cancelTimer();
        lateness = 0;
        if (kind == POLL && colorWait != null) {
            colorWait = null;
            conditionsMet++;
            advance();
        } else if (kind == APP_WAIT) {
            appWait = null;
            advance();
        } else if (kind == IMAGE_RETRY && imageWait != null) {
            imageWait = null;
            imageToken++;
            advance();
        } else if (kind == READ_RETRY && readWait != null) {
            readWait = null;
            readToken++;
            advance();
        } else {
            schedule(kind, 0);
        }
    }

    /** Change speed live; a wait that is already counting down is rescaled too. */
    public void setSpeed(double newSpeed) {
        newSpeed = Macro.clampSpeed(newSpeed);
        double old = speed;
        speed = newSpeed;
        if (old == newSpeed) return;
        if (state == State.RUNNING && timer != null && timer.kind != POLL && timer.kind != APP_WAIT && timer.kind != IMAGE_RETRY && timer.kind != READ_RETRY) {
            long remaining = Math.max(0, timer.due - host.uptimeMillis());
            int kind = timer.kind;
            cancelTimer();
            schedule(kind, Math.round(remaining * old / newSpeed));
        } else if (state == State.PAUSED && kindOnPause != 0 && kindOnPause != POLL && kindOnPause != APP_WAIT && kindOnPause != IMAGE_RETRY && kindOnPause != READ_RETRY) {
            remainingOnPause = Math.round(remainingOnPause * old / newSpeed);
        }
    }

    /** Playback time excluding pauses. */
    public long elapsed() {
        long now = state == State.PAUSED ? pausedAt : host.uptimeMillis();
        return now - startedAt - pausedTotal;
    }

    // ------------------------------------------------------------------ core
    private void advance() {
        if (state != State.RUNNING) return;
        long limit = root.settings.stopAfterMs;
        if (limit > 0 && elapsed() >= limit) { finish(REASON_TIME_LIMIT); return; }
        Step step;
        try {
            step = nextStep();
        } catch (IllegalStateException tooDeep) {
            finish(REASON_TOO_DEEP);
            return;
        }
        if (step == null) return; // finish() already called
        pending = step;
        long d = Math.round(jitterDelay(step.delay) / speed);
        if (lateness > 0) {
            long use = Math.min(d, lateness);
            d -= use;
            lateness -= use;
        }
        schedule(RUN_PENDING, d);
    }

    private void schedule(int kind, long delayMs) {
        delayMs = Math.max(0, delayMs);
        cancelTimer();
        Timer t = new Timer(kind, host.uptimeMillis() + delayMs);
        timer = t;
        host.postDelayed(t, delayMs);
    }

    private void cancelTimer() {
        if (timer != null) host.removeCallbacks(timer);
        timer = null;
    }

    private long jitterDelay(long d) {
        int pct = root.settings.jitterTimePct;
        if (pct <= 0 || d <= 0) return d;
        double u = random.nextDouble() * 2 - 1;
        return Math.max(0, Math.round(d * (1 + u * pct / 100.0)));
    }

    private int maxTurns(int loops) {
        if (!preview) return loops;
        return loops == 0 ? PREVIEW_MAX_TURNS : Math.min(loops, PREVIEW_MAX_TURNS);
    }

    /** Find the next executable step, walking loops and sub-macros. Null = finished. */
    private Step nextStep() {
        while (!stack.isEmpty()) {
            Frame f = stack.get(stack.size() - 1);
            List<Action> acts = f.list;
            if (f.index >= acts.size()) {
                if (stack.size() == 1) { // end of one pass of the root macro
                    f.loopsDone++;
                    int loops = maxTurns(root.settings.loops);
                    if (loops != 0 && f.loopsDone >= loops) { finish(REASON_DONE); return null; }
                    f.index = 0;
                    f.rep = 0;
                    carryDelay += root.settings.loopDelay;
                    if (gesturesThisPass == 0) {
                        // Nothing was touched in this pass: never loop faster than MIN_IDLE_PASS_MS.
                        long took = host.uptimeMillis() - passStartedAt;
                        long gap = Math.round(carryDelay / speed);
                        if (took + gap < MIN_IDLE_PASS_MS) carryDelay += Math.round((MIN_IDLE_PASS_MS - took - gap) * speed);
                    }
                    stepsThisPass = 0;
                    gesturesThisPass = 0;
                    passStartedAt = host.uptimeMillis() + Math.round(carryDelay / speed); // after the loop pause
                    continue;
                }
                f.runsLeft--;
                if (f.runsLeft > 0) { f.index = 0; f.rep = 0; continue; }
                stack.remove(stack.size() - 1);
                continue;
            }
            Action a = acts.get(f.index);
            if (!a.enabled || a.type == Action.Type.UNKNOWN) { f.index++; f.rep = 0; continue; }
            if (a.type == Action.Type.MACRO) {
                Macro child = lib.find(a.macroId);
                if (child != null && Macro.KIND_SCRIPT.equals(child.kind)) { // a script: one step, the engine waits for it
                    long d = a.delay + carryDelay;
                    carryDelay = 0;
                    int idx = f.index;
                    f.index++;
                    f.rep = 0;
                    stepsThisPass++;
                    return new Step(a, d, f, idx);
                }
                f.index++;
                f.rep = 0;
                if (child == null || !MacroGraph.hasRunnableSteps(child, lib)) continue; // missing/empty: skip
                if (stack.size() >= MAX_DEPTH) throw new IllegalStateException("too deep");
                carryDelay += a.delay;
                int runs = Math.max(1, a.repeat);
                if (preview) runs = Math.min(runs, PREVIEW_MAX_TURNS);
                stack.add(new Frame(child, runs));
                continue;
            }
            long d = (f.rep == 0 ? a.delay : a.interval) + carryDelay;
            carryDelay = 0;
            int idx = f.index;
            f.rep++;
            if (f.rep >= Math.max(1, a.repeat)) { f.index++; f.rep = 0; }
            stepsThisPass++;
            return new Step(a, d, f, idx);
        }
        finish(REASON_DONE);
        return null;
    }

    private void runPending() {
        final Step step = pending;
        pending = null;
        if (step == null) { advance(); return; }
        executedSteps++;
        Frame rootFrame = stack.isEmpty() ? null : stack.get(0);
        if (listener != null && rootFrame != null && step.frame != null) {
            listener.onProgress(rootFrame.loopsDone + 1, root.settings.loops,
                    step.index + 1, step.frame.list.size(), step.frame.owner.name, step.action);
        }
        Action a = step.action;
        if (a.isGesture()) {
            GesturePlanner.Limits lim = host.limits();
            GesturePlan plan = GesturePlanner.plan(a, speed, root.settings.jitterPx, random, scaler, lim);
            if (plan == null) { advance(); return; }
            dispatch(plan, step, null);
        } else if (a.type == Action.Type.IMAGE) {
            imageStep = step;
            imageWait = a;
            imageWaitStart = elapsed();
            imageFirst = null;
            coveredReported = false;
            requestImage();
        } else if (a.type == Action.Type.GLOBAL) {
            host.performGlobal(a.globalAction);
            screenSettledAt = host.uptimeMillis() + SETTLE_MS;
            schedule(ADVANCE, 0);
        } else if (a.type == Action.Type.WAIT) {
            long ms = Math.max(0, a.duration);
            if (a.isRandomWait()) ms += Math.round(random.nextDouble() * (a.durationMax - a.duration));
            schedule(ADVANCE, Math.round(ms / speed));
        } else if (a.type == Action.Type.WAIT_COLOR) {
            colorWait = a;
            colorWaitStart = elapsed();
            pollColor();
        } else if (a.type == Action.Type.LAUNCH_APP) {
            launch(a);
        } else if (a.type == Action.Type.PRINT) {
            host.print(Expr.expand(a.text, env), PRINT_USER);
            advance();
        } else if (a.type == Action.Type.SET_VAR) {
            setVariable(a);
            advance();
        } else if (a.type == Action.Type.MACRO) {
            runScriptStep(a);
        } else if (a.type == Action.Type.READ) {
            readWait = a;
            readWaitStart = elapsed();
            readTries = 0;
            readPrev = null;
            requestRead();
        } else {
            advance();
        }
    }

    // ------------------------------------------------------------------ script steps (2.1)
    private Action scriptWait;
    private boolean scriptDoneWhilePaused;

    private void runScriptStep(final Action a) {
        Macro script = lib.find(a.macroId);
        if (script == null) { advance(); return; }
        if (preview) { // the preview never runs code
            host.print("📜 „" + script.name + "” (scriptul nu rulează în previzualizare)", PRINT_INFO);
            advance();
            return;
        }
        scriptWait = a;
        scriptDoneWhilePaused = false;
        final int gen = generation;
        host.runScriptStep(script, Math.max(1, a.repeat), new Runnable() {
            @Override public void run() {
                if (gen != generation || scriptWait != a) return; // stopped / restarted meanwhile
                scriptWait = null;
                if (state == State.PAUSED) { scriptDoneWhilePaused = true; return; }
                if (state == State.RUNNING) advance();
            }
        });
    }

    // ------------------------------------------------------------------ variables
    private void setVariable(Action a) {
        String name = a.varName == null ? "" : a.varName.trim();
        if (!Expr.isValidName(name) || isBuiltinVar(name)) {
            host.print("⚠ nume de variabilă greșit: „" + name + "”", PRINT_WARN);
            return;
        }
        Object v;
        try {
            v = Expr.parse(a.text).eval(env);
        } catch (Expr.ExprError e) {
            host.print("⚠ " + name + " = " + a.text + ": " + e.getMessage(), PRINT_WARN);
            return;
        }
        vars.put(name.toLowerCase(java.util.Locale.ROOT), v);
    }

    private void storeRead(Action a, Object value) {
        String name = a.varName == null ? "" : a.varName.trim();
        if (Expr.isValidName(name) && !isBuiltinVar(name)) vars.put(name.toLowerCase(java.util.Locale.ROOT), value);
        host.print("📖 " + (name.length() > 0 ? name : "citit") + " = " + Expr.format(value), PRINT_INFO);
    }

    // ------------------------------------------------------------------ read steps
    private void requestRead() {
        final Action a = readWait;
        if (a == null || state != State.RUNNING) return;
        if (preview) {
            readWait = null;
            String name = a.varName == null || a.varName.length() == 0 ? "citit" : a.varName;
            Object old = env.get(name.toLowerCase(java.util.Locale.ROOT));
            host.print("👁 previzualizare: nu citesc ecranul, " + name + " rămâne " + (old == null ? "nesetată" : Expr.format(old)), PRINT_INFO);
            schedule(ADVANCE, 300);
            return;
        }
        long notBefore = Math.max(screenSettledAt, readTries > 0 ? lastReadFrame + 1 : 0);
        if (a.readMode == Action.READ_COLOR) {
            int c = host.sampleColor(Math.round(scaler.x(a.x)), Math.round(scaler.y(a.y)), notBefore);
            if (c == -2) { schedule(READ_RETRY, POLL_MS); return; }
            if (c < 0) {
                if (elapsed() - readWaitStart >= CAPTURE_GRACE_MS) { readWait = null; finish(REASON_NO_CAPTURE); return; }
                schedule(READ_RETRY, POLL_MS);
                return;
            }
            readWait = null;
            lateness = 0;
            storeRead(a, ColorMatch.toHex(c));
            advance();
            return;
        }
        final int token = ++readToken;
        ReadRequest req = new ReadRequest();
        float x1 = scaler.x(a.x), y1 = scaler.y(a.y), x2 = scaler.x(a.x2), y2 = scaler.y(a.y2);
        req.x1 = Math.max(0, Math.round(Math.min(x1, x2)));
        req.y1 = Math.max(0, Math.round(Math.min(y1, y2)));
        req.x2 = Math.min(host.screenWidth(), Math.round(Math.max(x1, x2)));
        req.y2 = Math.min(host.screenHeight(), Math.round(Math.max(y1, y2)));
        req.mode = a.readMode;
        req.notBefore = notBefore;
        host.readText(req, new ReadCallback() {
            @Override public void onRead(ReadResult r) {
                if (token != readToken || readWait != a || state != State.RUNNING) return; // stale
                onReadResult(a, r);
            }
        });
    }

    private void onReadResult(Action a, ReadResult r) {
        lastReadFrame = Math.max(lastReadFrame, r.frameTime);
        if (r.status == IMG_PROTECTED) { readWait = null; finish(REASON_PROTECTED); return; }
        if (r.status == IMG_NO_CAPTURE) {
            if (elapsed() - readWaitStart >= CAPTURE_GRACE_MS) { readWait = null; finish(REASON_NO_CAPTURE); return; }
            schedule(READ_RETRY, 300);
            return;
        }
        Object value;
        if (a.readMode == Action.READ_NUMBER) value = r.number;
        else value = r.text == null || r.text.trim().length() == 0 ? null : r.text.trim();
        readTries++;
        if (value != null && readPrev != null && Expr.format(value).equals(Expr.format(readPrev))) {
            readDone(a, value); // the same on two frames in a row
            return;
        }
        if (value != null) readPrev = value;
        if (readTries >= READ_MAX_TRIES) {
            if (readPrev != null) { readDone(a, readPrev); return; } // still changing: keep the last one seen
            readWait = null;
            lateness = 0;
            String seen = r.text == null || r.text.trim().length() == 0 ? "nimic" : "„" + r.text.trim() + "”";
            host.print("⚠ " + (a.varName == null || a.varName.length() == 0 ? "citire" : a.varName) + ": n-am putut citi "
                    + (a.readMode == Action.READ_NUMBER ? "un număr" : "textul") + " (am văzut " + seen + "); rămâne cum era", PRINT_WARN);
            advance();
            return;
        }
        schedule(READ_RETRY, IMAGE_RETRY_MS);
    }

    private void readDone(Action a, Object value) {
        readWait = null;
        readPrev = null;
        lateness = 0; // reading is not part of the recorded timeline
        storeRead(a, value);
        advance();
    }

    /**
     * Inject a gesture and continue when it is done. afterTap (image steps) runs instead of the
     * normal "next step" when the gesture completes.
     */
    private void dispatch(GesturePlan plan, final Step step, final Runnable afterTap) {
        gestureInFlight = true;
        gesturesThisPass++;
        final int gen = generation;
        // When the gesture should be over if everything ran exactly on time.
        final long expectedEnd = Math.min(runDue, host.uptimeMillis()) + plan.totalDuration();
        host.dispatchGesture(plan, new GestureCallback() {
            @Override public void onResult(boolean completed) {
                if (gen != generation) return; // stale callback after stop/restart
                gestureInFlight = false;
                long now = host.uptimeMillis();
                screenSettledAt = now + SETTLE_MS;
                if (!completed) failedGestures++;
                if (state != State.RUNNING) return; // paused meanwhile: resume() continues
                if (!completed && pauseOnCancel && !preview) {
                    retryOnResume = new Step(step.action, 0, step.frame, step.index);
                    pause(PAUSE_TOUCH);
                    return;
                }
                lateness = Math.min(MAX_LATENESS_DEBT, lateness + Math.max(0, now - expectedEnd));
                if (afterTap != null) afterTap.run(); else advance();
            }
        });
    }

    // ------------------------------------------------------------------ image steps
    /** Ask the host for the next frame (newer than the last touch and the last frame seen). */
    private void requestImage() {
        final Action a = imageWait;
        if (a == null || state != State.RUNNING) return;
        if (preview) { previewImage(a); return; }
        final int token = ++imageToken;
        long notBefore = Math.max(screenSettledAt, imageFirst != null ? lastImageFrame + 1 : 0);
        host.findImage(new ImageRequest(a.img, notBefore, a.allMatches), new ImageCallback() {
            @Override public void onImage(ImageResult r) {
                if (token != imageToken || imageWait != a || state != State.RUNNING) return; // stale
                PlaybackEngine.this.onImageResult(a, r);
            }
        });
    }

    private void onImageResult(Action a, ImageResult r) {
        lastImageFrame = Math.max(lastImageFrame, r.frameTime);
        long waited = elapsed() - imageWaitStart;
        if (r.status == IMG_PROTECTED) { imageWait = null; finish(REASON_PROTECTED); return; }
        if (r.status == IMG_MISSING) { imageWait = null; finish(REASON_IMAGE_MISSING); return; }
        if (r.status == IMG_NO_CAPTURE) {
            if (waited >= CAPTURE_GRACE_MS) { imageWait = null; finish(REASON_NO_CAPTURE); return; }
            schedule(IMAGE_RETRY, 300);
            return;
        }
        boolean present = r.status == IMG_FOUND;
        if (!present && r.covered && !coveredReported && notices != null) {
            coveredReported = true;
            notices.onNotice(NOTICE_IMAGE_COVERED, a);
        }
        boolean wanted = a.press == Action.PRESS_GONE ? !present : present;
        if (wanted) {
            if (imageFirst == null) { // seen once: confirm on the next frame (no taps on things fading out)
                imageFirst = r;
                requestImage();
                return;
            }
            imageFirst = null;
            imageFound(a, r);
            return;
        }
        imageFirst = null;
        if (a.waitMode == Action.MISSING_CONTINUE) { imageDone(); return; }
        if (a.duration > 0 && waited >= a.duration) {
            imageWait = null;
            conditionsTimedOut++;
            lateness = 0;
            if (a.stopOnTimeout) finish(REASON_CONDITION_TIMEOUT); else advance();
            return;
        }
        schedule(IMAGE_RETRY, IMAGE_RETRY_MS); // never a busy loop, whatever the host does
    }

    private void imageDone() {
        imageWait = null;
        lateness = 0; // the search is not part of the recorded timeline
        advance();
    }

    private void imageFound(final Action a, ImageResult r) {
        conditionsMet++;
        imageWait = null;
        lateness = 0;
        Integer h = imageHits.get(a);
        final int hits = h == null ? 1 : h + 1;
        imageHits.put(a, hits);
        final Runnable after = new Runnable() {
            @Override public void run() {
                if (a.maxHits > 0 && hits >= a.maxHits) finish(REASON_HIT_LIMIT); else advance();
            }
        };
        if ((a.press != Action.PRESS_TAP && a.press != Action.PRESS_LONG) || r.points.isEmpty()) { after.run(); return; }
        GesturePlan plan = imageTapPlan(a, r.points);
        if (plan == null) { after.run(); return; }
        runDue = host.uptimeMillis(); // the tap belongs to now, not to when the step started
        dispatch(plan, imageStep != null && imageStep.action == a ? imageStep : new Step(a, 0, stack.isEmpty() ? null : stack.get(0), 0), after);
    }

    /** One stroke per tap point, one after the other (≤ Android's finger limit). */
    private GesturePlan imageTapPlan(Action a, List<float[]> points) {
        GesturePlanner.Limits lim = host.limits();
        long press = a.press == Action.PRESS_LONG ? Math.max(a.pressMs, 400) : a.pressMs;
        press = Math.max(1, Math.min(press, lim.maxDuration / 2));
        List<Stroke> strokes = new ArrayList<Stroke>();
        long t = 0;
        int max = Math.max(1, Math.min(points.size(), Math.min(lim.maxStrokes, 10)));
        for (int i = 0; i < max; i++) {
            float[] p = points.get(i);
            if (t + press > lim.maxDuration) break;
            Stroke s = new Stroke();
            s.add(p[0], p[1], t);
            s.add(p[0], p[1], t + press);
            strokes.add(s);
            t += press + 120;
        }
        if (strokes.isEmpty()) return null;
        Action g = Action.gesture(strokes);
        return GesturePlanner.plan(g, 1.0, root.settings.jitterPx, random, CoordinateScaler.IDENTITY, lim);
    }

    /** Preview: no screen reading; the image is assumed there (tap drawn where it was cut) or not. */
    private void previewImage(final Action a) {
        long waited = elapsed() - imageWaitStart;
        boolean present = previewAssumeFound;
        boolean wanted = a.press == Action.PRESS_GONE ? !present : present;
        if (wanted) {
            ImageResult r = new ImageResult();
            r.status = IMG_FOUND;
            float sx = a.img.capScreenW > 0 ? host.screenWidth() / (float) a.img.capScreenW : 1f;
            float sy = a.img.capScreenH > 0 ? host.screenHeight() / (float) a.img.capScreenH : 1f;
            r.points.add(new float[]{(a.img.capX + a.img.tapDx) * sx, (a.img.capY + a.img.tapDy) * sy});
            imageFound(a, r);
            return;
        }
        if (a.waitMode == Action.MISSING_CONTINUE) { imageWait = null; schedule(ADVANCE, 300); return; }
        long limit = a.duration > 0 ? Math.min(a.duration, PREVIEW_MAX_WAIT_MS) : PREVIEW_MAX_WAIT_MS;
        if (waited >= limit) {
            imageWait = null;
            conditionsTimedOut++;
            if (a.duration == 0 || a.stopOnTimeout) finish(REASON_CONDITION_TIMEOUT); else advance();
            return;
        }
        schedule(IMAGE_RETRY, Math.min(300, limit - waited));
    }

    /** Check a colour condition once; re-schedules itself until met, timed out or stopped. */
    private void pollColor() {
        Action a = colorWait;
        if (a == null || state != State.RUNNING) return;
        long waited = elapsed() - colorWaitStart;
        if (preview) { previewCondition(a, waited); return; }
        int c = host.sampleColor(Math.round(scaler.x(a.x)), Math.round(scaler.y(a.y)), screenSettledAt);
        if (c == -2) { // capture works, the frame is just older than the last touch
            schedule(POLL, POLL_MS);
            return;
        }
        if (c < 0) {
            if (waited >= CAPTURE_GRACE_MS) { finish(REASON_NO_CAPTURE); return; }
            schedule(POLL, POLL_MS);
            return;
        }
        boolean match = ColorMatch.matches(c, a.color, a.tolerance);
        if (match != a.invert) {
            colorWait = null;
            conditionsMet++;
            lateness = 0; // the wait is not part of the recorded timeline
            advance();
            return;
        }
        if (a.duration > 0 && waited >= a.duration) {
            colorTimedOut(a);
            return;
        }
        schedule(POLL, POLL_MS);
    }

    private void colorTimedOut(Action a) {
        colorWait = null;
        conditionsTimedOut++;
        lateness = 0;
        if (a.stopOnTimeout) finish(REASON_CONDITION_TIMEOUT); else advance();
    }

    /** Preview: conditions are assumed found / not found instead of reading the screen. */
    private void previewCondition(Action a, long waited) {
        if (previewAssumeFound) {
            colorWait = null;
            conditionsMet++;
            lateness = 0;
            schedule(ADVANCE, 300); // a short beat so the step can be seen
            return;
        }
        long limit = a.duration > 0 ? Math.min(a.duration, PREVIEW_MAX_WAIT_MS) : PREVIEW_MAX_WAIT_MS;
        if (waited >= limit) {
            if (a.duration == 0) { // would wait forever
                colorWait = null;
                conditionsTimedOut++;
                finish(REASON_CONDITION_TIMEOUT);
                return;
            }
            colorTimedOut(a);
            return;
        }
        schedule(POLL, Math.min(POLL_MS, limit - waited));
    }

    private void launch(Action a) {
        String pkg = a.packageName == null ? "" : a.packageName.trim();
        boolean ok = pkg.length() > 0 && host.launchApp(pkg);
        screenSettledAt = host.uptimeMillis() + SETTLE_MS;
        if (!ok) {
            if (notices != null) notices.onNotice(NOTICE_APP_MISSING, a);
            advance();
            return;
        }
        if (a.duration <= 0 || preview) { lateness = 0; advance(); return; }
        appWait = a;
        appWaitStart = elapsed();
        pollApp();
    }

    private void pollApp() {
        Action a = appWait;
        if (a == null || state != State.RUNNING) return;
        String fg = host.foregroundPackage();
        if (a.packageName.trim().equals(fg)) {
            appWait = null;
            lateness = 0;
            screenSettledAt = host.uptimeMillis() + SETTLE_MS;
            advance();
            return;
        }
        if (elapsed() - appWaitStart >= a.duration) {
            appWait = null;
            lateness = 0;
            if (notices != null) notices.onNotice(NOTICE_APP_SLOW, a);
            advance();
            return;
        }
        schedule(APP_WAIT, APP_POLL_MS);
    }

    private void finish(String reason) {
        stopInternal(reason, true);
    }

    private void stopInternal(String reason, boolean notify) {
        generation++;
        if (scriptWait != null) { scriptWait = null; host.stopScriptStep(); }
        scriptDoneWhilePaused = false;
        cancelTimer();
        host.removeCallbacks(deadline);
        kindOnPause = 0;
        pending = null;
        retryOnResume = null;
        gestureInFlight = false;
        colorWait = null;
        appWait = null;
        imageWait = null;
        imageFirst = null;
        imageToken++;
        readWait = null;
        readToken++;
        stack.clear();
        setState(State.FINISHED);
        if (notify && listener != null) listener.onFinished(reason);
    }

    private void setState(State s) {
        if (state == s) return;
        state = s;
        if (listener != null) listener.onStateChanged(s);
    }
}
