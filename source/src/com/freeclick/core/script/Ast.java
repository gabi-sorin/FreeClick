package com.freeclick.core.script;

import java.util.ArrayList;
import java.util.List;

/** Syntax tree of a script, filled in by Parser and annotated (types, slots, builtins) by Checker. */
final class Ast {
    private Ast() {}

    // ------------------------------------------------------------------ types
    static final class StructDef {
        final String name;
        final List<String> fields = new ArrayList<String>();
        final List<Type> types = new ArrayList<Type>();
        final boolean builtin;
        StructDef(String name, boolean builtin) { this.name = name; this.builtin = builtin; }
        int field(String n) { return fields.indexOf(n); }
        StructDef add(String n, Type t) { fields.add(n); types.add(t); return this; }
    }

    static final class Type {
        enum K { INT, DOUBLE, BOOL, STRING, COLOR, VOID, VECTOR, PAIR, STRUCT, AUTO, ERROR }
        final K k;
        final Type a, b;       // vector element / pair parts
        final StructDef st;    // STRUCT
        private Type(K k, Type a, Type b, StructDef st) { this.k = k; this.a = a; this.b = b; this.st = st; }

        static final Type INT = new Type(K.INT, null, null, null), DOUBLE = new Type(K.DOUBLE, null, null, null),
                BOOL = new Type(K.BOOL, null, null, null), STRING = new Type(K.STRING, null, null, null),
                COLOR = new Type(K.COLOR, null, null, null), VOID = new Type(K.VOID, null, null, null),
                AUTO = new Type(K.AUTO, null, null, null), ERROR = new Type(K.ERROR, null, null, null);
        static final StructDef POINT_DEF = new StructDef("point", true).add("x", INT).add("y", INT);
        static final StructDef RECT_DEF = new StructDef("rect", true).add("x1", INT).add("y1", INT).add("x2", INT).add("y2", INT);
        /** A step of the clicker list (the 'pasi' vector). */
        static final StructDef PAS_DEF = new StructDef("Pas", true).add("tip", STRING).add("x", INT).add("y", INT).add("x2", INT)
                .add("y2", INT).add("durata", INT).add("intarziere", INT).add("repetari", INT).add("activ", BOOL).add("nota", STRING);
        static final Type POINT = new Type(K.STRUCT, null, null, POINT_DEF), RECT = new Type(K.STRUCT, null, null, RECT_DEF),
                PAS = new Type(K.STRUCT, null, null, PAS_DEF);

        static Type vector(Type e) { return new Type(K.VECTOR, e, null, null); }
        static Type pair(Type x, Type y) { return new Type(K.PAIR, x, y, null); }
        static Type struct(StructDef d) {
            if (d == POINT_DEF) return POINT;
            if (d == RECT_DEF) return RECT;
            if (d == PAS_DEF) return PAS;
            return new Type(K.STRUCT, null, null, d);
        }

        boolean numeric() { return k == K.INT || k == K.DOUBLE; }
        boolean isStruct(StructDef d) { return k == K.STRUCT && st == d; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Type)) return false;
            Type t = (Type) o;
            if (t.k != k) return false;
            if (k == K.VECTOR) return a.equals(t.a);
            if (k == K.PAIR) return a.equals(t.a) && b.equals(t.b);
            if (k == K.STRUCT) return st == t.st;
            return true;
        }

        @Override public int hashCode() { return k.hashCode(); }

        @Override public String toString() {
            switch (k) {
                case INT: return "int";
                case DOUBLE: return "double";
                case BOOL: return "bool";
                case STRING: return "string";
                case COLOR: return "color";
                case VOID: return "void";
                case VECTOR: return "vector<" + a + ">";
                case PAIR: return "pair<" + a + ", " + b + ">";
                case STRUCT: return st.name;
                case AUTO: return "auto";
                default: return "?";
            }
        }
    }

    // ------------------------------------------------------------------ expressions
    abstract static class Expr {
        int line, col;
        Type type = Type.ERROR;
    }

    static final class Lit extends Expr { Object v; }
    static final class Var extends Expr {
        String name;
        Sym sym; // resolved
    }
    static final class Unary extends Expr { String op; Expr e; boolean postfix; }
    static final class Binary extends Expr { String op; Expr l, r; }
    static final class Assign extends Expr { String op; Expr target, value; }
    static final class Cond extends Expr { Expr c, a, b; }
    static final class Index extends Expr { Expr target, index; boolean stringIndex; }
    static final class Member extends Expr {
        Expr target; String name;
        int field = -1;   // struct field
        int special;      // color.r/g/b, pair.first/second
    }
    /** f(args), obj.method(args), type(args) (constructors and casts). */
    static final class Call extends Expr {
        String name;
        Expr receiver;          // obj.method(...)
        Type ctor;              // point(1, 2), int(x), Tinta{...}
        final List<Expr> args = new ArrayList<Expr>();
        Func func;              // user function
        int builtin = -1;       // builtin id
    }
    /** {a, b, c}: vector / pair / struct literal, typed from its context. */
    static final class InitList extends Expr { final List<Expr> items = new ArrayList<Expr>(); }

    // ------------------------------------------------------------------ statements
    abstract static class Stmt { int line, col; }
    static final class Block extends Stmt { final List<Stmt> body = new ArrayList<Stmt>(); }
    static final class ExprStmt extends Stmt { Expr e; }
    static final class VarDecl extends Stmt {
        Type type; boolean isConst, isRef, keep;
        final List<String> names = new ArrayList<String>();
        final List<Expr> inits = new ArrayList<Expr>();
        final List<Sym> syms = new ArrayList<Sym>();
    }
    static final class If extends Stmt { Expr c; Stmt a, b; }
    static final class While extends Stmt { Expr c; Stmt body; boolean doWhile; }
    static final class For extends Stmt { Stmt init; Expr c; Expr step; Stmt body; }
    static final class ForEach extends Stmt { Type type; boolean isRef; String name; Sym sym; Expr seq; Stmt body; }
    static final class Return extends Stmt { Expr e; }
    static final class Break extends Stmt { }
    /** switch (e) { case v: … default: … } — labels point into one list of statements (C++ fall-through). */
    static final class Switch extends Stmt {
        Expr e;
        final List<Expr> values = new ArrayList<Expr>();   // one per "case"
        final List<Integer> targets = new ArrayList<Integer>(); // where each case starts in body
        int defaultTarget = -1;
        final List<Stmt> body = new ArrayList<Stmt>();
    }
    static final class Continue extends Stmt { }
    /** 2.5: timeout (ms) { … } else { … }: the body is left when the time is up, then else runs. */
    static final class Timeout extends Stmt { Expr ms; Stmt body, orElse; }
    /** 2.5: try { … } catch (string e) { … }: a mistake while running goes to catch with its message. */
    static final class Try extends Stmt { Stmt body, handler; String name; Sym sym; }
    static final class Print extends Stmt { final List<Expr> parts = new ArrayList<Expr>(); }
    static final class Empty extends Stmt { }

    // ------------------------------------------------------------------ program
    /** A variable: global (slot in the globals array) or local (slot in its function's frame). */
    static final class Sym {
        String name; Type type; boolean global, isConst, isRef, keep; int slot; int line;
    }

    static final class Param { Type type; boolean isRef, isConst; String name; Sym sym; }

    static final class Func {
        String name; Type ret; final List<Param> params = new ArrayList<Param>(); Block body; int line, col;
        int slots; // frame size
    }

    /** cand (cond) { }, la_fiecare(ms) { }, la_ora("22:00") { } */
    static final class Event {
        static final int WHEN = 0, EVERY = 1, AT = 2;
        int kind; Expr arg; Block body; int line; int slots;
    }

    static final class Program {
        final List<StructDef> structs = new ArrayList<StructDef>();
        final java.util.Map<String, Func> funcs = new java.util.LinkedHashMap<String, Func>();
        final List<Stmt> top = new ArrayList<Stmt>();     // global declarations and statements, in order
        final List<Event> events = new ArrayList<Event>();
        int globals;   // number of global slots
        int topSlots;  // frame size for the top-level statements' block locals
        Func main;
    }
}
