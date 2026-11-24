/*
 * Decompiled with CFR 0.152.
 */
package com.freeclick.desktop;

import com.freeclick.core.script.ScriptRunner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.swing.undo.UndoManager;

final class CodeArea
extends JTextPane {
    static final Color KW = new Color(0xC792EA);
    static final Color TYPE = new Color(0x82AAFF);
    static final Color FN = new Color(0x80CBC4);
    static final Color STR = new Color(0xC3E88D);
    static final Color NUM = new Color(0xF78C6C);
    static final Color COMMENT = new Color(0x8290A0);
    static final Color PLAIN = new Color(0xD6DEEB);
    static final Color PRE = new Color(0xA9B8CC);
    private static final Set<String> KEYWORDS = new HashSet<String>();
    private static final Set<String> TYPES = new HashSet<String>();
    final UndoManager undo = new UndoManager();
    private final Timer recolor;
    private Runnable onEdit;
    private Object errorTag;

    CodeArea() {
        Font font = new Font("Consolas", 0, 15);
        if (!"Consolas".equalsIgnoreCase(font.getFamily())) {
            font = new Font("Monospaced", 0, 15);
        }
        this.setFont(font);
        this.setBackground(new Color(0x11171E));
        this.setForeground(PLAIN);
        this.setCaretColor(new Color(0x38D6B0));
        this.setSelectionColor(new Color(0x29403F));
        this.setMargin(new Insets(6, 8, 6, 8));
        FontMetrics fontMetrics = this.getFontMetrics(font);
        int n = fontMetrics.charWidth(' ') * 4;
        TabStop[] tabStopArray = new TabStop[40];
        for (int i = 0; i < tabStopArray.length; ++i) {
            tabStopArray[i] = new TabStop((i + 1) * n);
        }
        SimpleAttributeSet simpleAttributeSet = new SimpleAttributeSet();
        StyleConstants.setTabSet(simpleAttributeSet, new TabSet(tabStopArray));
        this.getStyledDocument().setParagraphAttributes(0, 0, simpleAttributeSet, false);
        this.recolor = new Timer(200, actionEvent -> this.colorize());
        this.recolor.setRepeats(false);
        this.getDocument().addDocumentListener(new DocumentListener(){

            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                CodeArea.this.edited();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                CodeArea.this.edited();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });
        this.getDocument().addUndoableEditListener(undoableEditEvent -> {
            if (!"style change".equals(undoableEditEvent.getEdit().getPresentationName())) {
                this.undo.addEdit(undoableEditEvent.getEdit());
            }
        });
        this.key("ctrl Z", () -> {
            if (this.undo.canUndo()) {
                this.undo.undo();
            }
        });
        this.key("ctrl Y", () -> {
            if (this.undo.canRedo()) {
                this.undo.redo();
            }
        });
        this.key("ctrl shift Z", () -> {
            if (this.undo.canRedo()) {
                this.undo.redo();
            }
        });
        this.key("TAB", () -> this.replaceSelection("    "));
        this.key("ENTER", this::newline);
    }

    void key(String string, final Runnable runnable) {
        this.getInputMap(0).put(KeyStroke.getKeyStroke(string), string);
        this.getActionMap().put(string, new AbstractAction(){

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                runnable.run();
            }
        });
    }

    void onEdit(Runnable runnable) {
        this.onEdit = runnable;
    }

    private void edited() {
        this.recolor.restart();
        if (this.onEdit != null) {
            this.onEdit.run();
        }
    }

    private void newline() {
        try {
            int n = this.getCaretPosition();
            String string = this.getDocument().getText(0, n);
            int n2 = string.lastIndexOf(10) + 1;
            String string2 = string.substring(n2);
            StringBuilder stringBuilder = new StringBuilder();
            for (char c : string2.toCharArray()) {
                if (c != ' ' && c != '\t') break;
                stringBuilder.append(c);
            }
            if (string2.trim().endsWith("{")) {
                stringBuilder.append("    ");
            }
            this.replaceSelection("\n" + String.valueOf(stringBuilder));
        }
        catch (BadLocationException badLocationException) {
            this.replaceSelection("\n");
        }
    }

    void setCode(String string) {
        this.setText(string);
        this.undo.discardAllEdits();
        this.setCaretPosition(0);
        this.colorize();
    }

    String code() {
        try {
            return this.getDocument().getText(0, this.getDocument().getLength());
        }
        catch (BadLocationException badLocationException) {
            return this.getText();
        }
    }

    void markError(int n) {
        if (this.errorTag != null) {
            this.getHighlighter().removeHighlight(this.errorTag);
            this.errorTag = null;
        }
        if (n <= 0) {
            return;
        }
        Element element = this.getDocument().getDefaultRootElement();
        if (n > element.getElementCount()) {
            return;
        }
        Element element2 = element.getElement(n - 1);
        try {
            this.errorTag = this.getHighlighter().addHighlight(element2.getStartOffset(), Math.max(element2.getStartOffset(), element2.getEndOffset() - 1), new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFEE2E2)));
        }
        catch (BadLocationException badLocationException) {
            // empty catch block
        }
    }

    void goToLine(int n) {
        Element element = this.getDocument().getDefaultRootElement();
        if (n < 1 || n > element.getElementCount()) {
            return;
        }
        this.setCaretPosition(element.getElement(n - 1).getStartOffset());
        this.requestFocusInWindow();
    }

    String wordAtCaret() {
        String string = this.code();
        int n = Math.min(this.getCaretPosition(), string.length());
        int n3 = n;
        while (n > 0 && Character.isJavaIdentifierPart(string.charAt(n - 1))) {
            --n;
        }
        while (n3 < string.length() && Character.isJavaIdentifierPart(string.charAt(n3))) {
            ++n3;
        }
        return string.substring(n, n3);
    }

    private void colorize() {
        String string = this.code();
        StyledDocument styledDocument = this.getStyledDocument();
        styledDocument.setCharacterAttributes(0, string.length(), CodeArea.style(PLAIN, false, false), true);
        int n = 0;
        int n2 = string.length();
        while (n < n2) {
            int n3;
            char c = string.charAt(n);
            if (c == '/' && n + 1 < n2 && string.charAt(n + 1) == '/') {
                n3 = string.indexOf(10, n);
                if (n3 < 0) {
                    n3 = n2;
                }
                styledDocument.setCharacterAttributes(n, n3 - n, CodeArea.style(COMMENT, false, true), true);
                n = n3;
                continue;
            }
            if (c == '/' && n + 1 < n2 && string.charAt(n + 1) == '*') {
                n3 = string.indexOf("*/", n + 2);
                n3 = n3 < 0 ? n2 : n3 + 2;
                styledDocument.setCharacterAttributes(n, n3 - n, CodeArea.style(COMMENT, false, true), true);
                n = n3;
                continue;
            }
            if (c == '\"') {
                for (n3 = n + 1; n3 < n2 && string.charAt(n3) != '\"' && string.charAt(n3) != '\n'; ++n3) {
                    if (string.charAt(n3) != '\\') continue;
                    ++n3;
                }
                n3 = Math.min(n2, n3 + 1);
                styledDocument.setCharacterAttributes(n, n3 - n, CodeArea.style(STR, false, false), true);
                n = n3;
                continue;
            }
            if (c == '#' && (n == 0 || string.charAt(n - 1) == '\n' || string.charAt(n - 1) == ' ' || string.charAt(n - 1) == '\t') && n + 1 < n2 && Character.isLetter(string.charAt(n + 1))) {
                for (n3 = n + 1; n3 < n2 && Character.isLetter(string.charAt(n3)); ++n3) {
                }
                styledDocument.setCharacterAttributes(n, n3 - n, CodeArea.style(PRE, true, false), true);
                n = n3;
                continue;
            }
            if (c == '#' || Character.isDigit(c)) {
                for (n3 = n + 1; n3 < n2 && (Character.isLetterOrDigit(string.charAt(n3)) || string.charAt(n3) == '.'); ++n3) {
                }
                styledDocument.setCharacterAttributes(n, n3 - n, CodeArea.style(NUM, false, false), true);
                n = n3;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                for (n3 = n + 1; n3 < n2 && Character.isJavaIdentifierPart(string.charAt(n3)); ++n3) {
                }
                String string2 = string.substring(n, n3);
                AttributeSet attributeSet = null;
                if (KEYWORDS.contains(string2)) {
                    attributeSet = CodeArea.style(KW, true, false);
                } else if (TYPES.contains(string2)) {
                    attributeSet = CodeArea.style(TYPE, true, false);
                } else if (ScriptRunner.functionNames().contains(string2)) {
                    attributeSet = CodeArea.style(FN, false, false);
                }
                if (attributeSet != null) {
                    styledDocument.setCharacterAttributes(n, n3 - n, attributeSet, true);
                }
                n = n3;
                continue;
            }
            ++n;
        }
    }

    private static AttributeSet style(Color color, boolean bl, boolean bl2) {
        SimpleAttributeSet simpleAttributeSet = new SimpleAttributeSet();
        StyleConstants.setForeground(simpleAttributeSet, color);
        StyleConstants.setBold(simpleAttributeSet, bl);
        StyleConstants.setItalic(simpleAttributeSet, bl2);
        return simpleAttributeSet;
    }

    static {
        for (String string : ScriptRunner.KEYWORDS) {
            KEYWORDS.add(string);
        }
        for (String string : ScriptRunner.TYPES) {
            TYPES.add(string);
        }
        for (String string : new String[]{"true", "false", "case", "switch", "default", "cand", "la_fiecare", "la_ora", "pastreaza", "auto", "timeout", "limita", "try", "incearca", "catch", "prinde", "cout", "endl"}) {
            KEYWORDS.add(string);
        }
    }

    static final class LineNumbers
    extends JComponent {
        private final CodeArea area;

        LineNumbers(CodeArea codeArea) {
            this.area = codeArea;
            this.setFont(codeArea.getFont().deriveFont(13.0f));
            this.setForeground(new Color(0x8190A0));
            this.setOpaque(true);
            this.setBackground(new Color(0x171F29));
            codeArea.getDocument().addDocumentListener(new DocumentListener(){

                @Override
                public void insertUpdate(DocumentEvent documentEvent) {
                    CodeArea.LineNumbers.this.revalidate();
                    CodeArea.LineNumbers.this.repaint();
                }

                @Override
                public void removeUpdate(DocumentEvent documentEvent) {
                    CodeArea.LineNumbers.this.revalidate();
                    CodeArea.LineNumbers.this.repaint();
                }

                @Override
                public void changedUpdate(DocumentEvent documentEvent) {
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int n = this.area.getDocument().getDefaultRootElement().getElementCount();
            int n2 = this.getFontMetrics(this.getFont()).stringWidth(String.valueOf(Math.max(99, n))) + 18;
            return new Dimension(n2, this.area.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Rectangle rectangle = graphics.getClipBounds();
            graphics.setColor(new Color(0x171F29));
            graphics.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
            graphics.setColor(this.getForeground());
            graphics.setFont(this.getFont());
            FontMetrics fontMetrics = graphics.getFontMetrics();
            Element element = this.area.getDocument().getDefaultRootElement();
            int n = this.area.viewToModel2D(new Point(0, rectangle.y));
            int n2 = this.area.viewToModel2D(new Point(0, rectangle.y + rectangle.height));
            int n3 = element.getElementIndex(n);
            int n4 = element.getElementIndex(n2);
            for (int i = n3; i <= n4; ++i) {
                try {
                    Rectangle2D rectangle2D = this.area.modelToView2D(element.getElement(i).getStartOffset());
                    if (rectangle2D == null) continue;
                    String string = String.valueOf(i + 1);
                    graphics.drawString(string, this.getWidth() - fontMetrics.stringWidth(string) - 8, (int)(rectangle2D.getY() + rectangle2D.getHeight()) - fontMetrics.getDescent() - 1);
                    continue;
                }
                catch (BadLocationException badLocationException) {
                    // empty catch block
                }
            }
        }
    }
}

