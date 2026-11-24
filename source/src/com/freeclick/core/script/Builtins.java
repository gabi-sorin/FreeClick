package com.freeclick.core.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.freeclick.core.script.Ast.Type;

/** The functions a script can call: names (English and Romanian), parameter types, return type, id. */
final class Builtins {
    private Builtins() {}

    static final class Sig {
        final int id; final Type ret; final Type[] params; final int optional; final String name;
        Sig(int id, String name, Type ret, int optional, Type... params) { this.id = id; this.name = name; this.ret = ret; this.params = params; this.optional = optional; }
        int min() { return params.length - optional; }
    }

    // ---- ids (the interpreter switches on these)
    static final int TAP = 1, TAP_XY = 2, LONG = 3, LONG_XY = 4, DOUBLE_TAP = 5, DOUBLE_TAP_XY = 6, SWIPE = 7, SWIPE_XY = 8, DRAG = 9,
            WAIT = 10, WAIT_RANDOM = 11, BACK = 12, HOME = 13, RECENTS = 14, NOTIFICATIONS = 15, OPEN_APP = 16, STOP = 17,
            TOAST = 18, VIBRATE = 19, PIXEL = 20, PIXEL_XY = 21, NEAR = 22, READ = 23, READ_TEXT = 24, VISIBLE = 25, FIND = 26,
            FIND_ALL = 27, WAIT_IMAGE = 28, WAIT_COLOR = 29, FOUND = 30, SCREEN_W = 31, SCREEN_H = 32, SCREEN = 33, MILLIS = 34,
            CLOCK = 35, RANDOM = 36, SQRT = 37, POW = 38, ROUND = 39, FLOOR = 40, CEIL = 41, TO_INT = 42, TO_DOUBLE = 43,
            DIST = 44, CENTER = 45, CONTAINS_PT = 46, HEX = 47, RUN_STEPS = 48, RUN_STEP = 49, STEPS_OF = 50, RUN_MACRO = 51,
            STEP_TAP = 52, STEP_SWIPE = 53, STEP_WAIT = 54, STEP_LONG = 55, HOUR = 56, MINUTE = 57, WEEKDAY = 58,
            WAIT_STILL = 59, WAIT_CHANGE = 60, PATTERN = 61, TAP_ZONE = 62, SAVE_SHOT = 63, COPY = 64, OPEN_URL = 65,
            ZOOM_IN = 66, ZOOM_OUT = 67,
            // 2.3: your fingers
            TOUCH_WAIT = 68, GESTURE_WAIT = 69, SWIPE_WAIT = 70, DOUBLE_WAIT = 71, LONG_WAIT = 72, FINGERS_WAIT = 73,
            NO_FINGERS_WAIT = 74, FINGERS = 75, TOUCHING = 76, FINGER_POS = 77, LAST_TOUCH = 78, NEW_TAPS = 79,
            ZONE_TOUCH_WAIT = 80, PICK_ZONE = 81, PICK_COLOR = 82, LISTEN = 83, FORWARD = 84, GEST_START = 85,
            GEST_END = 86, GEST_FINGERS = 87, GEST_MS = 88, TAP_MULTI = 89,
            // 2.4
            PERCENT_COLOR = 90, FIND_COLOR = 91, RECORD = 92, HUD = 93, HUD_HIDE = 94, BATTERY = 95, CHARGING = 96,
            ORIENTATION = 97, NOTIFY = 98,
            // 2.5
            TAPS_WAIT = 400, NEW_GESTURES = 401, GEST_SCALE = 402, GEST_ROT = 403, BUTTON = 404, PRESSED = 405,
            WAIT_BUTTON = 406, REMOVE_BUTTON = 407, REMOVE_BUTTONS = 408, LISTEN_ZONE = 409, LISTEN_ALL = 410,
            WAIT_ANY = 411, ASK = 412, ASK_NUMBER = 413, CHOOSE = 414, FILE_WRITE = 415, FILE_APPEND = 416, FILE_READ = 417,
            FILE_EXISTS = 418, SWIPE_CURVE = 419,
            // generic (typed by the checker)
            PRINT = 100, TO_STRING = 101, ABS = 102, MIN = 103, MAX = 104, MAKE_PAIR = 105, SORT = 106, REVERSE = 107, SWAP = 108,
            // casts and constructors
            CAST = 200, CTOR = 201,
            // methods
            S_SIZE = 300, S_SUBSTR = 301, S_FIND = 302, S_CONTAINS = 303, S_EMPTY = 304, S_STARTS = 305, S_ENDS = 306,
            S_UPPER = 307, S_LOWER = 308, S_REPLACE = 309, S_TRIM = 310,
            V_PUSH = 320, V_POP = 321, V_SIZE = 322, V_EMPTY = 323, V_CLEAR = 324, V_BACK = 325, V_FRONT = 326, V_ERASE = 327,
            V_INSERT = 328, V_CONTAINS = 329, V_INDEX_OF = 330,
            C_HEX = 340;

    static final Map<String, List<Sig>> GLOBAL = new HashMap<String, List<Sig>>();

    private static void def(int id, String names, Type ret, int optional, Type... params) {
        for (String n : names.split("\\|")) {
            List<Sig> l = GLOBAL.get(n);
            if (l == null) { l = new ArrayList<Sig>(); GLOBAL.put(n, l); }
            l.add(new Sig(id, n, ret, optional, params));
        }
    }

    static final Type I = Type.INT, D = Type.DOUBLE, B = Type.BOOL, S = Type.STRING, C = Type.COLOR, P = Type.POINT, R = Type.RECT,
            V = Type.VOID, PAS = Type.PAS, STEPS = Type.vector(Type.PAS);

    static {
        // touches: optional press / move duration, then the pause after (defaults: DURATA_CLICK, DURATA_SWIPE,
        // INTERVAL_REPETARE when the script declares them at the top, like the clicker's settings)
        def(TAP, "tap|atinge|click", V, 2, P, I, I);
        def(TAP_XY, "tap|atinge|click", V, 2, I, I, I, I);
        def(LONG, "apasaLung|longPress|apasa_lung", V, 2, P, I, I);
        def(LONG_XY, "apasaLung|longPress|apasa_lung", V, 2, I, I, I, I);
        def(DOUBLE_TAP, "dubluTap|doubleTap|dublu_tap", V, 1, P, I);
        def(DOUBLE_TAP_XY, "dubluTap|doubleTap|dublu_tap", V, 1, I, I, I);
        def(SWIPE, "swipe|gliseaza", V, 2, P, P, I, I);
        def(SWIPE_XY, "swipe|gliseaza", V, 2, I, I, I, I, I, I);
        def(DRAG, "trage|drag", V, 2, P, P, I, I);
        def(WAIT, "wait|pauza|sleep|delay|asteapta", V, 0, I);
        def(WAIT_RANDOM, "waitRandom|pauzaAleatoare|pauza_aleatoare", V, 0, I, I);
        def(BACK, "back|inapoi", V, 0);
        def(HOME, "home|acasa", V, 0);
        def(RECENTS, "recente|recents", V, 0);
        def(NOTIFICATIONS, "notificari|notifications", V, 0);
        def(OPEN_APP, "deschide|openApp|open_app", B, 0, S);
        def(STOP, "stop|opreste", V, 0);
        def(TOAST, "toast|mesaj", V, 0, S);
        def(VIBRATE, "vibreaza|vibrate", V, 1, I);
        def(PIXEL, "pixel|culoare|pipeta", C, 0, P);
        def(PIXEL_XY, "pixel|culoare|pipeta", C, 0, I, I);
        def(NEAR, "aproape|near", B, 1, C, C, I);
        def(READ, "citeste|read|citesteNumar", D, 0, R);
        def(READ_TEXT, "citesteText|readText|citeste_text", S, 0, R);
        def(VISIBLE, "apare|visible|vizibil", B, 0, S);
        def(FIND, "gaseste|find", P, 0, S);
        def(FIND_ALL, "gasesteToate|findAll|gaseste_toate", Type.vector(P), 0, S);
        def(WAIT_IMAGE, "asteaptaImagine|waitImage|asteapta_imagine", B, 0, S, I);
        def(WAIT_COLOR, "asteaptaCuloare|waitColor|asteapta_culoare", B, 1, P, C, I, I);
        def(FOUND, "exista|found|valid", B, 0, P);
        def(SCREEN_W, "latime|screenW|screenWidth", I, 0);
        def(SCREEN_H, "inaltime|screenH|screenHeight", I, 0);
        def(SCREEN, "ecran|screen", R, 0);
        def(MILLIS, "timp|millis", I, 0);
        def(CLOCK, "ora|clock", S, 0);
        def(HOUR, "oraNum|hour", I, 0);
        def(MINUTE, "minut|minute", I, 0);
        def(WEEKDAY, "ziua|weekday", I, 0);
        def(RANDOM, "random|aleator|rand", I, 0, I, I);
        def(SQRT, "sqrt", D, 0, D);
        def(POW, "pow", D, 0, D, D);
        def(ROUND, "round|rotunjit", D, 0, D);
        def(FLOOR, "floor", D, 0, D);
        def(CEIL, "ceil", D, 0, D);
        def(TO_INT, "to_int|stoi|toInt|numar", I, 0, S);
        def(TO_DOUBLE, "to_double|stod|toDouble", D, 0, S);
        def(DIST, "dist|distanta", D, 0, P, P);
        def(CENTER, "centru|center", P, 0, R);
        def(CONTAINS_PT, "contine|contains", B, 0, R, P);
        def(HEX, "hex", S, 0, C);
        def(RUN_STEPS, "ruleaza|run|reda|replay", V, 1, STEPS, I);
        def(RUN_STEP, "ruleaza|run|reda|replay", V, 1, PAS, I);
        def(STEPS_OF, "pasiDin|stepsOf|pasi_din", STEPS, 0, S);
        def(RUN_MACRO, "ruleazaMacro|runMacro|ruleaza_macro", V, 1, S, I);
        def(STEP_TAP, "pasTap|stepTap", PAS, 0, P);
        def(STEP_SWIPE, "pasSwipe|stepSwipe", PAS, 1, P, P, I);
        def(STEP_WAIT, "pasPauza|stepWait", PAS, 0, I);
        def(STEP_LONG, "pasApasaLung|stepLongPress", PAS, 1, P, I);
        // 2.1: waiting for the screen, several pixels, a random spot, gallery, clipboard, links, two fingers
        def(WAIT_STILL, "asteaptaLiniste|waitStill|asteapta_liniste", B, 3, R, I, I);
        def(WAIT_CHANGE, "asteaptaSchimbare|waitChange|asteapta_schimbare", B, 1, R, I);
        def(PATTERN, "model|pattern|modelPixeli", B, 1, Type.vector(P), Type.vector(C), I);
        def(TAP_ZONE, "tapInZona|tapAleator|tapInZone|atingeInZona", V, 2, R, I, I);
        def(SAVE_SHOT, "salveazaCaptura|saveScreenshot|salveaza_captura", B, 1, S);
        def(COPY, "copiaza|copy|clipboard", V, 0, S);
        def(OPEN_URL, "deschideLink|openUrl|deschide_link", B, 0, S);
        def(ZOOM_IN, "zoomIn|mareste", V, 2, P, I, I);
        def(ZOOM_OUT, "zoomOut|micsoreaza", V, 2, P, I, I);
        // 2.3: what YOUR fingers do (FreeClick listens; the game gets the touch too)
        def(TOUCH_WAIT, "asteaptaAtingere|waitForTouch|wait_for_touch|asteapta_atingere", P, 1, I);
        def(GESTURE_WAIT, "asteaptaGest|waitGesture|wait_gesture|asteapta_gest", S, 1, I);
        def(SWIPE_WAIT, "asteaptaSwipe|waitSwipe|wait_swipe|asteapta_swipe", S, 1, I);
        def(DOUBLE_WAIT, "asteaptaDubluTap|waitDoubleTap|wait_double_tap", P, 1, I);
        def(LONG_WAIT, "asteaptaApasareLunga|waitLongPress|wait_long_press", P, 1, I);
        def(FINGERS_WAIT, "asteaptaDegete|waitFingers|wait_fingers|asteapta_degete", B, 1, I, I);
        def(NO_FINGERS_WAIT, "asteaptaFaraDegete|asteaptaRidicare|waitNoFingers|wait_release", B, 1, I);
        def(FINGERS, "degete|fingers", I, 0);
        def(TOUCHING, "atins|touching|ecranAtins", B, 0);
        def(FINGER_POS, "pozitieDeget|fingerPos|finger", P, 1, I);
        def(LAST_TOUCH, "ultimaAtingere|lastTouch|last_touch", P, 0);
        def(NEW_TAPS, "atingeriNoi|newTaps|new_taps", I, 0);
        def(ZONE_TOUCH_WAIT, "asteaptaAtingereInZona|waitTouchIn|wait_touch_in", B, 1, R, I);
        def(PICK_ZONE, "alegeZona|pickZone|pick_zone", R, 1, I);
        def(PICK_COLOR, "pipetaAtingere|get_touch_pipeta|getTouchPipeta|pickColorByTouch|pick_touch_color", C, 1, I);
        def(LISTEN, "asculta|listen", V, 0, B);
        def(FORWARD, "treciMaiDeparte|passThrough|forwardTouches", V, 0, B);
        def(GEST_START, "inceputGest|gestureStart", P, 0);
        def(GEST_END, "sfarsitGest|gestureEnd", P, 0);
        def(GEST_FINGERS, "degeteGest|gestureFingers", I, 0);
        def(GEST_MS, "durataGest|gestureMs", I, 0);
        def(TAP_MULTI, "atingeDeodata|tapAll|tap_all|multiTap|atinge_deodata", V, 2, Type.vector(P), I, I);
        def(PERCENT_COLOR, "procentCuloare|colorPercent|percentColor|procent_culoare", D, 1, R, C, I);
        def(FIND_COLOR, "gasesteCuloare|findColor|find_color|gaseste_culoare", P, 1, R, C, I);
        def(RECORD, "inregistreaza|record|inregistrare", STEPS, 2, I, I);
        def(HUD, "afiseazaPeEcran|showText|show_text|afiseaza_pe_ecran", V, 1, S, P);
        def(HUD_HIDE, "ascundeTextul|hideText|hide_text", V, 0);
        def(BATTERY, "baterie|battery", I, 0);
        def(CHARGING, "seIncarca|charging|isCharging", B, 0);
        def(ORIENTATION, "orientare|orientation", S, 0);
        def(NOTIFY, "notifica|notify", V, 1, S, S);
        def(TAPS_WAIT, "asteaptaTapuri|waitTaps|wait_taps", P, 1, I, I);
        def(NEW_GESTURES, "gesturiNoi|newGestures|new_gestures", I, 0);
        def(GEST_SCALE, "factorGest|gestureScale", D, 0);
        def(GEST_ROT, "rotireGest|gestureRotation", D, 0);
        def(BUTTON, "buton|button|adaugaButon", I, 0, S, R);
        def(PRESSED, "apasat|pressed|butonApasat", B, 0, I);
        def(WAIT_BUTTON, "asteaptaButon|waitButton|wait_button", I, 1, I);
        def(REMOVE_BUTTON, "stergeButon|removeButton", V, 0, I);
        def(REMOVE_BUTTONS, "stergeButoanele|removeButtons", V, 0);
        def(LISTEN_ZONE, "ascultaInZona|listenIn|listen_in", V, 0, R);
        def(LISTEN_ALL, "ascultaPeTotEcranul|listenEverywhere", V, 0);
        def(WAIT_ANY, "asteaptaOricare|waitAny|wait_any", I, 1, Type.vector(S), I);
        def(ASK, "intreaba|ask", S, 1, S, S);
        def(ASK_NUMBER, "intreabaNumar|askNumber|ask_number", I, 1, S, I);
        def(CHOOSE, "alege|choose", I, 0, S, Type.vector(S));
        def(FILE_WRITE, "scrieFisier|writeFile|write_file", V, 0, S, S);
        def(FILE_APPEND, "adaugaInFisier|appendFile|append_file", V, 0, S, S);
        def(FILE_READ, "citesteFisier|readFile|read_file", S, 0, S);
        def(FILE_EXISTS, "existaFisier|fileExists|file_exists", B, 0, S);
        def(SWIPE_CURVE, "swipeCurb|curvedSwipe|swipe_curb", V, 3, P, P, I, D, I);
    }

    /** Functions whose types depend on their arguments (handled by the checker). */
    static final Map<String, Integer> GENERIC = new HashMap<String, Integer>();
    static {
        for (String n : new String[]{"print", "afiseaza", "println"}) GENERIC.put(n, PRINT);
        GENERIC.put("to_string", TO_STRING); GENERIC.put("text", TO_STRING); GENERIC.put("toString", TO_STRING);
        GENERIC.put("abs", ABS); GENERIC.put("min", MIN); GENERIC.put("max", MAX);
        GENERIC.put("make_pair", MAKE_PAIR); GENERIC.put("sort", SORT); GENERIC.put("reverse", REVERSE); GENERIC.put("swap", SWAP);
    }

    /** Every name a script can call (for "did you mean" and the editor's colouring). */
    static java.util.Set<String> allNames() {
        java.util.Set<String> s = new java.util.TreeSet<String>(GLOBAL.keySet());
        s.addAll(GENERIC.keySet());
        return s;
    }
}
