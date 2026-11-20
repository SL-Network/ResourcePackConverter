# Phase 0 foundation — Minecraft 26.x

**Date:** 2026-11-12  
**Plan:** `.agent/milestone-26_3-plan.md` Phase 0

## Manifest version ids fetched

Via `./scripts/fetch_vanilla.sh` against Mojang `version_manifest_v2.json`:

| Requested | Manifest id | Present? | Files under `knowledge/vanilla/<id>/assets` |
|-----------|-------------|----------|-----------------------------------------------|
| 1.21.11 | `1.21.11` | yes (release) | ~10308 |
| 26.1 | `26.1` | yes (release) | ~10463 |
| 26.2 | `26.2` | yes (release) | ~10968 |
| 26.3 | `26.3` | yes (release) | ~11604 |

Also present on the manifest (not fetched for Phase 0 diffs): `26.1.1`, `26.1.2` (same protocol/pack_format line as `26.1`).

## protocol.json

| Version | protocol_version | pack_format | Source |
|---------|------------------|-------------|--------|
| 26.1 | 775 | 84.0 | wiki Protocol version table |
| 26.1.1 | 775 | 84.0 | same |
| 26.1.2 | 775 | 84.0 | same |
| 26.2 | 776 | 88.0 | same |
| 26.3 | 777 | 97.1 | same |

## Rename diffs

Generated with `harness/diff_vanilla.py` (hash-index of all assets under `assets/`):

- `knowledge/renames/1.21.11-26.1.json` — moved≈3927, removed=93, added=248
- `knowledge/renames/26.1-26.2.json` — moved≈3595, removed=98, added=603
- `knowledge/renames/26.2-26.3.json` — moved≈3673, removed=30, added=666

## PackMeta

No code change required. Existing float path (`to >= 1.21.10` protocol) writes `pack_format` / `min_format` / `max_format` as doubles once `26.3` resolves in `protocol.json`.

## Smoke / build

- `./harness/build.sh` — OK
- Convert `fixtures/synthetic/smoke_1_16/pack` `--from 1.16.5 --to 26.3` — OK  
  Resulting `pack.mcmeta`: `pack_format` **97.1**, `min_format` **65.0**, `max_format` **97.1**, `meta.game_version` **26.3**
- `./harness/run_l0.sh` — still targets 1.21.11; run after protocol change to confirm no regression
- PackMetaConverter.java — **no code change** (float path already correct for protocol ≥ 1.21.10)

## Out of scope (Phase 0)

- Hop converters under `conversions/Forwards/.../v26_*`
- Custom armor residual
- `run_tsf.sh 26.3` green
