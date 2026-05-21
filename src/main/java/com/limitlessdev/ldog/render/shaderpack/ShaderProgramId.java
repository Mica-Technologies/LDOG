package com.limitlessdev.ldog.render.shaderpack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical OptiFine / Iris shader-program identifiers and their
 * {@code .vsh}/{@code .fsh} filename pairs.
 *
 * <p>Tracks the "well-known" programs every modern 1.12.2 shader pack
 * names. Composite stages (composite, composite1, … composite15) and
 * deferred stages are not listed individually — they're enumerated at
 * runtime by probing the pack for {@code compositeN.{vsh,fsh}} files.
 *
 * <p>Per the LDOG external-references policy, no code is copied from
 * OptiFine or Iris — this is a clean re-derivation of the publicly
 * documented file naming.
 */
public final class ShaderProgramId {

    /** Map of {@code .vsh} filename → corresponding {@code .fsh} filename. */
    public static final Map<String, String> STANDARD_PAIRS = new LinkedHashMap<>();
    static {
        // gbuffers_* — the per-stage geometry pass shaders. Names match OF's
        // standard, which Iris reuses verbatim.
        for (String stage : new String[]{
            "basic",
            "textured",
            "textured_lit",
            "skybasic",
            "skytextured",
            "clouds",
            "terrain",
            "terrain_solid",
            "terrain_cutout",
            "terrain_cutout_mip",
            "damagedblock",
            "water",
            "block",
            "beaconbeam",
            "item",
            "entities",
            "armor_glint",
            "spidereyes",
            "hand",
            "weather",
        }) {
            STANDARD_PAIRS.put("gbuffers_" + stage + ".vsh",
                               "gbuffers_" + stage + ".fsh");
        }
        // Special non-prefixed stages.
        STANDARD_PAIRS.put("shadow.vsh",       "shadow.fsh");
        STANDARD_PAIRS.put("shadow_solid.vsh", "shadow_solid.fsh");
        STANDARD_PAIRS.put("final.vsh",        "final.fsh");
    }

    private ShaderProgramId() {}
}
