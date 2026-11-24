package com.freeclick.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Window helpers. Every window we show is TYPE_ACCESSIBILITY_OVERLAY:
 *  - no "draw over other apps" permission is needed;
 *  - Android treats these as *trusted* overlays, so since Android 12 the
 *    "untrusted touch occlusion" anti-tapjacking rule never blocks touches
 *    passing through them, and they do not mark other apps' touches as
 *    obscured. (Safe mode additionally hides them during injection for
 *    older Android versions and picky apps.)
 */
public final class Overlays {
    private Overlays() {}

    public static WindowManager.LayoutParams params(int w, int h, boolean touchable, boolean focusable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | (focusable ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        | (touchable ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    /** Toggle FLAG_NOT_TOUCHABLE on an attached overlay. Returns true if something changed. */
    public static boolean setTouchable(WindowManager wm, View v, boolean touchable) {
        if (!isAdded(v)) return false;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
        if (lp == null) return false;
        int old = lp.flags;
        if (touchable) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (old == lp.flags) return false;
        safeUpdate(wm, v, lp);
        return true;
    }

    /**
     * True once addView() has returned (the window manager sets the parent synchronously).
     * Do not use isAttachedToWindow() for this: it only turns true on the next frame,
     * which made the home screen think the panel was still hidden.
     */
    public static boolean isAdded(View v) { return v != null && v.getParent() != null; }

    public static void safeAdd(WindowManager wm, View v, WindowManager.LayoutParams lp) {
        try {
            if (!isAdded(v)) wm.addView(v, lp);
        } catch (RuntimeException ignored) { /* window token gone (service dying) */ }
    }

    public static void safeUpdate(WindowManager wm, View v, WindowManager.LayoutParams lp) {
        try {
            if (isAdded(v)) wm.updateViewLayout(v, lp);
        } catch (RuntimeException ignored) { }
    }

    public static void safeRemove(WindowManager wm, View v) {
        if (v == null) return;
        try {
            if (isAdded(v)) wm.removeViewImmediate(v);
        } catch (RuntimeException ignored) { }
    }

    /** Real screen size in pixels (same coordinate space as dispatchGesture). */
    public static Point screenSize(Context c) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        //noinspection deprecation
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    /** Themed context for dialogs shown from the accessibility service. */
    public static Context dialogContext(Context base) {
        return Ui.dialogContext(base);
    }

    /** Make an AlertDialog displayable from a service (on top of any app). */
    public static AlertDialog showOverlayDialog(AlertDialog d) {
        Window w = d.getWindow();
        if (w != null) w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        try {
            d.show();
        } catch (RuntimeException ignored) { }
        return d;
    }
}
