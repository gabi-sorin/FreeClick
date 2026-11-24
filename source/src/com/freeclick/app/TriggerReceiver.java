package com.freeclick.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.freeclick.core.Macro;

/**
 * Lets automation apps start/stop macros:
 *   action com.freeclick.action.RUN_MACRO, extra "name" (or "id")
 *   action com.freeclick.action.STOP
 * Ignored unless "Allow other apps" is enabled in Settings, so a random app
 * cannot drive your screen.
 */
public final class TriggerReceiver extends BroadcastReceiver {
    public static final String ACTION_RUN = "com.freeclick.action.RUN_MACRO";
    public static final String ACTION_STOP = "com.freeclick.action.STOP";

    @Override public void onReceive(Context context, Intent intent) {
        if (!App.get().prefs().allowExternalTriggers()) {
            Log.i("FreeClick", "external trigger ignored (disabled in settings)");
            return;
        }
        ClickService s = ClickService.get();
        if (s == null) return;
        if (ACTION_STOP.equals(intent.getAction())) {
            s.stopPlayback();
            return;
        }
        if (!ACTION_RUN.equals(intent.getAction())) return;
        Macro m = findMacro(intent.getStringExtra("id"), intent.getStringExtra("name"));
        if (m == null) {
            s.toast("FreeClick: nu găsesc macro-ul cerut");
            return;
        }
        s.playNow(m);
    }

    static Macro findMacro(String id, String name) {
        MacroRepository repo = App.get().repo();
        if (id != null) {
            Macro m = repo.find(id);
            if (m != null) return m;
        }
        if (name != null) {
            for (Macro m : repo.list()) if (m.name.equalsIgnoreCase(name.trim())) return m;
        }
        return null;
    }
}
