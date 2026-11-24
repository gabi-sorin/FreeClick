package com.freeclick.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.freeclick.core.GesturePlan;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.Turbo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 🔥 Turbo: while it is on, the screen is yours, faster. A short tap becomes a burst of taps on that
 * exact spot, holding the finger taps there non-stop until you touch the screen again, and a swipe
 * goes through as a swipe. A thin bar at the top says what happens; × turns Turbo off.
 *
 * How (Android): a see-through layer catches your finger (the app below does not get it), then it
 * lets touches through and FreeClick taps that spot (up to 20 taps per gesture). Your next touch
 * cancels the taps in flight, which is exactly the "stop".
 */
final class TurboLayer {
    interface Host {
        void injectTurbo(GesturePlan plan, PlaybackEngine.GestureCallback cb);
        int maxStrokes();
        void onTurboChanged(boolean on);
    }

    private final Context ctx;
    private final WindowManager wm;
    private final Handler main;
    private final Host host;
    private final Prefs prefs;
    private final Random random = new Random();

    private CatchView catcher;
    private LinearLayout pill;
    private TextView hint;
    private RingView ring;
    private int gen;
    private boolean spamming;
    private long spamStart;
    private int done, target;
    private float sx, sy;

    TurboLayer(Context c, WindowManager wm, Handler main, Prefs prefs, Host host) {
        this.ctx = c;
        this.wm = wm;
        this.main = main;
        this.prefs = prefs;
        this.host = host;
    }

    boolean isOn() { return catcher != null; }

    void toggle() { if (isOn()) stop(); else start(); }

    void start() {
        if (isOn()) return;
        catcher = new CatchView(ctx);
        Overlays.safeAdd(wm, catcher, Overlays.params(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true, false));
        ring = new RingView(ctx);
        Overlays.safeAdd(wm, ring, Overlays.params(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, false, false));
        addPill();
        idleHint();
        host.onTurboChanged(true);
    }

    void stop() {
        gen++;
        spamming = false;
        if (catcher != null) Overlays.safeRemove(wm, catcher);
        if (ring != null) Overlays.safeRemove(wm, ring);
        if (pill != null) Overlays.safeRemove(wm, pill);
        catcher = null;
        ring = null;
        pill = null;
        host.onTurboChanged(false);
    }

    // ------------------------------------------------------------------ the bar at the top
    private void addPill() {
        pill = Ui.horizontal(ctx);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(ctx, 12);
        pill.setPadding(p, Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6));
        android.graphics.drawable.GradientDrawable bg = Ui.rounded(0xF0251A0A, Ui.dp(ctx, 22));
        bg.setStroke(Ui.dp(ctx, 2), 0xFFFF8A3D);
        pill.setBackground(bg);
        TextView title = Ui.text(ctx, "🔥 Turbo", 15, 0xFFFFB27A, true);
        pill.addView(title);
        hint = Ui.text(ctx, "", 13, 0xFFFFE7D6, false);
        hint.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 4), 0);
        hint.setMaxLines(2);
        pill.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = Ui.text(ctx, "×", 26, 0xFFFFFFFF, true);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Oprește Turbo");
        Ui.clickable(close, new View.OnClickListener() { public void onClick(View v) { stop(); } }, Ui.dp(ctx, 20));
        pill.addView(close, new LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)));
        android.graphics.Point sz = Overlays.screenSize(ctx);
        WindowManager.LayoutParams lp = Overlays.params(sz.x - 2 * Ui.dp(ctx, 10), ViewGroup.LayoutParams.WRAP_CONTENT, true, false);
        lp.x = Ui.dp(ctx, 10);
        lp.y = statusBar() + Ui.dp(ctx, 6);
        Overlays.safeAdd(wm, pill, lp);
    }

    private int statusBar() {
        int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : Ui.dp(ctx, 24);
    }

    private void idleHint() {
        if (hint == null) return;
        int n = prefs.turboBurst();
        hint.setText((n > 0 ? "atinge = " + n + " atingeri" : "atinge = o atingere")
                + (prefs.turboHold() ? " · ține apăsat = non-stop" : "") + " · " + prefs.turboRate() + "/s");
    }

    // ------------------------------------------------------------------ your finger
    private final class CatchView extends View {
        private float downX, downY;
        private long downAt;
        private final List<float[]> path = new ArrayList<float[]>();

        CatchView(Context c) {
            super(c);
            setBackgroundColor(0x0AFF8A3D); // a faint orange tint: Turbo is on
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    downAt = SystemClock.uptimeMillis();
                    path.clear();
                    path.add(new float[]{downX, downY, 0});
                    return true;
                case MotionEvent.ACTION_MOVE:
                    path.add(new float[]{e.getRawX(), e.getRawY(), SystemClock.uptimeMillis() - downAt});
                    return true;
                case MotionEvent.ACTION_UP: {
                    long held = SystemClock.uptimeMillis() - downAt;
                    path.add(new float[]{e.getRawX(), e.getRawY(), held});
                    int what = Turbo.classify(Math.hypot(e.getRawX() - downX, e.getRawY() - downY), Ui.dp(ctx, 14),
                            held, prefs.turboHold(), prefs.turboBurst());
                    if (what == Turbo.SWIPE) swipe(new ArrayList<float[]>(path));
                    else if (what == Turbo.HOLD) spam(downX, downY, -1);
                    else if (what == Turbo.BURST) spam(downX, downY, prefs.turboBurst());
                    else spam(downX, downY, 1);
                    return true;
                }
                default:
                    return true;
            }
        }
    }

    /** A swipe you made goes through as a swipe (Turbo does not block scrolling). */
    private void swipe(List<float[]> pts) {
        android.graphics.Point sz = Overlays.screenSize(ctx);
        GesturePlan plan = Turbo.swipe(pts, sz.x, sz.y);
        if (plan.strokes.isEmpty()) return;
        letThrough(true);
        final int my = ++gen;
        final GesturePlan go = plan;
        // give the window manager a moment to let touches through, or the swipe would land on our own layer
        main.postDelayed(new Runnable() { public void run() {
            if (my != gen) return;
            host.injectTurbo(go, new PlaybackEngine.GestureCallback() {
                @Override public void onResult(boolean completed) { if (my == gen) letThrough(false); }
            });
        } }, 60);
    }

    // ------------------------------------------------------------------ the taps
    /** {@code count} taps on (x, y); -1 = non-stop until you touch the screen (or the time limit). */
    private void spam(float x, float y, int count) {
        sx = x;
        sy = y;
        target = count;
        done = 0;
        spamming = true;
        spamStart = SystemClock.uptimeMillis();
        letThrough(true);
        ring.show(x, y);
        if (hint != null) hint.setText(count < 0 ? "non-stop · atinge ecranul ca să oprești" : count + " atingeri…");
        final int my = ++gen;
        main.postDelayed(new Runnable() { public void run() { batch(my); } }, 60); // see swipe()
    }

    private void batch(final int my) {
        if (my != gen || !spamming || catcher == null) return;
        int left = target < 0 ? Integer.MAX_VALUE : target - done;
        if (left <= 0 || (target < 0 && SystemClock.uptimeMillis() - spamStart > prefs.turboMaxSeconds() * 1000L)) { finish(); return; }
        android.graphics.Point sz = Overlays.screenSize(ctx);
        GesturePlan plan = Turbo.taps(sx, sy, left, prefs.turboRate(), prefs.turboSpread(), Math.min(20, host.maxStrokes()), sz.x, sz.y, random);
        final int n = plan.strokes.size();
        final int sent = n;
        host.injectTurbo(plan, new PlaybackEngine.GestureCallback() {
            @Override public void onResult(boolean completed) {
                if (my != gen) return;
                if (!completed) { finish(); return; } // your touch: stop
                done += sent;
                if (hint != null) hint.setText((target < 0 ? "non-stop · " : done + "/" + target + " · ")
                        + Turbo.measuredRate(done, SystemClock.uptimeMillis() - spamStart) + " atingeri/s · atinge ca să oprești");
                ring.pulse();
                batch(my);
            }
        });
    }

    private void finish() {
        spamming = false;
        if (ring != null) ring.hide();
        letThrough(false);
        main.postDelayed(new Runnable() { public void run() { if (!spamming) idleHint(); } }, 900);
    }

    /** While FreeClick taps, the catching layer lets everything through (also your stopping touch). */
    private void letThrough(boolean through) {
        if (catcher != null) Overlays.setTouchable(wm, catcher, !through);
    }

    // ------------------------------------------------------------------ the spot being tapped
    private static final class RingView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x, y, phase;
        private boolean on;

        RingView(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(c, 3));
            p.setColor(0xFFFF8A3D);
        }

        void show(float x, float y) {
            int[] me = new int[2];
            getLocationOnScreen(me);
            this.x = x - me[0];
            this.y = y - me[1];
            on = true;
            invalidate();
        }

        void pulse() { phase = (phase + 0.35f) % 1f; invalidate(); }

        void hide() { on = false; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            if (!on) return;
            float r = Ui.dp(getContext(), 18) + phase * Ui.dp(getContext(), 14);
            p.setAlpha((int) (255 * (1 - phase * 0.7f)));
            c.drawCircle(x, y, r, p);
            p.setAlpha(255);
            c.drawCircle(x, y, Ui.dp(getContext(), 6), p);
        }
    }
}
