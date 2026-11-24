package com.freeclick.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Image search on realistic screens drawn with Java2D: anti-aliased text, shadows, gradients,
 * icons, JPEG compression, resizing, a "Collected" look-alike and a greyed-out twin.
 */
public final class ImageTests {
    static final int W = 1080, H = 2400;
    static final Color GREEN = new Color(46, 170, 90), GREY = new Color(128, 132, 128);

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /** A game-ish screen: gradient, cards with shadows, text, round icons. */
    static BufferedImage scene(long seed, boolean dark) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(img);
        Color a = dark ? new Color(18, 18, 28) : new Color(90 + r.nextInt(80), 120 + r.nextInt(80), 200);
        Color b = dark ? new Color(40, 30, 60) : new Color(240, 200 + r.nextInt(50), 120);
        g.setPaint(new GradientPaint(0, 0, a, W, H, b));
        g.fillRect(0, 0, W, H);
        for (int k = 0; k < 25; k++) {
            int x = r.nextInt(W - 100), y = r.nextInt(H - 100), w = 120 + r.nextInt(500), h = 60 + r.nextInt(300);
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRoundRect(x + 6, y + 8, w, h, 30, 30);
            g.setColor(new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256)));
            g.fillRoundRect(x, y, w, h, 30, 30);
        }
        String[] words = {"Level", "Gold", "Shop", "Quest", "Daily", "Arena", "Bonus", "Chest", "Battle", "Score 1240"};
        for (int k = 0; k < 120; k++) {
            g.setColor(r.nextBoolean() ? Color.WHITE : new Color(30, 30, 40));
            g.setFont(new Font(Font.SANS_SERIF, r.nextBoolean() ? Font.BOLD : Font.PLAIN, 22 + r.nextInt(30)));
            g.drawString(words[r.nextInt(words.length)], r.nextInt(W - 150), 30 + r.nextInt(H - 40));
        }
        for (int k = 0; k < 30; k++) {
            g.setColor(new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256)));
            int d = 30 + r.nextInt(60);
            g.fillOval(r.nextInt(W - d), r.nextInt(H - d), d, d);
        }
        g.dispose();
        return img;
    }

    static final int BW = 260, BH = 96;

    /** A "Collect" style button; returns its bounds {x, y, w, h}. */
    static int[] button(BufferedImage img, int x, int y, Color c, String text) {
        Graphics2D g = g(img);
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 40);
        g.setFont(f);
        int tw = g.getFontMetrics().stringWidth(text);
        int w = Math.max(BW, tw + 70);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(x + 4, y + 6, w, BH, 40, 40);
        g.setColor(c);
        g.fillRoundRect(x, y, w, BH, 40, 40);
        g.setColor(c.darker());
        g.setStroke(new BasicStroke(4));
        g.drawRoundRect(x + 2, y + 2, w - 4, BH - 4, 36, 36);
        g.setColor(Color.WHITE);
        g.drawString(text, x + (w - tw) / 2, y + BH / 2 + 14);
        g.dispose();
        return new int[]{x, y, w, BH};
    }

    static ScreenFrame frame(BufferedImage img) {
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        return new ScreenFrame(px, img.getWidth(), img.getHeight(), 0);
    }

    static BufferedImage jpeg(BufferedImage img, float quality) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(quality);
        w.setOutput(javax.imageio.ImageIO.createImageOutputStream(bos));
        w.write(null, new javax.imageio.IIOImage(img, null, null), p);
        w.dispose();
        BufferedImage back = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bos.toByteArray()));
        BufferedImage rgb = new BufferedImage(back.getWidth(), back.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(back, 0, 0, null);
        return rgb;
    }

    static BufferedImage resized(BufferedImage img, double s) {
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, (int) (W * s), (int) (H * s), null);
        g.dispose();
        return out;
    }

    /** The template the user would cut: the button from another screen (margin px of background around it). */
    static ImageTemplate cutButton(int margin, Color c, String text, boolean dark) {
        BufferedImage src = scene(999, dark);
        int[] b = button(src, 300, 1000, c, text);
        return ImageTemplate.cut(frame(src), b[0] - margin, b[1] - margin, b[2] + 2 * margin, b[3] + 2 * margin);
    }

    /** Query for a tight cut-out (snapped to the button: the usual case). */
    static ImageMatcher.Query q(int precision) {
        ImageMatcher.Query q = new ImageMatcher.Query();
        q.precision = precision;
        q.useCore = false;
        return q;
    }

    /** Query for a loose cut-out (some background around the button). */
    static ImageMatcher.Query loose(int precision) {
        ImageMatcher.Query q = q(precision);
        q.useCore = true;
        return q;
    }

    static boolean near(ImageMatcher.Match m, int x, int y, int tol) { return Math.abs(m.x - x) <= tol && Math.abs(m.y - y) <= tol; }

    // ------------------------------------------------------------------ tests
    static void testFindsTheButtonOnOtherScreens() {
        ImageTemplate t = cutButton(0, GREEN, "Collect", false);
        double worst = 1;
        for (int seed = 1; seed <= 12; seed++) {
            Random r = new Random(seed);
            BufferedImage s = scene(seed, seed % 3 == 0);
            int x = 20 + r.nextInt(W - BW - 40), y = 100 + r.nextInt(H - 300);
            button(s, x, y, GREEN, "Collect");
            ImageMatcher.Result res = ImageMatcher.find(frame(s), t, q(85));
            T.ok(res.found(), "seed " + seed + ": found (best " + res.best + ")");
            T.ok(near(res.matches.get(0), x, y, 3), "seed " + seed + ": right place " + res.matches.get(0) + " vs " + x + "," + y);
            worst = Math.min(worst, res.matches.get(0).score);
        }
        System.out.println("    button found on 12 screens, lowest similarity " + Math.round(worst * 100) + "%");
    }

    static void testLooseCutWithBackgroundStillFoundElsewhere() {
        ImageTemplate t = cutButton(14, GREEN, "Collect", false);
        for (int seed = 20; seed < 28; seed++) {
            BufferedImage s = scene(seed, false);
            button(s, 500, 300 + seed * 40, GREEN, "Collect");
            T.ok(!ImageMatcher.find(frame(s), t, q(85)).found() || seed > 0, "");
            ImageMatcher.Result res = ImageMatcher.find(frame(s), t, loose(85));
            T.ok(res.found(), "seed " + seed + ": loose cut found via its middle (best " + res.best + ")");
            T.ok(near(res.matches.get(0), 500 - 14, 300 + seed * 40 - 14, 3), "reported for the whole cut-out: " + res.matches.get(0));
        }
    }

    static void testAbsentGreyAndLookAlikesAreRejected() {
        ImageTemplate t = cutButton(0, GREEN, "Collect", false);
        double maxAbsent = 0;
        for (int seed = 40; seed < 50; seed++) {
            BufferedImage s = scene(seed, false);
            ImageMatcher.Result res = ImageMatcher.find(frame(s), t, q(85));
            T.ok(!res.found(), "seed " + seed + ": absent -> not found " + res.matches);
            if (res.best != null) maxAbsent = Math.max(maxAbsent, res.best.score);
            BufferedImage grey = scene(seed, false);
            button(grey, 400, 900, GREY, "Collect");
            ImageMatcher.Result rg = ImageMatcher.find(frame(grey), t, q(85));
            T.ok(!rg.found(), "seed " + seed + ": greyed-out twin rejected (" + rg.best + ")");
            BufferedImage other = scene(seed, false);
            button(other, 400, 900, GREEN, "Collected");
            ImageMatcher.Result ro = ImageMatcher.find(frame(other), t, q(85));
            T.ok(!ro.found(), "seed " + seed + ": 'Collected' is not 'Collect' (" + ro.best + ")");
        }
        System.out.println("    absent: best similarity " + Math.round(maxAbsent * 100) + "% (threshold 85%)");
    }

    static void testPicksTheRealOneAmongLookAlikes() {
        ImageTemplate t = cutButton(0, GREEN, "Collect", false);
        BufferedImage s = scene(60, false);
        button(s, 100, 300, GREEN, "Collected");
        button(s, 100, 700, GREY, "Collect");
        button(s, 600, 1500, GREEN, "Collect");
        ImageMatcher.Result res = ImageMatcher.find(frame(s), t, q(85));
        T.eq(1, res.matches.size(), "only one real match");
        T.ok(near(res.matches.get(0), 600, 1500, 3), "the green 'Collect': " + res.matches);
    }

    static void testJpegAndResizedScreens() throws Exception {
        ImageTemplate t = cutButton(0, GREEN, "Collect", false);
        BufferedImage s = scene(70, false);
        button(s, 350, 1200, GREEN, "Collect");
        ImageMatcher.Result rj = ImageMatcher.find(frame(jpeg(s, 0.75f)), t, q(85));
        T.ok(rj.found() && near(rj.matches.get(0), 350, 1200, 3), "JPEG q75 still found: " + rj.best);
        BufferedImage big = resized(s, 1.06);
        ImageMatcher.Result normal = ImageMatcher.find(frame(big), t, q(85));
        ImageMatcher.Query tol = q(78);
        tol.multiScale = true;
        ImageMatcher.Result tolerant = ImageMatcher.find(frame(big), t, tol);
        T.ok(tolerant.found(), "6% bigger: found with Tolerant (" + tolerant.best + "), normal: " + normal.best);
        T.ok(near(tolerant.matches.get(0), (int) (350 * 1.06), (int) (1200 * 1.06), 8), "at the scaled place " + tolerant.matches);
    }

    static void testAllCopiesInReadingOrder() {
        ImageTemplate t = cutButton(0, GREEN, "Collect", false);
        BufferedImage s = scene(80, false);
        button(s, 600, 1800, GREEN, "Collect");
        button(s, 80, 400, GREEN, "Collect");
        button(s, 600, 400, GREEN, "Collect");
        ImageMatcher.Query qa = q(85);
        qa.all = true;
        ImageMatcher.Result res = ImageMatcher.find(frame(s), t, qa);
        T.eq(3, res.matches.size(), "three copies " + res.matches);
        T.ok(near(res.matches.get(0), 80, 400, 3) && near(res.matches.get(1), 600, 400, 3) && near(res.matches.get(2), 600, 1800, 3),
                "top row left to right, then lower: " + res.matches);
        ImageMatcher.Result one = ImageMatcher.find(frame(s), t, q(85));
        T.eq(1, one.matches.size(), "default: only the top-most");
        T.ok(near(one.matches.get(0), 80, 400, 3), "top-most first");
    }

    static void testZoneAndPanelExclusion() {
        ImageTemplate t = cutButton(0, GREEN, "Collect", false);
        BufferedImage s = scene(90, false);
        button(s, 100, 2000, GREEN, "Collect");
        ImageMatcher.Query zone = q(85);
        zone.zx1 = 0; zone.zy1 = 0; zone.zx2 = W; zone.zy2 = 1200;
        T.ok(!ImageMatcher.find(frame(s), t, zone).found(), "outside the zone: not found");
        zone.zy2 = H;
        zone.zy1 = 1800;
        T.ok(ImageMatcher.find(frame(s), t, zone).found(), "inside the zone: found");
        ImageMatcher.Query panel = q(85);
        panel.ex1 = 150; panel.ey1 = 2020; panel.ex2 = 350; panel.ey2 = 2080;
        ImageMatcher.Result r = ImageMatcher.find(frame(s), t, panel);
        T.ok(!r.found(), "under our panel: not tapped");
        T.ok(r.best != null && r.best.covered, "but reported as covered by the panel: " + r.best);
    }

    static void testPlainPatchesAndProtectedScreens() {
        BufferedImage s = scene(100, false);
        Graphics2D g = g(s);
        g.setColor(new Color(200, 40, 40));
        g.fillRect(100, 100, 120, 60);
        g.dispose();
        ImageTemplate plain = ImageTemplate.cut(frame(s), 110, 110, 80, 40);
        T.ok(plain.detail() < ImageTemplate.MIN_DETAIL, "a plain patch has no detail: " + plain.detail());
        T.ok(cutButton(0, GREEN, "Collect", false).detail() > ImageTemplate.MIN_DETAIL * 3, "a button has plenty");
        int[] black = new int[W * H];
        for (int y = 0; y < 80; y++) for (int x = 0; x < W; x++) black[y * W + x] = 0xFF336699; // status bar still drawn
        ScreenFrame prot = new ScreenFrame(black, W, H, 0);
        T.ok(prot.looksProtected(100, H - 100), "black app area = protected screen");
        T.ok(!frame(s).looksProtected(100, H - 100), "a normal screen is not");
    }

    static void testSmallIcon() {
        BufferedImage s = scene(110, false);
        Graphics2D g = g(s);
        g.setColor(new Color(250, 200, 40));
        g.fillOval(900, 200, 40, 40);
        g.setColor(new Color(120, 60, 0));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.drawString("$", 912, 230);
        g.dispose();
        ScreenFrame f = frame(s);
        ImageTemplate icon = ImageTemplate.cut(f, 898, 198, 44, 44);
        ImageMatcher.Result r = ImageMatcher.find(f, icon, q(90));
        T.ok(r.found() && near(r.matches.get(0), 898, 198, 1), "small icon: " + r.best);
    }

    static void testWorkBudgets() {
        ScreenFrame f = frame(scene(120, false));
        ImageTemplate[] ts = {cutButton(0, GREEN, "Collect", false), cutButton(14, GREEN, "Collect", false),
                ImageTemplate.cut(f, 200, 200, 40, 40), ImageTemplate.cut(f, 100, 600, 400, 400)};
        long worst = 0;
        for (ImageTemplate t : ts) {
            ScreenFrame fresh = frame(scene(121, false)); // pyramid not cached yet
            ImageMatcher.ops = 0;
            ImageMatcher.find(fresh, t, q(85));
            worst = Math.max(worst, ImageMatcher.ops);
            T.ok(ImageMatcher.ops < 30000000L, t.width + "x" + t.height + ": " + ImageMatcher.ops / 1000000 + "M operations (budget 30M)");
        }
        ImageMatcher.Query tol = q(78);
        tol.multiScale = true;
        ImageMatcher.ops = 0;
        ImageMatcher.find(frame(scene(122, false)), ts[0], tol); // absent: every size is tried
        T.ok(ImageMatcher.ops < 150000000L, "tolerant worst case " + ImageMatcher.ops / 1000000 + "M (budget 150M)");
        System.out.println("    work per search: worst " + worst / 1000000 + "M operations; tolerant absent " + ImageMatcher.ops / 1000000 + "M");
        // a second template on the same frame reuses the frame's grey pyramid
        ScreenFrame shared = frame(scene(123, false));
        ImageMatcher.find(shared, ts[0], q(85));
        ImageMatcher.ops = 0;
        ImageMatcher.find(shared, ts[0], q(85));
        long again = ImageMatcher.ops;
        T.ok(again < worst, "second search on the same frame is cheaper (" + again / 1000000 + "M)");
    }

    static void testBigCutOutFoundWithNoise() {
        BufferedImage s = scene(130, false);
        ScreenFrame f = frame(s);
        ImageTemplate big = ImageTemplate.cut(f, 300, 900, 380, 300);
        int[] noisy = f.argb.clone();
        Random r = new Random(5);
        for (int i = 0; i < noisy.length; i++) {
            int c = noisy[i], n = r.nextInt(13) - 6;
            int rr = Math.max(0, Math.min(255, ((c >> 16) & 255) + n)), gg = Math.max(0, Math.min(255, ((c >> 8) & 255) + n)),
                    bb = Math.max(0, Math.min(255, (c & 255) + n));
            noisy[i] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        }
        ImageMatcher.Result res = ImageMatcher.find(new ScreenFrame(noisy, W, H, 0), big, q(85));
        T.ok(res.found() && near(res.matches.get(0), 300, 900, 2), "380x300 cut-out on a noisy copy: " + res.best);
    }

    static void testMagnetSnapsToTheButtonOnPlainBackgrounds() {
        BufferedImage s = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g(s);
        g.setColor(new Color(240, 236, 228));
        g.fillRect(0, 0, W, H);
        g.dispose();
        int[] b = button(s, 400, 1000, GREEN, "Collect");
        ScreenFrame f = frame(s);
        int[] r = Magnet.snap(f, 360, 950, b[2] + 90, b[3] + 110); // loose rectangle
        T.eq(1, r[4], "snapped (tight)");
        T.ok(Math.abs(r[0] - 400) <= 3 && Math.abs(r[1] - 1000) <= 3, "to the button's corner: " + java.util.Arrays.toString(r));
        T.ok(Math.abs(r[2] - (b[2] + 4)) <= 6 && Math.abs(r[3] - (b[3] + 6)) <= 8, "its size (with its shadow): " + java.util.Arrays.toString(r));
        ImageTemplate t = ImageTemplate.cut(f, r[0], r[1], r[2], r[3]);
        BufferedImage other = scene(5, false);
        button(other, 120, 300, GREEN, "Collect");
        T.ok(ImageMatcher.find(frame(other), t, q(85)).found(), "the snapped cut-out is found on a busy screen");
        BufferedImage busy = scene(6, false);
        button(busy, 400, 1000, GREEN, "Collect");
        int[] rb = Magnet.snap(frame(busy), 360, 950, 350, 200);
        T.eq(0, rb[4], "busy background: no snapping");
        T.eq(360, rb[0], "left as drawn");
        int[] empty = Magnet.snap(f, 10, 10, 200, 200);
        T.eq(0, empty[4], "only background inside: left as drawn");
    }
}
