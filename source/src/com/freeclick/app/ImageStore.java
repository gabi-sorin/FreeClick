package com.freeclick.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.freeclick.core.ImageSpec;
import com.freeclick.core.ImageTemplate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cut-outs of image steps: files/images/<hash>.png (app-private, survives updates).
 * The name is the SHA-1 of the PNG bytes, so the same picture is stored once, names never
 * collide, and an imported file can be checked against its name. Unused pictures are removed
 * by gc(), which is careful never to delete a picture a macro or draft still needs.
 */
final class ImageStore {
    /** Cut-outs not used by anything for this long may be deleted (unsaved work may still want them). */
    static final long GRACE_MS = 24L * 3600 * 1000;

    private final File dir;
    private final Map<String, ImageTemplate> cache = new LinkedHashMap<String, ImageTemplate>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ImageTemplate> e) { return size() > 24; }
    };

    ImageStore(Context c) {
        this(new File(c.getFilesDir(), "images"));
    }

    /** Tests: a store on any folder. */
    ImageStore(File dir) {
        this.dir = dir;
    }

    File file(String id) { return new File(dir, id + ".png"); }

    boolean has(String id) { return ImageSpec.isSafeId(id) && file(id).isFile(); }

    /** Save ARGB pixels as a PNG; returns the picture's id. */
    synchronized String save(int[] argb, int w, int h) throws IOException {
        Bitmap b = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bos);
        b.recycle();
        return savePng(bos.toByteArray());
    }

    /** Store PNG bytes under their content hash (re-using an existing identical file). */
    synchronized String savePng(byte[] png) throws IOException {
        String id = hash(png);
        File f = file(id);
        if (f.isFile()) {
            f.setLastModified(System.currentTimeMillis()); // in use again: not garbage
            return id;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Nu pot crea dosarul pentru imagini");
        File tmp = new File(dir, id + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(png);
            out.getFD().sync();
        } finally {
            out.close();
        }
        if (!tmp.renameTo(f)) { tmp.delete(); throw new IOException("Nu pot salva imaginea"); }
        return id;
    }

    /**
     * A picture from an imported file: decoded first (must be a real, reasonably small image),
     * then stored under its real hash. Returns the id, or null if the data is not acceptable.
     */
    synchronized String importPng(byte[] png) {
        if (png == null || png.length == 0 || png.length > com.freeclick.core.MacroCodec.MAX_IMAGE_BYTES) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(png, 0, png.length, o);
        if (o.outWidth < 8 || o.outHeight < 8 || o.outWidth > 1024 || o.outHeight > 1024) return null;
        try {
            return savePng(png);
        } catch (IOException e) {
            return null;
        }
    }

    synchronized byte[] read(String id) {
        if (!has(id)) return null;
        File f = file(id);
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] b = new byte[(int) f.length()];
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                return off == b.length ? b : null;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** The cut-out ready for searching (cached), or null if its file is missing. */
    synchronized ImageTemplate template(String id) {
        ImageTemplate t = cache.get(id);
        if (t != null) return t;
        if (!has(id)) return null;
        Bitmap b = BitmapFactory.decodeFile(file(id).getPath());
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        t = new ImageTemplate(px, w, h);
        cache.put(id, t);
        return t;
    }

    /** A thumbnail for forms and lists (null if missing). */
    Bitmap bitmap(String id) {
        if (!has(id)) return null;
        return BitmapFactory.decodeFile(file(id).getPath());
    }

    /**
     * Delete pictures nobody uses. `used` must hold every id referenced by saved items, drafts
     * and the work in memory; pass allRead=false if any of them could not be read: then nothing
     * is deleted (a picture of an unreadable item must not disappear). Recently written files are
     * kept too (an undo step or an editor may still bring them back).
     * @return how many files were deleted.
     */
    synchronized int gc(Set<String> used, boolean allRead) {
        if (!allRead) return 0;
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        long now = System.currentTimeMillis();
        int n = 0;
        for (File f : fs) {
            String name = f.getName();
            if (name.endsWith(".tmp") && now - f.lastModified() > GRACE_MS) { if (f.delete()) n++; continue; }
            if (!name.endsWith(".png")) continue;
            String id = name.substring(0, name.length() - 4);
            if (used.contains(id) || now - f.lastModified() < GRACE_MS) continue;
            if (f.delete()) { n++; cache.remove(id); }
        }
        return n;
    }

    static String hash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i] & 0xFF));
            return sb.toString(); // 32 hex chars
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Every picture file present (for backups). */
    synchronized String[] all() {
        File[] fs = dir.listFiles();
        if (fs == null) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (File f : fs) {
            String n = f.getName();
            if (n.endsWith(".png") && ImageSpec.isSafeId(n.substring(0, n.length() - 4))) out.add(n.substring(0, n.length() - 4));
        }
        return out.toArray(new String[0]);
    }
}
