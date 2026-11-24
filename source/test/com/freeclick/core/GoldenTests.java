package com.freeclick.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Files and playback traces frozen from FreeClick 1.5 (tools/golden/GoldenGen.java, run against
 * the 1.5 source). Every newer version must open these files and play them exactly the same way,
 * except for the intentional changes listed in INTENDED (each with its reason).
 */
public final class GoldenTests {

    /** case -> {old line, new line} replacements that are deliberate behaviour changes. */
    static final Map<String, String[][]> INTENDED = new LinkedHashMap<String, String[][]>();
    static {
        // 1.6: a macro with nothing to run is refused before it starts (like a cycle), instead of
        // starting and finding an empty pass.
        INTENDED.put("empty", new String[][]{{"0 state RUNNING", null}});
        // 1.6: the time limit has its own timer, so it stops exactly at the limit (4321 ms)
        // instead of at the next step after it.
        INTENDED.put("scaled_time_limit", new String[][]{
                {"4500 state FINISHED", "4321 state FINISHED"},
                {"4500 end time-limit steps=8 failed=0 met=0 timedOut=0", "4321 end time-limit steps=8 failed=0 met=0 timedOut=0"}});
    }

    static String read(File f) throws Exception {
        Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        r.close();
        return sb.toString();
    }

    static File[] cases() {
        File dir = new File("test/golden");
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) throw new AssertionError("no golden files in " + dir.getAbsolutePath());
        java.util.Arrays.sort(fs);
        return fs;
    }

    @SuppressWarnings("unchecked")
    static void testGoldenTracesFrom15AreReproduced() throws Exception {
        StringBuilder problems = new StringBuilder();
        for (File f : cases()) {
            String name = f.getName().replace(".json", "");
            Map<String, Object> c = (Map<String, Object>) Json.parse(read(f));
            List<String> expected = new ArrayList<String>();
            for (Object o : Json.getArray(c, "trace")) expected.add((String) o);
            String[][] repl = INTENDED.get(name);
            if (repl != null) for (String[] r : repl) {
                int i = expected.indexOf(r[0]);
                if (i < 0) throw new AssertionError(name + ": intended change refers to a missing line " + r[0]);
                if (r[1] == null) expected.remove(i); else expected.set(i, r[1]);
            }
            List<String> actual = GoldenRunner.run(c);
            if (!expected.equals(actual)) {
                problems.append("\n  ").append(name).append(":");
                int n = Math.max(expected.size(), actual.size());
                int shown = 0;
                for (int i = 0; i < n && shown < 6; i++) {
                    String e = i < expected.size() ? expected.get(i) : "(none)";
                    String a = i < actual.size() ? actual.get(i) : "(none)";
                    if (!e.equals(a)) { problems.append("\n    expected: ").append(e).append("\n    actual:   ").append(a); shown++; }
                }
            }
        }
        if (problems.length() > 0) throw new AssertionError("golden traces differ:" + problems);
    }

    /** Files written by 1.5 load and re-save without losing anything. */
    @SuppressWarnings("unchecked")
    static void testGoldenFilesRoundTrip() throws Exception {
        for (File f : cases()) {
            Map<String, Object> c = (Map<String, Object>) Json.parse(read(f));
            List<Object> all = new ArrayList<Object>();
            all.add(Json.getObject(c, "macro"));
            List<Object> lib = Json.getArray(c, "library");
            if (lib != null) all.addAll(lib);
            for (Object o : all) {
                Map<String, Object> old = (Map<String, Object>) o;
                Macro m = MacroCodec.fromJson(Json.stringify(old));
                Map<String, Object> again = (Map<String, Object>) Json.parse(MacroCodec.toJson(m));
                Map<String, Object> a = new LinkedHashMap<String, Object>(old), b = new LinkedHashMap<String, Object>(again);
                a.remove("version"); b.remove("version");
                T.eq(Json.stringify(a), Json.stringify(b), f.getName() + " " + old.get("name") + " survives load+save");
            }
        }
    }
}
