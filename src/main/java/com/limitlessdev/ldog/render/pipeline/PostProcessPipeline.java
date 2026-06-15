package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.passes.BilinearBlitPass;
import com.limitlessdev.ldog.render.pipeline.passes.BloomPass;
import com.limitlessdev.ldog.render.pipeline.passes.EntityMotionVectorPass;
import com.limitlessdev.ldog.render.pipeline.passes.FSR1EASUPass;
import com.limitlessdev.ldog.render.pipeline.passes.FSR1QualityPass;
import com.limitlessdev.ldog.render.pipeline.passes.FSR2ReconstructionPass;
import com.limitlessdev.ldog.render.pipeline.passes.HDRTonemapPass;
import com.limitlessdev.ldog.render.pipeline.passes.LDOGFXAAPass;
import com.limitlessdev.ldog.render.pipeline.passes.RCASSharpenPass;
import com.limitlessdev.ldog.render.pipeline.passes.ShaderPackCompositePass;
import com.limitlessdev.ldog.render.pipeline.passes.TAAAccumulatePass;
import com.limitlessdev.ldog.render.pipeline.passes.VignettePass;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the post-process pass chain that runs at the tail of each
 * world-render pass.
 *
 * The mixin is responsible for binding the scene target at HEAD; this class
 * is responsible for running passes, surfacing diagnostics, and disposing on
 * fatal errors.
 */
public final class PostProcessPipeline {

    public static final PostProcessPipeline INSTANCE = new PostProcessPipeline();

    /**
     * After this many frames with the pipeline gate on but binding never
     * activating, emit a one-shot WARN explaining which guard is blocking.
     * Tuned to ignore normal warmup (first frame or two may miss before RTM
     * is ready).
     */
    private static final int BIND_WATCHDOG_FRAMES = 120;

    private final List<PostProcessPass> passes = new ArrayList<>();
    private boolean initialized;
    private int width;
    private int height;
    private boolean loggedReady;
    private boolean loggedFirstBind;
    private boolean loggedBindWatchdog;
    private int framesSinceLastBind;

    private PostProcessPipeline() {
        registerPasses();
    }

    /**
     * (Re)populate the pass chain. Idempotent — safe to call again to recover
     * the pipeline after a pass was dropped or the chain was cleared by a
     * fatal-error reset, so the scene→main resolve pass can never go
     * permanently missing (which would leave a black screen).
     */
    private void registerPasses() {
        for (PostProcessPass p : passes) {
            try { p.dispose(); } catch (Exception ignored) { }
        }
        passes.clear();
        // Bloom runs FIRST while the scene is still HDR — the bright-pass
        // shader needs to see luminance values exceeding [0,1] to produce a
        // proper glow on suns/torches/lava. Composites bloom additively
        // back into the scene texture (still HDR).
        passes.add(new BloomPass());
        // HDR tonemap runs second so downstream passes (upscaler, RCAS, FXAA,
        // vignette, blit-back) operate on LDR-clamped values. After tonemap
        // the scene texture holds [0,1] values in HDR storage — that's what
        // the blit to MC's RGBA8 main FB expects without information loss.
        passes.add(new HDRTonemapPass());
        // All upscalers are always registered; each pass's isEnabled() checks
        // the selected algorithm so exactly one runs per frame. New upscalers
        // (NIS, FSR2, etc.) plug in here alongside the existing ones.
        // 9c.3-C: per-entity motion-vector emission. Must run BEFORE any
        // upscaler that consumes MV (specifically FSR2). The MV pass only
        // depends on CameraState + the per-frame entity queue — not on the
        // scene color texture's content — so it's safe to run this early.
        // TAA also consumes the MV target but runs later in the chain.
        passes.add(new EntityMotionVectorPass());
        passes.add(new BilinearBlitPass());
        passes.add(new FSR1EASUPass());
        passes.add(new FSR1QualityPass());
        // 9c.4: FSR2 is a temporal upscaler (Lanczos source + jittered
        // history + entity MV reprojection) — slotted alongside the spatial
        // upscalers since exactly one runs per frame based on
        // upscalerAlgorithm. When FSR2 is selected, the standalone TAA pass
        // short-circuits because FSR2 owns history accumulation itself.
        passes.add(new FSR2ReconstructionPass());
        // TAA runs AFTER the upscaler — temporal accumulation operates on the
        // native-res upscaled image. Companion MixinEntityRendererJitter
        // offsets the projection matrix per frame so samples hit different
        // pixel centers for detail accumulation.
        passes.add(new TAAAccumulatePass());
        // RCAS sharpens the blended TAA result (not a noisy pre-blend image).
        passes.add(new RCASSharpenPass());
        // FXAA runs late so it smooths any aliasing introduced earlier.
        passes.add(new LDOGFXAAPass());
        // Shader pack composite chain: runs the user's active pack's
        // composite + final stages against the post-AA main framebuffer.
        // Slotted late so it sees the fully-resolved upscaled+TAA'd image.
        // Skips itself when no pack is active.
        passes.add(new ShaderPackCompositePass());
        // Vignette runs ABSOLUTE LAST — it's a final-image multiplicative
        // darkening that shouldn't be smoothed by FXAA (FXAA's edge detector
        // would otherwise treat the vignette gradient as an edge to soften).
        passes.add(new VignettePass());
    }

    /**
     * Tick the pass chain for one frame. Called from the renderWorldPass
     * RETURN hook with all the state the mixin has already gathered.
     */
    public void onFrame(PostProcessContext ctx) {
        if (!LDOGConfig.enablePostProcessPipeline) return;
        if (ctx.mainWidth() <= 0 || ctx.mainHeight() <= 0) return;

        long t0 = System.nanoTime();

        try {
            ensureInitialized(ctx.mainWidth(), ctx.mainHeight());
            int active = runPasses(ctx);
            PipelineDebugStats.update(active, ctx.mainWidth(), ctx.mainHeight(), System.nanoTime() - t0);

            RenderTargetManager rtm = RenderTargetManager.INSTANCE;
            PipelineDebugStats.updateTargets(
                rtm.isReady(), rtm.getScale(), rtm.getScaledWidth(), rtm.getScaledHeight());

            diagnoseBinding(ctx);
        } catch (Exception e) {
            LDOGMod.LOGGER.error("LDOG: Post-process pipeline fatal error; disabling for this session", e);
            disableAll();
        }
    }

    /**
     * True when another LDOG feature currently owns the world-pass FBO binding.
     *
     * The 8c binding hook must yield to MSAA (which wraps renderWorldPass with
     * its own multisampled FBO). Consolidated here as a single source of
     * truth so the mixin and any future pass share the check.
     */
    public static boolean hasConflictingFeatureOn() {
        return LDOGConfig.enableMSAA;
    }

    /**
     * The render scale actually used this frame. A deferred shader pack renders
     * at NATIVE resolution (1.0) — the internal-scale upscaling otherwise blurs
     * distant geometry, and the pack owns the final image. Otherwise honour the
     * user's {@code internalRenderScale}. Both the pipeline and the world-bind
     * mixin must agree on this, so it lives here as the single source of truth.
     */
    public static float effectiveRenderScale() {
        if (com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager.isDeferredActive()) {
            return 1.0f;
        }
        return (float) LDOGConfig.internalRenderScale;
    }

    private void ensureInitialized(int w, int h) throws Exception {
        RenderTargetManager.INSTANCE.ensure(w, h, effectiveRenderScale(), LDOGConfig.enableHDRPipeline);

        // Self-heal: if the chain was emptied (fatal-error reset) or a pass was
        // dropped, rebuild it so re-enabling the pipeline always has its passes
        // (including the scene→main resolve) back. Without this, toggling the
        // pipeline off→on after any error left a black screen.
        if (passes.isEmpty()) {
            registerPasses();
            initialized = false;
        }

        if (!initialized) {
            this.width = w;
            this.height = h;
            for (PostProcessPass pass : passes) {
                pass.init(w, h);
            }
            initialized = true;
            if (!loggedReady) {
                loggedReady = true;
                RenderTargetManager rtm = RenderTargetManager.INSTANCE;
                LDOGMod.LOGGER.info(
                    "LDOG: Post-process pipeline ready ({} pass(es), base {}x{}, scaled {}x{} @ {}, targets={}, msaa={}, fxaa={})",
                    passes.size(), w, h,
                    rtm.getScaledWidth(), rtm.getScaledHeight(), rtm.getScale(),
                    rtm.isReady() ? "ok" : "unavailable",
                    LDOGConfig.enableMSAA ? "on (pipeline yields binding)" : "off",
                    LDOGConfig.enableFXAA ? "on (composites after pipeline)" : "off");
            }
            return;
        }

        if (this.width != w || this.height != h) {
            this.width = w;
            this.height = h;
            for (PostProcessPass pass : passes) {
                pass.resize(w, h);
            }
            LDOGMod.LOGGER.info("LDOG: Post-process pipeline resized to {}x{}", w, h);
        }
    }

    private int runPasses(PostProcessContext context) {
        int active = 0;

        // When an external (deferred) shader pack is driving the image, run ONLY
        // the scene resolve + the pack's composite chain. LDOG's own upscale /
        // temporal (FSR2/TAA) / sharpen / AA / grade passes would fight the pack
        // — in particular the per-frame jitter + temporal accumulation cause
        // heavy flicker. OptiFine has no such stack underneath a pack either.
        boolean packDrives = com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager.isDeferredActive();

        // A pass that throws is skipped THIS frame (not permanently removed —
        // removing it could drop the scene→main resolve pass and black the
        // screen forever; a transient error must not be fatal).
        for (PostProcessPass pass : passes) {
            if (packDrives) {
                // BilinearBlit resolves the (possibly scaled) scene to the main
                // FB; the composite then renders the pack on top. Force both to
                // run regardless of their own isEnabled gating; skip everything
                // else.
                boolean allow = pass instanceof ShaderPackCompositePass
                    || pass instanceof BilinearBlitPass;
                if (!allow) continue;
            } else if (!pass.isEnabled()) {
                continue;
            }

            try {
                pass.execute(context);
                active++;
            } catch (Exception e) {
                // Skip this frame only. Rate-limit the log so a persistently
                // failing pass doesn't spam, but never remove it — the chain
                // must keep its resolve pass.
                if (loggedPassError != pass) {
                    loggedPassError = pass;
                    LDOGMod.LOGGER.error("LDOG: Post-process pass '{}' threw (skipping this frame)", pass.id(), e);
                }
            }
        }

        return active;
    }

    /** Last pass we logged an execution error for (rate-limits repeat spam). */
    private PostProcessPass loggedPassError;

    /**
     * Diagnostic logs so operators can tell binding actually fired (the
     * "Pipeline render targets ready" line alone is ambiguous — targets are
     * allocated even when the mixin yields).
     */
    private void diagnoseBinding(PostProcessContext ctx) {
        PipelineDebugStats.updateBinding(ctx.bindingActive());

        if (ctx.bindingActive()) {
            framesSinceLastBind = 0;
            if (!loggedFirstBind) {
                loggedFirstBind = true;
                LDOGMod.LOGGER.info(
                    "LDOG: Pipeline binding ACTIVE — world rendered at {}x{}, blitting to {}x{}",
                    ctx.sceneWidth(), ctx.sceneHeight(), ctx.mainWidth(), ctx.mainHeight());
            }
            return;
        }

        framesSinceLastBind++;
        if (!loggedBindWatchdog && framesSinceLastBind >= BIND_WATCHDOG_FRAMES) {
            loggedBindWatchdog = true;
            String reason;
            if (hasConflictingFeatureOn()) {
                reason = "MSAA is on (pipeline correctly yields the binding slot)";
            } else if (!RenderTargetManager.INSTANCE.isReady()) {
                reason = "RenderTargetManager not ready (GL 3.0 unavailable or FBO allocation failed)";
            } else if (net.minecraft.client.Minecraft.getMinecraft().gameSettings.anaglyph) {
                reason = "anaglyph 3D is on (8c binding only runs on pass 2, which is the non-anaglyph world pass)";
            } else {
                reason = "unknown — binding guards may be misconfigured or the mixin @Redirect is not matching";
            }
            LDOGMod.LOGGER.warn(
                "LDOG: Pipeline enabled but binding has not activated after {} frames. Reason: {}",
                BIND_WATCHDOG_FRAMES, reason);
        }
    }

    private void disableAll() {
        for (PostProcessPass pass : passes) {
            try {
                pass.dispose();
            } catch (Exception ignored) {
                // Best effort cleanup.
            }
        }
        passes.clear();
        try {
            RenderTargetManager.INSTANCE.dispose();
        } catch (Exception ignored) {
            // Best effort cleanup — already logging on the fatal-error path.
        }
        initialized = false;
    }
}
