package com.freeclick.core.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autocomplete for the code editor: the word being typed → names that start with it (the script's
 * own variables, functions and structs first, then the app's functions, then keywords and types).
 * After a "." → methods and fields. Inside comments and strings: nothing.
 */
public final class Completions {
    private Completions() {}

    public static final int VAR = 0, FUNC = 1, KEYWORD = 2, TYPE = 3, MEMBER = 4;

    public static final class Item {
        public final String name;
        /** What replaces the typed word ("tap(" for a function). */
        public final String insert;
        public final int kind;

        Item(String name, String insert, int kind) { this.name = name; this.insert = insert; this.kind = kind; }
    }

    public static final class Result {
        /** Where the typed word starts (it is replaced from here to the cursor). */
        public final int start;
        public final List<Item> items;

        Result(int start, List<Item> items) { this.start = start; this.items = items; }
    }

    private static final List<String> FIELDS = Arrays.asList("x", "y", "x1", "y1", "x2", "y2", "r", "g", "b", "first", "second",
            "tip", "durata", "intarziere", "repetari", "activ", "nota");
    private static final Set<String> CALL_KEYWORDS = new java.util.HashSet<String>(Arrays.asList(
            "if", "while", "for", "switch", "cand", "la_fiecare", "la_ora", "daca", "cat_timp"));

    private static final Pattern DECL = Pattern.compile(
            "\\b(?:const\\s+)?(?:int|long|double|float|bool|string|color|point|rect|auto|Pas|vector\\s*<[^;{}()]*>|pair\\s*<[^;{}()]*>|[A-Z][A-Za-z0-9_]*)\\s*&?\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern STRUCT_FIELDS = Pattern.compile("\\bstruct\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\{([^{}]*)\\}");
    private static final Pattern FIELD = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=[^;]*)?;");

    public static Result at(String src, int cursor, int max) {
        if (src == null || cursor <= 0 || cursor > src.length()) return null;
        String clean = Signatures.stripCommentsAndStrings(src);
        if (clean.charAt(cursor - 1) == ' ' && src.charAt(cursor - 1) != ' ') return null; // inside a comment / string
        int start = cursor;
        while (start > 0 && (Character.isLetterOrDigit(src.charAt(start - 1)) || src.charAt(start - 1) == '_')) start--;
        boolean afterDot = start == cursor && src.charAt(cursor - 1) == '.' && cursor >= 2
                && (Character.isLetterOrDigit(src.charAt(cursor - 2)) || src.charAt(cursor - 2) == '_' || src.charAt(cursor - 2) == ')'
                || src.charAt(cursor - 2) == ']');
        if (start == cursor && !afterDot) return null;
        if (start < cursor && Character.isDigit(src.charAt(start))) return null;
        for (int i = start; i < cursor; i++) if (clean.charAt(i) != src.charAt(i)) return null; // blanked: comment / string
        if (start > 0 && src.charAt(start - 1) == '#') return null; // #RRGGBB
        String prefix = src.substring(start, cursor);
        boolean member = start > 0 && src.charAt(start - 1) == '.';

        // what the rest of the text names (the word being typed itself does not count)
        String others = clean.substring(0, start) + clean.substring(cursor);
        Set<String> seen = new LinkedHashSet<String>();
        List<Item> out = new ArrayList<Item>();
        if (member) {
            Set<String> names = new LinkedHashSet<String>();
            Matcher sf = STRUCT_FIELDS.matcher(others);
            while (sf.find()) {
                Matcher f = FIELD.matcher(sf.group(1));
                while (f.find()) names.add(f.group(1));
            }
            names.addAll(FIELDS);
            for (String n : names) offer(out, seen, prefix, n, n, MEMBER, max);
            List<String> methods = new ArrayList<String>(Signatures.methodNames());
            java.util.Collections.sort(methods);
            for (String n : methods) offer(out, seen, prefix, n, n + "(", MEMBER, max);
            return out.isEmpty() ? null : new Result(start, out);
        }
        // the script's own names
        Set<String> funcs = Signatures.userFunctions(others).keySet();
        for (String n : funcs) offer(out, seen, prefix, n, n + (Character.isUpperCase(n.charAt(0)) ? "{" : "("), FUNC, max);
        Matcher d = DECL.matcher(others);
        while (d.find()) {
            String n = d.group(1);
            if (!funcs.contains(n) && !Parser.KEYWORDS.contains(n)) offer(out, seen, prefix, n, n, VAR, max);
        }
        // the app's functions, then keywords and types
        for (String n : Builtins.allNames()) offer(out, seen, prefix, n, n + "(", FUNC, max);
        for (String n : ScriptRunner.KEYWORDS) offer(out, seen, prefix, n, n + (CALL_KEYWORDS.contains(n) ? " (" : " "), KEYWORD, max);
        for (String n : ScriptRunner.TYPES) offer(out, seen, prefix, n, n + " ", TYPE, max);
        for (String n : new String[]{"pasi", "DURATA_CLICK", "DURATA_SWIPE", "INTERVAL_REPETARE"}) offer(out, seen, prefix, n, n, VAR, max);
        return out.isEmpty() ? null : new Result(start, out);
    }

    private static void offer(List<Item> out, Set<String> seen, String prefix, String name, String insert, int kind, int max) {
        if (out.size() >= max || name.length() <= prefix.length() || !seen.add(name)) return;
        if (!name.startsWith(prefix) && !name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) return;
        out.add(new Item(name, insert, kind));
    }
}
