# Residuals 1–3 status — soft gaps after Milestone 2

**Date:** 2026-11-13  
**Parent:** `reports/milestone2_status.md`

## Gate results

| Command | Exit | Notes |
|---------|------|-------|
| `./harness/build.sh` | 0 | |
| `./harness/run_l0.sh` | 0 | + `shaders_cliffs`; beds_signs expects sliced `block/oak_sign` / bed faces / `text.*` |
| `./harness/run_tsf.sh 1.21.11` | 0 | equipment JSON assert green |
| `./harness/run_tsf.sh 26.3` | 0 | `tsf_sign_entity_sliced_26_2`; **no** `sign_entity_texture_needs_slicer` soft gap |

## 1) Sign / bed entity → block (Slicer)

| Item | Status |
|------|--------|
| Mojang Slicer v1.2.1 UV defs (MIT) | **shipped** — `BedsSignsSlicerDefinitions26_2` + library `slicing/multipart/*` |
| Slice `entity/bed/**`, `entity/signs/**`, hanging | **shipped** — deletes sources after success |
| Obsolete particle-only bed/sign models | **shipped** — delete when textures-only particle models |
| TSF `entity/signs/oak.png` | **cleared** → `textures/block/oak_sign.png` |
| L0 | `fixtures/synthetic/beds_signs_26_2` |

**Deferred:** unknown wood/color leftovers still emit review; non-particle obsolete models → review (no invented geometry).

## 2) Shader-pack converters

| Cliff | Class | Behavior |
|-------|--------|----------|
| 1.21.5 program JSON removal | `ShaderProgramJsonRemover1_21_5` | delete `shaders/core|post` + `post_effect` `*.json` |
| 26.1 lightmap uniforms | `ShaderLightmapUniformConverter26_1` | `DarkenWorldFactor` → `BossOverlayWorldDarkeningFactor`; **N/A** `AmbientLightFactor` → review |
| 26.2 text / moving-block | `ShaderTextConsolidationConverter26_2` | rename `rendertype_text`→`text`, `rendertype_text_background`→`text_background`, `rendertype_translucent_moving_block`→`block`; fold variants when dest exists |

L0: `fixtures/synthetic/shaders_cliffs`. Packs without `shaders/` no-op.

**Deferred:** IS_GUI / IS_SEE_THROUGH / IS_GRAYSCALE content ports; full lightmap AmbientColor rewrite.

## 3) Custom theatrical equipment JSON (+ flight-suit dye fix)

| Item | Status |
|------|--------|
| `CustomEquipmentJsonConverter1_21_2` | **shipped** — leather-style `dyeable` (`color_when_undyed` white) on base when overlay PNG exists; patches existing JSON missing dyeable |
| `ArmorOverlayTintIndexConverter1_21_4` | **shipped** — strip `tintindex` from overlay faces in item models |
| `LeatherArmorItemsTintConverter1_21_4` | **shipped** — ensure pack-local `items/leather_*.json` declare `minecraft:dye` when models are overridden |
| `ItemsAtlasConverter1_21_11` | **updated** — dual-stitch `models` / `models/armor` onto `items.json` (keep on blocks) |
| L0 | `custom_armor` — equipment+dyeable, leather items tint, items atlas dual-stitch |
| TSF | asserts dyeable on white/noxcrew; overlay tint strip; leather_chestplate dye tint |

**Deferred:** inventing item equippable components / host wiring beyond equipment asset JSON; horse/nautilus layers without textures.
