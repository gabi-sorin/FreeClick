package com.freeclick.core;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 📤 Share codes (2.3): any save (script, targets, recording, combination, or several at once) as one
 * line of text that fits in a chat message: "FREECLICK1:" + base64 of the compressed bundle file.
 * Pasting a whole message works: the code is found inside it, even if the chat app broke it into lines.
 * Importing never overwrites anything (MacroCodec gives new ids when they clash).
 */
public final class ShareCode {
    private ShareCode() {}

    public static final String PREFIX = "FREECLICK1:";
    /** Largest bundle a code may unpack to (pictures included). */
    public static final int MAX_JSON_BYTES = 30 * 1024 * 1024;

    public static String encode(String bundleJson) {
        byte[] in;
        try { in = bundleJson.getBytes("UTF-8"); } catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            d.setInput(in);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, in.length / 3));
            byte[] buf = new byte[8192];
            while (!d.finished()) {
                int n = d.deflate(buf);
                out.write(buf, 0, n);
            }
            return PREFIX + B64.encode(out.toByteArray());
        } finally {
            d.end();
        }
    }

    /** Whether the text holds a code (or is a FreeClick file pasted as text). */
    public static boolean contains(String text) {
        if (text == null) return false;
        return text.contains(PREFIX) || looksLikeBundle(text);
    }

    private static boolean looksLikeBundle(String text) {
        String t = text.trim();
        return t.startsWith("{") && t.contains("\"actions\"");
    }

    /** The bundle file inside a pasted text (a code anywhere in it, or the file itself). */
    public static String decode(String text) {
        if (text == null) throw new IllegalArgumentException("Nu e niciun cod FreeClick aici");
        int at = text.indexOf(PREFIX);
        if (at < 0) {
            if (looksLikeBundle(text)) return text.trim();
            throw new IllegalArgumentException("Nu e niciun cod FreeClick aici (începe cu " + PREFIX + ")");
        }
        StringBuilder b = new StringBuilder();
        for (int i = at + PREFIX.length(); i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue; // chat apps break long lines
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=') {
                b.append(c);
                continue;
            }
            break;
        }
        byte[] packed = B64.decode(b.toString());
        if (packed == null || packed.length == 0) throw new IllegalArgumentException("Codul e incomplet sau stricat (s-a copiat tot?)");
        Inflater inf = new Inflater();
        try {
            inf.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw new IllegalArgumentException("Codul e incomplet (s-a copiat tot?)");
                out.write(buf, 0, n);
                if (out.size() > MAX_JSON_BYTES) throw new IllegalArgumentException("Codul e prea mare");
            }
            return out.toString("UTF-8");
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Codul e stricat (s-a copiat tot?)");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            inf.end();
        }
    }

    /** The message that goes with a code: what it is and how to import it, then the code on its own line. */
    public static String message(List<Macro> items, String code) {
        StringBuilder b = new StringBuilder("FreeClick · ");
        if (items.size() == 1) {
            Macro m = items.get(0);
            b.append('„').append(m.name).append("” (").append(kindName(m.kind)).append(')');
        } else {
            b.append(items.size()).append(" salvări: ");
            for (int i = 0; i < items.size() && i < 6; i++) b.append(i > 0 ? ", " : "").append('„').append(items.get(i).name).append('”');
            if (items.size() > 6) b.append("…");
        }
        b.append("\nImportă: copiază mesajul și deschide FreeClick → Bibliotecă (îl găsește singur)\n");
        return b.append(code).toString();
    }

    static String kindName(String k) {
        if (Macro.KIND_RECORD.equals(k)) return "înregistrare";
        if (Macro.KIND_COMBO.equals(k)) return "combinație";
        if (Macro.KIND_SCRIPT.equals(k)) return "script";
        return "ținte";
    }
}
