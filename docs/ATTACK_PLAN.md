# LDOG Attack Plan

A phased development plan for building out Limitless Development Optigame, from foundation to full OptiFine replacement.

---

## Resume Prompt

> We're building LDOG (`ldog`), an open-source OptiFine replacement for Minecraft Forge 1.12.2. The project is at `E:\gitRepos\LDOF`. The build system is GregTechCEu Buildscripts (RetroFuturaGradle 1.4.0). Reference projects for conventions are at `E:\gitRepos\minecraft-city-super-mod` and `E:\gitRepos\LDFAWE`. Read `CLAUDE.md`, `docs/ATTACK_PLAN.md`, and `docs/ARCHITECTURE.md` to get up to speed, then check off what's been completed and pick up the next unchecked item.

---

## Phase 0: Foundation (Build System + Core Scaffolding)

Get the mod compiling, launching, and testable before writing any features.

- [x] Replace outdated build.gradle with v1762213476 (RFG 1.4.0)
- [x] Create `buildscript.properties` with LDOG mod config
- [x] Slim `gradle.properties` to Gradle-only settings
- [x] Scaffold package structure (`com.limitlessdev.ldog`)
- [x] Create `LDOGMod.java` entry point with sided proxy
- [x] Create `LDOGConfig.java` with feature toggles
- [x] Create `OptiFineCompat.java` for runtime detection
- [x] Update `mcmod.info` and `pack.mcmeta`
- [x] Initialize git repo
- [x] Create `CLAUDE.md`, `docs/FEASIBILITY.md`, `docs/ARCHITECTURE.md`
- [x] Add MixinBooter 10.7 (matching alto modpack) via `mixinProviderSpec` in `buildscript.properties`
- [x] Verify `./gradlew setupDecompWorkspace` succeeds
- [x] Verify `./gradlew build` produces a valid jar
- [x] Verify `./gradlew runClient` launches Minecraft with the mod loaded
- [x] Create initial git tag `v0.0.1-alpha` after first successful build

**Definition of done:** The mod loads in-game, shows up in the mod list, config file generates, OptiFine detection works (logs the correct message based on presence/absence).

---

## Phase 1: Rendering Optimizations

Start with perf improvements -- immediate value, low risk, and builds familiarity with the render pipeline.

### 1A: Chunk Rendering Optimizations
- [x] Research vanilla `RenderChunk` and `ChunkRenderDispatcher` internals
- [x] Analyzed empty chunk section skip -- vanilla already has `ChunkCache.isEmpty()` guard
- [x] Analyzed frustum culling -- vanilla plane computation is already efficient (once per frame)
- [x] Add F3 debug overlay with LDOG optimization stats

### 1B: Entity Rendering Optimizations
- [x] Mixin: Skip rendering entities outside configurable render distance
- [x] Mixin: Reduce entity rendering frequency for distant entities (LOD-style)
- [x] Mixin: Particle frustum culling for off-screen particles

### 1C: Memory / Startup Optimizations
- [ ] Deferred to Phase C2 (Vintage Fix / Censored ASM absorption)

**Definition of done:** Measurable FPS improvement in a test world with many chunks/entities loaded. No visual glitches. All optimizations independently toggleable.

---

## Phase 2: HD Texture Support

Low difficulty, high compatibility payoff -- enables resource pack features that follow.

- [x] Research `TextureMap.loadTextureAtlas()`, `TextureAtlasSprite`, and `Stitcher`
- [x] Mixin: Fix crash on non-square textures (vanilla throws RuntimeException)
- [x] Analyzed stitcher: vanilla already supports any sprite size up to GL max (no 16x16 limit)
- [x] Analyzed mipmap padding: Stitcher rounds up to 2^mipmapLevel automatically
- [ ] Test with a 32x and 64x resource pack
- [ ] Test with a 128x+ resource pack (stress test atlas size)

**Definition of done:** A 64x resource pack loads and renders correctly with LDOG installed, without OptiFine.

---

## Phase 3: Connected Textures (CTM)

High user demand. Study ConnectedTexturesMod (MIT) for reference.

- [x] Implement OptiFine `.properties` file parser for CTM definitions (CTMProperties)
- [x] Implement CTM connection logic: full 47-tile, horizontal, vertical (CTMLogic)
- [x] Create `CTMBakedModel` wrapper extending Forge `BakedModelWrapper` with retexture utility
- [x] Create `CTMRegistry` with ModelBakeEvent + TextureStitchEvent hooks
- [x] Support `assets/minecraft/optifine/ctm/` and `assets/minecraft/ldog/ctm/` paths
- [ ] Complete neighbor-aware texture swapping in CTMBakedModel.getQuads() (needs IBlockAccess context)
- [ ] Test with vanilla glass panes, bookshelves
- [ ] Test with an OptiFine-format resource pack that uses CTM

**Definition of done:** Glass panes connect seamlessly using an OptiFine-format resource pack, without OptiFine installed.

---

## Phase 4: Emissive Textures

High visual impact, moderate difficulty.

- [x] Implement `_e` suffix texture detection during atlas stitching (EmissiveTextureRegistry)
- [x] Parse `emissive.properties` from resource packs (configurable suffix)
- [x] Mixin: `BlockModelRenderer` -- intercept getQuads() to add emissive quads (MixinBlockModelRenderer)
- [x] EmissiveRenderHandler: creates fullbright retextured quads for each emissive overlay
- [ ] Mixin: `RenderItem` -- emissive layer for items in inventory/hand
- [ ] Test with a resource pack that has emissive textures
- [ ] Verify no depth-fighting between base and emissive layers

**Definition of done:** Blocks and items with `_e` suffix textures glow at full brightness regardless of ambient light level.

---

## Phase 5: Dynamic Lights

Popular feature, moderate complexity. Performance is the main challenge.

- [ ] Create `DynamicLightSource` interface and registry
- [ ] Track light-emitting entities (held torch, dropped glowstone, burning entities)
- [ ] Mixin: `World.getLight()` (client-side) -- inject dynamic light values
- [ ] Implement spatial indexing for light sources (avoid iterating all entities)
- [ ] Add configurable update interval (every tick vs. every N ticks)
- [ ] Add configurable light source list (which items/entities emit light)
- [ ] Profile: measure overhead with 0, 10, 50, 100 dynamic light sources
- [ ] Ensure lights don't persist after source is removed (cleanup)

**Definition of done:** Holding a torch in your hand illuminates the area around you in real-time, with minimal FPS impact.

---

## Phase 6: Resource Pack Features

A collection of smaller features that enhance resource pack support.

### 6A: Custom Sky
- [ ] Parse `sky/` properties files from resource packs
- [ ] Mixin: `RenderGlobal.renderSky()` -- replace sky rendering with custom cubemap
- [ ] Support multiple sky layers with blend modes
- [ ] Support custom sun/moon textures
- [ ] Test with an OptiFine sky resource pack

### 6B: Custom Colors
- [ ] Parse `color.properties` from resource packs
- [ ] Mixin hooks for: biome grass color, foliage color, water color, sky color
- [ ] Mixin hooks for: potion colors, map colors, dye colors
- [ ] Test with a resource pack that overrides biome colors

### 6C: Natural Textures
- [ ] Parse `natural.properties` from resource packs
- [ ] Mixin: `BlockModelRenderer` -- apply random rotation/flip to specified block faces
- [ ] Ensure rotations are deterministic per-position (no flickering on chunk rebuild)

### 6D: Random Mobs / Entity Textures
- [ ] Parse `random/` entity texture folders from resource packs
- [ ] Mixin: Entity renderers -- select texture variant based on entity UUID
- [ ] Support per-biome and per-height rules

### 6E: Better Grass / Better Snow
- [ ] Mixin: Extend grass block side textures to match top when surrounded by grass
- [ ] Same treatment for snow layers
- [ ] Config toggles for each

**Definition of done:** Resource packs with OptiFine-format custom sky, colors, natural textures, and random mobs work without OptiFine.

---

## Phase 7: Anti-Aliasing and Anisotropic Filtering

Mostly GL state configuration. Quick wins.

- [ ] Add config options for AA level (off, 2x, 4x, 8x) and AF level (off, 2x, 4x, 8x, 16x)
- [ ] Apply `GL_TEXTURE_MAX_ANISOTROPY_EXT` to block atlas texture
- [ ] Implement FXAA post-processing pass via simple shader (if no full shader pipeline yet)
- [ ] Test visual quality vs. performance impact at each level

**Definition of done:** Anisotropic filtering visibly improves distant texture clarity. FXAA smooths jagged edges.

---

## Phase 8: Shader Pipeline (Stretch Goal)

The big one. Only attempt after Phases 1-7 are solid.

### 8A: Foundation
- [ ] Study ShadersMod source (LGPL) for the original pipeline architecture
- [ ] Study OptiFine's shader API documentation (uniform names, buffer bindings, pass order)
- [ ] Implement `ShaderProgram` class (compile, link, validate GLSL programs)
- [ ] Implement `ShaderUniform` providers (time, sun position, camera, world info)
- [ ] Implement basic FBO (framebuffer object) management

### 8B: Render Pipeline Replacement
- [ ] Mixin: `EntityRenderer.renderWorld()` -- redirect through shader pipeline
- [ ] Implement GBuffer pass (diffuse, normal, depth, specular)
- [ ] Implement shadow map pass
- [ ] Implement composite passes (post-processing chain)
- [ ] Implement deferred lighting pass

### 8C: Shader Pack Loading
- [ ] Implement shader pack directory/zip loading
- [ ] Parse `shaders.properties` for pack configuration
- [ ] Implement shader pack selection GUI
- [ ] Hot-reload support (recompile shaders without restart)

### 8D: Compatibility
- [ ] Test with SEUS Renewed
- [ ] Test with BSL Shaders
- [ ] Test with Sildur's Vibrant Shaders
- [ ] Test with Complementary Shaders
- [ ] Document which uniforms/features are supported vs. not yet implemented

**Definition of done:** At least one popular shader pack (BSL or Sildur's) loads and renders correctly with basic shadows, lighting, and water effects.

---

## Phase C1: Quick Mod Absorptions (During Phase 1)

Trivial standalone mods that can be folded into LDOG immediately.

### C1A: FPS Reducer
- [ ] Detect window focus loss (LWJGL / `Minecraft.isGameFocused()`)
- [ ] Track last input time for AFK detection
- [ ] Reduce frame rate when unfocused/AFK (`Display.sync()` or sleep)
- [ ] Config: `unfocusedFpsLimit` (default 5), `afkTimeoutSeconds` (default 300), `afkFpsLimit` (default 15)

### C1B: Clear Water
- [ ] Mixin: Override water color/alpha in water render path
- [ ] Config: `enableClearWater` (default true), `waterTransparency` (0.0-1.0)

**Definition of done:** FPS Reducer and Clear Water mods can be removed from the alto modpack.

---

## Phase C2: Memory Optimization Absorption (After Phase 2)

Absorb Vintage Fix and Censored ASM (LoliASM) functionality.

### C2A: From Vintage Fix
- [ ] Research VintageFix source and license (GPL-3.0 via FoamFix)
- [ ] Model deduplication: Content-hash and share identical BakedQuad/IBakedModel instances
- [ ] BlockState compaction: Array-backed property storage instead of ImmutableMap
- [ ] Dynamic model loading: Lazy-load models, evict unused
- [ ] Property value interning
- [ ] Measure heap before/after with full alto modpack

### C2B: From Censored ASM (LoliASM)
- [ ] Research LoliASM source and license
- [ ] BakedQuad vertex data deduplication
- [ ] Texture sprite pixel data deduplication
- [ ] IBlockState lookup canonicalization (direct-mapped cache)
- [ ] NBT tag name string interning
- [ ] Class loading optimization (LaunchClassLoader improvements)
- [ ] Verify no overlap with C2A features

**Definition of done:** Vintage Fix and Censored ASM can be removed from the alto modpack. Heap usage is equal or better.

---

## Cross-Cutting Concerns (All Phases)

These apply throughout development, not to any single phase.

- [ ] Every feature gated by config toggle AND `OptiFineCompat.shouldHandle*()`
- [ ] Every Mixin documented: target class, method, why
- [ ] Performance profiled before merging each feature
- [ ] Zero overhead when a feature is disabled
- [ ] Test with and without OptiFine installed at each phase gate
- [ ] Keep `CLAUDE.md` and `docs/ARCHITECTURE.md` updated as code evolves
- [ ] Git tag at each phase completion (`v0.1.0`, `v0.2.0`, etc.)

---

## Version Milestones

| Version | Phase | What Users Get |
|---|---|---|
| `v0.0.1-alpha` | Phase 0 | Mod loads, nothing visible yet |
| `v0.1.0-alpha` | Phase 1 + C1 | FPS improvements, FPS reducer, clear water (replaces 3 mods) |
| `v0.2.0-alpha` | Phase 2 | HD resource pack support |
| `v0.2.5-alpha` | Phase C2 | Memory optimizations (replaces Vintage Fix + Censored ASM) |
| `v0.3.0-alpha` | Phase 3 | Connected textures |
| `v0.4.0-alpha` | Phase 4 | Emissive textures |
| `v0.5.0-alpha` | Phase 5 | Dynamic lights |
| `v0.6.0-alpha` | Phase 6 | Full resource pack feature parity |
| `v0.7.0-alpha` | Phase 7 | AA/AF options |
| `v1.0.0-beta` | Phase 8 | Shader support -- OptiFine fully replaceable |

At full maturity, LDOG replaces **5-7 separate mods** in the alto modpack.
