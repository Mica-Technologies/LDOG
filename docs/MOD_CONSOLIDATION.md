# Mod Consolidation Plan

The alto modpack currently uses 10+ separate mods for optimization, rendering fixes, and performance. LDOG's long-term vision is to consolidate as many of these as feasible into a single, well-integrated mod -- eliminating version conflicts, redundant patches, and the "which optimization mods are compatible with which" headache.

## Current Alto Optimization Mods

| Mod | Version | Function | Can LDOG Replace? |
|---|---|---|---|
| **OptiFine** | HD_U_G6_pre1 | Shaders, CTM, emissive, rendering | **Yes** (primary goal) |
| **Vintage Fix** | 0.5.1 | FoamFix successor: model dedup, blockstate compaction, dynamic loading | **Yes** |
| **Censored ASM (LoliASM)** | 5.30 | Memory: class loading, texture dedup, BakedQuad dedup | **Yes** |
| **Performant** | 1.11 | Entity/TE tick perf, pathfinding | **Partial** (rendering side yes, tick side maybe) |
| **Universal Tweaks** | 1.17.0 | Collection of vanilla fixes/tweaks | **Partial** (cherry-pick rendering-related ones) |
| **FPS Reducer** | 1.20 | Reduce FPS when AFK/unfocused | **Yes** (trivial) |
| **Clear Water** | 1.2 | Water transparency | **Yes** (trivial) |
| **Smooth Font** | 2.1.4 | TrueType font rendering | **Maybe** (low priority, niche) |
| **Spark** | (forge1122) | Profiler | **No** (keep as diagnostic tool) |
| **Lag Goggles** | 4.11 | Profiler | **No** (keep as diagnostic tool) |

---

## Consolidation Phases

### Phase C1: Quick Absorptions (During Phase 1)

These are trivial to implement and can be folded into Phase 1 work immediately.

#### FPS Reducer -> LDOG
**Current mod:** FPS Reducer 1.20
**What it does:** Reduces frame rate when the game window is unfocused or the player is AFK for a configurable duration.
**Implementation effort:** Very low (~50 lines)
**How:**
- [ ] Detect window focus via `Minecraft.getMinecraft().isGameFocused()` or LWJGL window focus callback
- [ ] Track last input time for AFK detection
- [ ] When unfocused/AFK, call `Display.sync(reducedFps)` or insert a `Thread.sleep()` in the render loop
- [ ] Config: `unfocusedFpsLimit` (default 5), `afkTimeoutSeconds` (default 300), `afkFpsLimit` (default 15)
**Risk:** None. Completely independent feature.
**Replaces:** FPS Reducer mod (can remove from modpack)

#### Clear Water -> LDOG
**Current mod:** Clear Water 1.2
**What it does:** Makes water transparent by modifying the water rendering to remove the murky overlay.
**Implementation effort:** Very low (~20 lines)
**How:**
- [ ] Mixin: `BlockLiquid` or water render path to override water color/alpha
- [ ] Config: `enableClearWater` (default true), `waterTransparency` (0.0-1.0)
**Risk:** None.
**Replaces:** Clear Water mod (can remove from modpack)

---

### Phase C2: Memory Optimizations (After Phase 2, Before Phase 3)

These require more careful implementation but are well-understood from VintageFix and LoliASM.

#### Vintage Fix -> LDOG
**Current mod:** Vintage Fix 0.5.1 (based on FoamFix by asiekierka)
**What it does:**
1. **Model deduplication** -- After baking, deduplicate identical BakedQuad and IBakedModel instances to save heap
2. **BlockState compaction** -- Replace vanilla's `ImmutableMap`-based property storage with bit-packed fields
3. **Dynamic model loading** -- Load block models on-demand instead of all at startup, unload when unused
4. **Property value interning** -- Intern frequently-repeated property value strings

**Implementation effort:** High (each sub-feature is moderate, but there are several)
**How:**
- [ ] Research VintageFix source (check license -- it's based on FoamFix GPL-3.0)
- [ ] Model dedup: Post-process `ModelManager` after baking, walk all models and deduplicate via content-hash
- [ ] BlockState compaction: Mixin `BlockStateContainer` to use array-backed storage instead of ImmutableMap
- [ ] Dynamic loading: Mixin `ModelManager` to lazy-load models, track access, evict unused
- [ ] Property interning: Mixin `AbstractProperty` to intern value strings
- [ ] Measure: heap usage before/after with a 200-mod pack
**Risk:** Moderate. These touch fundamental data structures. Must test thoroughly with all alto mods.
**Replaces:** Vintage Fix (can remove from modpack)

#### Censored ASM (LoliASM) -> LDOG
**Current mod:** Censored ASM 5.30
**What it does:**
1. **BakedQuad deduplication** -- Deduplicate vertex data arrays across identical quads
2. **Texture deduplication** -- Deduplicate identical sprite pixel data in the texture atlas
3. **Class loading optimizations** -- Reduce LaunchClassLoader overhead
4. **Canonical `IBlockState` lookups** -- Cache and canonicalize blockstate lookups
5. **NBT string interning** -- Intern NBT tag name strings

**Implementation effort:** High
**How:**
- [ ] Research LoliASM source and license
- [ ] BakedQuad dedup: Content-hash `int[]` vertex data, share references for identical arrays
- [ ] Texture dedup: Compare sprite pixel buffers after atlas stitch, share references
- [ ] Class loading: Mixin into `LaunchClassLoader` to optimize `findClass()` and reduce memory churn
- [ ] IBlockState canonicalization: Cache `getBlockState()` results in a direct-mapped cache
- [ ] NBT interning: Mixin `NBTTagCompound` to intern key strings
- [ ] Verify no overlap/conflict with VintageFix-equivalent features above
**Risk:** Moderate-high. Class loading changes are sensitive. Must test carefully.
**Replaces:** Censored ASM / LoliASM (can remove from modpack)

---

### Phase C3: Selective Absorptions (During Phase 6+)

These are larger mods where we cherry-pick specific features rather than absorbing the whole thing.

#### Performant (partial) -> LDOG
**Current mod:** Performant 1.11
**What it does:**
1. Entity tick batching and scheduling
2. Pathfinding optimization (reduced recalculation frequency)
3. TileEntity tick optimization
4. Mob spawning optimization
5. Various server-side performance fixes

**LDOG scope:** Only the **client-side rendering** optimizations. Server-side tick/pathfinding/spawning is outside LDOG's scope (we're a client rendering mod).
- [ ] Review Performant's client-side patches (if any exist beyond entity rendering)
- [ ] Our Phase 1 B1/B2/B3 (entity/TE render distance) already covers the rendering side
- [ ] If Performant has client-side-only patches we don't cover, absorb those
**Replaces:** Potentially Performant (if all its useful features are covered by LDOG + server-side alternatives)
**Risk:** Low. We're only taking rendering-related features.

#### Universal Tweaks (partial) -> LDOG
**Current mod:** Universal Tweaks 1.17.0
**What it does:** 100+ individual tweaks and fixes across all areas of the game. Categories include:
- Bug fixes (vanilla bugs)
- Performance tweaks
- Entity fixes
- Block fixes
- World fixes
- Misc tweaks

**LDOG scope:** Only rendering/performance-related tweaks.
- [ ] Review Universal Tweaks' full feature list and config
- [ ] Identify rendering-related tweaks (likely: entity rendering fixes, particle fixes, chunk rendering fixes)
- [ ] Absorb those specific tweaks
- [ ] Leave non-rendering tweaks to Universal Tweaks
**Replaces:** No -- Universal Tweaks does too many unrelated things. We cherry-pick.
**Risk:** Low. We only take what's in our scope.

#### Smooth Font -> LDOG
**Current mod:** Smooth Font 2.1.4
**What it does:** Replaces Minecraft's bitmap font renderer with TrueType font rendering via Java2D/AWT.

**LDOG scope:** Low priority. Font rendering is tangentially related to our rendering focus.
- [ ] Evaluate whether TrueType font rendering fits LDOG's scope
- [ ] If yes, implement as an optional feature module
- [ ] If no, leave Smooth Font as a separate mod
**Replaces:** Maybe Smooth Font.
**Risk:** Low if implemented as isolated feature.

---

## Consolidation Timeline

| When | What Gets Absorbed | Mods Removed from Alto |
|---|---|---|
| **Phase 1** (rendering opts) | FPS Reducer, Clear Water | 2 mods removed |
| **Phase C2** (after Phase 2) | Vintage Fix, Censored ASM | 2 mods removed |
| **Phase 6+** | Performant (partial), Universal Tweaks (partial) | 0-2 mods removed |
| **Full maturity** | OptiFine | 1 mod removed |
| **Total** | | **5-7 mods consolidated into LDOG** |

## Updated Alto Modpack (Post-LDOG Maturity)

**Removed:**
- ~~OptiFine~~ -> LDOG
- ~~Vintage Fix~~ -> LDOG
- ~~Censored ASM~~ -> LDOG
- ~~FPS Reducer~~ -> LDOG
- ~~Clear Water~~ -> LDOG

**Kept:**
- Performant (server-side tick optimizations not covered by LDOG)
- Universal Tweaks (non-rendering fixes not covered by LDOG)
- Spark (profiler)
- Lag Goggles (profiler)
- Smooth Font (maybe absorbed, maybe kept)

**Net result:** 5 optimization mods replaced by 1 integrated mod with better compatibility guarantees.
