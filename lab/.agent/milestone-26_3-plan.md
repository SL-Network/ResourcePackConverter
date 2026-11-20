# Milestone 2 plan — conversions through Minecraft 26.3

**Status:** Milestone 2 done — Phases 0–5 met (L0 + TSF→26.3 green; optional L1/L3 deferred)  
**Baseline:** Milestone 1 done — TSF **1.16.5 → 1.21.11** (pack_format **75.0**) structurally green  
**Target:** reliable forward convert **→ 26.3** (pack_format **97.1**), with TSF as the L2 gate  
**Rule:** do **not** invent vanilla renames; every path map comes from jar/index diffs or documented pack_format rules. Prefer hop-scoped converters. Success ≠ bumping `pack_format` only.  
**Status report:** `reports/milestone2_status.md`

Related: `AGENTS.md`, `knowledge/pack_format_versions.md`, `knowledge/pack_formats.json`, `reports/milestone1_status.md`, `.agent/hop-assignments.md`.

---

## Format ladder (after 1.21.11)

| pack_format | Releases | Notes |
|-------------|----------|--------|
| 75.0 | 1.21.11 | Current milestone 1 ceiling |
| 84.0 | 26.1 – 26.1.2 | Baby/entity layout, shader/lightmap tweaks |
| 88.0 | 26.2 | Beds/signs → block models; atlases; `end_cube` |
| 97.1 | **26.3** | Trim palettes / equipment trim overrides |

Also audit incomplete cliffs already on the path (**55 / 63 / 64 / 69.0**) before treating 26.x as the only gap.

---

## Phase 0 — Foundation ✅ *(done 2026-11-12)*

**Goal:** make 26.x a first-class convert target and give hop agents evidence.

1. ✅ `protocol.json`: `26.1` / `26.1.1` / `26.1.2` (775, 84.0), `26.2` (776, 88.0), `26.3` (777, 97.1).
2. ✅ Vanilla fetched: exact manifest ids `1.21.11`, `26.1`, `26.2`, `26.3` (see `reports/phase0_foundation.md`).
3. ✅ Rename JSONs: `knowledge/renames/1.21.11-26.1.json`, `26.1-26.2.json`, `26.2-26.3.json`.
4. ✅ `pack_formats.json` + `pack_format_versions.md` cover **63, 64, 69.0, 84.0, 88.0, 97.1**.
5. ✅ PackMeta smoke: `--to 26.3` writes float `pack_format` / `min_format` / `max_format` (~97.1).
6. ✅ Docs: `.agent/hop-assignments.md`, `.agent/tasks/phase0-foundation.md`; TSF gate note `run_tsf.sh 26.3` (not required green yet).

**Done when:** `--to 26.3` resolves in protocol; vanilla trees + rename JSONs exist; pack_format docs list 26.x cliffs; no behavioral hop converters required yet. **Met.**

---

## Phase 1 — Finish 1.21.x cliffs that still matter ✅ *(done 2026-11-12)*

These bite packs on the way to 26.3 even if format already “passes.” See `reports/phase1_status.md`.

| Format | Version | Work |
|--------|---------|------|
| 55 | 1.21.5 | Saddle **done**. `enchanted_glint_entity`→`enchanted_glint_armor` **done**. pig_saddle size / item-component / shader JSON → N/A or deferred (no TSF shaders) |
| 63 | 1.21.6 | Shader uniform-blocks **deferred** (no TSF shaders); leash sounds **N/A**; free rotations **N/A** |
| 64 | 1.21.7–8 | **N/A** — mostly additive |
| 69.0 | 1.21.9–10 | `chain`→`iron_chain` **done** (NameConverter `1_21_9`); end-flash/copper **N/A**; post shaders **deferred** |
| 75.0 | 1.21.11 | Items atlas + celestial + block/`item` rewrite **done** |

**P0 residual:** custom armor **fixed** — `ArmorMoverConverter1_21_2` generalizes layer moves + copies non-layer residuals to `entity/equipment/humanoid/`; ModelConverter no longer prefixes `item/` onto `models/armor/…`; L0 `fixtures/synthetic/custom_armor`; score/assert coverage for ≥1.21.2.

**Done when:** rename/audit findings closed or documented as N/A; custom-armor residual fixed or explicitly deferred with assert coverage decided; L0 + TSF→1.21.11 still green. **Met.**

---

## Phase 2 — 26.1 (format **84.0**) ✅ *(done 2026-11-13)*

Evidence from wiki + `1.21.11→26.1` jar diff / vanilla trees — see `reports/phase2_status.md`.

- ✅ Baby-mob / entity texture layout moves → `EntityTextureLayoutConverter26_1` (84 paths)
- Shader / lightmap uniform renames → **N/A / deferred** (no TSF shaders; jar “renames” are hash collisions)
- Baby `*_baby.png` → **N/A** (additive; do not invent by copying adults)
- Path renames only — additive content needs no converter

**Deliverables:** hop converter under `conversions/Forwards/.../v26_1/`, L0 `fixtures/synthetic/entity_layout_26_1`, rename JSON from Phase 0.

**Done when:** L0 for 26.1 cliff green; sample convert →26.1 + chain smoke OK. **Met.**

---

## Phase 3 — 26.2 (format **88.0**) ✅ *(done 2026-11-13)*

Higher impact for adventure / map packs — see `reports/phase3_status.md`.

- ✅ Beds / signs atlases removed; leftover entity bed/sign textures → **review findings** (no invented UVs; Mojang Slicer)
- ✅ Pillar side renames (`purpur_pillar` / `quartz_pillar` → `*_side`) + model texture ref rewrite
- ✅ Shader consolidation → **review findings** when pack ships legacy `rendertype_text*` (TSF N/A — no shaders)
- `minecraft:end_cube` → **N/A** (additive special type; no vanilla jar path migrations)

**Deliverables:** hop converters under `impl/v26_2/`, L0 `fixtures/synthetic/beds_signs_26_2`, TSF→26.2 smoke green with soft sign-slicer gap.

**Done when:** L0 green; no invented UVs — emit review findings when geometry is ambiguous. **Met.**

---

## Phase 4 — 26.3 (format **97.1**) ✅ *(done 2026-11-13)*

See `reports/phase4_status.md`.

- ✅ Trim palettes: `textures/trims/color_palettes/` → `textures/palettes/trim/` (+ `trim_palette.png` → `textures/palettes/trim_base.png`)
- ✅ Palette `.mcmeta` `palette.base_palette` on migrated remapping palettes
- ✅ Trim `override_armor_assets` → equipment `trim_overrides` (jar/wiki final field; snap1 `trim_palette_replacements` upgraded when present)
- ✅ Atlas palette id rewrite + remove `armor_trims.json`; `asset_name` → `palette_id` on datapack trim materials in-pack
- `humanoid_baby` / saddle same-hash rename-JSON noise → **N/A**

**Deliverables:** `TrimPaletteConverter26_3`, `TrimEquipmentOverrideConverter26_3`, L0 `fixtures/synthetic/trim_palettes_26_3`, format **97.1** rules updated.

**Done when:** L0 green; trim overrides survive convert when present in fixtures. **Met.**

---

## Phase 5 — Verification / integrator ✅ *(done 2026-11-13 — structural gates)*

1. ✅ L0 green for every new cliff fixture (`entity_layout_26_1`, `beds_signs_26_2`, `trim_palettes_26_3`, …)  
2. ✅ `./harness/build.sh` + `./harness/run_l0.sh`  
3. ✅ `./harness/run_tsf.sh 26.3` with asserts (hosts, atlases, equipment saddles, crop-host `block/item/` rewrites, armor copies) — soft sign-slicer review only  
4. Optional Faithful L1 path/pixel score — **deferred**  
5. Optional L3 in-game (armor, saddles, crop hosts, beds/signs) — **deferred**  
6. ✅ `reports/milestone2_status.md` written

**Milestone 2 done when:** `--to 26.3` works end-to-end; breaking path/schema changes 1.21.11→26.3 covered by hop converters or rename maps from diffs; TSF→26.3 asserts `ok: true`; residuals documented, not silently ignored. **Met** (optional L1/L3 out of structural scope).

---

## Suggested agent split

| Workstream | Owns |
|------------|------|
| **foundation** (Phase 0) | protocol, fetch, diffs, pack_formats, PackMeta check |
| **residual-equip** (Phase 1 P0) | custom armor + item/armor atlas |
| **hop-1_21_5…10 audit** | remaining 55–69 only if diffs prove need |
| **hop-26_1** | format 84.0 |
| **hop-26_2** | format 88.0 |
| **hop-26_3** | format 97.1 |
| **integrator** | full chain 1.16.5→26.3 + TSF gate + status report |

---

## Explicitly out of scope (unless reopened)

- Bedrock
- Invented renames / guessed UVs
- Weakening asserts to go green
- Treating “pack_format bumped” as done

---

## Execution note

Phase 0 complete. Spawn hop agents (`hop-26_1`, then `26_2` / `26_3`) using committed rename JSONs — do not invent path maps.
