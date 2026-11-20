# Phase 0 / Phase 1 baseline notes

**Date:** 2026-11-12  
**Milestone:** TSF `1.16.5` → `1.21.11` (pack_format 6 → 75.0)

## Vanilla fetch

**Success.** `./scripts/fetch_vanilla.sh` downloaded and extracted:

`1.19.2`, `1.19.3`, `1.20.1`, `1.20.2`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`

Rename diffs written under `knowledge/renames/` for consecutive pairs listed in the plan.

Log: `reports/baseline/fetch_vanilla.log` (local; may be large).

## Harness fix (required for meaningful baseline)

`harness/convert.sh` previously echoed the **input copy** (`…/input/<name>`) instead of the converter’s **`…/input/<name>_converted`** output. Score/assert therefore evaluated the unconverted pack (still `pack_format` 6, no `items/`, no atlases).

Fixed to print the `_converted` path. Re-ran `./harness/run_tsf.sh 1.21.11` afterward.

## Baseline result (after harness fix)

| Artifact | Result |
|----------|--------|
| `assert_tsf.json` | `"ok": true` |
| `score_tsf.json` | `"ok": true` (warn: `custom_ns_textures_check_atlas` for `tsf:`) |
| `pack.mcmeta` | `pack_format` / `max_format` **75.0**, `game_version` 1.21.11 |

Archived copies: `assert_tsf.json`, `score_tsf.json`, `convert.log`, `run_tsf.log` in this directory.

See `TRIAGE.md` for residual gaps that asserts do not fail on yet.
