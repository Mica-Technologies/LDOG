# LDOG Architecture

## Module Overview

LDOG is organized into feature modules, each independently toggleable via `LDOGConfig`. All rendering features are client-side only.

```
com.limitlessdev.ldog/
+-- LDOGMod.java              # @Mod entry, lifecycle
+-- config/
|   +-- LDOGConfig.java        # Forge @Config, feature toggles
+-- proxy/
|   +-- CommonProxy.java       # Server-safe initialization
|   +-- ClientProxy.java       # Client-only feature registration
+-- mixin/                     # All Mixins (required by buildscript config)
|   +-- render/                # Rendering pipeline mixins
|   +-- texture/               # Texture loading/stitching mixins
|   +-- world/                 # Client world mixins (dynamic lights)
+-- render/                    # Rendering feature implementations
|   +-- ctm/                   # Connected textures
|   +-- emissive/              # Emissive texture rendering
|   +-- dynamiclights/         # Dynamic light sources
|   +-- sky/                   # Custom sky rendering
|   +-- optimization/          # Chunk/entity render optimizations
+-- texture/                   # Texture processing
|   +-- hd/                    # HD texture support
|   +-- atlas/                 # Custom texture atlas management
+-- shader/ (stretch)          # GLSL shader pipeline
|   +-- pipeline/              # Render pass management
|   +-- uniform/               # Shader uniform providers
|   +-- program/               # Shader program compilation/linking
+-- resource/                  # Resource pack feature loading
|   +-- properties/            # .properties file parsers (OptiFine format)
+-- util/                      # Shared utilities
```

## Feature Module Pattern

Each feature module follows the same pattern:

1. **Config toggle** in `LDOGConfig` (e.g., `enableConnectedTextures`)
2. **Mixin(s)** in `mixin/` that hook into vanilla code, gated by config check
3. **Implementation** in the feature package (e.g., `render/ctm/`)
4. **Registration** in `ClientProxy.preInit()` or via `@SubscribeEvent`

Example flow for Connected Textures:
```
LDOGConfig.enableConnectedTextures == true
  -> MixinBlockModelRenderer intercepts bakeQuads()
  -> CTMBakedModel wraps the original model
  -> CTMLogic checks adjacent blocks and selects connected texture variant
```

## Mixin Guidelines

- All Mixins go in `com.limitlessdev.ldog.mixin` (enforced by buildscript)
- Every Mixin injection must check its config toggle before doing work
- Use `@Inject` with `cancellable = true` sparingly -- prefer `@Redirect` or `@ModifyVariable` when possible
- Document the vanilla method being targeted and why

## Resource Pack Compatibility

LDOG will read OptiFine-format resource pack properties where practical:
- `assets/minecraft/optifine/ctm/` -- Connected texture definitions
- `assets/minecraft/optifine/emissive.properties` -- Emissive texture suffix
- `assets/minecraft/optifine/sky/` -- Custom sky properties
- `assets/minecraft/optifine/color.properties` -- Custom colors

This allows existing OptiFine-compatible resource packs to work with LDOG without modification.
