package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.Toast;

import com.freeclick.core.Macro;


/** Home: one clear panel status, direct automation entry points, and a compact library link. */
public final class MainActivity extends Activity {
    private LinearLayout content;
    private Switch state;
    private boolean updating;
    private boolean renderedServiceOn;
    private String renderedMode = "";
    private ClickService listenedTo;
    private View scrim;
    private LinearLayout drawer;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { runOnUiThread(new Runnable() { public void run() { syncState(); } }); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        FrameLayout frame = new FrameLayout(this);
        LinearLayout root = Ui.vertical(this);
        frame.addView(root);
        LinearLayout header = Ui.header(this, "FreeClick", true, true, new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(); }
        }, null);
        drawerBtn = header.getChildAt(0);
        TextView brand = (TextView) header.getChildAt(1);
        android.text.SpannableString sp = new android.text.SpannableString("FreeClick");
        sp.setSpan(new android.text.style.ForegroundColorSpan(Ui.ACCENT), 4, 9, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(sp);
        root.addView(header);
        ScrollView scroll = new ScrollView(this);
        content = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        content.setPadding(p, 0, p, Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        buildDrawer(frame);
        setContentView(frame);
    }

    @Override protected void onResume() {
        super.onResume();
        listen();
        render();
        if (!tutorialShown && !App.get().prefs().sectionOpen("tutorial.app.v1")) {
            tutorialShown = true; // once per opening
            content.postDelayed(new Runnable() { public void run() { tutorial(); } }, 500);
        }
    }

    // ------------------------------------------------------------------ tutorial (2.1)
    private View drawerBtn, stateRow, cardManual, cardRecord, cardCombo, cardCode;
    private boolean renderedPanelOn;
    private boolean tutorialShown;

    private void tutorial() {
        if (isFinishing()) return;
        java.util.List<TutorialOverlay.Step> l = new java.util.ArrayList<TutorialOverlay.Step>();
        l.add(new TutorialOverlay.Step(null, "Bun venit în FreeClick! Îți arăt pe scurt ce e unde. Atinge oriunde ca să continui."));
        l.add(new TutorialOverlay.Step(stateRow, "Panoul plutitor: pornește sau oprește comenzile care stau peste jocuri și aplicații. "
                + "La prima folosire, activează serviciul FreeClick în setările de accesibilitate Android."));
        l.add(new TutorialOverlay.Step(cardManual, "Ținte manuale: alege modul, apoi pui puncte pe ecran cu + și le atinge în ordine, "
                + "cu intervalul și durata din Opțiuni. Cel mai simplu mod."));
        l.add(new TutorialOverlay.Step(cardRecord, "Înregistrare: apeși ●, faci tu atingerile, iar ▶ le repetă exact, cu aceleași pauze."));
        l.add(new TutorialOverlay.Step(cardCombo, "Combinații: pui mai multe salvări una după alta (și scripturi), cu repetări."));
        l.add(new TutorialOverlay.Step(cardCode, "Cod: programe în stil C++ (if, while, funcții), cu captura jocului lângă cod "
                + "ca să alegi punctele pe ea. Are tutorialul lui."));
        l.add(new TutorialOverlay.Step(drawerBtn, "☰: automatizările salvate, filmările, setările și ajutorul. "
                + "Pașii apar doar când deschizi un editor."));
        l.add(new TutorialOverlay.Step(null, "Oprire de urgență oricând: apasă de două ori repede volum jos. Spor la jucat!"));
        TutorialOverlay.show(this, l.toArray(new TutorialOverlay.Step[0]), new TutorialOverlay.OnClosed() {
            @Override public void onClosed(boolean neverAgain) { if (neverAgain) App.get().prefs().setSectionOpen("tutorial.app.v1", true); }
        });
    }

    @Override protected void onPause() {
        if (listenedTo != null) listenedTo.removeListener(refresh);
        listenedTo = null;
        super.onPause();
    }

    private void listen() {
        ClickService s = ClickService.get();
        if (s == listenedTo) return;
        if (listenedTo != null) listenedTo.removeListener(refresh);
        listenedTo = s;
        if (s != null) s.addListener(refresh);
    }

    /**
     * Called when the panel/service changes: update the switch in place. The whole
     * screen is rebuilt only if something structural changed (service on/off, mode),
     * so a view is never replaced under your finger.
     */
    private void syncState() {
        listen();
        ClickService s = ClickService.get();
        if ((s != null) != renderedServiceOn || !App.get().prefs().mode().equals(renderedMode)
                || (s != null && s.isPanelShown()) != renderedPanelOn) { // the card's colour and text follow the panel
            render();
            return;
        }
        if (state != null) {
            updating = true;
            state.setChecked(s != null && s.isPanelShown());
            updating = false;
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (TutorialOverlay.close(this)) return;
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) { closeDrawer(); return; }
        super.onBackPressed();
    }

    private void render() {
        content.removeAllViews();
        final ClickService svc = ClickService.get();
        renderedServiceOn = svc != null;
        renderedMode = App.get().prefs().mode();

        // ---- State
        // the panel's switch, as a card that lights up when the panel is on
        boolean panelOn = svc != null && svc.isPanelShown();
        renderedPanelOn = panelOn;
        LinearLayout row = Ui.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        android.graphics.drawable.GradientDrawable rowBg = Ui.rounded(panelOn ? 0xFF1B2C34 : Ui.SURFACE, Ui.dp(this, 14));
        rowBg.setStroke(Ui.dp(this, panelOn ? 1.5f : 1), panelOn ? Ui.ACCENT : 0xFF252F3D);
        row.setBackground(rowBg);
        TextView panelIcon = Ui.text(this, "◈", 21, Ui.ACCENT, true);
        panelIcon.setGravity(Gravity.CENTER);
        panelIcon.setBackground(Ui.rounded(0x2242D6B0, Ui.dp(this, 20)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38));
        iconLp.rightMargin = Ui.dp(this, 10);
        row.addView(panelIcon, iconLp);
        LinearLayout stateTexts = Ui.vertical(this);
        stateTexts.addView(Ui.text(this, svc == null ? "Acces necesar" : "Panou plutitor", 15, Ui.TEXT, true));
        stateTexts.addView(Ui.text(this, svc == null ? "Activează FreeClick ca să automatizezi atingerile"
                : panelOn ? "Pornit · comenzi peste aplicații" : "Oprit · alege un mod pentru a începe", 12,
                panelOn ? Ui.ACCENT : Ui.MUTED, false));
        row.addView(stateTexts, Ui.weight(1));
        if (svc == null) {
            android.widget.Button access = Ui.colored(this, "Activează", Ui.ACCENT, new View.OnClickListener() {
                @Override public void onClick(View v) { openAccessibilitySettings(); }
            });
            access.setTextSize(12);
            access.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
            row.addView(access);
        } else {
            state = new Switch(this);
            updating = true;
            state.setChecked(svc.isPanelShown());
            updating = false;
            state.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                    if (updating) return;
                    ClickService s = ClickService.get();
                    if (s == null) { syncState(); askToEnable(); return; }
                    if (on) s.showPanel(); else s.hidePanel();
                    syncState();
                }
            });
            row.addView(state);
        }
        stateRow = row;
        // The whole "Stare" row toggles, not only the small switch.
        Ui.clickable(row, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ClickService.get() == null) openAccessibilitySettings();
                else if (state != null) state.toggle();
            }
        }, Ui.dp(this, 12));
        LinearLayout.LayoutParams rowLp = Ui.matchWrap();
        rowLp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        content.addView(row, rowLp);

        // ---- Modes
        content.addView(Ui.sectionLabel(this, "CREEAZĂ SAU PORNEȘTE"));
        String mode = App.get().prefs().mode();
        cardManual = Ui.homeModeCard(this, "◎", Ui.ACCENT, "Manual", "Atinge ținte", mode.equals(Macro.KIND_MANUAL), new View.OnClickListener() {
            @Override public void onClick(View v) { openMode(Macro.KIND_MANUAL); }
        });
        cardRecord = Ui.homeModeCard(this, "●", Ui.DANGER, "Înregistrare", "Captează gesturi", mode.equals(Macro.KIND_RECORD), new View.OnClickListener() {
            @Override public void onClick(View v) { openMode(Macro.KIND_RECORD); }
        });
        cardCombo = Ui.homeModeCard(this, "⊞", 0xFF9B83FF, "Combinații", "Leagă macro-uri", mode.equals(Macro.KIND_COMBO), new View.OnClickListener() {
            @Override public void onClick(View v) { openMode(Macro.KIND_COMBO); }
        });
        cardCode = Ui.homeModeCard(this, "</>", 0xFF55D9C2, "Cod", "Construiește scripturi", false, new View.OnClickListener() {
                    @Override public void onClick(View v) { openCode(); }
                });
        content.addView(cardManual, Ui.matchWrap());
        content.addView(cardRecord, Ui.matchWrap());
        content.addView(cardCombo, Ui.matchWrap());
        content.addView(cardCode, Ui.matchWrap());

        LinearLayout saved = Ui.settingRow(this, "Automatizările mele",
                App.get().repo().list().size() + " salvări · macro-uri, combinații și scripturi", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, LibraryActivity.class)
                                .putExtra(LibraryActivity.EXTRA_KIND, LibraryActivity.KIND_ALL));
                    }
                });
        LinearLayout.LayoutParams savedLp = Ui.matchWrap();
        savedLp.topMargin = Ui.dp(this, 10);
        content.addView(saved, savedLp);

        LinearLayout emergency = Ui.horizontal(this);
        emergency.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11));
        emergency.setBackground(Ui.surface(this, Ui.SURFACE, 16));
        TextView shield = Ui.text(this, "⬡", 23, Ui.MUTED, true);
        shield.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams shieldLp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38));
        shieldLp.rightMargin = Ui.dp(this, 10);
        emergency.addView(shield, shieldLp);
        View divider = new View(this);
        divider.setBackgroundColor(Ui.DIVIDER);
        emergency.addView(divider, new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 28)));
        TextView emergencyText = Ui.text(this, "Oprire de urgență: apasă Volum jos de două ori", 12, Ui.MUTED, false);
        emergencyText.setPadding(Ui.dp(this, 12), 0, 0, 0);
        emergency.addView(emergencyText, Ui.weight(1));
        LinearLayout.LayoutParams emergencyLp = Ui.matchWrap();
        emergencyLp.topMargin = Ui.dp(this, 12);
        content.addView(emergency, emergencyLp);
    }

    private void openCode() {
        startActivity(new Intent(this, LibraryActivity.class).putExtra(LibraryActivity.EXTRA_KIND, Macro.KIND_SCRIPT));
    }

    private View modeCard(final String kind, String title, String desc, String selected) {
        String icon = Macro.KIND_MANUAL.equals(kind) ? "◎" : Macro.KIND_RECORD.equals(kind) ? "●" : "⊞";
        int tint = Macro.KIND_MANUAL.equals(kind) ? Ui.ACCENT : Macro.KIND_RECORD.equals(kind) ? Ui.DANGER : 0xFF9B83FF;
        return Ui.modeCard(this, icon, tint, title, desc, kind.equals(selected), new View.OnClickListener() {
            @Override public void onClick(View v) { openMode(kind); }
        });
    }

    /** Tapping a mode turns it on right away: the panel for that mode appears (no extra switch needed). */
    private void openMode(String kind) {
        App.get().prefs().setMode(kind);
        ClickService s = ClickService.get();
        if (s == null) {
            syncState();
            askToEnable();
            return;
        }
        s.switchMode(kind);
        if (!s.isPanelShown()) s.showPanel();
        syncState();
        Macro w = s.workspace();
        int n = w == null ? 0 : com.freeclick.core.ActionTree.count(w.actions);
        boolean saved = s.isWorkspaceSaved();
        String what = saved ? "„" + w.name + "”" : "";
        String hint;
        if (Macro.KIND_COMBO.equals(kind)) {
            if (n == 0) {
                // Nothing to play yet: go straight to the combinations list.
                startActivity(new Intent(this, LibraryActivity.class)
                        .putExtra(LibraryActivity.EXTRA_KIND, Macro.KIND_COMBO));
                hint = "Alege o combinație sau creează una cu +.";
            } else {
                hint = "Panoul e pornit: ▶ rulează combinația " + what + ".";
            }
        } else if (Macro.KIND_RECORD.equals(kind)) {
            hint = n == 0 ? "Panoul e pornit: apasă ● ca să înregistrezi, apoi ■ și ▶."
                    : "Panoul e pornit: ▶ redă înregistrarea " + what + " (" + n + " acțiuni), ● înregistrează alta.";
        } else {
            hint = n == 0 ? "Panoul e pornit: apasă + ca să pui ținte, apoi ▶."
                    : "Panoul e pornit cu " + (saved ? what + ", " : "") + n + " ținte. ▶ pornește.";
        }
        Toast.makeText(this, hint, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ side drawer
    private void buildDrawer(FrameLayout frame) {
        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });
        frame.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        drawer = Ui.vertical(this);
        drawer.setBackgroundColor(Ui.SURFACE);
        drawer.setClickable(true); // touches on the drawer never fall through to the screen below
        drawer.setElevation(Ui.dp(this, 12));
        drawer.setVisibility(View.GONE);
        int p = Ui.dp(this, 20);
        drawer.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 16));
        TextView name = Ui.text(this, "FreeClick", 26, Ui.TEXT, true);
        name.setPadding(p, 0, p, 0);
        drawer.addView(name);
        TextView sub = Ui.muted(this, "Gratuit · fără reclame · fără internet");
        sub.setPadding(p, Ui.dp(this, 2), p, Ui.dp(this, 16));
        drawer.addView(sub);
        drawer.addView(Ui.divider(this));
        drawerItem(android.R.drawable.ic_menu_agenda, "Toate salvările", new Runnable() { public void run() {
            startActivity(new Intent(MainActivity.this, LibraryActivity.class)
                    .putExtra(LibraryActivity.EXTRA_KIND, LibraryActivity.KIND_ALL)); } });
        drawerItem(android.R.drawable.ic_menu_camera, "Filmări", new Runnable() { public void run() {
            startActivity(new Intent(MainActivity.this, VideoListActivity.class)); } });
        drawerItem(android.R.drawable.ic_menu_preferences, "Setări", new Runnable() { public void run() {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } });
        drawerItem(android.R.drawable.ic_menu_help, "Ajutor", new Runnable() { public void run() { help(); } });
        drawerItem(android.R.drawable.ic_menu_search, "Documentație", new Runnable() { public void run() {
            startActivity(new Intent(MainActivity.this, DocsActivity.class)); } });
        drawerItem(android.R.drawable.ic_menu_info_details, "Tutorial", new Runnable() { public void run() {
            closeDrawer();
            content.postDelayed(new Runnable() { public void run() { tutorial(); } }, 300);
        } });
        drawerItem(android.R.drawable.ic_menu_view, "Tutorialul panoului", new Runnable() { public void run() {
            closeDrawer();
            ClickService s = ClickService.get();
            if (s == null) { askToEnable(); return; }
            if (!s.isPanelShown()) s.showPanel();
            moveTaskToBack(true);
            content.postDelayed(new Runnable() { public void run() { ClickService x = ClickService.get(); if (x != null) x.panelTutorial(); } }, 900);
        } });
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Ui.dp(this, 290),
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START);
        frame.addView(drawer, dl);
    }

    private void drawerItem(int icon, String label, final Runnable action) {
        LinearLayout row = Ui.horizontal(this);
        int p = Ui.dp(this, 20);
        row.setPadding(p, Ui.dp(this, 15), p, Ui.dp(this, 15));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.TEXT);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24));
        ip.rightMargin = Ui.dp(this, 18);
        row.addView(iv, ip);
        row.addView(Ui.text(this, label, 17, Ui.TEXT, true));
        Ui.clickable(row, new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeDrawer();
                action.run(); // open right away, no second tap needed
            }
        }, 0);
        drawer.addView(row, Ui.matchWrap());
    }

    private void openDrawer() {
        if (drawer.getVisibility() == View.VISIBLE) return;
        int w = Ui.dp(this, 290);
        drawer.setTranslationX(-w);
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(180).setListener(null).start();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(180).start();
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        drawer.animate().cancel();
        drawer.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
    }

    private void help() {
        new AlertDialog.Builder(this).setTitle("Cum se folosește")
                .setMessage("1. Alege un mod și pornește „Stare”.\n"
                        + "2. Deschide jocul sau aplicația.\n\n"
                        + "Ținte manuale: + adaugă tap/swipe, − șterge ultima țintă, mută țintele cu degetul, "
                        + "atinge o țintă ca s-o setezi. ▶ pornește.\n\n"
                        + "Mod înregistrare: ● înregistrează ce faci (inclusiv pauzele), ■ oprește, ▶ redă, "
                        + "− șterge ultima acțiune.\n\n"
                        + "Ține apăsat − ca să ștergi tot. ≡ → Ascunde punctele le face invizibile "
                        + "(rulează la fel); permanent: ☰ → Setări → Puncte pe ecran.\n\n"
                        + "Combină: ≡ → Combinații → + creează una din înregistrările salvate.\n\n"
                        + "≡ → Opțiuni: salvare, încărcare, interval, durate, repetări, viteză, acțiuni aleatoare "
                        + "și opțiunile noi (condiții de culoare, deschide aplicația, filmare, mod sigur).\n\n"
                        + "În redare: ■ stop, ⏸ pauză, 1× schimbă viteza pe loc. Dacă atingi ecranul, "
                        + "macro-ul intră în pauză; ▶ reia.\n\n"
                        + "Greșeli: ≡ → Anulează (și Refă). După o ștergere apare 5 s butonul ANULEAZĂ.\n\n"
                        + "≡ → Previzualizează: vezi unde ar atinge, fără să atingă nimic.\n\n"
                        + "+ → Imagine: decupezi un buton de pe ecran; macro-ul îl caută și îl apasă oriunde apare. "
                        + "Atinge ținta ca să alegi ce face și Testează acum.\n\n"
                        + "Editor: ↩ ↪, Previzualizare, Unelte (scurtează pauzele), ține apăsat pe rânduri ca să selectezi, "
                        + "⋮ → Rulează de aici.\n\n"
                        + "+ → Citește număr / text din zonă: tragi un dreptunghi peste un număr; valoarea intră într-o variabilă. "
                        + "+ → Afișează în consolă: „Aur: {numar1}”. ≡ → Consolă o arată. Dacă citește greșit: "
                        + "Testează citirea → scrii corect → ÎNVAȚĂ.\n\n"
                        + "≡ → Pipetă: află codul unei culori (#HEX, RGB, HSV). Culoarea unui pas o poți scrie și exact.\n\n"
                        + "Setările rare stau în Avansat, câte un meniu pe arie (Locație, Timp, Repetare…).\n\n"
                        + "Cod (scripturi): scrii un program în stil C++ (tap, swipe, pixel, citeste, if, while, funcții, "
                        + "vector, struct). „+” scrie cod din ce alegi pe ecran; ⋮ → Tutorial și Exemple.\n"
                        + "Oprire de urgență: volum jos de 2 ori.")
                .setPositiveButton("OK", null).show();
    }

    private void askToEnable() {
        new AlertDialog.Builder(this).setTitle("Serviciul e oprit")
                .setMessage("Activează FreeClick în Setări → Accesibilitate ca să poți folosi panoul.")
                .setPositiveButton("Deschide setările", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { openAccessibilitySettings(); }
                })
                .setNegativeButton("Anulează", null).show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
        Toast.makeText(this, "Caută „FreeClick” în listă și activează-l", Toast.LENGTH_LONG).show();
    }
}
