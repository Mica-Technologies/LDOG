# LDOG Attack Plan

A phased development plan for building out Limitless Development Optigame, from foundation to full OptiFine replacement.

---

## Resume Prompt

> We're building LDOG (`ldog`), an open-source OptiFine replacement for Minecraft Forge 1.12.2. The project is at `E:\gitRepos\LDOG`. The build system is GregTechCEu Buildscripts (RetroFuturaGradle 1.4.0). Reference projects for conventions are at `E:\gitRepos\minecraft-city-super-mod` and `E:\gitRepos\LDFAWE`. Read `CLAUDE.md`, `docs/ATTACK_PLAN.md`, and `docs/ARCHITECTURE.md` to get up to speed, then check off what's been completed and pick up the next unchecked item.

### Where We Left Off (2026-04-16)

**Phases 1-7 implemented** (Phase 7a/b with known caveats, 7c still open; 7d done).

- **Phase 1** (rendering optimizations, FPS reducer, clear water): complete
- **Phase 2** (HD textures): complete — tested with 256x resource pack
- **Phase 3** (CTM): complete — glass blocks, glass panes, bookshelf horizontal CTM
- **Phase 4** (emissive textures): complete — block + item emissive with fullbright lightmap
- **Phase 5** (dynamic lights + lighting): complete — dynamic lights, full lightmap customization (13 presets)
- **Phase 6** (resource pack features): complete — better grass/snow, natural textures, custom colors, custom sky, random entity textures
- **Phase 7a+b** (AF + MSAA): complete but experimental (see caveats below)
- **Phase 7d** (FXAA): complete — wraps MC's shipped fxaa.json shader pass

**Phase 7 known caveats:**
- **AF atlas bleed**: Enabling AF shows faint block-edge lines at distance. Cause: vanilla MC atlas has only 1px sprite border at mip 0, which halves each mip level. At grazing angles AF samples along an elongated line that crosses sprite boundaries in the smaller mips. OptiFine solves this by extending each mip level's sprite border (sometimes called "anisotropic-safe mipmaps" or "extended border mipmaps"). Tracked as **Phase 7c**.
- **MSAA edge lines**: MSAA reveals sub-pixel rasterization gaps at chunk/block-face seams on distant geometry (adjacent faces' edges are mathematically coincident but FP imprecision creates microscopic gaps that pre-MSAA rasterization didn't sample). OptiFine avoids this by setting display-level MSAA via `PixelFormat.withSamples()` and disabling MC's intermediate FBO — which loses spectator outlines. Not worth the tradeoff; recommend FXAA instead.

**Key next steps:**
1. **Phase 7c** (extended border mipmaps) — fixes the AF atlas bleed properly. Mixin on TextureAtlasSprite.generateMipmaps (or TextureMap) to pad each mip level's sprite with N extra pixels of the sprite's own edge color.
2. **Phase C3** (Smooth Font absorption) — antialiased TrueType text rendering with lazy + disk-cached glyph atlas. Target: drop Smooth Font from the modpack without paying its ~doubled-launch-time cost.
3. **Phase 8** (Shaders) + **Phase 9** (FSR): stretch goals, see Super Resolution + Radiance mods for reference.

**Test resource packs (already in run/resourcepacks/):**
- `default-1-12` (extracted) -- CTM glass + glass panes (47-tile)
- `Faithful 32x - 1.12.2` (extracted) -- CTM glass + bookshelf + custom sun/moon
- `emissive-ores-1-12-2` (extracted) -- emissive ore textures
- `Dramatic Skys Demo 1.5.3.36.3.zip` -- custom sky layers (optifine + mcpatcher paths)
- `Affinity-HD-Bundle-x256.zip` -- custom sky + random mob textures + colormaps
- `alto-resource-pack.zip` -- modpack resource pack

---

## Phase 0: Foundation (Build System + Core Scaffolding)

- [x] All items complete. Tagged `v0.0.1-alpha`.

---

## Phase 1: Rendering Optimizations + Mod Absorptions

- [x] All items complete. Tagged `v0.1.0-alpha`.

**Implemented features:**
- Entity render distance culling (configurable, default 64 blocks)
- Entity LOD (64-128 blocks: half framerate, 128+: quarter)
- Tile entity render distance culling (configurable, default 64 blocks)
- Particle frustum culling (dot product behind-camera check)
- FPS Reducer (replaces standalone mod): AFK + unfocused detection, mouse movement tracking, HUD indicator
- Clear Water (replaces standalone mod): surface alpha, underwater fog, RGB color tinting
- F3 debug overlay with [LDOG] stats section
- Scrollable settings GUI accessible from Options + Video Settings screens

---

## Phase 2: HD Texture Support

- [x] Research complete -- vanilla atlas already supports any sprite size up to GL max
- [x] MixinTextureAtlasSprite: prevents crash on non-square textures
- [x] Tested with 256x resource pack (covers 32x/64x/128x)

---

## Phase 3: Connected Textures (CTM)

- [x] CTMProperties: OptiFine .properties file parser (method, matchBlocks, tiles)
- [x] CTMLogic: 47-tile full CTM + horizontal + vertical index calculation
- [x] CTMBakedModel: BakedModelWrapper with per-quad retexturing via UV remap
- [x] CTMSprite: custom TextureAtlasSprite that loads PNGs from mcpatcher/ctm paths
- [x] CTMRegistry: scans resource pack dirs/zips, registers tiles, wraps models at ModelBakeEvent
- [x] CTMRenderContext: ThreadLocal passing IBlockAccess+BlockPos from renderBlock to getQuads
- [x] MixinBlockRendererDispatcher: sets/clears CTMRenderContext around block rendering
- [x] Supports both mcpatcher/ctm and optifine/ctm paths, numeric block IDs
- [x] Glass pane CTM: synthetic quads for absent arms, UV mirror convention, mirrorH tile selection
- [x] Seam suppression: removes UP/DOWN edge strips between stacked panes for seamless glass
- [x] Test bookshelf horizontal CTM

---

## Phase 4: Emissive Textures

- [x] EmissiveTextureRegistry: scans resource packs for *_e.png files directly
- [x] Reads optifine/emissive.properties and mcpatcher/emissive.properties for suffix config
- [x] EmissiveRenderHandler: creates fullbright retextured quads (UV remap to emissive sprite)
- [x] MixinBlockModelRenderer: intercepts getQuads() in both smooth and flat paths
- [x] Verified in-game: emissive ore overlays rendering with glow
- [x] Fullbright lightmap verified: lightmap(240, 240) in BLOCK format correctly bypasses AO/smooth lighting
- [x] RenderItem emissive layer: MixinRenderItem + EmissiveItemRenderHandler (ITEM format, global fullbright via OpenGlHelper, polygon offset)

---

## Phase 5: Dynamic Lights + Lighting Customization

- [x] DynamicLightManager: tracks entities holding light-emitting items, distance-attenuated
- [x] ItemLightRegistry: maps items to light levels (block items auto-detect + hardcoded overrides)
- [x] MixinWorldDynamicLights: injects into ChunkCache.getCombinedLight() (World loads too early for mixins)
- [x] DynamicLightTickHandler: per-tick entity scanning + per-frame smooth mode
- [x] Entities on fire emit light level 15; dropped items emit their item's light level
- [x] Configurable update interval (Smooth/per-frame, Fast/per-tick, or N ticks)
- [x] MixinEntityRendererLightmap: full lightmap customization via 16x16 texture manipulation
- [x] Separate block/sky light RGB tinting (warm torches + cool moonlight, no shaders)
- [x] Night darkness multiplier (0.5x brighter → 100x pitch black) with torch protection
- [x] Brightness boost (-1.0 to +1.0)
- [x] Pseudo-HDR tonemapping (ACES filmic curve)
- [x] 13 named presets (neutral, warm_torches, cinematic, candlelight, moonlit, dark_nights, horror, bright_caves, vivid, fluorescent, purple_haze, neon_blue, red_alert)
- [x] Full GUI: preset cycling + individual controls for every parameter
- [x] Auto-enable when any individual option is changed
- [ ] Per-light-source color (torch=warm, redstone=red) — possible future enhancement

---

## Phase 6: Resource Pack Features

### 6a: Better Grass / Better Snow
- [x] BetterGrassBakedModel: wraps grass/mycelium models, replaces side quads with biome-tinted top texture
- [x] BetterGrassHandler: captures sprites at TextureStitchEvent.Post, wraps models at ModelBakeEvent
- [x] Config toggle: betterGrass = "off" / "fast" / "fancy" (default: fancy)
- [x] BetterSnowHandler: renders snow-textured side quads on any opaque block with snow above
- [x] Flat lighting + directional shading + Z-offset for snow quads

### 6b: Natural Textures
- [x] NaturalTextureBakedModel: position-based UV rotation (0/90/180/270) and flip
- [x] NaturalTextureHandler: parses optifine/natural.properties, defaults for 16 common blocks
- [x] NaturalTextureMode: rotate, rotate+flip, flip, fixed (matching OptiFine format)
- [x] Config + GUI: enableNaturalTextures

### 6c: Custom Colors
- [x] CustomColorHandler: IResourceManagerReloadListener, fires after vanilla colorizers
- [x] Custom grass.png/foliage.png colormaps from optifine/colormap/
- [x] Parse optifine/color.properties for redstone wire colors + static overrides
- [ ] Per-biome water color overrides (future enhancement)
- [ ] Potion / map / dye color overrides (future enhancement)

### 6d: Custom Sky
- [x] CustomSkyRenderer: parses skyN.properties, lazy-loads on first render
- [x] Parse optifine/sky/world0/skyN.properties (texture, HH:MM fade times, rotate, blend, speed, axis)
- [x] CustomSkyLayer: time-based alpha fade with wrap-around, skybox cube rendering
- [x] Custom sun/moon already supported by vanilla resource pack system
- [ ] **BUG**: renderSky mixin had refmap ambiguity (two `renderSky` overloads). Fixed descriptor, regenerated refmap. Diagnostic logging added — needs in-game verification that mixin fires. If it does, may still need GL state or rendering fixes.

### 6e: Random Entity Textures
- [x] RandomEntityTextureHandler: scans optifine/random/entity/ for numbered variants
- [x] MixinRender: intercepts bindEntityTexture() for living entities (targets Render.class, not abstract method)
- [x] UUID-based deterministic selection with optional weighted .properties
- [x] Config + GUI: enableRandomEntityTextures

---

## Phase 7: Anti-aliasing / Anisotropic Filtering

### 7a: Anisotropic Filtering
- [x] Config: `enableAnisotropicFiltering`, `anisotropicLevel` (2/4/8/16, clamped to GPU max)
- [x] `AnisotropicFilteringHandler` applies `GL_TEXTURE_MAX_ANISOTROPY_EXT` at TextureStitchEvent.Post
- [x] GUI: toggle + level cycling button; re-applies on save without full resource reload
- [x] Graceful fallback if `GL_EXT_texture_filter_anisotropic` is missing
- [ ] **Known issue**: faint block-edge lines at distance (atlas sprite border bleed on small mip levels). See Phase 7c.

### 7b: MSAA
- [x] Config: `enableMSAA`, `msaaSamples` (2/4/8, clamped to `GL_MAX_SAMPLES`)
- [x] `MSAAFramebuffer` auxiliary multisampled FBO (GL_RGBA8 color renderbuffer + GL_DEPTH24_STENCIL8)
- [x] `MixinEntityRendererMSAA` binds MS FBO at renderWorldPass HEAD, blit-resolves color to mc.framebufferMc at RETURN
- [x] Auto-resizes on window size change
- [x] GUI: toggle + sample count cycling
- [x] Graceful fallback if GL 3.0 or EXT_framebuffer_multisample+blit missing
- [ ] **Known issue**: faint rasterization edge lines at distant chunk/block-face seams. OptiFine avoids this with display-level MSAA (PixelFormat.withSamples + disable fboEnable) but loses spectator outlines. Not fixing — FXAA (Phase 7d) is a better long-term AA answer.

### 7c: Extended border mipmaps (AF bleed fix)
- [ ] Not started. Target: mixin on `TextureAtlasSprite.generateMipmaps` (or `TextureMap.loadTextureAtlas`) to extend each sprite's mip level with N pixels of its own edge color (N >= 2^mipLevel), so AF anisotropic samples never cross into neighboring sprites. OptiFine/MCPatcher reference pattern.
- **Tried and reverted (2026-04-16)**: simpler mitigation — clamp `GL_TEXTURE_MAX_LOD` to `mipmapLevels - 2` on the block atlas when AF is on, to keep the sampler off the worst-bleed mip levels. Didn't visibly reduce the distant-block edge lines and added mild quality regression, so reverted uncommitted. The proper fix really does need extended borders — no GL-param shortcut exists. Likely structure when tackled: subclass/replace `Stitcher` to allocate `2^mipmapLevels` pixels of inter-sprite padding, then hook the atlas upload path to fill the padding area with each sprite's edge-extended pixel data. Post-upload, mipmaps downsample the halo correctly at every level.

### 7d: FXAA post-process
- [x] Config: `enableFXAA`
- [x] `FXAAHandler` toggles `EntityRenderer.loadShader("shaders/post/fxaa.json")` on/off
- [x] `AccessorEntityRenderer` mixin for clearing shaderGroup+useShader on disable
- [x] First-tick reconciliation so launching with FXAA pre-enabled picks it up
- [x] GUI row
- Notes: Leverages MC 1.12.2's shipped FXAA shader assets (Super Secret Settings remnant) — no GLSL authoring needed. Effect subtle on 16x vanilla textures by design; more visible on HD packs and alpha-test foliage.

---

## Phase 8: Shader Pipeline

- [ ] Not started (stretch goal — requires deep OpenGL expertise, multi-month effort)

---

## Phase 9: Upscaling (FSR)

- [ ] Not started (requires Phase 8 FBO rendering pipeline)

**Concept:** AMD FidelityFX Super Resolution 1.0 (spatial upscaler). Render the scene at reduced resolution to an FBO, then apply FSR's sharpening/upscale pass to output at native resolution. Works on any GPU (AMD, NVIDIA, Intel) — no vendor lock-in unlike DLSS.

**Why FSR 1.0 and not DLSS:**
- DLSS requires NVIDIA SDK (native C++ / JNI), DirectX 12 or Vulkan (not OpenGL), motion vectors, and depth buffer access. Fundamentally incompatible with MC 1.12.2's OpenGL 2.1 pipeline.
- FSR 1.0 is a single spatial post-processing pass (EASU + RCAS). Can be implemented as a GLSL shader applied to a framebuffer texture. No temporal data or motion vectors needed.

**Prerequisites:** FBO rendering pipeline from Phase 8 (shaders). FSR would be a post-processing pass applied after the scene is rendered to an FBO but before display.

---

## Phase C1: Mod Absorptions -- COMPLETE

- [x] FPS Reducer -> FpsReducerHandler (AFK + unfocused + mouse tracking + HUD overlay)
- [x] Clear Water -> MixinBlockFluidRenderer + ClearWaterHandler (alpha + fog + RGB tint)

---

## Phase C2: Memory Optimization Absorption

- [ ] Not started (deferred until Phases 2-4 are stable)

---

## Phase C3: Smooth Font Absorption

Replaces the Smooth Font mod's functionality: antialiased TrueType font rendering instead of MC's default bitmap font. Target slot: after Phase 7c, before Phase 8 (the user's modpack relies on Smooth Font, and Phase 8 shader work is much larger scope — shipping C3 earlier unblocks dropping another mod from the pack).

**Pain point driving this:** Smooth Font roughly doubles launch time because it rasterizes the entire Unicode glyph range (or the full font atlas) synchronously at startup. LDOG's implementation must not repeat that.

**Shortcut discovered (2026-04-16)**: the alto resource pack already ships an HD 4096×4096 ASCII font PNG at `assets/minecraft/optifine/font/ascii.png` (and the MCPatcher-path equivalent at `assets/minecraft/mcpatcher/font/ascii.png`). Glyphs are pre-rasterized with clean spacing — no TTF rasterization needed if packs provide this file. First implementation pass should just support this "HD font texture swap" path; true runtime TTF rendering can come later if users want non-pack-provided fonts.

- [ ] **Easy path (do first)**: at texture load time, detect HD font PNGs at the mcpatcher/optifine paths. When found, intercept FontRenderer's ResourceLocation for `textures/font/ascii.png` and related `glyph_XX.png` to substitute the HD variants. Flip `GL_TEXTURE_MIN/MAG_FILTER` to `GL_LINEAR` on the font texture for antialiased sampling. Needs checking whether the HD glyph spacing actually tolerates LINEAR filtering without inter-glyph bleed — probably fine at 16× resolution given visible padding in alto's pack.
- [ ] **Full path (later)**: research MC's FontRenderer hook points (`renderUnicodeChar`, `getCharWidth`); replace glyph source with Java AWT `Font` + `Graphics2D` antialiased rasterization; lazy glyph rasterization; persistent disk cache at `config/ldog/font-cache/<font-hash>/<size>.png`; multithreaded warm-up.
- [ ] Config: `enableSmoothFont` (toggle), `useHDFontWhenAvailable` (HD path toggle), `fontFamily` (TTF path — later), `antialiasMode` (none/grayscale/subpixel — later).
- [ ] GUI section: "Font Rendering".
- [ ] OptiFine conflict check: OptiFine has its own font rendering option; auto-disable LDOG's version when OptiFine is detected.

**Why the load-time budget matters:** Smooth Font takes ~5-10s extra on first launch because it synchronously rasterizes all 256 glyph pages × multiple sizes. Lazy + cached + threaded should put first launch at ~1s overhead and subsequent launches at near-zero.

---

## Key Technical Notes

### Mixin Registration
LDOG uses MixinBooter 10.7 with `ILateMixinLoader` (`LDOGMixinLoader`). Two config files:
- `mixins.ldog.json` (late): GUI mixins, RenderGlobal, TESR, ParticleManager, BlockModelRenderer, BlockRendererDispatcher
- `mixins.ldog.early.json` (also loaded via late loader): BlockFluidRenderer, TextureAtlasSprite

Classes loaded before late mixin application (Minecraft, BlockLiquid) cannot use Mixins -- use Forge events instead.

### TextureMap Timing
`mapRegisteredSprites` is **cleared** before `TextureStitchEvent.Pre` fires. Cannot enumerate existing sprites during Pre. Must scan resource packs directly or use other discovery methods.

### CTM World Context
`IBakedModel.getQuads()` doesn't receive IBlockAccess. Solved via `CTMRenderContext` ThreadLocal set by `MixinBlockRendererDispatcher.renderBlock()`.

### MCPatcher vs OptiFine Paths
Resource packs may use either `mcpatcher/ctm` or `optifine/ctm`. Tile PNGs live outside the `textures/` directory, so `CTMSprite` (custom loader) is needed.

---

## Version Milestones

| Version | Phase | What Users Get |
|---|---|---|
| `v0.0.1-alpha` | Phase 0 | Mod loads, nothing visible yet |
| `v0.1.0-alpha` | Phase 1 + C1 | FPS improvements, FPS reducer, clear water (replaces 3 mods) |
| `v0.4.0-alpha` | Phase 2-4 | HD textures, CTM, emissive textures |
| `v0.5.0-alpha` | Phase 5 | Dynamic lights, lighting customization (block/sky color, night darkness, HDR) |
| `v0.6.0-alpha` | Phase 6 | Full resource pack feature parity |
| `v1.0.0-beta` | Phase 8 | Shader support -- OptiFine fully replaceable |

At full maturity, LDOG replaces **5-7 separate mods** in the alto modpack.

---

## Git History (key commits)

| Tag/Hash | What |
|---|---|
| `v0.0.1-alpha` | Phase 0 scaffold |
| `v0.1.0-alpha` | Phase 1 complete |
| `v0.4.0-alpha` | Phase 2-4 initial implementation |
| `3325e11` | Emissive direct pack scanning + CTM null-side fix |
| `6a9bc9d` | CTMSprite mipmap crash fix |
| `3d518bb` | CTM tile mapping rewrite + emissive reflection fix |
| `aff578c` | CTM scanner + emissive sprite registration |

50 unit tests, all passing.
