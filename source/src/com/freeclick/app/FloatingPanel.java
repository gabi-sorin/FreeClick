package com.freeclick.app;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.freeclick.core.GesturePlan;
import com.freeclick.core.GlobalActions;

import java.util.Locale;

/**
 * Compact horizontal floating bar (drag it anywhere by any part of it):
 *   manual mode:   ▶  +  −  ≡
 *   record mode:   ▶  ●  ≡     (while recording: ■ count, and a row ◀ ● ■ ⌷ for system keys)
 *   combinations:  ▶  ≡
 *   while playing: ■  ❚❚  1×  progress
 */
final class FloatingPanel {

    interface Callbacks {
        void onPlay();
        void onStop();
        void onPauseToggle();
        void onAddMenu(View anchor);
        void onRemoveLast();
        void onClearAll();
        void onRecordToggle();
        void onGlobalDuringRecording(int globalAction);
        /** 🖼 while recording: insert "wait for this image". */
        void onImageDuringRecording();
        void onMenu(View anchor);
        void onSpeedMenu(View anchor);
        void onPanelMoved();
        /** Preview: skip the current wait. */
        void onSkip();
        /** 🔥 Turbo: your taps become fast bursts where you touch. */
        void onTurbo();
    }

    enum Mode { MANUAL, RECORD, COMBO }
    enum State { IDLE, PLAYING, PAUSED, RECORDING, WAITING }

    private final Context ctx;
    private final WindowManager wm;
    private final Callbacks cb;
    private final Prefs prefs;
    private final DragLayout root;
    private final WindowManager.LayoutParams lp;
    private Mode mode = Mode.MANUAL;
    private State state = State.IDLE;
    private TextView info;
    private TextView speedChip;
    private String infoText = "";
    private double speed = 1.0;
    private boolean preview;
    private boolean compact;

    FloatingPanel(Context ctx, WindowManager wm, Prefs prefs, Callbacks cb) {
        this.ctx = ctx;
        this.wm = wm;
        this.cb = cb;
        this.prefs = prefs;
        root = new DragLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(ctx, 5);
        root.setPadding(p, p, p, p);
        android.graphics.drawable.GradientDrawable panelBg = Ui.rounded(0xF2191F29, Ui.dp(ctx, 16));
        panelBg.setStroke(Ui.dp(ctx, 1), 0xFF34404F);
        root.setBackground(panelBg);
        lp = Overlays.params(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true, false);
        lp.x = prefs.panelX();
        lp.y = prefs.panelY();
    }

    void show() {
        root.setAlpha(prefs.panelOpacity() / 100f);
        Point screen = Overlays.screenSize(ctx);
        int btn = Ui.dp(ctx, prefs.panelButtonDp());
        lp.x = clamp(lp.x, 0, screen.x - btn * 4);
        lp.y = clamp(lp.y, 0, screen.y - btn * 2);
        build();
        Overlays.safeAdd(wm, root, lp);
    }

    void hide() { Overlays.safeRemove(wm, root); }

    boolean isShown() { return Overlays.isAdded(root); }

    /** Re-add so it is above windows added later (e.g. the recording layer). */
    void bringToFront() {
        if (!isShown()) return;
        Overlays.safeRemove(wm, root);
        Overlays.safeAdd(wm, root, lp);
    }

    void setMode(Mode m) {
        if (mode == m) return;
        mode = m;
        build();
    }

    Mode mode() { return mode; }

    /** A setting changed which buttons the panel shows (e.g. 🔥 Turbo). */
    void refresh() { if (root != null && root.getChildCount() > 0) build(); }

    void setState(State s) {
        if (state == s && root.getChildCount() > 0) return;
        state = s;
        if (s == State.IDLE) infoText = "";
        build();
    }

    State state() { return state; }

    /**
     * Runs that read the screen (colours, images): the panel shrinks in place to ■ and ❚❚, so it
     * hides as little of the screen as possible (what it covers cannot be seen by the search).
     */
    void setCompact(boolean c) {
        if (compact == c) return;
        compact = c;
        build();
    }

    /** Preview runs show an eye and a ⏭ (skip the current wait) instead of the speed chip. */
    void setPreview(boolean p) {
        if (preview == p) return;
        preview = p;
        build();
    }

    private boolean ear;

    /** 👂 in front of the info while a script listens to your fingers (the "I am waiting for you" signal). */
    void setEar(boolean on) {
        if (ear == on) return;
        ear = on;
        setInfo(infoText);
    }

    void setInfo(String s) {
        infoText = s == null ? "" : s;
        if (infoText.startsWith("👂 ")) infoText = infoText.substring(3);
        if (ear) infoText = "👂 " + infoText;
        if (info != null) {
            info.setText(infoText);
            info.setVisibility(infoText.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    void setSpeed(double s) {
        speed = s;
        if (speedChip != null) speedChip.setText(formatSpeed(s));
    }

    static String formatSpeed(double s) {
        String t = String.format(Locale.US, "%.2f", s);
        while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t + "×";
    }

    boolean isTouchable() {
        return (lp.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0 && root.getVisibility() == View.VISIBLE;
    }

    void setTouchable(boolean t) { Overlays.setTouchable(wm, root, t); }

    /** Hide (window becomes invisible to input as well) without losing state. */
    void setHidden(boolean hidden) { root.setVisibility(hidden ? View.GONE : View.VISIBLE); }

    private long touchedAt = -1;

    /** When a finger last went down on the panel (the touch-listening safety check). */
    long touchedAt() { return touchedAt; }

    /** The panel's rectangle on screen {left, top, right, bottom}, or null if it is not visible. */
    int[] screenRect() {
        if (!isShown() || root.getVisibility() != View.VISIBLE || root.getWidth() == 0) return null;
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        return new int[]{loc[0], loc[1], loc[0] + root.getWidth(), loc[1] + root.getHeight()};
    }

    /** True if any point of the gesture lands on (or right next to) the panel. */
    boolean intersects(GesturePlan plan) {
        if (!isShown() || root.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        int m = Ui.dp(ctx, 12);
        Rect r = new Rect(loc[0] - m, loc[1] - m, loc[0] + root.getWidth() + m, loc[1] + root.getHeight() + m);
        for (GesturePlan.PlannedStroke s : plan.strokes) {
            for (float[] pt : s.points) if (r.contains((int) pt[0], (int) pt[1])) return true;
            for (int i = 1; i < s.points.size(); i++) { // a swipe can cross the panel
                float[] a = s.points.get(i - 1), b = s.points.get(i);
                for (int k = 1; k < 8; k++) {
                    float t = k / 8f;
                    if (r.contains((int) (a[0] + (b[0] - a[0]) * t), (int) (a[1] + (b[1] - a[1]) * t))) return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ building
    private void build() {
        root.removeAllViews();
        info = null;
        speedChip = null;
        LinearLayout row = Ui.horizontal(ctx);
        root.addView(row);
        switch (state) {
            case IDLE:
                icon(row, PanelIcon.Kind.PLAY, "Pornește", new View.OnClickListener() { public void onClick(View v) { cb.onPlay(); } });
                if (mode == Mode.MANUAL) {
                    icon(row, PanelIcon.Kind.PLUS, "Adaugă țintă", new View.OnClickListener() { public void onClick(View v) { cb.onAddMenu(v); } });
                    minus(row, "Șterge ultima țintă (ține apăsat: șterge tot)");
                } else if (mode == Mode.RECORD) {
                    icon(row, PanelIcon.Kind.RECORD, "Înregistrează", new View.OnClickListener() { public void onClick(View v) { cb.onRecordToggle(); } });
                    minus(row, "Șterge ultima acțiune înregistrată (ține apăsat: șterge tot)");
                }
                if (mode != Mode.COMBO && prefs.turboButton()) {
                    icon(row, PanelIcon.Kind.TURBO, "Turbo: atingi și dă rafale exact acolo", new View.OnClickListener() { public void onClick(View v) { cb.onTurbo(); } });
                }
                icon(row, PanelIcon.Kind.MENU, "Meniu", new View.OnClickListener() { public void onClick(View v) { cb.onMenu(v); } });
                break;
            case PLAYING:
            case PAUSED:
                icon(row, PanelIcon.Kind.STOP, "Stop", new View.OnClickListener() { public void onClick(View v) { cb.onStop(); } });
                icon(row, state == State.PLAYING ? PanelIcon.Kind.PAUSE : PanelIcon.Kind.PLAY,
                        state == State.PLAYING ? "Pauză" : "Continuă",
                        new View.OnClickListener() { public void onClick(View v) { cb.onPauseToggle(); } });
                if (preview) {
                    icon(row, PanelIcon.Kind.SKIP, "Sari peste așteptare", new View.OnClickListener() { public void onClick(View v) { cb.onSkip(); } });
                    icon(row, PanelIcon.Kind.EYE, "Previzualizare: nimic nu se atinge de-adevăratelea", new View.OnClickListener() { public void onClick(View v) {
                        android.widget.Toast.makeText(ctx, "Previzualizare: atingerile doar se desenează", android.widget.Toast.LENGTH_SHORT).show();
                    } });
                }
                if (!compact || preview) speedChip = chip(row, formatSpeed(speed), new View.OnClickListener() { public void onClick(View v) { cb.onSpeedMenu(v); } });
                break;
            case RECORDING:
                icon(row, PanelIcon.Kind.RECORD_STOP, "Oprește înregistrarea", new View.OnClickListener() { public void onClick(View v) { cb.onRecordToggle(); } });
                break;
            case WAITING:
                icon(row, PanelIcon.Kind.STOP, "Anulează", new View.OnClickListener() { public void onClick(View v) { cb.onStop(); } });
                break;
        }
        // While something plays (or is about to), the screen must not go to sleep: long waits
        // would otherwise let it turn off and screen reading would fail.
        root.setKeepScreenOn(state == State.PLAYING || state == State.WAITING);
        info = Ui.text(ctx, infoText, 12, 0xFFFFFFFF, true);
        info.setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 6), 0);
        info.setVisibility(infoText.isEmpty() ? View.GONE : View.VISIBLE);
        row.addView(info);

        if (state == State.RECORDING) { // system keys, recorded too
            LinearLayout nav = Ui.horizontal(ctx);
            icon(nav, PanelIcon.Kind.BACK, "Înapoi", new View.OnClickListener() { public void onClick(View v) { cb.onGlobalDuringRecording(GlobalActions.BACK); } });
            icon(nav, PanelIcon.Kind.HOME, "Acasă", new View.OnClickListener() { public void onClick(View v) { cb.onGlobalDuringRecording(GlobalActions.HOME); } });
            icon(nav, PanelIcon.Kind.RECENTS, "Aplicații recente", new View.OnClickListener() { public void onClick(View v) { cb.onGlobalDuringRecording(GlobalActions.RECENTS); } });
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                icon(nav, PanelIcon.Kind.SCREENSHOT, "Captură de ecran", new View.OnClickListener() { public void onClick(View v) { cb.onGlobalDuringRecording(GlobalActions.TAKE_SCREENSHOT); } });
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                icon(nav, PanelIcon.Kind.IMAGE, "Așteaptă aici o imagine (decupează un buton)", new View.OnClickListener() { public void onClick(View v) { cb.onImageDuringRecording(); } });
            }
            root.addView(nav);
        }
    }

    /** A button of the panel as it is now (for the tutorial), or null. */
    View button(PanelIcon.Kind kind) { return find(root, kind); }

    View rootView() { return root; }

    private static View find(View v, PanelIcon.Kind kind) {
        if (v instanceof PanelIcon && ((PanelIcon) v).kind() == kind && v.isShown()) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = find(g.getChildAt(i), kind);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void icon(LinearLayout row, PanelIcon.Kind kind, final String description, View.OnClickListener l) {
        int size = Ui.dp(ctx, prefs.panelButtonDp());
        PanelIcon b = new PanelIcon(ctx, kind);
        b.setContentDescription(description);
        Ui.clickable(b, l, size / 2f);
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                android.widget.Toast.makeText(ctx, description, android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.leftMargin = Ui.dp(ctx, 2);
        p.rightMargin = Ui.dp(ctx, 2);
        row.addView(b, p);
    }

    /** "−": tap removes the last one, long-press clears everything (with a confirmation). */
    private void minus(LinearLayout row, String description) {
        icon(row, PanelIcon.Kind.MINUS, description, new View.OnClickListener() { public void onClick(View v) { cb.onRemoveLast(); } });
        View b = row.getChildAt(row.getChildCount() - 1);
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { cb.onClearAll(); return true; }
        });
    }

    private TextView chip(LinearLayout row, String text, View.OnClickListener l) {
        TextView t = Ui.text(ctx, text, 13, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(ctx, 14)));
        t.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 6), Ui.dp(ctx, 10), Ui.dp(ctx, 6));
        Ui.clickable(t, l, Ui.dp(ctx, 14));
        t.setContentDescription("Viteză de redare");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Ui.dp(ctx, 4);
        p.rightMargin = Ui.dp(ctx, 2);
        row.addView(t, p);
        return t;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(Math.max(lo, hi), v)); }

    /** The whole bar moves when dragged; short taps still reach the buttons. */
    private final class DragLayout extends LinearLayout {
        private final int slop;
        private float downX, downY;
        private int startX, startY;
        private boolean dragging;

        DragLayout(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchedAt = e.getEventTime();
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = lp.x; startY = lp.y;
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > slop) dragging = true;
                    return dragging;
                default:
                    return false;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = lp.x; startY = lp.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (!dragging && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > slop) dragging = true;
                    if (dragging) {
                        Point screen = Overlays.screenSize(ctx);
                        lp.x = clamp(startX + Math.round(e.getRawX() - downX), 0, screen.x - getWidth());
                        lp.y = clamp(startY + Math.round(e.getRawY() - downY), 0, screen.y - getHeight());
                        Overlays.safeUpdate(wm, this, lp);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        prefs.setPanelPos(lp.x, lp.y);
                        cb.onPanelMoved();
                    }
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
