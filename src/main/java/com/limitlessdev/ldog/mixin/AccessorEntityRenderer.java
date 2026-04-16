package com.limitlessdev.ldog.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.ShaderGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read/write access to the private shader-group state on EntityRenderer so
 * we can unload FXAA (or any mod-managed post-process shader) without
 * triggering a full resource reload.
 */
@Mixin(EntityRenderer.class)
public interface AccessorEntityRenderer {

    @Accessor("shaderGroup")
    ShaderGroup ldog$getShaderGroup();

    @Accessor("shaderGroup")
    void ldog$setShaderGroup(ShaderGroup shaderGroup);

    @Accessor("useShader")
    void ldog$setUseShader(boolean useShader);
}
