package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.passes.BilinearBlitPass;
import com.limitlessdev.ldog.render.pipeline.passes.FSR1EASUPass;
import com.limitlessdev.ldog.render.pipeline.passes.FSR1QualityPass;

import java.util.ArrayList;
import java.util.Iterator;
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
        // All upscalers are always registered; each pass's isEnabled() checks
        // the selected algorithm so exactly one runs per frame. New upscalers
        // (NIS, FSR2, etc.) plug in here alongside the existing ones.
        passes.add(new BilinearBlitPass());
        passes.add(new FSR1EASUPass());
        passes.add(new FSR1QualityPass());
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

    private void ensureInitialized(int w, int h) throws Exception {
        float scale = (float) LDOGConfig.internalRenderScale;
        RenderTargetManager.INSTANCE.ensure(w, h, scale);

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

        // Disable passes that throw so one bad pass cannot crash rendering.
        Iterator<PostProcessPass> it = passes.iterator();
        while (it.hasNext()) {
            PostProcessPass pass = it.next();
            if (!pass.isEnabled()) continue;

            try {
                pass.execute(context);
                active++;
            } catch (Exception e) {
                LDOGMod.LOGGER.error("LDOG: Disabling post-process pass '{}' after execution failure", pass.id(), e);
                pass.dispose();
                it.remove();
            }
        }

        return active;
    }

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
