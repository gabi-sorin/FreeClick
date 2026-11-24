/*
 * Decompiled with CFR 0.152.
 */
package com.freeclick.desktop;

import com.freeclick.desktop.Overlays;
import com.freeclick.desktop.Theme;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.plaf.basic.BasicButtonUI;

final class Ui {
    static final Color INDIGO = Theme.INDIGO;
    static final Color INDIGO_DARK = Theme.INDIGO_DARK;
    static final Color TEAL = Theme.TEAL;
    static final Color RED = Theme.RED;
    static final Color GREY = Theme.GREY;
    static final Color LIGHT = Theme.LIGHT;
    static final Color LINE = Theme.LINE;

    private Ui() {
    }

    static Font font(int n, float f) {
        return Overlays.font(n, Math.max(10.0f, f * 1.12f));
    }

    static JLabel title(String string) {
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(Ui.font(1, 17.0f));
        jLabel.setForeground(INDIGO_DARK);
        return jLabel;
    }

    static JLabel note(String string) {
        JLabel jLabel = new JLabel("<html>" + string + "</html>");
        jLabel.setFont(Ui.font(0, 13.0f));
        jLabel.setForeground(GREY);
        return jLabel;
    }

    static JButton primary(String string, Color color) {
        JButton jButton = new JButton(string);
        Ui.flat(jButton, color, Color.WHITE);
        jButton.setFont(Ui.font(1, 14.0f));
        return jButton;
    }

    static JButton secondary(String string) {
        JButton jButton = new JButton(string);
        Ui.flat(jButton, new Color(0x28323D), Color.WHITE);
        jButton.setFont(Ui.font(1, 13.0f));
        return jButton;
    }

    static void flat(AbstractButton abstractButton, Color color, Color color2) {
        abstractButton.setUI(new BasicButtonUI(){

            @Override
            public void paint(Graphics graphics, JComponent jComponent) {
                Graphics2D graphics2D = (Graphics2D)graphics.create();
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = jComponent.getBackground();
                if (!jComponent.isEnabled()) {
                    color = new Color(0x303B47);
                } else if (((AbstractButton)jComponent).getModel().isPressed()) {
                    color = color.darker();
                } else if (((AbstractButton)jComponent).getModel().isRollover()) {
                    color = Ui.blend(color, Color.WHITE, 0.12);
                }
                graphics2D.setColor(color);
                graphics2D.fillRoundRect(0, 0, jComponent.getWidth(), jComponent.getHeight(), 10, 10);
                graphics2D.dispose();
                super.paint(graphics, jComponent);
            }

            @Override
            protected void paintText(Graphics graphics, AbstractButton abstractButton, Rectangle rectangle, String string) {
                FontMetrics fontMetrics = graphics.getFontMetrics();
                graphics.setColor(abstractButton.isEnabled() ? abstractButton.getForeground() : new Color(0x8694A3));
                ((Graphics2D)graphics).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.drawString(string, rectangle.x, rectangle.y + fontMetrics.getAscent());
            }
        });
        abstractButton.setBackground(color);
        abstractButton.setForeground(color2);
        abstractButton.setOpaque(false);
        abstractButton.setContentAreaFilled(false);
        abstractButton.setBorderPainted(false);
        abstractButton.setFocusPainted(false);
        abstractButton.setRolloverEnabled(true);
        abstractButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        abstractButton.setCursor(Cursor.getPredefinedCursor(12));
    }

    static Color blend(Color color, Color color2, double d) {
        return new Color((int)((double)color.getRed() * (1.0 - d) + (double)color2.getRed() * d), (int)((double)color.getGreen() * (1.0 - d) + (double)color2.getGreen() * d), (int)((double)color.getBlue() * (1.0 - d) + (double)color2.getBlue() * d));
    }

    static JMenuItem item(String string, Runnable runnable) {
        JMenuItem jMenuItem = new JMenuItem(string);
        jMenuItem.setFont(Ui.font(0, 14.0f));
        jMenuItem.setBackground(Theme.LIGHT);
        jMenuItem.setForeground(Theme.INK);
        jMenuItem.setOpaque(true);
        jMenuItem.addActionListener(actionEvent -> runnable.run());
        return jMenuItem;
    }

    static JMenu submenu(String string) {
        JMenu jMenu = new JMenu(string);
        jMenu.setFont(Ui.font(0, 14.0f));
        jMenu.setBackground(Theme.LIGHT);
        jMenu.setForeground(Theme.INK);
        jMenu.setOpaque(true);
        return jMenu;
    }

    static void popupUnder(JPopupMenu jPopupMenu, JComponent jComponent) {
        jPopupMenu.show(jComponent, 0, jComponent.getHeight());
    }

    static List<Image> icons() {
        ArrayList<Image> arrayList = new ArrayList<Image>();
        for (int n : new int[]{16, 32, 64, 128}) {
            URL uRL = Ui.class.getResource("/res/icon" + n + ".png");
            if (uRL == null) continue;
            arrayList.add(Toolkit.getDefaultToolkit().getImage(uRL));
        }
        return arrayList;
    }
}

