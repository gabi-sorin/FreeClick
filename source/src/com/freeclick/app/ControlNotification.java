package com.freeclick.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * "FreeClick · <macro>" notification with ▶/❚❚, ■ and "Arată/Ascunde panoul", shown while the
 * panel is on screen. Useful when the panel is hidden (safe mode) or covered. It is only
 * updated when something changes (Android limits how often a notification may be updated).
 * If the user swipes it away it stays away until ≡ → "Arată notificarea".
 */
final class ControlNotification {
    static final String CHANNEL = "controls";
    static final int ID = 7;
    static final String ACTION_TOGGLE = "com.freeclick.ctl.TOGGLE";
    static final String ACTION_STOP = "com.freeclick.ctl.STOP";
    static final String ACTION_PANEL = "com.freeclick.ctl.PANEL";
    static final String ACTION_DISMISSED = "com.freeclick.ctl.DISMISSED";

    private final Context ctx;
    private final NotificationManager nm;
    private String lastKey;
    private boolean dismissed;

    ControlNotification(Context ctx) {
        this.ctx = ctx;
        this.nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Controale panou", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Pornește, pune pauză sau oprește din notificări");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    boolean isDismissed() { return dismissed; }

    void markDismissed() { dismissed = true; lastKey = null; }

    /** ≡ → "Arată notificarea" after it was swiped away. */
    void undismiss() { dismissed = false; lastKey = null; }

    /**
     * @param state "idle", "playing", "paused", "recording", "preview"
     * @param panelShown whether the floating panel is visible
     */
    void update(boolean enabled, String macroName, String state, boolean panelHidden) {
        if (!enabled || dismissed) { cancel(); return; }
        String key = macroName + "|" + state + "|" + panelHidden;
        if (key.equals(lastKey)) return;
        lastKey = key;
        String status;
        if ("playing".equals(state)) status = "rulează";
        else if ("paused".equals(state)) status = "în pauză";
        else if ("recording".equals(state)) status = "înregistrează";
        else if ("preview".equals(state)) status = "previzualizare";
        else status = "gata de pornire";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL) : new Notification.Builder(ctx);
        b.setSmallIcon("playing".equals(state) || "preview".equals(state) ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
                .setContentTitle("FreeClick · " + macroName)
                .setContentText(status)
                .setOngoing(!"idle".equals(state))
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setDeleteIntent(pending(ACTION_DISMISSED, 4));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        boolean running = "playing".equals(state) || "paused".equals(state) || "preview".equals(state);
        if (!"recording".equals(state)) {
            b.addAction(new Notification.Action.Builder(0, "playing".equals(state) || "preview".equals(state) ? "Pauză"
                    : "paused".equals(state) ? "Continuă" : "Pornește", pending(ACTION_TOGGLE, 1)).build());
        }
        if (running || "recording".equals(state)) {
            b.addAction(new Notification.Action.Builder(0, "Stop", pending(ACTION_STOP, 2)).build());
        }
        b.addAction(new Notification.Action.Builder(0, panelHidden ? "Arată panoul" : "Ascunde panoul", pending(ACTION_PANEL, 3)).build());
        try {
            nm.notify(ID, b.build());
        } catch (RuntimeException e) { // notifications blocked / permission missing: controls are optional
            lastKey = null;
        }
    }

    void cancel() {
        lastKey = null;
        try { nm.cancel(ID); } catch (RuntimeException ignored) { }
    }

    private PendingIntent pending(String action, int code) { return pendingAction(ctx, action, code); }

    /** The same buttons, for the 🌙 AFK notification. */
    static PendingIntent pendingAction(Context c, String action, int code) {
        Intent i = new Intent(c, Receiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** notifica(…) from a script (2.4); false if notifications are off for FreeClick. */
    static boolean scriptMessage(Context c, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || !nm.areNotificationsEnabled()) return false;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("script", "Mesaje din scripturi", NotificationManager.IMPORTANCE_DEFAULT));
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "script") : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
            nm.notify(100 + (int) (android.os.SystemClock.uptimeMillis() % 1000), b.build());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Not exported: only our own notification can send these. */
    public static final class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            ClickService s = ClickService.get();
            if (s == null || intent == null || intent.getAction() == null) return;
            s.onNotificationAction(intent.getAction());
        }
    }
}
