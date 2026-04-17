package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.passes.NoOpPass;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Minimal pipeline shell for Phase 8a.
 *
 * v1 goal is safe lifecycle wiring with a no-op pass and zero visual changes.
 */
public final class PostProcessPipeline {

    public static final PostProcessPipeline INSTANCE = new PostProcessPipeline();

    private final List<PostProcessPass> passes = new ArrayList<>();
    private boolean initialized;
    private int width;
    private int height;
    private boolean loggedReady;

    private PostProcessPipeline() {
        // Always keep one no-op pass to validate lifecycle/ordering.
        passes.add(new NoOpPass());
    }

    public void onWorldPassRendered(int width, int height, int pass, float partialTicks) {
        if (!LDOGConfig.enablePostProcessPipeline) return;
        if (width <= 0 || height <= 0) return;

        long t0 = System.nanoTime();

        try {
            ensureInitialized(width, height);
            PostProcessContext context = new PostProcessContext(width, height, pass, partialTicks);
            int active = runPasses(context);
            PipelineDebugStats.update(active, width, height, System.nanoTime() - t0);

            RenderTargetManager rtm = RenderTargetManager.INSTANCE;
            PipelineDebugStats.updateTargets(
                rtm.isReady(), rtm.getScale(), rtm.getScaledWidth(), rtm.getScaledHeight());
        } catch (Exception e) {
            LDOGMod.LOGGER.error("LDOG: Post-process pipeline fatal error; disabling for this session", e);
            disableAll();
        }
    }

    /**
     * True when another LDOG feature currently owns the world-pass FBO binding.
     *
     * Reserved for the Phase 9a binding hook — when the pipeline eventually
     * redirects world rendering into its scene target, it must yield to MSAA
     * (which wraps renderWorldPass with its own multisampled FBO). Kept here
     * as a single source of truth so callers don't duplicate the check.
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

