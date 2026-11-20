# Phase 4 status — 26.3 (pack_format **97.1**)

**Date:** 2026-11-13  
**Plan:** `.agent/milestone-26_3-plan.md` Phase 4  
**Evidence:** `knowledge/renames/26.2-26.3.json`, `knowledge/vanilla/26.2` vs `26.3`, `knowledge/pack_formats.json` → 97.1, wiki Pack format / Equipment / Armor trim definition

## Gate results

| Command | Exit | Notes |
|---------|------|-------|
| `./harness/build.sh` | 0 | |
| `./harness/run_l0.sh` | 0 | includes `fixtures/synthetic/trim_palettes_26_3` → pack_format **97.1** |
| `./harness/run_tsf.sh 1.21.11` | 0 | milestone 1 gate unchanged |
| `./harness/run_tsf.sh 26.3` | 0 | score + assert_tsf ok; soft gap: TSF `entity/signs/oak.png` → review (Phase 3) |

## Audit (breaking vs N/A)

| Candidate | Verdict | Action |
|-----------|---------|--------|
| `textures/trims/color_palettes/*.png` → `textures/palettes/trim/*.png` | **implemented** | `TrimPaletteConverter26_3` (jar same-hash moves) |
| `trim_palette.png` → `textures/palettes/trim_base.png` | **implemented** | same |
| Atlas `palette_key` / `permutations`: `…/trims/color_palettes/X` → `…:trim/X`; `…/trim_palette` → `…:trim_base` | **implemented** | rewrite in all `atlases/*.json` paletted_permutations sources |
| `atlases/armor_trims.json` removed | **implemented** | delete if present (entity trims on-demand) |
| Palette `.mcmeta` `palette.base_palette` | **implemented** | ensure on migrated non-base palettes; preserve author value if already set. Vanilla jar ships **no** built-in palette mcmeta — field required for custom remaps (wiki) |
| `override_armor_assets` (datapack trim materials) → equipment `trim_overrides` | **implemented** | `TrimEquipmentOverrideConverter26_3`; final jar field is `trim_overrides` (snap1 `trim_palette_replacements` upgraded when present) |
| Trim material `asset_name` → `palette_id` | **implemented** | when `data/*/trim_material/*.json` present in pack |
| Equipment `humanoid_baby` layer / textures | **N/A** | present in both 26.2 and 26.3; no path rename |
| Saddle equipment same-hash “swaps” in rename JSON | **N/A** | hash collisions across horse/donkey/mule/skeleton/zombie saddles |
| Additive 26.3 content | **N/A** | no converter |

## Converters

| Class | Register | `shouldConvert` |
|-------|----------|-----------------|
| `…v26_3.textures.TrimPaletteConverter26_3` | after `ShaderTextConsolidationConverter26_2` | from ≤26.2 → to ≥26.3 |
| `…v26_3.other.TrimEquipmentOverrideConverter26_3` | after palette converter | same |

**Behavior summary:**
- Move palette PNGs (+ sibling `.mcmeta`); write `palette.base_palette` for remapping palettes
- Rewrite atlas palette Texture IDs; remove dead `armor_trims` atlas
- Migrate `override_armor_assets` → `assets/*/equipment/*.json` `trim_overrides`; `asset_name` → `palette_id`
- Upgrade leftover snap1 `trim_palette_replacements` maps when material id is inferable (`ns:trim/<leaf>` → `ns:<leaf>`); else review finding

## Fixture / score

- L0: `fixtures/synthetic/trim_palettes_26_3` (`1.16.5` → `26.3`) expects `palettes/trim_base.png`, `palettes/trim/custom_gem.png`(+mcmeta), equipment `iron.json`, `conversion_review.json`
- Score: `check_trim_palettes_26_3` errors on leftover `color_palettes/`, `armor_trims.json`, legacy atlas refs, missing `base_palette`, leftover `override_armor_assets` / bare `asset_name`

## TSF impact (soft gaps)

- TSF does **not** ship trim palettes / trim materials → Phase 4 cliffs N/A for this pack
- Soft carry-over from Phase 3: `entity/signs/oak.png` → `sign_entity_texture_needs_slicer` review (asserts still green)
