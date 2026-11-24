package com.freeclick.app;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A drop-down group of settings: a header ("▸ 📍 Locație · 540, 1200") that opens and closes
 * the settings under it. Closed by default; the app remembers which ones you left open.
 * Sections can hold other sections ("⚙ Avansat" → one per area).
 */
final class Section {
    final LinearLayout root;
    /** Put the settings of this group here. */
    final LinearLayout body;
    private final TextView arrow, summaryView;
    private final String key;
    private boolean open;

    private Section(LinearLayout parent, String key, String title, String summary, boolean nested) {
        Context c = parent.getContext();
        this.key = key;
        root = Ui.vertical(c);
        LinearLayout head = Ui.horizontal(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 12);
        head.setPadding(p, p - 2, p, p - 2);
        head.setBackground(Ui.surface(c, nested ? Ui.SURFACE_HIGH : Ui.SURFACE, 14));
        arrow = Ui.text(c, "▸", 15, Ui.ACCENT, true);
        arrow.setPadding(0, 0, Ui.dp(c, 10), 0);
        head.addView(arrow);
        LinearLayout texts = Ui.vertical(c);
        texts.addView(Ui.text(c, title, 15, Ui.TEXT, true));
        summaryView = Ui.text(c, "", 12, Ui.MUTED, false);
        summaryView.setSingleLine(true);
        summaryView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(summaryView);
        head.addView(texts, Ui.weight(1));
        Ui.clickable(head, new View.OnClickListener() {
            @Override public void onClick(View v) { setOpen(!open, true); }
        }, Ui.dp(c, 10));
        LinearLayout.LayoutParams hl = Ui.matchWrap();
        hl.topMargin = Ui.dp(c, nested ? 4 : 8);
        root.addView(head, hl);
        body = Ui.vertical(c);
        body.setPadding(Ui.dp(c, nested ? 10 : 8), Ui.dp(c, 5), 0, Ui.dp(c, 6));
        root.addView(body, Ui.matchWrap());
        parent.addView(root, Ui.matchWrap());
        setSummary(summary);
        setOpen(App.get().prefs().sectionOpen(key), false);
    }

    /** A group at the top level of a form or screen. */
    static Section add(LinearLayout parent, String key, String title, String summary) {
        return new Section(parent, key, title, summary, false);
    }

    /** A group inside another group (lighter header). */
    static Section nested(LinearLayout parent, String key, String title, String summary) {
        return new Section(parent, key, title, summary, true);
    }

    void setSummary(String s) {
        boolean has = s != null && s.length() > 0;
        summaryView.setText(has ? s : "");
        summaryView.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    void setOpen(boolean o, boolean remember) {
        open = o;
        arrow.setText(o ? "▾" : "▸");
        body.setVisibility(o ? View.VISIBLE : View.GONE);
        if (remember) App.get().prefs().setSectionOpen(key, o);
    }

    /** Hide the whole group (e.g. nothing in it applies to this step). */
    void hideIfEmpty() {
        if (body.getChildCount() == 0) root.setVisibility(View.GONE);
    }
}
