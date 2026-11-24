package com.freeclick.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.freeclick.core.GesturePlan;
import com.freeclick.core.PlaybackEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🧪 The Laboratory (2.5): measures, on THIS phone, what FreeClick's touches really do. FreeClick sends test
 * touches onto the grey surface and the surface writes down what arrived: taps, swipes, two fingers at
 * once, free gestures, the listening layer that passes touches on, and the fastest rhythm. Every result
 * is "arrived right / sent", so any phone (any maker, Android 7+) can be judged by the same 99% rule.
 */
public final class LabActivity extends Activity {
    private Surface surface;
    private LinearLayout results;
    private TextView status;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private boolean running;
    private final StringBuilder report = new StringBuilder();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        LinearLayout root = Ui.vertical(this);
        root.addView(Ui.header(this, "🧪 Laborator", false, false, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }, null));
        LinearLayout body = Ui.vertical(this);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        body.addView(Ui.muted(this, "FreeClick trimite singur atingeri de test pe pătratul gri și numără ce a ajuns corect, "
                + "pe telefonul ăsta. Durează ~1 minut. Nu atinge ecranul cât rulează (atingerea ta oprește încercarea în curs)."));
        LinearLayout row = Ui.horizontal(this);
        row.addView(Ui.colored(this, "Pornește", Ui.ACCENT, new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        }));
        row.addView(Ui.button(this, "📋 Copiază rezultatele", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FreeClick Laborator", report.toString()));
                Toast.makeText(LabActivity.this, "Copiat", Toast.LENGTH_SHORT).show();
            }
        }));
        body.addView(row);
        status = Ui.text(this, "", 14, Ui.ACCENT, true);
        body.addView(status);
        results = Ui.vertical(this);
        body.addView(results);
        root.addView(body);
        surface = new Surface(this);
        surface.setBackground(Ui.surface(this, Ui.SURFACE, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lp.setMargins(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 16));
        root.addView(surface, lp);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        ClickService s = ClickService.get();
        if (s != null) s.labMode(true);
    }

    @Override protected void onPause() {
        super.onPause();
        running = false;
        ClickService s = ClickService.get();
        if (s != null) { s.labLayer(false); s.labMode(false); }
    }

    // ------------------------------------------------------------------ what arrives on the surface
    /** One finished touch on the surface: fingers, first and last point (screen px), duration, when it began. */
    static final class Got { int fingers; float x1, y1, x2, y2; long down, up; }

    private final class Surface extends View {
        final List<Got> got = new ArrayList<Got>();
        private Got cur;
        private final int[] loc = new int[2];
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lx = -1, ly = -1;

        Surface(Context c) { super(c); p.setColor(Ui.ACCENT); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            getLocationOnScreen(loc);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                cur = new Got();
                cur.x1 = e.getX() + loc[0];
                cur.y1 = e.getY() + loc[1];
                cur.down = SystemClock.uptimeMillis();
            }
            if (cur != null) {
                cur.fingers = Math.max(cur.fingers, e.getPointerCount());
                cur.x2 = e.getX(0) + loc[0];
                cur.y2 = e.getY(0) + loc[1];
                lx = e.getX(0);
                ly = e.getY(0);
            }
            if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && cur != null) {
                cur.up = SystemClock.uptimeMillis();
                if (a == MotionEvent.ACTION_UP) got.add(cur);
                cur = null;
            }
            invalidate();
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            if (lx >= 0) c.drawCircle(lx, ly, Ui.dp(getContext(), 10), p);
        }

        /** A point on the surface, away from its edges (screen px). */
        float[] randomPoint() {
            getLocationOnScreen(loc);
            int m = Ui.dp(getContext(), 24);
            return new float[]{loc[0] + m + rnd.nextInt(Math.max(1, getWidth() - 2 * m)), loc[1] + m + rnd.nextInt(Math.max(1, getHeight() - 2 * m))};
        }
    }

    // ------------------------------------------------------------------ the tests
    private interface Trial {
        /** The gesture to send and what must arrive. */
        GesturePlan plan(float[] at);
        boolean ok(Got g, float[] at);
    }

    private static final class Test {
        final String name;
        final int count;
        final Trial trial;
        final boolean layer;
        int good, sent;
        long latencySum;
        Test(String name, int count, boolean layer, Trial t) { this.name = name; this.count = count; this.layer = layer; this.trial = t; }
    }

    private final List<Test> tests = new ArrayList<Test>();
    private int ti, k;

    private static GesturePlan.PlannedStroke stroke(long start, long dur, float[]... pts) {
        GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(start, dur);
        for (float[] p : pts) s.points.add(p);
        return s;
    }

    private static boolean near(float ax, float ay, float bx, float by, float px) { return Math.hypot(ax - bx, ay - by) <= px; }

    private void start() {
        if (running) return;
        if (ClickService.get() == null) { Toast.makeText(this, "Pornește întâi FreeClick (serviciul de accesibilitate).", Toast.LENGTH_LONG).show(); return; }
        final float tol = Ui.dp(this, 4);
        tests.clear();
        tests.add(new Test("Atingeri", 100, false, new Trial() {
            public GesturePlan plan(float[] at) { GesturePlan g = new GesturePlan(); g.strokes.add(stroke(0, 40, at)); return g; }
            public boolean ok(Got g, float[] at) { return g.fingers == 1 && near(g.x1, g.y1, at[0], at[1], tol); }
        }));
        tests.add(new Test("Swipe-uri", 40, false, new Trial() {
            public GesturePlan plan(float[] at) {
                GesturePlan g = new GesturePlan();
                float[] end = {at[0], Math.max(at[1] - Ui.dp(LabActivity.this, 120), surfaceTop())};
                at = new float[]{at[0], at[1], end[0], end[1]};
                g.strokes.add(stroke(0, 250, new float[]{at[0], at[1]}, end));
                return g;
            }
            public boolean ok(Got g, float[] at) {
                return g.fingers == 1 && near(g.x1, g.y1, at[0], at[1], tol)
                        && near(g.x2, g.y2, at[0], Math.max(at[1] - Ui.dp(LabActivity.this, 120), surfaceTop()), tol * 3);
            }
        }));
        tests.add(new Test("Două degete deodată", 30, false, new Trial() {
            public GesturePlan plan(float[] at) {
                GesturePlan g = new GesturePlan();
                g.strokes.add(stroke(0, 60, at));
                g.strokes.add(stroke(0, 60, new float[]{Math.max(surfaceLeft(), at[0] - Ui.dp(LabActivity.this, 80)), at[1]}));
                return g;
            }
            public boolean ok(Got g, float[] at) { return g.fingers == 2; }
        }));
        tests.add(new Test("Gest liber (curbă)", 20, false, new Trial() {
            public GesturePlan plan(float[] at) {
                GesturePlan g = new GesturePlan();
                GesturePlan.PlannedStroke s = new GesturePlan.PlannedStroke(0, 400);
                for (int i = 0; i <= 12; i++) {
                    double u = i / 12.0;
                    s.points.add(new float[]{at[0] + (float) (Math.sin(u * Math.PI) * Ui.dp(LabActivity.this, 40)), at[1] - (float) (u * Ui.dp(LabActivity.this, 60))});
                }
                g.strokes.add(s);
                return g;
            }
            public boolean ok(Got g, float[] at) {
                return g.fingers == 1 && near(g.x1, g.y1, at[0], at[1], tol) && near(g.x2, g.y2, at[0], at[1] - Ui.dp(LabActivity.this, 60), tol * 3);
            }
        }));
        tests.add(new Test("Stratul care trece atingerile mai departe", 40, true, new Trial() {
            public GesturePlan plan(float[] at) { GesturePlan g = new GesturePlan(); g.strokes.add(stroke(0, 50, at)); return g; }
            public boolean ok(Got g, float[] at) { return g.fingers == 1 && near(g.x1, g.y1, at[0], at[1], tol * 2); }
        }));
        report.setLength(0);
        report.append("FreeClick Laborator · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE).append('\n');
        results.removeAllViews();
        running = true;
        ti = 0;
        k = 0;
        h.postDelayed(next, 600);
    }

    private float surfaceTop() { int[] l = new int[2]; surface.getLocationOnScreen(l); return l[1] + Ui.dp(this, 8); }
    private float surfaceLeft() { int[] l = new int[2]; surface.getLocationOnScreen(l); return l[0] + Ui.dp(this, 8); }

    private final Runnable next = new Runnable() {
        @Override public void run() {
            if (!running) return;
            final ClickService s = ClickService.get();
            if (s == null) { stop("FreeClick s-a oprit"); return; }
            if (ti >= tests.size()) { rhythm(s); return; }
            final Test t = tests.get(ti);
            if (k == 0) s.labLayer(t.layer);
            if (k >= t.count) { done(t); s.labLayer(false); ti++; k = 0; h.postDelayed(this, 400); return; }
            status.setText(t.name + " " + (k + 1) + " / " + t.count);
            final float[] at = surface.randomPoint();
            final int before = surface.got.size();
            final long sentAt = SystemClock.uptimeMillis();
            t.sent++;
            s.labInject(t.trial.plan(at), new PlaybackEngine.GestureCallback() {
                @Override public void onResult(boolean completed) { }
            });
            h.postDelayed(new Runnable() { public void run() {
                if (!running) return;
                if (surface.got.size() > before) {
                    Got g = surface.got.get(before);
                    if (t.trial.ok(g, at)) { t.good++; t.latencySum += Math.max(0, g.down - sentAt); }
                }
                k++;
                h.postDelayed(next, 80);
            } }, t.layer ? 900 : 650);
        }
    };

    /** The fastest rhythm: 200 taps sent 20 at a time (Android's limit per gesture), counted as they arrive. */
    private void rhythm(final ClickService s) {
        status.setText("Ritm maxim…");
        final int before = surface.got.size();
        final long t0 = SystemClock.uptimeMillis();
        final int[] batches = {0};
        final float[] at = surface.randomPoint();
        Runnable batch = new Runnable() { public void run() {
            if (!running) return;
            if (batches[0] >= 10) {
                h.postDelayed(new Runnable() { public void run() {
                    int n = surface.got.size() - before;
                    long ms = Math.max(1, SystemClock.uptimeMillis() - t0 - 500);
                    line("Ritm maxim", n + " / 200 atingeri ajunse · ~" + Math.round(n * 1000.0 / ms) + " pe secundă", n >= 198);
                    stop("Gata.");
                } }, 500);
                return;
            }
            batches[0]++;
            GesturePlan g = new GesturePlan();
            for (int i = 0; i < 20; i++) g.strokes.add(stroke(i * 17L, 8, at));
            final Runnable self = this;
            s.labInject(g, new PlaybackEngine.GestureCallback() {
                @Override public void onResult(boolean completed) { h.post(self); }
            });
        } };
        h.post(batch);
    }

    private void done(Test t) {
        double pc = t.sent == 0 ? 0 : t.good * 100.0 / t.sent;
        String extra = t.good > 0 ? " · întârziere medie " + (t.latencySum / t.good) + " ms" : "";
        line(t.name, t.good + " / " + t.sent + String.format(java.util.Locale.US, " (%.1f%%)", pc) + extra, pc >= 99);
    }

    private void line(String name, String value, boolean good) {
        TextView v = Ui.text(this, (good ? "✅ " : "⚠ ") + name + ": " + value, 14, good ? Ui.TEXT : 0xFFF5C26B, false);
        v.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        results.addView(v);
        report.append(good ? "OK  " : "!!  ").append(name).append(": ").append(value).append('\n');
    }

    private void stop(String why) {
        running = false;
        status.setText(why);
        ClickService s = ClickService.get();
        if (s != null) s.labLayer(false);
    }
}
