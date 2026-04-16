package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.randommobs.RandomEntityTextureHandler;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Hooks into Render.bindEntityTexture to provide random texture variants
 * for living entities.
 *
 * Targets Render (where bindEntityTexture is defined) rather than
 * RenderLivingBase (where it's only inherited). Only applies the random
 * texture swap when the entity is a living entity (EntityLivingBase).
 */
@Mixin(Render.class)
public abstract class MixinRenderLivingBase<T extends Entity> {

    @Shadow @Nullable
    protected abstract ResourceLocation getEntityTexture(T entity);

    @Shadow
    public abstract void bindTexture(ResourceLocation location);

    @SuppressWarnings("unchecked")
    @Inject(method = "bindEntityTexture", at = @At("HEAD"), cancellable = true)
    private void ldog$randomEntityTexture(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (!LDOGConfig.enableRandomEntityTextures) return;
        if (!(entity instanceof EntityLivingBase)) return;

        ResourceLocation original = this.getEntityTexture(entity);
        if (original == null) return;

        ResourceLocation variant = RandomEntityTextureHandler.getVariantTexture(original, entity);
        if (variant != null) {
            this.bindTexture(variant);
            cir.setReturnValue(true);
        }
    }
}
