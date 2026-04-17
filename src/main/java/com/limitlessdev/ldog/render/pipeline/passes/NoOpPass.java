package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;

/**
 * Baseline pass used to validate lifecycle/order wiring.
 */
public final class NoOpPass implements PostProcessPass {

    @Override
    public String id() {
        return "noop";
    }

    @Override
    public void init(int width, int height) {
        // Intentionally empty.
    }

    @Override
    public void resize(int width, int height) {
        // Intentionally empty.
    }

    @Override
    public void execute(PostProcessContext context) {
        // Intentionally empty.
    }

    @Override
    public void dispose() {
        // Intentionally empty.
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

