package com.freeclick.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader/writer.
 * Objects -> LinkedHashMap<String,Object>, arrays -> ArrayList<Object>,
 * integers -> Long, other numbers -> Double, plus String, Boolean and null.
 * Kept in the pure-Java core so macro files can be unit-tested on a plain JVM.
 */
public final class Json {
    private Json() {}

    public static final class ParseException extends RuntimeException {
        public ParseException(String msg, int pos) { super(msg + " at position " + pos); }
    }

    // ---------------------------------------------------------------- parsing
    public static Object parse(String text) {
        if (text == null) throw new ParseException("null input", 0);
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value(0);
        p.skipWs();
        if (p.pos != text.length()) throw new ParseException("trailing characters", p.pos);
        return v;
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 256;
        final String s; int pos;
        Parser(String s) { this.s = s; }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++; else break;
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw new ParseException("nesting too deep", pos);
            if (pos >= s.length()) throw new ParseException("unexpected end", pos);
            char c = s.charAt(pos);
            switch (c) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw new ParseException("unexpected character '" + c + "'", pos);
            }
        }

        void literal(String word) {
            if (!s.startsWith(word, pos)) throw new ParseException("expected " + word, pos);
            pos += word.length();
        }

        Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            pos++; skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') { pos++; return m; }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') throw new ParseException("expected key", pos);
                String key = string();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') throw new ParseException("expected ':'", pos);
                pos++; skipWs();
                m.put(key, value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw new ParseException("unterminated object", pos);
                char c = s.charAt(pos++);
                if (c == '}') return m;
                if (c != ',') throw new ParseException("expected ',' or '}'", pos - 1);
            }
        }

        List<Object> array(int depth) {
            List<Object> l = new ArrayList<Object>();
            pos++; skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') { pos++; return l; }
            while (true) {
                skipWs();
                l.add(value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw new ParseException("unterminated array", pos);
                char c = s.charAt(pos++);
                if (c == ']') return l;
                if (c != ',') throw new ParseException("expected ',' or ']'", pos - 1);
            }
        }

        String string() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) throw new ParseException("unterminated string", pos);
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) throw new ParseException("bad escape", pos);
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw new ParseException("bad unicode escape", pos);
                            try {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw new ParseException("bad unicode escape", pos);
                            }
                            pos += 4;
                            break;
                        default: throw new ParseException("bad escape \\" + e, pos - 1);
                    }
                } else {
                    if (c < 0x20) throw new ParseException("control character in string", pos - 1);
                    sb.append(c);
                }
            }
        }

        Object number() {
            int start = pos;
            boolean isFloat = false;
            if (s.charAt(pos) == '-') pos++;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') pos++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { isFloat = true; pos++; }
                else break;
            }
            String num = s.substring(start, pos);
            try {
                if (!isFloat) {
                    try { return Long.valueOf(num); } catch (NumberFormatException big) { return Double.valueOf(num); }
                }
                return Double.valueOf(num);
            } catch (NumberFormatException ex) {
                throw new ParseException("bad number '" + num + "'", start);
            }
        }
    }

    /** Independent copy of parsed JSON (maps, lists and immutable leaves). */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T v) {
        if (v instanceof Map) {
            Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) out.put(e.getKey(), deepCopy(e.getValue()));
            return (T) out;
        }
        if (v instanceof List) {
            List<Object> out = new java.util.ArrayList<Object>();
            for (Object o : (List<Object>) v) out.add(deepCopy(o));
            return (T) out;
        }
        return v;
    }

    // ---------------------------------------------------------------- writing
    public static String stringify(Object v) { return stringify(v, false); }

    public static String stringify(Object v, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, pretty, 0);
        return sb.toString();
    }

    private static void indent(StringBuilder sb, boolean pretty, int level) {
        if (!pretty) return;
        sb.append('\n');
        for (int i = 0; i < level; i++) sb.append("  ");
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object v, boolean pretty, int level) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String) { quote(sb, (String) v); return; }
        if (v instanceof Boolean) { sb.append(v.toString()); return; }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) { sb.append("0"); return; }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(Double.toString(d));
            return;
        }
        if (v instanceof Number) { sb.append(((Number) v).longValue()); return; }
        if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            sb.append('{');
            Iterator<Map.Entry<String, Object>> it = m.entrySet().iterator();
            boolean first = true;
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                if (!first) sb.append(',');
                first = false;
                indent(sb, pretty, level + 1);
                quote(sb, e.getKey());
                sb.append(pretty ? ": " : ":");
                write(sb, e.getValue(), pretty, level + 1);
            }
            if (!m.isEmpty()) indent(sb, pretty, level);
            sb.append('}');
            return;
        }
        if (v instanceof List) {
            List<Object> l = (List<Object>) v;
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                indent(sb, pretty, level + 1);
                write(sb, l.get(i), pretty, level + 1);
            }
            if (!l.isEmpty()) indent(sb, pretty, level);
            sb.append(']');
            return;
        }
        quote(sb, v.toString());
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- typed getters
    public static long getLong(Map<String, Object> m, String k, long def) {
        Object o = m.get(k);
        return o instanceof Number ? ((Number) o).longValue() : def;
    }

    public static int getInt(Map<String, Object> m, String k, int def) {
        Object o = m.get(k);
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    public static double getDouble(Map<String, Object> m, String k, double def) {
        Object o = m.get(k);
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    public static boolean getBool(Map<String, Object> m, String k, boolean def) {
        Object o = m.get(k);
        return o instanceof Boolean ? (Boolean) o : def;
    }

    public static String getString(Map<String, Object> m, String k, String def) {
        Object o = m.get(k);
        return o instanceof String ? (String) o : def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getArray(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o instanceof List ? (List<Object>) o : null;
    }
}
