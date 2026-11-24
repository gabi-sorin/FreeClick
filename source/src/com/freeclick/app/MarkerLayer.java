package com.freeclick.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.freeclick.core.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * Manual mode: numbered, draggable targets for every tap / long press /
 * double tap / swipe in the macro (swipes get a start and an end target
 * joined by an arrow). Each target is its own small window, so touches
 * anywhere else still reach the app underneath.
 */
final class MarkerLayer {

    interface Callbacks {
        void onMarkerTapped(Action a);
        void onMarkerMoved(Action a);
    }

    private final Context ctx;
    private final WindowManager wm;
    private final Callbacks cb;
    private final List<MarkerView> markers = new ArrayList<MarkerView>();
    private LinesView lines;
    private List<Action> actions = new ArrayList<Action>();
    private boolean interactive = true;
    private boolean visible = true;
    private final int size;

    MarkerLayer(Context ctx, WindowManager wm, Callbacks cb) {
        this.ctx = ctx;
        this.wm = wm;
        this.cb = cb;
        this.size = Ui.dp(ctx, 44);
    }

    /** Rebuild all markers from the action list (call after any structural change). */
    void sync(List<Action> list) {
        removeAll();
        actions = list;
        boolean anySwipe = false;
        for (Action a : list) if ((a.type == Action.Type.SWIPE || a.type == Action.Type.IMAGE || isZone(a)) && a.enabled) anySwipe = true;
        if (anySwipe) {
            lines = new LinesView(ctx);
            WindowManager.LayoutParams lp = Overlays.params(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, false, false);
            Overlays.safeAdd(wm, lines, lp);
        }
        int n = 0;
        for (Action a : list) {
            n++;
            if (!a.isMarkerEditable() || !a.enabled) continue;
            add(new MarkerView(ctx, a, n, false));
            if (a.type == Action.Type.SWIPE) add(new MarkerView(ctx, a, n, true));
        }
        applyVisibility();
    }

    private void add(MarkerView m) {
        WindowManager.LayoutParams lp = Overlays.params(size, size, interactive, false);
        lp.x = Math.round(m.cx() - size / 2f);
        lp.y = Math.round(m.cy() - size / 2f);
        markers.add(m);
        Overlays.safeAdd(wm, m, lp);
    }

    void removeAll() {
        for (MarkerView m : markers) Overlays.safeRemove(wm, m);
        markers.clear();
        Overlays.safeRemove(wm, lines);
        lines = null;
    }

    /** During playback/recording markers must never catch a touch. */
    void setInteractive(boolean on) {
        interactive = on;
        for (MarkerView m : markers) Overlays.setTouchable(wm, m, on);
    }

    void setVisible(boolean on) {
        visible = on;
        applyVisibility();
    }

    boolean isVisible() { return visible; }

    private void applyVisibility() {
        int v = visible ? View.VISIBLE : View.GONE;
        for (MarkerView m : markers) m.setVisibility(v);
        if (lines != null) lines.setVisibility(v);
    }

    /** Image steps keep their spec in capture-screen pixels; this maps them to the current screen. */
    private float sx(Action a) {
        android.graphics.Point p = Overlays.screenSize(ctx);
        return a.img != null && a.img.capScreenW > 0 ? p.x / (float) a.img.capScreenW : 1f;
    }

    private float sy(Action a) {
        android.graphics.Point p = Overlays.screenSize(ctx);
        return a.img != null && a.img.capScreenH > 0 ? p.y / (float) a.img.capScreenH : 1f;
    }

    /** A read step that reads a zone (not a single point). */
    static boolean isZone(Action a) { return a.type == Action.Type.READ && a.readMode != Action.READ_COLOR; }

    static final int READ_COLOUR = 0xFFF5C542;

    /** Dark navy disc with a white ring; colour conditions get a ring in the awaited colour. */
    private static int ringFor(Action a) {
        if (a.type == Action.Type.IMAGE) return Ui.ACCENT;
        if (a.type == Action.Type.READ) return READ_COLOUR;
        return a.type == Action.Type.WAIT_COLOR ? 0xFF000000 | (a.color & 0xFFFFFF) : 0xFFFFFFFF;
    }

    // ------------------------------------------------------------------ views
    private final class MarkerView extends View {
        final Action action;
        final int number;
        final boolean isEnd;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        float downRawX, downRawY;
        int startX, startY;
        boolean dragging;
        final int slop;

        MarkerView(Context c, Action a, int number, boolean isEnd) {
            super(c);
            this.action = a;
            this.number = number;
            this.isEnd = isEnd;
            fill.setColor(0xEB1A1550);
            stroke.setColor(ringFor(a));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Ui.dp(c, a.type == Action.Type.WAIT_COLOR ? 4 : 2.5f));
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(Ui.dp(c, 13));
            text.setShadowLayer(Ui.dp(c, 2), 0, 0, 0xFF000000); // readable on any marker colour
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setContentDescription("Țintă " + number);
        }

        float cx() {
            if (action.type == Action.Type.IMAGE && action.img != null) return (action.img.capX + action.img.tapDx) * sx(action);
            if (isZone(action)) return (action.x + action.x2) / 2f;
            return isEnd ? action.x2 : action.x;
        }

        float cy() {
            if (action.type == Action.Type.IMAGE && action.img != null) return (action.img.capY + action.img.tapDy) * sy(action);
            if (isZone(action)) return (action.y + action.y2) / 2f;
            return isEnd ? action.y2 : action.y;
        }

        @Override protected void onDraw(Canvas c) {
            float r = getWidth() / 2f;
            float rr = r - stroke.getStrokeWidth() / 2f - 1;
            c.drawCircle(r, r, rr, fill);
            c.drawCircle(r, r, rr, stroke);
            String label;
            switch (action.type) {
                case SWIPE: label = (isEnd ? "E" : "S") + number; break;
                case LONG_PRESS: label = number + "L"; break;
                case DOUBLE_TAP: label = number + "×2"; break;
                case WAIT_COLOR: label = "C" + number; break;
                case IMAGE: label = number + "🖼"; break;
                case READ: label = (action.readMode == Action.READ_COLOR ? "🎨" : "📖") + number; break;
                default: label = String.valueOf(number);
            }
            if (action.repeat > 1) label += "·" + action.repeat;
            float ty = r - (text.descent() + text.ascent()) / 2f; // vertically centred
            c.drawText(label, r, ty, text);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX(); downRawY = e.getRawY();
                    startX = lp.x; startY = lp.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                    if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                    if (dragging) {
                        lp.x = startX + Math.round(dx);
                        lp.y = startY + Math.round(dy);
                        Overlays.safeUpdate(wm, this, lp);
                        float nx = Math.max(0, lp.x + size / 2f), ny = Math.max(0, lp.y + size / 2f);
                        if (action.type == Action.Type.IMAGE && action.img != null) {
                            // the cross is where it taps: relative to the image, in capture pixels
                            action.img.tapDx = nx / sx(action) - action.img.capX;
                            action.img.tapDy = ny / sy(action) - action.img.capY;
                            action.x = action.img.capX + action.img.tapDx;
                            action.y = action.img.capY + action.img.tapDy;
                        } else if (isZone(action)) {
                            // the handle sits in the middle of the zone: the whole zone moves with it
                            float hw = (action.x2 - action.x) / 2f, hh = (action.y2 - action.y) / 2f;
                            action.x = Math.max(0, nx - hw); action.y = Math.max(0, ny - hh);
                            action.x2 = action.x + 2 * hw; action.y2 = action.y + 2 * hh;
                        } else if (isEnd) { action.x2 = nx; action.y2 = ny; } else { action.x = nx; action.y = ny; }
                        if (lines != null) lines.invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging) cb.onMarkerMoved(action); else cb.onMarkerTapped(action);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /** Full-screen, non-touchable layer that draws swipe arrows and the outlines of image steps. */
    private final class LinesView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint zone = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path arrow = new Path();

        LinesView(Context c) {
            super(c);
            p.setColor(0xB3FFFFFF);
            p.setStrokeWidth(Ui.dp(c, 2.5f));
            p.setStyle(Paint.Style.STROKE);
            p.setShadowLayer(Ui.dp(c, 2), 0, 0, 0x99000000);
            img.setColor(Ui.ACCENT);
            img.setStrokeWidth(Ui.dp(c, 2));
            img.setStyle(Paint.Style.STROKE);
            img.setPathEffect(new android.graphics.DashPathEffect(new float[]{Ui.dp(c, 8), Ui.dp(c, 5)}, 0));
            img.setShadowLayer(Ui.dp(c, 2), 0, 0, 0x99000000);
            zone.setColor(0x99FFFFFF);
            zone.setStrokeWidth(Ui.dp(c, 1.5f));
            zone.setStyle(Paint.Style.STROKE);
            zone.setPathEffect(new android.graphics.DashPathEffect(new float[]{Ui.dp(c, 4), Ui.dp(c, 6)}, 0));
            read.setColor(READ_COLOUR);
            read.setStrokeWidth(Ui.dp(c, 2));
            read.setStyle(Paint.Style.STROKE);
            read.setPathEffect(new android.graphics.DashPathEffect(new float[]{Ui.dp(c, 6), Ui.dp(c, 4)}, 0));
            read.setShadowLayer(Ui.dp(c, 2), 0, 0, 0x99000000);
            readText.setColor(READ_COLOUR);
            readText.setTextSize(Ui.dp(c, 12));
            readText.setFakeBoldText(true);
            readText.setShadowLayer(Ui.dp(c, 2), 0, 0, 0xFF000000);
        }

        final Paint read = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint readText = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override protected void onDraw(Canvas c) {
            int[] loc = new int[2];
            getLocationOnScreen(loc); // convert screen coords to this view
            for (Action a : actions) {
                if (a.type != Action.Type.IMAGE || !a.enabled || a.img == null) continue;
                float kx = sx(a), ky = sy(a);
                c.drawRoundRect(a.img.capX * kx - loc[0], a.img.capY * ky - loc[1], (a.img.capX + a.img.w) * kx - loc[0],
                        (a.img.capY + a.img.h) * ky - loc[1], Ui.dp(getContext(), 6), Ui.dp(getContext(), 6), img);
                if (a.img.hasZone()) {
                    c.drawRect(a.img.zx1 * kx - loc[0], a.img.zy1 * ky - loc[1], a.img.zx2 * kx - loc[0], a.img.zy2 * ky - loc[1], zone);
                }
            }
            for (Action a : actions) {
                if (!isZone(a) || !a.enabled) continue;
                c.drawRect(a.x - loc[0], a.y - loc[1], a.x2 - loc[0], a.y2 - loc[1], read);
                String name = a.varName == null || a.varName.isEmpty() ? "citire" : a.varName;
                c.drawText("📖 " + name, a.x - loc[0], a.y - loc[1] - Ui.dp(getContext(), 4), readText);
            }
            for (Action a : actions) {
                if (a.type != Action.Type.SWIPE || !a.enabled) continue;
                float x1 = a.x - loc[0], y1 = a.y - loc[1], x2 = a.x2 - loc[0], y2 = a.y2 - loc[1];
                c.drawLine(x1, y1, x2, y2, p);
                double ang = Math.atan2(y2 - y1, x2 - x1);
                float len = Ui.dp(getContext(), 16);
                arrow.reset();
                arrow.moveTo((float) (x2 - len * Math.cos(ang - 0.5)), (float) (y2 - len * Math.sin(ang - 0.5)));
                arrow.lineTo(x2, y2);
                arrow.lineTo((float) (x2 - len * Math.cos(ang + 0.5)), (float) (y2 - len * Math.sin(ang + 0.5)));
                c.drawPath(arrow, p);
            }
        }
    }
}
