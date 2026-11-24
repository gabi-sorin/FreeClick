package com.freeclick.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile. Tap: open the panel with the last macro;
 * tap again: stop playback / close the panel.
 */
public final class QuickTileService extends TileService {

    static void requestUpdate(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, QuickTileService.class));
        } catch (RuntimeException ignored) { }
    }

    @Override public void onStartListening() { refresh(); }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        ClickService s = ClickService.get();
        if (s == null) {
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("FreeClick: activează");
        } else if (s.isPlaying()) {
            t.setState(Tile.STATE_ACTIVE);
            t.setLabel("FreeClick: oprește");
        } else if (s.isPanelShown()) {
            t.setState(Tile.STATE_ACTIVE);
            t.setLabel("FreeClick: ascunde");
        } else {
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("FreeClick");
        }
        t.updateTile();
    }

    @Override public void onClick() {
        ClickService s = ClickService.get();
        if (s == null) {
            Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapseCompat(i);
            return;
        }
        if (s.isPlaying()) {
            s.stopPlayback();
        } else if (s.isPanelShown()) {
            s.hidePanel();
        } else {
            collapseShade(s);
            s.showPanel();
        }
        refresh();
    }

    @SuppressWarnings("deprecation")
    private void startActivityAndCollapseCompat(Intent i) {
        if (Build.VERSION.SDK_INT >= 34) {
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0, i,
                    android.app.PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }

    @SuppressWarnings("deprecation")
    private void collapseShade(ClickService s) {
        if (Build.VERSION.SDK_INT >= 31) {
            s.performGlobalAction(15 /* GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE */);
        } else {
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
    }
}
