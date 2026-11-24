package com.freeclick.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** FreeClick 1.8: numbers on screens, expressions, variables, console and read steps, the text reader. */
public final class V18Tests {

    // ------------------------------------------------------------------ numbers
    static void testNumbersAsGamesWriteThem() {
        String[][] cases = {
                {"12345", "12345"}, {"12,345", "12345"}, {"1.234.567", "1234567"}, {"1 234 567", "1234567"},
                {"12,5", "12.5"}, {"12.5", "12.5"}, {"1,234.5", "1234.5"}, {"1.234,5", "1234.5"}, {"0,75", "0.75"},
                {"1.5k", "1500"}, {"1,5K", "1500"}, {"2.3M", "2300000"}, {"4B", "4000000000"}, {"7 k", "7000"},
                {"45%", "45"}, {"-7", "-7"}, {"(-12)", "-12"}, {"Gold: 1.250", "1250"}, {"Level-5", "5"},
                {"3/10", "3"}, {"12:30", "12"}, {"5 Kills", "5"}, {"x 1 234 y", "1234"}, {"12345 678", "12345"},
                {"1.2.3", "1"}, {"1,23", "1.23"}, {"1234,567", "1234.567"}, {"−8", "-8"}, {"1'234'567", "1234567"},
        };
        for (String[] c : cases) {
            Double v = Numbers.parse(c[0]);
            T.ok(v != null, "a number in \"" + c[0] + "\"");
            T.eq(Double.valueOf(c[1]), v, "\"" + c[0] + "\"");
        }
        T.eq(null, Numbers.parse("fără cifre"), "no digits");
        T.eq(null, Numbers.parse(""), "empty");
        T.ok(Numbers.isWholeNumber(" 1,5k "), "whole");
        T.ok(!Numbers.isWholeNumber("Level 12"), "not whole");
    }

    // ------------------------------------------------------------------ expressions
    static final class MapEnv implements Expr.Env {
        final Map<String, Object> vars = new HashMap<String, Object>();
        final java.util.List<String> warnings = new java.util.ArrayList<String>();
        final Random r = new Random(1);
        public Object get(String name) { return vars.get(name); }
        public double random() { return r.nextDouble(); }
        public void warn(String m) { warnings.add(m); }
    }

    static Object ev(String e, MapEnv env) { return Expr.eval(e, env); }

    static void testExpressions() {
        MapEnv env = new MapEnv();
        env.vars.put("aur", 1250.0);
        env.vars.put("nume", "Ana");
        T.eq(1251.0, ev("aur + 1", env), "variable + 1");
        T.eq(1.0, ev("n + 1", env), "unset variable is 0 (counters work from the start)");
        T.eq(14.0, ev("2 + 3 * 4", env), "precedence");
        T.eq(20.0, ev("(2 + 3) * 4", env), "parentheses");
        T.eq(1.0, ev("7 % 3", env), "modulo");
        T.eq(-5.0, ev("-(2 + 3)", env), "unary minus");
        T.eq(1.0, ev("aur >= 1000 && aur < 2000", env), "comparisons and &&");
        T.eq(1.0, ev("aur = 1250", env), "single = compares");
        T.eq(0.0, ev("!aur", env), "not");
        T.eq(1.0, ev("aur < 10 || nume == \"ana\"", env), "text equality ignores case");
        T.eq("Ana are 1250", ev("nume + \" are \" + aur", env), "text + number joins");
        T.eq(2500.0, ev("{aur} * 2", env), "{name} accepted inside expressions");
        T.eq(3.0, ev("max(1, 3, 2)", env), "max");
        T.eq(2.5, ev("round(2.4567, 1)", env), "round with decimals");
        T.eq(3.0, ev("len(nume)", env), "len");
        T.eq(1.0, ev("contine(nume, \"n\")", env), "contains");
        T.eq(1.0, ev("aproape(#34A853, #30A050, 10)", env), "colours close");
        T.eq(0.0, ev("aproape(#34A853, #FF0000)", env), "colours far");
        T.eq(1500.0, ev("num(\"1,5k\")", env), "num parses screen numbers");
        for (int i = 0; i < 200; i++) {
            double v = (Double) ev("random(1, 6)", env);
            T.ok(v >= 1 && v <= 6 && v == Math.rint(v), "dice " + v);
        }
        T.eq(0.0, ev("5 / 0", env), "division by 0 gives 0");
        T.ok(env.warnings.size() == 1 && env.warnings.get(0).contains("0"), "... with a warning");
    }

    static void testExpressionErrorsAreClear() {
        String[] bad = {"", "1 +", "(1 + 2", "max(", "\"abc", "#12", "aur ? 2", "random()", "len(1, 2)", "max"};
        for (String b : bad) {
            String err = Expr.check(b);
            T.ok(err != null && err.length() > 5, "error for \"" + b + "\": " + err);
        }
        T.eq(null, Expr.check("aur + 1"), "valid");
        T.ok(Expr.isValidName("aur") && Expr.isValidName("vieți_2") && !Expr.isValidName("2x") && !Expr.isValidName("a b")
                && !Expr.isValidName("max") && !Expr.isValidName(""), "variable names");
    }

    static void testTemplates() {
        MapEnv env = new MapEnv();
        env.vars.put("aur", 12.5);
        T.eq("Aur: 12.5, dublu 25", Expr.expand("Aur: {aur}, dublu {aur * 2}", env), "template");
        T.eq("{literal} 0", Expr.expand("{{literal}} {n}", env), "escaped braces and unset variable");
        T.eq("a {1 +} b", Expr.expand("a {1 +} b", env), "a broken part stays as written");
        T.eq(1, env.warnings.size(), "and is reported");
        T.eq(null, Expr.checkTemplate("x {aur} y"), "valid template");
        T.ok(Expr.checkTemplate("x {aur y") != null, "unclosed");
        T.eq("0.3333", Expr.format(1 / 3.0), "4 decimals at most");
        T.eq("1000000", Expr.format(1e6), "whole numbers without decimals");
    }

    // ------------------------------------------------------------------ engine
    static String last(V16Tests.Rec r) { return r.finished.isEmpty() ? null : r.finished.get(r.finished.size() - 1); }

    static Macro macro() {
        Macro m = new Macro("v");
        m.screenWidth = 1080; m.screenHeight = 2400;
        m.settings.loops = 1;
        return m;
    }

    static PlaybackEngine run(FakeHost h, Macro m, V16Tests.Rec r) {
        PlaybackEngine e = new PlaybackEngine(h, r);
        e.setNotices(r);
        h.engine = e;
        e.start(m, null);
        h.runAll();
        return e;
    }

    static void testCounterAndPrint() {
        FakeHost h = new FakeHost();
        Macro m = macro();
        m.settings.loops = 3;
        m.actions.add(Action.setVar("n", "n + 1"));
        m.actions.add(Action.print("tura {tura}: n = {n}"));
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = run(h, m, r);
        T.eq("[tura 1: n = 1, tura 2: n = 2, tura 3: n = 3]", h.printed.toString(), "printed");
        T.eq(3.0, e.variables().get("n"), "variables kept after the end");
        T.eq(PlaybackEngine.REASON_DONE, last(r), "done");
        // a new run starts with no variables
        e.start(m, null);
        h.runAll();
        T.eq(3.0, e.variables().get("n"), "counted again from 0");
    }

    static void testBadStepsWarnInsteadOfStopping() {
        FakeHost h = new FakeHost();
        Macro m = macro();
        m.actions.add(Action.setVar("tura", "5"));
        m.actions.add(Action.setVar("2x", "5"));
        m.actions.add(Action.setVar("a", "1 +"));
        m.actions.add(Action.setVar("b", "1 / 0"));
        m.actions.add(Action.print("gata {b}"));
        V16Tests.Rec r = new V16Tests.Rec();
        run(h, m, r);
        T.eq(PlaybackEngine.REASON_DONE, last(r), "ran to the end");
        T.eq("gata 0", h.printed.get(h.printed.size() - 1), "last line");
        int warns = 0;
        for (int k : h.printedKinds) if (k == PlaybackEngine.PRINT_WARN) warns++;
        T.eq(4, warns, "four warnings");
    }

    static void testReadNumberConfirmedOnTwoFrames() {
        FakeHost h = new FakeHost();
        h.frameIntervalMs = 350;
        // the number is still counting up for a moment, then settles
        h.textTimeline.put(0L, "Gold 1,190");
        h.textTimeline.put(1400L, "Gold 1,240");
        h.textTimeline.put(1700L, "Gold 1,250");
        Macro m = macro();
        m.actions.add(Action.read(100, 200, 400, 260, Action.READ_NUMBER, "aur"));
        m.actions.add(Action.print("aur={aur}"));
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = run(h, m, r);
        T.eq(1250.0, e.variables().get("aur"), "waits until two frames in a row agree (the count settled)");
        T.ok(h.readRequests.size() >= 2, "read at least twice");
        PlaybackEngine.ReadRequest q = h.readRequests.get(0);
        T.eq(100, q.x1, "zone x1"); T.eq(200, q.y1, "zone y1"); T.eq(400, q.x2, "zone x2"); T.eq(260, q.y2, "zone y2");
        T.ok(h.printed.contains("📖 aur = 1250") && h.printed.contains("aur=1250"), "reported " + h.printed);
        // a value that keeps changing: the last one is used after a few frames
        h = new FakeHost();
        h.frameIntervalMs = 350;
        for (long t = 0; t < 10000; t += 350) h.textTimeline.put(t, String.valueOf(t));
        e = run(h, m, new V16Tests.Rec());
        T.ok(e.variables().get("aur") != null, "still read something");
        T.eq((long) PlaybackEngine.READ_MAX_TRIES, h.readRequests.size(), "gave up waiting for a steady value");
    }

    static void testReadFailureKeepsTheOldValue() {
        FakeHost h = new FakeHost();
        h.textTimeline.put(0L, "nimic aici");
        Macro m = macro();
        m.actions.add(Action.setVar("aur", "77"));
        m.actions.add(Action.read(0, 0, 50, 50, Action.READ_NUMBER, "aur"));
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = run(h, m, r);
        T.eq(77.0, e.variables().get("aur"), "unchanged");
        T.ok(h.printed.get(h.printed.size() - 1).contains("„nimic aici”"), "says what it saw: " + h.printed);
        T.eq(PlaybackEngine.REASON_DONE, last(r), "carried on");
    }

    static void testReadTextAndColour() {
        FakeHost h = new FakeHost();
        h.textTimeline.put(0L, "  Ana  ");
        h.colorTimeline.put(0L, 0x34A853);
        Macro m = macro();
        m.actions.add(Action.read(0, 0, 50, 50, Action.READ_TEXT, "nume"));
        m.actions.add(Action.readColor(10, 10, "c"));
        m.actions.add(Action.print("{nume} {c} {aproape(c, #30A050, 10)}"));
        PlaybackEngine e = run(h, m, new V16Tests.Rec());
        T.eq("Ana", e.variables().get("nume"), "text trimmed");
        T.eq("#34A853", e.variables().get("c"), "colour as #RRGGBB");
        T.eq("Ana #34A853 1", h.printed.get(h.printed.size() - 1), "usable in expressions");
    }

    static void testReadStepsAndPauses() {
        FakeHost h = new FakeHost();
        h.readLatencyMs = 500;
        h.textTimeline.put(0L, "42");
        Macro m = macro();
        m.actions.add(Action.read(0, 0, 50, 50, Action.READ_NUMBER, "x"));
        V16Tests.Rec r = new V16Tests.Rec();
        PlaybackEngine e = new PlaybackEngine(h, r);
        h.engine = e;
        e.start(m, null);
        h.runUntil(h.now + 200);
        e.pause();
        h.runUntil(h.now + 2000); // the answer arrives while paused: ignored
        T.eq(null, e.variables().get("x"), "nothing stored while paused");
        e.resume();
        h.runAll();
        T.eq(42.0, e.variables().get("x"), "read again after resume");
        T.eq(0, h.pendingTimers(), "nothing left");
        // skip (preview's ⏭) abandons the read
        h = new FakeHost();
        h.readLatencyMs = 400;
        h.textTimeline.put(0L, "1");
        h.textTimeline.put(300L, "2");
        h.textTimeline.put(800L, "3");
        h.frameIntervalMs = 350;
        e = new PlaybackEngine(h, new V16Tests.Rec());
        h.engine = e;
        e.start(m, null);
        h.runUntil(h.now + 800);
        e.skipWait();
        h.runAll();
        T.eq(PlaybackEngine.State.FINISHED, e.state(), "finished");
    }

    static void testReadWithoutCaptureStops() {
        FakeHost h = new FakeHost();
        h.readNoCapture = true;
        Macro m = macro();
        m.actions.add(Action.read(0, 0, 50, 50, Action.READ_NUMBER, "x"));
        V16Tests.Rec r = new V16Tests.Rec();
        run(h, m, r);
        T.eq(PlaybackEngine.REASON_NO_CAPTURE, last(r), "no capture");
        h = new FakeHost();
        h.protectedFrom = 0;
        r = new V16Tests.Rec();
        run(h, m, r);
        T.eq(PlaybackEngine.REASON_PROTECTED, last(r), "protected screen");
    }

    static void testPreviewDoesNotRead() {
        FakeHost h = new FakeHost();
        Macro m = macro();
        m.actions.add(Action.read(0, 0, 50, 50, Action.READ_NUMBER, "x"));
        m.actions.add(Action.readColor(1, 1, "c"));
        PlaybackEngine e = new PlaybackEngine(h, new V16Tests.Rec());
        e.setPreview(true, true);
        e.start(m, null);
        h.runAll();
        T.eq(0, h.readRequests.size() + h.samples.size(), "no screen reads in preview");
        T.ok(h.printed.size() == 2 && h.printed.get(0).contains("previzualizare"), "explained: " + h.printed);
    }

    static void testReadZoneIsScaledToTheScreen() {
        FakeHost h = new FakeHost();
        h.width = 540; h.height = 1200; // half the recording screen
        h.textTimeline.put(0L, "5");
        Macro m = macro();
        m.settings.scaleToScreen = true;
        m.actions.add(Action.read(100, 200, 400, 260, Action.READ_NUMBER, "x"));
        run(h, m, new V16Tests.Rec());
        PlaybackEngine.ReadRequest q = h.readRequests.get(0);
        T.eq(50, q.x1, "x1 scaled"); T.eq(100, q.y1, "y1"); T.eq(200, q.x2, "x2"); T.eq(130, q.y2, "y2");
    }

    // ------------------------------------------------------------------ file format
    static void testNewStepsSurviveTheFile() {
        Macro m = macro();
        m.actions.add(Action.print("Aur {aur}\n„ă”"));
        m.actions.add(Action.setVar("aur", "aur + 1"));
        m.actions.add(Action.read(10, 20, 30, 40, Action.READ_TEXT, "nume"));
        m.actions.add(Action.readColor(5, 6, "c"));
        Macro back = MacroCodec.fromJson(MacroCodec.toJson(m));
        T.eq(4, back.actions.size(), "steps");
        T.eq("Aur {aur}\n„ă”", back.actions.get(0).text, "print text");
        T.eq("aur", back.actions.get(1).varName, "var");
        T.eq("aur + 1", back.actions.get(1).text, "expression");
        Action rd = back.actions.get(2);
        T.eq(Action.READ_TEXT, rd.readMode, "mode");
        T.ok(rd.x == 10 && rd.y == 20 && rd.x2 == 30 && rd.y2 == 40, "zone");
        T.eq(Action.READ_COLOR, back.actions.get(3).readMode, "colour mode");
        T.eq(MacroCodec.toJson(back), MacroCodec.toJson(MacroCodec.fromJson(MacroCodec.toJson(back))), "stable");
        T.ok(MacroCodec.toJson(m).contains("\"version\": " + MacroCodec.VERSION), "current format");
        // an older FreeClick refuses it instead of losing the steps (version gate tested in V16Tests)
        T.ok(ActionFormatter.describe(rd, null).contains("nume"), "described: " + ActionFormatter.describe(rd, null));
    }

    // ------------------------------------------------------------------ text reader
    static void testReaderOnPhoneFontsAndOthers() {
        String[] fams = {"Roboto", "Open Sans", "Inter", "Fira Sans", "FreeSans", "DejaVu Sans"};
        int[] sizes = {16, 18, 22, 28, 36, 48};
        int total = 0, ok = 0;
        StringBuilder rep = new StringBuilder();
        for (String f : fams) {
            OcrBench.Stats s = OcrBench.numbers(f, java.awt.Font.PLAIN, sizes, 30, f.hashCode());
            total += s.n; ok += s.exact;
            rep.append(f).append(' ').append(Math.round(s.rate() * 100)).append("% ");
            if (f.equals("Roboto")) T.ok(s.rate() >= 0.95, "the phone's own font: " + s.rate());
            else T.ok(s.rate() >= 0.8, f + ": " + s.rate());
        }
        System.out.println("    numbers read exactly: " + rep);
        T.ok(ok >= 0.92 * total, "numbers overall " + ok + "/" + total);
        OcrBench.Stats w = OcrBench.words("Roboto", java.awt.Font.PLAIN, new int[]{18, 22, 28, 36}, 30, 7);
        T.ok(w.charAcc() >= 0.95, "words in the phone's font, characters right: " + w.charAcc());
        OcrBench.Stats o = OcrBench.words("Open Sans", java.awt.Font.PLAIN, new int[]{18, 22, 28, 36}, 30, 8);
        T.ok(o.charAcc() >= 0.93, "words in another font: " + o.charAcc());
        System.out.println("    words, characters right: Roboto " + Math.round(w.charAcc() * 100) + "%, Open Sans " + Math.round(o.charAcc() * 100) + "%");
    }

    static void testTeachingAnUnusualFont() {
        FontBook book = AwtText.referenceBook();
        FontBook saved = AwtText.cachedBook;
        AwtText.cachedBook = book;
        try {
            int[] sizes = {22, 28, 36};
            OcrBench.Stats before = OcrBench.numbers("Anton", 0, sizes, 40, 21);
            for (String lesson : new String[]{"0123456789", "1.5k 2,3M 45% -7/8 9B"}) {
                java.awt.Font f = AwtText.font("Anton", 0, 40);
                int w = 40 * (lesson.length() + 4), hh = 120;
                AwtText.Scene sc = AwtText.scene(lesson, f, 0xFFFFFF, 0x1E5AA8, 0x1E5AA8, w, hh, 40, 80, 0);
                TextReader.Result r = TextReader.read(sc.argb, w, hh, sc.x0 - 4, sc.y0 - 4, sc.x1 + 4, sc.y1 + 4, TextReader.MODE_TEXT, book);
                T.ok(book.learn(r, lesson) > 0, "lesson learned: " + lesson + " (read " + r.text + ")");
            }
            OcrBench.Stats after = OcrBench.numbers("Anton", 0, sizes, 40, 21);
            System.out.println("    Anton (a game-like font): " + Math.round(before.rate() * 100) + "% → " + Math.round(after.rate() * 100) + "% after two lessons");
            T.ok(after.rate() >= before.rate() + 0.15, "teaching helps: " + before.rate() + " -> " + after.rate());
            // the taught letters survive the file (and nothing else is saved)
            FontBook copy = new FontBook();
            copy.loadLearned(book.toJson());
            T.eq(book.learnedCount(), copy.learnedCount(), "saved and loaded");
            T.eq(0, copy.referenceCount(), "references are never saved");
            T.eq(book.toJson(), copy.toJson(), "same file");
            T.ok(copy.forget('5') > 0 && !copy.learnedChars().containsKey('5'), "forget one character");
        } finally {
            AwtText.cachedBook = saved;
        }
    }

    static void testTeachingRefusesWhatDoesNotFit() {
        FontBook book = AwtText.book();
        java.awt.Font f = AwtText.font("Roboto", 0, 32);
        AwtText.Scene sc = AwtText.scene("123", f, 0x000000, 0xFFFFFF, 0xFFFFFF, 200, 100, 20, 60, 0);
        TextReader.Result r = TextReader.read(sc.argb, 200, 100, sc.x0 - 3, sc.y0 - 3, sc.x1 + 3, sc.y1 + 3, TextReader.MODE_NUMBER, book);
        T.eq("123", r.text, "read");
        FontBook fresh = new FontBook();
        T.ok(fresh.learn(r, "12345") < 0, "more characters than letters");
        T.ok(fresh.learn(r, "1.3") < 0, "a dot is not a digit-sized letter");
        T.eq(0, fresh.learnedCount(), "nothing learned");
    }

    static void testReaderRefusesUselessZones() {
        FontBook book = AwtText.book();
        int[] px = new int[100 * 50];
        java.util.Arrays.fill(px, 0xFF336699);
        TextReader.Result r = TextReader.read(px, 100, 50, 0, 0, 100, 50, TextReader.MODE_NUMBER, book);
        T.ok(r.problem != null && r.text.isEmpty() && r.number == null, "one colour: " + r.problem);
        r = TextReader.read(px, 100, 50, 10, 10, 11, 11, TextReader.MODE_NUMBER, book);
        T.ok(r.problem != null, "too small");
        r = TextReader.read(px, 100, 50, -50, -50, 500, 500, TextReader.MODE_NUMBER, book);
        T.ok(r.problem != null, "outside the frame is clipped, not a crash");
        // a frame line crossing the zone is not read as "-"
        java.awt.Font f = AwtText.font("Roboto", 0, 28);
        AwtText.Scene sc = AwtText.scene("250", f, 0xFFFFFF, 0x1E5AA8, 0x1E5AA8, 200, 90, 40, 60, 0);
        for (int x = 0; x < 200; x++) { sc.argb[20 * 200 + x] = 0xFFFFFFFF; sc.argb[21 * 200 + x] = 0xFFFFFFFF; }
        r = TextReader.read(sc.argb, 200, 90, 10, 15, 190, 75, TextReader.MODE_NUMBER, book);
        T.eq("250", r.text, "frame line ignored");
        T.eq(250.0, r.number, "number");
        T.eq(0, new FontBook().referenceCount(), "an empty book");
        T.ok(TextReader.read(sc.argb, 200, 90, 10, 15, 190, 75, TextReader.MODE_NUMBER, new FontBook()).problem != null, "no letters to compare");
    }

    static void testReaderOnRandomJunkNeverCrashes() {
        FontBook book = AwtText.book();
        Random r = new Random(5);
        for (int i = 0; i < 60; i++) {
            int w = 5 + r.nextInt(300), h = 5 + r.nextInt(120);
            int[] px = new int[w * h];
            int mode = r.nextInt(3);
            for (int k = 0; k < px.length; k++) px[k] = mode == 0 ? r.nextInt() : mode == 1 ? (r.nextInt(8) == 0 ? 0xFFFFFFFF : 0xFF000000) : 0xFF000000 | (k * 7919);
            TextReader.Result res = TextReader.read(px, w, h, r.nextInt(w), r.nextInt(h), r.nextInt(w + 20) - 10, r.nextInt(h + 20) - 10, r.nextInt(2), book);
            T.ok(res.text != null && res.text.length() < 5000, "bounded output");
        }
    }
}
