# Hop agent assignments

Use one subagent per **edge** (not per full product). Each agent:

1. Implements/fixes only that hop’s converters under `conversions/` at the repository root
2. Adds/extends L0 fixtures that cross that edge
3. Runs `./lab/harness/run_l0.sh` (and any hop-specific script)
4. Leaves `reports/` output; does not rewrite shared scoring to hide failures

## Milestone 2 edges (after Phase 0 foundation)

| ID | From → To | Focus | Status |
|----|-----------|--------|--------|
| hop-26_1 | 1.21.11 → 26.1 | baby/entity layout, lightmap/shader renames (84.0) | done — see `reports/phase2_status.md` |
| hop-26_2 | 26.1 → 26.2 | beds/signs → block models, atlases, `end_cube` (88.0) | done — see `reports/phase3_status.md` |
| hop-26_3 | 26.2 → 26.3 | trim palettes → `textures/palettes/trim/`, equipment trim overrides (97.1) | done — see `reports/phase4_status.md` |
| residual-equip | (≤1.21.11 path) | custom armor still under `textures/models/armor/` | Phase 1 P0 done (see phase1) |
| hop-audit-55–69 | 1.21.5–1.21.10 | residual cliffs 55 / 63 / 64 / 69.0 if diffs prove need | Phase 1 |

Rename evidence already committed: `knowledge/renames/1.21.11-26.1.json`, `26.1-26.2.json`, `26.2-26.3.json`.

## Suggested edges (Milestone 1 — largely done)

| ID | From → To | Focus |
|----|-----------|--------|
| hop-gui-1_20_2 | 1.20.1 → 1.20.2 | `icons.png` / `widgets.png` → gui sprites |
| hop-items-1_21_4 | 1.21.3 → 1.21.4 | `overrides` / CMD → `assets/.../items/*.json` |
| hop-equip-1_21_2 | 1.21.1 → 1.21.2 | armor `models/armor` → `entity/equipment` |
| hop-atlas-1_19_3 | 1.19.2 → 1.19.3 | atlases; custom NS outside block/item |
| hop-scute | (any → 1.20.5+) | `scute` → `turtle_scute` host + paths |
| hop-flatten-1_13 | 1.12.2 → 1.13.2 | blocks/items flattening (large) |

## Cross-cutting (not a hop)

| ID | Focus |
|----|--------|
| concern-cmd-adventure | TSF-style CMD hosts, `tsf:` atlas, presentation/barney/projector asserts |
| concern-harness | score.py, fixtures, AGENTS.md — keep stable for others |
| foundation | protocol, vanilla fetch, rename diffs, pack_formats — **Phase 0 done** |

## Integrator

Runs `./harness/run_l0.sh` + `./harness/run_tsf.sh` after merges.  
Milestone 1 gate: `1.21.11`. Milestone 2 gate: **`26.3`** (`./harness/run_tsf.sh 26.3`) once hop converters land.

## Task briefs

See `.agent/tasks/` — including `phase0-foundation.md` and per-hop goals.
