# LDOG Master App Plan

Single source of truth for LDOG's development roadmap, phase-by-phase status, research notes, and pickup state. Consolidates what used to live in `ATTACK_PLAN.md`, `FEASIBILITY.md`, `MOD_CONSOLIDATION.md`, `PHASE1_RESEARCH.md`, `P8_RESEARCH_AND_PLAN.md`, `POST_9A4_RESEARCH.md`, `PHASE_9C_TEMPORAL_DEEP_DIVE.md`, and `PHASE_9C3_OPTION_C_RESEARCH.md`.

For architecture and code-level conventions, see `docs/ARCHITECTURE.md` and `docs/CONVENTIONS.md` — those stay as informational references and are *not* duplicated here.

Status legend used throughout: `[x]` complete · `[~]` partial / unverified · `[ ]` outstanding · `[defer]` deliberately deferred.

---

## 1. Project Goal

LDOG (Limitless Development Optigame) is a Minecraft 1.12.2 Forge client mod that aims to be a high-performance, open-source replacement for OptiFine. Built with the GregTechCEu Buildscripts (RFG 1.4.0) wrapper. Client-only, config-driven, mixin-first.

Key feature targets:

| Area | Priority | Status |
|---|---|---|
| Rendering Optimizations | High | shipped (Phase 1) |
| Connected Textures (CTM) | High | shipped (Phase 3) |
| Emissive Textures | High | shipped (Phase 4) |
| Dynamic Lights | Medium | shipped (Phase 5) |
| HD Textures | Medium | shipped (Phase 2) |
| Custom Sky | Medium | shipped (Phase 6d) |
| Shader Pipeline | Stretch | foundation + HDR + Bloom + composite/final runner (2026-05-22); gbuffer dispatch v1 (opt-in, single-target) + LDOG built-in packs + dimension/include real-pack loading (2026-06-14). MRT gbuffers + shadow pass still open. |
| Temporal upscaling | High | shipped: TAA MVP (9c.1), camera MV (9c.2), entity reactive mask (9c.3-A), entity MV via BBox stamps (9c.3-C), FSR 2 reconstruction kernel (9c.4) — all LDOG-original on the 1.12.2 OpenGL 2.1 stack |

Long-term: replace 5-7 separate optimization mods in the alto modpack with one integrated mod (see §3).

---

## 2. Resume Prompt (for next session)

> We're building LDOG (`ldog`), an open-source OptiFine replacement for Minecraft Forge 1.12.2. The project is at `E:\gitRepos\LDOG`. Build system is GregTechCEu Buildscripts (RFG 1.4.0). Reference projects for conventions: `E:\gitRepos\minecraft-city-super-mod` and `E:\gitRepos\LDFAWE`. Read `CLAUDE.md`, `docs/MASTER_APP_PLAN.md`, `docs/ARCHITECTURE.md`, and `docs/CONVENTIONS.md` to get up to speed, then pick up at the next open item in §5 or §11 (backlog).
>
> Build: `JAVA_HOME=/c/Users/ahawk/.jdks/azul-17.0.18 ./gradlew compileJava` (or `build` for full + tests, or `runClient` for an in-game smoke test). Currently zero compile warnings, 50 unit tests passing. Default windows shell is PowerShell — Bash tool uses bash syntax.

### Where work left off (last session: 2026-06-14 — gbuffer dispatch + built-in packs + real-pack loading)

Tackled the headline OF-parity gap (per-object gbuffer shaders) plus a user request (LDOG-bundled shader packs), and fixed real-pack discovery. All compiles clean (zero mixin warnings), 50 unit tests pass, verified live in `runClient`.

1. **Gbuffer dispatch v1 (opt-in)** — the active pack's `gbuffers_*` programs now shade world geometry, not just the post-process composite chain.
   - `ShaderPackRuntime` now compiles every `gbuffers_*` + `shadow` program the pack ships (keyed map, fallback-resolvable). New `hasGbuffers()` / `hasCompositeChain()` split so a gbuffer-only pack doesn't trip the composite pass.
   - `GbufferProgram` enum — draw categories (SKY_BASIC/TEXTURED, CLOUDS, TERRAIN_SOLID/CUTOUT/TRANSLUCENT, ENTITIES, WEATHER, HAND) each with the OF/Iris fallback chain (e.g. terrain_cutout → terrain → textured_lit → textured → basic).
   - `ShaderPackGbufferManager` — `begin(category)`/`end()` bind the resolved program, feed the standard uniform set (reusing `ShaderPackUniforms`, snapshotted once per frame at renderSky HEAD when GL matrices are the real camera matrices) + sampler bindings (texture=0, lightmap=1, normals/specular/shadow→black). Program-stack save/restore. **Key enabler**: MC 1.12.2 renders the world fixed-function (no program bound), and both immediate-mode AND chunk-VBO draws feed `gl_Vertex`/`gl_Color`/`gl_MultiTexCoord`/`gl_ModelViewMatrix`, so a `#version 120` gbuffer shader using `ftransform()` transforms+textures correctly with no custom vertex format.
   - Hooks (all gated behind `enableShaderGbuffers`, default OFF — the working composite-only path is never disturbed): `MixinRenderGlobal` wraps `renderBlockLayer` (per-layer terrain/water), `renderClouds`, `renderEntities`; `MixinEntityRendererWeather` wraps `renderRainSnow`; `MixinItemRendererHand` wraps `renderItemInFirstPerson`.
   - **v1 limitations (the remaining gap)**: single colour target (no MRT `DRAWBUFFERS` → deferred-lighting packs only partially driven); no shadow pass (shadow programs compiled but not run); sky NOT dispatched (renderSky draws both untextured gradient + textured sun/moon in one method — needs finer hooks to avoid an untextured-sun regression); custom vertex attrs (mc_Entity, at_tangent) absent → degrade to zero.
   - **runClient bug caught + fixed**: `renderClouds` in 1.12.2 is `(F,I,D,D,D)` not `(F,I)`. Wrong descriptor failed the ENTIRE `MixinRenderGlobal` apply (silently disabling entity culling + custom sky + reactive mask). Fixed; re-verified "renderSky mixin CONFIRMED".

2. **Built-in LDOG shader packs** (user request — "sensible/common packs like HDR, realism, RTX") — LDOG ships its own packs inside the jar, unlike OF. `BuiltinShaderPack` (classpath-backed) + `BuiltinShaderPacks` registry, listed in the picker with an `[LDOG]` prefix after `(none)`. Four shipped, all composite/final + GLSL 120 so they ALWAYS compile + run: **HDR** (ACES + bright-pass glow + saturation), **Realism** (unsharp + balanced grade + vignette), **Cinematic** (teal/orange split-tone + filmic + vignette), **Pseudo-RTX** (screen-space depth AO contact shadows + cool ambient bounce + filmic; reads depthtex0 so needs the pipeline). GLSL under `assets/ldog/shaderpacks/<dir>/shaders/`. Verified: `[LDOG] HDR` activates, compiles, runs live in-world.

3. **Real-pack loading fixes** — diagnosed why the user's downloaded packs "did nothing": `ShaderPack` now does **dimension-aware resolution** (`resolvePath` tries `worldN/<file>` then root — modern packs like BSL v10 / Complementary Reimagined put programs under `shaders/world0/` etc., not the flat root) + recursive **`#include` preprocessing** (OF/Iris semantics, abs-from-root vs relative, cycle-guarded). `worldDir` set from the current dimension at activation. Better activation log explains incompatibility (GLSL 330+/Iris 1.16+ packs can't run on 1.12.2 GL 2.1). NOTE: this makes genuine 1.12.2-format packs work far better, but does NOT make MC 1.16+ packs (BSL v10, Complementary r5, Solas, Continuum 2.0.5) run — those are GLSL-330-core and fundamentally incompatible, same as OF-for-1.12.2 can't run them.

### Same session continued — MRT G-buffers + shadow pass (commit pending)

Built the deferred renderer's two load-bearing halves on top of the gbuffer dispatch above.

4. **MRT G-buffers** — `ShaderPackRuntime.Stage` now parses each program's `/* DRAWBUFFERS:NNN */` (and Iris `RENDERTARGETS:`) directive; `ShaderPackGbufferManager` owns a G-buffer FBO that **reuses** the pipeline's `sceneColorTex` as colortex0 + `sceneDepthTex`, and adds aux `colortex1..N` attachments (N = max index any program writes). The bind mixin (`MixinEntityRendererPostPipeline`) routes the world render into this MRT FBO when deferred; each gbuffer `begin()` applies `glDrawBuffers` from the program's directive so `gl_FragData[i]` lands in the right colortex; aux cleared to 0 each frame, draw buffers narrowed to colortex0 for un-dispatched draws. The composite pass now binds the **real** colortex0..7 (instead of 1×1 black) so deferred-lighting composites finally receive normal/material data. Reactive-mask path is mutually exclusive with deferred (guarded).
5. **Shadow pass** — `ShadowMapManager` renders a depth-only, camera-relative sun/moon-POV pass of opaque+cutout terrain into `shadowtex0` (via swapping GL matrices: `glOrtho` + `gluLookAt` from the light dir, then re-invoking `RenderGlobal.renderBlockLayer`; matrices read back with `glGetFloat` for the uniforms — no hand-rolled matrix math). Hooked at `renderEntities` HEAD (terrain + chunk list ready). Feeds `shadowProjection`/`shadowModelView` (+inverse) + `shadowMapResolution` and binds `shadowtex0/1` on unit 9 to both gbuffer and composite stages. A `shadowPass` flag suppresses gbuffer dispatch during the replay. Full GL push/pop + FBO/viewport save-restore. Gated behind `enableShaderShadows` (opt-in, default off) + `shaderShadowResolution`/`shaderShadowDistance` config.
6. **`[LDOG] Deferred` built-in pack** exercises the whole path: `gbuffers_terrain` writes lit+shadowed albedo to colortex0 + normal to colortex1 (`DRAWBUFFERS:01`) and samples the shadow map in-shader (using `gbufferModelViewInverse` → camera-relative world → shadow clip); `gbuffers_textured`/`basic` for entities/clouds; `final` tonemaps + reads colortex1 to prove the MRT round-trip.

**Verification status**: compiles clean; **in-world runtime VERIFIED** (runClient with `[LDOG] Deferred` + gbuffers + shadows on): logs `G-buffer ready — colortex0 (scene) + 1 aux`, `gbuffer dispatch ACTIVE … MRT=true (colortex0..1)`, `Shadow map ready (2048x2048 depth)`, `runner live`, and crucially **no `Shadow terrain pass failed`** — the risky sun-POV terrain replay ran clean. The only GL error (`1282 @ Post render`) is **pre-existing** (present in runs before this work, flags off) — NOT introduced by the deferred path. STILL TODO: visual tuning of shadow matrices/bias + correctness check of MRT lighting with a real 1.12.2 pack (machinery confirmed sound; the look needs eyes-on iteration).

### Same session continued — noisetex + uniforms + multi-write composites

7. **noisetex + common uniforms** (`7917484`) — `ShaderNoiseTexture` (256² fixed-seed REPEAT noise) bound to the `noisetex` sampler in gbuffer (unit 3) + composite (unit 10) + `noiseTextureResolution`. `ShaderPackUniforms` also now feeds `fogColor` (live `GL_FOG_COLOR`), `skyColor`, `nightVision`/`blindness`, `screenBrightness`, `eyeBrightness`/`eyeBrightnessSmooth`. `ShaderProgram.setUniform3f` added. Verified in-world (composite-only path, no new GL errors).
8. **Multi-write composite chains** (`959e961`) — `ShaderPackCompositePass` implements OF's colortex-flipping model on the deferred path: each composite stage reads the current colortex set + writes its `DRAWBUFFERS` targets to scratch textures that flip in (unwritten buffers persist). Engages only when a deferred pack writes colortex>0 (`usesMultiWrite`); else the single-target ping-pong runs unchanged. Wrapped in try/catch → falls back to single-target on any failure (can't break the working path). `[LDOG] Deferred` pack gains `composite`/`composite1` stages exercising the flip. **Multi-write flip NOT yet in-world-verified** (active pack was switched to Pseudo-RTX mid-test) — compile-clean + guarded; next: select `[LDOG] Deferred` (or a real multi-buffer pack) and confirm.

**Still deferred**: separate shadow frustum (v1 uses camera visibility); entity/TESR shadow casters; sky gbuffer split; custom vertex attrs (mc_Entity, at_tangent); per-dimension recompile on dim change; composite running at scene-res before upscale (currently runs late in the LDOG pipeline — an OF-semantics mismatch worth revisiting); the pre-existing `1282 @ Post render` GL error (predates this work).

### Where work left off (2026-05-22 — temporal upscaling + UX overhaul + shader runner)

Pushed nine commits across three thematic chunks: shipped the deferred temporal-upscaling work (Option C + FSR 2), did a comprehensive UX overhaul (tooltips, tabs, GUI reorg), and built the shader-pack composite runner so activated packs actually do something visible.

**Render features**:

1. **Phase 9c.3-C Option C** (`9264bb2`) — per-entity motion vectors via BBox-projected velocity stamps (Strategy 4 from §10). LDOG-original. New: `EntityRenderStateCache` (UUID-keyed WeakHashMap of cur+prev positions), `MotionVectorTarget` (half-res RG16F via `RenderTargetManager`), `EntityMotionVectorPass`, `MixinRenderManagerEntityMV` (one hook covers all entity types — mobs, items, XP orbs, projectiles, paintings, modded). TAA shader prefers entity MV over camera-only MV when present; reactive-mask drop bypassed where entity MV exists so entity pixels retain accumulated detail.
2. **Phase 9c.4 FSR 2** (`1e9cdde`) — LDOG-original Lanczos-3 temporal reconstruction. New `FSR2ReconstructionPass` combines the full 9c stack (jitter + camera MV + entity MV + reactive mask + history) into a single scaled→native pass with a 5×5 Lanczos source kernel + neighborhood color clamping + reactive weighting + light final-stage sharpen. Selectable as the `FSR2` upscaler algorithm; standalone TAA pass short-circuits when active (FSR2 owns history accumulation). EntityMotionVectorPass moved earlier in chain so MV target is populated before FSR2 samples it.

**Critical bug fixes (caught during runClient verification)**:

- **Three load-order bugs unbreak runClient** (`6e7c872`) — the day's earlier work crashed/no-op'd on first launch:
   - `MixinParticleManagerFilter$SpawnCounter` was a nested class in the `mixin` package; mixin booter refuses to load non-mixin classes from its owned package. `IllegalClassLoadError` at mod init. Moved counter to `render.particles.ParticleSpawnCounter`.
   - `MixinPotionColor`, `MixinDyeColor`, `MixinGuiIngameVignette/Crosshair/Hud` were registered in the late mixin config but their target classes load during `Bootstrap.register` before the late loader fires. Each logged "Critical problem ... loaded too early" and silently no-op'd — Phase 6c potion/dye overrides + the Comfort/Cinematic hide toggles were broken. Moved to `mixins.ldog.vanilla.json` (early `IEarlyMixinLoader`).
   - `MixinEntityRendererPostPipeline` was still using the 3-arg `RenderTargetManager.ensure()` overload which defaulted HDR=false. Every frame the pipeline's `ensure(..., true)` and this mixin's `ensure(..., false)` fought, reallocating targets — symptom: enabling HDR Pipeline made the world black (each frame rendered into a fresh empty target). Pass `LDOGConfig.enableHDRPipeline` explicitly.

**UX overhaul (player-focused)**:

- **Tooltip cleanup** (`7e52605`) — stripped phase numbers + GL jargon from existing tooltips. `"TAA (9c.1)"` → `"TAA"` on button labels. Tooltips for `Pipeline`, `AutoScale`, `TAA Enable`, `Reactive Mask`, `OF Shaders` row rewritten as player-facing prose.
- **Comprehensive tooltips** (`bc3df8d`) — added ~60 tooltips for previously-undocumented buttons across every section: Performance + per-particle, FPS Management, AF / MSAA / FXAA, HDR + Bloom, Atmosphere, Comfort (10 toggles), Info HUD additions, Hide HUD elements, QoL, Visual (water + presets + grass/snow/natural/colors/mobs), Dynamic Lights + interval, Light Customization (preset + 6 RGB sliders + brightness + night darkness + HDR lightmap), Pack feature toggles, TTF Bold/Italic/Subpixel.
- **Section reorg** (`af7ac9d`) — moved `Display` next to Comfort cluster (was awkwardly between Anti-aliasing and Post-Process); folded one-row Quality of Life into Hide HUD Elements.
- **3 mixin warnings + dedicated shader picker screen** (`fd7d9bd`) — added `remap = false` to three `@At` annotations targeting LWJGL methods (`Project.gluPerspective` ×2, `Display.setFullscreen`); LWJGL classes have no SRG mapping so the warnings were spurious. Also built standalone `GuiShaderPackPicker` as a list-style replacement for the cycle button.
- **Tabbed settings GUI** (`ed2defe`) — six tabs across the top of the settings screen: General / Rendering / Visual / Features / Shaders / UI-HUD. Active tab underlined yellow. Implementation: single `populateForActiveTab` method with `if (activeTab == X)` wraps around each existing section (no physical line reordering). Shaders tab is special — bypasses `settingsList` and embeds `GuiShaderPackList` directly. Tab switches deferred to next `updateScreen` tick via `pendingTabSwitch` field so we never clear `buttonList` mid-`mouseClicked`. `activeTab` is `static` so it survives child-screen round-trips.

**Shader pack composite runner**:

- **Composite + final stages run live** (`e056e67`) — activated packs now actually produce visible output. New `ShaderPackUniforms` computes + binds the standard OF/Iris uniform set (cameraPosition, sunPosition, moonPosition, gbufferModelView+Projection +Inverse +Previous, frameCounter, frameTimeCounter, viewWidth/Height, invMainSize, sunAngle, rainStrength, worldTime, isEyeInWater, near/far, aspectRatio) using OF naming convention so unmodified pack shaders link. New `ShaderPackRuntime` owns compiled GL programs for one active pack; compiles `composite.{vsh,fsh}` → `composite15` and terminal `final.{vsh,fsh}` on activate, disposes on deactivate; per-stage compile failures drop that stage but the rest run. New `ShaderPackCompositePass` (pipeline pass, runs after FXAA before vignette): copies main FB → `colortex0`, ping-pongs across two main-res FBOs through each composite stage feeding standard uniforms, then runs `final` to main FB (or blits the last composite output back when no `final.fsh`). `colortex1..7` + `depthtex1..2` bound to a 1×1 black texture so packs referencing uninitialized slots get safe zeros. **Scope still excludes**: gbuffer programs (per-object draw shaders) + shadow pass — the next big chunks for full OF compatibility. Composite is where most pack visual identity lives so a lot of packs partially work today.

**Verified live**: ran `gradlew runClient` after each major commit. Build clean, zero mixin warnings, 11-pass pipeline initializes, FSR 2 shader compiles, entity MV pass live (`LDOG: TAA entity MV reprojection ACTIVE`).

**Deferred (next session)**:
- **Shader pack gbuffer support** — full OF compatibility wants per-object draw shaders. ~weeks of work: identify every MC draw-call type (terrain solid/cutout/water, entities, sky, clouds, weather, particles, item, hand), hook each one to bind the right `gbuffers_*.{vsh,fsh}` from the active pack and feed vertex-attribute uniforms. Composite-only runner today produces visible output for most packs but doesn't drive per-object effects.
- **Shadow pass** — depth-only second world render from the sun's POV → `shadowtex0/1`. Required by realistic packs.
- **Phase C2 memory opts** (Vintage Fix + Censored ASM absorptions) — not started.
- **Phase 10b runtime-togglable borderless** — needs coordinated GL subsystem dispose. Risky.
- **Phase 9c.5 reactive mask polish + alpha-cutout classification** — minor polish on top of 9c.3 stack.

### Pre-2026-05-22 session — "hammer the master plan" (2026-05-21)

Pushed nine commits across small items + HDR + bloom + shader-pack scaffold. Then ran `runClient` and caught three load-order bugs (fixed in the 2026-05-22 session — see above).

1. **Phase 6c colors** (`25665b1`) — per-biome water, potion, dye colors via OF-format `color.properties`. Three new override maps in `CustomColorHandler`; `MixinPotionColor` + `MixinDyeColor` mixins. Water flows through `BiomeBlend.waterColorFor` so it's honored both at radius 1 and inside the smooth-biome kernel. Map colors deliberately not implemented (too invasive — patching `MapColor.COLORS` static array).
2. **Tier A + B** (`16a5115`) — Tier A: advanced item tooltips toggle (tick-handler-driven flag flip), nausea distortion suppression via `ModifyVariable` on `EntityRenderer.renderWorldPass`, Info HUD rows for Ping/Day/CPS (left+right mouse, 1-second sliding window). Tier B: hide armor/hunger/air/boss-health bars via Forge `RenderGameOverlayEvent.Pre` cancel — cleaner than mixing into `GuiIngame.renderPlayerStats` profiler sections.
3. **C3 + C4 polish** (`5f0c59b`) — Tooltip body for the 7 OF Interop GUI rows. C3: wire `ttfBold` + `ttfItalic` to GUI rows (config fields existed since C3 ship; just no UI surface); add `ttfSubpixel` config + GUI row driving `TTFFontRasterizer`'s `TEXT_ANTIALIAS_LCD_HRGB` path.
4. **Phase 1 perf + 9b doc** (`8b29d26`) — Phase 1 A2 skip-empty-sections (`RenderChunk.rebuildChunk` short-circuits when `ExtendedBlockStorage` is null/empty, saving 4096 `getBlockState` calls per section), C2 particle spawn cap, and `docs/PHASE_9B_VALIDATION.md` — user-driven test protocol for upscaler quality.
5. **HDR pipeline** (`74e468d`) — `RenderTargetManager` grows an HDR mode; when `enableHDRPipeline` is on, scene + ping color textures allocate as `GL_RGBA16F`. New `HDRTonemapPass` runs first in the chain with four operators (ACES filmic, Reinhard, Uncharted 2/Hable, linear) + user-tunable exposure. Also fixes `MixinRenderChunk` `@Shadow` — `RenderChunk.position` is private `MutableBlockPos`; switched to `@Shadow on getPosition()`.
6. **Bloom pass** (`5a924c6`) — `BloomPass` runs BEFORE HDR tonemap so bright-pass shader sees HDR values exceeding [0,1]. Three stages: bright-pass (luminance threshold, quadratic weight), separable 9-tap Gaussian blur (half-res ping-pongs), additive composite.
7. **Shader-pack discovery scaffold** (`17f243a`) — `ShaderPack` abstract + `DirectoryShaderPack` + `ZipShaderPack` (auto-detects shaders-at-root vs nested layout). `ShaderPackManager` scans `<.minecraft>/shaderpacks/`, exposes name list for GUI cycling. **Was log-only at the time** — composite runner shipped in the 2026-05-22 session.

### Pre-2026-05-21 session — "more options the merrier" (2026-04-18)

Shipped in two pushes that day:

1. **9a.9 auto-scale log fix** (`3374a86`) — one-shot INFO on first tick + per-decision DEBUG. User-verified.
2. **9c.3-A entity reactive mask** (`14628e1`) — MRT + per-attachment colorMaski to stamp entity pixels into a COLOR1 R8 attachment of sceneFbo; TAA drops history weight on flagged pixels. Kills moving-mob ghost trails without per-entity MV. User-verified. Companion plan for full Option C in §10.
3. **Phase 6d closure** (`2719c53`) — doc fix only; sky was already verified earlier.
4. **9a.9 ext — aggressive 3-state mode** (`9d77526`) — `AutoScaleMode` enum (Off/Normal/Aggressive). Aggressive adds a 7-tier extended ladder that also drives `upscalerAlgorithm` + `fxaaQuality` + `enableFXAA`. Config schema: `enableAutoScale` removed, `autoScaleMode` added.
5. **Phase C4 foundation** (`3b083bc`) — per-feature OptiFine override mode: `OFOverrideMode` enum (AUTO/LDOG_OVERRIDE/OPTIFINE_OVERRIDE), `OFFeature` catalog (7 features), `OFConfigBridge` lazy reflective probe on the GameSettings instance (OF stores feature toggles as instance fields on vanilla GameSettings via its transformer — NOT on a static Config class). Legacy `shouldHandle*` methods preserved as wrappers. New "OptiFine Interop" GUI section only rendered when OF detected. Defaults to AUTO — zero behavior change.
6. **Future Expansion Ideas doc** (`6ade2fd`, `6749a9c`) — 15 gaps from walking the OF jar (§13).
7. **Per-particle toggles** (`e25f1c7`) — 5 categories: Firework/Portal/Potion/Water/Dripping. Cancel at spawn via `MixinParticleManagerFilter`; class-name suffix matching.
8. **Vignette pass** (`68d4435`) — `VignettePass` runs LAST in the pipeline. Multiplicative GL_DST_COLOR blend (darken-only).
9. **Atmosphere section** (`e25d79d`) — cloud height override, fog distance multiplier, sun/moon size, weather render toggle + density, biome blend radius 1→2→3.
10. **Comfort/Cinematic + Info HUD** (`39fe2a2`, `9a258bc`, `374789c`) — 10 comfort toggles + 5-row info overlay (coords/facing/time/biome/light level).

**Pending verification** (deferred to later sessions):
- **C4 OptiFine interop**: needs in-prod test with real OF in a real MC launcher install. Look for `LDOG: OF interop bridge ready — N feature(s) controllable, M unmapped (...)` in logs to know which `OFFeature.candidateFieldNames` are right.
- This session's new features (particle filter, vignette, atmosphere, comfort, HUD hides, info overlays) — need in-game smoke-test.

---

## 3. Feasibility Snapshot

Tier 1 (high feasibility, high value — all shipped): rendering opts, CTM, HD textures, dynamic lights, better grass/snow.
Tier 2 (medium — mostly shipped): emissive, custom sky, custom colors, AA/AF, natural textures, random mobs.
Tier 3 (hard / stretch): full GLSL shader pack support (loading external `.zip` shader packs with vertex+fragment+composite stages); CEM (custom entity models); custom GUIs.

Open-source references (concept-only — no code copying per LDOG policy):

| Project | License | Use |
|---|---|---|
| ConnectedTexturesMod | MIT | CTM implementation patterns |
| AtomicStryker Dynamic Lights | Open | Dynamic lighting approach |
| FoamFix / VintageFix | GPL-3.0 | Memory/render opts |
| VanillaFix | MIT | Vanilla bug fixes |
| BetterFPS | MIT | Performance opts |
| ShadersMod | LGPL | Pre-OptiFine shader pipeline reference |
| Super Resolution (187J3X1-114514/superresolution) | — | FSR/temporal upscaling — but targets MC 1.18+ with GL 4.3 compute + Vulkan, near-zero direct reuse for 1.12.2 |
| Radiance (Minecraft-Radiance/Radiance) | — | Full Vulkan renderer replacement — bypasses MC's pipeline entirely, not applicable to LDOG approach |
| hancin/Fullscreen-Windowed-Minecraft | — | Concept reference for Phase 10b runtime borderless |

Risks: shader-pack compatibility (modern packs expect the full OF shader API; partial may frustrate users more than nothing); mod compatibility (some mods check for OF presence — provide shims); scope creep (10+ years of OF features — resist matching everything).

---

## 4. Mod Consolidation Plan

The alto modpack uses 10+ separate optimization/rendering mods. LDOG absorbs them in phases.

| Mod | Function | LDOG Strategy | Status |
|---|---|---|---|
| OptiFine | Shaders/CTM/emissive/render | **Replace** (primary goal) | most features at parity; composite + final shader stages run; gbuffer + shadow pass still open |
| Vintage Fix 0.5.1 | Model dedup, blockstate compaction, dynamic loading | **Coexist then integrate** (memory opts) | Phase C2 — not started |
| Censored ASM / LoliASM 5.30 | BakedQuad/texture dedup, class loading | **Coexist then integrate** | Phase C2 — not started |
| Performant 1.11 | Entity/TE tick perf, pathfinding | **Coexist** (server-side, outside scope) | kept |
| Universal Tweaks 1.17.0 | Misc vanilla fixes | **Cherry-pick rendering tweaks only** | cherry-pick deferred |
| FPS Reducer 1.20 | Reduce FPS when AFK/unfocused | **Integrate** | shipped Phase C1 |
| Clear Water 1.2 | Water transparency | **Integrate** | shipped Phase C1 |
| Smooth Font 2.1.4 | TrueType font rendering | **Integrate** | shipped Phase C3 |
| Spark / Lag Goggles | Profilers | **Coexist** (diagnostic) | kept |

At full maturity: 5-7 mods consolidated. Today's gap from full maturity: Vintage Fix + Censored ASM (Phase C2 not started) + OptiFine shader-pack gbuffer/shadow stages (composite stages now run, gbuffer + shadow still ahead — see Phase 8 stretch + §13 backlog).

---

## 5. Phases — Status and Detail

Section per phase. Each lists `Status:` block at the top so you can skim "what's left." Sub-bullets carry forward the original detail/gotcha content from the consolidated source docs.

### Phase 0 — Foundation

Status: `[x]` complete. Tagged `v0.0.1-alpha`. Build system, scaffolding, `@Mod` entry, config/proxy/compat infra.

### Phase 1 — Rendering Optimizations + Mod Absorptions

Status: `[x]` complete. Tagged `v0.1.0-alpha`.

Implemented features:
- Entity render distance culling (configurable, default 64 blocks).
- Entity LOD (64-128 blocks: half framerate, 128+: quarter).
- Tile entity render distance culling (configurable, default 64 blocks).
- Particle frustum culling (dot product behind-camera check).
- FPS Reducer (replaces standalone mod): AFK + unfocused detection, mouse movement tracking, HUD indicator.
- Clear Water (replaces standalone mod): surface alpha, underwater fog, RGB color tinting.
- F3 debug overlay with [LDOG] stats section.
- Scrollable settings GUI accessible from Options + Video Settings screens.

Research catalogue (for future reference / Phase 1.5 backlog): see §6.

### Phase 2 — HD Texture Support

Status: `[x]` complete.
- Vanilla atlas already supports any sprite size up to GL max — research confirmed.
- `MixinTextureAtlasSprite` prevents crash on non-square textures.
- Tested with 256x resource pack (covers 32x/64x/128x).

### Phase 3 — Connected Textures (CTM)

Status: `[x]` complete.
- `CTMProperties`: OptiFine `.properties` parser (method, matchBlocks, tiles).
- `CTMLogic`: 47-tile full CTM + horizontal + vertical index calculation.
- `CTMBakedModel`: `BakedModelWrapper` with per-quad retexturing via UV remap.
- `CTMSprite`: custom `TextureAtlasSprite` that loads PNGs from mcpatcher/ctm paths.
- `CTMRegistry`: scans resource pack dirs/zips, registers tiles, wraps models at `ModelBakeEvent`.
- `CTMRenderContext`: ThreadLocal passing `IBlockAccess` + `BlockPos` from `renderBlock` to `getQuads`.
- `MixinBlockRendererDispatcher`: sets/clears `CTMRenderContext` around block rendering.
- Supports both mcpatcher/ctm and optifine/ctm paths, numeric block IDs.
- Glass pane CTM: synthetic quads for absent arms, UV mirror convention, mirrorH tile selection.
- Seam suppression: removes UP/DOWN edge strips between stacked panes for seamless glass.

### Phase 4 — Emissive Textures

Status: `[x]` complete.
- `EmissiveTextureRegistry` scans resource packs for `*_e.png` directly.
- Reads `optifine/emissive.properties` and `mcpatcher/emissive.properties` for suffix config.
- `EmissiveRenderHandler` creates fullbright retextured quads (UV remap to emissive sprite).
- `MixinBlockModelRenderer` intercepts `getQuads()` in both smooth and flat paths.
- Verified in-game: emissive ore overlays glow.
- Fullbright lightmap verified: `lightmap(240, 240)` in BLOCK format correctly bypasses AO/smooth lighting.
- `RenderItem` emissive layer: `MixinRenderItem` + `EmissiveItemRenderHandler` (ITEM format, global fullbright via `OpenGlHelper`, polygon offset).

### Phase 5 — Dynamic Lights + Lighting Customization

Status: `[x]` complete (one optional enhancement deferred).
- `DynamicLightManager`: tracks entities holding light-emitting items, distance-attenuated.
- `ItemLightRegistry`: items → light levels (block items auto-detect + hardcoded overrides).
- `MixinWorldDynamicLights`: injects into `ChunkCache.getCombinedLight()` (NOT `World` — World loads before mixins).
- `DynamicLightTickHandler`: per-tick entity scan + per-frame smooth mode.
- Entities on fire emit light level 15; dropped items emit their item's light level.
- Configurable update interval (Smooth/per-frame, Fast/per-tick, or N ticks).
- `MixinEntityRendererLightmap`: full lightmap customization via 16×16 texture manipulation.
- Separate block/sky light RGB tinting (warm torches + cool moonlight, no shaders).
- Night darkness multiplier (0.5× brighter → 100× pitch black) with torch protection.
- Brightness boost (-1.0 to +1.0).
- Pseudo-HDR tonemapping (ACES filmic curve).
- 13 named presets (neutral, warm_torches, cinematic, candlelight, moonlit, dark_nights, horror, bright_caves, vivid, fluorescent, purple_haze, neon_blue, red_alert).
- Full GUI: preset cycling + individual controls.
- Auto-enable when any individual option is changed.
- `[ ]` Per-light-source color (torch=warm, redstone=red) — possible future enhancement.

### Phase 6 — Resource Pack Features

Status: `[x]` complete (6a-6e all shipped; 6c has two optional gaps).

**6a — Better Grass / Better Snow**: `BetterGrassBakedModel`, `BetterGrassHandler`, `BetterSnowHandler` shipped with flat lighting + directional shading + Z-offset. Config: `betterGrass = off/fast/fancy` (default fancy).

**6b — Natural Textures**: `NaturalTextureBakedModel` (position-based UV rotation 0/90/180/270 + flip), `NaturalTextureHandler` parses `optifine/natural.properties`, defaults for 16 common blocks. Modes: rotate, rotate+flip, flip, fixed.

**6c — Custom Colors**: `CustomColorHandler` fires after vanilla colorizers; reads `optifine/colormap/grass.png` + `foliage.png`; parses `optifine/color.properties` for redstone wire colors + static overrides.
- `[x]` Per-biome water color overrides — `water.<biomeId>=0xRRGGBB`, honored at radius 1 and inside biome blend kernel (2026-05-21).
- `[x]` Potion color overrides — `potion.<name>=0xRRGGBB`, `MixinPotionColor` on `Potion.getLiquidColor` (2026-05-21).
- `[x]` Dye color overrides — `dye.<name>=0xRRGGBB`, `MixinDyeColor` on `EnumDyeColor.getColorValue` (2026-05-21).
- `[defer]` Map color overrides — would require patching `MapColor.COLORS` static array, too invasive for user-visible payoff.

**6d — Custom Sky**: `CustomSkyRenderer` parses `optifine/sky/world0/skyN.properties` (texture, HH:MM fade times, rotate, blend, speed, axis); `CustomSkyLayer` does time-based alpha fade with wrap-around, skybox cube rendering. Custom sun/moon already supported by vanilla resource pack system. **Mixin verified in-game 2026-04-18** after earlier issues fixed across `c944b4e` (descriptor + refmap regen), `8ca2f52` (SRG name), `1113a60` (removed inverted pass check, MCPatcher layout). The `ldog$skyMixinConfirmed` one-shot log in `MixinRenderGlobal.renderSky` HEAD stays as regression detector.

**6e — Random Entity Textures**: `RandomEntityTextureHandler` scans `optifine/random/entity/`; `MixinRender` intercepts `bindEntityTexture()` for living entities (targets `Render.class`, not abstract method); UUID-based deterministic selection with optional weighted `.properties`.

### Phase 7 — Anti-aliasing / Anisotropic Filtering

Status: `[x]` complete (one known MSAA-edges issue documented as won't-fix; FXAA via Phase 9a.8 is the better answer).

**7a — Anisotropic Filtering**: config `enableAnisotropicFiltering`, `anisotropicLevel` (2/4/8/16, clamped to GPU max). `AnisotropicFilteringHandler` applies `GL_TEXTURE_MAX_ANISOTROPY_EXT` at `TextureStitchEvent.Post`. GUI: toggle + level cycling, re-applies on save without full resource reload. Graceful fallback if extension missing. **Block-edge bleed at distance fixed by Phase 7c**.

**7b — MSAA**: config `enableMSAA`, `msaaSamples` (2/4/8, clamped to `GL_MAX_SAMPLES`). `MSAAFramebuffer` aux multisampled FBO (GL_RGBA8 + GL_DEPTH24_STENCIL8); `MixinEntityRendererMSAA` binds at `renderWorldPass` HEAD, blit-resolves to `mc.framebufferMc` at RETURN. Auto-resizes on window size change. Graceful fallback if GL 3.0 or EXT_framebuffer_multisample+blit missing.
- **Known issue**: faint rasterization edge lines at distant chunk/block-face seams. OF avoids this with display-level MSAA (`PixelFormat.withSamples` + disable fboEnable) but loses spectator outlines. Not fixing — FXAA (Phase 9a.8 pipeline FXAA) is the better answer.

**7c — Extended border mipmaps (AF bleed fix)**: config `enableExtendedBorderMipmaps` (default off — opt-in, grows atlas ~3× for 16x packs).
- `ExtendedBorderHandler` holds per-stitch state (`mipmapLevels`, active flag), generates padded mipmap chains via clamp-to-edge halo at each mip level.
- `MixinStitcherHolder` `@Redirect`s `getIconWidth/Height` inside `Holder.<init>` to inflate packing dims by `2 * border` (border = `2^mipmapLevels`). Sprite's own width/height stay untouched so external callers see inner size.
- `MixinStitcher` `@Redirect`s the `initSprite` call in `getStichSlots` to shift sprite origin inward by `border`, so UVs address only the inner region.
- `MixinTextureMap` brackets stitch with `beginStitch`/`endStitch`; `@Redirect`s `TextureUtil.uploadTextureMipmap` in `finishLoading` to write padded pixel data at `(innerOrigin - border)` with dims `(w + 2*border, h + 2*border)`. Uses `remap = false` on the enclosing `@Redirect` because `finishLoading` is Forge-added.
- GUI toggle in AA/Filtering. On save triggers `mc.refreshResources()` (packing change, not live-refresh).
- 50 unit tests pass.
- **Limitations**: animated sprite halo stays as first frame's edge color (v1 tradeoff); atlas growth may push modpacks past `GL_MAX_TEXTURE_SIZE`.
- **Tried and reverted 2026-04-16**: clamping `GL_TEXTURE_MAX_LOD` to `mipmapLevels - 2` on the block atlas when AF is on — didn't visibly reduce edge lines, added mild regression.

**7d — FXAA post-process**: config `enableFXAA`. `FXAAHandler` toggles `EntityRenderer.loadShader("shaders/post/fxaa.json")` on/off. `AccessorEntityRenderer` mixin for clearing shaderGroup+useShader on disable. First-tick reconciliation. GUI row.
- **Pipeline FXAA path (Phase 9a.8)**: When the post-process pipeline is on, `FXAAHandler` yields MC's fixed shader and `LDOGFXAAPass` runs instead — LDOG-original FXAA 3.11-inspired shader with 5 quality levels (`FXAAQuality` enum Low/Medium/High/Ultra/Extreme). Live-adjustable. When pipeline OFF, MC's shipped fxaa.json runs.

### Phase 8 — Shader Pipeline

Status: `[x]` 8a + 8b + 8c shipped 2026-04-17. `[x]` HDR + Bloom shipped 2026-05-21 (`74e468d`, `5a924c6`). `[x]` Shader-pack discovery scaffold shipped 2026-05-21 (`17f243a`). `[x]` **Composite + final stage runner shipped 2026-05-22** (`e056e67`). `[x]` **Gbuffer dispatch v1 + built-in packs + dimension/include real-pack loading shipped 2026-06-14** (see §2 top) — per-object `gbuffers_*` programs bind around terrain/clouds/entities/weather/hand draws (opt-in `enableShaderGbuffers`, single colour target), LDOG ships 4 built-in packs, and `worldN/` + `#include` resolution makes genuine 1.12.2 packs load. `[ ]` Gbuffer MRT (`DRAWBUFFERS` multi-target) + shadow pass still open — the last chunks for full deferred-pack OF parity.

**HDR pipeline (2026-05-21)**:
- `RenderTargetManager` HDR mode: when `enableHDRPipeline` is on, scene + ping color textures allocate as `GL_RGBA16F` instead of RGBA8.
- `HDRTonemapPass` runs first in pipeline — four operators (ACES filmic, Reinhard, Uncharted 2/Hable, linear) + user-tunable exposure multiplier.
- Tonemap → LDR-clamped values stored in HDR storage so downstream passes (upscaler, RCAS, FXAA, vignette) don't need HDR awareness.
- `BloomPass` runs even earlier (before tonemap) so bright-pass shader sees HDR luminance >1.0. Three stages: bright-pass quadratic weight, separable 9-tap Gaussian blur (half-res ping-pong), additive composite.

**Shader-pack discovery scaffold (2026-05-21)**:
- `ShaderPack` abstract + Directory/Zip subclasses (auto-detects nested zip layouts).
- `ShaderProgramId` catalogues OF/Iris standard `.vsh`/`.fsh` filenames.
- `ShaderPackManager` discovers `<.minecraft>/shaderpacks/`, exposes name list, activates by config.
- GUI: dedicated Shaders tab with scrollable list, Open Folder + Rescan + Done buttons (2026-05-22 — replaced the original cycle button).

**Composite + final stage runner (2026-05-22)**:
- `ShaderPackUniforms` — per-frame snapshot + push of the standard OF/Iris uniform set. Naming follows the public OF convention (`cameraPosition`, `sunPosition`, `gbufferModelView`, `frameCounter`, `frameTimeCounter`, `rainStrength`, `worldTime`, `isEyeInWater`, etc.) so unmodified pack shaders link.
- `ShaderPackRuntime` — owns compiled GL programs for the active pack. Compiles `composite.{vsh,fsh}` → `composite15.{vsh,fsh}` + `final.{vsh,fsh}` on activate, disposes on deactivate. Per-stage compile failures drop that stage individually (logged at WARN) so a half-broken pack still renders the working stages.
- `ShaderPackCompositePass` — pipeline pass, runs after FXAA before vignette. Each frame: copy main FB → `colortex0` source texture, then walk composites with two main-res ping-pong FBOs, feeding the standard uniform set + binding `colortex0`/`depthtex0` (and `colortex1..7` / `depthtex1..2` to a 1×1 black tex for safe-zero). Final stage (if present) renders to the actual main FB; otherwise the last composite output gets blitted back.
- **Still NOT in v1**: gbuffer programs (per-object draw shaders), shadow pass, MRT `DRAWBUFFERS` directive, custom buffer formats from `shaders.properties`. Most pack visual identity DOES live in composite + final — typical packs partially work today.

**8a — Framework**: `PostProcessPass`, `PostProcessContext`, `PostProcessPipeline`, `passes/NoOpPass`. Mixin lifecycle hook on `EntityRenderer.renderWorldPass` (RETURN). `RenderTargetManager` owns scaled GL_RGBA8 color tex + GL_DEPTH24_STENCIL8 depth RBO scene target + color-only ping-pong. `ensure(baseW, baseH, scale)` reallocates on dim/scale change.

**8b — Observability**: `PipelineDebugStats` (active passes, frame nanos, target ready/scale/dims); perf overlay row; first-enable log reports base+scaled dims, target readiness, and MSAA/FXAA posture; fault-tolerant pass removal on execute failure; `disableAll` on fatal init/resize error disposes `RenderTargetManager`.

**8c — Binding hook**: `MixinEntityRendererPostPipelineBind` (single wrapper, replaces the older RETURN-only injector). At HEAD: if pipeline on + no MSAA conflict + `RenderTargetManager.isReady()`, save current FBO + viewport, bind scene FBO, set scaled viewport, clear color+depth, set `ldog$pipelineActive=true`. At RETURN: run pass chain against scene target, `glBlitFramebuffer` back to saved FBO at saved viewport (`GL_LINEAR`), restore viewport. MSAA path: pipeline does not bind (MSAA owns FBO); pass chain still runs on main FB after resolve. FXAA path: unchanged (composites after).

Critical: `pass != 2` guard — `renderWorldPass(pass)` with `pass != 2` is anaglyph-only. Default MC always uses `pass == 2`. Originally guarded on `pass == 0`, which broke binding. Fixed.

`ShaderProgram` utility (compile/link/uniform setters + cached locations) shipped with 8a, used by all 9a+ shader passes. `setUniformMatrix4` added for 9c.2.

### Phase 9 — Upscaling + Post-Process Chain

Status: 9a + 9c.1-9c.3a `[x]`; 9b `[ ]` doc-only protocol; 9c.3-Option-C, 9c.4, 9c.5 `[defer]`.

**Concept**: AMD FidelityFX Super Resolution 1.0 (spatial). Render world to scaled FBO, apply FSR sharpen/upscale pass to output at native. Works on any GPU. FSR 1.0 over DLSS because: DLSS requires NVIDIA SDK (native/JNI), DX12/Vulkan, motion vectors, depth — fundamentally incompatible with MC 1.12.2 OpenGL 2.1.

#### 9a — Spatial upscalers + post-process chain (all `[x]` 2026-04-17)

- **9a.1** `BilinearBlitPass` extracted from mixin into its own pass. Pipeline registers passes by algorithm with `isEnabled()` gating.
- **9a.2** `FSR1EASUPass` — LDOG-original unsharp-mask-on-bilinear with contrast-adaptive strength. GLSL 120. **Bug fixed**: initial anti-ringing 5-tap clamp trapped edge peaks back to input; widened then dropped entirely.
- **9a.3** FSR1 sharpness slider 0.0-2.0, live-tunable.
- **9a.4** `FSR1QualityPass` — direction-biased EASU variant. Sobel edge detection + anisotropic sampling along edge + contrast-adaptive sharpen. Noticeably crisper on diagonals.
- **9a.5** `UpscalerPreset` enum (Native/Ultra/Quality/Balanced/Performance/Custom). Auto-flips to Custom when individual controls edited.
- **9a.6** `RCASSharpenPass` — post-upscale sharpen via `glCopyTexSubImage2D`. Works at any scale including 1.0 (pure sharpen for native-res users). Config + slider.
- **9a.7** `LDOGPreset` enum — whole-mod presets (Vanilla/Performance/Default/Fancy/Ultra/Custom). At top of settings list. `LDOGPreset.apply()` sets AA/FXAA/ExtBorder/water change flags so `saveAndClose` triggers right reloads.
- **9a.8** `LDOGFXAAPass` — LDOG-original FXAA 3.11-inspired, 5 quality levels (see 7d).
- **9a.9** `AutoScaleHandler` — target-FPS dynamic resolution scaling. Every 2s (40 client ticks), compares `Minecraft.getDebugFPS()` against `min(Display.getDesktopDisplayMode().getFrequency(), gameSettings.limitFramerate)`, steps `internalRenderScale` through 5-tier ladder {1.00, 0.85, 0.75, 0.67, 0.50} with 0.9×/1.1× threshold dead-zone. Extended (Aggressive mode): 7-tier ladder that also drives `upscalerAlgorithm` + `fxaaQuality` + `enableFXAA` (FSR1-Quality → FSR1 → Bilinear, FXAA Ultra→High→Med→Low→off). Config: `autoScaleMode` (Off/Normal/Aggressive). One-shot INFO log on first tick + per-decision DEBUG.

#### 9b — Quality tuning + validation (`[ ]` doc-only, user-driven)

Artifact: write `docs/PHASE_9B_VALIDATION.md` with target packs (vanilla 16x, 32x HD, Stratum 256x), reference scenes (exact biome coords / screenshot recipes), scoring rubric per pack × upscaler × scale, artifact catalog per upscaler. Defer until user signals investment in quality testing. No code change.

#### 9c — Temporal upscaling

**Foundation matters**: temporal upscalers need source color + depth + motion vectors + jittered projection + history buffer + (optionally) exposure + reactive mask. MC 1.12.2 provides source + depth + camera pose + per-entity prev positions natively. Doesn't provide: per-entity MV emission (no hook), particle MV (batched draw), TESR prev transforms (custom code), chunk-local motion (texture animation — accept artifact).

Reference mods can't be code-mirrored: Super Resolution targets GL 4.3 compute + Vulkan (1.18+); Radiance bypasses MC's renderer entirely via Vulkan + C++. LDOG is writing **the first temporal reconstructor for MC 1.12.2's legacy immediate-mode pipeline** from first principles.

Stages (each independently shippable):

- **9c.1** `[x]` Jittered-projection TAA MVP — `JitterHelper` (Halton 2,3) + `TAAAccumulatePass` with neighborhood-clamped history blend. **Bug 1**: jitter injection targeted `setupCameraTransform` but `renderWorldPass` overwrites projection afterward (sky + terrain `gluPerspective` calls) — jitter was a no-op. Fix: inject at `renderWorldPass` on both ordinals. User-verified post-fix.
- **9c.2** `[x]` Camera motion vectors — `CameraState` singleton captures jittered viewProj + invCurViewProj + prevViewProj at the terrain-projection injection point (AFTER jitter). Scene depth attachment moved from RBO to `GL_DEPTH24_STENCIL8` texture (GL_NEAREST) for shader sampling. TAA shader reconstructs world-space from NDC + depth + invCurViewProj, reprojects via prevViewProj. Disocclusion check: reprojected UV outside [0,1] → skip history. **Bug 2**: initial impl captured un-jittered matrices while history stored jittered pixels — "drunk/swimming" visuals. Fix: capture AFTER `applyJitter()` so cur/prev matrices match history. User-verified post-fix.
- **9c.3-A** `[x]` Entity reactive mask (Option A from §10) — MRT + per-attachment `colorMaski`: sceneFbo gets a COLOR1 R8 attachment always-allocated; binding mixin `glDrawBuffers` to [COLOR0, COLOR1] and `glColorMaski(1, false)` around non-entity draws; `MixinRenderGlobal` opens `colorMaski(1, true)` around `renderEntities` HEAD/RETURN. Legacy fixed-function replicates `gl_FragColor` across attachments so no custom entity shader needed. TAA shader drops history weight on flagged pixels. Kills moving-mob ghost trails. User-verified.
- **9c.3-C** `[x]` Per-entity motion vectors via BBox-projected velocity stamps shipped 2026-05-22 (`9264bb2`). Strategy 4 from §10 — captures every dispatched entity through one `MixinRenderManagerEntityMV` hook (covers mobs/items/XP orbs/projectiles/paintings/modded), projects bbox + cur/prev positions through `CameraState`, stamps screen-space velocity into the half-res RG16F MV target. TAA + FSR 2 sample the MV target with priority over camera-only reprojection; reactive-mask drop bypassed where entity MV is present so entity pixels retain accumulated detail. Approximate (bbox-granularity not per-pixel, ~5% the cost of re-rendering entity geometry) — covers ~95% of the perceptual win.
- **9c.4** `[x]` FSR2-style temporal reconstruction shipped 2026-05-22 (`1e9cdde`). LDOG-original `FSR2ReconstructionPass` combines the full 9c stack into a single scaled→native pass: 5×5 Lanczos-3 source kernel + camera/entity MV reprojection + 3×3 neighborhood color clamping + reactive mask weighting + light final-stage sharpen. Selectable as the `FSR2` upscaler algorithm; the standalone TAA pass short-circuits when FSR 2 is active (FSR 2 owns history accumulation). Algorithm informed by AMD's public FSR2 specification but no code copied per project policy.
- **9c.5** `[defer]` Reactive mask polish + alpha-cutout classification.

### Phase 10 — Borderless Windowed Fullscreen

Status: `[x]` 10a (restart-required) shipped 2026-04-17. `[defer]` 10b (runtime-togglable) — see backlog.

**10a — Restart-required mode**:
- Core plugin reads LDOG config file directly (before ConfigManager initializes), sets `org.lwjgl.opengl.Window.undecorated=true` if flag is on; MC's Display is created undecorated for the session.
- `MixinMinecraftBorderless` (must be in `mixins.ldog.vanilla.json` early config) replaces exclusive fullscreen with resize-to-desktop + position (0,0).
- **Flicker fix**: `Display.setDisplayMode` must use `new DisplayMode(w, h)` — passing full `getDesktopDisplayMode()` carries refresh/bpp metadata that triggers fullscreen mode-switch intent in LWJGL 2.9.4 on Windows. Reordered: setResizable → setLocation → setDisplayMode.
- **Windows Fullscreen Optimizations dodge + user toggle**: window sized `desktop_h - 1` by default so Win10/11 DWM doesn't auto-transition into optimized-borderless-fullscreen. User toggle "Block FS Optim" (default ON). Trade-off: ON = flicker-free but taskbar visible; OFF = clean taskbar-hidden but brief transition flash.
- **Startup sizing fix**: `Minecraft.startGame` fullscreen-at-startup path goes through `toggleFullscreen()` (lines 601-604), not `setInitialDisplayMode`. Vanilla's toggleFullscreen calls `this.resize(displayWidth, displayHeight)` which invokes `currentScreen.onResize`; our handler initially only called `updateFramebufferSize` directly. Fixed: use `mc.resize(w, h)` in both enter/exit paths. Side benefit: also fixes F11 toggles while a settings screen is open.
- **Trade-off**: undecorated is session-level; windowed mode loses title bar/resize grips. Tooltip documents Alt+drag (Windows) or keyboard window movement.

**10b — Runtime-togglable** (deferred): `Display.destroy()` + `Display.create()` + coordinated LDOG GL subsystem cleanup. **Why hard**: destroying the Display invalidates the entire GL context — every LDOG-owned GL handle dies (RenderTargetManager FBOs, all shader programs, TTF font atlas, MSAAFramebuffer). MC's `refreshResources()` reloads MC atlases but NOT mod GL state. Sketch:
1. Dispose all LDOG GL state.
2. `Display.destroy()`.
3. Update `System.setProperty("org.lwjgl.opengl.Window.undecorated", ...)`.
4. `Display.setDisplayMode(...)`.
5. `Display.create(new PixelFormat().withDepthBits(24))`.
6. `mc.refreshResources()`.
7. LDOG subsystems lazy-reinit on next use.

Most subsystems already have `dispose()` — main risk is ordering, want a central `LDOGRenderingLifecycle` event bus. Priority: low unless multiple users request.

Architectural reference: `hancin/Fullscreen-Windowed-Minecraft` on GitHub — concept-only, no code copying.

### Phase C1 — Mod Absorptions (during Phase 1)

Status: `[x]` complete.
- FPS Reducer → `FpsReducerHandler` (AFK + unfocused + mouse tracking + HUD overlay).
- Clear Water → `MixinBlockFluidRenderer` + `ClearWaterHandler` (alpha + fog + RGB tint).

### Phase C2 — Memory Optimization Absorption

Status: `[ ]` not started (deferred until later phases stable).

Plan:
- **Vintage Fix → LDOG**: model dedup (post-bake walk + content-hash), blockstate compaction (array-backed `BlockStateContainer`), dynamic model loading (lazy + LRU evict), property value interning. Risk: moderate (touches fundamental data structures). Test with 200-mod pack.
- **Censored ASM / LoliASM → LDOG**: BakedQuad vertex-int-array dedup, sprite pixel dedup post-stitch, `LaunchClassLoader.findClass()` opts, IBlockState canonical cache, NBT tag-name string interning. Verify non-overlap with VintageFix-equivalents. Risk: moderate-high (class loading sensitive).

### Phase C3 — Smooth Font Absorption

Status: `[x]` complete 2026-04-17 (one cache enhancement still open).

Pain point: original Smooth Font roughly doubles launch time because it synchronously rasterizes all glyph pages × sizes at startup. LDOG implementation must not repeat that.

Shipped:
- **HD ASCII PNG swap path**: `SmoothFontHandler` scans active pack in priority order `optifine/font/ascii.png → mcpatcher/font/ascii.png`; registers `HDFontTexture` (SimpleTexture subclass) under stable `ldog:textures/font/hd_ascii`; applies `GL_LINEAR` filtering at upload. `MixinFontRenderer.@Redirect` on the `bindTexture(locationFontTexture)` call inside `renderDefaultChar` swaps to HD when available. `FontRendererInvoker` exposes protected `bindTexture` (both use `remap = false` — Forge-added method, no SRG mapping).
- **Width overrides**: parses `ascii.properties` (`width.N=W` format) in priority order `optifine/font/ → mcpatcher/font/ → font/`. `MixinFontRenderer.@Inject(TAIL)` on `readFontTexture` applies overrides on top of vanilla's auto-computed widths. Alto pack ships widths at `font/ascii.properties`.
- **Config**: `enableSmoothFont` (master), `useHDFontTexture`, `fontAntialiasing` (off/bilinear/trilinear), `useFontPropertyWidths`, `fontLodBias`, `fontAnisotropic`, `useTTFFont`, `ttfFontFamily`, `ttfBold`, `ttfItalic`, `ttfFontSize`, `ttfCellSize`, `fontDropShadows`. Live flips: AA off↔bilinear, drop shadows. Trilinear boundary + TTF source change + HD probe trigger `mc.refreshResources()`.
- **GUI**: "Font Rendering" section — 9 rows with per-button hover tooltips. Custom TTF families highlighted yellow vs built-in green.
- **OptiFine conflict check**: `OptiFineCompat.shouldHandleSmoothFont()` auto-disables all four features when OF detected.
- **TTF path**: `TTFFontRasterizer` uses `Graphics2D` with `TEXT_ANTIALIAS_ON` + `FRACTIONALMETRICS_ON` to rasterize 256-char default-font page from `Font` into 16×16 grid at configurable cell size. `TTFFontTexture` uploads via shared `FontTextureUploader`. Width comes from AWT `FontMetrics.charWidth(ch)` scaled to MC's logical 8-per-cell and written into `FontRenderer.charWidth[]` via `@Accessor` (not `@Inject(TAIL)` — our listener runs after FontRenderer's, TAIL would always read previous reload's table). Built-in families: SansSerif/Serif/Monospaced/Arial/Verdana/Tahoma/Segoe UI/Helvetica/Consolas/Courier New. ASCII page eager-rasterized at reload (~100ms for 256 glyphs).
- **Three-level AA**: `FontAAMode` enum — `off` (GL_NEAREST) / `bilinear` (GL_LINEAR no mipmaps) / `trilinear` (GL_LINEAR_MIPMAP_LINEAR + `glGenerateMipmap` + `GL_TEXTURE_MAX_LEVEL = log2(size)`). Trilinear is the only mode that actually antialiases at GUI scales — bilinear is ≳16:1 downsampling from 4096 atlas. Trilinear applies negative `fontLodBias` (default -0.5, tunable -4..4) + anisotropic sampling (default 16x, clamped to GPU max).
- **User-supplied fonts**: `TTFFontCatalog` scans `config/ldog/fonts/` for `.ttf`/`.otf` at preInit, loads via `Font.createFont(TRUETYPE_FONT, file)`, registers with `GraphicsEnvironment`. Rescanned on every resource reload so F3+T picks up new drops without restart. Already-registered files skipped. AWT registration failures (name collisions with installed system fonts) logged as warnings, not crashes.
- **Drop-shadow toggle**: `fontDropShadows` config + `@ModifyVariable` on `FontRenderer.drawString(String,F,F,I,Z)I`'s boolean arg at HEAD. All rendering funnels through this overload.
- **Subclass pass-through**: `MixinFontRenderer`'s redirect checks `self.getClass() != FontRenderer.class` and bypasses HD swap for subclasses. Fixed a Forge `SplashProgress$SplashFontRenderer` crash (separate GL context/thread).
- **Core-plugin early loader**: `LDOGCorePlugin` implements `IFMLLoadingPlugin` + MixinBooter's `IEarlyMixinLoader`, registers `mixins.ldog.vanilla.json`. FontRenderer is pulled into the classloader during FML bootstrap ahead of the late-loader window, so late mixins silently no-op with "loaded too early". `coreModClass` in `buildscript.properties` propagates to dev JVM args and jar manifest.
- **v1 deliberately skipped**: Unicode glyph pages (`glyph_XX.png`) untouched by the hook — ASCII HD swap is the user-visible win.

Future enhancements (not blocking):
- `[ ]` Persistent disk cache at `config/ldog/font-cache/<font-hash>/<size>.png`.
- `[ ]` Async rasterization on worker thread.
- `[ ]` Unicode `glyph_XX.png` runtime rasterization with lazy+cached per-page atlases.
- `[ ]` Bold/italic GUI toggles.
- `[ ]` Subpixel rendering hint.

### Phase C4 — OptiFine Override Mode

Status: `[x]` foundation shipped (`3b083bc`); `[ ]` in-prod verification + field-name corrections + parity benchmarking + tooltip polish all pending.

Today's compat: when OF is detected, LDOG auto-disables overlapping features. The override-mode goal: once an LDOG feature is demonstrably ≥ OptiFine's (faster, lower memory, better-looking, more configurable), flip it: LDOG takes over, OF's feature disabled.

Foundation shipped:
- `OFOverrideMode` enum: AUTO / LDOG_OVERRIDE / OPTIFINE_OVERRIDE. One `ofModeXxx` config string per feature.
- `OFFeature` catalog (7 features: CTM, emissive, sky, dynamic lights, random mobs, smooth font, custom colors).
- `OFConfigBridge` lazy reflective probe + setter on the **GameSettings instance** (key discovery: OF doesn't use a static Config class — OF's transformer adds `ofXxx` instance fields to vanilla `GameSettings`. The Config class at JAR root holds platform state only — openGlVersion, initGameSettings). Bridge walks class hierarchy, tries candidate field names per `OFFeature`.
- All features default to AUTO — zero behavior change until user opts in.
- GUI section "OptiFine Interop" rendered only when OF detected. Color-coded labels (grey/green/yellow with red for "uncontrollable").
- Graceful failure: bridge returns false on every error path; `OptiFineCompat.computeDecision` falls back to OPTIFINE_OVERRIDE with logged warning when LDOG_OVERRIDE can't be honored.
- Legacy `shouldHandle*` methods preserved as wrappers — all existing callers unchanged.

**CRITICAL gotcha — OF cannot run in `gradlew runClient`**: dropping the official OF production jar (`OptiFine_1.12.2_HD_U_G5.jar`) into `run/mods/` and launching via gradle crashes immediately at `FMLClientHandler.detectOptifine` with `NoClassDefFoundError: cer`. Root cause: OF's jar is obfuscated against production-MC (notch) class names; the dev workspace runs deobfuscated MC. OF's class transformer references obfuscated names like `cer` that don't exist in dev.

In-prod verification path:
1. `./gradlew build` → `build/libs/LimitlessDevelopmentOptigame-vX.X.X.jar`.
2. Copy to a real production MC 1.12.2 + Forge install's `mods/` (e.g., alto modpack instance).
3. Ensure OF is also in that production mods folder.
4. Launch via the normal Minecraft launcher (NOT gradle).
5. Look at `<.minecraft>/logs/latest.log` for `LDOG: OF interop bridge ready — N feature(s) controllable, M unmapped (...)`. The unmapped list tells us which `OFFeature.candidateFieldNames` need adjusting.
6. Toggle OF interop GUI rows; confirm OF features actually go off when set to LDOG.

**Don't put OF in `run/mods/` again.** Keep the jar outside the project tree (e.g., `Downloads/`) if needed for inspection.

User tried verification in a 100+ mod pack and hit compat errors unrelated to LDOG — deferred to cleaner test env.

Remaining work:
- `[ ]` In-prod verification with real OF.
- `[ ]` Field-name corrections for features the bridge logs as unmapped.
- `[ ]` Per-feature parity benchmarking before flipping any default to LDOG_OVERRIDE.
- `[ ]` Tooltip polish for the 7 OF Interop GUI rows.

---

## 6. Phase 1 Research — Catalogue (for future Phase 1.5)

The shipped Phase 1 covered B1/B2/B3 entity/TE distance + LOD, C1 particle frustum cull, and C1/D2 mod absorptions. The wider opportunity catalogue from the original research doc — items not yet implemented — sits here for reference. Ranked by impact × difficulty × conflict risk (lower priority = farther into future).

| # | Item | Target | Impact | Diff | Conflict |
|---|---|---|---|---|---|
| 1 | A2: Skip air blocks in chunk rebuild | `RenderChunk.rebuildChunk()` iteration loop; check `ExtendedBlockStorage.isEmpty()` | Mod | Low | Low |
| 2 | A3: Frustum reuse + tighter culling | `RenderGlobal.setupTerrain()`, `EntityRenderer.renderWorldPass()` | Mod | Low-Med | Low |
| 3 | A1: AO/Lighting cache | `BlockModelRenderer.AmbientOcclusionFace.updateVertexBrightness()` — 18×18×18 cache + `@Redirect` neighbor lookups | **Very High** | Mod | Mod (OF) |
| 4 | A4: Chunk rebuild prioritization (distance/frustum-aware queue) | `ChunkRenderDispatcher` | Mod | Mod | Low |
| 5 | C2: Particle count limiting | `ParticleManager` | Low-Med | Low | Low |
| 6 | A5: Batch chunk uploads | `ChunkRenderDispatcher.runChunkUploads()` | Low-Med | Mod | Low |
| 7 | D1: Reduce per-frame allocations (pooled Vec3d/AABB/BlockPos) | various hot paths | Mod | Mod | Low |
| 8 | E1: Cache vertex format GL state | `RenderGlobal.renderBlockLayer()` | Low | Very Low | Low |

A1 (AO/Lighting cache) is the single biggest unshipped optimization but moderate conflict risk with OF's own AO — gate via `OptiFineCompat`.

---

## 7. Phase 8/9 Deeper Plan (compressed)

Original deep-dive identified what to do (spatial FSR1 first, temporal as research), what not to do (DLSS — incompatible with MC 1.12.2 OpenGL 2.1; no platform shift in scope), and three research tracks (R1 NIS-style, R2 FSR2 feasibility, R3 XeSS viability memo) with explicit go/no-go gates.

**What's been validated by shipping**: Phase 8 framework + binding is stable; FSR1 + FSR1-Quality + Bilinear + RCAS are all shipped (R1's NIS-style track effectively superseded by FSR1-Quality which is direction-biased EASU — comparable quality without separate NIS implementation); 9c.1 + 9c.2 prove camera-MV temporal is feasible on the legacy pipeline (R2 partially answered — temporal works at camera scope).

**Still open from original research plan**:
- `[ ]` R3 XeSS viability memo — produce constraint matrix + classification (`researchable` vs `platform-shift`). Almost certainly classifies as platform-shift given runtime requirements.
- `[ ]` 9b validation protocol doc (§5).
- `[defer]` R2 full FSR2 feasibility — Option C entity MV is the gating prereq (see §10).

**Legal/compliance discipline** (carry forward): external mods (Super Resolution, Radiance) are concept-only references. No code copying. Document conceptual borrowings in design notes. Vendor SDK paths (DLSS-class) have higher legal/redistribution constraints and are separate go/no-go gates.

---

## 8. Post-9a.4 Backlog (compressed)

After 9a.4 (FSR1-Quality) shipped, options the user could pursue:

1. `[x]` **Quality Presets** — `UpscalerPreset` enum bundles scale + upscaler + sharpness — shipped as 9a.5.
2. `[x]` **RCAS standalone sharpen** — shipped as 9a.6 via `glCopyTexSubImage2D`.
3. `[ ]` **Phase 9b validation protocol doc** — see §5.
4. `[defer]` **Phase 10b runtime-togglable borderless** — see §5 (Phase 10).
5. `[defer]` **Phase 9c temporal upscaling** — see §5 (Phase 9c) and §9.

---

## 9. Phase 9c Temporal Upscaling — Deep Dive (compressed)

What temporal upscalers need (algorithm-level): source color, depth, motion vectors, jittered projection, history buffer, optional exposure, reactive mask.

What MC 1.12.2 provides natively: source color, depth, jittered projection (via mixin redirect on `Project.gluPerspective`), history buffer (just another FBO), camera pose (`ActiveRenderInfo` + projection matrix), per-entity `prevPosX/Y/Z` + `lastTickPosX/Y/Z` (read-only).

What MC 1.12.2 does NOT provide: per-entity MV emission hook (hard); particle MV (batched draw — hard); TESR prev transforms (custom code — hard); chunk-local texture animation motion (accept artifact).

Chunks aren't a blocker — they're world-space static, so depth-based camera MV is sufficient. Entities are the hard problem. Particles are even worse; accept particle ghosting behind reactive mask.

Staged plan (independently shippable):

| Stage | Scope | Est | Status |
|---|---|---|---|
| 9c.1 | Jittered TAA MVP (jitter + history + simple accum) | 3-5d | `[x]` |
| 9c.2 | Camera MV (depth-based, neighborhood clamping) | 1-2w | `[x]` |
| 9c.3-A | Entity reactive mask (drop history on entity pixels) | 1-2d | `[x]` |
| 9c.3-C | Full per-entity MV — see §10 | ~1w focused | `[defer]` |
| 9c.4 | FSR2-style reconstruction kernel (needs 9c.3-C) | 2-4w | `[defer]` |
| 9c.5 | Reactive-mask polish + alpha-cutout classification | 1-2w | `[defer]` |

**Worst case**: ~4 months focused for the full stack. **Minimum viable** already shipped (9c.1+9c.2+9c.3-A).

**When to pivot to "platform shift"**: only if users request DLSS-level quality AND project commits to a modern Minecraft fork. That's Radiance's territory — Vulkan + native renderer rewrite. Document as research archive, not roadmap.

---

## 10. Phase 9c.3 Option C — Full Per-Entity MV (concrete plan)

Companion to §9, written after Option A (reactive mask) shipped 2026-04-18.

### What Option A left on the table

| Problem | Severity |
|---|---|
| Entity TAA quality — reactive pixels get per-frame instability instead of accumulated detail. Entities look jaggy/shimmery in motion. | Medium |
| Particles still ghost — they aren't drawn from `RenderGlobal.renderEntities`. | Low |
| Mask is binary — slow-moving entities treated same as fast. Sub-pixel motion unnecessarily nuked. | Medium |
| Entity-on-entity occlusion via neighborhood clamping bleed | Low |
| TESR sub-pixel detail loss (banners, beacons, end portal) | Low-Medium |

Per-entity MV fixes all in one shot.

### Strategy comparison

- **Strategy 1** (two-pass with custom velocity shader) — clean separation, 2× entity draw cost worst case. Workable.
- **Strategy 2** (MRT during entity render, single-pass) — **don't**. Catastrophically invasive (replaces fixed-function entity rendering), breaks shader-mod compat.
- **Strategy 3** (optical flow approximation) — **don't**. Notoriously fragile, ~30ms per frame for a 7×7 search.
- **Strategy 4** (RECOMMENDED) — Strategy 1 for vanilla entities + reactive mask for modded/TESR fallback. Vanilla coverage is bulk of perceived value; modded compat surface stays at "reactive mask handles it." Lands working software in 1-2 weeks instead of weeks-to-months.

### Improvements over the naive plan

- **4.1 Per-entity displacement caching** with `prevPosX/Y/Z` interpolated via `partialTicks`. Cache in `WeakHashMap<UUID, EntityRenderState>`, evict on death/unload.
- **4.2 Velocity threshold** — skip MV emission for stationary entities (|prevPos - curPos| < ~0.001 blocks). ~80% of rendered entities in survival are stationary at any frame.
- **4.3 Lower-resolution MV target** — half-res by default; TAA bilinear-samples. Downside: thin entities (arrows, fishing lines) may miss pixels. Mitigation: per-entity full-res flag for known-thin classes.
- **4.4 MV format**: RG16F recommended (precision avoids visible quantization), fallback RG8 if `GL_R16F` missing.
- **4.5 Async MV pass** — render MV during frame N, TAA samples in frame N+1. One frame of transient incorrect MV at motion start; steady-state correct. Acceptable.
- **4.6 Combine with 9c.4** — MV is foundational for proper FSR2-style reconstruction. The investment pays off twice (ghost-free TAA + actual upscale quality). **If 9c.4 is ever a real roadmap goal, Option C is required, not optional.**

### Day-by-day plan (1 week focused / 2 weeks interrupted; 4-6d if already familiar with 9c.1+9c.2 code)

**Day 1 — Infrastructure (4-6h)**
- Create `MotionVectorTarget.java` — separate FBO, RG16F texture, half-res default.
- Allocate via `RenderTargetManager` lifecycle.
- Create `EntityVelocityShader.java` — vertex shader passes `prevClipPos`, fragment writes screen-space delta to RG.
- Create `EntityRenderStateCache` — `WeakHashMap<UUID, EntityRenderState>` with cur/prev transform.

**Day 2 — Hook entity rendering (4-6h)**
- `MixinRenderLivingBase` + `MixinRenderEntity` + `MixinRenderItem`: `@Inject` HEAD on `doRender` to capture pre-render GL_MODELVIEW.
- `@Inject` RETURN to capture post-render GL_MODELVIEW (or compute current matrix from entity position + camera state).
- Update `EntityRenderStateCache` with cur/prev pair.
- Schedule MV emission in per-frame queue.

**Day 3 — MV emission pass (4-6h)**
- `EntityMotionVectorPass` runs after scene render, before TAA.
- Bind `MotionVectorTarget` FBO.
- For each queued entity, bind velocity shader with cur/prev modelview uniforms, re-render geometry (investigate `RenderManager.renderEntityStatic` or equivalent for the "no lighting/texture" path).
- Verify only velocity is written.

**Day 4 — TAA integration + validation (4-6h)**
- `TAAAccumulatePass` shader: bind MV target on unit 4. Uniforms `u_motionVectors` + `u_useEntityMV`.
- Logic: if MV.r != 0 || MV.g != 0 → `histUV = v_texCoord - texture(motionVectors, v_texCoord).rg`. Else fall through to camera-only MV path.
- Remove reactive-mask drop-history for pixels with entity MV; keep reactive mask as fallback for non-MV entities.
- Validation matrix:
  - Static camera + moving sheep → entity TAA accumulates, no ghost.
  - Camera pan + static sheep → still works (camera MV).
  - Both moving → entity MV combines correctly.
  - Modded entity → reactive mask fallback fires.
  - TESR (banner, beacon) → reactive mask fallback fires.

**Days 5-7 — Polish + perf**
- Per-entity velocity threshold (skip stationary).
- Frustum culling on MV pass.
- Try lower-res MV target; verify quality.
- Profile: confirm < 2ms added per frame on test scene.
- Document modded entity compat list.

### Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `RenderManager.renderEntityStatic` doesn't exist/has wrong signature in 1.12.2 | Med | Investigate Day 1; fall back to `Render.doRender` with manual transform |
| Per-entity uniform updates throttle GPU | Low-Med | Batch by render type; cache uniform locations; consider instanced-array for many same-type |
| Half-res MV breaks thin entities | Med | Per-entity full-res flag, or reactive-mask fallback |
| Vanilla render path uses GL state we don't set in second pass (lighting, glow) | Med | Match vanilla setup; explicitly disable color/texture/lighting in velocity pass |
| OptiFine coexistence | Low | Detect OF, skip Option C entirely (reactive mask still works) |

### When to NOT pursue Option C

Stop at Option A if: user accepts reactive-mask quality; project priority shifts; 9c.4 (FSR2-style reconstruction) dropped from roadmap entirely. Option A is the 80/20 win for ghosting; Option C is the polish step.

---

## 11. Backlog — "More options" Tier A/B/C

Captured 2026-04-18. Sorted by effort × user-visibility.

**Tier A — high user value, clean hooks** (all shipped 2026-05-21 except world time):
- `[x]` Advanced item tooltips toggle — `AdvancedTooltipHandler` ticks the `gameSettings.advancedItemTooltips` flag, restoring on disable.
- `[x]` Disable nausea distortion — `MixinEntityRendererNausea` `@ModifyVariable` zeroes the interpolated portal-time local in `renderWorldPass`. Gameplay portal counter untouched.
- `[defer]` World time speed multiplier — risky (many client systems read `getWorldTime`), deferred.
- `[x]` Ping HUD (multiplayer) — `mc.getConnection().getPlayerInfo(...).getResponseTime()`, color-coded by latency.
- `[x]` Day counter HUD — `world.getWorldTime() / 24000`.
- `[x]` CPS counter — `MouseEvent` press-edge tracking, 1-second sliding window for left + right.

**Tier B — HUD element hides** (all shipped 2026-05-21 via `RenderGameOverlayEvent.Pre` cancel, no mixins needed):
- `[x]` Hide armor bar.
- `[x]` Hide hunger bar.
- `[x]` Hide air bar.
- `[x]` Hide boss health bars (BOSSHEALTH + BOSSINFO).

**Tier C — invasive / deferred**:
- `[ ]` Smart Animations — skip ticking texture animations for sprites not currently visible (hook `TextureAtlasSprite.updateAnimation` + per-sprite visibility tracking).
- `[ ]` Lagometer — per-stage frame-time graph overlay (chunk meshing vs render vs lighting vs ticks). Profiler API + rolling-window display.
- `[ ]` Cloud 2D/3D mode + opacity (beyond what current Atmosphere section covers).
- `[ ]` Per-bar HUD hide (armor/hunger/air) — see Tier B.
- `[ ]` Translucent block blending — correct color compositing for stacked transparent blocks (order-dependent transparent rendering is a big architectural change).
- `[x]` 9c.3 Option C — shipped 2026-05-22 via Strategy 4 BBox-projection (`9264bb2`). See §10 for the original plan; the actual ship took the simpler approach.
- `[x]` 9c.4 FSR2-style reconstruction — shipped 2026-05-22 (`1e9cdde`). LDOG-original Lanczos-3 kernel.
- `[ ]` **Shader pack gbuffer + shadow pass** — composite-stage runner shipped 2026-05-22; the remaining work is hooking each MC draw-call type (terrain solid/cutout/water, entities, sky basic/textured, clouds, weather, item, hand) to bind the matching `gbuffers_*.{vsh,fsh}` from the active pack, plus a depth-only shadow render pass. Weeks of focused effort. Gating prereq for `v1.0-beta`.
- `[ ]` Phase 10b runtime borderless (see §5).
- `[ ]` Phase C4 in-prod verification (see §5).
- `[ ]` Phase C4 field-name corrections.
- `[ ]` Phase C4 per-feature parity benchmarking.
- `[ ]` Phase C4 tooltips.
- `[ ]` Phase C3 polish: disk-cached TTF atlas, Unicode glyph pages, async rasterization, bold/italic GUI toggles, subpixel hint.

---

## 12. Critical Gotchas — Carry Forward

Non-obvious infrastructure facts a future reader (or session pickup) needs to know:

### Mixin loading / classloader timing
- **`Minecraft` and `FontRenderer` mixins MUST be in `mixins.ldog.vanilla.json`** (early config loaded via `IEarlyMixinLoader` in `LDOGCorePlugin`). These classes are pulled into the classloader during FML bootstrap BEFORE late mixin configs register. Late-loaded mixins on them silently no-op with "loaded too early" in the log.
- **Cannot target** `World`, `Block`, `BlockLiquid` from any mixin — they load before any MixinBooter config. Use Forge events or target wrapper classes (`ChunkCache` instead of `World`).
- Dynamic Light injection: `ChunkCache.getCombinedLight()` NOT `World.getCombinedLight()`. Return value packed `skyLight << 20 | blockLight << 4`.

### LWJGL 2.9.4 / Display
- `Display.setDisplayMode` must be called with **plain `new DisplayMode(w, h)`** — passing the full `Display.getDesktopDisplayMode()` carries bpp/refresh metadata that triggers fullscreen mode-switch intent even when not in exclusive fullscreen. Caused borderless flicker.
- `Display.destroy()` invalidates the entire GL context. Every LDOG-owned GL handle dies — main reason 10b runtime borderless is deferred.

### TAA / temporal
- TAA matrix capture must happen AFTER `applyJitter()`, not before. History stores jittered pixel positions; reprojection needs matrices that match what was actually rendered. (Bug 2 in 9c.2.)
- Jitter injection must target `renderWorldPass`, NOT `setupCameraTransform`. `renderWorldPass` overwrites projection (sky + terrain `gluPerspective` calls) and would invalidate `setupCameraTransform` jitter. (Bug 1 in 9c.1.)
- `renderWorldPass(pass)` with `pass != 2` is anaglyph-only (red/cyan eyes). Default MC always uses `pass == 2`. Don't gate on `pass == 0` unless explicitly wanting anaglyph-only.

### Phase 8/9 pipeline
- `AutoScaleHandler` uses `@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)` — matches `FXAAHandler` and `PerformanceOverlayRenderer` pattern. Registration auto at mod load.
- Preset changes need `extBorderSettingsChanged`, `fxaaSettingsChanged`, `waterSettingsChanged` on the GUI instance so `saveAndClose` triggers right reload paths.
- Auto-scale overrides manual Render Scale cycling — documented in tooltip.

### Phase 9c.3-A reactive mask
- Implemented via MRT + per-attachment `colorMaski`. sceneFbo gets COLOR1 R8 attachment always-allocated. Binding mixin `glDrawBuffers` to [COLOR0, COLOR1] and `glColorMaski(1, false)` around non-entity draws. `MixinRenderGlobal` opens `colorMaski(1, true)` around `renderEntities` HEAD/RETURN. Legacy fixed-function replicates `gl_FragColor` across bound attachments so no custom entity shader needed.

### Phase 9c.3-C entity MV
- BBox-projected velocity stamps (Strategy 4 from §10), not per-pixel geometry re-render. `MixinRenderManagerEntityMV` hooks `RenderManager.renderEntity` HEAD — covers every dispatched entity (mobs, items, XP orbs, projectiles, paintings, item frames, modded entities) through one mixin point.
- `EntityRenderStateCache.beginFrame()` is reset at the start of `renderWorldPass` (HEAD inside `MixinEntityRendererPostPipeline`) so the per-frame queue starts empty each render. If you add another mixin that affects renderWorldPass, ensure it doesn't reset the cache earlier.
- TAA + FSR 2 shaders read the MV target on a dedicated texture unit (4 for TAA, 3 for FSR 2). Both prefer entity MV when non-zero (per-pixel velocity > sub-pixel threshold) over the camera-only depth-based reprojection.
- MV target is half-res RG16F by design — TAA bilinear-samples it, soft transitions act as built-in smoothing.

### Phase 9c.4 FSR 2
- Standalone TAA pass MUST short-circuit when `UpscalerAlgorithm.selected() == FSR2`. FSR 2 owns history accumulation itself; two history-managing passes fight each other.
- `EntityMotionVectorPass` MUST run BEFORE FSR 2 in the pipeline pass list. The MV target needs to be populated by the time FSR 2 samples it. Easy mistake — MV pass naturally feels like it belongs near TAA, but TAA runs LATER.

### Shader pack runtime
- Composite stages get every standard OF/Iris uniform — `ShaderProgram.locate()` warns on missing uniforms. The runner sets all ~30 uniforms on every stage; shaders that declare only a few will spam "no active uniform" WARN lines on first compile. Acceptable noise — each warning fires once per uniform per shader thanks to the location cache.
- `colortex1..7` and `depthtex1..2` bind to a shared 1×1 black texture. Packs sampling these get zeroed values, NOT crashes. If you implement gbuffers later, those texture units need real allocations.
- Per-stage compile failures are logged at WARN, not ERROR, and the stage is dropped from the chain. A partially-working pack is more useful than no pack. Watch logs to spot which stages failed.

### Tabbed settings GUI
- Tab switches MUST defer to the next `updateScreen` tick via `pendingTabSwitch` — calling `initGui()` directly in `actionPerformed` clears `buttonList` while vanilla's `mouseClicked` loop is still iterating it, which can fire stale button indices.
- `activeTab` is `static` so it survives child-screen round-trips (e.g., the shader pack picker's standalone GuiScreen). Without this, clicking into a child screen and back resets to General.
- Shaders tab is special — `settingsList` is set to null when it's active, and `shaderPackList` is set to null when it's inactive. The mouse / draw routing checks `activeTab == Tab.SHADERS` to pick the right panel.

### LWJGL @At targets
- `@At` targets that reference LWJGL classes (`Lorg/lwjgl/util/glu/Project;...`, `Lorg/lwjgl/opengl/Display;...`) need `remap = false`. LWJGL isn't obfuscated, has no SRG mapping. Without `remap = false`, the annotation processor warns "Unable to locate method mapping" — non-fatal but noisy.

### Vignette
- Vignette pass MUST be absolute last in chain (after FXAA) so FXAA doesn't see the gradient as an edge to smooth.

### Biome blend
- Radius change requires `renderGlobal.loadRenderers()` to invalidate cached chunk meshes so the new radius takes effect immediately.

### Phase C4 OptiFine
- OF stores feature toggles as **instance fields on vanilla `GameSettings`** (added by OF's transformer), NOT on a static Config class. `optifine.Config` holds only platform state. `OFConfigBridge` reflects on `Minecraft.getMinecraft().gameSettings`, walks class hierarchy, tries candidate field names per `OFFeature`.
- **Do NOT put the OF jar in `run/mods/` for `gradlew runClient`** — crashes at `FMLClientHandler.detectOptifine` with `NoClassDefFoundError: cer` because OF's jar is obfuscated against production-MC class names. Verification path goes through a real launcher install.

### Other MC 1.12.2 quirks
- `EnumLightType` does NOT exist — MC 1.12.2 uses `EnumSkyBlock`. Compile-time bug.
- `TextureMap.mapRegisteredSprites` is cleared before `TextureStitchEvent.Pre` fires — cannot enumerate existing sprites during Pre. Scan resource packs directly or use other discovery.
- `IBakedModel.getQuads()` doesn't receive `IBlockAccess`. Solved via `CTMRenderContext` ThreadLocal set by `MixinBlockRendererDispatcher.renderBlock()`.
- Resource packs may use either `mcpatcher/ctm` or `optifine/ctm`. Tile PNGs live outside `textures/`, so `CTMSprite` (custom loader) is needed.
- GUI button-ID collisions: OF interop buttons originally started at 200 which collided with `BTN_DONE`; bumped to 400+ range.

### Mixin Registration
LDOG uses MixinBooter 10.7 with `ILateMixinLoader` (`LDOGMixinLoader`):
- `mixins.ldog.json` (late): GUI mixins, RenderGlobal, TESR, ParticleManager, BlockModelRenderer, BlockRendererDispatcher.
- `mixins.ldog.early.json` (also via late loader): BlockFluidRenderer, TextureAtlasSprite.
- `mixins.ldog.vanilla.json` (early via `IEarlyMixinLoader`): Minecraft, FontRenderer, anything that loads during FML bootstrap.

---

## 13. Future Expansion Ideas (from OptiFine inspection)

Catalogued 2026-04-18 by walking the OF 1.12.2 HD U G5 jar. Each is something OF supports that LDOG does not yet — a candidate for future phases beyond what's currently planned. Not commitments, just visibility. Sorted by estimated user impact.

- `[~]` **Real shader pack support** — partial. Discovery + composite + final stage execution shipped 2026-05-22 (composite chain produces visible post-process output for typical packs). Remaining: gbuffer programs (per-object draw shaders for terrain/entities/sky/clouds/water/item/hand etc.) + shadow pass + MRT `DRAWBUFFERS` directive + custom buffer formats from `shaders.properties`. This is the Phase 8 stretch goal.
- `[ ]` **CEM (Custom Entity Models)** — pack-supplied JSON model overrides for unique mob geometry.
- `[ ]` **Smart Animations** — skip ticking animations for non-visible sprites. Meaningful FPS gain on heavy-animation packs.
- `[ ]` **Multi-core / Smooth chunk loading** — chunk mesh build + upload across worker threads + frame budget. Reduces stutter on world-load and chunk-cross.
- `[ ]` **Smooth World** — distribute single-player tick work across frames.
- `[ ]` **Smooth Biomes** — blend grass/foliage/water colors at biome borders.
- `[ ]` **Per-particle-type toggles** — `[x]` 5 categories shipped (firework/portal/potion/water/dripping); remaining: Void, others.
- `[x]` **Vignette effect** — shipped.
- `[ ]` **Cloud quality + height controls** — `[x]` height shipped via Atmosphere section; `[ ]` 2D/3D modes still open.
- `[~]` **Fog customization** — `[x]` distance multiplier shipped; `[ ]` fancy fog toggle still open.
- `[ ]` **Translucent block blending** — correct color compositing for stacked transparents (big architectural change).
- `[ ]` **Custom GUIs** — texture-pack-replaceable backgrounds + button skins.
- `[ ]` **Lagometer** — per-stage frame-time visualization overlay.
- `[ ]` **Render Regions** — group nearby chunks into single VBO uploads.
- `[ ]` **Custom loading screens / panorama** — pack-supplied splash + background.

---

## 13.5. Testing Plan & Checklist

User-driven test checklist for the recent batches of features (2026-05-21 + 2026-05-22). Build (`./gradlew build`), drop the jar into a real MC 1.12.2+Forge install (gradle dev mode crashes when OF is present — see §12 Phase C4), launch, work through the boxes. Tick as you go; file findings in `docs/PHASE_9B_VALIDATION.md` for any upscaler-quality regression.

**Smoke (5 min)** — start in a flat creative world, verify build loads + opens settings GUI:
- [ ] Mod loads with no `Critical problem` or `Error` lines in `latest.log`.
- [ ] LDOG settings GUI opens from Options screen.
- [ ] LDOG settings GUI opens from Video Settings screen.
- [ ] F3+T resource reload doesn't crash or leave stale state.

**Phase 6c colors** — drop a pack containing `assets/minecraft/optifine/color.properties` into `resourcepacks/` with these lines, reload, verify:
```
water.6=0xFF0000        # red water in swamps (biome id 6)
potion.regeneration=0x00FF00
dye.blue=0xFF00FF       # blue dye renders magenta
```
- [ ] Swamp water visibly red.
- [ ] Regen potion bottle visibly green.
- [ ] Blue dye item icon + sheep wool visibly magenta.
- [ ] No override = vanilla color (test by removing keys).

**Tier A QoL**:
- [ ] Adv. Tooltips Always: hover an item with NBT data — debug-style details show without F3+H.
- [ ] No Nausea Distort: drink a Nausea II potion — screen does NOT swirl. Standing in a nether portal still ticks the gameplay timer (you go to nether eventually).
- [ ] Ping HUD: enable in multiplayer — green/yellow/red ms reading appears, hides in SP.
- [ ] Day Counter HUD: enable, sleep through a day — number increments.
- [ ] CPS HUD: enable, click rapidly — left/right counters tick up and decay.

**Tier B HUD hides**:
- [ ] Hide Armor Bar: equip armor — bar gone.
- [ ] Hide Hunger Bar: bar gone.
- [ ] Hide Air Bar: submerge underwater — air bubbles never appear.
- [ ] Hide Boss Health: spawn a Wither — boss bar gone (gameplay unaffected).

**Phase C3 font polish**:
- [ ] TTF Bold + Italic: enable both, change family — glyphs render bold-italic where AWT supports it.
- [ ] LCD Subpixel: enable — glyph edges visibly sharper on horizontal LCD; check for red/blue fringe (toggle off if present).

**Phase C4 OF Interop tooltips** — only meaningful with OptiFine installed in a production install:
- [ ] Hover each of the 7 OF Interop rows — tooltip body appears with Auto/LDOG/OF semantics.
- [ ] Shaders row tooltip notes "scaffold-only" caveat.

**Phase 1 perf** — useful via F3:
- [ ] `skipEmptyChunkSections` ON: ground level + sky chunks — F3 shows lower rebuild ms.
- [ ] `particleSpawnsPerTickLimit = 200`: detonate TNT in a flat area — particle count caps at ~200/tick, no FPS spike.

**HDR pipeline + Bloom**:
- [ ] Enable Post Pipeline + HDR Pipeline + Bloom — log line "HDR tonemap shader compiled OK" + "Bloom shaders compiled OK".
- [ ] Bright pixels (sun, torch, lava) bloom softly. Threshold slider toggles glow extent.
- [ ] Switch tonemap operator (ACES → Reinhard → Uncharted2 → Linear) — visible contrast/curve change.
- [ ] Exposure slider — overall brightness shifts.
- [ ] Linear operator + exposure > 2.0 — visible LDR clipping (sanity check: shows tonemap is actually mapping).
- [ ] Toggle HDR off + on — no FBO leaks (target reallocates cleanly per log).

**Shader-pack composite runner** (new 2026-05-22):
- [ ] `<.minecraft>/shaderpacks/` exists after first launch.
- [ ] Open Shaders tab — list shows all packs in the folder + a `(none)` row at top.
- [ ] Drop a new pack while game is running, click Rescan — pack appears.
- [ ] Open Folder button pops the OS file browser at the right directory.
- [ ] Click a pack — log line `LDOG: Shader pack 'X' compiled — N composite stage(s) + final` appears within a frame.
- [ ] Per-stage compile failure: WARN log entry quotes the GLSL error; other stages keep running.
- [ ] Activated pack visibly changes the scene (color grading / sky tint / atmospheric effects). Gbuffer-driven effects WILL NOT work yet — that's the next phase.
- [ ] Click `(none)` — pack deactivates, scene returns to vanilla-style render.

**Phase 9c.3-C entity MV** (new 2026-05-22):
- [ ] Enable Post Pipeline + TAA + Entity MV. Log line `LDOG: TAA entity MV reprojection ACTIVE (9c.3-C)` appears on first frame after a moving entity renders.
- [ ] Spawn moving mobs (sheep walking), pan camera at moderate speed — entities should NOT smear; sharper than Reactive-Mask-only mode.
- [ ] Try with FSR 2 selected — same crisp moving-entity result at sub-native render scale.

**Phase 9c.4 FSR 2** (new 2026-05-22):
- [ ] Set Upscaler = FSR2, Render Scale = 0.75, Post Pipeline ON, TAA ON. Log line `LDOG: FSR2 reconstruction live (sceneW x sceneH scaled -> mainW x mainH, scale 0.75)` appears.
- [ ] Visibly sharper than FSR1 / FSR1-Quality at the same scale on dense foliage / fine geometry.
- [ ] Disocclusion test: spin camera 180° — slight one-frame artifact on newly-visible pixels is expected and documented.
- [ ] Switch upscaler back to FSR1 — log shows TAA pass re-engages, FSR 2 short-circuits.

**Tabbed settings GUI** (new 2026-05-22):
- [ ] Six tabs across top: General / Rendering / Visual / Features / Shaders / UI-HUD. Active tab underlined yellow.
- [ ] Click each tab — content panel switches without flicker.
- [ ] Click a row in Shaders tab — pack activates; tab stays put.
- [ ] Visit Shaders tab, click a child screen (e.g., upscaler preset detail), return — same tab still active.
- [ ] Done button visible and works from every tab.

**Regression sanity** — features shipped earlier should still work:
- [ ] CTM glass + bookshelf textures wrap correctly.
- [ ] Emissive ores glow.
- [ ] Custom sky renders in `optifine/sky/world0/` pack.
- [ ] Borderless windowed activates after restart with `borderlessFullscreen=true`.
- [ ] Dynamic lights from held torch make a dark room visible.
- [ ] FSR1 / FSR1-Quality upscaler at 0.75 scale produces a visibly upscaled-but-sharp image.
- [ ] TAA + entity reactive mask kills moving-mob ghost trails.

**Unit tests**:
- [ ] `./gradlew test` — all 50 tests pass.

If any box fails: capture `latest.log` + a screenshot + the toggles that were on, drop it into the findings log in `docs/PHASE_9B_VALIDATION.md` §7.

---

## 14. Version Milestones

| Version | Phase | What Users Get |
|---|---|---|
| `v0.0.1-alpha` | Phase 0 | Mod loads, nothing visible yet |
| `v0.1.0-alpha` | Phase 1 + C1 | FPS improvements, FPS reducer, clear water (replaces 3 mods) |
| `v0.4.0-alpha` | Phase 2-4 | HD textures, CTM, emissive textures |
| `v0.5.0-alpha` | Phase 5 | Dynamic lights, lighting customization |
| `v0.6.0-alpha` | Phase 6 | Full resource pack feature parity |
| (2026-04-18) | Phase 7-10 + C3-C4 foundation | AA/AF, shader pipeline, FSR1/RCAS, TAA MVP, borderless, smooth font, OF interop foundation |
| (2026-05-21) | + HDR + Bloom + Phase 6c colors + Tier A/B + shader-pack scaffold | HDR pipeline, bloom, biome/potion/dye colors, full QoL toggles + HUD hides, shader-pack discovery |
| (current, 2026-05-22) | + Phase 9c.3-C entity MV + Phase 9c.4 FSR 2 + composite shader runner + tabbed GUI | Full temporal upscaling (FSR 2 + entity MV), composite/final stage execution for shader packs (most visual identity visible), tabbed settings with dedicated Shaders tab |
| `v1.0.0-beta` | Phase 8 stretch gbuffer + shadow pass | Full shader-pack OptiFine compatibility — gbuffer programs hooked into every MC draw-call type + shadow pass |

50 unit tests, all passing.

---

## 15. Open-Source Reference Mods

| Project | Used as | License |
|---|---|---|
| ConnectedTexturesMod (Chisel-Team) | CTM impl reference | MIT |
| AtomicStryker Dynamic Lights | Dynamic lighting concept | Open |
| FoamFix (asiekierka) / VintageFix | Memory opts concept | GPL-3.0 |
| VanillaFix (DimensionalDevelopment) | Bug fix patterns | MIT |
| BetterFPS (Guichaguri) | Perf opt patterns | MIT |
| ShadersMod (karyonix) | Original pre-OF shader pipeline reference | LGPL |
| Super Resolution (Modrinth) | FSR/temporal concept — but 1.18+ Vulkan target | — |
| Radiance (CurseForge) | Full renderer replacement concept — out of scope | — |
| hancin/Fullscreen-Windowed-Minecraft | Phase 10b runtime borderless reference | — |

Policy: external projects are design references only. No code copying. Implement runtime code independently in LDOG style; document conceptual borrowings.

---

## 16. Git History Snapshot (key landmarks)

| Tag/Hash | What |
|---|---|
| `v0.0.1-alpha` | Phase 0 scaffold |
| `v0.1.0-alpha` | Phase 1 complete |
| `v0.4.0-alpha` | Phase 2-4 initial |
| `3325e11` | Emissive direct pack scanning + CTM null-side fix |
| `6a9bc9d` | CTMSprite mipmap crash fix |
| `3d518bb` | CTM tile mapping rewrite + emissive reflection fix |
| `aff578c` | CTM scanner + emissive sprite registration |
| `3374a86` | 9a.9 auto-scale log fix (2026-04-18) |
| `14628e1` | 9c.3-A entity reactive mask (2026-04-18) |
| `9d77526` | 9a.9 ext aggressive 3-state mode (2026-04-18) |
| `3b083bc` | Phase C4 OF interop foundation (2026-04-18) |
| `e25f1c7` | Per-particle toggles (2026-04-18) |
| `68d4435` | Vignette pass (2026-04-18) |
| `e25d79d` | Atmosphere section (2026-04-18) |
| `39fe2a2`, `9a258bc` | Comfort/Cinematic toggles (2026-04-18) |
| `374789c` | Info HUD overlays (2026-04-18) |
| `25665b1` | Phase 6c per-biome water + potion + dye color overrides (2026-05-21) |
| `16a5115` | Tier A QoL + Tier B HUD element hides (2026-05-21) |
| `5f0c59b` | C3 TTF bold/italic/subpixel + C4 OF interop tooltips (2026-05-21) |
| `8b29d26` | Phase 1 A2 skip-empty-sections + C2 particle cap + 9b validation doc (2026-05-21) |
| `74e468d` | HDR pipeline (RGBA16F + ACES/Reinhard/Uncharted2/Linear tonemap) (2026-05-21) |
| `5a924c6` | HDR bloom pass (bright-extract + 9-tap Gaussian + composite) (2026-05-21) |
| `17f243a` | Shader-pack discovery + selection scaffold (2026-05-21) |
| `6e7c872` | Fix 3 load-order bugs: SpawnCounter inner class, late→early mixin moves, HDR ensure ping-pong (2026-05-22) |
| `9264bb2` | Phase 9c.3-C per-entity MV via BBox-projected velocity stamps (2026-05-22) |
| `1e9cdde` | Phase 9c.4 FSR 2 reconstruction kernel (2026-05-22) |
| `7e52605` | Tooltip dev-jargon cleanup (2026-05-22) |
| `bc3df8d` | ~60 new tooltips across every settings section (2026-05-22) |
| `af7ac9d` | Small GUI section reorg (2026-05-22) |
| `fd7d9bd` | 3 mixin warnings cleared + dedicated GuiShaderPackPicker (2026-05-22) |
| `ed2defe` | Tabbed settings GUI + Shaders tab embedding pack picker (2026-05-22) |
| `e056e67` | Shader-pack composite + final stage runner (2026-05-22) |
