# Phase 1 Research: Rendering Optimizations

## Alto Modpack Optimization Landscape

The alto modpack already includes these optimization mods. LDOG must either **integrate** (absorb their functionality), **coexist** (do non-overlapping work), or **replace** (do it better). The long-term goal is to reduce the number of separate optimization mods needed.

| Mod | Function | LDOG Strategy |
|---|---|---|
| **OptiFine** | Rendering, shaders, CTM, etc. | **Replace** (primary goal) |
| **Vintage Fix 0.5.1** | FoamFix successor -- model dedup, blockstate compaction, dynamic resource loading | **Coexist then integrate** -- these are memory opts, not rendering. Low conflict risk. Eventually absorb. |
| **Censored ASM (LoliASM) 5.30** | Memory opts, class loading, texture dedup, bakedquad dedup | **Coexist then integrate** -- similar to VintageFix. Some overlap between the two. |
| **Performant 1.11** | Entity/TE tick performance, pathfinding | **Coexist** -- server-side tick optimization, doesn't touch rendering pipeline. Keep separate. |
| **Universal Tweaks 1.17.0** | Collection of vanilla fixes and tweaks | **Coexist** -- broad scope, many unrelated fixes. Cherry-pick rendering-related tweaks only. |
| **FPS Reducer** | Reduces FPS when AFK/unfocused | **Integrate** -- trivial to implement, one config + one event handler. |
| **Spark / Lag Goggles** | Profilers | **Coexist** -- diagnostic tools, no conflict. Keep as-is. |
| **Smooth Font** | TrueType font rendering | **Coexist** -- unrelated to our scope. |
| **Clear Water** | Water transparency | **Integrate later** -- simple rendering tweak, can absorb in Phase 6. |

---

## Vanilla Rendering Pipeline Analysis

### The Frame Render Path

```
EntityRenderer.renderWorldPass()           <-- Entry point, called every frame
  +-- updateLightmap()                     <-- Lightmap texture update
  +-- setupCameraTransform()               <-- Matrix setup
  +-- new Frustum() / setPosition()        <-- Frustum creation (EVERY FRAME!)
  +-- RenderGlobal.setupTerrain()          <-- Determine visible chunks (BFS traversal)
  |     +-- 11 position/rotation checks    <-- Recalc if ANY viewpoint var changed
  |     +-- VisGraph per player chunk      <-- Visibility graph computation
  |     +-- BFS chunk neighbor traversal   <-- Queue-based, 6 neighbors per chunk
  +-- RenderGlobal.renderBlockLayer()      <-- x4 layers (SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT)
  |     +-- GL state enable/disable loop   <-- Per-layer vertex format iteration
  |     +-- Per-chunk VBO/display list draw
  +-- RenderGlobal.renderEntities()        <-- All visible entities
  |     +-- Per-chunk entity list iteration
  |     +-- shouldRender() check per entity
  +-- Particle rendering
  +-- Weather, sky, clouds
```

### The Chunk Rebuild Path (off main thread, but blocks rendering)

```
RenderChunk.rebuildChunk()
  +-- Loop all 4,096 positions in chunk section
  |     +-- getBlockState() per position
  |     +-- isOpaqueCube() check
  |     +-- hasTileEntity() check
  |     +-- Loop 3-4 BlockRenderLayers per block
  |           +-- canRenderInLayer() check
  |           +-- BlockModelRenderer.renderModel()
  |                 +-- renderQuadsSmooth() per face (with AO)
  |                       +-- AmbientOcclusionFace.updateVertexBrightness()
  |                             +-- 8+ getBlockState() calls per quad
  |                             +-- 4 getAmbientOcclusionLightValue() calls
  |                             +-- 4 getPackedLightmapCoords() calls
  |                             +-- 4 isTranslucent() checks
  +-- Upload compiled buffer to GPU (synchronized on main thread)
```

---

## Concrete Optimization Opportunities

### Category A: Chunk Rendering (Highest Impact)

#### A1. Ambient Occlusion / Lighting Cache
**Target:** `BlockModelRenderer.AmbientOcclusionFace.updateVertexBrightness()`
**Problem:** For every quad rendered, vanilla calls `getBlockState()` 8+ times, `getAmbientOcclusionLightValue()` 4 times, and `getPackedLightmapCoords()` 4 times on neighboring blocks. In a 16x16x16 chunk section with ~2000 non-air blocks averaging 4 visible faces with 1 quad each, that's **~64,000 getBlockState() lookups** per chunk rebuild -- and many of those are the *same* neighbor positions queried repeatedly by adjacent blocks.

**Fix:** Create a `LightingCache` that pre-populates a 18x18x18 array (chunk + 1-block border) of lightmap/AO values at the start of `rebuildChunk()`, then redirect quad rendering to read from the cache instead of calling `getBlockState()` repeatedly.

**Impact:** Very high. This is the single biggest bottleneck in chunk rendering.
**Difficulty:** Moderate. Mixin into `rebuildChunk()` to create cache, `@Redirect` the getBlockState/AO calls.
**Conflict risk:** Moderate with OptiFine (it has its own AO changes). Gate with `OptiFineCompat`.

#### A2. Skip Air Blocks in Chunk Rebuild
**Target:** `RenderChunk.rebuildChunk()` block iteration loop
**Problem:** Vanilla iterates all 4,096 positions in a chunk section, even though most blocks are air. A typical overworld chunk section is 60-90% air.

**Fix:** Use `ExtendedBlockStorage.getData()` (the chunk's palette) to identify non-air positions, or maintain a count of non-air blocks and skip the entire section if empty.

**Impact:** Moderate. Reduces iteration count by 60-90% for most chunks.
**Difficulty:** Low. Check `ExtendedBlockStorage.isEmpty()` before iterating, or use the block count.
**Conflict risk:** Low.

#### A3. Frustum Culling Improvements
**Target:** `RenderGlobal.setupTerrain()`, `EntityRenderer.renderWorldPass()`
**Problem:** (1) A new `Frustum` object is created every frame. (2) The frustum position update threshold is only 4 blocks, causing frequent full-list recalculations. (3) Vanilla's AABB-frustum test is loose -- it doesn't cull chunks that are partially behind the player.

**Fix:** (1) Reuse Frustum object, just update its position. (2) Increase hysteresis threshold. (3) Implement tighter frustum plane tests.

**Impact:** Moderate. Reduces per-frame allocation and CPU work.
**Difficulty:** Low-moderate. `@Redirect` Frustum construction, `@ModifyConstant` for threshold.
**Conflict risk:** Low.

#### A4. Chunk Rebuild Prioritization
**Target:** `ChunkRenderDispatcher`
**Problem:** Chunks are rebuilt in a FIFO queue with no priority. A chunk directly in front of the player gets the same priority as one at the edge of render distance behind them.

**Fix:** Priority queue based on distance to player and whether the chunk is in the view frustum. Visible, close chunks rebuild first.

**Impact:** Moderate. Doesn't reduce total work but makes visual updates feel faster.
**Difficulty:** Moderate. Replace the internal queue in ChunkRenderDispatcher.
**Conflict risk:** Low.

#### A5. Batch Chunk Uploads
**Target:** `ChunkRenderDispatcher.runChunkUploads()`
**Problem:** Chunk GPU uploads happen one at a time in a synchronized block on the main thread. Each upload acquires and releases the lock.

**Fix:** Batch multiple uploads per lock acquisition. Use `ConcurrentLinkedQueue` instead of synchronized `PriorityQueue`.

**Impact:** Low-moderate. Reduces lock contention and context switching.
**Difficulty:** Moderate. Requires careful thread safety.
**Conflict risk:** Low.

---

### Category B: Entity Rendering

#### B1. Entity Render Distance Culling
**Target:** `RenderGlobal.renderEntities()`
**Problem:** Every entity in every visible chunk is checked with `shouldRender()`, which does a distance calculation. With high render distance and many entities (farms, villages), this is expensive.

**Fix:** Configurable entity render distance (separate from chunk render distance). Skip entities beyond this distance entirely, without even calling `shouldRender()`.

**Impact:** High in entity-dense areas.
**Difficulty:** Low. Simple distance check before the shouldRender call.
**Conflict risk:** Low.

#### B2. Entity LOD (Tick Rate Reduction)
**Target:** `RenderGlobal.renderEntities()`
**Problem:** Distant entities are rendered at full fidelity every frame even though the player can't see the detail.

**Fix:** Reduce render frequency for distant entities (e.g., entities >64 blocks away only render every other frame, >128 blocks every 4th frame).

**Impact:** Moderate. Reduces vertex transformation work.
**Difficulty:** Low. Frame counter + distance check.
**Conflict risk:** Low. May cause visible "stuttering" on distant entities if too aggressive -- needs good defaults.

#### B3. Tile Entity Render Distance
**Target:** `TileEntityRendererDispatcher`
**Problem:** TESR (TileEntity Special Renderer) objects are rendered regardless of distance. Modded TEs with complex renders (e.g., Immersive Engineering multiblocks) are expensive.

**Fix:** Configurable TESR render distance. Skip TESR rendering beyond a threshold.

**Impact:** High in modded environments with many TESRs.
**Difficulty:** Low. Distance check in the TESR dispatch loop.
**Conflict risk:** Low.

---

### Category C: Particle Optimizations

#### C1. Particle Frustum Culling
**Target:** `ParticleManager.renderParticles()`
**Problem:** Particles outside the view frustum are still rendered. At standard FOV, roughly 11/12 of particles from an explosion centered on the player are off-screen.

**Fix:** Check particle position against frustum before rendering.

**Impact:** Moderate during particle-heavy events (explosions, mob farms).
**Difficulty:** Low. AABB frustum test per particle (or per particle batch).
**Conflict risk:** Low.

#### C2. Particle Count Limiting
**Target:** `ParticleManager`
**Problem:** Particle count can spike during explosions or redstone dust areas, causing frame drops.

**Fix:** Configurable particle count limit. When exceeded, skip spawning new particles or remove oldest.

**Impact:** Low-moderate. Prevents worst-case frame drops.
**Difficulty:** Low. Counter + early return in addEffect().
**Conflict risk:** Low.

---

### Category D: Memory / Allocation Optimizations

#### D1. Reduce Per-Frame Allocations
**Target:** Various render path classes
**Problem:** Vanilla allocates ~128MB/s during rendering. Key offenders: `Vec3d` creation in entity rendering, `AxisAlignedBB` in collision checks, `BlockPos` creation in lighting lookups.

**Fix:** Use thread-local pooled objects or mutable variants where safe. Replace `new Vec3d()` with cached instances in hot paths.

**Impact:** Moderate. Reduces GC pressure and pause frequency.
**Difficulty:** Moderate. Must be careful with thread safety and object lifetime.
**Conflict risk:** Low.

#### D2. FPS Reducer (Absorb from standalone mod)
**Target:** `Minecraft.runGameLoop()`
**Problem:** Game renders at full FPS even when minimized or AFK, wasting CPU/GPU.

**Fix:** Detect window focus loss and AFK state, reduce frame rate to configurable minimum (e.g., 5 FPS).

**Impact:** Low for gameplay, high for system resources when AFK.
**Difficulty:** Very low. One event handler + frame limiter.
**Conflict risk:** None. Replaces the standalone FPS Reducer mod.

---

### Category E: GL State Optimizations

#### E1. Cache Vertex Format State
**Target:** `RenderGlobal.renderBlockLayer()` GL state setup
**Problem:** Every block layer render iterates through `VertexFormatElement` array to enable/disable GL client states. This is the same format every time.

**Fix:** Cache the enable/disable calls. Only change GL state when the format actually changes.

**Impact:** Low. Small per-frame savings.
**Difficulty:** Very low. Static boolean + skip logic.
**Conflict risk:** Low.

---

## Recommended Phase 1 Implementation Order

Based on impact, difficulty, and conflict risk:

| Priority | Optimization | Impact | Difficulty | Conflict Risk |
|---|---|---|---|---|
| **1** | A2: Skip air blocks in rebuild | Moderate | Low | Low |
| **2** | B1: Entity render distance | High | Low | Low |
| **3** | B3: Tile entity render distance | High | Low | Low |
| **4** | A3: Frustum culling improvements | Moderate | Low-Med | Low |
| **5** | C1: Particle frustum culling | Moderate | Low | Low |
| **6** | A1: AO/Lighting cache | Very High | Moderate | Moderate |
| **7** | B2: Entity LOD | Moderate | Low | Low |
| **8** | C2: Particle count limiting | Low-Med | Low | Low |
| **9** | D2: FPS reducer (absorb) | Low-Med | Very Low | None |
| **10** | A4: Chunk rebuild prioritization | Moderate | Moderate | Low |
| **11** | E1: Cache vertex format state | Low | Very Low | Low |
| **12** | A5: Batch chunk uploads | Low-Med | Moderate | Low |
| **13** | D1: Reduce per-frame allocations | Moderate | Moderate | Low |

The order prioritizes quick wins first (low difficulty, low conflict) to build momentum and get testable improvements early. The AO/Lighting cache (A1) is the highest-impact single optimization but is placed at #6 due to its moderate difficulty and conflict risk -- by the time we get there, we'll have experience writing rendering Mixins.

---

## Mixin Targets Summary

| Vanilla Class | Method | Optimizations |
|---|---|---|
| `RenderChunk` | `rebuildChunk()` | A1 (cache init), A2 (skip air) |
| `BlockModelRenderer` | `renderQuadsSmooth()` | A1 (cache reads) |
| `BlockModelRenderer.AmbientOcclusionFace` | `updateVertexBrightness()` | A1 (cache reads) |
| `RenderGlobal` | `setupTerrain()` | A3 (frustum), A4 (rebuild priority) |
| `RenderGlobal` | `renderEntities()` | B1 (entity distance), B2 (entity LOD) |
| `RenderGlobal` | `renderBlockLayer()` | E1 (GL state cache) |
| `EntityRenderer` | `renderWorldPass()` | A3 (frustum reuse) |
| `ChunkRenderDispatcher` | `runChunkUploads()` | A5 (batch uploads) |
| `TileEntityRendererDispatcher` | `render()` | B3 (TE distance) |
| `ParticleManager` | `renderParticles()` | C1 (frustum cull) |
| `ParticleManager` | `addEffect()` | C2 (count limit) |
| `Minecraft` | `runGameLoop()` | D2 (FPS reducer) |

---

## Compatibility Notes

### VintageFix / Censored ASM Overlap
Both VintageFix and Censored ASM (LoliASM) focus on **memory** optimizations (model dedup, texture dedup, class loading). Our Phase 1 focuses on **rendering** optimizations, so there should be minimal overlap. The main area to watch:
- VintageFix's dynamic model loading touches `ModelManager` and texture atlas code -- if we do HD texture work (Phase 2) or A1 (block model rendering), test carefully.
- Censored ASM's BakedQuad deduplication changes quad data layout -- our A1 lighting cache reads quad vertex data, so verify it still works.

### Performant Overlap
Performant optimizes entity/TE **ticking** (server-side logic), not rendering. Our B1/B2/B3 optimize entity/TE **rendering** (client-side). These are complementary, not conflicting.

### Universal Tweaks
Universal Tweaks includes some rendering-related fixes. We should check its config for any rendering tweaks it applies and ensure we don't double-patch the same methods. Key areas to check: entity rendering, particle rendering.
