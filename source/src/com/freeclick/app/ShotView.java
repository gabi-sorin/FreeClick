package com.freeclick.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.freeclick.core.ColorMatch;
import com.freeclick.core.script.CodeMarks;
import com.freeclick.core.script.ScriptSimulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The code editor's picture pane: a screenshot of the game (zoom with two fingers, move, double tap
 * to fit), the places named in the code drawn on it (drag one → its numbers change in the code),
 * tools that write code from what you choose on it (with a magnifier), and the simulated touches.
 *
 * Three coordinate spaces: the view (what you see, zoomed), the picture's pixels, and the script's
 * own coordinates (the screen the script is made for). The code only ever sees the last one, so a
 * touch placed on the small picture lands on the same spot of the real game.
 */
final class ShotView extends View {
    static final int TOOL_NONE = 0, TOOL_POINT = 1, TOOL_SWIPE = 2, TOOL_ZONE = 3, TOOL_COLOR = 4, TOOL_IMAGE = 5, TOOL_TAPZONE = 6,
            TOOL_MODEL = 7;
    /** Category bits after CodeMarks.TOUCH … IMAGE. */
    static final int CAT_STEPS = 5, CAT_SIM = 6;

    interface Listener {
        /** A tool finished; values in the script's coordinates (point: x, y; swipe / zone / image: x1, y1, x2, y2). */
        void onPicked(int tool, int[] v, int rgb);
        /** A mark is dragged; done = the finger was lifted. */
        void onMarkMoved(int index, int[] v, boolean done);
        void onMarkTapped(int index);
        /** The one-shot tool went back to "none". */
        void onToolEnded();
    }

    private final Listener listener;
    private final float dp;
    private Bitmap bmp;
    private int designW = 1, designH = 1;
    private List<CodeMarks.Mark> marks = new ArrayList<CodeMarks.Mark>();
    private int selected = -1;
    private boolean showAll = true;
    private int mask = 0xFF;
    private List<float[]> steps = new ArrayList<float[]>(); // {x, y, x2, y2, swipe?1:0, index}
    private List<ScriptSimulator.Touch> sim;
    private int simUpTo = -1;
    private Map<String, List<int[]>> imageHits = new HashMap<String, List<int[]>>();
    private String zoneText;
    private Bitmap colorMask;
    private int tool = TOOL_NONE;
    /** Pixels chosen so far for a pattern (x, y, rgb), not yet written. */
    private List<int[]> pending = new ArrayList<int[]>();
    private String emptyText = "";

    // view transform: view = bitmap * scale + t
    private float scale = 1, tx, ty, fit = 1, zoom = 1;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint pixelPaint = new Paint(); // magnifier: crisp pixels
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int C_TOUCH = Ui.ACCENT, C_SWIPE = 0xFFFFFFFF, C_ZONE = 0xFFF5C26B, C_COLOR = 0xFFF472B6,
            C_IMAGE = 0xFFA78BFA, C_STEPS = 0xFF82AAFF, C_SEL = 0xFFFFE066;

    private final ScaleGestureDetector scaler;
    private final GestureDetector taps;

    ShotView(Context c, Listener l) {
        super(c);
        listener = l;
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.5f * dp);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(2 * dp);
        dash.setPathEffect(new DashPathEffect(new float[]{6 * dp, 4 * dp}, 0));
        text.setTextSize(11 * dp);
        text.setColor(0xFFFFFFFF);
        pixelPaint.setFilterBitmap(false);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoomBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (tool != TOOL_NONE) return false;
                zoom = 1;
                fitToView();
                invalidate();
                return true;
            }
        });
        setBackgroundColor(Ui.BG);
    }

    // ------------------------------------------------------------------ state from the editor
    void setPicture(Bitmap b, int designW, int designH) {
        bmp = b;
        this.designW = Math.max(1, designW);
        this.designH = Math.max(1, designH);
        zoom = 1;
        colorMask = null;
        imageHits.clear();
        zoneText = null;
        fitToView();
        invalidate();
    }

    Bitmap picture() { return bmp; }
    int designW() { return designW; }
    int designH() { return designH; }

    void setEmptyText(String s) { emptyText = s; invalidate(); }

    void setMarks(List<CodeMarks.Mark> m) {
        marks = m == null ? new ArrayList<CodeMarks.Mark>() : m;
        if (selected >= marks.size()) selected = -1;
        invalidate();
    }

    void setSelected(int i) {
        if (i == selected) return;
        selected = i;
        zoneText = null;
        colorMask = null;
        invalidate();
    }

    int selected() { return selected; }

    void setMarksShown(boolean all, int mask) { showAll = all; this.mask = mask; invalidate(); }

    void setSteps(List<float[]> s) { steps = s == null ? new ArrayList<float[]>() : s; invalidate(); }

    void setSim(List<ScriptSimulator.Touch> t, int upTo) { sim = t; simUpTo = upTo; invalidate(); }

    void setImageHits(Map<String, List<int[]>> hits) { imageHits = hits == null ? new HashMap<String, List<int[]>>() : hits; invalidate(); }

    void setZoneText(String s) { zoneText = s; invalidate(); }

    void setColorMask(Bitmap m) { colorMask = m; invalidate(); }

    void setTool(int t) { tool = t; gesture = G_NONE; invalidate(); }

    void setPending(List<int[]> p) { pending = p == null ? new ArrayList<int[]>() : p; invalidate(); }

    int tool() { return tool; }

    /** A finger is on the picture (dragging, choosing, zooming): the editor must not move the view now. */
    boolean busy() { return gesture != G_NONE; }

    /** Brings a mark into view (zoomed in, it may be outside). */
    void reveal(int index) {
        if (bmp == null || index < 0 || index >= marks.size() || !marks.get(index).hasPlace()) return;
        CodeMarks.Mark m = marks.get(index);
        float vx = vx(m.values[0]), vy = vy(m.values[1]);
        if (vx < 0 || vy < 0 || vx > getWidth() || vy > getHeight()) {
            tx += getWidth() / 2f - vx;
            ty += getHeight() / 2f - vy;
            clamp();
        }
        invalidate();
    }

    // ------------------------------------------------------------------ coordinates
    private float kx() { return bmp == null ? 1 : bmp.getWidth() / (float) designW; } // picture px per script unit
    private float ky() { return bmp == null ? 1 : bmp.getHeight() / (float) designH; }
    private float vx(float codeX) { return codeX * kx() * scale + tx; }
    private float vy(float codeY) { return codeY * ky() * scale + ty; }
    private int codeX(float viewX) { return Math.round((viewX - tx) / scale / kx()); }
    private int codeY(float viewY) { return Math.round((viewY - ty) / scale / ky()); }
    int codeToBmpX(int x) { return Math.round(x * kx()); }
    int codeToBmpY(int y) { return Math.round(y * ky()); }

    private void fitToView() {
        if (bmp == null || getWidth() == 0 || getHeight() == 0) return;
        fit = Math.min(getWidth() / (float) bmp.getWidth(), getHeight() / (float) bmp.getHeight());
        scale = fit * zoom;
        tx = (getWidth() - bmp.getWidth() * scale) / 2f;
        ty = (getHeight() - bmp.getHeight() * scale) / 2f;
    }

    private void zoomBy(float f, float fx, float fy) {
        if (bmp == null) return;
        float nz = Math.max(1f, Math.min(16f, zoom * f));
        float real = nz / zoom;
        zoom = nz;
        scale = fit * zoom;
        tx = fx - (fx - tx) * real;
        ty = fy - (fy - ty) * real;
        clamp();
        invalidate();
    }

    /** The picture never leaves the pane; smaller than the pane, it stays centred. */
    private void clamp() {
        if (bmp == null) return;
        float w = bmp.getWidth() * scale, h = bmp.getHeight() * scale;
        if (w <= getWidth()) tx = (getWidth() - w) / 2f;
        else tx = Math.min(0, Math.max(getWidth() - w, tx));
        if (h <= getHeight()) ty = (getHeight() - h) / 2f;
        else ty = Math.min(0, Math.max(getHeight() - h, ty));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (bmp == null) return;
        if (ow == 0 || oh == 0) { fitToView(); return; }
        // keep what was in the middle in the middle
        float cx = (ow / 2f - tx) / scale, cy = (oh / 2f - ty) / scale;
        fit = Math.min(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
        scale = fit * zoom;
        tx = w / 2f - cx * scale;
        ty = h / 2f - cy * scale;
        clamp();
    }

    private boolean shows(int cat) { return showAll && (mask & (1 << cat)) != 0; }

    // ------------------------------------------------------------------ touch
    private static final int G_NONE = 0, G_PAN = 1, G_DRAG = 2, G_TOOL = 3, G_MULTI = 4;
    private int gesture = G_NONE;
    private float downX, downY, lastX, lastY, curX, curY;
    private int dragIndex = -1, dragHandle;
    private int[] dragStartValues;
    private boolean moved;

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return true;
        scaler.onTouchEvent(e);
        taps.onTouchEvent(e);
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_POINTER_DOWN) {
            if (gesture == G_DRAG && moved) listener.onMarkMoved(dragIndex, current(dragIndex), true);
            gesture = G_MULTI; // two fingers: zoom / move only
            lastX = focusX(e);
            lastY = focusY(e);
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        if (gesture == G_MULTI) {
            if (act == MotionEvent.ACTION_MOVE && e.getPointerCount() > 1) {
                float fx = focusX(e), fy = focusY(e);
                tx += fx - lastX;
                ty += fy - lastY;
                lastX = fx;
                lastY = fy;
                clamp();
                invalidate();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                gesture = G_NONE;
            }
            return true;
        }
        float x = e.getX(), y = e.getY();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = lastX = curX = x;
                downY = lastY = curY = y;
                moved = false;
                if (tool != TOOL_NONE) {
                    gesture = G_TOOL;
                } else {
                    int[] hit = hit(x, y);
                    if (hit != null) {
                        gesture = G_DRAG;
                        dragIndex = hit[0];
                        dragHandle = hit[1];
                        dragStartValues = marks.get(dragIndex).values.clone();
                    } else {
                        gesture = G_PAN;
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                curX = x;
                curY = y;
                if (Math.hypot(x - downX, y - downY) > 6 * dp) moved = true;
                if (gesture == G_PAN) {
                    tx += x - lastX;
                    ty += y - lastY;
                    clamp();
                } else if (gesture == G_DRAG && moved && dragIndex < marks.size()) {
                    listener.onMarkMoved(dragIndex, current(dragIndex), false);
                }
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                curX = x;
                curY = y;
                if (gesture == G_DRAG && dragIndex < marks.size()) {
                    if (moved) listener.onMarkMoved(dragIndex, current(dragIndex), true);
                    else { selected = dragIndex; listener.onMarkTapped(dragIndex); }
                } else if (gesture == G_TOOL) {
                    finishTool();
                }
                gesture = G_NONE;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                gesture = G_NONE;
                invalidate();
                return true;
        }
        return true;
    }

    private static float focusX(MotionEvent e) {
        float s = 0;
        for (int i = 0; i < e.getPointerCount(); i++) s += e.getX(i);
        return s / e.getPointerCount();
    }

    private static float focusY(MotionEvent e) {
        float s = 0;
        for (int i = 0; i < e.getPointerCount(); i++) s += e.getY(i);
        return s / e.getPointerCount();
    }

    /** {mark index, handle} under the finger: handle 0/1 = first/second point, 2 = a swipe's middle, 0–3 zone corners, 4 = zone inside. */
    private int[] hit(float x, float y) {
        float r = 26 * dp;
        int best = -1, bestHandle = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = marks.size() - 1; i >= 0; i--) {
            CodeMarks.Mark m = marks.get(i);
            if (!m.hasPlace() || m.movable() == 0 || !shows(m.kind)) continue;
            float[][] pts;
            if (m.kind == CodeMarks.SWIPE) {
                pts = new float[][]{{vx(m.values[0]), vy(m.values[1])}, {vx(m.values[2]), vy(m.values[3])},
                        {(vx(m.values[0]) + vx(m.values[2])) / 2, (vy(m.values[1]) + vy(m.values[3])) / 2}};
            } else if (m.kind == CodeMarks.ZONE) {
                float x1 = vx(m.values[0]), y1 = vy(m.values[1]), x2 = vx(m.values[2]), y2 = vy(m.values[3]);
                pts = new float[][]{{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}};
            } else {
                pts = new float[][]{{vx(m.values[0]), vy(m.values[1])}};
            }
            for (int h = 0; h < pts.length; h++) {
                double d = Math.hypot(x - pts[h][0], y - pts[h][1]) - (i == selected ? 6 * dp : 0);
                if (d < r && d < bestD) { bestD = d; best = i; bestHandle = h; }
            }
            if (best < 0 && m.kind == CodeMarks.ZONE) {
                float x1 = Math.min(vx(m.values[0]), vx(m.values[2])), x2 = Math.max(vx(m.values[0]), vx(m.values[2]));
                float y1 = Math.min(vy(m.values[1]), vy(m.values[3])), y2 = Math.max(vy(m.values[1]), vy(m.values[3]));
                if (x >= x1 && x <= x2 && y >= y1 && y <= y2 && (x2 - x1) > 3 * r && (y2 - y1) > 2 * r) { best = i; bestHandle = 4; bestD = r; }
            }
        }
        return best < 0 ? null : new int[]{best, bestHandle};
    }

    /** The dragged mark's movable numbers for the finger's position now. */
    private int[] current(int index) {
        CodeMarks.Mark m = marks.get(index);
        int dx = codeX(curX) - codeX(downX), dy = codeY(curY) - codeY(downY);
        int[] s = dragStartValues;
        int[] v;
        if (m.kind == CodeMarks.SWIPE) {
            v = new int[]{s[0], s[1], s[2], s[3]};
            if (dragHandle == 0 || dragHandle == 2) { v[0] += dx; v[1] += dy; }
            if (dragHandle == 1 || dragHandle == 2) { v[2] += dx; v[3] += dy; }
        } else if (m.kind == CodeMarks.ZONE) {
            v = new int[]{s[0], s[1], s[2], s[3]};
            // corners 0 = (x1, y1), 1 = (x2, y1), 2 = (x1, y2), 3 = (x2, y2); 4 = all
            if (dragHandle == 0 || dragHandle == 2 || dragHandle == 4) v[0] += dx;
            if (dragHandle == 1 || dragHandle == 3 || dragHandle == 4) v[2] += dx;
            if (dragHandle == 0 || dragHandle == 1 || dragHandle == 4) v[1] += dy;
            if (dragHandle == 2 || dragHandle == 3 || dragHandle == 4) v[3] += dy;
        } else {
            v = new int[]{s[0] + dx, s[1] + dy};
        }
        for (int i = 0; i < v.length; i++) v[i] = Math.max(0, Math.min(i % 2 == 0 ? designW - 1 : designH - 1, v[i]));
        return v;
    }

    private void finishTool() {
        int t = tool;
        int x1 = clampX(codeX(downX)), y1 = clampY(codeY(downY)), x2 = clampX(codeX(curX)), y2 = clampY(codeY(curY));
        int[] v = null;
        int rgb = 0;
        if (t == TOOL_MODEL) { // one pixel more; the tool stays on until "✅ Scrie modelul"
            listener.onPicked(t, new int[]{x2, y2}, pixelAt(x2, y2));
            return;
        }
        if (t == TOOL_POINT || t == TOOL_COLOR) {
            v = new int[]{x2, y2}; // where the finger was lifted (it can be adjusted while looking in the magnifier)
            rgb = pixelAt(x2, y2);
        } else if (t == TOOL_SWIPE) {
            if (Math.hypot(x2 - x1, y2 - y1) >= 16) v = new int[]{x1, y1, x2, y2};
        } else if (t == TOOL_ZONE || t == TOOL_IMAGE || t == TOOL_TAPZONE) {
            if (Math.abs(x2 - x1) >= 4 && Math.abs(y2 - y1) >= 4) v = new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)};
        }
        tool = TOOL_NONE;
        listener.onToolEnded();
        if (v != null) listener.onPicked(t, v, rgb);
    }

    private int clampX(int x) { return Math.max(0, Math.min(designW - 1, x)); }
    private int clampY(int y) { return Math.max(0, Math.min(designH - 1, y)); }

    int pixelAt(int codeX, int codeY) {
        if (bmp == null) return 0;
        int bx = Math.max(0, Math.min(bmp.getWidth() - 1, codeToBmpX(codeX)));
        int by = Math.max(0, Math.min(bmp.getHeight() - 1, codeToBmpY(codeY)));
        return bmp.getPixel(bx, by) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ drawing
    @Override protected void onDraw(Canvas c) {
        if (bmp == null) {
            text.setTextSize(14 * dp);
            text.setColor(Ui.MUTED);
            text.setTextAlign(Paint.Align.CENTER);
            String[] lines = emptyText.split("\n");
            float y = lines.length == 1 ? getHeight() / 2f + 5 * dp : getHeight() / 2f - 20 * dp;
            for (String line : lines) {
                c.drawText(line, getWidth() / 2f, y, text);
                y += 20 * dp;
            }
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(11 * dp);
            text.setColor(0xFFFFFFFF);
            return;
        }
        c.save();
        c.translate(tx, ty);
        c.scale(scale, scale);
        c.drawBitmap(bmp, 0, 0, zoom > 3 ? pixelPaint : bmpPaint);
        if (colorMask != null && selected >= 0 && selected < marks.size() && marks.get(selected).kind == CodeMarks.COLOR) {
            c.drawBitmap(colorMask, null, new RectF(0, 0, bmp.getWidth(), bmp.getHeight()), pixelPaint);
        }
        c.restore();

        if (shows(CAT_STEPS)) drawSteps(c);
        if (showAll) {
            for (int i = 0; i < marks.size(); i++) if (i != selected) drawMark(c, i, false);
            if (selected >= 0 && selected < marks.size()) drawMark(c, selected, true);
        }
        if (shows(CAT_SIM)) drawSim(c);
        for (int i = 0; i < pending.size(); i++) { // the pattern being chosen
            int[] p = pending.get(i);
            float x = vx(p[0]), y = vy(p[1]), h = 8 * dp;
            fill.setColor(0xFF000000 | p[2]);
            c.drawRect(x - h, y - h, x + h, y + h, fill);
            stroke.setColor(C_SEL);
            stroke.setStrokeWidth(2.5f * dp);
            c.drawRect(x - h, y - h, x + h, y + h, stroke);
            label(c, String.valueOf(i + 1), x + h + 3 * dp, y - h, C_SEL, true);
        }
        if (gesture == G_TOOL) drawTool(c);
        if ((gesture == G_TOOL && tool != TOOL_NONE)
                || (gesture == G_DRAG && moved)) {
            drawMagnifier(c);
        }
    }

    private void drawMark(Canvas c, int i, boolean sel) {
        CodeMarks.Mark m = marks.get(i);
        if (!shows(m.kind)) return;
        int col = sel ? C_SEL : m.kind == CodeMarks.TOUCH ? C_TOUCH : m.kind == CodeMarks.SWIPE ? C_SWIPE : m.kind == CodeMarks.ZONE ? C_ZONE
                : m.kind == CodeMarks.COLOR ? C_COLOR : C_IMAGE;
        stroke.setColor(col);
        stroke.setStrokeWidth((sel ? 3.5f : 2.5f) * dp);
        float r = (sel ? 14 : 11) * dp;
        switch (m.kind) {
            case CodeMarks.TOUCH: {
                float x = vx(m.values[0]), y = vy(m.values[1]);
                fill.setColor((col & 0x00FFFFFF) | 0x44000000);
                c.drawCircle(x, y, r, fill);
                c.drawCircle(x, y, r, stroke);
                c.drawLine(x - r / 2, y, x + r / 2, y, stroke);
                c.drawLine(x, y - r / 2, x, y + r / 2, stroke);
                label(c, m.label, x + r + 3 * dp, y - r, col, sel);
                break;
            }
            case CodeMarks.SWIPE: {
                float x1 = vx(m.values[0]), y1 = vy(m.values[1]), x2 = vx(m.values[2]), y2 = vy(m.values[3]);
                arrow(c, x1, y1, x2, y2, stroke);
                fill.setColor(col);
                c.drawCircle(x1, y1, 5 * dp, fill);
                label(c, m.label, (x1 + x2) / 2 + 6 * dp, (y1 + y2) / 2, col, sel);
                break;
            }
            case CodeMarks.ZONE: {
                RectF rr = new RectF(Math.min(vx(m.values[0]), vx(m.values[2])), Math.min(vy(m.values[1]), vy(m.values[3])),
                        Math.max(vx(m.values[0]), vx(m.values[2])), Math.max(vy(m.values[1]), vy(m.values[3])));
                dash.setColor(col);
                c.drawRect(rr, dash);
                if (sel) {
                    fill.setColor(col);
                    float h = 5 * dp;
                    c.drawCircle(rr.left, rr.top, h, fill);
                    c.drawCircle(rr.right, rr.top, h, fill);
                    c.drawCircle(rr.left, rr.bottom, h, fill);
                    c.drawCircle(rr.right, rr.bottom, h, fill);
                }
                String l = m.label + (sel && zoneText != null ? " · citește: " + zoneText : "");
                label(c, l, rr.left, rr.bottom + 4 * dp + 12 * dp, col, sel);
                break;
            }
            case CodeMarks.COLOR: {
                if (!m.hasPlace()) break;
                float x = vx(m.values[0]), y = vy(m.values[1]);
                float s = (sel ? 9 : 7) * dp;
                int want = m.values[2];
                fill.setColor(want >= 0 ? 0xFF000000 | want : 0x00000000);
                c.drawRect(x - s, y - s, x + s, y + s, fill);
                c.drawRect(x - s, y - s, x + s, y + s, stroke);
                int got = pixelAt(m.values[0], m.values[1]);
                String l = m.label + (want >= 0 ? (ColorMatch.matches(got, want, m.tolerance < 0 ? 24 : m.tolerance) ? " · potrivire" : " · aici este " + ColorMatch.toHex(got)) : "");
                label(c, l, x + s + 4 * dp, y - s, col, sel);
                break;
            }
            case CodeMarks.IMAGE: {
                List<int[]> hits = imageHits.get(m.image == null ? "" : m.image.toLowerCase(Locale.ROOT));
                if (hits == null) break;
                for (int[] h : hits) {
                    RectF rr = new RectF(vx(h[0]), vy(h[1]), vx(h[2]), vy(h[3]));
                    c.drawRect(rr, stroke);
                    label(c, "IMG · " + m.image, rr.left, rr.top - 4 * dp, col, sel);
                }
                break;
            }
        }
    }

    private void drawSteps(Canvas c) {
        stroke.setColor(C_STEPS);
        stroke.setStrokeWidth(2 * dp);
        for (float[] s : steps) {
            float x = vx(s[0]), y = vy(s[1]);
            if (s[4] > 0) arrow(c, x, y, vx(s[2]), vy(s[3]), stroke);
            float h = 7 * dp;
            c.drawRect(x - h, y - h, x + h, y + h, stroke);
            label(c, "pasi[" + (int) s[5] + "]", x + h + 3 * dp, y - h, C_STEPS, false);
        }
    }

    private void drawSim(Canvas c) {
        if (sim == null || sim.isEmpty()) return;
        int n = Math.min(simUpTo, sim.size() - 1);
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        num.setTextSize(11 * dp);
        num.setTextAlign(Paint.Align.CENTER);
        num.setFakeBoldText(true);
        for (int i = 0; i <= n; i++) {
            ScriptSimulator.Touch t = sim.get(i);
            boolean last = i == n;
            float x = vx(t.x), y = vy(t.y);
            float r = (last ? 12 : 9) * dp;
            if (t.isSwipe()) {
                stroke.setColor(last ? C_SEL : 0xCCFFFFFF);
                stroke.setStrokeWidth(2 * dp);
                arrow(c, x, y, vx(t.x2), vy(t.y2), stroke);
            }
            fill.setColor(last ? C_SEL : 0xEEFFFFFF);
            c.drawCircle(x, y, r, fill);
            num.setColor(Ui.BG);
            c.drawText(String.valueOf(i + 1), x, y + 4 * dp, num);
        }
    }

    private void drawTool(Canvas c) {
        stroke.setColor(C_SEL);
        stroke.setStrokeWidth(2.5f * dp);
        if (tool == TOOL_SWIPE) arrow(c, downX, downY, curX, curY, stroke);
        else if (tool == TOOL_ZONE || tool == TOOL_IMAGE || tool == TOOL_TAPZONE) {
            dash.setColor(C_SEL);
            c.drawRect(Math.min(downX, curX), Math.min(downY, curY), Math.max(downX, curX), Math.max(downY, curY), dash);
        } else {
            c.drawLine(curX - 30 * dp, curY, curX + 30 * dp, curY, stroke);
            c.drawLine(curX, curY - 30 * dp, curX, curY + 30 * dp, stroke);
        }
    }

    /** A round magnifier in the corner away from the finger: picture pixels, a cross, the place and the colour. */
    private void drawMagnifier(Canvas c) {
        float R = 56 * dp;
        boolean left = curX > getWidth() / 2f;
        float mx = left ? R + 10 * dp : getWidth() - R - 10 * dp, my = R + 10 * dp;
        int codeX = clampX(codeX(curX)), codeY = clampY(codeY(curY));
        if (gesture == G_DRAG) { // the point being dragged, not the finger (the grab may be off-centre)
            int[] v = current(dragIndex);
            int k = dragHandle == 1 || dragHandle == 3 ? 2 : 0;
            if (marks.get(dragIndex).kind == CodeMarks.ZONE) {
                codeX = v[dragHandle == 1 || dragHandle == 3 ? 2 : 0];
                codeY = v[dragHandle >= 2 && dragHandle != 4 ? 3 : 1];
            } else if (v.length >= k + 2) { codeX = v[k]; codeY = v[k + 1]; }
        }
        float bx = codeX * kx(), by = codeY * ky();
        float per = 10 * dp; // view px per picture px inside the magnifier
        Path clip = new Path();
        clip.addCircle(mx, my, R, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawColor(0xFF000000);
        c.translate(mx - bx * per, my - by * per);
        c.scale(per, per);
        c.drawBitmap(bmp, 0, 0, pixelPaint);
        c.restore();
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(2 * dp);
        c.drawCircle(mx, my, R, stroke);
        stroke.setColor(C_SEL);
        stroke.setStrokeWidth(1.5f * dp);
        c.drawRect(mx - per / 2, my - per / 2, mx + per / 2, my + per / 2, stroke);
        int rgb = pixelAt(codeX, codeY);
        boolean showColor = tool == TOOL_COLOR || tool == TOOL_MODEL;
        String info = codeX + ", " + codeY + (showColor ? "  " + ColorMatch.toHex(rgb) : "");
        fill.setColor(0xFF000000 | rgb);
        if (showColor) c.drawCircle(mx - R + 8 * dp, my + R + 2 * dp, 7 * dp, fill);
        label(c, info, mx - R + (showColor ? 18 * dp : 0), my + R + 18 * dp, 0xFFFFFFFF, true);
    }

    private void arrow(Canvas c, float x1, float y1, float x2, float y2, Paint p) {
        c.drawLine(x1, y1, x2, y2, p);
        double a = Math.atan2(y2 - y1, x2 - x1);
        float h = 10 * dp;
        c.drawLine(x2, y2, (float) (x2 - h * Math.cos(a - 0.5)), (float) (y2 - h * Math.sin(a - 0.5)), p);
        c.drawLine(x2, y2, (float) (x2 - h * Math.cos(a + 0.5)), (float) (y2 - h * Math.sin(a + 0.5)), p);
    }

    /** A small dark pill with text (labels stay readable on any picture). */
    private void label(Canvas c, String s, float x, float y, int color, boolean strong) {
        if (s == null || s.isEmpty()) return;
        if (!strong && marks.size() > 14 && zoom < 1.5f) return; // many places, far away: only the selected one is named
        text.setColor(color);
        text.setFakeBoldText(strong);
        float w = text.measureText(s), pad = 4 * dp;
        x = Math.max(2 * dp, Math.min(getWidth() - w - 2 * pad, x));
        y = Math.max(14 * dp, Math.min(getHeight() - 4 * dp, y));
        fill.setColor(0xCC0E0B33);
        c.drawRoundRect(new RectF(x - pad, y - 12 * dp, x + w + pad, y + 4 * dp), 6 * dp, 6 * dp, fill);
        c.drawText(s, x, y, text);
    }
}
