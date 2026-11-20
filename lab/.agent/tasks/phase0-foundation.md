# Task: Phase 0 — Foundation (Milestone 2)

**Status:** done (2026-11-12)  
**Owner:** foundation agent  
**Plan:** `.agent/milestone-26_3-plan.md` Phase 0 only

## Done

- `protocol.json`: `26.1`, `26.1.1`, `26.1.2` (775 / 84.0), `26.2` (776 / 88.0), `26.3` (777 / 97.1)
- Vanilla fetched: `1.21.11`, `26.1`, `26.2`, `26.3` (manifest ids exact)
- Renames: `knowledge/renames/1.21.11-26.1.json`, `26.1-26.2.json`, `26.2-26.3.json`
- `knowledge/pack_formats.json` extended for **63, 64, 69.0, 84.0, 88.0, 97.1**
- PackMeta float formats confirmed via smoke convert `--to 26.3`
- Notes: `reports/phase0_foundation.md`

## Next hops (do not invent renames)

| ID | Edge | Format cliff |
|----|------|----------------|
| hop-26_1 | 1.21.11 → 26.1 | 84.0 baby/entity + shader/lightmap |
| hop-26_2 | 26.1 → 26.2 | 88.0 beds/signs/end_cube |
| hop-26_3 | 26.2 → 26.3 | 97.1 trim palettes / equipment |
| residual-equip | Phase 1 P0 | custom armor under `textures/models/armor/` |
| integrator | later | `./harness/run_tsf.sh 26.3` (not required for Phase 0) |
