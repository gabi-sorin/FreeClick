package com.freeclick.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Draft files: atomic writes and migration of drafts from older versions. */
public final class StorageTests {
    static File tempDir() {
        File d = new File(System.getProperty("java.io.tmpdir"), "fc-test-" + System.nanoTime());
        d.mkdirs();
        return d;
    }

    static final class MapLegacy implements DraftFiles.Legacy {
        final Map<String, String> m = new HashMap<String, String>();
        public String get(String k) { return m.get(k); }
        public void remove(String k) { m.remove(k); }
    }

    static void testAtomicWriteReadOverwrite() throws Exception {
        File d = new File(tempDir(), "drafts");
        DraftFiles f = new DraftFiles(d, null);
        T.ok(f.read("manual") == null, "nothing yet");
        T.ok(f.write("manual", "{\"a\":\"ăîșț\"}"), "written (dir created)");
        T.eq("{\"a\":\"ăîșț\"}", f.read("manual"), "unicode round trip");
        T.ok(f.write("manual", "{}"), "overwrite");
        T.eq("{}", f.read("manual"), "new content");
        T.ok(!new File(d, "manual.json.tmp").exists(), "no temp file left behind");
    }

    static void testMigratesDraftsFromOlderVersions() throws Exception {
        File d = tempDir();
        MapLegacy legacy = new MapLegacy();
        String old = MacroCodec.toJson(new Macro("din versiunea 1.4"));
        legacy.m.put("record", old);
        DraftFiles f = new DraftFiles(d, legacy);
        T.eq(old, f.read("record"), "old draft returned");
        T.ok(new File(d, "record.json").exists(), "moved to a file");
        T.ok(legacy.m.isEmpty(), "removed from the old place after a successful copy");
        T.eq(old, new DraftFiles(d, legacy).read("record"), "still there after a restart");
        // a file always wins over a stale legacy copy
        legacy.m.put("record", "{\"stale\":true}");
        T.eq(old, f.read("record"), "file preferred");
    }

    static void testWriteFailureKeepsLegacy() {
        File blocker = new File(tempDir(), "not-a-dir");
        T.ok(DraftFiles.writeAtomic(blocker, "x"), "plain file created");
        MapLegacy legacy = new MapLegacy();
        legacy.m.put("manual", "{\"keep\":1}");
        DraftFiles f = new DraftFiles(blocker, legacy); // dir path is a file: writes fail
        T.eq("{\"keep\":1}", f.read("manual"), "old draft still readable");
        T.eq("{\"keep\":1}", legacy.m.get("manual"), "not removed when it could not be copied");
        T.ok(!f.write("manual", "{}"), "write reports failure (caller keeps a fallback copy)");
    }
}
