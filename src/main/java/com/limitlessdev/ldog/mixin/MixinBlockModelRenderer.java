package com.limitlessdev.ldog.mixin;

import net.minecraft.client.renderer.BlockModelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder mixin on BlockModelRenderer.
 * Emissive rendering is now handled at the BlockRendererDispatcher level
 * (in the CUTOUT_MIPPED pass) rather than here, because Forge's
 * ForgeBlockModelRenderer overrides renderModelSmooth/renderModelFlat
 * and injects into renderModel don't reach the correct render layer.
 */
@Mixin(BlockModelRenderer.class)
public abstract class MixinBlockModelRenderer {
    // Emissive rendering moved to MixinBlockRendererDispatcher
}
