package io.github.seandettmann.schoolbag.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and a typed view over what it reads. Plain Java, so the
 * app's core runs (and is tested) off the device too.
 *
 * Objects become maps, arrays lists, numbers doubles. {@link J} wraps any of
 * them and never throws: a missing or wrongly typed field reads as empty, 0,
 * false or "", which suits the protocol's rule that fields can be left out.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static J parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        return new J(v);
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
            else break;
        }
    }

    private Object value() {
        if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw new IllegalArgumentException("bad JSON at " + i);
        i += word.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            if (s.charAt(i) != ':') throw new IllegalArgumentException("expected : at " + i);
            i++;
            ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw new IllegalArgumentException("expected , at " + (i - 1));
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw new IllegalArgumentException("expected , at " + (i - 1));
        }
    }

    private String string() {
        if (s.charAt(i) != '"') throw new IllegalArgumentException("expected string at " + i);
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: b.append(e);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw new IllegalArgumentException("bad JSON at " + i);
        return Double.parseDouble(s.substring(start, i));
    }

    /** Writes a string as a JSON string literal. */
    public static String quote(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    /** A forgiving view over a parsed value. */
    public static final class J {
        public static final J NONE = new J(null);
        private final Object v;

        public J(Object v) { this.v = v; }

        public boolean exists() { return v != null; }
        public Object raw() { return v; }

        @SuppressWarnings("unchecked")
        public J get(String key) {
            if (v instanceof Map) {
                Object o = ((Map<String, Object>) v).get(key);
                return o == null ? NONE : new J(o);
            }
            return NONE;
        }

        public J at(int index) {
            List<Object> l = list();
            return index >= 0 && index < l.size() && l.get(index) != null ? new J(l.get(index)) : NONE;
        }

        public boolean has(String key) { return get(key).exists(); }

        /** Arrays as a list. Lua can't tell an empty list from an empty object, so {} reads as []. */
        @SuppressWarnings("unchecked")
        public List<Object> list() {
            if (v instanceof List) return (List<Object>) v;
            return Collections.emptyList();
        }

        public List<J> items() {
            List<J> out = new ArrayList<>();
            for (Object o : list()) out.add(new J(o));
            return out;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> map() {
            if (v instanceof Map) return (Map<String, Object>) v;
            return Collections.emptyMap();
        }

        public int size() { return v instanceof List ? ((List<?>) v).size() : v instanceof Map ? ((Map<?, ?>) v).size() : 0; }

        public double num(double dflt) { return v instanceof Double ? (Double) v : dflt; }
        public double num() { return num(0); }
        public int integer(int dflt) { return v instanceof Double ? (int) Math.round((Double) v) : dflt; }
        public int integer() { return integer(0); }
        public long lng() { return v instanceof Double ? Math.round((Double) v) : 0; }
        public boolean bool() { return Boolean.TRUE.equals(v); }
        public String str(String dflt) { return v instanceof String ? (String) v : dflt; }
        public String str() { return str(""); }
    }
}
