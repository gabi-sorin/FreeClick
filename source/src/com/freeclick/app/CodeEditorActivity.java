package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;
import com.freeclick.core.ColorMatch;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.Macro;
import com.freeclick.core.MacroCodec;
import com.freeclick.core.script.ScriptError;
import com.freeclick.core.script.ScriptExamples;
import com.freeclick.core.script.ScriptRunner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Cod": write a script (C++-like), with line numbers, colours, mistakes shown as you type,
 * buttons that write code from what you choose on the screen, breakpoints (tap a line number),
 * ▶ run, 👣 step by step, examples, the list of functions and a "tap to continue" tutorial.
 */
public final class CodeEditorActivity extends Activity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_ERROR_LINE = "errorLine";
    public static final String EXTRA_ERROR = "error";

    /** Breakpoints per script, kept while the app runs. */
    private static final Map<String, Set<Integer>> BREAKPOINTS = new HashMap<String, Set<Integer>>();
    /** The script open last (coming back from choosing on the screen brings no id). */
    private static String lastId;

    /** For the panel's "📷 Captură pentru cod". */
    static String lastScriptId() { return lastId; }

    private static final int REQ_GALLERY = 41;
    private static final int REQ_DOCS = 42;

    private MacroRepository repo;
    private Macro macro;
    private EditText edit;
    private TextView gutter, status, title;
    private LinearLayout insertBar;
    private View runBtn, stepBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean applying;
    private int errorLine = -1;
    private int runtimeErrorLine = -1;
    private String runtimeError;
    private int pendingCursor = -1;
    private TextView moreBtn;

    // ---- 2.0: the picture pane (screenshot of the game next to the code)
    private LinearLayout root, shotPane, shotChips, toolsRow, simBar;
    private View divider;
    private ShotView shotView;
    private TextView paneBtn, marksAll, marksMore, simInfo;
    private LinearLayout marksBox;
    private android.widget.SeekBar simSeek;
    private ScrollView codeScroll;
    private final java.util.List<TextView> toolChips = new java.util.ArrayList<TextView>();
    private final java.util.List<int[]> modelPixels = new java.util.ArrayList<int[]>();
    private TextView modelDone;
    private java.util.List<ShotStore.Shot> shots = new java.util.ArrayList<ShotStore.Shot>();
    private ShotStore.Shot shot;
    private ShotTools shotTools;
    private java.util.List<com.freeclick.core.script.CodeMarks.Mark> marks = new java.util.ArrayList<com.freeclick.core.script.CodeMarks.Mark>();
    private String marksSource = "";
    private String imageNames = "";
    private int shotGen, selGen;
    private com.freeclick.core.script.ScriptSimulator.Result simResult;
    private boolean shapeWarned;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private LinearLayout bottomBar, suggestRow;
    private HorizontalScrollView suggestScroll;
    private TextView hintBar;
    private int tallestRoot, rootWidth;
    private boolean keyboardOpen;

    /** The code editor's text box: tells when the cursor moves (the picture lights the place on that line). */
    static final class CodeEdit extends EditText {
        Runnable onSelection;
        CodeEdit(Context c) { super(c); }
        @Override protected void onSelectionChanged(int start, int end) {
            super.onSelectionChanged(start, end);
            if (onSelection != null) onSelection.run();
        }
    }

    /** A new script: the example to start with, and the panel's current targets as its list of steps. */
    static Macro newScript(Context c, String name) {
        Macro m = new Macro(name);
        m.kind = Macro.KIND_SCRIPT;
        m.settings.loops = 1;
        Point sz = Overlays.screenSize(c);
        m.screenWidth = sz.x;
        m.screenHeight = sz.y;
        Macro.Settings st = new Macro.Settings();
        try {
            String d = App.get().prefs().draft(Macro.KIND_MANUAL);
            if (d != null) {
                Macro panel = MacroCodec.fromJson(d);
                st = panel.settings;
                for (Action a : panel.actions) m.actions.add(a.copy()); // tree-ok: top-level list
            }
        } catch (RuntimeException ignored) { }
        m.script = ScriptExamples.settingsHeader(st.tapDuration, st.swipeDuration, st.tapInterval) + ScriptExamples.NEW_SCRIPT;
        return m;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        repo = App.get().repo();
        if (!open(getIntent())) {
            Toast.makeText(this, "Scriptul nu mai există", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Ui.styleWindow(this);
        build();
        readErrorExtras(getIntent());
        // shown at every opening until "Nu mai afișa" (not again after a rotation)
        if (b == null && !App.get().prefs().sectionOpen("tutorial.code.v3")) {
            handler.postDelayed(new Runnable() { public void run() { tutorial(); } }, 400);
        }
    }

    /** Loads the script named by the intent (or the one open last). */
    private boolean open(Intent i) {
        String id = i.getStringExtra(EXTRA_ID);
        if (id == null) id = lastId;
        Macro src = id == null ? null : repo.find(id);
        if (src == null || !Macro.KIND_SCRIPT.equals(src.kind)) return false;
        macro = MacroCodec.fromJson(MacroCodec.toJson(src));
        lastId = macro.id;
        return true;
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String id = i.getStringExtra(EXTRA_ID);
        if (id != null && macro != null && !id.equals(macro.id)) { // another script: save this one, show that one
            save(false);
            if (!open(i)) { finish(); return; }
            runtimeError = null;
            runtimeErrorLine = -1;
            build();
        }
        readErrorExtras(i);
    }

    private void readErrorExtras(Intent i) {
        int line = i.getIntExtra(EXTRA_ERROR_LINE, -1);
        String msg = i.getStringExtra(EXTRA_ERROR);
        if (msg != null) {
            runtimeErrorLine = line;
            runtimeError = msg;
            i.removeExtra(EXTRA_ERROR);
            handler.post(new Runnable() { public void run() { recheck(); goToLine(runtimeErrorLine); } });
        }
    }

    // ------------------------------------------------------------------ layout
    private void build() {
        LinearLayout root = Ui.vertical(this);
        LinearLayout header = Ui.header(this, macro.name, false, false, null, new View.OnClickListener() {
            @Override public void onClick(View v) { more(v); }
        });
        title = (TextView) header.getChildAt(1);
        Ui.clickable(title, new View.OnClickListener() { public void onClick(View v) { rename(); } }, Ui.dp(this, 8));
        moreBtn = (TextView) header.getChildAt(header.getChildCount() - 1);
        paneBtn = Ui.text(this, "🖼", 20, Ui.TEXT, false);
        paneBtn.setGravity(Gravity.CENTER);
        Ui.clickable(paneBtn, new View.OnClickListener() { public void onClick(View v) { togglePane(); } }, Ui.dp(this, 24));
        header.addView(paneBtn, header.getChildCount() - 1, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 48)));
        root.addView(header);
        this.root = root;

        status = Ui.text(this, "", 13, Ui.MUTED, false);
        int p = Ui.dp(this, 12);
        status.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6));
        status.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 10)));
        Ui.clickable(status, new View.OnClickListener() {
            @Override public void onClick(View v) { goToLine(runtimeErrorLine > 0 ? runtimeErrorLine : errorLine); }
        }, 0);
        root.addView(status, Ui.matchWrap());

        buildShotPane(root);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        insertBar = Ui.horizontal(this);
        insertBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        chip("📖", new View.OnClickListener() { public void onClick(View v) {
            String w = wordAtCursor();
            docs(w != null && com.freeclick.core.script.Docs.find(w) != null ? w : null);
        } });
        // one "+" instead of six: what to pick on the real screen (or a step from the list) opens from it
        chip("+ 📱 Din ecran ▾", new View.OnClickListener() { public void onClick(final View v) {
            Ui.popup(v, java.util.Arrays.asList("Atingere", "Swipe", "Așteaptă o culoare", "Zonă", "Imagine", "Pas din listă…"), null, new Ui.OnPick() {
                @Override public void onPick(int i) {
                    if (i == 5) { insertStep(); return; }
                    int[] what = {ClickService.EDITOR_POINT, ClickService.EDITOR_SWIPE, ClickService.EDITOR_COLOR_WAIT, ClickService.EDITOR_ZONE, ClickService.EDITOR_IMAGE};
                    fromScreen(what[i]);
                }
            });
        } });
        chip("if", snippet("if (conditie) {\n    \n}", 4));
        chip("while", snippet("while (true) {\n    \n    wait(500);\n}", 19));
        chip("for", snippet("for (int i = 0; i < 10; i++) {\n    \n}", 35));
        chip("funcție", snippet("void numeFunctie() {\n    \n}", 5));
        chip("print", snippet("print(\"\");", 7));
        chip("wait", snippet("wait(1s);", 9));
        chip("cand", snippet("cand (apare(\"imagine\")) {\n    \n}", 12));
        chip("struct", snippet("struct Nume {\n    int x;\n};", 7));
        hs.addView(insertBar);
        root.addView(hs, Ui.matchWrap());

        // editor: line numbers + code, scrolled together; long lines scroll sideways (numbers stay aligned)
        ScrollView sv = new ScrollView(this);
        codeScroll = sv;
        sv.setFillViewport(true);
        LinearLayout row = Ui.horizontal(this);
        gutter = new TextView(this);
        gutter.setTypeface(Typeface.MONOSPACE);
        gutter.setTextSize(14);
        gutter.setTextColor(Ui.LABEL);
        gutter.setGravity(Gravity.END);
        gutter.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        gutter.setBackgroundColor(Ui.SURFACE);
        gutter.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) toggleBreakpoint(e.getY());
                return true;
            }
        });
        row.addView(gutter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        CodeEdit ce = new CodeEdit(this);
        edit = ce;
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setTextSize(14);
        edit.setTextColor(Ui.TEXT);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setHorizontallyScrolling(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI | android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        edit.setBackgroundColor(0xFF12171E);
        edit.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        edit.setText(macro.script);
        ce.onSelection = new Runnable() { public void run() { selectionMoved(); } };
        edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { hintSoon(); }
        });
        row.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sv.addView(row);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        edit.addTextChangedListener(new TextWatcher() {
            int newlineAt = -1, braceAt = -1;
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                boolean typed = !applying && count == 1 && before == 0 && st < s.length();
                newlineAt = typed && s.charAt(st) == '\n' ? st : -1;
                braceAt = typed && s.charAt(st) == '}' ? st : -1;
            }
            public void afterTextChanged(Editable e) {
                if (applying) return;
                if (newlineAt >= 0) autoIndent(e, newlineAt);
                else if (braceAt >= 0) unindentBrace(e, braceAt);
                newlineAt = -1;
                braceAt = -1;
                runtimeErrorLine = -1;
                runtimeError = null;
                updateGutter();
                rescanMarks();
                hintSoon();
                handler.removeCallbacks(recheck);
                handler.postDelayed(recheck, 350);
            }
        });

        // names that start with what is being typed (tap one to write it)
        suggestScroll = new HorizontalScrollView(this);
        suggestScroll.setHorizontalScrollBarEnabled(false);
        suggestScroll.setBackgroundColor(Ui.SURFACE);
        suggestRow = Ui.horizontal(this);
        suggestRow.setPadding(Ui.dp(this, 6), Ui.dp(this, 3), Ui.dp(this, 6), Ui.dp(this, 3));
        suggestScroll.addView(suggestRow);
        suggestScroll.setVisibility(View.GONE);
        root.addView(suggestScroll, Ui.matchWrap());

        // what the function being written expects (like a C++ IDE), right above the keyboard
        hintBar = Ui.text(this, "", 13, Ui.TEXT, false);
        hintBar.setTypeface(Typeface.MONOSPACE);
        hintBar.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        hintBar.setBackgroundColor(Ui.SURFACE_HIGH);
        hintBar.setMaxLines(3);
        hintBar.setVisibility(View.GONE);
        hintBar.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { // 📖 the page of the function being written
            String t = hintBar.getText().toString();
            int paren = t.indexOf('(');
            int sp = paren > 0 ? t.lastIndexOf(' ', paren) : -1;
            String name = paren > 0 ? t.substring(sp + 1, paren).trim() : null;
            docs(name != null && !name.isEmpty() ? name : null);
        } });
        root.addView(hintBar, Ui.matchWrap());

        LinearLayout bottom = Ui.horizontal(this);
        bottomBar = bottom;
        bottom.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 10));
        runBtn = Ui.colored(this, "▶ Rulează", Ui.ACCENT, new View.OnClickListener() { public void onClick(View v) { run(false); } });
        bottom.addView(runBtn, Ui.weight(1));
        stepBtn = Ui.button(this, "👣 Pas cu pas", new View.OnClickListener() { public void onClick(View v) { run(true); } });
        bottom.addView(stepBtn, Ui.weight(1));
        bottom.addView(Ui.button(this, "💾", new View.OnClickListener() { public void onClick(View v) { save(true); } }));
        root.addView(bottom, Ui.matchWrap());
        setContentView(root);
        updateGutter();
        recheck();
        rescanMarks();
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = b - t, w = r - l;
                if (w != rootWidth) { rootWidth = w; tallestRoot = h; } // turned: measure again
                tallestRoot = Math.max(tallestRoot, h);
                final boolean kb = h < tallestRoot * 0.78f; // the keyboard took the bottom of the screen
                if (kb != keyboardOpen) {
                    keyboardOpen = kb;
                    handler.post(new Runnable() { public void run() { applyKeyboard(); } });
                } else if (h != ob - ot) {
                    handler.post(new Runnable() { public void run() { fitPane(); } });
                }
            }
        });
        loadShots();
    }

    private void chip(String text, View.OnClickListener l) {
        TextView t = Ui.text(this, text, 14, text.startsWith("+") ? Ui.ACCENT : Ui.TEXT, true);
        int p = Ui.dp(this, 10);
        t.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6));
        t.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 14)));
        Ui.clickable(t, l, Ui.dp(this, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 6);
        insertBar.addView(t, lp);
    }

    private View.OnClickListener snippet(final String code, final int cursorInside) {
        return new View.OnClickListener() { public void onClick(View v) { insert(code, cursorInside); } };
    }

    // ------------------------------------------------------------------ editing helpers
    /** Insert code at the cursor (on its own line, indented like the line it goes into). */
    private void insert(String code, int cursorInside) {
        if (cursorInside < 0) cursorInside = code.length(); // -1: after the inserted code
        Editable e = edit.getText();
        int at = Math.max(0, edit.getSelectionStart());
        int lineStart = at;
        while (lineStart > 0 && e.charAt(lineStart - 1) != '\n') lineStart--;
        StringBuilder indent = new StringBuilder();
        for (int i = lineStart; i < e.length() && (e.charAt(i) == ' ' || e.charAt(i) == '\t'); i++) indent.append(e.charAt(i));
        int end = lineEnd(e, at);
        boolean blankLine = e.subSequence(lineStart, end).toString().trim().isEmpty();
        String text = code.replace("\n", "\n" + indent);
        // on an empty line the code goes right there; otherwise on a new line below the cursor's line
        String prefix = blankLine ? "" : "\n" + indent;
        e.insert(end, prefix + text);
        int cur = end + prefix.length() + indentAdjust(code, cursorInside, indent.length());
        edit.setSelection(Math.max(0, Math.min(e.length(), cur)));
        edit.requestFocus();
    }

    /** Position inside the inserted text, counting the indentation added to its later lines. */
    private static int indentAdjust(String code, int cursorInside, int indent) {
        int lines = 0;
        for (int i = 0; i < Math.min(cursorInside, code.length()); i++) if (code.charAt(i) == '\n') lines++;
        return cursorInside + lines * indent;
    }

    private static int lineEnd(Editable e, int at) {
        int i = Math.max(0, at);
        while (i < e.length() && e.charAt(i) != '\n') i++;
        return i;
    }

    /** Enter keeps the indentation of the line above (one level more after "{"). */
    private void autoIndent(Editable e, int nl) {
        int prevStart = nl;
        while (prevStart > 0 && e.charAt(prevStart - 1) != '\n') prevStart--;
        StringBuilder indent = new StringBuilder();
        for (int i = prevStart; i < nl && (e.charAt(i) == ' ' || e.charAt(i) == '\t'); i++) indent.append(e.charAt(i));
        String prev = e.subSequence(prevStart, nl).toString().trim();
        if (prev.endsWith("{")) indent.append("    ");
        if (indent.length() == 0) return;
        applying = true;
        try {
            e.insert(nl + 1, indent);
        } finally {
            applying = false;
        }
    }

    /** A "}" typed at the start of a line goes one level left (under its "{" line). */
    private void unindentBrace(Editable e, int at) {
        if (at > e.length() || at < 4) return;
        int lineStart = at;
        while (lineStart > 0 && e.charAt(lineStart - 1) != '\n') lineStart--;
        if (at - lineStart < 4) return;
        for (int i = lineStart; i < at; i++) if (e.charAt(i) != ' ') return;
        applying = true;
        try {
            e.delete(at - 4, at);
        } finally {
            applying = false;
        }
    }

    private void goToLine(int line) {
        if (line < 1) return;
        String t = edit.getText().toString();
        int pos = 0;
        for (int l = 1; l < line && pos >= 0; l++) { pos = t.indexOf('\n', pos); if (pos >= 0) pos++; }
        if (pos < 0) pos = t.length();
        edit.requestFocus();
        edit.setSelection(Math.min(pos, t.length()));
    }

    private void updateGutter() {
        int lines = 1;
        CharSequence t = edit.getText();
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == '\n') lines++;
        Set<Integer> bp = breakpoints();
        int digits = String.valueOf(lines).length();
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            if (i > 1) b.append('\n');
            String n = String.valueOf(i);
            b.append(bp.contains(i) ? "●" : " ");
            for (int k = n.length(); k < digits; k++) b.append(' ');
            b.append(n);
        }
        gutter.setText(b.toString());
    }

    private Set<Integer> breakpoints() {
        Set<Integer> s = BREAKPOINTS.get(macro.id);
        if (s == null) { s = new HashSet<Integer>(); BREAKPOINTS.put(macro.id, s); }
        return s;
    }

    private void toggleBreakpoint(float y) {
        Layout l = edit.getLayout();
        if (l == null) return;
        int line = l.getLineForVertical((int) (y - edit.getPaddingTop())) + 1;
        Set<Integer> bp = breakpoints();
        if (!bp.remove(line)) bp.add(line);
        updateGutter();
        Toast.makeText(this, bp.contains(line) ? "● Oprire la linia " + line + " (▶ sau 👣 se opresc aici)" : "Oprirea de la linia " + line + " a fost scoasă",
                Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ colours and mistakes
    private final Runnable recheck = new Runnable() { public void run() { recheck(); } };

    private static final Pattern TOKENS = Pattern.compile(
            "//[^\\n]*|/\\*[\\s\\S]*?(\\*/|$)|\"(\\\\.|[^\"\\\\\\n])*\"?|'(\\\\.|[^'\\\\\\n])*'?|#[0-9A-Fa-f]{6}\\b|#[0-9A-Fa-f]{3}\\b|\\b\\d+(\\.\\d+)?(ms|s|min|h)?\\b|[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> KW = new HashSet<String>(java.util.Arrays.asList(ScriptRunner.KEYWORDS));
    private static final Set<String> TY = new HashSet<String>(java.util.Arrays.asList(ScriptRunner.TYPES));
    private static final Set<String> FN = ScriptRunner.functionNames();

    private void recheck() {
        String src = edit.getText().toString();
        ScriptError err = ScriptRunner.check(src);
        errorLine = err == null ? -1 : err.line;
        if (runtimeError != null) {
            status.setText("❌ " + runtimeError + "  (atinge ca să mergi la linie)");
            status.setTextColor(0xFFFFA4B4);
        } else if (err == null) {
            int lines = src.isEmpty() ? 0 : src.split("\n", -1).length;
            status.setText("✅ Fără greșeli · " + lines + (lines == 1 ? " linie" : " linii") + " · " + macro.actions.size() + " pași în listă"); // tree-ok: pasi indexes the top-level list
            status.setTextColor(Ui.ACCENT);
        } else {
            status.setText("⚠ " + err.describe() + "  (atinge ca să mergi la linie)");
            status.setTextColor(0xFFFFC66D);
        }
        colour(src, err == null ? runtimeErrorLine : err.line);
    }

    private void colour(String src, int badLine) {
        Editable e = edit.getText();
        applying = true;
        try {
            for (ForegroundColorSpan s : e.getSpans(0, e.length(), ForegroundColorSpan.class)) e.removeSpan(s);
            for (BackgroundColorSpan s : e.getSpans(0, e.length(), BackgroundColorSpan.class)) e.removeSpan(s);
            if (src.length() > 200000) return; // huge text: no colours, stays fast
            java.util.Map<String, String> defs = src.indexOf("#define") >= 0 ? ScriptRunner.defines(src)
                    : java.util.Collections.<String, String>emptyMap();
            Matcher m = TOKENS.matcher(src);
            while (m.find()) {
                String t = m.group();
                int col;
                if ((t.equals("define") || t.equals("undef")) && m.start() > 0 && src.charAt(m.start() - 1) == '#') {
                    e.setSpan(new ForegroundColorSpan(0xFFC792EA), m.start() - 1, m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    continue;
                }
                if (defs.containsKey(t)) t = defs.get(t); // "#define p point": p is coloured like point
                if (t.startsWith("//") || t.startsWith("/*")) col = 0xFF6F8F72;
                else if (t.startsWith("\"") || t.startsWith("'")) col = 0xFFF5C26B;
                else if (t.startsWith("#")) {
                    int c = ColorMatch.parseHex(t);
                    e.setSpan(new BackgroundColorSpan(0x55000000 | c), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    col = 0xFF000000 | c;
                    int lum = (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000;
                    if (lum < 60) col = 0xFFB0B0B0;
                } else if (t.isEmpty()) continue;
                else if (Character.isDigit(t.charAt(0))) col = 0xFFFF9E64;
                else if (KW.contains(t)) col = 0xFFC792EA;
                else if (TY.contains(t)) col = Ui.ACCENT;
                else if (FN.contains(t)) col = 0xFF82AAFF;
                else if (t.equals("pasi")) col = 0xFFF78C6C;
                else continue;
                e.setSpan(new ForegroundColorSpan(col), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (badLine > 0) {
                int start = 0;
                for (int l = 1; l < badLine && start >= 0; l++) { start = src.indexOf('\n', start); if (start >= 0) start++; }
                if (start >= 0 && start <= src.length()) {
                    int end = src.indexOf('\n', start);
                    if (end < 0) end = src.length();
                    if (end > start) e.setSpan(new BackgroundColorSpan(0x55F43F5E), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } finally {
            applying = false;
        }
    }

    // ------------------------------------------------------------------ save / run
    private boolean save(boolean toastIt) {
        macro.script = edit.getText().toString();
        try {
            Macro stored = repo.find(macro.id);
            if (stored != null) { // the list of steps may have been edited meanwhile in the step editor
                macro.actions.clear();
                for (Action a : stored.actions) macro.actions.add(a.copy()); // tree-ok: keep the saved list
            }
            repo.save(macro);
            if (toastIt) Toast.makeText(this, "Salvat", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception ex) {
            Toast.makeText(this, "Eroare la salvare: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override protected void onPause() {
        if (macro != null && edit != null) save(false);
        ClickService s = ClickService.get();
        if (s != null) { s.onOptionsScreen(false); s.codeEditorInFront(false); }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        ClickService s = ClickService.get();
        if (s == null || macro == null) return;
        s.onOptionsScreen(true);
        s.cancelReadyBar();
        s.codeEditorInFront(true);
        Macro stored = repo.find(macro.id);
        if (stored != null) { macro.actions.clear(); for (Action a : stored.actions) macro.actions.add(a.copy()); } // tree-ok: fresh list
        if (pendingCursor >= 0 && pendingCursor <= edit.getText().length()) edit.setSelection(pendingCursor);
        pendingCursor = -1;
        Action picked = s.takeEditorAction();
        if (picked != null) insertPicked(picked);
        final ImageSpec cut = s.takeEditorImage();
        if (cut != null) nameImage(cut);
        android.graphics.Bitmap shotTaken = s.takeEditorShot();
        if (shotTaken != null) addShotBitmap(shotTaken);
        else if (App.get().shots().list(macro.id).size() != shots.size()) loadShots(); // taken from the panel meanwhile
        recheck();
        updateStepsOnPicture();
        refreshImageHits(true);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void run(boolean step) {
        if (!save(false)) return;
        ScriptError err = ScriptRunner.check(macro.script);
        if (err != null) {
            goToLine(err.line);
            new AlertDialog.Builder(this).setTitle("Greșeală în script").setMessage(err.describe()).setPositiveButton("OK", null).show();
            return;
        }
        ClickService s = ClickService.get();
        if (s == null) { Toast.makeText(this, "Activează întâi FreeClick în Setări → Accesibilitate", Toast.LENGTH_LONG).show(); return; }
        // not started here (it would touch the editor or the home screen): it goes on the panel, ▶ there starts it in the game
        if (s.armScript(macro, step, new HashSet<Integer>(breakpoints()))) {
            moveTaskToBack(true);
            if (step) Toast.makeText(this, "Pas cu pas: după ▶, bara de sus arată linia; ⏭ trece la următoarea", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ code from the screen
    private void fromScreen(int what) {
        ClickService s = ClickService.get();
        if (s == null) { Toast.makeText(this, "Activează întâi FreeClick în Setări → Accesibilitate", Toast.LENGTH_LONG).show(); return; }
        if (android.os.Build.VERSION.SDK_INT < 30 && what == ClickService.EDITOR_SHOT) {
            Toast.makeText(this, "Pe Android mai vechi de 11: fă un screenshot normal în joc și alege-l din galerie.", Toast.LENGTH_LONG).show();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT < 30 && what != ClickService.EDITOR_POINT) {
            Toast.makeText(this, "Pe Android mai vechi de 11 se poate alege de pe ecran doar un punct (+ Atingere).", Toast.LENGTH_LONG).show();
            return;
        }
        save(false);
        pendingCursor = edit.getSelectionStart();
        moveTaskToBack(true);
        s.captureForEditor(what, CodeEditorActivity.class);
    }

    private void insertPicked(Action a) {
        String code;
        switch (a.type) {
            case TAP: code = "tap(point(" + Math.round(a.x) + ", " + Math.round(a.y) + "));"; break;
            case SWIPE: code = "swipe(point(" + Math.round(a.x) + ", " + Math.round(a.y) + "), point(" + Math.round(a.x2) + ", " + Math.round(a.y2) + "), 300);"; break;
            case WAIT_COLOR: code = "aproape(pixel(point(" + Math.round(a.x) + ", " + Math.round(a.y) + ")), " + ColorMatch.toHex(a.color) + ", 24)"; break;
            case READ: code = "rect(" + Math.round(a.x) + ", " + Math.round(a.y) + ", " + Math.round(a.x2) + ", " + Math.round(a.y2) + ")"; break;
            default: return;
        }
        boolean statement = code.endsWith(";");
        if (statement) insert(code, code.length());
        else { // an expression: right where the cursor is
            int at = Math.max(0, edit.getSelectionStart());
            edit.getText().insert(at, code);
            edit.setSelection(at + code.length());
        }
        Toast.makeText(this, "Am scris: " + code, Toast.LENGTH_SHORT).show();
    }

    /** A picture cut for the script: kept as a (disabled) image step of its list, used by name. */
    private void nameImage(ImageSpec spec) { nameImage(spec, false); }

    /** @param statement write "if (apare(…)) tap(gaseste(…));" on its own line instead of gaseste(…) at the cursor */
    private void nameImage(final ImageSpec spec, final boolean statement) {
        int n = 1;
        Set<String> used = new HashSet<String>();
        for (Action a : macro.actions) if (a.img != null && a.img.name != null) used.add(a.img.name.toLowerCase(java.util.Locale.ROOT)); // tree-ok
        while (used.contains("imagine" + n)) n++;
        Ui.textDialog(this, "Numele imaginii (îl folosești în cod)", "imagine" + n, "OK", new Ui.OnString() {
            @Override public void onValue(String name) {
                spec.name = name.trim().replace("\"", "");
                Action a = Action.image(spec);
                a.enabled = false; // only a picture for gaseste()/apare(); ruleaza(pasi) skips it
                a.label = "imagine pentru cod";
                Macro stored = repo.find(macro.id);
                if (stored != null) {
                    stored.actions.add(a);
                    try { repo.save(stored); } catch (Exception ignored) { }
                }
                macro.actions.add(a);
                if (statement) {
                    insert("if (apare(\"" + spec.name + "\")) tap(gaseste(\"" + spec.name + "\"));", -1);
                } else {
                    String code = "gaseste(\"" + spec.name + "\")";
                    int at = Math.max(0, edit.getSelectionStart());
                    edit.getText().insert(at, code);
                    edit.setSelection(at + code.length());
                }
                save(false);
                refreshImageHits(true);
            }
        }, false);
    }

    private void insertStep() {
        if (macro.actions.isEmpty()) {
            Toast.makeText(this, "Lista de pași e goală: ⋮ → Lista de pași ca s-o completezi (sau ia ținte din panou)", Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[macro.actions.size() + 1]; // tree-ok: pasi indexes the top-level list
        items[0] = "Toată lista: ruleaza(pasi);";
        for (int i = 0; i < macro.actions.size(); i++) items[i + 1] = "pasi[" + i + "] · " + ActionFormatter.describe(macro.actions.get(i), repo); // tree-ok: pasi indexes the top-level list
        new AlertDialog.Builder(this).setTitle("Pas din listă").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                String code = which == 0 ? "ruleaza(pasi);" : "ruleaza(pasi[" + (which - 1) + "]);";
                insert(code, code.length());
            }
        }).show();
    }

    // ------------------------------------------------------------------ menu
    private void more(View anchor) {
        // the 3 used most, the rest under "Avansat ›" (like the panel's ≡)
        java.util.List<String> labels = java.util.Arrays.asList("📖 Documentație", "Exemple", "📤 Partajează scriptul", "Avansat  ›");
        Ui.popup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int i) {
                switch (i) {
                    case 0: docs(null); break;
                    case 1: examples(); break;
                    case 2:
                        save(false);
                        Macro m = App.get().repo().find(macro.id);
                        ShareUi.share(CodeEditorActivity.this, java.util.Collections.singletonList(m != null ? m : macro), null);
                        break;
                    default: handler.post(new Runnable() { public void run() { advancedMore(anchor); } });
                }
            }
        });
    }

    private void advancedMore(View anchor) {
        java.util.List<String> labels = java.util.Arrays.asList("Lista de pași (pasi)", "Ia țintele din panou în listă", "Tutorial",
                "Salvează", "Redenumește", "Setările clickului sus în cod",
                shotPane.getVisibility() == View.VISIBLE ? "Ascunde captura (doar cod)" : "Arată captura jocului", "Ce marcaje văd pe captură");
        Ui.popup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int i) {
                switch (i) {
                    case 0: save(false); startActivity(new Intent(CodeEditorActivity.this, EditorActivity.class).putExtra(EditorActivity.EXTRA_ID, macro.id)); break;
                    case 1: takePanelTargets(); break;
                    case 2: tutorial(); break;
                    case 3: save(true); break;
                    case 4: rename(); break;
                    case 5: addSettingsHeader(); break;
                    case 6: togglePane(); break;
                    default:
                        if (shotPane.getVisibility() != View.VISIBLE) togglePane();
                        handler.post(new Runnable() { public void run() { marksPopup(); } });
                }
            }
        });
    }

    /** 📖 The documentation, open at a name (null = the start). */
    private void docs(String name) {
        Intent i = new Intent(this, DocsActivity.class).putExtra(DocsActivity.EXTRA_FROM_EDITOR, true);
        if (name != null) i.putExtra(DocsActivity.EXTRA_NAME, name);
        startActivityForResult(i, REQ_DOCS);
    }

    /** The word the cursor is on (or right after), e.g. "tap" in "ta|p(1, 2)". */
    private String wordAtCursor() {
        String t = edit.getText().toString();
        int c = Math.max(0, Math.min(t.length(), edit.getSelectionStart()));
        int a = c, b = c;
        while (a > 0 && (Character.isLetterOrDigit(t.charAt(a - 1)) || t.charAt(a - 1) == '_' || t.charAt(a - 1) == '#')) a--;
        while (b < t.length() && (Character.isLetterOrDigit(t.charAt(b)) || t.charAt(b) == '_')) b++;
        return a < b ? t.substring(a, b) : null;
    }

    /** DURATA_CLICK / DURATA_SWIPE / INTERVAL_REPETARE at the top, with the panel's current values. */
    private void addSettingsHeader() {
        if (ScriptExamples.hasSettingsHeader(edit.getText().toString())) {
            Toast.makeText(this, "Sunt deja în cod (DURATA_CLICK, INTERVAL_REPETARE…)", Toast.LENGTH_LONG).show();
            return;
        }
        Macro.Settings st = new Macro.Settings();
        try {
            String d = App.get().prefs().draft(Macro.KIND_MANUAL);
            if (d != null) st = MacroCodec.fromJson(d).settings;
        } catch (RuntimeException ignored) { }
        edit.getText().insert(0, ScriptExamples.settingsHeader(st.tapDuration, st.swipeDuration, st.tapInterval,
                !java.util.regex.Pattern.compile("\\bp\\b").matcher(edit.getText()).find() && edit.getText().toString().indexOf("#define p ") < 0));
        edit.setSelection(0);
        showLine(1);
        Toast.makeText(this, "Le folosesc tap(), swipe()… când nu le dai alte valori", Toast.LENGTH_LONG).show();
    }

    private void examples() {
        new AlertDialog.Builder(this).setTitle("Exemple").setItems(ScriptExamples.NAMES, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, final int which) {
                new AlertDialog.Builder(CodeEditorActivity.this).setTitle(ScriptExamples.NAMES[which])
                        .setMessage("Înlocuiesc codul de acum cu exemplul? (sau îl adaug la sfârșit)")
                        .setPositiveButton("ÎNLOCUIEȘTE", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d2, int w) { edit.setText(ScriptExamples.CODE[which]); recheck(); }
                        })
                        .setNeutralButton("ADAUGĂ LA SFÂRȘIT", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d2, int w) { edit.append("\n" + ScriptExamples.CODE[which]); recheck(); }
                        })
                        .setNegativeButton("ANULEAZĂ", null).show();
            }
        }).show();
    }

    private void takePanelTargets() {
        String d = App.get().prefs().draft(Macro.KIND_MANUAL);
        Macro panel = null;
        try { if (d != null) panel = MacroCodec.fromJson(d); } catch (RuntimeException ignored) { }
        if (panel == null || panel.actions.isEmpty()) {
            Toast.makeText(this, "Panoul (modul Ținte manuale) nu are ținte acum.", Toast.LENGTH_LONG).show();
            return;
        }
        final Macro src = panel;
        new AlertDialog.Builder(this).setTitle("Ia țintele din panou")
                .setMessage("Lista scriptului (" + macro.actions.size() + " pași) se înlocuiește cu cele " + src.actions.size() // tree-ok: pasi indexes the top-level list
                        + " ținte din panou. Imaginile folosite de cod rămân.")
                .setPositiveButton("ÎNLOCUIEȘTE", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int w) {
                        java.util.List<Action> keep = new java.util.ArrayList<Action>();
                        for (Action a : macro.actions) if (a.type == Action.Type.IMAGE && !a.enabled) keep.add(a); // tree-ok
                        macro.actions.clear();
                        for (Action a : src.actions) macro.actions.add(a.copy()); // tree-ok: top-level list
                        macro.actions.addAll(keep);
                        try { repo.save(macro); } catch (Exception ignored) { }
                        recheck();
                        updateStepsOnPicture();
                    }
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    private void rename() {
        Ui.textDialog(this, "Numele scriptului", macro.name, "OK", new Ui.OnString() {
            @Override public void onValue(String n) { macro.name = n; title.setText(n); save(false); }
        }, false);
    }

    // ================================================================== 2.0: the picture pane
    private void buildShotPane(LinearLayout root) {
        shotPane = Ui.vertical(this);
        shotPane.setBackgroundColor(Ui.BG);

        // row 1: the screenshots (chips) + the marks box (👁 all · ▾ which)
        LinearLayout top = Ui.horizontal(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 2));
        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        shotChips = Ui.horizontal(this);
        chipsScroll.addView(shotChips);
        top.addView(chipsScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        marksBox = Ui.horizontal(this);
        marksBox.setGravity(Gravity.CENTER_VERTICAL);
        marksBox.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 14)));
        marksAll = Ui.text(this, "", 13, Ui.ACCENT, true);
        marksAll.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        Ui.clickable(marksAll, new View.OnClickListener() { public void onClick(View v) {
            App.get().prefs().setCodeMarksAll(!App.get().prefs().codeMarksAll());
            applyMarksVisibility();
        } }, Ui.dp(this, 14));
        marksBox.addView(marksAll);
        View sep = new View(this);
        sep.setBackgroundColor(0x44FFFFFF);
        marksBox.addView(sep, new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 18)));
        marksMore = Ui.text(this, "▾", 15, Ui.TEXT, true);
        marksMore.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        Ui.clickable(marksMore, new View.OnClickListener() { public void onClick(View v) { marksPopup(); } }, Ui.dp(this, 14));
        marksBox.addView(marksMore);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = Ui.dp(this, 6);
        top.addView(marksBox, mlp);
        shotPane.addView(top, Ui.matchWrap());

        // row 2: tools that write code from the picture, and the simulation
        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolsRow = Ui.horizontal(this);
        toolsRow.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 4));
        toolChip("👆 Punct", ShotView.TOOL_POINT);
        toolChip("↗ Swipe", ShotView.TOOL_SWIPE);
        toolChip("▭ Zonă", ShotView.TOOL_ZONE);
        toolChip("🎨 Culoare", ShotView.TOOL_COLOR);
        toolChip("✂ Imagine", ShotView.TOOL_IMAGE);
        toolChip("🎲 Tap în zonă", ShotView.TOOL_TAPZONE);
        toolChip("🔢 Model", ShotView.TOOL_MODEL);
        modelDone = paneChip("✅ Scrie modelul", Ui.ACCENT);
        Ui.clickable(modelDone, new View.OnClickListener() { public void onClick(View v) { writeModel(); } }, Ui.dp(this, 14));
        modelDone.setVisibility(View.GONE);
        toolsRow.addView(modelDone, chipLp());
        TextView sim = paneChip("▶ Simulează", Ui.ACCENT);
        Ui.clickable(sim, new View.OnClickListener() { public void onClick(View v) { simulate(); } }, Ui.dp(this, 14));
        toolsRow.addView(sim, chipLp());
        toolsScroll.addView(toolsRow);
        shotPane.addView(toolsScroll, Ui.matchWrap());

        shotView = new ShotView(this, new ShotView.Listener() {
            @Override public void onPicked(int tool, int[] v, int rgb) { pickedOnPicture(tool, v, rgb); }
            @Override public void onMarkMoved(int index, int[] v, boolean done) { moveMark(index, v, done); }
            @Override public void onMarkTapped(int index) { markTapped(index); }
            @Override public void onToolEnded() { highlightTool(ShotView.TOOL_NONE); }
        });
        shotPane.addView(shotView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, paneHeight()));

        // simulation bar (after ▶ Simulează)
        simBar = Ui.horizontal(this);
        simBar.setGravity(Gravity.CENTER_VERTICAL);
        simBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        LinearLayout simCol = Ui.vertical(this);
        simSeek = new android.widget.SeekBar(this);
        simCol.addView(simSeek, Ui.matchWrap());
        simInfo = Ui.text(this, "", 12, Ui.MUTED, false);
        simInfo.setMaxLines(2);
        Ui.clickable(simInfo, new View.OnClickListener() { public void onClick(View v) { simShowLine(); } }, 0);
        simCol.addView(simInfo, Ui.matchWrap());
        simBar.addView(simCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = Ui.text(this, "×", 16, Ui.TEXT, true);
        close.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        Ui.clickable(close, new View.OnClickListener() { public void onClick(View v) { closeSim(); } }, Ui.dp(this, 14));
        simBar.addView(close);
        simBar.setVisibility(View.GONE);
        simSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar b, int p, boolean user) { showSimStep(p); }
            @Override public void onStartTrackingTouch(android.widget.SeekBar b) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar b) { }
        });
        shotPane.addView(simBar, Ui.matchWrap());
        root.addView(shotPane, Ui.matchWrap());

        // the handle between the picture and the code: drag it to share the screen
        divider = new View(this) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas c) {
                p.setColor(Ui.LABEL);
                float w = Ui.dp(getContext(), 44), h = Ui.dp(getContext(), 4);
                c.drawRoundRect(new android.graphics.RectF((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2), h, h, p);
            }
        };
        divider.setBackgroundColor(Ui.SURFACE_HIGH);
        divider.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            int startH;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (shot == null) return false;
                        downY = e.getRawY();
                        startH = shotView.getHeight();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        setPaneHeight(startH + Math.round(e.getRawY() - downY));
                        return true;
                    case MotionEvent.ACTION_UP:
                        App.get().prefs().setCodeShotHeight(shotView.getLayoutParams().height);
                        return true;
                }
                return true;
            }
        });
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 18)));

        boolean visible = App.get().prefs().codeShotPane();
        shotPane.setVisibility(visible ? View.VISIBLE : View.GONE);
        divider.setVisibility(visible ? View.VISIBLE : View.GONE);
        paneBtn.setAlpha(visible ? 1f : 0.45f);
        applyMarksVisibility();
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 6);
        return lp;
    }

    private TextView paneChip(String text, int color) {
        TextView t = Ui.text(this, text, 13, color, true);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        t.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 14)));
        return t;
    }

    private void toolChip(String text, final int tool) {
        TextView t = paneChip(text, Ui.TEXT);
        t.setTag(tool);
        Ui.clickable(t, new View.OnClickListener() { public void onClick(View v) { chooseTool(tool); } }, Ui.dp(this, 14));
        toolChips.add(t);
        toolsRow.addView(t, chipLp());
    }

    private void chooseTool(int tool) {
        if (shotView.picture() == null) {
            Toast.makeText(this, "Adaugă întâi o captură a jocului (📷 + Captură).", Toast.LENGTH_LONG).show();
            return;
        }
        int t = shotView.tool() == tool ? ShotView.TOOL_NONE : tool;
        if (shotView.tool() == ShotView.TOOL_MODEL && t != ShotView.TOOL_MODEL) clearModel();
        shotView.setTool(t);
        highlightTool(t);
        String hint = t == ShotView.TOOL_POINT ? "Atinge locul pe captură; lupa arată pixelul exact. Ridici degetul → se scrie codul."
                : t == ShotView.TOOL_SWIPE ? "Trage pe captură de unde începe până unde ajunge swipe-ul."
                : t == ShotView.TOOL_ZONE ? "Trage un dreptunghi peste numărul sau textul de citit."
                : t == ShotView.TOOL_COLOR ? "Ține degetul pe culoare; lupa arată pixelul și codul culorii."
                : t == ShotView.TOOL_IMAGE ? "Încadrează butonul de căutat (strâns, fără mult fundal)."
                : t == ShotView.TOOL_TAPZONE ? "Trage un dreptunghi: scriptul atinge de fiecare dată alt loc din el."
                : t == ShotView.TOOL_MODEL ? "Atinge 3–5 pixeli care se văd doar pe ecranul căutat, apoi „✅ Scrie modelul”." : null;
        if (hint != null) Toast.makeText(this, hint, Toast.LENGTH_SHORT).show();
    }

    private void highlightTool(int tool) {
        for (TextView t : toolChips) {
            boolean on = ((Integer) t.getTag()) == tool;
            t.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_HIGH, Ui.dp(this, 14)));
            t.setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
        }
    }

    private int paneHeight() {
        int h = App.get().prefs().codeShotHeight();
        if (h <= 0) h = Math.round(Overlays.screenSize(this).y * 0.34f);
        return Math.max(Ui.dp(this, 90), h);
    }

    private void setPaneHeight(int h) {
        int max = root == null || root.getHeight() == 0 ? h : root.getHeight() - Ui.dp(this, 300);
        h = Math.max(Ui.dp(this, 90), Math.min(Math.max(Ui.dp(this, 90), max), h));
        ViewGroup.LayoutParams lp = shotView.getLayoutParams();
        if (lp.height != h) { lp.height = h; shotView.setLayoutParams(lp); }
    }

    /** The picture's height: the saved one (it shrinks if the screen is short); small while there is no picture. */
    private void fitPane() {
        if (shotPane == null || shotPane.getVisibility() != View.VISIBLE || root.getHeight() == 0) return;
        if (shot == null) {
            ViewGroup.LayoutParams lp = shotView.getLayoutParams();
            int h = Ui.dp(this, 64);
            if (lp.height != h) { lp.height = h; shotView.setLayoutParams(lp); }
            divider.setVisibility(View.GONE);
            return;
        }
        divider.setVisibility(View.VISIBLE);
        setPaneHeight(paneHeight());
    }

    private void togglePane() {
        boolean show = shotPane.getVisibility() != View.VISIBLE;
        if (show && keyboardOpen) hideKeyboard(); // the picture comes back when the keyboard goes
        shotPane.setVisibility(show ? View.VISIBLE : View.GONE);
        divider.setVisibility(show ? View.VISIBLE : View.GONE);
        paneBtn.setAlpha(show ? 1f : 0.45f);
        App.get().prefs().setCodeShotPane(show);
        if (show) fitPane();
        if (shot == null) divider.setVisibility(View.GONE);
        Toast.makeText(this, show ? "Captura jocului e sus" : "Doar cod (🖼 o arată din nou)", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ which marks are drawn
    private static final String[] CAT_NAMES = {"👆 Atingeri (tap, point)", "↗ Swipe-uri", "▭ Zone de citit", "🎨 Culori",
            "🖼 Imagini găsite pe captură", "📋 Pașii din listă (pasi)", "▶ Simularea"};

    private void applyMarksVisibility() {
        Prefs pr = App.get().prefs();
        boolean all = pr.codeMarksAll();
        int mask = pr.codeMarksMask();
        int on = 0;
        for (int i = 0; i < CAT_NAMES.length; i++) if ((mask & (1 << i)) != 0) on++;
        marksAll.setText(all ? (on == CAT_NAMES.length ? "👁 Marcaje" : "👁 " + on + "/" + CAT_NAMES.length) : "👁 Ascunse");
        marksAll.setTextColor(all ? Ui.ACCENT : Ui.MUTED);
        if (shotView != null) shotView.setMarksShown(all, mask);
    }

    /** The ▾ next to 👁: one switch for everything, then one per kind. */
    private void marksPopup() {
        final Prefs pr = App.get().prefs();
        LinearLayout box = Ui.vertical(this);
        int p = Ui.dp(this, 14);
        box.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 8));
        box.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 14)));
        final android.widget.Switch master = new android.widget.Switch(this);
        master.setText("Toate marcajele");
        master.setTextColor(Ui.TEXT);
        master.setTextSize(15);
        master.setChecked(pr.codeMarksAll());
        box.addView(master, Ui.matchWrap());
        View line = new View(this);
        line.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        llp.topMargin = llp.bottomMargin = Ui.dp(this, 6);
        box.addView(line, llp);
        final java.util.List<android.widget.Switch> each = new java.util.ArrayList<android.widget.Switch>();
        for (int i = 0; i < CAT_NAMES.length; i++) {
            final int bit = 1 << i;
            android.widget.Switch sw = new android.widget.Switch(this);
            sw.setText(CAT_NAMES[i]);
            sw.setTextColor(Ui.TEXT);
            sw.setTextSize(14);
            sw.setChecked((pr.codeMarksMask() & bit) != 0);
            sw.setEnabled(pr.codeMarksAll());
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                    int m = pr.codeMarksMask();
                    pr.setCodeMarksMask(on ? m | bit : m & ~bit);
                    applyMarksVisibility();
                }
            });
            each.add(sw);
            box.addView(sw, Ui.matchWrap());
        }
        master.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                pr.setCodeMarksAll(on);
                for (android.widget.Switch sw : each) sw.setEnabled(on);
                applyMarksVisibility();
            }
        });
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        android.widget.PopupWindow pw = new android.widget.PopupWindow(sv, Ui.dp(this, 280), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(Ui.dp(this, 8));
        pw.showAsDropDown(marksBox, 0, Ui.dp(this, 4), Gravity.END);
    }

    // ------------------------------------------------------------------ the screenshots
    private void loadShots() {
        shots = App.get().shots().list(macro.id);
        ShotStore.Shot pick = null;
        String want = App.get().prefs().codeShot(macro.id);
        for (ShotStore.Shot s : shots) if (s.id.equals(want)) pick = s;
        if (pick == null && !shots.isEmpty()) pick = shots.get(0);
        rebuildChips(pick);
        selectShot(pick);
    }

    private void rebuildChips(ShotStore.Shot current) {
        shotChips.removeAllViews();
        for (final ShotStore.Shot s : shots) {
            boolean on = current != null && current.id.equals(s.id);
            TextView t = paneChip("📷 " + s.name, on ? Ui.ON_ACCENT : Ui.TEXT);
            t.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_HIGH, Ui.dp(this, 14)));
            Ui.clickable(t, new View.OnClickListener() { public void onClick(View v) { rebuildChips(s); selectShot(s); } }, Ui.dp(this, 14));
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { shotMenu(s); return true; }
            });
            shotChips.addView(t, chipLp());
        }
        TextView add = paneChip("+ Captură", Ui.ACCENT);
        Ui.clickable(add, new View.OnClickListener() { public void onClick(View v) { addShotMenu(); } }, Ui.dp(this, 14));
        shotChips.addView(add, chipLp());
    }

    private void selectShot(final ShotStore.Shot s) {
        final int gen = ++shotGen;
        closeSim();
        shotTools = null;
        shot = s;
        if (s == null) {
            shotView.setPicture(null, 1, 1);
            shotView.setEmptyText("Nicio captură a jocului încă.\nApasă „+ Captură” (din joc sau din galerie).");
            fitPane();
            return;
        }
        App.get().prefs().setCodeShot(macro.id, s.id);
        shotView.setPicture(null, 1, 1);
        shotView.setEmptyText("Se încarcă…");
        worker.execute(new Runnable() { public void run() {
            final android.graphics.Bitmap b = App.get().shots().load(macro.id, s);
            handler.post(new Runnable() { public void run() {
                if (gen != shotGen || isFinishing()) return;
                if (b == null) { shotView.setEmptyText("Nu pot deschide captura (prea mare sau stricată)."); return; }
                int[] d = com.freeclick.core.script.ScreenSpace.designFor(macro.screenWidth, macro.screenHeight, b.getWidth(), b.getHeight());
                fitPane();
                shotView.setPicture(b, d[0], d[1]);
                shotTools = new ShotTools(b, d[0], d[1]);
                shotView.setMarks(marks);
                updateStepsOnPicture();
                refreshImageHits(true);
                selectionMoved();
                if (!shapeWarned && macro.screenWidth > 0 && !com.freeclick.core.script.ScreenSpace.sameShape(macro.screenWidth, macro.screenHeight, b.getWidth(), b.getHeight())) {
                    shapeWarned = true;
                    Toast.makeText(CodeEditorActivity.this, "Captura are alte proporții decât ecranul scriptului (" + macro.screenWidth + "×"
                            + macro.screenHeight + "): coordonatele se scalează, dar pot ieși puțin deplasate.", Toast.LENGTH_LONG).show();
                }
            } });
        } });
    }

    private void addShotMenu() {
        if (shots.size() >= ShotStore.MAX_PER_SCRIPT) {
            Toast.makeText(this, "Maximum " + ShotStore.MAX_PER_SCRIPT + " capturi pe script. Ține apăsat pe una ca s-o ștergi.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = {"📷 Fă o captură din joc", "🖼 Alege din galerie (un screenshot)"};
        new AlertDialog.Builder(this).setTitle("Captură nouă").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which == 0) fromScreen(ClickService.EDITOR_SHOT);
                else {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                    try { startActivityForResult(Intent.createChooser(i, "Alege un screenshot al jocului"), REQ_GALLERY); }
                    catch (RuntimeException e) { Toast.makeText(CodeEditorActivity.this, "Nu găsesc o galerie pe telefon", Toast.LENGTH_LONG).show(); }
                }
            }
        }).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_DOCS) { // 📖 "Pune în cod": the example goes in at the cursor
            String code = res == RESULT_OK && data != null ? data.getStringExtra(DocsActivity.EXTRA_INSERT) : null;
            if (code != null) { insert(code.endsWith("\n") ? code : code + "\n", code.length()); recheck(); }
            return;
        }
        if (req != REQ_GALLERY || res != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        worker.execute(new Runnable() { public void run() {
            android.graphics.Bitmap b = null;
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                try { android.graphics.BitmapFactory.decodeStream(in, null, o); } finally { if (in != null) in.close(); }
                int sample = 1;
                while (Math.max(o.outWidth, o.outHeight) / sample > 4096) sample *= 2;
                o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = sample;
                o.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
                o.inScaled = false;
                in = getContentResolver().openInputStream(uri);
                try { b = android.graphics.BitmapFactory.decodeStream(in, null, o); } finally { if (in != null) in.close(); }
            } catch (Exception | OutOfMemoryError e) {
                b = null;
            }
            final android.graphics.Bitmap got = b;
            handler.post(new Runnable() { public void run() {
                if (got == null) Toast.makeText(CodeEditorActivity.this, "Nu pot citi poza aleasă", Toast.LENGTH_LONG).show();
                else addShotBitmap(got);
            } });
        } });
    }

    private void addShotBitmap(final android.graphics.Bitmap b) {
        final String name = "captură " + (shots.size() + 1);
        worker.execute(new Runnable() { public void run() {
            String err = null;
            ShotStore.Shot added = null;
            try { added = App.get().shots().add(macro.id, b, name); } catch (Exception e) { err = e.getMessage(); }
            final ShotStore.Shot a = added;
            final String problem = err;
            handler.post(new Runnable() { public void run() {
                if (a == null) { Toast.makeText(CodeEditorActivity.this, "Nu am putut salva captura: " + problem, Toast.LENGTH_LONG).show(); return; }
                App.get().prefs().setCodeShot(macro.id, a.id);
                if (shotPane.getVisibility() != View.VISIBLE) togglePane();
                loadShots();
                Toast.makeText(CodeEditorActivity.this, "📷 " + a.name + " · ține apăsat pe ea ca s-o redenumești (ex. „meniu”)", Toast.LENGTH_LONG).show();
            } });
        } });
    }

    private void shotMenu(final ShotStore.Shot s) {
        new AlertDialog.Builder(this).setTitle(s.name).setItems(new String[]{"Redenumește", "Șterge"}, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which == 0) {
                    Ui.textDialog(CodeEditorActivity.this, "Numele capturii", s.name, "OK", new Ui.OnString() {
                        @Override public void onValue(String n) {
                            try { App.get().shots().rename(macro.id, s.id, n); } catch (Exception ignored) { }
                            loadShots();
                        }
                    }, false);
                } else {
                    new AlertDialog.Builder(CodeEditorActivity.this).setTitle("Ștergi captura „" + s.name + "”?")
                            .setMessage("Codul nu se schimbă; doar poza dispare.")
                            .setPositiveButton("ȘTERGE", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d2, int w) {
                                    try { App.get().shots().delete(macro.id, s.id); } catch (Exception ignored) { }
                                    loadShots();
                                }
                            }).setNegativeButton("ANULEAZĂ", null).show();
                }
            }
        }).show();
    }

    // ------------------------------------------------------------------ code ↔ picture
    private void rescanMarks() {
        if (edit == null) return;
        marksSource = edit.getText().toString();
        marks = com.freeclick.core.script.CodeMarks.scan(marksSource);
        if (shotView != null) shotView.setMarks(marks);
        refreshImageHits(false);
    }

    private final Runnable selectionWork = new Runnable() { public void run() { lightSelectedMark(); } };

    private void selectionMoved() {
        handler.removeCallbacks(selectionWork);
        handler.postDelayed(selectionWork, 120);
        hintSoon();
    }

    // ------------------------------------------------------------------ parameter hints
    private final Runnable hintWork = new Runnable() { public void run() { updateHint(); } };

    private void hintSoon() {
        handler.removeCallbacks(hintWork);
        handler.postDelayed(hintWork, 90);
    }

    /** "void tap(point p, int durata = DURATA_CLICK, …)" with the parameter being written in bold. */
    private void updateHint() {
        if (hintBar == null || edit == null) return;
        updateSuggestions();
        com.freeclick.core.script.Signatures.Hint h = edit.hasFocus()
                ? com.freeclick.core.script.Signatures.at(edit.getText().toString(), Math.max(0, edit.getSelectionStart())) : null;
        if (h == null) { hintBar.setVisibility(View.GONE); return; }
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(h.text);
        int split = h.text.indexOf("   //");
        if (split > 0) b.setSpan(new ForegroundColorSpan(Ui.MUTED), split, h.text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (h.paramStart >= 0) {
            b.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), h.paramStart, h.paramEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(Ui.ACCENT), h.paramStart, h.paramEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (h.others > 0) {
            int at = b.length();
            b.append("   (+").append(String.valueOf(h.others)).append(h.others == 1 ? " altă formă)" : " alte forme)");
            b.setSpan(new ForegroundColorSpan(Ui.MUTED), at, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        hintBar.setText(b);
        hintBar.setVisibility(View.VISIBLE);
    }

    /** Chips with names that start with the word at the cursor. */
    private void updateSuggestions() {
        final int cur = Math.max(0, edit.getSelectionStart());
        final com.freeclick.core.script.Completions.Result r = edit.hasFocus() && edit.getSelectionStart() == edit.getSelectionEnd()
                ? com.freeclick.core.script.Completions.at(edit.getText().toString(), cur, 12) : null;
        suggestRow.removeAllViews();
        if (r == null) { suggestScroll.setVisibility(View.GONE); return; }
        for (final com.freeclick.core.script.Completions.Item it : r.items) {
            int col = it.kind == com.freeclick.core.script.Completions.FUNC ? 0xFF82AAFF
                    : it.kind == com.freeclick.core.script.Completions.KEYWORD ? 0xFFC792EA
                    : it.kind == com.freeclick.core.script.Completions.TYPE ? Ui.ACCENT : Ui.TEXT;
            TextView t = Ui.text(this, it.name, 14, col, true);
            t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            t.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
            t.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 12)));
            Ui.clickable(t, new View.OnClickListener() { public void onClick(View v) {
                Editable e = edit.getText();
                int end = Math.min(Math.max(0, edit.getSelectionStart()), e.length());
                int start = Math.min(r.start, end);
                // replace the whole word under the cursor (also the part after it)
                int wordEnd = end;
                while (wordEnd < e.length() && (Character.isLetterOrDigit(e.charAt(wordEnd)) || e.charAt(wordEnd) == '_')) wordEnd++;
                String ins = it.insert;
                if (ins.endsWith("(") && wordEnd < e.length() && e.charAt(wordEnd) == '(') ins = ins.substring(0, ins.length() - 1);
                e.replace(start, wordEnd, ins);
                edit.setSelection(Math.min(e.length(), start + ins.length()));
            } }, Ui.dp(this, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            suggestRow.addView(t, lp);
        }
        suggestScroll.setVisibility(View.VISIBLE);
        suggestScroll.scrollTo(0, 0);
    }

    // ------------------------------------------------------------------ keyboard
    /** While typing, the code gets the room: the picture and the run buttons step aside. */
    private void applyKeyboard() {
        if (shotPane == null) return;
        boolean pane = !keyboardOpen && App.get().prefs().codeShotPane();
        shotPane.setVisibility(pane ? View.VISIBLE : View.GONE);
        divider.setVisibility(pane ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
        if (pane) fitPane();
        if (keyboardOpen) showLine(cursorLine());
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        edit.clearFocus();
    }

    private int cursorLine() {
        int pos = Math.max(0, edit.getSelectionStart());
        CharSequence t = edit.getText();
        int line = 1;
        for (int i = 0; i < Math.min(pos, t.length()); i++) if (t.charAt(i) == '\n') line++;
        return line;
    }

    /** The place named on the cursor's line lights up; a zone shows what it reads, a colour where it matches. */
    private void lightSelectedMark() {
        if (shotView == null) return;
        if (shotView.busy()) { selectionMoved(); return; } // a mark is being dragged: after the finger is lifted
        if (!edit.getText().toString().equals(marksSource)) rescanMarks();
        com.freeclick.core.script.CodeMarks.Mark m = com.freeclick.core.script.CodeMarks.onLine(marks, cursorLine());
        int idx = m == null ? -1 : marks.indexOf(m);
        shotView.setSelected(idx);
        if (idx >= 0) shotView.reveal(idx);
        final ShotTools tools = shotTools;
        if (m == null || tools == null) return;
        final int gen = ++selGen;
        if (m.kind == com.freeclick.core.script.CodeMarks.ZONE) {
            final int[] v = m.values.clone();
            String lineText = lineText(m.line);
            final boolean number = !lineText.contains("citesteText") && !lineText.contains("readText");
            worker.execute(new Runnable() { public void run() {
                String r = tools.read(v[0], v[1], v[2], v[3], number);
                final String shown = r == null ? "nimic" : "„" + r + "”";
                handler.post(new Runnable() { public void run() { if (gen == selGen) shotView.setZoneText(shown); } });
            } });
        } else if (m.kind == com.freeclick.core.script.CodeMarks.COLOR && m.values[2] >= 0) {
            final int rgb = m.values[2], tol = m.tolerance < 0 ? 24 : m.tolerance;
            worker.execute(new Runnable() { public void run() {
                final android.graphics.Bitmap mask = tools.colorMask(rgb, tol);
                handler.post(new Runnable() { public void run() { if (gen == selGen) shotView.setColorMask(mask); } });
            } });
        }
    }

    private String lineText(int line) {
        String[] l = edit.getText().toString().split("\n", -1);
        return line >= 1 && line <= l.length ? l[line - 1] : "";
    }

    /** A mark dragged on the picture: exactly its numbers change in the code. */
    private void moveMark(int index, int[] v, boolean done) {
        if (!edit.getText().toString().equals(marksSource)) rescanMarks();
        if (index < 0 || index >= marks.size()) return;
        com.freeclick.core.script.CodeMarks.Mark m = marks.get(index);
        if (m.movable() != v.length) return;
        Editable e = edit.getText();
        applying = true;
        try {
            for (int k = m.movable() - 1; k >= 0; k--) {
                String now = e.subSequence(m.starts[k], m.ends[k]).toString(), want = String.valueOf(v[k]);
                if (!now.equals(want)) e.replace(m.starts[k], m.ends[k], want);
            }
        } finally {
            applying = false;
        }
        rescanMarks();
        if (done) {
            handler.removeCallbacks(recheck);
            recheck();
            int sel = Math.min(m.start, edit.getText().length());
            edit.setSelection(sel); // the cursor follows the mark (its zone / colour is shown)
            showLine(m.line);
            status.setText("✏ Linia " + m.line + ": " + m.label + " → " + java.util.Arrays.toString(v).replace("[", "(").replace("]", ")"));
            status.setTextColor(Ui.ACCENT);
        } else {
            handler.removeCallbacks(recheck);
            handler.postDelayed(recheck, 350);
        }
    }

    private void markTapped(int index) {
        if (index < 0 || index >= marks.size()) return;
        com.freeclick.core.script.CodeMarks.Mark m = marks.get(index);
        edit.setSelection(Math.min(m.start, edit.getText().length()));
        showLine(m.line);
    }

    /** Scrolls the code to a line without opening the keyboard. */
    private void showLine(final int line) {
        handler.post(new Runnable() { public void run() {
            Layout l = edit.getLayout();
            if (l == null || line < 1) return;
            int top = l.getLineTop(Math.min(line - 1, l.getLineCount() - 1)) + edit.getTop();
            codeScroll.smoothScrollTo(0, Math.max(0, top - codeScroll.getHeight() / 3));
        } });
    }

    /** The script's list of steps on the picture (pasi[i]). */
    private void updateStepsOnPicture() {
        if (shotView == null) return;
        java.util.List<float[]> l = new java.util.ArrayList<float[]>();
        for (int i = 0; i < macro.actions.size(); i++) { // tree-ok: pasi indexes the top-level list
            Action a = macro.actions.get(i);
            switch (a.type) {
                case TAP: case LONG_PRESS: case DOUBLE_TAP:
                    l.add(new float[]{a.x, a.y, 0, 0, 0, i});
                    break;
                case SWIPE:
                    l.add(new float[]{a.x, a.y, a.x2, a.y2, 1, i});
                    break;
                default:
            }
        }
        shotView.setSteps(l);
    }

    /** Where each image named in the code (gaseste("…") / apare("…")) is on the picture. */
    private void refreshImageHits(boolean force) {
        final ShotTools tools = shotTools;
        if (tools == null) return;
        final java.util.Map<String, ImageSpec> specs = new java.util.LinkedHashMap<String, ImageSpec>();
        for (com.freeclick.core.script.CodeMarks.Mark m : marks) {
            if (m.kind != com.freeclick.core.script.CodeMarks.IMAGE || m.image == null) continue;
            String key = m.image.trim().toLowerCase(java.util.Locale.ROOT);
            for (Action a : macro.actions) { // tree-ok: pictures are top-level steps of the script's list
                if (a.type == Action.Type.IMAGE && a.img != null && a.img.name != null && a.img.name.trim().equalsIgnoreCase(m.image.trim())) {
                    specs.put(key, a.img);
                    break;
                }
            }
        }
        String names = specs.keySet().toString();
        if (!force && names.equals(imageNames)) return;
        imageNames = names;
        final int gen = shotGen;
        worker.execute(new Runnable() { public void run() {
            final java.util.Map<String, java.util.List<int[]>> hits = new java.util.HashMap<String, java.util.List<int[]>>();
            for (java.util.Map.Entry<String, ImageSpec> e : specs.entrySet()) hits.put(e.getKey(), tools.findBoxes(e.getValue(), true));
            handler.post(new Runnable() { public void run() { if (gen == shotGen) shotView.setImageHits(hits); } });
        } });
    }

    // ------------------------------------------------------------------ tools → code
    /** True if the cursor is where a value goes (after "=", "(" or ","), so an expression is written there. */
    private boolean expressionHere() {
        Editable e = edit.getText();
        int i = Math.max(0, edit.getSelectionStart()) - 1;
        while (i >= 0 && (e.charAt(i) == ' ' || e.charAt(i) == '\t')) i--;
        if (i < 0) return false;
        char c = e.charAt(i);
        return c == '=' || c == '(' || c == ',' || c == '<' || c == '>' || c == '!' || c == '&' || c == '|';
    }

    private void insertInline(String code) {
        int at = Math.max(0, edit.getSelectionStart());
        edit.getText().insert(at, code);
        edit.setSelection(at + code.length());
    }

    private String freeName(String base) {
        String t = edit.getText().toString();
        int n = 1;
        while (t.matches("(?s).*\\b" + base + n + "\\b.*")) n++;
        return base + n;
    }

    private void pickedOnPicture(int tool, final int[] v, int rgb) {
        // the cursor was never put in the code: the new line goes at the end, not into the first comment
        if (!edit.hasFocus() && edit.getSelectionStart() <= 0) edit.setSelection(edit.length());
        final boolean expr = expressionHere();
        String hex = ColorMatch.toHex(rgb);
        switch (tool) {
            case ShotView.TOOL_POINT:
                if (expr) insertInline("point(" + v[0] + ", " + v[1] + ")");
                else insert("tap(point(" + v[0] + ", " + v[1] + "));", -1);
                break;
            case ShotView.TOOL_COLOR:
                if (expr) insertInline("aproape(pixel(point(" + v[0] + ", " + v[1] + ")), " + hex + ", 24)");
                else {
                    String code = "if (aproape(pixel(point(" + v[0] + ", " + v[1] + ")), " + hex + ", 24)) {\n    \n}";
                    insert(code, code.indexOf("{\n") + 6);
                }
                break;
            case ShotView.TOOL_MODEL:
                modelPixels.add(new int[]{v[0], v[1], rgb});
                shotView.setPending(new java.util.ArrayList<int[]>(modelPixels));
                modelDone.setText("✅ Scrie modelul (" + modelPixels.size() + ")");
                modelDone.setVisibility(View.VISIBLE);
                return;
            case ShotView.TOOL_TAPZONE:
                insert("tapInZona(rect(" + v[0] + ", " + v[1] + ", " + v[2] + ", " + v[3] + "));", -1);
                break;
            case ShotView.TOOL_SWIPE:
                insert("swipe(point(" + v[0] + ", " + v[1] + "), point(" + v[2] + ", " + v[3] + "), 300);", -1);
                break;
            case ShotView.TOOL_ZONE: {
                String r = "rect(" + v[0] + ", " + v[1] + ", " + v[2] + ", " + v[3] + ")";
                if (expr) insertInline(r);
                else insert("rect " + freeName("zona") + " = " + r + ";", -1);
                break;
            }
            case ShotView.TOOL_IMAGE: {
                final ShotTools tools = shotTools;
                if (tools == null) return;
                worker.execute(new Runnable() { public void run() {
                    final String[] problem = {null};
                    ImageSpec spec = null;
                    try { spec = tools.cut(v[0], v[1], v[2], v[3], problem); } catch (Exception e) { problem[0] = "Nu pot salva imaginea: " + e.getMessage(); }
                    final ImageSpec got = spec;
                    handler.post(new Runnable() { public void run() {
                        if (got == null) Toast.makeText(CodeEditorActivity.this, problem[0], Toast.LENGTH_LONG).show();
                        else nameImage(got, !expr);
                    } });
                } });
                return;
            }
            default:
                return;
        }
        rescanMarks();
        selectionMoved();
    }

    private void clearModel() {
        modelPixels.clear();
        shotView.setPending(null);
        modelDone.setVisibility(View.GONE);
    }

    /** model({p1, p2…}, {#c1, #c2…}, 24): the pixels chosen with "🔢 Model". */
    private void writeModel() {
        if (modelPixels.isEmpty()) return;
        StringBuilder pts = new StringBuilder(), cols = new StringBuilder();
        for (int[] p : modelPixels) {
            if (pts.length() > 0) { pts.append(", "); cols.append(", "); }
            pts.append("point(").append(p[0]).append(", ").append(p[1]).append(")");
            cols.append(ColorMatch.toHex(p[2]));
        }
        String m = "model({" + pts + "}, {" + cols + "}, 24)";
        if (!edit.hasFocus() && edit.getSelectionStart() <= 0) edit.setSelection(edit.length());
        if (expressionHere()) insertInline(m);
        else {
            String code = "if (" + m + ") {\n    \n}";
            insert(code, code.indexOf("{\n") + 6);
        }
        clearModel();
        shotView.setTool(ShotView.TOOL_NONE);
        highlightTool(ShotView.TOOL_NONE);
        rescanMarks();
        selectionMoved();
    }

    // ------------------------------------------------------------------ simulation
    private void simulate() {
        final ShotTools tools = shotTools;
        if (tools == null) { Toast.makeText(this, "Adaugă întâi o captură a jocului (+ Captură).", Toast.LENGTH_LONG).show(); return; }
        final String src = edit.getText().toString();
        ScriptError err = ScriptRunner.check(src);
        if (err != null) { recheck(); goToLine(err.line); Toast.makeText(this, err.describe(), Toast.LENGTH_LONG).show(); return; }
        final java.util.List<Action> steps = new java.util.ArrayList<Action>();
        for (Action a : macro.actions) steps.add(a.copy()); // tree-ok: the script's own top-level list
        final int gen = shotGen;
        simInfo.setText("Simulez…");
        simBar.setVisibility(View.VISIBLE);
        worker.execute(new Runnable() { public void run() {
            final com.freeclick.core.script.ScriptSimulator.Result r =
                    com.freeclick.core.script.ScriptSimulator.run(src, steps, tools.screen(steps), 300, 10 * 60 * 1000L, 4000);
            handler.post(new Runnable() { public void run() {
                if (gen != shotGen) return;
                simResult = r;
                int n = r.touches.size();
                if (!App.get().prefs().codeMarksAll() || (App.get().prefs().codeMarksMask() & (1 << ShotView.CAT_SIM)) == 0) {
                    Toast.makeText(CodeEditorActivity.this, "Simularea e ascunsă din 👁 ▾", Toast.LENGTH_SHORT).show();
                }
                simSeek.setMax(Math.max(0, n));
                simSeek.setProgress(n);
                showSimStep(n);
                if (r.error != null && r.error.line > 0) { runtimeErrorLine = r.error.line; runtimeError = "Simulare: " + r.error.describe(); recheck(); }
            } });
        } });
    }

    private void showSimStep(int k) {
        com.freeclick.core.script.ScriptSimulator.Result r = simResult;
        if (r == null) return;
        int n = r.touches.size();
        shotView.setSim(r.touches, k - 1);
        if (n == 0) {
            String last = r.prints.isEmpty() ? "" : " · " + r.prints.get(r.prints.size() - 1).text;
            simInfo.setText("Nicio atingere pe captura asta: " + r.describeEnd() + last);
            return;
        }
        if (k <= 0) { simInfo.setText("Trage ca să vezi atingerile în ordine (" + n + ")"); return; }
        com.freeclick.core.script.ScriptSimulator.Touch t = r.touches.get(k - 1);
        String what = t.isSwipe() ? "swipe (" + t.x + ", " + t.y + ") → (" + t.x2 + ", " + t.y2 + ")"
                : (t.type == Action.Type.LONG_PRESS ? "apăsare lungă" : t.type == Action.Type.DOUBLE_TAP ? "dublu tap" : "tap") + " (" + t.x + ", " + t.y + ")";
        String printed = "";
        for (com.freeclick.core.script.ScriptSimulator.Printed p : r.prints) if (p.time <= t.time) printed = p.text;
        simInfo.setText("#" + k + "/" + n + " · " + String.format(java.util.Locale.US, "%.1f s", t.time / 1000.0) + " · "
                + (t.fromSteps ? "din pasi, " : "") + (t.line > 0 ? "linia " + t.line + " · " : "") + what
                + (printed.isEmpty() ? "" : "\nconsola: " + printed) + (k == n ? "\n" + r.describeEnd() : ""));
    }

    private void simShowLine() {
        com.freeclick.core.script.ScriptSimulator.Result r = simResult;
        int k = simSeek.getProgress();
        if (r == null || k < 1 || k > r.touches.size()) return;
        int line = r.touches.get(k - 1).line;
        if (line > 0) {
            edit.setSelection(lineStartOffset(line));
            showLine(line);
        }
    }

    private int lineStartOffset(int line) {
        String t = edit.getText().toString();
        int pos = 0;
        for (int l = 1; l < line && pos >= 0; l++) { pos = t.indexOf('\n', pos); if (pos >= 0) pos++; }
        return pos < 0 ? t.length() : Math.min(pos, t.length());
    }

    private void closeSim() {
        simResult = null;
        if (simBar != null) simBar.setVisibility(View.GONE);
        if (shotView != null) shotView.setSim(null, -1);
    }

    // ------------------------------------------------------------------ tutorial
    private void tutorial() {
        TutorialOverlay.Step[] steps = {
                new TutorialOverlay.Step(edit, "Aici scrii codul, în stil C++: variabile (int, double, string, point, color…), if, while, for, "
                        + "funcții, struct, vector. Fiecare instrucțiune se termină cu ;"),
                new TutorialOverlay.Step(shotPane.getVisibility() == View.VISIBLE ? shotPane : null,
                        "Sus e captura jocului (📷 din joc sau din galerie). Alegi un instrument — 👆 Punct, ↗ Swipe, ▭ Zonă, 🎨 Culoare, "
                        + "✂ Imagine, 🎲 Tap în zonă, 🔢 Model (3–5 pixeli) — și atingi poza: codul se scrie singur, cu coordonatele ecranului real. Tragi un marcaj → cifrele din cod "
                        + "se schimbă. ▶ Simulează arată în ce ordine ar atinge scriptul."),
                new TutorialOverlay.Step(shotPane.getVisibility() == View.VISIBLE ? marksBox : paneBtn,
                        "👁 arată sau ascunde toate marcajele; ▾ alegi ce vezi (atingeri, swipe-uri, zone, culori…). "
                        + "🖼 de sus ascunde captura ca să vezi doar codul."),
                new TutorialOverlay.Step(insertBar, "„+ 📱 Din ecran” alege pe ecranul real al telefonului o atingere, un swipe, o culoare, o zonă sau o imagine "
                        + "(când n-ai o captură). Restul pun if, while, for…"),
                new TutorialOverlay.Step(status, "Aici vezi greșelile chiar în timp ce scrii, cu linia lor (de ex. „Linia 7: lipsește ;”). "
                        + "Atinge mesajul ca să sari la linie."),
                new TutorialOverlay.Step(gutter, "Atinge numărul unei linii ca să pui ● o oprire acolo: scriptul se oprește la ea "
                        + "și îți arată linia și variabilele."),
                new TutorialOverlay.Step(runBtn, "▶ pune scriptul pe panoul plutitor: deschizi jocul și apeși ▶ pe panou, acolo pornește. "
                        + "În timpul rulării: ■ (stop) și ⏸ (pauză); print() scrie în consolă. ≡ → Codul te aduce înapoi aici."),
                new TutorialOverlay.Step(stepBtn, "👣 la fel, dar pas cu pas: o bară sus arată linia curentă și variabilele; ⏭ trece la următoarea."),
                new TutorialOverlay.Step(edit, "Când scrii, deasupra tastaturii apar nume potrivite (atingi unul ca să-l scrii) și ce așteaptă funcția, ca în C++: "
                        + "„void tap(point p, int durata = DURATA_CLICK, …)”, cu parametrul la care ești îngroșat. Merge și pentru funcțiile tale."),
                new TutorialOverlay.Step(moreBtn, "⋮ → 📖 Documentație (fiecare funcție explicată, cu exemple; ține apăsat pe un nume din cod ca să-i vezi pagina), "
                        + "Exemple (scripturi gata făcute), 📤 Partajează (un cod pe care îl trimiți oricui). Restul e în Avansat."),
                new TutorialOverlay.Step(null, "Gata! Încearcă ⋮ → Exemple → „Începe aici”, apoi ▶. Tutorialul îl găsești oricând în ⋮ → Avansat → Tutorial."),
        };
        TutorialOverlay.show(this, steps, new TutorialOverlay.OnClosed() {
            @Override public void onClosed(boolean neverAgain) {
                if (neverAgain) App.get().prefs().setSectionOpen("tutorial.code.v3", true);
            }
        });
    }

    @Override public void onBackPressed() {
        if (TutorialOverlay.close(this)) return; // Înapoi închide tutorialul (revine data viitoare)
        super.onBackPressed();
    }
}
