package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.freeclick.core.Macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * ⚡ "Start when I open the app": one saved macro (or script) per app. Opening the app shows the panel
 * with it, or starts it after 3 s. Kept apart from the macro (not copied by Duplică / export).
 */
final class AutoStart {
    private AutoStart() {}

    static final String PANEL = "panel", RUN = "run";

    /** The app this macro starts with (null if none). */
    static String appOf(Macro m) {
        for (Map.Entry<String, String> e : App.get().prefs().autostarts().entrySet()) {
            if (e.getValue().startsWith(m.id + "|")) return e.getKey();
        }
        return null;
    }

    static String describe(Context c, Macro m) {
        String pkg = appOf(m);
        if (pkg == null) return "oprită";
        String v = App.get().prefs().autostart(pkg);
        return label(c, pkg) + (v != null && v.endsWith("|" + RUN) ? " · pornește singur" : " · arată panoul");
    }

    static String label(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return "Niciuna";
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** Choose the app, then what happens; or turn it off. */
    static void dialog(final Activity a, final Macro m, final Runnable after) {
        final String current = appOf(m);
        List<String> items = new ArrayList<String>();
        items.add(current == null ? "Alege aplicația…" : "Altă aplicație (acum: " + label(a, current) + ")…");
        if (current != null) items.add("Oprește pornirea automată");
        new AlertDialog.Builder(a).setTitle("Pornire automată: „" + m.name + "”")
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 1) {
                            App.get().prefs().setAutostart(current, null);
                            if (after != null) after.run();
                            return;
                        }
                        pickApp(a, m, new Ui.OnString() {
                            @Override public void onValue(String pkg) { if (!pkg.isEmpty()) chooseMode(a, m, pkg, after); }
                        });
                    }
                }).setNegativeButton("ÎNCHIDE", null).show();
    }

    private static void chooseMode(final Activity a, final Macro m, final String pkg, final Runnable after) {
        String[] modes = {"Doar arată panoul cu „" + m.name + "”", "Pornește singur după 3 secunde"};
        new AlertDialog.Builder(a).setTitle("Când deschizi " + label(a, pkg) + ":")
                .setItems(modes, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, final int which) {
                        final String value = m.id + "|" + (which == 1 ? RUN : PANEL);
                        String other = App.get().prefs().autostart(pkg);
                        String otherId = other == null ? null : other.substring(0, other.indexOf('|') < 0 ? other.length() : other.indexOf('|'));
                        final Macro was = otherId == null || otherId.equals(m.id) ? null : App.get().repo().find(otherId);
                        Runnable save = new Runnable() { public void run() {
                            String old = appOf(m);
                            if (old != null) App.get().prefs().setAutostart(old, null); // one app per macro
                            App.get().prefs().setAutostart(pkg, value);
                            android.widget.Toast.makeText(a, "La deschiderea " + label(a, pkg) + (which == 1 ? " pornește singur" : " apare panoul"),
                                    android.widget.Toast.LENGTH_LONG).show();
                            if (after != null) after.run();
                        } };
                        if (was == null) { save.run(); return; }
                        final Runnable go = save;
                        new AlertDialog.Builder(a).setTitle("Înlocuiești „" + was.name + "”?")
                                .setMessage(label(a, pkg) + " pornește acum „" + was.name + "”. O aplicație pornește un singur macro.")
                                .setPositiveButton("ÎNLOCUIEȘTE", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int w) { go.run(); }
                                }).setNegativeButton("ANULEAZĂ", null).show();
                    }
                }).setNegativeButton("ANULEAZĂ", null).show();
    }

    /** The phone's apps (the macro's own app first, if it has one). */
    static void pickApp(Activity a, Macro m, final Ui.OnString cb) {
        PackageManager pm = a.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String[]> rows = new ArrayList<String[]>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(a.getPackageName()) || !seen.add(pkg)) continue;
            rows.add(new String[]{String.valueOf(r.loadLabel(pm)), pkg});
        }
        Collections.sort(rows, new Comparator<String[]>() {
            @Override public int compare(String[] x, String[] y) { return x[0].compareToIgnoreCase(y[0]); }
        });
        String own = m == null ? "" : !m.settings.launchPackage.isEmpty() ? m.settings.launchPackage : m.settings.lockToPackage;
        if (own != null && !own.isEmpty()) rows.add(0, new String[]{"★ " + label(a, own) + " (aplicația macro-ului)", own});
        String[] labels = new String[rows.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = rows.get(i)[0];
        new AlertDialog.Builder(a).setTitle("Alege aplicația")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { cb.onValue(rows.get(which)[1]); }
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }
}
