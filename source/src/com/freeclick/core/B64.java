package com.freeclick.core;

/** Base64 (RFC 4648) without java.util.Base64, which Android only has from version 8. */
public final class B64 {
    private B64() {}
    private static final char[] A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] D = new int[128];
    static {
        java.util.Arrays.fill(D, -1);
        for (int i = 0; i < A.length; i++) D[A[i]] = i;
    }

    public static String encode(byte[] b) {
        StringBuilder sb = new StringBuilder((b.length + 2) / 3 * 4);
        for (int i = 0; i < b.length; i += 3) {
            int n = (b[i] & 255) << 16 | (i + 1 < b.length ? (b[i + 1] & 255) << 8 : 0) | (i + 2 < b.length ? b[i + 2] & 255 : 0);
            sb.append(A[n >> 18 & 63]).append(A[n >> 12 & 63]);
            sb.append(i + 1 < b.length ? A[n >> 6 & 63] : '=');
            sb.append(i + 2 < b.length ? A[n & 63] : '=');
        }
        return sb.toString();
    }

    /** Decodes; whitespace is ignored; null if the text is not valid base64. */
    public static byte[] decode(String s) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length() * 3 / 4);
        int buf = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=') break;
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue;
            if (c >= 128 || D[c] < 0) return null;
            buf = buf << 6 | D[c];
            bits += 6;
            if (bits >= 8) { bits -= 8; out.write(buf >> bits & 255); }
        }
        return out.toByteArray();
    }
}
