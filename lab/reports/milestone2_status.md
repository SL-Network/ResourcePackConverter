# Milestone 2 status — conversions through Minecraft **26.3**

**Declared structurally done** (2026-11-13). Forward convert **→ 26.3** (pack_format **97.1**) with L0 cliffs + TSF L2 gate green; soft residuals documented below (not silently ignored).

**Baseline:** Milestone 1 — TSF **1.16.5 → 1.21.11** (pack_format **75.0**)  
**Plan:** `.agent/milestone-26_3-plan.md`

## Gate results

| Command | Exit | Notes |
|---------|------|-------|
| `./harness/build.sh` | 0 | jar + symlink OK |
| `./harness/run_l0.sh` | 0 | all synthetic fixtures ok (incl. `entity_layout_26_1`, `beds_signs_26_2`, `trim_palettes_26_3`) |
| `./harness/run_tsf.sh 1.21.11` | 0 | milestone 1 regression gate |
| `./harness/run_tsf.sh 26.3` | 0 | `score_tsf.json` + `assert_tsf.json` both `ok: true` |

## Phase rollup

| Phase | Focus | Status | Report |
|-------|--------|--------|--------|
| 0 | Protocol / vanilla / rename JSONs / pack_formats | done | `reports/phase0_foundation.md` |
| 1 | Residual 1.21.x cliffs (armor, chain, …) | done | `reports/phase1_status.md` |
| 2 | 26.1 / 84.0 entity layout | done | `reports/phase2_status.md` |
| 3 | 26.2 / 88.0 beds-signs-pillars | done | `reports/phase3_status.md` |
| 4 | 26.3 / 97.1 trim palettes + equipment overrides | done | `reports/phase4_status.md` |
| 5 | Integrator verification | done (L0 + TSF→26.3; L1/L3 optional deferred) | this file |

## Spot-check (converted TSF → 26.3)

- `pack.mcmeta` float `pack_format` / `min_format` / `max_format` ≈ **97.1**
- Theatrical hosts / `tsf:` atlas / items peel / `block/item/` crop-host rewrites / equipment saddles / custom armor copies — asserts green
- No TSF trim palettes → Phase 4 path moves N/A for this pack

## Soft residuals

**Cleared (2026-11-13)** — see `reports/residuals_123_status.md`:

1. **Sign/bed entity → block slicer** — Mojang Slicer v1.2.1 UVs; TSF oak sign → `block/oak_sign.png` (no `sign_entity_texture_needs_slicer`).
2. **Shader cliffs** — 1.21.5 program JSON removal; 26.1 `DarkenWorldFactor` rename (+ AmbientLightFactor N/A review); 26.2 text/block consolidation renames; **`IncompatibleCoreShaderStripper26_1`** removes pack `shaders/core|post|include` when converting ≤1.21.11 → ≥26.1 (OpenGL GLSL cannot be reliably auto-ported to RenderPearl/SPIR-V/Vulkan — custom effects dropped, review id `core_shaders_stripped_vulkan_26_1`).
3. **Custom theatrical equipment JSON** — `equipment/<id>.json` for non-vanilla humanoid materials (noxcrew, rainbow, …).

**Still deferred (optional):**

4. **Optional L1 Faithful path/pixel score** — not run for this milestone gate.
5. **Optional L3 in-game screenshots** — armor/trims/beds/signs visual QA deferred.
6. Hand-authored Vulkan ports of custom core effects (e.g. sun cubemap); IS_GUI / AmbientColor content rewrites — N/A mechanical.

## Delivered hop converters (26.x)

| Hop | Classes |
|-----|---------|
| 1.21.2+ | `CustomEquipmentJsonConverter1_21_2` (after ArmorMover) |
| 1.21.5 | `ShaderProgramJsonRemover1_21_5` |
| 26.1 | `EntityTextureLayoutConverter26_1`, `ShaderLightmapUniformConverter26_1`, `IncompatibleCoreShaderStripper26_1` |
| 26.2 | `BedsSignsConverter26_2` (+ Mojang slicer defs), `PillarSideConverter26_2`, `ShaderTextConsolidationConverter26_2` |
| 26.3 | `TrimPaletteConverter26_3`, `TrimEquipmentOverrideConverter26_3` |

## Knowledge

- Renames: `knowledge/renames/1.21.11-26.1.json`, `26.1-26.2.json`, `26.2-26.3.json`
- Format rules: `knowledge/pack_formats.json` → **84.0 / 88.0 / 97.1**
- Vanilla trees: `knowledge/vanilla/{1.21.11,26.1,26.2,26.3}` (gitignored; not committed)
