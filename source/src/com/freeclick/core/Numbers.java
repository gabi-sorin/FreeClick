package com.freeclick.core;

/**
 * Turns what is written on a screen into a number: "12 345", "1,234,567", "1.234,5", "12,5",
 * "1,5k", "2.3M", "4B", "45%", "-7", "Gold: 1.250". The first number in the text wins.
 *
 * Separators: a separator followed by exactly 3 digits groups thousands (games write 1,234 and
 * 1.234 for one thousand two hundred thirty-four); followed by 1, 2 or 4+ digits it is a decimal
 * point. With two different separators the last one is the decimal point (1,234.5 / 1.234,5).
 * Spaces and apostrophes only ever group thousands.
 */
public final class Numbers {
    private Numbers() {}

    public static Double parse(String text) {
        Scan s = scan(text);
        return s == null ? null : s.value;
    }

    /** True if the whole text (trimmed) is one number, nothing else. */
    public static boolean isWholeNumber(String text) {
        if (text == null) return false;
        String t = text.trim();
        Scan s = scan(t);
        return s != null && s.start == 0 && s.end == t.length();
    }

    static final class Scan { Double value; int start, end; }

    private static boolean isSep(char c) {
        return c == '.' || c == ',' || c == ' ' || c == ' ' || c == ' ' || c == '\'' || c == '’';
    }

    private static boolean grouping(char c) { return c == ' ' || c == ' ' || c == ' ' || c == '\'' || c == '’'; }

    static Scan scan(String text) {
        if (text == null) return null;
        int n = text.length();
        int i = 0;
        while (i < n && !Character.isDigit(text.charAt(i))) i++;
        if (i >= n) return null;
        int start = i;
        boolean neg = false;
        if (i > 0) {
            char p = text.charAt(i - 1);
            // "-7", "(-7)", but not the hyphen of "Level-5"
            if ((p == '-' || p == '−' || p == '–') && (i < 2 || !Character.isLetterOrDigit(text.charAt(i - 2)))) {
                neg = true;
                start = i - 1;
            }
        }
        // groups of digits and the separators between them
        java.util.List<String> groups = new java.util.ArrayList<String>();
        java.util.List<Character> seps = new java.util.ArrayList<Character>();
        StringBuilder g = new StringBuilder();
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) { g.append(c); i++; continue; }
            if (isSep(c) && i + 1 < n && Character.isDigit(text.charAt(i + 1))) {
                // a grouping separator needs exactly 3 digits after it and a short group before
                int k = i + 1;
                while (k < n && Character.isDigit(text.charAt(k))) k++;
                int after = k - i - 1;
                if (grouping(c) && (after != 3 || g.length() > 3)) break;
                groups.add(g.toString());
                g = new StringBuilder();
                seps.add(c);
                i++;
                continue;
            }
            break;
        }
        groups.add(g.toString());
        int end = i;

        String intPart, frac = "";
        int last = seps.size() - 1;
        if (seps.isEmpty()) {
            intPart = groups.get(0);
        } else {
            char lastSep = seps.get(last);
            boolean mixed = false;
            for (int k = 0; k < last; k++) if (seps.get(k) != lastSep) mixed = true;
            boolean lastIsDecimal;
            if (grouping(lastSep)) lastIsDecimal = false;
            else if (mixed) lastIsDecimal = true;
            else if (seps.size() >= 2) lastIsDecimal = false;
            else lastIsDecimal = groups.get(1).length() != 3 || groups.get(0).length() > 3 || suffixAt(text, end) != 0;
            if (seps.size() >= 2 && !mixed && !lastIsDecimal) {
                // 1.234.567: every group after the first must have 3 digits; stop at the first that does not
                int keep = 1;
                while (keep < groups.size() && groups.get(keep).length() == 3) keep++;
                if (keep < groups.size()) {
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < keep; k++) b.append(groups.get(k));
                    end = endOfGroups(text, start, keep);
                    return finish(b.toString(), "", neg, start, end, text);
                }
            }
            StringBuilder b = new StringBuilder();
            int intGroups = lastIsDecimal ? groups.size() - 1 : groups.size();
            for (int k = 0; k < intGroups; k++) b.append(groups.get(k));
            intPart = b.toString();
            if (lastIsDecimal) frac = groups.get(groups.size() - 1);
        }
        return finish(intPart, frac, neg, start, end, text);
    }

    private static int endOfGroups(String text, int start, int groupsWanted) {
        int i = start, seen = 0;
        while (i < text.length() && !Character.isDigit(text.charAt(i))) i++;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) { i++; continue; }
            if (++seen >= groupsWanted) return i;
            i++;
        }
        return i;
    }

    private static Scan finish(String intPart, String frac, boolean neg, int start, int end, String text) {
        if (intPart.length() == 0) intPart = "0";
        double v;
        try {
            v = Double.parseDouble(intPart + (frac.length() > 0 ? "." + frac : ""));
        } catch (NumberFormatException e) {
            return null;
        }
        double mul = suffixAt(text, end);
        if (mul != 0) {
            v *= mul;
            end = skipSuffix(text, end);
        } else if (end < text.length() && text.charAt(end) == '%') {
            end++;
        }
        Scan s = new Scan();
        s.value = neg ? -v : v;
        s.start = start;
        s.end = end;
        return s;
    }

    /** k/K = thousand, M = million, B/b = billion, T = trillion; directly (or after one space) behind the number, not the start of a word. */
    static double suffixAt(String text, int i) {
        if (i < text.length() && text.charAt(i) == ' ') i++;
        if (i >= text.length()) return 0;
        char c = text.charAt(i);
        double m;
        if (c == 'k' || c == 'K') m = 1e3;
        else if (c == 'M') m = 1e6;
        else if (c == 'B' || c == 'b') m = 1e9;
        else if (c == 'T') m = 1e12;
        else return 0;
        if (i + 1 < text.length() && Character.isLetter(text.charAt(i + 1))) return 0; // "5 Kills"
        return m;
    }

    private static int skipSuffix(String text, int i) {
        if (i < text.length() && text.charAt(i) == ' ') i++;
        return Math.min(text.length(), i + 1);
    }
}
