package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.emissive.EmissiveItemRenderHandler;
import com.limitlessdev.ldog.render.emissive.EmissiveTextureRegistry;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into item model rendering to add emissive texture overlays.
 *
 * Targets the private renderModel(IBakedModel, int, ItemStack) method,
 * which is the core tessellation point for all item quad rendering.
 * This is called from both GUI inventory rendering and 3D held-item
 * rendering, so the emissive overlay appears in all contexts.
 *
 * The emissive overlay is drawn after the base item quads but before
 * the enchantment glint effect (which is applied in the caller,
 * renderItem(ItemStack, IBakedModel)).
 */
@Mixin(RenderItem.class)
public abstract class MixinRenderItem {

    @Inject(method = "renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
            at = @At("RETURN"))
    private void ldog$renderEmissiveItemOverlay(IBakedModel model, int color,
                                                 ItemStack stack, CallbackInfo ci) {
        if (LDOGConfig.enableEmissiveTextures && EmissiveTextureRegistry.getEmissiveSpriteCount() > 0) {
            EmissiveItemRenderHandler.renderEmissiveOverlay(model);
        }
    }
}
