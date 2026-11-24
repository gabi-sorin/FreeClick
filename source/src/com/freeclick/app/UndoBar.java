package com.freeclick.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "Șterse 12 ținte · ANULEAZĂ" for 5 seconds at the bottom of the screen, instead of
 * asking "Are you sure?" before deleting (deleting is quick, undoing is one tap).
 */
final class UndoBar {
    private static final long SHOW_MS = 5000;

    private final Context ctx;
    private final WindowManager wm;
    private final android.os.Handler main;
    private LinearLayout view;

    private final Runnable autoHide = new Runnable() {
        @Override public void run() { hide(); }
    };

    UndoBar(Context ctx, WindowManager wm, android.os.Handler main) {
        this.ctx = ctx;
        this.wm = wm;
        this.main = main;
    }

    void show(String message, final Runnable undo) {
        hide();
        view = Ui.horizontal(ctx);
        view.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(ctx, 12);
        view.setPadding(p + Ui.dp(ctx, 4), p / 2, p / 2, p / 2);
        view.setBackground(Ui.rounded(0xF0140F45, Ui.dp(ctx, 12)));
        TextView msg = Ui.text(ctx, message, 14, 0xFFFFFFFF, false);
        view.addView(msg);
        TextView action = Ui.text(ctx, "ANULEAZĂ", 14, Ui.ACCENT, true);
        action.setPadding(p, p / 2, p, p / 2);
        Ui.clickable(action, new View.OnClickListener() {
            @Override public void onClick(View v) {
                hide();
                undo.run();
            }
        }, Ui.dp(ctx, 8));
        view.addView(action);
        WindowManager.LayoutParams lp = Overlays.params(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true, false);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = Ui.dp(ctx, 96);
        Overlays.safeAdd(wm, view, lp);
        main.postDelayed(autoHide, SHOW_MS);
    }

    void hide() {
        main.removeCallbacks(autoHide);
        if (view != null) Overlays.safeRemove(wm, view);
        view = null;
    }
}
