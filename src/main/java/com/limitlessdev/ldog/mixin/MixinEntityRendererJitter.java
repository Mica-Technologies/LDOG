package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.JitterHelper;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

/**
 * Phase 9c.1: applies sub-pixel jitter to GL_PROJECTION after each
 * {@code Project.gluPerspective} call in renderWorldPass / setupCameraTransform.
 *
 * Technique: read the current projection matrix, offset its (0,3) and (1,3)
 * elements (the clip-space x/y translation terms) by a sub-pixel amount in
 * NDC units, load it back. Equivalent to pre-multiplying by a translation
 * matrix — each frame renders with a sub-pixel-shifted projection so
 * temporal accumulation sees different pixel centers.
 *
 * Frame advance happens on renderWorldPass HEAD (pass=2, non-anaglyph) so
 * the jitter cycles through Halton(2, 3) at one sample per rendered frame.
 * Jitter amount is scaled to the source render target's pixel size — scene
 * target when the pipeline is upscaling, else the main framebuffer.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererJitter {

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void ldog$advanceJitter(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass != 2) return;
        if (!LDOGConfig.enableTAA) return;
        JitterHelper.advanceFrame();
    }

    @Inject(
        method = "setupCameraTransform",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
            shift = At.Shift.AFTER))
    private void ldog$applyJitter(float partialTicks, int pass, CallbackInfo ci) {
        if (pass != 2) return;
        if (!LDOGConfig.enableTAA) return;

        // Pick the source-dim basis for sub-pixel jitter. When the pipeline
        // is upscaling, world rasterization happens at the scaled scene target
        // — jitter should be in THAT pixel space. When pipeline is off, use
        // main framebuffer dims.
        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        int srcW;
        int srcH;
        if (LDOGConfig.enablePostProcessPipeline && rtm.isReady()) {
            srcW = rtm.getScaledWidth();
            srcH = rtm.getScaledHeight();
        } else {
            srcW = Minecraft.getMinecraft().displayWidth;
            srcH = Minecraft.getMinecraft().displayHeight;
        }
        if (srcW <= 0 || srcH <= 0) return;

        // Halton jitter is in texel units [-0.5, 0.5]. Convert to NDC: a full
        // texel in NDC is 2/width, so half-texel = 1/width. Using 2*jitter/width
        // gives a [-1/width, 1/width] NDC shift — 1 texel of range centered on 0.
        float jx = JitterHelper.jitterX() * 2f / srcW;
        float jy = JitterHelper.jitterY() * 2f / srcH;

        // Column-major 4x4: P[row][col] at flat index (row + 4*col).
        // For clip-space x translation: P[0][3] → flat index 12.
        // For clip-space y translation: P[1][3] → flat index 13.
        FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, buf);
        buf.put(12, buf.get(12) + jx);
        buf.put(13, buf.get(13) + jy);
        buf.rewind();
        GL11.glLoadMatrix(buf);
    }
}
