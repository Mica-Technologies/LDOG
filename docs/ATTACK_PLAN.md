# LDOG Attack Plan

A phased development plan for building out Limitless Development Optigame, from foundation to full OptiFine replacement.

---

## Resume Prompt

> We're building LDOG (`ldog`), an open-source OptiFine replacement for Minecraft Forge 1.12.2. The project is at `E:\gitRepos\LDOG`. The build system is GregTechCEu Buildscripts (RetroFuturaGradle 1.4.0). Reference projects for conventions are at `E:\gitRepos\minecraft-city-super-mod` and `E:\gitRepos\LDFAWE`. Read `CLAUDE.md`, `docs/ATTACK_PLAN.md`, and `docs/ARCHITECTURE.md` to get up to speed, then check off what's been completed and pick up the next unchecked item.

### Where We Left Off (2026-04-17, end of day)

**Phases 1-7 implemented** (Phase 7a/b with known caveats, 7c done 2026-04-17; 7d done). **Phase C3 complete 2026-04-17** — full font system:
- HD font texture swap (OptiFine/MCPatcher paths), pack-provided `ascii.properties` width overrides (with the `+1` spacing convention vanilla uses)
- 3-level AA (off / bilinear / trilinear), with LOD bias (default `-0.5`) and anisotropic (default 16x) polish applied to the trilinear path
- TTF runtime rasterization via Java AWT `Graphics2D` — ASCII page rasterized into a 16×16 grid atlas at a configurable cell size, widths computed from `FontMetrics.charWidth(ch)` straight into `FontRenderer.charWidth[]`
- User-supplied fonts: drop `.ttf`/`.otf` files into `config/ldog/fonts/`, they're loaded via `Font.createFont` + registered with `GraphicsEnvironment` at preInit, and appear in the GUI family cycle highlighted in yellow. Rescanned on each resource reload so add/remove without restart.
- Drop-shadow toggle — `@ModifyVariable` on the `dropShadow` param of `FontRenderer.drawString`, flips live.
- Moved FontRenderer mixins to a core-plugin early loader (`IEarlyMixinLoader` via `LDOGCorePlugin` + `mixins.ldog.vanilla.json`) because vanilla/Forge pulls FontRenderer in ahead of the normal late-loader window.
- Subclass pass-through in the bind redirect so Forge's `SplashFontRenderer` (which has its own private texture pool + separate GL context) doesn't crash.

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
1. **Phase C4** (OptiFine Override Mode) — reverse the coexistence once parity is proven; research-heavy (reflective writes against `optifine.Config`).
2. **Phase 6d** custom-sky mixin verification — landed but needs in-game confirmation the injection fires.
3. **Phase 8** (Shaders) + **Phase 9** (FSR): stretch goals, see Super Resolution + Radiance mods for reference.
4. **C3 polish** (optional): disk-cached TTF atlas, async rasterization, Unicode glyph pages.

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
- [x] **Fixed by Phase 7c**: block-edge bleed at distance addressed by extended-border mipmaps (opt-in).

### 7b: MSAA
- [x] Config: `enableMSAA`, `msaaSamples` (2/4/8, clamped to `GL_MAX_SAMPLES`)
- [x] `MSAAFramebuffer` auxiliary multisampled FBO (GL_RGBA8 color renderbuffer + GL_DEPTH24_STENCIL8)
- [x] `MixinEntityRendererMSAA` binds MS FBO at renderWorldPass HEAD, blit-resolves color to mc.framebufferMc at RETURN
- [x] Auto-resizes on window size change
- [x] GUI: toggle + sample count cycling
- [x] Graceful fallback if GL 3.0 or EXT_framebuffer_multisample+blit missing
- [ ] **Known issue**: faint rasterization edge lines at distant chunk/block-face seams. OptiFine avoids this with display-level MSAA (PixelFormat.withSamples + disable fboEnable) but loses spectator outlines. Not fixing — FXAA (Phase 7d) is a better long-term AA answer.

### 7c: Extended border mipmaps (AF bleed fix)
- [x] Config: `enableExtendedBorderMipmaps` (default off — opt-in, grows atlas ~3x for 16x packs).
- [x] `ExtendedBorderHandler`: holds per-stitch state (mipmapLevels, active flag), generates padded mipmap chains via clamp-to-edge halo at each mip level.
- [x] `MixinStitcherHolder`: `@Redirect`s `getIconWidth/Height` inside `Holder.<init>` to inflate packing dims by `2 * border` (border = `2^mipmapLevels`). Sprite's own `width/height` stay untouched so external callers see inner size.
- [x] `MixinStitcher`: `@Redirect`s the `initSprite` call in `getStichSlots` to shift sprite origin inward by `border`, so UVs address only the inner region.
- [x] `MixinTextureMap`: brackets the stitch pass with `beginStitch`/`endStitch`; `@Redirect`s the `TextureUtil.uploadTextureMipmap` call in `finishLoading` to write padded pixel data at `(innerOrigin - border)` with dims `(w + 2*border, h + 2*border)`. Uses `remap = false` on the enclosing `@Redirect` because `finishLoading` is Forge-added (no SRG mapping), but inner `@At(target = ..., remap = true)` keeps the MCP-mapped target remapped.
- [x] GUI toggle in the AA/Filtering section. On save, triggers `mc.refreshResources()` to rebuild the atlas (packing change, not a live-refresh like AF).
- [x] All 50 unit tests pass.
- **Known limitations**: animated sprite halo stays as the first frame's edge color (updateAnimation is not redirected, v1 tradeoff). Atlas growth may push some modpacks past `GL_MAX_TEXTURE_SIZE` — enable only if your atlas has headroom.
- **Tried and reverted (2026-04-16, pre-implementation)**: clamp `GL_TEXTURE_MAX_LOD` to `mipmapLevels - 2` on the block atlas when AF is on. Didn't visibly reduce the distant-block edge lines and added mild quality regression, so reverted uncommitted. Confirmed: extended borders were the right approach.

### 7d: FXAA post-process
- [x] Config: `enableFXAA`
- [x] `FXAAHandler` toggles `EntityRenderer.loadShader("shaders/post/fxaa.json")` on/off
- [x] `AccessorEntityRenderer` mixin for clearing shaderGroup+useShader on disable
- [x] First-tick reconciliation so launching with FXAA pre-enabled picks it up
- [x] GUI row
- Notes: Leverages MC 1.12.2's shipped FXAA shader assets (Super Secret Settings remnant) — no GLSL authoring needed. Effect subtle on 16x vanilla textures by design; more visible on HD packs and alpha-test foliage.

---

## Phase 8: Shader Pipeline

- [~] **Phase 8a in progress (2026-04-17):** post-process pipeline scaffold landed — pass contract, pipeline shell, no-op pass, mixin lifecycle hook on `EntityRenderer.renderWorldPass`, debug telemetry, fault-tolerant pass disabling, and an off-by-default `enablePostProcessPipeline` config gate. No visual change yet.
- [ ] 8a remaining: `RenderTargetManager` (scaled world target + ping-pong), parity validation evidence, overlay/"active scale" surfacing.
- [ ] 8b hardening, 9a FSR1 MVP, 9b tuning, 9c temporal research — not started.
- Deep-dive planning and feasibility reference: `docs/P8_RESEARCH_AND_PLAN.md`

---

## Phase 9: Upscaling (FSR)

- [ ] Not started (requires Phase 8 FBO rendering pipeline)
- Deep-dive planning and feasibility reference: `docs/P8_RESEARCH_AND_PLAN.md`

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

- [x] **Easy path shipped 2026-04-17**: `SmoothFontHandler` scans the active resource pack for HD ASCII PNG in priority order `optifine/font/ascii.png → mcpatcher/font/ascii.png`; registers a `HDFontTexture` (SimpleTexture subclass) under stable `ldog:textures/font/hd_ascii`; applies `GL_LINEAR` filtering at upload for antialiased sampling. `MixinFontRenderer.@Redirect` on the `bindTexture(locationFontTexture)` call inside `renderDefaultChar` swaps the bound texture to the HD variant when available. `FontRendererInvoker` exposes the protected `bindTexture` method so the redirect can invoke vanilla behavior with a substituted ResourceLocation. Both use `remap = false` since `FontRenderer.bindTexture` is a Forge-added method with no SRG mapping.
- [x] **Width overrides**: Smooth Font / OptiFine `ascii.properties` format (`width.N=W`) parsed from `optifine/font/ → mcpatcher/font/ → font/` priority order. `MixinFontRenderer.@Inject(TAIL)` on `readFontTexture` applies overrides on top of vanilla's auto-computed widths. Alto pack ships widths at `font/ascii.properties` — discovered and honored.
- [x] Config: `enableSmoothFont` (master), `useHDFontTexture` (HD swap), `fontAntialiasing` (off/bilinear/trilinear), `useFontPropertyWidths` (override widths), `fontLodBias`, `fontAnisotropic`, `useTTFFont`, `ttfFontFamily`, `ttfBold`, `ttfItalic`, `ttfFontSize`, `ttfCellSize`, `fontDropShadows`. Live flips: AA off↔bilinear, drop shadows. Trilinear boundary + TTF source change + HD probe trigger `mc.refreshResources()`.
- [x] GUI section: "Font Rendering" — 9 rows covering every toggle/cycle with per-button hover tooltips. Custom TTF families highlighted yellow vs built-in green.
- [x] OptiFine conflict check: `OptiFineCompat.shouldHandleSmoothFont()` auto-disables all four features when OptiFine is detected.
- [x] Unicode glyph pages (`glyph_XX.png`) deliberately unchanged in v1 — ASCII HD swap is the user-visible win; unicode pages are lower priority and untouched by the hook.
- [x] **Full path shipped 2026-04-17**: `TTFFontRasterizer` uses `java.awt.Graphics2D` with `TEXT_ANTIALIAS_ON` + `FRACTIONALMETRICS_ON` hints to rasterize MC's 256-char default-font page from an AWT `Font` into a 16×16 grid atlas at a configurable cell size. `TTFFontTexture` uploads that atlas via the shared `FontTextureUploader` (same filter/mipmap/LOD/anisotropic stack as the HD path). `SmoothFontHandler` prioritizes TTF over HD when `useTTFFont` is on; widths come straight from AWT `FontMetrics.charWidth(ch)` scaled to MC's logical 8-per-cell space and written into `FontRenderer.charWidth[]` via a mixin `@Accessor` — not `@Inject(TAIL)`, because our listener runs after FontRenderer's and a TAIL inject would always read the previous reload's table. GUI family cycle (built-in: SansSerif/Serif/Monospaced/Arial/Verdana/Tahoma/Segoe UI/Helvetica/Consolas/Courier New) + size cycle. ASCII page eager-rasterized at reload (~100ms for 256 glyphs — a single page, not Smooth Font's all-Unicode-pages × multiple-sizes double-launch cost).
- [x] **Three-level AA (2026-04-17)**: `FontAAMode` enum drives `off` (GL_NEAREST) / `bilinear` (GL_LINEAR, no mipmaps) / `trilinear` (GL_LINEAR_MIPMAP_LINEAR + `glGenerateMipmap` + `GL_TEXTURE_MAX_LEVEL = log2(size)`). Trilinear is the only mode that actually antialiases at typical GUI scales — plain bilinear is ≳16:1 downsampling from a 4096 atlas, which `GL_LINEAR` alone cannot resolve. Trilinear path also applies a negative `fontLodBias` (default -0.5, user-tunable -4..4) to recover crispness that box-filter mipmap generation softens, and anisotropic sampling (default 16x, clamped to GPU max) for sub-pixel edge detail.
- [x] **User-supplied fonts (2026-04-17)**: `TTFFontCatalog` scans `config/ldog/fonts/` for `.ttf`/`.otf` files at `preInit`, loads via `Font.createFont(TRUETYPE_FONT, file)`, registers with `GraphicsEnvironment` so family names resolve system-wide. Rescanned on every resource reload so F3+T picks up newly-dropped files without restart. Already-registered files skipped on rescan. GUI highlights custom families yellow vs built-in green. AWT's `registerFont()` rejections (name collisions with installed system fonts) logged as warnings rather than crashing.
- [x] **Drop-shadow toggle (2026-04-17)**: `fontDropShadows` config, `@ModifyVariable` on `FontRenderer.drawString(String,F,F,I,Z)I`'s boolean arg at HEAD. All rendering funnels through this overload, so one modify-variable catches every caller. Flips live without a reload.
- [x] **Subclass pass-through (2026-04-17)**: `MixinFontRenderer`'s redirect checks `self.getClass() != FontRenderer.class` and bypasses the HD swap for subclasses. Fixed a Forge `SplashProgress$SplashFontRenderer` crash where our `ldog:` location was rejected by the splash renderer's private texture pool (separate GL context, separate thread).
- [x] **Core-plugin early loader (2026-04-17)**: `LDOGCorePlugin` implements `IFMLLoadingPlugin` + MixinBooter's `IEarlyMixinLoader`, registers `mixins.ldog.vanilla.json`. FontRenderer gets pulled into the classloader during FML bootstrap ahead of the late-loader window, so late mixins were silently no-opping with "loaded too early". `coreModClass` in `buildscript.properties` propagates to both dev JVM args and the jar manifest.
- [ ] **Future enhancements (not blocking)**: persistent disk cache at `config/ldog/font-cache/<font-hash>/<size>.png`, async rasterization on a worker thread, Unicode glyph_XX.png runtime rasterization with lazy+cached per-page atlases, bold/italic GUI toggles, subpixel rendering hint.

**Why the load-time budget matters:** Smooth Font takes ~5-10s extra on first launch because it synchronously rasterizes all 256 glyph pages × multiple sizes. Lazy + cached + threaded should put first launch at ~1s overhead and subsequent launches at near-zero.

---

## Phase C4: OptiFine Override Mode (reverse the coexistence)

Today's compat model: if OptiFine is detected, LDOG auto-disables its overlapping features (CTM, emissive, sky, etc.) and lets OptiFine handle them. This is the safe default. Once LDOG's implementation of a given feature is demonstrably *as good or better* than OptiFine's (faster, lower memory, better-looking, more configurable), we want the ability to flip it: LDOG disables OptiFine's version and takes over.

- [ ] **Research feasibility**: OptiFine's settings live in `optifine.OptiFineConfig` / `Config` (class names vary by version). Investigate whether those fields are reflectively writable at runtime. If yes, we can toggle individual features off. If no (final fields, package-private methods with bytecode checks), fall back to editing `options.txt` before MC reads it, or CoreMod-level bytecode patching of the relevant Config initializer.
- [ ] **Per-feature override flag**: extend `OptiFineCompat` so every feature has three modes rather than two:
  - `AUTO` (current default): detect OptiFine, let it handle this feature
  - `LDOG_OVERRIDE`: detect OptiFine, forcibly disable OptiFine's version, use LDOG's
  - `OPTIFINE_OVERRIDE`: detect OptiFine, disable LDOG's version (current `AUTO` behavior)
- [ ] **Conservative defaults**: ship with `AUTO` as the default for every feature; only flip individual ones to `LDOG_OVERRIDE` after benchmarked parity (FPS delta, memory footprint, visual equivalence).
- [ ] **Graceful failure**: if the OptiFine toggle fails (reflection blocked, field renamed across OptiFine versions), log a warning and fall back to `OPTIFINE_OVERRIDE` — never leave both systems fighting over the same feature.
- [ ] **GUI section**: "OptiFine Interop" with a per-feature dropdown (only visible when OptiFine is detected).
- [ ] **Version-aware**: OptiFine field/class names drift across versions. Probe at startup, build a feature-to-field map for the detected OptiFine version, log what LDOG can and can't control.

**Why this is its own phase**: requires every LDOG feature to already be implemented and benchmarked vs OptiFine's equivalent, so it naturally comes after the core phases settle. Think of it as the switch-over point where LDOG transitions from "coexists with OptiFine" to "replaces OptiFine" — users with both installed can progressively migrate.

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
