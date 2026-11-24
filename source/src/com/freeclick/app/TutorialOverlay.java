package com.freeclick.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "Tap to continue" tutorial: the screen darkens except the part being explained (a lit hole with a
 * ring), a bubble explains it; a tap anywhere goes to the next step. No button while it runs (nothing
 * to press by mistake); the last bubble has "Închide" (it comes again next time) and "Nu mai afișa".
 */
final class TutorialOverlay extends FrameLayout {

    static final class Step {
        final View target; // null: nothing lit, the bubble sits in the middle
        final String text;

        Step(View target, String text) {
            this.target = target;
            this.text = text;
        }
    }

    interface OnClosed {
        /** @param neverAgain the user chose "Nu mai afișa" */
        void onClosed(boolean neverAgain);
    }

    /** Shows the tutorial over the activity; {@code onClosed} runs when it is closed. */
    static TutorialOverlay show(Activity a, Step[] steps, OnClosed onClosed) {
        ViewGroup root = (ViewGroup) a.findViewById(android.R.id.content);
        if (root == null || steps.length == 0) return null;
        close(a); // only one at a time
        TutorialOverlay o = new TutorialOverlay(a, steps, onClosed);
        root.addView(o, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        o.setAlpha(0f);
        o.animate().alpha(1f).setDuration(200).start();
        o.go(0);
        return o;
    }

    /**
     * The same tutorial over everything (an overlay window), e.g. for the floating panel; targets
     * may be in other windows (positions are compared on the screen).
     */
    static TutorialOverlay showOverScreen(Context c, android.view.WindowManager wm, Step[] steps, OnClosed onClosed) {
        if (steps.length == 0) return null;
        TutorialOverlay o = new TutorialOverlay(c, steps, onClosed);
        o.wm = wm;
        android.view.WindowManager.LayoutParams lp = Overlays.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true, false);
        Overlays.safeAdd(wm, o, lp);
        o.setAlpha(0f);
        o.animate().alpha(1f).setDuration(200).start();
        o.go(0);
        return o;
    }

    private android.view.WindowManager wm;

    /** Back key: closes a tutorial on screen (it comes again next time). True if there was one. */
    static boolean close(Activity a) {
        ViewGroup root = (ViewGroup) a.findViewById(android.R.id.content);
        if (root == null) return false;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof TutorialOverlay) {
                ((TutorialOverlay) root.getChildAt(i)).end(false, false);
                return true;
            }
        }
        return false;
    }

    private final Step[] steps;
    private final OnClosed onClosed;
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF hole = new RectF();
    private boolean hasHole;
    private int index;
    private boolean ended;
    private final LinearLayout bubble, lastRow;
    private final TextView text, counter;

    private TutorialOverlay(Context c, Step[] steps, OnClosed onClosed) {
        super(c);
        this.steps = steps;
        this.onClosed = onClosed;
        setWillNotDraw(false);
        setClickable(true);
        dim.setColor(0xC8050318);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 2.5f));
        ring.setColor(Ui.ACCENT);

        bubble = Ui.vertical(c);
        int p = Ui.dp(c, 16);
        bubble.setPadding(p, p, p, Ui.dp(c, 10));
        bubble.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(c, 16)));
        text = Ui.text(c, "", 16, Ui.TEXT, false);
        text.setLineSpacing(0, 1.15f);
        bubble.addView(text);
        counter = Ui.text(c, "", 13, Ui.MUTED, false);
        counter.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
        bubble.addView(counter);
        // only on the last bubble: the choice, made when the tutorial is over
        lastRow = Ui.horizontal(c);
        lastRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        lastRow.setPadding(0, Ui.dp(c, 4), 0, 0);
        lastRow.addView(choice(c, "Închide", Ui.MUTED, false));
        lastRow.addView(choice(c, "Nu mai afișa", Ui.ACCENT, true));
        bubble.addView(lastRow);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(c, 16);
        lp.leftMargin = m;
        lp.rightMargin = m;
        addView(bubble, lp);
    }

    private TextView choice(Context c, String label, int color, final boolean neverAgain) {
        TextView t = Ui.text(c, label, 15, color, true);
        int p = Ui.dp(c, 12);
        t.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        Ui.clickable(t, new OnClickListener() { public void onClick(View v) { end(true, neverAgain); } }, Ui.dp(c, 8));
        return t;
    }

    private boolean last() { return index == steps.length - 1; }

    private void go(int i) {
        index = i;
        Step s = steps[i];
        text.setText(s.text);
        boolean end = i == steps.length - 1;
        counter.setText(end ? "Gata (" + (i + 1) + "/" + steps.length + "). La următoarea deschidere îl vezi din nou, "
                + "dacă nu alegi „Nu mai afișa”." : "Atinge ca să continui (" + (i + 1) + "/" + steps.length + ")");
        lastRow.setVisibility(end ? VISIBLE : GONE);
        bubble.setVisibility(INVISIBLE);
        post(new Runnable() { public void run() { place(); } });
    }

    /** Lights the target and puts the bubble below it, above it, or over it (whichever fits). */
    private void place() {
        if (ended) return;
        Step s = steps[index];
        hasHole = false;
        if (s.target != null && s.target.isShown() && s.target.getWidth() > 0) {
            int[] me = new int[2], t = new int[2];
            getLocationOnScreen(me); // the target may be in another window (the panel)
            s.target.getLocationOnScreen(t);
            float pad = Ui.dp(getContext(), 6);
            hole.set(t[0] - me[0] - pad, t[1] - me[1] - pad,
                    t[0] - me[0] + s.target.getWidth() + pad, t[1] - me[1] + s.target.getHeight() + pad);
            // a target taller than the screen part we see is cut to it
            hole.intersect(pad / 2, pad / 2, getWidth() - pad / 2, getHeight() - pad / 2);
            hasHole = true;
        }
        int bh = bubble.getHeight();
        if (bh == 0 || bubble.getVisibility() != VISIBLE) {
            bubble.measure(MeasureSpec.makeMeasureSpec(getWidth() - Ui.dp(getContext(), 32), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
            bh = bubble.getMeasuredHeight();
        }
        int gap = Ui.dp(getContext(), 12);
        float y;
        if (!hasHole) y = (getHeight() - bh) / 2f;
        else if (getHeight() - hole.bottom >= bh + 2 * gap) y = hole.bottom + gap;
        else if (hole.top >= bh + 2 * gap) y = hole.top - gap - bh;
        else y = hole.centerY() - bh / 2f; // big target: the bubble sits over its middle
        bubble.setY(Math.max(gap, Math.min(getHeight() - bh - gap, y)));
        bubble.setVisibility(VISIBLE);
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (!ended) post(new Runnable() { public void run() { place(); } });
    }

    @Override protected void onDraw(Canvas c) {
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        float r = Ui.dp(getContext(), 12);
        if (hasHole) path.addRoundRect(hole, r, r, Path.Direction.CW);
        c.drawPath(path, dim);
        if (hasHole) c.drawRoundRect(hole, r, r, ring);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        // on the last bubble only its two buttons do something (a quick extra tap does not close it)
        if (e.getActionMasked() == MotionEvent.ACTION_UP && !last()) go(index + 1);
        return true; // the screen under the tutorial does not react meanwhile
    }

    private void end(boolean animate, boolean neverAgain) {
        if (ended) return;
        ended = true;
        if (onClosed != null) onClosed.onClosed(neverAgain);
        if (wm != null) {
            animate().alpha(0f).setDuration(animate ? 180 : 0).withEndAction(new Runnable() {
                @Override public void run() { Overlays.safeRemove(wm, TutorialOverlay.this); }
            }).start();
            return;
        }
        final ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return;
        if (!animate) { parent.removeView(this); return; }
        animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
            @Override public void run() { parent.removeView(TutorialOverlay.this); }
        }).start();
    }
}
