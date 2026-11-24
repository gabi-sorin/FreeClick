package com.freeclick.app;

import android.content.Context;
import android.util.Log;

import com.freeclick.core.Macro;
import com.freeclick.core.MacroCodec;
import com.freeclick.core.MacroLibrary;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saves macros as one JSON file each in the app's private storage.
 * Writes are atomic (temp file + rename) so a crash never leaves a half file.
 * All methods are synchronized; macro files are small so I/O on the caller's
 * thread is fine.
 */
public final class MacroRepository implements MacroLibrary {
    private static final String TAG = "FreeClick";
    private final File dir;
    private final Map<String, Macro> cache = new HashMap<String, Macro>();
    private boolean loaded;
    /** False if some saved file could not be read (then no picture may be deleted as "unused"). */
    private boolean allReadable = true;

    public interface ChangeListener { void onMacrosChanged(); }
    private final List<ChangeListener> listeners = new ArrayList<ChangeListener>();

    MacroRepository(Context c) {
        dir = new File(c.getFilesDir(), "macros");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "cannot create " + dir);
    }

    public synchronized void addListener(ChangeListener l) { listeners.add(l); }
    public synchronized void removeListener(ChangeListener l) { listeners.remove(l); }

    private void notifyChanged() {
        List<ChangeListener> copy;
        synchronized (this) { copy = new ArrayList<ChangeListener>(listeners); }
        for (ChangeListener l : copy) l.onMacrosChanged();
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".json")) continue;
            try {
                Macro m = MacroCodec.fromJson(readText(new FileInputStream(f)));
                cache.put(m.id, m);
            } catch (Exception e) {
                allReadable = false;
                Log.w(TAG, "skipping unreadable macro " + f, e);
            }
        }
    }

    /** Newest first. */
    public synchronized List<Macro> list() {
        ensureLoaded();
        List<Macro> out = new ArrayList<Macro>(cache.values());
        Collections.sort(out, new Comparator<Macro>() {
            @Override public int compare(Macro a, Macro b) { return Long.compare(b.updatedAt, a.updatedAt); }
        });
        return out;
    }

    @Override public synchronized Macro find(String id) {
        ensureLoaded();
        return id == null ? null : cache.get(id);
    }

    public synchronized Set<String> ids() {
        ensureLoaded();
        return new HashSet<String>(cache.keySet());
    }

    /** Snapshot copy for playback, so edits during playback cannot corrupt the run. */
    public synchronized MacroLibrary.InMemory snapshot() {
        ensureLoaded();
        MacroLibrary.InMemory lib = new MacroLibrary.InMemory();
        for (Macro m : cache.values()) lib.add(m);
        return lib;
    }

    public void save(Macro m) throws IOException {
        synchronized (this) {
            ensureLoaded();
            m.updatedAt = System.currentTimeMillis();
            File tmp = new File(dir, safeName(m.id) + ".json.tmp");
            File dst = new File(dir, safeName(m.id) + ".json");
            OutputStream os = new FileOutputStream(tmp);
            try {
                os.write(MacroCodec.toJson(m).getBytes("UTF-8"));
                os.flush();
                ((FileOutputStream) os).getFD().sync();
            } finally {
                os.close();
            }
            if (!tmp.renameTo(dst)) {
                if (!(dst.delete() && tmp.renameTo(dst))) throw new IOException("Nu pot salva " + dst);
            }
            cache.put(m.id, m);
        }
        notifyChanged();
    }

    public void delete(String id) {
        synchronized (this) {
            ensureLoaded();
            cache.remove(id);
            File f = new File(dir, safeName(id) + ".json");
            if (f.exists() && !f.delete()) Log.w(TAG, "cannot delete " + f);
        }
        notifyChanged();
    }

    public synchronized boolean allReadable() {
        ensureLoaded();
        return allReadable;
    }

    /** Pictures used by the image steps of every saved item. */
    public synchronized Set<String> usedImages() {
        ensureLoaded();
        Set<String> out = new HashSet<String>();
        for (Macro m : cache.values()) addImages(m, out);
        return out;
    }

    static void addImages(Macro m, Set<String> out) {
        for (com.freeclick.core.Action a : com.freeclick.core.ActionTree.all(m.actions)) {
            if (a.img != null && a.img.id != null) out.add(a.img.id);
        }
    }

    // ------------------------------------------------------------------ files in and out
    /** A shareable file: the macro, the macros it runs, and their pictures. */
    String exportBundle(Macro m, final ImageStore images) {
        return MacroCodec.exportBundle(m, this, new MacroCodec.ImageSource() {
            @Override public byte[] png(String id) { return images.read(id); }
        });
    }

    /** What an import did. */
    static final class ImportResult {
        int macros;
        int images;
        int badImages;
        /** Taught letters added from the backup. */
        int letters;
        boolean backup;
    }

    /**
     * Import a shared .json file (with its pictures) or a backup (.zip from 1.7, or .json from
     * older versions). Every macro is saved; pictures are checked and stored under their real
     * content hash (steps are pointed at it).
     */
    ImportResult importStream(InputStream raw, ImageStore images) throws IOException {
        java.io.BufferedInputStream in = new java.io.BufferedInputStream(raw);
        in.mark(4);
        int b0 = in.read(), b1 = in.read();
        in.reset();
        if (b0 == 'P' && b1 == 'K') return importZip(in, images);
        String text = readText(in);
        ImportResult r = new ImportResult();
        Map<String, byte[]> pics = MacroCodec.importImages(text);
        return finishImport(text, pics, images, r);
    }

    private ImportResult importZip(InputStream in, ImageStore images) throws IOException {
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in);
        String text = null, letters = null;
        Map<String, byte[]> pics = new HashMap<String, byte[]>();
        Map<String, byte[]> shots = new java.util.LinkedHashMap<String, byte[]>();
        long shotBytes = 0;
        try {
            java.util.zip.ZipEntry e;
            int entries = 0;
            while ((e = zin.getNextEntry()) != null) {
                if (++entries > 20000) throw new IOException("Arhivă invalidă");
                String n = e.getName();
                if ("backup.json".equals(n)) {
                    text = new String(readLimited(zin, 64L * 1024 * 1024), "UTF-8");
                } else if (Letters.FILE.equals(n)) {
                    letters = new String(readLimited(zin, 8L * 1024 * 1024), "UTF-8");
                } else if (n.startsWith("images/") && n.endsWith(".png")) {
                    String id = n.substring(7, n.length() - 4);
                    if (com.freeclick.core.ImageSpec.isSafeId(id)) pics.put(id, readLimited(zin, MacroCodec.MAX_IMAGE_BYTES));
                } else if (n.startsWith(ShotStore.DIR + "/") && shotBytes < 400L * 1024 * 1024) {
                    byte[] data = readLimited(zin, 40L * 1024 * 1024);
                    shotBytes += data.length;
                    shots.put(n, data);
                }
                zin.closeEntry();
            }
        } finally {
            zin.close();
        }
        if (text == null) throw new IOException("Arhiva nu conține un backup FreeClick");
        ImportResult r = finishImport(text, pics, images, new ImportResult());
        if (!shots.isEmpty()) { // screenshots of the code editor, for the scripts that came back
            Set<String> scripts = new java.util.HashSet<String>();
            for (Macro m : list()) if (Macro.KIND_SCRIPT.equals(m.kind)) scripts.add(safeName(m.id));
            ShotStore store = App.get().shots();
            Set<String> touched = new java.util.HashSet<String>();
            for (Map.Entry<String, byte[]> e : shots.entrySet()) store.restore(e.getKey(), e.getValue(), scripts, touched);
        }
        if (letters != null) {
            try {
                Letters l = App.get().letters();
                r.letters = l.bookNoDraw().mergeLearned(letters);
                l.saveIfChanged();
            } catch (RuntimeException badLetters) { /* the macros are restored anyway */ }
        }
        return r;
    }

    /** 📤 A pasted share code (or a FreeClick file pasted as text). Never overwrites anything. */
    ImportResult importText(String text, ImageStore images) throws IOException {
        String json = com.freeclick.core.ShareCode.decode(text);
        if (MacroCodec.isBackup(json)) throw new IOException("Un backup complet se restaurează din Setări → Date și backup");
        return finishImport(json, MacroCodec.importImages(json), images, new ImportResult());
    }

    private ImportResult finishImport(String text, Map<String, byte[]> pics, ImageStore images, ImportResult r) throws IOException {
        r.backup = MacroCodec.isBackup(text);
        List<Macro> macros = MacroCodec.importBundle(text, ids());
        Map<String, String> renamed = new HashMap<String, String>();
        for (Map.Entry<String, byte[]> e : pics.entrySet()) {
            String real = images.importPng(e.getValue());
            if (real == null) { r.badImages++; continue; }
            r.images++;
            if (!real.equals(e.getKey())) renamed.put(e.getKey(), real); // never trust a file's own name for it
        }
        MacroCodec.renameImages(macros, renamed);
        for (Macro m : macros) save(m);
        r.macros = macros.size();
        return r;
    }

    private static byte[] readLimited(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > limit) throw new IOException("Fișier prea mare");
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /**
     * Full backup as a ZIP: backup.json (every saved item) + images/<id>.png for every picture
     * they use. Streamed entry by entry, so many pictures never sit in one huge string.
     */
    void writeBackupZip(OutputStream os, ImageStore images) throws IOException {
        List<Macro> all = list();
        java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(os);
        try {
            z.putNextEntry(new java.util.zip.ZipEntry("backup.json"));
            z.write(MacroCodec.exportAll(all).getBytes("UTF-8"));
            z.closeEntry();
            Set<String> used = new java.util.TreeSet<String>();
            for (Macro m : all) addImages(m, used);
            for (String id : used) {
                byte[] png = images.read(id);
                if (png == null) continue;
                z.putNextEntry(new java.util.zip.ZipEntry("images/" + id + ".png"));
                z.write(png);
                z.closeEntry();
            }
            ShotStore shots = App.get().shots();
            for (java.io.File f : shots.backupFiles()) {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                try {
                    z.putNextEntry(new java.util.zip.ZipEntry(shots.backupName(f)));
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                    z.closeEntry();
                } finally {
                    in.close();
                }
            }
            java.io.File letters = App.get().letters().file();
            if (letters.isFile()) {
                z.putNextEntry(new java.util.zip.ZipEntry(Letters.FILE));
                z.write(Letters.readAll(letters).getBytes("UTF-8"));
                z.closeEntry();
            }
        } finally {
            z.close();
        }
    }

    /** Ids are UUIDs we generate, but imported files could contain anything: never trust them as paths. */
    static String safeName(String id) {
        return id.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    public static String readText(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 20L * 1024 * 1024) throw new IOException("Fișier prea mare");
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
