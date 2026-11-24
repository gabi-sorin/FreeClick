package com.freeclick.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small expressions for variables (1.8): numbers, "text", #RRGGBB colours, variables,
 * + - * / %, comparisons (== != < <= > >=, a single = means ==), && || !, parentheses and a few
 * functions (random, min, max, abs, round, floor, ceil, sqrt, len, num, text, contains, near).
 *
 * Values are Double or String. A variable that was never set is 0, so "n + 1" works as a counter
 * from the first turn. Errors while running (division by 0...) give 0 and a warning, never a crash;
 * errors in the text itself are reported with their position when the step is saved.
 */
public final class Expr {

    /** A mistake in the expression text (message in Romanian, pos = 0-based character index). */
    public static final class ExprError extends IllegalArgumentException {
        public final int pos;
        ExprError(String msg, int pos) { super(msg + " (poziția " + (pos + 1) + ")"); this.pos = pos; }
    }

    /** Where variables come from. */
    public interface Env {
        /** The value of a variable (lower-case name), or null if it was never set. */
        Object get(String name);
        /** A random double in [0, 1). */
        double random();
        /** Something odd happened while evaluating (division by 0, ...). */
        void warn(String message);
    }

    /** A parsed expression; evaluate as often as needed. */
    public abstract static class Node {
        public abstract Object eval(Env env);
    }

    private Expr() {}

    public static Object eval(String text, Env env) { return parse(text).eval(env); }

    public static Node parse(String text) {
        if (text == null || text.trim().length() == 0) throw new ExprError("Expresia e goală", 0);
        Parser p = new Parser(text);
        Node n = p.or();
        p.skip();
        if (p.i < p.s.length()) throw new ExprError("Nu înțeleg „" + p.s.charAt(p.i) + "”", p.i);
        return n;
    }

    /** Null if the text is a valid expression, otherwise the error message. */
    public static String check(String text) {
        try { parse(text); return null; } catch (ExprError e) { return e.getMessage(); }
    }

    // ------------------------------------------------------------------ values
    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Double) { double d = (Double) v; return d != 0 && !Double.isNaN(d); }
        String s = v.toString();
        return s.length() > 0 && !s.equals("0");
    }

    /** Numeric value: numbers as they are, text through Numbers.parse ("1,5k" = 1500), otherwise 0. */
    public static double num(Object v) {
        if (v == null) return 0;
        if (v instanceof Double) return (Double) v;
        Double d = Numbers.parse(v.toString());
        return d == null ? 0 : d;
    }

    /** How a value is shown: 12, 12.5, 0.3333 (at most 4 decimals), text as is. */
    public static String format(Object v) {
        if (v == null) return "";
        if (!(v instanceof Double)) return v.toString();
        double d = (Double) v;
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        String s = String.format(Locale.US, "%.4f", d);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Names of variables: a letter first, then letters, digits or _, at most 32 characters. */
    public static boolean isValidName(String n) {
        if (n == null || n.length() == 0 || n.length() > 32) return false;
        if (!isNameStart(n.charAt(0))) return false;
        for (int i = 1; i < n.length(); i++) if (!isNamePart(n.charAt(i))) return false;
        return !isFunction(n.toLowerCase(Locale.ROOT));
    }

    static boolean isNameStart(char c) { return Character.isLetter(c) || c == '_'; }
    static boolean isNamePart(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    static final String[] FUNCTIONS = {"random", "aleator", "min", "max", "abs", "round", "rotunjit",
            "floor", "ceil", "sqrt", "len", "lungime", "num", "numar", "text", "contains", "contine", "near", "aproape"};

    static boolean isFunction(String lower) {
        for (String f : FUNCTIONS) if (f.equals(lower)) return true;
        return false;
    }

    // ------------------------------------------------------------------ templates
    /**
     * Text with {expressions}: "Aur: {aur}, dublu {aur * 2}". "{{" and "}}" are literal braces.
     * A broken {part} is kept as written and reported once through env.warn.
     */
    public static String expand(String template, Env env) {
        if (template == null) return "";
        StringBuilder out = new StringBuilder();
        int i = 0, n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < n && template.charAt(i + 1) == '{') { out.append('{'); i += 2; continue; }
            if (c == '}' && i + 1 < n && template.charAt(i + 1) == '}') { out.append('}'); i += 2; continue; }
            if (c != '{') { out.append(c); i++; continue; }
            int end = closing(template, i);
            if (end < 0) { out.append(template.substring(i)); break; }
            String inner = template.substring(i + 1, end);
            try {
                out.append(format(parse(inner).eval(env)));
            } catch (ExprError e) {
                env.warn("{" + inner + "}: " + e.getMessage());
                out.append(template, i, end + 1);
            }
            i = end + 1;
        }
        return out.toString();
    }

    /** Null if every {part} of the template is a valid expression, otherwise the first error. */
    public static String checkTemplate(String template) {
        if (template == null) return null;
        int i = 0, n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if ((c == '{' || c == '}') && i + 1 < n && template.charAt(i + 1) == c) { i += 2; continue; }
            if (c != '{') { i++; continue; }
            int end = closing(template, i);
            if (end < 0) return "Lipsește „}” după „{” (poziția " + (i + 1) + ")";
            String err = check(template.substring(i + 1, end));
            if (err != null) return "{" + template.substring(i + 1, end) + "}: " + err;
            i = end + 1;
        }
        return null;
    }

    /** Index of the '}' closing the '{' at start, skipping braces inside "strings". */
    private static int closing(String s, int start) {
        boolean str = false;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (str) { if (c == '\\') i++; else if (c == '"') str = false; continue; }
            if (c == '"') str = true;
            else if (c == '}') return i;
            else if (c == '{') return -1;
        }
        return -1;
    }

    // ------------------------------------------------------------------ parser
    private static final class Parser {
        final String s;
        int i;
        int depth;
        Parser(String s) { this.s = s; }

        void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        boolean eat(String tok) {
            skip();
            if (s.startsWith(tok, i)) { i += tok.length(); return true; }
            return false;
        }

        Node or() {
            if (++depth > 64) throw new ExprError("Expresie prea adâncă", i);
            Node l = and();
            while (eat("||")) { final Node a = l, b = and(); l = new Node() { public Object eval(Env e) { return bool(truthy(a.eval(e)) || truthy(b.eval(e))); } }; }
            depth--;
            return l;
        }

        Node and() {
            Node l = cmp();
            while (eat("&&")) { final Node a = l, b = cmp(); l = new Node() { public Object eval(Env e) { return bool(truthy(a.eval(e)) && truthy(b.eval(e))); } }; }
            return l;
        }

        Node cmp() {
            Node l = add();
            skip();
            String op = null;
            for (String o : new String[]{"==", "!=", "<=", ">=", "<", ">", "="}) {
                if (s.startsWith(o, i)) { op = o; break; }
            }
            if (op == null) return l;
            i += op.length();
            final Node a = l, b = add();
            final String o = op;
            return new Node() { public Object eval(Env e) { return bool(compare(o, a.eval(e), b.eval(e))); } };
        }

        Node add() {
            Node l = mul();
            while (true) {
                skip();
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    final char op = s.charAt(i++);
                    final Node a = l, b = mul();
                    l = new Node() {
                        public Object eval(Env e) {
                            Object x = a.eval(e), y = b.eval(e);
                            if (op == '+' && (x instanceof String || y instanceof String)) return format(x) + format(y);
                            return clean(op == '+' ? num(x) + num(y) : num(x) - num(y), e);
                        }
                    };
                } else return l;
            }
        }

        Node mul() {
            Node l = unary();
            while (true) {
                skip();
                if (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/' || s.charAt(i) == '%')) {
                    final char op = s.charAt(i++);
                    final Node a = l, b = unary();
                    l = new Node() {
                        public Object eval(Env e) {
                            double x = num(a.eval(e)), y = num(b.eval(e));
                            if (op == '*') return clean(x * y, e);
                            if (y == 0) { e.warn("împărțire la 0 (rezultat 0)"); return 0.0; }
                            return clean(op == '/' ? x / y : x % y, e);
                        }
                    };
                } else return l;
            }
        }

        Node unary() {
            skip();
            if (i < s.length() && s.charAt(i) == '!' && !s.startsWith("!=", i)) {
                i++;
                final Node a = unary();
                return new Node() { public Object eval(Env e) { return bool(!truthy(a.eval(e))); } };
            }
            if (i < s.length() && s.charAt(i) == '-') {
                i++;
                final Node a = unary();
                return new Node() { public Object eval(Env e) { return clean(-num(a.eval(e)), e); } };
            }
            if (i < s.length() && s.charAt(i) == '+') { i++; return unary(); }
            return primary();
        }

        Node primary() {
            skip();
            if (i >= s.length()) throw new ExprError("Expresia se termină prea devreme", i);
            char c = s.charAt(i);
            if (c == '(') {
                i++;
                Node n = or();
                if (!eat(")")) throw new ExprError("Lipsește „)”", i);
                return n;
            }
            if (c == '{') { // {name} is accepted too, like in templates
                i++;
                Node n = or();
                if (!eat("}")) throw new ExprError("Lipsește „}”", i);
                return n;
            }
            if (c == '"') return string();
            if (c == '#') return colour();
            if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) return number();
            if (isNameStart(c)) return name();
            throw new ExprError("Nu înțeleg „" + c + "”", i);
        }

        Node number() {
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            try {
                final Double v = Double.valueOf(s.substring(st, i));
                return new Node() { public Object eval(Env e) { return v; } };
            } catch (NumberFormatException ex) {
                throw new ExprError("Număr greșit „" + s.substring(st, i) + "”", st);
            }
        }

        Node string() {
            int st = i++;
            StringBuilder b = new StringBuilder();
            while (i < s.length() && s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\' && i < s.length()) c = s.charAt(i++);
                b.append(c);
            }
            if (i >= s.length()) throw new ExprError("Lipsesc ghilimelele de final", st);
            i++;
            final String v = b.toString();
            return new Node() { public Object eval(Env e) { return v; } };
        }

        Node colour() {
            int st = i++;
            while (i < s.length() && Character.digit(s.charAt(i), 16) >= 0) i++;
            int len = i - st - 1;
            if (len != 6 && len != 3) throw new ExprError("Culoarea se scrie #RRGGBB", st);
            final String v = ColorMatch.toHex(ColorMatch.parseHex(s.substring(st, i)));
            return new Node() { public Object eval(Env e) { return v; } };
        }

        Node name() {
            int st = i;
            while (i < s.length() && isNamePart(s.charAt(i))) i++;
            final String nm = s.substring(st, i).toLowerCase(Locale.ROOT);
            skip();
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                final List<Node> args = new ArrayList<Node>();
                if (!eat(")")) {
                    do { args.add(or()); } while (eat(","));
                    if (!eat(")")) throw new ExprError("Lipsește „)” după argumentele lui " + nm, i);
                }
                return function(nm, args, st);
            }
            if (nm.equals("adevarat") || nm.equals("true")) return constant(1.0);
            if (nm.equals("fals") || nm.equals("false")) return constant(0.0);
            if (isFunction(nm)) throw new ExprError(nm + " e o funcție: scrie " + nm + "(…)", st);
            return new Node() {
                public Object eval(Env e) {
                    Object v = e.get(nm);
                    return v == null ? (Object) 0.0 : v;
                }
            };
        }

        Node constant(final Object v) { return new Node() { public Object eval(Env e) { return v; } }; }

        Node function(final String f, final List<Node> a, int pos) {
            final int n = a.size();
            String need = null;
            if ((f.equals("random") || f.equals("aleator")) && (n < 1 || n > 2)) need = "random(min, max)";
            if ((f.equals("min") || f.equals("max")) && n < 1) need = f + "(a, b, …)";
            if ((f.equals("abs") || f.equals("floor") || f.equals("ceil") || f.equals("sqrt") || f.equals("len") || f.equals("lungime")
                    || f.equals("num") || f.equals("numar") || f.equals("text")) && n != 1) need = f + "(x)";
            if ((f.equals("round") || f.equals("rotunjit")) && (n < 1 || n > 2)) need = f + "(x) sau " + f + "(x, zecimale)";
            if ((f.equals("contains") || f.equals("contine")) && n != 2) need = f + "(text, bucată)";
            if ((f.equals("near") || f.equals("aproape")) && (n < 2 || n > 3)) need = f + "(culoare1, culoare2, toleranță)";
            if (need != null) throw new ExprError("Se scrie " + need, pos);
            return new Node() {
                public Object eval(Env e) {
                    if (f.equals("random") || f.equals("aleator")) {
                        double lo = n == 2 ? num(a.get(0).eval(e)) : 1, hi = num(a.get(n - 1).eval(e));
                        long l = (long) Math.ceil(Math.min(lo, hi)), h = (long) Math.floor(Math.max(lo, hi));
                        if (h < l) return (double) l;
                        return (double) (l + (long) Math.floor(e.random() * (h - l + 1)));
                    }
                    if (f.equals("min") || f.equals("max")) {
                        double r = num(a.get(0).eval(e));
                        for (int k = 1; k < n; k++) { double v = num(a.get(k).eval(e)); r = f.equals("min") ? Math.min(r, v) : Math.max(r, v); }
                        return r;
                    }
                    if (f.equals("abs")) return Math.abs(num(a.get(0).eval(e)));
                    if (f.equals("floor")) return Math.floor(num(a.get(0).eval(e)));
                    if (f.equals("ceil")) return Math.ceil(num(a.get(0).eval(e)));
                    if (f.equals("sqrt")) { double v = num(a.get(0).eval(e)); if (v < 0) { e.warn("sqrt dintr-un număr negativ"); return 0.0; } return Math.sqrt(v); }
                    if (f.equals("round") || f.equals("rotunjit")) {
                        double v = num(a.get(0).eval(e));
                        int d = n == 2 ? (int) Math.max(0, Math.min(10, num(a.get(1).eval(e)))) : 0;
                        double p = Math.pow(10, d);
                        return Math.round(v * p) / p;
                    }
                    if (f.equals("len") || f.equals("lungime")) return (double) format(a.get(0).eval(e)).length();
                    if (f.equals("num") || f.equals("numar")) return num(a.get(0).eval(e));
                    if (f.equals("text")) return format(a.get(0).eval(e));
                    if (f.equals("contains") || f.equals("contine")) {
                        String hay = format(a.get(0).eval(e)).toLowerCase(Locale.ROOT);
                        return bool(hay.contains(format(a.get(1).eval(e)).toLowerCase(Locale.ROOT)));
                    }
                    // near / aproape: two colours closer than the tolerance (per channel, default 24)
                    int c1 = ColorMatch.parseHex(format(a.get(0).eval(e))), c2 = ColorMatch.parseHex(format(a.get(1).eval(e)));
                    int tol = n == 3 ? (int) num(a.get(2).eval(e)) : 24;
                    return bool(ColorMatch.matches(c1, c2, tol));
                }
            };
        }
    }

    static Double bool(boolean b) { return b ? 1.0 : 0.0; }

    static Double clean(double v, Env e) {
        if (Double.isNaN(v) || Double.isInfinite(v)) { e.warn("rezultat prea mare sau nedefinit (0)"); return 0.0; }
        return v;
    }

    static boolean compare(String op, Object x, Object y) {
        boolean numeric = (x instanceof Double || isNumeric(x)) && (y instanceof Double || isNumeric(y));
        int c;
        if (numeric) c = Double.compare(num(x), num(y));
        else {
            String a = format(x), b = format(y);
            if (a.startsWith("#") && b.startsWith("#")) { a = a.toUpperCase(Locale.ROOT); b = b.toUpperCase(Locale.ROOT); }
            c = a.compareToIgnoreCase(b);
        }
        if (op.equals("==") || op.equals("=")) return c == 0;
        if (op.equals("!=")) return c != 0;
        if (op.equals("<")) return c < 0;
        if (op.equals("<=")) return c <= 0;
        if (op.equals(">")) return c > 0;
        return c >= 0;
    }

    private static boolean isNumeric(Object v) {
        if (v instanceof Double) return true;
        if (v == null) return true;
        String s = v.toString().trim();
        if (s.length() == 0) return false;
        Double d = Numbers.parse(s);
        // the whole text must be the number ("12" yes, "Level 12" no)
        return d != null && Numbers.isWholeNumber(s);
    }
}
