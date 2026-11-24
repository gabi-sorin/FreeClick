package com.freeclick.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Sesiune AFK mode: while a long run is on, FreeClick is a foreground service with its own notification
 * ("Sesiune AFK · Farm · 1 h 23 min · ciclul 412"), so Android and the phone maker's battery manager do not
 * stop it after a while. The notification also has Pauză / Stop (the panel controls are then in it).
 * Nothing runs here: the work stays in ClickService; this only keeps the process important.
 */
public final class SessionService extends Service {
    static final int ID = 9, ENDED_ID = 10;
    static final String CHANNEL = "afk";

    private static volatile String title = "FreeClick · sesiune AFK", text = "";
    private static volatile boolean paused;
    private static volatile boolean running;

    static boolean isRunning() { return running; }

    static void start(Context c, String t, String x) {
        title = t;
        text = x;
        Intent i = new Intent(c, SessionService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (RuntimeException e) { // not allowed right now: the rest of AFK mode still works
            running = false;
        }
    }

    static void update(Context c, String t, String x, boolean isPaused) {
        title = t;
        text = x;
        paused = isPaused;
        if (!running) return;
        try {
            ((NotificationManager) c.getSystemService(NOTIFICATION_SERVICE)).notify(ID, build(c));
        } catch (RuntimeException ignored) { }
    }

    static void stop(Context c) {
        running = false;
        try { c.stopService(new Intent(c, SessionService.class)); } catch (RuntimeException ignored) { }
    }

    /** The session stopped while nobody was looking (gave up, battery...): a normal notification says why. */
    static void notifyEnded(Context c, String why) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            channel(nm);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Sesiunea AFK s-a oprit").setContentText(why)
                    .setStyle(new Notification.BigTextStyle().bigText(why)).setAutoCancel(true).setContentIntent(openApp(c));
            nm.notify(ENDED_ID, b.build());
        } catch (RuntimeException ignored) { }
    }

    private static void channel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Mod AFK (sesiuni lungi)", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Cât rulează o sesiune lungă: timp, cicluri, pauză / stop");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(c, 11, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification build(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        channel(nm);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(paused ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(openApp(c));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        b.addAction(new Notification.Action.Builder(0, paused ? "Continuă" : "Pauză",
                ControlNotification.pendingAction(c, ControlNotification.ACTION_TOGGLE, 1)).build());
        b.addAction(new Notification.Action.Builder(0, "Stop", ControlNotification.pendingAction(c, ControlNotification.ACTION_STOP, 2)).build());
        return b.build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = build(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(ID, n);
            running = true;
        } catch (RuntimeException e) {
            running = false;
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
