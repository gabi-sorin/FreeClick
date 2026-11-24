package com.freeclick.core;

import java.awt.Font;
import java.util.Random;

/** Accuracy of TextReader on drawn scenes (fonts not in the reference book). Used by the tests and for tuning. */
final class OcrBench {
    private OcrBench() {}

    static final int[][] COLOURS = { // fg, bg, bg2
            {0xFFFFFF, 0x1E5AA8, 0x1E5AA8}, {0x111111, 0xFFFFFF, 0xFFFFFF}, {0xFFD700, 0x7A1010, 0x5A0808},
            {0xDDDDDD, 0x333333, 0x333333}, {0x34A853, 0xFFFFFF, 0xF0F0F0}, {0xFFFFFF, 0x0E7A3A, 0x2EB860},
            {0x202020, 0xF5C542, 0xF5C542}};

    static String number(Random r) {
        int kind = r.nextInt(9);
        long v = (long) Math.pow(10, 1 + r.nextInt(6)) + r.nextInt(1000000);
        v = v % (long) Math.pow(10, 1 + r.nextInt(7));
        switch (kind) {
            case 0: return Long.toString(v);
            case 1: return String.format(java.util.Locale.US, "%,d", v);
            case 2: return String.format(java.util.Locale.US, "%,d", v).replace(',', '.');
            case 3: return String.format(java.util.Locale.US, "%,d", v).replace(',', ' ');
            case 4: return (1 + r.nextInt(99)) + "." + r.nextInt(10) + "k";
            case 5: return (1 + r.nextInt(9)) + "." + r.nextInt(10) + "M";
            case 6: return r.nextInt(101) + "%";
            case 7: return "-" + (1 + r.nextInt(500));
            default: return (r.nextInt(50)) + "/" + (50 + r.nextInt(50));
        }
    }

    static final String[] WORDS = {"Collect", "Gold", "Level 12", "Start", "Victory!", "Setări", "Înapoi", "Ana are mere",
            "Player One", "Next", "Daily Reward", "Upgrade", "Coins: 250", "OK", "Game Over", "Retry", "Shop", "Ștefan",
            "Energy 5/10", "Claim", "Battle", "Quest", "Inventory", "Free Spin", "Bonus x2"};

    static final class Stats {
        int n, exact; long chars, charErr;
        double rate() { return n == 0 ? 1 : exact / (double) n; }
        double charAcc() { return chars == 0 ? 1 : 1 - charErr / (double) chars; }
    }

    static Stats numbers(String family, int style, int[] sizes, int count, long seed) {
        Stats st = new Stats();
        Random r = new Random(seed);
        FontBook book = AwtText.book();
        for (int i = 0; i < count; i++) {
            String s = number(r);
            int px = sizes[r.nextInt(sizes.length)];
            int[] col = COLOURS[r.nextInt(COLOURS.length)];
            Font f = AwtText.font(family, style, px);
            int w = px * (s.length() + 4), h = px * 3;
            AwtText.Scene sc = AwtText.scene(s, f, col[0], col[1], col[2], w, h, px, px * 2, r.nextBoolean() ? r.nextLong() : 0);
            int m = 2 + r.nextInt(Math.max(1, px / 3));
            TextReader.Result res = TextReader.read(sc.argb, w, h, sc.x0 - m - r.nextInt(5), sc.y0 - m, sc.x1 + m + r.nextInt(5), sc.y1 + m, TextReader.MODE_NUMBER, book);
            Double want = Numbers.parse(s);
            boolean ok = res.number != null && Math.abs(res.number - want) < 1e-6 * Math.max(1, Math.abs(want));
            st.n++;
            if (ok) st.exact++;
            else if (System.getProperty("ocr.verbose") != null) System.out.println("      " + family + " " + px + "px: \"" + s + "\" read \"" + res.text + "\" " + res.problem);
        }
        return st;
    }

    static Stats words(String family, int style, int[] sizes, int count, long seed) {
        Stats st = new Stats();
        Random r = new Random(seed);
        FontBook book = AwtText.book();
        for (int i = 0; i < count; i++) {
            String s = WORDS[r.nextInt(WORDS.length)];
            int px = sizes[r.nextInt(sizes.length)];
            int[] col = COLOURS[r.nextInt(COLOURS.length)];
            Font f = AwtText.font(family, style, px);
            int w = px * (s.length() + 4), h = px * 3;
            AwtText.Scene sc = AwtText.scene(s, f, col[0], col[1], col[2], w, h, px, px * 2, r.nextBoolean() ? r.nextLong() : 0);
            int m = 2 + r.nextInt(Math.max(1, px / 3));
            TextReader.Result res = TextReader.read(sc.argb, w, h, sc.x0 - m, sc.y0 - m - px / 4, sc.x1 + m, sc.y1 + m + px / 4, TextReader.MODE_TEXT, book);
            st.n++;
            if (res.text.equals(s)) st.exact++;
            else if (System.getProperty("ocr.verbose") != null) System.out.println("      " + family + " " + px + "px: \"" + s + "\" read \"" + res.text + "\"");
            st.chars += s.length();
            st.charErr += editDistance(s, res.text);
        }
        return st;
    }

    static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
        return d[a.length()][b.length()];
    }

    public static void main(String[] args) {
        String[] fams = args.length > 0 ? args[0].split(",") : new String[]{"Roboto", "Open Sans", "Inter", "Fira Sans", "Poppins", "FreeSans", "DejaVu Sans", "Anton"};
        int[] small = {14, 16, 18}, big = {22, 28, 36, 48};
        for (String fam : fams) {
            for (int style : new int[]{Font.PLAIN, Font.BOLD}) {
                Stats a = numbers(fam, style, small, 60, 1), b = numbers(fam, style, big, 60, 2);
                Stats c = words(fam, style, small, 40, 3), d = words(fam, style, big, 40, 4);
                System.out.printf("%-12s %s  numbers small %3.0f%% big %3.0f%% | words small %3.0f%% (chars %3.0f%%) big %3.0f%% (chars %3.0f%%)%n",
                        fam, style == Font.BOLD ? "B" : " ", a.rate() * 100, b.rate() * 100, c.rate() * 100, c.charAcc() * 100, d.rate() * 100, d.charAcc() * 100);
            }
        }
    }
}
