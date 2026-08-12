# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Guidelines

- **Create commits** when work reaches a logical checkpoint -- keep them descriptive and well-organized.
- **Never push** to any remote. The user will review and push manually.
- Use conventional, descriptive commit messages that explain *why*, not just *what*.
- Group related changes into single commits; don't lump unrelated work together.

## Build Commands

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

**Requirements:** Java 17 works for local dev, but Java 21 is now recommended -- RetroFuturaGradle deprecates older Gradle-JVM versions and CI already runs on JDK 21 (mod code still targets JVM 8 via Jabel regardless of which JDK runs Gradle). Heap is set to `-Xmx3G` in `gradle.properties` for decompilation.

**JDK Location:** The JDK is managed via IntelliJ and located at `C:\Users\<username>\.jdks\azul-17.0.18` (or an equivalent `azul-21.x` install). When running Gradle from the CLI, set `JAVA_HOME` to this path:
```bash
JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build
```

## Architecture Overview

This is **LDOG** (Limitless Development Optigame), a **Minecraft 1.12.2 Forge mod** (mod ID: `ldog`) that aims to be a high-performance, open-source replacement for OptiFine. The build system is GregTechCEu Buildscripts (RetroFuturaGradle wrapper).

### Project Goal

Replace OptiFine's functionality with a well-architected, performant, open-source alternative. Status of the major feature areas (see `docs/ARCHITECTURE.md` for how each is implemented):

| Feature Area | Status | Description |
|---|---|---|
| **Rendering Optimizations** | Shipped | Entity/TESR distance culling + LOD, particle culling/filters/caps, FPS reducer, weather density, fog controls, biome blend radius |
| **HD Textures** | Shipped | Textures larger than 16x16, plus extended-border mipmaps |
| **Connected Textures (CTM)** | Shipped, partial format coverage | Glass panes, bookshelves, etc. connecting visually |
| **Emissive Textures** | Shipped | Glow layers on blocks/items without light emission |
| **Dynamic Lights** | Shipped | Light from held torches, dropped glowstone, etc. |
| **Lighting Customization** | Shipped | Color temperature, fullbright, night darkness, HDR tonemapping |
| **Custom Sky** | Shipped, partial format coverage | Configurable sky rendering, custom sun/moon |
| **Better Grass/Snow, Natural Textures, Random Mobs, Custom Colors** | Shipped, partial format coverage | OptiFine-format resource pack features |
| **Shader Support** | Shipped, real-pack parity expanding | OptiFine-format packs: gbuffers MRT dispatch, shadow pass, composite/final chain, GLSL preprocessor, `#include`, `worldN` overrides |
| **Beyond OptiFine** | Shipped | FSR1/FSR2-style upscaling + RCAS, TAA, HDR pipeline + bloom, borderless windowed, tabbed settings GUI with presets |

### Source Layout

```
src/main/java/com/limitlessdev/ldog/
+-- LDOGMod.java           # @Mod entry point
+-- config/                 # Forge @Config-based configuration + presets
|   +-- LDOGConfig.java
|   +-- LDOGPreset.java
+-- proxy/                  # Client/server proxy pattern
|   +-- ClientProxy.java
|   +-- CommonProxy.java
+-- compat/                 # OptiFine detection + per-feature override modes
|   +-- OptiFineCompat.java
+-- asm/                    # FML core plugin (early mixin loading)
|   +-- LDOGCorePlugin.java
+-- gui/                    # Tabbed in-game settings GUI, shader pack pickers
+-- texture/                 # HD textures, anisotropic filtering, extended-border mipmaps
+-- mixin/                  # ~150 Mixin transformations (three configs -- see docs/CONVENTIONS.md)
+-- render/                  # Feature implementations, one package per feature
    +-- pipeline/            # Post-process pipeline: render targets, upscalers, TAA, HDR/bloom
    +-- shaderpack/           # OptiFine-format shader pack loading + gbuffer/shadow/composite dispatch
    +-- ctm/, emissive/, dynamiclights/, sky/, bettergrass/, biome/,
    |   color/, display/, font/, fxaa/, msaa/, natural/, particles/, randommobs/
```

### Key Design Decisions

- **Mixins over ASM:** Prefer Mixins (via MixinBooter) for transforming vanilla code. Mixins are more maintainable and less brittle than raw ASM. Only fall back to ASM/core mods if Mixins cannot reach the target.
- **Client-side only:** Almost all features are client-rendering. The mod should be safe to install client-only with no server dependency.
- **Config-driven:** Every feature should be independently toggleable via `LDOGConfig`.
- **OptiFine coexistence:** LDOG detects OptiFine at runtime via `OptiFineCompat`. When OptiFine is present, LDOG auto-disables overlapping features (CTM, emissive, shaders, etc.) to avoid conflicts, but keeps non-conflicting render optimizations active. This lets users run both mods together -- OptiFine handles shaders while LDOG handles what it does better. As LDOG matures, users can drop OptiFine entirely.
- **No OptiFine compile dependency:** LDOG never imports OptiFine classes. Detection is purely reflective (`Class.forName`).

### Build Configuration

- **Mod-specific settings** go in `buildscript.properties` (mod name, ID, group, features)
- **Gradle-only settings** go in `gradle.properties` (JVM args, logging)
- **Dependencies** go in `dependencies.gradle`
- **Custom Maven repos** go in `repositories.gradle`
- **Do NOT edit `build.gradle`** -- it is auto-generated by GregTechCEu Buildscripts and will be overwritten

### Version

Version is derived from Git tags. No manual version setting needed (see `modVersion` in `buildscript.properties`).

## Documentation

`docs/` holds tracked technical documentation about the code:
- `docs/ARCHITECTURE.md` -- Architecture of each feature module
- `docs/CONVENTIONS.md` -- Code/mixin/GUI conventions established during development

`docs/agent-plans/` is a **gitignored** folder for plans and session logs worked on with Claude/agents (local-only, never committed):
- `docs/agent-plans/MASTER_APP_PLAN.md` -- Single source of truth for roadmap, phase status, research notes, backlog, gotchas, and resume prompt
- `docs/agent-plans/PHASE_9B_VALIDATION.md` -- User-driven upscaler quality validation protocol
- `docs/agent-plans/SESSION_*.md` -- Per-session work logs
