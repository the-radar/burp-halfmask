package net.theradar.halfmask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies a rule set to message text. Pure Java, no Burp types, so it is unit-testable. */
public final class Masker {
    public static final char DEFAULT_MASK_CHAR = '*'; // renders in every editor font; the block glyph does not
    private final List<MaskRule> rules;
    private final char maskChar;

    public Masker(List<MaskRule> rules, char maskChar) {
        this.rules = rules; this.maskChar = maskChar;
    }

    public String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        // Ranges already masked are tracked so a broad rule (email regex) does not re-mask the prefix a narrow rule left visible.
        boolean[] done = new boolean[text.length()];
        for (MaskRule r : rules) {
            if (!r.enabled || r.pattern == null || r.pattern.isEmpty()) continue;
            Pattern p;
            try { p = r.compile(); } catch (RuntimeException e) { continue; }
            Matcher m = p.matcher(text);
            while (m.find()) {
                int start = -1, end = -1;
                for (int g = 1; g <= m.groupCount(); g++) {
                    if (m.group(g) != null) { start = m.start(g); end = m.end(g); break; }
                }
                if (start < 0) { start = m.start(); end = m.end(); }
                if (end <= start) continue;
                maskRange(sb, done, start, end, r.visible);
            }
        }
        return sb.toString();
    }

    private void maskRange(StringBuilder sb, boolean[] done, int start, int end, int visible) {
        int len = end - start;
        int keep = visible < 0 ? len / 2 : Math.min(visible, len);
        if (len <= 2) keep = 0;              // a one or two character value has nothing safe to show
        if (keep >= len) keep = Math.max(0, len - 1);
        for (int i = start + keep; i < end; i++) {
            if (done[i]) continue;
            char c = sb.charAt(i);
            if (c == '\r' || c == '\n') continue;
            sb.setCharAt(i, maskChar);
            done[i] = true;
        }
        for (int i = start; i < start + keep; i++) done[i] = true;
    }

    /**
     * Shortens any run of mask characters longer than {@code max} to {@code max} characters plus a count, for figures and
     * clipboard text. The Masked editor tab keeps the full length so it lines up with the real message.
     */
    public static String collapse(String masked, char maskChar, int max) {
        if (masked == null) return null;
        StringBuilder out = new StringBuilder(masked.length());
        int i = 0, n = masked.length();
        while (i < n) {
            if (masked.charAt(i) != maskChar) { out.append(masked.charAt(i)); i++; continue; }
            int j = i; while (j < n && masked.charAt(j) == maskChar) j++;
            int run = j - i;
            if (run <= max) out.append(masked, i, j);
            else out.append(masked, i, i + max).append("[+").append(run - max).append(" masked]");
            i = j;
        }
        return out.toString();
    }

    /** The rules a fresh install starts with. Users edit them in the HalfMask tab. */
    public static List<MaskRule> defaultRules() {
        List<MaskRule> l = new ArrayList<>();
        l.add(new MaskRule(true, "Authorization header", MaskRule.Kind.HEADER, "Authorization", 12));
        l.add(new MaskRule(true, "Cookie header", MaskRule.Kind.HEADER, "Cookie", -1));
        l.add(new MaskRule(true, "Set-Cookie header", MaskRule.Kind.HEADER, "Set-Cookie", -1));
        l.add(new MaskRule(true, "X-API-Key header", MaskRule.Kind.HEADER, "X-API-Key", 6));
        // Long credentials (tokens) keep a fixed 24-character prefix: enough to match the same token across figures, never half of a 1,500-character JWE.
        for (String f : new String[]{"token", "access_token", "refresh_token", "id_token", "jwt", "secret", "client_secret", "api_key", "apiKey"}) {
            l.add(new MaskRule(true, "JSON " + f, MaskRule.Kind.JSON_FIELD, f, 24));
        }
        // Short personal values keep half.
        for (String f : new String[]{"password", "pass", "pwd", "otp", "code", "ssn", "dob", "dateOfBirth"}) {
            l.add(new MaskRule(true, "JSON " + f, MaskRule.Kind.JSON_FIELD, f, -1));
        }
        for (String f : new String[]{"password", "token", "access_token", "api_key", "code"}) {
            l.add(new MaskRule(true, "Param " + f, MaskRule.Kind.PARAM, f, -1));
        }
        l.add(new MaskRule(true, "JWT anywhere", MaskRule.Kind.REGEX, "eyJ[A-Za-z0-9_-]{8,}+(?:\\.[A-Za-z0-9_-]*+){2,4}+", 24));
        // The lookbehind makes the scan start only at the first character of a run, so an 8 MB base64 body without an @ is linear.
        l.add(new MaskRule(true, "Email address", MaskRule.Kind.REGEX, "(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]++@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}+", 3));
        l.add(new MaskRule(false, "US phone number", MaskRule.Kind.REGEX, "\\(?\\b[2-9][0-9]{2}\\)?[-. ][0-9]{3}[-. ][0-9]{4}\\b", 3));
        l.add(new MaskRule(false, "Patient name fields", MaskRule.Kind.JSON_FIELD, "patientName", -1));
        return Collections.unmodifiableList(l);
    }
}
