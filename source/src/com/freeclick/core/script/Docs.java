package com.freeclick.core.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 📖 The documentation (2.3), in the style of Godot's class reference: a page for every function and
 * every part of the app, with links between them ([[name]]), the ways to call it and an example.
 * Written in docs/reference.md (generated into {@link DocsText}); the tests check that every function
 * has a page, every link leads somewhere and every example is a script that compiles.
 */
public final class Docs {
    private Docs() {}

    public static final class Entry {
        public final String id, category, summary, body, example;
        public final List<String> see;

        Entry(String id, String category, String summary, String body, String example, List<String> see) {
            this.id = id; this.category = category; this.summary = summary; this.body = body; this.example = example; this.see = see;
        }

        /** The ways to call it, e.g. "void tap(point p, int durata = DURATA_CLICK, …)". Empty for topics. */
        public List<String> signatures() { return Signatures.of(id); }

        /** Other names of the same function ("atinge", "click" for tap). */
        public List<String> aliases() { return Docs.aliases(id); }
    }

    private static List<Entry> entries;
    private static Map<String, Entry> byKey;

    public static synchronized List<Entry> all() {
        if (entries == null) load();
        return entries;
    }

    public static List<String> categories() {
        List<String> out = new ArrayList<String>();
        for (Entry e : all()) if (!out.contains(e.category)) out.add(e.category);
        return out;
    }

    public static List<Entry> inCategory(String c) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : all()) if (e.category.equals(c)) out.add(e);
        return out;
    }

    /** The page of a name: a page title, any name of a function, a keyword, a method ("size", "push_back"). */
    public static Entry find(String name) {
        if (name == null) return null;
        all();
        String k = name.trim();
        if (k.startsWith("#")) k = "#define";
        Entry e = byKey.get(k.toLowerCase(Locale.ROOT));
        if (e != null) return e;
        for (String a : aliasesOf(k)) {
            e = byKey.get(a.toLowerCase(Locale.ROOT));
            if (e != null) return e;
        }
        String topic = KEYWORDS.get(k);
        return topic == null ? null : byKey.get(topic.toLowerCase(Locale.ROOT));
    }

    /** Names first (starting with, then containing), then pages that mention the words. */
    public static List<Entry> search(String q) {
        List<Entry> out = new ArrayList<Entry>();
        String s = q == null ? "" : fold(q.trim());
        if (s.isEmpty()) return new ArrayList<Entry>(all());
        for (int pass = 0; pass < 3; pass++) {
            for (Entry e : all()) {
                if (out.contains(e)) continue;
                String id = fold(e.id);
                boolean hit;
                if (pass == 0) hit = id.startsWith(s) || anyStarts(e.aliases(), s);
                else if (pass == 1) hit = id.contains(s) || anyContains(e.aliases(), s);
                else hit = fold(e.summary + " " + e.body + " " + e.example).contains(s);
                if (hit) out.add(e);
            }
        }
        return out;
    }

    private static boolean anyStarts(List<String> l, String s) { for (String a : l) if (fold(a).startsWith(s)) return true; return false; }
    private static boolean anyContains(List<String> l, String s) { for (String a : l) if (fold(a).contains(s)) return true; return false; }

    /** Lower case without Romanian diacritics, so "asteapta" finds "așteaptă". */
    static String fold(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            switch (c) {
                case 'ă': case 'â': b.append('a'); break;
                case 'î': b.append('i'); break;
                case 'ș': case 'ş': b.append('s'); break;
                case 'ț': case 'ţ': b.append('t'); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    /** The [[links]] in a text, in order. */
    public static List<String> links(String text) {
        List<String> out = new ArrayList<String>();
        int i = 0;
        while ((i = text.indexOf("[[", i)) >= 0) {
            int j = text.indexOf("]]", i + 2);
            if (j < 0) break;
            out.add(text.substring(i + 2, j));
            i = j + 2;
        }
        return out;
    }

    // ------------------------------------------------------------------ names
    /** Words that are not functions but have a page. */
    private static final Map<String, String> KEYWORDS = new HashMap<String, String>();
    static {
        String[][] k = {
                {"if", "if"}, {"else", "if"}, {"switch", "switch"}, {"case", "switch"}, {"default", "switch"},
                {"while", "while"}, {"for", "while"}, {"do", "while"}, {"break", "while"}, {"continue", "while"},
                {"return", "funcții"}, {"void", "funcții"}, {"struct", "struct"}, {"vector", "vector"}, {"pair", "vector"},
                {"make_pair", "vector"}, {"first", "vector"}, {"second", "vector"}, {"push_back", "vector"}, {"pop_back", "vector"},
                {"back", "back"}, {"front", "vector"}, {"erase", "vector"}, {"insert", "vector"}, {"index_of", "vector"},
                {"clear", "vector"}, {"size", "Text (string)"}, {"substr", "Text (string)"}, {"find", "Text (string)"},
                {"upper", "Text (string)"}, {"lower", "Text (string)"}, {"replace", "Text (string)"}, {"trim", "Text (string)"},
                {"empty", "Text (string)"}, {"string", "Text (string)"}, {"int", "Variabile și tipuri"}, {"double", "Variabile și tipuri"},
                {"bool", "Variabile și tipuri"}, {"auto", "Variabile și tipuri"}, {"const", "Variabile și tipuri"},
                {"long", "Variabile și tipuri"}, {"float", "Variabile și tipuri"}, {"true", "Variabile și tipuri"}, {"false", "Variabile și tipuri"},
                {"point", "point"}, {"rect", "rect"}, {"color", "color"}, {"x", "point"}, {"y", "point"}, {"r", "color"}, {"g", "color"},
                {"b", "color"}, {"hex", "hex"}, {"cand", "Evenimente"}, {"when", "Evenimente"}, {"la_fiecare", "Evenimente"},
                {"every", "Evenimente"}, {"la_ora", "Evenimente"}, {"at", "Evenimente"}, {"pastreaza", "pastreaza"},
                {"define", "#define"}, {"undef", "#define"}, {"DURATA_CLICK", "Setările clickului"}, {"DURATA_SWIPE", "Setările clickului"},
                {"INTERVAL_REPETARE", "Setările clickului"}, {"pasi", "pasi"}, {"Pas", "pasi"}, {"cout", "print"}, {"endl", "print"},
                {"sqrt", "Matematică"}, {"pow", "Matematică"}, {"round", "Matematică"}, {"floor", "Matematică"}, {"ceil", "Matematică"},
                {"abs", "Matematică"}, {"min", "Matematică"}, {"max", "Matematică"}, {"rotunjit", "Matematică"},
                {"to_double", "to_int"}, {"stod", "to_int"}, {"toDouble", "to_int"}, {"text", "to_string"}, {"toString", "to_string"},
                {"reverse", "sort"}, {"swap", "sort"}, {"contine", "contine"}, {"println", "print"}, {"afiseaza", "print"},
                {"turbo", "Turbo"}, {"afk", "Mod AFK"},
        };
        for (String[] e : k) KEYWORDS.put(e[0], e[1]);
    }

    /** Every name of the function called {@code name} (itself included), from the language's own table. */
    private static List<String> aliasesOf(String name) {
        List<String> out = new ArrayList<String>();
        List<Builtins.Sig> sigs = Builtins.GLOBAL.get(name);
        if (sigs == null) return out;
        java.util.Set<Integer> ids = new java.util.HashSet<Integer>();
        for (Builtins.Sig s : sigs) ids.add(s.id);
        for (Map.Entry<String, List<Builtins.Sig>> e : new java.util.TreeMap<String, List<Builtins.Sig>>(Builtins.GLOBAL).entrySet()) {
            for (Builtins.Sig s : e.getValue()) if (ids.contains(s.id) && !out.contains(e.getKey())) out.add(e.getKey());
        }
        return out;
    }

    static List<String> aliases(String id) {
        List<String> l = aliasesOf(id);
        l.remove(id);
        // "pipeta" is two things (pixel with coordinates, your touch without): its page names both
        return l;
    }

    // ------------------------------------------------------------------ reading docs/reference.md
    private static void load() {
        List<Entry> list = new ArrayList<Entry>();
        Map<String, Entry> keys = new LinkedHashMap<String, Entry>();
        String category = "";
        String id = null;
        StringBuilder body = new StringBuilder(), example = new StringBuilder();
        String see = "";
        boolean inCode = false;
        for (String line : (DocsText.text() + "\n# end\n").split("\n", -1)) {
            if (inCode) {
                if (line.startsWith("```")) { inCode = false; continue; }
                example.append(line).append('\n');
                continue;
            }
            if (line.startsWith("```")) { inCode = true; continue; }
            if (line.startsWith("# ") || line.startsWith("## ")) {
                if (id != null) {
                    Entry e = entry(id, category, body.toString().trim(), example.toString().trim(), see);
                    list.add(e);
                    keys.put(id.toLowerCase(Locale.ROOT), e);
                }
                id = null;
                body.setLength(0);
                example.setLength(0);
                see = "";
                if (line.startsWith("## ")) id = line.substring(3).trim();
                else category = line.substring(2).trim();
                continue;
            }
            if (line.startsWith("Vezi:")) { see = line.substring(5).trim(); continue; }
            body.append(line).append('\n');
        }
        entries = list;
        byKey = keys;
    }

    private static Entry entry(String id, String category, String body, String example, String see) {
        int dot = body.indexOf('\n');
        String first = dot < 0 ? body : body.substring(0, dot);
        List<String> links = links(see);
        return new Entry(id, category, first, body, example, links);
    }
}
