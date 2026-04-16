package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.dynamiclights.DynamicLightManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects dynamic light values into ChunkCache.getCombinedLight().
 *
 * ChunkCache is the IBlockAccess used during chunk rendering (created in
 * RenderChunk.rebuildChunk). Targeting it instead of World avoids the
 * "loaded too early" problem (World is loaded before any mixin system).
 *
 * When a dynamic light source (entity holding a torch, dropped glowstone, etc.)
 * is near a block position, the block light component of the returned value is
 * raised to reflect the dynamic illumination.
 */
@Mixin(ChunkCache.class)
public abstract class MixinWorldDynamicLights {

    @Inject(method = "getCombinedLight", at = @At("RETURN"), cancellable = true)
    private void ldog$injectDynamicLight(BlockPos pos, int lightValue,
                                          CallbackInfoReturnable<Integer> cir) {
        if (!LDOGConfig.enableDynamicLights) return;

        int dynamicLight = DynamicLightManager.getInstance().getDynamicLightLevel(pos);
        if (dynamicLight <= 0) return;

        int original = cir.getReturnValue();
        int skyLight = (original >> 20) & 0xF;
        int blockLight = (original >> 4) & 0xF;

        if (dynamicLight > blockLight) {
            blockLight = Math.min(dynamicLight, 15);
            cir.setReturnValue(skyLight << 20 | blockLight << 4);
        }
    }
}
