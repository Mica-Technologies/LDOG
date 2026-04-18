# Post-Phase 9a.4 Research

Written while user was AFK after shipping the FSR1-Quality (direction-biased EASU) upscaler. Catalogs the realistic next-step options, with implementation sketches and risk assessments so the next session can pick decisively.

## TL;DR recommendation order

1. **Quality Presets (GUI-only, 30 min)** — bundles `internalRenderScale` + `upscalerAlgorithm` + `fsr1Sharpness` into a single dropdown so users pick "Quality" instead of tuning three dials. Zero new rendering code. Ship first.
2. **RCAS standalone sharpen pass (1-2 hrs)** — new post-upscale sharpen that also works at scale=1.0 (purely a sharpen mode, no upscaling needed). Small GL work, reuses `ShaderProgram` infrastructure. Needs a main-dims ping-pong target.
3. **Phase 9b quality validation protocol (doc-only)** — test matrix + artifact catalog for the three upscalers across resource packs. Not "development", but the artifact is a test plan so regressions have a reference.
4. **Phase 10b runtime-togglable borderless (3-6 hrs, HIGH RISK)** — `Display.destroy()` + `Display.create()` with coordinated LDOG-subsystem GL cleanup. Ships only if users complain about restart-required behavior.
5. **Phase 9c temporal upscaling research (weeks)** — deferred. See `docs/P8_RESEARCH_AND_PLAN.md` for the existing detailed plan.

---

## 1. Quality Presets

### Motivation

Three sliders (scale + upscaler + sharpness) means users need to understand the interactions. A preset dropdown gives them one-click options with sensible defaults.

### Proposed presets

| Name | Scale | Upscaler | Sharpness | Target |
|---|---|---|---|---|
| `Native` | 1.00 | Bilinear | 0.00 | baseline, perf overhead negligible |
| `Ultra` | 0.85 | FSR1-Quality | 0.75 | light perf boost, near-native visual quality |
| `Quality` | 0.75 | FSR1-Quality | 1.00 | balanced quality/perf |
| `Balanced` | 0.67 | FSR1 | 1.25 | noticeable perf gain, accept softer edges |
| `Performance` | 0.50 | FSR1 | 1.50 | max perf, visibly softer |
| `Custom` | — | — | — | when user touches individual sliders, preset flips to Custom |

### Implementation sketch

- New `LDOGConfig.upscalerPreset` (string, default "custom").
- New `UpscalerPreset` enum with a `apply(LDOGConfig)` method that writes the three fields atomically.
- GUI: add "Preset" cycle button in Post-Process section, above the three individual controls.
- Individual slider clicks reset preset to "custom" so users don't get confused about which is active.
- No shader or pipeline changes.

### Risk

Trivial. All changes are in `LDOGConfig`, `GuiLDOGSettings`, and a new tiny enum class.

---

## 2. RCAS Standalone Sharpen Pass

### Motivation

Some users run native resolution (scale=1.0) but still want edge sharpening — e.g. they picked a 16x resource pack and want it to look a bit crisper without rendering at reduced resolution. Right now, they can enable FSR1 at scale=1.0 but the sharpen amplitude degenerates because contrast gating is calibrated for upscaled content.

RCAS (Robust Contrast Adaptive Sharpening) is AMD's companion to FSR1. It's purely a sharpening pass — no upscaling component. Can run at any scale.

### Implementation sketch

**Config:**
- `LDOGConfig.enableRcasSharpen` (bool, default false)
- `LDOGConfig.rcasStrength` (double, 0-1, default 0.4)

**New pass: `RCASSharpenPass`**
- Runs AFTER the upscaler pass (last in the chain).
- Reads the current main-FB contents, applies sharpen, writes back.
- Since you can't sample what you're rendering to, need a main-dims ping-pong target:
  - Option A (simpler): `glCopyTexSubImage2D` from main FB to a texture owned by RCAS, then draw a fullscreen pass sampling that texture.
  - Option B (cleaner): expand `RenderTargetManager` with a `mainPingPong` target at main dims, blit main→mainPingPong, then shader reads mainPingPong and writes main.

Option B is more reusable — other future passes (bloom, tonemapping) will want a main-dims ping-pong too.

**Shader (GLSL 120):** similar structure to current FSR1 — 5-tap unsharp mask with contrast clamping. Can reuse most of that code. The difference from FSR1 EASU is:
- RCAS does NOT upscale — always operates on 1:1 pixels.
- Uses a specific negative-lobe filter kernel (public AMD spec) for better visual result than plain unsharp-mask.
- Clamping uses the 4-neighbor diamond (not a 3x3 box).

**Pipeline ordering:**
- BilinearBlitPass / FSR1 / FSR1Quality: one runs per frame at their isEnabled() gate.
- RCASSharpenPass: runs after the upscaler, isEnabled() = `LDOGConfig.enableRcasSharpen && bindingActive`.

**GUI:** new toggle + slider in Post-Process section.

### Risk

Medium. Requires expanding `RenderTargetManager` for a main-dims ping-pong, then a new shader pass. Shader is simple but the ping-pong plumbing is new territory for the pipeline. ShaderProgram utility is already battle-tested.

### Open questions

- Can RCAS run when upscaler is Bilinear at scale=1.0? Yes — it just sharpens the bilinear-blitted output. But at scale=1.0 the "bilinear blit" is a 1:1 copy, so effectively RCAS reads the rendered world texels directly. Works fine.
- What about when pipeline is off entirely? RCAS wouldn't run — its isEnabled() requires bindingActive. That's the right behavior (no pipeline = no LDOG post-processing).

---

## 3. Phase 9b: Quality Validation Protocol

### Artifact goal

A markdown test plan under `docs/PHASE_9B_VALIDATION.md` with:
- List of target resource packs (vanilla 16x, 32x HD, Stratum 256x — what the user already has).
- Reference scenes (exact biome coordinates or screenshot recipes).
- Scoring rubric per pack × upscaler × scale combination.
- Artifact catalog: what each upscaler does well / poorly.

### Approach

Not pure development. User-driven testing. I can write the doc but the actual scoring happens with the user running the client and eyeballing.

Defer until user signals they want to invest in quality testing. Ship after 9b is green-lit.

---

## 4. Phase 10b: Runtime-Togglable Borderless

### Motivation

Current Phase 10 requires a game restart to toggle borderless mode. Real fix is destroy+create the Display at runtime, which LWJGL 2.9.4 supports but requires coordinated cleanup.

### Why it's hard

`Display.destroy()` invalidates the entire OpenGL context. **Every GL handle LDOG owns dies**:
- `RenderTargetManager` FBOs (scene + ping-pong + their textures).
- `FSR1EASUPass` + `FSR1QualityPass` shader programs.
- `TTFFontHandler` custom font atlas texture.
- `MSAAFramebuffer` multisampled FBO + renderbuffers.
- Anisotropic filtering atlas bindings (actually those are on MC's atlas textures, which MC's refreshResources handles, so OK).
- Anyone else who created GL resources directly.

MC's `refreshResources()` reloads MC's own atlases but NOT mod-owned GL state.

### Implementation sketch

- `BorderlessFullscreenHandler.toggleAtRuntime()`:
  1. Dispose all LDOG GL state (pipeline, shaders, font atlas, MSAA).
  2. `Display.destroy()`.
  3. Update `System.setProperty("org.lwjgl.opengl.Window.undecorated", ...)` to new value.
  4. `Display.setDisplayMode(...)` with appropriate dims.
  5. `Display.create(new PixelFormat().withDepthBits(24))`.
  6. `mc.refreshResources()` — reloads MC atlases.
  7. LDOG subsystems lazy-reinit on next use (pipeline's ensureInitialized, etc.).

**Blocking subsystem changes:**
- `RenderTargetManager.dispose()` already exists. ✅
- `FSR1EASUPass` / `FSR1QualityPass`: `dispose()` exists. ✅
- `TTFFontHandler`: needs a `dispose()` + rebuild on resource reload. Probably already handled by `refreshResources()` since font is a ResourceLocation-registered texture. Verify.
- `MSAAFramebuffer.dispose()` exists. ✅

Actually much of this is already designed for dispose — the risk is lower than I flagged initially.

### Risk

Medium-high, not "definitely breaks". The hard part is ordering: make sure `Display.destroy()` happens AFTER every LDOG subsystem has released its GL handles, otherwise the destroy tries to free already-invalid handles. A central `LDOGRenderingLifecycle` event bus would help coordinate.

### Priority

Low — current restart-required behavior is workable. Revisit if multiple users request it.

---

## 5. Phase 9c: Temporal Upscaling Prerequisites

See `docs/P8_RESEARCH_AND_PLAN.md` for the existing detailed research plan (R1/R2/R3 tracks).

### What would need to exist before even a prototype FSR2

- **Motion vectors**: per-pixel screen-space velocity of world geometry. Requires hooking every MC draw call to emit velocity data. In MC 1.12.2's immediate-mode pipeline this is invasive — no modern vertex shader pipeline to hook cleanly.
- **Jittered projection**: sub-pixel camera offsets per frame to sample different pixel positions over time.
- **History buffer**: previous-frame color + validity mask for reprojection.
- **Disocclusion detection**: detect newly-visible pixels that have no history.
- **Reactive mask**: mark alpha-tested geometry (leaves, fences, glass) that should use less temporal accumulation.

### Realistic scope

Weeks of work to get a prototype that's anywhere close to FSR2 quality. Likely bounded by MC 1.12.2's architectural limitations.

### Priority

Deferred indefinitely. Document only for completeness.

---

## Concrete proposal for next session

**Ship order:**

1. **Quality Presets** (30 min). Low risk, immediate UX win.
2. **RCAS standalone sharpen** (1-2 hrs). Adds a valuable mode (scale=1.0 sharpening).
3. Leave 9b/9c/10b as documented backlog.

**What each looks like done:**

- Quality Presets: new "Preset" button above the three individual controls. Cycles Native/Ultra/Quality/Balanced/Performance/Custom. Clicking preset writes all three config fields.
- RCAS sharpen: new "RCAS Sharpen" toggle + strength slider in Post-Process. Works independently of the upscaler choice (including Bilinear at scale=1.0).

Both are building on already-shipped infrastructure (ShaderProgram, pipeline pass framework, upscaler selector). No new core systems.
