package com.freeclick.app;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;
import com.freeclick.core.ColorMatch;
import com.freeclick.core.Expr;
import com.freeclick.core.GlobalActions;
import com.freeclick.core.Macro;
import com.freeclick.core.MacroGraph;
import com.freeclick.core.MacroLibrary;
import com.freeclick.core.PlaybackEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits one Action. Used by the list editor and by the on-screen markers.
 *
 * Only what matters for that kind of step is visible; everything else sits in "⚙ Avansat",
 * itself split into one drop-down per area (📍 Locație, ⏱ Timp, 🔁 Repetare, 📝 Altele...),
 * each with a one-line summary so it rarely needs opening.
 */
final class ActionEditForm {

    /** Things a form can do only with the real screen (provided by the panel or the editor). */
    interface ScreenTools {
        void testImage(Action draft);
        void recaptureImage(Action draft);
        /** Pick the point and its colour on the screen (colour waits, colour reads). */
        void pickColor(Action draft);
        /** Choose again the zone a read step reads from. */
        void pickZone(Action draft);
        /** Read the zone now, show what was read, offer to teach the letters. */
        void testRead(Action draft);
    }

    private final Context ctx;
    private final Action action;
    private final Macro owner;
    private final List<Macro> allMacros;
    private final MacroLibrary lib;
    private final ScreenTools tools;
    private final boolean overlay;
    private final ScrollView view;

    private EditText delay, duration, x, y, x2, y2, repeat, interval, label, durationMax, text, varName, zoomSpan;
    private Spinner zoomDir;
    private CheckBox enabled, stopOnTimeout, randomWait;
    private Spinner tapKind, global, macro, app, colorWhen, readMode;
    private final List<String> appPackages = new ArrayList<String>();
    private final List<String> macroIds = new ArrayList<String>();
    private final List<Integer> globalIds = new ArrayList<Integer>();
    // image step
    private EditText imgName, imgPrecision, imgHits, imgPress;
    private Spinner imgDoes, imgMissing, imgPrecisionKind, imgWhere, imgMany;
    // colour (wait for colour)
    private int colorValue, tolValue;

    private static final Action.Type[] TAP_KINDS = {Action.Type.TAP, Action.Type.LONG_PRESS, Action.Type.DOUBLE_TAP};

    ActionEditForm(Context c, Action a, Macro owner, List<Macro> allMacros, MacroLibrary lib) {
        this(c, a, owner, allMacros, lib, null, false);
    }

    ActionEditForm(Context c, Action a, Macro owner, List<Macro> allMacros, MacroLibrary lib, ScreenTools tools, boolean overlay) {
        this.ctx = c;
        this.action = a;
        this.owner = owner;
        this.allMacros = allMacros;
        this.lib = lib;
        this.tools = tools;
        this.overlay = overlay;
        view = new ScrollView(c);
        build();
    }

    View view() { return view; }

    /** The step was changed from outside (a new zone or colour picked on the screen): show it again. */
    void reload() { build(); }

    private void build() {
        Context c = ctx;
        final Action a = action;
        view.removeAllViews();
        LinearLayout f = Ui.vertical(c);
        int p = Ui.dp(c, 16);
        f.setPadding(p, Ui.dp(c, 10), p, Ui.dp(c, 12));
        view.setFillViewport(true);
        view.addView(f);
        delay = duration = x = y = x2 = y2 = repeat = interval = label = durationMax = text = varName = null;
        enabled = stopOnTimeout = randomWait = null;
        tapKind = global = macro = app = colorWhen = readMode = null;
        imgName = imgPrecision = imgHits = imgPress = null;
        imgDoes = imgMissing = imgPrecisionKind = imgWhere = imgMany = null;
        appPackages.clear();
        macroIds.clear();
        globalIds.clear();
        colorValue = a.color;
        tolValue = a.tolerance;

        // ---------------------------------------------------------------- what matters for this step
        switch (a.type) {
            case TAP:
            case LONG_PRESS:
            case DOUBLE_TAP: {
                String[] names = new String[TAP_KINDS.length];
                int sel = 0;
                for (int i = 0; i < TAP_KINDS.length; i++) {
                    names[i] = ActionFormatter.typeName(TAP_KINDS[i]);
                    if (TAP_KINDS[i] == a.type) sel = i;
                }
                tapKind = spinner(c, f, "Tip", names, sel);
                f.addView(Ui.muted(c, "Locul îl schimbi trăgând de punct pe ecran (sau exact, în Avansat → Locație)."));
                break;
            }
            case SWIPE:
                f.addView(Ui.muted(c, "Capetele le tragi pe ecran (sau exact, în Avansat → Locație)."));
                break;
            case GESTURE:
                if (a.isZoom()) { // two fingers: direction, how far apart, how long
                    zoomDir = spinner(c, f, "Cele două degete", new String[]{"se depărtează (zoom în)", "se apropie (zoom out)"}, a.zoomSpan > 0 ? 0 : 1);
                    zoomSpan = Ui.numberRow(f, "Distanța dintre degete la capăt (px)", Math.abs(a.zoomSpan), false);
                    duration = Ui.numberRow(f, "Durata (ms)", a.duration, false);
                    f.addView(Ui.muted(c, "Centrul îl muți trăgând marcajul pe ecran."));
                    break;
                }
                f.addView(Ui.muted(c, ActionFormatter.describe(a, lib)
                        + "\nTraseul înregistrat nu se editează punct cu punct; poți schimba întârzierea și repetările."));
                break;
            case WAIT: {
                randomWait = new CheckBox(c);
                randomWait.setText("Aleatoare (de fiecare dată alt timp, între minim și maxim)");
                randomWait.setChecked(a.isRandomWait());
                f.addView(randomWait);
                duration = Ui.numberRow(f, a.isRandomWait() ? "Minim (ms)" : "Durata pauzei (ms)", a.duration, false);
                final LinearLayout maxRow = Ui.vertical(c);
                durationMax = Ui.numberRow(maxRow, "Maxim (ms)", a.isRandomWait() ? a.durationMax : Math.max(a.duration * 2, 1000), false);
                maxRow.setVisibility(a.isRandomWait() ? View.VISIBLE : View.GONE);
                f.addView(maxRow);
                final TextView minLabel = findLabel(f, duration);
                randomWait.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        maxRow.setVisibility(on ? View.VISIBLE : View.GONE);
                        if (minLabel != null) minLabel.setText(on ? "Minim (ms)" : "Durata pauzei (ms)");
                    }
                });
                break;
            }
            case LAUNCH_APP: {
                f.addView(Ui.muted(c, "Deschide o aplicație în mijlocul macro-ului (de ex. revii în joc după o reclamă)."));
                List<String> names = new ArrayList<String>();
                int sel = 0;
                for (String[] e : launchableApps(c)) {
                    if (e[1].equals(a.packageName)) sel = names.size();
                    names.add(e[0]);
                    appPackages.add(e[1]);
                }
                if (a.packageName != null && a.packageName.length() > 0 && !appPackages.contains(a.packageName)) {
                    sel = names.size();
                    names.add(a.packageName + " (nu e instalată)");
                    appPackages.add(a.packageName);
                }
                if (names.isEmpty()) names.add("(nicio aplicație găsită)");
                app = spinner(c, f, "Aplicația", names.toArray(new String[0]), sel);
                break;
            }
            case UNKNOWN:
                f.addView(Ui.muted(c, "Acest pas a fost făcut cu o versiune mai nouă de FreeClick. Îl păstrez neschimbat, dar nu rulează aici."));
                return;
            case IMAGE:
                buildImageTop(c, f, a);
                break;
            case WAIT_COLOR:
                buildColorTop(c, f, a);
                break;
            case GLOBAL: {
                List<String> names = new ArrayList<String>();
                int sel = 0;
                for (int g : GlobalActions.ALL) {
                    if (android.os.Build.VERSION.SDK_INT < GlobalActions.minApi(g)) continue;
                    if (g == a.globalAction) sel = names.size();
                    globalIds.add(g);
                    names.add(GlobalActions.name(g));
                }
                global = spinner(c, f, "Acțiune de sistem", names.toArray(new String[0]), sel);
                break;
            }
            case MACRO: {
                List<String> names = new ArrayList<String>();
                int sel = 0;
                for (Macro m : allMacros) {
                    if (owner != null && MacroGraph.wouldCreateCycle(owner, m.id, lib)) continue;
                    if (m.id.equals(a.macroId)) sel = names.size();
                    macroIds.add(m.id);
                    names.add(Macro.KIND_SCRIPT.equals(m.kind) ? "📜 " + m.name + " (cod)" : m.name); // a script runs as one step
                }
                if (names.isEmpty()) names.add("(nu există alte macro-uri)");
                macro = spinner(c, f, "Macro de rulat (sunt ascunse cele care ar crea o buclă)", names.toArray(new String[0]), sel);
                repeat = Ui.numberRow(f, "De câte ori", a.repeat, false);
                break;
            }
            case PRINT:
                buildPrintTop(c, f, a);
                break;
            case SET_VAR:
                buildSetVarTop(c, f, a);
                break;
            case READ:
                buildReadTop(c, f, a);
                break;
        }

        // ---------------------------------------------------------------- ⚙ Avansat, one group per area
        Section adv = Section.add(f, "form.adv", "⚙ Avansat", "");
        LinearLayout A = adv.body;
        List<String> areas = new ArrayList<String>();
        switch (a.type) {
            case TAP:
            case LONG_PRESS:
            case DOUBLE_TAP: {
                Section s = Section.nested(A, "form.pos", "📍 Locație", "(" + Math.round(a.x) + ", " + Math.round(a.y) + ")");
                x = Ui.numberRow(s.body, "X (px)", Math.round(a.x), false);
                y = Ui.numberRow(s.body, "Y (px)", Math.round(a.y), false);
                areas.add("Locație");
                Section t = Section.nested(A, "form.time", "⏱ Timp", "apăsare " + ActionFormatter.ms(a.duration) + delaySummary(a));
                duration = Ui.numberRow(t.body, "Durata apăsării (ms)", a.duration, false);
                delayRow(t.body, a);
                areas.add("Timp");
                if (tapKind != null) {
                    tapKind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                            long cur = Ui.parseLong(duration, a.duration, 1, 60000);
                            if (TAP_KINDS[pos] == Action.Type.LONG_PRESS && cur < 400) duration.setText(String.valueOf(Action.DEFAULT_LONG_PRESS_MS));
                            if (TAP_KINDS[pos] != Action.Type.LONG_PRESS && cur >= 400) duration.setText(String.valueOf(Action.DEFAULT_TAP_MS));
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) { }
                    });
                }
                break;
            }
            case SWIPE: {
                Section s = Section.nested(A, "form.pos", "📍 Locație", "(" + Math.round(a.x) + ", " + Math.round(a.y) + ") → ("
                        + Math.round(a.x2) + ", " + Math.round(a.y2) + ")");
                x = Ui.numberRow(s.body, "Start X", Math.round(a.x), false);
                y = Ui.numberRow(s.body, "Start Y", Math.round(a.y), false);
                x2 = Ui.numberRow(s.body, "Final X", Math.round(a.x2), false);
                y2 = Ui.numberRow(s.body, "Final Y", Math.round(a.y2), false);
                areas.add("Locație");
                Section t = Section.nested(A, "form.time", "⏱ Timp", "swipe " + ActionFormatter.ms(a.duration) + delaySummary(a));
                duration = Ui.numberRow(t.body, "Durata swipe-ului (ms)", a.duration, false);
                delayRow(t.body, a);
                areas.add("Timp");
                break;
            }
            case WAIT_COLOR: {
                Section s = Section.nested(A, "form.pos", "📍 Locație", "(" + Math.round(a.x) + ", " + Math.round(a.y) + ")");
                x = Ui.numberRow(s.body, "X (px)", Math.round(a.x), false);
                y = Ui.numberRow(s.body, "Y (px)", Math.round(a.y), false);
                areas.add("Locație");
                Section t = Section.nested(A, "form.time", "⏱ Timp", (a.duration > 0 ? "max " + ActionFormatter.ms(a.duration) : "oricât")
                        + (a.stopOnTimeout ? ", apoi stop" : "") + delaySummary(a));
                duration = Ui.numberRow(t.body, "Timp maxim de așteptare (ms, 0 = oricât)", a.duration, false);
                stopOnTimeout = new CheckBox(c);
                stopOnTimeout.setText("Dacă expiră timpul, oprește macro-ul (altfel continuă)");
                stopOnTimeout.setChecked(a.stopOnTimeout);
                t.body.addView(stopOnTimeout);
                delayRow(t.body, a);
                areas.add("Timp");
                break;
            }
            case IMAGE:
                buildImageAdvanced(c, A, a, areas);
                break;
            case READ: {
                if (a.readMode == Action.READ_COLOR) {
                    Section s = Section.nested(A, "form.pos", "📍 Locație", "(" + Math.round(a.x) + ", " + Math.round(a.y) + ")");
                    x = Ui.numberRow(s.body, "X (px)", Math.round(a.x), false);
                    y = Ui.numberRow(s.body, "Y (px)", Math.round(a.y), false);
                    areas.add("Locație");
                } else {
                    Section s = Section.nested(A, "form.zone", "📍 Zona", "(" + Math.round(a.x) + ", " + Math.round(a.y) + ") – ("
                            + Math.round(a.x2) + ", " + Math.round(a.y2) + ")");
                    x = Ui.numberRow(s.body, "Stânga (px)", Math.round(a.x), false);
                    y = Ui.numberRow(s.body, "Sus (px)", Math.round(a.y), false);
                    x2 = Ui.numberRow(s.body, "Dreapta (px)", Math.round(a.x2), false);
                    y2 = Ui.numberRow(s.body, "Jos (px)", Math.round(a.y2), false);
                    areas.add("Zona");
                }
                Section t = Section.nested(A, "form.time", "⏱ Timp", delaySummary(a).replaceFirst("^ · ", ""));
                delayRow(t.body, a);
                areas.add("Timp");
                break;
            }
            case LAUNCH_APP: {
                Section t = Section.nested(A, "form.time", "⏱ Timp", (a.duration > 0 ? "așteaptă max " + ActionFormatter.ms(a.duration) : "nu așteaptă") + delaySummary(a));
                duration = Ui.numberRow(t.body, "Așteaptă să apară max (ms, 0 = nu aștepta)", a.duration, false);
                delayRow(t.body, a);
                areas.add("Timp");
                break;
            }
            default: {
                Section t = Section.nested(A, "form.time", "⏱ Timp", delaySummary(a).replaceFirst("^ · ", ""));
                delayRow(t.body, a);
                areas.add("Timp");
            }
        }
        if (a.type != Action.Type.MACRO) {
            Section r = Section.nested(A, "form.repeat", "🔁 Repetare",
                    a.repeat > 1 ? "×" + a.repeat + ", pauză " + ActionFormatter.ms(a.interval) : "o dată");
            repeat = Ui.numberRow(r.body, "Repetări", a.repeat, false);
            interval = Ui.numberRow(r.body, "Pauză între repetări (ms)", a.interval, false);
            if (a.type == Action.Type.IMAGE) imgHits = Ui.numberRow(r.body, "Oprește macro-ul după atâtea găsiri (0 = niciodată)", a.maxHits, false);
            areas.add("Repetare");
        }
        Section o = Section.nested(A, "form.other", "📝 Altele", (a.enabled ? "activă" : "dezactivată")
                + (a.label != null && a.label.length() > 0 ? " · " + a.label : ""));
        enabled = new CheckBox(c);
        enabled.setText("Activă");
        enabled.setChecked(a.enabled);
        o.body.addView(enabled);
        if (a.type == Action.Type.IMAGE && a.img != null) imgName = Ui.textRow(o.body, "Nume (apare în mesaje)", a.img.name == null ? "" : a.img.name);
        label = Ui.textRow(o.body, "Notă (opțional)", a.label);
        areas.add("Altele");
        adv.setSummary(join(areas));
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(", "); b.append(s); }
        return b.toString();
    }

    private static String delaySummary(Action a) { return a.delay > 0 ? " · după " + ActionFormatter.ms(a.delay) : ""; }

    private void delayRow(LinearLayout parent, Action a) {
        delay = Ui.numberRow(parent, "Întârziere înainte (ms)", a.delay, false);
    }

    private Spinner spinner(Context c, LinearLayout f, String lbl, String[] items, int sel) {
        f.addView(Ui.muted(c, lbl));
        Spinner sp = new Spinner(c);
        sp.setAdapter(new ArrayAdapter<String>(c, android.R.layout.simple_spinner_dropdown_item, items));
        sp.setSelection(Math.max(0, Math.min(items.length - 1, sel)));
        f.addView(sp);
        return sp;
    }

    // ------------------------------------------------------------------ wait for a colour
    private TextView colorText;
    private View colorSwatch;

    private void buildColorTop(final Context c, LinearLayout f, final Action a) {
        colorWhen = spinner(c, f, "Așteaptă până când culoarea", new String[]{"apare în punct", "dispare din punct"}, a.invert ? 1 : 0);
        LinearLayout row = Ui.horizontal(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 10);
        row.setPadding(p, p, p, p);
        row.setBackground(Ui.rounded(0x22FFFFFF, Ui.dp(c, 10)));
        colorSwatch = new View(c);
        row.addView(colorSwatch, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        colorText = Ui.text(c, "", 15, Ui.TEXT, true);
        colorText.setPadding(Ui.dp(c, 12), 0, 0, 0);
        row.addView(colorText, Ui.weight(1));
        row.addView(Ui.text(c, "✏", 18, Ui.ACCENT, true));
        Ui.clickable(row, new View.OnClickListener() {
            @Override public void onClick(View v) { openColorDialog(); }
        }, Ui.dp(c, 10));
        f.addView(row, Ui.matchWrap());
        showColor();
        if (tools != null) {
            f.addView(Ui.button(c, "🎯 Alege punctul și culoarea de pe ecran", new View.OnClickListener() {
                @Override public void onClick(View v) { apply(); tools.pickColor(a); }
            }));
        }
    }

    private void showColor() {
        if (colorSwatch == null) return;
        colorSwatch.setBackground(Ui.rounded(0xFF000000 | colorValue, Ui.dp(ctx, 8)));
        colorText.setText(ColorMatch.toHex(colorValue) + " · toleranță " + tolValue);
    }

    private void openColorDialog() {
        Runnable pick = tools == null ? null : new Runnable() {
            @Override public void run() { apply(); tools.pickColor(action); }
        };
        ColorDialog.show(ctx, "Culoare", colorValue, tolValue, pick, new ColorDialog.OnColor() {
            @Override public void onColor(int rgb, int tolerance) {
                colorValue = rgb;
                tolValue = tolerance;
                showColor();
            }
        }, overlay);
    }

    // ------------------------------------------------------------------ console / variables / reading
    private void buildPrintTop(Context c, LinearLayout f, Action a) {
        f.addView(Ui.muted(c, "Textul apare în consolă (≡ → Consolă). Pune variabile între acolade: {aur}, chiar și calcule: {aur * 2}. "
                + "Incluse: {tura}, {timp}, {ora}."));
        text = new EditText(c);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        text.setMinLines(2);
        text.setText(a.text);
        f.addView(text, Ui.matchWrap());
        final TextView check = Ui.text(c, "", 13, Ui.MUTED, false);
        f.addView(check);
        liveCheck(text, check, true);
    }

    private void buildSetVarTop(Context c, LinearLayout f, Action a) {
        varName = Ui.textRow(f, "Variabila", a.varName);
        varName.setHint("de ex. aur, n, vieti");
        f.addView(Ui.muted(c, "primește valoarea"));
        text = new EditText(c);
        text.setSingleLine(true);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        text.setText(a.text);
        text.setHint("n + 1");
        f.addView(text, Ui.matchWrap());
        final TextView check = Ui.text(c, "", 13, Ui.MUTED, false);
        f.addView(check);
        liveCheck(text, check, false);
        f.addView(Ui.muted(c, "Exemple: n + 1 (numără) · aur - 100 · random(1, 6) · max(a, b) · nume + \"!\" · aur >= 1000 (1 = da, 0 = nu). "
                + "O variabilă nesetată valorează 0."));
    }

    private void buildReadTop(Context c, LinearLayout f, final Action a) {
        readMode = spinner(c, f, "Citește", new String[]{"Un număr din zonă (12 345 · 1,5k · 45%)", "Textul din zonă (un nume, un mesaj)",
                "Culoarea unui punct (#RRGGBB)"}, a.readMode);
        varName = Ui.textRow(f, "în variabila", a.varName);
        varName.setHint("de ex. aur");
        LinearLayout btns = Ui.horizontal(c);
        if (tools != null) {
            if (a.readMode == Action.READ_COLOR) {
                btns.addView(Ui.button(c, "🎯 Alege punctul", new View.OnClickListener() {
                    @Override public void onClick(View v) { apply(); tools.pickColor(a); }
                }));
            } else {
                btns.addView(Ui.button(c, "▭ Alege zona", new View.OnClickListener() {
                    @Override public void onClick(View v) { apply(); tools.pickZone(a); }
                }));
                btns.addView(Ui.button(c, "🔍 Testează citirea", new View.OnClickListener() {
                    @Override public void onClick(View v) { apply(); tools.testRead(a); }
                }));
            }
        }
        f.addView(btns, Ui.matchWrap());
        f.addView(Ui.muted(c, "Afișezi valoarea cu un pas „Afișează în consolă”: Aur = {" + (a.varName.length() > 0 ? a.varName : "aur") + "}. "
                + "Se citește de 2 ori la rând (un număr care încă se schimbă nu dă valori greșite); "
                + "dacă nu reușește, variabila rămâne cum era."));
        readMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int first = a.readMode;
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == first) return;
                first = pos;
                apply();  // keep what was typed
                build();  // the zone / point fields differ
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }

    /** Shows under the field whether the expression / template is right, as you type. */
    private void liveCheck(final EditText e, final TextView out, final boolean template) {
        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            public void onTextChanged(CharSequence s, int a, int b, int d) { }
            public void afterTextChanged(Editable ed) {
                String t = ed.toString();
                String err = template ? Expr.checkTemplate(t) : (t.trim().isEmpty() ? "Scrie o valoare sau un calcul" : Expr.check(t));
                out.setText(err == null ? "✅ corect" : "⚠ " + err);
                out.setTextColor(err == null ? Ui.ACCENT : Ui.DANGER);
            }
        };
        e.addTextChangedListener(w);
        w.afterTextChanged(e.getText());
    }

    // ------------------------------------------------------------------ image step
    private static final String[] DOES = {"Apasă pe ea", "Apasă lung pe ea", "Doar așteaptă să apară (nu apasă)", "Așteaptă să dispară"};
    private static final String[] MISSING = {"Mergi mai departe", "Așteaptă până apare, apoi mergi mai departe",
            "Așteaptă până apare, altfel oprește macro-ul"};
    private static final String[] PRECISION = {"Strictă (doar identic)", "Normală", "Tolerantă (acceptă și mărimi puțin diferite)", "Avansat (număr)"};
    private static final int[] PRECISION_VALUES = {com.freeclick.core.ImageSpec.PRECISION_STRICT, com.freeclick.core.ImageSpec.PRECISION_NORMAL,
            com.freeclick.core.ImageSpec.PRECISION_TOLERANT};
    private static final String[] WHERE = {"Pe tot ecranul", "Doar în jurul locului unde a fost decupată", "Doar în jumătatea de sus", "Doar în jumătatea de jos"};
    private static final String[] MANY = {"Apasă pe cea mai de sus", "Apasă pe toate (de sus în jos)"};

    private void buildImageTop(Context c, LinearLayout f, final Action a) {
        com.freeclick.core.ImageSpec sp = a.img;
        LinearLayout top = Ui.horizontal(c);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.ImageView thumb = new android.widget.ImageView(c);
        android.graphics.Bitmap bm = App.get().images().bitmap(sp.id);
        if (bm != null) thumb.setImageBitmap(bm);
        else thumb.setImageResource(android.R.drawable.ic_menu_report_image);
        thumb.setAdjustViewBounds(true);
        thumb.setMaxHeight(Ui.dp(c, 72));
        thumb.setMaxWidth(Ui.dp(c, 160));
        top.addView(thumb);
        LinearLayout btns = Ui.vertical(c);
        if (tools != null) {
            btns.addView(Ui.button(c, "🔍 Testează acum", new View.OnClickListener() {
                @Override public void onClick(View v) { apply(); tools.testImage(a); }
            }));
            btns.addView(Ui.button(c, "✂ Recapturează", new View.OnClickListener() {
                @Override public void onClick(View v) { apply(); tools.recaptureImage(a); }
            }));
        }
        top.addView(btns, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        f.addView(top);
        if (bm == null) f.addView(Ui.muted(c, "⚠ Poza acestui pas lipsește (de ex. fișier importat fără ea). Recapturează imaginea."));
        imgDoes = spinner(c, f, "Când e pe ecran", DOES, a.press);
        int missing = a.waitMode == Action.MISSING_CONTINUE ? 0 : a.stopOnTimeout ? 2 : 1;
        imgMissing = spinner(c, f, "Dacă nu e pe ecran", MISSING, missing);
        f.addView(Ui.muted(c, "Crucea de pe țintă arată unde apasă: o poți trage (de ex. puțin lângă imagine)."));
    }

    private void buildImageAdvanced(Context c, LinearLayout A, Action a, List<String> areas) {
        com.freeclick.core.ImageSpec sp = a.img;
        int pk = 3;
        for (int i = 0; i < PRECISION_VALUES.length; i++) if (PRECISION_VALUES[i] == sp.precision) pk = i;
        Section s = Section.nested(A, "form.precision", "🎯 Precizie și zonă", PRECISION[pk].replaceFirst(" \\(.*", "") + " · "
                + WHERE[whereOf(sp)].toLowerCase(java.util.Locale.ROOT) + (a.allMatches ? " · toate" : ""));
        imgPrecisionKind = spinner(c, s.body, "Precizie", PRECISION, pk);
        final LinearLayout advRow = Ui.vertical(c);
        imgPrecision = Ui.numberRow(advRow, "Precizie (70 tolerant … 98 strict)", sp.precision, false);
        advRow.setVisibility(pk == 3 ? View.VISIBLE : View.GONE);
        s.body.addView(advRow);
        imgPrecisionKind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { advRow.setVisibility(pos == 3 ? View.VISIBLE : View.GONE); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        imgWhere = spinner(c, s.body, "Unde caută", WHERE, whereOf(sp));
        imgMany = spinner(c, s.body, "Dacă apare de mai multe ori", MANY, a.allMatches ? 1 : 0);
        areas.add("Precizie");
        Section t = Section.nested(A, "form.time", "⏱ Timp", (a.duration > 0 ? "max " + ActionFormatter.ms(a.duration) : "oricât")
                + " · apăsare " + ActionFormatter.ms(a.pressMs) + delaySummary(a));
        duration = Ui.numberRow(t.body, "Cât aștepți cel mult (ms, 0 = oricât)", a.duration, false);
        imgPress = Ui.numberRow(t.body, "Durata apăsării (ms)", a.pressMs, false);
        delayRow(t.body, a);
        areas.add("Timp");
    }

    /** Which "Unde caută" choice a zone corresponds to. */
    private static int whereOf(com.freeclick.core.ImageSpec s) {
        if (!s.hasZone()) return 0;
        if (s.capScreenH > 0 && s.zx1 == 0 && s.zy1 == 0 && s.zx2 == s.capScreenW && s.zy2 == s.capScreenH / 2f) return 2;
        if (s.capScreenH > 0 && s.zx1 == 0 && s.zy1 == s.capScreenH / 2f && s.zx2 == s.capScreenW && s.zy2 == s.capScreenH) return 3;
        return 1;
    }

    private static void applyWhere(com.freeclick.core.ImageSpec s, int where) {
        int W = s.capScreenW > 0 ? s.capScreenW : 1080, H = s.capScreenH > 0 ? s.capScreenH : 2400;
        switch (where) {
            case 1: {
                if (whereOf(s) == 1) return; // keep a zone that was already "around the cut-out"
                float mx = Math.max(s.w * 1.5f, W / 6f), my = Math.max(s.h * 1.5f, H / 8f);
                s.zx1 = Math.max(0, s.capX - mx); s.zy1 = Math.max(0, s.capY - my);
                s.zx2 = Math.min(W, s.capX + s.w + mx); s.zy2 = Math.min(H, s.capY + s.h + my);
                break;
            }
            case 2: s.zx1 = 0; s.zy1 = 0; s.zx2 = W; s.zy2 = H / 2f; break;
            case 3: s.zx1 = 0; s.zy1 = H / 2f; s.zx2 = W; s.zy2 = H; break;
            default: s.zx1 = s.zy1 = s.zx2 = s.zy2 = 0;
        }
    }

    /** The label TextView that Ui.numberRow put right before the given field, if any. */
    private static TextView findLabel(LinearLayout parent, View field) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout l = (LinearLayout) v;
                for (int k = 1; k < l.getChildCount(); k++) {
                    if (l.getChildAt(k) == field && l.getChildAt(k - 1) instanceof TextView) return (TextView) l.getChildAt(k - 1);
                }
            }
            if (v == field && i > 0 && parent.getChildAt(i - 1) instanceof TextView) return (TextView) parent.getChildAt(i - 1);
        }
        return null;
    }

    /** {label, package} of every app that has a launcher icon, sorted by name (ours excluded). */
    static List<String[]> launchableApps(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
                out.add(new String[]{r.loadLabel(pm).toString(), pkg});
            }
        } catch (RuntimeException ignored) { }
        java.util.Collections.sort(out, new java.util.Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) { return a[0].compareToIgnoreCase(b[0]); }
        });
        return out;
    }

    /** Writes the form into the action. */
    void apply() {
        Action a = action;
        if (a.type == Action.Type.UNKNOWN) return; // kept exactly as it was written
        if (tapKind != null) a.type = TAP_KINDS[tapKind.getSelectedItemPosition()];
        if (x != null) a.x = Ui.parseLong(x, Math.round(a.x), 0, 100000);
        if (y != null) a.y = Ui.parseLong(y, Math.round(a.y), 0, 100000);
        if (x2 != null) a.x2 = Ui.parseLong(x2, Math.round(a.x2), 0, 100000);
        if (y2 != null) a.y2 = Ui.parseLong(y2, Math.round(a.y2), 0, 100000);
        if (duration != null) {
            boolean waitLike = a.type == Action.Type.WAIT || a.type == Action.Type.WAIT_COLOR || a.type == Action.Type.LAUNCH_APP || a.type == Action.Type.IMAGE;
            a.duration = Ui.parseLong(duration, a.duration, waitLike ? 0 : 1, waitLike ? 24L * 3600 * 1000 : 60000);
        }
        if (zoomDir != null && a.isZoom()) {
            int span = (int) Ui.parseLong(zoomSpan, Math.abs(a.zoomSpan), 80, 4000);
            a.zoomSpan = zoomDir.getSelectedItemPosition() == 0 ? span : -span;
            a.rebuildZoom();
        }
        if (randomWait != null) {
            long max = Ui.parseLong(durationMax, a.durationMax, 0, 24L * 3600 * 1000);
            a.durationMax = randomWait.isChecked() && max > a.duration ? max : 0;
        }
        if (app != null && !appPackages.isEmpty()) a.packageName = appPackages.get(app.getSelectedItemPosition());
        if (a.type == Action.Type.IMAGE && a.img != null && imgDoes != null) {
            if (imgName != null) a.img.name = imgName.getText().toString().trim();
            a.press = imgDoes.getSelectedItemPosition();
            int missing = imgMissing.getSelectedItemPosition();
            a.waitMode = missing == 0 ? Action.MISSING_CONTINUE : Action.MISSING_WAIT;
            a.stopOnTimeout = missing == 2;
            if (imgPrecisionKind != null) {
                int pk = imgPrecisionKind.getSelectedItemPosition();
                a.img.precision = pk < 3 ? PRECISION_VALUES[pk] : (int) Ui.parseLong(imgPrecision, a.img.precision, 70, 98);
            }
            if (imgWhere != null) applyWhere(a.img, imgWhere.getSelectedItemPosition());
            if (imgMany != null) a.allMatches = imgMany.getSelectedItemPosition() == 1;
            if (imgHits != null) a.maxHits = (int) Ui.parseLong(imgHits, a.maxHits, 0, 1000000);
            if (imgPress != null) a.pressMs = Ui.parseLong(imgPress, a.pressMs, 1, 60000);
        }
        if (a.type == Action.Type.WAIT_COLOR) {
            a.color = colorValue & 0xFFFFFF;
            a.tolerance = Math.max(0, Math.min(255, tolValue));
            if (colorWhen != null) a.invert = colorWhen.getSelectedItemPosition() == 1;
        }
        if (stopOnTimeout != null && a.type != Action.Type.IMAGE) a.stopOnTimeout = stopOnTimeout.isChecked();
        if (global != null && !globalIds.isEmpty()) a.globalAction = globalIds.get(global.getSelectedItemPosition());
        if (macro != null && !macroIds.isEmpty()) a.macroId = macroIds.get(macro.getSelectedItemPosition());
        if (text != null) a.text = text.getText().toString();
        if (varName != null) a.varName = varName.getText().toString().trim();
        if (readMode != null) {
            int m = readMode.getSelectedItemPosition();
            if (m != Action.READ_COLOR && a.readMode == Action.READ_COLOR) {
                // from a point to a zone: start with a box around the point
                a.x2 = a.x + 200; a.y2 = a.y + 60;
            }
            if (m == Action.READ_COLOR) { a.x2 = a.x; a.y2 = a.y; }
            a.readMode = m;
        }
        if (delay != null) a.delay = Ui.parseLong(delay, a.delay, 0, 24L * 3600 * 1000);
        if (repeat != null) a.repeat = (int) Ui.parseLong(repeat, a.repeat, 1, 1000000);
        if (interval != null) a.interval = Ui.parseLong(interval, a.interval, 0, 24L * 3600 * 1000);
        if (enabled != null) a.enabled = enabled.isChecked();
        if (label != null) a.label = label.getText().toString();
    }

    /** Why the variable name cannot be used (null if it can). Shown when saving a new step. */
    static String nameProblem(String n) {
        if (n == null || n.trim().isEmpty()) return "Dă un nume variabilei (de ex. aur).";
        if (PlaybackEngine.isBuiltinVar(n.trim())) return "„" + n.trim() + "” e o variabilă a aplicației; alege alt nume.";
        if (!Expr.isValidName(n.trim())) return "Numele începe cu o literă și are doar litere, cifre și _ (fără spații).";
        return null;
    }
}
