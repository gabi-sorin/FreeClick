package com.freeclick.app;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Lists, opens, shares and deletes the screen recordings made by FreeClick. */
final class VideoStore {
    static final class Item {
        String name;
        Uri uri;
        File file;
        long size;
        long dateMs;
        long durationMs;
    }

    private VideoStore() {}

    static File legacyDir(Context c) {
        File d = new File(c.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FreeClick");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<Item>();
        if (Build.VERSION.SDK_INT >= 29) {
            Uri base = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String[] cols = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DURATION};
            Cursor cur = null;
            try {
                cur = c.getContentResolver().query(base, cols,
                        MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[]{Environment.DIRECTORY_MOVIES + "/FreeClick%"},
                        MediaStore.MediaColumns.DATE_ADDED + " DESC");
                while (cur != null && cur.moveToNext()) {
                    Item it = new Item();
                    it.uri = ContentUris.withAppendedId(base, cur.getLong(0));
                    it.name = cur.getString(1);
                    it.size = cur.getLong(2);
                    it.dateMs = cur.getLong(3) * 1000;
                    it.durationMs = cur.isNull(4) ? 0 : cur.getLong(4);
                    out.add(it);
                }
            } catch (RuntimeException e) {
                Toast.makeText(c, "Nu pot citi lista: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                if (cur != null) cur.close();
            }
        } else {
            File[] files = legacyDir(c).listFiles();
            if (files != null) {
                Arrays.sort(files, new Comparator<File>() {
                    @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
                });
                for (File f : files) {
                    if (!f.getName().endsWith(".mp4")) continue;
                    Item it = new Item();
                    it.file = f;
                    it.name = f.getName();
                    it.size = f.length();
                    it.dateMs = f.lastModified();
                    it.uri = FileShareProvider.uriFor(FileShareProvider.ROOT_MOVIES, f.getName());
                    out.add(it);
                }
            }
        }
        return out;
    }

    static void open(Context c, Item it) {
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(it.uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(c, "Nu există o aplicație pentru video", Toast.LENGTH_SHORT).show();
        }
    }

    static void share(Context c, Item it) {
        Intent s = new Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, it.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(s, "Trimite filmarea").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static boolean delete(Context c, Item it) {
        try {
            if (it.file != null) return it.file.delete();
            return c.getContentResolver().delete(it.uri, null, null) > 0;
        } catch (RuntimeException e) { // e.g. video made before a reinstall belongs to the old install
            Toast.makeText(c, "Șterge-o din Galerie: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
