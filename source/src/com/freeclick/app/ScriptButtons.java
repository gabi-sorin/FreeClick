package com.freeclick.app;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2.5: buttons a script puts over the game (buton("🔥", zona)). Each is a small window of FreeClick, so a
 * press on it never reaches the game and is always heard. Presses are queued; the script takes them
 * (apasat(b), asteaptaButon()) from its own thread. All of them go away when the script ends.
 */
final class ScriptButtons {
    private final Context ctx;
    private final WindowManager wm;
    private final Map<Integer, TextView> views = new HashMap<Integer, TextView>();
    private final List<Integer> presses = new ArrayList<Integer>(); // guarded by itself

    ScriptButtons(Context ctx, WindowManager wm) {
        this.ctx = ctx;
        this.wm = wm;
    }

    /** Main thread. Screen coordinates. */
    void add(final int id, String text, int x1, int y1, int x2, int y2) {
        remove(id);
        final TextView b = Ui.text(ctx, text, 18, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = Ui.rounded(0xB0140F45, Ui.dp(ctx, 16));
        bg.setStroke(Ui.dp(ctx, 2), Ui.ACCENT);
        b.setBackground(bg);
        b.setContentDescription(text);
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) v.setAlpha(0.55f);
                if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) synchronized (presses) { presses.add(id); }
                }
                return true;
            }
        });
        WindowManager.LayoutParams lp = Overlays.params(Math.max(1, x2 - x1), Math.max(1, y2 - y1), true, false);
        lp.x = x1;
        lp.y = y1;
        Overlays.safeAdd(wm, b, lp);
        views.put(id, b);
    }

    /** Main thread. 0 = all. */
    void remove(int id) {
        if (id == 0) {
            for (TextView v : views.values()) Overlays.safeRemove(wm, v);
            views.clear();
            synchronized (presses) { presses.clear(); }
            return;
        }
        TextView v = views.remove(id);
        if (v != null) Overlays.safeRemove(wm, v);
        synchronized (presses) { presses.removeAll(java.util.Collections.singleton(id)); }
    }

    /** Any thread. */
    boolean take(int id) {
        synchronized (presses) { return presses.remove(Integer.valueOf(id)); }
    }

    /** Any thread: the oldest press, or -1. */
    int takeAny() {
        synchronized (presses) { return presses.isEmpty() ? -1 : presses.remove(0); }
    }
}
