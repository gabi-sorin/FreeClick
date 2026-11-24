package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** UI kit built in code: graphite surfaces, teal actions, list rows, dialogs and popup menus. */
public final class Ui {
    private Ui() {}

    // ---------------------------------------------------------------- palette
    public static final int BG = 0xFF101319;
    public static final int SURFACE = 0xFF191F29;      // cards, search, panel
    public static final int SURFACE_HIGH = 0xFF252F3D; // raised cards, dialogs, popups
    public static final int ACCENT = 0xFF42D6B0;       // FreeClick teal
    public static final int ON_ACCENT = 0xFF082D28;
    public static final int LABEL = 0xFF9AA5B5;        // section labels
    public static final int TEXT = 0xFFF5F7FA;
    public static final int MUTED = 0xFF9AA5B5;
    public static final int DIVIDER = 0x332F3B4A;
    public static final int DANGER = 0xFFF15B66;
    // Older names kept for existing screens.
    public static final int PRIMARY = ACCENT;
    public static final int OK = ACCENT;
    public static final int CARD = SURFACE;

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /**
     * Back from any sub-screen: return to the home screen. If the screen was opened
     * from the floating panel with no home screen behind it, open the home screen
     * instead of leaving the app.
     */
    public static void goBack(Activity a) {
        if (a.isTaskRoot() && !(a instanceof MainActivity)) {
            a.startActivity(new android.content.Intent(a, MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        a.finish();
    }

    /** Status/navigation bar colours matching the screen. */
    /** A soft top-to-bottom light on every screen (deeper at the bottom), not a flat colour. */
    public static final int BG_TOP = 0xFF121820, BG_BOTTOM = 0xFF0B0E13;

    public static void styleWindow(Activity a) {
        a.getWindow().setStatusBarColor(BG_TOP);
        a.getWindow().setNavigationBarColor(BG_BOTTOM);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG, BG_BOTTOM});
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            a.getWindow().setStatusBarColor(BG_TOP);
            a.getWindow().setNavigationBarColor(BG_BOTTOM);
            a.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        a.getWindow().setBackgroundDrawable(g);
    }

    /** A card surface: slightly lighter than the background, a thin light edge, round corners. */
    public static GradientDrawable surface(Context c, int color, float radiusDp) {
        GradientDrawable g = rounded(color, dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), 0x1FFFFFFF);
        return g;
    }

    // ---------------------------------------------------------------- basic views
    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 20, TEXT, true);
        t.setPadding(0, dp(c, 18), 0, dp(c, 8));
        return t;
    }

    public static TextView muted(Context c, String s) { return text(c, s, 13, MUTED, false); }

    /** "APP MODES"-style uppercase section label. */
    public static TextView sectionLabel(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 11, LABEL, true);
        t.setLetterSpacing(0.22f);
        t.setPadding(dp(c, 2), dp(c, 20), 0, dp(c, 10));
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    public static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** Touch ripple over an optional background. */
    public static Drawable ripple(Drawable content, float radius) {
        GradientDrawable mask = rounded(Color.WHITE, radius);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    public static void clickable(View v, View.OnClickListener l, float radius) {
        v.setBackground(ripple(v.getBackground(), radius));
        v.setClickable(true);
        v.setOnClickListener(l);
    }

    public static Button button(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setStateListAnimator(null);
        b.setBackground(ripple(surface(c, SURFACE_HIGH, 14), dp(c, 14)));
        b.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 3), dp(c, 4), dp(c, 3), dp(c, 4));
        b.setLayoutParams(lp);
        return b;
    }

    public static Button colored(Context c, String s, int color, View.OnClickListener l) {
        Button b = button(c, s, l);
        b.setTextColor(color == ACCENT ? ON_ACCENT : Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g;
        if (color == ACCENT) { // a little light on the main button
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF6DE8C6, ACCENT});
            g.setCornerRadius(dp(c, 14));
        } else {
            g = rounded(color, dp(c, 14));
        }
        b.setBackground(ripple(g, dp(c, 14)));
        return b;
    }

    public static View card(Context c, View content) {
        LinearLayout wrap = vertical(c);
        wrap.setBackground(surface(c, SURFACE, 18));
        int p = dp(c, 14);
        wrap.setPadding(p, p, p, p);
        wrap.addView(content);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        wrap.setLayoutParams(lp);
        return wrap;
    }

    /** Centered tip box, darker than the background. */
    public static View tipCard(Context c, String s) {
        TextView t = text(c, "i  " + s, 14, MUTED, false);
        t.setGravity(Gravity.START);
        t.setLineSpacing(0, 1.15f);
        t.setBackground(surface(c, 0x99100C3A, 16));
        int p = dp(c, 16);
        t.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(c, 14), 0, dp(c, 8));
        t.setLayoutParams(lp);
        return t;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---------------------------------------------------------------- screen parts
    /**
     * Top bar: optional left icon (back arrow or hamburger), title, optional ⋮.
     * @param centered title centered (home screen) or left-aligned (sub screens).
     */
    public static LinearLayout header(final Activity a, String titleText, boolean hamburger, boolean centered,
                                      View.OnClickListener left, View.OnClickListener more) {
        LinearLayout bar = horizontal(a);
        bar.setMinimumHeight(dp(a, 64));
        bar.setPadding(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        if (left != null || !centered) {
            TextView l = iconText(a, hamburger ? "☰" : "←", 22);
            l.setBackground(rounded(SURFACE, dp(a, 16)));
            if (left != null) clickable(l, left, dp(a, 24));
            else {
                clickable(l, new View.OnClickListener() { public void onClick(View v) { goBack(a); } }, dp(a, 24));
            }
            bar.addView(l, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        }
        TextView t = text(a, titleText, 22, TEXT, true);
        t.setSingleLine(true);
        t.setGravity(centered ? Gravity.CENTER : Gravity.START | Gravity.CENTER_VERTICAL);
        t.setPadding(dp(a, centered ? 0 : 10), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (more != null) {
            TextView m = iconText(a, "⋮", 24);
            m.setBackground(rounded(SURFACE, dp(a, 16)));
            clickable(m, more, dp(a, 24));
            bar.addView(m, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        } else if (centered) {
            bar.addView(new View(a), new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        }
        return bar;
    }

    private static TextView iconText(Context c, String glyph, float sp) {
        TextView t = text(c, glyph, sp, TEXT, false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** Big uppercase action row (SAVE, SAVE AS, LOAD...). */
    public static TextView actionRow(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 15, TEXT, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        t.setBackground(surface(c, SURFACE, 14));
        if (l != null) clickable(t, l, dp(c, 14));
        else t.setAlpha(0.45f);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(c, 4), 0, dp(c, 4));
        t.setLayoutParams(lp);
        return t;
    }

    /** Title + value/description row. */
    public static LinearLayout settingRow(Context c, String title, String value, View.OnClickListener l) {
        LinearLayout row = horizontal(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(c, 13);
        row.setPadding(p, dp(c, 11), p, dp(c, 11));
        LinearLayout copy = vertical(c);
        copy.addView(text(c, title, 15, TEXT, true));
        if (value != null && value.length() > 0) {
            TextView detail = text(c, value, 12, MUTED, false);
            detail.setLineSpacing(0, 1.08f);
            copy.addView(detail);
        }
        row.addView(copy, weight(1));
        if (l != null) {
            TextView chevron = text(c, "›", 24, LABEL, false);
            chevron.setGravity(Gravity.CENTER);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 36)));
            row.setBackground(surface(c, SURFACE, 14));
            clickable(row, l, dp(c, 14));
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(c, 3), 0, dp(c, 3));
        row.setLayoutParams(lp);
        return row;
    }

    /** Title + description on the left, switch on the right. */
    public static Switch toggleRow(LinearLayout parent, String title, String desc, boolean checked,
                                   CompoundButton.OnCheckedChangeListener l) {
        Context c = parent.getContext();
        LinearLayout row = horizontal(c);
        int p = dp(c, 13);
        row.setPadding(p, dp(c, 11), p, dp(c, 11));
        row.setBackground(surface(c, SURFACE, 14));
        LinearLayout texts = vertical(c);
        texts.addView(text(c, title, 16, TEXT, true));
        if (desc != null && desc.length() > 0) texts.addView(text(c, desc, 12, MUTED, false));
        row.addView(texts, weight(1));
        final Switch s = new Switch(c);
        s.setChecked(checked);
        if (l != null) s.setOnCheckedChangeListener(l);
        row.addView(s);
        clickable(row, new View.OnClickListener() { public void onClick(View v) { s.toggle(); } }, dp(c, 14));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(c, 3), 0, dp(c, 3));
        row.setLayoutParams(lp);
        parent.addView(row, matchWrap());
        return s;
    }

    /** Selectable mode card (selected = darker card with a white dot). */
    public static View modeCard(Context c, String title, String desc, boolean selected, View.OnClickListener l) {
        return modeCard(c, null, 0, title, desc, selected, l);
    }

    /**
     * A mode card: a round coloured badge with an emoji, the title and one line of description,
     * on a card surface; the chosen one has an accent edge and a check.
     */
    public static View modeCard(Context c, String icon, int tint, String title, String desc, boolean selected, View.OnClickListener l) {
        LinearLayout card = horizontal(c);
        int p = dp(c, 14);
        card.setPadding(p, dp(c, 14), p, dp(c, 14));
        GradientDrawable bg = rounded(selected ? 0xFF1B2C34 : SURFACE, dp(c, 18));
        bg.setStroke(dp(c, selected ? 2 : 1), selected ? ACCENT : 0xFF252F3D);
        card.setBackground(bg);
        if (icon != null) {
            TextView badge = text(c, icon, 22, TEXT, false);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded((tint & 0x00FFFFFF) | 0x33000000, dp(c, 24)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48));
            bl.rightMargin = dp(c, 14);
            card.addView(badge, bl);
        }
        LinearLayout texts = vertical(c);
        texts.addView(text(c, title, 19, TEXT, true));
        TextView d = text(c, desc, 13, MUTED, false);
        d.setLineSpacing(0, 1.1f);
        d.setPadding(0, dp(c, 2), 0, 0);
        texts.addView(d);
        card.addView(texts, weight(1));
        TextView mark = text(c, selected ? "●" : "›", selected ? 14 : 26, selected ? ACCENT : MUTED, true);
        mark.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(c, 24), ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.leftMargin = dp(c, 8);
        card.addView(mark, dl);
        clickable(card, l, dp(c, 18));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(c, 5), 0, dp(c, 5));
        card.setLayoutParams(lp);
        return card;
    }

    /** Compact full-width mode row: clear task choice without oversized dashboard tiles. */
    public static View homeModeCard(Context c, String icon, int tint, String title, String desc,
                                    boolean selected, View.OnClickListener l) {
        LinearLayout card = horizontal(c);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(c, 12);
        card.setPadding(p, dp(c, 10), p, dp(c, 10));
        GradientDrawable bg = rounded(selected ? 0xFF1B2C34 : SURFACE, dp(c, 14));
        bg.setStroke(dp(c, selected ? 1.5f : 1), selected ? ACCENT : 0xFF252F3D);
        card.setBackground(bg);
        TextView glyph = text(c, icon, 20, tint, true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(rounded((tint & 0x00FFFFFF) | 0x33000000, dp(c, 20)));
        LinearLayout.LayoutParams glyphLp = new LinearLayout.LayoutParams(dp(c, 38), dp(c, 38));
        glyphLp.rightMargin = dp(c, 12);
        card.addView(glyph, glyphLp);
        LinearLayout copy = vertical(c);
        copy.addView(text(c, title, 15, TEXT, true));
        TextView detail = text(c, desc, 12, MUTED, false);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(detail);
        card.addView(copy, weight(1));
        TextView mark = text(c, selected ? "●" : "›", selected ? 12 : 23, selected ? ACCENT : MUTED, true);
        mark.setGravity(Gravity.CENTER);
        mark.setContentDescription(selected ? "Mod selectat" : "Deschide modulul");
        card.addView(mark, new LinearLayout.LayoutParams(dp(c, 28), dp(c, 36)));
        clickable(card, l, dp(c, 14));
        card.setMinimumHeight(dp(c, 62));
        return card;
    }

    /** Rounded search box; calls back on every change. */
    public static LinearLayout searchField(Context c, final OnText cb) {
        LinearLayout box = horizontal(c);
        box.setBackground(rounded(SURFACE, dp(c, 10)));
        box.setPadding(dp(c, 12), 0, dp(c, 4), 0);
        final EditText e = new EditText(c);
        e.setHint("Caută…");
        e.setSingleLine(true);
        e.setBackground(null);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) { }
            public void onTextChanged(CharSequence s, int a, int b, int c2) { }
            public void afterTextChanged(Editable s) { cb.onText(s.toString()); }
        });
        box.addView(e, weight(1));
        TextView x = iconText(c, "×", 18);
        clickable(x, new View.OnClickListener() { public void onClick(View v) { e.setText(""); } }, dp(c, 20));
        box.addView(x, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return box;
    }

    public interface OnText { void onText(String s); }

    /** Round floating "+" button. */
    public static TextView fab(Context c, View.OnClickListener l) {
        TextView f = text(c, "+", 30, ON_ACCENT, false);
        f.setGravity(Gravity.CENTER);
        f.setBackground(rounded(ACCENT, dp(c, 30)));
        f.setElevation(dp(c, 6));
        clickable(f, l, dp(c, 30));
        return f;
    }

    // ---------------------------------------------------------------- popup menu
    public interface OnPick { void onPick(int index); }

    /**
     * Dark rounded popup with icon + label rows, anchored to a view (works from
     * activities and from overlay windows). icons may be null or contain 0s.
     */
    public static PopupWindow popup(View anchor, List<String> labels, List<Integer> icons, final OnPick cb) {
        Context c = anchor.getContext();
        LinearLayout list = vertical(c);
        list.setBackground(surface(c, SURFACE_HIGH, 14));
        list.setPadding(0, dp(c, 6), 0, dp(c, 6));
        final PopupWindow pw = new PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            LinearLayout row = horizontal(c);
            row.setPadding(dp(c, 14), dp(c, 11), dp(c, 22), dp(c, 11));
            int icon = icons == null || i >= icons.size() || icons.get(i) == null ? 0 : icons.get(i);
            if (icon != 0) {
                ImageView iv = new ImageView(c);
                iv.setImageResource(icon);
                iv.setColorFilter(Color.WHITE);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
                ip.rightMargin = dp(c, 12);
                row.addView(iv, ip);
            }
            row.addView(text(c, labels.get(i), 15, TEXT, true));
            clickable(row, new View.OnClickListener() {
                @Override public void onClick(View v) { pw.dismiss(); cb.onPick(idx); }
            }, dp(c, 8));
            list.addView(row, matchWrap());
        }
        pw.setBackgroundDrawable(new ColorDrawable(0x00000000));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(c, 8));
        try {
            pw.showAsDropDown(anchor, 0, dp(c, 4));
        } catch (RuntimeException ignored) { }
        return pw;
    }

    // ---------------------------------------------------------------- dialogs
    public interface OnLong { void onValue(long v); }
    public interface OnString { void onValue(String s); }

    public static Context dialogContext(Context base) {
        return new android.view.ContextThemeWrapper(base, com.freeclick.R.style.FreeClick_Dialog);
    }

    private static AlertDialog show(AlertDialog d, boolean overlay) {
        if (overlay) return Overlays.showOverlayDialog(d);
        d.show();
        return d;
    }

    /**
     * Time input in ms with a unit drop-down (ms / s / min), like:
     * "Enter the time in milliseconds (or use the drop-down to convert seconds or minutes)".
     */
    public static AlertDialog timeDialog(Context base, String title, long valueMs, long minMs, long maxMs,
                                         final OnLong cb, boolean overlay) {
        Context c = dialogContext(base);
        LinearLayout box = vertical(c);
        int p = dp(c, 22);
        box.setPadding(p, dp(c, 4), p, 0);
        box.addView(text(c, "Introdu timpul în milisecunde (sau alege din listă secunde ori minute, "
                + "iar valoarea se convertește automat).", 14, MUTED, false));
        LinearLayout row = horizontal(c);
        final EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(String.valueOf(valueMs));
        e.setSelectAllOnFocus(true);
        row.addView(e, weight(1));
        final Spinner unit = new Spinner(c);
        unit.setAdapter(new ArrayAdapter<String>(c, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"ms", "s", "min"}));
        row.addView(unit);
        box.addView(row, matchWrap());
        final long[] factors = {1, 1000, 60000};
        AlertDialog d = new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton("SETEAZĂ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) {
                        double v;
                        try {
                            v = Double.parseDouble(e.getText().toString().trim().replace(',', '.'));
                        } catch (NumberFormatException ex) {
                            return;
                        }
                        long ms = Math.round(v * factors[unit.getSelectedItemPosition()]);
                        cb.onValue(Math.max(minMs, Math.min(maxMs, ms)));
                    }
                })
                .setNegativeButton("ANULEAZĂ", null).create();
        return show(d, overlay);
    }

    /** Whole-number input (counts). */
    public static AlertDialog numberDialog(Context base, String title, String help, long value, long min, long max,
                                           final OnLong cb, boolean overlay) {
        Context c = dialogContext(base);
        LinearLayout box = vertical(c);
        int p = dp(c, 22);
        box.setPadding(p, dp(c, 4), p, 0);
        if (help != null) box.addView(text(c, help, 14, MUTED, false));
        final EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        e.setSelectAllOnFocus(true);
        box.addView(e, matchWrap());
        AlertDialog d = new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton("SETEAZĂ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) {
                        cb.onValue(parseLong(e, value, min, max));
                    }
                })
                .setNegativeButton("ANULEAZĂ", null).create();
        return show(d, overlay);
    }

    /** Single-line text input. */
    public static AlertDialog textDialog(Context base, String title, String value, String ok,
                                         final OnString cb, boolean overlay) {
        Context c = dialogContext(base);
        FrameLayout box = new FrameLayout(c);
        int p = dp(c, 22);
        box.setPadding(p, dp(c, 8), p, 0);
        final EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(value == null ? "" : value);
        e.setSelectAllOnFocus(true);
        box.addView(e);
        AlertDialog d = new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) {
                        String s = e.getText().toString().trim();
                        if (!s.isEmpty()) cb.onValue(s);
                    }
                })
                .setNegativeButton("ANULEAZĂ", null).create();
        return show(d, overlay);
    }

    // ---------------------------------------------------------------- form rows (editor dialogs)
    /** "Label: [number]" row. */
    public static EditText numberRow(LinearLayout parent, String label, long value, boolean decimal) {
        Context c = parent.getContext();
        LinearLayout row = horizontal(c);
        TextView l = text(c, label, 15, TEXT, false);
        row.addView(l, weight(1));
        EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        e.setText(String.valueOf(value));
        e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.END);
        e.setMinWidth(dp(c, 96));
        row.addView(e);
        parent.addView(row, matchWrap());
        return e;
    }

    public static EditText decimalRow(LinearLayout parent, String label, double value) {
        EditText e = numberRow(parent, label, 0, true);
        String s = String.valueOf(value);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        e.setText(s);
        return e;
    }

    public static EditText textRow(LinearLayout parent, String label, String value) {
        Context c = parent.getContext();
        parent.addView(text(c, label, 13, MUTED, false));
        EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setText(value == null ? "" : value);
        parent.addView(e, matchWrap());
        return e;
    }

    public static Switch switchRow(LinearLayout parent, String label, boolean checked,
                                   CompoundButton.OnCheckedChangeListener l) {
        Context c = parent.getContext();
        Switch s = new Switch(c);
        s.setText(label);
        s.setTextColor(TEXT);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setChecked(checked);
        s.setPadding(0, dp(c, 8), 0, dp(c, 8));
        if (l != null) s.setOnCheckedChangeListener(l);
        parent.addView(s, matchWrap());
        return s;
    }

    public static long parseLong(EditText e, long def, long min, long max) {
        try {
            long v = Long.parseLong(e.getText().toString().trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public static double parseDouble(EditText e, double def, double min, double max) {
        try {
            double v = Double.parseDouble(e.getText().toString().trim().replace(',', '.'));
            if (Double.isNaN(v)) return def;
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /** "200 [ms]" / "1.5 [s]" style value text. */
    public static String msLabel(long ms) {
        if (ms < 10000) return ms + " [ms]";
        if (ms % 60000 == 0) return (ms / 60000) + " [min]";
        if (ms % 1000 == 0) return (ms / 1000) + " [s]";
        return ms + " [ms]";
    }

    /** Swallow unused-parameter warnings for listeners on spinners. */
    static AdapterView.OnItemSelectedListener noop() {
        return new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { }
            public void onNothingSelected(AdapterView<?> p) { }
        };
    }
}
