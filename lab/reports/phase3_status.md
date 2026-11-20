# Phase 3 status — 26.2 (pack_format **88.0**)

**Date:** 2026-11-13  
**Plan:** `.agent/milestone-26_3-plan.md` Phase 3  
**Evidence:** `knowledge/renames/26.1-26.2.json`, `knowledge/vanilla/26.1` vs `26.2`, `knowledge/pack_formats.json` → 88.0, wiki pack format 88.0 / Mojang Slicer v1.2.1

## Gate results

| Command | Exit | Notes |
|---------|------|-------|
| `./harness/build.sh` | 0 | |
| `./harness/run_l0.sh` | 0 | includes `fixtures/synthetic/beds_signs_26_2` → pack_format **88.0** |
| `./harness/run_tsf.sh 1.21.11` | 0 | milestone 1 gate unchanged |
| smoke `run_tsf.sh 26.2` | 0 | score + assert_tsf ok; soft gap: TSF `entity/signs/oak.png` → review (needs Slicer) |

## Audit (breaking vs N/A)

| Candidate | Verdict | Action |
|-----------|---------|--------|
| `atlases/beds.json` / `atlases/signs.json` removed | **implemented** | `BedsSignsConverter26_2` deletes if present |
| Entity bed textures → many `block/<color>_bed_*` faces | **review** | UV layout ambiguous; emit review; Mojang Slicer v1.2.1 is the oracle — do not invent UVs |
| Entity standing/hanging sign textures → `block/<wood>_sign` / `_hanging_sign` | **review** | Path destinations known but UV/size change (64×32→32×32); review finding only |
| Obsolete particle-only `models/block/*_sign.json` / `bed.json` | **review** | Detect + finding; do not invent `*_sign_rot_*` / bed head-foot geometry |
| `block/purpur_pillar.png` → `purpur_pillar_side.png` | **implemented** | `PillarSideConverter26_2` (same-hash jar evidence) + model texture ref rewrite |
| `block/quartz_pillar.png` → `quartz_pillar_side.png` | **implemented** | same |
| `minecraft:end_cube` model type | **N/A** | Additive special type; zero usages in vanilla 26.1/26.2 jar assets; no path migration |
| Text shaders `rendertype_text*` → `core/text` (+ `text_background`); moving block → `core/block` | **review / deferred** | Detect legacy files → review finding; content port non-mechanical. **TSF N/A** (no `shaders/`) |
| Waxed-copper / skull same-hash “swaps” in rename JSON | **N/A** | Hash collisions, not real renames |
| Additive 26.2 content (sulfur/cinnabar, etc.) | **N/A** | No converter |

## Converters

| Class | Register | `shouldConvert` |
|-------|----------|-----------------|
| `…v26_2.other.BedsSignsConverter26_2` | after `EntityTextureLayoutConverter26_1` | from ≤26.1 → to ≥26.2 |
| `…v26_2.textures.PillarSideConverter26_2` | after beds/signs | same |
| `…v26_2.other.ShaderTextConsolidationConverter26_2` | after pillars | same |

**Behavior summary:**
- Remove dead beds/signs atlases; write `conversion_review.json` for entity bed/sign PNGs, obsolete sign/bed models, and legacy text shaders
- Move pillar PNGs (+ `.mcmeta`) and rewrite exact `block/purpur_pillar` / `quartz_pillar` texture refs to `*_side`
- **NameConverter:** no new mappings (hop-scoped)

## Fixture / score

- L0: `fixtures/synthetic/beds_signs_26_2` (`1.16.5` → `26.2`) expects pillar sides + `conversion_review.json`; atlases must be gone
- Score: `check_beds_signs_pillars_26_2` errors on leftover atlases / unmoved pillars; **review** (non-failing) for leftover entity bed/sign textures; merges other `conversion_review.json` findings

## TSF impact (soft gaps)

- TSF ships `textures/entity/signs/oak.png` only — converted pack keeps it and emits `sign_entity_texture_needs_slicer` review; theatrical hosts / atlases / equipment asserts still green
- No TSF beds, pillars, or shaders → those cliffs N/A for this pack

## What’s left for Phase 4 (26.3 / 97.1)

- Trim palettes: `textures/trims/color_palettes/` → `textures/palettes/trim/` (+ base palette path)
- Palette `.mcmeta` `base_palette` requirements
- Trim `override_armor_assets` → equipment `trim_palette_replacements`
- Other `26.2→26.3` path/schema changes from rename JSON / vanilla diff
