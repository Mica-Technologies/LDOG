# LDOG Attack Plan

A phased development plan for building out Limitless Development Optigame, from foundation to full OptiFine replacement.

---

## Resume Prompt

> We're building LDOG (`ldog`), an open-source OptiFine replacement for Minecraft Forge 1.12.2. The project is at `E:\gitRepos\LDOG`. The build system is GregTechCEu Buildscripts (RetroFuturaGradle 1.4.0). Reference projects for conventions are at `E:\gitRepos\minecraft-city-super-mod` and `E:\gitRepos\LDFAWE`. Read `CLAUDE.md`, `docs/ATTACK_PLAN.md`, `docs/ARCHITECTURE.md`, and the deep-dive docs in `docs/` (P8_RESEARCH_AND_PLAN.md, POST_9A4_RESEARCH.md, PHASE_9C_TEMPORAL_DEEP_DIVE.md) to get up to speed, then pick up at the next open checklist item.

### Where We Left Off (end of 2026-04-17 — marathon session)

**Phases 0-10 complete, plus Phase 9c.1+9c.2 temporal stack shipped.** Phases 1-7 and C3 done in earlier sessions. Today was a ~12-hour run covering Phase 8, all of 9a, 9c.1+9c.2, and Phase 10.

**Phase 8 (Shader Pipeline) — full stack shipped + verified:**
- 8a framework: `PostProcessPass`, `PostProcessPipeline`, `RenderTargetManager`, `PipelineDebugStats`. Fault-tolerant pass removal on exception.
- 8b observability: perf-overlay row showing binding state + scale + pass timings, watchdog log if binding never activates.
- 8c binding hook: redirects world render into scaled scene target, `@Redirect` on vanilla viewport, blits back to main FB. Yields to MSAA. Parity verified at 1.0 + scaled scenes visibly softer.
- `ShaderProgram` utility for compile/link/uniform across all pipeline passes. `setUniformMatrix4` added for 9c.2.

**Phase 9 (Upscaling) — three upscalers + post-processing chain, all shipped:**
- 9a.1 `BilinearBlitPass` extracted from mixin.
- 9a.2 `FSR1EASUPass` — LDOG-original unsharp-mask-on-bilinear. **User-verified.**
- 9a.3 FSR1 sharpness slider 0-2.0. **User-verified.**
- 9a.4 `FSR1QualityPass` — direction-biased EASU (Sobel + anisotropic). **User-verified** ("really good").
- 9a.5 `UpscalerPreset` enum (Native/Ultra/Quality/Balanced/Performance/Custom). **User-verified.**
- 9a.6 `RCASSharpenPass` — post-upscale sharpen via `glCopyTexSubImage2D`. **User-verified.**
- 9a.7 `LDOGPreset` enum — whole-mod presets. **User-verified.**
- 9a.8 `LDOGFXAAPass` — 5 quality levels. **User-verified.**
- 9a.9 `AutoScaleHandler` — target-FPS dynamic resolution scaling. **Unverified: user's test didn't produce log output** (see "Open issues" below).

**Phase 9c (Temporal upscaling) — MVP + camera MV:**
- 9c.1 `JitterHelper` (Halton 2,3) + `TAAAccumulatePass` — **verified** after bug fix (see below).
- 9c.2 Camera motion vectors — `CameraState` + depth-texture attachment + reprojection in TAA shader — **verified** after "drunk visuals" bug fix (see below).

**Phase 10 (Borderless Windowed Fullscreen) — shipped + verified:**
- Restart-required mode: core-plugin sets `org.lwjgl.opengl.Window.undecorated=true` before Display creation.
- Flicker fix (plain `DisplayMode`, reorder operations).
- Windows Fullscreen Optimizations dodge with user toggle.
- Startup sizing fix: `mc.resize()` instead of `updateFramebufferSize` so `currentScreen.onResize` fires.

**Bugs caught during this session (all fixed):**
1. **9a.1→9a.2 FSR1 anti-ringing clamp killed sharpening.** 5-tap clamp to local min/max trapped edge peaks back to input. Widened clamp neighborhood, then dropped it entirely. Fixed.
2. **8c `pass != 2` guard wrong.** Originally `pass != 0`; that's anaglyph-only. Non-anaglyph uses `pass == 2`. 8c binding never activated on default config before this fix.
3. **Borderless flicker** on F11 caused by `Display.getDesktopDisplayMode()`'s bpp/refresh metadata triggering mode-switch semantics in LWJGL. Fixed with plain `new DisplayMode(w, h)`.
4. **Windows Fullscreen Optimizations flash** when window exactly matches desktop. Workaround: window is `desktop_h - 1` by default (user toggle).
5. **Borderless startup sizing** — game launched into fullscreen had a tiny main menu in the corner. Root cause: our handler called `updateFramebufferSize` but not `mc.resize()`, so `currentScreen.onResize` never fired. Fixed.
6. **Minecraft/FontRenderer mixin class-load race.** `Minecraft` is pulled into the classloader during FML bootstrap BEFORE late mixin configs register. Mixins targeting it must go in `mixins.ldog.vanilla.json` (early config loaded by `LDOGCorePlugin` via `IEarlyMixinLoader`). Affected `MixinMinecraftBorderless` + `AccessorMinecraft`.
7. **9c.1 jitter was a no-op.** Mixin injected jitter in `setupCameraTransform` but `renderWorldPass` overwrites `GL_PROJECTION` twice afterward (sky + terrain gluPerspective calls). Moved injection to `renderWorldPass` on both ordinals.
8. **9c.2 "drunk / swimming" visuals.** Captured un-jittered camera matrices while history stored jittered pixel positions — reprojection UVs swung with the Halton cycle. Fixed: capture AFTER `applyJitter()` so cur/prev matrices match what was actually rendered.

**Critical gotchas / non-obvious infrastructure (for next reader):**
- **`Minecraft` and `FontRenderer` mixins MUST be in `mixins.ldog.vanilla.json`** (early config loaded via `IEarlyMixinLoader`). Mixin targets pulled into the classloader during FML bootstrap before late configs register.
- `Display.setDisplayMode` in LWJGL 2.9.4 must be called with `new DisplayMode(w, h)` — passing the full desktop mode (with bpp/refresh) triggers mode-switch semantics even when not in exclusive fullscreen.
- TAA matrix capture must happen AFTER jitter, not before. History stores jittered pixel positions; reprojection needs matrices that match that jitter.
- `renderWorldPass` with `pass != 2` is anaglyph-only (red/cyan eyes). Default MC always uses `pass == 2`. Don't gate on `pass == 0` unless you actually want anaglyph only.
- Auto scale handler (`AutoScaleHandler`) uses `@Mod.EventBusSubscriber` with `modid = Tags.MODID, value = Side.CLIENT` — matches the pattern used by `FXAAHandler` and `PerformanceOverlayRenderer`. Registration should be automatic at mod load.
- Preset changes require setting `extBorderSettingsChanged`, `fxaaSettingsChanged`, `waterSettingsChanged` on the GUI instance so `saveAndClose` triggers the right reload paths.

**Open issues / pending verification:**
- **9a.9 Auto Scale — no log output in user test.** Two hypotheses, untested:
  1. FPS stayed inside the dead zone (0.9x — 1.1x target), so no tier change fired → no log. Current code only logs on actual changes.
  2. Handler didn't register, or guards (`pipeline off`, `world == null`) short-circuited every tick.
  - **Fix approach (next session):** add a one-shot "AutoScale handler tick running" log on first invocation to distinguish registration failure from no-op due to dead-zone. Also consider logging each decision at DEBUG level so the user can set log level to see "AutoScale hold (fps=144, target=144, scale=1.00)" even when no adjustment fires.
- **9a.9 design limitation:** auto-scale overrides manual Render Scale cycling. User can see the current value in the GUI slider but the next auto-tick will override. Documented in the tooltip. Better UX would be "auto-scale within manual's max bound" but adds complexity.

**Key next steps (priority order):**
1. **Debug 9a.9 auto-scale logging.** Add tick-confirmation log, retry, decide if fix is needed or if behavior is actually correct-but-invisible.
2. **Phase 9c.3 — entity motion vectors** (2-4 weeks, invasive). Fixes ghosting on moving mobs. Per `docs/PHASE_9C_TEMPORAL_DEEP_DIVE.md` this is the highest-cost stage; needs careful per-entity-render hook strategy.
3. **Phase 10b** runtime-togglable borderless — `Display.destroy`/`create` + LDOG subsystem GL-state coordination.
4. **Phase C4** (OptiFine Override Mode) — reverse coexistence via reflective writes to `optifine.Config`.
5. ~~**Phase 6d** custom-sky injection verification~~ — verified 2026-04-18; checklist item closed.
6. **C3 polish** (optional): disk-cached TTF atlas, Unicode pages.
7. **9a.9 extensions** (if auto-scale proves out): also auto-adjust upscaler algorithm and AA toggles at the tail of the ladder.

**Test resource packs (already in run/resourcepacks/):**
- `default-1-12` (extracted) — CTM glass + glass panes (47-tile)
- `Faithful 32x - 1.12.2` (extracted) — CTM glass + bookshelf + custom sun/moon
- `emissive-ores-1-12-2` (extracted) — emissive ore textures
- `Dramatic Skys Demo 1.5.3.36.3.zip` — custom sky layers (optifine + mcpatcher paths)
- `Affinity-HD-Bundle-x256.zip` — custom sky + random mob textures + colormaps
- `alto-resource-pack.zip` — modpack resource pack
- `Stratum 256x (1.12.2).zip` — HD test pack

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
- [x] **Mixin verified in-game (user-confirmed 2026-04-18)**. Earlier issues fixed across `c944b4e` (descriptor + refmap regen), `8ca2f52` (SRG name), `1113a60` (removed inverted pass check, MCPatcher layout). The `ldog$skyMixinConfirmed` one-shot log in `MixinRenderGlobal.renderSky` HEAD remains as a regression detector.

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
- [x] **(2026-04-17) LDOG pipeline FXAA with quality levels (Phase 9a.8):** `LDOGFXAAPass` ships an LDOG-original FXAA 3.11-inspired shader with tunable search-step count + edge threshold. When the post-process pipeline is on, `FXAAHandler` yields MC's fixed shader and the pipeline pass runs instead. Five quality levels (Low/Medium/High/Ultra/Extreme) via the `FXAAQuality` enum, live-adjustable in GUI.
- Notes: When the pipeline is OFF, MC's shipped fxaa.json runs as before (no quality levels). When pipeline is ON, LDOG's pass runs and the quality slider applies.

---

## Phase 8: Shader Pipeline

- [x] **Phase 8a (2026-04-17):** pass contract, pipeline shell, no-op pass, mixin lifecycle hook on `EntityRenderer.renderWorldPass`, `RenderTargetManager` (scaled color texture + depth RBO + ping-pong target), lifecycle wiring, fault-tolerant pass disabling, config + GUI toggle for `enablePostProcessPipeline` + `internalRenderScale`.
- [x] **Phase 8b (2026-04-17):** `PipelineDebugStats`, perf overlay row, first-enable log with coexistence posture, watchdog WARN if binding guards never pass.
- [x] **Phase 8c (2026-04-17):** binding hook redirects world rendering into scaled scene target, `@Redirect` on vanilla viewport call, RETURN-time blit-back to main framebuffer. Yields to MSAA (MSAA owns its own FBO swap). Parity tested at 1.0 and scaled; user confirmed visible softness at 0.5 scale before 9a shader passes landed.
- [x] **`ShaderProgram` utility shipped (2026-04-17):** compile + link + uniform setters + cached location lookups, used by all 9a.2+ shader passes.
- Deep-dive planning and feasibility reference: `docs/P8_RESEARCH_AND_PLAN.md`

---

## Phase 9: Upscaling (FSR) + Post-Process Chain

- [x] **9a.1 (2026-04-17):** `BilinearBlitPass` extracted from mixin into its own pass. Pipeline registers passes by algorithm with `isEnabled()` gating.
- [x] **9a.2 (2026-04-17):** `FSR1EASUPass` shipped — LDOG-original unsharp-mask-on-bilinear with contrast-adaptive strength. GLSL 120.
- [x] **9a.3 (2026-04-17):** FSR1 sharpness slider (0.0 — 2.0) live-tunable in GUI. User-verified edge crispness deltas.
- [x] **9a.4 (2026-04-17):** `FSR1QualityPass` — direction-biased EASU variant. Sobel edge detection + anisotropic sampling along detected edge + contrast-adaptive sharpen. Noticeably crisper on diagonals. Third upscaler option alongside Bilinear and basic FSR1.
- [x] **9a.5 (2026-04-17):** `UpscalerPreset` enum — one-click bundles (Native / Ultra / Quality / Balanced / Performance / Custom). Auto-flips to Custom when individual controls are edited.
- [x] **9a.6 (2026-04-17):** `RCASSharpenPass` — post-upscale sharpen via `glCopyTexSubImage2D` approach. Works at any scale including 1.0 (pure sharpen mode for native-res users). Config + slider.
- [x] **9a.7 (2026-04-17):** `LDOGPreset` enum — whole-mod presets (Vanilla / Performance / Default / Fancy / Ultra / Custom). Renders at top of settings list. `LDOGPreset.apply()` sets AA/FXAA/ExtBorder/water change flags so `saveAndClose` triggers the right reloads.
- [x] **9a.8 (2026-04-17):** `LDOGFXAAPass` — LDOG-original FXAA 3.11-inspired shader with 5 quality levels. Pipeline FXAA replaces MC's fixed FXAA when pipeline is on; `FXAAHandler` unloads MC's shader in that case. See Phase 7d for details.
- [~] **9a.9 (2026-04-17, UNVERIFIED):** `AutoScaleHandler` — target-FPS dynamic resolution scaling. Every 2s (40 client ticks), compares `Minecraft.getDebugFPS()` against `min(Display.getDesktopDisplayMode().getFrequency(), gameSettings.limitFramerate)` and steps `internalRenderScale` through a 5-tier ladder {1.00, 0.85, 0.75, 0.67, 0.50}. 0.9x/1.1x threshold dead-zone prevents tier-bouncing at target. Config + GUI toggle. **Open: user's test showed no `LDOG: AutoScale …` log output.** Either FPS stayed in the dead zone (no tier change = no log under current implementation) or the handler isn't firing. Next-session action: add a one-shot "AutoScale handler tick running" log on first invocation to distinguish the two cases.
- [ ] **9b quality tuning + validation (next):** test matrix across resource packs. Doc-only; no code change until user runs the protocol. See `docs/POST_9A4_RESEARCH.md`.
- [x] **9c.1 (2026-04-17, verified):** jittered-projection TAA MVP. `JitterHelper` generates Halton(2, 3) sub-pixel offsets over a 16-frame cycle. `TAAAccumulatePass` with neighborhood-clamped history blend. **Initial bug:** jitter injection targeted `setupCameraTransform`, but `renderWorldPass` overwrites projection afterward — jitter was a no-op. Fixed by moving the injection to `renderWorldPass` at both sky + terrain gluPerspective ordinals. User-verified post-fix.
- [x] **9c.2 (2026-04-17, verified):** camera motion vectors. `CameraState` singleton captures jittered viewProj + invCurViewProj + prevViewProj at the terrain-projection injection point (after jitter is applied). Scene depth attachment moved from RBO to `GL_DEPTH24_STENCIL8` texture (GL_NEAREST) for shader sampling. TAA shader reconstructs world-space from NDC + depth + invCurViewProj, reprojects via prevViewProj, samples history at the reprojected UV. Disocclusion check: reprojected UV outside [0,1] → skip history. `ShaderProgram.setUniformMatrix4` added. **Initial bug:** captured un-jittered matrices while history stored jittered pixels — classic TAA math error producing "drunk / swimming" visuals. Fixed by capturing AFTER `applyJitter()` so cur/prev matrices align with stored history. User-verified post-fix: camera pan is much smoother, static-scene detail accumulates correctly.
- [ ] **9c.3+ (deferred):** entity MV (2-4 weeks, invasive), FSR2-style reconstruction (weeks), reactive mask + polish. 9c.3 is the natural next step for ghost-free mobs. Evaluate against `docs/PHASE_9C_TEMPORAL_DEEP_DIVE.md` risk matrix before committing.
- Deep-dive planning: `docs/P8_RESEARCH_AND_PLAN.md` (spatial family), `docs/POST_9A4_RESEARCH.md` (next-step menu), `docs/PHASE_9C_TEMPORAL_DEEP_DIVE.md` (temporal feasibility).

**Concept:** AMD FidelityFX Super Resolution 1.0 (spatial upscaler). Render the scene at reduced resolution to an FBO, then apply FSR's sharpening/upscale pass to output at native resolution. Works on any GPU (AMD, NVIDIA, Intel) — no vendor lock-in unlike DLSS.

**Why FSR 1.0 and not DLSS:**
- DLSS requires NVIDIA SDK (native C++ / JNI), DirectX 12 or Vulkan (not OpenGL), motion vectors, and depth buffer access. Fundamentally incompatible with MC 1.12.2's OpenGL 2.1 pipeline.
- FSR 1.0 is a single spatial post-processing pass (EASU + RCAS). Can be implemented as a GLSL shader applied to a framebuffer texture. No temporal data or motion vectors needed.

**Prerequisites:** FBO rendering pipeline from Phase 8 (shaders). FSR would be a post-processing pass applied after the scene is rendered to an FBO but before display.

---

## Phase 10: Borderless Windowed Fullscreen

- [x] **Shipped 2026-04-17 (restart-required mode):** core plugin reads the LDOG config file directly (before ConfigManager initializes), sets `org.lwjgl.opengl.Window.undecorated=true` if the flag is on, MC's Display is then created undecorated for the session. A mixin on `Minecraft.toggleFullscreen` replaces exclusive fullscreen with resize-to-desktop + position (0,0) when the feature is active. Vanilla behavior is untouched when the config is off.
- [x] **Flicker fix (2026-04-17):** `Display.setDisplayMode` was being called with the full `DisplayMode` from `Display.getDesktopDisplayMode()` which carries refresh/bpp metadata; LWJGL 2.9.4 on Windows interpreted that as fullscreen mode-switch intent. Stripped to a plain `new DisplayMode(w, h)` and reordered the sequence (setResizable, setLocation, then setDisplayMode) so the window doesn't center-then-jump. Also added per-step timing logs.
- [x] **Windows Fullscreen Optimizations dodge + user toggle (2026-04-17):** window sized `desktop_h - 1` by default so Win10/11 DWM doesn't auto-transition into optimized-borderless-fullscreen (that transition was the remaining desktop flash). Toggle available in the GUI under `Display → Block FS Optim`. Trade-off: ON = flicker-free but taskbar visible, OFF = clean taskbar-hidden but brief transition flash. Default ON.
- [x] **Startup sizing fix (2026-04-17, pending user verification):** root-caused by tracing `Minecraft.startGame` — the fullscreen-at-startup path actually goes through `toggleFullscreen()` at line 601-604 of `Minecraft.java`, not `setInitialDisplayMode`. Vanilla's toggleFullscreen calls `this.resize(displayWidth, displayHeight)` which invokes `currentScreen.onResize`; our handler only called `updateFramebufferSize` directly, so the framebuffer resized but the already-displayed main menu never got a layout pass. Fixed by replacing the direct dims-assignment + updateFramebufferSize call with `mc.resize(w, h)` in both enter/exit paths. Side benefit: also fixes F11 toggles while a settings screen is open.
- **Known trade-off:** undecorated is a session-level flag, so windowed mode loses title bar / resize grips. Documented clearly in the GUI tooltip. Dragging requires Alt+drag (Windows) or keyboard window movement.
- **Future work (Phase 10b):** runtime-togglable version via `Display.destroy()` + `Display.create()` + coordinated LDOG subsystem GL cleanup. Higher risk; deferred. Also: the `blockFullscreenOptimizations` flag is read from `LDOGConfig` defaults at startup time because ConfigManager hasn't synced yet — could be upgraded to use the same early-config read path as `borderlessFullscreen` itself.

**Concept:** Vanilla MC 1.12.2 only supports exclusive fullscreen (`F11` toggles a true fullscreen mode that grabs the display). Add an LDOG option to switch fullscreen to **borderless windowed** — a maximized frameless window covering the whole screen. Behaves like fullscreen visually but leaves alt-tab instant, multi-monitor cursor movement unbroken, and discord/browser overlays functional.

**Implementation sketch:**
- Hook `Minecraft.toggleFullscreen()` via mixin.
- When `LDOGConfig.borderlessFullscreen` is true, instead of calling `Display.setFullscreen(true)`:
  - `Display.setFullscreen(false)`
  - `Display.setDisplayMode(desktopDisplayMode)` (match native desktop size)
  - Use LWJGL `System.setProperty("org.lwjgl.opengl.Window.undecorated", "true")` before the window is recreated, OR recreate the Display with undecorated flag.
  - Position the window at (0, 0).
- When toggling back off, restore the pre-fullscreen windowed size/position and re-enable decorations.

**Risks / gotchas:**
- LWJGL 2.9.4 (what MC 1.12.2 uses) has limited borderless support — `undecorated` is set at Display creation, not toggleable. May need to recreate the Display entirely, which is invasive (reloads GL context, resource packs).
- Multi-monitor: need to pick the correct monitor (the one MC was on).
- Aspect ratio: if the user's desktop resolution doesn't match, the pre-fullscreen windowed size should be preserved for toggling back.

**Priority:** quality-of-life, post-Phase 9.

**Architectural reference (concept-level only):** `hancin/Fullscreen-Windowed-Minecraft` on GitHub is a long-standing mod solving this exact problem for older MC versions — worth reading for how it handles the Display-recreation flow, monitor selection, and windowed-size restore. **Do not copy code.** Per `docs/P8_RESEARCH_AND_PLAN.md` policy: external projects are design references only; all runtime code stays LDOG-original. Document which concepts were adopted and how LDOG implemented them independently.

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
