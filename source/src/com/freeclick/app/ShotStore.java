package com.freeclick.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Screenshots of the game kept with a script, for the code editor's picture pane:
 * files/screens/<script id>/index.json + <shot id>.png (PNG: colours stay exact).
 * Kept apart from the scripts' JSON, so a script file stays small; part of the backup.
 */
final class ShotStore {
    static final int MAX_PER_SCRIPT = 12;
    static final String DIR = "screens";

    static final class Shot {
        String id;
        String name;
        int w, h;
    }

    private final File root;

    ShotStore(Context c) { root = new File(c.getFilesDir(), DIR); }

    File dir(String scriptId) { return new File(root, MacroRepository.safeName(scriptId)); }

    private File index(String scriptId) { return new File(dir(scriptId), "index.json"); }

    File file(String scriptId, Shot s) { return new File(dir(scriptId), MacroRepository.safeName(s.id) + ".png"); }

    synchronized List<Shot> list(String scriptId) {
        List<Shot> out = new ArrayList<Shot>();
        File f = index(scriptId);
        if (!f.isFile()) return out;
        try {
            JSONArray a = new JSONArray(Letters.readAll(f));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Shot s = new Shot();
                s.id = o.getString("id");
                s.name = o.optString("name", "captură");
                s.w = o.optInt("w");
                s.h = o.optInt("h");
                if (file(scriptId, s).isFile()) out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void write(String scriptId, List<Shot> shots) throws IOException {
        JSONArray a = new JSONArray();
        try {
            for (Shot s : shots) a.put(new JSONObject().put("id", s.id).put("name", s.name).put("w", s.w).put("h", s.h));
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
        writeAtomic(index(scriptId), a.toString().getBytes("UTF-8"));
    }

    private static void writeAtomic(File f, byte[] data) throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("nu pot crea " + dir);
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try { out.write(data); } finally { out.close(); }
        if (!tmp.renameTo(f)) {
            if (!f.delete() || !tmp.renameTo(f)) throw new IOException("nu pot scrie " + f);
        }
    }

    /** Saves a new screenshot (PNG) at the end of the script's list. */
    synchronized Shot add(String scriptId, Bitmap bmp, String name) throws IOException {
        List<Shot> shots = list(scriptId);
        if (shots.size() >= MAX_PER_SCRIPT) throw new IOException("maximum " + MAX_PER_SCRIPT + " capturi pe script: șterge una");
        Shot s = new Shot();
        s.id = UUID.randomUUID().toString();
        s.name = name;
        s.w = bmp.getWidth();
        s.h = bmp.getHeight();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)) throw new IOException("nu pot salva poza");
        writeAtomic(file(scriptId, s), bos.toByteArray());
        shots.add(s);
        write(scriptId, shots);
        return s;
    }

    synchronized void rename(String scriptId, String shotId, String name) throws IOException {
        List<Shot> shots = list(scriptId);
        for (Shot s : shots) if (s.id.equals(shotId)) s.name = name;
        write(scriptId, shots);
    }

    synchronized void delete(String scriptId, String shotId) throws IOException {
        List<Shot> shots = list(scriptId);
        List<Shot> keep = new ArrayList<Shot>();
        for (Shot s : shots) {
            if (s.id.equals(shotId)) { File f = file(scriptId, s); if (f.exists()) f.delete(); }
            else keep.add(s);
        }
        write(scriptId, keep);
    }

    /** When a script is deleted. */
    synchronized void deleteAll(String scriptId) {
        File d = dir(scriptId);
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        d.delete();
    }

    /** When a script is duplicated: the copy gets the same pictures. */
    synchronized void copyAll(String fromId, String toId) {
        File from = dir(fromId);
        File[] fs = from.listFiles();
        if (fs == null) return;
        File to = dir(toId);
        if (!to.isDirectory() && !to.mkdirs()) return;
        for (File f : fs) {
            if (f.getName().endsWith(".tmp")) continue;
            try { writeAtomic(new File(to, f.getName()), readBytes(f)); } catch (IOException ignored) { }
        }
    }

    Bitmap load(String scriptId, Shot s) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inScaled = false;
        try {
            return BitmapFactory.decodeFile(file(scriptId, s).getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ backup
    /** Every file to put in a backup: "screens/<script>/<file>" → file. */
    synchronized List<File> backupFiles() {
        List<File> out = new ArrayList<File>();
        File[] dirs = root.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            File[] fs = d.listFiles();
            if (fs == null) continue;
            for (File f : fs) if (!f.getName().endsWith(".tmp")) out.add(f);
        }
        return out;
    }

    String backupName(File f) { return DIR + "/" + f.getParentFile().getName() + "/" + f.getName(); }

    /** Restores one backup entry; only for scripts that exist and have no pictures yet (never overwrites). */
    synchronized void restore(String entry, byte[] data, java.util.Set<String> existingScripts, java.util.Set<String> touched) {
        String[] parts = entry.split("/");
        if (parts.length != 3 || !DIR.equals(parts[0])) return;
        String script = parts[1], name = parts[2];
        if (!script.matches("[A-Za-z0-9_-]+") || !name.matches("[A-Za-z0-9_.-]+") || name.contains("..")) return;
        if (!existingScripts.contains(script)) return;
        File d = new File(root, script);
        if (!touched.contains(script) && new File(d, "index.json").isFile()) return; // has its own pictures: keep them
        touched.add(script);
        try { writeAtomic(new File(d, name), data); } catch (IOException ignored) { }
    }

    private static byte[] readBytes(File f) throws IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }
}
