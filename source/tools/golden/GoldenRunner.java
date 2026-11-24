package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Replays a golden case (macro + library + host conditions) on the virtual clock and
 * returns a textual trace. The same file is compiled against FreeClick 1.5 (to produce
 * the expected traces) and against the current code (test/.../GoldenTests).
 */
public final class GoldenRunner {

    public static List<String> run(Map<String, Object> c) {
        final List<String> out = new ArrayList<String>();
        Macro m = MacroCodec.fromJson(Json.stringify(Json.getObject(c, "macro")));
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory();
        List<Object> libs = Json.getArray(c, "library");
        if (libs != null) for (Object o : libs) {
            @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) o;
            lib.add(MacroCodec.fromJson(Json.stringify(mm)));
        }
        final FakeHost h = new FakeHost();
        h.width = Json.getInt(c, "screenW", 1080);
        h.height = Json.getInt(c, "screenH", 2400);
        h.callbackLatencyMs = Json.getLong(c, "latency", 0);
        Map<String, Object> colors = Json.getObject(c, "colors");
        if (colors != null) for (Map.Entry<String, Object> e : colors.entrySet()) {
            h.colorTimeline.put(1000 + Long.parseLong(e.getKey()), ((Number) e.getValue()).intValue());
        }
        final PlaybackEngine[] eng = new PlaybackEngine[1];
        PlaybackEngine.Listener l = new PlaybackEngine.Listener() {
            public void onStateChanged(PlaybackEngine.State s) { out.add((h.now - 1000) + " state " + s); }
            public void onProgress(int loop, int total, int step, int steps, String name, Action a) {
                out.add((h.now - 1000) + " progress " + loop + "/" + total + " " + step + "/" + steps + " " + name);
            }
            public void onFinished(String reason) {
                PlaybackEngine e = eng[0];
                out.add((h.now - 1000) + " end " + reason + " steps=" + e.executedSteps() + " failed=" + e.failedGestures()
                        + " met=" + e.conditionsMet() + " timedOut=" + e.conditionsTimedOut());
            }
        };
        PlaybackEngine e = new PlaybackEngine(h, l);
        eng[0] = e;
        h.engine = e;
        e.setRandom(new Random(Json.getLong(c, "seed", 1)));
        e.start(m, lib);
        long pauseAt = Json.getLong(c, "pauseAt", -1), resumeAt = Json.getLong(c, "resumeAt", -1);
        long speedAt = Json.getLong(c, "speedAt", -1);
        long stopAt = Json.getLong(c, "stopAt", -1);
        if (speedAt >= 0) { h.runUntil(1000 + speedAt); e.setSpeed(Json.getDouble(c, "newSpeed", 1)); }
        if (pauseAt >= 0) { h.runUntil(1000 + pauseAt); e.pause(); h.runUntil(1000 + resumeAt); e.resume(); }
        if (stopAt >= 0) { h.runUntil(1000 + stopAt); e.stop(); }
        h.runAll(200000);
        List<String> trace = new ArrayList<String>();
        int gi = 0;
        for (FakeHost.Event ev : h.events) {
            StringBuilder sb = new StringBuilder();
            sb.append(ev.time - 1000).append(' ').append(ev.kind);
            if (ev.plan != null) {
                for (GesturePlan.PlannedStroke s : ev.plan.strokes) {
                    sb.append(" [").append(s.startTime).append('/').append(s.duration).append(':');
                    for (float[] p : s.points) sb.append(Math.round(p[0] * 10) / 10.0).append(',').append(Math.round(p[1] * 10) / 10.0).append(';');
                    sb.append(']');
                }
            } else {
                sb.append(' ').append(ev.global);
            }
            trace.add(sb.toString());
            gi++;
        }
        trace.addAll(out);
        trace.add("violations=" + h.violations);
        return trace;
    }
}
