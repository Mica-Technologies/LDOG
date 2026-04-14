# LDOG (Limitless Development Optigame)

A high-performance, open-source Minecraft 1.12.2 Forge mod designed as a replacement for OptiFine.

LDOG aims to provide all the key rendering enhancements that OptiFine offers -- connected textures, emissive textures, HD texture support, dynamic lights, custom sky rendering, and shader support -- while being fully open-source, well-architected, and compatible with the broader modding ecosystem.

## Features

| Feature | Status | Description |
|---|---|---|
| Rendering Optimizations | In Progress | Chunk culling, entity batching, particle limits |
| Connected Textures (CTM) | In Progress | Glass panes, bookshelves, etc. connecting visually |
| Emissive Textures | In Progress | Glow layers on blocks/items without light emission |
| Dynamic Lights | Planned | Light from held torches, dropped glowstone, etc. |
| HD Textures | Planned | Support for textures larger than 16x16 |
| Custom Sky | Planned | Configurable sky rendering, custom sun/moon |
| Shader Support | Stretch | GLSL shader pipeline for post-processing and lighting |

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
- **JDK:** Java 17 ([Azul Zulu Community](https://www.azul.com/downloads/?version=java-17-lts&package=jdk) recommended)

### Getting Started

1. Clone the repository and open in IntelliJ IDEA
2. Install JDK 17 (Azul Zulu Community) if you haven't already
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
- `docs/ATTACK_PLAN.md` -- Phased development plan with checklists
- `docs/FEASIBILITY.md` -- Feasibility analysis of replicating OptiFine features
- `docs/ARCHITECTURE.md` -- Planned architecture for each feature module
- `docs/PHASE1_RESEARCH.md` -- Rendering optimization research with vanilla code analysis
- `docs/MOD_CONSOLIDATION.md` -- Plan for absorbing/replacing other optimization mods

### Source Layout

```
src/main/java/com/limitlessdev/ldog/
├── LDOGMod.java          # @Mod entry point
├── config/                # Forge @Config-based configuration
├── proxy/                 # Client/server proxy pattern
├── compat/                # Mod compatibility (OptiFine detection)
└── mixin/                 # Mixin transformations for vanilla rendering
```

## Credits

- Build system: [GregTechCEu Buildscripts](https://github.com/GregTechCEu/Buildscripts) (RetroFuturaGradle)
- Mixin framework: [MixinBooter](https://github.com/CleanroomMC/MixinBooter)

## License

This project is licensed under the LGPL 2.1. See [LICENSE.txt](LICENSE.txt) for details.
