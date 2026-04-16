# LDOG Attack Plan

A phased development plan for building out Limitless Development Optigame, from foundation to full OptiFine replacement.

---

## Resume Prompt

> We're building LDOG (`ldog`), an open-source OptiFine replacement for Minecraft Forge 1.12.2. The project is at `E:\gitRepos\LDOF`. The build system is GregTechCEu Buildscripts (RetroFuturaGradle 1.4.0). Reference projects for conventions are at `E:\gitRepos\minecraft-city-super-mod` and `E:\gitRepos\LDFAWE`. Read `CLAUDE.md`, `docs/ATTACK_PLAN.md`, and `docs/ARCHITECTURE.md` to get up to speed, then check off what's been completed and pick up the next unchecked item.

### Where We Left Off (2026-04-15)

**Phases 1-5 implemented and tested.** All core features working in-game.

- **Phase 1** (rendering optimizations, FPS reducer, clear water): complete
- **Phase 2** (HD textures): structurally complete, needs testing with 32x/64x/128x packs
- **Phase 3** (CTM): working — glass blocks, glass panes (synthetic quads, UV mirroring, seam suppression), bookshelf horizontal CTM
- **Phase 4** (emissive textures): working — ore glow overlays verified in-game
- **Phase 5** (dynamic lights + lighting): working — dynamic lights with smooth mode, full lightmap customization (block/sky color, night darkness, brightness, HDR, 13 presets)

**Key next steps:**
1. Phase 2: test with HD resource packs (32x, 64x, 128x)
2. Phase 4: RenderItem emissive layer for items in inventory/hand
3. Phase 6-8: resource pack features, AA/AF, shaders
4. Phase 9: FSR upscaling (requires Phase 8 FBO pipeline)

**Test resource packs (already in run/resourcepacks/):**
- `default-1-12` (extracted) -- CTM glass + glass panes (47-tile)
- `Faithful 32x - 1.12.2` (extracted) -- CTM glass + bookshelf
- `emissive-ores-1-12-2` (extracted) -- emissive ore textures

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
- [ ] Test with 32x, 64x, 128x resource packs

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
- [ ] Emissive layer may need fullbright lightmap injection (current approach adds quads but lighting may override the glow)
- [ ] RenderItem emissive layer for items in inventory/hand

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

## Phase 6-8: Resource Pack Features, AA/AF, Shaders

- [ ] Not started

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
