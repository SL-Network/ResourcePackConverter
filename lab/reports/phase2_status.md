# Phase 2 status — 26.1 (pack_format **84.0**)

**Date:** 2026-11-13  
**Plan:** `.agent/milestone-26_3-plan.md` Phase 2  
**Evidence:** `knowledge/renames/1.21.11-26.1.json`, `knowledge/vanilla/1.21.11` vs `knowledge/vanilla/26.1`, `knowledge/pack_formats.json` → 84.0

## Gate results

| Command | Exit | Notes |
|---------|------|-------|
| `./harness/build.sh` | 0 | |
| `./harness/run_l0.sh` | 0 | includes `fixtures/synthetic/entity_layout_26_1` → pack_format **84.0** |
| `./harness/run_tsf.sh 1.21.11` | 0 | milestone 1 gate unchanged |
| smoke `run_tsf.sh 26.1` | 0 | score + assert_tsf ok; TSF also had `armorstand/wood` → `armorstand/armorstand` |

## Audit (breaking vs N/A)

| Candidate | Verdict | Action |
|-----------|---------|--------|
| Entity texture flat → per-entity folder (e.g. `bat.png` → `bat/bat.png`) | **implemented** | `EntityTextureLayoutConverter26_1` |
| Variant renames (`cat/tabby` → `cat/cat_tabby`, `pig/cold_pig` → `pig/pig_cold`, rabbits, cows, pandas, …) | **implemented** | same converter (84 paths from vanilla tree diff) |
| Specials (`armorstand/wood` → `armorstand/armorstand`, `turtle/big_sea_turtle` → `turtle/turtle`, spectral/tipped arrows) | **implemented** | same |
| `projectiles/arrow.png` | **N/A** | still present at same path in 26.1 |
| `cat/cat_collar.png`, `cat/ocelot.png` | **N/A** | same path in both trees |
| Baby `*_baby.png` (+ equipment `*_baby.json`) | **N/A** | additive in jar; do **not** invent by copying adult textures |
| Villager ↔ zombie_villager profession “swaps” in rename JSON | **N/A** | same-hash collisions, not real renames |
| Conduit wind ↔ wind_vertical / blockstate waxed swaps in rename JSON | **N/A** | hash collisions |
| Shader path “moves” in rename JSON | **N/A** | same-hash collisions between unrelated shader files |
| Lightmap / shader uniform renames (`AmbientLightFactor`, `BossOverlayWorldDarkeningFactor`, core/item vs core/entity) | **deferred** | content edits inside shaders; TSF ships no `shaders/`; needed only for shader-shipping packs |
| Removed core rendertype shaders (entity_alpha, entity_decal, item_entity_translucent_cull, translucent_moving_block) | **deferred** | same — shader packs only |
| Item model `transformation` fields | **N/A** | additive schema; no path rewrite |

## Converter

- **Class:** `com.agentdid127.resourcepack.forwards.impl.v26_1.textures.EntityTextureLayoutConverter26_1`
- **Register:** `ForwardsPackConverter` after `ItemsAtlasConverter1_21_11`
- **`shouldConvert`:** `from ≤ 1.21.11` and `to ≥ 26.1`
- **Behavior:** `FileUtil.moveIfExists` for each of 84 texture paths (+ sibling `.mcmeta` if present)
- **NameConverter:** no new mappings (entity layout is hop-scoped moves, not block/item JSON maps)

## Fixture / score

- L0: `fixtures/synthetic/entity_layout_26_1` (`1.16.5` → `26.1`) asserts moved destinations + untouched `projectiles/arrow.png`
- Score: `check_entity_layout_26_1` errors if sampled old paths remain without new destinations when `--to` is 26.x+

## What’s left for Phase 3 (26.2 / 88.0)

- Beds / signs / hanging signs → block models; `beds` / `signs` atlas removal
- `minecraft:end_cube` model type where end patterns break
- Shader consolidation for shader-shipping packs
- Do **not** invent UVs — emit review findings when geometry is ambiguous
