# Task: hop-atlas-1_19_3

**Milestone target:** TSF **1.16.5 → 1.21.11**.  
Atlas work must also survive **1.21.11** items-vs-blocks atlas split (format 75.0).

## Goal

Ensure non-`minecraft` namespaces with textures outside `textures/block` and `textures/item` get atlas coverage from **1.19.3+** (e.g. `demo:textures/npc`, TSF `tsf:textures/npc`).

## Allowed paths

- `conversions/Forwards/.../v1_19/other/AtlasConverter1_19_3.java` (and related atlas helpers)
- Later 1.21.11 atlas split converters **only if** coordinator confirms after items cliff is green
- `fixtures/backlog/custom_ns_atlas/` → move to `fixtures/synthetic/` when green

## Forbidden paths

- Inventing texture path renames
- Changing `assert_tsf.py` to drop `tsf_needs_atlas`
- Broad refactors of unrelated hops

## Commands

```bash
./harness/build.sh
./harness/convert.sh --from 1.16.5 --to 1.21.11 --pack fixtures/backlog/custom_ns_atlas/pack --out reports/work/hop_atlas
python3 harness/score.py --pack <converted> --to 1.21.11 --name hop_atlas
./harness/run_backlog.sh
./harness/run_tsf.sh 1.21.11
```

## Done criteria

- Backlog `custom_ns_atlas` no longer fails with `custom_ns_textures_without_atlases`
- TSF `assert_tsf.json` clears `tsf_needs_atlas` (or documents remaining coverage gaps as findings)
- Item models do not incorrectly bind the blocks atlas under 1.21.11 rules (verify against vanilla knowledge / rename diffs)
- L0 synthetic remains green

## Priority

**P0** for milestone 1 (updated from Phase 1 triage) — TSF already has `items/` defs; residual gap is `tsf:` not present in atlas sources (`custom_ns_textures_check_atlas` warn). Parallelize only if no shared file conflict with other agents.
