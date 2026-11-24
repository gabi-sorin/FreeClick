package com.freeclick.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.freeclick.core.FontBook;
import com.freeclick.core.TextReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The letters the text reader knows: drawn once from this phone's own fonts (regular, bold,
 * condensed, medium, serif, monospace) the first time something is read, plus the letters the
 * user taught, kept in files/fonts/learned.json (part of backups). Reading happens on the
 * matcher thread; drawing the references takes a fraction of a second, once.
 */
final class Letters {
    static final String FILE = "fonts/learned.json";

    private final File file;
    private final FontBook book = new FontBook();
    private volatile boolean refsReady;
    private int savedVersion;

    Letters(Context c) {
        file = new File(c.getFilesDir(), FILE);
        reload();
    }

    File file() { return file; }

    /** Read the taught letters from the file again (after a restore). */
    synchronized void reload() {
        try {
            book.loadLearned(file.exists() ? readAll(file) : null);
        } catch (RuntimeException | IOException e) {
            book.loadLearned(null); // a broken file is ignored (the letters can be taught again)
        }
        savedVersion = book.version();
    }

    /** The book with the reference letters ready (call on a background thread). */
    FontBook book() {
        if (!refsReady) drawReferences();
        return book;
    }

    /** Just the taught letters, without drawing anything (for counts in the settings). */
    FontBook bookNoDraw() { return book; }

    private synchronized void drawReferences() {
        if (refsReady) return;
        book.clearReferences();
        Typeface[] faces = {
                Typeface.DEFAULT, Typeface.DEFAULT_BOLD,
                Typeface.create("sans-serif-condensed", Typeface.NORMAL),
                Typeface.create("sans-serif-medium", Typeface.NORMAL),
                Typeface.SERIF, Typeface.MONOSPACE};
        String chars = TextReader.TEXT_CHARS;
        for (Typeface tf : faces) {
            for (int px : new int[]{18, 44}) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setTypeface(tf);
                p.setTextSize(px);
                p.setColor(Color.BLACK);
                Rect hb = new Rect();
                p.getTextBounds("H", 0, 1, hb);
                int capH = Math.max(1, hb.height());
                int w = px * 3, h = px * 3, base = px * 2;
                Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(bm);
                int[] argb = new int[w * h];
                for (int i = 0; i < chars.length(); i++) {
                    bm.eraseColor(Color.WHITE);
                    cv.drawText(chars, i, i + 1, px / 2f, base, p);
                    bm.getPixels(argb, 0, w, 0, 0, w, h);
                    book.addReference(chars.charAt(i), argb, w, h, base, capH);
                }
                bm.recycle();
            }
        }
        refsReady = true;
    }

    /** Save the taught letters if they changed (atomic: a crash never leaves half a file). */
    synchronized void saveIfChanged() {
        if (book.version() == savedVersion) return;
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(file.getPath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(book.toJson().getBytes("UTF-8"));
                out.getFD().sync();
            } finally {
                out.close();
            }
            if (!tmp.renameTo(file)) { if (!file.delete() || !tmp.renameTo(file)) return; }
            savedVersion = book.version();
        } catch (IOException ignored) { }
    }

    static String readAll(File f) throws IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
