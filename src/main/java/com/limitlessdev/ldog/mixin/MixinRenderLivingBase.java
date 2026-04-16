package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.randommobs.RandomEntityTextureHandler;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into entity texture resolution to provide random texture variants.
 *
 * Targets RenderLivingBase.getEntityTexture() — the method that returns
 * the texture ResourceLocation for each living entity. If the
 * RandomEntityTextureHandler has a variant for this entity, the texture
 * is replaced based on the entity's UUID.
 *
 * This covers all living entity renderers (animals, mobs, villagers, etc.)
 * since they all extend RenderLivingBase.
 */
@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBase<T extends EntityLivingBase> {

    @Inject(method = "getEntityTexture",
            at = @At("RETURN"),
            cancellable = true)
    private void ldog$randomEntityTexture(T entity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (!LDOGConfig.enableRandomEntityTextures) return;

        ResourceLocation original = cir.getReturnValue();
        if (original == null) return;

        ResourceLocation variant = RandomEntityTextureHandler.getVariantTexture(original, entity);
        if (variant != null) {
            cir.setReturnValue(variant);
        }
    }
}
