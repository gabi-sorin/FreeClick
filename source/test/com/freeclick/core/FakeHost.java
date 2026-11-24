package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;

/** Virtual-clock Host for PlaybackEngine tests. Gestures "complete" after their duration. */
public final class FakeHost implements PlaybackEngine.Host {

    static final class Timer {
        final long due; final long seq; final Runnable r;
        Timer(long due, long seq, Runnable r) { this.due = due; this.seq = seq; this.r = r; }
    }

    public static final class Event {
        public final long time; public final String kind; public final GesturePlan plan; public final int global;
        Event(long time, String kind, GesturePlan plan, int global) {
            this.time = time; this.kind = kind; this.plan = plan; this.global = global;
        }
        @Override public String toString() { return time + ":" + kind; }
    }

    public long now = 1000;
    private long seq;
    private final List<Timer> timers = new ArrayList<Timer>();
    public final List<Event> events = new ArrayList<Event>();
    public boolean failGestures;
    /** Extra delay before a gesture's completion callback (real phones report completion a bit late). */
    public long callbackLatencyMs;
    public int width = 1080, height = 2400;
    public GesturePlanner.Limits limits = new GesturePlanner.Limits();
    /** Screen colour over time: from each key time on, the pixel has that colour (-1 = no capture). */
    public final java.util.TreeMap<Long, Integer> colorTimeline = new java.util.TreeMap<Long, Integer>();
    public final List<String> samples = new ArrayList<String>();

    /** Fuzz checks: never two gestures at once, never a gesture while not RUNNING. */
    public PlaybackEngine engine;
    public int inFlight, violations;
    public final List<String> violationLog = new ArrayList<String>();
    public java.util.Random random; // when set: random failures and latencies

    @Override public void dispatchGesture(final GesturePlan plan, final PlaybackEngine.GestureCallback cb) {
        events.add(new Event(now, "gesture", plan, 0));
        if (inFlight > 0) { violations++; violationLog.add(now + ": gesture while another is in flight"); }
        if (engine != null && engine.state() != PlaybackEngine.State.RUNNING) {
            violations++;
            violationLog.add(now + ": gesture while " + engine.state());
        }
        checkPlan(plan);
        inFlight++;
        final boolean fail = failGestures || (random != null && random.nextInt(10) == 0);
        long latency = callbackLatencyMs + (random != null ? random.nextInt(80) : 0);
        postDelayed(new Runnable() { public void run() { inFlight--; cb.onResult(!fail); } }, plan.totalDuration() + latency);
    }

    /** Every plan must be acceptable to Android's GestureDescription. */
    void checkPlan(GesturePlan plan) {
        GesturePlanner.Limits l = limits();
        if (plan.strokes.isEmpty() || plan.strokes.size() > l.maxStrokes) bad("stroke count " + plan.strokes.size());
        if (plan.totalDuration() > l.maxDuration) bad("too long " + plan.totalDuration());
        for (GesturePlan.PlannedStroke s : plan.strokes) {
            if (s.points.isEmpty()) bad("empty stroke");
            if (s.startTime < 0 || s.duration < 1) bad("bad times " + s.startTime + "/" + s.duration);
            for (float[] p : s.points) {
                if (Float.isNaN(p[0]) || Float.isNaN(p[1]) || p[0] < 0 || p[1] < 0 || p[0] > width - 1 || p[1] > height - 1) {
                    bad("point off screen " + p[0] + "," + p[1]);
                }
            }
        }
    }

    private void bad(String what) { violations++; violationLog.add(now + ": " + what); }

    @Override public boolean performGlobal(int action) {
        events.add(new Event(now, "global", null, action));
        return true;
    }

    @Override public void postDelayed(Runnable r, long delayMs) {
        timers.add(new Timer(now + Math.max(0, delayMs), seq++, r));
    }

    @Override public void removeCallbacks(Runnable r) {
        for (int i = timers.size() - 1; i >= 0; i--) if (timers.get(i).r == r) timers.remove(i);
    }

    @Override public long uptimeMillis() { return now; }
    /** Frames arrive every frameIntervalMs (0 = every read sees the current screen). */
    public long frameIntervalMs;
    /** Every notBefore the engine asked for (fresh-frame rule). */
    public final List<Long> notBefores = new ArrayList<Long>();

    @Override public int sampleColor(int x, int y, long notBefore) {
        if (samples.size() < 10000) { samples.add(x + "," + y); notBefores.add(notBefore); }
        long frameTime = frameIntervalMs > 0 ? (now / frameIntervalMs) * frameIntervalMs : now;
        java.util.Map.Entry<Long, Integer> e = colorTimeline.floorEntry(frameTime);
        if (e == null) return -1;
        if (frameTime < notBefore) return -2;
        return e.getValue();
    }

    /** Installed apps for launchApp; the app comes to the front appLaunchDelayMs later. */
    public final java.util.Set<String> installed = new java.util.HashSet<String>();
    public long appLaunchDelayMs = 400;
    public String foreground = "com.example.home";
    public final List<String> launched = new ArrayList<String>();

    @Override public boolean launchApp(final String pkg) {
        events.add(new Event(now, "launch:" + pkg, null, 0));
        if (!installed.contains(pkg)) return false;
        launched.add(pkg);
        postDelayed(new Runnable() { public void run() { foreground = pkg; } }, appLaunchDelayMs);
        return true;
    }

    @Override public String foregroundPackage() { return foreground; }

    // ------------------------------------------------------------------ images
    /** An image visible on screen from `from` to `to` (virtual ms) with its top-left at x,y. */
    public static final class Appearance {
        public final long from, to; public final float x, y; public boolean covered;
        public Appearance(long from, long to, float x, float y) { this.from = from; this.to = to; this.x = x; this.y = y; }
    }
    public final java.util.Map<String, List<Appearance>> imageTimeline = new java.util.HashMap<String, List<Appearance>>();
    /** Time from the frame to the answer (matching). */
    public long imageLatencyMs = 60;
    public boolean imageNoCapture;
    public boolean imageMissing;
    public long protectedFrom = -1;
    public final List<Long> imageRequests = new ArrayList<Long>();
    public int imageAnswers;

    public FakeHost show(String id, long from, long to, float x, float y) {
        List<Appearance> l = imageTimeline.get(id);
        if (l == null) { l = new ArrayList<Appearance>(); imageTimeline.put(id, l); }
        l.add(new Appearance(from, to, x, y));
        return this;
    }

    @Override public void findImage(final PlaybackEngine.ImageRequest req, final PlaybackEngine.ImageCallback cb) {
        imageRequests.add(req.notBefore);
        long t = Math.max(now, req.notBefore);
        if (frameIntervalMs > 0) t = ((t + frameIntervalMs - 1) / frameIntervalMs) * frameIntervalMs;
        final long frameTime = t;
        long latency = imageLatencyMs + (random != null ? random.nextInt(150) : 0);
        postDelayed(new Runnable() {
            public void run() {
                imageAnswers++;
                PlaybackEngine.ImageResult r = new PlaybackEngine.ImageResult();
                r.frameTime = frameTime;
                if (imageNoCapture) { r.status = PlaybackEngine.IMG_NO_CAPTURE; cb.onImage(r); return; }
                if (imageMissing) { r.status = PlaybackEngine.IMG_MISSING; cb.onImage(r); return; }
                if (protectedFrom >= 0 && frameTime >= protectedFrom) { r.status = PlaybackEngine.IMG_PROTECTED; cb.onImage(r); return; }
                List<Appearance> l = imageTimeline.get(req.spec.id);
                List<Appearance> vis = new ArrayList<Appearance>();
                if (l != null) for (Appearance ap : l) if (frameTime >= ap.from && frameTime < ap.to) vis.add(ap);
                java.util.Collections.sort(vis, new java.util.Comparator<Appearance>() {
                    public int compare(Appearance a, Appearance b) { return a.y != b.y ? Float.compare(a.y, b.y) : Float.compare(a.x, b.x); }
                });
                boolean any = false;
                for (Appearance ap : vis) {
                    if (ap.covered) { r.covered = true; continue; }
                    any = true;
                    r.points.add(new float[]{ap.x + req.spec.tapDx, ap.y + req.spec.tapDy});
                    if (!req.all) break;
                }
                r.status = any ? PlaybackEngine.IMG_FOUND : PlaybackEngine.IMG_NOT_FOUND;
                r.best = any ? 0.97 : 0.4;
                cb.onImage(r);
            }
        }, frameTime - now + latency);
    }
    // ------------------------------------------------------------------ console + reading
    public final List<String> printed = new ArrayList<String>();
    public final List<Integer> printedKinds = new ArrayList<Integer>();
    @Override public void print(String text, int kind) {
        if (printed.size() < 20000) { printed.add(text); printedKinds.add(kind); }
    }
    /** What the read zone shows over time (from each key time on). */
    public final java.util.TreeMap<Long, String> textTimeline = new java.util.TreeMap<Long, String>();
    public long readLatencyMs = 80;
    public boolean readNoCapture;
    public final List<PlaybackEngine.ReadRequest> readRequests = new ArrayList<PlaybackEngine.ReadRequest>();

    @Override public void readText(final PlaybackEngine.ReadRequest req, final PlaybackEngine.ReadCallback cb) {
        readRequests.add(req);
        long t = Math.max(now, req.notBefore);
        if (frameIntervalMs > 0) t = ((t + frameIntervalMs - 1) / frameIntervalMs) * frameIntervalMs;
        final long frameTime = t;
        long latency = readLatencyMs + (random != null ? random.nextInt(120) : 0);
        postDelayed(new Runnable() {
            public void run() {
                PlaybackEngine.ReadResult r = new PlaybackEngine.ReadResult();
                r.frameTime = frameTime;
                if (readNoCapture) { r.status = PlaybackEngine.IMG_NO_CAPTURE; cb.onRead(r); return; }
                if (protectedFrom >= 0 && frameTime >= protectedFrom) { r.status = PlaybackEngine.IMG_PROTECTED; cb.onRead(r); return; }
                java.util.Map.Entry<Long, String> e = textTimeline.floorEntry(frameTime);
                r.text = e == null ? "" : e.getValue();
                r.number = req.mode == Action.READ_NUMBER ? Numbers.parse(r.text) : null;
                r.confidence = 0.9;
                cb.onRead(r);
            }
        }, frameTime - now + latency);
    }

    @Override public int screenWidth() { return width; }
    @Override public int screenHeight() { return height; }
    @Override public GesturePlanner.Limits limits() {
        limits.screenWidth = width; limits.screenHeight = height; return limits;
    }

    /** Run timers in order until the clock reaches `until` (inclusive). */
    public void runUntil(long until) {
        while (true) {
            Timer next = null;
            for (Timer t : timers) if (next == null || t.due < next.due || (t.due == next.due && t.seq < next.seq)) next = t;
            if (next == null || next.due > until) break;
            timers.remove(next);
            now = Math.max(now, next.due);
            next.r.run();
        }
        now = Math.max(now, until);
    }

    /** Run everything (bounded, to catch accidental infinite loops). */
    public void runAll() { runAll(100000); }

    public void runAll(int maxTimers) {
        int n = 0;
        while (!timers.isEmpty()) {
            if (++n > maxTimers) throw new AssertionError("too many timers - infinite loop?");
            Timer next = null;
            for (Timer t : timers) if (next == null || t.due < next.due || (t.due == next.due && t.seq < next.seq)) next = t;
            timers.remove(next);
            now = Math.max(now, next.due);
            next.r.run();
        }
    }

    public int pendingTimers() { return timers.size(); }

    public String pendingDescription() {
        StringBuilder sb = new StringBuilder();
        for (Timer t : timers) sb.append(t.r.getClass().getName()).append('@').append(t.due - 1000).append(' ');
        return sb.toString();
    }

    public List<Event> gestures() {
        List<Event> out = new ArrayList<Event>();
        for (Event e : events) if (e.kind.equals("gesture")) out.add(e);
        return out;
    }

    // ---- script steps: they "run" for scriptMs of virtual time
    long scriptMs = 1000;
    final List<String> scripts = new ArrayList<String>();
    boolean scriptPaused;
    Runnable scriptDone;

    @Override public void runScriptStep(Macro script, int times, final Runnable done) {
        scripts.add(script.name + " x" + times);
        scriptDone = done;
        postDelayed(new Runnable() { public void run() { if (scriptDone == done) { scriptDone = null; done.run(); } } }, scriptMs * times);
    }

    @Override public void stopScriptStep() { scripts.add("stop"); scriptDone = null; }

    @Override public void pauseScriptStep(boolean paused) { scriptPaused = paused; }
}
