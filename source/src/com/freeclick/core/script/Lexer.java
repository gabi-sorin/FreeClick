package com.freeclick.core.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Splits script text into tokens (C++-like: //, /* *\/ comments, "strings", 'c', 12, 1.5, #34A853). */
final class Lexer {
    enum K { IDENT, INT, DOUBLE, STRING, COLOR, OP, EOF }

    static final class Tok {
        final K k;
        final String s;
        final int line, col, pos;
        long iv;
        double dv;
        Tok(K k, String s, int line, int col, int pos) { this.k = k; this.s = s; this.line = line; this.col = col; this.pos = pos; }
        boolean is(String op) { return (k == K.OP || k == K.IDENT) && s.equals(op); }
        @Override public String toString() { return k == K.EOF ? "sfârșitul textului" : "„" + s + "”"; }
    }

    private static final String[] OPS = {"<<=", ">>=", "...", "->", "::", "++", "--", "+=", "-=", "*=", "/=", "%=", "==", "!=", "<=", ">=",
            "&&", "||", "<<", ">>", "+", "-", "*", "/", "%", "=", "<", ">", "!", "&", "|", "^", "~", "?", ":", ";", ",", ".",
            "(", ")", "{", "}", "[", "]"};

    /** A #define: object-like ({@code params == null}) or function-like; {@code body == null} = #undef. */
    static final class Def {
        final String name;
        final List<String> params;
        final List<Tok> body;
        final int pos;
        Def(String name, List<String> params, List<Tok> body, int pos) { this.name = name; this.params = params; this.body = body; this.pos = pos; }
    }

    /** The tokens of a script, with #define / #undef applied (as in C++: plain text replacement of names). */
    static List<Tok> lex(String src) {
        List<Def> defs = new ArrayList<Def>();
        List<Tok> raw = lexRaw(src, defs);
        return defs.isEmpty() ? raw : expand(raw, defs);
    }

    /** The #defines of a script, name → replacement (for the editor: colours, hints, marks). Never throws. */
    static Map<String, String> defines(String src) {
        Map<String, String> m = new HashMap<String, String>();
        if (src == null || src.indexOf("#define") < 0) return m;
        try {
            List<Def> defs = new ArrayList<Def>();
            lexRaw(src, defs);
            for (Def d : defs) {
                if (d.body == null) { m.remove(d.name); continue; }
                StringBuilder b = new StringBuilder();
                for (Tok t : d.body) { if (b.length() > 0) b.append(' '); b.append(t.k == K.STRING ? "\"" + t.s + "\"" : t.s); }
                m.put(d.name, d.params == null ? b.toString() : d.name + "(" + join(d.params) + ") → " + b);
            }
        } catch (ScriptError ignored) { }
        return m;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String x : l) { if (b.length() > 0) b.append(", "); b.append(x); }
        return b.toString();
    }

    private static List<Tok> lexRaw(String src, List<Def> defs) {
        List<Tok> out = new ArrayList<Tok>();
        int i = 0, n = src.length(), line = 1, lineStart = 0;
        while (true) {
            // whitespace and comments
            while (i < n) {
                char c = src.charAt(i);
                if (c == '\n') { line++; i++; lineStart = i; continue; }
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') { while (i < n && src.charAt(i) != '\n') i++; continue; }
                if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                    int sl = line, sc = i - lineStart + 1;
                    i += 2;
                    while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                        if (src.charAt(i) == '\n') { line++; lineStart = i + 1; }
                        i++;
                    }
                    if (i >= n) throw new ScriptError("Comentariul /* nu e închis cu */", sl, sc);
                    i += 2;
                    continue;
                }
                break;
            }
            int col = i - lineStart + 1;
            if (i >= n) { out.add(new Tok(K.EOF, "", line, col, i)); return out; }
            char c = src.charAt(i);
            int start = i;
            if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                out.add(new Tok(K.IDENT, src.substring(start, i), line, col, start));
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                boolean dbl = false;
                if (c == '0' && i + 1 < n && (src.charAt(i + 1) == 'x' || src.charAt(i + 1) == 'X')) {
                    i += 2;
                    while (i < n && Character.digit(src.charAt(i), 16) >= 0) i++;
                    Tok t = new Tok(K.INT, src.substring(start, i), line, col, start);
                    try { t.iv = Long.parseLong(src.substring(start + 2, i), 16); }
                    catch (NumberFormatException e) { throw new ScriptError("Număr hex greșit " + t, line, col); }
                    out.add(t);
                    continue;
                }
                while (i < n && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '\'')) i++;
                if (i < n && src.charAt(i) == '.' && !(i + 1 < n && src.charAt(i + 1) == '.')) { dbl = true; i++; while (i < n && Character.isDigit(src.charAt(i))) i++; }
                if (i < n && (src.charAt(i) == 'e' || src.charAt(i) == 'E') && i + 1 < n
                        && (Character.isDigit(src.charAt(i + 1)) || ((src.charAt(i + 1) == '-' || src.charAt(i + 1) == '+') && i + 2 < n && Character.isDigit(src.charAt(i + 2))))) {
                    dbl = true;
                    i += 2;
                    while (i < n && Character.isDigit(src.charAt(i))) i++;
                }
                String num = src.substring(start, i).replace("'", "");
                Tok t = new Tok(dbl ? K.DOUBLE : K.INT, src.substring(start, i), line, col, start);
                try {
                    if (dbl) t.dv = Double.parseDouble(num);
                    else t.iv = Long.parseLong(num);
                } catch (NumberFormatException e) {
                    throw new ScriptError("Număr prea mare sau greșit: " + src.substring(start, i), line, col);
                }
                // 300ms, 5s, 2min: durations written naturally
                if (i < n && Character.isLetter(src.charAt(i))) {
                    int u = i;
                    while (u < n && Character.isLetter(src.charAt(u))) u++;
                    String unit = src.substring(i, u);
                    long mul = unit.equals("ms") ? 1 : unit.equals("s") ? 1000 : unit.equals("min") ? 60000 : unit.equals("h") ? 3600000 : 0;
                    if (mul > 0) {
                        double v = dbl ? t.dv : t.iv;
                        t = new Tok(K.INT, src.substring(start, u), line, col, start);
                        t.iv = Math.round(v * mul);
                        i = u;
                    }
                }
                out.add(t);
                continue;
            }
            if (c == '"' || c == '\'') {
                char q = c;
                i++;
                StringBuilder b = new StringBuilder();
                while (i < n && src.charAt(i) != q) {
                    char ch = src.charAt(i++);
                    if (ch == '\n') throw new ScriptError("Textul dintre ghilimele nu e închis pe aceeași linie", line, col);
                    if (ch == '\\' && i < n) {
                        char e = src.charAt(i++);
                        ch = e == 'n' ? '\n' : e == 't' ? '\t' : e == '0' ? '\0' : e;
                    }
                    b.append(ch);
                }
                if (i >= n) throw new ScriptError("Lipsesc ghilimelele de final", line, col);
                i++;
                Tok t = new Tok(K.STRING, b.toString(), line, col, start);
                out.add(t);
                continue;
            }
            if (c == '#' && directiveAt(src, i)) {
                // #define NAME body / #define NAME(a, b) body / #undef NAME — the rest of the line ("\\" continues it)
                int end = i;
                StringBuilder text = new StringBuilder();
                while (end < n) {
                    char ch = src.charAt(end);
                    if (ch == '\n') {
                        if (text.length() > 0 && text.charAt(text.length() - 1) == '\\') { text.setLength(text.length() - 1); text.append(' '); end++; line++; lineStart = end; continue; }
                        break;
                    }
                    text.append(ch);
                    end++;
                }
                defs.add(directive(text.toString(), line, col, start));
                i = end;
                continue;
            }
            if (c == '#') {
                i++;
                while (i < n && Character.digit(src.charAt(i), 16) >= 0) i++;
                int len = i - start - 1;
                if (len != 6 && len != 3) throw new ScriptError("O culoare se scrie #RRGGBB (de ex. #34A853)", line, col);
                String h = src.substring(start + 1, i);
                if (len == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                Tok t = new Tok(K.COLOR, src.substring(start, i), line, col, start);
                t.iv = Long.parseLong(h, 16);
                out.add(t);
                continue;
            }
            String op = null;
            for (String o : OPS) if (src.startsWith(o, i)) { op = o; break; }
            if (op == null) throw new ScriptError("Caracter neașteptat „" + c + "”", line, col);
            i += op.length();
            out.add(new Tok(K.OP, op, line, col, start));
        }
    }

    private static boolean directiveAt(String src, int i) {
        for (String d : new String[]{"#define", "#undef"}) {
            if (src.startsWith(d, i) && (i + d.length() >= src.length() || Character.isWhitespace(src.charAt(i + d.length())))) {
                for (int k = i - 1; k >= 0 && src.charAt(k) != '\n'; k--) if (!Character.isWhitespace(src.charAt(k))) return false;
                return true;
            }
        }
        return false;
    }

    private static Def directive(String text, int line, int col, int pos) {
        boolean undef = text.startsWith("#undef");
        String rest = text.substring(undef ? 6 : 7);
        int k = 0;
        while (k < rest.length() && Character.isWhitespace(rest.charAt(k))) k++;
        int ns = k;
        while (k < rest.length() && (Character.isLetterOrDigit(rest.charAt(k)) || rest.charAt(k) == '_')) k++;
        String name = rest.substring(ns, k);
        if (name.isEmpty() || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            throw new ScriptError(undef ? "După #undef scrie numele, de ex. #undef p" : "După #define scrie un nume, de ex. #define p point", line, col);
        }
        if (undef) return new Def(name, null, null, pos);
        List<String> params = null;
        if (k < rest.length() && rest.charAt(k) == '(') { // function-like: no space before "("
            int close = rest.indexOf(')', k);
            if (close < 0) throw new ScriptError("#define " + name + "( … lipsește )", line, col);
            params = new ArrayList<String>();
            for (String a : rest.substring(k + 1, close).split(",")) {
                String t = a.trim();
                if (t.isEmpty() && params.isEmpty() && rest.substring(k + 1, close).trim().isEmpty()) break;
                if (t.isEmpty() || !t.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new ScriptError("#define " + name + ": parametrul „" + t + "” nu e un nume", line, col);
                params.add(t);
            }
            k = close + 1;
        }
        List<Tok> body;
        try {
            body = lexRaw(rest.substring(k), new ArrayList<Def>());
        } catch (ScriptError e) {
            throw new ScriptError("#define " + name + ": " + e.getMessage(), line, col);
        }
        body.remove(body.size() - 1); // EOF
        for (Tok t : body) if (t.k == K.IDENT && t.s.equals(name) && params == null && body.size() == 1) {
            throw new ScriptError("#define " + name + " " + name + " nu schimbă nimic", line, col);
        }
        return new Def(name, params, body, pos);
    }

    private static final int MAX_EXPANDED = 200000;

    private static List<Tok> expand(List<Tok> raw, List<Def> defs) {
        Map<String, Def> active = new HashMap<String, Def>();
        List<Tok> out = new ArrayList<Tok>();
        int di = 0;
        for (int i = 0; i < raw.size(); ) {
            Tok t = raw.get(i);
            while (di < defs.size() && defs.get(di).pos < t.pos) {
                Def d = defs.get(di++);
                if (d.body == null) active.remove(d.name); else active.put(d.name, d);
            }
            if (t.k == K.IDENT && active.containsKey(t.s)) {
                i = expandOne(raw, i, active, new HashSet<String>(), null, out);
            } else {
                out.add(t);
                i++;
            }
            if (out.size() > MAX_EXPANDED) throw new ScriptError("#define se extinde la nesfârșit", t.line, t.col);
        }
        return out;
    }

    /** Expands the macro named by in[i] into out; returns the index after what it used. */
    private static int expandOne(List<Tok> in, int i, Map<String, Def> active, Set<String> busy, Tok at, List<Tok> out) {
        Tok t = in.get(i);
        Def d = active.get(t.s);
        Tok where = at != null ? at : t;
        if (d == null || busy.contains(t.s)) { out.add(at == null ? t : moved(t, where)); return i + 1; }
        List<Tok> body;
        int next;
        if (d.params == null) {
            body = d.body;
            next = i + 1;
        } else {
            if (i + 1 >= in.size() || !in.get(i + 1).is("(")) { out.add(at == null ? t : moved(t, where)); return i + 1; }
            List<List<Tok>> args = new ArrayList<List<Tok>>();
            List<Tok> cur = new ArrayList<Tok>();
            int depth = 0, k = i + 2;
            for (; k < in.size(); k++) {
                Tok a = in.get(k);
                if (a.k == K.EOF) throw new ScriptError(t.s + "( … lipsește )", t.line, t.col);
                if (depth == 0 && a.is(")")) break;
                if (depth == 0 && a.is(",")) { args.add(cur); cur = new ArrayList<Tok>(); continue; }
                if (a.is("(") || a.is("[") || a.is("{")) depth++;
                if (a.is(")") || a.is("]") || a.is("}")) depth--;
                cur.add(a);
            }
            if (k >= in.size()) throw new ScriptError(t.s + "( … lipsește )", t.line, t.col);
            if (!cur.isEmpty() || !args.isEmpty()) args.add(cur);
            if (args.size() != d.params.size()) {
                throw new ScriptError(t.s + "(…) primește " + d.params.size() + (d.params.size() == 1 ? " valoare" : " valori")
                        + ", nu " + args.size() + " (vezi #define " + t.s + ")", t.line, t.col);
            }
            body = new ArrayList<Tok>();
            for (Tok b : d.body) {
                int pi = b.k == K.IDENT ? d.params.indexOf(b.s) : -1;
                if (pi >= 0) body.addAll(args.get(pi)); else body.add(b);
            }
            next = k + 1;
        }
        Set<String> inner = new HashSet<String>(busy);
        inner.add(d.name);
        for (int j = 0; j < body.size(); ) {
            Tok b = body.get(j);
            if (b.k == K.IDENT && active.containsKey(b.s) && !inner.contains(b.s)) j = expandOne(body, j, active, inner, where, out);
            else { out.add(moved(b, where)); j++; }
            if (out.size() > MAX_EXPANDED) throw new ScriptError("#define se extinde la nesfârșit", t.line, t.col);
        }
        return next;
    }

    /** A replacement token reports errors at the place where the name was used. */
    private static Tok moved(Tok b, Tok at) {
        Tok c = new Tok(b.k, b.s, at.line, at.col, at.pos);
        c.iv = b.iv;
        c.dv = b.dv;
        return c;
    }
}
