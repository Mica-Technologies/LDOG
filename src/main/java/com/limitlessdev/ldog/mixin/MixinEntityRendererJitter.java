package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.CameraState;
import com.limitlessdev.ldog.render.pipeline.JitterHelper;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm;
import org.spongepowered.asm.mixin.Unique;
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
 *   - Terrain gluPerspective (ordinal=1): apply jitter FIRST, then capture the
 *     resulting (post-jitter) matrices for motion-vector reprojection.
 *
 * Capture order matters, and post-jitter is the correct order: history stores
 * each frame as it was actually rendered (jitter baked in), so cur/prev must
 * match that. See ldog$jitterAndCaptureTerrain for the full rationale — the
 * intuitive "capture the logical camera" order produces a per-frame swimming
 * artifact.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererJitter {

    /**
     * Jitter + camera-matrix capture are needed by ANY temporal consumer, not
     * just LDOG's standalone TAA. FSR2 is also temporal: it early-returns (drawing
     * nothing → black world + stale UI ghosts) when {@code CameraState} was never
     * captured. So this must be true whenever TAA is on OR FSR2 is the selected
     * upscaler — otherwise FSR2-with-TAA-off renders a blank screen.
     */
    @Unique
    private static boolean ldog$temporalActive() {
        return LDOGConfig.enableTAA || UpscalerAlgorithm.selected() == UpscalerAlgorithm.FSR2;
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void ldog$advanceJitter(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass != 2) return;
        // Jitter is applied only for TAA. FSR2-without-TAA still needs the camera
        // capture (below) but NOT jitter — LDOG's FSR2 doesn't fully resolve the
        // sub-pixel offset, so it shows up as flicker/shimmer during motion.
        if (!LDOGConfig.enableTAA) return;
        // No jitter while an external shader pack is driving — the per-frame
        // sub-pixel offset (unresolved without LDOG's TAA, which is skipped for
        // packs) shows up as heavy flicker.
        if (com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager.isDeferredActive()) return;
        JitterHelper.advanceFrame();
    }

    /** Sky projection: jitter only, no capture. */
    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
            ordinal = 0,
            shift = At.Shift.AFTER,
            // LWJGL class — no SRG mapping exists, suppress refmap lookup
            // and the spurious "Unable to locate method mapping" warning.
            remap = false))
    private void ldog$jitterSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass != 2) return;
        if (!LDOGConfig.enableTAA) return;  // jitter only for TAA (see advanceJitter)
        // No jitter while an external shader pack is driving — the per-frame
        // sub-pixel offset (unresolved without LDOG's TAA, which is skipped for
        // packs) shows up as heavy flicker.
        if (com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager.isDeferredActive()) return;
        applyJitter();
    }

    /** Terrain projection: jitter FIRST, then capture JITTERED matrices.
     *
     * Initially I had the opposite order (capture before jitter), reasoning
     * that MV should track the "logical" camera pose. That's wrong for TAA:
     * the history texture is stored with each frame's jitter baked in
     * (pixels are at slightly-shifted world positions each frame). If cur
     * and prev are captured pre-jitter, reprojection unprojects to the
     * WRONG world position and re-projects to the WRONG history UV,
     * producing a fractional-pixel offset that swings per-frame with the
     * Halton cycle — the "drunk / swimming" visual artifact.
     *
     * Capturing post-jitter keeps cur/prev self-consistent with what was
     * actually rendered, so reprojected UVs land on the right history
     * pixels. Classic TAA gotcha. */
    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
            ordinal = 1,
            shift = At.Shift.AFTER,
            remap = false))
    private void ldog$jitterAndCaptureTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (pass != 2) return;
        if (!ldog$temporalActive()) return;
        // No jitter while an external shader pack is driving — the per-frame
        // sub-pixel offset (unresolved without LDOG's TAA, which is skipped for
        // packs) shows up as heavy flicker.
        if (com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager.isDeferredActive()) return;

        // Jitter only for TAA. FSR2-without-TAA renders un-jittered and captures
        // un-jittered matrices below — consistent, and without the jitter shimmer.
        if (LDOGConfig.enableTAA) applyJitter();

        // Capture matrices for temporal MV reprojection (needed by FSR2 too, or
        // it draws nothing). When TAA jittered the projection above, the capture
        // is post-jitter so cur/prev stay consistent with the stored history.
        CameraState.captureCurrentMatrices();
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
