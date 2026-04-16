package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.randommobs.RandomEntityTextureHandler;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into entity texture binding to provide random texture variants.
 *
 * Targets bindEntityTexture (concrete method inherited from Render) rather
 * than getEntityTexture (abstract, no method body to inject into). When a
 * variant exists, binds the variant texture and returns true, cancelling
 * the original method. When no variant exists, falls through to vanilla.
 */
@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBase<T extends EntityLivingBase> extends Render<T> {

    protected MixinRenderLivingBase(RenderManager renderManager) {
        super(renderManager);
    }

    @Inject(method = "bindEntityTexture(Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void ldog$randomEntityTexture(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (!LDOGConfig.enableRandomEntityTextures) return;

        ResourceLocation original = this.getEntityTexture(entity);
        if (original == null) return;

        ResourceLocation variant = RandomEntityTextureHandler.getVariantTexture(original, entity);
        if (variant != null) {
            this.bindTexture(variant);
            cir.setReturnValue(true);
        }
    }
}
