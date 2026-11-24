package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** FreeClick 2.2: 🔥 Turbo (taps exactly where you touch). */
public final class V22Tests {

    static void testTurboReadsYourTouch() {
        T.eq(Turbo.SWIPE, Turbo.classify(40, 14, 100, true, 10), "moved = swipe goes through");
        T.eq(Turbo.SWIPE, Turbo.classify(40, 14, 2000, true, 10), "a slow swipe is still a swipe");
        T.eq(Turbo.HOLD, Turbo.classify(3, 14, 600, true, 10), "held = non-stop");
        T.eq(Turbo.BURST, Turbo.classify(3, 14, 600, false, 10), "hold off: a long tap is a burst");
        T.eq(Turbo.BURST, Turbo.classify(0, 14, 80, true, 10), "short tap = burst");
        T.eq(Turbo.PASS, Turbo.classify(0, 14, 80, true, 0), "burst 0 = an ordinary tap");
        T.eq(Turbo.HOLD, Turbo.classify(0, 14, Turbo.HOLD_MS, true, 0), "exactly the hold time counts as held");
    }

    static void testTurboTapsFitAndroid() {
        Random r = new Random(7);
        for (int rate = 1; rate <= 100; rate += 3) {
            GesturePlan p = Turbo.taps(500, 900, 1000, rate, 0, 20, 1080, 2400, r);
            T.eq(20, p.strokes.size(), "capped at the strokes Android takes (rate " + rate + ")");
            long period = Turbo.period(rate);
            T.ok(period >= 1000 / Turbo.MAX_RATE && period <= 1000 / Turbo.MIN_RATE, "period in range " + period);
            for (int i = 0; i < p.strokes.size(); i++) {
                GesturePlan.PlannedStroke s = p.strokes.get(i);
                T.eq((long) i * period, s.startTime, "evenly spaced");
                T.ok(s.duration >= 8 && s.duration <= 40 && s.duration < period, "finger up before the next tap");
                T.eq(1, s.points.size(), "a tap is one point");
                T.eq(500f, s.points.get(0)[0], "x");
                T.eq(900f, s.points.get(0)[1], "y");
            }
            T.ok(p.totalDuration() < 60000, "well under Android's 60 s gesture limit");
        }
        T.eq(3, Turbo.taps(1, 1, 3, 20, 0, 20, 0, 0, r).strokes.size(), "only what is left");
        T.eq(0, Turbo.taps(1, 1, 0, 20, 0, 20, 0, 0, r).strokes.size(), "nothing left = nothing sent");
        T.eq(1, Turbo.taps(1, 1, 5, 20, 0, 0, 0, 0, r).strokes.size(), "a phone reporting 0 strokes still gets one");
    }

    static void testTurboSpreadStaysNearAndOnScreen() {
        Random r = new Random(11);
        for (int k = 0; k < 200; k++) {
            float x = r.nextInt(1080), y = r.nextInt(2400);
            int spread = r.nextInt(41);
            GesturePlan p = Turbo.taps(x, y, 20, 30, spread, 20, 1080, 2400, r);
            for (GesturePlan.PlannedStroke s : p.strokes) {
                float[] pt = s.points.get(0);
                T.ok(pt[0] >= 0 && pt[0] <= 1079 && pt[1] >= 0 && pt[1] <= 2399, "on screen " + pt[0] + "," + pt[1]);
                T.ok(Math.abs(pt[0] - x) <= spread + 0.01 && Math.abs(pt[1] - y) <= spread + 0.01, "within the spread");
            }
        }
        GesturePlan edge = Turbo.taps(2, 2, 20, 30, 40, 20, 1080, 2400, r);
        for (GesturePlan.PlannedStroke s : edge.strokes) T.ok(s.points.get(0)[0] >= 0 && s.points.get(0)[1] >= 0, "never negative");
    }

    static void testTurboSwipeGoesThrough() {
        List<float[]> pts = new ArrayList<float[]>();
        for (int i = 0; i <= 120; i++) pts.add(new float[]{100 + i * 5, 2000 - i * 10, i * 4});
        GesturePlan p = Turbo.swipe(pts, 1080, 2400);
        T.eq(1, p.strokes.size(), "one finger");
        GesturePlan.PlannedStroke s = p.strokes.get(0);
        T.ok(s.points.size() <= 32 && s.points.size() >= 2, "a few points: " + s.points.size());
        T.eq(100f, s.points.get(0)[0], "starts where you started");
        float[] end = s.points.get(s.points.size() - 1);
        T.eq(700f, end[0], "ends where you lifted (x)");
        T.eq(800f, end[1], "ends where you lifted (y)");
        T.eq(480L, s.duration, "as fast as you swiped");
        List<float[]> flick = new ArrayList<float[]>();
        flick.add(new float[]{10, 10, 0});
        flick.add(new float[]{-50, 3000, 5});
        GesturePlan f = Turbo.swipe(flick, 1080, 2400);
        T.eq(60L, f.strokes.get(0).duration, "a flick still lasts long enough to register");
        float[] fe = f.strokes.get(0).points.get(1);
        T.ok(fe[0] == 0 && fe[1] == 2399, "kept on screen");
        T.eq(0, Turbo.swipe(new ArrayList<float[]>(), 1080, 2400).strokes.size(), "no swipe from nothing");
    }

    static void testTurboMeasuredRate() {
        T.eq(20, Turbo.measuredRate(20, 1000), "20 in a second");
        T.eq(0, Turbo.measuredRate(5, 0), "no time yet");
        T.eq(33, Turbo.measuredRate(100, 3000), "rounded");
    }

    // ------------------------------------------------------------------ 🌙 AFK (2.2.1)
    static void testAfkPausesOutsideTheGameAndResumesWhenBack() {
        AfkWatch w = new AfkWatch();
        w.start("com.game");
        T.ok(w.watching(), "watching the game");
        T.eq(AfkWatch.NONE, w.onForeground("com.game", 0), "in the game");
        T.eq(AfkWatch.PAUSE, w.onForeground("com.whatsapp", 1000), "another app: pause, tap nothing there");
        T.eq(AfkWatch.NONE, w.onForeground("com.other", 2000), "already paused");
        T.eq(AfkWatch.NONE, w.tick(5000), "not long enough");
        T.eq(AfkWatch.RESUME, w.onForeground("com.game", 6000), "back: resume");
        T.eq(AfkWatch.IN_GAME, w.phase(), "in game");
        T.eq(AfkWatch.NONE, w.tick(60000), "nothing to do in the game");
    }

    static void testAfkReopensAClosedGameAndStartsOver() {
        AfkWatch w = new AfkWatch();
        w.start("com.game");
        T.eq(AfkWatch.PAUSE, w.onForeground("com.launcher", 0), "the game crashed to the home screen");
        T.eq(AfkWatch.ASK, w.tick(w.awayMs), "after a while: say what happens next (with Cancel)");
        T.eq(AfkWatch.NONE, w.tick(w.awayMs + 5000), "the countdown runs");
        long t = w.awayMs + w.askMs;
        T.eq(AfkWatch.REOPEN, w.tick(t), "reopen the game");
        T.eq(AfkWatch.NONE, w.onForeground("com.launcher", t + 100), "still loading");
        T.eq(AfkWatch.NONE, w.onForeground("com.game", t + 3000), "the game is up");
        T.eq(AfkWatch.SETTLING, w.phase(), "let it load");
        T.eq(AfkWatch.NONE, w.tick(t + 3000 + w.settleMs - 1), "still loading");
        T.eq(AfkWatch.RESTART, w.tick(t + 3000 + w.settleMs), "start the run over");
        T.eq(1, w.restartCount(), "counted");
        T.ok(!w.pausedByUs(), "nothing left paused");
    }

    static void testAfkCancelWaitsForYou() {
        AfkWatch w = new AfkWatch();
        w.start("com.game");
        w.onForeground("com.chat", 0);
        T.eq(AfkWatch.ASK, w.tick(w.awayMs), "ask");
        w.hold();
        T.eq(AfkWatch.HELD, w.phase(), "held");
        T.eq(AfkWatch.NONE, w.tick(10 * 60000), "never reopens while you said no");
        T.eq(AfkWatch.RESUME, w.onForeground("com.game", 10 * 60000 + 1), "you came back: resume");
    }

    static void testAfkAppsTheRunOpensAreNotAway() {
        AfkWatch w = new AfkWatch();
        w.start("com.game");
        w.runOpened("com.game2", 1000);
        T.eq(AfkWatch.NONE, w.onForeground("com.game2", 1500), "an 'open app' step");
        T.eq(AfkWatch.NONE, w.onForeground("com.game2", 60000), "still part of the run later");
        w.runSwitched(70000);
        T.eq(AfkWatch.NONE, w.onForeground("com.launcher", 71000), "a home step");
        T.eq(AfkWatch.NONE, w.onForeground("com.launcher", 200000), "home is part of this run now");
        T.eq(AfkWatch.PAUSE, w.onForeground("com.ads", 300000), "something else is still away");
    }

    static void testAfkGivesUpInsteadOfLooping() {
        AfkWatch w = new AfkWatch();
        w.start("com.game");
        long t = 0;
        for (int i = 0; i < w.maxRestartsPerHour; i++) {
            T.eq(AfkWatch.PAUSE, w.onForeground("com.launcher", t), "crash " + i);
            T.eq(AfkWatch.ASK, w.tick(t += w.awayMs), "ask " + i);
            T.eq(AfkWatch.REOPEN, w.tick(t += w.askMs), "reopen " + i);
            w.onForeground("com.game", t += 1000);
            T.eq(AfkWatch.RESTART, w.tick(t += w.settleMs), "restart " + i);
            t += 1000;
        }
        w.onForeground("com.launcher", t);
        w.tick(t += w.awayMs);
        T.eq(AfkWatch.GIVE_UP, w.tick(t += w.askMs), "too many in an hour: stop");
        T.eq(AfkWatch.NONE, w.onForeground("com.game", t + 1), "gave up: nothing more");
        AfkWatch v = new AfkWatch();
        v.start("com.game");
        v.onForeground("com.launcher", 0);
        v.tick(v.awayMs);
        long u = v.awayMs + v.askMs;
        T.eq(AfkWatch.REOPEN, v.tick(u), "try 1");
        T.eq(AfkWatch.REOPEN, v.tick(u += v.loadTimeoutMs), "try 2");
        T.eq(AfkWatch.REOPEN, v.tick(u += v.loadTimeoutMs), "try 3");
        T.eq(AfkWatch.GIVE_UP, v.tick(u + v.loadTimeoutMs), "the game does not open: stop");
    }

    static void testAfkWithoutAGameWatchesNothing() {
        AfkWatch w = new AfkWatch();
        w.start(null);
        T.ok(!w.watching(), "no game");
        T.eq(AfkWatch.NONE, w.onForeground("com.x", 0), "nothing");
        T.eq(AfkWatch.NONE, w.tick(999999), "nothing");
        AfkWatch k = new AfkWatch();
        k.start("com.game");
        k.expectReopen(0);
        T.eq(AfkWatch.NONE, k.onForeground("com.game", 2000), "after Android restarted FreeClick: the game comes up");
        T.eq(AfkWatch.RESTART, k.tick(2000 + k.settleMs), "then the run starts over");
    }

    static void testAfkBatteryAndHeat() {
        T.eq(AfkWatch.BATTERY_OK, AfkWatch.battery(80, false, 30, 15, AfkWatch.BATTERY_OK), "fine");
        T.eq(AfkWatch.BATTERY_LOW, AfkWatch.battery(15, false, 30, 15, AfkWatch.BATTERY_OK), "low");
        T.eq(AfkWatch.BATTERY_LOW, AfkWatch.battery(18, false, 30, 15, AfkWatch.BATTERY_LOW), "waits for +5 %");
        T.eq(AfkWatch.BATTERY_OK, AfkWatch.battery(20, false, 30, 15, AfkWatch.BATTERY_LOW), "enough again");
        T.eq(AfkWatch.BATTERY_OK, AfkWatch.battery(10, true, 30, 15, AfkWatch.BATTERY_LOW), "charging: go on");
        T.eq(AfkWatch.BATTERY_OK, AfkWatch.battery(3, false, 30, 0, AfkWatch.BATTERY_OK), "0 = off");
        T.eq(AfkWatch.BATTERY_HOT, AfkWatch.battery(80, true, 45, 15, AfkWatch.BATTERY_OK), "hot");
        T.eq(AfkWatch.BATTERY_HOT, AfkWatch.battery(80, true, 42, 15, AfkWatch.BATTERY_HOT), "cools down to 40 first");
        T.eq(AfkWatch.BATTERY_OK, AfkWatch.battery(80, true, 40, 15, AfkWatch.BATTERY_HOT), "cool again");
        T.eq(AfkWatch.BATTERY_OK, AfkWatch.battery(-1, false, 30, 15, AfkWatch.BATTERY_OK), "unknown level: never blocks");
        T.eq("45 s", AfkWatch.duration(45000), "seconds");
        T.eq("12 min", AfkWatch.duration(12 * 60000 + 5000), "minutes");
        T.eq("2 h 05 min", AfkWatch.duration((125 * 60 + 30) * 1000L), "hours");
    }

    // ------------------------------------------------------------------ #define (2.3)
    private static final com.freeclick.core.script.ScriptSimulator.Screen BLACK = new com.freeclick.core.script.ScriptSimulator.Screen() {
        public int pixel(int x, int y) { return 0; }
        public int width() { return 1080; }
        public int height() { return 2400; }
        public String read(int x1, int y1, int x2, int y2, boolean number) { return null; }
        public List<int[]> find(ImageSpec s, boolean all) { return new ArrayList<int[]>(); }
    };

    private static com.freeclick.core.script.ScriptSimulator.Result sim(String src) {
        return com.freeclick.core.script.ScriptSimulator.run(src, new ArrayList<Action>(), BLACK, 100, 600000, 3000);
    }

    static void testDefineLikeCpp() {
        com.freeclick.core.script.ScriptSimulator.Result r = sim("#define p point\n"
                + "p a = p(100, 200);\n"
                + "tap(a);\n"
                + "tap(p(5, 6));\n");
        T.eq(com.freeclick.core.script.ScriptSimulator.END_DONE, r.end, r.describeEnd());
        T.eq(2, r.touches.size(), "two taps");
        T.ok(r.touches.get(0).x == 100 && r.touches.get(0).y == 200, "p(100, 200)");
        T.ok(r.touches.get(1).x == 5 && r.touches.get(1).line == 4, "line numbers stay right");
        r = sim("#define N 3\n#define BTN p(10, 20)\n#define p point\nfor (int i = 0; i < N; i++) tap(BTN);\n");
        T.eq(3, r.touches.size(), "constants and nested names (" + r.describeEnd() + ")");
        r = sim("#define TAP2(a, b) tap(a); tap(b)\nTAP2(point(1, 1), point(2, 2));\n");
        T.eq(2, r.touches.size(), "with parameters (" + r.describeEnd() + ")");
        T.eq(2, r.touches.get(1).x, "second argument");
        r = sim("#define MSG \"salut\"\nprint(MSG);\n#undef MSG\nint MSG = 3;\nprint(MSG);\n");
        T.eq("salut", r.prints.get(0).text, "string");
        T.eq("3", r.prints.get(1).text, "#undef");
        r = sim("#define LUNG tap(1, 1); \\\n  tap(2, 2)\nLUNG;\n");
        T.eq(2, r.touches.size(), "\\ continues the line (" + r.describeEnd() + ")");
        r = sim("int x = #ABCDEF == #abcdef ? 1 : 0;\nprint(x);\n");
        T.ok(r.error == null, "colours still work: " + r.describeEnd());
    }

    static void testDefineMistakesAreExplained() {
        com.freeclick.core.script.ScriptError e = com.freeclick.core.script.ScriptRunner.check("#define\ntap(1, 1);");
        T.ok(e != null && e.getMessage().contains("#define") && e.line == 1, "no name: " + (e == null ? "" : e.describe()));
        e = com.freeclick.core.script.ScriptRunner.check("#define F(a, b) tap(a, b)\nF(1);");
        T.ok(e != null && e.line == 2 && e.getMessage().contains("2"), "wrong count: " + (e == null ? "" : e.describe()));
        e = com.freeclick.core.script.ScriptRunner.check("#define A B\n#define B A\nint A = 1;");
        T.ok(e == null || e.getMessage() != null, "a loop of names does not hang");
        e = com.freeclick.core.script.ScriptRunner.check("int y = 1; #define p point");
        T.ok(e != null, "#define only at the start of a line");
    }

    static void testDefinedPointsShowOnThePicture() {
        List<com.freeclick.core.script.CodeMarks.Mark> m = com.freeclick.core.script.CodeMarks.scan(
                "#define p point\ntap(p(100, 200));\nswipe(p(1, 2), p(3, 4));\nint p2 = pipeta(p(7, 8)) == #34A853 ? 1 : 0;\n");
        int touches = 0, swipes = 0, colors = 0;
        for (com.freeclick.core.script.CodeMarks.Mark k : m) {
            if (k.kind == com.freeclick.core.script.CodeMarks.TOUCH) touches++;
            if (k.kind == com.freeclick.core.script.CodeMarks.SWIPE) swipes++;
            if (k.kind == com.freeclick.core.script.CodeMarks.COLOR) colors++;
        }
        T.ok(touches >= 1, "p(100, 200) is a point on the picture (" + m.size() + " marks)");
        T.eq(1, swipes, "swipe(p(…), p(…))");
        T.eq(1, colors, "pipeta(p) + #colour");
        String one = "#define p point\ntap(p(100, 200));\n";
        String moved = com.freeclick.core.script.CodeMarks.replace(one, com.freeclick.core.script.CodeMarks.scan(one).get(0), new int[]{300, 400});
        T.ok(moved.contains("p(300, 400)"), "moving it keeps the short name: " + moved);
    }

    // ------------------------------------------------------------------ your fingers (2.3)
    private static String run(ScriptTests.Host h, String src) {
        ScriptTests.Rec r = ScriptTests.run(src, h);
        if (r.error != null) throw new AssertionError("script failed: " + r.error.describe());
        StringBuilder b = new StringBuilder();
        for (String x : h.out) { if (b.length() > 0) b.append('|'); b.append(x); }
        return b.toString();
    }

    static void testTrackerReadsGestures() {
        TouchTracker t = new TouchTracker();
        t.feed(TouchTracker.DOWN, 0, 100, 200, 0);
        T.eq(1, t.fingers(), "one finger");
        t.feed(TouchTracker.UP, 0, 102, 201, 80);
        T.eq(0, t.fingers(), "lifted");
        T.eq("tap", t.next(0, null).describe(), "a tap");
        t.feed(TouchTracker.DOWN, 0, 104, 205, 200);
        t.feed(TouchTracker.UP, 0, 104, 205, 260);
        T.ok(t.next(1, TouchTracker.DOUBLE) != null, "two taps close together: double tap");
        long s = t.seq();
        t.feed(TouchTracker.DOWN, 0, 500, 1500, 1000);
        t.feed(TouchTracker.MOVE, 0, 500, 1000, 1100);
        t.feed(TouchTracker.UP, 0, 510, 600, 1200);
        TouchTracker.Gesture g = t.next(s, null);
        T.eq("swipe sus", g.describe(), "swipe up");
        T.ok(g.x1 == 500 && g.y2 == 600, "from where to where");
        s = t.seq();
        t.feed(TouchTracker.DOWN, 0, 300, 300, 2000);
        t.feed(TouchTracker.UP, 0, 300, 300, 2700);
        T.eq("lung", t.next(s, null).describe(), "long press");
        s = t.seq();
        t.feed(TouchTracker.DOWN, 0, 300, 300, 3000);
        t.feed(TouchTracker.DOWN, 1, 600, 300, 3020);
        T.eq(2, t.fingers(), "two fingers");
        T.ok(t.lastAtLeast(2) == 3020, "when two were down");
        int[] f1 = t.finger(1);
        T.ok(f1 != null && f1[0] == 600, "second finger");
        t.feed(TouchTracker.UP, 1, 600, 300, 3100);
        t.feed(TouchTracker.UP, 0, 300, 300, 3110);
        T.eq("2 degete", t.next(s, null).describe(), "two-finger tap");
        T.eq(3110L, t.lastZero(), "no finger left");
        t.feed(TouchTracker.DOWN, 0, 1, 1, 4000);
        t.feed(TouchTracker.CANCEL, 0, 0, 0, 4010);
        T.eq(0, t.fingers(), "cancel clears");
    }

    static void testWaitForTouchGivesThePlace() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.finger(2000, 0, 300, 700, 300, 700, 60);
        String out = run(h, "#define p point\np unde = asteaptaAtingere();\nprint(unde.x, \",\", unde.y);\nfor (int i = 0; i < 3; i++) tap(unde);\n");
        T.eq("300,700", out, "the place you touched");
        T.eq(3, h.did.size(), "then tapped there 3 times: " + h.did);
        T.ok(h.did.get(0).contains("300,700"), "exactly there");
        T.eq("true/true", h.listening.get(0), "listened (and passed the touch on)");
        T.eq("false/true", h.listening.get(1), "stopped listening after");
    }

    static void testGesturesFingersAndTimeouts() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.finger(1500, 0, 500, 1500, 500, 600, 200);   // swipe up
        h.finger(3000, 0, 100, 100, 100, 100, 50);     // tap
        h.finger(3080, 0, 102, 101, 102, 101, 50);     // tap again: double
        h.finger(5000, 0, 200, 200, 200, 200, 300);    // two fingers
        h.finger(5050, 1, 400, 200, 400, 200, 200);
        String out = run(h, "print(asteaptaGest());\nprint(sfarsitGest().y);\npoint d = asteaptaDubluTap();\nprint(d.x);\n"
                + "print(asteaptaDegete(2));\nprint(asteaptaFaraDegete());\nprint(asteaptaAtingere(500).x);\n"
                + "print(asteaptaSwipe(300) == \"\");\n");
        T.eq("swipe sus|600|102|true|true|-1|true", out, "gesture, end point, double tap, two fingers, none, timeouts");
    }

    static void testTouchInZoneAndPickedZone() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.finger(1200, 0, 50, 50, 50, 50, 40);        // outside: ignored
        h.finger(1600, 0, 550, 1250, 550, 1250, 40);  // inside
        h.finger(2500, 0, 100, 900, 800, 400, 300);   // a dragged zone
        String out = run(h, "rect buton = rect(500, 1200, 600, 1300);\nprint(asteaptaAtingereInZona(buton));\n"
                + "rect z = alegeZona();\nprint(z.x1, \" \", z.y1, \" \", z.x2, \" \", z.y2);\n");
        T.eq("true|100 400 800 900", out, "only a touch inside counts; the zone is sorted");
    }

    static void testLiveFingersAndNewTaps() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.finger(1500, 0, 10, 20, 10, 20, 40);
        h.finger(1700, 0, 30, 40, 30, 40, 40);
        String out = run(h, "asculta(true);\nprint(atingeriNoi());\nwait(1s);\nprint(atingeriNoi());\nprint(ultimaAtingere().x);\nprint(degete());\n");
        T.eq("0|2|30|0", out, "taps since the last look; the last place touched");
        T.eq("true/true", h.listening.get(0), "asculta(true) listens");
        T.ok(com.freeclick.core.script.ScriptRunner.usesTouchInput("int n = degete();"), "a listening script");
        T.ok(com.freeclick.core.script.ScriptRunner.usesTouchInput("color c = get_touch_pipeta();"), "get_touch_pipeta() listens");
        T.ok(!com.freeclick.core.script.ScriptRunner.usesTouchInput("color c = pipeta(1, 2);"), "pipeta(x, y) only reads the screen");
        // the rule: live finger readings need asculta(true)
        ScriptTests.Rec r = ScriptTests.run("int n = degete();", new ScriptTests.Host());
        T.ok(r.error != null && r.error.getMessage().contains("asculta(true)"), "explained: " + (r.error == null ? "" : r.error.describe()));
        com.freeclick.core.script.ScriptError e = com.freeclick.core.script.ScriptRunner.check("color c = pipeta();");
        T.ok(e != null && e.getMessage().contains("pipetaAtingere"), "pipeta() without coordinates is a mistake: " + (e == null ? "" : e.describe()));
    }

    static void testPipetaReadsWhereYouTouch() {
        ScriptTests.Host h = new ScriptTests.Host();
        h.pixels.put("40,50", 0x34A853);
        h.finger(1500, 0, 40, 50, 40, 50, 40);
        String out = run(h, "color c = get_touch_pipeta();\nprint(c.hex());\nprint(pipeta(40, 50) == c);\n");
        T.eq("#34A853|true", out, "the colour under your finger, and pipeta(x, y)");
    }

    // ------------------------------------------------------------------ 📤 share codes (2.3)
    static void testShareCodesRoundTrip() {
        Macro s = new Macro("Farm aur");
        s.kind = Macro.KIND_SCRIPT;
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 40; i++) code.append("tap(").append(100 + i).append(", 200); wait(300);\n");
        s.script = code.toString();
        Macro t = new Macro("Ținte boss");
        t.actions.add(Action.tap(10, 20));
        Macro child = new Macro("sub");
        child.actions.add(Action.tap(1, 2));
        Action call = new Action(Action.Type.MACRO);
        call.macroId = child.id;
        t.actions.add(call);
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory();
        lib.add(s); lib.add(t); lib.add(child);

        String one = ShareCode.encode(MacroCodec.exportBundle(s, lib));
        T.ok(one.startsWith(ShareCode.PREFIX), "starts with the prefix");
        T.ok(one.length() < s.script.length(), "compressed: " + one.length() + " < " + s.script.length());
        T.ok(one.matches("[A-Za-z0-9+/=:]+"), "one word, safe in any chat");
        String msg = ShareCode.message(java.util.Collections.singletonList(s), one);
        String broken = msg.substring(0, msg.indexOf(ShareCode.PREFIX) + 40) + "\n" + msg.substring(msg.indexOf(ShareCode.PREFIX) + 40) + "\n\nmersi!";
        T.ok(ShareCode.contains(broken), "found inside a message");
        List<Macro> back = MacroCodec.importBundle(ShareCode.decode(broken), new java.util.HashSet<String>());
        T.eq(1, back.size(), "one script");
        T.eq(s.script, back.get(0).script, "same code, even split by the chat app");

        List<Macro> roots = new ArrayList<Macro>();
        roots.add(s);
        roots.add(t);
        String pack = ShareCode.encode(MacroCodec.exportPack(roots, lib, null));
        java.util.Set<String> existing = new java.util.HashSet<String>();
        existing.add(t.id);
        back = MacroCodec.importBundle(ShareCode.decode(pack), existing);
        T.eq(3, back.size(), "both, plus the sub-macro they use");
        Macro t2 = null, c2 = null;
        for (Macro m : back) { if (m.name.equals("Ținte boss")) t2 = m; if (m.name.equals("sub")) c2 = m; }
        T.ok(t2 != null && !t2.id.equals(t.id), "never overwrites what you have");
        T.eq(c2.id, t2.actions.get(1).macroId, "the call still points at its sub-macro"); // tree-ok: test
        T.ok(ShareCode.contains("{\"format\": \"x\", \"actions\": []}"), "a pasted file counts too");
    }

    static void testBrokenCodesAreExplained() {
        String code = ShareCode.encode(MacroCodec.exportBundle(new Macro("x"), new MacroLibrary.InMemory()));
        for (String bad : new String[]{"salut", ShareCode.PREFIX, ShareCode.PREFIX + "!!!", code.substring(0, code.length() / 2)}) {
            try {
                ShareCode.decode(bad);
                T.ok(false, "should fail: " + bad);
            } catch (IllegalArgumentException e) {
                T.ok(e.getMessage() != null && e.getMessage().length() > 5, "explained: " + e.getMessage());
            }
        }
        Random r = new Random(5);
        for (int k = 0; k < 300; k++) { // random junk after the prefix never crashes
            StringBuilder b = new StringBuilder(ShareCode.PREFIX);
            int n = r.nextInt(200);
            for (int i = 0; i < n; i++) b.append("ABCDEFabcdef0123456789+/=\n ".charAt(r.nextInt(27)));
            try { ShareCode.decode(b.toString()); } catch (IllegalArgumentException expected) { }
        }
    }

    // ------------------------------------------------------------------ 📖 documentation (2.3)
    static void testEveryFunctionHasAPage() {
        List<String> missing = new ArrayList<String>();
        for (String n : com.freeclick.core.script.ScriptRunner.functionNames()) {
            if (com.freeclick.core.script.Docs.find(n) == null) missing.add(n);
        }
        T.ok(missing.isEmpty(), "functions without a page: " + missing);
        T.eq("tap", com.freeclick.core.script.Docs.find("click").id, "an alias leads to the page");
        T.eq("vector", com.freeclick.core.script.Docs.find("push_back").id, "a method leads to its type");
        T.eq("#define", com.freeclick.core.script.Docs.find("#define").id, "#define");
        T.ok(com.freeclick.core.script.Docs.find("tap").aliases().contains("atinge"), "aliases listed");
        T.ok(!com.freeclick.core.script.Docs.find("tap").signatures().isEmpty(), "ways to call it");
        T.eq("asteaptaAtingere", com.freeclick.core.script.Docs.search("wait_for").get(0).id, "search by any name");
        T.ok(!com.freeclick.core.script.Docs.search("asteapta atingere").isEmpty() || !com.freeclick.core.script.Docs.search("atingere").isEmpty(), "search without diacritics");
    }

    static void testEveryLinkLeadsSomewhere() {
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (com.freeclick.core.script.Docs.Entry e : com.freeclick.core.script.Docs.all()) {
            T.ok(ids.add(e.id), "one page per name: " + e.id);
            T.ok(e.summary.length() > 10, "a summary: " + e.id);
            List<String> links = com.freeclick.core.script.Docs.links(e.body);
            links.addAll(e.see);
            for (String l : links) T.ok(com.freeclick.core.script.Docs.find(l) != null, e.id + " → [[" + l + "]]");
        }
        T.ok(com.freeclick.core.script.Docs.all().size() > 100, "pages: " + com.freeclick.core.script.Docs.all().size());
    }

    static void testEveryExampleIsAScript() {
        for (com.freeclick.core.script.Docs.Entry e : com.freeclick.core.script.Docs.all()) {
            if (e.example.isEmpty()) continue;
            com.freeclick.core.script.ScriptError err = com.freeclick.core.script.ScriptRunner.check(e.example);
            T.ok(err == null, e.id + ": " + (err == null ? "" : err.describe()));
            err = com.freeclick.core.script.ScriptRunner.check("#define p point\n" + e.example);
            T.ok(err == null, e.id + " under #define p point: " + (err == null ? "" : err.describe()));
        }
    }

    // ------------------------------------------------------------------ 🖐 several fingers at once (2.3)
    static void testSyncTapsBecomeOneGesture() {
        List<Action> l = new ArrayList<Action>();
        Action a = Action.tap(100, 200); a.delay = 300; a.duration = 40;
        l.add(a);
        l.add(Action.tap(500, 200));
        l.add(Action.tap(900, 200));
        l.add(Action.swipe(1, 2, 3, 4, 300));
        Action off = Action.tap(7, 7); off.enabled = false;
        l.add(off);
        l.add(Action.tap(10, 10));
        List<Action> s = MacroTransform.syncTaps(l);
        T.eq(4, s.size(), "3 taps → 1 gesture, swipe, the off one, a lone tap");
        Action g = s.get(0);
        T.eq(Action.Type.GESTURE, g.type, "one gesture");
        T.eq(3, g.strokes.size(), "three fingers");
        for (Stroke k : g.strokes) T.eq(0L, k.startTime(), "all down at the same moment");
        T.eq(40L, g.strokes.get(0).endTime(), "each finger as long as its tap");
        T.eq(300L, g.delay, "the pause before the first tap stays");
        T.eq(Action.Type.SWIPE, s.get(1).type, "order kept");
        T.ok(s.get(3).type == Action.Type.TAP && s.get(3).x == 10, "a lone tap stays a tap");
        T.eq(6, l.size(), "the original list is not changed"); // tree-ok: test
        List<Action> many = new ArrayList<Action>();
        for (int i = 0; i < 13; i++) many.add(Action.tap(i, i));
        List<Action> m = MacroTransform.syncTaps(many);
        T.eq(2, m.size(), "10 fingers at most, then the next group");
        T.eq(10, m.get(0).strokes.size(), "ten");
        T.eq(3, m.get(1).strokes.size(), "three");
        Macro mm = new Macro("x");
        mm.settings.syncTaps = true;
        T.ok(MacroCodec.fromJson(MacroCodec.toJson(mm)).settings.syncTaps, "the setting is saved");
        T.ok(!MacroCodec.toJson(new Macro("y")).contains("syncTaps"), "files without it stay as before");
    }

    static void testTapAllFromCode() {
        ScriptTests.Host h = new ScriptTests.Host();
        String out = run(h, "atingeDeodata({point(1, 2), point(3, 4)});\nprint(\"ok\");\n");
        T.eq("ok", out, "ran");
        T.eq(1, h.did.size(), "one gesture: " + h.did);
        T.ok(h.did.get(0).startsWith("GESTURE"), "with fingers: " + h.did.get(0));
        ScriptTests.Rec r = ScriptTests.run("vector<point> v;\nfor (int i = 0; i < 11; i++) v.push_back(point(i, i));\natingeDeodata(v);", new ScriptTests.Host());
        T.ok(r.error != null && r.error.getMessage().contains("10"), "at most 10: " + (r.error == null ? "" : r.error.describe()));
    }
}
