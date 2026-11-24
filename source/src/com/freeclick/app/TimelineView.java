package com.freeclick.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;

import java.util.List;
import java.util.Locale;

/**
 * "Cronologie": the steps of a recording on a time band. Each step is a block where it happens
 * (its pause before it is the empty space). Drag a block sideways to move it in time (everything
 * after moves with it); tap it to select it (a small screen shows where it touches); double-tap
 * to edit it. Two fingers zoom the time; one finger on empty space scrolls.
 */
final class TimelineView extends View {
    interface Listener {
        void onSelected(int index);
        /** A block was moved: its pause before it becomes {@code newDelay} (finger lifted). */
        void onDelayChanged(int index, long newDelay);
        void onEdit(int index);
    }

    private final Listener listener;
    private final float dp;
    private List<Action> steps;
    private int screenW = 1080, screenH = 2400;
    private int selected = -1;
    private float msPerPx = 10; // zoom
    private float scroll;      // px from the start
    private long[] start = new long[0], end = new long[0];

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaler;
    private final GestureDetector taps;

    TimelineView(Context c, Listener l) {
        super(c);
        listener = l;
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextSize(11 * dp);
        setBackgroundColor(Ui.BG);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float fx = d.getFocusX();
                float atMs = (fx + scroll) * msPerPx;
                msPerPx = Math.max(0.5f, Math.min(2000f, msPerPx / d.getScaleFactor()));
                scroll = Math.max(0, atMs / msPerPx - fx);
                invalidate();
                return true;
            }
        });
        taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                int i = blockAt(e.getX(), e.getY());
                selected = i;
                listener.onSelected(i);
                invalidate();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                int i = blockAt(e.getX(), e.getY());
                if (i >= 0) listener.onEdit(i);
                return true;
            }
        });
    }

    void setSteps(List<Action> list, int sw, int sh) {
        steps = list;
        if (sw > 0 && sh > 0) { screenW = sw; screenH = sh; }
        layoutTimes();
        if (start.length > 0 && getWidth() > 0 && msPerPx == 10) fitAll();
        invalidate();
    }

    void setSelected(int i) { selected = i; invalidate(); }

    /** Start / end time of every step at 1× (its pause before comes first). */
    private void layoutTimes() {
        int n = steps == null ? 0 : steps.size();
        start = new long[n];
        end = new long[n];
        long t = 0;
        for (int i = 0; i < n; i++) {
            Action a = steps.get(i);
            t += Math.max(0, a.delay);
            start[i] = t;
            long own = Math.max(0, a.ownDuration()) * Math.max(1, a.repeat) + Math.max(0, a.interval) * Math.max(0, a.repeat - 1);
            end[i] = t + own;
            t = end[i];
        }
    }

    private void fitAll() {
        long total = end.length == 0 ? 1000 : end[end.length - 1];
        msPerPx = Math.max(0.5f, (total + 500f) / Math.max(1, getWidth() - 40 * dp));
        scroll = 0;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (ow == 0) fitAll();
    }

    private float xOf(long ms) { return ms / msPerPx - scroll + 20 * dp; }

    private float bandTop() { return 30 * dp; }
    private float bandBottom() { return bandTop() + 64 * dp; }

    private int blockAt(float x, float y) {
        if (y < bandTop() - 10 * dp || y > bandBottom() + 10 * dp) return -1;
        int best = -1;
        float bestD = 24 * dp;
        for (int i = 0; i < start.length; i++) {
            float x1 = xOf(start[i]), x2 = Math.max(x1 + 6 * dp, xOf(end[i]));
            float d = x < x1 ? x1 - x : x > x2 ? x - x2 : 0;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    // ------------------------------------------------------------------ touch: drag a block / scroll
    private int dragging = -1;
    private float downX, lastX;
    private long dragStartDelay;
    private boolean moved, multi;

    @Override public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        taps.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = lastX = e.getX();
                moved = false;
                multi = false;
                dragging = blockAt(e.getX(), e.getY());
                if (dragging >= 0) dragStartDelay = steps.get(dragging).delay;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multi = true;
                if (dragging >= 0 && moved) listener.onDelayChanged(dragging, steps.get(dragging).delay);
                dragging = -1;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (multi) return true;
                if (Math.abs(e.getX() - downX) > 8 * dp) moved = true;
                if (!moved) return true;
                if (dragging >= 0) {
                    long d = Math.max(0, Math.round(dragStartDelay + (e.getX() - downX) * msPerPx));
                    steps.get(dragging).delay = d; // live; the editor records it for undo when the finger is lifted
                    layoutTimes();
                } else {
                    scroll = Math.max(0, scroll - (e.getX() - lastX));
                }
                lastX = e.getX();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging >= 0 && moved && !multi) {
                    long now = steps.get(dragging).delay;
                    steps.get(dragging).delay = dragStartDelay; // the editor applies it (undoable)
                    listener.onDelayChanged(dragging, now);
                }
                dragging = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging >= 0) steps.get(dragging).delay = dragStartDelay;
                dragging = -1;
                layoutTimes();
                invalidate();
                return true;
        }
        return true;
    }

    // ------------------------------------------------------------------ drawing
    @Override protected void onDraw(Canvas c) {
        float top = bandTop(), bottom = bandBottom();
        // time ruler
        long tick = niceTick(msPerPx * 90 * dp);
        text.setColor(Ui.LABEL);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setColor(0x337A78B8);
        stroke.setStrokeWidth(1);
        long first = Math.max(0, (long) ((scroll - 20 * dp) * msPerPx) / tick * tick);
        for (long t = first; xOf(t) < getWidth(); t += tick) {
            float x = xOf(t);
            c.drawLine(x, 18 * dp, x, bottom + 6 * dp, stroke);
            c.drawText(label(t), x, 13 * dp, text);
        }
        if (steps == null || steps.isEmpty()) {
            text.setColor(Ui.MUTED);
            c.drawText("Nimic de arătat", getWidth() / 2f, (top + bottom) / 2, text);
            return;
        }
        // blocks
        text.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < start.length; i++) {
            float x1 = xOf(start[i]), x2 = Math.max(x1 + 6 * dp, xOf(end[i]));
            if (x2 < 0 || x1 > getWidth()) continue;
            Action a = steps.get(i);
            int col = colour(a);
            fill.setColor(a.enabled ? col : 0x55888888);
            RectF r = new RectF(x1, top + 8 * dp, x2, bottom - 8 * dp);
            c.drawRoundRect(r, 5 * dp, 5 * dp, fill);
            if (i == selected) {
                stroke.setColor(0xFFFFE066);
                stroke.setStrokeWidth(3 * dp);
                c.drawRoundRect(r, 5 * dp, 5 * dp, stroke);
            }
            if (x2 - x1 > 18 * dp || i == selected) {
                text.setColor(Ui.BG);
                c.drawText(String.valueOf(i + 1), x1 + 4 * dp, top + 26 * dp, text);
            }
        }
        // what is selected: its name, time, and where on the screen it touches
        if (selected >= 0 && selected < steps.size()) {
            Action a = steps.get(selected);
            text.setColor(Ui.TEXT);
            String info = (selected + 1) + ". " + ActionFormatter.describe(a, null) + " · la " + label(start[selected])
                    + " · pauză înainte " + label(a.delay);
            c.drawText(ellipsize(info, getWidth() - 110 * dp), 8 * dp, bottom + 22 * dp, text);
            text.setColor(Ui.LABEL);
            c.drawText("Trage blocul ca să-l muți în timp · dublu-tap: editează", 8 * dp, bottom + 40 * dp, text);
            drawScreen(c, a);
        } else {
            text.setColor(Ui.LABEL);
            c.drawText("Atinge un bloc · două degete: zoom pe timp · trage pe gol: derulează", 8 * dp, bottom + 22 * dp, text);
        }
    }

    /** A small phone screen in the corner: where the selected step touches. */
    private void drawScreen(Canvas c, Action a) {
        float h = 70 * dp, w = h * screenW / (float) screenH;
        if (w > 90 * dp) { w = 90 * dp; h = w * screenH / (float) screenW; }
        float left = getWidth() - w - 8 * dp, top = bandBottom() + 6 * dp;
        RectF r = new RectF(left, top, left + w, top + h);
        fill.setColor(Ui.SURFACE);
        c.drawRoundRect(r, 4 * dp, 4 * dp, fill);
        stroke.setColor(Ui.SURFACE_HIGH);
        stroke.setStrokeWidth(1.5f * dp);
        c.drawRoundRect(r, 4 * dp, 4 * dp, stroke);
        if (!a.isGesture() && a.type != Action.Type.WAIT_COLOR && a.type != Action.Type.READ) return;
        float x = left + a.x / screenW * w, y = top + a.y / screenH * h;
        fill.setColor(0xFFFFE066);
        c.drawCircle(x, y, 3 * dp, fill);
        if (a.type == Action.Type.SWIPE) {
            stroke.setColor(0xFFFFE066);
            c.drawLine(x, y, left + a.x2 / screenW * w, top + a.y2 / screenH * h, stroke);
        }
    }

    private String ellipsize(String s, float max) {
        if (text.measureText(s) <= max) return s;
        while (s.length() > 3 && text.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static int colour(Action a) {
        switch (a.type) {
            case TAP: case DOUBLE_TAP: case LONG_PRESS: return Ui.ACCENT;
            case SWIPE: return 0xFFE8E6FF;
            case GESTURE: return 0xFFA78BFA;
            case WAIT: return Ui.LABEL;
            case IMAGE: case WAIT_COLOR: case READ: return 0xFFF5C26B;
            default: return 0xFF82AAFF;
        }
    }

    private static long niceTick(float ms) {
        long[] steps = {50, 100, 200, 500, 1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000, 600000};
        for (long s : steps) if (s >= ms) return s;
        return 1800000;
    }

    private static String label(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60000) return String.format(Locale.US, ms % 1000 == 0 ? "%.0f s" : "%.1f s", ms / 1000.0);
        return (ms / 60000) + " min " + (ms % 60000) / 1000 + " s";
    }
}
