package com.freeclick.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Tiny test runner (no JUnit available offline): runs every static void test*() method. */
public final class T {
    static int passed, failed;

    public static void eq(Object expected, Object actual, String what) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
    }

    public static void eq(Object expected, Object actual) { eq(expected, actual, "value"); }

    public static void eq(long expected, long actual, String what) {
        if (expected != actual) throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
    }

    public static void near(double expected, double actual, double tol, String what) {
        if (Math.abs(expected - actual) > tol)
            throw new AssertionError(what + ": expected ~" + expected + " but was " + actual);
    }

    public static void ok(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
    }

    public static void throwsAny(Runnable r, String what) {
        try { r.run(); } catch (RuntimeException e) { return; }
        throw new AssertionError(what + ": expected an exception");
    }

    public static void main(String[] args) throws Exception {
        for (String cls : args) {
            Class<?> c = Class.forName(cls);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().startsWith("test") || !Modifier.isStatic(m.getModifiers())) continue;
                try {
                    m.setAccessible(true);
                    m.invoke(null);
                    passed++;
                    System.out.println("  PASS " + c.getSimpleName() + "." + m.getName());
                } catch (java.lang.reflect.InvocationTargetException e) {
                    failed++;
                    System.out.println("  FAIL " + c.getSimpleName() + "." + m.getName() + " -> " + e.getCause());
                    StackTraceElement[] st = e.getCause().getStackTrace();
                    for (int i = 0; i < Math.min(4, st.length); i++) System.out.println("        at " + st[i]);
                }
            }
        }
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
