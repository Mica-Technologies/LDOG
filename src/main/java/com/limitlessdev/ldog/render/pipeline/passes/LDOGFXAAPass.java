package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.FXAAQuality;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * LDOG's pipeline-native FXAA with tunable quality levels. LDOG-original
 * GLSL — concept-inspired by NVIDIA's FXAA 3.11 public spec (contrast-
 * gated edge detection + directional search for sub-pixel edge position),
 * but simplified for a self-contained implementation.
 *
 * Runs as a pipeline pass AFTER any upscaler + RCAS, so it smooths the
 * final composed world image. Ordering rationale: if RCAS runs before
 * FXAA, the sharpened edges are exactly what FXAA is designed to smooth,
 * giving a balanced result.
 *
 * Coordinates with FXAAHandler: when the pipeline is on, FXAAHandler
 * unloads MC's fixed-quality FXAA shader so only this pass runs. When
 * the pipeline is off, MC's FXAA runs as before (no quality levels,
 * but preserves existing behavior).
 */
public final class LDOGFXAAPass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    v_texCoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    private static final String FRAG_SOURCE =
        "#version 120\n" +
        "uniform sampler2D u_srcTex;\n" +
        "uniform vec2 u_invMainDim;\n" +
        "uniform int u_searchSteps;\n" +
        "uniform float u_edgeThreshold;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "float luma(vec3 c) {\n" +
        "    return dot(c, vec3(0.299, 0.587, 0.114));\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 off = u_invMainDim;\n" +
        "    vec3 rgbM = texture2D(u_srcTex, v_texCoord).rgb;\n" +
        "    vec3 rgbN = texture2D(u_srcTex, v_texCoord + vec2(0.0, -off.y)).rgb;\n" +
        "    vec3 rgbS = texture2D(u_srcTex, v_texCoord + vec2(0.0,  off.y)).rgb;\n" +
        "    vec3 rgbE = texture2D(u_srcTex, v_texCoord + vec2( off.x, 0.0)).rgb;\n" +
        "    vec3 rgbW = texture2D(u_srcTex, v_texCoord + vec2(-off.x, 0.0)).rgb;\n" +
        "\n" +
        "    float lumaM = luma(rgbM);\n" +
        "    float lumaN = luma(rgbN);\n" +
        "    float lumaS = luma(rgbS);\n" +
        "    float lumaE = luma(rgbE);\n" +
        "    float lumaW = luma(rgbW);\n" +
        "    float lumaMax = max(max(lumaN, lumaS), max(lumaE, lumaW));\n" +
        "    float lumaMin = min(min(lumaN, lumaS), min(lumaE, lumaW));\n" +
        "    float lumaRange = max(lumaM, lumaMax) - min(lumaM, lumaMin);\n" +
        "\n" +
        "    // Early-out: low-contrast pixels are not on edges.\n" +
        "    if (lumaRange < u_edgeThreshold) {\n" +
        "        gl_FragColor = vec4(rgbM, 1.0);\n" +
        "        return;\n" +
        "    }\n" +
        "\n" +
        "    // Edge orientation: horizontal edge has strong vertical luma 2nd derivative.\n" +
        "    float vertGrad = abs(lumaN - 2.0 * lumaM + lumaS);\n" +
        "    float horiGrad = abs(lumaE - 2.0 * lumaM + lumaW);\n" +
        "    bool isHorizontal = vertGrad >= horiGrad;\n" +
        "\n" +
        "    // Walk PERPENDICULAR to the edge to find which side the edge crosses.\n" +
        "    vec2 edgeStep = isHorizontal ? vec2(off.x, 0.0) : vec2(0.0, off.y);\n" +
        "    vec2 perpStep = isHorizontal ? vec2(0.0, off.y) : vec2(off.x, 0.0);\n" +
        "    float lumaSideP = isHorizontal ? lumaS : lumaE;\n" +
        "    float lumaSideN = isHorizontal ? lumaN : lumaW;\n" +
        "    float gradSideP = abs(lumaSideP - lumaM);\n" +
        "    float gradSideN = abs(lumaSideN - lumaM);\n" +
        "    bool pickPos = gradSideP >= gradSideN;\n" +
        "    vec2 blendOffset = pickPos ? perpStep : -perpStep;\n" +
        "    float lumaAvg = 0.5 * (lumaM + (pickPos ? lumaSideP : lumaSideN));\n" +
        "    float lumaDelta = abs(lumaAvg - lumaM);\n" +
        "\n" +
        "    // Search ALONG the edge to find how far the edge extends in each direction.\n" +
        "    // More iterations (= higher quality level) = better sub-pixel blend estimate.\n" +
        "    vec2 searchStart = v_texCoord + blendOffset * 0.5;\n" +
        "    float distP = 0.0;\n" +
        "    float distN = 0.0;\n" +
        "    bool doneP = false;\n" +
        "    bool doneN = false;\n" +
        "\n" +
        "    for (int i = 1; i <= 32; i++) {\n" +
        "        if (i > u_searchSteps) break;\n" +
        "        float fi = float(i);\n" +
        "        if (!doneP) {\n" +
        "            float lP = luma(texture2D(u_srcTex, searchStart + edgeStep * fi).rgb);\n" +
        "            if (abs(lP - lumaAvg) > lumaDelta * 1.5) {\n" +
        "                distP = fi;\n" +
        "                doneP = true;\n" +
        "            }\n" +
        "        }\n" +
        "        if (!doneN) {\n" +
        "            float lN = luma(texture2D(u_srcTex, searchStart - edgeStep * fi).rgb);\n" +
        "            if (abs(lN - lumaAvg) > lumaDelta * 1.5) {\n" +
        "                distN = fi;\n" +
        "                doneN = true;\n" +
        "            }\n" +
        "        }\n" +
        "        if (doneP && doneN) break;\n" +
        "    }\n" +
        "\n" +
        "    // Fall back to max distance if we didn't find an endpoint within the step budget.\n" +
        "    if (!doneP) distP = float(u_searchSteps);\n" +
        "    if (!doneN) distN = float(u_searchSteps);\n" +
        "\n" +
        "    // Blend amount based on shortest distance to edge end — pixels near the\n" +
        "    // middle of a long edge blend more; pixels near the end blend less.\n" +
        "    float edgeLen = distP + distN;\n" +
        "    float minDist = min(distP, distN);\n" +
        "    float blend = max(0.0, 0.5 - minDist / edgeLen);\n" +
        "\n" +
        "    gl_FragColor = vec4(texture2D(u_srcTex, v_texCoord + blendOffset * blend).rgb, 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private int sourceTex;
    private int sourceTexWidth;
    private int sourceTexHeight;
    private boolean loggedFirstExecute;
    private FXAAQuality lastLoggedQuality;

    @Override
    public String id() {
        return "ldog_fxaa";
    }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_fxaa", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: FXAA shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: FXAA shader compile failed; pass will no-op", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // source texture reallocated lazily in execute on dim change.
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (shaderFailed || shader == null) return;
        if (!LDOGConfig.enableFXAA) return;

        int w = ctx.mainWidth();
        int h = ctx.mainHeight();
        if (w <= 0 || h <= 0) return;

        ensureSourceTexture(w, h);
        if (sourceTex == 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        // Copy current main-FB into our source texture so the fragment
        // shader can sample it (can't sample what you're rendering to).
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
        GlStateManager.viewport(0, 0, w, h);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);

        FXAAQuality quality = FXAAQuality.selected();

        shader.bind();
        shader.setUniform1i("u_srcTex", 0);
        shader.setUniform2f("u_invMainDim", 1.0f / w, 1.0f / h);
        shader.setUniform1i("u_searchSteps", quality.searchSteps());
        shader.setUniform1f("u_edgeThreshold", quality.edgeThreshold());

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();

        ShaderProgram.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glPopAttrib();

        if (!loggedFirstExecute || lastLoggedQuality != quality) {
            loggedFirstExecute = true;
            lastLoggedQuality = quality;
            LDOGMod.LOGGER.info("LDOG: FXAA pass live at {}x{} — quality '{}' ({} steps, threshold {})",
                w, h, quality.displayName(), quality.searchSteps(), quality.edgeThreshold());
        }
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (sourceTex != 0) {
            GL11.glDeleteTextures(sourceTex);
            sourceTex = 0;
            sourceTexWidth = 0;
            sourceTexHeight = 0;
        }
    }

    @Override
    public boolean isEnabled() {
        // Gate inline in execute() so we can cheaply skip when FXAA is off.
        return true;
    }

    private void ensureSourceTexture(int w, int h) {
        if (sourceTex != 0 && sourceTexWidth == w && sourceTexHeight == h) return;

        if (sourceTex != 0) {
            GL11.glDeleteTextures(sourceTex);
            sourceTex = 0;
        }

        sourceTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        sourceTexWidth = w;
        sourceTexHeight = h;
    }
}
