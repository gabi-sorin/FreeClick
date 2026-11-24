/*
 * Decompiled with CFR 0.152.
 */
package com.freeclick.desktop;

import com.freeclick.core.Action;
import com.freeclick.core.Macro;
import com.freeclick.core.script.CodeFromSteps;
import com.freeclick.core.script.ScriptExamples;
import com.freeclick.desktop.AutoClicker;
import com.freeclick.desktop.ConsolePane;
import com.freeclick.desktop.EditorWindow;
import com.freeclick.desktop.Hotkeys;
import com.freeclick.desktop.Main;
import com.freeclick.desktop.Overlays;
import com.freeclick.desktop.Paths;
import com.freeclick.desktop.Picker;
import com.freeclick.desktop.Store;
import com.freeclick.desktop.Ui;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

final class MainWindow
extends JFrame {
    static {
        Color bg = new Color(0x11171E);
        Color surface = new Color(0x1B242E);
        Color ink = new Color(0xEEF3F8);
        UIManager.put("Panel.background", surface);
        UIManager.put("TabbedPane.background", bg);
        UIManager.put("TabbedPane.foreground", ink);
        UIManager.put("TabbedPane.selected", surface);
        UIManager.put("TabbedPane.contentAreaColor", bg);
        UIManager.put("TabbedPane.focus", new Color(0x38D6B0));
        UIManager.put("RadioButton.background", surface);
        UIManager.put("RadioButton.foreground", ink);
        UIManager.put("CheckBox.background", surface);
        UIManager.put("CheckBox.foreground", ink);
        UIManager.put("TextField.background", new Color(0x26313D));
        UIManager.put("TextField.foreground", ink);
        UIManager.put("TextField.caretForeground", ink);
        UIManager.put("ComboBox.background", new Color(0x26313D));
        UIManager.put("ComboBox.foreground", ink);
        UIManager.put("Spinner.background", new Color(0x26313D));
        UIManager.put("List.background", surface);
        UIManager.put("List.foreground", ink);
        UIManager.put("OptionPane.background", surface);
        UIManager.put("OptionPane.messageForeground", ink);
    }
    private final Main main;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton[] navButtons = new JButton[5];
    private final JLabel pageTitle = new JLabel();
    private final JLabel pageDescription = new JLabel();
    private final JSpinner interval = new JSpinner(new SpinnerNumberModel(100, 1, 3600000, 10));
    private final JComboBox<String> unit = new JComboBox<String>(new String[]{"milisecunde", "secunde", "minute"});
    private final JComboBox<String> button = new JComboBox<String>(new String[]{"St\u00e2nga", "Dreapta", "Mijloc"});
    private final JComboBox<String> clickType = new JComboBox<String>(new String[]{"Simplu", "Dublu"});
    private final JRadioButton atMouse = new JRadioButton("Unde e mouse-ul");
    private final JRadioButton atPoint = new JRadioButton("\u00centr-un punct ales:");
    private final JLabel pointLabel = new JLabel("(niciunul)");
    private final JRadioButton forever = new JRadioButton("P\u00e2n\u0103 \u00eel opresc");
    private final JRadioButton nTimes = new JRadioButton("De");
    private final JSpinner times = new JSpinner(new SpinnerNumberModel(100, 1, 100000000, 10));
    private final JButton autoButton = Ui.primary("", Ui.TEAL);
    private final JLabel autoCount = new JLabel(" ");
    private int px = -1;
    private int py = -1;
    private final DefaultListModel<Macro> items = new DefaultListModel();
    private final JList<Macro> list = new JList<Macro>(this.items);
    private final DefaultListModel<Macro> scriptItems = new DefaultListModel();
    private final JList<Macro> scriptList = new JList<Macro>(this.scriptItems);
    private JPanel codeEmpty;
    private JPanel codeCenter;
    private JScrollPane codeScroll;
    private JTextField listSearch;
    private JPanel automationListCards;
    private JLabel automationEmpty;
    private JLabel automationName;
    private JLabel automationType;
    private JLabel automationInfo;
    private JButton editSelectedButton;
    private JButton advancedAutomationButton;
    private final JButton runButton = Ui.primary("", Ui.TEAL);
    private final JButton barButton = Ui.primary("", Ui.TEAL);
    private JLabel barStatus;
    private JLabel barMetric;
    private JLabel savedCount;
    private final List<JRadioButton> modeButtons = new ArrayList<JRadioButton>();
    private final List<JPanel> modeCards = new ArrayList<JPanel>();
    static final int TAB_SCRIPTS = 3;

    MainWindow(Main main) {
        super("FreeClick");
        this.main = main;
        this.setIconImages(Ui.icons());
        this.setDefaultCloseOperation(0);
        this.addWindowListener(new WindowAdapter(){

            @Override
            public void windowClosing(WindowEvent windowEvent) {
                MainWindow.this.closeWindow();
            }
        });
        this.tabs.setFont(Ui.font(1, 14.0f));
        this.tabs.addTab("Panou", this.barTab());
        this.tabs.addTab("Click", this.autoTab());
        this.tabs.addTab("Automatizări", this.libraryTab());
        this.tabs.addTab("Cod", this.codeTab());
        this.tabs.addTab("Set\u0103ri", this.settingsTab());
        this.getContentPane().setBackground(Theme.WINDOW_BG);
        this.tabs.setBackground(Theme.WINDOW_BG);
        this.tabs.setForeground(Theme.INK);
        this.tabs.setOpaque(true);
        this.tabs.setUI(new BasicTabbedPaneUI() {
            @Override protected int calculateTabAreaHeight(int placement, int runs, int maxHeight) { return 0; }
            @Override protected int calculateTabAreaWidth(int placement, int runs, int maxWidth) { return 0; }
            @Override protected Insets getContentBorderInsets(int placement) { return new Insets(0, 0, 0, 0); }
            @Override protected void paintTabArea(java.awt.Graphics g, int placement, int selectedIndex) { }
        });
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(Theme.WINDOW_BG);
        shell.add(this.navigationRail(), BorderLayout.WEST);
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(Theme.WINDOW_BG);
        workspace.add(this.workspaceHeader(), BorderLayout.NORTH);
        workspace.add(this.tabs, BorderLayout.CENTER);
        shell.add(workspace, BorderLayout.CENTER);
        this.getContentPane().setLayout(new BorderLayout());
        this.getContentPane().add(shell, BorderLayout.CENTER);
        main.auto.addListener((bl, l) -> this.refreshAuto());
        main.runner.addListener(this::refreshRun);
        main.app.store.onChange(() -> SwingUtilities.invokeLater(() -> {
            this.refreshList();
            this.refreshCodeList();
        }));
        this.refreshAuto();
        this.refreshList();
        this.refreshCodeList();
        this.refreshRun();
        int n = main.app.store.getInt("tab2", 0);
        if (n == 3) n = 4;
        if (n > 0 && n < this.tabs.getTabCount()) {
            this.tabs.setSelectedIndex(n);
        }
        this.tabs.addChangeListener(changeEvent -> {
            main.app.store.set("tab2", this.tabs.getSelectedIndex());
            this.styleNavigation();
            this.refreshBarTab();
        });
        this.styleNavigation();
        this.setSize(1180, 800);
        this.setMinimumSize(new Dimension(960, 660));
        this.setLocationRelativeTo(null);
    }

    private JPanel navigationRail() {
        JPanel rail = new JPanel(new BorderLayout());
        rail.setPreferredSize(new Dimension(218, 0));
        rail.setBackground(new Color(0x141B23));
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));
        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBackground(new Color(0x141B23));
        brand.setBorder(BorderFactory.createEmptyBorder(24, 20, 22, 18));
        JLabel name = new JLabel("FreeClick");
        name.setFont(Ui.font(1, 22.0f));
        name.setForeground(Theme.INK);
        JLabel edition = Ui.note("WINDOWS DESKTOP");
        edition.setFont(Ui.font(1, 10.5f));
        brand.add(name);
        brand.add(Box.createVerticalStrut(5));
        brand.add(edition);
        rail.add(brand, BorderLayout.NORTH);

        JPanel items = new JPanel();
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        items.setOpaque(false);
        items.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));
        JLabel label = Ui.note("SPAȚIU DE LUCRU");
        label.setFont(Ui.font(1, 10.5f));
        label.setBorder(BorderFactory.createEmptyBorder(2, 8, 9, 0));
        items.add(label);
        String[] names = {"Panou", "Click", "Automatizări", "Cod", "Setări"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            JButton button = new JButton(names[i]);
            button.setFont(Ui.font(1, 13.0f));
            button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            button.setMargin(new Insets(0, 14, 0, 8));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 8));
            button.addActionListener(e -> this.tabs.setSelectedIndex(index));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
            this.navButtons[i] = button;
            items.add(button);
            items.add(Box.createVerticalStrut(4));
        }
        rail.add(items, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setBackground(new Color(0x141B23));
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE), BorderFactory.createEmptyBorder(14, 20, 16, 18)));
        JLabel state = Ui.note("Gata de lucru");
        state.setForeground(Theme.GREY);
        JLabel version = Ui.note("FreeClick pentru Windows");
        version.setFont(Ui.font(0, 11.0f));
        footer.add(state, BorderLayout.NORTH);
        footer.add(version, BorderLayout.SOUTH);
        rail.add(footer, BorderLayout.SOUTH);
        return rail;
    }

    private JPanel workspaceHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(Theme.WINDOW_BG);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), BorderFactory.createEmptyBorder(18, 26, 16, 24)));
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        this.pageTitle.setFont(Ui.font(1, 20.0f));
        this.pageTitle.setForeground(Theme.INK);
        this.pageDescription.setFont(Ui.font(0, 12.5f));
        this.pageDescription.setForeground(Theme.GREY);
        titles.add(this.pageTitle);
        titles.add(Box.createVerticalStrut(3));
        titles.add(this.pageDescription);
        header.add(titles, BorderLayout.CENTER);
        JLabel badge = new JLabel("●  LOCAL");
        badge.setFont(Ui.font(1, 10.5f));
        badge.setForeground(Ui.TEAL);
        badge.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private void styleNavigation() {
        int selected = this.tabs.getSelectedIndex();
        String[] titles = {"Panou", "Click", "Automatizări", "Cod", "Setări"};
        String[] descriptions = {"Alege modul de lucru și pornește panoul flotant.", "Configurează clicurile repetitive și comportamentul lor.", "Gestionează automatizările salvate.", "Scrie, importă și rulează scripturi.", "Personalizează comenzile și comportamentul aplicației."};
        if (selected >= 0 && selected < titles.length) {
            this.pageTitle.setText(titles[selected]);
            this.pageDescription.setText(descriptions[selected]);
        }
        for (int i = 0; i < this.navButtons.length; i++) {
            JButton button = this.navButtons[i];
            if (button == null) continue;
            boolean active = i == selected;
            Ui.flat(button, active ? new Color(0x203B3A) : new Color(0x141B23), active ? Ui.TEAL : Theme.GREY);
            button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            button.setMargin(new Insets(0, 14, 0, 8));
            button.setIcon(new NavIcon(i, active ? Ui.TEAL : Theme.GREY));
            button.setIconTextGap(11);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        }
    }

    private static final class NavIcon implements javax.swing.Icon {
        private final int type;
        private final Color color;
        NavIcon(int type, Color color) { this.type = type; this.color = color; }
        public int getIconWidth() { return 18; }
        public int getIconHeight() { return 18; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D)graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            if (type == 0) {
                g.drawRoundRect(2, 2, 6, 6, 2, 2); g.drawRoundRect(10, 2, 6, 6, 2, 2);
                g.drawRoundRect(2, 10, 6, 6, 2, 2); g.drawRoundRect(10, 10, 6, 6, 2, 2);
            } else if (type == 1) {
                g.drawOval(1, 1, 16, 16); g.drawOval(5, 5, 8, 8); g.fillOval(8, 8, 3, 3);
            } else if (type == 2) {
                for (int row = 0; row < 3; row++) { g.drawRoundRect(2, 2 + row * 5, 14, 3, 2, 2); }
            } else if (type == 3) {
                java.awt.geom.Path2D p = new java.awt.geom.Path2D.Float();
                p.moveTo(7, 3); p.lineTo(2, 9); p.lineTo(7, 15); g.draw(p);
                p.reset(); p.moveTo(11, 3); p.lineTo(16, 9); p.lineTo(11, 15); g.draw(p);
            } else {
                g.drawOval(5, 5, 8, 8); g.drawOval(8, 8, 2, 2);
                for (int a = 0; a < 8; a++) {
                    double angle = Math.PI * a / 4.0;
                    int x1 = 9 + (int)Math.round(5 * Math.cos(angle)); int y1 = 9 + (int)Math.round(5 * Math.sin(angle));
                    int x2 = 9 + (int)Math.round(8 * Math.cos(angle)); int y2 = 9 + (int)Math.round(8 * Math.sin(angle));
                    g.drawLine(x1, y1, x2, y2);
                }
            }
            g.dispose();
        }
    }

    private static final class RadioIcon implements javax.swing.Icon {
        private final boolean selected;
        private final Color color;
        RadioIcon(boolean selected, Color color) { this.selected = selected; this.color = color; }
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D)graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new java.awt.BasicStroke(1.6f));
            g.drawOval(2, 2, 12, 12);
            if (selected) g.fillOval(5, 5, 6, 6);
            g.dispose();
        }
    }
    private void closeWindow() {
        if (this.main.runner.running() || this.main.auto.on() || this.main.recorder.on()) {
            this.setState(1);
            return;
        }
        if (this.main.app.overlays.tray != null && this.main.app.store.getBool("closeToTray", false)) {
            this.setVisible(false);
            return;
        }
        this.main.quit();
    }

    private JComponent barTab() {
        JPanel page = new JPanel(new BorderLayout(16, 16));
        page.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        JPanel barCard = summaryCard("BARA FLOTANTĂ", "Ascunsă", "Mut-o oriunde pe ecran", Ui.TEAL);
        this.barMetric = (JLabel)barCard.getClientProperty("metric");
        stats.add(barCard);
        JPanel savedCard = summaryCard("BIBLIOTECĂ", "0 automatizări", "Macro-uri și scripturi salvate", new Color(0x8B9CF6));
        this.savedCount = (JLabel)savedCard.getClientProperty("metric");
        stats.add(savedCard);
        stats.add(summaryCard("SCURTĂTURĂ", Hotkeys.label(this.main.app.store, 3), "Pornește sau oprește execuția", new Color(0xF3B85C)));
        page.add(stats, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
        JPanel modes = new JPanel();
        modes.setLayout(new BoxLayout(modes, BoxLayout.Y_AXIS));
        modes.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(18, 18, 16, 18)));
        modes.setBackground(Theme.LIGHT);
        JLabel modeTitle = Ui.title("Alege modul de lucru");
        JLabel modeNote = Ui.note("Creează și rulează automatizări fără să scrii cod.");
        modeTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        modes.add(modeTitle);
        modes.add(Box.createVerticalStrut(4));
        modes.add(modeNote);
        modes.add(Box.createVerticalStrut(14));
        ButtonGroup buttonGroup = new ButtonGroup();
        String[][] options = {{"manual", "Ținte manuale", "Adaugă și aranjează acțiuni vizual."}, {"record", "Înregistrare", "Arată acțiunile o dată, apoi repetă-le."}, {"combo", "Combinații", "Rulează macro-uri și scripturi în ordine."}};
        for (String[] option : options) {
            JRadioButton choice = new JRadioButton("<html><b>" + option[1] + "</b><br><span style='color:#A1AEBB'>" + option[2] + "</span></html>");
            choice.setFont(Ui.font(0, 13.0f));
            choice.setOpaque(false);
            choice.setIcon(new RadioIcon(false, Theme.GREY));
            choice.setSelectedIcon(new RadioIcon(true, Ui.TEAL));
            choice.setActionCommand(option[0]);
            choice.addActionListener(e -> {
                this.main.bar().work.switchMode(e.getActionCommand());
                this.refreshBarTab();
            });
            buttonGroup.add(choice);
            this.modeButtons.add(choice);
            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(new Color(0x202B35));
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            card.add(choice, BorderLayout.CENTER);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
            modes.add(card);
            modes.add(Box.createVerticalStrut(8));
            this.modeCards.add(card);
        }
        body.add(modes);

        JPanel launch = new JPanel();
        launch.setLayout(new BoxLayout(launch, BoxLayout.Y_AXIS));
        launch.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(20, 20, 18, 20)));
        launch.setBackground(Theme.LIGHT);
        JLabel launchTitle = Ui.title("Panou flotant");
        launchTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.barStatus = Ui.note("Bara este ascunsă.<br>Deschide-o pentru a lucra peste aplicația ta.");
        this.barStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.barButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.barButton.setPreferredSize(new Dimension(250, 48));
        this.barButton.setMaximumSize(new Dimension(300, 48));
        launch.add(launchTitle);
        launch.add(Box.createVerticalStrut(6));
        launch.add(this.barStatus);
        launch.add(Box.createVerticalStrut(18));
        this.barButton.addActionListener(actionEvent -> {
            if (this.main.barShown()) {
                this.main.bar().hide();
            } else {
                this.main.bar().show();
            }
            this.refreshBarTab();
        });
        launch.add(this.barButton);
        launch.add(Box.createVerticalStrut(16));
        JLabel shortcut = Ui.note("Poți muta bara cu mouse-ul.<br>O poți restrânge din controlul ei.");
        shortcut.setAlignmentX(Component.LEFT_ALIGNMENT);
        launch.add(shortcut);
        launch.add(Box.createVerticalStrut(18));
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quick.setOpaque(false);
        JButton record = Ui.secondary("Înregistrează");
        record.addActionListener(e -> this.main.toggleRecord());
        JButton automations = Ui.secondary("Bibliotecă");
        automations.addActionListener(e -> this.tabs.setSelectedIndex(2));
        JButton code = Ui.secondary("Cod");
        code.addActionListener(e -> this.tabs.setSelectedIndex(3));
        quick.add(record);
        quick.add(automations);
        quick.add(code);
        quick.setAlignmentX(Component.LEFT_ALIGNMENT);
        launch.add(quick);
        launch.add(Box.createVerticalGlue());
        body.add(launch);
        page.add(body, BorderLayout.CENTER);
        this.refreshBarTab();
        return page;
    }

    private JPanel summaryCard(String label, String value, String detail, Color accent) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.LIGHT);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JLabel eyebrow = Ui.note(label);
        eyebrow.setFont(Ui.font(1, 10.5f));
        eyebrow.setForeground(accent);
        JLabel metric = new JLabel(value);
        metric.setFont(Ui.font(1, 17.0f));
        metric.setForeground(Theme.INK);
        JLabel note = Ui.note(detail);
        note.setFont(Ui.font(0, 11.5f));
        card.add(eyebrow);
        card.add(Box.createVerticalStrut(5));
        card.add(metric);
        card.add(Box.createVerticalStrut(2));
        card.add(note);
        card.putClientProperty("metric", metric);
        return card;
    }

    void refreshBarTab() {
        boolean bl = this.main.barShown();
        this.barButton.setText(bl ? "Ascunde bara" : "\u25ba Arat\u0103 bara");
        this.barButton.setBackground(bl ? new Color(0x303B47) : Ui.TEAL);
        if (this.barStatus != null) this.barStatus.setText(bl ? "<html>Bara este vizibilă.<br>Mut-o ori restrânge-o după preferință.</html>" : "<html>Bara este ascunsă.<br>Deschide-o pentru a lucra peste aplicația ta.</html>");
        if (this.barMetric != null) this.barMetric.setText(bl ? "Vizibilă" : "Ascunsă");
        if (this.savedCount != null) this.savedCount.setText(this.main.app.store.list().size() + " automatizări");
        String string = this.main.barKind();
        for (int i = 0; i < this.modeButtons.size(); i++) {
            JRadioButton jRadioButton = this.modeButtons.get(i);
            boolean selected = jRadioButton.getActionCommand().equals(string);
            jRadioButton.setSelected(selected);
            this.modeCards.get(i).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? Ui.TEAL : Theme.LINE, selected ? 2 : 1),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        }
    }

    private JComponent autoTab() {
        JPanel jPanel = new JPanel(new GridBagLayout());
        jPanel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = 17;
        gridBagConstraints.insets = new Insets(6, 0, 6, 12);
        Store store = this.main.app.store;
        this.interval.setValue(store.getInt("auto.interval", 100));
        this.unit.setSelectedIndex(store.getInt("auto.unit", 0));
        this.button.setSelectedIndex(store.getInt("auto.button", 0));
        this.clickType.setSelectedIndex(store.getInt("auto.double", 0));
        this.px = store.getInt("auto.x", -1);
        this.py = store.getInt("auto.y", -1);
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(this.atMouse);
        buttonGroup.add(this.atPoint);
        (store.getBool("auto.fixed", false) && this.px >= 0 ? this.atPoint : this.atMouse).setSelected(true);
        ButtonGroup buttonGroup2 = new ButtonGroup();
        buttonGroup2.add(this.forever);
        buttonGroup2.add(this.nTimes);
        this.times.setValue(store.getInt("auto.times", 100));
        (store.getBool("auto.limited", false) ? this.nTimes : this.forever).setSelected(true);
        jPanel.add((Component)MainWindow.label("Un click la fiecare"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        jPanel.add((Component)MainWindow.row(this.interval, this.unit), gridBagConstraints);
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        jPanel.add((Component)MainWindow.label("Butonul"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        jPanel.add((Component)MainWindow.row(this.button, this.clickType), gridBagConstraints);
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        jPanel.add((Component)MainWindow.label("Unde"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        JButton jButton = Ui.secondary("Alege punctul");
        jButton.addActionListener(actionEvent -> Picker.pick(this.main.app, Picker.Mode.POINT, this.main.windowsToHide(null), (n, n2, n3, n4, n5, screenFrame) -> {
            this.px = n;
            this.py = n2;
            this.atPoint.setSelected(true);
            this.saveAuto();
            this.refreshAuto();
        }));
        JPanel jPanel2 = new JPanel();
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        JPanel jPanel3 = MainWindow.row(this.atMouse);
        JPanel jPanel4 = MainWindow.row(this.atPoint, this.pointLabel, jButton);
        jPanel3.setAlignmentX(0.0f);
        jPanel4.setAlignmentX(0.0f);
        jPanel2.add(jPanel3);
        jPanel2.add(Box.createVerticalStrut(4));
        jPanel2.add(jPanel4);
        jPanel.add((Component)jPanel2, gridBagConstraints);
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        jPanel.add((Component)MainWindow.label("C\u00e2t timp"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        jPanel.add((Component)MainWindow.row(this.forever, this.nTimes, this.times, new JLabel("clicuri")), gridBagConstraints);
        gridBagConstraints.gridx = 0;
        ++gridBagConstraints.gridy;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new Insets(22, 0, 6, 0);
        this.autoButton.setFont(Ui.font(1, 17.0f));
        this.autoButton.setPreferredSize(new Dimension(320, 52));
        this.autoButton.addActionListener(actionEvent -> this.toggleAuto());
        jPanel.add((Component)this.autoButton, gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.insets = new Insets(4, 0, 4, 0);
        this.autoCount.setFont(Ui.font(0, 14.0f));
        jPanel.add((Component)this.autoCount, gridBagConstraints);
        ++gridBagConstraints.gridy;
        jPanel.add((Component)Ui.note("Porne\u0219te \u0219i opre\u0219te din orice joc cu tasta " + Hotkeys.label(this.main.app.store, 1) + ". \u00centr-un punct ales, se opre\u0219te singur dac\u0103 mi\u0219ti mouse-ul."), gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.weighty = 1.0;
        jPanel.add(Box.createVerticalGlue(), gridBagConstraints);
        for (JComponent jComponent : new JComponent[]{this.interval, this.unit, this.button, this.clickType, this.times}) {
            jComponent.setFont(Ui.font(0, 14.0f));
        }
        ActionListener actionListener = actionEvent -> this.saveAuto();
        this.unit.addActionListener(actionListener);
        this.button.addActionListener(actionListener);
        this.clickType.addActionListener(actionListener);
        this.atMouse.addActionListener(actionListener);
        this.atPoint.addActionListener(actionListener);
        this.forever.addActionListener(actionListener);
        this.nTimes.addActionListener(actionListener);
        this.interval.addChangeListener(changeEvent -> this.saveAuto());
        this.times.addChangeListener(changeEvent -> this.saveAuto());
        return jPanel;
    }

    private static JLabel label(String string) {
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(Ui.font(1, 14.0f));
        jLabel.setForeground(Theme.INK);
        return jLabel;
    }

    private static JPanel row(Component ... componentArray) {
        JPanel jPanel = new JPanel(new FlowLayout(0, 6, 0));
        for (Component component : componentArray) {
            if (component instanceof JComponent) {
                ((JComponent)component).setFont(Ui.font(0, 14.0f));
            }
            jPanel.add(component);
        }
        return jPanel;
    }

    private void saveAuto() {
        Store store = this.main.app.store;
        store.set("auto.interval", this.interval.getValue());
        store.set("auto.unit", this.unit.getSelectedIndex());
        store.set("auto.button", this.button.getSelectedIndex());
        store.set("auto.double", this.clickType.getSelectedIndex());
        store.set("auto.fixed", this.atPoint.isSelected());
        store.set("auto.x", this.px);
        store.set("auto.y", this.py);
        store.set("auto.limited", this.nTimes.isSelected());
        store.set("auto.times", this.times.getValue());
    }

    void toggleAuto() {
        if (this.main.auto.on()) {
            this.main.auto.stop();
            return;
        }
        if (this.main.recorder.on()) {
            return;
        }
        AutoClicker.Settings settings = new AutoClicker.Settings();
        long l = ((Number)this.interval.getValue()).longValue();
        settings.intervalMs = this.unit.getSelectedIndex() == 0 ? l : (this.unit.getSelectedIndex() == 1 ? l * 1000L : l * 60000L);
        settings.button = this.button.getSelectedIndex();
        settings.doubleClick = this.clickType.getSelectedIndex() == 1;
        settings.fixedPoint = this.atPoint.isSelected() && this.px >= 0;
        settings.x = this.px;
        settings.y = this.py;
        settings.times = this.nTimes.isSelected() ? ((Number)this.times.getValue()).longValue() : 0L;
        this.main.auto.start(settings);
    }

    private void refreshAuto() {
        boolean bl = this.main.auto.on();
        String string = Hotkeys.label(this.main.app.store, 1);
        this.autoButton.setText(bl ? "\u25a0 Opre\u0219te (" + string + ")" : "\u25ba Porne\u0219te (" + string + ")");
        this.autoButton.setBackground(bl ? Ui.RED : Ui.TEAL);
        this.pointLabel.setText((String)(this.px >= 0 ? "(" + this.px + ", " + this.py + ")" : "(niciunul)"));
        long l = this.main.auto.clicks();
        this.autoCount.setText((String)(bl ? "Clicuri: " + l : (l > 0L ? "Ultima dat\u0103: " + l + " clicuri" : " ")));
    }

    private JComponent libraryTab() {
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton record = Ui.primary("Înregistrează (" + Hotkeys.label(this.main.app.store, 2) + ")", Ui.RED);
        record.addActionListener(e -> this.main.toggleRecord());
        JButton manual = Ui.secondary("Creează cu pași");
        manual.addActionListener(e -> {
            this.main.bar().work.switchMode("manual");
            this.main.bar().show();
            this.main.bar().openSteps(null);
            this.refreshBarTab();
        });
        JButton importScript = Ui.secondary("Importă de pe Android");
        importScript.addActionListener(e -> this.importCode());
        actions.add(record);
        actions.add(manual);
        actions.add(importScript);
        page.add(actions, BorderLayout.NORTH);

        this.list.setSelectionMode(0);
        this.list.setCellRenderer(new ItemRenderer());
        this.list.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2 && MainWindow.this.list.getSelectedValue() != null) {
                    MainWindow.this.edit(MainWindow.this.list.getSelectedValue());
                }
            }
        });
        this.list.addKeyListener(new KeyAdapter(){

            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == 10 && MainWindow.this.list.getSelectedValue() != null) {
                    MainWindow.this.runSelected();
                }
                if (keyEvent.getKeyCode() == 127 && MainWindow.this.list.getSelectedValue() != null) {
                    MainWindow.this.delete(MainWindow.this.list.getSelectedValue());
                }
            }
        });
        this.list.addListSelectionListener(listSelectionEvent -> {
            this.refreshRun();
            this.refreshAutomationDetail();
        });
        JScrollPane listScroll = new JScrollPane(this.list);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);
        listScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        JPanel listArea = new JPanel(new BorderLayout(0, 10));
        this.listSearch = new JTextField();
        this.listSearch.setFont(Ui.font(0, 13.0f));
        this.listSearch.setToolTipText("Caută după nume");
        this.listSearch.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        this.listSearch.setBackground(new Color(0x202B35));
        this.listSearch.setForeground(Theme.INK);
        this.listSearch.putClientProperty("JTextField.placeholderText", "Caută automatizări...");
        this.listSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshList(); }
            public void removeUpdate(DocumentEvent e) { refreshList(); }
            public void changedUpdate(DocumentEvent e) { refreshList(); }
        });
        listArea.add(this.listSearch, BorderLayout.NORTH);
        this.automationEmpty = Ui.note("Nu ai automatizări salvate încă.<br>Înregistrează o acțiune sau creează una din pași.");
        this.automationEmpty.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        JPanel emptyWrap = new JPanel(new GridBagLayout());
        emptyWrap.setBackground(Theme.LIGHT);
        emptyWrap.add(this.automationEmpty, new GridBagConstraints());
        this.automationListCards = new JPanel(new CardLayout());
        this.automationListCards.add(listScroll, "list");
        this.automationListCards.add(emptyWrap, "empty");
        listArea.add(this.automationListCards, BorderLayout.CENTER);

        this.runButton.addActionListener(actionEvent -> {
            if (this.main.runner.running()) {
                this.main.runner.stop();
            } else {
                this.runSelected();
            }
        });
        this.editSelectedButton = Ui.secondary("Editează pașii");
        this.editSelectedButton.addActionListener(e -> {
            Macro macro = this.list.getSelectedValue();
            if (macro != null) this.edit(macro);
        });
        JButton advanced = Ui.secondary("Mai multe");
        this.advancedAutomationButton = advanced;
        advanced.addActionListener(actionEvent -> {
            Macro macro = this.list.getSelectedValue();
            if (macro != null) {
                Ui.popupUnder(this.advancedMenu(macro), advanced);
            }
        });

        JPanel detail = new JPanel(new BorderLayout(0, 14));
        detail.setBackground(Theme.LIGHT);
        detail.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(20, 20, 18, 20)));
        JPanel detailIntro = new JPanel();
        detailIntro.setLayout(new BoxLayout(detailIntro, BoxLayout.Y_AXIS));
        detailIntro.setOpaque(false);
        JLabel eyebrow = Ui.note("DETALII AUTOMATIZARE");
        eyebrow.setFont(Ui.font(1, 10.5f));
        eyebrow.setForeground(Ui.TEAL);
        this.automationName = Ui.title("Selectează o automatizare");
        this.automationName.setFont(Ui.font(1, 20.0f));
        this.automationType = Ui.note("Alege un element din bibliotecă pentru a vedea acțiunile disponibile.");
        this.automationInfo = Ui.note("");
        detailIntro.add(eyebrow);
        detailIntro.add(Box.createVerticalStrut(8));
        detailIntro.add(this.automationName);
        detailIntro.add(Box.createVerticalStrut(6));
        detailIntro.add(this.automationType);
        detailIntro.add(Box.createVerticalStrut(10));
        detailIntro.add(this.automationInfo);
        detail.add(detailIntro, BorderLayout.NORTH);
        JPanel detailActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        detailActions.setOpaque(false);
        detailActions.add(this.runButton);
        detailActions.add(this.editSelectedButton);
        detailActions.add(advanced);
        JPanel detailBody = new JPanel(new BorderLayout(0, 12));
        detailBody.setOpaque(false);
        detailBody.add(detailActions, BorderLayout.NORTH);
        detailBody.add(Ui.note("Pentru automatizările fără cod, deschide editorul de pași. Poți reordona acțiunile, modifica fiecare pas și reveni cu Undo/Redo."), BorderLayout.CENTER);
        detail.add(detailBody, BorderLayout.CENTER);

        detail.setPreferredSize(new Dimension(390, 100));
        JSplitPane librarySplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listArea, detail);
        librarySplit.setResizeWeight(0.62);
        librarySplit.setBorder(null);
        librarySplit.setDividerSize(8);
        page.add(librarySplit, BorderLayout.CENTER);
        this.refreshAutomationDetail();
        return page;
    }

    private JComponent codeTab() {
        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));
        JPanel intro = new JPanel(new BorderLayout(0, 8));
        intro.add((Component)Ui.title("Editor de cod"), BorderLayout.NORTH);
        intro.add((Component)Ui.note("Creează scripturi cu variabile, condiții și funcții. Alege puncte direct de pe ecran."), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(0, 8, 0));
        JButton create = Ui.primary("+ Script nou", Ui.TEAL);
        create.addActionListener(e -> this.newScript());
        JButton importScript = Ui.secondary("Importă cod");
        importScript.addActionListener(e -> this.importCode());
        actions.add(create);
        actions.add(importScript);
        intro.add(actions, BorderLayout.SOUTH);
        page.add(intro, BorderLayout.NORTH);

        this.scriptList.setSelectionMode(0);
        this.scriptList.setCellRenderer(new ItemRenderer());
        this.scriptList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Macro selected = MainWindow.this.scriptList.getSelectedValue();
                if (e.getClickCount() == 2 && selected != null) MainWindow.this.main.edit(selected);
            }
        });
        this.codeScroll = new JScrollPane(this.scriptList);
        this.codeScroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        this.codeEmpty = new JPanel(new GridBagLayout());
        this.codeEmpty.setBackground(Theme.LIGHT);
        JPanel emptyCard = new JPanel();
        emptyCard.setLayout(new BoxLayout(emptyCard, BoxLayout.Y_AXIS));
        emptyCard.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
        emptyCard.setBackground(Theme.INDIGO);
        emptyCard.setPreferredSize(new Dimension(600, 190));
        JLabel emptyTitle = Ui.title("Codul tău începe aici");
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel emptyNote = Ui.note("<html><div style='text-align:center'>Creează un script ca să deschizi editorul<br>și să lucrezi cu codul.</div></html>");
        emptyNote.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton emptyCreate = Ui.primary("+ Script nou", Ui.TEAL);
        emptyCreate.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyCreate.addActionListener(e -> this.newScript());
        emptyCard.add(emptyTitle);
        emptyCard.add(Box.createVerticalStrut(8));
        emptyCard.add(emptyNote);
        emptyCard.add(Box.createVerticalStrut(18));
        emptyCard.add(emptyCreate);
        this.codeEmpty.add(emptyCard, new GridBagConstraints());
        this.codeCenter = new JPanel(new CardLayout());
        this.codeCenter.add(this.codeScroll, "scripts");
        this.codeCenter.add(this.codeEmpty, "empty");
        page.add(this.codeCenter, BorderLayout.CENTER);
        JLabel hint = Ui.note("Dublu clic pe un script ca să-l deschizi în editor.");
        page.add(hint, BorderLayout.SOUTH);
        return page;
    }

    private void refreshCodeList() {
        Macro selected = this.scriptList.getSelectedValue();
        this.scriptItems.clear();
        for (Macro macro : this.main.app.store.list()) {
            if ("script".equals(macro.kind)) this.scriptItems.addElement(macro);
        }
        if (this.codeEmpty != null) {
            boolean empty = this.scriptItems.isEmpty();
            ((CardLayout)this.codeCenter.getLayout()).show(this.codeCenter, empty ? "empty" : "scripts");
        }
        if (selected != null) {
            for (int i = 0; i < this.scriptItems.size(); i++) {
                if (this.scriptItems.get(i).id.equals(selected.id)) {
                    this.scriptList.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    private void refreshList() {
        Macro macro = this.list.getSelectedValue();
        this.items.clear();
        String filter = this.listSearch == null ? "" : this.listSearch.getText().trim().toLowerCase(Locale.ROOT);
        for (Macro macro2 : this.main.app.store.list()) {
            if (!filter.isEmpty() && !macro2.name.toLowerCase(Locale.ROOT).contains(filter)) continue;
            this.items.addElement(macro2);
        }
        if (macro != null) {
            for (int i = 0; i < this.items.size(); ++i) {
                if (!this.items.get((int)i).id.equals(macro.id)) continue;
                this.list.setSelectedIndex(i);
                break;
            }
        } else if (!this.items.isEmpty()) {
            this.list.setSelectedIndex(0);
        }
        if (this.automationListCards != null) {
            boolean empty = this.items.isEmpty();
            boolean libraryEmpty = this.main.app.store.list().isEmpty();
            this.automationEmpty.setText(libraryEmpty
                    ? "<html><div style='text-align:center'>Nu ai automatizări salvate încă.<br>Înregistrează o acțiune sau creează una din pași.</div></html>"
                    : "<html><div style='text-align:center'>Niciun rezultat pentru căutarea curentă.</div></html>");
            ((CardLayout)this.automationListCards.getLayout()).show(this.automationListCards, empty ? "empty" : "list");
        }
        this.refreshAutomationDetail();
        this.refreshRun();
    }

    private void refreshAutomationDetail() {
        if (this.automationName == null) return;
        Macro macro = this.list.getSelectedValue();
        if (macro == null) {
            this.automationName.setText("Selectează o automatizare");
            this.automationType.setText("Alege un element din bibliotecă pentru a vedea acțiunile disponibile.");
            this.automationInfo.setText("");
            this.editSelectedButton.setText("Editează pașii");
            this.editSelectedButton.setEnabled(false);
            if (this.advancedAutomationButton != null) this.advancedAutomationButton.setEnabled(false);
            return;
        }
        boolean script = "script".equals(macro.kind);
        this.automationName.setText(macro.name);
        this.automationType.setText(script ? "SCRIPT · COD EDITABIL" : "MACRO · EDITOR VIZUAL DE PAȘI");
        int lines = macro.script == null || macro.script.isEmpty() ? 0 : macro.script.split("\\n", -1).length;
        this.automationInfo.setText(script ? lines + " linii de cod · " + macro.actions.size() + " acțiuni conectate"
                : macro.actions.size() + (macro.actions.size() == 1 ? " acțiune" : " acțiuni") + " · editor cu reordonare, duplicare și Undo/Redo");
        this.editSelectedButton.setText(script ? "Deschide codul" : "Editează pașii");
        this.editSelectedButton.setEnabled(true);
        if (this.advancedAutomationButton != null) this.advancedAutomationButton.setEnabled(true);
    }

    void refreshLater() {
        SwingUtilities.invokeLater(this::refreshList);
    }

    void showScripts(Macro macro) {
        this.tabs.setSelectedIndex(TAB_SCRIPTS);
        this.refreshCodeList();
        for (int i = 0; i < this.scriptItems.size(); ++i) {
            if (!this.scriptItems.get(i).id.equals(macro.id)) continue;
            this.scriptList.setSelectedIndex(i);
            this.scriptList.ensureIndexIsVisible(i);
        }
    }

    private void refreshRun() {
        String string = Hotkeys.label(this.main.app.store, 3);
        boolean bl = this.main.runner.running();
        this.runButton.setText(bl ? "\u25a0 Opre\u0219te (" + string + ")" : "\u25ba Ruleaz\u0103 (" + string + ")");
        this.runButton.setBackground(bl ? Ui.RED : Ui.TEAL);
        this.runButton.setEnabled(bl || this.list.getSelectedValue() != null);
    }

    void runSelected() {
        Macro macro = this.list.getSelectedValue();
        if (macro == null) {
            if (!this.items.isEmpty()) {
                this.list.setSelectedIndex(0);
                macro = this.items.get(0);
            } else {
                return;
            }
        }
        if (this.main.runner.run(macro, null) && this.main.app.store.getBool("hideWhileRunning", true)) {
            this.main.hideForRun(this);
        }
    }

    private void edit(Macro macro) {
        if (!"script".equals(macro.kind)) {
            this.main.openStepsOf(macro);
            return;
        }
        this.main.edit(macro);
    }

    private Macro toScript(Macro macro) {
        Macro macro2 = new Macro(this.main.app.store.uniqueName(macro.name + " (script)"));
        macro2.kind = "script";
        macro2.screenWidth = macro.screenWidth;
        macro2.screenHeight = macro.screenHeight;
        macro2.script = CodeFromSteps.code(macro);
        for (Action action : macro.actions) {
            macro2.actions.add(action.copy());
        }
        return macro2;
    }

    private void newScript() {
        String string = JOptionPane.showInputDialog(this, "Numele scriptului:", this.main.app.store.uniqueName("Script"));
        if (string == null || string.trim().isEmpty()) {
            return;
        }
        Macro macro = new Macro(this.main.app.store.uniqueName(string.trim()));
        macro.kind = "script";
        macro.screenWidth = this.main.app.screen.width;
        macro.screenHeight = this.main.app.screen.height;
        macro.script = "#define p point   // scurt\u0103tur\u0103: p(800, 450) \u00een loc de point(800, 450)\n\n// Scriptul t\u0103u. Coordonatele sunt pixelii ecranului (x de la st\u00e2nga, y de sus).\n// \u201e+ Din ecran\u201d alege un punct, o culoare sau o imagine direct de pe ecran. F1 = documenta\u021bia.\n// Pe calculator: tap() = click st\u00e2nga, clickDreapta(), tasta(\"enter\"), scrie(\"text\"), rotita(3).\n\nprint(\"Pornesc!\");\n// tap(p(" + this.main.app.screen.width / 2 + ", " + this.main.app.screen.height / 2 + "));   \u2190 scoate // ca s\u0103 dea click\nwait(1s);\nprint(\"Gata.\");\n";
        this.main.app.store.save(macro);
        this.showScripts(macro);
        this.main.edit(macro);
    }

    private void importCode() {
        String string = "";
        try {
            string = (String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        }
        catch (Exception exception) {
            // empty catch block
        }
        JTextArea jTextArea = new JTextArea(string != null && string.contains("FREECLICK1:") ? string : "", 8, 50);
        jTextArea.setLineWrap(true);
        jTextArea.setFont(new Font("Monospaced", 0, 12));
        int n = JOptionPane.showConfirmDialog(this, new Object[]{"Lipe\u0219te codul FREECLICK1:\u2026 primit (de pe telefon sau de la cineva):", new JScrollPane(jTextArea)}, "Import\u0103 cod", 2, -1);
        if (n != 0) {
            return;
        }
        try {
            Store.ImportResult importResult = this.main.app.store.importText(jTextArea.getText());
            String string2 = "Am ad\u0103ugat " + importResult.items + (importResult.items == 1 ? " element" : " elemente") + (String)(importResult.images > 0 ? " \u0219i " + importResult.images + " poze" : "") + ".";
            boolean bl = false;
            for (Macro macro : importResult.macros) {
                if (macro.screenWidth <= 0 || macro.screenWidth >= macro.screenHeight) continue;
                bl = true;
            }
            if (bl) {
                string2 = string2 + "\nSunt f\u0103cute pe un telefon: coordonatele se scaleaz\u0103 la ecranul t\u0103u, dar verific\u0103 punctele.";
            }
            JOptionPane.showMessageDialog(this, string2);
            if (!importResult.macros.isEmpty()) {
                this.showScripts(importResult.macros.get(0));
            }
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Import\u0103 cod", 2);
        }
    }

    private JPopupMenu advancedMenu(Macro macro) {
        JPopupMenu jPopupMenu = new JPopupMenu();
        jPopupMenu.add(Ui.item("Redenume\u0219te", () -> {
            String string = JOptionPane.showInputDialog(this, "Numele nou:", macro.name);
            if (string != null && !string.trim().isEmpty()) {
                macro.name = string.trim();
                this.main.app.store.save(macro);
            }
        }));
        jPopupMenu.add(Ui.item("Duplic\u0103", () -> this.main.app.store.save(macro.duplicate(this.main.app.store.uniqueName(macro.name + " copie")))));
        jPopupMenu.add(Ui.item("Export\u0103 cod (\u00eel copiez)", () -> {
            String string = this.main.app.store.exportCode(Collections.singletonList(macro));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(string), null);
            JOptionPane.showMessageDialog(this, "Am copiat codul FreeClick (" + string.length() + " caractere). \u00cel lipe\u0219ti pe telefon \u00een \ud83d\udce4 Import\u0103 sau pe alt PC.");
        }));
        JMenu jMenu = Ui.submenu("Avansat \u203a");
        if (!"script".equals(macro.kind)) {
            jMenu.add(Ui.item("Repet\u0103ri \u0219i vitez\u0103\u2026", () -> this.loops(macro)));
            jMenu.add(Ui.item("F\u0103 un script din pa\u0219i", () -> {
                Macro macro2 = this.toScript(macro);
                this.main.app.store.save(macro2);
                this.showScripts(macro2);
                this.main.edit(macro2);
            }));
        }
        jMenu.add(Ui.item("\u0218terge", () -> this.delete(macro)));
        jPopupMenu.add(jMenu);
        return jPopupMenu;
    }

    private void loops(Macro macro) {
        JSpinner jSpinner = new JSpinner(new SpinnerNumberModel(macro.settings.loops, 0, 1000000, 1));
        JSpinner jSpinner2 = new JSpinner(new SpinnerNumberModel(macro.settings.speed, 0.1, 10.0, 0.1));
        int n = JOptionPane.showConfirmDialog(this, new Object[]{"De c\u00e2te ori (0 = la nesf\u00e2r\u0219it):", jSpinner, "Viteza (1 = normal):", jSpinner2}, "Repet\u0103ri \u0219i vitez\u0103", 2);
        if (n != 0) {
            return;
        }
        macro.settings.loops = ((Number)jSpinner.getValue()).intValue();
        macro.settings.speed = Macro.clampSpeed(((Number)jSpinner2.getValue()).doubleValue());
        this.main.app.store.save(macro);
    }

    private void delete(Macro macro) {
        int n = JOptionPane.showConfirmDialog(this, "\u0218terg \u201e" + macro.name + "\u201d?", "\u0218terge", 0, 2);
        if (n != 0) {
            return;
        }
        EditorWindow.closeFor(macro);
        this.main.app.store.delete(macro);
    }

    private JComponent settingsTab() {
        Store store = this.main.app.store;
        JPanel jPanel = new JPanel(new GridBagLayout());
        jPanel.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = 17;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new Insets(5, 0, 5, 12);
        gridBagConstraints.gridwidth = 2;
        jPanel.add((Component)Ui.title("Comenzi rapide"), gridBagConstraints);
        gridBagConstraints.gridwidth = 1;
        for (int i = 1; i <= 3; ++i) {
            ++gridBagConstraints.gridy;
            gridBagConstraints.gridx = 0;
            jPanel.add((Component)MainWindow.label(Hotkeys.NAMES[i]), gridBagConstraints);
            gridBagConstraints.gridx = 1;
            jPanel.add((Component)this.hotkeyField(i), gridBagConstraints);
        }
        ++gridBagConstraints.gridy;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new Insets(18, 0, 5, 0);
        jPanel.add((Component)Ui.title("Comportament"), gridBagConstraints);
        gridBagConstraints.insets = new Insets(3, 0, 3, 0);
        ++gridBagConstraints.gridy;
        jPanel.add((Component)this.check("Pauz\u0103 c\u00e2nd mi\u0219ti mouse-ul (\u00ee\u021bi iei controlul \u00eenapoi)", "pauseOnMouse", true), gridBagConstraints);
        ++gridBagConstraints.gridy;
        jPanel.add((Component)this.check("Ascunde FreeClick c\u00e2t ruleaz\u0103 un script", "hideWhileRunning", true), gridBagConstraints);
        ++gridBagConstraints.gridy;
        jPanel.add((Component)this.check("Închiderea ferestrei minimizează lângă ceas", "closeToTray", false), gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.insets = new Insets(18, 0, 5, 0);
        jPanel.add((Component)Ui.title("Date și stocare"), gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.insets = new Insets(3, 0, 3, 0);
        jPanel.add((Component)Ui.note("Scripturile, pozele \u0219i set\u0103rile stau \u00een " + Overlays.esc(Paths.data().getAbsolutePath()) + ".<br>O versiune nou\u0103 a FreeClick (alt folder sau peste cel vechi) le g\u0103se\u0219te tot acolo."), gridBagConstraints);
        ++gridBagConstraints.gridy;
        JButton jButton = Ui.secondary("Deschide folderul");
        jButton.addActionListener(actionEvent -> {
            try {
                Desktop.getDesktop().open(Paths.data());
            }
            catch (Exception exception) {
                // empty catch block
            }
        });
        jPanel.add((Component)jButton, gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.insets = new Insets(18, 0, 5, 0);
        jPanel.add((Component)Ui.title("Despre FreeClick"), gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.insets = new Insets(3, 0, 3, 0);
        String string = this.main.app.input.isNative() ? (this.main.app.input.hasHooks() ? "mouse \u0219i tastatur\u0103 prin fchook.exe (complet)" : "fchook.exe pornit, dar f\u0103r\u0103 ascultare global\u0103") : "java.awt.Robot (f\u0103r\u0103 scurt\u0103turi globale \u0219i f\u0103r\u0103 \u00eenregistrare)";
        jPanel.add((Component)Ui.note("FreeClick pentru Windows 1.1 \u00b7 ecran " + this.main.app.screen.width + "\u00d7" + this.main.app.screen.height + " (scalare " + Math.round(this.main.app.screen.scale() * 100.0) + "%) \u00b7 " + string), gridBagConstraints);
        ++gridBagConstraints.gridy;
        gridBagConstraints.weighty = 1.0;
        jPanel.add(Box.createVerticalGlue(), gridBagConstraints);
        JScrollPane jScrollPane = new JScrollPane(jPanel);
        jScrollPane.setBorder(null);
        return jScrollPane;
    }

    private JCheckBox check(String string, String string2, boolean bl) {
        JCheckBox jCheckBox = new JCheckBox(string, this.main.app.store.getBool(string2, bl));
        jCheckBox.setFont(Ui.font(0, 14.0f));
        jCheckBox.addActionListener(actionEvent -> this.main.app.store.set(string2, jCheckBox.isSelected()));
        return jCheckBox;
    }

    private JComponent hotkeyField(final int n) {
        final JTextField jTextField = new JTextField(Hotkeys.label(this.main.app.store, n), 14);
        jTextField.setFont(Ui.font(1, 14.0f));
        jTextField.setEditable(false);
        jTextField.setToolTipText("Click aici, apoi apas\u0103 tasta (sau combina\u021bia) dorit\u0103");
        jTextField.addKeyListener(new KeyAdapter(){

            @Override
            public void keyPressed(KeyEvent keyEvent) {
                String string = Hotkeys.fromEvent(keyEvent);
                keyEvent.consume();
                if (string == null || Hotkeys.parse(string) == null) {
                    return;
                }
                for (int i = 1; i <= 3; ++i) {
                    if (i == n || !Hotkeys.combo(MainWindow.this.main.app.store, i).equalsIgnoreCase(string)) continue;
                    JOptionPane.showMessageDialog(MainWindow.this, string.toUpperCase(Locale.ROOT) + " e deja pentru \u201e" + Hotkeys.NAMES[i] + "\u201d.");
                    return;
                }
                MainWindow.this.main.app.store.set("hk." + n, string);
                jTextField.setText(string.toUpperCase(Locale.ROOT));
                Hotkeys.apply(MainWindow.this.main.app);
                MainWindow.this.refreshAuto();
                MainWindow.this.refreshRun();
            }
        });
        return jTextField;
    }

    private static List<String> examples() {
        return Arrays.asList(ScriptExamples.NAMES);
    }

    private static final class ItemRenderer
    extends JPanel
    implements ListCellRenderer<Macro> {
        private final JLabel name = new JLabel();
        private final JLabel info = new JLabel();
        private final JLabel badge = new JLabel();

        ItemRenderer() {
            super(new BorderLayout(10, 0));
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.LINE), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            this.name.setFont(Ui.font(1, 15.0f));
            this.info.setFont(Ui.font(0, 12.0f));
            this.info.setForeground(Ui.GREY);
            this.badge.setFont(Ui.font(1, 11.0f));
            this.badge.setOpaque(true);
            this.badge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            JPanel jPanel = new JPanel(new BorderLayout());
            jPanel.setOpaque(false);
            jPanel.add((Component)this.name, "North");
            jPanel.add((Component)this.info, "South");
            JPanel jPanel2 = new JPanel(new FlowLayout(1, 0, 6));
            jPanel2.setOpaque(false);
            jPanel2.add(this.badge);
            this.add((Component)jPanel2, "West");
            this.add((Component)jPanel, "Center");
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Macro> jList, Macro macro, int n, boolean bl, boolean bl2) {
            boolean bl3 = "script".equals(macro.kind);
            boolean bl4 = bl3 && macro.name.startsWith("\u00cenregistrare");
            boolean bl5 = "record".equals(macro.kind);
            boolean bl6 = "combo".equals(macro.kind);
            this.badge.setText(bl4 || bl5 ? "\u00ceNREG." : (bl3 ? "SCRIPT" : (bl6 ? "COMBO" : "\u021aINTE")));
            this.badge.setBackground(bl4 || bl5 ? new Color(0x4B2630) : (bl3 ? new Color(0x2C344C) : (bl6 ? new Color(0x40351F) : new Color(0x1E3A35))));
            this.badge.setForeground(bl4 || bl5 ? new Color(0xFF9A9E) : (bl3 ? new Color(0xB8C6FF) : (bl6 ? new Color(0xF2C879) : new Color(0x68DCC1))));
            this.name.setForeground(Theme.INK);
            this.name.setText(macro.name);
            String string = new SimpleDateFormat("d MMM yyyy, HH:mm", new Locale("ro")).format(new Date(macro.updatedAt));
            String string2 = bl3 ? ItemRenderer.lines(macro.script) + " linii" : macro.actions.size() + " pa\u0219i";
            this.info.setText(string2 + " \u00b7 " + string + (macro.screenWidth > 0 && macro.screenWidth < macro.screenHeight ? " \u00b7 de pe telefon" : ""));
            this.setBackground(bl ? new Color(0x29403F) : new Color(0x1B242E));
            return this;
        }

        private static int lines(String string) {
            return string == null || string.isEmpty() ? 0 : string.split("\n", -1).length;
        }
    }
}

