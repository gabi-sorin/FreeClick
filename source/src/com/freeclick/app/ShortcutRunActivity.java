package com.freeclick.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.freeclick.R;
import com.freeclick.core.Macro;

import java.security.MessageDigest;

/** Target of the "run macro" home-screen shortcuts. Invisible; finishes immediately. */
public final class ShortcutRunActivity extends Activity {
    static final String EXTRA_ID = "id";
    static final String EXTRA_TOKEN = "token";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            run();
        } finally {
            finish();
        }
    }

    private void run() {
        Intent i = getIntent();
        String token = i.getStringExtra(EXTRA_TOKEN);
        if (token == null || !constantTimeEquals(token, App.get().prefs().shortcutToken())) {
            return; // not one of our shortcuts: ignore silently
        }
        Macro m = App.get().repo().find(i.getStringExtra(EXTRA_ID));
        if (m == null) {
            Toast.makeText(this, "Macro-ul acestei scurtături a fost șters", Toast.LENGTH_LONG).show();
            return;
        }
        ClickService s = ClickService.get();
        if (s == null) {
            Toast.makeText(this, "Activează întâi FreeClick în Setări → Accesibilitate", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (m.settings.launchPackage == null || m.settings.launchPackage.isEmpty()) {
            Toast.makeText(this, "„" + m.name + "” pornește peste ecranul curent. Setează „Deschide întâi aplicația” "
                    + "ca scurtătura să deschidă singură jocul.", Toast.LENGTH_LONG).show();
        }
        s.playNow(m);
    }

    private static boolean constantTimeEquals(String a, String b) {
        try {
            return MessageDigest.isEqual(a.getBytes("UTF-8"), b.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            return false;
        }
    }

    /** Ask the launcher to pin a shortcut for the macro (Android 8+). */
    static boolean pin(Context c, Macro m) {
        if (Build.VERSION.SDK_INT < 26) return false;
        ShortcutManager sm = c.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) return false;
        Intent run = new Intent(c, ShortcutRunActivity.class).setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_ID, m.id).putExtra(EXTRA_TOKEN, App.get().prefs().shortcutToken());
        String label = m.name.length() > 24 ? m.name.substring(0, 23) + "…" : m.name;
        ShortcutInfo info = new ShortcutInfo.Builder(c, "macro-" + m.id)
                .setShortLabel(label)
                .setLongLabel("Rulează " + m.name)
                .setIcon(Icon.createWithResource(c, R.mipmap.ic_launcher))
                .setIntent(run)
                .build();
        return sm.requestPinShortcut(info, null);
    }
}
