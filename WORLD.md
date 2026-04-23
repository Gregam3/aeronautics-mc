# World Rendering & Map Design

**Version:** 0.2.0
**Last updated:** 2026-04-23
**Scope:** How the overworld looks at world-creation time — the interaction
between Tectonic (terrain), Regions Unexplored + TerraBlender (biomes), and
our Voronoi tier-swap biome source (`caero_rings`).

Per-layer plans:
- Voronoi tiering — [`glue/ring-biomes/PLAN.md`](./glue/ring-biomes/PLAN.md)
- Difficulty pillars — [`heuristics.md`](./heuristics.md)

---

## 1. Current mod stack (verified working 2026-04-23)

| Layer | Mod | Version | What it decides |
|---|---|---|---|
| Terrain shape | **Tectonic** | 3.0.22 | Elevation, erosion, ridges, cliffs — dramatic mountains and canyons. No biome decisions. |
| Biome injection | **Regions Unexplored** + **TerraBlender** | RU 0.5.9, TB 4.1.0.8 | 70+ new biomes injected into vanilla multi_noise via TerraBlender API. |
| Tier re-skin | **`caero_rings` (Glue #5)** | 0.1.0 | Wraps multi_noise biome source. Swaps biomes to match Voronoi-assigned tier. |
| Dependency | **Lithostitched** | 1.7.0 | Required by Tectonic for density function modifiers. |
| Dependency | **Kotlin For Forge** | 5.11.0 | Required by caero_rings (Kotlin mod). |

### Why Regions Unexplored over Terralith

Terralith 2.5.8 ships its own `noise_settings/overworld.json` and full
`noise_router/` density functions, which override Tectonic's terrain
amplification. Result: max height 89 (vanilla-scale) despite Tectonic being
loaded. Multiple GitHub issues confirm this conflict (#90, #171, #376).

Regions Unexplored uses TerraBlender, which injects biomes at runtime without
overriding any noise settings or density functions. Tectonic's terrain shaping
applies cleanly. Verified: max height 128+ in headless testing with the new
stack.

Terralith 2.6.x has switched to TerraBlender but only targets MC 1.21.5+
(NeoForge 26.1). Not available for our 1.21.1 target.

---

## 2. How the layers compose

```
Tectonic:       Replaces density functions → dramatic terrain shape
                (via Lithostitched wrap_density_function modifiers)
                    ↓
TerraBlender:   Injects RU biomes into vanilla multi_noise parameter space
                (at runtime, no datapack overrides)
                    ↓
multi_noise:    Maps climate parameters → biome ID
                    ↓
caero_rings:    Wraps multi_noise. Checks Voronoi cell tier.
                If biome tier ≠ cell tier → substitute with
                temperature-matched biome from correct tier.
                Oceans/rivers/caves pass through untouched.
```

Each layer operates at a different stage. They compose without conflict
because:
- Tectonic modifies WHERE land is and how tall (density functions)
- TerraBlender modifies WHICH biomes exist (parameter space injection)
- caero_rings modifies which biome WINS at each location (biome source wrapper)

---

## 3. Voronoi tier layout

From `glue/ring-biomes/src/main/resources/data/minecraft/dimension/overworld.json`:

```
1 easy seed:    (0, 0)
6 medium seeds: hexagonal ring at r=2400
8 hard seeds:   rough ring at r≈4000 (hand-placed)
medium_min_radius: 1500  (guaranteed easy core)
hard_min_radius:   1500
```

### Tier biome counts (as of 2026-04-23)
- **Easy:** 36 biomes (gentle forests, plains, meadows, jungles)
- **Medium:** 37 biomes (taigas, savannas, swamps, mountains, deserts)
- **Hard:** 18 biomes (badlands, ice spikes, peaks, alien/extreme RU biomes)
- **Hard substitution pool:** 9 biomes (palette-based hardness only; terrain-dependent biomes like spires/cliffs excluded)
- **Untagged (pass-through):** oceans, rivers, beaches, caves, deep dark

### Substitution logic
When a biome needs to be swapped to match its cell's tier:
1. Gather candidates within ±0.5 temperature of the natural biome
2. If pool is empty, use single closest-temperature candidate
3. Deterministic `hashCell(x, z)` picks from pool for variety
4. Hard tier uses `tier_hard_common` (not full `tier_hard`) to prevent
   terrain-dependent biomes flooding the outer ring

---

## 4. What's working (verified 2026-04-23)

- [x] Tectonic terrain amplification (max height 128+ confirmed)
- [x] Regions Unexplored biomes appearing in world
- [x] Voronoi tier assignment (easy near spawn, medium/hard further out)
- [x] Minimum radius floors (no medium inside 1500 blocks)
- [x] Temperature-matched substitution with variety
- [x] Biome label HUD (action bar: "Biome Name · Easy/Medium/Hard")
- [x] Oceans/rivers pass through untouched
- [x] Hard pool rebalanced (6 hot/dry, 2 cold, 1 neutral)

---

## 5. What's next

The world generation foundation is complete. Next steps are about making
the tiers feel mechanically different — not just different biomes, but
different gameplay rules:

- [ ] **Mob difficulty scaling per tier.** Harder mobs / more spawns in
      medium and hard rings. Could be vanilla gamerule tweaks, mob-modifier
      mod, or a small glue mod that buffs mob attributes by tier.
- [ ] **Loot scaling per tier.** Better loot tables in harder tiers.
      Structures in hard ring drop rare materials.
- [ ] **Resource distribution.** Certain ores or resources only available
      in medium/hard tiers, incentivizing exploration beyond the easy ring.
- [ ] **Glue Mod #1 (Numismatics → OPAC bridge)** still ships before
      server provisioning. Ring biomes are a parallel workstream.
- [ ] **Server provisioning** gated on glue mods being buildable and
      passing gametests.

---

## 6. Offline renderer

```
cd glue/ring-biomes
python3 tools/render_map.py
```

Outputs `renders/tier_voronoi.png` — Voronoi cell tier map at 1px=10 blocks.
Validates seed placement and tier geometry without booting the game.

---

## 7. Deploy to PrismLauncher

```
cd glue/ring-biomes
./gradlew build
cp build/libs/caero_rings-0.1.0.jar \
   ~/.local/share/PrismLauncher/instances/1.21.1/minecraft/mods/caero_rings.jar
```

Required mods in Prism `mods/` folder:
- `caero_rings.jar`
- `regions_unexplored-neoforge-1.21.1-0.5.9.jar`
- `TerraBlender-neoforge-1.21.1-4.1.0.8.jar`
- `tectonic-3.0.22-neoforge-21.1.jar`
- `lithostitched-1.7.0-neoforge-21.1.jar`
- `kotlinforforge-5.11.0-all.jar`
- `YungsApi-1.21.1-NeoForge-5.1.6.jar`
