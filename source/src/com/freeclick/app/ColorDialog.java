package com.freeclick.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.freeclick.core.ColorMatch;

/**
 * Exact colour: big swatch, #HEX field, R/G/B and H/S/V sliders with numbers (all kept in sync),
 * the tolerance, and "🎯 Alege de pe ecran" when the caller can pick on the screen.
 */
final class ColorDialog {
    interface OnColor { void onColor(int rgb, int tolerance); }

    private final View swatch;
    private final EditText hex;
    private final SeekBar[] bars = new SeekBar[6];
    private final EditText[] nums = new EditText[6];
    private SeekBar tolBar;
    private TextView tolLabel, info;
    private int rgb;
    private int tol;
    private boolean updating;

    private static final String[] NAMES = {"R", "G", "B", "H", "S", "V"};
    private static final int[] MAX = {255, 255, 255, 360, 100, 100};

    /**
     * @param tolerance -1 hides the tolerance (plain colour)
     * @param pickOnScreen null if picking on the screen is not possible here
     */
    static AlertDialog show(Context base, String title, int rgb, int tolerance, final Runnable pickOnScreen,
                            final OnColor cb, boolean overlay) {
        final Context c = Ui.dialogContext(base);
        final ColorDialog d = new ColorDialog(c, rgb, tolerance);
        AlertDialog.Builder b = new AlertDialog.Builder(c).setTitle(title).setView(d.view)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) { cb.onColor(d.rgb, d.tol); }
                })
                .setNegativeButton("ANULEAZĂ", null);
        if (pickOnScreen != null) {
            b.setNeutralButton("Alege de pe ecran", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dlg, int w) { pickOnScreen.run(); }
            });
        }
        AlertDialog dlg = b.create();
        if (overlay) return Overlays.showOverlayDialog(dlg);
        dlg.show();
        return dlg;
    }

    private final ScrollView view;

    private ColorDialog(Context c, int rgb, int tolerance) {
        this.rgb = rgb & 0xFFFFFF;
        this.tol = tolerance;
        view = new ScrollView(c);
        LinearLayout f = Ui.vertical(c);
        int p = Ui.dp(c, 20);
        f.setPadding(p, Ui.dp(c, 6), p, 0);
        view.addView(f);

        LinearLayout top = Ui.horizontal(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        swatch = new View(c);
        top.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
        LinearLayout right = Ui.vertical(c);
        right.setPadding(Ui.dp(c, 14), 0, 0, 0);
        right.addView(Ui.muted(c, "Cod hex"));
        hex = new EditText(c);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        right.addView(hex, Ui.matchWrap());
        info = Ui.text(c, "", 12, Ui.MUTED, false);
        right.addView(info);
        top.addView(right, Ui.weight(1));
        f.addView(top, Ui.matchWrap());
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            public void onTextChanged(CharSequence s, int a, int b, int d) { }
            public void afterTextChanged(Editable e) {
                if (updating) return;
                String t = e.toString().trim();
                String h = t.startsWith("#") ? t.substring(1) : t;
                if ((h.length() == 6 || h.length() == 3) && h.matches("[0-9A-Fa-f]+")) set(ColorMatch.parseHex(t), 0);
            }
        });

        f.addView(Ui.sectionLabel(c, "RGB"));
        for (int i = 0; i < 3; i++) slider(c, f, i);
        f.addView(Ui.sectionLabel(c, "HSV (nuanță, saturație, luminozitate)"));
        for (int i = 3; i < 6; i++) slider(c, f, i);

        if (tolerance >= 0) {
            f.addView(Ui.sectionLabel(c, "Toleranță"));
            tolLabel = Ui.text(c, "", 14, Ui.TEXT, false);
            f.addView(tolLabel);
            tolBar = new SeekBar(c);
            tolBar.setMax(128);
            tolBar.setProgress(Math.min(128, tolerance));
            f.addView(tolBar, Ui.matchWrap());
            tolBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int v, boolean user) { tol = v; showTolerance(); }
                public void onStartTrackingTouch(SeekBar s) { }
                public void onStopTrackingTouch(SeekBar s) { }
            });
            showTolerance();
        }
        set(this.rgb, -1);
    }

    private void slider(Context c, LinearLayout f, final int i) {
        LinearLayout row = Ui.horizontal(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(c, NAMES[i], 14, Ui.TEXT, true);
        row.addView(name, new LinearLayout.LayoutParams(Ui.dp(c, 22), LinearLayout.LayoutParams.WRAP_CONTENT));
        final SeekBar bar = new SeekBar(c);
        bar.setMax(MAX[i]);
        row.addView(bar, Ui.weight(1));
        final EditText num = new EditText(c);
        num.setInputType(InputType.TYPE_CLASS_NUMBER);
        num.setSingleLine(true);
        num.setGravity(Gravity.END);
        num.setSelectAllOnFocus(true);
        row.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 64), LinearLayout.LayoutParams.WRAP_CONTENT));
        f.addView(row, Ui.matchWrap());
        bars[i] = bar;
        nums[i] = num;
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { if (user && !updating) fromChannel(i, v); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        num.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            public void onTextChanged(CharSequence s, int a, int b, int d) { }
            public void afterTextChanged(Editable e) {
                if (updating) return;
                try {
                    int v = Math.max(0, Math.min(MAX[i], Integer.parseInt(e.toString().trim())));
                    fromChannel(i, v);
                } catch (NumberFormatException ignored) { }
            }
        });
    }

    /** One slider / number changed: recompute the colour from its group (RGB or HSV). */
    private void fromChannel(int i, int v) {
        int[] cur = values(rgb);
        cur[i] = v;
        int c;
        if (i < 3) c = (cur[0] << 16) | (cur[1] << 8) | cur[2];
        else c = Color.HSVToColor(new float[]{Math.min(359.99f, cur[3]), cur[4] / 100f, cur[5] / 100f}) & 0xFFFFFF;
        set(c, i < 3 ? 1 : 2, i, v);
    }

    private static int[] values(int rgb) {
        float[] hsv = new float[3];
        Color.RGBToHSV((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, hsv);
        return new int[]{(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255,
                Math.round(hsv[0]) % 360, Math.round(hsv[1] * 100), Math.round(hsv[2] * 100)};
    }

    private void set(int c, int source) { set(c, source, -1, 0); }

    /**
     * Show colour c everywhere. source: 0 = typed hex (do not rewrite the field), 1 = RGB, 2 = HSV,
     * -1 = initial. The control being moved keeps its own value (no jumping while you drag).
     */
    private void set(int c, int source, int keepIndex, int keepValue) {
        updating = true;
        rgb = c & 0xFFFFFF;
        swatch.setBackground(Ui.rounded(0xFF000000 | rgb, Ui.dp(swatch.getContext(), 12)));
        if (source != 0) hex.setText(ColorMatch.toHex(rgb));
        int[] v = values(rgb);
        if (keepIndex >= 0) v[keepIndex] = keepValue;
        for (int i = 0; i < 6; i++) {
            if (i == keepIndex) continue;
            bars[i].setProgress(v[i]);
            if (!nums[i].hasFocus() || source == 0 || (source == 1) != (i < 3)) nums[i].setText(String.valueOf(v[i]));
        }
        if (keepIndex >= 0) {
            bars[keepIndex].setProgress(keepValue);
            if (!nums[keepIndex].hasFocus()) nums[keepIndex].setText(String.valueOf(keepValue));
        }
        info.setText("RGB " + v[0] + ", " + v[1] + ", " + v[2] + " · HSV " + v[3] + "°, " + v[4] + "%, " + v[5] + "%");
        updating = false;
    }

    private void showTolerance() {
        if (tolLabel == null) return;
        String how = tol == 0 ? "exact" : tol <= 12 ? "foarte strictă" : tol <= 32 ? "normală" : tol <= 64 ? "largă" : "foarte largă";
        tolLabel.setText(tol + " · " + how + " (cât poate diferi fiecare canal R, G, B)");
    }
}
