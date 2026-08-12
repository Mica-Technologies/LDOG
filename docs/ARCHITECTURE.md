# LDOG Architecture

A map of the actual code for contributors -- not a roadmap. See the root `README.md` for feature/status and `docs/CONVENTIONS.md` for naming/style conventions.

## Module Overview

LDOG is organized into feature modules, each independently toggleable via `LDOGConfig`. All rendering features are client-side only; the mod loads (and no-ops) on a dedicated server.

```
com.limitlessdev.ldog/
+-- LDOGMod.java              # @Mod entry, lifecycle delegates to proxy
+-- LDOGMixinLoader.java       # ILateMixinLoader: registers mixins.ldog.json + mixins.ldog.early.json
+-- config/
|   +-- LDOGConfig.java        # Forge @Config, all feature toggles + tunables
|   +-- LDOGPreset.java        # Whole-feature-set presets (vanilla/performance/default/fancy/ultra)
+-- proxy/
|   +-- CommonProxy.java       # Server-safe initialization
|   +-- ClientProxy.java       # Client-only feature registration (reload listeners, tick handlers)
+-- compat/                    # OptiFine detection + per-feature override modes
|   +-- OptiFineCompat.java, OFFeature.java, OFOverrideMode.java, OFConfigBridge.java
+-- asm/
|   +-- LDOGCorePlugin.java    # IFMLLoadingPlugin + IEarlyMixinLoader (see "Mixin Loading" below)
|   +-- BorderlessFullscreenConfig.java  # Raw .cfg read, needed before ConfigManager exists
+-- gui/                       # Tabbed in-game settings GUI, shader pack pickers
+-- texture/                   # HD textures, anisotropic filtering, extended-border mipmaps
+-- mixin/                     # ~150 Mixin classes (all configs; see "Mixin Loading")
+-- render/                    # Feature implementations, one package per feature
    +-- pipeline/               # Post-process pipeline: render targets, upscalers, TAA, HDR/bloom
    +-- shaderpack/              # OptiFine-format shader pack loading + gbuffer/shadow/composite dispatch
    +-- ctm/                     # Connected textures
    +-- emissive/                # Emissive texture overlays
    +-- dynamiclights/           # Dynamic light sources
    +-- sky/                     # Custom sky layers
    +-- bettergrass/             # Better Grass / Better Snow
    +-- biome/                   # Biome color blend radius
    +-- color/                   # Custom colors (grass/foliage maps, redstone, potions, dyes)
    +-- display/                 # Borderless windowed fullscreen
    +-- font/                    # Smooth/HD/TTF font rendering
    +-- fxaa/, msaa/             # Anti-aliasing
    +-- natural/                 # Natural (rotated/flipped) textures
    +-- particles/               # Particle filters + spawn caps
    +-- randommobs/              # Random entity texture variants
    +-- (top-level render/)      # FPS reducer, HUD hide, performance overlay, tooltips, clear water
```

## Bootstrap & Mixin Loading

Mixins are split across **three configs**, loaded through two different MixinBooter mechanisms because vanilla classes are pulled into the classloader at different points during FML startup:

| Config | Loader | Registered from | Covers classes loaded... |
|---|---|---|---|
| `mixins.ldog.vanilla.json` | `IEarlyMixinLoader` | `LDOGCorePlugin` (an `IFMLLoadingPlugin`, `asm/LDOGCorePlugin.java`) | During the FML core-plugin phase, before most bootstrap -- `Minecraft`, `FontRenderer`, `GuiIngame`, `EnumDyeColor`, `Potion` |
| `mixins.ldog.json` | `ILateMixinLoader` | `LDOGMixinLoader` | After mod init -- renderers, GUI screens, `RenderChunk`, `RenderGlobal`, particle/biome/entity-renderer hooks |
| `mixins.ldog.early.json` | `ILateMixinLoader` (same loader, separate config) | `LDOGMixinLoader` | Slightly earlier than the above but still post-init -- `BlockFluidRenderer`, `TextureAtlasSprite`, `TextureMap`, `Stitcher` |

`Minecraft` **is** a valid mixin target (`MixinMinecraftBorderless` in `mixins.ldog.vanilla.json` redirects its fullscreen toggle for borderless-windowed mode) -- it just has to go through the early config, not the late one. `World`, `Block`, and `BlockLiquid` are never targeted directly; `World` state is reached via `ChunkCache` (loaded later) instead, and `Block`/`Item` hooks go through Forge events.

`LDOGCorePlugin.injectData()` also does one early, non-mixin thing: it reads the borderless-fullscreen flag straight out of the LDOG `.cfg` file (via `BorderlessFullscreenConfig`, since `ConfigManager` doesn't exist yet at this phase) and sets LWJGL's `Window.undecorated` system property before `Display.create()` runs. That's why toggling borderless fullscreen requires a restart.

## Config System

`config/LDOGConfig.java` is a single Forge `@Config`-annotated class holding every feature toggle and tunable, grouped by section comment (Global Preset, Visual/AA, Font, HDR pipeline, Performance, Particles, Vignette, Atmosphere, Comfort, HUD overlays, FPS Management, Water, OptiFine Interop, etc.). `config/LDOGPreset.java` defines whole-feature-set bundles (`vanilla`, `performance`, `default`, `fancy`, `ultra`) that overwrite the major visual/perf toggles at once; font settings, tint colors, FPS limits, and borderless-fullscreen are excluded from presets since they're user-specific.

## Feature Module Pattern

Most `render/` packages follow the same shape:

1. **Config toggle** in `LDOGConfig` (e.g. `enableConnectedTextures`).
2. **Registry/Manager** (`@Mod.EventBusSubscriber` singleton) that discovers resource-pack assets (`TextureStitchEvent`, `ModelBakeEvent`) and holds runtime state.
3. **Mixin(s)** that hook into vanilla rendering, gated by the config check.
4. **Registration** in `ClientProxy.preInit/init/postInit()` for anything that needs explicit wiring (reload listeners, tick handlers).

Example flow for Connected Textures: `LDOGConfig.enableConnectedTextures` gates `CTMRegistry`, which scans `mcpatcher/ctm/` and `optifine/ctm/` resource-pack paths at `TextureStitchEvent.Pre`, registers tile sprites, then at `ModelBakeEvent` wraps matching block models in `CTMBakedModel`. `CTMLogic` checks adjacent blocks each `getQuads()` call and selects the connected-texture variant; `CTMSprite` handles the non-standard (outside `textures/`) tile paths OptiFine packs use.

The resource-pack-driven visual features (`ctm`, `emissive`, `sky`, `bettergrass`, `natural`, `randommobs`, `color`, `biome`) all read OptiFine's `.properties` conventions under `assets/minecraft/optifine/...` (with `mcpatcher/...` as a fallback path for older packs), so existing OptiFine-compatible resource packs work with LDOG largely unmodified. Coverage is partial for several of these -- each module's class doc notes what subset of the format it currently parses.

## Rendering Optimizations

Distance/LOD culling and load-shedding live as small, mostly independent handlers rather than one module: entity/TESR distance culling and entity LOD are mixin-gated in `mixin/`, particle culling/filtering is `render/particles/` (`ParticleSpawnCounter`, `ParticleTypeFilter`) plus `MixinParticleManager(Filter)`, and `render/FpsReducerHandler.java` throttles frame rate when unfocused/AFK. Fog distance, weather density, and biome-blend radius (`render/biome/BiomeBlend.java` -- extends vanilla's hardcoded 3x3 smoothing to 5x5/7x7) are config-driven tweaks applied via their respective mixins (`MixinEntityRendererFog`, `MixinEntityRendererWeather`, `MixinBiomeColorHelper`).

## Texture Pipeline

`texture/` holds atlas-level features independent of any one block-rendering feature:

- **`HDTextureHandler`** -- logs atlas size after stitching; the actual "textures larger than 16x16" support falls out of Minecraft's own atlas code once the atlas isn't artificially constrained.
- **`AnisotropicFilteringHandler`** -- applies `GL_TEXTURE_MAX_ANISOTROPY_EXT` to stitched atlases, clamped to the GPU's reported maximum.
- **`ExtendedBorderHandler`** -- OptiFine-style extended-border mipmaps: pads each sprite with an edge-extended halo (via `Stitcher.Holder`/`TextureMap` mixins) wide enough that anisotropic sampling can't bleed into neighboring sprites at the deepest mip level. Only useful alongside AF; grows atlas size (~3x for 16px packs).

## Post-Process Pipeline (`render/pipeline/`)

`PostProcessPipeline` (singleton `INSTANCE`) owns an ordered list of `PostProcessPass` implementations (`render/pipeline/passes/`) and runs them once per frame from a `renderWorldPass` RETURN hook (`MixinEntityRendererPostPipeline`). `RenderTargetManager` owns the offscreen scene color/depth targets (plus ping-pong targets for multi-pass filters) that the mixin binds before world rendering. Each pass independently reports `isEnabled()`, so passes for features the user has off are cheap no-ops.

The pass chain, in registration order (`PostProcessPipeline.registerPasses()`), and why the order matters:

1. **`BloomPass`** -- runs first, while the scene is still HDR, so the bright-pass shader sees luminance above `[0,1]` for glow on suns/torches/lava. Composites additively back into the (still HDR) scene texture.
2. **`HDRTonemapPass`** -- ACES filmic tonemap, clamping to `[0,1]` so every downstream pass works on LDR values.
3. **`EntityMotionVectorPass`** -- per-entity motion vectors, emitted early since it only depends on `CameraState` + the per-frame entity queue, not on scene color content. Must run before FSR2 (which consumes MV for reprojection).
4. **`BilinearBlitPass`**, **`FSR1EASUPass`**, **`FSR1QualityPass`**, **`FSR2ReconstructionPass`** -- all upscalers are always registered; each checks `LDOGConfig.upscalerAlgorithm` in `isEnabled()` so exactly one actually runs per frame. FSR2 is the temporal upscaler (Lanczos source + jittered history + entity-MV reprojection); when it's selected, the standalone TAA pass short-circuits because FSR2 owns its own history accumulation.
5. **`TAAAccumulatePass`** -- runs after the upscaler, accumulating over the native-res upscaled image. Paired with `MixinEntityRendererJitter`, which offsets the projection matrix per frame for sub-pixel sampling.
6. **`RCASSharpenPass`** -- sharpens the blended TAA result (not a pre-blend noisy image).
7. **`LDOGFXAAPass`** -- runs late so it can clean up aliasing introduced by earlier passes.
8. **`ShaderPackCompositePass`** -- runs the active shader pack's composite/final chain against the fully-resolved, post-AA image (self-skips when no pack is active).
9. **`VignettePass`** -- absolute last: a multiplicative final-image darkening that must not be smoothed by FXAA's edge detector.

`effectiveRenderScale()` is the single source of truth both the pipeline and the world-bind mixin read: it forces native resolution (1.0) whenever a shader pack is active (packs own the final image; internal-scale upscaling would blur geometry the pack still needs to shade), otherwise it honors `LDOGConfig.internalRenderScale`. `PostProcessPipeline.hasConflictingFeatureOn()` (currently: MSAA) tells the binding mixin to yield when another feature already owns the world-pass FBO.

## Shader Pack Support (`render/shaderpack/`)

Targets the OptiFine/Iris `shaderpacks/<name>(.zip)/shaders/` convention: `shaders.properties`, `gbuffers_*.{vsh,fsh}`, `shadow.{vsh,fsh}`, `composite[N].{vsh,fsh}`, `final.{vsh,fsh}`, and `worldN/` per-dimension overrides. Key classes:

- **`ShaderPack`** / **`DirectoryShaderPack`** / **`ZipShaderPack`** -- discovery and raw source/property access for a pack on disk.
- **`BuiltinShaderPack`** / **`BuiltinShaderPacks`** -- packs LDOG ships inside its own jar (classpath resources under `/assets/ldog/shaderpacks/`), written directly against LDOG's runner and GLSL 120 so they're guaranteed to compile even when a downloaded pack targets a newer Iris/MC.
- **`ShaderPackManager`** (singleton `INSTANCE`) -- discovery + activation glue; scanned once at `postInit`, selection applied from config or the GUI.
- **`ShaderPackRuntime`** -- the compiled, executable form of an activated pack: owns the GL programs for the composite chain and the (compiled-but-currently-shadow-only) gbuffer/shadow programs.
- **`GlslPreprocessor`** -- a minimal `#if`/`#ifdef` resolver that strips dead conditional branches before scanning for the active `DRAWBUFFERS`/`RENDERTARGETS` comment-directive, so MRT output mapping doesn't pick a branch the GL driver didn't actually compile.
- **`ShaderPackGbufferManager`** -- per-draw-call dispatcher for the pack's `gbuffers_*` programs plus the MRT G-buffer (`colortex0..N`) they write into; mixins on each MC draw type call `begin`/`end` around it. `GbufferProgram` encodes OptiFine's documented per-category program-fallback chain (e.g. `gbuffers_terrain` -> `gbuffers_textured` -> `gbuffers_basic`).
- **`ShadowMapManager`** -- sun/moon-POV depth-only shadow pass rendered through the pack's own `shadow` program by swapping GL matrices and re-invoking `RenderGlobal.renderBlockLayer`, reading the resulting matrices back for the `shadowModelView`/`shadowProjection` uniforms.
- **`ShaderColortexFormats`** -- parses pack-declared `colortexNFormat`/`gauxNFormat` constants so buffers are allocated at the precision the pack expects (HDR buffers as `R11F_G11F_B10F`, etc.) instead of clipping to `RGBA8`.
- **`ShaderPackUniforms`** -- per-frame OF/Iris-compatible uniform set (`cameraPosition`, etc.), fed to each composite stage.
- **`ShaderMacros`**, **`ShaderNoiseTexture`**, **`ShaderProgramId`** -- macro plumbing, the shared blue-noise texture, and the canonical program-name/file-pair table.

`render/pipeline/passes/ShaderPackCompositePass.java` is the pipeline-side driver: it walks `composite` through `composite15` then `final`, binding `colortex0`/`depthtex0` (with 1x1 black stand-ins for unbound `colortex1..7`), feeding `ShaderPackUniforms`, and ping-ponging between two fullscreen targets.

## GUI (`gui/`)

`GuiLDOGSettings` is a tabbed settings screen (`Tab` enum: General, Rendering, Visual, Features, Shaders, UI/HUD) backed by `GuiLDOGSettingsList` (a scrollable `GuiListExtended`) for content, with a fixed Done button. `GuiShaderPackList`/`GuiShaderPackPicker` provide the Shaders tab's pack browser. See `docs/CONVENTIONS.md` for button-ID ranges and label-formatting conventions.

## OptiFine Compatibility (`compat/`)

`OptiFineCompat` detects OptiFine reflectively (never a compile dependency) and, per `OFFeature`, applies one of three `OFOverrideMode`s: `AUTO` (defer to OF when present -- the legacy default), `LDOG_OVERRIDE` (try to disable OF's version via `OFConfigBridge`, falling back to deferring if the reflective write fails), or `OPTIFINE_OVERRIDE` (always defer). `OFConfigBridge` writes to the `ofXxx` instance fields OptiFine's core-mod transformer adds to vanilla `GameSettings` -- there is no separate `optifine.Config` class to target on 1.12.2.
