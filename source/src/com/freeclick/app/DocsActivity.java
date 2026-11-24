package com.freeclick.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.freeclick.core.script.Docs;
import com.freeclick.core.script.ScriptRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 📖 The documentation (2.3), in the style of Godot's help: search at the top, the chapters with every
 * function and setting, and a page for each: how to call it, other names, what it does, an example
 * (copy it, or put it in your code when opened from the editor) and links to the related pages.
 */
public final class DocsActivity extends Activity {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_CATEGORY = "category";
    /** Opened from the code editor: the example can be put into the code (returned as EXTRA_INSERT). */
    public static final String EXTRA_FROM_EDITOR = "from_editor";
    public static final String EXTRA_INSERT = "insert";

    private LinearLayout content;
    private ScrollView scroll;
    private TextView title;
    private String query = "";
    private final List<String> history = new ArrayList<String>(); // "" = the chapters, "#Cat" = a chapter, else a page
    private boolean fromEditor;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        fromEditor = getIntent().getBooleanExtra(EXTRA_FROM_EDITOR, false);
        LinearLayout root = Ui.vertical(this);
        LinearLayout header = Ui.header(this, "Documentație", false, false, new View.OnClickListener() {
            @Override public void onClick(View v) { back(); }
        }, null);
        title = (TextView) header.getChildAt(1);
        root.addView(header);
        LinearLayout searchRow = Ui.vertical(this);
        searchRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6));
        searchRow.addView(Ui.searchField(this, new Ui.OnText() {
            @Override public void onText(String s) {
                query = s.trim();
                if (query.isEmpty()) show(history.isEmpty() ? "" : history.get(history.size() - 1), false);
                else showSearch();
            }
        }), Ui.matchWrap());
        root.addView(searchRow);
        scroll = new ScrollView(this);
        content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        String cat = getIntent().getStringExtra(EXTRA_CATEGORY);
        Docs.Entry e = name == null ? null : Docs.find(name);
        if (e != null) show(e.id, true);
        else if (cat != null) show("#" + cat, true);
        else show("", true);
        if (name != null && e == null) Toast.makeText(this, "„" + name + "” nu are o pagină (e o variabilă sau o funcție de-a ta?)", Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ navigation
    private void show(String where, boolean push) {
        if (push && (history.isEmpty() || !history.get(history.size() - 1).equals(where))) history.add(where);
        content.removeAllViews();
        if (where.isEmpty()) showChapters();
        else if (where.startsWith("#")) showChapter(where.substring(1));
        else showPage(Docs.find(where));
        scroll.post(new Runnable() { public void run() { scroll.scrollTo(0, 0); } });
    }

    private void open(String name) {
        Docs.Entry e = Docs.find(name);
        if (e == null) return;
        show(e.id, true);
    }

    private void back() {
        if (history.size() > 1) {
            history.remove(history.size() - 1);
            show(history.get(history.size() - 1), false);
        } else {
            finish();
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { back(); }

    // ------------------------------------------------------------------ the chapters
    private static final String[] CHAPTER_ICONS = {"GO", "◎", "◷", "IMG", "↗", "≡", "TXT", "123", "AI", "SET"};

    private void showChapters() {
        title.setText("Documentație");
        content.addView(Ui.muted(this, "Fiecare funcție și setare are o pagină. Caută sus (merge orice nume: tap, click, wait_for_touch…) "
                + "sau alege un capitol."));
        List<String> cats = Docs.categories();
        for (int i = 0; i < cats.size(); i++) {
            final String c = cats.get(i);
            List<Docs.Entry> in = Docs.inCategory(c);
            StringBuilder names = new StringBuilder();
            for (int k = 0; k < in.size() && k < 6; k++) names.append(k > 0 ? " · " : "").append(in.get(k).id);
            if (in.size() > 6) names.append(" …");
            content.addView(Ui.modeCard(this, CHAPTER_ICONS[i % CHAPTER_ICONS.length], Ui.ACCENT, c,
                    in.size() + (in.size() == 1 ? " pagină: " : " pagini: ") + names, false, new View.OnClickListener() {
                        @Override public void onClick(View v) { show("#" + c, true); }
                    }));
        }
    }

    private void showChapter(String c) {
        title.setText(c);
        for (Docs.Entry e : Docs.inCategory(c)) content.addView(row(e));
    }

    private void showSearch() {
        content.removeAllViews();
        title.setText("Caut „" + query + "”");
        List<Docs.Entry> found = Docs.search(query);
        if (found.isEmpty()) content.addView(Ui.muted(this, "Nimic. Încearcă alt nume (de ex. „atinge”, „culoare”, „degete”)."));
        for (int i = 0; i < found.size() && i < 60; i++) content.addView(row(found.get(i)));
    }

    /** A line of a list: the name (code-like for functions) and its one-line summary. */
    private View row(final Docs.Entry e) {
        LinearLayout r = Ui.vertical(this);
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        r.setBackground(Ui.surface(this, Ui.SURFACE, 14));
        boolean fn = !e.signatures().isEmpty();
        TextView n = Ui.text(this, fn ? e.id + "()" : e.id, 16, fn ? 0xFF82AAFF : Ui.TEXT, true);
        if (fn) n.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        r.addView(n);
        TextView s = Ui.text(this, plain(e.summary), 13, Ui.MUTED, false);
        s.setMaxLines(2);
        r.addView(s);
        Ui.clickable(r, new View.OnClickListener() {
            @Override public void onClick(View v) { show(e.id, true); }
        }, Ui.dp(this, 14));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        r.setLayoutParams(lp);
        return r;
    }

    // ------------------------------------------------------------------ a page
    private void showPage(final Docs.Entry e) {
        if (e == null) { showChapters(); return; }
        title.setText(e.id);
        TextView cat = Ui.text(this, e.category, 12, Ui.ACCENT, true);
        Ui.clickable(cat, new View.OnClickListener() {
            @Override public void onClick(View v) { show("#" + e.category, true); }
        }, Ui.dp(this, 8));
        content.addView(cat);
        List<String> sigs = e.signatures();
        TextView h = Ui.text(this, sigs.isEmpty() ? e.id : e.id + "()", 24, Ui.TEXT, true);
        if (!sigs.isEmpty()) h.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        h.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 8));
        content.addView(h);
        if (!sigs.isEmpty()) {
            LinearLayout box = Ui.vertical(this);
            box.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            box.setBackground(Ui.surface(this, Ui.BG, 12));
            for (String s : sigs) {
                TextView t = Ui.text(this, "", 14, Ui.TEXT, false);
                t.setTypeface(Typeface.MONOSPACE);
                t.setText(colourCode(s));
                t.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
                box.addView(t);
            }
            content.addView(box, Ui.matchWrap());
            List<String> al = e.aliases();
            if (!al.isEmpty()) {
                TextView a = Ui.muted(this, "Se mai scrie: " + join(al));
                a.setPadding(0, Ui.dp(this, 6), 0, 0);
                content.addView(a);
            }
        }
        for (String para : e.body.split("\n")) {
            if (para.trim().isEmpty()) continue;
            TextView t = Ui.text(this, "", 15, Ui.TEXT, false);
            t.setLineSpacing(0, 1.15f);
            t.setText(rich(para));
            t.setMovementMethod(LinkMovementMethod.getInstance());
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            content.addView(t);
        }
        if (!e.example.isEmpty()) {
            content.addView(sectionLabel("Exemplu"));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            TextView code = Ui.text(this, "", 13, Ui.TEXT, false);
            code.setTypeface(Typeface.MONOSPACE);
            code.setText(colourCode(e.example));
            code.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            hs.addView(code);
            hs.setBackground(Ui.surface(this, Ui.BG, 12));
            content.addView(hs, Ui.matchWrap());
            LinearLayout actions = Ui.horizontal(this);
            actions.addView(Ui.button(this, "Copiază", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FreeClick", e.example));
                    Toast.makeText(DocsActivity.this, "Copiat", Toast.LENGTH_SHORT).show();
                }
            }));
            if (fromEditor) {
                actions.addView(Ui.colored(this, "↪ Pune în cod", Ui.ACCENT, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        setResult(RESULT_OK, new Intent().putExtra(EXTRA_INSERT, e.example));
                        finish();
                    }
                }));
            }
            content.addView(actions);
        }
        if (!e.see.isEmpty()) {
            content.addView(sectionLabel("Vezi și"));
            LinearLayout chips = Ui.vertical(this);
            for (final String s : e.see) {
                Docs.Entry t = Docs.find(s);
                if (t == null) continue;
                TextView c = Ui.text(this, (t.signatures().isEmpty() ? t.id : t.id + "()") + "  ·  " + plain(t.summary), 14, Ui.TEXT, false);
                c.setSingleLine(true);
                c.setEllipsize(android.text.TextUtils.TruncateAt.END);
                c.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
                c.setBackground(Ui.surface(this, Ui.SURFACE, 12));
                Ui.clickable(c, new View.OnClickListener() {
                    @Override public void onClick(View v) { open(s); }
                }, Ui.dp(this, 12));
                LinearLayout.LayoutParams lp = Ui.matchWrap();
                lp.setMargins(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
                chips.addView(c, lp);
            }
            content.addView(chips);
        }
    }

    private TextView sectionLabel(String s) {
        TextView t = Ui.sectionLabel(this, s);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        return t;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(", "); b.append(s); }
        return b.toString();
    }

    /** The text without the [[ ]] and ` marks (for one-line summaries). */
    private static String plain(String s) { return s.replace("[[", "").replace("]]", "").replace("`", ""); }

    /** [[links]] become tappable, `code` becomes monospace. */
    private CharSequence rich(String s) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        int i = 0;
        while (i < s.length()) {
            int link = s.indexOf("[[", i), tick = s.indexOf('`', i);
            if (link < 0 && tick < 0) { b.append(s.substring(i)); break; }
            if (link >= 0 && (tick < 0 || link < tick)) {
                int end = s.indexOf("]]", link + 2);
                if (end < 0) { b.append(s.substring(i)); break; }
                b.append(s.substring(i, link));
                final String name = s.substring(link + 2, end);
                int st = b.length();
                b.append(name);
                b.setSpan(new ClickableSpan() {
                    @Override public void onClick(View w) { open(name); }
                    @Override public void updateDrawState(TextPaint p) { p.setColor(Ui.ACCENT); p.setUnderlineText(true); }
                }, st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end + 2;
            } else {
                int end = s.indexOf('`', tick + 1);
                if (end < 0) { b.append(s.substring(i)); break; }
                b.append(s.substring(i, tick));
                int st = b.length();
                b.append(s.substring(tick + 1, end));
                b.setSpan(new TypefaceSpan("monospace"), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.setSpan(new BackgroundColorSpan(0x33FFFFFF), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end + 1;
            }
        }
        return b;
    }

    // ------------------------------------------------------------------ code colours (as in the editor)
    private static final Pattern TOKENS = Pattern.compile(
            "//[^\\n]*|\"(\\\\.|[^\"\\\\\\n])*\"?|#[0-9A-Fa-f]{6}\\b|\\b\\d+(\\.\\d+)?(ms|s|min|h)?\\b|#?[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> KW = new HashSet<String>(Arrays.asList(ScriptRunner.KEYWORDS));
    private static final Set<String> TY = new HashSet<String>(Arrays.asList(ScriptRunner.TYPES));
    private static final Set<String> FN = ScriptRunner.functionNames();

    static CharSequence colourCode(String src) {
        SpannableStringBuilder b = new SpannableStringBuilder(src);
        Matcher m = TOKENS.matcher(src);
        while (m.find()) {
            String t = m.group();
            int col;
            if (t.startsWith("//")) col = 0xFF6F8F72;
            else if (t.startsWith("\"")) col = 0xFFF5C26B;
            else if (t.startsWith("#define") || t.startsWith("#undef")) col = 0xFFC792EA;
            else if (t.startsWith("#") && t.length() == 7) col = 0xFF000000 | Integer.parseInt(t.substring(1), 16);
            else if (Character.isDigit(t.charAt(0))) col = 0xFFFF9E64;
            else if (KW.contains(t)) col = 0xFFC792EA;
            else if (TY.contains(t)) col = Ui.ACCENT;
            else if (FN.contains(t)) col = 0xFF82AAFF;
            else continue;
            b.setSpan(new ForegroundColorSpan(col), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }
}
