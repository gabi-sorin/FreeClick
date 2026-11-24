package com.freeclick.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Test helper: draws text with the JDK (AWT, headless) the way the app draws reference letters on the phone. */
final class AwtText {
    private AwtText() {}

    static Font font(String family, int style, int px) {
        Font f = new Font(family, style, px);
        if (!f.getFamily().equalsIgnoreCase(family) && !f.getFontName().toLowerCase().startsWith(family.toLowerCase())) {
            throw new AssertionError("font not installed: " + family + " (got " + f.getFamily() + ")");
        }
        return f;
    }

    static Graphics2D g(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    static int[] argb(BufferedImage im) {
        return im.getRGB(0, 0, im.getWidth(), im.getHeight(), null, 0, im.getWidth());
    }

    /** Adds every character of `chars` in this font to the book, as the app does with the phone's fonts. */
    static int addFont(FontBook book, Font f, String chars) {
        int px = f.getSize();
        int w = px * 3, h = px * 3, base = px * 2;
        // capital height measured on the drawn "H" (same thresholding as the letters themselves)
        int[] H = draw(f, "H", w, h, base);
        int top = h, bottom = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((H[y * w + x] & 255) < 128) { top = Math.min(top, y); bottom = Math.max(bottom, y + 1); }
        int capH = bottom - top;
        int n = 0;
        for (int i = 0; i < chars.length(); i++) {
            int[] px2 = draw(f, String.valueOf(chars.charAt(i)), w, h, base);
            if (book.addReference(chars.charAt(i), px2, w, h, bottom, capH)) n++;
        }
        return n;
    }

    static int[] draw(Font f, String s, int w, int h, int base) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(im);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(f);
        g.drawString(s, f.getSize() / 2, base);
        g.dispose();
        return argb(im);
    }

    /** A scene: `text` in colour fg on bg (optionally a vertical gradient), with the text box returned in box. */
    static final class Scene {
        int[] argb; int w, h; int x0, y0, x1, y1;
    }

    static Scene scene(String text, Font f, int fg, int bg, int bg2, int w, int h, int tx, int ty, long noiseSeed) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(im);
        if (bg2 == bg) {
            g.setColor(new Color(bg));
            g.fillRect(0, 0, w, h);
        } else {
            g.setPaint(new java.awt.GradientPaint(0, 0, new Color(bg), 0, h, new Color(bg2)));
            g.fillRect(0, 0, w, h);
        }
        g.setColor(new Color(fg));
        g.setFont(f);
        g.drawString(text, tx, ty);
        FontMetrics fm = g.getFontMetrics();
        java.awt.Rectangle r = f.createGlyphVector(g.getFontRenderContext(), text).getPixelBounds(g.getFontRenderContext(), tx, ty);
        g.dispose();
        Scene s = new Scene();
        s.argb = argb(im);
        s.w = w; s.h = h;
        s.x0 = r.x; s.y0 = r.y; s.x1 = r.x + r.width; s.y1 = r.y + r.height;
        if (fm == null) throw new AssertionError();
        if (noiseSeed != 0) {
            java.util.Random rnd = new java.util.Random(noiseSeed);
            for (int i = 0; i < s.argb.length; i++) {
                int c = s.argb[i];
                int rr = clamp(((c >> 16) & 255) + rnd.nextInt(9) - 4), gg = clamp(((c >> 8) & 255) + rnd.nextInt(9) - 4), bb = clamp((c & 255) + rnd.nextInt(9) - 4);
                s.argb[i] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
            }
        }
        return s;
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /**
     * Letters like the ones the app draws from the phone's fonts: Roboto (Android's sans-serif, regular + bold),
     * a serif, a monospace and a condensed sans. The tests read text in other fonts to see how it copes.
     */
    static FontBook referenceBook() {
        FontBook b = new FontBook();
        String all = TextReader.TEXT_CHARS + "kKMB";
        String[][] fonts = {{"Roboto", "0"}, {"Roboto", "1"}, {"DejaVu Serif", "0"}, {"DejaVu Sans Mono", "0"},
                {"DejaVu Sans Condensed", "0"}, {"Liberation Sans", "0"}};
        for (String[] fd : fonts) {
            for (int px : new int[]{18, 44}) addFont(b, font(fd[0], Integer.parseInt(fd[1]), px), all);
        }
        return b;
    }

    static FontBook cachedBook;
    static synchronized FontBook book() {
        if (cachedBook == null) cachedBook = referenceBook();
        return cachedBook;
    }
}
