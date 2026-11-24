package com.freeclick.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/**
 * One optional schedule per macro: run at HH:MM, once or every day.
 * Stored as "hh:mm:daily" in preferences; alarms are re-armed on change.
 */
final class Schedules {
    private Schedules() {}

    static final class Entry {
        int hour, minute;
        boolean daily;
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("schedules", Context.MODE_PRIVATE);
    }

    static Entry get(Context c, String macroId) {
        String v = sp(c).getString(macroId, null);
        if (v == null) return null;
        String[] p = v.split(":");
        if (p.length != 3) return null;
        try {
            Entry e = new Entry();
            e.hour = Integer.parseInt(p[0]);
            e.minute = Integer.parseInt(p[1]);
            e.daily = "1".equals(p[2]);
            return e;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static void set(Context c, String macroId, int hour, int minute, boolean daily) {
        sp(c).edit().putString(macroId, hour + ":" + minute + ":" + (daily ? 1 : 0)).apply();
        arm(c, macroId);
    }

    static void clear(Context c, String macroId) {
        sp(c).edit().remove(macroId).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(c, macroId));
    }

    /** Next trigger time in millis for an entry (today if still ahead, else tomorrow). */
    static long nextTime(Entry e, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, e.hour);
        cal.set(Calendar.MINUTE, e.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    static void arm(Context c, String macroId) {
        Entry e = get(c, macroId);
        if (e == null) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long at = nextTime(e, System.currentTimeMillis());
        PendingIntent pi = pending(c, macroId);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000, pi); // within a minute
    }

    static void armAll(Context c) {
        for (String id : sp(c).getAll().keySet()) arm(c, id);
    }

    static PendingIntent pending(Context c, String macroId) {
        Intent i = new Intent(c, ScheduleReceiver.class).putExtra("id", macroId)
                .setData(android.net.Uri.parse("freeclick://schedule/" + macroId)); // unique per macro
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
