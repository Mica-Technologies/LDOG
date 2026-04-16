package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.dynamiclights.DynamicLightManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects dynamic light values into the world's combined light calculation.
 * getCombinedLight is @SideOnly(Side.CLIENT), so this mixin only applies on the client.
 *
 * When a dynamic light source (entity holding a torch, dropped glowstone, etc.)
 * is near a block position, the block light component of the returned value is
 * raised to reflect the dynamic illumination.
 */
@Mixin(World.class)
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
