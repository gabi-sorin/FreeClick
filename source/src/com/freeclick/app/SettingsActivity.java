package com.freeclick.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** App settings. Everything is free: there are no locked options. */
public final class SettingsActivity extends Activity {
    private Prefs prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = App.get().prefs();
        Ui.styleWindow(this);
        LinearLayout root = Ui.vertical(this);
        root.addView(Ui.header(this, "Setări", false, false, null, null));
        ScrollView sv = new ScrollView(this);
        final LinearLayout top = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        top.setPadding(p, Ui.dp(this, 4), p, p);
        sv.addView(top);
        // every area is a drop-down: closed it shows what is inside, open it shows only its own settings
        LinearLayout f;
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        TextView intro = Ui.text(this, "Ajustează comportamentul panoului, automatizarea și datele aplicației.", 13, Ui.MUTED, false);
        intro.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        intro.setBackground(Ui.surface(this, Ui.SURFACE, 14));
        LinearLayout.LayoutParams introLp = Ui.matchWrap();
        introLp.bottomMargin = Ui.dp(this, 10);
        top.addView(intro, 0, introLp);
        top.addView(Ui.sectionLabel(this, "Ajutor și instrumente"), 1);
        top.addView(Ui.actionRow(this, "🧪 Laborator: măsoară telefonul tău", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, LabActivity.class)); }
        }));
        top.addView(Ui.actionRow(this, "📖 Ce face fiecare setare", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, DocsActivity.class).putExtra(DocsActivity.EXTRA_CATEGORY, "Setări și aplicație"));
            }
        }));
        f = Section.add(top, "set.safety", "🛡 Siguranță", "oprire de urgență, pauză la atingere, alte aplicații").body;
        Ui.toggleRow(f, "Oprire de urgență", "Apasă de 2 ori rapid volum jos ca să oprești tot", prefs.volumeEmergencyStop(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setVolumeEmergencyStop(on);
                        ClickService s = ClickService.get();
                        if (s != null) s.applyServiceFlags();
                    }
                });
        Ui.toggleRow(f, "Pauză când ating ecranul", "Dacă atingi ecranul sau tragi bara de notificări în timpul redării, "
                + "macro-ul intră în pauză (atingerile nu mai nimeresc butoanele sistemului). ▶ continuă de unde a rămas.",
                prefs.pauseOnTouch(), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setPauseOnTouch(on); }
                });
        Switch ext = Ui.toggleRow(f, "Pornire din alte aplicații", "Tasker, MacroDroid etc. pot porni salvări după nume", prefs.allowExternalTriggers(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setAllowExternalTriggers(on); }
                });
        // Tapjacking protection where it matters: another app's window cannot trick you into enabling this.
        ext.setFilterTouchesWhenObscured(true);
        ((View) ext.getParent()).setFilterTouchesWhenObscured(true);
        f.addView(Ui.muted(this, "Intent: com.freeclick.action.RUN_MACRO cu extra „name” = numele macro-ului; "
                + "com.freeclick.action.STOP oprește. Lasă oprit dacă nu folosești asta: altfel orice aplicație "
                + "instalată ar putea porni macro-uri."));

        f = Section.add(top, "set.points", "📍 Puncte pe ecran", "ținte, puncte înregistrate, unde atinge").body;
        f.addView(Ui.muted(this, "Ascunderea punctelor nu schimbă nimic la rulare: atingerile se fac la fel. "
                + "Le poți ascunde/arăta rapid și din panou: ≡ → Ascunde punctele."));
        Ui.toggleRow(f, "Ținte în modul manual", "Cercurile numerotate și swipe-urile S/E (le muți și le setezi)",
                prefs.pointsVisible(com.freeclick.core.Macro.KIND_MANUAL), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setPointsVisible(com.freeclick.core.Macro.KIND_MANUAL, on);
                        ClickService s = ClickService.get();
                        if (s != null) s.onPointsSettingChanged();
                    }
                });
        Ui.toggleRow(f, "Puncte în modul înregistrare", "Atingerile înregistrate afișate ca puncte (implicit ascunse)",
                prefs.pointsVisible(com.freeclick.core.Macro.KIND_RECORD), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setPointsVisible(com.freeclick.core.Macro.KIND_RECORD, on);
                        ClickService s = ClickService.get();
                        if (s != null) s.onPointsSettingChanged();
                    }
                });
        Ui.toggleRow(f, "Arată unde atinge", "Un cerc scurt apare unde FreeClick atinge ecranul, în timpul redării", prefs.showTouches(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setShowTouches(on); }
                });

        f = Section.add(top, "set.panel", "🎛 Panou și redare", "notificare, opacitate, mărime, numărătoare").body;
        Ui.toggleRow(f, "Controale în notificare", "Pornește / pauză / stop și ascunde panoul din bara de notificări, cât timp panoul e pornit",
                prefs.notificationControls(), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setNotificationControls(on);
                        if (on && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2);
                        }
                        ClickService s = ClickService.get();
                        if (s != null) s.onNotificationSettingChanged();
                    }
                });
        seek(f, "Opacitate panou", 30, 100, prefs.panelOpacity(), "%", new Setter() {
            public void set(int v) { prefs.setPanelOpacity(v); }
        });
        seek(f, "Mărime butoane panou", 32, 64, prefs.panelButtonDp(), " dp", new Setter() {
            public void set(int v) { prefs.setPanelButtonDp(v); }
        });
        seek(f, "Numărătoare inversă la pornire", 0, 10, prefs.startCountdown(), " s", new Setter() {
            public void set(int v) { prefs.setStartCountdown(v); }
        });
        f.addView(Ui.muted(this, "Mărimea/opacitatea se aplică la următoarea deschidere a panoului."));

        touchSection(top);
        f = Section.add(top, "set.afk", "🌙 Mod AFK (sesiuni lungi)", prefs.afkOn() ? "pornit · ecran aprins, se reface singur" : "oprit").body;
        Ui.toggleRow(f, "Mod AFK", "Cât rulează ceva: ecranul rămâne aprins, FreeClick are notificarea lui (Android nu îl oprește "
                + "după ore) și face pauză dacă blochezi telefonul.", prefs.afkOn(), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setAfkOn(on); }
                });
        Ui.toggleRow(f, "Se reface singur", "Dacă jocul se închide sau iese de pe ecran: pauză (nu atinge nimic altundeva). "
                + "Dacă nu revine în 20 s, îl redeschide și ia rularea de la capăt (ai 10 s să apeși Anulează). "
                + "Dacă Android oprește FreeClick, reia sesiunea când pornește din nou.", prefs.afkRecover(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setAfkRecover(on); }
                });
        seek(f, "Pauză când bateria scade la", 0, 50, prefs.afkMinBattery(), " %", new Setter() {
            public void set(int v) { prefs.setAfkMinBattery(v); }
        });
        f.addView(Ui.muted(this, "0 = niciodată. Continuă când îl pui la încărcat. Peste 45 °C face pauză până se răcește la 40 °C."));
        Ui.toggleRow(f, "Ecran întunecat cât rulează", "Luminozitate minimă (atingerile și citirea ecranului merg la fel). "
                + "Revine când pui pauză sau atingi ecranul.", prefs.afkDim(), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setAfkDim(on);
                        ClickService s = ClickService.get();
                        if (s != null) s.onAfkSettingChanged();
                    }
                });
        batteryRow(f);

        f = Section.add(top, "set.turbo", "🔥 Turbo", "atingi și dă rafale exact acolo · " + prefs.turboRate() + "/s").body;
        f.addView(Ui.muted(this, "Apasă 🔥 pe panou. Cât e pornit: o atingere scurtă dă o rafală pe locul acela, "
                + "ținut apăsat atinge acolo non-stop până atingi din nou ecranul, iar un swipe trece normal. × sus îl oprește."));
        Ui.toggleRow(f, "Buton 🔥 pe panou", "Butonul apare în modurile manual și înregistrare", prefs.turboButton(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setTurboButton(on);
                        ClickService s = ClickService.get();
                        if (s != null) s.onPanelButtonsChanged();
                    }
                });
        seek(f, "Ritm", 5, 60, prefs.turboRate(), " atingeri/s", new Setter() {
            public void set(int v) { prefs.setTurboRate(v); }
        });
        seek(f, "Rafală la atingere scurtă", 0, 100, prefs.turboBurst(), " atingeri", new Setter() {
            public void set(int v) { prefs.setTurboBurst(v); }
        });
        f.addView(Ui.muted(this, "0 = atingerea scurtă trece normal și doar ținut apăsat dă rafale (poți folosi aplicația ca de obicei)."));
        Ui.toggleRow(f, "Ține apăsat = non-stop", "Ții degetul o clipă (~½ s) și ridici: atinge acolo până atingi ecranul din nou",
                prefs.turboHold(), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.setTurboHold(on); }
                });
        seek(f, "Non-stop se oprește singur după", 5, 600, prefs.turboMaxSeconds(), " s", new Setter() {
            public void set(int v) { prefs.setTurboMaxSeconds(v); }
        });
        seek(f, "Împrăștiere (atingeri puțin diferite)", 0, 40, prefs.turboSpread(), " px", new Setter() {
            public void set(int v) { prefs.setTurboSpread(v); }
        });

        lettersSection(top);
        f = Section.add(top, "set.video", "🎥 Filmare ecran", new String[]{"calitate mică", "calitate medie", "calitate mare"}[Math.max(0, Math.min(2, prefs.videoQuality()))]).body;
        RadioGroup rg = new RadioGroup(this);
        String[] q = {"Mică (960p, fișiere mici)", "Medie (1280p)", "Mare (1920p)"};
        for (int i = 0; i < q.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(q[i]);
            r.setTextColor(Ui.TEXT);
            r.setId(100 + i);
            rg.addView(r);
        }
        rg.check(100 + prefs.videoQuality());
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) { prefs.setVideoQuality(id - 100); }
        });
        f.addView(rg);

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                f = Section.add(top, "set.alarms", "⏰ Programări", "alarme exacte").body;
                f.addView(Ui.muted(this, "Fără „Alarme exacte”, macro-urile programate pornesc cu până la un minut întârziere."));
                f.addView(Ui.button(this, "Permite alarme exacte", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (RuntimeException ignored) { }
                    }
                }));
            }
        }

        f = Section.add(top, "set.data", "💾 Date și backup", App.get().repo().list().size() + " salvări · backup, restaurare").body;
        f.addView(Ui.muted(this, "Salvările, ciornele „Nesalvat”, programările și setările rămân la fiecare actualizare. "
                + "Backup-ul e pentru cazul în care dezinstalezi aplicația sau schimbi telefonul."));
        f.addView(Ui.settingRow(this, "Exportă backup complet", App.get().repo().list().size()
                + " salvări într-un singur fișier", new View.OnClickListener() {
            @Override public void onClick(View v) { startBackup(); }
        }));
        f.addView(Ui.settingRow(this, "Restaurează din backup", "Readuce salvările; cele existente cu același nume "
                + "intern sunt înlocuite, nu dublate", new View.OnClickListener() {
            @Override public void onClick(View v) { startRestore(); }
        }));

        top.addView(Ui.sectionLabel(this, "Despre"));
        top.addView(Ui.muted(this, "FreeClick 2.5 · gratuit, fără reclame, fără internet. Salvările stau doar pe telefon."));
    }

    /** Taught letters of the text reader: how many, and forgetting them. */
    private void lettersSection(LinearLayout top) {
        final Letters letters = App.get().letters();
        final com.freeclick.core.FontBook book = letters.bookNoDraw();
        final Section s = Section.add(top, "set.letters", "📖 Citire text", book.learnedCount() == 0 ? "nicio literă învățată"
                : book.learnedCount() + " litere învățate");
        s.body.addView(Ui.muted(this, "Pașii „Citește” recunosc literele fonturilor telefonului. Pentru fonturi de joc ciudate, "
                + "folosește „🔍 Testează citirea” → scrii ce e corect → „Învață”. Literele învățate intră în backup."));
        java.util.Map<Character, Integer> chars = book.learnedChars();
        if (!chars.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (java.util.Map.Entry<Character, Integer> e : chars.entrySet()) {
                if (b.length() > 0) b.append("  ");
                b.append(e.getKey()).append(e.getValue() > 1 ? "×" + e.getValue() : "");
            }
            s.body.addView(Ui.text(this, b.toString(), 14, Ui.TEXT, false));
            s.body.addView(Ui.button(this, "Uită toate literele învățate", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new android.app.AlertDialog.Builder(SettingsActivity.this).setTitle("Uiți literele învățate?")
                            .setMessage("Citirea revine la literele fonturilor telefonului. Le poți învăța din nou oricând.")
                            .setPositiveButton("UITĂ", new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int w) {
                                    book.forget((char) 0);
                                    new Thread(new Runnable() { public void run() { letters.saveIfChanged(); } }, "freeclick-letters").start();
                                    recreate();
                                }
                            })
                            .setNegativeButton("ANULEAZĂ", null).show();
                }
            }));
        }
    }

    private interface Setter { void set(int v); }

    // ------------------------------------------------------------------ 👂 your fingers (2.3)
    private TextView touchState;

    private void touchSection(LinearLayout top) {
        LinearLayout f = Section.add(top, "set.touch", "👂 Atingerile tale (pentru scripturi)",
                prefs.touchDirect() ? "ascultare directă" : "prin strat, când scriptul ascultă").body;
        touchState = Ui.muted(this, "");
        f.addView(touchState);
        if (TouchListener.directAvailable()) {
            f.addView(Ui.button(this, "Testează ascultarea directă", new View.OnClickListener() {
                @Override public void onClick(View v) { touchTest(); }
            }));
            f.addView(Ui.button(this, "Oprește ascultarea directă", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.setTouchDirect(false);
                    ClickService s = ClickService.get();
                    if (s != null) s.setTouchDirect(false);
                    updateTouchState();
                }
            }));
        }
        f.addView(Ui.muted(this, "Funcțiile care ascultă: asteaptaAtingere(), asteaptaGest(), asteaptaDegete(2), degete(), "
                + "asteaptaFaraDegete(), asteaptaAtingereInZona(z), alegeZona(), pipetaAtingere(); cu asculta(true): degete(), atins(), atingeriNoi()… (📖 Documentație)."));
        updateTouchState();
    }

    private void updateTouchState() {
        if (touchState == null) return;
        String how;
        if (prefs.touchDirect()) {
            how = "✅ Ascultare directă: cât un script ascultă, jocul primește atingerile imediat și FreeClick aude o copie. "
                    + "Fără un script care ascultă, FreeClick nu aude nimic.";
        } else if (TouchListener.directAvailable()) {
            how = "Telefonul tău ar putea avea ascultare directă (jocul primește atingerea imediat). Testează-o: durează 5 secunde.\n"
                    + "Până atunci: doar cât un script ascultă, un strat prinde gestul și îl trimite mai departe în joc după ce ridici degetul. "
                    + "Altfel nu e nimic în cale.";
        } else {
            how = "Doar cât un script ascultă, un strat prinde gestul tău și îl trimite mai departe în joc după ce ridici degetul "
                    + "(o mică întârziere; un deget ținut apăsat ajunge în joc abia când îl ridici). Fără un script care ascultă, "
                    + "nu e nimic în cale. Ascultarea directă cere Android 15.";
        }
        touchState.setText(how);
    }

    /** Turns Android's copies on, asks for 3 taps on a square, keeps them only if the taps both arrived and were heard. */
    private void touchTest() {
        final ClickService s = ClickService.get();
        if (s == null) { Toast.makeText(this, "Pornește întâi FreeClick (serviciul de accesibilitate).", Toast.LENGTH_LONG).show(); return; }
        if (!s.testTouchDirect(true)) {
            s.testTouchDirect(false);
            s.setTouchDirect(prefs.touchDirect());
            Toast.makeText(this, "Telefonul tău nu permite ascultarea directă.", Toast.LENGTH_LONG).show();
            return;
        }
        final long heard0 = s.touchesHeard();
        final int[] taps = {0};
        final boolean[] done = {false};
        final TextView box = Ui.text(this, "Atinge pătratul de 3 ori", 18, Ui.TEXT, true);
        box.setGravity(android.view.Gravity.CENTER);
        box.setBackground(Ui.surface(this, Ui.SURFACE_HIGH, 18));
        box.setMinHeight(Ui.dp(this, 220));
        final android.app.AlertDialog d = new android.app.AlertDialog.Builder(this).setTitle("👂 Test: ascultare directă")
                .setView(box).setNegativeButton("Anulează", null).create();
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable finish = new Runnable() { public void run() {
            if (done[0]) return;
            done[0] = true;
            long heard = s.touchesHeard() - heard0;
            String msg;
            s.testTouchDirect(false);
            if (taps[0] >= 1 && heard >= 2) {
                prefs.setTouchDirect(true);
                s.setTouchDirect(true);
                msg = "✅ Merge: atingerile ajung la ecran și FreeClick le aude. O folosesc doar cât un script ascultă.";
            } else {
                s.setTouchDirect(false);
                prefs.setTouchDirect(false);
                msg = taps[0] == 0 ? "Nu a ajuns nicio atingere la pătrat, așa că am oprit-o. Rămâne stratul."
                        : "Atingerile ajung, dar Android nu i le trimite lui FreeClick pe telefonul ăsta. Rămâne stratul.";
            }
            if (d.isShowing()) d.dismiss();
            Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show();
            updateTouchState();
        } };
        box.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                    taps[0]++;
                    box.setText("Atinge pătratul de 3 ori\n" + taps[0] + " / 3 · auzite: " + (s.touchesHeard() - heard0));
                    if (taps[0] >= 3) h.postDelayed(finish, 300);
                }
                return true;
            }
        });
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface di) { h.post(finish); }
        });
        d.show();
        h.postDelayed(finish, 6000); // touches held back by Android would end here, by themselves
    }

    /** Whether Android may stop FreeClick to save battery, and the system's own dialog to say no. */
    private TextView batteryState;

    private void batteryRow(LinearLayout f) {
        batteryState = Ui.text(this, "", 14, Ui.TEXT, false);
        batteryState.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        f.addView(batteryState);
        f.addView(Ui.button(this, "Nu lăsa Android să oprească FreeClick", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    if (Build.VERSION.SDK_INT >= 23 && !ignoringBatteryOptimizations()) {
                        startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } else {
                        startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                } catch (RuntimeException e) {
                    Toast.makeText(SettingsActivity.this, "Deschide Setări → Aplicații → FreeClick → Baterie → Nerestricționat.", Toast.LENGTH_LONG).show();
                }
            }
        }));
        f.addView(Ui.muted(this, "Pe Samsung, în plus: Setări → Baterie → Limite de utilizare în fundal → "
                + "„Aplicații care nu intră niciodată în repaus” → adaugă FreeClick."));
        updateBatteryState();
    }

    private boolean ignoringBatteryOptimizations() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void updateBatteryState() {
        if (batteryState == null) return;
        boolean ok = ignoringBatteryOptimizations();
        batteryState.setText(ok ? "✅ Android nu oprește FreeClick ca să economisească bateria."
                : "⚠ Android poate opri FreeClick ca să economisească bateria (mai ales după ore).");
    }

    @Override protected void onResume() {
        super.onResume();
        updateBatteryState();
    }

    private void seek(LinearLayout f, final String label, final int min, int max, int value, final String unit, final Setter s) {
        final TextView t = Ui.text(this, label + ": " + value + unit, 15, Ui.TEXT, false);
        t.setPadding(0, Ui.dp(this, 10), 0, 0);
        f.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                t.setText(label + ": " + (progress + min) + unit);
                if (fromUser) s.set(progress + min);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        f.addView(sb, Ui.matchWrap());
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { Ui.goBack(this); }

    private static final int REQ_BACKUP = 21;
    private static final int REQ_RESTORE = 22;

    private void startBackup() {
        String name = "FreeClick_backup_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                .format(new java.util.Date()) + ".zip";
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("application/zip")
                .putExtra(android.content.Intent.EXTRA_TITLE, name);
        try { startActivityForResult(i, REQ_BACKUP); } catch (RuntimeException e) { toast("Nu există selector de fișiere"); }
    }

    private void startRestore() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*");
        try { startActivityForResult(i, REQ_RESTORE); } catch (RuntimeException e) { toast("Nu există selector de fișiere"); }
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final MacroRepository repo = App.get().repo();
        final android.net.Uri uri = data.getData();
        final boolean backup = req == REQ_BACKUP;
        if (req != REQ_BACKUP && req != REQ_RESTORE) return;
        toast(backup ? "Salvez backup-ul…" : "Restaurez…");
        // Files with many pictures can take a moment: never on the screen's thread.
        new Thread(new Runnable() { public void run() {
            String msg;
            boolean ok = false;
            try {
                if (backup) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                    if (os == null) throw new java.io.IOException("Nu pot scrie fișierul");
                    repo.writeBackupZip(os, App.get().images());
                    msg = "Backup salvat (" + repo.list().size() + " salvări, cu imagini)";
                } else {
                    MacroRepository.ImportResult r = repo.importStream(getContentResolver().openInputStream(uri), App.get().images());
                    msg = "Restaurat: " + r.macros + " salvări" + (r.images > 0 ? " și " + r.images + " imagini" : "")
                            + (r.badImages > 0 ? " (" + r.badImages + " imagini stricate sărite)" : "")
                            + (r.letters > 0 ? " · " + r.letters + " litere învățate" : "");
                    ok = true;
                }
            } catch (Exception e) {
                msg = "Eroare: " + e.getMessage();
            }
            final String m = msg;
            final boolean refresh = ok;
            runOnUiThread(new Runnable() { public void run() {
                toast(m);
                if (refresh && !isFinishing()) recreate();
            } });
        } }, "freeclick-backup").start();
    }

    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }
}
