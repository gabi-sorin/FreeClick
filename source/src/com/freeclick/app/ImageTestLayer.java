package com.freeclick.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

/**
 * "Testează acum": draws on top of the real screen where the image was found (green boxes,
 * "Găsit 96%"), or the closest place (orange, "Nu apare acum · cel mai apropiat 71%").
 * Not touchable; removed after a few seconds.
 */
final class ImageTestLayer extends View {
    private final List<RectF> found;
    private final RectF closest;
    private final String message;
    private final boolean ok;
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] loc = new int[2];

    ImageTestLayer(Context c, List<RectF> found, RectF closest, String message) {
        super(c);
        this.found = found;
        this.closest = closest;
        this.message = message;
        this.ok = !found.isEmpty();
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(Ui.dp(c, 4));
        text.setColor(0xFFFFFFFF);
        text.setTextSize(Ui.dp(c, 15));
        text.setFakeBoldText(true);
        pill.setColor(ok ? 0xF0157F5B : 0xF0B45309);
    }

    @Override protected void onDraw(Canvas c) {
        getLocationOnScreen(loc);
        c.translate(-loc[0], -loc[1]);
        box.setColor(0xFF22C55E);
        for (RectF r : found) c.drawRoundRect(r, Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), box);
        if (!ok && closest != null) {
            box.setColor(0xFFF59E0B);
            c.drawRoundRect(closest, Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), box);
        }
        RectF anchor = ok ? found.get(0) : closest;
        float pad = Ui.dp(getContext(), 12), h = text.getTextSize() * 2f, w = text.measureText(message) + pad * 2;
        float x = anchor == null ? (getWidth() - w) / 2 : Math.max(pad, Math.min(getWidth() - w - pad, anchor.centerX() - w / 2));
        float y = anchor == null ? getHeight() * 0.4f : (anchor.top - h - pad > loc[1] + pad * 4 ? anchor.top - h - pad : anchor.bottom + pad);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), h / 2, h / 2, pill);
        c.drawText(message, x + pad, y + h / 2 + text.getTextSize() * 0.35f, text);
    }
}
