# Phase 9c: Temporal Upscaling Deep Dive

Honest feasibility analysis for bringing temporal upscaling (FSR2-class or TAA-style) to LDOG on MC 1.12.2's OpenGL 2.1/3.0 stack.

## Executive summary

**Temporal upscaling is achievable on LDOG's current stack, but it's genuinely pioneering work** — neither of the two reference Minecraft mods we originally flagged can serve as a direct concept source, because both assume modern graphics APIs we don't have. We'd be writing the first temporal reconstructor for MC 1.12.2's legacy OpenGL pipeline from first principles.

What's realistic in stages:
- **9c.1 — Jittered-projection TAA** (3-5 days): TAA-style temporal anti-aliasing without upscaling. Ships a real quality improvement on its own.
- **9c.2 — Camera-only motion vectors** (1-2 weeks): reproject history using depth-based MV. Handles camera motion correctly. Ghosting on moving entities remains.
- **9c.3 — Entity motion vectors** (2-4 weeks, invasive): per-entity MV overlay. Fixes entity ghosting. Requires hooking every entity/TE/particle draw.
- **9c.4 — FSR2-style reconstruction** (2-4 weeks): combine jitter + MV + history into a proper reconstruction kernel for actual upscaling.
- **9c.5 — Reactive mask + polish** (1-2 weeks): alpha-cutout flagging, quality tuning.

**Total worst-case**: ~4 months of focused work for the full stack.
**Minimum viable**: 9c.1 alone is ~1 week and delivers tangible value.

Our existing `docs/P8_RESEARCH_AND_PLAN.md` (R2 track) was right to be skeptical about temporal — this document refines that with concrete gap analysis and a stage-gate plan.

---

## 1. What temporal upscalers need (algorithm-level)

All modern temporal upscalers (FSR2, DLSS, XeSS) share these inputs:

| Input | What it is | Why needed |
|---|---|---|
| **Source color** | rendered frame at sub-native resolution | the thing being upscaled |
| **Depth buffer** | per-pixel scene depth | disocclusion detection |
| **Motion vectors** | per-pixel 2D screen-space velocity (prev → current) | history reprojection |
| **Jittered projection** | projection matrix offset sub-pixel per frame | sample different pixel positions over time |
| **History buffer** | previous-frame resolved output + validity | accumulate temporal detail |
| **Exposure** | scalar for HDR content | tone-map before temporal blend |
| **Reactive mask** | per-pixel flag for alpha-tested content | reduce history weight on high-frequency edges |

**Core algorithm (simplified):**
1. Render source color at scaled resolution with jittered projection.
2. Compute motion vectors.
3. For each output pixel:
   - Reproject: use MV to find this pixel's previous-frame location in history.
   - Validate: check if that history location is stale (disoccluded) or reactive.
   - Clamp: constrain history to local color box to kill ghosting.
   - Blend: weighted sum of current source + reprojected history.
4. Store blended result as next frame's history.

**DLAA / TAA (no upscale):** same algorithm but source is native resolution. Pure quality improvement, no perf win.

---

## 2. What MC 1.12.2 provides natively

| Need | MC provides | Difficulty |
|---|---|---|
| Source color | ✅ rendered to framebuffer each frame | easy |
| Depth buffer | ✅ attached to MC's main framebuffer + LDOG's scene target | easy |
| Jittered projection | ⚠️ MC builds projection via `Project.gluPerspective` each frame; we can mixin-redirect | medium |
| History buffer | ✅ just a new FBO, LDOG pipeline already manages FBOs | easy |
| Camera pose (for MV) | ✅ `ActiveRenderInfo` + projection matrix accessible | easy |
| Per-entity prev transform | ✅ `Entity.prevPosX/Y/Z` + `Entity.lastTickPosX/Y/Z` available | easy to READ |
| Per-entity MV rendering | ❌ no hook for "render this entity's velocity" | **hard** |
| Particle prev transform | ⚠️ some particles have prev positions, many don't | medium-hard |
| TESR prev transform | ❌ TESRs are custom draw code; no standard prev-state | **hard** |
| Chunk-local motion (leaf wave, water anim) | ❌ texture-animated, no world-space motion | accept artifact |
| Alpha-cutout classification | ⚠️ block render layers (CUTOUT/CUTOUT_MIPPED) accessible; entity alpha harder | medium |
| Exposure (HDR) | N/A | MC's output is LDR, not an issue |

### Why MC's immediate-mode chunks aren't a blocker

Chunks ARE static in world-space. They don't move frame-to-frame. So depth-based camera-motion-vector suffices for chunk content — we don't need per-chunk prev transforms at all. The chunk pipeline is mostly fine.

### Why entities ARE the hard problem

Entities move frame-to-frame. Their MV can't be derived from camera motion alone. Options:
1. **Render entities twice**: once to color, once to MV target with their velocity as the "color". Cost: ~1 extra pass per entity.
2. **Hook `RenderLivingBase.doRender` etc.**: capture per-entity transform; write velocity to MV target via auxiliary shader. Still ~1 extra draw per entity.
3. **Post-process approximation**: optical flow between current and previous color buffers. Less accurate but no draw-call hook needed.

Option 3 is the escape hatch if 1/2 prove too invasive.

### Why particles are worse than entities

Particles are created/destroyed each frame. Their "previous position" may not exist for a particle that just spawned. And MC's particle renderer is a single batched draw — can't easily tag per-particle MV.

Acceptable simplification: don't track particle MV. Particles have small screen footprint, ghosting artifacts are minor. Use reactive mask for them.

---

## 3. Gap analysis

### Easy gaps (days, not weeks)

- **Jittered projection**: mixin into `Project.gluPerspective` or `EntityRenderer.setupCameraTransform` to inject a sub-pixel offset from a Halton (2, 3) sequence.
- **History buffer**: extend `RenderTargetManager` with a history FBO (color texture, main dims). Ping-pong two buffers if we want multi-frame history.
- **Camera motion vectors**: a single fullscreen fragment shader pass that reads depth + inverse-projects + re-projects with previous matrices. No render-path changes.
- **TAA-style accumulation (9c.1)**: jitter + history + camera-MV → works for static-dominant scenes.

### Medium gaps (1-2 weeks each)

- **Disocclusion detection**: depth compare between reprojected prev-depth and current-depth. Standard technique.
- **Neighborhood color clamping**: sample 3×3 around current pixel, clamp reprojected history to that color range. Kills most ghosting.
- **Block-layer reactive mask**: mixin into `RenderChunk` to tag draws by layer. Alpha-cutout layers get a reactive flag written to an auxiliary target.

### Hard gaps (weeks to months)

- **Per-entity motion vectors**: every `Render<T>` subclass in the forge ecosystem would need instrumentation. For vanilla, a general mixin on `RenderLivingBase.doRender` + `Render.doRender` could cover the common path. Custom TESRs would be left out — ghosting fallback.
- **Particle MV**: MC's `ParticleManager.renderParticles` is a single batched draw with no per-particle metadata. Either accept particle ghosting (reactive mask) or rewrite the particle render path.
- **FSR2 reconstruction kernel**: the actual reconstruction shader is algorithmically complex (~500 lines of HLSL in AMD's public reference). LDOG-original GLSL would be weeks of careful translation-by-concept.

---

## 4. Reference mod inspection (concept-only per policy)

We flagged two external references earlier in the project:

### Super Resolution (187J3X1-114514/superresolution)

**Target**: MC 1.18+. Forge, Fabric, NeoForge.
**Algorithms**: FSR1, FSR2, SGSR1, SGSR2, NIS (in development).
**Architecture**: OpenGL 4.3+ compute shaders + Vulkan 1.2+, native C/C++ libraries, SPIR-V binaries.
**Features**: Direct state access (`GL_ARB_direct_state_access`), history buffers + motion vectors for temporal paths.

**Applicability to LDOG 1.12.2**: **near-zero as a direct reference**. Requires:
- OpenGL 4.3+ (we have 2.1/3.0 baseline).
- Compute shaders (we don't have them).
- Vulkan (we don't have it).
- Native C/C++ libraries (we don't ship native code).

**What we can learn conceptually**: it confirms temporal upscaling in Minecraft works with the expected ingredients (MV + history) but needs modern APIs. Our 1.12.2 target forces us to implement the same concepts with only fragment shaders and GL 3.0 features.

### Radiance (Minecraft-Radiance/Radiance)

**Target**: under development; targeting modern MC.
**Architecture**: **complete OpenGL renderer replacement via Vulkan + C++ backend (MCVR)**.
**Features**: Hardware ray tracing, DLSS, FSR3, XeSS SR integration, path tracing.

**Applicability to LDOG 1.12.2**: **effectively zero**. Radiance works BECAUSE they rewrote the renderer. We're keeping MC's immediate-mode OpenGL 2.1 renderer and patching around it.

**What we can learn conceptually**: a complete-renderer approach is the "correct" way to enable modern temporal upscaling in Minecraft. We're not doing that. Accept we'll have a more constrained implementation.

### Net implication for LDOG

Both reference mods either require modern GL APIs (Super Resolution) or bypass MC's renderer entirely (Radiance). Neither approach is available to us. **We'd be writing the first temporal reconstructor for MC 1.12.2's legacy immediate-mode OpenGL pipeline.** This is a real undertaking — we have no template to follow, only the algorithmic principles from AMD's public FSR2 spec.

---

## 5. Staged implementation plan

Each stage is independently shippable. User can stop at any stage without the previous work being wasted.

### Stage 9c.1 — Jittered-projection TAA (MVP)

**Scope**: sub-pixel projection jitter + history buffer + simple accumulation. No motion vectors yet.

**Visible result**: temporal anti-aliasing on static scenes. Looks better than FXAA when the camera is still. Has visible ghosting on any movement.

**New code:**
- `JitterHelper` — Halton-sequence offset generator, called each frame.
- `MixinEntityRendererJitter` — redirects projection-matrix setup to apply jitter when enabled.
- `TAAAccumulatePass` — new PostProcessPass that blends current color with history.
- Extend `RenderTargetManager` with a history texture (ping-pong pair).
- Config: `LDOGConfig.enableTAA` (bool), `LDOGConfig.taaHistoryWeight` (double, 0-0.95).

**Estimate**: 3-5 days.
**Risk**: low. Established technique, narrow scope.
**Go/no-go gate**: static scenes look cleaner than FXAA; moving scenes have acceptable (if ugly) ghosting.

### Stage 9c.2 — Camera motion vectors

**Scope**: depth-based MV from camera pose delta. Reproject history with MV. Neighborhood color clamping for ghosting mitigation.

**Visible result**: camera pan/rotate now has no ghosting. Moving entities still ghost (entity MV not yet implemented).

**New code:**
- `CameraState` — tracks previous-frame view/projection matrices.
- `CameraMVPass` — fullscreen pass computing MV from depth + matrix deltas.
- `TAAAccumulatePass` upgraded to use MV for reprojection.
- Neighborhood color clamping in the accumulate shader.

**Estimate**: 1-2 weeks.
**Risk**: medium. Matrix math has to be exactly right; edge cases (negative W, NaN) need handling.
**Go/no-go gate**: camera-only motion is clean; entity ghosting is the only remaining issue.

### Stage 9c.3 — Entity motion vectors (invasive)

**Scope**: per-entity velocity emission to MV target. Covers vanilla entities via `Render.doRender` hook; custom TESRs left as fallback (camera-only MV).

**Visible result**: moving entities reproject correctly. Ghosting reduced to just custom TESRs and particles.

**New code:**
- `MixinRender` — hook every entity draw to emit velocity to auxiliary MV target.
- Auxiliary MV render target + fragment shader that writes entity-local velocity.
- Particle reactive-mask path (particles won't get correct MV; reactive mask reduces history weight for them).

**Estimate**: 2-4 weeks.
**Risk**: high. Every entity type has custom render code; modded entities are a huge compat surface.
**Go/no-go gate**: vanilla entities + common forge entities ghost-free; known compat list for edge cases.

### Stage 9c.4 — FSR2-style reconstruction

**Scope**: replace the simple TAA accumulate with a proper FSR2-style reconstruction kernel that handles upscaling (source rendered at <1.0 scale).

**Visible result**: actual temporal upscaling — scale 0.67 looks close to native, without FSR1's spatial-only blur.

**New code:**
- `TemporalReconstructionPass` — replaces `TAAAccumulatePass` when upscaling is active.
- LDOG-original GLSL kernel inspired by FSR2 spec: Lanczos-like source sampling, disocclusion check, history clamping, reactive weighting.

**Estimate**: 2-4 weeks.
**Risk**: high. Algorithm is complex; tuning takes time.
**Go/no-go gate**: image quality visibly better than FSR1-Quality at the same scale.

### Stage 9c.5 — Reactive mask + polish

**Scope**: alpha-cutout classification → reactive mask target; quality tuning; artifact mitigation.

**Visible result**: fences, leaves, glass panes no longer shimmer or ghost.

**New code:**
- `MixinRenderChunk` to tag alpha-cutout draws.
- Reactive mask render target.
- Integration into reconstruction shader.

**Estimate**: 1-2 weeks.
**Risk**: medium. Depends on how much forge-compat we want.
**Go/no-go gate**: no visible shimmer on common alpha-cutout content.

---

## 6. Risk assessment and go/no-go gates

### Hard blockers we'd need to solve

1. **Per-entity MV emission**. There's no MC-provided hook. We'd need a mixin on the entity render base class plus forge-mod-compat considerations. **Mitigation**: start with camera-only MV (9c.2) which handles 80% of the perceived motion and defer entity MV.

2. **Particle MV**. MC's particle renderer is batched; no per-particle metadata. **Mitigation**: accept particle ghosting behind a reactive mask.

3. **Custom TESRs**. Modded tile entities have bespoke render code. **Mitigation**: camera-only MV fallback; TESRs appear to "move through history" like static world.

4. **Modded entity compat**. Every mod's entity renderer has its own transform logic. **Mitigation**: vanilla-only MV at first; document limitation.

### Known non-blockers

- Depth buffer access: already have it.
- History FBO: RenderTargetManager extension is trivial.
- Jittered projection: straightforward mixin on setupCameraTransform.
- Shader authoring: ShaderProgram utility is solid from 9a.2-9a.6.
- Pipeline integration: pass framework is mature; adding new passes is well-understood.

### Go/no-go gates between stages

- **9c.1 → 9c.2**: static-scene TAA looks visibly better than FXAA; ghosting on motion is acceptable as baseline for upcoming fix.
- **9c.2 → 9c.3**: camera motion clean; decision point to invest in entity MV (high cost) or stop at "TAA for static scenes + FSR1/FSR1-Quality for movement".
- **9c.3 → 9c.4**: vanilla entities clean; decision point to invest in full FSR2-style reconstruction.
- **9c.4 → 9c.5**: upscaling quality demonstrates real wins over spatial FSR1-Quality; commit to polish.

---

## 7. Honest recommendation

### Ship **9c.1 (Jittered-projection TAA)** as the starting point.

Reasons:
- Real user-visible quality improvement even without upscaling.
- 3-5 days of focused work — tractable in one session.
- All infrastructure is already built (pipeline framework, ShaderProgram, RenderTargetManager).
- Naturally extends to 9c.2 if 9c.1 is successful.
- Fails safely — if the TAA looks bad, disable the config and no damage done.

### Evaluate **9c.2** as a natural follow-up if 9c.1 validates.

Reasons:
- Camera-only MV is the biggest quality jump per unit of effort.
- Doesn't require entity-render invasions.
- Static-scene perfection + accepted entity ghosting is a reasonable end state for LDOG.

### Defer **9c.3/9c.4/9c.5** until we have clear user demand.

Reasons:
- Entity MV is invasive and has a real mod-compat cost.
- Full FSR2-style reconstruction is multiple weeks without a ready reference.
- Spatial FSR1-Quality already delivers a lot of value at scale 0.85 for users who want performance.
- Implementation cost per marginal quality improvement gets steep past 9c.2.

### When to pivot to "platform shift" instead

If users request better-than-FSR2 quality (DLSS-level) AND we commit to maintaining a modern Minecraft fork, the honest answer is to follow Radiance's lead: rewrite the renderer in Vulkan + native. That's a different project entirely. Document as a "research archive" item, not a roadmap item.

---

## 8. Resume prompt updates

When this document informs next session work:

- `docs/P8_RESEARCH_AND_PLAN.md` should be updated to mark Phase 9c from "research" to "staged plan available in PHASE_9C_TEMPORAL_DEEP_DIVE.md".
- `docs/ATTACK_PLAN.md` should list 9c.1 through 9c.5 as discrete sub-phases rather than the current "Phase 9c (Optional)" treatment.
- Estimated effort: 9c.1 ships in a week; 9c.2 in another 1-2; beyond that, weeks-to-months.
