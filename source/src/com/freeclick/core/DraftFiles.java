package com.freeclick.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Unsaved work of each mode as files/drafts/<mode>.json, written atomically
 * (temp file + rename, never a half-written draft). Drafts that older versions
 * kept elsewhere (the "legacy" store) are moved here on first read.
 */
public final class DraftFiles {
    public interface Legacy {
        String get(String kind);
        void remove(String kind);
    }

    private final File dir;
    private final Legacy legacy;

    public DraftFiles(File dir, Legacy legacy) {
        this.dir = dir;
        this.legacy = legacy;
    }

    public synchronized String read(String kind) {
        File f = new File(dir, safe(kind) + ".json");
        if (f.exists()) {
            try {
                return readText(f);
            } catch (IOException ignored) {
                // fall back to the legacy copy if there is one
            }
        }
        String old = legacy == null ? null : legacy.get(kind);
        if (old != null && write(kind, old)) legacy.remove(kind);
        return old;
    }

    public synchronized boolean write(String kind, String json) {
        return writeAtomic(new File(dir, safe(kind) + ".json"), json);
    }

    static String safe(String kind) { return kind.replaceAll("[^A-Za-z0-9_-]", "_"); }

    /** Write text so that readers see either the old or the new content, never a mix. */
    public static boolean writeAtomic(File dst, String text) {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        File tmp = new File(dst.getPath() + ".tmp");
        try {
            FileOutputStream os = new FileOutputStream(tmp);
            try {
                os.write(text.getBytes("UTF-8"));
                os.getFD().sync();
            } finally {
                os.close();
            }
            if (tmp.renameTo(dst)) return true;
            return dst.delete() && tmp.renameTo(dst);
        } catch (IOException e) {
            return false;
        }
    }

    public static String readText(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
