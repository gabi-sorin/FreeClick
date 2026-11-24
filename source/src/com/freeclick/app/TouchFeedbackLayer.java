package com.freeclick.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.freeclick.core.GesturePlan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Non-touchable layer that briefly draws where each injected gesture lands.
 * In preview it draws "ghost fingers" that move along each path in real time, with a
 * caption, and short labels for steps that are not touches (Back, open app...).
 */
final class TouchFeedbackLayer {
    private static final long FADE_MS = 450;

    private final Context ctx;
    private final WindowManager wm;
    private FeedbackView view;

    private static final class Item {
        final GesturePlan plan; final long start; final boolean ghost; final String caption;
        Item(GesturePlan p, long s, boolean ghost, String caption) { plan = p; start = s; this.ghost = ghost; this.caption = caption; }
    }

    private static final long LABEL_MS = 1400;

    TouchFeedbackLayer(Context ctx, WindowManager wm) {
        this.ctx = ctx;
        this.wm = wm;
    }

    void show(GesturePlan plan) { add(new Item(plan, SystemClock.uptimeMillis(), false, null)); }

    /** Preview: a finger moving along the gesture, with a caption ("3/12 · Apăsare"). */
    void showGhost(GesturePlan plan, String caption) { add(new Item(plan, SystemClock.uptimeMillis(), true, caption)); }

    /** Preview: a step that is not a touch ("◀ Înapoi", "Deschide aplicația …"). */
    void showLabel(String text) {
        ensureView();
        view.label = text;
        view.labelAt = SystemClock.uptimeMillis();
        view.postInvalidateOnAnimation();
    }

    private void add(Item item) {
        ensureView();
        view.items.add(item);
        view.postInvalidateOnAnimation();
    }

    private void ensureView() {
        if (view != null) return;
        view = new FeedbackView(ctx);
        WindowManager.LayoutParams lp = Overlays.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, false, false);
        Overlays.safeAdd(wm, view, lp);
    }

    void hide() {
        Overlays.safeRemove(wm, view);
        view = null;
    }

    private static final class FeedbackView extends View {
        final List<Item> items = new ArrayList<Item>();
        final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final int[] loc = new int[2];
        final float radius;
        final Paint ghostFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF box = new android.graphics.RectF();
        String label;
        long labelAt;

        FeedbackView(Context c) {
            super(c);
            radius = Ui.dp(c, 22);
            ghostFill.setStyle(Paint.Style.FILL);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(Ui.dp(c, 14));
            text.setFakeBoldText(true);
            pill.setColor(0xE6140F45);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(Ui.dp(c, 3));
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Ui.dp(c, 4));
            line.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            getLocationOnScreen(loc);
            Iterator<Item> it = items.iterator();
            while (it.hasNext()) {
                Item item = it.next();
                long age = now - item.start;
                long life = item.plan.totalDuration() + FADE_MS;
                if (age > life) { it.remove(); continue; }
                float fade = age < item.plan.totalDuration() ? 1f
                        : 1f - (age - item.plan.totalDuration()) / (float) FADE_MS;
                int alpha = (int) (200 * Math.max(0, fade));
                if (item.ghost) { drawGhost(c, item, age, alpha); continue; }
                dot.setColor((alpha << 24) | 0x00FF5722);
                line.setColor((alpha << 24) | 0x00FFC107);
                for (GesturePlan.PlannedStroke s : item.plan.strokes) {
                    float[] first = s.points.get(0);
                    float grow = Math.min(1f, age / 200f);
                    c.drawCircle(first[0] - loc[0], first[1] - loc[1], radius * (0.5f + 0.5f * grow), dot);
                    if (s.points.size() > 1) {
                        path.reset();
                        path.moveTo(first[0] - loc[0], first[1] - loc[1]);
                        for (int i = 1; i < s.points.size(); i++) {
                            float[] p = s.points.get(i);
                            path.lineTo(p[0] - loc[0], p[1] - loc[1]);
                        }
                        c.drawPath(path, line);
                    }
                }
            }
            if (label != null) {
                long age = now - labelAt;
                if (age > LABEL_MS) label = null;
                else drawPill(c, label, getWidth() / 2f, getHeight() * 0.42f);
            }
            if (!items.isEmpty() || label != null) postInvalidateOnAnimation();
        }

        /** A translucent finger that moves along each stroke at the gesture's real speed. */
        private void drawGhost(Canvas c, Item item, long age, int alpha) {
            line.setColor((alpha / 2 << 24) | 0x002DD4BF);
            ghostFill.setColor((alpha << 24) | 0x002DD4BF);
            float[] captionAt = null;
            for (GesturePlan.PlannedStroke s : item.plan.strokes) {
                float[] first = s.points.get(0);
                if (captionAt == null) captionAt = first;
                if (s.points.size() > 1) {
                    path.reset();
                    path.moveTo(first[0] - loc[0], first[1] - loc[1]);
                    for (int i = 1; i < s.points.size(); i++) path.lineTo(s.points.get(i)[0] - loc[0], s.points.get(i)[1] - loc[1]);
                    c.drawPath(path, line);
                }
                long t = age - s.startTime;
                if (t < 0) continue;
                float f = s.duration <= 0 ? 1f : Math.min(1f, t / (float) s.duration);
                float idx = f * (s.points.size() - 1);
                int i0 = (int) Math.floor(idx), i1 = Math.min(s.points.size() - 1, i0 + 1);
                float k = idx - i0;
                float x = s.points.get(i0)[0] + (s.points.get(i1)[0] - s.points.get(i0)[0]) * k;
                float y = s.points.get(i0)[1] + (s.points.get(i1)[1] - s.points.get(i0)[1]) * k;
                boolean pressed = t <= s.duration;
                c.drawCircle(x - loc[0], y - loc[1], radius * (pressed ? 0.8f : 0.55f), ghostFill);
            }
            if (item.caption != null && captionAt != null && alpha > 60) {
                drawPill(c, item.caption, captionAt[0] - loc[0], captionAt[1] - loc[1] - radius * 2.2f);
            }
        }

        private void drawPill(Canvas c, String s, float cx, float cy) {
            float w = text.measureText(s), pad = Ui.dp(getContext(), 10), h = text.getTextSize() * 1.9f;
            float left = Math.max(pad, Math.min(getWidth() - w - pad * 3, cx - w / 2 - pad));
            float top = Math.max(pad, cy - h / 2);
            box.set(left, top, left + w + pad * 2, top + h);
            c.drawRoundRect(box, h / 2, h / 2, pill);
            c.drawText(s, left + pad, top + h / 2 + text.getTextSize() * 0.35f, text);
        }
    }
}
