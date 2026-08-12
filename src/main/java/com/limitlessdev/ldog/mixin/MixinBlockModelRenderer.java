package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.emissive.EmissiveRenderHandler;
import com.limitlessdev.ldog.render.emissive.EmissiveTextureRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into block model rendering to add emissive texture overlays.
 *
 * Targets renderModel (the public dispatch method) rather than
 * renderModelSmooth/renderModelFlat, because Forge's
 * ForgeBlockModelRenderer overrides the smooth/flat methods.
 *
 * Emissive quads are rendered into the CUTOUT_MIPPED buffer (obtained
 * from the chunk rebuild's ThreadLocal) so alpha testing can discard
 * transparent pixels.
 */
@Mixin(BlockModelRenderer.class)
public abstract class MixinBlockModelRenderer {

    @Inject(method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;ZJ)Z",
            at = @At("RETURN"))
    private void ldog$renderEmissiveOverlay(IBlockAccess worldIn, IBakedModel modelIn,
                                             IBlockState stateIn, BlockPos posIn,
                                             BufferBuilder buffer, boolean checkSides,
                                             long rand, CallbackInfoReturnable<Boolean> cir) {
        // Per-block early-out: only emissive-mapped blocks need the overlay.
        // Without this, every block in a chunk rebuild paid a 6-face getQuads
        // scan (and started the emissive layer buffer) for nothing.
        //
        // isActive() (config toggle + OptiFine interop decision) is tested LAST
        // on purpose: this runs per block per chunk rebuild, on worker threads,
        // so the two array-lookup guards ahead of it eliminate all but the
        // handful of blocks that were about to do real work anyway.
        if (EmissiveTextureRegistry.getEmissiveSpriteCount() > 0
                && EmissiveTextureRegistry.isEmissiveBlock(stateIn.getBlock())
                && EmissiveTextureRegistry.isActive()) {
            EmissiveRenderHandler.renderEmissiveOverlay(
                modelIn, stateIn, worldIn, posIn, checkSides, rand);
        }
    }
}
