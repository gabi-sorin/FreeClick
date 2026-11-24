package com.freeclick.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Round panel button drawn with Canvas (crisp at any size, no image assets). */
final class PanelIcon extends View {
    enum Kind { PLAY, STOP, PAUSE, PLUS, MINUS, MENU, RECORD, RECORD_STOP, BACK, HOME, RECENTS, SCREENSHOT, SKIP, EYE, IMAGE, TURBO }

    private Kind kind;

    Kind kind() { return kind; }
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    PanelIcon(Context c, Kind kind) {
        super(c);
        this.kind = kind;
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription(kind.name());
    }

    void setKind(Kind k) { kind = k; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f * 0.78f;
        float sw = r * 0.16f;
        ink.setStrokeWidth(sw);
        switch (kind) {
            case PLAY: {
                fill.setColor(0xFFCFD7E1);
                c.drawCircle(cx, cy, r, fill);
                ink.setColor(Ui.BG);
                ink.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(cx - r * 0.28f, cy - r * 0.42f);
                path.lineTo(cx + r * 0.46f, cy);
                path.lineTo(cx - r * 0.28f, cy + r * 0.42f);
                path.close();
                c.drawPath(path, ink);
                break;
            }
            case STOP:
            case RECORD_STOP: {
                fill.setColor(0xFFF43F5E);
                c.drawCircle(cx, cy, r, fill);
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.FILL);
                float s = r * 0.38f;
                rect.set(cx - s, cy - s, cx + s, cy + s);
                c.drawRoundRect(rect, s * 0.25f, s * 0.25f, ink);
                break;
            }
            case PAUSE: {
                fill.setColor(0xFFCFD7E1);
                c.drawCircle(cx, cy, r, fill);
                ink.setColor(Ui.BG);
                ink.setStyle(Paint.Style.FILL);
                float bw = r * 0.18f, bh = r * 0.42f;
                c.drawRect(cx - r * 0.28f - bw / 2, cy - bh, cx - r * 0.28f + bw / 2, cy + bh, ink);
                c.drawRect(cx + r * 0.28f - bw / 2, cy - bh, cx + r * 0.28f + bw / 2, cy + bh, ink);
                break;
            }
            case PLUS:
            case MINUS: {
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r, fill);
                ink.setColor(Ui.BG);
                ink.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, ink);
                if (kind == Kind.PLUS) c.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, ink);
                break;
            }
            case TURBO: { // 🔥 Turbo: an orange disc with a lightning bolt
                fill.setColor(0xFFFF8A3D);
                c.drawCircle(cx, cy, r, fill);
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(cx + r * 0.12f, cy - r * 0.62f);
                path.lineTo(cx - r * 0.34f, cy + r * 0.08f);
                path.lineTo(cx - r * 0.02f, cy + r * 0.08f);
                path.lineTo(cx - r * 0.14f, cy + r * 0.62f);
                path.lineTo(cx + r * 0.34f, cy - r * 0.10f);
                path.lineTo(cx + r * 0.02f, cy - r * 0.10f);
                path.close();
                c.drawPath(path, ink);
                ink.setStyle(Paint.Style.STROKE);
                break;
            }
            case MENU: {
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeCap(Paint.Cap.BUTT);
                for (int i = -1; i <= 1; i++) c.drawLine(cx - r * 0.75f, cy + i * r * 0.42f, cx + r * 0.75f, cy + i * r * 0.42f, ink);
                ink.setStrokeCap(Paint.Cap.ROUND);
                break;
            }
            case RECORD: {
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx, cy, r - sw / 2, ink);
                fill.setColor(0xFFF43F5E);
                c.drawCircle(cx, cy, r * 0.38f, fill);
                break;
            }
            case BACK: {
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(cx + r * 0.38f, cy - r * 0.45f);
                path.lineTo(cx - r * 0.42f, cy);
                path.lineTo(cx + r * 0.38f, cy + r * 0.45f);
                path.close();
                c.drawPath(path, ink);
                break;
            }
            case HOME: {
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r * 0.42f, fill);
                break;
            }
            case RECENTS: {
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.FILL);
                float s = r * 0.40f;
                rect.set(cx - s, cy - s, cx + s, cy + s);
                c.drawRoundRect(rect, s * 0.2f, s * 0.2f, ink);
                break;
            }
            case SCREENSHOT: {
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(sw * 0.8f);
                rect.set(cx - r * 0.55f, cy - r * 0.38f, cx + r * 0.55f, cy + r * 0.42f);
                c.drawRoundRect(rect, r * 0.12f, r * 0.12f, ink);
                c.drawCircle(cx, cy + r * 0.02f, r * 0.17f, ink);
                break;
            }
            case SKIP: { // ⏭ : two triangles and a bar
                fill.setColor(0xFFCFD7E1);
                c.drawCircle(cx, cy, r, fill);
                ink.setColor(Ui.BG);
                ink.setStyle(Paint.Style.FILL);
                float t = r * 0.30f;
                path.reset();
                path.moveTo(cx - r * 0.50f, cy - t); path.lineTo(cx - r * 0.08f, cy); path.lineTo(cx - r * 0.50f, cy + t); path.close();
                path.moveTo(cx - r * 0.10f, cy - t); path.lineTo(cx + r * 0.32f, cy); path.lineTo(cx - r * 0.10f, cy + t); path.close();
                c.drawPath(path, ink);
                c.drawRect(cx + r * 0.34f, cy - t, cx + r * 0.46f, cy + t, ink);
                break;
            }
            case IMAGE: { // a framed picture with a mountain: "image"
                ink.setColor(0xFFFFFFFF);
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(sw * 0.8f);
                rect.set(cx - r * 0.6f, cy - r * 0.45f, cx + r * 0.6f, cy + r * 0.45f);
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, ink);
                ink.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(cx - r * 0.5f, cy + r * 0.35f);
                path.lineTo(cx - r * 0.15f, cy - r * 0.05f);
                path.lineTo(cx + r * 0.05f, cy + r * 0.15f);
                path.lineTo(cx + r * 0.25f, cy - r * 0.15f);
                path.lineTo(cx + r * 0.5f, cy + r * 0.35f);
                path.close();
                c.drawPath(path, ink);
                c.drawCircle(cx + r * 0.25f, cy - r * 0.25f, r * 0.08f, ink);
                break;
            }
            case EYE: { // preview: an eye
                ink.setColor(Ui.ACCENT);
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(sw * 0.8f);
                path.reset();
                path.moveTo(cx - r * 0.75f, cy);
                path.quadTo(cx, cy - r * 0.75f, cx + r * 0.75f, cy);
                path.quadTo(cx, cy + r * 0.75f, cx - r * 0.75f, cy);
                c.drawPath(path, ink);
                fill.setColor(Ui.ACCENT);
                c.drawCircle(cx, cy, r * 0.22f, fill);
                break;
            }
        }
    }
}
