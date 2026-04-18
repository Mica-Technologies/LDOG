# Phase 9c.3 — Option C Research: Full Per-Entity Motion Vectors

Companion to `docs/PHASE_9C_TEMPORAL_DEEP_DIVE.md`. Written after shipping Option A (entity reactive mask) on 2026-04-18.

## Why this document exists

`PHASE_9C_TEMPORAL_DEEP_DIVE.md` §3 estimates full Option C at "2-4 weeks, invasive" but stops short of a concrete architecture. This doc lays one out, identifies improvements over the naive plan, and gives the next session a runnable starting point.

---

## 1. What Option A leaves on the table

Option A (shipped: reactive mask via MRT + per-attachment colorMask) addresses the **most visually objectionable** TAA artifact — persistent ghost trails behind moving entities — by dropping history weight to zero on entity silhouettes.

What it does **not** solve:

| Problem | Why Option A doesn't fix it | Severity |
|---|---|---|
| Entity TAA quality | Reactive pixels get per-frame instability instead of accumulated detail. Entities look jaggy/shimmery in motion. | Medium — better than ghosting, worse than proper TAA |
| Particles still ghost | Particles aren't drawn from `RenderGlobal.renderEntities`, so the colorMask flip doesn't reach them. | Low — particles have small screen footprint |
| Mask is binary, not graded | Slow-moving entities get the same "drop history" treatment as fast-moving ones. Sub-pixel motion gets unnecessarily nuked. | Medium — visible on slowly-walking mobs |
| Entity-on-entity occlusion | When two entities cross, one entity's history color may bleed into the other through neighborhood clamping. | Low — rarely noticeable |
| TESR sub-pixel detail | TileEntities (banners, beacons, end portal) lose TAA accumulation despite being mostly static. | Low-medium — banners look noisier |

**Per-entity MV** fixes all of these in one shot: instead of "drop history on entity pixels," it becomes "reproject history correctly per entity," so temporal accumulation continues across the entity's screen-space path.

---

## 2. The naive Option C, refined

The deep-dive doc proposes (§3):
> Per-entity motion vectors: every `Render<T>` subclass in the forge ecosystem would need instrumentation. For vanilla, a general mixin on `RenderLivingBase.doRender` + `Render.doRender` could cover the common path. Custom TESRs would be left out — ghosting fallback.

That description is correct but understates the architecture work. The actual minimum-viable implementation needs:

1. **A second render target (MV target)** — RG16F or RG8 (signed-normalized) per-pixel screen-space velocity. **Not** an MRT attachment to `sceneFbo` because immediate-mode chunk drawing can't emit per-pixel velocities (no per-vertex previous-frame transform). Must be a separate FBO.

2. **A custom velocity-emitting shader** — bound only during entity passes. Fragment shader receives per-vertex `prevClipPos` + `curClipPos`, computes screen-space delta, writes to RG channels.

3. **Per-entity transform tracking** — for each rendered entity, capture this frame's transform matrix and the previous frame's transform matrix. Pass both to the velocity shader as uniforms (or as a vertex attribute via a dynamic VBO).

4. **Hook into entity rendering** — to capture the transform deltas and trigger the second pass per entity.

5. **TAA shader integration** — sample MV target; for entity pixels (where MV is non-zero), use MV-driven reprojection instead of camera-only depth-driven reprojection.

The naive plan re-renders entities a second time per frame — that's the "2x entity draw cost" the deep-dive flags. Below are improvements that bring this closer to "10-30% extra cost" instead of "2x."

---

## 3. Implementation strategies — comparison

### Strategy 1: Two-pass entity render with custom velocity shader (the naive baseline)

**Architecture:**
1. After scene rendering finishes (post `renderEntities`), bind MV FBO.
2. Re-iterate the entity render list using vanilla code, but with a custom shader bound that emits velocity instead of color.
3. Per-entity uniform: `cur_modelview` and `prev_modelview` matrices.
4. Vertex shader computes `prevClipPos = projection * prev_modelview * gl_Vertex` and `curClipPos = projection * cur_modelview * gl_Vertex`; passes both to fragment.
5. Fragment shader writes `(curClipPos.xy/w - prevClipPos.xy/w) * 0.5` to RG channels.

**Pros:**
- Clean separation; vanilla color path entirely untouched.
- Modded entities work automatically (same Render<T> subclass system).
- Custom shader is small and well-scoped.

**Cons:**
- 2x entity draw cost (worst case).
- Re-iterating entity list requires reaching into `RenderManager`'s state — there's no public API for "render this entity with this shader."

**Recommended refinement:** Combine with frustum culling — entities outside the view frustum skip the MV pass entirely. Most far-distance entities are LOD-skipped by Phase 1 already, so the actual "entities to MV" count is small.

### Strategy 2: MRT during entity render (single-pass)

**Architecture:**
1. Replace vanilla fixed-function entity render with a custom shader that emits to both COLOR0 (color) and COLOR1 (MV).
2. The shader needs per-entity prev/cur transforms as uniforms.

**Pros:**
- 1x entity draw cost (no re-iteration).
- All MV emission happens during the existing entity loop.

**Cons:**
- Replacing vanilla entity rendering is **catastrophically invasive**. Every entity render path uses fixed-function color, lighting, glow, alpha. Reproducing that in a custom shader == rewriting MC's entity renderer.
- Breaks compat with shader mods (OptiFine shaders, future LDOG shader passes).

**Verdict:** **Don't.** The complexity-to-value ratio is wrong.

### Strategy 3: Optical flow approximation

**Architecture:**
1. After scene render, post-process pass that compares current and previous color buffers.
2. For each pixel, search a small neighborhood for the closest color match in the previous frame; the offset is the MV.

**Pros:**
- Zero hooks into entity rendering.
- Modded compat: trivially universal.

**Cons:**
- Optical flow is **notoriously fragile**: fails on rotation, scale changes, lighting changes, partial occlusion.
- Search-neighborhood cost: O(W × H × neighborhood_size²) per frame. ~30ms at 1080p with a 7×7 search neighborhood. Tankrate.
- Best implementations (Lucas-Kanade, Horn-Schunck) are weeks of GLSL work.

**Verdict:** **Don't.** Use only as a research curiosity.

### Strategy 4: Hybrid — Strategy 1 for vanilla + reactive-mask fallback for modded (RECOMMENDED)

**Architecture:**
1. Implement Strategy 1 for vanilla entity classes only (`EntityLivingBase`, `EntityItem`, `EntityXPOrb`, etc.).
2. Modded entities and TESRs: keep using the Phase 9c.3-A reactive mask path.
3. The TAA shader checks: MV target alpha > 0 → use MV reprojection; reactive mask > 0 → drop history; else → camera-only MV.

**Pros:**
- Vanilla coverage is the bulk of the visual benefit (most rendered entities in survival are vanilla mobs/items).
- Modded compat surface stays at "reactive mask works for everything else" — no regression risk.
- Implementation can ship vanilla-only first, expand to specific modded entities later.

**Cons:**
- Two parallel code paths to maintain.
- Modded entities still have the per-frame instability of reactive mask (worse than full MV but no regression vs. shipped Option A).

**Verdict:** **Pursue this.** It's the path that lands working software in 1-2 weeks instead of "weeks-to-months."

---

## 4. Improvements over the naive plan

These reduce cost or improve quality and were not flagged in the deep-dive doc.

### 4.1 Per-entity displacement caching with `prevPosX/Y/Z`

`Entity.prevPosX/Y/Z` is updated by MC every tick (20 Hz). For frame-rate interpolation, MC uses `prevPos + (pos - prevPos) * partialTicks`. We can derive per-entity transform delta from these without re-running the entire entity render math:

```java
double interpX = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
double prevInterpX = entity.lastTickPosX + (entity.prevPosX - entity.lastTickPosX) * partialTicks;
// Note: lastTickPosX/Y/Z is what we want for "previous frame", not prevPosX/Y/Z
```

We need to track `lastTickPosX` ourselves since vanilla only stores one tick of history. Cache per-entity in a `WeakHashMap<UUID, EntityRenderState>`, evict on death/unload.

### 4.2 Velocity threshold — skip MV emission for stationary entities

Most entities at any moment are stationary (pigs idling, item frames, paintings). For these, camera MV alone reproduces them correctly — the entity-MV pass is redundant.

Add an early-out: if `|prevPos - curPos| < threshold`, skip MV emission for that entity. Threshold of ~0.001 blocks excludes idle-bobbing animation (which is sub-pixel anyway).

**Expected savings:** in survival worlds, ~80% of rendered entities are stationary at any frame.

### 4.3 Lower-resolution MV target

Per-pixel MV doesn't need full resolution. A half-res or quarter-res MV target:
- Halves/quarters bandwidth for MV writes.
- TAA shader bilinear-samples the MV target — soft transitions are actually beneficial (smooths motion-blur fringes).
- One downside: thin entities (arrows, fishing lines) may not have any covered pixels in the down-sampled MV.

Recommended starting point: half-res MV target (1/2 W × 1/2 H), TAA samples with `texture2DLodOffset` for interpolation.

### 4.4 MV target format choice

| Format | Pros | Cons |
|---|---|---|
| RG8 (signed-normalized) | Smallest. Each component packs ±1 NDC unit. | Limited precision: ~8 bit. Quantization visible on slow motion. |
| RG16F | Float precision. Handles arbitrary screen-space ranges. | 2x bandwidth vs. RG8. |
| RGBA8 with packed encoding | Use 16 bits per channel by splitting hi/lo bytes across RGBA. | Encoding/decoding overhead in shader; clever but complex. |

**Recommended:** RG16F. Bandwidth cost is acceptable, precision avoids visible artifacts. Falls back to RG8 if GL_R16F is missing (rare on GL 3.0+).

### 4.5 Async MV pass (one-frame latency)

Render MV target during frame N; TAA samples it during frame N+1. This decouples the MV pass from the critical render path:
- MV pass can run during a "free" GPU window (e.g., during GUI rendering).
- Frame N's TAA uses frame N-1's MV — slight latency mismatch but visually equivalent at 60+ FPS.

Tradeoff: one frame of incorrect MV at the start of motion, then steady-state correct. Acceptable for a quality optimization.

### 4.6 Combine with FSR2-style reconstruction (Phase 9c.4)

MV is foundational for proper temporal upscaling. Once Option C ships, Phase 9c.4 (FSR2-style reconstruction) becomes unblocked. The investment in entity MV pays off twice: once for ghost-free TAA, once for actual upscale quality.

If the project will eventually want 9c.4, **building Option C is required, not optional.**

---

## 5. Concrete next-session plan

Assuming the user picks up Option C after Option A validates in-game.

### Day 1 — Infrastructure (4-6h)

- Create `MotionVectorTarget.java` — separate FBO, RG16F texture, half-res by default.
- Add `MotionVectorTarget.INSTANCE` allocation alongside `RenderTargetManager` lifecycle.
- Create `EntityVelocityShader.java` — vertex shader passes `prevClipPos`, fragment shader emits screen-space delta.
- Create `EntityRenderStateCache` — WeakHashMap keyed by `Entity.getUniqueID()`, holds last-frame transform.

### Day 2 — Hook entity rendering (4-6h)

- `MixinRenderLivingBase` (and similar for `RenderEntity`, `RenderItem`): `@Inject` HEAD on `doRender` to capture pre-render GL_MODELVIEW.
- `@Inject` RETURN to capture post-render GL_MODELVIEW (or just compute current matrix from entity position + camera state).
- Update `EntityRenderStateCache` with cur/prev pair.
- Schedule MV emission for this entity in a per-frame queue.

### Day 3 — MV emission pass (4-6h)

- New `EntityMotionVectorPass` — runs after scene render, before TAA.
- Bind `MotionVectorTarget` FBO.
- For each entity in the frame's queue, bind velocity shader with cur/prev modelview uniforms, re-render entity geometry with `RenderManager.renderEntityStatic` (or equivalent — investigate which API path bypasses lighting/texture work).
- Verify only velocity is being written (fragment shader should ignore color/texture entirely).

### Day 4 — TAA integration + validation (4-6h)

- Update `TAAAccumulatePass` shader: bind MV target on unit 4. New uniforms `u_motionVectors` + `u_useEntityMV`.
- Shader logic: if MV.r != 0 || MV.g != 0 (entity pixel with MV), use `histUV = v_texCoord - texture(motionVectors, v_texCoord).rg`. Otherwise fall through to camera-only MV path.
- Remove the reactive-mask drop-history logic for pixels where entity MV is present; keep reactive mask as fallback for non-MV-covered entities.
- Validation matrix:
  - Static camera + moving sheep → entity TAA accumulates, no ghost.
  - Camera pan + static sheep → still works (camera MV path).
  - Both moving → entity MV combines correctly.
  - Modded entity (e.g., Tinkers' Construct entity) → reactive mask fallback fires.
  - TESR (banner, beacon) → reactive mask fallback fires.

### Days 5-7 — Polish + perf

- Per-entity velocity threshold (skip stationary entities).
- Frustum culling on MV pass.
- Try lower-res MV target; verify quality.
- Profile: confirm < 2ms added per frame on test scene.
- Document modded entity compat list as we discover edge cases.

**Total: ~1 week if dedicated, ~2 weeks if interrupted by other work.** Substantially shorter than the deep-dive's "2-4 weeks" because Strategy 4 (vanilla-only + reactive fallback) shrinks the modded compat surface to "the existing reactive mask catches it, no new code needed."

---

## 6. Risks and mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| `RenderManager.renderEntityStatic` doesn't exist or has the wrong signature in 1.12.2 | Medium | Investigate alternative APIs in Day 1; fall back to `Render.doRender` directly with manual transform if needed. |
| Per-entity uniform updates throttle GPU (too many state changes) | Low-medium | Batch entities by render type; cache shader uniform locations; consider instanced-array approach for many same-type entities. |
| Half-res MV breaks thin entities (arrows, particles) | Medium | Add per-entity flag to render at full res for known-thin entity classes; or fall back to reactive mask for them. |
| Vanilla render path internally uses GL state we don't set in our second pass (lighting, glow) | Medium | Match vanilla setup carefully; explicitly disable color/texture/lighting in the velocity pass to ensure MV-only output. |
| OptiFine coexistence | Low | OF's renderer hooks may interfere. Detect OF and skip Option C path entirely (reactive mask still works). |

---

## 7. When to NOT pursue Option C

The honest answer: **stop at Option A** if any of these hold:

1. User feedback says reactive-mask quality is acceptable.
2. The project's priority shifts to other features (custom sky, OptiFine override, etc.).
3. Phase 9c.4 (FSR2-style reconstruction) is dropped from roadmap entirely.

Option A is the 80/20 win for ghosting. Option C is the polish step that turns "no ghosts but shimmery" into "no ghosts and crisp" — valuable, but not load-bearing for a usable temporal AA.

---

## 8. Resume prompt addendum

When this document informs next session work:

- `docs/ATTACK_PLAN.md` Phase 9c.3 entry should be updated to reference both `PHASE_9C_TEMPORAL_DEEP_DIVE.md` (general feasibility) and this doc (concrete plan).
- The next-session opening should be: "Read `PHASE_9C3_OPTION_C_RESEARCH.md` §5 for the day-by-day plan and pick up at Day 1."
- Estimated effort for full Option C: 1 week focused, 2 weeks interrupted. Drops to 4-6 days if developer is already familiar with the existing 9c.1+9c.2 code.
