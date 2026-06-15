package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import org.lwjgl.opengl.GL11;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage-boundary GL error drain + one-shot localizer for the LDOG render
 * pipeline.
 *
 * <p>MC calls {@code glGetError} once at the end of the frame ("@ Post render")
 * and spams the log for every error it finds. A single GL error left uncleared
 * by any per-frame pipeline stage therefore becomes a per-frame flood. This
 * helper is dropped at each stage boundary in frame order: it drains the error
 * queue (so the error never reaches MC's pedantic post-render check) and logs
 * the offending stage <em>once per (stage, error-code)</em>, so the flood stops
 * immediately AND the first stage to report tells us exactly where the error is
 * produced — because the previous boundary's drain cleared everything before it.
 *
 * <p>Draining is legitimate here: a legacy-GL (2.1) compatibility pipeline mixed
 * with pack GLSL inevitably trips benign driver errors that MC's per-call
 * checker would otherwise surface as noise. We keep the diagnostic (log-once)
 * so a genuine error is never silently swallowed — it's recorded, just not
 * repeated 165×/second.
 *
 * <p>Cost is one {@code glGetError} per boundary (a handful per frame); the same
 * order of magnitude MC itself spends. Always on — no config gate — so the log
 * stays clean for every user without a toggle to discover.
 */
public final class PipelineGlProbe {

    private PipelineGlProbe() {}

    /** (stage,code) pairs already reported, so each is logged exactly once. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** Safety cap so a runaway error state can't spin here forever. */
    private static final int MAX_DRAIN = 32;

    /**
     * Drain any pending GL errors, attributing them to {@code stage}. Logs each
     * distinct (stage, error) once. Returns true if at least one error was drained.
     */
    public static boolean drain(String stage) {
        boolean any = false;
        for (int i = 0; i < MAX_DRAIN; i++) {
            int err = GL11.glGetError();
            if (err == GL11.GL_NO_ERROR) break;
            any = true;
            String key = stage + ":" + err;
            if (REPORTED.add(key)) {
                LDOGMod.LOGGER.error(
                    "LDOG GL probe: error 0x{} ({}) localized to stage '{}' — drained so it "
                    + "won't flood MC's post-render check. Logged once.",
                    Integer.toHexString(err), name(err), stage);
            }
        }
        return any;
    }

    private static String name(int err) {
        switch (err) {
            case GL11.GL_INVALID_ENUM:      return "GL_INVALID_ENUM";
            case GL11.GL_INVALID_VALUE:     return "GL_INVALID_VALUE";
            case GL11.GL_INVALID_OPERATION: return "GL_INVALID_OPERATION";
            case GL11.GL_STACK_OVERFLOW:    return "GL_STACK_OVERFLOW";
            case GL11.GL_STACK_UNDERFLOW:   return "GL_STACK_UNDERFLOW";
            case GL11.GL_OUT_OF_MEMORY:     return "GL_OUT_OF_MEMORY";
            case 0x0506:                    return "GL_INVALID_FRAMEBUFFER_OPERATION";
            default:                        return "0x" + Integer.toHexString(err);
        }
    }
}
