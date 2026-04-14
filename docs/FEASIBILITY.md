# OptiFine Replacement Feasibility Analysis

## TL;DR

Replicating OptiFine is **feasible but a large undertaking**. The good news: most individual features are well-understood, some already exist as standalone mods, and 1.12.2 is a stable target that won't move under you. The hard part is the shader pipeline -- that alone is a multi-month effort requiring deep OpenGL expertise.

A practical approach is to build incrementally, starting with the highest-value, lowest-risk features and working toward shaders as a stretch goal.

## Feature-by-Feature Breakdown

### Tier 1: High Feasibility, High Value

These are well-understood, have existing open-source references, and can be implemented with Mixins.

| Feature | Difficulty | Notes |
|---|---|---|
| **Rendering Optimizations** | Medium | Chunk culling, frustum checks, entity render distance. Vanilla 1.12.2 rendering is inefficient -- lots of low-hanging fruit. Reference: BetterFPS, FoamFix, VanillaFix. |
| **Connected Textures (CTM)** | Medium | The [ConnectedTexturesMod](https://github.com/Chisel-Team/ConnectedTexturesMod) is open-source (MIT) for 1.12.2. We could integrate similar logic or depend on it. Involves custom `IBakedModel` wrapping and texture stitching. |
| **HD Texture Support** | Low-Medium | Mostly involves patching the texture atlas stitcher to handle non-power-of-two and >16x textures. Relatively straightforward Mixin targets. |
| **Dynamic Lights** | Medium | Track light-emitting entities/items, inject temporary light values into the client-side light map. Reference: AtomicStryker's Dynamic Lights (open-source). Tricky part: performance -- updating light values every tick for moving entities. |
| **Better Grass/Snow** | Low | Texture replacement based on adjacent block checks. Simple Mixin on `BlockModelRenderer`. |

### Tier 2: Medium Feasibility

These require more rendering knowledge but are still achievable.

| Feature | Difficulty | Notes |
|---|---|---|
| **Emissive Textures** | Medium | Render a second pass on block/item models with fullbright lightmap coords. Requires hooking into `BlockModelRenderer` and `RenderItem`. The `_e` suffix texture convention from OptiFine is simple to replicate. |
| **Custom Sky** | Medium | Replace `RenderGlobal.renderSky()` with custom logic. Load sky cubemaps from resource packs. Well-defined rendering pipeline. |
| **Custom Colors** | Medium | Override biome colors, potion colors, map colors, etc. from resource pack properties files. Lots of individual hooks but each one is simple. |
| **Anti-aliasing / Anisotropic Filtering** | Low | GL state changes (`glTexParameteri` for AF, FBO post-processing for AA). Mostly configuration, not complex logic. |
| **Natural Textures / Random Mobs** | Low-Medium | Rotation/flip variations for block textures; random entity texture selection. Straightforward model/renderer hooks. |

### Tier 3: Hard -- Stretch Goals

| Feature | Difficulty | Notes |
|---|---|---|
| **Shader Support (GLSL)** | Very High | This is the big one. OptiFine's shader pipeline (`net.optifine.shaders`) is ~15,000+ lines of deeply intertwined OpenGL code. It replaces the entire rendering pipeline with a deferred/forward hybrid system supporting shadow maps, GBuffers, composite passes, and custom uniforms. **This is not impossible**, but it requires: (1) deep OpenGL 2.1/3.3 knowledge, (2) understanding of Minecraft's entire render pipeline, (3) months of work. Reference: ShadersMod (the original open-source project OptiFine absorbed) was ~5,000 lines for basic support. Modern shader packs (SEUS, BSL) expect the full OptiFine shader API. |
| **Custom Entity Models (CEM)** | High | Requires a model format parser, bone/animation system, and hooking into every entity renderer. Each entity type has its own renderer with hardcoded geometry. Very labor-intensive. |
| **Custom GUIs** | Medium-High | Resource pack driven GUI customization. Large surface area of vanilla GUI classes to hook. |

## Recommended Development Order

1. **Rendering optimizations** -- Immediate value, builds familiarity with the render pipeline
2. **HD textures** -- Low difficulty, enables resource pack compatibility
3. **Connected textures** -- High user demand, well-understood problem
4. **Emissive textures** -- Moderate difficulty, high visual impact
5. **Dynamic lights** -- Popular feature, moderate complexity
6. **Custom sky / colors / natural textures** -- Resource pack features, moderate effort each
7. **Shader pipeline** -- The big stretch goal, tackle once the foundation is solid

## Technical Approach

### Mixins (Primary)

Use MixinBooter for 1.12.2 Mixin support. Target classes:
- `net.minecraft.client.renderer.BlockModelRenderer` -- CTM, emissive textures
- `net.minecraft.client.renderer.RenderGlobal` -- Custom sky, render optimizations
- `net.minecraft.client.renderer.chunk.RenderChunk` -- Chunk optimizations
- `net.minecraft.client.renderer.texture.TextureMap` -- HD textures, texture stitching
- `net.minecraft.client.renderer.EntityRenderer` -- Shader pipeline entry point
- `net.minecraft.world.World` (client) -- Dynamic lights

### Access Transformers

Some private fields/methods will need AT access. Create `ldog_at.cfg` as needed.

### Performance Budget

OptiFine's reputation for being "glitchy and non-performant" means we have a real opportunity -- but also a responsibility. Every feature must:
- Be independently toggleable
- Have zero overhead when disabled
- Be profiled before merge
- Not increase startup time significantly

## Existing Open-Source References

| Project | License | Useful For |
|---|---|---|
| [ConnectedTexturesMod](https://github.com/Chisel-Team/ConnectedTexturesMod) | MIT | CTM implementation |
| [Dynamic Lights](https://www.curseforge.com/minecraft/mc-mods/dynamic-lights) | Open source | Dynamic lighting approach |
| [FoamFix](https://github.com/asiekierka/FoamFix) | GPL-3.0 | Rendering/memory optimizations |
| [VanillaFix](https://github.com/DimensionalDevelopment/VanillaFix) | MIT | Vanilla bug fixes |
| [BetterFPS](https://github.com/Guichaguri/BetterFPS) | MIT | Performance optimizations |
| [ShadersMod](https://github.com/karyonix/ShadersMod) | LGPL | Original shader pipeline (pre-OptiFine absorption) |

## Risks

- **Shader pack compatibility:** Modern shader packs are written against OptiFine's shader API. Full compatibility requires implementing the same uniform names, buffer bindings, and pass structure. Partial shader support that breaks popular packs may frustrate users more than no shader support.
- **Mod compatibility:** Other mods may check for OptiFine's presence or use its APIs. We should provide compatibility shims where practical.
- **Scope creep:** OptiFine has accumulated 10+ years of features. We should resist trying to match everything and instead focus on doing fewer things well.
