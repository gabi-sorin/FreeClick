package com.freeclick.core.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.freeclick.core.script.Ast.*;
import com.freeclick.core.script.Builtins.Sig;

/**
 * Before running: every name must exist, every value must have the right type (as in C++,
 * with friendly conversions: int ↔ double, numbers as conditions, anything + string), and
 * every variable gets its slot. Implicit conversions become explicit casts in the tree, so the
 * interpreter never has to guess. The first mistake is reported with its line, in Romanian.
 */
final class Checker {
    private final Program prog;
    private Scope scope;
    private Func func;          // function being checked (null = top level / event)
    private int slots;          // next free slot in the current frame
    private int maxSlots;
    private int loops, switches;
    private int globals;

    private static final class Scope {
        final Scope parent;
        final Map<String, Sym> vars = new HashMap<String, Sym>();
        final boolean global;
        Scope(Scope parent, boolean global) { this.parent = parent; this.global = global; }
        Sym find(String n) { for (Scope s = this; s != null; s = s.parent) { Sym v = s.vars.get(n); if (v != null) return v; } return null; }
    }

    private final Scope globalScope = new Scope(null, true);
    /** The predefined variable 'pasi' (the clicker list) is global slot 0. */
    static final int PASI_SLOT = 0;

    private Checker(Program p) { this.prog = p; }

    static void check(Program p) { new Checker(p).run(); }

    private ScriptError err(Expr e, String m) { return new ScriptError(m, e.line, e.col); }
    private ScriptError err(Stmt s, String m) { return new ScriptError(m, s.line, s.col); }

    private void run() {
        Sym pasi = new Sym();
        pasi.name = "pasi"; pasi.type = Type.vector(Type.PAS); pasi.global = true; pasi.slot = globals++;
        globalScope.vars.put("pasi", pasi);
        for (Func f : prog.funcs.values()) {
            if (Builtins.GLOBAL.containsKey(f.name) || Builtins.GENERIC.containsKey(f.name)) {
                throw new ScriptError("„" + f.name + "” e deja o funcție a aplicației; alege alt nume", f.line, f.col);
            }
            if (f.name.equals("main") && !f.params.isEmpty()) throw new ScriptError("main() nu primește parametri", f.line, f.col);
            for (Param pa : f.params) if (pa.type == Type.AUTO) throw new ScriptError("Parametrii nu pot fi auto: scrie tipul", f.line, f.col);
            if (f.ret == Type.AUTO) throw new ScriptError("Scrie tipul pe care îl întoarce funcția (de ex. int), nu auto", f.line, f.col);
        }
        // top level, in order: global variables and statements
        scope = globalScope;
        slots = 0; maxSlots = 0;
        for (Stmt s : prog.top) stmt(s);
        prog.topSlots = maxSlots;
        // functions
        for (Func f : prog.funcs.values()) checkFunc(f);
        // events
        for (Event e : prog.events) {
            scope = new Scope(globalScope, false);
            func = null;
            slots = 0; maxSlots = 0;
            e.arg = expr(e.arg);
            if (e.kind == Event.WHEN) e.arg = cond(e.arg);
            else if (e.kind == Event.EVERY) e.arg = convert(e.arg, Type.INT, "intervalul lui la_fiecare (ms)");
            else e.arg = convert(e.arg, Type.STRING, "ora lui la_ora (\"HH:MM\")");
            block(e.body);
            e.slots = maxSlots;
        }
        prog.globals = globals;
    }

    private void checkFunc(Func f) {
        func = f;
        scope = new Scope(globalScope, false);
        slots = 0; maxSlots = 0;
        for (Param pa : f.params) {
            if (scope.vars.containsKey(pa.name)) throw new ScriptError("Doi parametri se numesc „" + pa.name + "”", f.line, f.col);
            pa.sym = declare(pa.name, pa.type, pa.isConst, pa.isRef, f.line);
        }
        Scope inner = new Scope(scope, false);
        scope = inner;
        for (Stmt s : f.body.body) stmt(s);
        f.slots = maxSlots;
        func = null;
    }

    private Sym declare(String name, Type t, boolean isConst, boolean isRef, int line) {
        Sym s = new Sym();
        s.name = name; s.type = t; s.isConst = isConst; s.isRef = isRef; s.line = line;
        if (scope.global) { s.global = true; s.slot = globals++; }
        else { s.slot = slots++; maxSlots = Math.max(maxSlots, slots); }
        scope.vars.put(name, s);
        return s;
    }

    // ------------------------------------------------------------------ statements
    private void block(Block b) {
        Scope saved = scope;
        int savedSlots = slots;
        scope = new Scope(scope, false);
        for (Stmt s : b.body) stmt(s);
        scope = saved;
        slots = savedSlots; // slots of a finished block are reused
    }

    private void stmt(Stmt s) {
        if (s instanceof Block) { block((Block) s); return; }
        if (s instanceof Empty) return;
        if (s instanceof ExprStmt) { ExprStmt es = (ExprStmt) s; es.e = expr(es.e); return; }
        if (s instanceof VarDecl) { varDecl((VarDecl) s); return; }
        if (s instanceof If) {
            If i = (If) s;
            i.c = cond(expr(i.c));
            inBlock(i.a);
            if (i.b != null) inBlock(i.b);
            return;
        }
        if (s instanceof While) {
            While w = (While) s;
            w.c = cond(expr(w.c));
            loops++;
            inBlock(w.body);
            loops--;
            return;
        }
        if (s instanceof Timeout) {
            Timeout w = (Timeout) s;
            w.ms = convert(expr(w.ms), Type.INT, "timpul din timeout (ms)");
            inBlock(w.body);
            if (w.orElse != null) inBlock(w.orElse);
            return;
        }
        if (s instanceof Try) {
            Try w = (Try) s;
            inBlock(w.body);
            Scope saved = scope;
            int savedSlots = slots;
            scope = new Scope(scope, false);
            w.sym = declare(w.name, Type.STRING, false, false, s.line);
            inBlock(w.handler);
            scope = saved;
            slots = savedSlots;
            return;
        }
        if (s instanceof Switch) {
            Switch w = (Switch) s;
            w.e = expr(w.e);
            Type t = w.e.type;
            if (t.k != Type.K.INT && t.k != Type.K.STRING && t.k != Type.K.COLOR && t.k != Type.K.BOOL) {
                throw err(w.e, "switch merge pe int, string, color sau bool, nu pe " + t);
            }
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (int i = 0; i < w.values.size(); i++) {
                Expr v = convert(expr(w.values.get(i)), t, "valoarea de la case");
                w.values.set(i, v);
                if (v instanceof Lit && !seen.add(String.valueOf(((Lit) v).v))) throw err(v, "Cazul " + ((Lit) v).v + " apare de două ori");
            }
            Scope saved = scope;
            int savedSlots = slots;
            scope = new Scope(scope, false);
            switches++;
            for (Stmt x : w.body) stmt(x);
            switches--;
            scope = saved;
            slots = savedSlots;
            return;
        }
        if (s instanceof For) {
            For f = (For) s;
            Scope saved = scope;
            int savedSlots = slots;
            scope = new Scope(scope, false);
            if (f.init != null) stmt(f.init);
            if (f.c != null) f.c = cond(expr(f.c));
            if (f.step != null) f.step = expr(f.step);
            loops++;
            inBlock(f.body);
            loops--;
            scope = saved;
            slots = savedSlots;
            return;
        }
        if (s instanceof ForEach) {
            ForEach f = (ForEach) s;
            f.seq = expr(f.seq);
            Type elem;
            if (f.seq.type.k == Type.K.VECTOR) elem = f.seq.type.a;
            else if (f.seq.type.k == Type.K.STRING) elem = Type.STRING;
            else throw err(f.seq, "for (… : x) merge pe vector sau string, nu pe " + f.seq.type);
            if (f.isRef && !(f.seq.type.k == Type.K.VECTOR && isLvalue(f.seq))) throw err(f.seq, "for (auto& x : …) cere o variabilă vector");
            Type t = f.type == Type.AUTO ? elem : f.type;
            if (!t.equals(elem) && !(t.numeric() && elem.numeric() && !f.isRef)) {
                throw err(s, "Elementele sunt " + elem + ", nu " + t);
            }
            Scope saved = scope;
            int savedSlots = slots;
            scope = new Scope(scope, false);
            f.sym = declare(f.name, t, false, f.isRef, s.line);
            loops++;
            inBlock(f.body);
            loops--;
            scope = saved;
            slots = savedSlots;
            return;
        }
        if (s instanceof Return) {
            Return r = (Return) s;
            if (func == null) {
                if (r.e != null) throw err(s, "return cu valoare merge doar într-o funcție (aici oprește doar scriptul: scrie return;)");
                return;
            }
            if (func.ret == Type.VOID) {
                if (r.e != null) throw err(s, "Funcția „" + func.name + "” e void: nu întoarce nimic");
            } else {
                if (r.e == null) throw err(s, "Funcția „" + func.name + "” trebuie să întoarcă un " + func.ret);
                r.e = convert(expr(r.e, func.ret), func.ret, "valoarea întoarsă de „" + func.name + "”");
            }
            return;
        }
        if (s instanceof Break || s instanceof Continue) {
            if (s instanceof Break ? loops == 0 && switches == 0 : loops == 0) {
                throw err(s, s instanceof Break ? "break merge doar într-o buclă (while / for) sau în switch" : "continue merge doar într-o buclă (while / for)");
            }
            return;
        }
        if (s instanceof Print) {
            Print p = (Print) s;
            for (int i = 0; i < p.parts.size(); i++) {
                Expr e = expr(p.parts.get(i));
                if (e.type == Type.VOID) throw err(e, "Asta nu are valoare de afișat");
                p.parts.set(i, e);
            }
            return;
        }
        throw err(s, "Instrucțiune necunoscută");
    }

    /** A statement that is the body of if/while/for gets its own scope even without braces. */
    private void inBlock(Stmt s) {
        Scope saved = scope;
        int savedSlots = slots;
        scope = new Scope(scope, false);
        stmt(s);
        scope = saved;
        slots = savedSlots;
    }

    private void varDecl(VarDecl d) {
        for (int i = 0; i < d.names.size(); i++) {
            String n = d.names.get(i);
            if (scope.vars.containsKey(n)) throw err(d, "„" + n + "” e deja declarată aici");
            if (n.equals("pasi")) throw err(d, "„pasi” e lista de pași a scriptului; alege alt nume");
            Expr init = d.inits.get(i);
            Type t = d.type;
            if (d.isRef) {
                if (init == null) throw err(d, "O referință (&) trebuie legată de o variabilă: " + t + "& " + n + " = …;");
                init = expr(init, t == Type.AUTO ? null : t);
                if (!isLvalue(init)) throw err(init, "O referință (&) se leagă doar de o variabilă, un element sau un câmp");
                if (t == Type.AUTO) t = init.type;
                if (!t.equals(init.type)) throw err(init, "Referința e " + t + ", dar variabila e " + init.type);
            } else if (init != null) {
                init = expr(init, t == Type.AUTO ? null : t);
                if (t == Type.AUTO) {
                    if (init.type == Type.VOID || init.type == Type.ERROR) throw err(init, "auto nu poate primi o valoare fără tip");
                    t = init.type;
                }
                init = convert(init, t, "valoarea lui „" + n + "”");
            } else if (t == Type.AUTO) {
                throw err(d, "auto cere o valoare: auto " + n + " = …;");
            }
            if (d.isConst && init == null) throw err(d, "O constantă trebuie să primească o valoare");
            d.inits.set(i, init);
            Sym sym = declare(n, t, d.isConst, d.isRef, d.line);
            sym.keep = d.keep;
            if (d.keep && !sym.global) throw err(d, "pastreaza merge doar pentru variabile declarate sus, în afara funcțiilor");
            if (d.keep && !(t == Type.INT || t == Type.DOUBLE || t == Type.BOOL || t == Type.STRING || t == Type.COLOR)) {
                throw err(d, "pastreaza merge pentru int, double, bool, string și color");
            }
            d.syms.add(sym);
        }
    }

    // ------------------------------------------------------------------ expressions
    private Expr expr(Expr e) { return expr(e, null); }

    /** Type an expression. `want` is the type the context expects (for {…} lists), may be null. */
    private Expr expr(Expr e, Type want) {
        if (e instanceof Lit) return e;
        if (e instanceof Var) {
            Var v = (Var) e;
            v.sym = scope.find(v.name);
            if (v.sym == null) throw err(e, unknown(v.name));
            v.type = v.sym.type;
            return v;
        }
        if (e instanceof InitList) return initList((InitList) e, want);
        if (e instanceof Unary) return unary((Unary) e);
        if (e instanceof Binary) return binary((Binary) e);
        if (e instanceof Assign) return assign((Assign) e);
        if (e instanceof Cond) {
            Cond c = (Cond) e;
            c.c = cond(expr(c.c));
            c.a = expr(c.a, want);
            c.b = expr(c.b, want);
            if (c.a.type.equals(c.b.type)) c.type = c.a.type;
            else if (c.a.type.numeric() && c.b.type.numeric()) {
                c.type = Type.DOUBLE;
                c.a = convert(c.a, Type.DOUBLE, "");
                c.b = convert(c.b, Type.DOUBLE, "");
            } else throw err(e, "Cele două ramuri ale lui ?: au tipuri diferite: " + c.a.type + " și " + c.b.type);
            return c;
        }
        if (e instanceof Index) {
            Index ix = (Index) e;
            ix.target = expr(ix.target);
            ix.index = convert(expr(ix.index), Type.INT, "poziția din […]");
            if (ix.target.type.k == Type.K.VECTOR) ix.type = ix.target.type.a;
            else if (ix.target.type.k == Type.K.STRING) { ix.type = Type.STRING; ix.stringIndex = true; }
            else throw err(e, "[…] merge pe vector sau string, nu pe " + ix.target.type);
            return ix;
        }
        if (e instanceof Member) return member((Member) e);
        if (e instanceof Call) return call((Call) e, want);
        throw err(e, "Expresie necunoscută");
    }

    private String unknown(String name) {
        String best = null;
        int bd = 3;
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (Scope s = scope; s != null; s = s.parent) names.addAll(s.vars.keySet());
        names.addAll(prog.funcs.keySet());
        names.addAll(Builtins.allNames());
        for (String n : names) {
            int d = edit(n.toLowerCase(java.util.Locale.ROOT), name.toLowerCase(java.util.Locale.ROOT));
            if (d < bd) { bd = d; best = n; }
        }
        return "Nu știu ce e „" + name + "”" + (best != null ? " (ai vrut „" + best + "”?)" : " (declar-o întâi, de ex. int " + name + " = 0;)");
    }

    private static int edit(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++)
        {
            d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
        return d[a.length()][b.length()];
    }

    /** A condition: bool, or a number (≠ 0 is true). */
    private Expr cond(Expr e) {
        if (e.type == Type.BOOL) return e;
        if (e.type.numeric()) return cast(e, Type.BOOL);
        throw err(e, "Aici trebuie o condiție (bool), nu " + e.type);
    }

    private Expr initList(InitList l, Type want) {
        if (want == null) throw err(l, "Nu știu ce tip are {…} aici; scrie tipul, de ex. vector<int>{1, 2} sau point{1, 2}");
        Call c = new Call();
        c.line = l.line; c.col = l.col;
        c.ctor = want;
        c.name = "{}";
        c.args.addAll(l.items);
        return call(c, want);
    }

    private Expr unary(Unary u) {
        u.e = expr(u.e);
        String op = u.op;
        if (op.equals("!")) { u.e = cond(u.e); u.type = Type.BOOL; return u; }
        if (op.equals("-") || op.equals("+")) {
            if (u.e.type.numeric()) { u.type = u.e.type; return u; }
            if (u.e.type.equals(Type.POINT) && op.equals("-")) { u.type = Type.POINT; return u; }
            throw err(u, "„" + op + "” merge pe numere, nu pe " + u.e.type);
        }
        // ++ / --
        if (!u.e.type.numeric()) throw err(u, op + " merge pe numere, nu pe " + u.e.type);
        lvalue(u.e, op);
        u.type = u.e.type;
        return u;
    }

    private boolean isLvalue(Expr e) {
        if (e instanceof Var) return true;
        if (e instanceof Index) return !((Index) e).stringIndex && isLvalue(((Index) e).target);
        if (e instanceof Member) return ((Member) e).field >= 0 && isLvalue(((Member) e).target);
        return false;
    }

    private void lvalue(Expr e, String what) {
        if (!isLvalue(e)) throw err(e, "Lui " + what + " îi trebuie o variabilă (sau un element / câmp), nu o valoare calculată");
        Expr root = e;
        while (true) {
            if (root instanceof Index) root = ((Index) root).target;
            else if (root instanceof Member) root = ((Member) root).target;
            else break;
        }
        if (root instanceof Var && ((Var) root).sym.isConst) throw err(e, "„" + ((Var) root).name + "” e const: nu se poate schimba");
    }

    private Expr binary(Binary b) {
        b.l = expr(b.l);
        b.r = expr(b.r);
        Type l = b.l.type, r = b.r.type;
        String op = b.op;
        if (op.equals("&&") || op.equals("||")) {
            b.l = cond(b.l); b.r = cond(b.r); b.type = Type.BOOL; return b;
        }
        if (op.equals("+") && (l == Type.STRING || r == Type.STRING)) {
            if (l == Type.VOID || r == Type.VOID) throw err(b, "Nu pot lipi un void la un text");
            b.type = Type.STRING;
            return b;
        }
        if (l.numeric() && r.numeric()) {
            Type t = (l == Type.DOUBLE || r == Type.DOUBLE) ? Type.DOUBLE : Type.INT;
            if (op.equals("%") && t == Type.DOUBLE) throw err(b, "% (restul) merge doar pe int; pentru double folosește fmod… sau int(x) % n");
            b.l = convert(b.l, t, ""); b.r = convert(b.r, t, "");
            b.type = isCompare(op) ? Type.BOOL : t;
            return b;
        }
        if (l.equals(Type.POINT) && r.equals(Type.POINT) && (op.equals("+") || op.equals("-"))) { b.type = Type.POINT; return b; }
        if (l.equals(Type.POINT) && r.numeric() && (op.equals("*") || op.equals("/"))) { b.r = convert(b.r, Type.DOUBLE, ""); b.type = Type.POINT; return b; }
        if (op.equals("==") || op.equals("!=")) {
            if (l.equals(r) && l.k != Type.K.VOID) { b.type = Type.BOOL; return b; }
            if ((l == Type.BOOL && r.numeric()) || (r == Type.BOOL && l.numeric())) {
                b.l = convert(b.l, Type.BOOL, ""); b.r = convert(b.r, Type.BOOL, ""); b.type = Type.BOOL; return b;
            }
        }
        if (isCompare(op) && l == Type.STRING && r == Type.STRING) { b.type = Type.BOOL; return b; }
        throw err(b, "„" + op + "” nu merge între " + l + " și " + r);
    }

    private static boolean isCompare(String op) {
        return op.equals("==") || op.equals("!=") || op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=");
    }

    private Expr assign(Assign a) {
        a.target = expr(a.target);
        lvalue(a.target, a.op);
        Type t = a.target.type;
        if (a.op.equals("=")) {
            a.value = convert(expr(a.value, t), t, "valoarea atribuită");
        } else if (a.op.equals("+=") && t == Type.STRING) {
            a.value = expr(a.value);
            if (a.value.type == Type.VOID) throw err(a.value, "Nu pot adăuga un void la text");
        } else if (t.numeric()) {
            a.value = expr(a.value);
            if (!a.value.type.numeric()) throw err(a.value, a.op + " cere un număr, nu " + a.value.type);
            if (a.op.equals("%=") && (t == Type.DOUBLE || a.value.type == Type.DOUBLE)) throw err(a, "%= merge doar pe int");
            a.value = convert(a.value, t == Type.INT && a.value.type == Type.DOUBLE ? Type.DOUBLE : t, "");
        } else if (t.equals(Type.POINT) && (a.op.equals("+=") || a.op.equals("-="))) {
            a.value = convert(expr(a.value), Type.POINT, "");
        } else {
            throw err(a, a.op + " nu merge pe " + t);
        }
        a.type = t;
        return a;
    }

    private Expr member(Member m) {
        m.target = expr(m.target);
        Type t = m.target.type;
        if (t.k == Type.K.STRUCT) {
            int f = t.st.field(m.name);
            if (f < 0) throw err(m, t.st.name + " nu are câmpul „" + m.name + "” (are: " + join(t.st.fields) + ")");
            m.field = f;
            m.type = t.st.types.get(f);
            return m;
        }
        if (t.k == Type.K.PAIR) {
            if (m.name.equals("first")) { m.field = 0; m.type = t.a; return m; }
            if (m.name.equals("second")) { m.field = 1; m.type = t.b; return m; }
            throw err(m, "Un pair are doar .first și .second");
        }
        if (t.k == Type.K.COLOR) {
            int sp = m.name.equals("r") ? 1 : m.name.equals("g") ? 2 : m.name.equals("b") ? 3 : 0;
            if (sp == 0) throw err(m, "O culoare are .r, .g, .b (și .hex())");
            m.special = sp;
            m.type = Type.INT;
            return m;
        }
        throw err(m, t + " nu are câmpuri (" + m.name + ")");
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(", "); b.append(s); }
        return b.toString();
    }

    // ------------------------------------------------------------------ conversions
    /** Make `e` a `to`: same type as is, numbers/bools converted, anything else is a mistake. */
    Expr convert(Expr e, Type to, String what) {
        Type from = e.type;
        if (from.equals(to)) return e;
        if (from == Type.ERROR) return e;
        boolean ok = (from.numeric() || from == Type.BOOL) && (to.numeric() || to == Type.BOOL);
        if (ok) return cast(e, to);
        throw err(e, (what.isEmpty() ? "Valoarea" : capital(what)) + " trebuie să fie " + to + ", dar e " + from);
    }

    private static String capital(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private Expr cast(Expr e, Type to) {
        Call c = new Call();
        c.line = e.line; c.col = e.col;
        c.ctor = to;
        c.builtin = Builtins.CAST;
        c.args.add(e);
        c.type = to;
        return c;
    }

    // ------------------------------------------------------------------ calls
    private Expr call(Call c, Type want) {
        if (c.ctor != null) return construct(c);
        if (c.receiver != null) return method(c);
        Func f = prog.funcs.get(c.name);
        if (f != null) {
            if (c.args.size() != f.params.size()) {
                throw err(c, "„" + c.name + "” primește " + f.params.size() + " parametri, nu " + c.args.size());
            }
            for (int i = 0; i < c.args.size(); i++) {
                Param pa = f.params.get(i);
                Expr a = expr(c.args.get(i), pa.type);
                if (pa.isRef) {
                    if (!isLvalue(a)) throw err(a, "Parametrul „" + pa.name + "” e " + pa.type + "& : dă-i o variabilă, nu o valoare");
                    if (!a.type.equals(pa.type)) throw err(a, "Parametrul „" + pa.name + "” e " + pa.type + "&, dar primește " + a.type);
                    if (!pa.isConst) lvalue(a, "parametrul " + pa.name);
                } else {
                    a = convert(a, pa.type, "parametrul „" + pa.name + "” al lui " + c.name);
                }
                c.args.set(i, a);
            }
            c.func = f;
            c.type = f.ret;
            return c;
        }
        Integer gen = Builtins.GENERIC.get(c.name);
        if (gen != null) return generic(c, gen);
        List<Sig> sigs = Builtins.GLOBAL.get(c.name);
        if (sigs == null) throw err(c, unknown(c.name));
        for (int i = 0; i < c.args.size(); i++) {
            Expr a = c.args.get(i);
            c.args.set(i, a instanceof InitList ? a : expr(a));
        }
        Sig best = null;
        for (Sig s : sigs) if (fits(s, c.args)) { best = s; break; }
        if (best == null) {
            StringBuilder b = new StringBuilder();
            for (Sig s : sigs) {
                if (b.length() > 0) b.append(" sau ");
                b.append(c.name).append('(');
                for (int i = 0; i < s.params.length; i++) {
                    if (i > 0) b.append(", ");
                    if (i >= s.min()) b.append('[');
                    b.append(s.params[i]);
                    if (i >= s.min()) b.append(']');
                }
                b.append(')');
            }
            String hint = (c.name.equals("pipeta") || c.name.equals("pixel") || c.name.equals("culoare")) && c.args.isEmpty()
                    ? ". Ca să aștepte atingerea ta și să ia culoarea de acolo: pipetaAtingere() (sau get_touch_pipeta())" : "";
            throw err(c, "Nu pot apela " + c.name + " așa. Se scrie: " + b + hint);
        }
        for (int i = 0; i < c.args.size(); i++) {
            Expr a = c.args.get(i);
            if (a instanceof InitList) a = expr(a, best.params[i]);
            c.args.set(i, convert(a, best.params[i], "parametrul " + (i + 1) + " al lui " + c.name));
        }
        c.builtin = best.id;
        c.type = best.ret;
        return c;
    }

    private boolean fits(Sig s, List<Expr> args) {
        if (args.size() < s.min() || args.size() > s.params.length) return false;
        for (int i = 0; i < args.size(); i++) {
            Expr a = args.get(i);
            if (a instanceof InitList) {
                if (s.params[i].k != Type.K.STRUCT && s.params[i].k != Type.K.VECTOR && s.params[i].k != Type.K.PAIR) return false;
                continue;
            }
            Type from = a.type, to = s.params[i];
            if (from.equals(to)) continue;
            if ((from.numeric() || from == Type.BOOL) && (to.numeric() || to == Type.BOOL)) continue;
            return false;
        }
        return true;
    }

    private Expr generic(Call c, int id) {
        for (int i = 0; i < c.args.size(); i++) c.args.set(i, expr(c.args.get(i)));
        int n = c.args.size();
        c.builtin = id;
        switch (id) {
            case Builtins.PRINT:
                for (Expr a : c.args) if (a.type == Type.VOID) throw err(a, "Asta nu are valoare de afișat");
                c.type = Type.VOID;
                return c;
            case Builtins.TO_STRING:
                if (n != 1) throw err(c, "to_string(x) primește o valoare");
                c.type = Type.STRING;
                return c;
            case Builtins.ABS:
                if (n != 1 || !c.args.get(0).type.numeric()) throw err(c, "abs(x) primește un număr");
                c.type = c.args.get(0).type;
                return c;
            case Builtins.MIN:
            case Builtins.MAX: {
                if (n < 2) throw err(c, c.name + "(a, b, …) primește cel puțin două valori");
                boolean dbl = false, str = true;
                for (Expr a : c.args) { if (!a.type.numeric()) str &= a.type == Type.STRING; else str = false; if (a.type == Type.DOUBLE) dbl = true; }
                if (str) { c.type = Type.STRING; return c; }
                Type t = dbl ? Type.DOUBLE : Type.INT;
                for (int i = 0; i < n; i++) c.args.set(i, convert(c.args.get(i), t, "parametrii lui " + c.name));
                c.type = t;
                return c;
            }
            case Builtins.MAKE_PAIR:
                if (n != 2) throw err(c, "make_pair(a, b) primește două valori");
                c.type = Type.pair(c.args.get(0).type, c.args.get(1).type);
                return c;
            case Builtins.SORT:
            case Builtins.REVERSE: {
                if (n != 1 || c.args.get(0).type.k != Type.K.VECTOR) throw err(c, c.name + "(v) primește un vector");
                lvalue(c.args.get(0), c.name);
                Type e = c.args.get(0).type.a;
                if (id == Builtins.SORT && !(e.numeric() || e == Type.STRING)) throw err(c, "sort merge pe vectori de numere sau de texte");
                c.type = Type.VOID;
                return c;
            }
            case Builtins.SWAP:
                if (n != 2 || !c.args.get(0).type.equals(c.args.get(1).type)) throw err(c, "swap(a, b) primește două variabile de același tip");
                lvalue(c.args.get(0), "swap");
                lvalue(c.args.get(1), "swap");
                c.type = Type.VOID;
                return c;
            default:
                throw err(c, "?");
        }
    }

    /** Casts and constructors: int(x), string(x), point(1, 2), color(255, 0, 0), color("#34A853"), vector<int>{1, 2}, Tinta{…}. */
    private Expr construct(Call c) {
        Type t = c.ctor;
        c.type = t;
        if (c.builtin == Builtins.CAST) return c;
        int n = c.args.size();
        if (t.numeric() || t == Type.BOOL) {
            if (n != 1) throw err(c, t + "(x) primește o valoare");
            Expr a = expr(c.args.get(0));
            c.args.set(0, a);
            if (a.type == Type.STRING && t.numeric()) { c.builtin = t == Type.INT ? Builtins.TO_INT : Builtins.TO_DOUBLE; return c; }
            return convert(a, t, t + "(…)");
        }
        if (t == Type.STRING) {
            if (n == 0) { c.builtin = Builtins.CTOR; return c; }
            if (n != 1) throw err(c, "string(x) primește o valoare");
            c.args.set(0, expr(c.args.get(0)));
            c.builtin = Builtins.TO_STRING;
            return c;
        }
        if (t == Type.COLOR) {
            for (int i = 0; i < n; i++) c.args.set(i, expr(c.args.get(i)));
            if (n == 1 && c.args.get(0).type == Type.STRING) { c.builtin = Builtins.CTOR; return c; }
            if (n == 1 && c.args.get(0).type == Type.COLOR) return c.args.get(0);
            if (n != 3) throw err(c, "color(r, g, b) cu valori 0–255, sau color(\"#34A853\")");
            for (int i = 0; i < 3; i++) c.args.set(i, convert(c.args.get(i), Type.INT, "componenta " + "rgb".charAt(i)));
            c.builtin = Builtins.CTOR;
            return c;
        }
        if (t.k == Type.K.VECTOR) {
            // vector<int>(n) / vector<int>(n, x) / vector<int>{a, b, c}
            c.builtin = Builtins.CTOR;
            if (!"{}".equals(c.name)) {
                if (n > 2) throw err(c, "vector<T>(n) sau vector<T>(n, valoare)");
                if (n >= 1) c.args.set(0, convert(expr(c.args.get(0)), Type.INT, "mărimea vectorului"));
                if (n == 2) c.args.set(1, convert(expr(c.args.get(1), t.a), t.a, "valoarea de început"));
                c.name = "(n)";
                return c;
            }
            for (int i = 0; i < n; i++) c.args.set(i, convert(expr(c.args.get(i), t.a), t.a, "elementul " + (i + 1)));
            return c;
        }
        if (t.k == Type.K.PAIR) {
            if (n != 0 && n != 2) throw err(c, t + " primește două valori: {a, b}");
            if (n == 2) {
                c.args.set(0, convert(expr(c.args.get(0), t.a), t.a, ".first"));
                c.args.set(1, convert(expr(c.args.get(1), t.b), t.b, ".second"));
            }
            c.builtin = Builtins.CTOR;
            return c;
        }
        if (t.k == Type.K.STRUCT) {
            List<Type> ft = t.st.types;
            if (n > ft.size()) throw err(c, t.st.name + " are doar " + ft.size() + " câmpuri (" + join(t.st.fields) + ")");
            for (int i = 0; i < n; i++) c.args.set(i, convert(expr(c.args.get(i), ft.get(i)), ft.get(i), "câmpul „" + t.st.fields.get(i) + "”"));
            c.builtin = Builtins.CTOR;
            return c;
        }
        throw err(c, "Nu se poate construi un " + t);
    }

    private Expr method(Call c) {
        c.receiver = expr(c.receiver);
        Type t = c.receiver.type;
        for (int i = 0; i < c.args.size(); i++) c.args.set(i, expr(c.args.get(i), t.k == Type.K.VECTOR ? t.a : null));
        int n = c.args.size();
        String m = c.name;
        if (t == Type.STRING) {
            if ((m.equals("size") || m.equals("length")) && n == 0) return ret(c, Builtins.S_SIZE, Type.INT);
            if (m.equals("empty") && n == 0) return ret(c, Builtins.S_EMPTY, Type.BOOL);
            if (m.equals("substr") && (n == 1 || n == 2)) { ints(c, 0); return ret(c, Builtins.S_SUBSTR, Type.STRING); }
            if (m.equals("find") && n == 1) { strs(c); return ret(c, Builtins.S_FIND, Type.INT); }
            if ((m.equals("contine") || m.equals("contains")) && n == 1) { strs(c); return ret(c, Builtins.S_CONTAINS, Type.BOOL); }
            if (m.equals("starts_with") && n == 1) { strs(c); return ret(c, Builtins.S_STARTS, Type.BOOL); }
            if (m.equals("ends_with") && n == 1) { strs(c); return ret(c, Builtins.S_ENDS, Type.BOOL); }
            if ((m.equals("upper") || m.equals("mari")) && n == 0) return ret(c, Builtins.S_UPPER, Type.STRING);
            if ((m.equals("lower") || m.equals("mici")) && n == 0) return ret(c, Builtins.S_LOWER, Type.STRING);
            if ((m.equals("replace") || m.equals("inlocuieste")) && n == 2) { strs(c); return ret(c, Builtins.S_REPLACE, Type.STRING); }
            if (m.equals("trim") && n == 0) return ret(c, Builtins.S_TRIM, Type.STRING);
            throw err(c, "string are: size(), substr(poz, lung), find(x), contine(x), starts_with, ends_with, upper(), lower(), replace(a, b), trim()");
        }
        if (t.k == Type.K.VECTOR) {
            Type e = t.a;
            boolean mut = m.equals("push_back") || m.equals("pop_back") || m.equals("clear") || m.equals("erase") || m.equals("insert") || m.equals("adauga");
            if (mut) lvalue(c.receiver, m);
            if ((m.equals("push_back") || m.equals("adauga")) && n == 1) { c.args.set(0, convert(c.args.get(0), e, "elementul")); return ret(c, Builtins.V_PUSH, Type.VOID); }
            if (m.equals("pop_back") && n == 0) return ret(c, Builtins.V_POP, Type.VOID);
            if (m.equals("size") && n == 0) return ret(c, Builtins.V_SIZE, Type.INT);
            if (m.equals("empty") && n == 0) return ret(c, Builtins.V_EMPTY, Type.BOOL);
            if (m.equals("clear") && n == 0) return ret(c, Builtins.V_CLEAR, Type.VOID);
            if (m.equals("back") && n == 0) return ret(c, Builtins.V_BACK, e);
            if (m.equals("front") && n == 0) return ret(c, Builtins.V_FRONT, e);
            if (m.equals("erase") && n == 1) { ints(c, 0); return ret(c, Builtins.V_ERASE, Type.VOID); }
            if (m.equals("insert") && n == 2) { ints(c, 0); c.args.set(1, convert(c.args.get(1), e, "elementul")); return ret(c, Builtins.V_INSERT, Type.VOID); }
            if ((m.equals("contine") || m.equals("contains")) && n == 1) { c.args.set(0, convert(c.args.get(0), e, "elementul")); return ret(c, Builtins.V_CONTAINS, Type.BOOL); }
            if ((m.equals("index_of") || m.equals("pozitia")) && n == 1) { c.args.set(0, convert(c.args.get(0), e, "elementul")); return ret(c, Builtins.V_INDEX_OF, Type.INT); }
            throw err(c, "vector are: push_back(x), pop_back(), size(), empty(), clear(), back(), front(), erase(i), insert(i, x), contine(x), index_of(x)");
        }
        if (t == Type.COLOR && m.equals("hex") && n == 0) return ret(c, Builtins.C_HEX, Type.STRING);
        throw err(c, t + " nu are metoda „" + m + "”");
    }

    private void ints(Call c, int from) { for (int i = from; i < c.args.size(); i++) c.args.set(i, convert(c.args.get(i), Type.INT, "poziția")); }
    private void strs(Call c) { for (int i = 0; i < c.args.size(); i++) c.args.set(i, convert(c.args.get(i), Type.STRING, "textul")); }
    private Call ret(Call c, int id, Type t) { c.builtin = id; c.type = t; return c; }
}
