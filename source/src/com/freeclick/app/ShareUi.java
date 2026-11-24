package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.freeclick.core.Macro;
import com.freeclick.core.MacroCodec;
import com.freeclick.core.ShareCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 📤 Sharing (2.3): any save, or several, as a code you paste in a chat ("FREECLICK1:…"), or as a file.
 * Importing: paste a code (FreeClick also notices one you copied), open a file, or share a message /
 * file to FreeClick from any app. A confirmation shows what comes in; nothing is ever overwritten.
 */
final class ShareUi {
    private ShareUi() {}

    /** Codes longer than this (pictures inside) are better sent as a file. */
    private static final int LONG_CODE = 60000;

    static void share(final Activity a, final List<Macro> items, final Runnable saveFile) {
        final MacroRepository repo = App.get().repo();
        final String code;
        try {
            code = ShareCode.encode(MacroCodec.exportPack(items, repo, new MacroCodec.ImageSource() {
                @Override public byte[] png(String id) { return App.get().images().read(id); }
            }));
        } catch (RuntimeException e) {
            Toast.makeText(a, "Nu am putut pregăti: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        final String msg = ShareCode.message(items, code);
        boolean longCode = code.length() > LONG_CODE;
        List<String> opts = new ArrayList<String>();
        opts.add("Copiază codul");
        opts.add("Trimite codul ca mesaj");
        opts.add("Trimite fișierul");
        if (saveFile != null) opts.add("Salvează fișierul…");
        String title = (items.size() == 1 ? "„" + items.get(0).name + "”" : items.size() + " salvări") + " · cod de " + kb(code.length())
                + (longCode ? " (are poze: mai bine ca fișier)" : "");
        new AlertDialog.Builder(a).setTitle(title).setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                switch (w) {
                    case 0:
                        copy(a, msg);
                        Toast.makeText(a, "Copiat. Lipește-l oriunde; cine îl copiază îl poate importa în FreeClick.", Toast.LENGTH_LONG).show();
                        break;
                    case 1:
                        try {
                            a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, msg), "Trimite codul"));
                        } catch (RuntimeException e) {
                            copy(a, msg);
                            Toast.makeText(a, "Copiat în clipboard.", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 2: sendFile(a, items); break;
                    default: if (saveFile != null) saveFile.run();
                }
            }
        }).show();
    }

    private static String kb(int chars) {
        return chars < 1024 ? chars + " caractere" : (chars / 1024) + " KB";
    }

    private static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FreeClick", text));
    }

    static String fileName(List<Macro> items) {
        String base = items.size() == 1 ? items.get(0).name : "FreeClick " + items.size() + " salvari";
        base = base.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        if (base.isEmpty()) base = "macro";
        return base.replace(' ', '_') + ".freeclick.json";
    }

    private static void sendFile(Activity a, List<Macro> items) {
        try {
            File f = new File(FileShareProvider.shareDir(a), fileName(items));
            OutputStream os = new FileOutputStream(f);
            try {
                os.write(MacroCodec.exportPack(items, App.get().repo(), new MacroCodec.ImageSource() {
                    @Override public byte[] png(String id) { return App.get().images().read(id); }
                }).getBytes("UTF-8"));
            } finally { os.close(); }
            Uri uri = FileShareProvider.uriFor(FileShareProvider.ROOT_SHARE, f.getName());
            Intent s = new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(s, "Trimite"));
        } catch (Exception e) {
            Toast.makeText(a, "Eroare: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ importing
    /** The code in the clipboard, or null (Android 10+ only lets the app in front read it). */
    static String clipboardCode(Context c) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return null;
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
            return t != null && t.toString().contains(ShareCode.PREFIX) ? t.toString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Shows what the text brings ("Importă 2: „Farm”, „Boss”?") and imports it on OK. */
    static void confirmImport(final Activity a, final String text, final Runnable after) {
        final List<Macro> preview;
        try {
            preview = MacroCodec.importBundle(ShareCode.decode(text), new HashSet<String>());
        } catch (RuntimeException e) {
            new AlertDialog.Builder(a).setTitle("Nu pot importa").setMessage(e.getMessage() == null ? "Codul nu e bun." : e.getMessage())
                    .setPositiveButton("OK", null).show();
            return;
        }
        StringBuilder what = new StringBuilder();
        for (int i = 0; i < preview.size() && i < 12; i++) {
            Macro m = preview.get(i);
            what.append("• ").append(m.name).append(" (").append(LibraryActivity.kindName(m.kind)).append(")\n");
        }
        if (preview.size() > 12) what.append("… și încă ").append(preview.size() - 12).append('\n');
        what.append("\nNu înlocuiește nimic din ce ai. Scripturile nu pornesc singure.");
        AlertDialog d = new AlertDialog.Builder(a).setTitle("Importă " + preview.size() + (preview.size() == 1 ? " salvare?" : " salvări?"))
                .setMessage(what.toString())
                .setPositiveButton("IMPORTĂ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) {
                        try {
                            MacroRepository.ImportResult r = App.get().repo().importText(text, App.get().images());
                            Toast.makeText(a, "Importat: " + r.macros + (r.images > 0 ? " (cu " + r.images + " imagini)" : ""), Toast.LENGTH_LONG).show();
                            if (after != null) after.run();
                        } catch (Exception e) {
                            Toast.makeText(a, "Eroare: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("ANULEAZĂ", null)
                .show();
        // tapjacking: another app's window must not be able to press IMPORTĂ for you
        if (d.getButton(AlertDialog.BUTTON_POSITIVE) != null) d.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
    }
}
