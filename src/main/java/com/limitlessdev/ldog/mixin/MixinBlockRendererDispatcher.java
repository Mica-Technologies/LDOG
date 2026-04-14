package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.ctm.CTMRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sets the CTM render context (world + position) before each block renders.
 * This allows CTMBakedModel.getQuads() to check neighbor blocks for
 * connected texture decisions.
 */
@Mixin(BlockRendererDispatcher.class)
public abstract class MixinBlockRendererDispatcher {

    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void ldog$setRenderContext(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                        BufferBuilder bufferBuilderIn,
                                        CallbackInfoReturnable<Boolean> cir) {
        CTMRenderContext.set(blockAccess, pos);
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void ldog$clearRenderContext(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                          BufferBuilder bufferBuilderIn,
                                          CallbackInfoReturnable<Boolean> cir) {
        CTMRenderContext.clear();
    }
}
