package com.freeclick.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.freeclick.core.Action;
import com.freeclick.core.GestureRecorder;

import java.util.List;

/**
 * Record mode. A transparent full-screen layer catches your fingers (all of
 * them), the GestureRecorder turns each gesture into an Action, and the
 * service then replays that gesture on the app below so you can keep using
 * it while recording. While a gesture is being forwarded the layer lets
 * touches through and ignores events, so the forwarded touch can never be
 * recorded a second time.
 */
final class RecordingLayer {

    interface Callbacks { void onGestureRecorded(Action a); }

    private final Context ctx;
    private final WindowManager wm;
    private final Callbacks cb;
    private final GestureRecorder recorder;
    private CaptureView view;
    private boolean forwarding;

    RecordingLayer(Context ctx, WindowManager wm, Callbacks cb) {
        this.ctx = ctx;
        this.wm = wm;
        this.cb = cb;
        GestureRecorder.Config cfg = new GestureRecorder.Config();
        cfg.touchSlopPx = ViewConfiguration.get(ctx).getScaledTouchSlop();
        cfg.longPressMs = ViewConfiguration.getLongPressTimeout();
        recorder = new GestureRecorder(cfg);
    }

    boolean isActive() { return view != null; }

    int count() { return recorder.actions().size(); }

    void start() {
        if (view != null) return;
        recorder.start(SystemClock.uptimeMillis());
        view = new CaptureView(ctx);
        WindowManager.LayoutParams lp = Overlays.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true, false);
        Overlays.safeAdd(wm, view, lp);
        forwarding = false;
    }

    List<Action> stop() {
        List<Action> out = recorder.stop();
        Overlays.safeRemove(wm, view);
        view = null;
        forwarding = false;
        return out;
    }

    Action addGlobal(int global) { return recorder.addGlobal(global, SystemClock.uptimeMillis()); }

    /** Insert a step at this moment (see GestureRecorder.addStep). */
    Action addStep(Action a, long uptime) { return recorder.addStep(a, uptime); }

    /** The user spent this long in a FreeClick screen: not part of the recording. */
    void skipTime(long ms) { recorder.skipTime(ms); }

    /** Hide / show the "recording" layer without ending the recording (e.g. to cut out an image). */
    void setVisible(boolean v) { if (view != null) view.setVisibility(v ? View.VISIBLE : View.GONE); }

    /** While true the layer is pass-through and ignores events. */
    void setForwarding(boolean on) {
        forwarding = on;
        if (view != null) Overlays.setTouchable(wm, view, !on);
    }

    private final class CaptureView extends View {
        final Paint border = new Paint();
        final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        final SparseArray<Path> paths = new SparseArray<Path>();
        final int[] loc = new int[2];

        CaptureView(Context c) {
            super(c);
            border.setColor(0xCCDC2626);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Ui.dp(c, 4));
            trail.setColor(0xCCFFEB3B);
            trail.setStyle(Paint.Style.STROKE);
            trail.setStrokeWidth(Ui.dp(c, 5));
            trail.setStrokeCap(Paint.Cap.ROUND);
            trail.setStrokeJoin(Paint.Join.ROUND);
            label.setColor(0xFFDC2626);
            label.setTextSize(Ui.dp(c, 13));
            label.setFakeBoldText(true);
            label.setShadowLayer(Ui.dp(c, 2), 0, 0, 0xFFFFFFFF);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0x0FFF0000);
            float b = border.getStrokeWidth() / 2;
            c.drawRect(b, b, getWidth() - b, getHeight() - b, border);
            c.drawText("● ÎNREGISTRARE  (" + recorder.actions().size() + ")", Ui.dp(getContext(), 16),
                    Ui.dp(getContext(), 48), label);
            for (int i = 0; i < paths.size(); i++) c.drawPath(paths.valueAt(i), trail);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (forwarding) return true;
            getLocationOnScreen(loc);
            int action = e.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int i = e.getActionIndex();
                    int id = e.getPointerId(i);
                    float x = e.getX(i) + loc[0], y = e.getY(i) + loc[1];
                    recorder.pointerDown(id, x, y, e.getEventTime());
                    Path p = new Path();
                    p.moveTo(e.getX(i), e.getY(i));
                    paths.put(id, p);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (int h = 0; h < e.getHistorySize(); h++) {
                        for (int i = 0; i < e.getPointerCount(); i++) {
                            move(e.getPointerId(i), e.getHistoricalX(i, h), e.getHistoricalY(i, h),
                                    e.getHistoricalEventTime(h));
                        }
                    }
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        move(e.getPointerId(i), e.getX(i), e.getY(i), e.getEventTime());
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int i = e.getActionIndex();
                    Action a = recorder.pointerUp(e.getPointerId(i), e.getX(i) + loc[0], e.getY(i) + loc[1],
                            e.getEventTime());
                    if (a != null) {
                        paths.clear();
                        cb.onGestureRecorded(a);
                    }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    recorder.cancelGesture();
                    paths.clear();
                    break;
            }
            invalidate();
            return true;
        }

        private void move(int id, float lx, float ly, long t) {
            recorder.pointerMove(id, lx + loc[0], ly + loc[1], t);
            Path p = paths.get(id);
            if (p != null) p.lineTo(lx, ly);
        }
    }
}
