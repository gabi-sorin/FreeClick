package com.freeclick.app;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.freeclick.core.Action;
import com.freeclick.core.CoordinateScaler;
import com.freeclick.core.GesturePlan;
import com.freeclick.core.GesturePlanner;
import com.freeclick.core.ImageMatcher;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.ImageTemplate;
import com.freeclick.core.Macro;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.ScreenFrame;
import com.freeclick.core.TextReader;
import com.freeclick.core.script.ScriptError;
import com.freeclick.core.script.ScriptHost;
import com.freeclick.core.script.ScriptRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * One run of a script: the ScriptRunner on its own thread, and everything it asks of the phone
 * (touches, frames, reads, the normal engine for 'ruleaza(pasi)'), done on the main thread while
 * the script thread waits. Also the debugger bar (step by step, breakpoints).
 */
final class ScriptSession implements ScriptHost, ScriptRunner.Listener {
    final ClickService svc;
    final Macro macro;
    final boolean fromEditor;
    private final Handler main;
    private final String[] lines;
    final ScriptRunner runner;
    private volatile long lastTouchEnd;
    private final java.util.Random random = new java.util.Random();
    /** Set while 'ruleaza' plays steps through the engine. */
    volatile CountDownLatch stepsDone;
    private View debugBar;

    ScriptSession(ClickService svc, Handler main, Macro macro, boolean fromEditor, boolean step, java.util.Set<Integer> breakpoints) {
        this.svc = svc;
        this.main = main;
        this.macro = macro;
        this.fromEditor = fromEditor;
        this.lines = (macro.script == null ? "" : macro.script).split("\n", -1);
        List<Action> steps = new ArrayList<Action>();
        for (Action a : macro.actions) steps.add(a.copy()); // tree-ok: the script's own top-level list
        runner = new ScriptRunner(macro.script, steps, this, this);
        // a script that listens to your fingers takes touches as input, not as "pause"
        runner.setPauseOnTouch(svc.prefsPauseOnTouch() && !ScriptRunner.usesTouchInput(macro.script));
        runner.setStepMode(step);
        runner.setBreakpoints(breakpoints);
    }

    void start() { runner.start(); }

    String lineText(int line) {
        if (line < 1 || line > lines.length) return "";
        return lines[line - 1].trim();
    }

    // ------------------------------------------------------------------ waiting for the main thread
    private interface MainJob { void run(CountDownLatch done); }

    private void onMain(final MainJob job) throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        main.post(new Runnable() { public void run() {
            try { job.run(done); } catch (RuntimeException e) { done.countDown(); }
        } });
        done.await();
    }

    // ------------------------------------------------------------------ ScriptHost (script thread)
    @Override public long now() { return SystemClock.uptimeMillis(); }

    // ------------------------------------------------------------------ 2.3: your fingers
    @Override public com.freeclick.core.TouchTracker touches() { return svc.touchTracker(); }

    @Override public void listen(final boolean on, final boolean forward) {
        main.post(new Runnable() { public void run() { svc.scriptListen(on, forward); } });
    }

    // ------------------------------------------------------------------ 2.4: showing, telling, the phone
    @Override public void hud(final String text, int dx, int dy) {
        final int x, y;
        if (dx < 0) { x = -1; y = -1; } else { CoordinateScaler sc = scaler(); x = Math.round(sc.x(dx)); y = Math.round(sc.y(dy)); }
        main.post(new Runnable() { public void run() { svc.showHud(text, x, y); } });
    }

    @Override public int battery() {
        android.os.BatteryManager bm = (android.os.BatteryManager) svc.getSystemService(android.content.Context.BATTERY_SERVICE);
        int v = bm == null ? -1 : bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return v < 0 || v > 100 ? -1 : v;
    }

    @Override public boolean charging() {
        android.content.Intent i = svc.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        return i != null && i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    @Override public boolean landscape() {
        android.graphics.Point p = Overlays.screenSize(svc);
        return p.x > p.y;
    }

    // ------------------------------------------------------------------ 2.5: buttons and the listening zone
    private final java.util.concurrent.atomic.AtomicInteger buttonIds = new java.util.concurrent.atomic.AtomicInteger();

    @Override public int addButton(final String text, int dx1, int dy1, int dx2, int dy2) {
        final int id = buttonIds.incrementAndGet();
        CoordinateScaler sc = scaler();
        final int x1 = Math.round(sc.x(dx1)), y1 = Math.round(sc.y(dy1)), x2 = Math.round(sc.x(dx2)), y2 = Math.round(sc.y(dy2));
        main.post(new Runnable() { public void run() { svc.scriptButtons().add(id, text, x1, y1, x2, y2); } });
        return id;
    }

    @Override public void removeButton(final int id) {
        main.post(new Runnable() { public void run() { svc.scriptButtons().remove(id); } });
    }

    @Override public boolean takePress(int id) { return svc.scriptButtons().take(id); }

    @Override public int takeAnyPress() { return svc.scriptButtons().takeAny(); }

    @Override public void listenZone(int dx1, int dy1, int dx2, int dy2) {
        final int[] z;
        if (dx1 < 0) z = null;
        else {
            CoordinateScaler sc = scaler();
            z = new int[]{Math.round(sc.x(dx1)), Math.round(sc.y(dy1)), Math.round(sc.x(dx2)), Math.round(sc.y(dy2))};
        }
        main.post(new Runnable() { public void run() { svc.scriptListenZone(z); } });
    }

    // ------------------------------------------------------------------ 2.5: files (FreeClick's folder: files/scripts)
    private java.io.File scriptFile(String name) { return new java.io.File(new java.io.File(svc.getFilesDir(), "scripts"), name); }

    @Override public String readFile(String name) {
        java.io.File f = scriptFile(name);
        if (!f.isFile()) return null;
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 8L << 20)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try { int n = 0, r; while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r; } finally { in.close(); }
            return new String(b, "UTF-8");
        } catch (java.io.IOException e) {
            return null;
        }
    }

    @Override public boolean writeFile(String name, String text, boolean append) {
        java.io.File f = scriptFile(name);
        java.io.File dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        if (!f.exists()) {
            String[] all = dir.list();
            if (all != null && all.length >= 200) return false; // a script in a loop does not fill the phone
        }
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(f, append);
            try { out.write(text.getBytes("UTF-8")); } finally { out.close(); }
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 2.5: asking you
    @Override public String ask(final String q, final String preset, final boolean number) throws InterruptedException {
        final String[] got = {null};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.scriptAsk(q, preset, number, new ClickService.Answer() { public void done(String s, int i) { got[0] = s; done.countDown(); } });
        } });
        return got[0];
    }

    @Override public int choose(final String title, final List<String> options) throws InterruptedException {
        final int[] got = {-1};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.scriptChoose(title, options, new ClickService.Answer() { public void done(String s, int i) { got[0] = i; done.countDown(); } });
        } });
        return got[0];
    }

    @Override public void notifyUser(final String title, final String text) {
        main.post(new Runnable() { public void run() { svc.scriptNotification(title, text); } });
    }

    @Override public int[] toScript(int x, int y) {
        CoordinateScaler sc = scaler();
        return new int[]{(int) Math.round(x / (double) sc.sx), (int) Math.round(y / (double) sc.sy)};
    }
    @Override public long wallTime() { return System.currentTimeMillis(); }
    @Override public void sleep(long ms) throws InterruptedException { Thread.sleep(Math.max(0, ms)); }

    // ------------------------------------------------------------------ the script's coordinates
    /**
     * The numbers in the code are for the screen the script was made on (its saved size); on
     * another phone they are scaled. On the same phone (either orientation) this is 1:1.
     */
    private int[] design() {
        android.graphics.Point sz = Overlays.screenSize(svc);
        return com.freeclick.core.script.ScreenSpace.designFor(macro.screenWidth, macro.screenHeight, sz.x, sz.y);
    }

    private CoordinateScaler scaler() {
        android.graphics.Point sz = Overlays.screenSize(svc);
        int[] d = design();
        return CoordinateScaler.of(d[0], d[1], sz.x, sz.y, true);
    }

    @Override public boolean gesture(Action a) throws InterruptedException {
        final GesturePlan plan = GesturePlanner.plan(a, 1.0, 0, random, scaler(), svc.limits());
        if (plan == null) return true;
        final boolean[] ok = {false};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.scriptGesture(plan, new PlaybackEngine.GestureCallback() {
                @Override public void onResult(boolean completed) { ok[0] = completed; done.countDown(); }
            });
        } });
        lastTouchEnd = SystemClock.uptimeMillis();
        return ok[0];
    }

    @Override public boolean global(final int g) throws InterruptedException {
        onMain(new MainJob() { public void run(CountDownLatch done) { svc.performGlobalForScript(g); done.countDown(); } });
        lastTouchEnd = SystemClock.uptimeMillis();
        return true;
    }

    @Override public boolean openApp(final String pkg) throws InterruptedException {
        final boolean[] ok = {false};
        onMain(new MainJob() { public void run(CountDownLatch done) { ok[0] = svc.launchApp(pkg); done.countDown(); } });
        lastTouchEnd = SystemClock.uptimeMillis() + 500;
        return ok[0];
    }

    /**
     * A frame taken after the last touch settled (the app had time to react) and not older than one
     * sampling interval (a loop that only looks keeps seeing the screen as it is now).
     */
    private long notBefore() {
        return Math.max(lastTouchEnd + 150, SystemClock.uptimeMillis() - ScreenSampler.intervalMs() - 200);
    }

    @Override public int pixel(int dx, int dy) throws InterruptedException {
        if (!ScreenSampler.supported()) return -1;
        CoordinateScaler sc = scaler();
        final int x = Math.round(sc.x(dx)), y = Math.round(sc.y(dy));
        final int[] c = {-1};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.sampler().whenFrame(notBefore(), new ScreenSampler.FrameCallback() {
                @Override public void onFrame(ScreenFrame f) {
                    if (f != null && x >= 0 && y >= 0 && x < f.width && y < f.height) c[0] = f.argb[y * f.width + x] & 0xFFFFFF;
                    done.countDown();
                }
            });
        } });
        return c[0];
    }

    @Override public int[] grid(int dx1, int dy1, int dx2, int dy2, final int cols, final int rows) throws InterruptedException {
        if (!ScreenSampler.supported()) return null;
        CoordinateScaler sc = scaler();
        final int x1 = Math.round(sc.x(dx1)), y1 = Math.round(sc.y(dy1)), x2 = Math.round(sc.x(dx2)), y2 = Math.round(sc.y(dy2));
        final int[][] out = {null};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.sampler().whenFrame(notBefore(), new ScreenSampler.FrameCallback() {
                @Override public void onFrame(final ScreenFrame f) {
                    if (f != null) {
                        out[0] = com.freeclick.core.ColorZones.grid(new com.freeclick.core.ColorZones.Pixels() {
                            public int at(int x, int y) { return x < 0 || y < 0 || x >= f.width || y >= f.height ? -1 : f.argb[y * f.width + x] & 0xFFFFFF; }
                        }, x1, y1, x2, y2, cols, rows);
                    }
                    done.countDown();
                }
            });
        } });
        return out[0];
    }

    @Override public long zoneHash(int dx1, int dy1, int dx2, int dy2) throws InterruptedException {
        if (!ScreenSampler.supported()) return -1;
        CoordinateScaler sc = scaler();
        final int x1 = Math.round(sc.x(dx1)), y1 = Math.round(sc.y(dy1)), x2 = Math.round(sc.x(dx2)), y2 = Math.round(sc.y(dy2));
        final long[] h = {-1};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.sampler().whenFrame(notBefore(), new ScreenSampler.FrameCallback() {
                @Override public void onFrame(final ScreenFrame f) {
                    if (f != null) {
                        h[0] = com.freeclick.core.script.ScriptSimulator.hashOf(new com.freeclick.core.script.ScriptSimulator.Screen() {
                            public int width() { return f.width; }
                            public int height() { return f.height; }
                            public int pixel(int x, int y) { return x < 0 || y < 0 || x >= f.width || y >= f.height ? -1 : f.argb[y * f.width + x] & 0xFFFFFF; }
                            public String read(int a, int b, int c, int d, boolean n) { return null; }
                            public List<int[]> find(ImageSpec s, boolean all) { return null; }
                        }, x1, y1, x2, y2);
                    }
                    done.countDown();
                }
            });
        } });
        return h[0];
    }

    /** "salveazaCaptura()": what is on screen now (without FreeClick's windows) → Pictures/FreeClick. */
    @Override public boolean saveScreenshot(final String name) throws InterruptedException {
        if (!ScreenSampler.supported()) return false;
        final android.graphics.Bitmap[] got = {null};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.sampler().whenFrame(notBefore(), new ScreenSampler.FrameCallback() {
                @Override public void onFrame(ScreenFrame f) {
                    try {
                        if (f != null) got[0] = android.graphics.Bitmap.createBitmap(f.argb, f.width, f.height, android.graphics.Bitmap.Config.ARGB_8888);
                    } catch (OutOfMemoryError ignored) { }
                    done.countDown();
                }
            });
        } });
        if (got[0] == null) return false;
        String file = (name == null || name.trim().isEmpty() ? "freeclick" : name.trim().replaceAll("[^A-Za-z0-9_ .-]", "_"))
                + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".png";
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file);
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/FreeClick");
            }
            android.net.Uri uri = svc.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            java.io.OutputStream out = svc.getContentResolver().openOutputStream(uri);
            if (out == null) return false;
            try { got[0].compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); } finally { out.close(); }
            print("Captură salvată: Galerie → FreeClick → " + file, PlaybackEngine.PRINT_INFO);
            return true;
        } catch (Exception e) {
            print("Nu am putut salva captura: " + e.getMessage(), PlaybackEngine.PRINT_WARN);
            return false;
        }
    }

    @Override public void copyText(final String text) {
        main.post(new Runnable() { public void run() {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) svc.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("FreeClick", text));
        } });
    }

    @Override public boolean openUrl(final String url) throws InterruptedException {
        final boolean[] ok = {false};
        onMain(new MainJob() { public void run(CountDownLatch done) {
            try {
                svc.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url.trim()))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                ok[0] = true;
            } catch (RuntimeException e) {
                ok[0] = false;
            }
            done.countDown();
        } });
        lastTouchEnd = SystemClock.uptimeMillis() + 800;
        return ok[0];
    }

    @Override public String readText(int dx1, int dy1, int dx2, int dy2, boolean number) throws InterruptedException {
        if (!ScreenSampler.supported()) return null;
        CoordinateScaler sc = scaler();
        int x1 = Math.round(sc.x(dx1)), y1 = Math.round(sc.y(dy1)), x2 = Math.round(sc.x(dx2)), y2 = Math.round(sc.y(dy2));
        final android.graphics.Rect r = new android.graphics.Rect(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
        final Object[] got = new Object[3]; // crop, w, h
        final boolean[] prot = {false};
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.sampler().whenFrame(notBefore(), new ScreenSampler.FrameCallback() {
                @Override public void onFrame(ScreenFrame f) {
                    if (f != null) {
                        prot[0] = f.looksProtected(svc.statusBarHeight(), f.height - svc.navigationBarHeight());
                        int[] px = ClickService.crop(f, r);
                        if (px != null) {
                            got[0] = px;
                            got[1] = Math.min(r.right, f.width) - Math.max(0, r.left);
                            got[2] = Math.min(r.bottom, f.height) - Math.max(0, r.top);
                        }
                    }
                    done.countDown();
                }
            });
        } });
        if (prot[0]) return null;
        if (got[0] == null) return "";
        int w = (Integer) got[1], h = (Integer) got[2];
        TextReader.Result res = TextReader.read((int[]) got[0], w, h, 0, 0, w, h, number ? TextReader.MODE_NUMBER : TextReader.MODE_TEXT,
                App.get().letters().book());
        return res.text;
    }

    @Override public List<int[]> findImage(final ImageSpec spec, final boolean all) throws InterruptedException {
        if (!ScreenSampler.supported() || spec == null || !App.get().images().has(spec.id)) return null;
        final Object[] got = new Object[2]; // frame, query
        onMain(new MainJob() { public void run(final CountDownLatch done) {
            svc.sampler().whenFrame(notBefore(), new ScreenSampler.FrameCallback() {
                @Override public void onFrame(ScreenFrame f) {
                    if (f != null) {
                        ScreenFrame fr = svc.sampler().acquire(f);
                        got[0] = fr;
                        got[1] = svc.queryFor(spec, all, fr);
                    }
                    done.countDown();
                }
            });
        } });
        final ScreenFrame fr = (ScreenFrame) got[0];
        if (fr == null) return null;
        try {
            if (fr.looksProtected(svc.statusBarHeight(), fr.height - svc.navigationBarHeight())) return null;
            ImageTemplate t0 = App.get().images().template(spec.id);
            if (t0 == null) return null;
            double sx = spec.capScreenW > 0 ? fr.width / (double) spec.capScreenW : 1, sy = spec.capScreenH > 0 ? fr.height / (double) spec.capScreenH : 1;
            ImageMatcher.Result res = ImageMatcher.find(fr, t0.scaled(sx, sy), (ImageMatcher.Query) got[1]);
            List<int[]> out = new ArrayList<int[]>();
            CoordinateScaler sc = scaler(); // back to the script's own coordinates
            for (ImageMatcher.Match m : res.matches) {
                out.add(new int[]{(int) Math.round((m.x + spec.tapDx * sx) / sc.sx), (int) Math.round((m.y + spec.tapDy * sy) / sc.sy)});
            }
            return out;
        } finally {
            main.post(new Runnable() { public void run() { svc.sampler().releaseFrame(fr); } });
        }
    }

    @Override public void print(final String text, final int kind) {
        main.post(new Runnable() { public void run() { svc.print(text, kind); } });
    }

    @Override public void toast(final String text) {
        main.post(new Runnable() { public void run() { svc.toast(text); } });
    }

    @Override public void vibrate(long ms) {
        try {
            android.os.Vibrator v = (android.os.Vibrator) svc.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (RuntimeException ignored) { }
    }

    @Override public int screenWidth() { return design()[0]; }
    @Override public int screenHeight() { return design()[1]; }

    @Override public void runSteps(List<Action> steps, int times) throws InterruptedException {
        int[] d = design();
        final Macro m = new Macro(macro.name + " · pași");
        m.kind = Macro.KIND_MANUAL;
        m.screenWidth = d[0];
        m.screenHeight = d[1];
        m.settings.loops = Math.max(1, times);
        m.settings.scaleToScreen = true; // 1:1 on the phone it was made on
        for (Action a : steps) m.actions.add(a.copy()); // tree-ok: top-level list handed over by the script
        final CountDownLatch done = new CountDownLatch(1);
        stepsDone = done;
        main.post(new Runnable() { public void run() { svc.runStepsForScript(ScriptSession.this, m); } });
        try {
            done.await();
        } catch (InterruptedException e) {
            main.post(new Runnable() { public void run() { svc.stopStepsForScript(); } });
            throw e;
        } finally {
            stepsDone = null;
            lastTouchEnd = SystemClock.uptimeMillis();
        }
    }

    @Override public List<Action> stepsOf(String name) {
        for (Macro m : App.get().repo().list()) {
            if (!Macro.KIND_SCRIPT.equals(m.kind) && m.name.trim().equalsIgnoreCase(name.trim())) {
                List<Action> l = new ArrayList<Action>();
                for (Action a : m.actions) l.add(a.copy()); // tree-ok: top-level list of that item
                return l;
            }
        }
        return null;
    }

    private File keptFile() { return new File(new File(svc.getFilesDir(), "scriptvars"), MacroRepository.safeName(macro.id) + ".json"); }

    @Override public String loadKept() {
        try {
            File f = keptFile();
            return f.isFile() ? Letters.readAll(f) : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    @Override public void saveKept(String json) {
        try {
            File f = keptFile();
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(f.getPath() + ".tmp");
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            try { out.write(json.getBytes("UTF-8")); } finally { out.close(); }
            if (!tmp.renameTo(f)) { if (f.delete()) tmp.renameTo(f); }
        } catch (java.io.IOException ignored) { }
    }

    // ------------------------------------------------------------------ Listener (script thread → main)
    /** Started by a "run a script" step of a macro (the macro waits for it), not on its own. */
    boolean nested;

    @Override public void onState(final int state, final int line, final String why) {
        if (nested) return;
        main.post(new Runnable() { public void run() { svc.onScriptState(ScriptSession.this, state, line, why); } });
    }

    @Override public void onFinished(final String reason, final ScriptError error) {
        main.post(new Runnable() { public void run() {
            if (nested) svc.onNestedScriptFinished(ScriptSession.this, reason, error);
            else svc.onScriptFinished(ScriptSession.this, reason, error);
        } });
    }

    // ------------------------------------------------------------------ the debugger bar (main thread)
    void showDebugBar(int line, String why) {
        hideDebugBar();
        Context c = svc;
        LinearLayout bar = Ui.vertical(c);
        int p = Ui.dp(c, 10);
        bar.setPadding(p, p / 2, p, p / 2);
        bar.setBackground(Ui.rounded(0xF0140F45, Ui.dp(c, 14)));
        String head = (ScriptRunner.WHY_BREAKPOINT.equals(why) ? "● Oprit la linia " : "Linia ") + line;
        bar.addView(Ui.text(c, head, 13, Ui.LABEL, true));
        TextView code = Ui.text(c, lineText(line), 14, 0xFFFFFFFF, false);
        code.setTypeface(android.graphics.Typeface.MONOSPACE);
        code.setMaxLines(2);
        bar.addView(code);
        StringBuilder vars = new StringBuilder();
        int k = 0;
        for (java.util.Map.Entry<String, String> e : runner.variables().entrySet()) {
            if (k++ >= 6) { vars.append(" …"); break; }
            if (vars.length() > 0) vars.append(" · ");
            String v = e.getValue();
            vars.append(e.getKey()).append(" = ").append(v.length() > 24 ? v.substring(0, 23) + "…" : v);
        }
        if (vars.length() > 0) bar.addView(Ui.text(c, vars.toString(), 12, Ui.MUTED, false));
        LinearLayout row = Ui.horizontal(c);
        row.addView(button(c, "⏭ Pas", new View.OnClickListener() { public void onClick(View v) { hideDebugBar(); runner.step(); } }));
        row.addView(button(c, "▶ Continuă", new View.OnClickListener() { public void onClick(View v) { hideDebugBar(); runner.resume(); } }));
        row.addView(button(c, "■ Stop", new View.OnClickListener() { public void onClick(View v) { hideDebugBar(); svc.stopScript(); } }));
        bar.addView(row);
        WindowManager.LayoutParams lp = Overlays.params(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true, false);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = svc.statusBarHeight() + Ui.dp(c, 6);
        debugBar = bar;
        Overlays.safeAdd(svc.windowManager(), bar, lp);
    }

    void hideDebugBar() {
        if (debugBar != null) Overlays.safeRemove(svc.windowManager(), debugBar);
        debugBar = null;
    }

    private TextView button(Context c, String s, View.OnClickListener l) {
        TextView t = Ui.text(c, s, 14, Ui.ACCENT, true);
        int p = Ui.dp(c, 10);
        t.setPadding(p, p / 2, p, p / 2);
        Ui.clickable(t, l, Ui.dp(c, 8));
        return t;
    }
}
