/*
 * Decompiled with CFR 0.152.
 */
package com.freeclick.desktop;

import com.freeclick.core.Action;
import com.freeclick.core.ActionFormatter;
import com.freeclick.core.Macro;
import com.freeclick.core.MacroTransform;
import com.freeclick.core.Workspace;
import com.freeclick.desktop.App;
import com.freeclick.desktop.BarCtl;
import com.freeclick.desktop.DeskWork;
import com.freeclick.desktop.ImageCut;
import com.freeclick.desktop.Markers;
import com.freeclick.desktop.Picker;
import com.freeclick.desktop.StepForm;
import com.freeclick.desktop.Theme;
import com.freeclick.desktop.Ui;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

final class StepsWindow
extends JFrame
implements StepForm.Host {
    private final BarCtl ctl;
    private final DeskWork work;
    private final DefaultListModel<Action> model = new DefaultListModel();
    private final Reorder ro = new Reorder();
    private final JList<Action> list = new JList<Action>(this.model){

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (StepsWindow.this.model.isEmpty()) {
                Graphics2D g = (Graphics2D)graphics.create();
                g.setColor(Theme.GREY);
                g.setFont(Ui.font(0, 12.0f));
                String text = "Pașii tăi vor apărea aici";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(text, Math.max(16, (getWidth() - fm.stringWidth(text)) / 2), Math.max(42, getHeight() / 2));
                g.dispose();
            }
            if (!StepsWindow.this.ro.dragging || StepsWindow.this.ro.over < 0 || StepsWindow.this.model.isEmpty()) {
                return;
            }
            int n = Math.min(StepsWindow.this.ro.over, StepsWindow.this.model.size() - 1);
            Rectangle rectangle = this.getCellBounds(n, n);
            int n2 = StepsWindow.this.ro.over >= StepsWindow.this.model.size() ? rectangle.y + rectangle.height - 2 : rectangle.y;
            Graphics2D graphics2D = (Graphics2D)graphics;
            graphics2D.setColor(Theme.TEAL);
            graphics2D.setStroke(new BasicStroke(3.0f));
            graphics2D.drawLine(4, n2, this.getWidth() - 4, n2);
        }
    };
    private final JPanel formHolder = new JPanel(new BorderLayout());
    private final JTabbedPane right = new JTabbedPane();
    private final JTextField name = new JTextField(22);
    private final JLabel status = new JLabel(" ");
    private final JButton save = Ui.primary("Salveaz\u0103", Theme.TEAL);
    private final JButton run = Ui.primary("\u25ba Ruleaz\u0103", Theme.TEAL);
    private final JButton undo = Ui.secondary("Anuleaz\u0103");
    private final JButton redo = Ui.secondary("Ref\u0103");
    private JButton addAction;
    private JPanel settings;
    private StepForm form;
    private boolean syncing;
    private String pendingLabel;
    private final Timer commitLater;
    private Action playing;
    private final List<Runnable> settingsReload = new ArrayList<Runnable>();
    private boolean settingsLoading;

    StepsWindow(final BarCtl barCtl) {
        super("Pa\u0219i");
        JComponent jComponent2;
        this.ctl = barCtl;
        this.work = barCtl.work;
        this.setIconImages(Ui.icons());
        this.setDefaultCloseOperation(2);
        this.getContentPane().setBackground(Theme.INDIGO);
        this.commitLater = new Timer(700, actionEvent -> this.flush());
        this.commitLater.setRepeats(false);
        this.addWindowListener(new WindowAdapter(){

            @Override
            public void windowClosed(WindowEvent windowEvent) {
                StepsWindow.this.flush();
                barCtl.stepsClosed();
                StepsWindow.this.saveBounds();
            }
        });
        this.addComponentListener(new ComponentAdapter(){

            @Override
            public void componentResized(ComponentEvent componentEvent) {
                StepsWindow.this.saveBounds();
            }

            @Override
            public void componentMoved(ComponentEvent componentEvent) {
                StepsWindow.this.saveBounds();
            }
        });
        JPanel jPanel = new JPanel(new BorderLayout(10, 0));
        jPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        jPanel.setBackground(Theme.INDIGO);
        this.name.setFont(Ui.font(1, 16.0f));
        this.name.setBackground(new Color(0x26313D));
        this.name.setForeground(Theme.INK);
        this.name.setCaretColor(Theme.INK);
        this.name.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(5, 9, 5, 9)));
        this.name.addActionListener(actionEvent -> this.rename());
        this.name.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent focusEvent) {
                StepsWindow.this.rename();
            }
        });
        JPanel jPanel2 = new JPanel(new FlowLayout(0, 8, 0));
        jPanel2.setBackground(Theme.INDIGO);
        JLabel jLabel = new JLabel("Nume:");
        jLabel.setFont(Ui.font(0, 14.0f));
        jLabel.setForeground(Theme.GREY);
        jPanel2.add(jLabel);
        jPanel2.add(this.name);
        this.status.setFont(Ui.font(0, 12.5f));
        this.status.setForeground(Theme.GREY);
        jPanel2.add(this.status);
        this.save.addActionListener(actionEvent -> {
            this.flush();
            barCtl.saveFlow();
        });
        JButton jButton = Ui.secondary("Salveaz\u0103 ca\u2026");
        jButton.addActionListener(actionEvent -> {
            this.flush();
            barCtl.saveAsFlow();
        });
        JPanel jPanel3 = new JPanel(new FlowLayout(2, 8, 0));
        jPanel3.setBackground(Theme.INDIGO);
        jPanel3.add(jButton);
        jPanel3.add(this.save);
        jPanel.add((Component)jPanel2, "Center");
        jPanel.add((Component)jPanel3, "East");
        JPanel jPanel4 = new JPanel(new BorderLayout());
        jPanel4.setBackground(Theme.INDIGO);
        jPanel4.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        JPanel jPanel5 = new JPanel(new FlowLayout(0, 6, 0));
        jPanel5.setBackground(Theme.INDIGO);
        JButton jButton2 = Ui.secondary("+ Adaug\u0103");
        this.addAction = jButton2;
        jButton2.addActionListener(actionEvent -> this.addMenu().show(jButton2, 0, jButton2.getHeight()));
        JButton jButton3 = Ui.secondary("Duplic\u0103");
        jButton3.setToolTipText("Ctrl+D");
        jButton3.addActionListener(actionEvent -> this.duplicate());
        JButton jButton4 = Ui.secondary("\u0218terge");
        jButton4.setToolTipText("Delete");
        jButton4.addActionListener(actionEvent -> this.delete());
        JButton jButton5 = Ui.secondary("Sus");
        JButton jButton6 = Ui.secondary("Jos");
        jButton5.setToolTipText("Alt+\u2191 (sau trage pasul \u00een list\u0103)");
        jButton6.setToolTipText("Alt+\u2193");
        jButton5.addActionListener(actionEvent -> this.move(-1));
        jButton6.addActionListener(actionEvent -> this.move(1));
        this.undo.setToolTipText("Ctrl+Z");
        this.redo.setToolTipText("Ctrl+Y");
        this.undo.addActionListener(actionEvent -> {
            this.flush();
            this.work.undo();
        });
        this.redo.addActionListener(actionEvent -> {
            this.flush();
            this.work.redo();
        });
        for (JComponent button : new JComponent[]{jButton2, jButton3, jButton4, jButton5, jButton6, this.undo, this.redo}) {
            jPanel5.add(button);
        }
        JPanel jPanel6 = new JPanel(new FlowLayout(2, 6, 0));
        jPanel6.setBackground(Theme.INDIGO);
        this.run.addActionListener(actionEvent -> {
            this.flush();
            if (barCtl.main.runner.running()) {
                barCtl.main.runner.stop();
            } else {
                barCtl.play();
            }
        });
        JButton jButton7 = Ui.secondary("Avansat \u203a");
        jButton7.addActionListener(actionEvent -> this.advancedMenu().show(jButton7, 0, jButton7.getHeight()));
        jPanel6.add(jButton7);
        jPanel6.add(this.run);
        jPanel4.add((Component)jPanel5, "Center");
        jPanel4.add((Component)jPanel6, "East");
        JPanel jPanel7 = new JPanel(new BorderLayout());
        jPanel7.setBackground(Theme.INDIGO);
        jPanel7.add((Component)jPanel, "North");
        jPanel7.add((Component)jPanel4, "South");
        this.list.setSelectionMode(2);
        this.list.setBackground(Theme.LIGHT);
        this.list.setForeground(Theme.INK);
        this.list.setOpaque(true);
        this.list.setCellRenderer(new Row());
        this.list.setFixedCellHeight(-1);
        this.list.addListSelectionListener(listSelectionEvent -> {
            if (!listSelectionEvent.getValueIsAdjusting() && !this.syncing) {
                this.selectionChanged();
            }
        });
        this.list.addMouseListener(this.ro);
        this.list.addMouseMotionListener(this.ro);
        jComponent2 = new JScrollPane(this.list);
        ((JScrollPane)jComponent2).getVerticalScrollBar().setUnitIncrement(18);
        ((JScrollPane)jComponent2).setBackground(Theme.LIGHT);
        ((JScrollPane)jComponent2).getViewport().setBackground(Theme.LIGHT);
        jComponent2.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 1, Theme.LINE));
        this.formHolder.setBackground(Theme.WINDOW_BG);
        JScrollPane jScrollPane = new JScrollPane(this.formHolder, 20, 31);
        jScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        jScrollPane.setBackground(Theme.WINDOW_BG);
        jScrollPane.getViewport().setBackground(Theme.WINDOW_BG);
        jScrollPane.setBorder(null);
        this.right.setFont(Ui.font(1, 13.0f));
        this.right.setOpaque(true);
        this.right.setBackground(Theme.INDIGO);
        this.right.setForeground(Theme.INK);
        this.right.setBorder(BorderFactory.createEmptyBorder());
        this.right.setUI(new BasicTabbedPaneUI() {
            @Override protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
                g.setColor(selected ? Theme.WINDOW_BG : Theme.INDIGO);
                g.fillRect(x, y, w, h);
                if (selected) {
                    g.setColor(Theme.TEAL);
                    g.fillRect(x + 10, y + h - 3, w - 20, 2);
                }
            }
            @Override protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics, int index, String title, Rectangle textRect, boolean selected) {
                g.setColor(selected ? Theme.INK : Theme.GREY);
                super.paintText(g, placement, font, metrics, index, title, textRect, selected);
            }
            @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
                g.setColor(Theme.LINE);
            }
        });
        this.right.addTab("Pasul", jScrollPane);
        this.right.addTab("Set\u0103ri automate", new JScrollPane(this.settingsPanel(), 20, 31));
        JSplitPane jSplitPane = new JSplitPane(1, jComponent2, this.right);
        jSplitPane.setResizeWeight(0.38);
        jSplitPane.setDividerLocation(380);
        jSplitPane.setBackground(Theme.INDIGO);
        jSplitPane.setContinuousLayout(true);
        jSplitPane.setBorder(null);
        this.getContentPane().add((Component)jPanel7, "North");
        this.getContentPane().add((Component)jSplitPane, "Center");
        this.keys();
        int n = barCtl.app.store.getInt("steps.w", 1120);
        int n2 = barCtl.app.store.getInt("steps.h", 760);
        this.setSize(Math.max(1120, n), Math.max(760, n2));
        int n3 = barCtl.app.store.getInt("steps.x", Integer.MIN_VALUE);
        if (n3 == Integer.MIN_VALUE) {
            this.setLocationRelativeTo(null);
        } else {
            this.setLocation(n3, barCtl.app.store.getInt("steps.y", 80));
        }
        this.setMinimumSize(new Dimension(860, 580));
        this.reloadList();
    }

    private void saveBounds() {
        if (!this.isShowing() || this.getExtendedState() != 0) {
            return;
        }
        this.ctl.app.store.set("steps.w", this.getWidth());
        this.ctl.app.store.set("steps.h", this.getHeight());
        this.ctl.app.store.set("steps.x", this.getX());
        this.ctl.app.store.set("steps.y", this.getY());
    }

    void open(Action action) {
        if (!this.isVisible()) {
            this.setVisible(true);
        }
        this.setState(0);
        this.toFront();
        if (action != null) {
            int n = this.model.indexOf(action);
            if (n >= 0) {
                this.list.setSelectedIndex(n);
                this.list.ensureIndexIsVisible(n);
                this.right.setSelectedIndex(0);
            }
        } else if (this.list.isSelectionEmpty() && !this.model.isEmpty()) {
            this.list.setSelectedIndex(0);
        }
    }

    private void keys() {
        JRootPane jRootPane = this.getRootPane();
        StepsWindow.bind(jRootPane, KeyStroke.getKeyStroke(90, 128), () -> {
            this.flush();
            this.work.undo();
        });
        StepsWindow.bind(jRootPane, KeyStroke.getKeyStroke(89, 128), () -> {
            this.flush();
            this.work.redo();
        });
        StepsWindow.bind(jRootPane, KeyStroke.getKeyStroke(90, 192), () -> {
            this.flush();
            this.work.redo();
        });
        StepsWindow.bind(jRootPane, KeyStroke.getKeyStroke(83, 128), () -> {
            this.flush();
            this.ctl.saveFlow();
        });
        StepsWindow.bind(jRootPane, KeyStroke.getKeyStroke(119, 0), () -> {
            this.flush();
            if (this.ctl.main.runner.running()) {
                this.ctl.main.runner.stop();
            } else {
                this.ctl.play();
            }
        });
        StepsWindow.bind(this.list, KeyStroke.getKeyStroke(127, 0), this::delete);
        StepsWindow.bind(this.list, KeyStroke.getKeyStroke(68, 128), this::duplicate);
        StepsWindow.bind(this.list, KeyStroke.getKeyStroke(38, 512), () -> this.move(-1));
        StepsWindow.bind(this.list, KeyStroke.getKeyStroke(40, 512), () -> this.move(1));
    }

    private static void bind(JComponent jComponent, KeyStroke keyStroke, final Runnable runnable) {
        String string = "fc-" + String.valueOf(keyStroke);
        jComponent.getInputMap(jComponent instanceof JRootPane ? 2 : 0).put(keyStroke, string);
        jComponent.getActionMap().put(string, new AbstractAction(){

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                runnable.run();
            }
        });
    }

    void workChanged() {
        this.reloadList();
        this.refreshSettings();
    }

    void runChanged() {
        boolean bl = this.ctl.main.runner.running();
        this.run.setText(bl ? "\u25a0 Opre\u0219te" : "\u25ba Ruleaz\u0103");
        this.run.setBackground(bl ? Theme.RED : Theme.TEAL);
        if (!bl) {
            this.playing = null;
            this.list.repaint();
        }
    }

    void showPlaying(Action action) {
        this.playing = action;
        this.list.repaint();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * WARNING - void declaration
     */
    private void reloadList() {
        Macro macro = this.work.current();
        this.syncing = true;
        try {
            int n;
            List<Action> list = macro == null ? Collections.emptyList() : macro.actions;
            boolean bl = this.model.size() == list.size();
            for (n = 0; bl && n < list.size(); ++n) {
                if (this.model.get(n) == list.get(n)) continue;
                bl = false;
            }
            if (!bl) {
                int i;
                int nArray = 0;
                n = this.list.getLeadSelectionIndex();
                this.model.clear();
                for (Action action : list) {
                    this.model.addElement(action);
                }
                ArrayList arrayList = new ArrayList();
                boolean n2 = false;
                while (nArray < this.model.size()) {
                    if (this.work.isSelected(this.model.get((int)nArray))) {
                        arrayList.add((int)nArray);
                    }
                    ++nArray;
                }
                if (arrayList.isEmpty() && this.form != null && (i = this.model.indexOf(this.form.a)) >= 0) {
                    arrayList.add(i);
                }
                if (arrayList.isEmpty() && n >= 0 && !this.model.isEmpty()) {
                    arrayList.add(Math.min(n, this.model.size() - 1));
                }
                int[] nArray2 = new int[arrayList.size()];
                for (int j = 0; j < nArray2.length; ++j) {
                    nArray2[j] = (Integer)arrayList.get(j);
                }
                this.list.setSelectedIndices(nArray2);
            } else {
                ArrayList<Integer> arrayList = new ArrayList<Integer>();
                for (int i = 0; i < this.model.size(); ++i) {
                    if (!this.work.isSelected(this.model.get(i))) continue;
                    arrayList.add(i);
                }
                if (!arrayList.isEmpty()) {
                    int var6_15 = 0;
                    int[] nArray = new int[arrayList.size()];
                    boolean bl2 = false;
                    while (var6_15 < nArray.length) {
                        nArray[var6_15] = (Integer)arrayList.get((int)var6_15);
                        ++var6_15;
                    }
                    if (!Arrays.equals(nArray, this.list.getSelectedIndices())) {
                        this.list.setSelectedIndices(nArray);
                    }
                }
                this.list.repaint();
            }
        }
        finally {
            this.syncing = false;
        }
        this.showForm();
        this.updateTitle();
    }

    private void updateTitle() {
        boolean bl;
        Macro macro = this.work.current();
        String string = macro == null ? "" : macro.name;
        this.setTitle("Pa\u0219i \u2014 " + string + " \u00b7 " + DeskWork.modeName(this.work.kind()));
        if (!this.name.isFocusOwner() && !this.name.getText().equals(string)) {
            this.name.setText(string);
        }
        this.status.setText(!(bl = this.work.ws.isSaved()) ? "nesalvat" : (this.work.ws.isDirty() ? "modificat" : "salvat"));
        this.save.setText(bl && !this.work.ws.isDirty() ? "Salvat" : "Salveaz\u0103");
        this.undo.setEnabled(this.work.ws.canUndo());
        this.undo.setToolTipText((String)(this.work.ws.canUndo() ? "Anuleaz\u0103: " + this.work.ws.undoLabel() + " (Ctrl+Z)" : "Ctrl+Z"));
        this.redo.setEnabled(this.work.ws.canRedo());
        this.redo.setToolTipText((String)(this.work.ws.canRedo() ? "Ref\u0103: " + this.work.ws.redoLabel() + " (Ctrl+Y)" : "Ctrl+Y"));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void selectionChanged() {
        List<Action> list = this.list.getSelectedValuesList();
        ArrayList<Action> arrayList = new ArrayList<Action>();
        for (Action action : list) {
            if (!Markers.shown(action)) continue;
            arrayList.add(action);
        }
        this.syncing = true;
        try {
            this.work.selectAll(arrayList);
        }
        finally {
            this.syncing = false;
        }
        this.showForm();
    }

    private void showForm() {
        Action action;
        List<Action> list = this.list.getSelectedValuesList();
        Action action2 = action = list.size() == 1 ? list.get(0) : null;
        if (this.form != null && this.form.a == action) {
            this.form.reload();
            return;
        }
        this.flush();
        this.formHolder.removeAll();
        this.form = null;
        if (action != null) {
            this.form = new StepForm(action, this);
            this.formHolder.add((Component)this.form, "North");
        } else if (this.model.isEmpty()) {
            this.formHolder.add(this.emptyState(), "Center");
        } else {
            JLabel jLabel = new JLabel((String)(list.isEmpty() ? "Alege un pas din list\u0103." : list.size() + " pa\u0219i selecta\u021bi: Duplic\u0103, \u0218terge, Sus/Jos merg pe to\u021bi."));
            jLabel.setFont(Ui.font(0, 14.0f));
            jLabel.setForeground(Theme.GREY);
            jLabel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 16));
            this.formHolder.add((Component)jLabel, "North");
        }
        this.formHolder.revalidate();
        this.formHolder.repaint();
    }

    private JPanel emptyState() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.WINDOW_BG);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.CENTER; c.insets = new Insets(7, 18, 7, 18);
        JLabel eyebrow = new JLabel("EDITOR VIZUAL");
        eyebrow.setFont(Ui.font(1, 11.0f));
        eyebrow.setForeground(Theme.TEAL);
        c.gridy = 0; panel.add(eyebrow, c);
        JLabel title = new JLabel("Construiește automatizarea");
        title.setFont(Ui.font(1, 22.0f));
        title.setForeground(Theme.INK);
        c.gridy = 1; panel.add(title, c);
        JLabel note = new JLabel("Adaugă acțiuni, apoi selectează un pas ca să-i editezi detaliile.");
        note.setFont(Ui.font(0, 13.0f));
        note.setForeground(Theme.GREY);
        c.gridy = 2; panel.add(note, c);
        JButton add = Ui.primary("+ Adaugă primul pas", Theme.TEAL);
        add.addActionListener(e -> this.addMenu().show(add, 0, add.getHeight()));
        c.gridy = 3; c.insets = new Insets(18, 18, 7, 18); panel.add(add, c);
        return panel;
    }

    @Override
    public void changed(String string) {
        this.pendingLabel = "\u201e" + string + "\u201d";
        this.ctl.markers.place();
        this.list.repaint();
        this.commitLater.restart();
    }

    @Override
    public void rebuild() {
        this.flush();
        Action action = this.form == null ? null : this.form.a;
        this.formHolder.removeAll();
        this.form = null;
        if (action != null) {
            this.form = new StepForm(action, this);
            this.formHolder.add((Component)this.form, "North");
        }
        this.formHolder.revalidate();
        this.formHolder.repaint();
        this.ctl.markers.sync();
        this.list.repaint();
    }

    @Override
    public void pick(Picker.Mode mode, Picker.Result result) {
        ArrayList<Window> arrayList = new ArrayList<Window>();
        arrayList.add(this);
        arrayList.add(this.ctl.bar);
        for (Window window : Window.getWindows()) {
            if (!(window instanceof Markers.Piece) || !window.isVisible()) continue;
            arrayList.add(window);
        }
        Picker.pick(this.ctl.app, mode, arrayList.toArray(new Window[0]), result);
    }

    @Override
    public App app() {
        return this.ctl.app;
    }

    @Override
    public Macro owner() {
        return this.work.current();
    }

    void flush() {
        this.commitLater.stop();
        if (this.pendingLabel == null) {
            return;
        }
        String string = this.pendingLabel;
        this.pendingLabel = null;
        this.work.commit("editarea " + string);
    }

    private List<Action> selected() {
        return this.list.getSelectedValuesList();
    }

    private void duplicate() {
        this.flush();
        List<Action> list = this.selected();
        if (list.isEmpty()) {
            return;
        }
        List<Action> list2 = this.work.duplicate(list);
        this.selectExact(list2);
    }

    private void delete() {
        this.flush();
        List<Action> list = this.selected();
        if (list.isEmpty()) {
            return;
        }
        int n = this.list.getMinSelectionIndex();
        this.work.remove(list, (String)(list.size() > 1 ? "\u0219tergerea a " + list.size() + " pa\u0219i" : "\u0219tergerea pasului"));
        if (!this.model.isEmpty()) {
            this.list.setSelectedIndex(Math.min(n, this.model.size() - 1));
        }
    }

    private void move(int n) {
        this.flush();
        List<Action> list = this.selected();
        Macro macro = this.work.current();
        if (list.isEmpty() || macro == null) {
            return;
        }
        List<Action> list2 = macro.actions;
        int[] nArray = this.list.getSelectedIndices();
        if (n < 0 && nArray[0] == 0) {
            return;
        }
        if (n > 0 && nArray[nArray.length - 1] == list2.size() - 1) {
            return;
        }
        if (n < 0) {
            for (int n2 : nArray) {
                Collections.swap(list2, n2, n2 - 1);
            }
        } else {
            for (int i = nArray.length - 1; i >= 0; --i) {
                Collections.swap(list2, nArray[i], nArray[i] + 1);
            }
        }
        this.work.commit("mutarea \u00een list\u0103");
        this.selectExact(list);
    }

    private void moveTo(int n) {
        Macro macro = this.work.current();
        List<Action> list = this.selected();
        if (macro == null || list.isEmpty()) {
            return;
        }
        List<Action> list2 = macro.actions;
        int n2 = 0;
        for (int i = 0; i < n && i < list2.size(); ++i) {
            if (!list.contains(list2.get(i))) continue;
            ++n2;
        }
        ArrayList<Action> arrayList = new ArrayList<Action>(list2);
        arrayList.removeIf(list::contains);
        int n3 = Math.max(0, Math.min(arrayList.size(), n - n2));
        arrayList.addAll(n3, list);
        if (arrayList.equals(list2)) {
            return;
        }
        list2.clear();
        list2.addAll(arrayList);
        this.work.commit("mutarea \u00een list\u0103");
        this.selectExact(list);
    }

    private void selectExact(List<Action> list) {
        ArrayList<Integer> arrayList = new ArrayList<Integer>();
        for (int i = 0; i < this.model.size(); ++i) {
            for (Action action : list) {
                if (this.model.get(i) != action) continue;
                arrayList.add(i);
            }
        }
        int[] nArray = new int[arrayList.size()];
        for (int i = 0; i < nArray.length; ++i) {
            nArray[i] = (Integer)arrayList.get(i);
        }
        this.list.setSelectedIndices(nArray);
        if (nArray.length > 0) {
            this.list.ensureIndexIsVisible(nArray[0]);
        }
    }

    private void rename() {
        Macro macro = this.work.current();
        String string = this.name.getText().trim();
        if (macro == null || string.isEmpty() || string.equals(macro.name)) {
            return;
        }
        if (!this.work.ws.isSaved()) {
            this.flush();
            String string2 = this.work.saveAs(string);
            if (string2 != null) {
                this.ctl.message(string2);
                this.name.setText(macro.name);
            } else {
                this.ctl.app.overlays.toast("Salvat \u201e" + this.work.current().name + "\u201d.");
            }
            return;
        }
        macro.name = this.ctl.app.store.uniqueNameExcept(string, macro.id);
        this.work.commit("redenumirea");
    }

    private JPopupMenu addMenu() {
        JPopupMenu jPopupMenu = new JPopupMenu();
        jPopupMenu.setBackground(Theme.LIGHT);
        jPopupMenu.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        jPopupMenu.add(Ui.item("Click", () -> this.addAfter(this.target(Workspace.Target.TAP))));
        jPopupMenu.add(Ui.item("Trage (swipe)", () -> this.addAfter(this.target(Workspace.Target.SWIPE))));
        jPopupMenu.add(Ui.item("Pauz\u0103", () -> this.addAfter(Action.waitFor(1000L))));
        JMenu jMenu = Ui.submenu("Avansat  \u203a");
        jMenu.add(Ui.item("Click \u021binut", () -> this.addAfter(this.target(Workspace.Target.LONG_PRESS))));
        jMenu.add(Ui.item("Dublu click", () -> this.addAfter(Action.doubleTap((float)this.ctl.app.screen.width / 2.0f, (float)this.ctl.app.screen.height / 2.0f))));
        jMenu.add(Ui.item("Click dreapta / mijloc, roti\u021b\u0103, mutare", () -> this.addAfter(Action.mouse(1, (float)this.ctl.app.screen.width / 2.0f, (float)this.ctl.app.screen.height / 2.0f))));
        jMenu.add(Ui.item("Tast\u0103", () -> this.addAfter(Action.key("enter", 0, 30L))));
        jMenu.add(Ui.item("Scrie un text", () -> this.addAfter(Action.typeText(""))));
        jMenu.add(Ui.item("Imagine (o alegi de pe ecran)", () -> this.pick(Picker.Mode.IMAGE, (n, n2, n3, n4, n5, screenFrame) -> {
            try {
                Action action = Action.image(ImageCut.make(this.ctl.app, screenFrame, n, n2, n3, n4, ""));
                action.waitMode = 0;
                this.addAfter(action);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.ctl.message(illegalArgumentException.getMessage());
            }
        })));
        jMenu.add(Ui.item("A\u0219teapt\u0103 o culoare (pipet\u0103)", () -> this.pick(Picker.Mode.COLOR, (n, n2, n3, n4, n5, screenFrame) -> this.addAfter(Action.waitColor(n, n2, n5, 30000L)))));
        jMenu.add(Ui.item("Cite\u0219te num\u0103r / text din zon\u0103", () -> this.pick(Picker.Mode.ZONE, (n, n2, n3, n4, n5, screenFrame) -> this.addAfter(Action.read(n, n2, n3, n4, 0, "numar")))));
        jMenu.add(Ui.item("Afi\u0219eaz\u0103 \u00een consol\u0103", () -> this.addAfter(Action.print("Tura {tura}, timp {timp} s"))));
        jMenu.add(Ui.item("Seteaz\u0103 o variabil\u0103", () -> this.addAfter(Action.setVar("n", "n + 1"))));
        jMenu.add(Ui.item("Ruleaz\u0103 un macro sau un script", () -> this.addAfter(Action.macro(null, 1))));
        jMenu.add(Ui.item("Deschide un program", () -> this.addAfter(Action.launchApp("", 2000L))));
        jPopupMenu.add(jMenu);
        return jPopupMenu;
    }

    private Action target(Workspace.Target target) {
        Macro macro = this.work.current();
        Action action;
        if (target == Workspace.Target.SWIPE) {
            action = Action.swipe((float)this.ctl.app.screen.width / 2.0f, (float)this.ctl.app.screen.height * 0.62f, (float)this.ctl.app.screen.width / 2.0f, (float)this.ctl.app.screen.height * 0.38f, macro.settings.swipeDuration);
        } else if (target == Workspace.Target.LONG_PRESS) {
            action = Action.longPress((float)this.ctl.app.screen.width / 2.0f, (float)this.ctl.app.screen.height / 2.0f, 800L);
        } else {
            action = Action.tap((float)this.ctl.app.screen.width / 2.0f, (float)this.ctl.app.screen.height / 2.0f);
            action.duration = macro.settings.tapDuration;
        }
        action.delay = macro.settings.tapInterval;
        return action;
    }

    private void addAfter(Action action) {
        this.flush();
        Macro macro = this.work.current();
        if (macro == null) {
            return;
        }
        int n = this.list.getMaxSelectionIndex();
        if (action.delay == 0L && !macro.actions.isEmpty() && action.type != Action.Type.WAIT && !action.isGesture()) {
            action.delay = 0L;
        }
        if (n >= 0 && n < macro.actions.size()) {
            macro.actions.add(n + 1, action);
        } else {
            macro.actions.add(action);
        }
        this.work.commit("ad\u0103ugarea");
        this.selectExact(Collections.singletonList(action));
        this.right.setSelectedIndex(0);
    }

    private JPopupMenu advancedMenu() {
        JPopupMenu jPopupMenu = new JPopupMenu();
        jPopupMenu.setBackground(Theme.LIGHT);
        jPopupMenu.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        jPopupMenu.add(Ui.item("Ruleaz\u0103 de la pasul selectat", () -> {
            this.flush();
            int n = this.list.getMinSelectionIndex();
            this.ctl.playFrom(Math.max(0, n));
        }));
        jPopupMenu.add(Ui.item("Previzualizeaz\u0103 (f\u0103r\u0103 clicuri)", () -> {
            this.flush();
            this.ctl.preview();
        }));
        jPopupMenu.add(Ui.item("Export\u0103 cod (\u00eel copiez)", () -> {
            this.flush();
            Macro macro = this.work.current();
            if (macro == null) {
                return;
            }
            if (!this.work.ws.isSaved() && !this.ctl.saveFlow()) {
                return;
            }
            String string = this.ctl.app.store.exportCode(Collections.singletonList(this.ctl.app.store.find(this.work.current().id)));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(string), null);
            this.ctl.message("Am copiat codul FreeClick (" + string.length() + " caractere). \u00cel lipe\u0219ti pe telefon \u00een Import\u0103 sau pe alt calculator.");
        }));
        JMenu jMenu = Ui.submenu("Avansat  \u203a");
        jMenu.add(Ui.item("Activeaz\u0103 / dezactiveaz\u0103 pa\u0219ii selecta\u021bi", () -> {
            this.flush();
            List<Action> list = this.selected();
            if (list.isEmpty()) {
                return;
            }
            boolean bl = !list.get((int)0).enabled;
            for (Action action : list) {
                action.enabled = bl;
            }
            this.work.commit(bl ? "activarea" : "dezactivarea");
        }));
        jMenu.add(Ui.item("F\u0103 un script din pa\u0219i", () -> {
            this.flush();
            this.ctl.toScript();
        }));
        jMenu.add(Ui.item("\u00cencepe din nou (gol)", () -> {
            this.flush();
            this.ctl.newFlow();
        }));
        jPopupMenu.add(jMenu);
        return jPopupMenu;
    }

    private JPanel settingsPanel() {
        this.settings = new JPanel(new GridBagLayout());
        this.settings.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new Insets(4, 0, 4, 8);
        gridBagConstraints.anchor = 17;
        this.sNum(gridBagConstraints, "C\u00e2te ture (0 = p\u00e2n\u0103 o opresc)", () -> this.work.current().settings.loops, l -> {
            this.work.current().settings.loops = (int)l;
        }, 0L, 1000000L, 1L);
        this.sNum(gridBagConstraints, "Pauz\u0103 \u00eentre ture (ms)", () -> this.work.current().settings.loopDelay, l -> {
            this.work.current().settings.loopDelay = l;
        }, 0L, 86400000L, 50L);
        this.sSpeed(gridBagConstraints);
        this.sNum(gridBagConstraints, "Decalaj aleator al clicurilor (px)", () -> this.work.current().settings.jitterPx, l -> {
            this.work.current().settings.jitterPx = (int)l;
        }, 0L, 200L, 1L);
        this.sNum(gridBagConstraints, "Varia\u021bie aleatoare a pauzelor (%)", () -> this.work.current().settings.jitterTimePct, l -> {
            this.work.current().settings.jitterTimePct = (int)l;
        }, 0L, 90L, 5L);
        this.sNum(gridBagConstraints, "Opre\u0219te dup\u0103 (minute, 0 = niciodat\u0103)", () -> this.work.current().settings.stopAfterMs / 60000L, l -> {
            this.work.current().settings.stopAfterMs = l * 60000L;
        }, 0L, 100000L, 1L);
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new Insets(16, 0, 4, 0);
        JLabel jLabel = new JLabel("Pentru \u021bintele noi");
        jLabel.setFont(Ui.font(1, 14.0f));
        this.settings.add((Component)jLabel, gridBagConstraints);
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.insets = new Insets(4, 0, 4, 8);
        this.sNum(gridBagConstraints, "Pauz\u0103 \u00eenainte de fiecare \u021bint\u0103 (ms)", () -> this.work.current().settings.tapInterval, l -> {
            this.work.current().settings.tapInterval = l;
        }, 0L, 3600000L, 50L);
        this.sNum(gridBagConstraints, "Durata unui click (ms)", () -> this.work.current().settings.tapDuration, l -> {
            this.work.current().settings.tapDuration = Math.max(1L, l);
        }, 1L, 60000L, 10L);
        this.sNum(gridBagConstraints, "Durata unei trageri (ms)", () -> this.work.current().settings.swipeDuration, l -> {
            this.work.current().settings.swipeDuration = Math.max(1L, l);
        }, 1L, 60000L, 50L);
        JButton jButton = Ui.secondary("Aplic\u0103 la toate \u021bintele de acum");
        jButton.addActionListener(actionEvent -> {
            Macro macro = this.work.current();
            MacroTransform.applyManualTiming(macro, macro.settings.tapInterval, macro.settings.tapDuration, macro.settings.swipeDuration);
            this.work.commit("aplicarea duratelor");
        });
        gridBagConstraints.gridx = 1;
        ++gridBagConstraints.gridy;
        this.settings.add((Component)jButton, gridBagConstraints);
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new Insets(16, 0, 4, 0);
        JLabel jLabel2 = new JLabel("C\u00e2t ruleaz\u0103 (pentru tot FreeClick)");
        jLabel2.setFont(Ui.font(1, 14.0f));
        this.settings.add((Component)jLabel2, gridBagConstraints);
        gridBagConstraints.insets = new Insets(2, 0, 2, 0);
        ++gridBagConstraints.gridy;
        this.settings.add((Component)this.appCheck("Pauz\u0103 c\u00e2nd mi\u0219ti tu mouse-ul", "pauseOnMouse", true), gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.weighty = 1.0;
        JPanel jPanel = new JPanel();
        jPanel.setOpaque(false);
        this.settings.add((Component)jPanel, gridBagConstraints);
        return this.settings;
    }

    private JCheckBox appCheck(String string, String string2, boolean bl) {
        JCheckBox jCheckBox = new JCheckBox(string, this.ctl.app.store.getBool(string2, bl));
        jCheckBox.setFont(Ui.font(0, 14.0f));
        jCheckBox.addActionListener(actionEvent -> this.ctl.app.store.set(string2, jCheckBox.isSelected()));
        return jCheckBox;
    }

    private void sNum(GridBagConstraints gridBagConstraints, String string, Getter getter, Setter setter, long l, long l2, long l3) {
        long l4 = this.work.current() == null ? l : getter.get();
        JSpinner jSpinner = new JSpinner(new SpinnerNumberModel((Number)Math.max(l, Math.min(l2, l4)), Long.valueOf(l), Long.valueOf(l2), (Number)l3));
        jSpinner.setFont(Ui.font(0, 14.0f));
        ((JSpinner.DefaultEditor)jSpinner.getEditor()).getTextField().setColumns(8);
        jSpinner.addChangeListener(changeEvent -> {
            if (this.settingsLoading || this.work.current() == null) {
                return;
            }
            setter.set(((Number)jSpinner.getValue()).longValue());
            this.pendingLabel = "\u201e" + string.toLowerCase(Locale.ROOT) + "\u201d";
            this.commitLater.restart();
        });
        this.settingsReload.add(() -> {
            if (this.work.current() == null || ((JSpinner.DefaultEditor)jSpinner.getEditor()).getTextField().isFocusOwner()) {
                return;
            }
            long currentValue = Math.max(l, Math.min(l2, getter.get()));
            if (((Number)jSpinner.getValue()).longValue() != currentValue) {
                jSpinner.setValue(currentValue);
            }
        });
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        gridBagConstraints.weightx = 0.0;
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(Ui.font(0, 14.0f));
        this.settings.add((Component)jLabel, gridBagConstraints);
        gridBagConstraints.gridx = 1;
        gridBagConstraints.weightx = 1.0;
        JPanel jPanel = new JPanel(new FlowLayout(0, 0, 0));
        jPanel.setOpaque(false);
        jPanel.add(jSpinner);
        this.settings.add((Component)jPanel, gridBagConstraints);
    }

    private void sSpeed(GridBagConstraints gridBagConstraints) {
        JSpinner jSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 10.0, 0.1));
        jSpinner.setFont(Ui.font(0, 14.0f));
        jSpinner.addChangeListener(changeEvent -> {
            if (this.settingsLoading || this.work.current() == null) {
                return;
            }
            double d = ((Number)jSpinner.getValue()).doubleValue();
            this.work.ws.setSpeed(d);
            this.ctl.main.runner.setSpeed(d);
            this.ctl.refresh();
        });
        this.settingsReload.add(() -> {
            if (this.work.current() == null) {
                return;
            }
            double d = this.work.current().settings.speed;
            if (Math.abs(((Number)jSpinner.getValue()).doubleValue() - d) > 1.0E-9) {
                jSpinner.setValue(d);
            }
        });
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        JLabel jLabel = new JLabel("Viteza (1 = normal, 2 = de dou\u0103 ori mai repede)");
        jLabel.setFont(Ui.font(0, 14.0f));
        this.settings.add((Component)jLabel, gridBagConstraints);
        gridBagConstraints.gridx = 1;
        JPanel jPanel = new JPanel(new FlowLayout(0, 0, 0));
        jPanel.setOpaque(false);
        jPanel.add(jSpinner);
        this.settings.add((Component)jPanel, gridBagConstraints);
    }

    private void refreshSettings() {
        this.settingsLoading = true;
        try {
            for (Runnable runnable : this.settingsReload) {
                runnable.run();
            }
        }
        finally {
            this.settingsLoading = false;
        }
    }

    private final class Reorder
    extends MouseAdapter {
        int from = -1;
        int over = -1;
        boolean dragging;

        private Reorder() {
        }

        @Override
        public void mousePressed(MouseEvent mouseEvent) {
            this.from = StepsWindow.this.list.locationToIndex(mouseEvent.getPoint());
            this.dragging = false;
        }

        @Override
        public void mouseDragged(MouseEvent mouseEvent) {
            if (this.from < 0 || !StepsWindow.this.list.isSelectedIndex(this.from)) {
                return;
            }
            this.dragging = true;
            int n = StepsWindow.this.list.locationToIndex(mouseEvent.getPoint());
            if (n < 0) {
                return;
            }
            Rectangle rectangle = StepsWindow.this.list.getCellBounds(n, n);
            this.over = mouseEvent.getY() > rectangle.y + rectangle.height / 2 ? n + 1 : n;
            StepsWindow.this.list.setCursor(Cursor.getPredefinedCursor(13));
            StepsWindow.this.list.repaint();
        }

        @Override
        public void mouseReleased(MouseEvent mouseEvent) {
            StepsWindow.this.list.setCursor(Cursor.getDefaultCursor());
            if (this.dragging && this.over >= 0) {
                StepsWindow.this.flush();
                StepsWindow.this.moveTo(this.over);
            }
            this.dragging = false;
            this.over = -1;
            this.from = -1;
            StepsWindow.this.list.repaint();
        }
    }

    private final class Row
    extends JPanel
    implements ListCellRenderer<Action> {
        private final JLabel num;
        private final JLabel text;
        private final JLabel sub;
        private Color dot;
        private boolean off;

        Row() {
            super(new BorderLayout(8, 0));
            this.num = new JLabel();
            this.text = new JLabel();
            this.sub = new JLabel();
            this.dot = Theme.MARKER_TAP;
            this.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            this.num.setFont(Ui.font(1, 13.0f));
            this.num.setPreferredSize(new Dimension(34, 20));
            this.num.setHorizontalAlignment(4);
            this.text.setFont(Ui.font(0, 14.0f));
            this.sub.setFont(Ui.font(0, 12.0f));
            this.sub.setForeground(Theme.GREY);
            JPanel jPanel = new JPanel(new BorderLayout());
            jPanel.setOpaque(false);
            jPanel.add((Component)this.text, "Center");
            jPanel.add((Component)this.sub, "South");
            JPanel jPanel2 = new JPanel(new BorderLayout()){

                @Override
                protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    Graphics2D graphics2D = (Graphics2D)graphics;
                    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics2D.setColor(Row.this.off ? Theme.MARKER_OFF : Row.this.dot);
                    graphics2D.fillOval(2, this.getHeight() / 2 - 6, 12, 12);
                }
            };
            jPanel2.setOpaque(false);
            jPanel2.setPreferredSize(new Dimension(52, 20));
            jPanel2.add((Component)this.num, "East");
            this.add((Component)jPanel2, "West");
            this.add((Component)jPanel, "Center");
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Action> jList, Action action, int n, boolean bl, boolean bl2) {
            this.num.setText(n + 1 + ".");
            this.dot = Theme.marker(action);
            this.off = !action.enabled;
            String string = ActionFormatter.describe(action, StepsWindow.this.ctl.app.store);
            int n2 = string.indexOf(" \u00b7 dup\u0103 ");
            String string2 = n2 > 0 ? string.substring(0, n2) : string;
            this.text.setText(string2);
            this.text.setForeground(action.enabled ? Theme.INK : Theme.MARKER_OFF);
            StringBuilder stringBuilder = new StringBuilder();
            if (action.delay > 0L) {
                stringBuilder.append("dup\u0103 ").append(ActionFormatter.ms(action.delay));
            }
            if (action.label != null && !action.label.trim().isEmpty()) {
                stringBuilder.append(stringBuilder.length() > 0 ? " \u00b7 " : "").append(action.label.trim());
            }
            if (action.isPcOnly()) {
                stringBuilder.append(stringBuilder.length() > 0 ? " \u00b7 " : "").append("pe telefon e s\u0103rit");
            }
            this.sub.setText(stringBuilder.length() == 0 ? " " : stringBuilder.toString());
            this.setBackground(action == StepsWindow.this.playing ? Theme.LIST_PLAYING : (bl ? Theme.LIST_SELECTED : Color.WHITE));
            this.setOpaque(true);
            return this;
        }
    }

    static interface Getter {
        public long get();
    }

    static interface Setter {
        public void set(long var1);
    }
}


