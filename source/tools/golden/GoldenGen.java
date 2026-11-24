package com.freeclick.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Run ONCE against FreeClick 1.5 to freeze its file format and playback behaviour. */
public final class GoldenGen {

    static Map<String, Object> caseOf(Macro m, Macro... lib) {
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("macro", Json.parse(MacroCodec.toJson(m)));
        List<Object> l = new ArrayList<Object>();
        for (Macro x : lib) l.add(Json.parse(MacroCodec.toJson(x)));
        c.put("library", l);
        return c;
    }

    static Macro base(String name, String kind) {
        Macro m = new Macro(name);
        m.id = "g_" + name.replace(' ', '_');
        m.kind = kind;
        m.screenWidth = 1080; m.screenHeight = 2400;
        return m;
    }

    static Stroke stroke(float x0, float y0, long t0, float x1, float y1, long t1, int steps) {
        Stroke s = new Stroke();
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            s.add(x0 + (x1 - x0) * f + (float) Math.sin(f * 3) * 20, y0 + (y1 - y0) * f, t0 + Math.round((t1 - t0) * f));
        }
        return s;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        Map<String, Map<String, Object>> cases = new LinkedHashMap<String, Map<String, Object>>();

        // 1. Manual targets: loops, jitter (seeded), labels, disabled target.
        Macro manual = base("Manual taps", Macro.KIND_MANUAL);
        manual.settings.loops = 3; manual.settings.loopDelay = 400;
        manual.settings.jitterPx = 6; manual.settings.jitterTimePct = 20;
        manual.settings.tapInterval = 250; manual.settings.tapDuration = 60;
        for (int i = 0; i < 4; i++) {
            Action t = Action.tap(200 + i * 150, 500 + i * 300); t.delay = 250; t.duration = 60;
            t.label = "T" + i; manual.actions.add(t);
        }
        manual.actions.get(2).enabled = false;
        Action sw = Action.swipe(540, 1800, 540, 600, 350); sw.delay = 300; manual.actions.add(sw);
        Action lp = Action.longPress(900, 2000, 900); lp.delay = 200; lp.repeat = 2; lp.interval = 150; manual.actions.add(lp);
        Map<String, Object> c1 = caseOf(manual); c1.put("seed", 42L); cases.put("manual_jitter", c1);

        // 2. Recording: every gesture type, waits, a global action, speed 1.5, phone latency.
        Macro rec = base("Recording", Macro.KIND_RECORD);
        rec.settings.speed = 1.5; rec.settings.loops = 1;
        Action a1 = Action.tap(100, 100); a1.delay = 1730; rec.actions.add(a1);
        Action a2 = Action.doubleTap(500, 900); a2.delay = 420; rec.actions.add(a2);
        List<Stroke> two = new ArrayList<Stroke>();
        two.add(stroke(300, 1500, 0, 300, 800, 600, 12));
        two.add(stroke(800, 1500, 40, 800, 900, 640, 12));
        Action g = Action.gesture(two); g.delay = 980; rec.actions.add(g);
        Action w = Action.waitFor(1200); w.delay = 0; rec.actions.add(w);
        Action back = Action.global(GlobalActions.BACK); back.delay = 333; rec.actions.add(back);
        Action a3 = Action.longPress(640, 1300, 1100); a3.delay = 75; rec.actions.add(a3);
        Action a4 = Action.swipe(100, 2000, 1000, 2000, 500); a4.delay = 3; rec.actions.add(a4);
        Map<String, Object> c2 = caseOf(rec); c2.put("latency", 35L); cases.put("recording_all_types", c2);

        // 3. Same recording with a live speed change and a pause/resume in the middle.
        Map<String, Object> c3 = caseOf(rec); c3.put("latency", 20L);
        c3.put("speedAt", 2500L); c3.put("newSpeed", 0.5);
        c3.put("pauseAt", 4100L); c3.put("resumeAt", 9000L);
        cases.put("recording_speed_pause", c3);

        // 4. Combination: sub-macros with repeats, a missing reference, nested combo.
        Macro sub1 = base("Sub one", Macro.KIND_RECORD);
        Action s1 = Action.tap(300, 300); s1.delay = 500; sub1.actions.add(s1);
        Action s2 = Action.swipe(300, 300, 700, 700, 200); s2.delay = 100; sub1.actions.add(s2);
        Macro sub2 = base("Sub two", Macro.KIND_MANUAL);
        Action s3 = Action.tap(900, 900); s3.delay = 50; sub2.actions.add(s3);
        Macro inner = base("Inner combo", Macro.KIND_COMBO);
        Action i1 = Action.macro(sub2.id, 3); i1.delay = 70; inner.actions.add(i1);
        Macro combo = base("Combo", Macro.KIND_COMBO);
        combo.settings.loops = 2; combo.settings.loopDelay = 1000;
        Action k1 = Action.macro(sub1.id, 2); k1.delay = 300; combo.actions.add(k1);
        Action k2 = Action.macro("does_not_exist", 1); combo.actions.add(k2);
        Action k3 = Action.waitFor(500); combo.actions.add(k3);
        Action k4 = Action.macro(inner.id, 1); k4.delay = 10; combo.actions.add(k4);
        cases.put("combo_nested", caseOf(combo, sub1, sub2, inner));

        // 5. Colour conditions: appears later, invert, timeout continue, timeout stop.
        Macro col = base("Colors", Macro.KIND_MANUAL);
        Action wc1 = Action.waitColor(540, 1200, 0x00FF00, 0); col.actions.add(wc1);
        Action ct = Action.tap(540, 1200); ct.delay = 200; col.actions.add(ct);
        Action wc2 = Action.waitColor(540, 1200, 0x00FF00, 5000); wc2.invert = true; wc2.tolerance = 10; col.actions.add(wc2);
        Action wc3 = Action.waitColor(10, 10, 0xFF0000, 1500); col.actions.add(wc3);
        Action ct2 = Action.tap(100, 100); ct2.delay = 100; col.actions.add(ct2);
        Action wc4 = Action.waitColor(10, 10, 0xFF0000, 1000); wc4.stopOnTimeout = true; col.actions.add(wc4);
        Action never = Action.tap(1, 1); col.actions.add(never);
        Map<String, Object> c5 = caseOf(col);
        Map<String, Object> colors = new LinkedHashMap<String, Object>();
        colors.put("0", 0x000000L); colors.put("2345", 0x00F800L); colors.put("6000", 0x123456L);
        c5.put("colors", colors);
        cases.put("colors", c5);

        // 6. No screen capture for a colour condition.
        Macro nocap = base("No capture", Macro.KIND_MANUAL);
        nocap.actions.add(Action.waitColor(5, 5, 0xFFFFFF, 0));
        cases.put("no_capture", caseOf(nocap));

        // 7. Scaling from a smaller recording screen + time limit with infinite loops.
        Macro sc = base("Scaled", Macro.KIND_MANUAL);
        sc.screenWidth = 720; sc.screenHeight = 1600;
        sc.settings.loops = 0; sc.settings.stopAfterMs = 4321;
        Action t1 = Action.tap(360, 800); t1.delay = 300; sc.actions.add(t1);
        Action t2 = Action.swipe(0, 0, 719, 1599, 250); t2.delay = 150; sc.actions.add(t2);
        cases.put("scaled_time_limit", caseOf(sc));

        // 8. Infinite loop stopped by the user; gestures failing (cancelled by touches).
        Macro inf = base("Forever", Macro.KIND_MANUAL);
        inf.settings.loops = 0; inf.settings.loopDelay = 0;
        Action f1 = Action.tap(400, 400); f1.delay = 100; inf.actions.add(f1);
        Map<String, Object> c8 = caseOf(inf); c8.put("stopAt", 2000L); c8.put("latency", 12L);
        cases.put("forever_stopped", c8);

        // 9. Everything disabled -> empty; cycle -> refused.
        Macro empty = base("Empty", Macro.KIND_MANUAL);
        Action d = Action.tap(1, 1); d.enabled = false; empty.actions.add(d);
        cases.put("empty", caseOf(empty));
        Macro cyc = base("Cycle", Macro.KIND_COMBO);
        cyc.actions.add(Action.macro(cyc.id, 1));
        cases.put("cycle", caseOf(cyc, cyc));

        for (Map.Entry<String, Map<String, Object>> e : cases.entrySet()) {
            Map<String, Object> c = e.getValue();
            List<String> trace = GoldenRunner.run(c);
            c.put("trace", new ArrayList<Object>(trace));
            Writer w2 = new OutputStreamWriter(new FileOutputStream(new File(dir, e.getKey() + ".json")), "UTF-8");
            w2.write(Json.stringify(c, true));
            w2.close();
            System.out.println(e.getKey() + ": " + trace.size() + " trace lines, last: " + trace.get(trace.size() - 2));
        }
    }
}
