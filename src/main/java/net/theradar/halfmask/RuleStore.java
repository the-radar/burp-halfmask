package net.theradar.halfmask;

import burp.api.montoya.persistence.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Holds the live rule list, saves it in Burp's user preferences, and hands out a Masker built from the current state. */
public final class RuleStore {
    private static final String KEY_RULES = "halfmask.rules.v1";
    private static final String KEY_ENABLED = "halfmask.enabled";
    private static final String KEY_MASKCHAR = "halfmask.maskchar";

    private final Preferences prefs;
    private final List<MaskRule> rules = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean enabled = true;
    private volatile char maskChar = Masker.DEFAULT_MASK_CHAR;

    public RuleStore(Preferences prefs) {
        this.prefs = prefs;
        load();
    }

    private void load() {
        String raw = prefs == null ? null : prefs.getString(KEY_RULES);
        List<MaskRule> loaded = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String line : raw.split("\n")) {
                MaskRule r = MaskRule.parse(line);
                if (r != null) loaded.add(r);
            }
        }
        if (loaded.isEmpty()) for (MaskRule r : Masker.defaultRules()) loaded.add(r.copy());
        rules.clear(); rules.addAll(loaded);
        Boolean en = prefs == null ? null : prefs.getBoolean(KEY_ENABLED);
        enabled = en == null || en;
        String mc = prefs == null ? null : prefs.getString(KEY_MASKCHAR);
        if (mc != null && mc.length() == 1) maskChar = mc.charAt(0);
    }

    public void save() {
        if (prefs != null) {
            StringBuilder sb = new StringBuilder();
            for (MaskRule r : rules) sb.append(r.serialize()).append('\n');
            prefs.setString(KEY_RULES, sb.toString());
            prefs.setBoolean(KEY_ENABLED, enabled);
            prefs.setString(KEY_MASKCHAR, String.valueOf(maskChar));
        }
        for (Runnable l : listeners) l.run();
    }

    public List<MaskRule> rules() { return rules; }
    public void replaceRules(List<MaskRule> newRules) { rules.clear(); rules.addAll(newRules); save(); }
    public void resetToDefaults() { List<MaskRule> d = new ArrayList<>(); for (MaskRule r : Masker.defaultRules()) d.add(r.copy()); replaceRules(d); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean b) { enabled = b; save(); }
    public char maskChar() { return maskChar; }
    public void setMaskChar(char c) { maskChar = c; save(); }
    public Masker masker() { return new Masker(new ArrayList<>(rules), maskChar); }
    public void addListener(Runnable r) { listeners.add(r); }
    public void clearListeners() { listeners.clear(); }
}
