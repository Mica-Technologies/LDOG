package com.limitlessdev.ldog.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin for {@code mixins.ldog.json} (the late config).
 *
 * <h3>Why this exists</h3>
 *
 * <p>Three of LDOG's mixins hang the post-process pipeline off
 * {@code EntityRenderer.renderWorldPass}, and they do it with injection points
 * that assume vanilla's exact bytecode:
 *
 * <ul>
 *   <li>{@code MixinEntityRendererJitter} — {@code INVOKE} on
 *       {@code org.lwjgl.util.glu.Project.gluPerspective} at <em>ordinal 0</em>
 *       (sky projection) and <em>ordinal 1</em> (terrain projection).</li>
 *   <li>{@code MixinEntityRendererPostPipeline} — HEAD/RETURN injects plus a
 *       {@code @Redirect} of the {@code GlStateManager.viewport} call.</li>
 *   <li>{@code MixinEntityRendererMSAA} — HEAD/RETURN injects on the same
 *       method.</li>
 * </ul>
 *
 * <p>OptiFine rewrites {@code EntityRenderer} wholesale. Its {@code
 * renderWorldPass} has a different shape: extra {@code gluPerspective} calls
 * for its own shader/zoom handling (so the ordinals point somewhere else
 * entirely), and a rearranged viewport setup. Best case those injectors land in
 * the wrong place and silently corrupt the frame; worst case Mixin throws
 * {@code InvalidInjectionException} at apply time and takes the whole game down
 * at startup.
 *
 * <p>None of it would do anything useful anyway: LDOG's post-process pipeline
 * defers to OptiFine's shader stack under the OptiFine-interop rules
 * ({@code OptiFineCompat}), so these three mixins have no work to do when OF is
 * present. Skipping them is strictly better than applying them.
 *
 * <h3>Detection</h3>
 *
 * <p>Mixins are applied long before {@code OptiFineCompat} (or Forge's
 * {@code Loader}) can be safely touched, and this must not depend on LDOG
 * config load order. So detection is a self-contained classloader probe for the
 * same class {@code OptiFineCompat} looks for — deliberately kept in sync with
 * it. The resource lookup is tried first because it answers the question
 * without defining (and therefore transforming) an OptiFine class during the
 * transform phase; {@code Class.forName} is only the fallback.
 *
 * <p>LDOG never imports OptiFine classes — the probe is purely by name.
 */
public class LDOGMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("LDOG");

    /** Same probe target as {@code com.limitlessdev.ldog.compat.OptiFineCompat}. */
    private static final String OPTIFINE_PROBE = "optifine.OptiFineForgeTweaker";

    /**
     * Mixins that target {@code EntityRenderer.renderWorldPass} with bytecode-shape
     * -sensitive injection points. Fully-qualified, matching the {@code mixinClassName}
     * handed to {@link #shouldApplyMixin}.
     */
    private static final Set<String> ENTITY_RENDERER_PIPELINE_MIXINS = new HashSet<>(Arrays.asList(
        "com.limitlessdev.ldog.mixin.MixinEntityRendererPostPipeline",
        "com.limitlessdev.ldog.mixin.MixinEntityRendererJitter",
        "com.limitlessdev.ldog.mixin.MixinEntityRendererMSAA",
        // Slice anchored on applyBobbing/orientCamera inside renderWorldPass —
        // same bytecode-shape assumption as the three above.
        "com.limitlessdev.ldog.mixin.MixinEntityRendererNausea"
    ));

    private static Boolean optiFinePresent;
    private static boolean loggedDisable;

    /**
     * True when OptiFine is on the classpath. Computed once; the result is
     * cached because {@link #shouldApplyMixin} is called for every mixin in the
     * config.
     */
    private static boolean isOptiFinePresent() {
        if (optiFinePresent != null) return optiFinePresent;

        boolean found = false;
        ClassLoader cl = LDOGMixinPlugin.class.getClassLoader();
        try {
            if (cl != null
                && cl.getResource(OPTIFINE_PROBE.replace('.', '/') + ".class") != null) {
                found = true;
            }
        } catch (Throwable ignored) {
            // Some classloaders refuse resource lookups mid-transform; fall through.
        }
        if (!found) {
            try {
                Class.forName(OPTIFINE_PROBE, false, cl);
                found = true;
            } catch (Throwable ignored) {
                // Not present (ClassNotFoundException) or unloadable — treat as absent.
            }
        }

        optiFinePresent = found;
        return found;
    }

    @Override
    public void onLoad(String mixinPackage) {
        // Nothing to prepare — detection is lazy so an early call can't fail.
    }

    @Override
    public String getRefMapperConfig() {
        return null; // use the config's own refmap
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!ENTITY_RENDERER_PIPELINE_MIXINS.contains(mixinClassName)) return true;
        if (!isOptiFinePresent()) return true;

        if (!loggedDisable) {
            loggedDisable = true;
            LOGGER.info(
                "LDOG: OptiFine detected — skipping {} EntityRenderer pipeline mixins ({}). "
                    + "OptiFine rewrites EntityRenderer.renderWorldPass, so their injection "
                    + "points no longer match; LDOG's post-process pipeline defers to "
                    + "OptiFine's shader stack anyway.",
                ENTITY_RENDERER_PIPELINE_MIXINS.size(),
                String.join(", ", ENTITY_RENDERER_PIPELINE_MIXINS));
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No cross-config coordination needed.
    }

    @Override
    public List<String> getMixins() {
        return null; // no dynamically-added mixins
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
