package com.limitlessdev.ldog.render.shaderpack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the shader packs LDOG ships inside its own jar.
 *
 * <p>Unlike OptiFine — which ships no packs and leaves users to hunt down
 * third-party ones — LDOG bundles a handful of sensible, guaranteed-compatible
 * packs written directly against LDOG's composite/final runner and GLSL 120
 * (MC 1.12.2's GL 2.1 ceiling). They always compile + run, so they're the
 * reliable way to see the shader pipeline working even when a downloaded pack
 * targets a newer Minecraft / Iris and can't run here.
 *
 * <p>Built-in names are shown in the picker with a {@code [LDOG]} prefix and
 * resolve to classpath resources under
 * {@code /assets/ldog/shaderpacks/<dir>/shaders/}.
 */
public final class BuiltinShaderPacks {

    /** Display-name prefix that marks a pack as one of LDOG's built-ins. */
    public static final String PREFIX = "[LDOG] ";

    /** Ordered map of display label (without prefix) → resource directory. */
    private static final Map<String, String> PACKS = new LinkedHashMap<>();
    static {
        PACKS.put("HDR",        "hdr");
        PACKS.put("Realism",    "realism");
        PACKS.put("Cinematic",  "cinematic");
        PACKS.put("Pseudo-RTX", "pseudortx");
        // Deferred: a full gbuffer + shadow demo (needs Pack Gbuffers, and
        // Pack Shadows for the cast shadows). Shows the deferred path working.
        PACKS.put("Deferred",   "deferred");
    }

    private BuiltinShaderPacks() {}

    /** Prefixed display names, in registry order, for the picker list. */
    public static List<String> displayNames() {
        List<String> out = new ArrayList<>(PACKS.size());
        for (String label : PACKS.keySet()) out.add(PREFIX + label);
        return Collections.unmodifiableList(out);
    }

    /** True when {@code name} is a built-in pack's (prefixed) display name. */
    public static boolean isBuiltin(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /**
     * Resource directory for a built-in pack's display name (prefixed or not),
     * or null when the name isn't a registered built-in.
     */
    public static String resourceDir(String displayName) {
        if (displayName == null) return null;
        String key = displayName.startsWith(PREFIX)
            ? displayName.substring(PREFIX.length()) : displayName;
        return PACKS.get(key);
    }
}
