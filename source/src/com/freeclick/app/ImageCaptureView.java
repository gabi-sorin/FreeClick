package com.freeclick.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.freeclick.core.Magnet;
import com.freeclick.core.ScreenFrame;

/**
 * The screen frozen as it was, to choose something on it. Three uses:
 *  - MODE_IMAGE: drag a rectangle around a button; corners can be dragged, the rectangle moved,
 *    and when the finger lifts the sides snap to the button ("magnet") if the background is plain;
 *  - MODE_ZONE: a rectangle around a number or a text to read (no magnet, any size), with a live
 *    preview of what would be read;
 *  - MODE_COLOR: a colour picker: touch and drag, a strong magnifier shows the pixels, the colour
 *    under the cross is shown as #HEX, RGB and HSV.
 * ✓ hands the rectangle (a 1×1 one for a colour) back; nothing is saved here.
 */
final class ImageCaptureView extends FrameLayout {
    interface Callbacks {
        void onCaptureDone(Rect r, boolean tight);
        void onCaptureCancel();
    }

    static final int MODE_IMAGE = 0, MODE_ZONE = 1, MODE_COLOR = 2, MODE_SWIPE = 3;
    /** MODE_SWIPE: start and end of the swipe (screen px), or null. */
    private int[] line;

    /** MODE_ZONE: asked (on the UI thread) what would be read in a rectangle; answer with showReading. */
    interface ZonePreview { void preview(Rect r, ImageCaptureView v); }

    private final int mode;
    private ZonePreview previewer;
    private String reading;
    /** MODE_COLOR: the chosen point (screen px), or null. */
    private int[] pick;

    private final ScreenFrame frame;
    private final Bitmap shot;
    private final Callbacks cb;
    private final int statusBar;
    private final Canvas_ canvasView;
    private final LinearLayout bar;
    private final TextView hint;
    private Rect sel;
    private boolean tight;

    ImageCaptureView(Context c, ScreenFrame frame, int statusBar, Callbacks cb) {
        this(c, frame, statusBar, MODE_IMAGE, cb);
    }

    ImageCaptureView(Context c, ScreenFrame frame, int statusBar, int mode, Callbacks cb) {
        super(c);
        this.mode = mode;
        this.frame = frame;
        this.cb = cb;
        this.statusBar = statusBar;
        this.shot = Bitmap.createBitmap(frame.argb, frame.width, frame.height, Bitmap.Config.ARGB_8888);
        canvasView = new Canvas_(c);
        addView(canvasView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hint = Ui.text(c, emptyHint(), 15, 0xFFFFFFFF, true);
        hint.setBackground(Ui.rounded(0xE6140F45, Ui.dp(c, 18)));
        int p = Ui.dp(c, 12);
        hint.setPadding(p + 4, p / 2 + 2, p + 4, p / 2 + 2);
        LayoutParams hl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hl.topMargin = statusBar + Ui.dp(c, 8);
        addView(hint, hl);

        bar = Ui.horizontal(c);
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(Ui.rounded(0xF0140F45, Ui.dp(c, 18)));
        bar.setPadding(p / 2, p / 2, p / 2, p / 2);
        bar.addView(button(c, "× Renunță", 0x33FFFFFF, new OnClickListener() { public void onClick(View v) { cb.onCaptureCancel(); } }));
        bar.addView(button(c, "🔄 Din nou", 0x33FFFFFF, new OnClickListener() { public void onClick(View v) {
            sel = null; tight = false; pick = null; line = null; reading = null; update(); } }));
        bar.addView(button(c, "✅ Gata", Ui.ACCENT, new OnClickListener() {
            public void onClick(View v) {
                if (ImageCaptureView.this.mode == MODE_SWIPE) {
                    if (line == null) { hint.setText("Întâi trage cu degetul pe unde vrei swipe-ul"); return; }
                    cb.onCaptureDone(new Rect(line[0], line[1], line[2], line[3]), false); // not ordered: start → end
                    return;
                }
                if (ImageCaptureView.this.mode == MODE_COLOR) {
                    if (pick == null) { hint.setText("Întâi atinge punctul a cărui culoare o vrei"); return; }
                    cb.onCaptureDone(new Rect(pick[0], pick[1], pick[0] + 1, pick[1] + 1), false);
                    return;
                }
                if (sel == null) { hint.setText("Întâi " + emptyHint().toLowerCase(java.util.Locale.ROOT)); return; }
                cb.onCaptureDone(new Rect(sel), tight);
            }
        }));
        LayoutParams bl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.dp(c, 28);
        addView(bar, bl);
        update();
    }

    private TextView button(Context c, String s, int bg, OnClickListener l) {
        TextView t = Ui.text(c, s, 15, bg == Ui.ACCENT ? Ui.ON_ACCENT : 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        int p = Ui.dp(c, 12);
        t.setPadding(p + 2, p - 2, p + 2, p - 2);
        t.setBackground(Ui.rounded(bg, Ui.dp(c, 14)));
        Ui.clickable(t, l, Ui.dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = Ui.dp(c, 4);
        t.setLayoutParams(lp);
        return t;
    }

    void setZonePreview(ZonePreview p) { previewer = p; }

    /** MODE_ZONE: what the reader sees in the current rectangle. */
    void showReading(String text) {
        reading = text;
        update();
    }

    private String emptyHint() {
        if (mode == MODE_ZONE) return "Trage un dreptunghi peste numărul sau textul de citit";
        if (mode == MODE_COLOR) return "Atinge punctul a cărui culoare o vrei (trage ca să potrivești)";
        if (mode == MODE_SWIPE) return "Trage cu degetul de unde începe swipe-ul până unde se termină";
        return "Trage un dreptunghi în jurul butonului";
    }

    /** "#34A853 · RGB 52, 168, 83 · HSV 136°, 69%, 66%" */
    static String describeColor(int rgb) {
        float[] hsv = new float[3];
        android.graphics.Color.RGBToHSV((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, hsv);
        return com.freeclick.core.ColorMatch.toHex(rgb) + " · RGB " + ((rgb >> 16) & 255) + ", " + ((rgb >> 8) & 255) + ", " + (rgb & 255)
                + " · HSV " + Math.round(hsv[0]) % 360 + "°, " + Math.round(hsv[1] * 100) + "%, " + Math.round(hsv[2] * 100) + "%";
    }

    /** A message in the hint bubble (toasts would be hidden under this full-screen layer). */
    void showProblem(String text) {
        hint.setText("⚠ " + text);
        hint.setBackground(Ui.rounded(0xF0B4233C, Ui.dp(getContext(), 18)));
    }

    /** Free the copy of the screenshot. */
    void dispose() { if (!shot.isRecycled()) shot.recycle(); }

    /** Move the buttons away from the selection, update the hint. */
    private void update() {
        LayoutParams bl = (LayoutParams) bar.getLayoutParams();
        boolean selLow = sel != null && sel.centerY() > frame.height / 2;
        bl.gravity = (selLow ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        bl.topMargin = selLow ? statusBar + Ui.dp(getContext(), 56) : 0;
        bl.bottomMargin = selLow ? 0 : Ui.dp(getContext(), 28);
        bar.setLayoutParams(bl);
        LayoutParams hl = (LayoutParams) hint.getLayoutParams();
        boolean selHigh = sel != null && sel.centerY() < frame.height / 3;
        hl.gravity = (selHigh ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
        hl.topMargin = selHigh ? 0 : statusBar + Ui.dp(getContext(), 8);
        hl.bottomMargin = selHigh ? Ui.dp(getContext(), 100) : 0;
        hint.setLayoutParams(hl);
        hint.setBackground(Ui.rounded(0xE6140F45, Ui.dp(getContext(), 18)));
        if (mode == MODE_SWIPE) {
            if (line == null) hint.setText(emptyHint());
            else hint.setText("(" + line[0] + ", " + line[1] + ") → (" + line[2] + ", " + line[3] + ") · trage din nou sau apasă ✅");
        } else if (mode == MODE_COLOR) {
            if (pick == null) hint.setText(emptyHint());
            else {
                int rgb = frame.argb[pick[1] * frame.width + pick[0]] & 0xFFFFFF;
                hint.setText(describeColor(rgb) + "\n(" + pick[0] + ", " + pick[1] + ") · apasă ✅");
                hint.setBackground(Ui.rounded(0xF0000000 | rgb, Ui.dp(getContext(), 18)));
                int lum = (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
                hint.setTextColor(lum > 140 ? 0xFF000000 : 0xFFFFFFFF);
            }
        } else if (sel == null) hint.setText(emptyHint());
        else if (mode == MODE_ZONE) hint.setText(sel.width() + " × " + sel.height() + " · " + (reading == null ? "citesc…" : reading) + "\ntrage de colțuri sau apasă ✅");
        else hint.setText(sel.width() + " × " + sel.height() + (tight ? " · lipit de buton 🧲" : "") + " · trage de colțuri sau apasă ✅");
        if (mode != MODE_COLOR || pick == null) hint.setTextColor(0xFFFFFFFF);
        canvasView.invalidate();
    }

    // ------------------------------------------------------------------ drawing & touch
    private final class Canvas_ extends View {
        final Paint dim = new Paint();
        final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint loupeBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint cross = new Paint();
        final int[] loc = new int[2];
        final float handleR, touchR;
        int dragMode; // 0 none, 1 new, 2 move, 3..6 corners (tl, tr, bl, br)
        float downX, downY;
        Rect start;
        float fingerX = -1, fingerY = -1;

        Canvas_(Context c) {
            super(c);
            dim.setColor(0x99000000);
            border.setColor(Ui.ACCENT);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Ui.dp(c, 2));
            handle.setColor(Ui.ACCENT);
            loupeBorder.setColor(0xFFFFFFFF);
            loupeBorder.setStyle(Paint.Style.STROKE);
            loupeBorder.setStrokeWidth(Ui.dp(c, 3));
            cross.setColor(0xFFF43F5E);
            cross.setStrokeWidth(Ui.dp(c, 1));
            handleR = Ui.dp(c, 7);
            touchR = Ui.dp(c, 32);
        }

        @Override protected void onDraw(Canvas c) {
            getLocationOnScreen(loc);
            c.save();
            c.translate(-loc[0], -loc[1]);
            c.drawBitmap(shot, 0, 0, null);
            int w = frame.width, h = frame.height;
            if (mode == MODE_SWIPE) {
                if (line != null) {
                    c.drawLine(line[0], line[1], line[2], line[3], border);
                    c.drawCircle(line[0], line[1], handleR * 1.4f, handle);
                    double ang = Math.atan2(line[3] - line[1], line[2] - line[0]);
                    float len = Ui.dp(getContext(), 18);
                    c.drawLine(line[2], line[3], (float) (line[2] - len * Math.cos(ang - 0.5)), (float) (line[3] - len * Math.sin(ang - 0.5)), border);
                    c.drawLine(line[2], line[3], (float) (line[2] - len * Math.cos(ang + 0.5)), (float) (line[3] - len * Math.sin(ang + 0.5)), border);
                }
            } else if (mode == MODE_COLOR) {
                if (pick != null) {
                    float r = Ui.dp(getContext(), 14);
                    c.drawLine(pick[0] - r, pick[1], pick[0] - 3, pick[1], cross);
                    c.drawLine(pick[0] + 3, pick[1], pick[0] + r, pick[1], cross);
                    c.drawLine(pick[0], pick[1] - r, pick[0], pick[1] - 3, cross);
                    c.drawLine(pick[0], pick[1] + 3, pick[0], pick[1] + r, cross);
                    fingerX = pick[0];
                    fingerY = pick[1];
                    drawLoupe(c);
                }
            } else if (sel == null) {
                c.drawRect(0, 0, w, h, dim);
            } else {
                c.drawRect(0, 0, w, sel.top, dim);
                c.drawRect(0, sel.bottom, w, h, dim);
                c.drawRect(0, sel.top, sel.left, sel.bottom, dim);
                c.drawRect(sel.right, sel.top, w, sel.bottom, dim);
                c.drawRect(sel, border);
                c.drawCircle(sel.left, sel.top, handleR, handle);
                c.drawCircle(sel.right, sel.top, handleR, handle);
                c.drawCircle(sel.left, sel.bottom, handleR, handle);
                c.drawCircle(sel.right, sel.bottom, handleR, handle);
            }
            if (fingerX >= 0 && mode != MODE_COLOR) drawLoupe(c);
            c.restore();
        }

        /** 3× magnifier of the pixels under the finger, in a corner away from it. */
        private void drawLoupe(Canvas c) {
            float r = Ui.dp(getContext(), mode == MODE_COLOR ? 76 : 64);
            float cx = fingerX < frame.width / 2f ? frame.width - r - Ui.dp(getContext(), 16) : r + Ui.dp(getContext(), 16);
            float cy = statusBar + r + Ui.dp(getContext(), mode == MODE_COLOR ? 120 : 64);
            if (mode == MODE_COLOR && fingerY < cy + r + Ui.dp(getContext(), 20)) cy = frame.height - r - Ui.dp(getContext(), 140);
            int half = Math.max(3, Math.round(r / (mode == MODE_COLOR ? 9 : 3)));
            Rect src = new Rect(Math.round(fingerX) - half, Math.round(fingerY) - half, Math.round(fingerX) + half, Math.round(fingerY) + half);
            RectF dst = new RectF(cx - r, cy - r, cx + r, cy + r);
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            c.drawColor(0xFF000000);
            c.drawBitmap(shot, src, dst, null); // no filtering: pixels stay sharp squares
            c.drawLine(cx - r, cy, cx + r, cy, cross);
            c.drawLine(cx, cy - r, cx, cy + r, cross);
            c.restore();
            c.drawCircle(cx, cy, r, loupeBorder);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getRawX(), y = e.getRawY();
            if (mode == MODE_SWIPE) {
                int a = e.getActionMasked();
                int px = clamp(Math.round(x), 0, frame.width - 1), py = clamp(Math.round(y), 0, frame.height - 1);
                if (a == MotionEvent.ACTION_DOWN) { line = new int[]{px, py, px, py}; fingerX = x; fingerY = y; }
                else if ((a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) && line != null) { line[2] = px; line[3] = py; fingerX = a == MotionEvent.ACTION_UP ? -1 : x; fingerY = y; }
                else return super.onTouchEvent(e);
                update();
                return true;
            }
            if (mode == MODE_COLOR) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                    pick = new int[]{clamp(Math.round(x), 0, frame.width - 1), clamp(Math.round(y), 0, frame.height - 1)};
                    update();
                    return true;
                }
                return super.onTouchEvent(e);
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x; downY = y;
                    start = sel == null ? null : new Rect(sel);
                    dragMode = 1;
                    if (sel != null) {
                        if (near(x, y, sel.left, sel.top)) dragMode = 3;
                        else if (near(x, y, sel.right, sel.top)) dragMode = 4;
                        else if (near(x, y, sel.left, sel.bottom)) dragMode = 5;
                        else if (near(x, y, sel.right, sel.bottom)) dragMode = 6;
                        else if (sel.contains((int) x, (int) y)) dragMode = 2;
                    }
                    fingerX = x; fingerY = y;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    fingerX = x; fingerY = y;
                    int dx = Math.round(x - downX), dy = Math.round(y - downY);
                    if (dragMode == 1) {
                        sel = ordered(downX, downY, x, y);
                    } else if (dragMode == 2 && start != null) {
                        sel = new Rect(start);
                        sel.offset(clamp(dx, -start.left, frame.width - start.right), clamp(dy, -start.top, frame.height - start.bottom));
                    } else if (start != null) {
                        float l = start.left, t = start.top, r = start.right, b = start.bottom;
                        if (dragMode == 3) { l += dx; t += dy; }
                        if (dragMode == 4) { r += dx; t += dy; }
                        if (dragMode == 5) { l += dx; b += dy; }
                        if (dragMode == 6) { r += dx; b += dy; }
                        sel = ordered(l, t, r, b);
                    }
                    tight = false;
                    update();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    fingerX = -1;
                    if (sel != null && (sel.width() < 6 || sel.height() < 6)) sel = null; // a tap, not a rectangle
                    if (sel != null && dragMode != 2 && mode == MODE_IMAGE) {
                        int[] r = Magnet.snap(frame, sel.left, sel.top, sel.width(), sel.height());
                        sel = new Rect(r[0], r[1], r[0] + r[2], r[1] + r[3]);
                        tight = r[4] == 1;
                    }
                    dragMode = 0;
                    if (mode == MODE_ZONE && sel != null && previewer != null) { reading = null; previewer.preview(new Rect(sel), ImageCaptureView.this); }
                    update();
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private boolean near(float x, float y, float px, float py) { return Math.hypot(x - px, y - py) < touchR; }

        private Rect ordered(float x1, float y1, float x2, float y2) {
            int l = clamp(Math.round(Math.min(x1, x2)), 0, frame.width), r = clamp(Math.round(Math.max(x1, x2)), 0, frame.width);
            int t = clamp(Math.round(Math.min(y1, y2)), 0, frame.height), b = clamp(Math.round(Math.max(y1, y2)), 0, frame.height);
            return new Rect(l, t, r, b);
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
