package com.freeclick.core.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.freeclick.core.script.Ast.*;
import com.freeclick.core.script.Lexer.K;
import com.freeclick.core.script.Lexer.Tok;

/** Recursive-descent parser: tokens → Ast.Program (C++-like syntax, Romanian error messages). */
final class Parser {
    private final List<Tok> toks;
    private int p;
    private final Map<String, StructDef> structs = new HashMap<String, StructDef>();
    private final Program prog = new Program();

    private Parser(List<Tok> toks) { this.toks = toks; }

    static Program parse(String src) {
        Parser ps = new Parser(Lexer.lex(src));
        ps.prescanStructs();
        ps.program();
        return ps.prog;
    }

    // ------------------------------------------------------------------ helpers
    private Tok cur() { return toks.get(p); }
    private Tok peek(int k) { return toks.get(Math.min(toks.size() - 1, p + k)); }
    private Tok next() { Tok t = toks.get(p); if (p < toks.size() - 1) p++; return t; }
    private boolean at(String s) { return cur().is(s); }
    private boolean eat(String s) { if (at(s)) { next(); return true; } return false; }

    private ScriptError err(Tok t, String msg) { return new ScriptError(msg, t.line, t.col); }

    private Tok expect(String s) {
        if (at(s)) return next();
        // a '>>' closing two templates at once: split it
        if (s.equals(">") && at(">>")) { // vector<vector<int>>: split ">>" into two ">" for good
            Tok t = cur();
            toks.set(p, new Tok(K.OP, ">", t.line, t.col, t.pos));
            toks.add(p + 1, new Tok(K.OP, ">", t.line, t.col + 1, t.pos + 1));
            return next();
        }
        Tok t = cur();
        String what = s.equals(";") ? "Lipsește „;” (la sfârșitul instrucțiunii de dinainte)" : "Aștept „" + s + "”, dar am găsit " + t;
        if (s.equals(";") && p > 0) { Tok prev = toks.get(p - 1); throw new ScriptError(what, prev.line, prev.col + prev.s.length()); }
        throw err(t, what);
    }

    private String ident() {
        Tok t = cur();
        if (t.k != K.IDENT || KEYWORDS.contains(t.s)) throw err(t, "Aștept un nume, dar am găsit " + t);
        next();
        return t.s;
    }

    static final java.util.Set<String> KEYWORDS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "if", "else", "while", "for", "do", "return", "break", "continue", "struct", "const", "true", "false",
            "int", "long", "short", "unsigned", "signed", "float", "double", "bool", "string", "color", "point", "rect", "void",
            "auto", "vector", "pair", "cout", "endl", "std", "daca", "altfel", "cat_timp", "pastreaza", "switch", "case", "default"));

    private static final java.util.Set<String> TYPE_WORDS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "int", "long", "short", "unsigned", "signed", "float", "double", "bool", "string", "color", "point", "rect", "void",
            "auto", "vector", "pair", "Pas", "std"));

    private boolean isTypeStart(Tok t) {
        return t.k == K.IDENT && (TYPE_WORDS.contains(t.s) || structs.containsKey(t.s));
    }

    private void prescanStructs() {
        for (int i = 0; i + 1 < toks.size(); i++) {
            if (toks.get(i).is("struct") && toks.get(i + 1).k == K.IDENT) {
                String n = toks.get(i + 1).s;
                if (!structs.containsKey(n)) {
                    StructDef d = new StructDef(n, false);
                    structs.put(n, d);
                    prog.structs.add(d);
                }
            }
        }
    }

    // ------------------------------------------------------------------ types
    private Type type() {
        Tok t = cur();
        if (eat("std")) { expect("::"); t = cur(); }
        String w = t.s;
        if (t.k != K.IDENT) throw err(t, "Aștept un tip (int, double, bool, string, point, vector<…>…), dar am găsit " + t);
        next();
        if (w.equals("unsigned") || w.equals("signed")) { if (at("int") || at("long") || at("short")) next(); if (at("long")) next(); return Type.INT; }
        if (w.equals("long")) { if (at("long")) next(); if (at("int")) next(); if (at("double")) { next(); return Type.DOUBLE; } return Type.INT; }
        if (w.equals("int") || w.equals("short")) return Type.INT;
        if (w.equals("float") || w.equals("double")) return Type.DOUBLE;
        if (w.equals("bool")) return Type.BOOL;
        if (w.equals("string")) return Type.STRING;
        if (w.equals("color")) return Type.COLOR;
        if (w.equals("point")) return Type.POINT;
        if (w.equals("rect")) return Type.RECT;
        if (w.equals("Pas")) return Type.PAS;
        if (w.equals("void")) return Type.VOID;
        if (w.equals("auto")) return Type.AUTO;
        if (w.equals("vector")) {
            expect("<");
            Type e = type();
            expect(">");
            return Type.vector(e);
        }
        if (w.equals("pair")) {
            expect("<");
            Type a = type();
            expect(",");
            Type b = type();
            expect(">");
            return Type.pair(a, b);
        }
        StructDef d = structs.get(w);
        if (d != null) return Type.struct(d);
        throw err(t, "Nu știu tipul „" + w + "”");
    }

    // ------------------------------------------------------------------ program
    private void program() {
        while (cur().k != K.EOF) {
            Tok t = cur();
            if (t.is("struct")) { structDecl(); continue; }
            if ((t.is("cand") || t.is("when") || t.is("la_fiecare") || t.is("every") || t.is("la_ora") || t.is("at")) && peek(1).is("(")) {
                event();
                continue;
            }
            if (isFuncStart()) { func(); continue; }
            prog.top.add(statement());
        }
    }

    /** type name ( … ) { — a function definition. */
    private boolean isFuncStart() {
        if (!isTypeStart(cur())) return false;
        int save = p;
        try {
            type();
            eat("&");
            if (cur().k != K.IDENT || KEYWORDS.contains(cur().s)) return false;
            next();
            if (!at("(")) return false;
            Tok after = peek(1); // "int f(int a)" is a function, "vector<int> v(3)" a variable
            return after.is(")") || after.is("const") || isTypeStart(after);
        } catch (ScriptError e) {
            return false;
        } finally {
            p = save;
        }
    }

    private void structDecl() {
        Tok kw = next();
        String name = ident();
        StructDef d = structs.get(name);
        if (!d.fields.isEmpty()) throw err(kw, "Structura „" + name + "” e definită de două ori");
        expect("{");
        while (!eat("}")) {
            if (cur().k == K.EOF) throw err(cur(), "Lipsește „}” la sfârșitul structurii „" + name + "”");
            Type ft = type();
            do {
                Tok nt = cur();
                String fn = ident();
                if (d.field(fn) >= 0) throw err(nt, "Câmpul „" + fn + "” apare de două ori în „" + name + "”");
                if (ft.isStruct(d)) throw err(nt, "O structură nu se poate conține pe ea însăși (folosește vector<" + name + ">)");
                d.add(fn, ft);
            } while (eat(","));
            expect(";");
        }
        eat(";");
        if (d.fields.isEmpty()) throw err(kw, "Structura „" + name + "” nu are niciun câmp");
    }

    private void event() {
        Tok t = next();
        Event e = new Event();
        e.line = t.line;
        e.kind = t.is("cand") || t.is("when") ? Event.WHEN : t.is("la_fiecare") || t.is("every") ? Event.EVERY : Event.AT;
        expect("(");
        e.arg = expr();
        expect(")");
        e.body = block();
        prog.events.add(e);
    }

    private void func() {
        Tok start = cur();
        Func f = new Func();
        f.ret = type();
        if (eat("&")) throw err(start, "Funcțiile nu pot întoarce referințe (&) aici");
        Tok nt = cur();
        f.name = ident();
        f.line = nt.line;
        f.col = nt.col;
        if (prog.funcs.containsKey(f.name)) throw err(nt, "Funcția „" + f.name + "” e definită de două ori (aici nu există supraîncărcare)");
        expect("(");
        if (!at(")")) {
            if (at("void") && peek(1).is(")")) next();
            else do {
                Param pa = new Param();
                pa.isConst = eat("const");
                pa.type = type();
                pa.isRef = eat("&");
                pa.name = ident();
                f.params.add(pa);
            } while (eat(","));
        }
        expect(")");
        f.body = block();
        prog.funcs.put(f.name, f);
        if (f.name.equals("main")) prog.main = f;
    }

    // ------------------------------------------------------------------ statements
    private Block block() {
        Tok t = expect("{");
        Block b = new Block();
        b.line = t.line; b.col = t.col;
        while (!eat("}")) {
            if (cur().k == K.EOF) throw err(t, "Lipsește „}” pentru „{” de pe linia " + t.line);
            b.body.add(statement());
        }
        return b;
    }

    private <S extends Stmt> S at(S s, Tok t) { s.line = t.line; s.col = t.col; return s; }

    private Stmt statement() {
        Tok t = cur();
        if (t.is("{")) return block();
        if (t.is(";")) { next(); return at(new Empty(), t); }
        if (t.is("if") || t.is("daca")) {
            next();
            If s = at(new If(), t);
            expect("(");
            s.c = expr();
            expect(")");
            s.a = statement();
            if (eat("else") || eat("altfel")) s.b = statement();
            return s;
        }
        if (t.is("while") || t.is("cat_timp")) {
            next();
            While s = at(new While(), t);
            expect("(");
            s.c = expr();
            expect(")");
            s.body = statement();
            return s;
        }
        if (t.is("do")) {
            next();
            While s = at(new While(), t);
            s.doWhile = true;
            s.body = statement();
            expect("while");
            expect("(");
            s.c = expr();
            expect(")");
            expect(";");
            return s;
        }
        if (t.is("for")) return forStmt();
        if (t.is("timeout") || t.is("limita")) {
            next();
            Timeout s = at(new Timeout(), t);
            expect("(");
            s.ms = expr();
            expect(")");
            s.body = statement();
            if (eat("else") || eat("altfel")) s.orElse = statement();
            return s;
        }
        if (t.is("try") || t.is("incearca")) {
            next();
            Try s = at(new Try(), t);
            if (!at("{")) throw err(cur(), "După try vine un bloc: try { … } catch (string e) { … }");
            s.body = statement();
            Tok c = cur();
            if (!eat("catch") && !eat("prinde")) throw err(c, "După try { … } vine catch (string e) { … }");
            expect("(");
            if (eat("string") || eat("auto")) { /* the type is always string */ }
            Tok n = cur();
            if (n.k != Lexer.K.IDENT) throw err(n, "În catch ( … ) scrie un nume pentru mesaj, de ex. catch (string e)");
            next();
            s.name = n.s;
            expect(")");
            if (!at("{")) throw err(cur(), "După catch (…) vine un bloc { … }");
            s.handler = statement();
            return s;
        }
        if (t.is("catch") || t.is("prinde")) throw err(t, "catch merge doar după try { … }");
        if (t.is("switch")) {
            next();
            Switch s = at(new Switch(), t);
            expect("(");
            s.e = expr();
            expect(")");
            expect("{");
            while (!at("}")) {
                Tok k = cur();
                if (k.k == Lexer.K.EOF) throw err(k, "Lipsește } la sfârșitul lui switch");
                if (eat("case")) {
                    s.values.add(ternary());
                    s.targets.add(s.body.size());
                    expect(":");
                } else if (eat("default")) {
                    if (s.defaultTarget >= 0) throw err(k, "switch are deja un default");
                    s.defaultTarget = s.body.size();
                    expect(":");
                } else {
                    if (s.values.isEmpty() && s.defaultTarget < 0) throw err(k, "În switch, înainte de cod vine case valoare: sau default:");
                    s.body.add(statement());
                }
            }
            expect("}");
            return s;
        }
        if (t.is("case") || t.is("default")) throw err(t, t.s + " merge doar în switch (…) { }");
        if (t.is("return")) {
            next();
            Return s = at(new Return(), t);
            if (!at(";")) s.e = expr();
            expect(";");
            return s;
        }
        if (t.is("break")) { next(); expect(";"); return at(new Break(), t); }
        if (t.is("continue")) { next(); expect(";"); return at(new Continue(), t); }
        if (t.is("cout") || (t.is("std") && peek(2).is("cout"))) {
            if (t.is("std")) { next(); next(); }
            next();
            Print s = at(new Print(), t);
            if (!at("<<")) throw err(cur(), "După cout urmează << (de ex. cout << \"aur \" << aur << endl;)");
            while (eat("<<")) {
                if (at("endl") || (at("std") && peek(2).is("endl"))) {
                    if (at("std")) { next(); next(); }
                    Tok e = next();
                    Lit l = new Lit();
                    l.line = e.line; l.col = e.col; l.v = "\n"; l.type = Type.STRING;
                    s.parts.add(l);
                } else {
                    s.parts.add(ternary());
                }
            }
            expect(";");
            return s;
        }
        if (t.is("struct")) throw err(t, "Structurile se definesc în afara funcțiilor (sus, în fișier)");
        if (t.is("pastreaza") || t.is("const") || (isTypeStart(t) && !peek(1).is("(") && !peek(1).is("{") && !peek(1).is("::") || (t.is("std") && isTypeStart(peek(2))))) {
            VarDecl d = varDecl();
            expect(";");
            return d;
        }
        ExprStmt s = at(new ExprStmt(), t);
        s.e = expr();
        expect(";");
        return s;
    }

    private VarDecl varDecl() {
        Tok t = cur();
        VarDecl d = at(new VarDecl(), t);
        d.keep = eat("pastreaza");
        d.isConst = eat("const");
        d.type = type();
        d.isRef = eat("&");
        if (d.type == Type.VOID) throw err(t, "O variabilă nu poate fi void");
        do {
            String name = ident();
            Expr init = null;
            if (eat("=")) init = d.isRef ? expr() : assignmentOrInit();
            else if (at("(") && !d.isRef) { // point p(1, 2);
                Tok pt = next();
                Call c = new Call();
                c.line = pt.line; c.col = pt.col;
                c.ctor = d.type;
                if (!at(")")) do { c.args.add(expr()); } while (eat(","));
                expect(")");
                init = c;
            } else if (at("{")) init = initList();
            d.names.add(name);
            d.inits.add(init);
        } while (eat(","));
        return d;
    }

    private Expr assignmentOrInit() { return at("{") ? initList() : expr(); }

    private Stmt forStmt() {
        Tok t = next();
        expect("(");
        // range-for: for (type [&] name : seq)
        if (isTypeStart(cur())) {
            int save = p;
            Type ty = null;
            boolean ref = false;
            String name = null;
            try {
                eat("const");
                ty = type();
                ref = eat("&");
                if (cur().k == K.IDENT && peek(1).is(":")) { name = next().s; next(); }
            } catch (ScriptError e) { name = null; }
            if (name != null) {
                ForEach f = at(new ForEach(), t);
                f.type = ty; f.isRef = ref; f.name = name;
                f.seq = expr();
                expect(")");
                f.body = statement();
                return f;
            }
            p = save;
        }
        For f = at(new For(), t);
        if (!at(";")) {
            if (isTypeStart(cur()) && !peek(1).is("(")) f.init = varDecl();
            else { ExprStmt es = at(new ExprStmt(), cur()); es.e = expr(); f.init = es; }
        }
        expect(";");
        if (!at(";")) f.c = expr();
        expect(";");
        if (!at(")")) f.step = expr();
        expect(")");
        f.body = statement();
        return f;
    }

    // ------------------------------------------------------------------ expressions
    private <E extends Expr> E at(E e, Tok t) { e.line = t.line; e.col = t.col; return e; }

    Expr expr() { return assignment(); }

    private static final java.util.Set<String> ASSIGN = new java.util.HashSet<String>(java.util.Arrays.asList("=", "+=", "-=", "*=", "/=", "%="));

    private Expr assignment() {
        Expr l = ternary();
        Tok t = cur();
        if (t.k == K.OP && ASSIGN.contains(t.s)) {
            next();
            Assign a = at(new Assign(), t);
            a.op = t.s;
            a.target = l;
            a.value = t.s.equals("=") && at("{") ? initList() : assignment();
            return a;
        }
        return l;
    }

    private Expr ternary() {
        Expr c = binary(0);
        if (at("?")) {
            Tok t = next();
            Cond e = at(new Cond(), t);
            e.c = c;
            e.a = assignment();
            expect(":");
            e.b = assignment();
            return e;
        }
        return c;
    }

    private static final String[][] LEVELS = {{"||"}, {"&&"}, {"==", "!="}, {"<", "<=", ">", ">="}, {"+", "-"}, {"*", "/", "%"}};

    private Expr binary(int level) {
        if (level >= LEVELS.length) return unary();
        Expr l = binary(level + 1);
        while (true) {
            Tok t = cur();
            String op = null;
            if (t.k == K.OP) for (String o : LEVELS[level]) if (t.s.equals(o)) op = o;
            if (op == null && level == 0 && t.is("or")) op = "||";
            if (op == null && level == 1 && t.is("and")) op = "&&";
            if (op == null) return l;
            next();
            Binary b = at(new Binary(), t);
            b.op = op;
            b.l = l;
            b.r = binary(level + 1);
            l = b;
        }
    }

    private Expr unary() {
        Tok t = cur();
        if (t.k == K.OP && (t.s.equals("!") || t.s.equals("-") || t.s.equals("+") || t.s.equals("++") || t.s.equals("--")) || t.is("not")) {
            next();
            Unary u = at(new Unary(), t);
            u.op = t.is("not") ? "!" : t.s;
            u.e = unary();
            return u;
        }
        return postfix(primary());
    }

    private Expr postfix(Expr e) {
        while (true) {
            Tok t = cur();
            if (t.is("[")) {
                next();
                Index ix = at(new Index(), t);
                ix.target = e;
                ix.index = expr();
                expect("]");
                e = ix;
            } else if (t.is(".") || t.is("->")) {
                next();
                Tok nt = cur();
                String name = ident();
                if (at("(")) {
                    Call c = at(new Call(), nt);
                    c.name = name;
                    c.receiver = e;
                    args(c);
                    e = c;
                } else {
                    Member m = at(new Member(), nt);
                    m.target = e;
                    m.name = name;
                    e = m;
                }
            } else if (t.is("++") || t.is("--")) {
                next();
                Unary u = at(new Unary(), t);
                u.op = t.s;
                u.e = e;
                u.postfix = true;
                e = u;
            } else {
                return e;
            }
        }
    }

    private void args(Call c) {
        expect("(");
        if (!at(")")) do { c.args.add(at("{") ? initList() : expr()); } while (eat(","));
        expect(")");
    }

    private InitList initList() {
        Tok t = expect("{");
        InitList l = at(new InitList(), t);
        if (!at("}")) do { if (at("}")) break; l.items.add(at("{") ? initList() : expr()); } while (eat(","));
        expect("}");
        return l;
    }

    private Expr primary() {
        Tok t = cur();
        switch (t.k) {
            case INT: { next(); Lit l = at(new Lit(), t); l.v = t.iv; l.type = Type.INT; return l; }
            case DOUBLE: { next(); Lit l = at(new Lit(), t); l.v = t.dv; l.type = Type.DOUBLE; return l; }
            case STRING: { next(); Lit l = at(new Lit(), t); l.v = t.s; l.type = Type.STRING; return l; }
            case COLOR: { next(); Lit l = at(new Lit(), t); l.v = (int) t.iv; l.type = Type.COLOR; return l; }
            case EOF: throw err(t, "Textul se termină prea devreme");
            default: break;
        }
        if (t.is("(")) {
            next();
            Expr e = expr();
            expect(")");
            return e;
        }
        if (t.is("{")) return initList();
        if (t.is("true") || t.is("adevarat")) { next(); Lit l = at(new Lit(), t); l.v = Boolean.TRUE; l.type = Type.BOOL; return l; }
        if (t.is("false") || t.is("fals")) { next(); Lit l = at(new Lit(), t); l.v = Boolean.FALSE; l.type = Type.BOOL; return l; }
        if (t.is("endl")) { next(); Lit l = at(new Lit(), t); l.v = "\n"; l.type = Type.STRING; return l; }
        // constructors and casts: point(1, 2), int(x), vector<int>{1, 2}, Tinta{...}
        if (isTypeStart(t) && !t.is("auto") && !t.is("void")) {
            Type ty = type();
            Call c = at(new Call(), t);
            c.ctor = ty;
            if (at("{")) {
                InitList l = initList();
                c.args.addAll(l.items);
                c.name = "{}";
            } else {
                args(c);
            }
            return c;
        }
        if (t.k == K.IDENT) {
            if (KEYWORDS.contains(t.s) && !t.s.equals("std")) throw err(t, "„" + t.s + "” nu se poate folosi aici");
            next();
            if (t.s.equals("std") && eat("::")) return primary();
            if (at("(")) {
                Call c = at(new Call(), t);
                c.name = t.s;
                args(c);
                return c;
            }
            Var v = at(new Var(), t);
            v.name = t.s;
            return v;
        }
        throw err(t, "Nu înțeleg " + t + " aici");
    }
}
