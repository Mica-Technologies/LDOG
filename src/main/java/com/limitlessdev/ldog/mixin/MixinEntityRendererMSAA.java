package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.msaa.MSAAFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps EntityRenderer.renderWorldPass with bind/resolve around a multisampled
 * auxiliary FBO to add MSAA to world geometry only.
 *
 * GUI and HUD render to mc.framebufferMc as normal (post-resolve), so MSAA
 * doesn't smear 2D UI pixels.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererMSAA {

    @Unique private int ldog$savedFbo = 0;
    @Unique private boolean ldog$msaaActive = false;

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void ldog$msaaBind(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ldog$msaaActive = false;
        if (!LDOGConfig.enableMSAA) return;
        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null) return;

        int w = fb.framebufferTextureWidth;
        int h = fb.framebufferTextureHeight;
        if (!MSAAFramebuffer.ensure(w, h)) return;

        ldog$savedFbo = fb.framebufferObject;
        MSAAFramebuffer.bind();
        // Clear stale pixels from a previous frame — sky rendering doesn't
        // always cover every pixel at the view's far edges.
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        ldog$msaaActive = true;
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("RETURN"))
    private void ldog$msaaResolve(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!ldog$msaaActive) return;
        MSAAFramebuffer.resolveTo(ldog$savedFbo);
        ldog$msaaActive = false;
    }
}
