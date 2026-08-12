# LDOG (Limitless Development Optigame)

A high-performance, open-source Minecraft 1.12.2 Forge mod designed as a replacement for OptiFine.

LDOG ships most of OptiFine's headline rendering features -- connected textures, emissive textures, HD texture support, dynamic lights, custom sky rendering, and full OptiFine-format shader pack support -- and goes beyond OptiFine with an FSR1/FSR2-style upscaling stack, TAA, an HDR + bloom pipeline, borderless windowed fullscreen, and a tabbed in-game settings GUI with one-click quality presets. It's fully open-source, built on Mixins instead of ASM patches, and can run alongside OptiFine with per-feature override modes while it matures.

## Features

Everything below is implemented and playable today unless noted otherwise. "Partial format coverage" means the feature works but doesn't yet parse every property OptiFine's resource-pack format supports -- more packs work every release.

| Feature | Status | Description |
|---|---|---|
| Rendering Optimizations | Shipped | Entity/TESR distance culling + LOD, particle culling/filters/caps, FPS reducer, weather density, fog controls, biome blend radius |
| HD Textures | Shipped | Textures larger than 16x16, plus extended-border mipmaps to stop anisotropic atlas bleed |
| Connected Textures (CTM) | Shipped, partial format coverage | Glass panes, bookshelves, etc. connecting visually |
| Emissive Textures | Shipped | Glow layers on blocks/items without light emission |
| Dynamic Lights | Shipped | Light from held torches, dropped glowstone, etc. |
| Lighting Customization | Shipped | Lightmap color temperature, fullbright, adjustable night darkness, HDR tonemapping |
| Custom Sky | Shipped, partial format coverage | Configurable sky layers, custom sun/moon |
| Better Grass / Better Snow | Shipped | Seamless grass/snow block sides |
| Natural Textures | Shipped, partial format coverage | Per-position texture rotation/flip to reduce tiling repetition |
| Random Mobs | Shipped, partial format coverage | Random entity texture variants from resource packs |
| Custom Colors | Shipped, partial format coverage | Custom grass/foliage colormaps, redstone/potion/dye color overrides |
| Smooth Font | Shipped | Antialiased TTF font rendering, custom font family/size |
| Shader Pack Support | Shipped, real-pack parity expanding | OptiFine-format packs: gbuffers MRT dispatch, shadow pass, composite/final chain, GLSL preprocessor, `#include`, `worldN` overrides. Works today for simple packs; BSL-class packs are the current parity target |
| OptiFine Coexistence | Shipped | Runtime OptiFine detection with per-feature AUTO / LDOG-override / OptiFine-override modes |

### Beyond OptiFine

These aren't things OptiFine offers -- they're LDOG's own answer to modern upscaling and image quality:

| Feature | Status | Description |
|---|---|---|
| FSR Upscaling Stack | Shipped | FSR1 spatial upscaling + RCAS sharpening, plus an FSR2-style temporal upscaler with quality presets and auto-scale |
| TAA | Shipped | Temporal anti-aliasing with jittered sampling and entity motion vectors |
| Anisotropic Filtering + MSAA + FXAA | Shipped | Standard AA/filtering stack, independently toggleable |
| HDR Pipeline + Bloom | Shipped | ACES filmic tonemapping and bloom, running before the AA/upscale chain |
| Borderless Windowed Fullscreen | Shipped | Resize-to-desktop borderless mode as an alternative to exclusive fullscreen |
| Tabbed Settings GUI | Shipped | In-game settings screen (General / Rendering / Visual / Features / Shaders / UI tabs) with global quality presets |

## Player Information

- **Supported Minecraft Version:** 1.12.2
- **Mod Loader:** Forge
- **Client-side only:** Safe to install client-only with no server dependency
- **OptiFine Compatible:** LDOG auto-detects OptiFine and disables overlapping features to avoid conflicts

### Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) for Minecraft 1.12.2
2. Download the latest LDOG release from the [Releases](../../releases) page
3. Place the `.jar` file in your `.minecraft/mods/` folder
4. Launch Minecraft with the Forge profile

## Developer Information

### Prerequisites

- **IDE:** IntelliJ IDEA (recommended, latest version)
- **JDK:** Java 17 works for local dev; Java 21 is recommended ([Azul Zulu Community](https://www.azul.com/downloads/)). RetroFuturaGradle deprecates older Gradle-JVM versions, and CI already runs on JDK 21.

### Getting Started

1. Clone the repository and open in IntelliJ IDEA
2. Install JDK 17 or 21 (Azul Zulu Community) if you haven't already
3. Import the Gradle project
4. Set up the workspace:
   ```bash
   JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew setupDecompWorkspace
   ```

### Build Commands

```bash
# Setup workspace (required first time, or after clean)
./gradlew setupDecompWorkspace

# Build the mod
./gradlew build

# Run Minecraft client in dev
./gradlew runClient

# Run Minecraft server in dev
./gradlew runServer

# Clean build artifacts
./gradlew clean

# Run tests (JUnit 5)
./gradlew test
```

### Architecture

LDOG uses **Mixins** (via MixinBooter) to transform vanilla rendering code. This approach is more maintainable and less brittle than raw ASM. Every feature is independently toggleable via `LDOGConfig`.

For detailed architecture documentation, see the `docs/` directory:
- `docs/ARCHITECTURE.md` -- Actual architecture of each feature module (module map for contributors)
- `docs/CONVENTIONS.md` -- Code/mixin/GUI conventions established during development

(Development plans and session logs live in the gitignored `docs/agent-plans/` folder and are not part of the repository.)

### Source Layout

```
src/main/java/com/limitlessdev/ldog/
├── LDOGMod.java           # @Mod entry point
├── config/                # Forge @Config-based configuration + presets
├── proxy/                 # Client/server proxy pattern
├── compat/                # OptiFine detection + per-feature override modes
├── asm/                   # FML core plugin (early mixin loading, pre-Display config reads)
├── gui/                   # Tabbed in-game settings GUI, shader pack pickers
├── texture/                # HD textures, anisotropic filtering, extended-border mipmaps
├── mixin/                 # ~150 Mixin transformations for vanilla rendering (three configs)
└── render/                 # Feature implementations, one package per feature:
    ├── pipeline/           # Post-process pipeline: render targets, upscalers, TAA, HDR/bloom
    ├── shaderpack/          # OptiFine-format shader pack loading + gbuffer/shadow/composite dispatch
    ├── ctm/                 # Connected textures
    ├── emissive/            # Emissive texture overlays
    ├── dynamiclights/       # Dynamic light sources
    ├── sky/                 # Custom sky layers
    ├── bettergrass/         # Better Grass / Better Snow
    ├── biome/               # Biome color blend radius
    ├── color/               # Custom colors (grass/foliage maps, redstone, potions, dyes)
    ├── display/             # Borderless windowed fullscreen
    ├── font/                # Smooth/HD/TTF font rendering
    ├── fxaa/, msaa/         # Anti-aliasing
    ├── natural/             # Natural (rotated/flipped) textures
    ├── particles/           # Particle filters + spawn caps
    └── randommobs/          # Random entity texture variants
```

## Credits

- Build system: [GregTechCEu Buildscripts](https://github.com/GregTechCEu/Buildscripts) (RetroFuturaGradle)
- Mixin framework: [MixinBooter](https://github.com/CleanroomMC/MixinBooter)

## License

This project is licensed under the LGPL 2.1. See [LICENSE.txt](LICENSE.txt) for details.
