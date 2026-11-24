package com.freeclick.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.freeclick.core.PlaybackEngine;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Floating console: what "Afișează în consolă" steps write, what read steps read, warnings,
 * and (optionally) every step as it runs. Dragged by its title bar, folded into a one-line bar,
 * copied or cleared. While a macro runs it lets touches through (and is excluded from image
 * searches), so it never gets in the way of the taps.
 */
final class ConsoleLayer {
    static final int MAX_LINES = 200;

    private final Context ctx;
    private final WindowManager wm;
    private final Prefs prefs;
    private final ArrayDeque<String[]> lines = new ArrayDeque<String[]>(); // {time, text, kind}
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private LinearLayout root;
    private TextView body, stepsBtn, foldBtn, title;
    private ScrollView scroll;
    private WindowManager.LayoutParams lp;
    private boolean folded, running;
    private String lastLine;
    private int repeats;

    ConsoleLayer(Context ctx, WindowManager wm, Prefs prefs) {
        this.ctx = ctx;
        this.wm = wm;
        this.prefs = prefs;
    }

    boolean isShown() { return Overlays.isAdded(root); }

    void show() {
        if (isShown()) return;
        build();
        Overlays.safeAdd(wm, root, lp);
        prefs.setConsoleOpen(true);
        render();
        setRunning(running);
    }

    void hide() {
        Overlays.safeRemove(wm, root);
        prefs.setConsoleOpen(false);
    }

    void toggle() { if (isShown()) hide(); else show(); }

    /** Remove the window without forgetting that it was open (service stopping, panel off). */
    void detach() { Overlays.safeRemove(wm, root); }

    /** A line from a step (PRINT_USER), a reading (PRINT_INFO) or a warning (PRINT_WARN). */
    void add(String text, int kind) {
        if (text == null) return;
        String t = text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
        // the same line over and over (a loop): one line with a counter
        if (t.equals(lastLine) && !lines.isEmpty()) {
            repeats++;
            String[] last = lines.peekLast();
            last[0] = clock.format(new Date());
            last[1] = t + "  ×" + (repeats + 1);
        } else {
            repeats = 0;
            lines.addLast(new String[]{clock.format(new Date()), t, String.valueOf(kind)});
            while (lines.size() > MAX_LINES) lines.removeFirst();
        }
        lastLine = t;
        if (isShown()) render();
    }

    void clear() {
        lines.clear();
        lastLine = null;
        if (isShown()) render();
    }

    /** While running: let touches through and fade a little. */
    void setRunning(boolean on) {
        running = on;
        if (!isShown()) return;
        Overlays.setTouchable(wm, root, !on);
        root.setAlpha(on ? 0.82f : 1f);
        if (title != null) title.setText(on ? "Consolă · rulează (atingerile trec prin ea)" : "⋮ Consolă");
    }

    /** Where it is on the screen {left, top, right, bottom}, or null if hidden. */
    int[] screenRect() {
        if (!isShown() || root.getWidth() == 0) return null;
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        return new int[]{loc[0], loc[1], loc[0] + root.getWidth(), loc[1] + root.getHeight()};
    }

    // ------------------------------------------------------------------ view
    private void build() {
        if (root != null) return;
        Context c = ctx;
        android.graphics.Point sz = Overlays.screenSize(c);
        root = Ui.vertical(c);
        root.setBackground(Ui.rounded(0xE6100C33, Ui.dp(c, 12)));
        int p = Ui.dp(c, 8);
        root.setPadding(p, p / 2, p, p);

        LinearLayout head = Ui.horizontal(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        title = Ui.text(c, "⋮ Consolă", 13, Ui.LABEL, true);
        head.addView(title, Ui.weight(1));
        stepsBtn = small(c, "≡", new View.OnClickListener() { public void onClick(View v) {
            prefs.setConsoleSteps(!prefs.consoleSteps());
            showStepsState();
            toast(prefs.consoleSteps() ? "Consola arată și fiecare pas" : "Consola arată doar mesajele");
        } });
        head.addView(stepsBtn);
        head.addView(small(c, "⧉", new View.OnClickListener() { public void onClick(View v) { copy(); } }));
        head.addView(small(c, "×", new View.OnClickListener() { public void onClick(View v) { clear(); } }));
        foldBtn = small(c, "—", new View.OnClickListener() { public void onClick(View v) { folded = !folded; applyFold(); } });
        head.addView(foldBtn);
        head.addView(small(c, "×", new View.OnClickListener() { public void onClick(View v) { hide(); } }));
        root.addView(head, Ui.matchWrap());
        drag(head);
        drag(title);

        scroll = new ScrollView(c);
        body = new TextView(c);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(12);
        body.setTextColor(Ui.TEXT);
        body.setTextIsSelectable(false);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(sz.y * 0.26f)));

        int w = Math.round(Math.min(sz.x, sz.y) * 0.86f);
        lp = Overlays.params(w, ViewGroup.LayoutParams.WRAP_CONTENT, true, false);
        int x = prefs.consoleX(), y = prefs.consoleY();
        lp.x = x >= 0 ? Math.min(x, Math.max(0, sz.x - w)) : (sz.x - w) / 2;
        lp.y = y >= 0 ? Math.min(y, Math.max(0, sz.y - Ui.dp(c, 60))) : Math.round(sz.y * 0.12f);
        showStepsState();
        applyFold();
    }

    private TextView small(Context c, String s, View.OnClickListener l) {
        TextView t = Ui.text(c, s, 15, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        int w = Ui.dp(c, 34);
        t.setLayoutParams(new LinearLayout.LayoutParams(w, w));
        Ui.clickable(t, l, w / 2f);
        return t;
    }

    private void showStepsState() {
        if (stepsBtn != null) stepsBtn.setAlpha(prefs.consoleSteps() ? 1f : 0.4f);
    }

    private void applyFold() {
        if (scroll == null) return;
        LinearLayout.LayoutParams l = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        android.graphics.Point sz = Overlays.screenSize(ctx);
        l.height = folded ? Ui.dp(ctx, 20) : Math.round(sz.y * 0.26f);
        scroll.setLayoutParams(l);
        foldBtn.setText(folded ? "□" : "—");
        render();
    }

    private void drag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            int sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = Math.max(0, sx + Math.round(e.getRawX() - dx));
                        lp.y = Math.max(0, sy + Math.round(e.getRawY() - dy));
                        Overlays.safeUpdate(wm, root, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        prefs.setConsolePos(lp.x, lp.y);
                        return true;
                }
                return false;
            }
        });
    }

    private void render() {
        if (body == null) return;
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (lines.isEmpty()) {
            b.append("Gol. Aici apar mesajele pașilor „Afișează în consolă”, ce citesc pașii „Citește” și avertismentele.");
            b.setSpan(new ForegroundColorSpan(Ui.MUTED), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        java.util.Iterator<String[]> it = folded ? lastOnly() : lines.iterator();
        while (it.hasNext()) {
            String[] l = it.next();
            if (b.length() > 0) b.append('\n');
            int st = b.length();
            b.append(l[0]).append(' ');
            b.setSpan(new ForegroundColorSpan(Ui.LABEL), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int ts = b.length();
            b.append(l[1]);
            int kind = Integer.parseInt(l[2]);
            int col = kind == PlaybackEngine.PRINT_WARN ? 0xFFFFA4B4 : kind == PlaybackEngine.PRINT_INFO ? Ui.MUTED : Ui.TEXT;
            b.setSpan(new ForegroundColorSpan(col), ts, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        body.setText(b);
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }

    private java.util.Iterator<String[]> lastOnly() {
        java.util.List<String[]> l = new java.util.ArrayList<String[]>();
        if (!lines.isEmpty()) l.add(lines.peekLast());
        return l.iterator();
    }

    private void copy() {
        StringBuilder b = new StringBuilder();
        for (String[] l : lines) b.append(l[0]).append(' ').append(l[1]).append('\n');
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FreeClick", b.toString()));
        toast(lines.size() + " rânduri copiate");
    }

    private void toast(String s) { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show(); }
}
