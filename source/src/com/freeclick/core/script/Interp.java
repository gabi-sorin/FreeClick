package com.freeclick.core.script;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;
import com.freeclick.core.ColorMatch;
import com.freeclick.core.Expr;
import com.freeclick.core.GlobalActions;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.Numbers;
import com.freeclick.core.PlaybackEngine;
import com.freeclick.core.Stroke;
import com.freeclick.core.script.Ast.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs a checked program, statement by statement, on the script thread. Values: Long (int),
 * Double, Boolean, String, Integer (color), Obj (struct, point, rect, Pas, pair), Vec (vector).
 * Structs and vectors behave like in C++: copied when assigned or passed by value, shared
 * through & references.
 */
final class Interp {
    static final int MAX_DEPTH = 400;
    static final int MAX_SIZE = 1000000;

    /** A struct / pair / point / rect / Pas value. Pas keeps its original step in `step`. */
    static final class Obj {
        final Object[] f;
        Action step;
        Obj(int n) { f = new Object[n]; }
        Obj copy() {
            Obj o = new Obj(f.length);
            for (int i = 0; i < f.length; i++) o.f[i] = Interp.copy(f[i]);
            o.step = step;
            return o;
        }
    }

    static final class Vec {
        final ArrayList<Object> a;
        Vec() { a = new ArrayList<Object>(); }
        Vec(int n) { a = new ArrayList<Object>(n); }
        Vec copy() { Vec v = new Vec(a.size()); for (Object o : a) v.a.add(Interp.copy(o)); return v; }
    }

    /** Where a & reference points. */
    abstract static class Ref { abstract Object get(); abstract void set(Object v); }

    static final class SlotRef extends Ref {
        final Object[] arr; final int i;
        SlotRef(Object[] arr, int i) { this.arr = arr; this.i = i; }
        Object get() { return arr[i]; }
        void set(Object v) { arr[i] = v; }
    }

    static final class FieldRef extends Ref {
        final Obj o; final int i;
        FieldRef(Obj o, int i) { this.o = o; this.i = i; }
        Object get() { return o.f[i]; }
        void set(Object v) { o.f[i] = v; }
    }

    final class ElemRef extends Ref {
        final Vec v; final int i; final Ast.Expr where;
        ElemRef(Vec v, int i, Ast.Expr where) { this.v = v; this.i = i; this.where = where; }
        Object get() { check(); return v.a.get(i); }
        void set(Object x) { check(); v.a.set(i, x); }
        private void check() { if (i >= v.a.size()) throw rt(where, "Elementul " + i + " nu mai există (vectorul s-a micșorat)"); }
    }

    // control flow
    static final class BreakSignal extends RuntimeException { BreakSignal() { super(null, null, false, false); } }
    static final class ContinueSignal extends RuntimeException { ContinueSignal() { super(null, null, false, false); } }
    static final class ReturnSignal extends RuntimeException { ReturnSignal() { super(null, null, false, false); } }
    /** stop() in the script, or Stop pressed. */
    static final class StopSignal extends RuntimeException { StopSignal() { super(null, null, false, false); } }
    private static final BreakSignal BREAK = new BreakSignal();
    private static final ContinueSignal CONTINUE = new ContinueSignal();
    private static final ReturnSignal RETURN = new ReturnSignal();
    static final StopSignal STOP = new StopSignal();

    final Program prog;
    final Object[] globals;
    final ScriptHost host;
    final ScriptRunner runner;
    private Object returned;
    private int depth;
    private final StringBuilder cout = new StringBuilder();

    Interp(Program prog, ScriptHost host, ScriptRunner runner) {
        this.prog = prog;
        this.host = host;
        this.runner = runner;
        globals = new Object[Math.max(1, prog.globals)];
        // the clicker's settings, when the script declares them at the top (const int DURATA_CLICK = 50; …)
        for (Ast.Stmt st : prog.top) {
            if (!(st instanceof Ast.VarDecl)) continue;
            Ast.VarDecl d = (Ast.VarDecl) st;
            for (Ast.Sym sym : d.syms) {
                if (sym == null || !sym.global || sym.type == null) continue;
                if (sym.type.k != Ast.Type.K.INT && sym.type.k != Ast.Type.K.DOUBLE) continue;
                if (CLICK_DURATION.equals(sym.name)) clickSlot = sym.slot;
                else if (SWIPE_DURATION.equals(sym.name)) swipeSlot = sym.slot;
                else if (REPEAT_INTERVAL.equals(sym.name)) intervalSlot = sym.slot;
            }
        }
    }

    /** Names of the clicker's settings in a script (written at its top by the editor). */
    static final String CLICK_DURATION = "DURATA_CLICK", SWIPE_DURATION = "DURATA_SWIPE", REPEAT_INTERVAL = "INTERVAL_REPETARE";
    private int clickSlot = -1, swipeSlot = -1, intervalSlot = -1;

    long clickMs() { return Math.max(1, Math.min(60000, slotMs(clickSlot, com.freeclick.core.Action.DEFAULT_TAP_MS))); }
    long swipeMs() { return Math.max(1, Math.min(60000, slotMs(swipeSlot, com.freeclick.core.Action.DEFAULT_SWIPE_MS))); }
    long intervalMs() { return Math.max(0, Math.min(24L * 3600000, slotMs(intervalSlot, 0))); }

    private long slotMs(int slot, long def) {
        if (slot < 0 || slot >= globals.length) return def;
        Object v = globals[slot];
        if (v instanceof Long) return (Long) v;
        if (v instanceof Double) return Math.round((Double) v);
        return def; // not set yet
    }

    // ------------------------------------------------------------------ values
    static Object copy(Object v) {
        if (v instanceof Obj) return ((Obj) v).copy();
        if (v instanceof Vec) return ((Vec) v).copy();
        return v;
    }

    static Object defaultValue(Type t) {
        switch (t.k) {
            case INT: return 0L;
            case DOUBLE: return 0.0;
            case BOOL: return Boolean.FALSE;
            case STRING: return "";
            case COLOR: return 0;
            case VECTOR: return new Vec();
            case PAIR: { Obj o = new Obj(2); o.f[0] = defaultValue(t.a); o.f[1] = defaultValue(t.b); return o; }
            case STRUCT: {
                Obj o = new Obj(t.st.types.size());
                for (int i = 0; i < o.f.length; i++) o.f[i] = defaultValue(t.st.types.get(i));
                if (t.st == Type.PAS_DEF) { o.f[0] = "pauza"; o.f[8] = Boolean.TRUE; o.f[7] = 1L; o.step = Action.waitFor(0); }
                return o;
            }
            default: return null;
        }
    }

    ScriptError rt(Ast.Expr e, String msg) {
        ScriptError s = new ScriptError(msg, e == null ? 0 : e.line, e == null ? 0 : e.col);
        s.runtime = true;
        return s;
    }

    ScriptError rt(Stmt s, String msg) {
        ScriptError e = new ScriptError(msg, s.line, s.col);
        e.runtime = true;
        return e;
    }

    /** How a value is printed: 12, 1.5, true, #34A853, (540, 1200), [1, 2, 3]… */
    String show(Object v, Type t) {
        if (v == null) return "";
        if (v instanceof Long) return v.toString();
        if (v instanceof Double) return Expr.format(v);
        if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
        if (v instanceof Integer) return ColorMatch.toHex((Integer) v);
        if (v instanceof String) return (String) v;
        if (v instanceof Vec) {
            StringBuilder b = new StringBuilder("[");
            Vec vec = (Vec) v;
            for (int i = 0; i < vec.a.size(); i++) {
                if (i > 0) b.append(", ");
                if (i >= 50) { b.append("… (").append(vec.a.size()).append(" în total)"); break; }
                b.append(show(vec.a.get(i), t == null ? null : t.a));
            }
            return b.append(']').toString();
        }
        if (v instanceof Obj) {
            Obj o = (Obj) v;
            if (t != null && t.equals(Type.POINT)) return "(" + o.f[0] + ", " + o.f[1] + ")";
            if (t != null && t.equals(Type.RECT)) return "(" + o.f[0] + ", " + o.f[1] + ")–(" + o.f[2] + ", " + o.f[3] + ")";
            if (t != null && t.equals(Type.PAS)) return ActionFormatter.describe(toAction(o), null);
            StringBuilder b = new StringBuilder(t != null && t.k == Type.K.PAIR ? "(" : "{");
            for (int i = 0; i < o.f.length; i++) {
                if (i > 0) b.append(", ");
                Type ft = t == null ? null : t.k == Type.K.PAIR ? (i == 0 ? t.a : t.b) : t.k == Type.K.STRUCT ? t.st.types.get(i) : null;
                b.append(show(o.f[i], ft));
            }
            return b.append(t != null && t.k == Type.K.PAIR ? ")" : "}").toString();
        }
        return v.toString();
    }

    // ------------------------------------------------------------------ the clicker steps as Pas values
    static final String[] TIP = {"tap", "apasare_lunga", "dublu_tap", "swipe", "gest", "pauza", "sistem", "macro", "culoare",
            "aplicatie", "imagine", "afiseaza", "variabila", "citeste", "necunoscut"};

    static String tip(Action.Type t) {
        switch (t) {
            case TAP: return TIP[0];
            case LONG_PRESS: return TIP[1];
            case DOUBLE_TAP: return TIP[2];
            case SWIPE: return TIP[3];
            case GESTURE: return TIP[4];
            case WAIT: return TIP[5];
            case GLOBAL: return TIP[6];
            case MACRO: return TIP[7];
            case WAIT_COLOR: return TIP[8];
            case LAUNCH_APP: return TIP[9];
            case IMAGE: return TIP[10];
            case PRINT: return TIP[11];
            case SET_VAR: return TIP[12];
            case READ: return TIP[13];
            default: return TIP[14];
        }
    }

    static Obj fromAction(Action a) {
        Obj o = new Obj(Type.PAS_DEF.fields.size());
        o.f[0] = tip(a.type);
        o.f[1] = (long) Math.round(a.x); o.f[2] = (long) Math.round(a.y);
        o.f[3] = (long) Math.round(a.x2); o.f[4] = (long) Math.round(a.y2);
        o.f[5] = a.duration; o.f[6] = a.delay; o.f[7] = (long) a.repeat;
        o.f[8] = a.enabled; o.f[9] = a.label == null ? "" : a.label;
        o.step = a;
        return o;
    }

    /** The step a Pas stands for, with the script's changes applied (a fresh copy every time). */
    static Action toAction(Obj o) {
        Action a = o.step != null ? o.step.copy() : Action.waitFor(0);
        float nx = ((Long) o.f[1]), ny = ((Long) o.f[2]);
        if (a.type == Action.Type.IMAGE && a.img != null && (Math.round(a.x) != (long) (Long) o.f[1] || Math.round(a.y) != (long) (Long) o.f[2])) {
            a.img.tapDx += nx - a.x; a.img.tapDy += ny - a.y; // moving an image step's point moves where it taps
        }
        if (a.type == Action.Type.GESTURE && (Math.round(a.x) != (long) (Long) o.f[1] || Math.round(a.y) != (long) (Long) o.f[2])) {
            float dx = nx - a.x, dy = ny - a.y; // a recorded gesture moves as a whole
            List<Stroke> moved = new ArrayList<Stroke>();
            for (Stroke s : a.strokes) {
                Stroke m = new Stroke();
                for (int i = 0; i < s.size(); i++) m.add(s.x(i) + dx, s.y(i) + dy, s.times.get(i));
                moved.add(m);
            }
            a.strokes = moved;
        }
        a.x = nx; a.y = ny;
        a.x2 = (Long) o.f[3]; a.y2 = (Long) o.f[4];
        long d = Math.max(0, (Long) o.f[5]);
        if (a.type != Action.Type.GESTURE) a.duration = a.isGesture() ? Math.max(1, Math.min(60000, d)) : d;
        a.delay = Math.max(0, (Long) o.f[6]);
        a.repeat = (int) Math.max(1, Math.min(1000000, (Long) o.f[7]));
        a.enabled = (Boolean) o.f[8];
        a.label = (String) o.f[9];
        return a;
    }

    static Vec stepsVec(List<Action> steps) {
        Vec v = new Vec();
        if (steps != null) for (Action a : steps) v.a.add(fromAction(a)); // tree-ok: the top-level steps are the list
        return v;
    }

    // ------------------------------------------------------------------ running
    void initGlobals(List<Action> pasi) {
        globals[Checker.PASI_SLOT] = stepsVec(pasi);
    }

    void runTop() {
        Object[] frame = new Object[Math.max(1, prog.topSlots)];
        try {
            for (Stmt s : prog.top) exec(s, frame);
            if (prog.main != null) callFunc(prog.main, new ArrayList<Ast.Expr>(), frame, null);
        } catch (ReturnSignal r) {
            // return; at the top level ends the script
        } finally {
            flushCout();
        }
    }

    void runEvent(Event e) {
        Object[] frame = new Object[Math.max(1, e.slots)];
        try {
            exec(e.body, frame);
        } catch (ReturnSignal r) {
            // return; ends the event's block
        } catch (BreakSignal | ContinueSignal ignored) {
            // nothing to break out of
        }
    }

    boolean eventCondition(Event e) {
        Object[] frame = new Object[Math.max(1, e.slots)];
        return (Boolean) eval(e.arg, frame);
    }

    Object eventArg(Event e) { return eval(e.arg, new Object[Math.max(1, e.slots)]); }

    private void flushCout() {
        if (cout.length() > 0) { host.print(cout.toString(), PlaybackEngine.PRINT_USER); cout.setLength(0); }
    }

    // ------------------------------------------------------------------ statements
    void exec(Stmt s, Object[] fr) {
        if (!(s instanceof Block)) runner.checkpoint(s.line);
        if (s instanceof ExprStmt) { eval(((ExprStmt) s).e, fr); return; }
        if (s instanceof Block) { for (Stmt x : ((Block) s).body) exec(x, fr); return; }
        if (s instanceof VarDecl) { varDecl((VarDecl) s, fr); return; }
        if (s instanceof If) {
            If i = (If) s;
            if ((Boolean) eval(i.c, fr)) exec(i.a, fr); else if (i.b != null) exec(i.b, fr);
            return;
        }
        if (s instanceof While) {
            While w = (While) s;
            if (w.doWhile) {
                do {
                    try { exec(w.body, fr); } catch (BreakSignal b) { break; } catch (ContinueSignal c) { /* next turn */ }
                    runner.checkpoint(w.line);
                } while ((Boolean) eval(w.c, fr));
            } else {
                while ((Boolean) eval(w.c, fr)) {
                    try { exec(w.body, fr); } catch (BreakSignal b) { break; } catch (ContinueSignal c) { /* next turn */ }
                    runner.checkpoint(w.line);
                }
            }
            return;
        }
        if (s instanceof For) {
            For f = (For) s;
            if (f.init != null) exec(f.init, fr);
            while (f.c == null || (Boolean) eval(f.c, fr)) {
                try { exec(f.body, fr); } catch (BreakSignal b) { break; } catch (ContinueSignal c) { /* step */ }
                if (f.step != null) eval(f.step, fr);
                runner.checkpoint(f.line);
            }
            return;
        }
        if (s instanceof ForEach) { forEach((ForEach) s, fr); return; }
        if (s instanceof Switch) {
            Switch w = (Switch) s;
            Object v = eval(w.e, fr);
            int start = -1;
            for (int i = 0; i < w.values.size() && start < 0; i++) if (v.equals(eval(w.values.get(i), fr))) start = w.targets.get(i);
            if (start < 0) start = w.defaultTarget;
            if (start < 0) return;
            try {
                for (int k = start; k < w.body.size(); k++) exec(w.body.get(k), fr); // falls through, like C++
            } catch (BreakSignal b) { /* end of the switch */ }
            return;
        }
        if (s instanceof Timeout) {
            Timeout w = (Timeout) s;
            long ms = (Long) eval(w.ms, fr);
            int level = runner.pushDeadline(host.now() + Math.max(0, ms));
            boolean expired = false;
            try {
                exec(w.body, fr);
            } catch (ScriptRunner.TimeoutSignal t) {
                if (t.level != level) throw t; // an outer timeout ran out: it is not ours to catch
                expired = true;
            } finally {
                runner.popDeadline(level);
            }
            if (expired && w.orElse != null) exec(w.orElse, fr);
            return;
        }
        if (s instanceof Try) {
            Try w = (Try) s;
            try {
                exec(w.body, fr);
            } catch (ScriptError e) { // only mistakes while running; stop, break, return and timeouts pass
                Object[] arr = w.sym.global ? globals : fr;
                arr[w.sym.slot] = e.getMessage() == null ? "eroare" : e.getMessage();
                exec(w.handler, fr);
            }
            return;
        }
        if (s instanceof Return) {
            Return r = (Return) s;
            returned = r.e == null ? null : eval(r.e, fr);
            throw RETURN;
        }
        if (s instanceof Break) throw BREAK;
        if (s instanceof Continue) throw CONTINUE;
        if (s instanceof Print) {
            for (Ast.Expr e : ((Print) s).parts) {
                String t = show(eval(e, fr), e.type);
                for (int i = 0; i < t.length(); i++) {
                    char ch = t.charAt(i);
                    if (ch == '\n') flushLine(); else cout.append(ch);
                }
                if (cout.length() > 20000) flushLine();
            }
            return;
        }
        // Empty
    }

    private void flushLine() {
        host.print(cout.toString(), PlaybackEngine.PRINT_USER);
        cout.setLength(0);
    }

    private void varDecl(VarDecl d, Object[] fr) {
        for (int i = 0; i < d.syms.size(); i++) {
            Sym s = d.syms.get(i);
            Ast.Expr init = d.inits.get(i);
            Object[] arr = s.global ? globals : fr;
            if (s.isRef) { arr[s.slot] = ref(init, fr); continue; }
            Object kv = s.keep ? runner.kept.get(s.name) : null;
            if (kv != null && sameKind(kv, s.type)) { arr[s.slot] = kv; continue; }
            arr[s.slot] = init == null ? defaultValue(s.type) : copy(eval(init, fr));
            if (s.keep) runner.keep(s.name, arr[s.slot]);
        }
    }

    private static boolean sameKind(Object v, Type t) {
        switch (t.k) {
            case INT: return v instanceof Long;
            case DOUBLE: return v instanceof Double;
            case BOOL: return v instanceof Boolean;
            case STRING: return v instanceof String;
            case COLOR: return v instanceof Integer;
            default: return false;
        }
    }

    private void forEach(ForEach f, Object[] fr) {
        Object seq = eval(f.seq, fr);
        Object[] arr = f.sym.global ? globals : fr;
        if (seq instanceof String) {
            String str = (String) seq;
            for (int i = 0; i < str.length(); i++) {
                arr[f.sym.slot] = String.valueOf(str.charAt(i));
                try { exec(f.body, fr); } catch (BreakSignal b) { break; } catch (ContinueSignal c) { /* next */ }
            }
            return;
        }
        Vec v = (Vec) seq;
        for (int i = 0; i < v.a.size(); i++) {
            if (f.isRef) arr[f.sym.slot] = new ElemRef(v, i, f.seq);
            else arr[f.sym.slot] = convertValue(copy(v.a.get(i)), f.sym.type);
            try { exec(f.body, fr); } catch (BreakSignal b) { break; } catch (ContinueSignal c) { /* next */ }
            runner.checkpoint(f.line);
        }
    }

    private static Object convertValue(Object v, Type t) {
        if (t == Type.DOUBLE && v instanceof Long) return ((Long) v).doubleValue();
        if (t == Type.INT && v instanceof Double) return (long) (double) (Double) v;
        return v;
    }

    // ------------------------------------------------------------------ expressions
    Object eval(Ast.Expr e, Object[] fr) {
        if (e instanceof Lit) return ((Lit) e).v;
        if (e instanceof Var) return readVar((Var) e, fr);
        if (e instanceof Binary) return binary((Binary) e, fr);
        if (e instanceof Call) return call((Call) e, fr);
        if (e instanceof Member) return member((Member) e, fr);
        if (e instanceof Index) return index((Index) e, fr);
        if (e instanceof Assign) return assign((Assign) e, fr);
        if (e instanceof Unary) return unary((Unary) e, fr);
        if (e instanceof Cond) { Cond c = (Cond) e; return (Boolean) eval(c.c, fr) ? eval(c.a, fr) : eval(c.b, fr); }
        throw rt(e, "Expresie necunoscută");
    }

    private Object readVar(Var v, Object[] fr) {
        Object x = (v.sym.global ? globals : fr)[v.sym.slot];
        if (v.sym.isRef) return ((Ref) x).get();
        if (x == null) throw rt(v, "„" + v.name + "” încă nu are valoare aici");
        return x;
    }

    private Ref ref(Ast.Expr e, Object[] fr) {
        if (e instanceof Var) {
            Var v = (Var) e;
            Object[] arr = v.sym.global ? globals : fr;
            if (v.sym.isRef) return (Ref) arr[v.sym.slot];
            return new SlotRef(arr, v.sym.slot);
        }
        if (e instanceof Index) {
            Index ix = (Index) e;
            Vec v = (Vec) eval(ix.target, fr);
            int i = idx(ix, v, eval(ix.index, fr));
            return new ElemRef(v, i, e);
        }
        if (e instanceof Member) {
            Member m = (Member) e;
            return new FieldRef((Obj) eval(m.target, fr), m.field);
        }
        throw rt(e, "Aici trebuie o variabilă");
    }

    private int idx(Ast.Expr where, Vec v, Object i) {
        long k = (Long) i;
        if (k < 0 || k >= v.a.size()) {
            throw rt(where, "Poziția " + k + " nu există: vectorul are " + v.a.size() + (v.a.size() == 1 ? " element" : " elemente")
                    + (v.a.isEmpty() ? "" : " (de la 0 la " + (v.a.size() - 1) + ")"));
        }
        return (int) k;
    }

    private Object index(Index ix, Object[] fr) {
        Object t = eval(ix.target, fr);
        Object i = eval(ix.index, fr);
        if (ix.stringIndex) {
            String s = (String) t;
            long k = (Long) i;
            if (k < 0 || k >= s.length()) throw rt(ix, "Poziția " + k + " nu există în textul „" + s + "” (are " + s.length() + " caractere)");
            return String.valueOf(s.charAt((int) k));
        }
        Vec v = (Vec) t;
        return v.a.get(idx(ix, v, i));
    }

    private Object member(Member m, Object[] fr) {
        Object t = eval(m.target, fr);
        if (m.special > 0) {
            int c = (Integer) t;
            return (long) (m.special == 1 ? (c >> 16) & 255 : m.special == 2 ? (c >> 8) & 255 : c & 255);
        }
        return ((Obj) t).f[m.field];
    }

    private void store(Ast.Expr target, Object v, Object[] fr) {
        if (target instanceof Var) {
            Var var = (Var) target;
            Object[] arr = var.sym.global ? globals : fr;
            if (var.sym.isRef) ((Ref) arr[var.sym.slot]).set(v);
            else arr[var.sym.slot] = v;
            if (var.sym.keep) runner.keep(var.sym.name, v);
            return;
        }
        if (target instanceof Index) {
            Index ix = (Index) target;
            Vec vec = (Vec) eval(ix.target, fr);
            vec.a.set(idx(ix, vec, eval(ix.index, fr)), v);
            return;
        }
        if (target instanceof Member) {
            Member m = (Member) target;
            ((Obj) eval(m.target, fr)).f[m.field] = v;
            return;
        }
        throw rt(target, "Aici trebuie o variabilă");
    }

    void storeFromNative(Ast.Expr target, Object v, Object[] fr) { store(target, v, fr); }

    private Object assign(Assign a, Object[] fr) {
        Object v;
        if (a.op.equals("=")) v = copy(eval(a.value, fr));
        else {
            Object cur = eval(a.target, fr);
            Object r = eval(a.value, fr);
            String op = a.op.substring(0, 1);
            if (cur instanceof String) v = cur + show(r, a.value.type);
            else if (cur instanceof Obj) v = pointOp(op, (Obj) cur, (Obj) r);
            else {
                v = arith(a, op, cur, r);
                if (a.target.type == Type.INT && v instanceof Double) v = (long) (double) (Double) v;
            }
        }
        if (v instanceof String && ((String) v).length() > MAX_SIZE) throw rt(a, "Text prea lung (peste 1.000.000 de caractere)");
        store(a.target, v, fr);
        return v;
    }

    private Object unary(Unary u, Object[] fr) {
        if (u.op.equals("!")) return !(Boolean) eval(u.e, fr);
        if (u.op.equals("+")) return eval(u.e, fr);
        if (u.op.equals("-")) {
            Object v = eval(u.e, fr);
            if (v instanceof Long) return -(Long) v;
            if (v instanceof Double) return -(Double) v;
            Obj p = (Obj) v;
            Obj o = new Obj(2);
            o.f[0] = -(Long) p.f[0]; o.f[1] = -(Long) p.f[1];
            return o;
        }
        Object cur = eval(u.e, fr);
        long d = u.op.equals("++") ? 1 : -1;
        Object nv = cur instanceof Long ? (Object) ((Long) cur + d) : (Object) ((Double) cur + d);
        store(u.e, nv, fr);
        return u.postfix ? cur : nv;
    }

    private Object binary(Binary b, Object[] fr) {
        String op = b.op;
        if (op.equals("&&")) return (Boolean) eval(b.l, fr) && (Boolean) eval(b.r, fr);
        if (op.equals("||")) return (Boolean) eval(b.l, fr) || (Boolean) eval(b.r, fr);
        Object l = eval(b.l, fr), r = eval(b.r, fr);
        if (b.type == Type.STRING) {
            String s = show(l, b.l.type) + show(r, b.r.type);
            if (s.length() > MAX_SIZE) throw rt(b, "Text prea lung (peste 1.000.000 de caractere)");
            return s;
        }
        if (op.equals("==")) return equal(l, r);
        if (op.equals("!=")) return !equal(l, r);
        if (l instanceof String) {
            int c = ((String) l).compareTo((String) r);
            return op.equals("<") ? c < 0 : op.equals("<=") ? c <= 0 : op.equals(">") ? c > 0 : c >= 0;
        }
        if (l instanceof Obj) {
            if (r instanceof Obj) return pointOp(op, (Obj) l, (Obj) r);
            double k = (Double) r;
            if (op.equals("/") && k == 0) throw rt(b, "Împărțire la 0");
            Obj p = (Obj) l, o = new Obj(2);
            o.f[0] = Math.round(op.equals("*") ? (Long) p.f[0] * k : (Long) p.f[0] / k);
            o.f[1] = Math.round(op.equals("*") ? (Long) p.f[1] * k : (Long) p.f[1] / k);
            return o;
        }
        if (l instanceof Long && (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">="))) {
            long x = (Long) l, y = (Long) r;
            return op.equals("<") ? x < y : op.equals("<=") ? x <= y : op.equals(">") ? x > y : x >= y;
        }
        if (l instanceof Double && (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">="))) {
            double x = (Double) l, y = (Double) r;
            return op.equals("<") ? x < y : op.equals("<=") ? x <= y : op.equals(">") ? x > y : x >= y;
        }
        return arith(b, op, l, r);
    }

    private Object arith(Ast.Expr where, String op, Object l, Object r) {
        if (l instanceof Long && r instanceof Long) {
            long x = (Long) l, y = (Long) r;
            switch (op.charAt(0)) {
                case '+': return x + y;
                case '-': return x - y;
                case '*': return x * y;
                case '/': if (y == 0) throw rt(where, "Împărțire la 0"); return x / y;
                default: if (y == 0) throw rt(where, "Împărțire la 0 (la %)"); return x % y;
            }
        }
        double x = ((Number) l).doubleValue(), y = ((Number) r).doubleValue();
        switch (op.charAt(0)) {
            case '+': return x + y;
            case '-': return x - y;
            case '*': return x * y;
            case '/': if (y == 0) throw rt(where, "Împărțire la 0"); return x / y;
            default: if (y == 0) throw rt(where, "Împărțire la 0 (la %)"); return x % y;
        }
    }

    private static Obj pointOp(String op, Obj a, Obj b) {
        Obj o = new Obj(2);
        boolean plus = op.startsWith("+");
        o.f[0] = plus ? (Long) a.f[0] + (Long) b.f[0] : (Long) a.f[0] - (Long) b.f[0];
        o.f[1] = plus ? (Long) a.f[1] + (Long) b.f[1] : (Long) a.f[1] - (Long) b.f[1];
        return o;
    }

    static boolean equal(Object a, Object b) {
        if (a instanceof Obj && b instanceof Obj) {
            Obj x = (Obj) a, y = (Obj) b;
            if (x.f.length != y.f.length) return false;
            for (int i = 0; i < x.f.length; i++) if (!equal(x.f[i], y.f[i])) return false;
            return true;
        }
        if (a instanceof Vec && b instanceof Vec) {
            Vec x = (Vec) a, y = (Vec) b;
            if (x.a.size() != y.a.size()) return false;
            for (int i = 0; i < x.a.size(); i++) if (!equal(x.a.get(i), y.a.get(i))) return false;
            return true;
        }
        if (a instanceof String && b instanceof String) return a.equals(b);
        return a != null && a.equals(b);
    }

    // ------------------------------------------------------------------ calls
    private Object call(Call c, Object[] fr) {
        if (c.func != null) return callFunc(c.func, c.args, fr, c);
        if (c.builtin == Builtins.CAST) return cast(eval(c.args.get(0), fr), c.type);
        if (c.builtin == Builtins.CTOR) return construct(c, fr);
        if (c.receiver != null) return method(c, fr);
        return Natives.call(this, c, fr);
    }

    Object callFunc(Func f, List<Ast.Expr> args, Object[] fr, Call where) {
        if (depth >= MAX_DEPTH) throw rt(where, "Prea multe apeluri unul în altul (" + MAX_DEPTH + "): o funcție se cheamă la nesfârșit?");
        Object[] frame = new Object[Math.max(1, f.slots)];
        for (int i = 0; i < f.params.size(); i++) {
            Param pa = f.params.get(i);
            frame[pa.sym.slot] = pa.isRef ? ref(args.get(i), fr) : copy(eval(args.get(i), fr));
        }
        depth++;
        try {
            for (Stmt s : f.body.body) exec(s, frame);
            if (f.ret != Type.VOID) throw rt(where, "Funcția „" + f.name + "” s-a terminat fără return (trebuie să întoarcă un " + f.ret + ")");
            return null;
        } catch (ReturnSignal r) {
            Object v = returned;
            returned = null;
            return v;
        } finally {
            depth--;
        }
    }

    static Object cast(Object v, Type t) {
        if (t == Type.BOOL) {
            if (v instanceof Boolean) return v;
            return ((Number) v).doubleValue() != 0;
        }
        double d = v instanceof Boolean ? ((Boolean) v ? 1 : 0) : ((Number) v).doubleValue();
        if (t == Type.INT) {
            if (v instanceof Long) return v;
            if (Double.isNaN(d)) return 0L;
            return (long) d;
        }
        return d;
    }

    private Object construct(Call c, Object[] fr) {
        Type t = c.type;
        int n = c.args.size();
        if (t == Type.STRING) return "";
        if (t == Type.COLOR) {
            if (n == 1) {
                String s = (String) eval(c.args.get(0), fr);
                String h = s.trim().startsWith("#") ? s.trim().substring(1) : s.trim();
                if (!h.matches("[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3}")) throw rt(c, "„" + s + "” nu e o culoare #RRGGBB");
                return ColorMatch.parseHex(h);
            }
            int r = clamp255(eval(c.args.get(0), fr)), g = clamp255(eval(c.args.get(1), fr)), b = clamp255(eval(c.args.get(2), fr));
            return (r << 16) | (g << 8) | b;
        }
        if (t.k == Type.K.VECTOR) {
            Vec v = new Vec();
            if ("(n)".equals(c.name)) {
                long k = n >= 1 ? (Long) eval(c.args.get(0), fr) : 0;
                if (k < 0 || k > MAX_SIZE) throw rt(c, "Mărime de vector greșită: " + k);
                Object fill = n == 2 ? eval(c.args.get(1), fr) : null;
                for (long i = 0; i < k; i++) v.a.add(fill != null ? copy(fill) : defaultValue(t.a));
                return v;
            }
            for (Ast.Expr e : c.args) v.a.add(copy(eval(e, fr)));
            return v;
        }
        Obj o = (Obj) defaultValue(t);
        for (int i = 0; i < n; i++) o.f[i] = copy(eval(c.args.get(i), fr));
        if (t.equals(Type.PAS)) o.step = toAction(o); // a Pas built by hand: its kind comes from the fields
        return o;
    }

    private static int clamp255(Object v) { return (int) Math.max(0, Math.min(255, (Long) v)); }

    private Object method(Call c, Object[] fr) {
        Object recv = eval(c.receiver, fr);
        int n = c.args.size();
        Object[] a = new Object[n];
        for (int i = 0; i < n; i++) a[i] = eval(c.args.get(i), fr);
        switch (c.builtin) {
            case Builtins.S_SIZE: return (long) ((String) recv).length();
            case Builtins.S_EMPTY: return ((String) recv).isEmpty();
            case Builtins.S_SUBSTR: {
                String s = (String) recv;
                long pos = (Long) a[0];
                if (pos < 0 || pos > s.length()) throw rt(c, "substr: poziția " + pos + " e în afara textului (are " + s.length() + " caractere)");
                long len = n > 1 ? (Long) a[1] : s.length();
                return s.substring((int) pos, (int) Math.min(s.length(), pos + Math.max(0, len)));
            }
            case Builtins.S_FIND: return (long) ((String) recv).indexOf((String) a[0]);
            case Builtins.S_CONTAINS: return ((String) recv).toLowerCase(java.util.Locale.ROOT).contains(((String) a[0]).toLowerCase(java.util.Locale.ROOT));
            case Builtins.S_STARTS: return ((String) recv).startsWith((String) a[0]);
            case Builtins.S_ENDS: return ((String) recv).endsWith((String) a[0]);
            case Builtins.S_UPPER: return ((String) recv).toUpperCase(java.util.Locale.ROOT);
            case Builtins.S_LOWER: return ((String) recv).toLowerCase(java.util.Locale.ROOT);
            case Builtins.S_REPLACE: return ((String) a[0]).isEmpty() ? recv : ((String) recv).replace((String) a[0], (String) a[1]);
            case Builtins.S_TRIM: return ((String) recv).trim();
            case Builtins.C_HEX: return ColorMatch.toHex((Integer) recv);
            default: break;
        }
        Vec v = (Vec) recv;
        switch (c.builtin) {
            case Builtins.V_PUSH:
                if (v.a.size() >= MAX_SIZE) throw rt(c, "Vector prea mare (peste 1.000.000 de elemente)");
                v.a.add(copy(a[0]));
                return null;
            case Builtins.V_POP:
                if (v.a.isEmpty()) throw rt(c, "pop_back() pe un vector gol");
                v.a.remove(v.a.size() - 1);
                return null;
            case Builtins.V_SIZE: return (long) v.a.size();
            case Builtins.V_EMPTY: return v.a.isEmpty();
            case Builtins.V_CLEAR: v.a.clear(); return null;
            case Builtins.V_BACK: if (v.a.isEmpty()) throw rt(c, "back() pe un vector gol"); return v.a.get(v.a.size() - 1);
            case Builtins.V_FRONT: if (v.a.isEmpty()) throw rt(c, "front() pe un vector gol"); return v.a.get(0);
            case Builtins.V_ERASE: v.a.remove(idx(c, v, a[0])); return null;
            case Builtins.V_INSERT: {
                long k = (Long) a[0];
                if (k < 0 || k > v.a.size()) throw rt(c, "insert: poziția " + k + " e în afara vectorului (0 … " + v.a.size() + ")");
                if (v.a.size() >= MAX_SIZE) throw rt(c, "Vector prea mare (peste 1.000.000 de elemente)");
                v.a.add((int) k, copy(a[1]));
                return null;
            }
            case Builtins.V_CONTAINS: for (Object x : v.a) if (equal(x, a[0])) return true; return false;
            case Builtins.V_INDEX_OF: for (int i = 0; i < v.a.size(); i++) if (equal(v.a.get(i), a[0])) return (long) i; return -1L;
            default: throw rt(c, "Metodă necunoscută");
        }
    }

    // ------------------------------------------------------------------ helpers for Natives
    static Obj point(long x, long y) { Obj o = new Obj(2); o.f[0] = x; o.f[1] = y; return o; }
    static Obj rect(long x1, long y1, long x2, long y2) { Obj o = new Obj(4); o.f[0] = x1; o.f[1] = y1; o.f[2] = x2; o.f[3] = y2; return o; }

    @SuppressWarnings("unchecked")
    static void sort(Vec v) {
        Collections.sort(v.a, new java.util.Comparator<Object>() {
            public int compare(Object x, Object y) {
                if (x instanceof String) return ((String) x).compareTo((String) y);
                return Double.compare(((Number) x).doubleValue(), ((Number) y).doubleValue());
            }
        });
    }

    /** The image step called `name` in the script's steps (or anywhere in 'pasi'). */
    ImageSpec image(String name, Ast.Expr where) {
        Vec steps = (Vec) globals[Checker.PASI_SLOT];
        for (Object o : steps.a) {
            Action a = ((Obj) o).step;
            if (a != null && a.type == Action.Type.IMAGE && a.img != null && a.img.name != null && a.img.name.trim().equalsIgnoreCase(name.trim())) return a.img;
        }
        for (Object o : runner.images) {
            ImageSpec s = (ImageSpec) o;
            if (s.name != null && s.name.trim().equalsIgnoreCase(name.trim())) return s;
        }
        throw rt(where, "Nu am imaginea „" + name + "”. Decupeaz-o cu „+ Imagine” (în bara editorului) și dă-i numele ăsta.");
    }

    static String clockText(long wall) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(wall);
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND));
    }

    static int globalOf(int id) {
        switch (id) {
            case Builtins.BACK: return GlobalActions.BACK;
            case Builtins.HOME: return GlobalActions.HOME;
            case Builtins.RECENTS: return GlobalActions.RECENTS;
            default: return GlobalActions.NOTIFICATIONS;
        }
    }

    static Number num(String s) { return Numbers.parse(s); }
}
