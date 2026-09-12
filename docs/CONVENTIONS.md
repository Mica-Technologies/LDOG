# LDOG Development Conventions

Patterns and conventions established during development. Reference this when adding new features to maintain consistency.

---

## Mixin Conventions

### Naming
- Class: `Mixin<TargetClassName>` (e.g., `MixinBlockModelRenderer`, `MixinWorldDynamicLights`)
- Injected methods: `ldog$<descriptiveName>` prefix (e.g., `ldog$injectDynamicLight`, `ldog$renderEmissiveOverlay`)
- Unique fields: `ldog$<name>` prefix with `@Unique` annotation

### Config Gating
Every mixin injection should early-return when its feature is disabled:
```java
if (!LDOGConfig.enableFeatureName) return;
```

### Mixin Configs
- **`mixins.ldog.json`** (late, via `ILateMixinLoader` in `LDOGMixinLoader`): Most mixins go here. Works for classes loaded after mod init — renderers, GUI, chunk builders.
- **`mixins.ldog.early.json`** (also `ILateMixinLoader`): For classes loaded slightly earlier — `BlockFluidRenderer`, `TextureAtlasSprite`, `TextureMap`, `Stitcher`.
- **`mixins.ldog.vanilla.json`** (**early**, via `IEarlyMixinLoader` in `LDOGCorePlugin`, an `IFMLLoadingPlugin`): For classes loaded during the FML core-plugin phase, before either late config gets a chance to register — `Minecraft`, `FontRenderer`, `GuiIngame`, `EnumDyeColor`, `Potion`. `MixinMinecraftBorderless` is the example: it targets `Minecraft` directly and only works because it's routed through this config, not the late one.
- **Still cannot target**: `World`, `Block`, `BlockLiquid` — no LDOG mixin targets these directly, even via the early config. Use Forge events, or target a wrapper class that loads later (`ChunkCache` instead of `World`).

### Known Target Workarounds
| Want to target | Use instead | Why |
|---|---|---|
| `World.getCombinedLight` | `ChunkCache.getCombinedLight` | World loads before any mixin config, early or late |
| `Block` methods | Forge events | Block loads too early for any mixin config |
| `Minecraft` | `mixins.ldog.vanilla.json` (early, `IEarlyMixinLoader`) | Loads too early for the late configs, but the early config reaches it — see `MixinMinecraftBorderless` |

---

## Config Conventions (`LDOGConfig.java`)

### Organization
Config fields are grouped by section with `// ----` comment headers. Current sections, in file order (verify against `LDOGConfig.java` — new features append a new section rather than overloading an existing one):
1. **Global Preset** — the `globalPreset` string (`LDOGPreset`)
2. **Future Features** — historical header name; these toggles (CTM, emissive, dynamic lights, custom sky, better grass/snow, natural textures, custom colors, random mobs, lighting customization) are all shipped, not future work — don't take the header literally
3. **Visual: Anti-aliasing / Texture filtering** — AF, extended-border mipmaps
4. **Font Rendering** — smooth/HD/TTF font settings
5. **HDR pipeline** — tonemap, exposure, bloom
6. **Performance: Rendering Optimizations** — distances, culling, LOD
7. **Per-type particle toggles**
8. **Vignette post-process**
9. **Atmosphere: Clouds / Fog / Sky / Weather**
10. **Comfort / Cinematic toggles**
11. **Info HUD overlays**
12. **Tier A small features** / **Tier B HUD hides**
13. **Performance: FPS Management** — FPS reducer, AFK settings
14. **Visual: Water**
15. **OptiFine Interop** — per-feature `OFOverrideMode` selections

### Naming
- Feature toggles: `enable<FeatureName>` (boolean, default `true`)
- Numeric settings: descriptive name with `@Config.RangeInt` or `@Config.RangeDouble`
- Preset strings: `<feature>Preset` with valid values listed in comment

### OptiFine Auto-Disable
Features that overlap with OptiFine are auto-disabled at runtime via `OptiFineCompat`. Config values stay as-is; the compat layer overrides behavior.

---

## GUI Settings Conventions (`GuiLDOGSettings.java`)

### Button IDs
Allocated in ranges by section (verify against the `BTN_*` constants at the top of `GuiLDOGSettings.java` — the GUI has grown well past the original single-digit-decade scheme, so treat this as a map of occupied ranges, not an exhaustive list):
- 10-14: Performance / render opts
- 20-23: FPS Management
- 30-36: Visual (water)
- 40-48: Features (CTM, emissive, dynamic lights, custom sky, HD textures, shaders, gbuffers, shadows)
- 50-60: Lighting (dynamic-light temperature, HDR toggle)
- 70-75: Better grass/snow, natural textures, custom colors, random mobs, perf overlay
- 80-85: Anisotropic filtering, MSAA, FXAA, extended-border mipmaps
- 90-99, 580: Font (smooth/HD/TTF)
- 100-114: Post-process pipeline (scale, upscaler, FSR1/RCAS, TAA, auto-scale, borderless)
- 200: Done button (`BTN_DONE`)
- 300: Global LDOG preset cycle
- **400-406: OptiFine-interop mode buttons.** Deliberately placed at 400+ — an earlier revision put these in a lower range and collided with `BTN_DONE` (200); they were moved specifically to stay clear of 200 and 300 (see the comment above `BTN_OF_MODE_CTM` in `GuiLDOGSettings.java`). Don't reclaim 200-399 for new OF-interop buttons.
- 500-573: Particle/vignette/atmosphere toggles, comfort toggles, HUD-hide/overlay toggles
- 600-610: HDR pipeline / bloom, shader pack picker button
- 1000+: Tab buttons (`BTN_TAB_BASE`) and Shaders-tab pack-row buttons (1090+)

New buttons should go in an unused sub-range of their section, or start a new hundred-block if the section is full — don't renumber existing constants.

### Cycling Values
Discrete options use `cycleValue(int[] values, int current)` or `cycleValue(double[] values, double current)`. Arrays define the allowed steps.

### Presets Pattern
For features with multiple related settings (water color, light temperature):
1. Define a preset array/enum with named configurations
2. Add a "Preset" button that cycles through them
3. Individual setting buttons allow manual override (show "Custom" when preset doesn't match)
4. Presets auto-apply related toggles (e.g., water preset enables clear water + tint)

### Label Formatting
- Toggles: `"Name: §aON"` / `"Name: §cOFF"` (green/red)
- Values: `"Name: §a<value>"` (green)
- Disabled: `"Name: §7OptiFine"` (gray, button disabled)
- Color channels: use color codes (`§c` red, `§a` green, `§9` blue)

---

## Feature Module Structure

### Package Layout
Each feature gets its own package under `render/`:
```
render/
  ctm/           — Connected textures
  emissive/      — Emissive texture overlays
  dynamiclights/ — Dynamic lights + light temperature
```

### Standard Components
A typical feature module has:
- **Registry/Manager** (singleton): Loads resources, manages state
- **Properties/Config parser**: Reads resource pack configs (`.properties` files)
- **Mixin(s)**: Hooks into vanilla rendering pipeline
- **Tick/Event handler**: Registered on `MinecraftForge.EVENT_BUS` in `ClientProxy.init()`

### ThreadLocal Pattern
When `IBakedModel.getQuads()` needs data not in its parameters (world position, block access), use a ThreadLocal context:
```java
public class FeatureRenderContext {
    private static final ThreadLocal<Data> CONTEXT = new ThreadLocal<>();
    public static void set(Data data) { CONTEXT.set(data); }
    public static Data get() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
```
Set in a mixin at `@At("HEAD")` of the rendering method, clear at `@At("RETURN")`.

---

## CTM-Specific Conventions

### Glass Pane Handling
- Pane models mirror UV on WEST and NORTH faces — use `mirrorH` flag when calculating CTM tile index for these faces
- Synthetic quads fill absent arm areas so CTM borders appear at block edges
- Axis guard: only add synthetic quads when at least one arm on the same axis is present (`hasNS`/`hasEW`)
- Seam suppression: remove UP/DOWN edge strips between stacked panes for seamless glass

### Vertex Format
Synthetic quads must use `DefaultVertexFormats.BLOCK` (7 ints/vertex: pos + color + tex + lightmap), not `ITEM` (6 ints/vertex).

### retextureQuad
Uses `getUnInterpolatedU/V` → `getInterpolatedU/V` to remap UV from old sprite to new sprite. Preserves the original model's UV layout.

---

## Rendering Pipeline Notes

### Lightmap Customization
The lightmap is a 16×16 texture (256 entries) mapping `skyLight*16 + blockLight` to ARGB color. Modify `lightmapColors[]` in `EntityRenderer.updateLightmap()` just before `updateDynamicTexture()` to apply global color shifts. Zero per-block cost.

**Effect ordering matters:**
1. Block/sky light color tinting (weighted blend based on which light source dominates)
2. Brightness boost (additive shift, applied before darkness so darkness overrides it)
3. Night darkness (multiplicative reduction — must come AFTER brightness)
4. HDR tonemapping (ACES curve, applied last)

**Night darkness formula:** `darkMul = skyFactor / (skyFactor + nightDark - 1)` with block light protection for torches (`blockLevel >= 8` = full protection). This is a global brightness reduction, not just a sky-based clamp, so vanilla gamma/brightness can't counteract it.

### Dynamic Light Injection
Inject into `ChunkCache.getCombinedLight()` (NOT `World` — World loads before any mixin system). The return value is packed as `skyLight << 20 | blockLight << 4`. Extract, modify, repack.

### Chunk Re-renders
Call `RenderGlobal.markBlockRangeForRenderUpdate(x1,y1,z1, x2,y2,z2)` when dynamic state changes. Only mark when the block position actually changes, not sub-block movement.

---

## Resource Pack Conventions

### CTM Properties
Scanned from `mcpatcher/ctm/` and `optifine/ctm/` paths. Tile PNGs live outside `textures/` — `CTMSprite` (custom `TextureAtlasSprite` loader) handles non-standard paths.

### Emissive Textures
Suffix-based: `<texture>_e.png` for emissive overlay. Suffix configurable via `emissive.properties`.

---

## Build & Test

### Quick Commands
```bash
JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.19" ./gradlew compileJava  # fast compile check
JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.19" ./gradlew build        # full build
JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.19" ./gradlew runClient     # launch game
```
Gradle 8.9 runs on JDK 8–21 only; a JDK 25/26 in `~/.jdks` cannot drive the wrapper. RFG prints a
"Java < 21 is deprecated" notice on 17 — harmless, CI runs on 21.

### Checking Mixin Issues
Search `run/logs/latest.log` for:
- `"loaded too early"` — target class loaded before mixin applied; need different target or earlier config
- `"Critical problem"` — mixin failed to apply
- `"Error"` + mixin class name — injection point not found (wrong method signature)

---

## Gotchas Carried Forward

Hard-won facts from development sessions, migrated out of the working plans on 2026-09-12 so they
survive plan retirement. Each one cost at least an hour once.

### Mixin loading and class timing
- **A wrong method descriptor fails the entire mixin class**, not just that injector, and without a crash. `RenderGlobal.renderClouds` in 1.12.2 is `(F,I,D,D,D)`; the `(F,I)` guess silently disabled every hook in `MixinRenderGlobal` (entity culling, custom sky, reactive mask). After any mixin edit, grep `latest.log` for the target's one-shot confirmation lines (`renderSky mixin CONFIRMED` etc.).
- **The `mixin` package may only contain mixins.** A nested helper class there (`MixinParticleManagerFilter$SpawnCounter`) makes MixinBooter throw `IllegalClassLoadError` at mod init. Helpers live under `render/`.
- Classes touched during `Bootstrap.register` (`Potion`, `EnumDyeColor`, `GuiIngame`) must be in `mixins.ldog.vanilla.json`; a late-config mixin on them logs "Critical problem … loaded too early" and no-ops.
- `@At` targets that reference LWJGL classes (`Project.gluPerspective`, `Display.setFullscreen`) and Forge-added methods (`TextureMap.finishLoading`, `FontRenderer.bindTexture`) need `remap = false` — there is no SRG mapping, and the annotation processor warns "Unable to locate method mapping" without it.
- `RenderChunk.position` is a private `MutableBlockPos`; `@Shadow` the `getPosition()` accessor instead.
- `EnumLightType` does not exist in 1.12.2 — it is `EnumSkyBlock`.
- `TextureMap.mapRegisteredSprites` is cleared before `TextureStitchEvent.Pre` fires — sprites cannot be enumerated during Pre; scan the resource packs directly.
- `MixinFontRenderer` must bypass subclasses (`self.getClass() != FontRenderer.class`) — Forge's `SplashProgress$SplashFontRenderer` runs on a separate GL context/thread.

### LWJGL 2.9.4 / Display
- `Display.setDisplayMode` takes a plain `new DisplayMode(w, h)`. Passing `getDesktopDisplayMode()` carries bpp/refresh metadata that triggers a fullscreen mode-switch even when not in exclusive fullscreen (the borderless flicker). Order: `setResizable` → `setLocation` → `setDisplayMode`.
- `Display.destroy()` invalidates the whole GL context; every LDOG-owned handle (FBOs, shader programs, font atlases, MSAA FBO) dies. This is why the runtime borderless toggle is deferred.
- Windows "Fullscreen Optimizations": a window exactly desktop-sized gets auto-promoted by DWM. Default height `desktop_h - 1` dodges it ("Block FS Optim" toggle).
- Fullscreen-at-startup goes through `Minecraft.toggleFullscreen()`, which calls `resize(w, h)` → `currentScreen.onResize`. Use `mc.resize(w, h)`, not `updateFramebufferSize` directly.

### Post-process pipeline, TAA, FSR2
- `renderWorldPass(pass)` with `pass != 2` is the anaglyph path; default MC always passes 2. Gate on `pass != 2`, never `pass == 0`.
- Jitter injection targets `renderWorldPass` (both `gluPerspective` ordinals), not `setupCameraTransform` — the latter's projection is overwritten before terrain draws.
- TAA/FSR2 matrix capture happens **after** `applyJitter()`; history stores jittered pixel positions, and un-jittered matrices produce "drunk/swimming" reprojection.
- The standalone TAA pass short-circuits when FSR2 is the upscaler; FSR2 owns history accumulation. Two history managers fight.
- `EntityMotionVectorPass` runs **before** FSR2 in the pass list so the MV target is populated. `EntityRenderStateCache.beginFrame()` resets at `renderWorldPass` HEAD — another mixin on that method must not reset it earlier.
- Reactive mask = MRT + per-attachment `glColorMaski`; legacy fixed-function replicates `gl_FragColor` across bound attachments, so no custom entity shader is needed. The reactive mask and the deferred gbuffer MRT path are mutually exclusive.
- Vignette is the absolute last pass (after FXAA) so FXAA does not treat the gradient as an edge.
- Every `RenderTargetManager.ensure(...)` caller must pass the same HDR flag. Two callers disagreeing reallocate targets every frame (symptom: enabling HDR turns the world black).
- `glPopAttrib` restores real GL state behind `GlStateManager`'s cache, after which later `GlStateManager` calls are silently skipped. Call `GlStateSync` after every `glPopAttrib`.
- The pass chain self-heals: a throwing pass is skipped, not removed, and `registerPasses()` rebuilds when the chain is empty. Removing passes permanently caused black screens on toggle.
- `PipelineGlProbe.drain(stage)` runs at every stage boundary, so the first stage to report an error is where it was born. Known drained-and-benign `0x502`s: `gbuffer:bind:*`, `shadow:depth-render`, `composite (multi-write)`.
- `AutoScaleHandler` overrides manual Render Scale and must stay quiet under the FPS-reducer cap and MSAA, or it pumps the resolution every 2 s.

### Shader packs
- Uniform **types** matter: `cameraPosition` / `sunPosition` are `vec3`, `atlasSize` is `ivec2`. Pushing the wrong type silently zeroes them (packs saw a zero camera for weeks).
- Resolve `DRAWBUFFERS` only after `GlslPreprocessor` (seeded with `ShaderMacros`) has stripped dead branches, so the surviving directive is the active one. Pad unwritten slots with `GL_NONE` — compacting shifts `gl_FragData[i]` into the wrong colortex.
- Composite-only colortex buffers persist frame to frame (OF `colortexNClear = false` semantics). Only re-copy buffers the gbuffer programs actually wrote; clearing everything zeroes pack TAA history (BSL "invisible except sky").
- Render the shadow map **through the pack's `shadow` program** when it ships one. BSL warps xy and scales z in `shadow.vsh` and undoes it on the sampling side; a fixed-function ortho map makes every lookup read occluded (uniform darkness).
- Detach all colour attachments before each multi-write composite stage; a stale attachment creates a feedback loop (`0x502`).
- `MC_NORMAL_MAP` / `MC_SPECULAR_MAP` are deliberately undefined until real map textures are fed; defining them makes packs sample black.
- Pack programs use `ShaderProgram.quietMissingUniforms()` (they use a subset by design); LDOG's own passes keep the warnings.
- MC 1.16+ builds of packs (GLSL 330 core: BSL v10 for 1.16, Complementary r5, Solas, Continuum 2.0.5) cannot run on 1.12.2 GL 2.1, same as under OptiFine. The **1.12.2 builds** of BSL v8.2 / v10.1 are `#version 120` and are the reference deferred packs; SEUS v11 is the reference forward pack.
- `colortex1..7` / `depthtex1..2` fall back to a shared 1×1 black texture when no gbuffer path allocated them — packs get zeros, not crashes.

### Settings GUI
- Tab switches defer to the next `updateScreen` tick via `pendingTabSwitch`; calling `initGui()` from `actionPerformed` clears `buttonList` while vanilla's `mouseClicked` loop is still iterating it.
- `activeTab` is static so a child screen (shader-pack picker) round-trip does not reset it.
- `onGuiClosed` → `doSave`: Esc saves.
- Preset changes must set `extBorderSettingsChanged` / `fxaaSettingsChanged` / `waterSettingsChanged` so `saveAndClose` triggers the right reloads.
- OF interop buttons live at ID 400+ (200 collided with `BTN_DONE`).

### OptiFine
- OF stores its feature toggles as **instance fields on vanilla `GameSettings`** (added by its transformer), not on a static `Config` class. `OFConfigBridge` reflects on `mc.gameSettings` and walks the hierarchy.
- **Never put the OF jar in `run/mods/`.** `gradlew runClient` crashes at `FMLClientHandler.detectOptifine` with `NoClassDefFoundError: cer` — OF is obfuscated against notch names. Verify OF coexistence in a production launcher install.
- `OptiFineCompat.isActive()` = config flag && `shouldHandle`; `LDOGMixinPlugin` disables the four `renderWorldPass` mixins when OF is present.

### Miscellaneous
- Biome blend radius changes need `renderGlobal.loadRenderers()` to invalidate cached chunk meshes.
- Dynamic-light lookups run on chunk-worker threads: read the `lastPos` snapshot, never `entity.getPosition()`.
- `skipEmptyChunkSections` must not mark an empty section's faces as occluding in `CompiledChunk` — that was the "terrain holes across a valley" bug.
- A resource pack that ships `optifine/natural.properties` must not disable the built-in defaults for blocks it does not mention.
