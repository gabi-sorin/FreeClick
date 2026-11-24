package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.freeclick.core.Macro;
import com.freeclick.core.MacroTransform;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "Setări mod manual / înregistrare" (≡ → Opțiuni): save / save as / load,
 * then the settings of the current work. Edits go straight to the panel's
 * current macro; the panel keeps it as "Nesalvat" until you save.
 */
public final class ModeSettingsActivity extends Activity {
    private LinearLayout content;
    private ClickService svc;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        LinearLayout root = Ui.vertical(this);
        root.addView(Ui.header(this, title(), false, false, null, new View.OnClickListener() {
            @Override public void onClick(View v) { more(v); }
        }));
        ScrollView sv = new ScrollView(this);
        content = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        content.setPadding(p, 0, p, Ui.dp(this, 24));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private String title() {
        String k = App.get().prefs().mode();
        if (Macro.KIND_RECORD.equals(k)) return "Setări mod înregistrare";
        if (Macro.KIND_COMBO.equals(k)) return "Setări combinație";
        return "Setări mod manual";
    }

    @Override protected void onResume() {
        super.onResume();
        svc = ClickService.get();
        if (svc != null) svc.onOptionsScreen(true);
        render();
    }

    @Override protected void onPause() {
        if (svc != null) svc.onOptionsScreen(false);
        super.onPause();
    }

    private Macro m() { return svc == null ? null : svc.workspace(); }

    private void changed() {
        svc.markDirty();
        render();
    }

    private void render() {
        content.removeAllViews();
        final Macro m = m();
        if (m == null) {
            content.addView(Ui.tipCard(this, "Pornește „Stare” din ecranul principal ca să vezi setările modului."));
            return;
        }
        final boolean saved = svc.isWorkspaceSaved();
        final Macro.Settings s = m.settings;

        // ---- current preset: name, date, number of actions
        LinearLayout cur = Ui.horizontal(this);
        cur.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        cur.setBackground(Ui.surface(this, Ui.SURFACE, 16));
        LinearLayout names = Ui.vertical(this);
        names.addView(Ui.text(this, saved ? m.name : ClickService.UNSAVED, 20, Ui.TEXT, true));
        names.addView(Ui.muted(this, saved
                ? new SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(new Date(m.updatedAt))
                + (svc.isDirty() ? " · modificări nesalvate" : " · salvat")
                : "Lucru curent · nesalvat"));
        cur.addView(names, Ui.weight(1));
        TextView badge = Ui.text(this, String.valueOf(com.freeclick.core.ActionTree.count(m.actions)), 13, Ui.TEXT, true);
        badge.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable ring = Ui.rounded(0x00000000, Ui.dp(this, 18));
        ring.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        badge.setBackground(ring);
        cur.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        content.addView(cur, Ui.matchWrap());
        content.addView(Ui.divider(this));

        // ---- save / save as / load
        content.addView(Ui.actionRow(this, "Salvează", saved && svc.isDirty() ? new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (svc.saveWorkspace()) Toast.makeText(ModeSettingsActivity.this, "Setări salvate!", Toast.LENGTH_SHORT).show();
                render();
            }
        } : null));
        content.addView(Ui.actionRow(this, "Salvează ca", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.textDialog(ModeSettingsActivity.this, "Nume", saved ? m.name + " (copie)" : "", "SALVEAZĂ",
                        new Ui.OnString() {
                            @Override public void onValue(String name) {
                                if (svc.saveWorkspaceAs(name)) Toast.makeText(ModeSettingsActivity.this, "Setări salvate!", Toast.LENGTH_SHORT).show();
                                render();
                            }
                        }, false);
            }
        }));
        content.addView(Ui.actionRow(this, "Încarcă, exportă sau gestionează", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(ModeSettingsActivity.this, LibraryActivity.class)
                        .putExtra(LibraryActivity.EXTRA_KIND, m.kind));
            }
        }));
        content.addView(Ui.divider(this));

        // ---- the 3 settings used most; everything else under ⚙ Avansat (one drop-down per area)
        content.addView(Ui.title(this, "Setări"));
        if (Macro.KIND_MANUAL.equals(m.kind)) {
            content.addView(Ui.settingRow(this, "Interval de repetare", Ui.msLabel(s.tapInterval) + " · pauza dinaintea fiecărei ținte",
                    new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Ui.timeDialog(ModeSettingsActivity.this, "Interval de repetare", s.tapInterval, 0, 24L * 3600000,
                            new Ui.OnLong() { public void onValue(long ms) {
                                MacroTransform.applyManualTiming(m, ms, s.tapDuration, s.swipeDuration); changed(); } }, false);
                }
            }));
            repeatCountRow(s);
        } else {
            repeatCountRow(s);
            speedRow(s);
        }
        content.addView(Ui.settingRow(this, "Editează pașii unul câte unul",
                com.freeclick.core.ActionTree.count(m.actions) + " acțiuni · ordine, pauze, cronologie", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(ModeSettingsActivity.this, EditorActivity.class)
                                .putExtra(EditorActivity.EXTRA_WORKSPACE, true));
                    }
                }));

        // ---- ⚙ Avansat: closed by default, one drop-down per area inside (only that area's settings)
        final LinearLayout main = content;
        Section adv = Section.add(main, "mode.adv", "⚙ Avansat", "Atingeri, Locație, Timp, Aplicație, Siguranță, Filmare");
        try {
            // 🖐 Atingeri: how long each touch lasts, what is drawn on the screen
            content = Section.nested(adv.body, "mode.touch", "🖐 Atingeri", Macro.KIND_MANUAL.equals(m.kind)
                    ? "click " + Ui.msLabel(s.tapDuration) + " · swipe " + Ui.msLabel(s.swipeDuration) : "puncte pe ecran").body;
            if (Macro.KIND_MANUAL.equals(m.kind)) {
                content.addView(Ui.settingRow(this, "Durată click", Ui.msLabel(s.tapDuration), new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Ui.timeDialog(ModeSettingsActivity.this, "Durată click", s.tapDuration, 1, 60000,
                                new Ui.OnLong() { public void onValue(long ms) {
                                    MacroTransform.applyManualTiming(m, s.tapInterval, ms, s.swipeDuration); changed(); } }, false);
                    }
                }));
                content.addView(Ui.settingRow(this, "Durată swipe", Ui.msLabel(s.swipeDuration), new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Ui.timeDialog(ModeSettingsActivity.this, "Durată swipe", s.swipeDuration, 1, 60000,
                                new Ui.OnLong() { public void onValue(long ms) {
                                    MacroTransform.applyManualTiming(m, s.tapInterval, s.tapDuration, ms); changed(); } }, false);
                    }
                }));
            }
            if (Macro.KIND_MANUAL.equals(m.kind)) {
                Ui.toggleRow(content, "Atinge țintele deodată", "Tap-urile una după alta se fac în același moment, ca mai multe degete "
                        + "(până la 10). Swipe-urile și restul rămân pe rând.", s.syncTaps, new CompoundButton.OnCheckedChangeListener() {
                            @Override public void onCheckedChanged(CompoundButton b, boolean on) { s.syncTaps = on; changed(); }
                        });
            }
            if (!Macro.KIND_COMBO.equals(m.kind)) {
                final Prefs prefs = App.get().prefs();
                Ui.toggleRow(content, "Arată punctele pe ecran", Macro.KIND_MANUAL.equals(m.kind)
                        ? "Țintele numerotate (ascunse = rulează la fel, dar nu le poți muta)"
                        : "Atingerile înregistrate ca puncte numerotate", prefs.pointsVisible(m.kind),
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                                prefs.setPointsVisible(m.kind, on);
                                if (svc != null) svc.onPointsSettingChanged();
                            }
                        });
            }

            content = Section.nested(adv.body, "mode.pos", "📍 Locație", (s.jitterPx > 0 ? "abatere " + s.jitterPx + " px" : "fără abatere")
                    + (s.scaleToScreen ? " · adaptat la ecran" : "")).body;
            if (s.jitterPx > 0 || s.jitterTimePct > 0) {
                content.addView(Ui.settingRow(this, "Abatere poziție", s.jitterPx + " [px]", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Ui.numberDialog(ModeSettingsActivity.this, "Abatere poziție (px)", null, s.jitterPx, 0, 500,
                                new Ui.OnLong() { public void onValue(long v) { s.jitterPx = (int) v; changed(); } }, false);
                    }
                }));
            } else {
                content.addView(Ui.muted(this, "Abaterea poziției apare aici când pornești „Acțiuni aleatoare”."));
            }
            Ui.toggleRow(content, "Adaptează la rezoluția ecranului", "Dacă rezoluția sau orientarea diferă", s.scaleToScreen,
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton b, boolean on) { s.scaleToScreen = on; changed(); }
                    });

            content = Section.nested(adv.body, "mode.time", "⏱ Timp", (s.stopAfterMs == 0 ? "fără limită" : "stop după " + Ui.msLabel(s.stopAfterMs))
                    + (s.jitterTimePct > 0 ? " · abatere " + s.jitterTimePct + "%" : "")).body;
            if (s.jitterPx > 0 || s.jitterTimePct > 0) {
                content.addView(Ui.settingRow(this, "Abatere timp", s.jitterTimePct + " [%]", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Ui.numberDialog(ModeSettingsActivity.this, "Abatere timp (%)", null, s.jitterTimePct, 0, 100,
                                new Ui.OnLong() { public void onValue(long v) { s.jitterTimePct = (int) v; changed(); } }, false);
                    }
                }));
            }
            if (Macro.KIND_MANUAL.equals(m.kind)) speedRow(s);
            content.addView(Ui.settingRow(this, "Pauză înainte de repetare", Ui.msLabel(s.loopDelay), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Ui.timeDialog(ModeSettingsActivity.this, "Pauză înainte de repetare", s.loopDelay, 0, 24L * 3600000,
                            new Ui.OnLong() { public void onValue(long ms) { s.loopDelay = ms; changed(); } }, false);
                }
            }));
            content.addView(Ui.settingRow(this, "Oprește automat după",
                    s.stopAfterMs == 0 ? "Niciodată" : Ui.msLabel(s.stopAfterMs), new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            Ui.timeDialog(ModeSettingsActivity.this, "Oprește după (0 = niciodată)", s.stopAfterMs, 0,
                                    7L * 24 * 3600000, new Ui.OnLong() { public void onValue(long ms) { s.stopAfterMs = ms; changed(); } }, false);
                        }
                    }));

            content = Section.nested(adv.body, "mode.app", "📱 Aplicație", (s.launchPackage.isEmpty() ? "nu deschide nimic" : "deschide " + appLabel(s.launchPackage))
                    + (s.lockToPackage.isEmpty() ? "" : " · doar în " + appLabel(s.lockToPackage))).body;
            content.addView(Ui.settingRow(this, "Deschide întâi aplicația", appLabel(s.launchPackage), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pickApp(new Ui.OnString() { public void onValue(String pkg) { s.launchPackage = pkg; changed(); } });
                }
            }));
            final ClickService svcNow = ClickService.get();
            final Macro savedNow = svcNow != null && svcNow.isWorkspaceSaved() && svcNow.workspace() != null
                    ? App.get().repo().find(svcNow.workspace().id) : null;
            content.addView(Ui.settingRow(this, "⚡ Pornește când deschid aplicația",
                    savedNow == null ? "salvează întâi" : AutoStart.describe(this, savedNow), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (savedNow == null) {
                        Toast.makeText(ModeSettingsActivity.this, "Salvează întâi (Salvează ca…), apoi alegi aplicația", Toast.LENGTH_LONG).show();
                        return;
                    }
                    AutoStart.dialog(ModeSettingsActivity.this, savedNow, new Runnable() { public void run() { recreate(); } });
                }
            }));
            content.addView(Ui.settingRow(this, "Rulează doar în aplicația", appLabel(s.lockToPackage) +
                    (s.lockToPackage.isEmpty() ? "" : " · altfel pauză, reia când revii"), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pickApp(new Ui.OnString() { public void onValue(String pkg) { s.lockToPackage = pkg; changed(); } });
                }
            }));

            content = Section.nested(adv.body, "mode.safe", "🛡 Siguranță", (s.safeMode ? "mod sigur pornit" : "mod sigur oprit")
                    + (s.jitterPx > 0 || s.jitterTimePct > 0 ? " · acțiuni aleatoare" : "")).body;
            Ui.toggleRow(content, "Acțiuni aleatoare", "Fiecare atingere cade puțin diferit și pauzele variază "
                    + "(poate ajuta împotriva protecțiilor anti-bot)", s.jitterPx > 0 || s.jitterTimePct > 0,
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                            if (on) { s.jitterPx = Math.max(s.jitterPx, 8); s.jitterTimePct = Math.max(s.jitterTimePct, 15); }
                            else { s.jitterPx = 0; s.jitterTimePct = 0; }
                            changed();
                        }
                    });
            Ui.toggleRow(content, "Mod sigur", "Ascunde panoul și țintele exact cât durează fiecare atingere "
                    + "(pentru aplicații care ignoră atingerile când ceva e desenat peste ele)", s.safeMode,
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton b, boolean on) { s.safeMode = on; changed(); }
                    });

            content = Section.nested(adv.body, "mode.video", "🎥 Filmare", s.recordVideo ? "filmează fiecare rulare" : "nu filmează").body;
            Ui.toggleRow(content, "Filmează ecranul la rulare", "Salvează un MP4 în Galerie (Android cere permisiunea "
                    + "de captură la fiecare filmare)", s.recordVideo,
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton b, boolean on) { s.recordVideo = on; changed(); }
                    });
        } finally {
            content = main;
        }
    }

    private static boolean hasSwipe(Macro m) {
        if (com.freeclick.core.ActionTree.anyOfType(m.actions, com.freeclick.core.Action.Type.SWIPE)) return true;
        return false;
    }

    private void repeatCountRow(final Macro.Settings s) {
        content.addView(Ui.settingRow(this, "Număr de repetări", s.loops == 0 ? "Infinit" : String.valueOf(s.loops),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Ui.numberDialog(ModeSettingsActivity.this, "Număr de repetări", "0 = la infinit (până apeși stop)",
                                s.loops, 0, 10000000, new Ui.OnLong() { public void onValue(long n) { s.loops = (int) n; changed(); } }, false);
                    }
                }));
    }

    private void speedRow(final Macro.Settings s) {
        content.addView(Ui.settingRow(this, "Viteză de redare", FloatingPanel.formatSpeed(s.speed), new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<String> labels = new ArrayList<String>();
                for (double sp : ClickService.SPEEDS) labels.add(FloatingPanel.formatSpeed(sp));
                Ui.popup(v, labels, null, new Ui.OnPick() {
                    @Override public void onPick(int i) { s.speed = ClickService.SPEEDS[i]; changed(); }
                });
            }
        }));
    }

    private String appLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "Niciuna";
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg + " (neinstalată)";
        }
    }

    /** Installed apps with a launcher icon; first entry clears the choice. */
    private void pickApp(final Ui.OnString cb) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String[]> rows = new ArrayList<String[]>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            rows.add(new String[]{String.valueOf(r.loadLabel(pm)), pkg});
        }
        Collections.sort(rows, new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) { return a[0].compareToIgnoreCase(b[0]); }
        });
        rows.add(0, new String[]{"Niciuna", ""});
        String[] labels = new String[rows.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = rows.get(i)[0];
        new AlertDialog.Builder(this).setTitle("Alege aplicația")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { cb.onValue(rows.get(which)[1]); }
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    private void more(View anchor) {
        Ui.popup(anchor, Arrays.asList("Nou (golește)", "Ajutor"), null, new Ui.OnPick() {
            @Override public void onPick(int i) {
                if (i == 0) {
                    if (svc == null) return;
                    new AlertDialog.Builder(ModeSettingsActivity.this).setTitle("Începi de la zero?")
                            .setMessage("Lucrul curent nesalvat se pierde. Cele salvate rămân în listă.")
                            .setPositiveButton("DA", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) { svc.newWorkspace(); render(); }
                            })
                            .setNegativeButton("ANULEAZĂ", null).show();
                } else {
                    new AlertDialog.Builder(ModeSettingsActivity.this).setTitle("Ajutor")
                            .setMessage("Interval de repetare: pauza dinaintea fiecărei ținte.\n"
                                    + "Durată click / swipe: cât ține apăsarea / mișcarea.\n"
                                    + "Se aplică tuturor țintelor; o țintă anume o reglezi atingând-o pe ecran.\n"
                                    + "Număr de repetări: de câte ori rulează lista (0 = la infinit).\n"
                                    + "Viteză: se poate schimba și din panou în timpul redării.")
                            .setPositiveButton("OK", null).show();
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { Ui.goBack(this); }
}
