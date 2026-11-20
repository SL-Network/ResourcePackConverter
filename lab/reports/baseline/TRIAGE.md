# Phase 1 triage — TSF 1.16.5 → 1.21.11

Observed under `reports/work/tsf_1.16.5_to_1.21.11/input/tsf_1.16.5_converted/` after harness path fix.

## Structural gates (current harness)

| Check | Status | Notes |
|-------|--------|-------|
| `assert_tsf.json` | **ok** | Theatrical hosts resolve via `items/*.json` |
| `score_tsf.json` | **ok** | Error-level clear; **warn** on custom NS atlas coverage |
| CMD → `items/` | Present | 23 hosts under `assets/minecraft/items/`; model overrides cleared |
| GUI slice | Ran | `GuiSlicerConverter1_20_2` produced `textures/gui/sprites/`; many vanilla sheets missing (ignored) |
| Armor mover | Partial | Vanilla-ish layers moved to `textures/entity/equipment/`; **custom** TSF armor PNGs remain under `textures/models/armor/` |
| Celestial 1.21.11 | Ran | `CelestialSlicerUpdater1_21_11` executed (no separate assert) |

## Residual gaps → likely owners

| Observation | Evidence | Likely converter / task |
|-------------|----------|-------------------------|
| **`tsf:` NPC textures not in atlas sources** | `assets/minecraft/atlases/blocks.json` exists but does **not** mention `tsf`; score warn `custom_ns_textures_check_atlas`; assert only checks atlases **dir** exists | `AtlasConverter1_19_3` (scans `minecraft/textures` only) → **hop-atlas-1_19_3** |
| **Only `blocks.json` atlas** | No `items.json` (or similar) under `atlases/` after →1.21.11 | Post-46 / format **75.0** items-vs-blocks atlas split → **post-46-hops-1_21_11** (verify against vanilla 1.21.11) |
| **`scute` not renamed** | `items/scute.json` + `models/item/scute.json` present; **no** `turtle_scute` | NameConverter / scute rename → **hop-scute** |
| **Custom armor leftovers** | `textures/models/armor/{noxcrew,rainbow,community,white,…}.png` still present after `ArmorMoverConverter1_21_2` | hop-equip / armor mover follow-up (verify-on-fail) |
| **Items schema / post-46 semantics** | Items use `range_dispatch` + `custom_model_data`; hosts look wired, but 1.21.5+ / 75.0 component/atlas rules not deeply asserted | **post-46-hops-1_21_11** after atlas/scute |

## What is *not* the primary gap anymore

Earlier false baseline (scoring the input copy) suggested total CMD failure (`host_still_overrides_*`, `overrides_without_items_defs`). On the real `_converted` pack, **ModelConverter already emits `items/` for TSF hosts**. Treat **hop-items-1_21_4** as **verify + harden** (synthetic backlog fixtures, schema correctness) rather than greenfield.

## Recommended spawn order (updated)

1. **hop-atlas-1_19_3** — clear real `tsf:` coverage gap; tighten assert beyond “atlases dir exists” if coordinator agrees  
2. **hop-scute** — `scute` → `turtle_scute` through chain  
3. **hop-items-1_21_4 / concern-cmd-adventure** — keep backlog fixtures green; validate items JSON vs 1.21.4/1.21.11 vanilla  
4. **post-46-hops-1_21_11** — items atlas split / celestial / any 75.0 breakage  
5. **hop-gui-1_20_2** — verify-only (sprites already appear for present sheets)

## Error / finding ids (baseline archive)

**assert_tsf:** no error-level ids (`ok: true`). Info: `host_ok_*` via `items`, `tsf_barney_textures_present`.

**score_tsf:** no error-level ids. Warn: `custom_ns_textures_check_atlas` (ns `tsf`, npc paths).
