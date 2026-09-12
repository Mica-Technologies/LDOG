package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

/**
 * Extends vanilla's terrain vertex format ({@code DefaultVertexFormats.BLOCK},
 * 28 bytes: position, colour, uv, lightmap) with a packed normal
 * ({@code NORMAL_3B} + one padding byte, 32 bytes total).
 *
 * <p>Why: shader packs light terrain with {@code dot(gl_Normal, sunDir)}, and
 * vanilla chunk geometry carries no per-vertex normal at all — every terrain
 * vertex sees whatever {@code glNormal} happened to be set last, so a pack's
 * direct-light term is the same (usually zero) for every face. OptiFine
 * extends this same format when its shaders are on; this is LDOG's version.
 *
 * <p>How the normals get filled: Forge's block lighting pipeline
 * ({@code VertexLighterFlat} / {@code VertexLighterSmoothAo}) already computes
 * a per-quad normal (from the baked quad, or from the vertex positions when
 * the quad has none) and its buffer consumer writes every element of the
 * target buffer's format — so once BLOCK has a normal slot, every chunk quad
 * gets a correct normal for free. That is why the extension is gated on
 * {@code forgeLightPipelineEnabled}: the vanilla path copies raw 28-int quad
 * data into the buffer and would mis-stride a 32-byte format.
 *
 * <p>Applied once at client preInit (before any chunk is compiled, before
 * Forge's lighter caches {@code BLOCK}'s normal-augmented twin) and never
 * undone: the format is baked into every compiled chunk and every quad
 * built with {@code BLOCK} afterwards, so it is a restart-level switch.
 */
public final class TerrainVertexFormat {

    private TerrainVertexFormat() {}

    /**
     * The 28-byte layout BLOCK had before extension. LDOG code that builds
     * quads by hand with 7 ints per vertex must tag them with THIS (or
     * {@code ITEM}) so Forge's lighter unpacks them with the right stride and
     * generates their normal, rather than with the extended BLOCK.
     */
    public static final VertexFormat LEGACY_BLOCK = new VertexFormat()
        .addElement(DefaultVertexFormats.POSITION_3F)
        .addElement(DefaultVertexFormats.COLOR_4UB)
        .addElement(DefaultVertexFormats.TEX_2F)
        .addElement(DefaultVertexFormats.TEX_2S);

    private static boolean extended;
    private static int normalOffset = -1;

    /** True once BLOCK carries a normal element. */
    public static boolean isExtended() { return extended; }

    /** Byte stride of the (possibly extended) BLOCK format. */
    public static int stride() { return DefaultVertexFormats.BLOCK.getSize(); }

    /** Byte offset of the normal within a BLOCK vertex, or -1 when not extended. */
    public static int normalOffset() { return normalOffset; }

    /**
     * Extend BLOCK if the config asks for it and Forge's light pipeline is on.
     * Idempotent. Call from the client proxy's preInit.
     */
    public static void applyAtStartup() {
        if (extended) return;
        if (!LDOGConfig.extendTerrainVertexFormat) {
            LDOGMod.LOGGER.info("LDOG: terrain vertex normals disabled by config; shader packs get flat sun lighting");
            return;
        }
        if (!net.minecraftforge.common.ForgeModContainer.forgeLightPipelineEnabled) {
            LDOGMod.LOGGER.warn("LDOG: forgeLightPipelineEnabled=false in forge.cfg — cannot add terrain normals (the vanilla path copies raw 28-byte quads). Shader packs get flat sun lighting.");
            return;
        }
        VertexFormat block = DefaultVertexFormats.BLOCK;
        if (block.hasNormal()) {
            extended = true;
            normalOffset = findNormalOffset(block);
            LDOGMod.LOGGER.info("LDOG: BLOCK vertex format already carries a normal (another mod extended it); using it as-is");
            return;
        }
        if (block.getSize() != 28) {
            LDOGMod.LOGGER.warn("LDOG: BLOCK vertex format is {} bytes, not the vanilla 28 — another mod changed it; leaving it alone", block.getSize());
            return;
        }
        block.addElement(DefaultVertexFormats.NORMAL_3B);
        block.addElement(DefaultVertexFormats.PADDING_1B);
        extended = true;
        normalOffset = findNormalOffset(block);
        LDOGMod.LOGGER.info("LDOG: terrain vertex format extended with per-vertex normals (BLOCK = {} bytes, normal @ {})",
            block.getSize(), normalOffset);
    }

    private static int findNormalOffset(VertexFormat f) {
        for (int i = 0; i < f.getElementCount(); i++) {
            if (f.getElement(i).getUsage() == VertexFormatElement.EnumUsage.NORMAL) return f.getOffset(i);
        }
        return -1;
    }
}
