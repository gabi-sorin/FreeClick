package com.freeclick.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;
import com.freeclick.core.ActionTree;
import com.freeclick.core.EditHistory;
import com.freeclick.core.GlobalActions;
import com.freeclick.core.Macro;
import com.freeclick.core.MacroCodec;
import com.freeclick.core.MacroGraph;
import com.freeclick.core.MacroLibrary;
import com.freeclick.core.MacroTransform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Step-by-step editor: reorder, duplicate, delete and edit every action.
 * Opens either a saved item (EXTRA_ID) or the panel's current work (EXTRA_WORKSPACE).
 * For combinations, "+" adds saved recordings / presets.
 *
 * 1.6: every change can be undone (↶ ↷); 👁 previews the steps over the app below without
 * touching anything; "Unelte" shortens pauses; long-press a row to select several steps
 * (faster ×2, disable, duplicate, delete); a row's ⋮ can run the macro from that step.
 */
public final class EditorActivity extends Activity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_WORKSPACE = "workspace";

    private MacroRepository repo;
    private Macro macro;
    private boolean workspaceMode;
    private boolean dirty;
    private TextView summary;
    private Adapter adapter;
    private MacroLibrary.InMemory cachedLib;
    private final EditHistory history = new EditHistory(30, 4000000L);
    private final TreeSet<Integer> selected = new TreeSet<Integer>();
    private LinearLayout toolbar;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleWindow(this);
        repo = App.get().repo();
        workspaceMode = getIntent().getBooleanExtra(EXTRA_WORKSPACE, false);
        Macro source;
        if (workspaceMode) {
            ClickService s = ClickService.get();
            source = s == null ? null : s.workspace();
        } else {
            source = repo.find(getIntent().getStringExtra(EXTRA_ID));
        }
        if (source == null) { finish(); return; } // also: brought back after the editor was closed
        macro = MacroCodec.fromJson(MacroCodec.toJson(source)); // edit a copy until "Salvează"

        LinearLayout root = Ui.vertical(this);
        root.addView(Ui.header(this, Macro.KIND_COMBO.equals(macro.kind) ? "Editează combinația" : "Editează acțiunile",
                false, false, new View.OnClickListener() { public void onClick(View v) { onBackPressed(); } },
                new View.OnClickListener() { public void onClick(View v) { more(v); } }));

        LinearLayout body = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        body.setPadding(p, 0, p, Ui.dp(this, 8));
        final EditText name = new EditText(this);
        name.setText(workspaceMode && !isSaved() ? "" : macro.name);
        name.setHint(ClickService.UNSAVED);
        name.setSingleLine(true);
        name.setTextColor(Ui.TEXT);
        name.setTextSize(20);
        name.setSingleLine(true);
        name.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11));
        name.setBackground(Ui.surface(this, Ui.SURFACE, 14));
        name.setHintTextColor(Ui.LABEL);
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable s) {
                String n = s.toString().trim();
                if (!n.isEmpty() && !n.equals(macro.name)) { macro.name = n; dirty = true; }
            }
        });
        body.addView(name, Ui.matchWrap());
        summary = Ui.muted(this, "");
        summary.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        summary.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 12)));
        LinearLayout.LayoutParams summaryLp = Ui.matchWrap();
        summaryLp.topMargin = Ui.dp(this, 8);
        summaryLp.bottomMargin = Ui.dp(this, 6);
        body.addView(summary, summaryLp);
        if (workspaceMode) {
            body.addView(Ui.muted(this, "Modifici lucrul curent din panou. Salvarea în listă se face din Opțiuni."));
        }
        toolbar = Ui.horizontal(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(toolbar, Ui.matchWrap());
        root.addView(body);

        // "Listă | Cronologie" (2.1): the steps on a time band, for lists without blocks
        if (!Macro.KIND_COMBO.equals(macro.kind)) {
            LinearLayout tabs = Ui.horizontal(this);
            tabs.setPadding(p, 0, p, Ui.dp(this, 6));
            tabList = tab("Listă", true);
            tabTime = tab("Cronologie", false);
            tabs.addView(tabList, Ui.weight(1));
            tabs.addView(tabTime, Ui.weight(1));
            root.addView(tabs, Ui.matchWrap());
        }

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setBackgroundColor(Ui.BG);
        list.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 8));
        adapter = new Adapter();
        list.setAdapter(adapter);
        listView = list;
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        timeline = new TimelineView(this, new TimelineView.Listener() {
            @Override public void onSelected(int index) { }
            @Override public void onDelayChanged(int index, long newDelay) {
                if (index < 0 || index >= macro.actions.size()) return; // tree-ok: the timeline shows top-level steps
                before("mutarea în timp");
                macro.actions.get(index).delay = newDelay; // tree-ok
                changed();
            }
            @Override public void onEdit(int index) { editAction(index); }
        });
        timeline.setVisibility(View.GONE);
        root.addView(timeline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = Ui.horizontal(this);
        bottom.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 12));
        final View addBtn = Ui.button(this, "+ Adaugă", new View.OnClickListener() {
            @Override public void onClick(View v) { addAction(); }
        });
        bottom.addView(addBtn, Ui.weight(1));
        final View saveBtn = Ui.colored(this, workspaceMode ? "Gata" : "Salvează", Ui.ACCENT, new View.OnClickListener() {
            @Override public void onClick(View v) { if (save(true) && workspaceMode) Ui.goBack(EditorActivity.this); }
        });
        bottom.addView(saveBtn, Ui.weight(1));
        root.addView(bottom, Ui.matchWrap());

        setContentView(root);
        refresh();
        if (b == null && !App.get().prefs().sectionOpen("tutorial.editor.v1")) {
            root.postDelayed(new Runnable() { public void run() {
                if (isFinishing()) return;
                java.util.List<TutorialOverlay.Step> l = new java.util.ArrayList<TutorialOverlay.Step>();
                l.add(new TutorialOverlay.Step(listView, "Aici sunt pașii, în ordine. Atinge unul ca să-l editezi (poziție, durată, "
                        + "pauza dinainte, repetări); ține apăsat ca să selectezi mai mulți."));
                l.add(new TutorialOverlay.Step(toolbar, "Bara de unelte: anulează / refă, iar pentru pașii selectați: mută sus / jos, "
                        + "duplică, dezactivează, șterge."));
                if (tabTime != null) l.add(new TutorialOverlay.Step(tabTime, "Cronologie: pașii pe o bandă de timp. Tragi un bloc ca să-l "
                        + "muți în timp; un ecran mic arată unde atinge pasul ales."));
                l.add(new TutorialOverlay.Step(addBtn, "+ Adaugă: atingere, swipe, zoom, pauză, imagine, culoare, citire din ecran, "
                        + "alt macro sau un 📜 script…"));
                l.add(new TutorialOverlay.Step(saveBtn, "Salvează păstrează schimbările. Înapoi fără salvare te întreabă înainte."));
                TutorialOverlay.show(EditorActivity.this, l.toArray(new TutorialOverlay.Step[0]), new TutorialOverlay.OnClosed() {
                    @Override public void onClosed(boolean neverAgain) { if (neverAgain) App.get().prefs().setSectionOpen("tutorial.editor.v1", true); }
                });
            } }, 500);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        ClickService s = ClickService.get();
        if (s != null) {
            s.onOptionsScreen(true);
            s.cancelReadyBar(); // back in the editor: the "open the right screen" bar goes
            com.freeclick.core.ImageSpec sp = s.takeEditorImage(); // back from cutting out an image
            if (sp != null && macro != null) {
                Action a = Action.image(sp);
                a.waitMode = Action.MISSING_WAIT;
                append(a, 0);
            }
            Action picked = s.takeEditorAction(); // back from choosing a zone / a colour
            if (picked != null && macro != null) {
                if (picked.varName != null && !picked.varName.isEmpty()) picked.varName = freeVarName(picked.varName);
                append(picked, 0);
            }
            if (activeForm != null) activeForm.reload(); // a new zone / colour picked for the step being edited
            if (macro != null) refresh(); // a test or a new cut-out may have changed a step
        }
    }

    /** The form of the step being edited (refreshed when we come back from choosing on the screen). */
    private ActionEditForm activeForm;

    private ClickService serviceOrToast() {
        ClickService s = ClickService.get();
        if (s == null) Toast.makeText(this, "Activează întâi serviciul de accesibilitate", Toast.LENGTH_LONG).show();
        return s;
    }

    /** Images, zones and colours need the real screen: the editor steps aside meanwhile. */
    private final ActionEditForm.ScreenTools screenTools = new ActionEditForm.ScreenTools() {
        @Override public void testImage(Action draft) {
            ClickService s = serviceOrToast();
            if (s == null) return;
            moveTaskToBack(true);
            s.testForEditor(draft);
        }

        @Override public void recaptureImage(Action draft) {
            ClickService s = serviceOrToast();
            if (s == null) return;
            moveTaskToBack(true);
            s.recaptureForEditor(draft);
        }

        @Override public void pickColor(Action draft) {
            ClickService s = serviceOrToast();
            if (s == null) return;
            moveTaskToBack(true);
            s.pickColorForEditor(draft);
        }

        @Override public void pickZone(Action draft) {
            ClickService s = serviceOrToast();
            if (s == null) return;
            if (android.os.Build.VERSION.SDK_INT < 30) { Toast.makeText(EditorActivity.this, "Citirea din zonă are nevoie de Android 11 sau mai nou.", Toast.LENGTH_LONG).show(); return; }
            moveTaskToBack(true);
            s.pickZoneForEditor(draft);
        }

        @Override public void testRead(Action draft) {
            ClickService s = serviceOrToast();
            if (s == null) return;
            if (android.os.Build.VERSION.SDK_INT < 30) { Toast.makeText(EditorActivity.this, "Citirea din zonă are nevoie de Android 11 sau mai nou.", Toast.LENGTH_LONG).show(); return; }
            moveTaskToBack(true);
            s.testReadForEditor(draft, macro);
        }
    };

    /** numar1, numar2... a variable name no step of this macro uses yet. */
    private String freeVarName(String base) {
        java.util.Set<String> used = new java.util.HashSet<String>();
        for (Action a : ActionTree.all(macro.actions)) if (a.varName != null) used.add(a.varName.trim().toLowerCase(java.util.Locale.ROOT));
        for (int i = 1; ; i++) if (!used.contains(base + i)) return base + i;
    }

    @Override protected void onPause() {
        ClickService s = ClickService.get();
        if (s != null) s.onOptionsScreen(false);
        super.onPause();
    }

    private boolean isSaved() { return repo.find(macro.id) != null; }

    private ListView listView;
    private TimelineView timeline;
    private TextView tabList, tabTime;

    private TextView tab(String label, boolean on) {
        TextView t = Ui.text(this, label, 14, on ? Ui.ON_ACCENT : Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        t.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_HIGH, Ui.dp(this, 10)));
        final boolean wantTimeline = label.startsWith("Cron");
        Ui.clickable(t, new View.OnClickListener() { public void onClick(View v) { showTimeline(wantTimeline); } }, Ui.dp(this, 10));
        return t;
    }

    private void showTimeline(boolean on) {
        if (on) {
            for (Action a : macro.actions) { // tree-ok: only top-level steps have a place in time
                if (a.isBlock()) {
                    Toast.makeText(this, "Cronologia arată doar liste fără blocuri (Dacă / Repetă)", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        listView.setVisibility(on ? View.GONE : View.VISIBLE);
        timeline.setVisibility(on ? View.VISIBLE : View.GONE);
        tabList.setBackground(Ui.rounded(on ? Ui.SURFACE_HIGH : Ui.ACCENT, Ui.dp(this, 10)));
        tabList.setTextColor(on ? Ui.TEXT : Ui.ON_ACCENT);
        tabTime.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_HIGH, Ui.dp(this, 10)));
        tabTime.setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT);
        if (on) timeline.setSteps(macro.actions, macro.screenWidth, macro.screenHeight); // tree-ok
    }

    private void refresh() {
        cachedLib = null;
        if (timeline != null && timeline.getVisibility() == View.VISIBLE) timeline.setSteps(macro.actions, macro.screenWidth, macro.screenHeight); // tree-ok
        // tree-ok: the 1.6 editor lists top-level steps (blocks get their own rows in 1.8)
        while (!selected.isEmpty() && selected.last() >= macro.actions.size()) selected.remove(selected.last());
        long est = MacroGraph.estimateDurationMs(macro, lib());
        summary.setText(ActionTree.count(macro.actions) + " acțiuni · o rulare ≈ " + ActionFormatter.ms(est)
                + " la 1× · viteză " + FloatingPanel.formatSpeed(macro.settings.speed)
                + " · repetări " + (macro.settings.loops == 0 ? "∞" : macro.settings.loops));
        buildToolbar();
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------ undo / redo
    private String snapshot() { return MacroCodec.toJson(macro); }

    /** Call before changing the macro: the change becomes undoable under this name. */
    private void before(String label) { history.record(snapshot(), label); }

    private void changed() {
        history.dropIfUnchanged(snapshot());
        dirty = true;
        refresh();
    }

    private void undo() {
        String back = history.undo(snapshot());
        if (back == null) return;
        String label = history.redoLabel();
        restore(back);
        Toast.makeText(this, "Anulat: " + label, Toast.LENGTH_SHORT).show();
    }

    private void redo() {
        String again = history.redo(snapshot());
        if (again == null) return;
        restore(again);
        Toast.makeText(this, "Refăcut: " + history.undoLabel(), Toast.LENGTH_SHORT).show();
    }

    private void restore(String json) {
        Macro m = MacroCodec.fromJson(json);
        m.id = macro.id;
        m.name = macro.name; // the name field is edited live, not through undo
        macro = m;
        selected.clear();
        changed();
    }

    // ------------------------------------------------------------------ toolbar
    private void buildToolbar() {
        toolbar.removeAllViews();
        if (!selected.isEmpty()) {
            toolbar.addView(Ui.text(this, selected.size() + " selectate", 14, Ui.TEXT, true), Ui.weight(1));
            toolbar.addView(tool("×2", "Mai rapid ×2", true, new View.OnClickListener() { public void onClick(View v) { speedUpSelected(); } }));
            toolbar.addView(tool("⏸", "Activează / dezactivează", true, new View.OnClickListener() { public void onClick(View v) { toggleSelected(); } }));
            toolbar.addView(tool("📋", "Duplică", true, new View.OnClickListener() { public void onClick(View v) { duplicateSelected(); } }));
            toolbar.addView(tool("🗑", "Șterge", true, new View.OnClickListener() { public void onClick(View v) { deleteSelected(); } }));
            toolbar.addView(tool("×", "Gata cu selecția", true, new View.OnClickListener() { public void onClick(View v) { selected.clear(); refresh(); } }));
            return;
        }
        toolbar.addView(tool("↩", history.canUndo() ? "Anulează: " + history.undoLabel() : "Nimic de anulat", history.canUndo(),
                new View.OnClickListener() { public void onClick(View v) { undo(); } }));
        toolbar.addView(tool("↪", history.canRedo() ? "Refă: " + history.redoLabel() : "Nimic de refăcut", history.canRedo(),
                new View.OnClickListener() { public void onClick(View v) { redo(); } }));
        View spacer = new View(this);
        toolbar.addView(spacer, Ui.weight(1));
        TextView preview = Ui.text(this, "👁 Previzualizează", 14, Ui.ACCENT, true);
        preview.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        Ui.clickable(preview, new View.OnClickListener() { public void onClick(View v) { preview(); } }, Ui.dp(this, 8));
        toolbar.addView(preview);
        TextView tools = Ui.text(this, "Unelte ▾", 14, Ui.ACCENT, true);
        tools.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        Ui.clickable(tools, new View.OnClickListener() { public void onClick(View v) { tools(v); } }, Ui.dp(this, 8));
        toolbar.addView(tools);
    }

    private View tool(String glyph, final String description, boolean enabled, View.OnClickListener l) {
        TextView b = Ui.text(this, glyph, 18, enabled ? Ui.ACCENT : 0x55FFFFFF, true);
        b.setGravity(Gravity.CENTER);
        int w = Ui.dp(this, 42);
        b.setLayoutParams(new LinearLayout.LayoutParams(w, w));
        b.setContentDescription(description);
        if (enabled) Ui.clickable(b, l, w / 2f);
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Toast.makeText(EditorActivity.this, description, Toast.LENGTH_SHORT).show(); return true; }
        });
        return b;
    }

    // ------------------------------------------------------------------ preview / run from here
    private void preview() {
        ClickService s = ClickService.get();
        if (s == null) { Toast.makeText(this, "Activează întâi serviciul de accesibilitate", Toast.LENGTH_LONG).show(); return; }
        if (ActionTree.isEmpty(macro.actions)) { Toast.makeText(this, "Nu e nimic de previzualizat.", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Previzualizare peste aplicația de dinainte; revin aici la final.", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
        s.previewFromEditor(MacroCodec.fromJson(snapshot()));
    }

    private void runFrom(int index) {
        ClickService s = ClickService.get();
        if (s == null) { Toast.makeText(this, "Activează întâi serviciul de accesibilitate", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, "Rulez de la pasul " + (index + 1) + "; revin aici la final (■ pe panou oprește).", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
        s.playFromEditor(MacroCodec.fromJson(snapshot()), index);
    }

    // ------------------------------------------------------------------ tools
    private void tools(View anchor) {
        List<String> labels = Arrays.asList("Scurtează pauza de început…", "Scurtează pauzele lungi…", "Tot macro-ul mai rapid ×2",
                "Selectează mai mulți pași (sau ține apăsat un rând)");
        Ui.popup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int i) {
                if (i == 0) shortenFirst();
                else if (i == 1) capPauses();
                else if (i == 2) {
                    before("mai rapid ×2");
                    MacroTransform.speedUp(macro.actions, 2); // tree-ok: speedUp walks the tree
                    changed();
                    Toast.makeText(EditorActivity.this, "Pauzele și glisările sunt de 2× mai scurte (apăsările rămân la fel).", Toast.LENGTH_LONG).show();
                } else {
                    if (!macro.actions.isEmpty()) { selected.add(0); refresh(); }
                }
            }
        });
    }

    private void shortenFirst() {
        Ui.timeDialog(this, "Pauza de început devine cel mult", 500, 0, 3600000L, new Ui.OnLong() {
            @Override public void onValue(final long ms) {
                before("pauza de început");
                long old = MacroTransform.shortenFirstPause(macro.actions, ms);
                if (old < 0) { history.dropIfUnchanged(snapshot()); Toast.makeText(EditorActivity.this, "Pauza de început e deja atât de scurtă.", Toast.LENGTH_SHORT).show(); return; }
                changed();
                Toast.makeText(EditorActivity.this, "Pauza de început: " + ActionFormatter.ms(old) + " → " + ActionFormatter.ms(ms), Toast.LENGTH_SHORT).show();
            }
        }, false);
    }

    private void capPauses() {
        Ui.timeDialog(this, "Pauzele mai lungi decât asta se scurtează la atât", 3000, 0, 3600000L, new Ui.OnLong() {
            @Override public void onValue(final long ms) {
                before("pauzele lungi");
                int n = MacroTransform.capPauses(macro.actions, ms);
                if (n == 0) { history.dropIfUnchanged(snapshot()); Toast.makeText(EditorActivity.this, "Nicio pauză nu e mai lungă de " + ActionFormatter.ms(ms) + ".", Toast.LENGTH_SHORT).show(); return; }
                changed();
                Toast.makeText(EditorActivity.this, n + " pauze scurtate la " + ActionFormatter.ms(ms), Toast.LENGTH_SHORT).show();
            }
        }, false);
    }

    // ------------------------------------------------------------------ selection
    private List<Action> selectedActions() {
        List<Action> out = new ArrayList<Action>();
        for (int i : selected) if (i < macro.actions.size()) out.add(macro.actions.get(i));
        return out;
    }

    private void speedUpSelected() {
        before("mai rapid ×2");
        MacroTransform.speedUp(selectedActions(), 2);
        changed();
    }

    private void toggleSelected() {
        before("activare / dezactivare");
        boolean anyOn = false;
        for (Action a : selectedActions()) anyOn |= a.enabled;
        for (Action a : selectedActions()) a.enabled = !anyOn;
        changed();
    }

    private void duplicateSelected() {
        before("duplicarea");
        List<Action> copies = new ArrayList<Action>();
        for (Action a : selectedActions()) copies.add(a.copy());
        int at = selected.last() + 1;
        macro.actions.addAll(at, copies);
        selected.clear();
        changed();
    }

    private void deleteSelected() {
        before("ștergerea");
        List<Action> gone = selectedActions();
        macro.actions.removeAll(gone);
        selected.clear();
        changed();
        Toast.makeText(this, gone.size() + " pași șterși · ↩ ca să-i readuci", Toast.LENGTH_SHORT).show();
    }

    private void rowMenu(View anchor, final int i) {
        final Action a = macro.actions.get(i);
        List<String> labels = Arrays.asList("▶ Rulează de aici", "Adaugă după acesta…", "Duplică", a.enabled ? "Dezactivează" : "Activează", "Selectează", "Șterge");
        Ui.popup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int k) {
                switch (k) {
                    case 0: runFrom(i); break;
                    case 1: addAction(i + 1); break;
                    case 2: before("duplicarea"); macro.actions.add(i + 1, a.copy()); changed(); break;
                    case 3: before(a.enabled ? "dezactivarea" : "activarea"); a.enabled = !a.enabled; changed(); break;
                    case 4: selected.add(i); refresh(); break;
                    default: before("ștergerea"); macro.actions.remove(i); changed(); break;
                }
            }
        });
    }

    /** Saved items plus the one being edited (cached between list redraws). */
    private MacroLibrary.InMemory lib() {
        if (cachedLib == null) {
            cachedLib = repo.snapshot();
            cachedLib.add(macro);
        }
        return cachedLib;
    }

    private boolean save(boolean toastIt) {
        ClickService s = ClickService.get();
        if (workspaceMode) {
            if (s == null) { Toast.makeText(this, "Serviciul e oprit", Toast.LENGTH_LONG).show(); return false; }
            s.replaceWorkspace(macro);
            dirty = false;
            return true;
        }
        try {
            repo.save(MacroCodec.fromJson(MacroCodec.toJson(macro)));
            dirty = false;
            if (s != null) s.refreshFromRepo(macro.id);
            if (toastIt) Toast.makeText(this, "Setări salvate!", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Eroare: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (TutorialOverlay.close(this)) return;
        if (!dirty) { Ui.goBack(this); return; }
        new AlertDialog.Builder(this).setTitle(workspaceMode ? "Păstrezi modificările?" : "Salvezi modificările?")
                .setPositiveButton(workspaceMode ? "PĂSTREAZĂ" : "SALVEAZĂ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { if (save(false)) Ui.goBack(EditorActivity.this); }
                })
                .setNeutralButton("RENUNȚĂ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { Ui.goBack(EditorActivity.this); }
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    private void more(View anchor) {
        List<String> labels = new ArrayList<String>(Arrays.asList("Setări de redare"));
        if (!workspaceMode) labels.add("Încarcă în panou");
        Ui.popup(anchor, labels, null, new Ui.OnPick() {
            @Override public void onPick(int i) {
                if (i == 0) editSettings();
                else loadInPanel();
            }
        });
    }

    private void loadInPanel() {
        ClickService s = ClickService.get();
        if (s == null) { Toast.makeText(this, "Activează întâi serviciul de accesibilitate", Toast.LENGTH_LONG).show(); return; }
        if (dirty && !save(false)) return;
        s.load(macro);
        moveTaskToBack(true);
    }

    private void editSettings() {
        ClickService s = ClickService.get();
        final MacroSettingsForm form = new MacroSettingsForm(this, macro.settings, s == null ? null : s.foregroundPackage(), false);
        new AlertDialog.Builder(this).setTitle("Setări de redare").setView(form.view())
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { before("setările de redare"); form.apply(); changed(); }
                })
                .setNegativeButton("ANULEAZĂ", null).show();
    }

    private void editAction(final int index) {
        final Action a = macro.actions.get(index);
        final Action copy = a.copy();
        final ActionEditForm form = new ActionEditForm(this, copy, macro, repo.list(), lib(), screenTools, false);
        activeForm = form;
        new AlertDialog.Builder(this).setTitle((index + 1) + " · " + ActionFormatter.typeName(a.type))
                .setView(form.view())
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        form.apply();
                        if (MacroCodec.toJson(macro).equals(snapshotWith(index, copy))) return; // nothing changed
                        before("editarea pasului " + (index + 1));
                        macro.actions.set(index, copy);
                        changed();
                    }
                })
                .setNegativeButton("ANULEAZĂ", null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface d) { if (activeForm == form) activeForm = null; }
                })
                .show();
    }

    /** The macro's JSON as it would be with step `index` replaced (to skip no-op edits). */
    private String snapshotWith(int index, Action replacement) {
        Action old = macro.actions.get(index);
        macro.actions.set(index, replacement);
        String json = MacroCodec.toJson(macro);
        macro.actions.set(index, old);
        return json;
    }

    /** Where the next added step goes (end, or right after a row via its ⋮ menu). */
    private int insertAt = -1;

    private void addAction() { addAction(-1); }

    private void addAction(int at) {
        insertAt = at;
        final boolean combo = Macro.KIND_COMBO.equals(macro.kind);
        final String[] kinds = combo
                ? new String[]{"Înregistrare, presetare sau 📜 script salvat", "Pauză", "Pauză aleatoare", "Acțiune de sistem", "Deschide o aplicație",
                "Afișează în consolă", "Setează o variabilă (calcule)"}
                : new String[]{"Atingere", "Apăsare lungă", "Dublu tap", "Swipe", "Pauză", "Pauză aleatoare", "Acțiune de sistem",
                "Deschide o aplicație", "Alt macro sau 📜 script (cod)", "Așteaptă o culoare (alegi de pe ecran)", "Imagine (decupează de pe ecran)",
                "Citește număr / text din zonă", "Citește culoarea unui punct", "Afișează în consolă", "Setează o variabilă (calcule)",
                "Zoom în (două degete)", "Zoom out (două degete)"};
        new AlertDialog.Builder(this).setTitle(at >= 0 ? "Adaugă după pasul " + at : "Adaugă").setItems(kinds, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (combo) {
                    switch (which) {
                        case 0: pickSubMacro(); break;
                        case 1: append(Action.waitFor(1000), 0); break;
                        case 2: append(Action.randomWait(1000, 3000), 0); break;
                        case 3: append(Action.global(GlobalActions.BACK), 500); break;
                        case 4: append(Action.launchApp("", 10000), 0); break;
                        case 5: append(Action.print(lastVarTemplate()), 0); break;
                        default: append(Action.setVar("n", "n + 1"), 0); break;
                    }
                    return;
                }
                Point sz = Overlays.screenSize(EditorActivity.this);
                float cx = sz.x / 2f, cy = sz.y / 2f;
                Macro.Settings st = macro.settings;
                switch (which) {
                    case 0: { Action t = Action.tap(cx, cy); t.duration = st.tapDuration; append(t, st.tapInterval); break; }
                    case 1: append(Action.longPress(cx, cy, Action.DEFAULT_LONG_PRESS_MS), st.tapInterval); break;
                    case 2: append(Action.doubleTap(cx, cy), st.tapInterval); break;
                    case 3: append(Action.swipe(cx, sz.y * 0.65f, cx, sz.y * 0.35f, st.swipeDuration), st.tapInterval); break;
                    case 4: append(Action.waitFor(1000), 0); break;
                    case 5: append(Action.randomWait(1000, 3000), 0); break;
                    case 6: append(Action.global(GlobalActions.BACK), 500); break;
                    case 7: append(Action.launchApp("", 10000), 0); break;
                    case 8: pickSubMacro(); break;
                    case 9: pickOnScreen(ClickService.EDITOR_COLOR_WAIT, Action.waitColor(cx, cy, 0xFFFFFF, 30000)); break;
                    case 10: cutImage(); break;
                    case 11: pickOnScreen(ClickService.EDITOR_ZONE, null); break;
                    case 12: pickOnScreen(ClickService.EDITOR_COLOR_READ, Action.readColor(cx, cy, freeVarName("culoare"))); break;
                    case 13: append(Action.print(lastVarTemplate()), 0); break;
                    case 14: append(Action.setVar("n", "n + 1"), 0); break;
                    case 15: append(Action.zoom(cx, cy, 400, 400, true), st.tapInterval); break;
                    default: append(Action.zoom(cx, cy, 400, 400, false), st.tapInterval); break;
                }
            }
        }).show();
    }

    /**
     * New steps that live on the screen (a colour point, a zone to read): chosen over the app below.
     * Without the service (or for colours on old phones) the fallback step is added and edited by numbers.
     */
    private void pickOnScreen(int what, Action fallback) {
        ClickService s = ClickService.get();
        boolean needsFrames = what == ClickService.EDITOR_ZONE;
        if (s == null || (needsFrames && android.os.Build.VERSION.SDK_INT < 30)) {
            if (fallback != null) { append(fallback, 0); return; }
            Toast.makeText(this, s == null ? "Activează întâi serviciul de accesibilitate"
                    : "Citirea din zonă are nevoie de Android 11 sau mai nou.", Toast.LENGTH_LONG).show();
            return;
        }
        moveTaskToBack(true);
        s.captureForEditor(what);
    }

    /** "aur = {aur}" for the last variable a step of this macro reads, or a general example. */
    private String lastVarTemplate() {
        String v = null;
        for (Action a : ActionTree.all(macro.actions)) if (a.type == Action.Type.READ && a.varName != null && !a.varName.trim().isEmpty()) v = a.varName.trim();
        return v != null ? v + " = {" + v + "}" : "Tura {tura}, timp {timp} s";
    }

    /** "Adaugă → Imagine": the editor steps aside, the image is cut out over the app below, then we come back. */
    private void cutImage() {
        ClickService s = ClickService.get();
        if (s == null) { Toast.makeText(this, "Activează întâi serviciul de accesibilitate", Toast.LENGTH_LONG).show(); return; }
        if (android.os.Build.VERSION.SDK_INT < 30) { Toast.makeText(this, "Pașii cu imagini au nevoie de Android 11 sau mai nou.", Toast.LENGTH_LONG).show(); return; }
        moveTaskToBack(true);
        s.captureForEditor();
    }

    private void append(Action a, long delay) {
        a.delay = delay;
        before("adăugarea");
        if (macro.screenWidth <= 0) {
            Point sz = Overlays.screenSize(this);
            macro.screenWidth = sz.x;
            macro.screenHeight = sz.y;
        }
        int at = insertAt >= 0 && insertAt <= macro.actions.size() ? insertAt : macro.actions.size();
        insertAt = -1;
        macro.actions.add(at, a);
        changed();
        editAction(at);
    }

    private void pickSubMacro() {
        final List<Macro> options = new ArrayList<Macro>();
        for (Macro m : repo.list()) if (!m.id.equals(macro.id) && !MacroGraph.wouldCreateCycle(macro, m.id, lib())) options.add(m);
        if (options.isEmpty()) {
            Toast.makeText(this, "Salvează întâi înregistrări sau presetări, apoi le poți combina.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[options.size()];
        for (int i = 0; i < names.length; i++) {
            Macro o = options.get(i);
            names[i] = (Macro.KIND_SCRIPT.equals(o.kind) ? "📜 " : "") + o.name + "  (" + LibraryActivity.kindName(o.kind) + ")";
        }
        new AlertDialog.Builder(this).setTitle("Alege").setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { append(Action.macro(options.get(which).id, 1), 0); }
        }).show();
    }

    private void move(int i, int dir) {
        int j = i + dir;
        if (j < 0 || j >= macro.actions.size()) return;
        before("mutarea");
        Action t = macro.actions.get(i);
        macro.actions.set(i, macro.actions.get(j));
        macro.actions.set(j, t);
        if (selected.remove(i)) selected.add(j);
        changed();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return macro.actions.size(); }
        @Override public Object getItem(int i) { return macro.actions.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(final int i, View convert, ViewGroup parent) {
            LinearLayout row = Ui.horizontal(EditorActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(EditorActivity.this, 12), Ui.dp(EditorActivity.this, 7), Ui.dp(EditorActivity.this, 7), Ui.dp(EditorActivity.this, 7));
            final boolean isSel = selected.contains(i);
            android.graphics.drawable.GradientDrawable rowBg = Ui.rounded(isSel ? Ui.SURFACE_HIGH : Ui.SURFACE, Ui.dp(EditorActivity.this, 14));
            rowBg.setStroke(Ui.dp(EditorActivity.this, isSel ? 2 : 1), isSel ? Ui.ACCENT : 0xFF252F3D);
            row.setBackground(rowBg);
            Action a = macro.actions.get(i);
            String desc = (isSel ? "✅ " : "") + (i + 1) + ". " + ActionFormatter.describe(a, lib());
            if (a.label != null && !a.label.isEmpty()) desc += "\n   " + a.label;
            TextView t = Ui.text(EditorActivity.this, desc, 14, a.enabled ? Ui.TEXT : Ui.MUTED, false);
            Ui.clickable(t, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (selected.isEmpty()) { editAction(i); return; }
                    if (!selected.remove(i)) selected.add(i); // selection mode: tap toggles
                    refresh();
                }
            }, Ui.dp(EditorActivity.this, 6));
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (!selected.remove(i)) selected.add(i);
                    refresh();
                    return true;
                }
            });
            row.addView(t, Ui.weight(1));
            row.addView(small("↑", new View.OnClickListener() { public void onClick(View v) { move(i, -1); } }));
            row.addView(small("↓", new View.OnClickListener() { public void onClick(View v) { move(i, +1); } }));
            row.addView(small("⋮", new View.OnClickListener() { public void onClick(View v) { rowMenu(v, i); } }));
            row.addView(small("×", new View.OnClickListener() { public void onClick(View v) {
                before("ștergerea"); macro.actions.remove(i); changed(); } }));
            row.setMinimumHeight(Ui.dp(EditorActivity.this, 62));
            return row;
        }

        private View small(String s, View.OnClickListener l) {
            TextView b = Ui.text(EditorActivity.this, s, 18, Ui.ACCENT, true);
            b.setGravity(Gravity.CENTER);
            int w = Ui.dp(EditorActivity.this, 40);
            b.setLayoutParams(new LinearLayout.LayoutParams(w, w));
            Ui.clickable(b, l, w / 2f);
            return b;
        }
    }
}
