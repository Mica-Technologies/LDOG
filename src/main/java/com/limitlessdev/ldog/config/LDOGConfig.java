package com.limitlessdev.ldog.config;

import com.limitlessdev.ldog.Tags;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Tags.MODID)
public class LDOGConfig {

    // ---- Global Preset ----

    @Config.Comment({
        "LDOG config preset: custom, vanilla, performance, default, fancy, or ultra.",
        "Picking a preset overwrites all major visual + perf toggles at once.",
        "Font settings, tint colors, FPS limits, and borderless-fullscreen are",
        "preserved across preset changes since they're user-specific."
    })
    public static String globalPreset = "custom";

    // ---- Future Features (not yet implemented) ----

    @Config.Comment({
        "Enable connected textures (CTM).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableConnectedTextures = true;

    @Config.Comment({
        "Enable emissive texture rendering.",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableEmissiveTextures = true;

    @Config.Comment({
        "Enable dynamic lights (light from held items).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableDynamicLights = true;

    @Config.Comment("Update interval for dynamic lights. 0 = Smooth (every frame, best quality), 1 = every tick, 2+ = every N ticks (better performance).")
    @Config.RangeInt(min = 0, max = 20)
    public static int dynamicLightsUpdateInterval = 1;

    @Config.Comment("Enable lighting customization (color temperature, brightness, night darkness, HDR).")
    public static boolean enableLightTemperature = false;

    @Config.Comment("Block light (torches/lamps) red multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double blockLightRed = 1.25;

    @Config.Comment("Block light green multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double blockLightGreen = 1.0;

    @Config.Comment("Block light blue multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double blockLightBlue = 0.7;

    @Config.Comment("Sky light (sun/moon) red multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double skyLightRed = 0.8;

    @Config.Comment("Sky light green multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double skyLightGreen = 0.88;

    @Config.Comment("Sky light blue multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double skyLightBlue = 1.25;

    @Config.Comment("Overall brightness boost. -1.0 = very dark, 0.0 = vanilla, 1.0 = very bright.")
    @Config.RangeDouble(min = -1.0, max = 1.0)
    public static double lightBrightnessBoost = 0.0;

    @Config.Comment("Night darkness multiplier. 1.0 = vanilla, >1.0 = darker nights, 100.0 = pitch black at night.")
    @Config.RangeDouble(min = 0.5, max = 100.0)
    public static double nightDarkness = 1.0;

    @Config.Comment("Enable pseudo-HDR tonemapping (ACES filmic curve for cinematic contrast).")
    public static boolean enableHDR = true;

    @Config.Comment({
        "Better Grass rendering mode.",
        "off = vanilla, fast = always show grass top on sides, fancy = only when neighbor below is also grass."
    })
    public static String betterGrass = "fancy";

    @Config.Comment("Enable Better Snow (snow-textured sides on blocks with snow layers on top).")
    public static boolean enableBetterSnow = true;

    @Config.Comment("Enable natural textures (random rotation/flip of block textures to reduce visual repetition).")
    public static boolean enableNaturalTextures = true;

    @Config.Comment("Enable custom colors from resource packs (custom grass/foliage colormaps, redstone wire colors, etc.).")
    public static boolean enableCustomColors = true;

    @Config.Comment("Enable random entity textures from resource packs (multiple texture variants per mob type).")
    public static boolean enableRandomEntityTextures = true;

    @Config.Comment("Show performance metrics overlay in the upper-left corner (FPS, frame time, memory, culling stats).")
    public static boolean enablePerformanceOverlay = false;

    // ---- Visual: Anti-aliasing / Texture filtering ----

    @Config.Comment("Enable anisotropic filtering (sharpens textures viewed at glancing angles).")
    public static boolean enableAnisotropicFiltering = false;

    @Config.Comment("Anisotropic filtering level. Clamped to the GPU's reported maximum if higher.")
    @Config.RangeInt(min = 2, max = 16)
    public static int anisotropicLevel = 4;

    @Config.Comment({
        "Extended-border mipmaps: pads each atlas sprite with an edge-extended halo so",
        "anisotropic sampling at grazing angles cannot bleed into neighboring sprites.",
        "Fixes the faint block-edge lines visible at distance when AF is on.",
        "Grows atlas size (~3x for 16x packs) — enable only alongside Anisotropic Filtering.",
        "Changes take effect on next resource reload (auto-triggered on save)."
    })
    public static boolean enableExtendedBorderMipmaps = false;

    // ---- Font Rendering (Phase C3: Smooth Font absorption) ----

    @Config.Comment({
        "Enable LDOG's smooth font rendering (replaces Smooth Font mod functionality).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableSmoothFont = true;

    @Config.Comment({
        "Use HD ASCII font PNG from resource pack if available.",
        "Checks optifine/font/ascii.png and mcpatcher/font/ascii.png.",
        "Falls back to vanilla textures/font/ascii.png when not present.",
        "Ignored when useTTFFont is on (TTF takes priority)."
    })
    public static boolean useHDFontTexture = true;

    @Config.Comment({
        "Runtime TTF rasterization — rasterize glyphs from a system TrueType",
        "font at startup using Java AWT, similar to Smooth Font's default mode.",
        "Produces pixel-perfect antialiased glyphs at the chosen cell size,",
        "which is crisper than any filter applied to a pre-rasterized atlas.",
        "Overrides useHDFontTexture when enabled (TTF is the active atlas)."
    })
    public static boolean useTTFFont = false;

    @Config.Comment({
        "TTF font family name. Logical names (SansSerif, Serif, Monospaced,",
        "Dialog, DialogInput) are guaranteed resolvable; OS-specific names",
        "(Arial, Segoe UI, etc.) fall back to the system default if missing."
    })
    public static String ttfFontFamily = "SansSerif";

    @Config.Comment("Request a bold face from the TTF font.")
    public static boolean ttfBold = false;

    @Config.Comment("Request an italic face from the TTF font.")
    public static boolean ttfItalic = false;

    @Config.Comment({
        "AWT point size for TTF rasterization. Roughly 70-80% of ttfCellSize",
        "gives good results — larger values risk clipping ascenders/descenders,",
        "smaller values leave empty padding inside each cell."
    })
    @Config.RangeInt(min = 8, max = 96)
    public static int ttfFontSize = 24;

    @Config.Comment({
        "Pixel size of each cell in the 16x16 glyph atlas. Full atlas is",
        "16 * ttfCellSize on a side. Larger = sharper base level (mipmaps",
        "handle downsampling for smaller GUI scales). 32 is a good default;",
        "64 looks slightly crisper on 4K displays at the cost of ~4x memory."
    })
    @Config.RangeInt(min = 8, max = 128)
    public static int ttfCellSize = 32;

    @Config.Comment({
        "Font antialiasing level. Applies when an HD font texture is active.",
        "  off       — GL_NEAREST (blocky, vanilla look).",
        "  bilinear  — GL_LINEAR on the base level (smooth at 1:1, but aliased at",
        "              GUI scale because the HD atlas has no mip chain).",
        "  trilinear — GL_LINEAR_MIPMAP_LINEAR with generated mipmaps. Smoothest:",
        "              the GPU picks a pre-downsampled level matching the screen",
        "              footprint and trilinearly blends between levels.",
        "Changes take effect on next resource reload."
    })
    public static String fontAntialiasing = "trilinear";

    @Config.Comment({
        "Mipmap LOD bias for the HD font texture when trilinear AA is on.",
        "Negative values shift sampling toward sharper (higher-detail) levels,",
        "recovering crispness that box-filter mipmap generation loses at heavy",
        "downsampling. Typical range: -1.0 (sharp) to 0.0 (GPU default).",
        "Ignored in off/bilinear modes."
    })
    @Config.RangeDouble(min = -4.0, max = 4.0)
    public static double fontLodBias = -0.5;

    @Config.Comment({
        "Anisotropic sampling level on the HD font texture when trilinear AA",
        "is on. Takes more source texels per screen pixel along the axis of",
        "maximum change, which recovers sub-pixel edge detail that trilinear",
        "loses to mipmap blurring. 1 = off. Clamped to the GPU's max."
    })
    @Config.RangeInt(min = 1, max = 16)
    public static int fontAnisotropic = 16;

    @Config.Comment({
        "Render drop shadows behind text. Disable to flatten MC's default",
        "1-pixel offset dark copy that's drawn behind most UI strings.",
        "Flips live without a resource reload."
    })
    public static boolean fontDropShadows = true;

    @Config.Comment({
        "Override per-glyph widths from a pack-provided ascii.properties file.",
        "Checked paths: optifine/font/ascii.properties, mcpatcher/font/ascii.properties,",
        "font/ascii.properties. When absent, widths are computed from the PNG as vanilla does."
    })
    public static boolean useFontPropertyWidths = true;

    @Config.Comment({
        "Enable multi-sample anti-aliasing (MSAA) for world geometry.",
        "Requires a GPU that supports multisampled framebuffers."
    })
    public static boolean enableMSAA = false;

    @Config.Comment("MSAA sample count. Clamped to the GPU's reported maximum if higher.")
    @Config.RangeInt(min = 2, max = 8)
    public static int msaaSamples = 4;

    @Config.Comment({
        "Enable FXAA post-process anti-aliasing.",
        "Smooths alpha-test edges (leaves, fences, grass) that MSAA can't touch.",
        "Uses MC's built-in FXAA shader pass."
    })
    public static boolean enableFXAA = false;

    @Config.Comment({
        "Enable Phase 8a post-process pipeline scaffold.",
        "Experimental: currently lifecycle wiring + no-op pass only.",
        "Does not change rendered output yet."
    })
    public static boolean enablePostProcessPipeline = false;

    @Config.Comment({
        "Internal render scale for the post-process pipeline.",
        "1.0 = native resolution; <1.0 renders the world smaller for future upscaling (FSR1, Phase 9a).",
        "Experimental — only consumed when enablePostProcessPipeline is true."
    })
    @Config.RangeDouble(min = 0.5, max = 1.0)
    public static double internalRenderScale = 1.0;

    @Config.Comment({
        "Upscaling algorithm used to resolve the scaled scene target back to native resolution.",
        "bilinear = plain GL_LINEAR blit (cheap, blurry at low scales — Phase 9a.1 baseline).",
        "fsr1     = FSR1-style edge-adaptive spatial upsampling (sharper, Phase 9a.2).",
        "Only consumed when enablePostProcessPipeline is true and internalRenderScale < 1.0."
    })
    public static String upscalerAlgorithm = "bilinear";

    @Config.Comment({
        "FSR1 sharpening strength. 0.0 = no sharpening (pure bilinear behavior),",
        "1.0 = moderate, 1.5 = aggressive (default), 2.0 = very aggressive (may show halos).",
        "Only consumed when upscalerAlgorithm=fsr1."
    })
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double fsr1Sharpness = 1.5;

    @Config.Comment({
        "Active upscaler preset name: native, ultra, quality, balanced, performance, or custom.",
        "Choosing a named preset overwrites internalRenderScale + upscalerAlgorithm + fsr1Sharpness.",
        "Editing any of those three directly flips this back to 'custom'."
    })
    public static String upscalerPreset = "custom";

    @Config.Comment({
        "Enable RCAS-style post-upscale sharpening. Runs AFTER the upscaler pass",
        "and operates at native resolution, so it works even at render scale 1.0",
        "(pure sharpening, no upscaling). Complements the upscaler rather than",
        "replacing its internal sharpen."
    })
    public static boolean enableRcasSharpen = false;

    @Config.Comment({
        "RCAS post-upscale sharpening strength. 0.0 = no effect, 1.0 = maximum.",
        "Typical sweet spot is 0.3 — 0.6. Values above 0.8 start to show halos.",
        "Only consumed when enableRcasSharpen is true."
    })
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public static double rcasSharpness = 0.4;

    @Config.Comment({
        "FXAA quality level when the post-process pipeline is active.",
        "  low      = 4 search steps, threshold 0.200 — cheapest, coarse.",
        "  medium   = 6 steps, threshold 0.125.",
        "  high     = 8 steps, threshold 0.100 (default).",
        "  ultra    = 12 steps, threshold 0.080.",
        "  extreme  = 24 steps, threshold 0.063 — most refined, most samples.",
        "When the pipeline is OFF, MC's fixed-quality FXAA is used regardless",
        "of this setting (it's only controlled by enableFXAA)."
    })
    public static String fxaaQuality = "high";

    @Config.Comment({
        "Enable temporal anti-aliasing (TAA) — Phase 9c.1 MVP.",
        "Applies sub-pixel jitter to the projection matrix and blends each",
        "frame's render with the previous frame's output for smoother edges",
        "and accumulated sub-pixel detail on static scenes.",
        "",
        "Expected behavior: static scenes get cleaner edges than FXAA alone.",
        "Moving the camera produces visible ghosting — next stage (9c.2) adds",
        "motion vectors to fix that. Use neighborhood color clamping to keep",
        "ghosting manageable in the meantime.",
        "",
        "Requires the post-process pipeline to be ON."
    })
    public static boolean enableTAA = false;

    @Config.Comment({
        "TAA history blend weight. Higher = more temporal smoothing but more",
        "ghosting on motion. 0.0 = no history (identity, TAA does nothing),",
        "0.9 = default, 0.95 = maximum smoothing."
    })
    @Config.RangeDouble(min = 0.0, max = 0.95)
    public static double taaHistoryWeight = 0.9;

    @Config.Comment({
        "Borderless windowed fullscreen: replaces exclusive fullscreen with an",
        "undecorated window sized to the desktop. Enables instant alt-tab,",
        "functional external overlays, and multi-monitor cursor movement.",
        "",
        "Trade-off: the game window has NO decorations in windowed mode either",
        "(no title bar, no min/max/close buttons, no resize grips). Drag via",
        "Alt+drag on Windows or move the window by keyboard shortcut.",
        "",
        "REQUIRES RESTART to take effect — LWJGL only reads the undecorated",
        "flag at Display creation time."
    })
    @Config.RequiresMcRestart
    public static boolean borderlessFullscreen = false;

    @Config.Comment({
        "Block Windows Fullscreen Optimizations when in borderless mode.",
        "ON  = window is 1 pixel shorter than the desktop. Windows doesn't",
        "      detect it as fullscreen, so toggling is flicker-free — but the",
        "      taskbar stays visible at the bottom of the screen.",
        "OFF = window matches the desktop exactly. Windows auto-hides the",
        "      taskbar (clean look) but shows a brief desktop flash during",
        "      the DWM compositor transition each time you toggle fullscreen.",
        "",
        "Only consumed when borderlessFullscreen is true. Live-toggleable."
    })
    public static boolean blockFullscreenOptimizations = true;

    @Config.Comment({
        "Enable shader support (stretch goal).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableShaders = false;

    @Config.Comment({
        "Enable custom sky rendering.",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableCustomSky = true;

    @Config.Comment({
        "Enable HD texture support (textures larger than 16x16).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableHDTextures = true;

    // ---- Performance: Rendering Optimizations ----

    @Config.Comment("Enable chunk rendering optimizations. Runs even alongside OptiFine.")
    public static boolean enableRenderOptimizations = true;

    @Config.Comment("Maximum distance (in blocks) at which entities are rendered. 0 = use vanilla behavior.")
    @Config.RangeInt(min = 0, max = 512)
    public static int entityRenderDistance = 64;

    @Config.Comment("Maximum distance (in blocks) at which tile entity special renderers (TESRs) run. 0 = use vanilla behavior.")
    @Config.RangeInt(min = 0, max = 512)
    public static int tileEntityRenderDistance = 64;

    @Config.Comment("Enable frustum culling for particles (skip rendering off-screen particles).")
    public static boolean enableParticleCulling = true;

    @Config.Comment({
        "Enable entity LOD (Level of Detail).",
        "Distant entities render less frequently to save GPU work.",
        "64-128 blocks: every other frame. 128+: every 4th frame."
    })
    public static boolean enableEntityLOD = true;

    // ---- Performance: FPS Management ----

    @Config.Comment("Enable FPS reduction when the game window is unfocused or player is AFK.")
    public static boolean enableFpsReducer = true;

    @Config.Comment("Maximum FPS when the game window is unfocused.")
    @Config.RangeInt(min = 1, max = 60)
    public static int unfocusedFpsLimit = 5;

    @Config.Comment("Seconds of inactivity before AFK mode activates. 0 = disable AFK detection.")
    @Config.RangeInt(min = 0, max = 3600)
    public static int afkTimeoutSeconds = 300;

    @Config.Comment("Maximum FPS during AFK mode.")
    @Config.RangeInt(min = 1, max = 60)
    public static int afkFpsLimit = 15;

    // ---- Visual: Water ----

    @Config.Comment("Enable clear water rendering (removes murky underwater overlay).")
    public static boolean enableClearWater = true;

    @Config.Comment("Water opacity multiplier. 0.0 = fully transparent, 1.0 = vanilla opacity, >1.0 = murkier than vanilla (quadratic scale).")
    @Config.RangeDouble(min = 0.0, max = 10.0)
    public static double waterOpacity = 0.4;

    @Config.Comment({
        "Enable custom water color tinting.",
        "When enabled, the RGB values below are multiplied with the biome water color."
    })
    public static boolean enableWaterTint = false;

    @Config.Comment("Water red channel multiplier. 1.0 = unchanged, lower = less red.")
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double waterTintRed = 0.7;

    @Config.Comment("Water green channel multiplier. 1.0 = unchanged, higher = more green.")
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double waterTintGreen = 0.85;

    @Config.Comment("Water blue channel multiplier. 1.0 = unchanged, lower = less saturated blue.")
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double waterTintBlue = 0.85;

    @Mod.EventBusSubscriber(modid = Tags.MODID)
    private static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(Tags.MODID)) {
                ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
