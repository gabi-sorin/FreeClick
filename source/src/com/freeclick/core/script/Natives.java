package com.freeclick.core.script;

import com.freeclick.core.Action;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.Stroke;
import com.freeclick.core.TouchTracker;
import com.freeclick.core.script.Ast.Call;
import com.freeclick.core.script.Interp.Obj;
import com.freeclick.core.script.Interp.Vec;

import java.util.ArrayList;
import java.util.List;

/** The app's functions (tap, wait, pixel, citeste, gaseste, ruleaza…), run on the script thread. */
final class Natives {
    private Natives() {}

    private static long L(Object o) { return (Long) o; }
    private static double D(Object o) { return (Double) o; }
    private static int X(Object p) { return (int) L(((Obj) p).f[0]); }
    private static int Y(Object p) { return (int) L(((Obj) p).f[1]); }

    static Object call(Interp in, Call c, Object[] fr) {
        int n = c.args.size();
        Object[] a = new Object[n];
        for (int i = 0; i < n; i++) a[i] = in.eval(c.args.get(i), fr);
        ScriptRunner r = in.runner;
        switch (c.builtin) {
            // ---------------------------------------------------------- touches
            case Builtins.TAP: {
                Action t = Action.tap(X(a[0]), Y(a[0]));
                t.duration = n > 1 ? ms(in, c, a[1]) : in.clickMs();
                touch(r, c, t, n > 2 ? pause(a[2]) : in.intervalMs());
                return null;
            }
            case Builtins.TAP_XY: {
                Action t = Action.tap(L(a[0]), L(a[1]));
                t.duration = n > 2 ? ms(in, c, a[2]) : in.clickMs();
                touch(r, c, t, n > 3 ? pause(a[3]) : in.intervalMs());
                return null;
            }
            case Builtins.LONG:
                touch(r, c, Action.longPress(X(a[0]), Y(a[0]), n > 1 ? ms(in, c, a[1]) : Action.DEFAULT_LONG_PRESS_MS), n > 2 ? pause(a[2]) : in.intervalMs());
                return null;
            case Builtins.LONG_XY:
                touch(r, c, Action.longPress(L(a[0]), L(a[1]), n > 2 ? ms(in, c, a[2]) : Action.DEFAULT_LONG_PRESS_MS), n > 3 ? pause(a[3]) : in.intervalMs());
                return null;
            case Builtins.DOUBLE_TAP: touch(r, c, Action.doubleTap(X(a[0]), Y(a[0])), n > 1 ? pause(a[1]) : in.intervalMs()); return null;
            case Builtins.DOUBLE_TAP_XY: touch(r, c, Action.doubleTap(L(a[0]), L(a[1])), n > 2 ? pause(a[2]) : in.intervalMs()); return null;
            case Builtins.SWIPE:
                touch(r, c, Action.swipe(X(a[0]), Y(a[0]), X(a[1]), Y(a[1]), n > 2 ? ms(in, c, a[2]) : in.swipeMs()), n > 3 ? pause(a[3]) : in.intervalMs());
                return null;
            case Builtins.SWIPE_XY:
                touch(r, c, Action.swipe(L(a[0]), L(a[1]), L(a[2]), L(a[3]), n > 4 ? ms(in, c, a[4]) : in.swipeMs()), n > 5 ? pause(a[5]) : in.intervalMs());
                return null;
            case Builtins.ZOOM_IN:
            case Builtins.ZOOM_OUT:
                touch(r, c, pinch(X(a[0]), Y(a[0]), n > 1 ? (int) Math.max(80, Math.min(4000, L(a[1]))) : 400,
                        n > 2 ? ms(in, c, a[2]) : 400, c.builtin == Builtins.ZOOM_IN), in.intervalMs());
                return null;
            case Builtins.TAP_ZONE: {
                Obj z = (Obj) a[0];
                int x1 = Math.min(iz(z, 0), iz(z, 2)), x2 = Math.max(iz(z, 0), iz(z, 2));
                int y1 = Math.min(iz(z, 1), iz(z, 3)), y2 = Math.max(iz(z, 1), iz(z, 3));
                // inside the zone, away from its edges (10% on each side)
                double mx = (x2 - x1) * 0.1, my = (y2 - y1) * 0.1;
                long x = Math.round(x1 + mx + r.random.nextDouble() * Math.max(0, x2 - x1 - 2 * mx));
                long y = Math.round(y1 + my + r.random.nextDouble() * Math.max(0, y2 - y1 - 2 * my));
                Action t = Action.tap(x, y);
                t.duration = n > 1 ? ms(in, c, a[1]) : in.clickMs();
                touch(r, c, t, n > 2 ? pause(a[2]) : in.intervalMs());
                return null;
            }
            case Builtins.WAIT_STILL: {
                Obj z = n > 0 ? (Obj) a[0] : null;
                long quiet = n > 1 ? Math.max(50, L(a[1])) : 800, max = n > 2 ? Math.max(0, L(a[2])) : 10000;
                if (z == null) z = screenZone(in);
                long until = in.host.now() + max, last = r.zoneHash(z, c), since = in.host.now();
                while (true) {
                    if (in.host.now() - since >= quiet) return true;
                    if (in.host.now() >= until) return false;
                    r.sleep(Math.min(200, Math.max(50, quiet / 4)));
                    long h = r.zoneHash(z, c);
                    if (h != last) { last = h; since = in.host.now(); }
                }
            }
            case Builtins.WAIT_CHANGE: {
                Obj z = (Obj) a[0];
                long until = in.host.now() + (n > 1 ? Math.max(0, L(a[1])) : 30000), first = r.zoneHash(z, c);
                while (in.host.now() < until) {
                    r.sleep(200);
                    if (r.zoneHash(z, c) != first) return true;
                }
                return false;
            }
            case Builtins.PATTERN: {
                List<Object> pts = ((Vec) a[0]).a, cols = ((Vec) a[1]).a;
                if (pts.size() != cols.size()) throw in.rt(c, "model(): " + pts.size() + " puncte, dar " + cols.size() + " culori (trebuie câte una la fiecare punct)");
                int tol = n > 2 ? (int) L(a[2]) : 24;
                for (int i = 0; i < pts.size(); i++) {
                    int got = r.pixel(X(pts.get(i)), Y(pts.get(i)), c);
                    if (!com.freeclick.core.ColorMatch.matches(got, (Integer) cols.get(i), tol)) return false;
                }
                return true;
            }
            case Builtins.RECORD: { // what you do now, as steps: replay them with ruleaza() / reda()
                long quiet = n > 0 ? Math.max(300, L(a[0])) : 3000, max = n > 1 ? Math.max(quiet, L(a[1])) : 60000;
                TouchTracker t = in.host.touches();
                long from = t.seq(), start = in.host.now();
                r.listenBegin();
                List<TouchTracker.Gesture> got;
                try {
                    while (true) {
                        r.sleep(25);
                        got = t.since(from);
                        long now = in.host.now();
                        if (now - start >= max) break;
                        if (!got.isEmpty() && t.fingers() == 0 && now - got.get(got.size() - 1).end >= quiet) break;
                    }
                } finally { r.listenEnd(); }
                return Interp.stepsVec(recordedSteps(in, got));
            }
            case Builtins.TAPS_WAIT: { // n quick taps in the same place (3 = a triple tap)
                int want = (int) Math.max(1, Math.min(10, L(a[0])));
                TouchTracker t = in.host.touches();
                long from = t.seq(), until = n > 1 && L(a[1]) >= 0 ? in.host.now() + L(a[1]) : Long.MAX_VALUE;
                r.listenBegin();
                try {
                    while (true) {
                        TouchTracker.Gesture g = t.nthTap(from, want);
                        if (g != null) { r.lastGesture = g; return scriptPoint(in, g.x1, g.y1); }
                        if (in.host.now() >= until) return Interp.point(-1, -1);
                        r.sleep(25);
                    }
                } finally { r.listenEnd(); }
            }
            case Builtins.NEW_GESTURES: {
                mustListen(in, r, c);
                TouchTracker t = in.host.touches();
                long mark = r.gesturesMark;
                r.gesturesMark = t.seq();
                return mark < 0 ? 0L : (long) t.gesturesSince(mark);
            }
            case Builtins.GEST_SCALE: return r.lastGesture == null ? 1.0 : r.lastGesture.scale;
            case Builtins.GEST_ROT: return r.lastGesture == null ? 0.0 : r.lastGesture.rotation;
            case Builtins.FILE_WRITE:
            case Builtins.FILE_APPEND: {
                String name = fileName(in, c, (String) a[0]);
                String text = (String) a[1];
                if (text.length() > MAX_FILE) throw in.rt(c, "Textul e prea lung pentru un fișier (cel mult " + MAX_FILE / 1000 + " 000 de caractere)");
                if (c.builtin == Builtins.FILE_APPEND) {
                    String old = in.host.readFile(name);
                    if (old != null && old.length() + text.length() > MAX_FILE) throw in.rt(c, "Fișierul „" + name + "” ar depăși " + MAX_FILE / 1000 + " 000 de caractere");
                }
                if (!in.host.writeFile(name, text, c.builtin == Builtins.FILE_APPEND)) throw in.rt(c, "Nu am putut scrie fișierul „" + name + "”");
                return null;
            }
            case Builtins.FILE_READ: {
                String t = in.host.readFile(fileName(in, c, (String) a[0]));
                return t == null ? "" : t;
            }
            case Builtins.FILE_EXISTS: return in.host.readFile(fileName(in, c, (String) a[0])) != null;
            case Builtins.SWIPE_CURVE: { // a swipe along a bend, starting and ending slowly, like a finger
                long ms = n > 2 ? ms(in, c, a[2]) : in.swipeMs();
                double bend = n > 3 ? D(a[3]) : 0.25;
                double x1 = X(a[0]), y1 = Y(a[0]), x2 = X(a[1]), y2 = Y(a[1]);
                double cx = (x1 + x2) / 2 - (y2 - y1) * bend, cy = (y1 + y2) / 2 + (x2 - x1) * bend;
                Stroke s = new Stroke();
                int steps = 24;
                for (int i = 0; i <= steps; i++) {
                    double u = i / (double) steps, e = u * u * (3 - 2 * u); // slow, fast, slow
                    double px = (1 - e) * (1 - e) * x1 + 2 * (1 - e) * e * cx + e * e * x2;
                    double py = (1 - e) * (1 - e) * y1 + 2 * (1 - e) * e * cy + e * e * y2;
                    s.add((float) px, (float) py, Math.round(ms * u));
                }
                List<Stroke> one = new ArrayList<Stroke>();
                one.add(s);
                touch(r, c, Action.gesture(one), n > 4 ? pause(a[4]) : in.intervalMs());
                return null;
            }
            case Builtins.WAIT_ANY: { // whichever picture comes first
                List<Object> names = ((Vec) a[0]).a;
                if (names.isEmpty()) throw in.rt(c, "asteaptaOricare(): lista de imagini e goală");
                List<ImageSpec> specs = new ArrayList<ImageSpec>();
                for (Object o : names) specs.add(in.image((String) o, c));
                long until = n > 1 && L(a[1]) >= 0 ? in.host.now() + L(a[1]) : Long.MAX_VALUE;
                while (true) {
                    for (int i = 0; i < specs.size(); i++) if (!r.find(specs.get(i), false, c).isEmpty()) return (long) i;
                    if (in.host.now() >= until) return -1L;
                    r.sleep(100);
                }
            }
            case Builtins.ASK: {
                String got = r.ask((String) a[0], n > 1 ? (String) a[1] : "", false);
                return got == null ? (n > 1 ? (String) a[1] : "") : got;
            }
            case Builtins.ASK_NUMBER: {
                long preset = n > 1 ? L(a[1]) : 0;
                String got = r.ask((String) a[0], String.valueOf(preset), true);
                Number v = got == null ? null : Interp.num(got.trim());
                return v == null ? preset : v.longValue();
            }
            case Builtins.CHOOSE: {
                List<String> opts = new ArrayList<String>();
                for (Object o : ((Vec) a[1]).a) opts.add((String) o);
                if (opts.isEmpty()) throw in.rt(c, "alege(): lista de variante e goală");
                return (long) r.choose((String) a[0], opts);
            }
            case Builtins.BUTTON: {
                Obj z = (Obj) a[1];
                int x1 = Math.min(iz(z, 0), iz(z, 2)), x2 = Math.max(iz(z, 0), iz(z, 2));
                int y1 = Math.min(iz(z, 1), iz(z, 3)), y2 = Math.max(iz(z, 1), iz(z, 3));
                if (x2 - x1 < 10 || y2 - y1 < 10) throw in.rt(c, "buton(): zona e prea mică pentru un buton (" + (x2 - x1) + " × " + (y2 - y1) + ")");
                return (long) in.host.addButton((String) a[0], x1, y1, x2, y2);
            }
            case Builtins.PRESSED: return in.host.takePress((int) L(a[0]));
            case Builtins.WAIT_BUTTON: {
                long until = n > 0 && L(a[0]) >= 0 ? in.host.now() + L(a[0]) : Long.MAX_VALUE;
                while (true) {
                    int id = in.host.takeAnyPress();
                    if (id > 0) return (long) id;
                    if (in.host.now() >= until) return -1L;
                    r.sleep(25);
                }
            }
            case Builtins.REMOVE_BUTTON: in.host.removeButton((int) Math.max(1, L(a[0]))); return null;
            case Builtins.REMOVE_BUTTONS: in.host.removeButton(0); return null;
            case Builtins.LISTEN_ZONE: {
                Obj z = (Obj) a[0];
                in.host.listenZone(Math.min(iz(z, 0), iz(z, 2)), Math.min(iz(z, 1), iz(z, 3)), Math.max(iz(z, 0), iz(z, 2)), Math.max(iz(z, 1), iz(z, 3)));
                return null;
            }
            case Builtins.LISTEN_ALL: in.host.listenZone(-1, -1, -1, -1); return null;
            case Builtins.HUD:
                if (n > 1) in.host.hud((String) a[0], X(a[1]), Y(a[1])); else in.host.hud((String) a[0], -1, -1);
                return null;
            case Builtins.HUD_HIDE: in.host.hud("", -1, -1); return null;
            case Builtins.BATTERY: return (long) in.host.battery();
            case Builtins.CHARGING: return in.host.charging();
            case Builtins.ORIENTATION: return in.host.landscape() ? "peisaj" : "portret";
            case Builtins.NOTIFY:
                if (n > 1) in.host.notifyUser((String) a[0], (String) a[1]); else in.host.notifyUser("FreeClick", (String) a[0]);
                return null;
            case Builtins.PERCENT_COLOR: { // how full a bar is
                Obj z = (Obj) a[0];
                int x1 = Math.min(iz(z, 0), iz(z, 2)), x2 = Math.max(iz(z, 0), iz(z, 2));
                int y1 = Math.min(iz(z, 1), iz(z, 3)), y2 = Math.max(iz(z, 1), iz(z, 3));
                if (x2 <= x1 || y2 <= y1) throw in.rt(c, "procentCuloare(): zona e goală (" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")");
                int[] dims = com.freeclick.core.ColorZones.barGrid(x2 - x1, y2 - y1);
                int[] g = r.grid(x1, y1, x2, y2, dims[0], dims[1], c);
                double p = com.freeclick.core.ColorZones.percent(g, dims[0], dims[1], (Integer) a[1], n > 2 ? (int) L(a[2]) : 30);
                return Math.round(p * 10) / 10.0;
            }
            case Builtins.FIND_COLOR: {
                Obj z = (Obj) a[0];
                int x1 = Math.min(iz(z, 0), iz(z, 2)), x2 = Math.max(iz(z, 0), iz(z, 2));
                int y1 = Math.min(iz(z, 1), iz(z, 3)), y2 = Math.max(iz(z, 1), iz(z, 3));
                if (x2 <= x1 || y2 <= y1) throw in.rt(c, "gasesteCuloare(): zona e goală (" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")");
                int[] dims = com.freeclick.core.ColorZones.findGrid(x2 - x1, y2 - y1);
                int[] g = r.grid(x1, y1, x2, y2, dims[0], dims[1], c);
                int[] cell = com.freeclick.core.ColorZones.find(g, dims[0], dims[1], (Integer) a[1], n > 2 ? (int) L(a[2]) : 30,
                        dims[0] == x2 - x1 && dims[1] == y2 - y1);
                if (cell == null) return Interp.point(-1, -1);
                int[] p = com.freeclick.core.ColorZones.cellCentre(x1, y1, x2, y2, dims[0], dims[1], cell);
                return Interp.point(p[0], p[1]);
            }
            case Builtins.TAP_MULTI: { // several fingers at the same moment
                List<Object> pts = ((Vec) a[0]).a;
                if (pts.isEmpty()) return null;
                if (pts.size() > com.freeclick.core.MacroTransform.MAX_SYNC_FINGERS) {
                    throw in.rt(c, "atingeDeodata(): cel mult " + com.freeclick.core.MacroTransform.MAX_SYNC_FINGERS + " puncte deodată, nu " + pts.size());
                }
                long press = n > 1 ? ms(in, c, a[1]) : in.clickMs();
                List<Stroke> fingers = new ArrayList<Stroke>();
                for (Object p : pts) fingers.add(new Stroke().add(X(p), Y(p), 0).add(X(p), Y(p), Math.max(1, press)));
                touch(r, c, Action.gesture(fingers), n > 2 ? pause(a[2]) : in.intervalMs());
                return null;
            }
            // ---------------------------------------------------------- 2.3: your fingers
            case Builtins.TOUCH_WAIT: {
                TouchTracker.Gesture g = waitGesture(in, r, null, n > 0 ? L(a[0]) : -1);
                return g == null ? Interp.point(-1, -1) : scriptPoint(in, g.x1, g.y1);
            }
            case Builtins.GESTURE_WAIT: {
                TouchTracker.Gesture g = waitGesture(in, r, null, n > 0 ? L(a[0]) : -1);
                return g == null ? "" : g.describe();
            }
            case Builtins.SWIPE_WAIT: {
                TouchTracker.Gesture g = waitGesture(in, r, TouchTracker.SWIPE, n > 0 ? L(a[0]) : -1);
                return g == null ? "" : g.direction;
            }
            case Builtins.DOUBLE_WAIT: {
                TouchTracker.Gesture g = waitGesture(in, r, TouchTracker.DOUBLE, n > 0 ? L(a[0]) : -1);
                return g == null ? Interp.point(-1, -1) : scriptPoint(in, g.x1, g.y1);
            }
            case Builtins.LONG_WAIT: {
                TouchTracker.Gesture g = waitGesture(in, r, TouchTracker.LONG, n > 0 ? L(a[0]) : -1);
                return g == null ? Interp.point(-1, -1) : scriptPoint(in, g.x1, g.y1);
            }
            case Builtins.FINGERS_WAIT: {
                int want = (int) Math.max(1, Math.min(10, L(a[0])));
                TouchTracker t = in.host.touches();
                long t0 = in.host.now(), until = n > 1 && L(a[1]) >= 0 ? t0 + L(a[1]) : Long.MAX_VALUE;
                r.listenBegin();
                try {
                    while (true) {
                        if (t.fingers() >= want || t.lastAtLeast(want) >= t0) return true;
                        if (in.host.now() >= until) return false;
                        r.sleep(25);
                    }
                } finally { r.listenEnd(); }
            }
            case Builtins.NO_FINGERS_WAIT: {
                TouchTracker t = in.host.touches();
                long t0 = in.host.now(), until = n > 0 && L(a[0]) >= 0 ? t0 + L(a[0]) : Long.MAX_VALUE;
                r.listenBegin();
                try {
                    while (true) {
                        if (t.fingers() == 0 || t.lastZero() >= t0) return true;
                        if (in.host.now() >= until) return false;
                        r.sleep(25);
                    }
                } finally { r.listenEnd(); }
            }
            case Builtins.FINGERS: mustListen(in, r, c); return (long) in.host.touches().fingers();
            case Builtins.TOUCHING: mustListen(in, r, c); return in.host.touches().fingers() > 0;
            case Builtins.FINGER_POS: {
                mustListen(in, r, c);
                int[] f = in.host.touches().finger(n > 0 ? (int) Math.max(0, L(a[0])) : 0);
                return f == null ? Interp.point(-1, -1) : scriptPoint(in, f[0], f[1]);
            }
            case Builtins.LAST_TOUCH: {
                mustListen(in, r, c);
                int[] f = in.host.touches().lastDown();
                return f[0] < 0 ? Interp.point(-1, -1) : scriptPoint(in, f[0], f[1]);
            }
            case Builtins.NEW_TAPS: {
                mustListen(in, r, c);
                TouchTracker t = in.host.touches();
                long mark = r.tapsMark;
                r.tapsMark = t.seq();
                return mark < 0 ? 0L : (long) t.tapsSince(mark);
            }
            case Builtins.ZONE_TOUCH_WAIT: {
                Obj z = (Obj) a[0];
                int x1 = Math.min(iz(z, 0), iz(z, 2)), x2 = Math.max(iz(z, 0), iz(z, 2));
                int y1 = Math.min(iz(z, 1), iz(z, 3)), y2 = Math.max(iz(z, 1), iz(z, 3));
                TouchTracker t = in.host.touches();
                long from = t.seq(), until = n > 1 && L(a[1]) >= 0 ? in.host.now() + L(a[1]) : Long.MAX_VALUE;
                r.listenBegin();
                try {
                    while (true) {
                        TouchTracker.Gesture g = t.next(from, TouchTracker.TAP);
                        if (g != null) {
                            from = g.seq;
                            int[] p = in.host.toScript(g.x1, g.y1);
                            if (p[0] >= x1 && p[0] <= x2 && p[1] >= y1 && p[1] <= y2) { r.lastGesture = g; return true; }
                            continue;
                        }
                        if (in.host.now() >= until) return false;
                        r.sleep(25);
                    }
                } finally { r.listenEnd(); }
            }
            case Builtins.PICK_ZONE: {
                TouchTracker.Gesture g = waitGesture(in, r, TouchTracker.SWIPE, n > 0 ? L(a[0]) : -1);
                if (g == null) return Interp.rect(-1, -1, -1, -1);
                int[] p = in.host.toScript(g.x1, g.y1), q = in.host.toScript(g.x2, g.y2);
                return Interp.rect(Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.max(p[0], q[0]), Math.max(p[1], q[1]));
            }
            case Builtins.PICK_COLOR: {
                TouchTracker.Gesture g = waitGesture(in, r, TouchTracker.TAP, n > 0 ? L(a[0]) : -1);
                if (g == null) return -1;
                int[] p = in.host.toScript(g.x1, g.y1);
                r.sleep(150); // what the tap opened has drawn itself; the colour is read where you touched
                return r.pixel(p[0], p[1], c);
            }
            case Builtins.LISTEN: r.setListening((Boolean) a[0]); return null;
            case Builtins.FORWARD: r.setForward((Boolean) a[0]); return null;
            case Builtins.GEST_START: {
                TouchTracker.Gesture g = r.lastGesture;
                return g == null ? Interp.point(-1, -1) : scriptPoint(in, g.x1, g.y1);
            }
            case Builtins.GEST_END: {
                TouchTracker.Gesture g = r.lastGesture;
                return g == null ? Interp.point(-1, -1) : scriptPoint(in, g.x2, g.y2);
            }
            case Builtins.GEST_FINGERS: return r.lastGesture == null ? 0L : (long) r.lastGesture.fingers;
            case Builtins.GEST_MS: return r.lastGesture == null ? 0L : r.lastGesture.duration();
            case Builtins.SAVE_SHOT: return r.saveScreenshot(n > 0 ? (String) a[0] : "");
            case Builtins.COPY: in.host.copyText((String) a[0]); return null;
            case Builtins.OPEN_URL: return r.openUrl((String) a[0]);
            case Builtins.DRAG: {
                long move = n > 2 ? ms(in, c, a[2]) : 400, hold = n > 3 ? Math.max(0, L(a[3])) : 500;
                Stroke s = new Stroke();
                s.add(X(a[0]), Y(a[0]), 0);
                s.add(X(a[0]), Y(a[0]), Math.max(1, hold));
                s.add(X(a[1]), Y(a[1]), hold + move);
                List<Stroke> l = new ArrayList<Stroke>();
                l.add(s);
                r.gesture(Action.gesture(l), c);
                return null;
            }
            case Builtins.WAIT: r.sleep(Math.max(0, L(a[0]))); return null;
            case Builtins.WAIT_RANDOM: {
                long lo = Math.min(L(a[0]), L(a[1])), hi = Math.max(L(a[0]), L(a[1]));
                r.sleep(Math.max(0, lo + (long) (r.random.nextDouble() * (hi - lo + 1))));
                return null;
            }
            case Builtins.BACK: case Builtins.HOME: case Builtins.RECENTS: case Builtins.NOTIFICATIONS:
                r.global(Interp.globalOf(c.builtin));
                return null;
            case Builtins.OPEN_APP: return r.openApp((String) a[0]);
            case Builtins.STOP: r.scriptStop(); return null;
            case Builtins.TOAST: in.host.toast((String) a[0]); return null;
            case Builtins.VIBRATE: in.host.vibrate(n > 0 ? Math.max(1, Math.min(5000, L(a[0]))) : 300); return null;
            // ---------------------------------------------------------- the screen
            case Builtins.PIXEL: return r.pixel(X(a[0]), Y(a[0]), c);
            case Builtins.PIXEL_XY: return r.pixel((int) L(a[0]), (int) L(a[1]), c);
            case Builtins.NEAR: return com.freeclick.core.ColorMatch.matches((Integer) a[0], (Integer) a[1], n > 2 ? (int) L(a[2]) : 24);
            case Builtins.READ: {
                Obj z = (Obj) a[0];
                String t = r.read(z, true, c);
                Number v = t == null ? null : Interp.num(t);
                if (v == null) in.host.print("⚠ linia " + c.line + ": n-am putut citi un număr (am văzut „" + (t == null ? "" : t) + "”); întorc -1", PlaybackEngine.PRINT_WARN);
                return v == null ? -1.0 : v.doubleValue();
            }
            case Builtins.READ_TEXT: { String t = r.read((Obj) a[0], false, c); return t == null ? "" : t.trim(); }
            case Builtins.VISIBLE: return !r.find(in.image((String) a[0], c), false, c).isEmpty();
            case Builtins.FIND: {
                List<int[]> pts = r.find(in.image((String) a[0], c), false, c);
                return pts.isEmpty() ? Interp.point(-1, -1) : Interp.point(pts.get(0)[0], pts.get(0)[1]);
            }
            case Builtins.FIND_ALL: {
                Vec v = new Vec();
                for (int[] p : r.find(in.image((String) a[0], c), true, c)) v.a.add(Interp.point(p[0], p[1]));
                return v;
            }
            case Builtins.WAIT_IMAGE: {
                ImageSpec spec = in.image((String) a[0], c);
                long until = in.host.now() + Math.max(0, L(a[1]));
                while (true) {
                    if (!r.find(spec, false, c).isEmpty()) return true;
                    if (in.host.now() >= until) return false;
                    r.sleep(200);
                }
            }
            case Builtins.WAIT_COLOR: {
                long until = in.host.now() + (n > 2 ? Math.max(0, L(a[2])) : 30000);
                int tol = n > 3 ? (int) L(a[3]) : 24;
                while (true) {
                    if (com.freeclick.core.ColorMatch.matches(r.pixel(X(a[0]), Y(a[0]), c), (Integer) a[1], tol)) return true;
                    if (in.host.now() >= until) return false;
                    r.sleep(150);
                }
            }
            case Builtins.FOUND: return X(a[0]) >= 0 && Y(a[0]) >= 0;
            case Builtins.SCREEN_W: return (long) in.host.screenWidth();
            case Builtins.SCREEN_H: return (long) in.host.screenHeight();
            case Builtins.SCREEN: return Interp.rect(0, 0, in.host.screenWidth(), in.host.screenHeight());
            // ---------------------------------------------------------- time and numbers
            case Builtins.MILLIS: return in.host.now() - r.startedAt;
            case Builtins.CLOCK: return Interp.clockText(in.host.wallTime());
            case Builtins.HOUR: return (long) cal(in).get(java.util.Calendar.HOUR_OF_DAY);
            case Builtins.MINUTE: return (long) cal(in).get(java.util.Calendar.MINUTE);
            case Builtins.WEEKDAY: { int d = cal(in).get(java.util.Calendar.DAY_OF_WEEK); return (long) (d == java.util.Calendar.SUNDAY ? 7 : d - 1); }
            case Builtins.RANDOM: {
                long lo = Math.min(L(a[0]), L(a[1])), hi = Math.max(L(a[0]), L(a[1]));
                return lo + (long) Math.floor(r.random.nextDouble() * (hi - lo + 1));
            }
            case Builtins.SQRT: if (D(a[0]) < 0) throw in.rt(c, "sqrt dintr-un număr negativ"); return Math.sqrt(D(a[0]));
            case Builtins.POW: return Math.pow(D(a[0]), D(a[1]));
            case Builtins.ROUND: return (double) Math.round(D(a[0]));
            case Builtins.FLOOR: return Math.floor(D(a[0]));
            case Builtins.CEIL: return Math.ceil(D(a[0]));
            case Builtins.TO_INT: {
                Number v = Interp.num((String) a[0]);
                if (v == null) throw in.rt(c, "„" + a[0] + "” nu conține un număr");
                return (long) v.doubleValue();
            }
            case Builtins.TO_DOUBLE: {
                Number v = Interp.num((String) a[0]);
                if (v == null) throw in.rt(c, "„" + a[0] + "” nu conține un număr");
                return v.doubleValue();
            }
            case Builtins.DIST: return Math.hypot(X(a[0]) - X(a[1]), Y(a[0]) - Y(a[1]));
            case Builtins.CENTER: { Obj z = (Obj) a[0]; return Interp.point((L(z.f[0]) + L(z.f[2])) / 2, (L(z.f[1]) + L(z.f[3])) / 2); }
            case Builtins.CONTAINS_PT: {
                Obj z = (Obj) a[0];
                return X(a[1]) >= L(z.f[0]) && X(a[1]) < L(z.f[2]) && Y(a[1]) >= L(z.f[1]) && Y(a[1]) < L(z.f[3]);
            }
            case Builtins.HEX: return com.freeclick.core.ColorMatch.toHex((Integer) a[0]);
            // ---------------------------------------------------------- the clicker steps
            case Builtins.RUN_STEPS: {
                List<Action> list = new ArrayList<Action>();
                for (Object o : ((Vec) a[0]).a) list.add(Interp.toAction((Obj) o));
                r.runSteps(list, n > 1 ? (int) Math.max(1, L(a[1])) : 1, c);
                return null;
            }
            case Builtins.RUN_STEP: {
                List<Action> list = new ArrayList<Action>();
                list.add(Interp.toAction((Obj) a[0]));
                r.runSteps(list, n > 1 ? (int) Math.max(1, L(a[1])) : 1, c);
                return null;
            }
            case Builtins.STEPS_OF: {
                List<Action> l = in.host.stepsOf((String) a[0]);
                if (l == null) throw in.rt(c, "Nu am o salvare numită „" + a[0] + "”");
                return Interp.stepsVec(l);
            }
            case Builtins.RUN_MACRO: {
                List<Action> l = in.host.stepsOf((String) a[0]);
                if (l == null) throw in.rt(c, "Nu am o salvare numită „" + a[0] + "”");
                r.runSteps(l, n > 1 ? (int) Math.max(1, L(a[1])) : 1, c);
                return null;
            }
            case Builtins.STEP_TAP: return Interp.fromAction(Action.tap(X(a[0]), Y(a[0])));
            case Builtins.STEP_SWIPE: return Interp.fromAction(Action.swipe(X(a[0]), Y(a[0]), X(a[1]), Y(a[1]), n > 2 ? Math.max(1, L(a[2])) : Action.DEFAULT_SWIPE_MS));
            case Builtins.STEP_WAIT: return Interp.fromAction(Action.waitFor(Math.max(0, L(a[0]))));
            case Builtins.STEP_LONG: return Interp.fromAction(Action.longPress(X(a[0]), Y(a[0]), n > 1 ? Math.max(1, L(a[1])) : Action.DEFAULT_LONG_PRESS_MS));
            // ---------------------------------------------------------- generic
            case Builtins.PRINT: {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) b.append(in.show(a[i], c.args.get(i).type));
                in.host.print(b.toString(), PlaybackEngine.PRINT_USER);
                return null;
            }
            case Builtins.TO_STRING: return in.show(a[0], c.args.get(0).type);
            case Builtins.ABS: return a[0] instanceof Long ? (Object) Math.abs(L(a[0])) : (Object) Math.abs(D(a[0]));
            case Builtins.MIN:
            case Builtins.MAX: {
                Object best = a[0];
                for (int i = 1; i < n; i++) {
                    int cmp = a[i] instanceof String ? ((String) a[i]).compareTo((String) best)
                            : Double.compare(((Number) a[i]).doubleValue(), ((Number) best).doubleValue());
                    if (c.builtin == Builtins.MIN ? cmp < 0 : cmp > 0) best = a[i];
                }
                return best;
            }
            case Builtins.MAKE_PAIR: { Obj o = new Obj(2); o.f[0] = Interp.copy(a[0]); o.f[1] = Interp.copy(a[1]); return o; }
            case Builtins.SORT: Interp.sort((Vec) a[0]); return null;
            case Builtins.REVERSE: java.util.Collections.reverse(((Vec) a[0]).a); return null;
            case Builtins.SWAP: {
                // values are swapped through the variables themselves
                Object x = Interp.copy(a[0]), y = Interp.copy(a[1]);
                in.storeFromNative(c.args.get(0), y, fr);
                in.storeFromNative(c.args.get(1), x, fr);
                return null;
            }
            default: throw in.rt(c, "Funcție necunoscută (" + c.builtin + ")");
        }
    }

    private static int iz(Obj z, int i) { return (int) L(z.f[i]); }

    /** Two fingers moving apart (zoom in) or together (zoom out) around a point, side by side. */
    static Action pinch(int cx, int cy, int span, long ms, boolean in) {
        int near = Math.max(20, span / 6), far = Math.max(near + 40, span / 2);
        int from = in ? near : far, to = in ? far : near;
        List<Stroke> strokes = new ArrayList<Stroke>();
        for (int side = -1; side <= 1; side += 2) {
            Stroke s = new Stroke();
            s.add(cx + side * from, cy, 0);
            s.add(cx + side * to, cy, Math.max(1, ms));
            strokes.add(s);
        }
        return Action.gesture(strokes);
    }

    /** A touch, then the pause after it (INTERVAL_REPETARE or the one written in the call). */
    private static void touch(ScriptRunner r, Call c, Action a, long pauseAfter) {
        r.gesture(a, c);
        if (pauseAfter > 0) r.sleep(pauseAfter);
    }

    private static Obj screenZone(Interp in) {
        Obj z = new Obj(4);
        z.f[0] = 0L; z.f[1] = 0L; z.f[2] = (long) in.host.screenWidth(); z.f[3] = (long) in.host.screenHeight();
        return z;
    }

    private static long pause(Object v) { return Math.max(0, Math.min(24L * 3600000, L(v))); }

    private static long ms(Interp in, Call c, Object v) {
        long m = L(v);
        if (m < 1) throw in.rt(c, "Durata trebuie să fie cel puțin 1 ms");
        return Math.min(m, 60000);
    }

    private static java.util.Calendar cal(Interp in) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(in.host.wallTime());
        return c;
    }

    /** Largest file a script may write (1 000 000 characters). */
    static final int MAX_FILE = 1000000;

    /** A plain name, kept inside FreeClick's own folder for scripts. */
    private static String fileName(Interp in, Call c, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 64 || !n.matches("[\\p{L}\\p{N} _.-]+") || n.startsWith(".")) {
            throw in.rt(c, "Numele de fișier „" + name + "” nu merge: litere, cifre, spațiu, . _ - (cel mult 64), fără foldere");
        }
        return n;
    }

    /**
     * Your gestures as steps, with the pauses between them: a tap or a long press as such, everything else
     * (swipes, curves, several fingers) as the exact path of every finger, so the replay is the same.
     */
    static List<Action> recordedSteps(Interp in, List<TouchTracker.Gesture> gs) {
        List<Action> out = new ArrayList<Action>();
        long prevEnd = -1;
        for (TouchTracker.Gesture g : gs) {
            Action a;
            int[] p = in.host.toScript(g.x1, g.y1);
            if (TouchTracker.TAP.equals(g.type) && g.fingers <= 1) {
                a = Action.tap(p[0], p[1]);
                a.duration = Math.max(1, g.duration());
            } else if (TouchTracker.LONG.equals(g.type) && g.fingers <= 1) {
                a = Action.longPress(p[0], p[1], Math.max(1, g.duration()));
            } else {
                List<Stroke> fingers = new ArrayList<Stroke>();
                for (List<float[]> path : g.paths) {
                    Stroke s = new Stroke();
                    for (float[] pt : path) {
                        int[] q = in.host.toScript(Math.round(pt[0]), Math.round(pt[1]));
                        s.add(q[0], q[1], (long) pt[2]);
                    }
                    if (s.size() > 0) fingers.add(s);
                }
                if (fingers.isEmpty()) continue;
                a = Action.gesture(fingers);
            }
            a.delay = prevEnd < 0 ? 0 : Math.max(0, g.start - prevEnd);
            prevEnd = g.end;
            out.add(a);
        }
        return out;
    }

    // ---------------------------------------------------------- 2.3: your fingers
    /** Waits for your next gesture of this type (null = any); null after {@code timeoutMs} (< 0 = no limit). */
    private static TouchTracker.Gesture waitGesture(Interp in, ScriptRunner r, String type, long timeoutMs) {
        TouchTracker t = in.host.touches();
        long from = t.seq(), until = timeoutMs < 0 ? Long.MAX_VALUE : in.host.now() + timeoutMs;
        r.listenBegin();
        try {
            while (true) {
                TouchTracker.Gesture g = t.next(from, type);
                if (g != null) { r.lastGesture = g; return g; }
                if (in.host.now() >= until) return null;
                r.sleep(25);
            }
        } finally { r.listenEnd(); }
    }

    /** degete(), atins()… read your fingers right now: that needs asculta(true) first (a rule, not a guess). */
    private static void mustListen(Interp in, ScriptRunner r, Call c) {
        if (!r.listeningKept()) {
            throw in.rt(c, c.name + "() citește degetele de acum, deci scriptul trebuie să asculte: scrie asculta(true); înainte "
                    + "(și asculta(false); când nu mai ai nevoie). Funcțiile asteapta…() ascultă singure cât așteaptă.");
        }
    }

    private static Obj scriptPoint(Interp in, int x, int y) {
        int[] p = in.host.toScript(x, y);
        return Interp.point(p[0], p[1]);
    }

}
