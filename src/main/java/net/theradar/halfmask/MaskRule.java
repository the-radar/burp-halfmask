package net.theradar.halfmask;

import java.util.regex.Pattern;

/**
 * One masking rule. A rule finds a sensitive value in the text of an HTTP message and says how much of it stays visible.
 * Length is always preserved, so a masked screenshot keeps the layout of the real one and a reader can still match the
 * visible prefix of a token across figures.
 */
public final class MaskRule {
    public enum Kind { HEADER, COOKIE, JSON_FIELD, PARAM, REGEX }

    public boolean enabled;
    public String name;
    public Kind kind;
    /** Header name, cookie name, JSON key, parameter name, or a regular expression (group 1 is the value, else the whole match). */
    public String pattern;
    /** Number of leading characters left visible. -1 means half of the value. */
    public int visible;

    public MaskRule(boolean enabled, String name, Kind kind, String pattern, int visible) {
        this.enabled = enabled; this.name = name; this.kind = kind; this.pattern = pattern; this.visible = visible;
    }

    public Pattern compile() {
        String q = Pattern.quote(pattern);
        switch (kind) {
            // Possessive repeats keep matching linear on untrusted, possibly multi-megabyte message bodies.
            case HEADER:     return Pattern.compile("(?im)^" + q + ":[ \\t]*+([^\\r\\n]*?)[ \\t]*+$");
            case COOKIE:     return Pattern.compile("(?i)(?<![\\w-])" + q + "=([^;\\r\\n]++)");
            case JSON_FIELD: return Pattern.compile("(?i)\"" + q + "\"\\s*+:\\s*+(?:\"((?:[^\"\\\\]|\\\\.)*+)\"|(-?[0-9][0-9.eE+-]*+|true|false))");
            case PARAM:      return Pattern.compile("(?i)(?<![\\w-])" + q + "=([^&\\s\"']++)");
            case REGEX:
            default:         return Pattern.compile(pattern, Pattern.MULTILINE);
        }
    }

    /** One line per rule, tab separated. Tabs and newlines do not occur in valid patterns of this tool. */
    public String serialize() {
        return (enabled ? "1" : "0") + "\t" + name.replace('\t', ' ') + "\t" + kind.name() + "\t" + pattern.replace('\t', ' ').replace("\n", "\\n") + "\t" + visible;
    }

    public static MaskRule parse(String line) {
        String[] p = line.split("\t", -1);
        if (p.length < 5) return null;
        try {
            return new MaskRule("1".equals(p[0]), p[1], Kind.valueOf(p[2]), p[3].replace("\\n", "\n"), Integer.parseInt(p[4]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public MaskRule copy() { return new MaskRule(enabled, name, kind, pattern, visible); }
}
