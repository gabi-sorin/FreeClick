package com.freeclick.core.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "What does this function expect?" while typing, like an IDE for C++: when the cursor is inside a
 * call, the editor shows the signature (return type, parameters with names, default values) and
 * marks the parameter being written. Knows the app's functions, string / vector methods, and the
 * script's own functions and structs (found in the text, even while it is half-written).
 */
public final class Signatures {
    private Signatures() {}

    /** One hint: the signature text and where the current parameter is in it (-1 if none). */
    public static final class Hint {
        public final String text;
        public final int paramStart, paramEnd;
        /** Other ways to call it (overloads), not shown in full. */
        public final int others;

        Hint(String text, int ps, int pe, int others) { this.text = text; this.paramStart = ps; this.paramEnd = pe; this.others = others; }
    }

    private static final Map<String, List<String>> FUNCS = new HashMap<String, List<String>>();
    private static final Map<String, List<String>> METHODS = new HashMap<String, List<String>>();

    private static void f(String names, String... sigs) {
        for (String n : names.split("\\|")) {
            List<String> l = new ArrayList<String>();
            for (String s : sigs) l.add(s.replace("$", n));
            FUNCS.put(n, l);
        }
    }

    /** What a function takes, written out ("void tap(point p, …)"); empty if it is not one of the app's. */
    static List<String> of(String name) {
        List<String> l = FUNCS.get(name);
        return l == null ? new ArrayList<String>() : l;
    }

    private static void m(String names, String... sigs) {
        for (String n : names.split("\\|")) {
            List<String> l = METHODS.get(n);
            if (l == null) { l = new ArrayList<String>(); METHODS.put(n, l); }
            for (String s : sigs) l.add(s.replace("$", n));
        }
    }

    static {
        String P = "int pauza = INTERVAL_REPETARE";
        f("tap|atinge|click", "void $(point p, int durata = DURATA_CLICK, " + P + ")", "void $(int x, int y, int durata = DURATA_CLICK, " + P + ")");
        f("apasaLung|longPress|apasa_lung", "void $(point p, int durata = 800, " + P + ")", "void $(int x, int y, int durata = 800, " + P + ")");
        f("dubluTap|doubleTap|dublu_tap", "void $(point p, " + P + ")", "void $(int x, int y, " + P + ")");
        f("swipe|gliseaza", "void $(point de_la, point pana_la, int durata = DURATA_SWIPE, " + P + ")",
                "void $(int x1, int y1, int x2, int y2, int durata = DURATA_SWIPE, " + P + ")");
        f("trage|drag", "void $(point de_la, point pana_la, int ms_miscare = 400, int ms_tinut = 500)");
        f("wait|pauza|sleep|delay|asteapta", "void $(int ms)   // ex. $(2s), $(500)");
        f("waitRandom|pauzaAleatoare|pauza_aleatoare", "void $(int min_ms, int max_ms)");
        f("back|inapoi", "void $()   // butonul Înapoi");
        f("home|acasa", "void $()   // ecranul principal");
        f("recente|recents", "void $()   // aplicațiile recente");
        f("notificari|notifications", "void $()   // trage bara de notificări");
        f("deschide|openApp|open_app", "bool $(string pachet)   // ex. \"com.android.chrome\"");
        f("stop|opreste", "void $()   // oprește scriptul");
        f("toast|mesaj", "void $(string text)");
        f("vibreaza|vibrate", "void $(int ms = 300)");
        f("pixel|culoare", "color $(point p)", "color $(int x, int y)");
        f("pipeta", "color $(point p)", "color $(int x, int y)");
        f("pipetaAtingere|get_touch_pipeta|getTouchPipeta|pickColorByTouch|pick_touch_color", "color $(int ms_maxim = fără limită)   // așteaptă să atingi; culoarea de acolo");
        f("aproape|near", "bool $(color a, color b, int toleranta = 24)");
        f("citeste|read|citesteNumar", "double $(rect zona)   // numărul din zonă");
        f("citesteText|readText|citeste_text", "string $(rect zona)");
        f("apare|visible|vizibil", "bool $(string imagine)");
        f("gaseste|find", "point $(string imagine)   // point(-1, -1) dacă nu e");
        f("gasesteToate|findAll|gaseste_toate", "vector<point> $(string imagine)");
        f("asteaptaImagine|waitImage|asteapta_imagine", "bool $(string imagine, int ms_maxim)");
        f("asteaptaCuloare|waitColor|asteapta_culoare", "bool $(point p, color c, int ms_maxim = 30000, int toleranta = 24)");
        f("exista|found|valid", "bool $(point p)   // false pentru point(-1, -1)");
        f("latime|screenW|screenWidth", "int $()");
        f("inaltime|screenH|screenHeight", "int $()");
        f("ecran|screen", "rect $()   // tot ecranul");
        f("timp|millis", "int $()   // ms de la pornirea scriptului");
        f("ora|clock", "string $()   // ex. \"14:05\"");
        f("oraNum|hour", "int $()");
        f("minut|minute", "int $()");
        f("ziua|weekday", "int $()   // 1 = luni … 7 = duminică");
        f("random|aleator|rand", "int $(int min, int max)");
        f("sqrt", "double $(double x)");
        f("pow", "double $(double baza, double exponent)");
        f("round|rotunjit", "double $(double x)");
        f("floor", "double $(double x)");
        f("ceil", "double $(double x)");
        f("to_int|stoi|toInt|numar", "int $(string text)");
        f("to_double|stod|toDouble", "double $(string text)");
        f("dist|distanta", "double $(point a, point b)");
        f("centru|center", "point $(rect r)");
        f("contine|contains", "bool $(rect r, point p)");
        f("hex", "string $(color c)   // \"#RRGGBB\"");
        f("ruleaza|run|reda|replay", "void $(vector<Pas> pasi, int ori = 1)", "void $(Pas pas, int ori = 1)");
        f("pasiDin|stepsOf|pasi_din", "vector<Pas> $(string salvare)");
        f("ruleazaMacro|runMacro|ruleaza_macro", "void $(string salvare, int ori = 1)");
        f("pasTap|stepTap", "Pas $(point p)");
        f("pasSwipe|stepSwipe", "Pas $(point a, point b, int durata = 300)");
        f("pasPauza|stepWait", "Pas $(int ms)");
        f("pasApasaLung|stepLongPress", "Pas $(point p, int durata = 800)");
        f("asteaptaLiniste|waitStill|asteapta_liniste", "bool $(rect zona = ecran(), int ms_liniste = 800, int ms_maxim = 10000)   // nimic nu se mai mișcă");
        f("asteaptaSchimbare|waitChange|asteapta_schimbare", "bool $(rect zona, int ms_maxim = 30000)   // ceva se schimbă în zonă");
        f("model|pattern|modelPixeli", "bool $(vector<point> puncte, vector<color> culori, int toleranta = 24)   // toate se potrivesc");
        f("tapInZona|tapAleator|tapInZone|atingeInZona", "void $(rect zona, int durata = DURATA_CLICK, " + P + ")   // un loc la întâmplare din zonă");
        f("salveazaCaptura|saveScreenshot|salveaza_captura", "bool $(string nume = \"freeclick\")   // Galerie → FreeClick");
        f("copiaza|copy|clipboard", "void $(string text)");
        f("deschideLink|openUrl|deschide_link", "bool $(string link)   // https://… sau link-ul unei aplicații");
        f("afiseazaPeEcran|showText|show_text|afiseaza_pe_ecran", "void $(string text)   // sus, la mijloc", "void $(string text, point unde)   // \"\" îl ascunde");
        f("ascundeTextul|hideText|hide_text", "void $()");
        f("baterie|battery", "int $()   // 0…100");
        f("seIncarca|charging|isCharging", "bool $()");
        f("orientare|orientation", "string $()   // \"portret\" sau \"peisaj\"");
        f("notifica|notify", "void $(string text)", "void $(string titlu, string text)");
        f("asteaptaTapuri|waitTaps|wait_taps", "point $(int cate, int ms_maxim = fără limită)   // 3 = tap triplu; locul");
        f("gesturiNoi|newGestures|new_gestures", "int $()   // câte gesturi ai făcut de la ultimul apel (cere asculta(true))");
        f("factorGest|gestureScale", "double $()   // zoom: cât s-au depărtat degetele (2 = de două ori)");
        f("rotireGest|gestureRotation", "double $()   // rotire: câte grade (+ în sensul acelor de ceas)");
        f("buton|button|adaugaButon", "int $(string text, rect zona)   // un buton al tău peste joc; îți dă numărul lui");
        f("apasat|pressed|butonApasat", "bool $(int buton)   // l-ai apăsat de la ultima întrebare");
        f("asteaptaButon|waitButton|wait_button", "int $(int ms_maxim = fără limită)   // numărul butonului apăsat; -1 dacă a expirat");
        f("stergeButon|removeButton", "void $(int buton)");
        f("stergeButoanele|removeButtons", "void $()");
        f("ascultaInZona|listenIn|listen_in", "void $(rect zona)   // aude doar gesturile care încep aici; restul merge direct în joc");
        f("ascultaPeTotEcranul|listenEverywhere", "void $()");
        f("asteaptaOricare|waitAny|wait_any", "int $(vector<string> imagini, int ms_maxim = fără limită)   // a câta a apărut; -1 dacă a expirat");
        f("intreaba|ask", "string $(string intrebare, string implicit = \"\")   // te întreabă cât rulează");
        f("intreabaNumar|askNumber|ask_number", "int $(string intrebare, int implicit = 0)");
        f("alege|choose", "int $(string titlu, vector<string> variante)   // a câta variantă; -1 la Anulează");
        f("scrieFisier|writeFile|write_file", "void $(string nume, string text)   // în folderul FreeClick; îl înlocuiește");
        f("adaugaInFisier|appendFile|append_file", "void $(string nume, string text)   // la sfârșit");
        f("citesteFisier|readFile|read_file", "string $(string nume)   // \"\" dacă nu există");
        f("existaFisier|fileExists|file_exists", "bool $(string nume)");
        f("swipeCurb|curvedSwipe|swipe_curb", "void $(point de_la, point pana_la, int durata = DURATA_SWIPE, double curbura = 0.25, int pauza = INTERVAL_REPETARE)");
        f("inregistreaza|record|inregistrare", "vector<Pas> $(int liniste_ms = 3000, int ms_maxim = 60000)   // ce faci tu, ca pași; reda(…) le repetă");
        f("procentCuloare|colorPercent|percentColor|procent_culoare", "double $(rect zona, color culoare, int toleranta = 30)   // 0…100: cât de plină e bara");
        f("gasesteCuloare|findColor|find_color|gaseste_culoare", "point $(rect zona, color culoare, int toleranta = 30)   // mijlocul petei; (-1, -1) dacă nu e");
        f("atingeDeodata|tapAll|tap_all|multiTap|atinge_deodata", "void $(vector<point> puncte, int durata = DURATA_CLICK, int pauza = INTERVAL_REPETARE)   // toate în același moment");
        // 2.3: your fingers
        String T = "int ms_maxim = fără limită";
        f("asteaptaAtingere|waitForTouch|wait_for_touch|asteapta_atingere", "point $(" + T + ")   // unde ai atins; (-1, -1) dacă a expirat");
        f("asteaptaGest|waitGesture|wait_gesture|asteapta_gest", "string $(" + T + ")   // \"tap\", \"lung\", \"swipe sus\", \"2 degete\", \"zoom mareste\", \"rotire dreapta\"…");
        f("asteaptaSwipe|waitSwipe|wait_swipe|asteapta_swipe", "string $(" + T + ")   // \"sus\", \"jos\", \"stanga\", \"dreapta\"; \"\" dacă a expirat");
        f("asteaptaDubluTap|waitDoubleTap|wait_double_tap", "point $(" + T + ")");
        f("asteaptaApasareLunga|waitLongPress|wait_long_press", "point $(" + T + ")");
        f("asteaptaDegete|waitFingers|wait_fingers|asteapta_degete", "bool $(int cate, " + T + ")   // atâtea degete deodată pe ecran");
        f("asteaptaFaraDegete|asteaptaRidicare|waitNoFingers|wait_release", "bool $(" + T + ")   // niciun deget pe ecran");
        f("degete|fingers", "int $()   // câte degete sunt pe ecran acum (cere asculta(true))");
        f("atins|touching|ecranAtins", "bool $()   // e vreun deget pe ecran acum (cere asculta(true))");
        f("pozitieDeget|fingerPos|finger", "point $(int al_catelea = 0)   // unde e degetul acum (cere asculta(true))");
        f("ultimaAtingere|lastTouch|last_touch", "point $()   // unde ai atins ultima dată (cere asculta(true))");
        f("atingeriNoi|newTaps|new_taps", "int $()   // câte tap-uri ai dat de la ultimul apel (cere asculta(true))");
        f("asteaptaAtingereInZona|waitTouchIn|wait_touch_in", "bool $(rect zona, " + T + ")");
        f("alegeZona|pickZone|pick_zone", "rect $(" + T + ")   // tragi cu degetul un dreptunghi");
        f("asculta|listen", "void $(bool da)   // true: ascultă tot timpul (pentru degete(), atins()…)");
        f("treciMaiDeparte|passThrough|forwardTouches", "void $(bool da)   // atingerile tale ajung și în joc (implicit da)");
        f("inceputGest|gestureStart", "point $()   // unde a început ultimul gest");
        f("sfarsitGest|gestureEnd", "point $()   // unde s-a terminat ultimul gest");
        f("degeteGest|gestureFingers", "int $()   // câte degete a avut ultimul gest");
        f("durataGest|gestureMs", "int $()   // cât a durat ultimul gest (ms)");
        f("zoomIn|mareste", "void $(point centru, int distanta = 400, int durata = 400)   // două degete se depărtează");
        f("zoomOut|micsoreaza", "void $(point centru, int distanta = 400, int durata = 400)   // două degete se apropie");
        f("print|afiseaza|println", "void $(...)   // oricâte valori, cu virgulă între ele");
        f("to_string|text|toString", "string $(T valoare)");
        f("abs", "T $(T x)");
        f("min", "T $(T a, T b)");
        f("max", "T $(T a, T b)");
        f("make_pair", "pair<A, B> $(A first, B second)");
        f("sort", "void $(vector<T>& v)");
        f("reverse", "void $(vector<T>& v)");
        f("swap", "void $(T& a, T& b)");
        f("point", "point(int x, int y)");
        f("rect", "rect(int x1, int y1, int x2, int y2)");
        f("cand", "cand (bool conditie) { … }   // când devine adevărată, oricând");
        f("la_fiecare", "la_fiecare (int ms) { … }   // ex. la_fiecare (30s)");
        f("la_ora", "la_ora (string ora) { … }   // ex. la_ora (\"22:00\")");

        m("size|length", "int string.$()", "int vector.$()");
        m("empty", "bool string.$()", "bool vector.$()");
        m("substr", "string string.$(int de_la, int lungime = pana_la_capat)");
        m("find", "int string.$(string text)   // -1 dacă nu e");
        m("contine|contains", "bool string.$(string text)", "bool vector.$(T element)");
        m("starts_with", "bool string.$(string inceput)");
        m("ends_with", "bool string.$(string sfarsit)");
        m("upper|mari", "string string.$()");
        m("lower|mici", "string string.$()");
        m("replace|inlocuieste", "string string.$(string ce, string cu)");
        m("trim", "string string.$()");
        m("push_back|adauga", "void vector.$(T element)");
        m("pop_back", "void vector.$()");
        m("clear", "void vector.$()");
        m("back", "T vector.$()   // ultimul");
        m("front", "T vector.$()   // primul");
        m("erase", "void vector.$(int pozitie)");
        m("insert", "void vector.$(int pozitie, T element)");
        m("index_of|pozitia", "int vector.$(T element)   // -1 dacă nu e");
        m("hex", "string color.$()   // \"#RRGGBB\"");
    }

    static Set<String> methodNames() { return METHODS.keySet(); }

    private static final Set<String> NOT_CALLS = new HashSet<String>(Arrays.asList(
            "if", "while", "for", "switch", "return", "daca", "cat_timp", "sizeof", "catch"));

    // ------------------------------------------------------------------ the script's own functions
    private static final String TYPE = "(?:const\\s+)?[A-Za-z_][A-Za-z0-9_]*(?:\\s*<[^;{}()]*>)?\\s*[&*]?";
    private static final Pattern FUNC = Pattern.compile("(?m)^[ \\t]*(" + TYPE + ")\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^(){};]*)\\)\\s*(?:const\\s*)?\\{");
    private static final Pattern STRUCT = Pattern.compile("\\bstruct\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{([^{}]*)\\}");

    /** name → signatures written in the script (functions and struct constructors). */
    public static Map<String, List<String>> userFunctions(String src) {
        Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        String clean = stripCommentsAndStrings(src);
        Matcher m = FUNC.matcher(clean);
        while (m.find()) {
            String type = m.group(1).trim().replaceAll("\\s+", " "), name = m.group(2);
            if (NOT_CALLS.contains(name) || type.equals("return") || type.equals("else") || type.equals("new")) continue;
            String params = m.group(3).trim().replaceAll("\\s+", " ");
            add(out, name, type + " " + name + "(" + params + ")   // funcția ta");
        }
        Matcher s = STRUCT.matcher(clean);
        while (s.find()) {
            String name = s.group(1);
            StringBuilder fields = new StringBuilder();
            for (String decl : s.group(2).split(";")) {
                String d = decl.trim().replaceAll("\\s+", " ");
                if (d.isEmpty()) continue;
                if (fields.length() > 0) fields.append(", ");
                fields.append(d.replaceAll("\\s*=.*$", ""));
            }
            add(out, name, name + "{" + fields + "}   // struct-ul tău, câmpurile în ordine");
        }
        return out;
    }

    private static void add(Map<String, List<String>> m, String k, String v) {
        List<String> l = m.get(k);
        if (l == null) { l = new ArrayList<String>(); m.put(k, l); }
        l.add(v);
    }

    /** Same length, with comments and the inside of strings blanked (positions stay the same). */
    static String stripCommentsAndStrings(String s) {
        StringBuilder b = new StringBuilder(s);
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') { b.setCharAt(i, ' '); i++; }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) if (s.charAt(k) != '\n') b.setCharAt(k, ' ');
                i = end;
            } else if (c == '"' || c == '\'') {
                int k = i + 1;
                while (k < n && s.charAt(k) != c && s.charAt(k) != '\n') {
                    if (s.charAt(k) == '\\' && k + 1 < n) { b.setCharAt(k, ' '); k++; }
                    b.setCharAt(k, ' ');
                    k++;
                }
                i = Math.min(n, k + 1);
            } else {
                i++;
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ the hint at the cursor
    /** The signature of the call the cursor is in (and which parameter), or null. */
    public static Hint at(String src, int cursor) {
        if (src == null || cursor < 0 || cursor > src.length()) return null;
        String clean = stripCommentsAndStrings(src);
        // open "(" / "{" before the cursor, innermost last; commas counted per level
        int[] open = new int[256], commas = new int[256];
        char[] kind = new char[256];
        int depth = 0;
        for (int i = 0; i < cursor; i++) {
            char c = clean.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                if (depth < open.length) { open[depth] = i; commas[depth] = 0; kind[depth] = c; }
                depth++;
            } else if ((c == ')' || c == '}' || c == ']') && depth > 0) {
                depth--;
            } else if (c == ',' && depth > 0 && depth <= open.length) {
                commas[depth - 1]++;
            } else if (c == ';' && depth > 0 && depth <= open.length && kind[depth - 1] == '(') {
                // "for (a; b; c)": not a call; a stray ";" ends a broken call
                commas[depth - 1] = -1;
            }
        }
        for (int d = Math.min(depth, open.length) - 1; d >= 0; d--) {
            if (kind[d] == '[') continue;
            if (commas[d] < 0) return null;
            int p = open[d] - 1;
            while (p >= 0 && Character.isWhitespace(clean.charAt(p))) p--;
            int end = p + 1;
            while (p >= 0 && (Character.isLetterOrDigit(clean.charAt(p)) || clean.charAt(p) == '_')) p--;
            String name = clean.substring(p + 1, end);
            if (name.isEmpty() || Character.isDigit(name.charAt(0))) {
                if (kind[d] == '{') continue; // a block: look further out
                return null;
            }
            if (kind[d] == '{') { // "Tinta{…}" builds a struct; "struct Tinta {" and blocks do not
                int q = p;
                while (q >= 0 && Character.isWhitespace(clean.charAt(q))) q--;
                boolean decl = q >= 5 && clean.substring(q - 5, q + 1).equals("struct");
                if (decl || !Character.isUpperCase(name.charAt(0))) continue;
            }
            if (NOT_CALLS.contains(name)) return null;
            boolean method = p >= 0 && clean.charAt(p) == '.';
            List<String> sigs = null;
            if (!method) {
                sigs = userFunctions(src).get(name);
                if (sigs == null) sigs = FUNCS.get(name);
                if (sigs == null && src.indexOf("#define") >= 0) { // "#define p point": p( shows what point( takes
                    String to = Lexer.defines(src).get(name);
                    if (to != null && to.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        sigs = userFunctions(src).get(to);
                        if (sigs == null) sigs = FUNCS.get(to);
                    }
                }
            } else {
                sigs = METHODS.get(name);
            }
            if (sigs == null || sigs.isEmpty()) return null;
            return pick(sigs, commas[d]);
        }
        return null;
    }

    /** The first signature with enough parameters for the one being written; that parameter marked. */
    private static Hint pick(List<String> sigs, int index) {
        String best = null;
        for (String s : sigs) if (paramSpan(s, index) != null) { best = s; break; }
        if (best == null) best = sigs.get(0);
        int[] span = paramSpan(best, index);
        return new Hint(best, span == null ? -1 : span[0], span == null ? -1 : span[1], sigs.size() - 1);
    }

    /** Where parameter 'index' is inside "type name(a, b = x, c)" (null if there is no such parameter). */
    static int[] paramSpan(String sig, int index) {
        int open = -1;
        char close = ')';
        for (int i = 0; i < sig.length(); i++) {
            char c = sig.charAt(i);
            if (c == '(' || c == '{') { open = i; close = c == '(' ? ')' : '}'; break; }
        }
        if (open < 0) return null;
        int depth = 0, start = open + 1, k = 0;
        for (int i = open + 1; i < sig.length(); i++) {
            char c = sig.charAt(i);
            if (c == '<' || c == '(') depth++;
            else if ((c == '>' || c == ')') && depth > 0) depth--;
            else if ((c == ',' && depth == 0) || (c == close && depth == 0)) {
                if (k == index) {
                    int s = start, e = i;
                    while (s < e && sig.charAt(s) == ' ') s++;
                    if (s >= e) return null;
                    return new int[]{s, e};
                }
                if (c == close) {
                    // "print(...)" takes any number
                    String all = sig.substring(open + 1, i).trim();
                    return all.equals("...") ? new int[]{open + 1, i} : null;
                }
                k++;
                start = i + 1;
            }
        }
        return null;
    }
}
