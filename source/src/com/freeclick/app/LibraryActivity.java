package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.freeclick.core.ActionFormatter;
import com.freeclick.core.Macro;
import com.freeclick.core.MacroCodec;
import com.freeclick.core.MacroGraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Saved items of one mode (or all): import, search, sort, load and manage. */
public final class LibraryActivity extends Activity implements MacroRepository.ChangeListener {
    public static final String EXTRA_KIND = "kind";
    public static final String KIND_ALL = "all";
    private static final int REQ_IMPORT = 10;
    private static final int REQ_EXPORT = 11;

    private String kind;
    private LinearLayout list;
    private MacroRepository repo;
    private String query = "";
    private boolean byName;
    private String pendingExportId;
    private TextView countSummary;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        repo = App.get().repo();
        kind = getIntent().getStringExtra(EXTRA_KIND);
        if (kind == null) kind = KIND_ALL;
        if (b != null) pendingExportId = b.getString("export");

        FrameLayout frame = new FrameLayout(this);
        LinearLayout root = Ui.vertical(this);
        frame.addView(root);
        root.addView(Ui.header(this, title(), false, false, null, null));
        countSummary = Ui.text(this, "", 12, Ui.MUTED, false);
        countSummary.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 4));
        root.addView(countSummary);
        LinearLayout top = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        top.setPadding(p, 0, p, 0);
        top.addView(Ui.actionRow(this, "Importă sau partajează salvări", new View.OnClickListener() {
            @Override public void onClick(View v) { importMenu(v); }
        }));
        pasteBanner = Ui.vertical(this);
        top.addView(pasteBanner);
        LinearLayout searchRow = Ui.horizontal(this);
        searchRow.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        searchRow.addView(Ui.searchField(this, new Ui.OnText() {
            @Override public void onText(String s) { query = s.trim().toLowerCase(Locale.ROOT); render(); }
        }), Ui.weight(1));
        final TextView sort = Ui.text(this, "↕", 18, Ui.TEXT, true);
        sort.setGravity(Gravity.CENTER);
        sort.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 14)));
        sort.setContentDescription("Sortează");
        Ui.clickable(sort, new View.OnClickListener() {
            @Override public void onClick(View v) {
                byName = !byName;
                Toast.makeText(LibraryActivity.this, byName ? "Sortare după nume" : "Sortare după dată", Toast.LENGTH_SHORT).show();
                render();
            }
        }, Ui.dp(this, 22));
        searchRow.addView(sort, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        top.addView(searchRow, Ui.matchWrap());
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        list = Ui.vertical(this);
        list.setPadding(p, 0, p, Ui.dp(this, 96));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        if (Macro.KIND_COMBO.equals(kind) || Macro.KIND_SCRIPT.equals(kind)) {
            final boolean script = Macro.KIND_SCRIPT.equals(kind);
            TextView fab = Ui.fab(this, new View.OnClickListener() {
                @Override public void onClick(View v) { if (script) newScript(); else newCombination(); }
            });
            fab.setContentDescription(script ? "Script nou" : "Combinație nouă");
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60),
                    Gravity.BOTTOM | Gravity.END);
            fl.setMargins(0, 0, Ui.dp(this, 20), Ui.dp(this, 24));
            frame.addView(fab, fl);
        }
        setContentView(frame);
        repo.addListener(this);
    }

    private String title() {
        if (Macro.KIND_MANUAL.equals(kind)) return "Presetări ținte";
        if (Macro.KIND_RECORD.equals(kind)) return "Înregistrări";
        if (Macro.KIND_COMBO.equals(kind)) return "Combinații";
        if (Macro.KIND_SCRIPT.equals(kind)) return "Cod (scripturi)";
        return "Toate salvările";
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("export", pendingExportId);
    }

    @Override protected void onResume() {
        super.onResume();
        ClickService s = ClickService.get();
        if (s != null) s.onOptionsScreen(true);
        render();
    }

    @Override protected void onPause() {
        ClickService s = ClickService.get();
        if (s != null) s.onOptionsScreen(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        repo.removeListener(this);
        super.onDestroy();
    }

    @Override public void onMacrosChanged() {
        runOnUiThread(new Runnable() { public void run() { render(); } });
    }

    private void render() {
        list.removeAllViews();
        List<Macro> items = new ArrayList<Macro>();
        for (Macro m : repo.list()) {
            if (!KIND_ALL.equals(kind) && !kind.equals(m.kind)) continue;
            if (!query.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            items.add(m);
        }
        String tapAction = Macro.KIND_SCRIPT.equals(kind) ? "deschizi"
                : KIND_ALL.equals(kind) ? "deschizi sau încarci" : "încarci";
        countSummary.setText(items.size() + (items.size() == 1 ? " salvare" : " salvări")
                + (query.isEmpty() ? " · atinge un rând ca să " + tapAction
                + " · ordonate după " + (byName ? "nume" : "activitate recentă") : " · rezultate"));
        if (byName) {
            Collections.sort(items, new Comparator<Macro>() {
                @Override public int compare(Macro a, Macro b) { return a.name.compareToIgnoreCase(b.name); }
            });
        }
        if (items.isEmpty()) {
            LinearLayout empty = Ui.vertical(this);
            empty.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20));
            empty.setBackground(Ui.surface(this, Ui.SURFACE, 18));
            empty.addView(Ui.text(this, query.isEmpty() ? "Încă nu ai nimic aici" : "Niciun rezultat găsit", 17, Ui.TEXT, true));
            TextView emptyDetail = Ui.muted(this, query.isEmpty()
                    ? (Macro.KIND_COMBO.equals(kind) ? "Nicio combinație încă. Apasă + ca să creezi una."
                    : Macro.KIND_SCRIPT.equals(kind) ? "Niciun script încă. Apasă + ca să scrii primul (îți arăt pe loc cum)."
                    : "Alege un mod din ecranul principal și salvează de acolo când ai terminat. Salvările apar aici.")
                    : "Niciun rezultat.");
            emptyDetail.setPadding(0, Ui.dp(this, 6), 0, 0);
            empty.addView(emptyDetail);
            list.addView(empty);
        }
        for (final Macro m : items) {
            // a calm row: what kind it is (badge), the name, and one short line (when · how much)
            LinearLayout row = Ui.horizontal(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10));
            row.setBackground(Ui.surface(this, Ui.SURFACE, 16));
            TextView badge = Ui.text(this, kindIcon(m.kind), 18, Ui.TEXT, false);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.rounded((kindTint(m.kind) & 0x00FFFFFF) | 0x33000000, Ui.dp(this, 20)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
            bl.rightMargin = Ui.dp(this, 12);
            row.addView(badge, bl);
            LinearLayout texts = Ui.vertical(this);
            TextView name = Ui.text(this, m.name, 16, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name);
            StringBuilder meta = new StringBuilder(when(m.updatedAt));
            if (Macro.KIND_SCRIPT.equals(m.kind)) {
                int lines = m.script == null || m.script.isEmpty() ? 0 : m.script.split("\n", -1).length;
                meta.append(" · ").append(lines).append(lines == 1 ? " linie" : " linii");
            } else {
                int n = com.freeclick.core.ActionTree.count(m.actions);
                meta.append(" · ").append(n).append(n == 1 ? " pas" : " pași").append(" · ~")
                        .append(ActionFormatter.ms(MacroGraph.estimateDurationMs(m, repo)));
            }
            Schedules.Entry e = Schedules.get(this, m.id);
            if (e != null) meta.append(String.format(Locale.US, " · programat %02d:%02d", e.hour, e.minute));
            if (AutoStart.appOf(m) != null) meta.append(" · pornire automată");
            TextView mt = Ui.text(this, meta.toString(), 13, Ui.MUTED, false);
            mt.setSingleLine(true);
            mt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(mt);
            row.addView(texts, Ui.weight(1));
            TextView open = Ui.text(this, "›", 25, Ui.ACCENT, true);
            open.setGravity(Gravity.CENTER);
            open.setContentDescription(Macro.KIND_SCRIPT.equals(m.kind) ? "Deschide scriptul" : "Încarcă salvarea în panou");
            row.addView(open, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 42)));
            final TextView more = Ui.text(this, "⋮", 24, Ui.TEXT, false);
            more.setGravity(Gravity.CENTER);
            Ui.clickable(more, new View.OnClickListener() {
                @Override public void onClick(View v) { menu(v, m); }
            }, Ui.dp(this, 22));
            row.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
            Ui.clickable(row, new View.OnClickListener() {
                @Override public void onClick(View v) { if (Macro.KIND_SCRIPT.equals(m.kind)) openCode(m); else loadIntoPanel(m); }
            }, Ui.dp(this, 16));
            LinearLayout.LayoutParams rl = Ui.matchWrap();
            rl.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            list.addView(row, rl);
        }
    }

    static String kindIcon(String k) {
        if (Macro.KIND_RECORD.equals(k)) return "●";
        if (Macro.KIND_COMBO.equals(k)) return "⊞";
        if (Macro.KIND_SCRIPT.equals(k)) return "</>";
        return "◎";
    }

    static int kindTint(String k) {
        if (Macro.KIND_RECORD.equals(k)) return Ui.DANGER;
        if (Macro.KIND_COMBO.equals(k)) return 0xFF9B83FF;
        if (Macro.KIND_SCRIPT.equals(k)) return Ui.ACCENT;
        return Ui.ACCENT;
    }

    /** "azi, 14:32" / "ieri, 09:05" / "12 nov." / "12 nov. 2025": how people say it, no seconds. */
    static String when(long t) {
        java.util.Calendar now = java.util.Calendar.getInstance(), c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
        boolean sameYear = now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR);
        int dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - c.get(java.util.Calendar.DAY_OF_YEAR);
        if (sameYear && dayDiff == 0) return "azi, " + hm;
        if (sameYear && dayDiff == 1) return "ieri, " + hm;
        String[] luni = {"ian.", "feb.", "mar.", "apr.", "mai", "iun.", "iul.", "aug.", "sept.", "oct.", "nov.", "dec."};
        String d = c.get(java.util.Calendar.DAY_OF_MONTH) + " " + luni[c.get(java.util.Calendar.MONTH)];
        return sameYear ? d : d + " " + c.get(java.util.Calendar.YEAR);
    }

    static String kindName(String k) {
        if (Macro.KIND_RECORD.equals(k)) return "înregistrare";
        if (Macro.KIND_COMBO.equals(k)) return "combinație";
        if (Macro.KIND_SCRIPT.equals(k)) return "script";
        return "ținte";
    }

    private void menu(final View anchor, final Macro m) {
        final boolean script = Macro.KIND_SCRIPT.equals(m.kind);
        // the 3 used most, the rest under "Avansat ›" (same as the panel's ≡)
        List<String> labels = new ArrayList<String>(Arrays.asList(script ? "Rulează" : "Încarcă în panou", script ? "Editează codul" : "Editează",
                "Redenumește", "Avansat  ›"));
        final List<String> l = labels;
        Ui.popup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int i) {
                if (l.get(i).startsWith("Avansat")) {
                    anchor.post(new Runnable() { public void run() { advancedMenu(anchor, m); } });
                    return;
                }
                act(l.get(i), m);
            }
        });
    }

    private void advancedMenu(View anchor, final Macro m) {
        final boolean script = Macro.KIND_SCRIPT.equals(m.kind);
        List<String> labels = new ArrayList<String>();
        if (script) labels.add("Lista de pași");
        else labels.add("Fă script din ea");
        labels.addAll(Arrays.asList("Partajează…", "Duplică", "Pornire automată (" + AutoStart.describe(this, m) + ")", "Programează…"));
        if (Build.VERSION.SDK_INT >= 26) labels.add("Creează scurtătură");
        labels.add("Șterge");
        final List<String> l = labels;
        Ui.popup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int i) { act(l.get(i), m); }
        });
    }

    private void act(String what, final Macro m) {
        if (what.startsWith("Pornire automată")) { AutoStart.dialog(this, m, null); return; }
        if (what.startsWith("Fă script")) { toScript(m); return; }
        {
            {
                if (what.startsWith("Încarcă")) loadIntoPanel(m);
                else if (what.equals("Rulează")) runScript(m);
                else if (what.equals("Editează codul")) openCode(m);
                else if (what.equals("Editează") || what.equals("Lista de pași")) startActivity(new Intent(LibraryActivity.this, EditorActivity.class)
                        .putExtra(EditorActivity.EXTRA_ID, m.id));
                else if (what.equals("Redenumește")) rename(m);
                else if (what.equals("Duplică")) {
                    Macro copy = m.duplicate(m.name + " (copie)");
                    if (save(copy) && Macro.KIND_SCRIPT.equals(m.kind)) App.get().shots().copyAll(m.id, copy.id);
                }
                else if (what.startsWith("Partajează")) {
                    ShareUi.share(LibraryActivity.this, java.util.Collections.singletonList(m), new Runnable() { public void run() { startExport(m); } });
                }
                else if (what.startsWith("Programează")) schedule(m);
                else if (what.startsWith("Creează")) {
                    if (!ShortcutRunActivity.pin(LibraryActivity.this, m)) {
                        Toast.makeText(LibraryActivity.this, "Launcher-ul nu acceptă scurtături fixate", Toast.LENGTH_LONG).show();
                    }
                } else if (what.equals("Șterge")) confirmDelete(m);
            }
        }
    }

    private void loadIntoPanel(Macro m) {
        ClickService s = ClickService.get();
        if (s == null) {
            Toast.makeText(this, "Activează întâi FreeClick în Setări → Accesibilitate", Toast.LENGTH_LONG).show();
            return;
        }
        s.load(m);
        Toast.makeText(this, "Încărcat: " + m.name, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** 💻 The same touches as code: a new script (the original stays as it was). */
    private void toScript(Macro m) {
        Macro s = new Macro(m.name + " (cod)");
        s.kind = Macro.KIND_SCRIPT;
        s.settings.loops = 1;
        s.screenWidth = m.screenWidth;
        s.screenHeight = m.screenHeight;
        for (com.freeclick.core.Action a : m.actions) s.actions.add(a.copy()); // tree-ok: pasi = the same top-level steps
        s.script = com.freeclick.core.script.ScriptExamples.settingsHeader(m.settings.tapDuration, m.settings.swipeDuration,
                m.settings.tapInterval) + com.freeclick.core.script.CodeFromSteps.code(m);
        try {
            repo.save(s);
        } catch (Exception e) {
            Toast.makeText(this, "Nu am putut salva: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Gata: „" + s.name + "” (originalul a rămas neschimbat)", Toast.LENGTH_SHORT).show();
        openCode(s);
    }

    private void openCode(Macro m) {
        startActivity(new Intent(this, CodeEditorActivity.class).putExtra(CodeEditorActivity.EXTRA_ID, m.id));
    }

    private void runScript(Macro m) {
        ClickService s = ClickService.get();
        if (s == null) {
            Toast.makeText(this, "Activează întâi FreeClick în Setări → Accesibilitate", Toast.LENGTH_LONG).show();
            return;
        }
        if (s.armScript(m, false, null)) moveTaskToBack(true); // ▶ on the panel starts it, in the game
    }

    private void newScript() {
        Ui.textDialog(this, "Nume pentru scriptul nou", "Scriptul meu", "CREEAZĂ", new Ui.OnString() {
            @Override public void onValue(String name) {
                Macro c = CodeEditorActivity.newScript(LibraryActivity.this, name);
                if (save(c)) openCode(c);
            }
        }, false);
    }

    private void newCombination() {
        Ui.textDialog(this, "Nume pentru combinația nouă", "", "ADAUGĂ", new Ui.OnString() {
            @Override public void onValue(String name) {
                Macro c = new Macro(name);
                c.kind = Macro.KIND_COMBO;
                c.settings.loops = 1;
                if (save(c)) {
                    startActivity(new Intent(LibraryActivity.this, EditorActivity.class).putExtra(EditorActivity.EXTRA_ID, c.id));
                }
            }
        }, false);
    }

    private boolean save(Macro m) {
        try {
            repo.save(m);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Eroare: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void rename(final Macro m) {
        Ui.textDialog(this, "Nume nou", m.name, "OK", new Ui.OnString() {
            @Override public void onValue(String n) { m.name = n; save(m); }
        }, false);
    }

    private void confirmDelete(final Macro m) {
        new AlertDialog.Builder(this).setTitle("Ștergi „" + m.name + "”?")
                .setMessage("Combinațiile care îl folosesc îl vor sări.")
                .setPositiveButton("ȘTERGE", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Schedules.clear(LibraryActivity.this, m.id);
                        repo.delete(m.id);
                        if (Macro.KIND_SCRIPT.equals(m.kind)) App.get().shots().deleteAll(m.id);
                    }
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    private void schedule(final Macro m) {
        LinearLayout box = Ui.vertical(this);
        final TimePicker tp = new TimePicker(this);
        tp.setIs24HourView(true);
        Schedules.Entry cur = Schedules.get(this, m.id);
        if (cur != null) { tp.setHour(cur.hour); tp.setMinute(cur.minute); }
        box.addView(tp);
        final CheckBox daily = new CheckBox(this);
        daily.setText("Repetă zilnic");
        daily.setChecked(cur == null || cur.daily);
        box.addView(daily);
        box.addView(Ui.muted(this, "La ora aleasă pornește peste ce e pe ecran (sau deschide aplicația setată în "
                + "„Deschide întâi aplicația”). Telefonul trebuie să fie deblocat."));
        int p = Ui.dp(this, 16);
        box.setPadding(p, 0, p, 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Programează „" + m.name + "”").setView(sv)
                .setPositiveButton("SALVEAZĂ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Schedules.set(LibraryActivity.this, m.id, tp.getHour(), tp.getMinute(), daily.isChecked());
                        Toast.makeText(LibraryActivity.this, String.format(Locale.US, "Programat la %02d:%02d",
                                tp.getHour(), tp.getMinute()), Toast.LENGTH_SHORT).show();
                        render();
                    }
                })
                .setNegativeButton("ANULEAZĂ", null);
        if (cur != null) {
            b.setNeutralButton("ȘTERGE PROGRAMAREA", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { Schedules.clear(LibraryActivity.this, m.id); render(); }
            });
        }
        b.show();
    }

    // ------------------------------------------------------------------ 📥 / 📤 (2.3)
    private LinearLayout pasteBanner;
    private String offeredClip;

    private void importMenu(View anchor) {
        Ui.popup(anchor, Arrays.asList("Lipește un cod copiat", "Deschide un fișier", "Partajează mai multe…"), null, new Ui.OnPick() {
            @Override public void onPick(int i) {
                if (i == 0) {
                    String clip = ShareUi.clipboardCode(LibraryActivity.this);
                    if (clip == null) {
                        Toast.makeText(LibraryActivity.this, "Nu e niciun cod FreeClick copiat. Copiază mesajul cu codul (FREECLICK1:…), apoi revino.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        ShareUi.confirmImport(LibraryActivity.this, clip, new Runnable() { public void run() { render(); } });
                    }
                } else if (i == 1) {
                    startImport();
                } else {
                    pickSeveral();
                }
            }
        });
    }

    /** Several saves in one code / file. */
    private void pickSeveral() {
        final List<Macro> all = new ArrayList<Macro>(repo.list());
        if (all.isEmpty()) { Toast.makeText(this, "Nu ai nimic salvat încă.", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[all.size()];
        final boolean[] on = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) names[i] = kindIcon(all.get(i).kind) + " " + all.get(i).name;
        new AlertDialog.Builder(this).setTitle("Ce partajezi?")
                .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int w, boolean c) { on[w] = c; }
                })
                .setPositiveButton("PARTAJEAZĂ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        List<Macro> chosen = new ArrayList<Macro>();
                        for (int i = 0; i < all.size(); i++) if (on[i]) chosen.add(all.get(i));
                        if (chosen.isEmpty()) return;
                        ShareUi.share(LibraryActivity.this, chosen, null);
                    }
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    /** A code you copied in a chat is offered right away (Android lets the app read it once it is in front). */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || pasteBanner == null) return;
        final String clip = ShareUi.clipboardCode(this);
        pasteBanner.removeAllViews();
        if (clip == null || clip.equals(offeredClip)) return;
        LinearLayout card = Ui.horizontal(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        card.setBackground(Ui.surface(this, Ui.SURFACE_HIGH, 16));
        card.addView(Ui.text(this, "Cod FreeClick copiat", 15, Ui.TEXT, true), Ui.weight(1));
        card.addView(Ui.colored(this, "Importă", Ui.ACCENT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                offeredClip = clip;
                pasteBanner.removeAllViews();
                ShareUi.confirmImport(LibraryActivity.this, clip, new Runnable() { public void run() { render(); } });
            }
        }));
        TextView x = Ui.text(this, "×", 24, Ui.MUTED, false);
        x.setGravity(Gravity.CENTER);
        Ui.clickable(x, new View.OnClickListener() {
            @Override public void onClick(View v) { offeredClip = clip; pasteBanner.removeAllViews(); }
        }, Ui.dp(this, 20));
        card.addView(x, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        pasteBanner.addView(card, lp);
    }

    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Nu există selector de fișiere", Toast.LENGTH_SHORT).show();
        }
    }

    private void startExport(Macro m) {
        pendingExportId = m.id;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, fileName(m));
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Nu există selector de fișiere", Toast.LENGTH_SHORT).show();
        }
    }

    private static String fileName(Macro m) {
        String base = m.name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        if (base.isEmpty()) base = "macro";
        return base + ".freeclick.json";
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_IMPORT) {
                MacroRepository.ImportResult r = repo.importStream(getContentResolver().openInputStream(uri), App.get().images());
                Toast.makeText(this, "Importat: " + r.macros + (r.images > 0 ? " (cu " + r.images + " imagini)" : "")
                        + (r.badImages > 0 ? " · " + r.badImages + " imagini stricate au fost sărite" : ""), Toast.LENGTH_LONG).show();
            } else if (req == REQ_EXPORT) {
                Macro m = repo.find(pendingExportId);
                if (m == null) return;
                OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                if (os == null) throw new java.io.IOException("Nu pot scrie fișierul");
                try { os.write(repo.exportBundle(m, App.get().images()).getBytes("UTF-8")); } finally { os.close(); }
                Toast.makeText(this, "Exportat", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Eroare: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { Ui.goBack(this); }
}
