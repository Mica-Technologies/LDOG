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
- **`mixins.ldog.json`** (late, via `ILateMixinLoader`): Most mixins go here. Works for classes loaded after mod init — renderers, GUI, chunk builders.
- **`mixins.ldog.early.json`** (also late loader): For classes loaded slightly earlier — `BlockFluidRenderer`, `TextureAtlasSprite`.
- **Cannot target**: `World`, `Minecraft`, `Block`, `BlockLiquid` — these load before any MixinBooter config. Use Forge events or target wrapper classes (`ChunkCache` instead of `World`).

### Known Target Workarounds
| Want to target | Use instead | Why |
|---|---|---|
| `World.getCombinedLight` | `ChunkCache.getCombinedLight` | World loads before mixins |
| `Block` methods | Forge events | Block loads too early |
| `Minecraft` | Forge events | Loads too early |

---

## Config Conventions (`LDOGConfig.java`)

### Organization
Config fields are grouped by section with comment headers:
1. **Future Features** — toggles for major feature modules (CTM, emissive, dynamic lights, shaders)
2. **Performance** — rendering optimizations, distances, culling
3. **FPS Management** — FPS reducer, AFK settings
4. **Visual** — water, lighting

### Naming
- Feature toggles: `enable<FeatureName>` (boolean, default `true`)
- Numeric settings: descriptive name with `@Config.RangeInt` or `@Config.RangeDouble`
- Preset strings: `<feature>Preset` with valid values listed in comment

### OptiFine Auto-Disable
Features that overlap with OptiFine are auto-disabled at runtime via `OptiFineCompat`. Config values stay as-is; the compat layer overrides behavior.

---

## GUI Settings Conventions (`GuiLDOGSettings.java`)

### Button IDs
Allocated in ranges by section:
- 10-19: Performance
- 20-29: FPS Management
- 30-39: Visual (water)
- 40-49: Features (CTM, emissive, etc.)
- 50-59: Lighting (dynamic lights, temperature)
- 200: Done button

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

### Lightmap Tinting
The lightmap is a 16×16 texture (256 entries) mapping `skyLight*16 + blockLight` to ARGB color. Modify `lightmapColors[]` in `EntityRenderer.updateLightmap()` just before `updateDynamicTexture()` to apply global color shifts. Zero per-block cost.

### Dynamic Light Injection
Inject into `ChunkCache.getCombinedLight()` to raise the block light component. The return value is packed as `skyLight << 20 | blockLight << 4`. Extract, modify, repack.

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
JAVA_HOME="C:/Users/ahawk/.jdks/azul-17.0.18" ./gradlew compileJava  # fast compile check
JAVA_HOME="C:/Users/ahawk/.jdks/azul-17.0.18" ./gradlew build        # full build
JAVA_HOME="C:/Users/ahawk/.jdks/azul-17.0.18" ./gradlew runClient     # launch game
```

### Checking Mixin Issues
Search `run/logs/latest.log` for:
- `"loaded too early"` — target class loaded before mixin applied; need different target or earlier config
- `"Critical problem"` — mixin failed to apply
- `"Error"` + mixin class name — injection point not found (wrong method signature)
