/*
 * Decompiled with CFR 0.152.
 */
package com.freeclick.desktop;

import com.freeclick.core.Action;
import com.freeclick.core.ActionTree;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.ImageTemplate;
import com.freeclick.core.Macro;
import com.freeclick.core.ScreenFrame;
import com.freeclick.core.script.ScriptError;
import com.freeclick.core.script.ScriptExamples;
import com.freeclick.core.script.ScriptRunner;
import com.freeclick.core.script.Signatures;
import com.freeclick.desktop.CodeArea;
import com.freeclick.desktop.ConsolePane;
import com.freeclick.desktop.DesktopHost;
import com.freeclick.desktop.DocsWindow;
import com.freeclick.desktop.Main;
import com.freeclick.desktop.Overlays;
import com.freeclick.desktop.Picker;
import com.freeclick.desktop.Runner;
import com.freeclick.desktop.Ui;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class EditorWindow
extends JFrame
implements Runner.Listener {
    private static final Map<String, EditorWindow> OPEN = new HashMap<String, EditorWindow>();
    private final Main main;
    private final Macro macro;
    private final CodeArea code = new CodeArea();
    private final JLabel hint = new JLabel(" ");
    private final ConsolePane console;
    private final JButton run = Ui.primary("\u25ba Ruleaz\u0103", Ui.TEAL);
    private final JButton stop = Ui.primary("\u25a0 Opre\u0219te", Ui.RED);
    private final Timer check;
    private ScriptError lastError;

    static void open(Main main, Macro macro) {
        EditorWindow editorWindow = OPEN.get(macro.id);
        if (editorWindow == null) {
            editorWindow = new EditorWindow(main, macro);
            OPEN.put(macro.id, editorWindow);
        }
        editorWindow.setVisible(true);
        editorWindow.setState(0);
        editorWindow.toFront();
    }

    static void closeFor(Macro macro) {
        EditorWindow editorWindow = OPEN.remove(macro.id);
        if (editorWindow != null) {
            editorWindow.dispose();
        }
    }

    private EditorWindow(final Main main, Macro macro) {
        super("\u201e" + macro.name + "\u201d \u2014 FreeClick");
        this.main = main;
        this.macro = macro;
        this.setIconImages(Ui.icons());
        this.setDefaultCloseOperation(2);
        this.console = new ConsolePane(main.console);
        this.getContentPane().setBackground(Theme.INDIGO);
        JPanel jPanel = new JPanel(new FlowLayout(0, 10, 10));
        jPanel.setBackground(Theme.INDIGO);
        jPanel.setBorder(BorderFactory.createEmptyBorder(2, 12, 0, 12));
        this.run.addActionListener(actionEvent -> this.runIt());
        this.stop.addActionListener(actionEvent -> main.runner.stop());
        JButton jButton = Ui.secondary("+ Din ecran");
        jButton.addActionListener(actionEvent -> Ui.popupUnder(this.fromScreenMenu(), jButton));
        JButton jButton2 = Ui.secondary("Documenta\u021bie (F1)");
        jButton2.addActionListener(actionEvent -> DocsWindow.show(main, this.code.wordAtCaret()));
        JButton jButton3 = Ui.secondary("Mai mult");
        jButton3.addActionListener(actionEvent -> Ui.popupUnder(this.moreMenu(), jButton3));
        jPanel.add(this.run);
        jPanel.add(this.stop);
        jPanel.add(jButton);
        jPanel.add(jButton2);
        jPanel.add(jButton3);
        this.code.setCode(macro.script == null ? "" : macro.script);
        JScrollPane jScrollPane = new JScrollPane(this.code);
        jScrollPane.setRowHeaderView(new CodeArea.LineNumbers(this.code));
        jScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Ui.LINE));
        jScrollPane.setBackground(Theme.WINDOW_BG);
        jScrollPane.getViewport().setBackground(Theme.WINDOW_BG);
        jScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        this.hint.setFont(Ui.font(0, 13.0f));
        this.hint.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        this.hint.setOpaque(true);
        this.hint.setBackground(Theme.LIGHT);
        this.hint.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (EditorWindow.this.lastError != null) {
                    EditorWindow.this.code.goToLine(EditorWindow.this.lastError.line);
                } else {
                    DocsWindow.show(main, EditorWindow.this.code.wordAtCaret());
                }
            }
        });
        JPanel jPanel2 = new JPanel(new BorderLayout());
        jPanel2.setBackground(Theme.WINDOW_BG);
        jPanel2.add((Component)jScrollPane, "Center");
        jPanel2.add((Component)this.hint, "South");
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBackground(Theme.INDIGO);
        JLabel consoleTitle = new JLabel("CONSOL\u0102   ·   Mesajele scriptului apar aici");
        consoleTitle.setFont(Ui.font(1, 11.0f));
        consoleTitle.setForeground(Theme.GREY);
        consoleTitle.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        consolePanel.add(consoleTitle, BorderLayout.NORTH);
        consolePanel.add(this.console, BorderLayout.CENTER);
        JSplitPane jSplitPane = new JSplitPane(0, jPanel2, consolePanel);
        jSplitPane.setResizeWeight(0.84);
        jSplitPane.setBackground(Theme.INDIGO);
        jSplitPane.setBorder(null);
        jSplitPane.setContinuousLayout(true);
        this.getContentPane().add((Component)jPanel, "North");
        this.getContentPane().add((Component)jSplitPane, "Center");
        this.check = new Timer(600, actionEvent -> this.checkAndSave());
        this.check.setRepeats(false);
        this.code.onEdit(this.check::restart);
        this.code.addCaretListener(caretEvent -> SwingUtilities.invokeLater(this::showHint));
        this.code.key("F1", () -> DocsWindow.show(main, this.code.wordAtCaret()));
        this.code.key("ctrl S", this::checkAndSave);
        this.code.key("F5", this::runIt);
        this.addWindowListener(new WindowAdapter(){

            @Override
            public void windowClosed(WindowEvent windowEvent) {
                EditorWindow.this.checkAndSave();
                EditorWindow.this.console.detach();
                OPEN.remove(EditorWindow.this.macro.id);
                main.runner.removeListenerSafe(EditorWindow.this);
            }
        });
        main.runner.addListener(this);
        this.runChanged();
        this.checkAndSave();
        this.setSize(1180, 820);
        this.setLocationByPlatform(true);
        this.setMinimumSize(new Dimension(900, 620));
    }

    private void checkAndSave() {
        String string = this.code.code();
        if (!string.equals(this.macro.script)) {
            this.macro.script = string;
            this.main.app.store.save(this.macro);
        }
        this.lastError = ScriptRunner.check(string);
        this.code.markError(this.lastError == null ? 0 : this.lastError.line);
        this.showHint();
    }

    private void showHint() {
        if (this.lastError != null) {
            this.hint.setForeground(Ui.RED);
            this.hint.setText("Linia " + this.lastError.line + ": " + this.lastError.describe().replaceFirst("^(Linia|Line) \\d+[:,]?\\s*", "") + "   (click: du-m\u0103 acolo)");
            return;
        }
        Signatures.Hint hint = Signatures.at(this.code.code(), this.code.getCaretPosition());
        this.hint.setForeground(Ui.INDIGO_DARK);
        if (hint == null) {
            this.hint.setText("F\u0103r\u0103 gre\u0219eli. F1 = documenta\u021bia cuv\u00e2ntului de la cursor \u00b7 F5 = ruleaz\u0103 \u00b7 Ctrl+Z = anuleaz\u0103");
            return;
        }
        Object object = hint.text;
        object = hint.paramStart >= 0 && hint.paramEnd > hint.paramStart && hint.paramEnd <= ((String)object).length() ? Overlays.esc(((String)object).substring(0, hint.paramStart)) + "<b>" + Overlays.esc(((String)object).substring(hint.paramStart, hint.paramEnd)) + "</b>" + Overlays.esc(((String)object).substring(hint.paramEnd)) : Overlays.esc((String)object);
        this.hint.setText("<html>" + (String)object + (String)(hint.others > 0 ? "  <span style='color:#6B7280'>(+" + hint.others + " variante)</span>" : "") + "</html>");
    }

    private void runIt() {
        this.checkAndSave();
        if (this.lastError != null) {
            this.code.goToLine(this.lastError.line);
            return;
        }
        if (this.main.runner.run(this.macro, null) && this.main.app.store.getBool("hideWhileRunning", true)) {
            this.main.hideForRun(this);
        }
    }

    @Override
    public void runChanged() {
        boolean bl = this.main.runner.running();
        this.run.setEnabled(!bl);
        this.stop.setEnabled(bl);
    }

    private JPopupMenu fromScreenMenu() {
        JPopupMenu jPopupMenu = new JPopupMenu();
        jPopupMenu.add(Ui.item("Punct (point)", () -> this.pick(Picker.Mode.POINT)));
        jPopupMenu.add(Ui.item("Culoare (#RRGGBB)", () -> this.pick(Picker.Mode.COLOR)));
        jPopupMenu.add(Ui.item("Imagine (gaseste)", () -> this.pick(Picker.Mode.IMAGE)));
        JMenu jMenu = Ui.submenu("Avansat \u203a");
        jMenu.add(Ui.item("Zon\u0103 (rect)", () -> this.pick(Picker.Mode.ZONE)));
        jMenu.add(Ui.item("Pozi\u021bia mouse-ului peste 3 secunde", this::mouseIn3s));
        jPopupMenu.add(jMenu);
        return jPopupMenu;
    }

    private void pick(Picker.Mode mode) {
        Window[] windowArray = this.main.windowsToHide(this);
        Picker.pick(this.main.app, mode, windowArray, (n, n2, n3, n4, n5, screenFrame) -> {
            DesktopHost desktopHost = new DesktopHost(this.main.app, this.macro, null);
            int[] nArray = desktopHost.toScript(n, n2);
            int[] nArray2 = desktopHost.toScript(n3, n4);
            switch (mode) {
                case POINT: {
                    this.insert("point(" + nArray[0] + ", " + nArray[1] + ")");
                    break;
                }
                case COLOR: {
                    this.insert(String.format("#%06X", n5));
                    break;
                }
                case ZONE: {
                    this.insert("rect(" + nArray[0] + ", " + nArray[1] + ", " + nArray2[0] + ", " + nArray2[1] + ")");
                    break;
                }
                default: {
                    this.addImage(screenFrame, n, n2, n3, n4);
                }
            }
        });
    }

    private void mouseIn3s() {
        this.hint.setForeground(Ui.INDIGO_DARK);
        this.hint.setText("Du mouse-ul acolo unde vrei: \u00eel citesc \u00een 3 secunde\u2026");
        Timer timer = new Timer(3000, actionEvent -> {
            int[] nArray = this.main.app.input.pos();
            int[] nArray2 = new DesktopHost(this.main.app, this.macro, null).toScript(nArray[0], nArray[1]);
            this.insert("point(" + nArray2[0] + ", " + nArray2[1] + ")");
            this.toFront();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void addImage(ScreenFrame screenFrame, int n, int n2, int n3, int n4) {
        int n5 = n3 - n;
        int n6 = n4 - n2;
        if (n5 < 16 || n6 < 16) {
            JOptionPane.showMessageDialog(this, "Prea mic\u0103: trage un dreptunghi de cel pu\u021bin 16 px.");
            return;
        }
        if (n5 > 400 || n6 > 400) {
            JOptionPane.showMessageDialog(this, "Prea mare: prinde doar butonul (cel mult 400 px).");
            return;
        }
        ImageTemplate imageTemplate = ImageTemplate.cut(screenFrame, n, n2, n5, n6);
        if (imageTemplate.detail() < 6.0) {
            JOptionPane.showMessageDialog(this, "Zona e aproape de o singur\u0103 culoare. Alege ceva cu text sau un desen.");
            return;
        }
        String string = JOptionPane.showInputDialog(this, "Un nume pentru imagine (\u00eel folose\u0219ti \u00een cod):", "buton" + (this.images() + 1));
        if (string == null || string.trim().isEmpty()) {
            return;
        }
        string = string.trim().replace("\"", "");
        BufferedImage bufferedImage = new BufferedImage(imageTemplate.width, imageTemplate.height, 2);
        bufferedImage.setRGB(0, 0, imageTemplate.width, imageTemplate.height, imageTemplate.argb, 0, imageTemplate.width);
        ImageSpec imageSpec = new ImageSpec();
        imageSpec.id = this.main.app.store.saveImage(bufferedImage);
        if (imageSpec.id == null) {
            return;
        }
        imageSpec.w = imageTemplate.width;
        imageSpec.h = imageTemplate.height;
        imageSpec.capX = n;
        imageSpec.capY = n2;
        imageSpec.capScreenW = screenFrame.width;
        imageSpec.capScreenH = screenFrame.height;
        imageSpec.tapDx = (float)imageTemplate.width / 2.0f;
        imageSpec.tapDy = (float)imageTemplate.height / 2.0f;
        imageSpec.tight = true;
        imageSpec.name = string;
        for (Action action : ActionTree.all(this.macro.actions)) {
            if (action.type != Action.Type.IMAGE || action.img == null || !string.equals(action.img.name)) continue;
            action.img.name = string + "_vechi";
        }
        this.macro.actions.add(Action.image(imageSpec));
        this.main.app.store.save(this.macro);
        this.insert("gaseste(\"" + string + "\")");
    }

    private int images() {
        int n = 0;
        for (Action action : ActionTree.all(this.macro.actions)) {
            if (action.type != Action.Type.IMAGE) continue;
            ++n;
        }
        return n;
    }

    private void insert(String string) {
        this.code.replaceSelection(string);
        this.code.requestFocusInWindow();
        this.checkAndSave();
    }

    private JPopupMenu moreMenu() {
        JPopupMenu jPopupMenu = new JPopupMenu();
        JMenu jMenu = Ui.submenu("Exemple");
        for (int i = 0; i < ScriptExamples.NAMES.length; ++i) {
            int n = i;
            jMenu.add(Ui.item(ScriptExamples.NAMES[i], () -> this.example(n)));
        }
        jPopupMenu.add(jMenu);
        jPopupMenu.add(Ui.item("Export\u0103 cod (\u00eel copiez)", () -> {
            String string = this.main.app.store.exportCode(Collections.singletonList(this.macro));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(string), null);
            JOptionPane.showMessageDialog(this, "Am copiat codul FreeClick. \u00cel po\u021bi lipi oricui: pe telefon se import\u0103 din \ud83d\udce4, pe PC din \u201eImport\u0103 cod\u201d.");
        }));
        jPopupMenu.add(Ui.item("Redenume\u0219te", () -> {
            String string = JOptionPane.showInputDialog(this, "Numele nou:", this.macro.name);
            if (string != null && !string.trim().isEmpty()) {
                this.macro.name = string.trim();
                this.main.app.store.save(this.macro);
                this.setTitle("\u201e" + this.macro.name + "\u201d \u2014 FreeClick");
            }
        }));
        JMenu jMenu2 = Ui.submenu("Avansat \u203a");
        jMenu2.add(Ui.item("Gole\u0219te consola", this.main.console::clear));
        jMenu2.add(Ui.item("Adaug\u0103 set\u0103rile clickului sus \u00een cod", () -> {
            if (ScriptExamples.hasSettingsHeader(this.code.code())) {
                return;
            }
            this.code.setCaretPosition(0);
            this.code.replaceSelection(ScriptExamples.settingsHeader(50L, 300L, 100L, !this.code.code().contains("#define p")));
            this.checkAndSave();
        }));
        jPopupMenu.add(jMenu2);
        return jPopupMenu;
    }

    private void example(int n) {
        Object[] objectArray = new Object[]{"\u00cenlocuie\u0219te", "Adaug\u0103 la final", "Renun\u021b\u0103"};
        int n2 = JOptionPane.showOptionDialog(this, "\u201e" + ScriptExamples.NAMES[n] + "\u201d: \u00eel pun \u00een locul codului t\u0103u sau la final?", "Exemplu", -1, 3, null, objectArray, objectArray[0]);
        if (n2 == 0) {
            this.code.setCode(ScriptExamples.CODE[n]);
        } else if (n2 == 1) {
            this.code.setCaretPosition(this.code.getDocument().getLength());
            this.code.replaceSelection("\n" + ScriptExamples.CODE[n]);
        }
        this.checkAndSave();
    }
}

