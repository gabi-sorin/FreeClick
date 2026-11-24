/*
 * Decompiled with CFR 0.152.
 */
package com.freeclick.desktop;

import com.freeclick.desktop.ConsoleModel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

final class ConsolePane
extends JScrollPane
implements ConsoleModel.View {
    private final JTextPane text = new JTextPane();
    private final ConsoleModel model;

    ConsolePane(ConsoleModel consoleModel) {
        this.model = consoleModel;
        this.text.setEditable(false);
        Font font = new Font("Consolas", 0, 13);
        if (!"Consolas".equalsIgnoreCase(font.getFamily())) {
            font = new Font("Monospaced", 0, 13);
        }
        this.text.setFont(font);
        this.text.setBackground(new Color(0x11171E));
        this.text.setForeground(new Color(0xD6DEEB));
        this.text.setMargin(new Insets(6, 8, 6, 8));
        this.setViewportView(this.text);
        this.setBackground(new Color(0x11171E));
        this.setBorder(BorderFactory.createLineBorder(new Color(0x303A46)));
        consoleModel.addView(this);
    }

    void detach() {
        this.model.removeView(this);
    }

    @Override
    public void appended(String string, String string2, int n) {
        StyledDocument styledDocument = this.text.getStyledDocument();
        SimpleAttributeSet simpleAttributeSet = new SimpleAttributeSet();
        StyleConstants.setForeground(simpleAttributeSet, new Color(0x8290A0));
        SimpleAttributeSet simpleAttributeSet2 = new SimpleAttributeSet();
        StyleConstants.setForeground(simpleAttributeSet2, n == 2 ? new Color(0xFF8B8B) : (n == 1 ? new Color(0xF2C879) : new Color(0xD6DEEB)));
        try {
            styledDocument.insertString(styledDocument.getLength(), string + "  ", simpleAttributeSet);
            styledDocument.insertString(styledDocument.getLength(), string2 + "\n", simpleAttributeSet2);
            if (styledDocument.getLength() > 200000) {
                styledDocument.remove(0, 50000);
            }
        }
        catch (BadLocationException badLocationException) {
            // empty catch block
        }
        this.text.setCaretPosition(styledDocument.getLength());
    }

    @Override
    public void cleared() {
        this.text.setText("");
    }
}

