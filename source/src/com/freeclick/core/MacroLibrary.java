package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lookup of macros by id (used for combination mode). */
public interface MacroLibrary {
    Macro find(String id);

    /** Simple in-memory library (tests, and snapshots taken before playback). */
    final class InMemory implements MacroLibrary {
        private final Map<String, Macro> byId = new HashMap<String, Macro>();

        public InMemory add(Macro m) { byId.put(m.id, m); return this; }
        @Override public Macro find(String id) { return id == null ? null : byId.get(id); }
        public List<Macro> all() { return new ArrayList<Macro>(byId.values()); }
    }
}
