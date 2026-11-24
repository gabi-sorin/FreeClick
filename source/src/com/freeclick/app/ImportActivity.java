package com.freeclick.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 📤 "Share → FreeClick" from any app (2.3): a message with a share code, or a .freeclick.json file.
 * Shows what it brings and imports it only after "Importă" (nothing is overwritten, nothing runs).
 */
public final class ImportActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        String text = null;
        Intent i = getIntent();
        try {
            if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
                CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (t != null) text = t.toString();
                if (text == null) {
                    Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (u != null) text = read(u);
                }
            } else if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
                text = read(i.getData());
            }
        } catch (Exception e) {
            Toast.makeText(this, "Nu pot citi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (text == null || !com.freeclick.core.ShareCode.contains(text)) {
            Toast.makeText(this, "Aici nu e niciun cod sau fișier FreeClick.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ShareUi.confirmImport(this, text, new Runnable() { public void run() {
            startActivity(new Intent(ImportActivity.this, LibraryActivity.class));
        } });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // the dialog took the focus and gave it back: it is closed, so are we
        if (hasFocus && shown) finish();
        if (!hasFocus) shown = true;
    }

    private boolean shown;

    private String read(Uri u) throws Exception {
        InputStream in = getContentResolver().openInputStream(u);
        if (in == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > com.freeclick.core.ShareCode.MAX_JSON_BYTES) throw new java.io.IOException("Fișier prea mare");
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
