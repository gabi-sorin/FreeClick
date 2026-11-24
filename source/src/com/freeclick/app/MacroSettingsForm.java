package com.freeclick.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

import com.freeclick.core.Macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Playback settings of a macro (loops, speed, randomness, safety, video). */
final class MacroSettingsForm {
    private final Macro.Settings s;
    private final ScrollView view;
    private final EditText loops, loopDelay, speed, jitterPx, jitterPct, stopAfter, lockPkg, launchPkg;
    private final boolean overlay;
    private final Switch scale, safe, video;

    /** @param currentPackage app currently in front (for the "lock to app" shortcut), may be null. */
    MacroSettingsForm(final Context c, Macro.Settings settings, final String currentPackage, boolean overlay) {
        this.s = settings;
        this.overlay = overlay;
        view = new ScrollView(c);
        LinearLayout f = Ui.vertical(c);
        int p = Ui.dp(c, 20);
        f.setPadding(p, Ui.dp(c, 8), p, 0);
        view.addView(f);

        f.addView(Ui.title(c, "Redare"));
        loops = Ui.numberRow(f, "Număr de rulări (0 = la infinit)", s.loops, false);
        loopDelay = Ui.numberRow(f, "Pauză între rulări (ms)", s.loopDelay, false);
        speed = Ui.decimalRow(f, "Viteză (0.1 – 10)", s.speed);

        // ⚙ Avansat, one drop-down per area (only that area's settings)
        Section adv = Section.add(f, "msf.adv", "Avansat", "Locație, Timp, Aplicație, Siguranță, Filmare");
        Section pos = Section.nested(adv.body, "msf.pos", "Locație", (s.scaleToScreen ? "adaptat la ecran" : "coordonate exacte")
                + (s.jitterPx > 0 ? " · abatere " + s.jitterPx + " px" : ""));
        scale = Ui.switchRow(pos.body, "Adaptează coordonatele la rezoluția ecranului", s.scaleToScreen, null);
        jitterPx = Ui.numberRow(pos.body, "Abatere poziție (px, ca la un om)", s.jitterPx, false);

        Section time = Section.nested(adv.body, "msf.time", "Timp", (s.stopAfterMs > 0 ? "stop după " + (s.stopAfterMs / 1000) + " s" : "fără limită")
                + (s.jitterTimePct > 0 ? " · abatere " + s.jitterTimePct + "%" : ""));
        stopAfter = Ui.numberRow(time.body, "Oprește după (secunde, 0 = niciodată)", s.stopAfterMs / 1000, false);
        jitterPct = Ui.numberRow(time.body, "Abatere timp (%)", s.jitterTimePct, false);

        Section appS = Section.nested(adv.body, "msf.app", "Aplicație", (s.launchPackage.isEmpty() ? "nu deschide nimic" : "deschide " + s.launchPackage)
                + (s.lockToPackage.isEmpty() ? "" : " · doar în " + s.lockToPackage));
        launchPkg = Ui.textRow(appS.body, "Deschide întâi aplicația (pachet; gol = nu)", s.launchPackage);
        appS.body.addView(Ui.button(c, "Alege aplicația de deschis…", new View.OnClickListener() {
            @Override public void onClick(View v) { pickApp(c, launchPkg); }
        }));
        appS.body.addView(Ui.muted(c, "La pornire, FreeClick deschide aplicația, așteaptă 2,5 s, apoi rulează."));
        lockPkg = Ui.textRow(appS.body, "Rulează doar în aplicația (pachet; gol = oriunde)", s.lockToPackage);
        if (currentPackage != null && currentPackage.length() > 0) {
            Button use = Ui.button(c, "Folosește aplicația curentă: " + currentPackage, new View.OnClickListener() {
                @Override public void onClick(View v) { lockPkg.setText(currentPackage); }
            });
            appS.body.addView(use);
        }
        appS.body.addView(Ui.button(c, "Alege aplicația…", new View.OnClickListener() {
            @Override public void onClick(View v) { pickApp(c, lockPkg); }
        }));
        appS.body.addView(Ui.muted(c, "Dacă iese din aplicația aleasă, redarea intră pe pauză."));

        Section safeS = Section.nested(adv.body, "msf.safe", "Siguranță", s.safeMode ? "mod sigur pornit" : "mod sigur oprit");
        safe = Ui.switchRow(safeS.body, "Mod sigur (ascunde panoul și țintele când atinge)", s.safeMode, null);
        safeS.body.addView(Ui.muted(c, "Pentru aplicațiile cu protecție anti-tapjacking care ignoră atingerile când ceva e desenat peste ele."));

        Section vid = Section.nested(adv.body, "msf.video", "Filmare", s.recordVideo ? "filmează fiecare rulare" : "nu filmează");
        video = Ui.switchRow(vid.body, "Filmează ecranul cât rulează macro-ul", s.recordVideo, null);
    }

    View view() { return view; }

    /** List of installed apps that have a launcher icon; picking one fills the field. */
    private void pickApp(Context c, final EditText target) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        final List<String[]> rows = new ArrayList<String[]>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            rows.add(new String[]{String.valueOf(r.loadLabel(pm)), pkg});
        }
        Collections.sort(rows, new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) { return a[0].compareToIgnoreCase(b[0]); }
        });
        String[] labels = new String[rows.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = rows.get(i)[0] + "\n" + rows.get(i)[1];
        AlertDialog d = new AlertDialog.Builder(c).setTitle("Alege aplicația")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int which) { target.setText(rows.get(which)[1]); }
                })
                .setNegativeButton("Anulează", null).create();
        if (overlay) Overlays.showOverlayDialog(d); else d.show();
    }

    void apply() {
        s.loops = (int) Ui.parseLong(loops, s.loops, 0, 10000000);
        s.loopDelay = Ui.parseLong(loopDelay, s.loopDelay, 0, 24L * 3600 * 1000);
        s.speed = Macro.clampSpeed(Ui.parseDouble(speed, s.speed, Macro.MIN_SPEED, Macro.MAX_SPEED));
        s.stopAfterMs = Ui.parseLong(stopAfter, s.stopAfterMs / 1000, 0, 7L * 24 * 3600) * 1000;
        s.scaleToScreen = scale.isChecked();
        s.jitterPx = (int) Ui.parseLong(jitterPx, s.jitterPx, 0, 500);
        s.jitterTimePct = (int) Ui.parseLong(jitterPct, s.jitterTimePct, 0, 100);
        s.safeMode = safe.isChecked();
        s.lockToPackage = lockPkg.getText().toString().trim();
        s.launchPackage = launchPkg.getText().toString().trim();
        s.recordVideo = video.isChecked();
    }
}
