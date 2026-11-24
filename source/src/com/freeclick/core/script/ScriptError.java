package com.freeclick.core.script;

/** A mistake in a script (found before running, or while running), with its line (1-based) and column. */
public final class ScriptError extends RuntimeException {
    public final int line, col;
    /** True if found while running (a division by 0, an index past the end...), false if found before. */
    public boolean runtime;

    public ScriptError(String message, int line, int col) {
        super(message);
        this.line = line;
        this.col = col;
    }

    /** "Linia 7: …" */
    public String describe() {
        return (line > 0 ? "Linia " + line + ": " : "") + getMessage();
    }
}
