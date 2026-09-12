package com.limitlessdev.ldog.render.shaderpack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OptiFine's legacy sampler names for the colour attachments, and the
 * {@code colortexN} index each one aliases.
 *
 * <p>Packs written before the {@code colortexN} naming — SEUS v11 is the
 * reference case, and it is still one of the best-behaved 1.12.2 packs —
 * declare their composite inputs as {@code gcolor} / {@code gdepth} /
 * {@code gnormal} / {@code composite} / {@code gaux1..4} and never mention
 * {@code colortexN} at all. OptiFine accepts both spellings for the same
 * attachment.
 *
 * <p>Getting this wrong is silent and ugly rather than fatal: a sampler
 * uniform that is never assigned reads texture unit 0, which is the scene
 * colour, so {@code texture2D(gnormal, uv)} returns the rendered image where
 * the shader expects a normal. SEUS's Nether chain leans on {@code gnormal},
 * {@code gaux2} and {@code gaux3} hard enough that the result is unrecognisable
 * pastel smearing; its overworld chain leans on them less and merely looked
 * a bit off.
 *
 * <p>{@code gdepthtex} is deliberately NOT in this table: it aliases the depth
 * texture ({@code depthtex0}), not a colour attachment, so callers bind it
 * alongside {@code depthtex0} instead.
 */
public final class ShaderSamplerAliases {

    /** Legacy sampler name → the {@code colortexN} index it refers to. */
    public static final Map<String, Integer> LEGACY_COLORTEX;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("gcolor", 0);
        m.put("gdepth", 1);
        m.put("gnormal", 2);
        m.put("composite", 3);
        m.put("gaux1", 4);
        m.put("gaux2", 5);
        m.put("gaux3", 6);
        m.put("gaux4", 7);
        LEGACY_COLORTEX = Collections.unmodifiableMap(m);
    }

    /** Legacy alias for {@code depthtex0}. */
    public static final String LEGACY_DEPTH = "gdepthtex";

    /** Legacy alias for {@code shadowcolor0}. */
    public static final String LEGACY_SHADOW_COLOR = "shadowcolor";

    private ShaderSamplerAliases() {}
}
