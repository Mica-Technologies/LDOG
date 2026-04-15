package com.limitlessdev.ldog.mixin;

import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors the current render layer to our public tracker in
 * MixinBlockRendererDispatcher, since Forge's renderLayer field
 * is package-private and inaccessible from outside the package.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient {

    @Inject(method = "setRenderLayer", at = @At("HEAD"))
    private static void ldog$trackRenderLayer(BlockRenderLayer layer, CallbackInfo ci) {
        MixinBlockRendererDispatcher.ldog$setRenderLayer(layer);
    }
}
