package com.freeclick.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.freeclick.core.Macro;

/** Fires a scheduled macro (internal, not exported). */
public final class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Schedules.armAll(c);
            return;
        }
        String id = intent.getStringExtra("id");
        if (id == null) return;
        Schedules.Entry e = Schedules.get(c, id);
        if (e == null) return;
        if (e.daily) Schedules.arm(c, id); else Schedules.clear(c, id);

        Macro m = App.get().repo().find(id);
        ClickService s = ClickService.get();
        if (m == null) return;
        if (s == null) {
            notify(c, "Nu am putut rula „" + m.name + "”", "Serviciul de accesibilitate FreeClick este oprit.");
            return;
        }
        s.playNow(m);
        notify(c, "A pornit „" + m.name + "”", "Programare FreeClick");
    }

    private static void notify(Context c, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, App.CHANNEL_INFO) : new Notification.Builder(c);
        try {
            nm.notify(100, b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title)
                    .setContentText(text).setAutoCancel(true).build());
        } catch (SecurityException ignored) { /* notifications not allowed */ }
    }
}
