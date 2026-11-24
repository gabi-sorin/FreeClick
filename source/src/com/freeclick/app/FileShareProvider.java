package com.freeclick.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Minimal read-only file provider (AndroidX is not used in this project).
 * content://com.freeclick.files/<root>/<file name>; roots are fixed folders
 * and names may not contain path separators, so nothing else can be read.
 */
public final class FileShareProvider extends ContentProvider {
    static final String AUTHORITY = "com.freeclick.files";
    static final String ROOT_SHARE = "share";   // exported macro files (cache)
    static final String ROOT_MOVIES = "movies"; // videos on Android < 10

    static Uri uriFor(String root, String name) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(root).appendPath(name).build();
    }

    static File shareDir(android.content.Context c) {
        File d = new File(c.getCacheDir(), "share");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    @Override public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 2) throw new FileNotFoundException("bad uri");
        String name = seg.get(1);
        if (name.contains("/") || name.contains("\\") || name.startsWith(".")) throw new FileNotFoundException("bad name");
        File dir;
        if (ROOT_SHARE.equals(seg.get(0))) dir = shareDir(getContext());
        else if (ROOT_MOVIES.equals(seg.get(0))) dir = VideoStore.legacyDir(getContext());
        else throw new FileNotFoundException("bad root");
        File f = new File(dir, name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return f;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String p = uri.getLastPathSegment();
        if (p == null) return null;
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        File f;
        try { f = resolve(uri); } catch (FileNotFoundException e) { return null; }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
