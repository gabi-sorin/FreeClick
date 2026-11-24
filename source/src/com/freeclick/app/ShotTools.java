package com.freeclick.app;

import android.graphics.Bitmap;

import com.freeclick.core.ColorMatch;
import com.freeclick.core.ImageMatcher;
import com.freeclick.core.ImageSpec;
import com.freeclick.core.ImageTemplate;
import com.freeclick.core.ScreenFrame;
import com.freeclick.core.TextReader;
import com.freeclick.core.script.ScriptSimulator;

import java.util.ArrayList;
import java.util.List;

/**
 * The heavy work on a screenshot (off the main thread): what a zone reads, where an image is, which
 * pixels match a colour, cutting a picture for an image step, and the still "screen" a simulation
 * looks at. Positions in and out are in the script's coordinates (design size dw × dh).
 */
final class ShotTools {
    final Bitmap bmp;
    final int dw, dh;
    private ScreenFrame frame;

    ShotTools(Bitmap bmp, int dw, int dh) {
        this.bmp = bmp;
        this.dw = Math.max(1, dw);
        this.dh = Math.max(1, dh);
    }

    private float kx() { return bmp.getWidth() / (float) dw; }
    private float ky() { return bmp.getHeight() / (float) dh; }

    /** All the pixels as a frame (about 10 MB for a phone screen, made once). */
    synchronized ScreenFrame frame() {
        if (frame == null) {
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            frame = new ScreenFrame(px, w, h, 0);
        }
        return frame;
    }

    /** What citeste / citesteText would read in the zone (script coordinates); null if nothing. */
    String read(int x1, int y1, int x2, int y2, boolean number) {
        int bx1 = Math.round(Math.min(x1, x2) * kx()), by1 = Math.round(Math.min(y1, y2) * ky());
        int bx2 = Math.round(Math.max(x1, x2) * kx()), by2 = Math.round(Math.max(y1, y2) * ky());
        if (bx2 - bx1 < 4 || by2 - by1 < 4) return null;
        try {
            TextReader.Result r = TextReader.read(frame(), bx1, by1, bx2, by2, number ? TextReader.MODE_NUMBER : TextReader.MODE_TEXT,
                    App.get().letters().book());
            return r == null || r.text == null || r.text.isEmpty() ? null : r.text;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Boxes {x1, y1, x2, y2} (script coordinates) where the image is, top-most first. */
    List<int[]> findBoxes(ImageSpec spec, boolean all) {
        List<int[]> out = new ArrayList<int[]>();
        for (ImageMatcher.Match m : match(spec, all)) {
            out.add(new int[]{Math.round(m.x / kx()), Math.round(m.y / ky()), Math.round((m.x + m.w) / kx()), Math.round((m.y + m.h) / ky())});
        }
        return out;
    }

    /** Tap points (script coordinates), as gaseste() gives them. */
    List<int[]> findTaps(ImageSpec spec, boolean all) {
        List<int[]> out = new ArrayList<int[]>();
        if (spec == null) return out;
        ScreenFrame f = frame();
        double sx = spec.capScreenW > 0 ? f.width / (double) spec.capScreenW : 1, sy = spec.capScreenH > 0 ? f.height / (double) spec.capScreenH : 1;
        for (ImageMatcher.Match m : match(spec, all)) {
            out.add(new int[]{(int) Math.round((m.x + spec.tapDx * sx) / kx()), (int) Math.round((m.y + spec.tapDy * sy) / ky())});
        }
        return out;
    }

    private List<ImageMatcher.Match> match(ImageSpec spec, boolean all) {
        List<ImageMatcher.Match> none = new ArrayList<ImageMatcher.Match>();
        if (spec == null || !App.get().images().has(spec.id)) return none;
        try {
            ImageTemplate t0 = App.get().images().template(spec.id);
            if (t0 == null) return none;
            ScreenFrame f = frame();
            double sx = spec.capScreenW > 0 ? f.width / (double) spec.capScreenW : 1, sy = spec.capScreenH > 0 ? f.height / (double) spec.capScreenH : 1;
            ImageMatcher.Query q = new ImageMatcher.Query();
            q.precision = spec.precision;
            q.multiScale = spec.multiScale();
            q.all = all;
            q.useCore = !spec.tight;
            if (spec.hasZone()) {
                q.zx1 = (int) Math.floor(spec.zx1 * sx); q.zy1 = (int) Math.floor(spec.zy1 * sy);
                q.zx2 = (int) Math.ceil(spec.zx2 * sx); q.zy2 = (int) Math.ceil(spec.zy2 * sy);
            }
            return ImageMatcher.find(f, t0.scaled(sx, sy), q).matches;
        } catch (RuntimeException e) {
            return none;
        }
    }

    /** A small see-through picture marking the pixels close to rgb (drawn over the screenshot). */
    Bitmap colorMask(int rgb, int tolerance) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int step = Math.max(1, (int) Math.ceil(Math.max(w, h) / 720.0));
        int mw = (w + step - 1) / step, mh = (h + step - 1) / step;
        int[] row = new int[w];
        int[] out = new int[mw * mh];
        for (int my = 0; my < mh; my++) {
            int y = Math.min(h - 1, my * step);
            bmp.getPixels(row, 0, w, 0, y, w, 1);
            for (int mx = 0; mx < mw; mx++) {
                int c = row[Math.min(w - 1, mx * step)] & 0xFFFFFF;
                out[my * mw + mx] = ColorMatch.matches(c, rgb, tolerance) ? 0xCCFF2BD6 : 0x66000000;
            }
        }
        return Bitmap.createBitmap(out, mw, mh, Bitmap.Config.ARGB_8888);
    }

    /** A picture for an image step, cut from the screenshot (zone in script coordinates); null + reason if it cannot be used. */
    ImageSpec cut(int x1, int y1, int x2, int y2, String[] problem) throws java.io.IOException {
        int bx = Math.round(x1 * kx()), by = Math.round(y1 * ky());
        int bw = Math.round((x2 - x1) * kx()), bh = Math.round((y2 - y1) * ky());
        if (bw < ImageTemplate.MIN_SIDE || bh < ImageTemplate.MIN_SIDE) {
            problem[0] = "Prea mic: prinde tot butonul (minim " + ImageTemplate.MIN_SIDE + " px pe captură).";
            return null;
        }
        if (bw > ImageTemplate.MAX_SIDE || bh > ImageTemplate.MAX_SIDE) {
            problem[0] = "Prea mare: prinde doar butonul (maxim " + ImageTemplate.MAX_SIDE + " px pe captură).";
            return null;
        }
        ScreenFrame f = frame();
        ImageTemplate t = ImageTemplate.cut(f, bx, by, bw, bh);
        if (t.detail() < ImageTemplate.MIN_DETAIL) {
            problem[0] = "Zona e aproape de o singură culoare. Alege ceva cu text sau un desen.";
            return null;
        }
        ImageSpec spec = new ImageSpec();
        spec.id = App.get().images().save(t.argb, t.width, t.height);
        spec.w = t.width;
        spec.h = t.height;
        spec.capX = bx;
        spec.capY = by;
        spec.capScreenW = f.width;
        spec.capScreenH = f.height;
        spec.tapDx = t.width / 2f;
        spec.tapDy = t.height / 2f;
        spec.tight = true;
        return spec;
    }

    /** The screenshot as the screen of a simulation. */
    ScriptSimulator.Screen screen(final java.util.List<com.freeclick.core.Action> steps) {
        return new ScriptSimulator.Screen() {
            @Override public int width() { return dw; }
            @Override public int height() { return dh; }
            @Override public int pixel(int x, int y) {
                int bx = Math.round(x * kx()), by = Math.round(y * ky());
                if (bx < 0 || by < 0 || bx >= bmp.getWidth() || by >= bmp.getHeight()) return -1;
                return bmp.getPixel(bx, by) & 0xFFFFFF;
            }
            @Override public String read(int x1, int y1, int x2, int y2, boolean number) {
                String s = ShotTools.this.read(x1, y1, x2, y2, number);
                return s == null ? "" : s;
            }
            @Override public List<int[]> find(ImageSpec spec, boolean all) { return findTaps(spec, all); }
        };
    }
}
