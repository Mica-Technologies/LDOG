# Phase 9b: Upscaler Quality Validation Protocol

User-driven test plan for the upscaler chain shipped in Phase 9a. Used to
catch regressions across resource packs, confirm preset claims hold up,
and document the artifact profile per algorithm so future tuning has
ground truth to compare against.

Not blocking on any code work — fill out the metric tables in §4 once and
treat them as the baseline. Re-run after material changes to FSR1EASU,
FSR1Quality, RCAS, the binding hook, or the auto-scale ladder.

---

## 1. Why this exists

Spatial upscalers behave very differently across content. A pack with hand-
painted textures (Faithful 32x) reads very differently under FSR1 than a
realism pack (Stratum 256x). Without a fixed protocol we end up tuning by
"vibes," and quality regressions slip through.

The deeper story is in `docs/MASTER_APP_PLAN.md` §9. This doc is the
operational checklist.

---

## 2. Target resource packs (already in `run/resourcepacks/`)

| Pack | Style | What it stresses |
|---|---|---|
| `default-1-12` (vanilla 16x) | Hand-painted, low-res | edge stability on chunky pixels; FSR1's sharpener can over-bias |
| `Faithful 32x - 1.12.2` | Hand-painted, 2x vanilla | high-contrast edges + CTM glass; ringing exposure |
| `Affinity-HD-Bundle-x256` | Realism, 256x | sub-pixel detail loss at scaled resolutions |
| `Stratum 256x (1.12.2)` | Realism, 256x | similar to Affinity; cross-check |
| `alto-resource-pack` | Modpack mix | real-world worst case (mixed resolutions in one atlas) |

Run each test row against all 5 packs unless flagged otherwise.

---

## 3. Reference scenes

Build each scene from a fresh creative world to keep results reproducible.

### S1 — Clarity / UI

Spawn in a flat creative world. Stand still, look at the horizon. Open chat
("/" then Esc), open the inventory (E), tab to the Recipe Book overlay.
Goal: verify text and item icons stay native-resolution regardless of upscaler.

### S2 — Foliage stress

Teleport to a dense forest biome (`/tp ~ ~ ~ -180 0`). Stand at canopy edge,
face into the densest leaves. Goal: see how each upscaler handles alpha-test
foliage edges.

### S3 — Motion (entity)

Spawn ~10 sheep in a line via `/summon sheep ~ ~ ~5` repeated. Stand 8 blocks
away. Spin in place at moderate speed (W+mouse to maintain rotation rate
of ~30°/s). Goal: confirm reactive mask kills entity ghost trails as
designed.

### S4 — UI-adjacent thin geometry

Build a wall of glass panes + iron fence segments + redstone wires + signs
on a flat color background. Camera at 10 blocks, slow strafe. Goal: catch
haloing and edge crawl on the thinnest one-pixel-wide world geometry.

### S5 — Lifecycle reset

While in scene S1, hit F3+T to reload resources. Walk to a different
dimension via nether portal. Alt-tab out and back. Goal: confirm pipeline
state survives — no black frames, no FBO leaks, no permanent quality
regression after a transition.

---

## 4. Capture protocol

Per row, capture:
- 3 still screenshots at matched camera transform (`F2`).
- 1 ~10-second clip at the same camera path (any screen recorder; FRAPS,
  OBS local-disk, etc.).

Render scales to sweep: `1.00`, `0.85`, `0.75`, `0.67`, `0.50`.
Upscalers to sweep: `Bilinear`, `FSR1`, `FSR1-Quality`.
RCAS off / on for each (0.4 strength = default).

Naming convention so future-you can find them:
`P9B_<scene>_<scale>_<upscaler>_<rcas>_<run>.png`
Example: `P9B_S2_0.75_FSR1-Quality_on_run01.png`.

---

## 5. Scoring rubric

Fill one table per pack. Each cell is the score from a fixed visual
inspection. Numeric scale 0-3, where:

- `0` = none / not visible
- `1` = mild / only noticeable side-by-side
- `2` = noticeable / detracts from the scene
- `3` = severe / breaks immersion

Five metrics:

| Metric | Definition |
|---|---|
| Haloing | bright fringe along high-contrast edges (RCAS overdrive symptom) |
| Edge crawl | sub-pixel shimmer on diagonal edges during motion |
| Foliage shimmer | leaf-edge breakup on alpha-tested geometry |
| Text/UI blur | confirmation that HUD stays native; should be 0 |
| FPS delta | self-reported % vs the Native (1.00 + Bilinear + no-RCAS) baseline |

### Example table (one per pack)

```
Pack: default-1-12

| Scene | Scale | Upscaler     | RCAS | Halo | EdgeCrawl | Foliage | UIBlur | FPS%
| S1    | 1.00  | Bilinear     | off  |   0  |     0     |    0    |   0    |  100
| S1    | 0.75  | FSR1         | off  |      |           |         |        |
| S1    | 0.75  | FSR1-Quality | off  |      |           |         |        |
| ...   | ...   | ...          | ...  |      |           |         |        |
```

(Fill in numbers during the session.)

---

## 6. Acceptance criteria

The shipped Phase 9a defaults pass when, across the 5 packs:

- **Native (1.00 + Bilinear)**: every metric is 0. If anything is non-zero
  here, the binding hook has a bug — investigate before doing anything else.
- **Quality preset (0.75 + FSR1-Quality + RCAS off)**: median halo ≤ 1,
  median edge crawl ≤ 1, median foliage shimmer ≤ 1, UI blur = 0.
- **Performance preset (0.50 + FSR1 + RCAS off)**: blur tolerated, but
  halo ≤ 2, foliage shimmer ≤ 2, UI blur still = 0.
- **FPS delta**: 0.85 scale yields ≥ +5% FPS, 0.75 ≥ +15%, 0.50 ≥ +35%
  on a mid-range GPU (GTX 1660 / RX 5500 class) at MC render distance 12.

If any threshold fails, log it in §7 and decide between (a) re-tune
defaults, (b) re-shape the preset, or (c) ship as-is with a documented
caveat in the GUI tooltip.

---

## 7. Findings log

Append-only. Each entry: date, pack, scene, the specific metric that
failed, and the decision.

```
[YYYY-MM-DD] <pack> <scene> <scale> <upscaler> <rcas>
  metric = score (threshold = X)
  decision: <re-tune | re-shape preset | ship-with-caveat | not-a-bug>
  notes: ...
```

(Empty until first run.)

---

## 8. Regression triggers

Re-run this protocol after any of:

- Changing the GLSL kernel in `FSR1EASUPass`, `FSR1QualityPass`, or `RCASSharpenPass`.
- Changing the upscaler-preset table.
- Changing the auto-scale ladder thresholds or step distances.
- Touching the binding mixin's clear / viewport / blit-back logic.
- Replacing or wrapping `RenderTargetManager`'s scene target.
- Once when HDR ships (will alter the input distribution).
- Once when FSR2 ships (separate test plan since it's a different pipeline).
