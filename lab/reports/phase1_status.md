# Phase 1 status — finish 1.21.x cliffs (pre-26.x)

**Date:** 2026-11-12  
**Baseline:** Phase 0 (`26737e9`); Milestone 1 TSF → 1.21.11 structurally green

## Gate results

| Command | Exit | Notes |
|---------|------|-------|
| `./harness/build.sh` | 0 | |
| `./harness/run_l0.sh` | 0 | includes new `fixtures/synthetic/custom_armor` |
| `./harness/run_tsf.sh 1.21.11` | 0 | `score_tsf` + `assert_tsf` ok; armor asserts info |

## A) Custom armor residual (P0) — fixed

**Problem:** `ArmorMoverConverter1_21_2` only moved a hardcoded vanilla material list. TSF custom PNGs (`noxcrew`, `rainbow`, `community`, `white`, …) stayed under `textures/models/armor/`. Item models with bare `layer0: models/armor/…` were rewritten to missing `item/models/armor/…` by ModelConverter’s 1.9 prefix heuristic.

**Fix:**
- Generalized armor mover: any `*_layer_1/2(_overlay).png` (+ `turtle_layer_1`) → `entity/equipment/humanoid*`; residual non-layer PNGs **copied** to `humanoid/` while originals kept for item models / blocks atlas.
- ModelConverter: only prefix bare `layer0` names (no `/` or `:`).
- Armor mover also rewrites leftover `item/models/armor/…` refs back to `models/armor/…` when the PNG exists.
- No invented `assets/minecraft/equipment/*.json`.

**Fixture:** `fixtures/synthetic/custom_armor` (1.16.5 → 1.21.11) — iron + custom layer materials, noxcrew residual, glint + chain rename smoke.

**Asserts:** `score.check_custom_armor_equipment` + `assert_tsf` equipment-copy / bad-prefix checks for ≥1.21.2; packs without `models/armor` skip cleanly.

## B) Format audit 55 / 63 / 64 / 69.0

| Format | Candidate | Verdict | Action |
|--------|-----------|---------|--------|
| **55** | pig/strider saddle path | **done** (prior) | `SaddleEquipmentConverter1_21_5` |
| **55** | `enchanted_glint_entity` → `enchanted_glint_armor` | **implemented** | `EnchantGlintConverter1_21_5`; evidence `knowledge/renames/1.21.4-1.21.5.json` |
| **55** | pig_saddle pixel size | **N/A** | content-only; path migrate covers adventure packs |
| **55** | item-model component tweaks | **N/A** | no TSF/L0 asset evidence requiring rewrite |
| **55** | shader JSON removal | **deferred** | TSF ships no shaders; only needed if packs ship `shaders/` |
| **63** | shader uniform-blocks | **deferred** | same — no TSF shaders |
| **63** | leash sound renames | **N/A** | TSF `sounds.json` has no leash entries; no rename JSON evidence wired |
| **63** | free model rotations | **N/A** | additive / no rewrite |
| **64** | 1.21.7–8 | **N/A** | mostly additive textures (wiki + rename samples) |
| **69.0** | `chain` → `iron_chain` | **implemented** | NameConverter + `items.json`/`blocks.json` `1_21_9`; evidence `knowledge/renames/1.21.8-1.21.10.json` |
| **69.0** | end-flash / copper additives | **N/A** | additive; TSF has neither |
| **69.0** | post shader merges | **deferred** | shader-shipping packs only |

## Remaining gaps before Phase 2 (26.1)

1. Shader-shipping packs still need format 55/63/69 post/core shader converters (out of TSF scope).
2. Custom armor: equipment **textures** migrated; no custom `equipment/*.json` emitted (would invent). Worn custom materials that need equipment assets remain a review/L3 concern.
3. Phase 2+ hop converters for 26.1 / 26.2 / 26.3 not started (rename JSONs from Phase 0 ready).

## Delivered converters / wiring

- `ArmorMoverConverter1_21_2` (generalized + residual copy + item-model ref fix)
- `EnchantGlintConverter1_21_5`
- NameConverter `1_21_9` chain → iron_chain (textures, models, blockstates, items defs)
- ModelConverter layer0 bare-name fix + 1.21.9 texture/parent remap
