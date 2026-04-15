# LDOG Attack Plan

A phased development plan for building out Limitless Development Optigame, from foundation to full OptiFine replacement.

---

## Resume Prompt

> We're building LDOG (`ldog`), an open-source OptiFine replacement for Minecraft Forge 1.12.2. The project is at `E:\gitRepos\LDOF`. The build system is GregTechCEu Buildscripts (RetroFuturaGradle 1.4.0). Reference projects for conventions are at `E:\gitRepos\minecraft-city-super-mod` and `E:\gitRepos\LDFAWE`. Read `CLAUDE.md`, `docs/ATTACK_PLAN.md`, and `docs/ARCHITECTURE.md` to get up to speed, then check off what's been completed and pick up the next unchecked item.

### Where We Left Off (2026-04-14)

**Phases 1-4 implemented.** Phase 1 (rendering optimizations, FPS reducer, clear water) is working. Phases 2-4 are structurally complete but have bugs being worked through:

**CTM (Phase 3) -- partially working:**
- Properties files are discovered (mcpatcher/ctm paths), 5 CTM definitions loaded, 21 models wrapped
- CTMSprite custom loader works (tiles load into atlas without crash)
- Glass blocks render but **tile index mapping is still wrong** -- glass shows some textures but corners/edges don't match the correct connected pattern
- Likely issue: the 47-tile lookup table in `CTMLogic.buildCTMMap()` doesn't match the actual OptiFine tile numbering convention. Need to compare against an OptiFine reference or dump the actual tile images to verify the mapping.
- Resource packs tested: Faithful 32x - 1.12.2 (uses `mcpatcher/ctm` path, numeric block IDs)

**Emissive (Phase 4) -- needs testing:**
- Previous blocker was that `mapRegisteredSprites` is cleared before `TextureStitchEvent.Pre` fires, so sprite enumeration found nothing
- Fixed by scanning resource pack files directly (both dirs and zips) for `*_e.png` files
- Last commit (`3325e11`) has this fix but hasn't been tested in-game yet
- Resource pack: `emissive-ores-1-12-2` (has `_e.png` files under `textures/blocks/`, `emissive.properties` with `suffix.emissive=_e`)

**Key debugging steps for next session:**
1. Run the game, check log for `LDOG: Found emissive:` lines -- should show 8 emissive ores if the scanning fix works
2. If emissives still don't show, check if `MixinBlockModelRenderer` is actually applying (look for the mixin in the late config)
3. For CTM tile mapping, place a 3x3 glass wall and compare the rendered tiles against the actual tile PNGs in `Faithful 32x - 1.12.2/assets/minecraft/mcpatcher/ctm/glass/glass/` to identify which tiles map to which positions
4. The CTM tile numbering likely follows the standard "blob tileset" convention used by MCPatcher -- may need to find documentation of the exact mapping

**Test resource packs (already in run/resourcepacks/):**
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
- [ ] **BUG: Tile index mapping doesn't match OptiFine convention** -- glass renders with wrong corners/edges
- [ ] Test bookshelf horizontal CTM
- [ ] Test glass pane CTM

---

## Phase 4: Emissive Textures

- [x] EmissiveTextureRegistry: scans resource packs for *_e.png files directly
- [x] Reads optifine/emissive.properties and mcpatcher/emissive.properties for suffix config
- [x] EmissiveRenderHandler: creates fullbright retextured quads (UV remap to emissive sprite)
- [x] MixinBlockModelRenderer: intercepts getQuads() in both smooth and flat paths
- [ ] **NEEDS TESTING** -- last fix (direct resource pack scanning) not yet verified in-game
- [ ] Emissive layer may need fullbright lightmap injection (current approach adds quads but lighting may override the glow)
- [ ] RenderItem emissive layer for items in inventory/hand

---

## Phase 5: Dynamic Lights

- [ ] Not started

---

## Phase 6-8: Resource Pack Features, AA/AF, Shaders

- [ ] Not started

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
| `v0.4.0-alpha` | Phase 2-4 | HD textures, CTM framework, emissive framework (in progress) |
| `v0.5.0-alpha` | Phase 5 | Dynamic lights |
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
