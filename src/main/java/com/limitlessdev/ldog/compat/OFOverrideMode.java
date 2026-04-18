package com.limitlessdev.ldog.compat;

/**
 * Per-feature interop mode between LDOG and OptiFine — Phase C4.
 *
 * Three states cycled by the GUI:
 *
 * <ul>
 *   <li><b>AUTO</b>: legacy behavior. If OptiFine is loaded, defer to it for
 *       the feature; if not, run LDOG's implementation. Safe default for every
 *       feature until LDOG's version is benchmarked at parity-or-better.</li>
 *   <li><b>LDOG_OVERRIDE</b>: when OF is loaded, forcibly disable OF's version
 *       of the feature via {@link OFConfigBridge}, then run LDOG's. Falls back
 *       to OPTIFINE_OVERRIDE if the reflective disable fails (field missing
 *       across OF versions, access blocked, etc.) — never leaves both systems
 *       fighting over the same feature.</li>
 *   <li><b>OPTIFINE_OVERRIDE</b>: explicitly defer to OF, even on the off chance
 *       LDOG would otherwise take over. Equivalent to AUTO when OF is loaded;
 *       differs from AUTO only in user intent (says "I want OF here, even if
 *       LDOG ships a better version later").</li>
 * </ul>
 *
 * String-backed so config files survive enum reorder / additions. Unknown
 * values fall back to AUTO.
 *
 * Naming pattern mirrors {@link com.limitlessdev.ldog.render.pipeline.AutoScaleMode}
 * for consistency with the rest of LDOG's enum-backed config surface.
 */
public enum OFOverrideMode {

    AUTO              ("auto",     "Auto"),
    LDOG_OVERRIDE     ("ldog",     "LDOG"),
    OPTIFINE_OVERRIDE ("optifine", "OptiFine");

    private final String configKey;
    private final String displayName;

    OFOverrideMode(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }

    /** Cycle to the next mode in display order; wraps the values list. */
    public OFOverrideMode next() {
        OFOverrideMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Resolve a string config value to a mode. Unknown / null → AUTO. */
    public static OFOverrideMode fromConfigKey(String key) {
        if (key == null) return AUTO;
        for (OFOverrideMode m : values()) {
            if (m.configKey.equalsIgnoreCase(key)) return m;
        }
        return AUTO;
    }
}
