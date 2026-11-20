# Task: hop-items-1_21_4 / concern-cmd-adventure

**Milestone target:** Terra Swoop Force **1.16.5 → 1.21.11** (pack_format 6 → 75.0).  
This hop owns the **format 46** cliff; final gate still requires items defs valid on **1.21.11**.

## Goal

Convert CMD / model `overrides` into correct `assets/minecraft/items/<host>.json` at **1.21.3 → 1.21.4**, and ensure theatrical hosts keep working through the chain to **1.21.11**.

## Allowed paths

- `conversions/Forwards/src/main/java/com/agentdid127/resourcepack/forwards/impl/other/ModelConverter.java` (and closely related 1.21.4 item-model helpers in the same package tree)
- `fixtures/backlog/overrides_to_items/`
- `fixtures/backlog/overrides_to_items_chain/`
- Move those fixtures to `fixtures/synthetic/` only when green

## Forbidden paths

- Inventing vanilla renames (use `knowledge/renames/` / jar diffs only)
- Weakening `harness/score.py` or `harness/assert_tsf.py` to hide failures
- Touching unrelated hop converters (GUI, atlas, celestial) unless coordinator expands scope
- 26.x / Bedrock / Faithful L1 work

## Commands

```bash
./harness/build.sh
# isolated cliff
./harness/convert.sh --from 1.16.5 --to 1.21.4 --pack fixtures/backlog/overrides_to_items/pack --out reports/work/hop_items_1214
python3 harness/score.py --pack reports/work/hop_items_1214/*/ --to 1.21.4 --name hop_items_1214
# chain to milestone
./harness/convert.sh --from 1.16.5 --to 1.21.11 --pack fixtures/backlog/overrides_to_items_chain/pack --out reports/work/hop_items_12111
./harness/run_l0.sh
./harness/run_tsf.sh 1.21.11
```

## Done criteria

- Convert →**1.21.4** produces `assets/minecraft/items/stick.json` (and host stem matches) without leaving unsourced overrides for that host
- Convert →**1.21.11** still has valid items defs (no `overrides_without_items_defs` for fixture hosts)
- TSF assert improves on `host_still_overrides_*` / missing `items/<host>.json` for barney/presentation/projector hosts
- `./harness/run_l0.sh` stays green for synthetic fixtures
- Leave findings in `reports/`; do not guess UV/pose — emit review findings if ambiguous

## Priority

**P2–P3 verify/harden** after atlas — Phase 1 triage shows TSF →1.21.11 already emits `items/` for theatrical hosts once harness scores the `_converted` pack. Still own synthetic backlog fixtures (`overrides_to_items*`) and schema correctness; do not parallelize other `ModelConverter` edits.
