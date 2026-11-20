# Task: post-46 hops toward 1.21.11

**Milestone target:** TSF **1.16.5 → 1.21.11** (pack_format **75.0**).

## Goal

After format **46** (1.21.4) CMD→items is green, cover remaining cliffs needed for a working TSF chain:

| pack_format | Releases | Likely work |
|-------------|---------|-------------|
| 55 | 1.21.5 | item model component changes |
| 63–64 | 1.21.6–1.21.8 | shaders / model rotation / textures |
| 69.0 | 1.21.9–1.21.10 | protocol / copper-era formats (`protocol.json` already lists 1.21.9) |
| **75.0** | **1.21.11** | items vs blocks atlas split; celestial paths; multi-axis rotation |

Prefer **minimal** fixes proven by TSF/L0 — not speculative full coverage of every snapshot.

## Allowed paths

- `conversions/Forwards/.../v1_21*/` hop converters (incl. `CelestialSlicerUpdater1_21_11`)
- `library/.../protocol.json` alignment only with documented formats
- `knowledge/renames/` from `diff_vanilla.py`
- New synthetic fixtures for a specific post-46 edge when needed

## Forbidden paths

- Re-opening 26.x
- Inventing renames
- Weakening asserts
- Large shared-library rewrites without integrator review
- Parallel edits to `ModelConverter` while hop-items agent owns it

## Commands

```bash
./scripts/fetch_vanilla.sh 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11   # as needed
python3 harness/diff_vanilla.py --from-ver A --to-ver B
./harness/build.sh
./harness/run_l0.sh
./harness/run_tsf.sh 1.21.11
# Inspect reports/baseline/TRIAGE.md and reports/work/tsf_* for actual gaps
```

## Done criteria

- TSF convert **→1.21.11** no longer fails solely due to post-46/celestial/atlas-split issues once items+atlas P0/P1 are done
- `protocol.json` remains aligned with `knowledge/pack_format_versions.md` for 1.21.9–1.21.11
- L0 green; `assert_tsf.json` `"ok": true` is the integrator’s final call (this task contributes the post-46 slice)

## Priority

**P4** — after items + atlas; drive from TRIAGE, not memory.
