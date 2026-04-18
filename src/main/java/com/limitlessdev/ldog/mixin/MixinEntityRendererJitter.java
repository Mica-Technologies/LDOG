package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.CameraState;
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
 * Phase 9c.1 (jitter) + 9c.2 (camera matrix capture).
 *
 * Targets `renderWorldPass` rather than `setupCameraTransform` because
 * renderWorldPass overwrites the projection matrix TWICE after setupCamera
 * runs — once for sky (near/far geared to the cloud dome), once for terrain
 * (geared to the render distance). Earlier 9c.1 bug: jitter was applied
 * inside setupCameraTransform and immediately overwritten by renderWorldPass,
 * so the visible TAA effect was actually just plain frame blending with no
 * sub-pixel sample variation. Moving the injection here fixes that.
 *
 * Two injection points:
 *   - Sky gluPerspective (ordinal=0): apply jitter only.
 *   - Terrain gluPerspective (ordinal=1): capture un-jittered matrices FIRST
 *     (for motion-vector reprojection in TAA), then apply jitter.
 *
 * Capture order matters: CameraState reads GL_PROJECTION directly, so it
 * must happen BEFORE the glLoadMatrix that applies jitter. The un-jittered
 * projection is the correct one for MV reprojection — jitter is for sample
 * placement, not for the logical camera pose that MV should track.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererJitter {

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void ldog$advanceJitter(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass != 2) return;
        if (!LDOGConfig.enableTAA) return;
        JitterHelper.advanceFrame();
    }

    /** Sky projection: jitter only, no capture. */
    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
            ordinal = 0,
            shift = At.Shift.AFTER))
    private void ldog$jitterSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass != 2) return;
        if (!LDOGConfig.enableTAA) return;
        applyJitter();
    }

    /** Terrain projection: capture un-jittered matrices first, THEN jitter. */
    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
            ordinal = 1,
            shift = At.Shift.AFTER))
    private void ldog$captureAndJitterTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass != 2) return;
        if (!LDOGConfig.enableTAA) return;

        // Capture BEFORE jitter so MV reprojection uses the logical camera
        // pose, not the sub-pixel-shifted sample-placement projection.
        CameraState.captureCurrentMatrices();

        applyJitter();
    }

    private static void applyJitter() {
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

        float jx = JitterHelper.jitterX() * 2f / srcW;
        float jy = JitterHelper.jitterY() * 2f / srcH;

        FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, buf);
        buf.put(12, buf.get(12) + jx);
        buf.put(13, buf.get(13) + jy);
        buf.rewind();
        GL11.glLoadMatrix(buf);
    }
}
