# Phase 8/9 Research and Plan (Shaders + Upscaling)

## Executive TLDR

LDOG can ship meaningful upscaling on the current stack, but only if we stage work correctly.

- `Recommended (Now)`: Build a minimal post-process pipeline and implement FSR1-style spatial upscaling first.
- `Recommended (Research)`: Keep FSR2/XeSS-class temporal paths as research tracks until motion vectors, reactive masks, and stable temporal history exist.
- `Not Recommended (Impossible)` on current stack: direct DLSS integration in the current Minecraft 1.12.2 OpenGL path.
- `Requires Platform Shift`: DLSS and likely production-grade modern temporal upscalers need a renderer/platform transition (for example Vulkan/DX12-era architecture, native integration path, and richer frame metadata).

This document is intentionally split into:
1) quick recommendation guidance, and
2) deeper technical/legal analysis and phased execution tasks.

---

## Scope and Policy

### Scope

This document covers:
- Phase 8 (shader pipeline foundation)
- Phase 9 (upscaling execution paths)
- feasibility and risk for FSR, DLSS, XeSS, NIS, and related options

### Policy: External References and Code Use

LDOG should use external projects for architecture study only.

- Do not copy/paste source code from external mods/projects into LDOG.
- Use concept-level inspiration (pipeline structure, ordering, data flow, test ideas).
- Implement all runtime code independently in LDOG style and conventions.
- Keep clear internal comments/doc notes for where ideas came from.

This keeps licensing/compliance risk low and preserves long-term maintainability.

---

## Current Baseline (Verified in LDOG)

Based on current code and docs:

- FXAA post-process toggle exists via `EntityRenderer.loadShader("shaders/post/fxaa.json")` in `src/main/java/com/limitlessdev/ldog/render/fxaa/FXAAHandler.java`.
- MSAA path exists with an auxiliary multisampled FBO and resolve in `src/main/java/com/limitlessdev/ldog/render/msaa/MSAAFramebuffer.java` and `src/main/java/com/limitlessdev/ldog/mixin/MixinEntityRendererMSAA.java`.
- Config surface already includes `enableShaders`, `enableMSAA`, and `enableFXAA` in `src/main/java/com/limitlessdev/ldog/config/LDOGConfig.java`.
- `docs/ATTACK_PLAN.md` already frames Phase 8 as shader-pipeline work and Phase 9 as FSR-first.

Practical implication: LDOG has post-process experience, but not yet a full generalized render graph/pipeline layer.

---

## Quick Decision Matrix (Recommendation + Prereqs + No-Go)

Status tags used here:
- `Recommended (Now)`
- `Recommended (Research)`
- `Not Recommended (Impossible)`
- `Requires Platform Shift`

| Technology | Status | Why | Prerequisites | No-Go Conditions |
|---|---|---|---|---|
| FSR1-style spatial upscaling (EASU + optional sharpen) | `Recommended (Now)` | Single-frame spatial path is compatible with a post-process FBO model and does not require motion vectors | Stable offscreen render target, fullscreen pass chain, resolution scale controls | If Phase 8a FBO path cannot be stabilized without severe regressions |
| NIS-style spatial upscaling/sharpen | `Recommended (Research)` | Similar deployment model to FSR1, possible fallback/alternative quality profile | Same as FSR1 + calibration and quality validation | If visual quality is strictly worse than FSR1 in LDOG target scenarios |
| FSR2-class temporal upscaling | `Recommended (Research)` | High upside but needs robust temporal data and artifact handling | Motion vectors, jitter strategy, history buffers, reactive/transparency masks, disocclusion handling | If required frame metadata cannot be generated reliably in 1.12.2 pipeline |
| XeSS-class temporal upscaling | `Recommended (Research)` | Same broad category as temporal upscalers, likely similar data burden | Same as FSR2 + API/runtime compatibility research | If runtime/API requirements exceed practical integration on current renderer |
| DLSS | `Not Recommended (Impossible)` on current stack | Requires NVIDIA SDK-native integration and modern graphics API assumptions not aligned with current 1.12.2 OpenGL pipeline | Platform/renderer migration and native integration surface | Current Forge 1.12.2 OpenGL render path unchanged |
| Frame generation technologies | `Requires Platform Shift` | Strong dependency on motion vectors, frame pacing, presentation control, and modern render architecture | Mature temporal renderer + low-latency frame pipeline control | Current architecture remains immediate goal of replacement parity |

---

## Concise Guidance by Technology Type

### Spatial Upscaling Family (FSR1/NIS-like)

- Best near-term ROI.
- Lower architecture burden than temporal methods.
- Main risk is quality tuning and UI/HUD integration order.

### Temporal Upscaling Family (FSR2/XeSS-like)

- Potentially better quality/performance tradeoff in motion.
- Requires major foundational rendering work first.
- High risk of ghosting/flicker without complete temporal inputs.

### Vendor SDK/Native-locked Paths (DLSS-class)

- Not viable as a near-term target on current stack.
- Keep as long-term architecture note only.

---

## Deeper Technical Feasibility

## 1) FSR1-style Spatial Path

### Why this is first

- It fits the existing direction already noted in `docs/ATTACK_PLAN.md`.
- It can be implemented as a bounded post-process feature with clear toggles.
- It provides immediate user-visible value without waiting for temporal infrastructure.

### Architecture requirements

- Render world into an internal color target at scaled resolution.
- Run one or two fullscreen passes (upscale and optional sharpen).
- Composite UI/HUD at native resolution afterward.
- Handle resize/resource reload transitions cleanly.

### Primary risks

- UI ordering mistakes (blurry HUD/text).
- aliasing mismatch when combined with FXAA/MSAA settings.
- quality variance across texture packs.

### Risk buffer suggestion

- Add 25 to 35 percent schedule buffer for visual tuning and compatibility testing.

---

## 2) Temporal Upscaling (FSR2/XeSS-like)

### Why research, not immediate implementation

Temporal upscalers generally need:
- per-pixel motion vectors
- jittered projection with stable history reprojection
- disocclusion logic
- reactive masks for alpha/transparency-heavy content

These are non-trivial in Minecraft 1.12.2 and likely require substantial render architecture changes.

### Primary risks

- ghosting on entities/particles/foliage
- shimmer from unstable history
- major debugging surface area and long iteration cycles

### Risk buffer suggestion

- Add 40 to 60 percent schedule buffer once active implementation starts.

### Preliminary research tracks (`Recommended (Research)`)

| Track | Key unknowns | Early feasibility signals | Minimum prototype experiment | Roadmap impact |
|---|---|---|---|---|
| NIS-style spatial + sharpen | Can NIS-like quality beat or complement FSR1 on 1.12.2 content mixes? | Cleaner edge stability than FSR1 at same scale in foliage-heavy scenes | Implement NIS-style pass as alternate backend on same Phase 8a pipeline; compare with fixed screenshots and motion clips | If positive, add as optional quality mode after Phase 9a |
| FSR2-class temporal | Can LDOG produce reliable motion vectors and history without severe artifacts? | Stable camera pan with low ghosting and acceptable disocclusion handling | Prototype motion-vector buffer + jittered projection in a debug branch and run ghosting test scenes | If positive, becomes a Phase 9c->Phase 10 candidate; otherwise remains deferred |
| XeSS-class temporal | Do runtime/API constraints allow practical integration without platform change? | Clear integration path and acceptable perf/quality profile in principle | Write integration feasibility memo + mock data-flow mapping against planned renderer outputs | If negative, formally classify as platform-shift-only |

#### NIS-style (`Recommended (Research)`)

- **Why research now:** Low incremental cost once Phase 8a fullscreen pass system exists.
- **What to verify first:**
  - visual consistency across 16x, 32x, and HD packs
  - interaction with FXAA toggled on/off
  - sharpening halos on UI-adjacent geometry edges
- **Preliminary go/no-go:**
  - Go if quality is clearly better than or complementary to FSR1 in at least one preset tier.
  - No-go if it adds complexity without measurable image-quality gain.

#### FSR2-class (`Recommended (Research)`)

- **Why research now:** It defines whether LDOG should invest in temporal infrastructure after FSR1.
- **What to verify first:**
  - motion vector completeness for world + entities + particles
  - history validity through chunk rebuilds and teleports
  - disocclusion behavior on alpha-tested foliage
- **Preliminary go/no-go:**
  - Go only if motion vector + history prototypes demonstrate stable output in known stress scenes.
  - No-go (for this branch) if ghosting/shimmer remains systemic after initial mitigation passes.

#### XeSS-class (`Recommended (Research)`)

- **Why research now:** Helps avoid mis-scoping long-term temporal work under current renderer constraints.
- **What to verify first:**
  - practical runtime/API path on Minecraft 1.12.2 stack
  - required frame inputs versus what LDOG can realistically emit
  - maintenance burden compared to expected user benefit
- **Preliminary go/no-go:**
  - Go only with a clear, supportable integration path that does not derail Phase 8/9 goals.
  - No-go if dependencies or architecture requirements imply immediate platform shift.

### Ranked research backlog (next to execute)

This is the concrete order to run research once `Phase 8a` no-op pipeline parity is complete.

| Priority | Track | Status tag | Target effort | Why this order | Expected output |
|---|---|---|---|---|---|
| R1 | NIS-style spatial compare | `Recommended (Research)` | `S` (3-5 days) | Lowest architectural risk; leverages same pass slot as FSR1 | Side-by-side quality/perf note and keep/drop recommendation |
| R2 | FSR2 feasibility spike | `Recommended (Research)` | `M` (1-2 weeks) | Highest roadmap impact if feasible; de-risks long-term temporal investment | Motion-vector/history prototype report with explicit go/no-go |
| R3 | XeSS integration viability memo | `Recommended (Research)` | `S/M` (4-8 days) | Clarifies whether XeSS is actionable or platform-shift-only | Constraint matrix + classification (`researchable` vs `platform-shift`) |

#### R1: NIS-style spatial compare (first)

**Entry criteria**
- [ ] `Phase 8a` no-op pass is stable.
- [ ] Internal render scale can be toggled and measured.

**Exact scene list (fixed runbook)**
- **S1 Clarity/UI:** static base in a safe interior with chat open, inventory open/closed, debug text visible.
- **S2 Foliage stress:** dense leaves/grass view (forest/jungle edge), stationary camera + slow pan.
- **S3 Motion stress:** entity movement through mid-distance geometry (mobs/particles in view) with controlled strafe path.
- **S4 UI-adjacent edges:** thin geometry near HUD/text overlays (fences, panes, wires) to catch haloing/ringing.

**Capture protocol (reproducible)**
- Render scales: `1.00`, `0.85`, `0.75`.
- Compare modes: `FSR1 baseline` vs `NIS-style candidate`.
- FXAA states: `OFF` and `ON` for each scene/scale pair.
- For each pair, capture:
  - `3` still frames at matched camera transform.
  - `10s` motion clip following the same camera path.
- Naming format:
  - `R1_<scene>_<scale>_<mode>_<fxaa>_<run>.png`
  - `R1_<scene>_<scale>_<mode>_<fxaa>_<run>.mp4`

**Tasks**
- [ ] Add a temporary alternate sharpening/upscale path in the same post-process slot used for FSR1 MVP experiments.
- [ ] Capture fixed-scene comparisons at identical scales.
- [ ] Compare with FXAA on and off.
- [ ] Record performance deltas and visible artifacts (haloing, shimmer, foliage edge crawl).

**Metrics template (fill per scene/scale/fxaa)**

| Scene | Scale | FXAA | Mode | Avg FPS | 1% Low FPS | Delta vs FSR1 | Haloing (0-3) | Shimmer (0-3) | Edge stability (0-3, high better) | Verdict |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---|
| S1 | 0.85 | OFF | NIS |  |  |  |  |  |  |  |

Scoring guidance:
- `Haloing/Shimmer`: `0 none`, `1 mild`, `2 noticeable`, `3 severe`.
- `Edge stability`: `0 poor`, `1 weak`, `2 acceptable`, `3 strong`.

**Acceptance thresholds (go/no-go)**
- Performance: NIS average FPS is not worse than FSR1 by more than `5%` on median across all test rows.
- Artifact budget: no `severe (3)` haloing in any S1/S4 row; shimmer median is `<= 1` in S2/S3.
- Quality win: NIS must outperform FSR1 in at least one of:
  - edge stability median by `>= 1` point, or
  - equal stability with lower shimmer median.

**Decision gate**
- **Advance** if all thresholds pass and at least one clear quality win is documented.
- **Conditional keep** if thresholds pass but quality wins are marginal; keep behind experimental flag and revisit after Phase 9a tuning.
- **Stop** if performance/artifact thresholds fail or no quality win appears.

**Estimated duration**
- `S` effort: `3-5 days` total
  - Day 1: instrument candidate path + scene setup
  - Days 2-3: capture matrix
  - Day 4: scoring + summary
  - Day 5 (buffer): reruns for inconsistent rows

#### R2: FSR2 feasibility spike (second)

**Entry criteria**
- [ ] `Phase 8a` and `8b` are stable in resize/reload/lifecycle tests.
- [ ] Post-process pipeline has debug taps for intermediate buffers.

**Prototype outputs to build (minimum)**
- `P1 Motion vectors`: world + entities (particles optional in v0), visualized with debug colors.
- `P2 Jitter path`: deterministic camera jitter sequence and projection-state logging.
- `P3 History buffers`: previous-frame color/history validity mask with reset hooks.
- `P4 Debug overlays`: ghosting/disocclusion inspection view and frame-to-frame diff view.

**Scene/test matrix (fixed runbook)**
- **T1 Foliage pan:** slow horizontal pan across dense leaves/grass.
- **T2 Entity crossing:** moving mobs crossing high-contrast geometry edges.
- **T3 Alpha-cutout stress:** fences/panes/wires/foliage with camera strafe.
- **T4 Disocclusion jump:** quick direction change/peek around occluders.
- **T5 Lifecycle reset:** teleport/chunk rebuild/reload transitions to test history invalidation.

**Capture protocol (reproducible)**
- Render scales for spike: `0.85`, `0.75`.
- For each scene + scale, capture:
  - `10s` motion clip with identical camera path (`3` runs)
  - `3` stills at matching frame indices (for ghosting/frame-diff inspection)
- Required variants:
  - `history on` vs `history reset every frame` (control)
  - `jitter on` vs `jitter off` (sanity)
- Naming format:
  - `R2_<test>_<scale>_<variant>_<run>.mp4`
  - `R2_<test>_<scale>_<variant>_<run>.png`

**Tasks**
- [ ] Prototype a motion vector output path for world + entities (particles optional in v0).
- [ ] Add jittered projection experiment branch and history buffer bookkeeping.
- [ ] Run ghosting/disocclusion stress tests (foliage, alpha-cutout blocks, moving entities).
- [ ] Document where data is missing or unstable (for example teleports/chunk rebuild transitions).

**Metrics template (fill per test/scale/variant)**

| Test | Scale | Variant | Ghosting (0-3) | Shimmer (0-3) | Disocclusion trails (0-3) | History validity failures | Avg FPS delta vs no-temporal | Verdict |
|---|---|---|---:|---:|---:|---:|---:|---|
| T1 | 0.85 | history on + jitter on |  |  |  |  |  |  |

Scoring guidance:
- `0 none`, `1 mild`, `2 noticeable`, `3 severe`.
- `History validity failures`: visible stale-history events per clip.

**Acceptance thresholds (go/no-go)**
- Artifact ceiling: median ghosting `<= 1` and median shimmer `<= 1` across T1-T4 at each scale.
- Disocclusion control: no more than `1` severe (`3`) trail event across all T4 runs.
- History safety: lifecycle test T5 shows correct reset behavior with no persistent stale frames.
- Performance sanity: temporal prototype overhead does not exceed `10%` vs non-temporal control in median.

**Decision gate**
- **Advance** if all thresholds pass and major failure modes have plausible mitigation paths.
- **Conditional continue** if thresholds narrowly miss but failures are isolated and fixable (time-box one follow-up spike).
- **Stop/defer** if systemic ghosting/shimmer or history invalidation failures persist after first mitigation attempt.

**Risk notes (watch early)**
- Chunk rebuild and teleport events may invalidate history more often than temporal methods tolerate.
- Alpha-heavy Minecraft content can over-amplify ghosting without robust reactive handling.
- Debug visibility is mandatory; do not evaluate purely from subjective play feel.

**Estimated duration**
- `M` effort: `1-2 weeks`
  - Days 1-3: prototype outputs `P1-P3`
  - Days 4-6: capture matrix runs
  - Days 7-8: scoring + failure analysis
  - Remaining days (buffer): targeted reruns/mitigation sanity checks

#### R3: XeSS integration viability memo (third)

**Entry criteria**
- [ ] R2 findings are documented (even if negative).

**Memo deliverables (required outputs)**
- `D1 Constraint matrix`: XeSS expected runtime/inputs vs LDOG-capable outputs.
- `D2 Blocker register`: hard/soft blockers with owner and mitigation possibility.
- `D3 Integration posture`: classification as `researchable` or `platform-shift`.
- `D4 Roadmap delta`: recommended changes to Phase 9c+ sequencing based on findings.

**Source/research matrix (minimum coverage)**

| Source class | What to extract | Why it matters |
|---|---|---|
| Vendor/public XeSS technical docs | required frame inputs, API/runtime assumptions, quality/perf constraints | establishes baseline feasibility conditions |
| LDOG current architecture docs/code | available buffers, frame stages, hook timing, lifecycle events | establishes what LDOG can emit today |
| Prior R2 temporal findings | motion/history reliability envelope and known failure modes | avoids duplicating disproven assumptions |
| Comparable open mod architecture notes (concept-level only) | integration pattern lessons and failure handling strategy | informs implementation shape without copying code |

**Tasks**
- [ ] Build a requirements matrix: expected inputs/runtime constraints vs LDOG-capable outputs.
- [ ] Identify hard blockers that imply platform/API shift.
- [ ] Estimate maintenance burden versus expected player impact.
- [ ] Produce final classification and roadmap recommendation (`researchable` vs `platform-shift`).

**Evaluation criteria**
- Input compatibility: can required temporal/auxiliary data be produced reliably on 1.12.2 renderer?
- Runtime/API compatibility: can integration occur without immediate graphics-platform migration?
- Operational burden: expected maintenance/debug load relative to forecast user value.
- Risk alignment: does effort compete with higher-priority FSR1/Phase 8 stabilization work?

**Blocker classification rubric**

| Blocker type | Definition | Typical outcome |
|---|---|---|
| `Hard (Platform Shift)` | Requires architecture/API/runtime changes outside current branch scope | classify XeSS as `platform-shift` |
| `Hard (Unknown)` | Missing critical public information to make safe implementation plan | remain `conditional` and defer |
| `Soft (Engineering)` | Solvable with bounded work in current renderer | keep `researchable` with follow-up tasks |

**Decision gate**
- **Advance (`researchable`)** if no hard platform blockers exist and required inputs are plausibly generatable with bounded effort.
- **Conditional** if blockers are unresolved/unknown but no explicit platform-shift requirement is proven yet; time-box one follow-up discovery task.
- **Platform-shift classification** if one or more hard blockers require API/runtime migration beyond current roadmap.

**Risk notes (watch early)**
- Marketing-level claims can hide runtime assumptions that are incompatible with legacy pipelines.
- Even if technically possible, maintenance burden may exceed practical value for LDOG scope.
- Classification drift risk: keep decisions tied to evidence from matrix/rubric, not optimism bias.

**Estimated duration**
- `S/M` effort: `4-8 days`
  - Days 1-2: gather and normalize source matrix
  - Days 3-4: map constraints to LDOG capabilities
  - Days 5-6: blocker classification + decision draft
  - Days 7-8 (buffer): resolve contradictions and finalize recommendation

**R3 output template (fill and store in docs notes)**
- **Objective:** determine whether XeSS is `researchable` or `platform-shift` for LDOG.
- **Evidence summary:** top constraints and compatibility findings.
- **Blockers:** list with rubric class (`hard/soft`) and rationale.
- **Decision:** advance / conditional / platform-shift.
- **Roadmap impact:** explicit change to Phase 9c+ scope and estimates.

### Research sprint order (R1 -> R2 -> R3)

This is the recommended execution sequence to run the full research program with minimal churn.

| Sprint week | Primary focus | Depends on | Exit criteria | Deliverable |
|---|---|---|---|---|
| Week 1 | `R1` NIS-style compare | `Phase 8a` no-op parity | R1 decision gate reached (`advance`/`conditional keep`/`stop`) | `R1` comparison report + keep/drop recommendation |
| Weeks 2-3 | `R2` FSR2 feasibility spike | `Phase 8a/8b` stability + R1 complete | R2 decision gate reached (`advance`/`conditional continue`/`stop/defer`) | `R2` prototype findings + temporal feasibility verdict |
| Week 4 | `R3` XeSS viability memo | R2 findings documented | R3 decision gate reached (`researchable`/`conditional`/`platform-shift`) | `R3` constraint matrix + posture classification |

#### Execution rules

- Do not start `R2` before `R1` is closed with a written decision.
- Do not start `R3` before `R2` findings are documented (positive or negative).
- If any track hits `stop`/`platform-shift`, log it and continue sequence; do not silently carry scope.
- Each track must end with a roadmap-impact note (phase/scope/estimate changes).

#### Consolidated stop/go checkpoints

- **Checkpoint A (after R1):** confirm whether NIS remains in scope as optional post-FSR1 mode.
- **Checkpoint B (after R2):** decide if temporal upscaling remains on roadmap or is deferred.
- **Checkpoint C (after R3):** classify XeSS as `researchable` follow-up or `platform-shift` archive item.

#### Final sprint package output

At the end of Week 4, publish a single `Research Sprint Summary` containing:
- R1/R2/R3 decisions
- blocker register (open/closed)
- recommended roadmap updates (Phase 9c+)
- estimate updates with any new risk buffer adjustments

### Research reporting template (use for each R-item)

- **Objective:** what uncertainty this item is trying to remove.
- **Method:** scenes, toggles, and measurement approach.
- **Findings:** artifact/perf/legal observations.
- **Decision:** advance, defer, or stop.
- **Impact on roadmap:** exact Phase/estimate changes.

---

## 3) DLSS

### Current recommendation

`Not Recommended (Impossible)` on current stack.

### Why

Given project constraints and current pipeline assumptions in `docs/ATTACK_PLAN.md`, DLSS integration is not an actionable near-term target.

### Revisit trigger

Only revisit after platform shift milestones are real and accepted (renderer/API/native boundary changes).

---

## Legal and Compliance Guidance

## Quick Rules (Concise)

- Do not copy external mod source code into LDOG.
- Treat external projects as design references, not implementation donors.
- Keep third-party shader/source imports explicit and license-reviewed.
- Prefer LDOG-authored shader code and internal abstractions.
- Document dependency licenses before adding any binary/runtime component.

## Detailed Compliance Notes (Fuller)

### External mod references (Super Resolution / Radiance)

- Use them to study architecture, module boundaries, and integration patterns.
- Avoid reusing proprietary assets or direct source snippets.
- Keep notes of what concepts were adopted and how LDOG implemented them independently.

### Vendor SDK paths (DLSS-class)

- Assume legal, redistribution, and binary-runtime constraints are significantly higher than spatial shader-only paths.
- Treat SDK-based features as separate legal track with explicit go/no-go gate.

### Internal review checkpoints

Before merging major Phase 8/9 features:
- confirm all new third-party artifacts and licenses
- confirm no direct source copy from external mods
- confirm attribution notes for conceptual references in docs

---

## Combined External Pattern Review (Super Resolution + Radiance)

References:
- Super Resolution GitHub: `https://github.com/187J3X1-114514/superresolution`
- Super Resolution Modrinth: `https://modrinth.com/mod/superresolution`
- Radiance CurseForge: `https://www.curseforge.com/minecraft/mc-mods/radiance`
- Radiance GitHub: `https://github.com/Minecraft-Radiance/Radiance`

### What to study (concept-level)

- How they structure render targets and pass sequencing.
- Where they place upscaling relative to post-processing and UI.
- How they expose quality presets and fallback modes.
- How they handle runtime toggles, resize events, and reloads.
- How they communicate limitations and compatibility caveats to users.

### What not to do

- Do not port code directly.
- Do not mirror implementation line-for-line.
- Do not assume their API/runtime constraints match LDOG 1.12.2.

---

## Phase 8/9 Proposed Work Breakdown

## Phase 8a - Minimal Post-Process Pipeline Foundation

**Status (2026-04-17): in progress.** Framework skeleton, mixin lifecycle hook, telemetry, and experimental config gate have landed; no visual change yet. Remaining work before 8a "done": `RenderTargetManager` (8a.2), parity validation evidence (8a.3), and overlay/"active scale" surfacing once a scaled target exists.

Goal: generalized internal post-process scaffold that can host FSR1-style passes.

### 8a objectives

- Build a reusable pass system instead of one-off shader hooks.
- Keep world rendering and UI compositing order explicit and testable.
- Make resizing/reload behavior deterministic under config changes.

### 8a architecture sketch (first pass)

1. World renders to an internal color target at selected render scale.
2. Post-process chain executes in fixed order (upscale, optional sharpen, debug taps).
3. Final composited image resolves to the main framebuffer.
4. UI/HUD renders at native resolution after upscaling pass chain.

### 8a concrete implementation map (file-level)

#### New package targets (proposed)

- `src/main/java/com/limitlessdev/ldog/render/pipeline/PostProcessPass.java`
  - Small pass contract (`init`, `resize`, `execute`, `dispose`, `isEnabled`).
- `src/main/java/com/limitlessdev/ldog/render/pipeline/PostProcessPipeline.java`
  - Owns pass order, orchestration, and fail-safe disable-on-error behavior.
- `src/main/java/com/limitlessdev/ldog/render/pipeline/RenderTargetManager.java`
  - Creates/scales/disposes world target + optional ping-pong target.
- `src/main/java/com/limitlessdev/ldog/render/pipeline/passes/NoOpPass.java`
  - Baseline parity pass for first validation.
- `src/main/java/com/limitlessdev/ldog/render/pipeline/PipelineDebugStats.java`
  - Exposes pass timings/state for overlay and logs.

#### Integration points (proposed)

- `src/main/java/com/limitlessdev/ldog/mixin/MixinEntityRendererPostPipeline.java`
  - Hook world render end and resolve before UI/HUD draw.
- `src/main/resources/mixins.ldog.json`
  - Register new mixin in client list.
- `src/main/java/com/limitlessdev/ldog/config/LDOGConfig.java`
  - Add guarded foundation toggles (for example pipeline master enable + internal render scale enum).
- `src/main/java/com/limitlessdev/ldog/gui/GuiLDOGSettings.java`
  - Add hidden/experimental controls only after stability gate passes.

#### Existing system interaction targets

- `src/main/java/com/limitlessdev/ldog/render/fxaa/FXAAHandler.java`
  - Ensure ordering is documented (FXAA before/after pipeline path decision).
- `src/main/java/com/limitlessdev/ldog/render/msaa/MSAAFramebuffer.java`
  - Confirm resolve ordering compatibility with pipeline target ownership.
- `src/main/java/com/limitlessdev/ldog/mixin/MixinEntityRendererMSAA.java`
  - Verify no double-resolve or wrong FBO binding after pipeline integration.

### 8a execution order (concrete)

1. **Skeleton pass framework**
   - Add interfaces/classes with no-op behavior only.
2. **Render target manager**
   - Allocate internal target and handle resize/dispose safely.
3. **Mixin integration**
   - Wire one hook path with no-op pass; confirm parity rendering.
4. **Debug telemetry**
   - Add pass count/timing/logging to speed up regressions.
5. **Fallback hardening**
   - Disable failing pass/pipeline and continue rendering safely.
6. **Config/UI exposure**
   - Keep gated until parity and stability checks pass.

### 8a PR slices (recommended)

- [x] **PR-8a.1 Framework-only:** pass contract + pipeline shell + no-op pass, no visible behavior change.
  - Landed: `PostProcessPass`, `PostProcessContext`, `PostProcessPipeline`, `passes/NoOpPass`.
- [ ] **PR-8a.2 Target management:** internal render target lifecycle and resize logic.
  - Not started. `RenderTargetManager` not yet created; pipeline is currently no-op and does not own an offscreen color target.
- [~] **PR-8a.3 Runtime hook:** mixin integration + parity validation evidence.
  - Mixin landed (`MixinEntityRendererPostPipeline` on `renderWorldPass` RETURN, registered in `mixins.ldog.json`). Parity validation capture still owed.
- [x] **PR-8a.4 Stability/telemetry:** debug stats, logging, and fallback behavior.
  - Landed: `PipelineDebugStats`; `PostProcessPipeline.runPasses` removes faulty passes on execute failure; `disableAll` on fatal init/resize error; ready/resize info logs.
- [x] **PR-8a.5 Config surfacing (gating only):** experimental toggle/scale controls (post-stability only).
  - `LDOGConfig.enablePostProcessPipeline` added, **default off**, tagged experimental. Scale controls and GUI entry intentionally deferred per plan (post-stability gate).

### 8a acceptance criteria

- [ ] Pipeline can run with one no-op post pass and produce identical output to baseline (within expected floating-point variance).
- [ ] UI/HUD remains native-resolution and does not get blurred by world upscaling.
- [ ] Resize and resource reload do not leak or leave stale FBO handles.
- [ ] Runtime toggle between native and scaled world target succeeds without restart.
- [ ] Fail-safe fallback path returns to vanilla/main framebuffer rendering when pass init fails.

### 8a implementation tasks (digestible)

- [x] Add a small internal pass contract (`init`, `resize`, `execute`, `dispose`) for post-process stages. _(`PostProcessPass` also has `id()` and `isEnabled()`.)_
- [ ] Implement render-target manager for scaled world color target + transient ping-pong target.
- [x] Add orchestration hook that executes registered passes after world render and before UI. _(Injected at `RETURN` of `EntityRenderer.renderWorldPass`; UI/HUD compositing ordering to be verified as part of 8a.3 parity work.)_
- [~] Add debug metrics (active scale, pass count, pass time budget) to LDOG overlay/logging. _(`PipelineDebugStats` captures active pass count, last width/height, last frame nanos; overlay surfacing and "active scale" still pending — depends on 8a.2 target manager.)_
- [x] Add defensive error handling: disable faulty pass and continue frame without crash.

### 8a preliminary validation checklist

- [ ] Baseline parity capture: native scale with no-op pass vs current rendering path.
- [ ] UI clarity check: chat, debug text, and inventory text remain crisp at all scales.
- [ ] Compatibility check: FXAA enabled/disabled, MSAA enabled/disabled, resource reload.
- [ ] Long-session check: repeated resize and dimension changes without resource growth.
- [ ] Stress check: dense foliage + moving entities for frame pacing regression.

### 8a test protocol (minimum)

- **Manual scene set A (clarity):** chat-heavy HUD, inventory UI, signs/books text.
- **Manual scene set B (stress):** foliage biome + moving mobs + particles.
- **Manual scene set C (lifecycle):** alt-tab, window resize loop, F3+T reload.
- **Logs to inspect:** mixin apply success, FBO allocation/reallocation/dispose, fallback triggers.

### 8a definition of done

- [ ] All acceptance criteria checked.
- [ ] No known crash path from pipeline init/resize/reload.
- [ ] No persistent blur on UI/HUD.
- [ ] Performance overhead documented for native-scale no-op path.
- [ ] Phase 9a can start without reworking the 8a core abstractions.

Estimate: `M`, 2-4 weeks, plus 1 week risk buffer.

## Phase 8b - Pipeline Hardening and Compatibility

Goal: make foundation robust across current LDOG features.

- [ ] Validate interaction with FXAA, MSAA path, and resource reload behavior.
- [ ] Add config toggles and safe fallback behavior.
- [ ] Add logging for pass failures and automatic downgrade path.

Estimate: `M`, 2-3 weeks, plus 1 week risk buffer.

## Phase 9a - FSR1-style Spatial Upscaling MVP

Goal: first usable upscaler option for players.

- [ ] Implement spatial upscale pass at controlled quality presets.
- [ ] Implement optional sharpen pass with conservative defaults.
- [ ] Add GUI/config controls for render scale and quality mode.
- [ ] Add compatibility tests with selected resource packs.

Estimate: `M/L`, 3-6 weeks, plus 2 weeks risk buffer.

## Phase 9b - Quality Tuning and Validation

Goal: production-ready defaults and predictable behavior.

- [ ] Tune presets for 16x, 32x, and HD pack scenarios.
- [ ] Verify artifact profile in motion-heavy scenes.
- [ ] Add user-facing caveat docs and troubleshooting notes.

Estimate: `M`, 2-4 weeks, plus 1-2 weeks risk buffer.

## Phase 9c - Temporal Research Track (Optional)

Goal: decide if temporal upscaling is realistically achievable on this branch.

- [ ] Prototype motion vector generation feasibility.
- [ ] Prototype temporal history stability checks.
- [ ] Produce explicit go/no-go report before full implementation.

Estimate: `L`, 4-8 weeks research, plus 2-4 weeks risk buffer.

---

## Timeline Summary (S/M/L + Week Range + Buffer)

| Work Slice | Size | Base Estimate | Risk Buffer | Buffered Range |
|---|---|---|---|---|
| Phase 8a foundation | `M` | 2-4 weeks | +1 week | 3-5 weeks |
| Phase 8b hardening | `M` | 2-3 weeks | +1 week | 3-4 weeks |
| Phase 9a FSR1 MVP | `M/L` | 3-6 weeks | +2 weeks | 5-8 weeks |
| Phase 9b quality tuning | `M` | 2-4 weeks | +1-2 weeks | 3-6 weeks |
| Phase 9c temporal research | `L` | 4-8 weeks | +2-4 weeks | 6-12 weeks |

Notes:
- Estimates assume no major platform/API shifts.
- If scope expands to temporal production implementation, schedule and risk profile increase materially.

---

## Go/No-Go Gates

Before Phase 9a starts:
- Phase 8a/8b must be stable in normal gameplay and reload cycles.

Before any temporal implementation:
- motion vectors and temporal history reliability must be proven in prototype.
- legal/compliance review must be completed for any new external dependencies.

Before any DLSS-class revisit:
- explicit project decision for platform shift and native integration model.

---

## Contributor Start-Here Path

If you are picking this up next:

1. Implement Phase 8a scaffold first (no temporal features).
2. Verify pass ordering and UI compositing.
3. Move to Phase 9a FSR1 MVP only after 8a/8b acceptance criteria are met.

This keeps execution incremental and avoids overcommitting to high-risk paths too early.

---

## Link Back to Main Attack Plan

Primary roadmap remains in `docs/ATTACK_PLAN.md`.
This file is the deep technical reference for the `Phase 8` and `Phase 9` items only.

