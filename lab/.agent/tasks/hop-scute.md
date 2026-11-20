# Task: hop-scute

**Milestone target:** TSF **1.16.5 → 1.21.11**.

## Goal

Ensure `scute` item host models/textures (including CMD overrides → items defs) rename to `turtle_scute` and survive the full forward chain to **1.21.11**.

## Allowed paths

- NameConverter / path rename wiring under `conversions/Forwards/` for the scute→turtle_scute edge
- Related armor/equipment movers **only** if required for host file rename consistency
- `fixtures/backlog/scute_host/` → `fixtures/synthetic/` when green
- `knowledge/renames/` entries produced by `harness/diff_vanilla.py` (do not invent)

## Forbidden paths

- Guessing rename maps without jar/index evidence
- Weakening score asserts
- Unrelated GUI/atlas/item-schema work

## Commands

```bash
./harness/build.sh
./harness/convert.sh --from 1.16.5 --to 1.21.11 --pack fixtures/backlog/scute_host/pack --out reports/work/hop_scute
# Expect turtle_scute host + items def on modern targets
./harness/run_l0.sh
./harness/run_tsf.sh 1.21.11
```

## Done criteria

- After convert →1.21.11: `scute` host is not left as the sole modern host; `turtle_scute` model and/or `items/turtle_scute.json` present as appropriate
- CMD custom model still referenced after rename
- Fixture moves to synthetic when score is green
- No regressions on L0 / TSF hosts

## Priority

**P2** — after items cliff; may depend on ModelConverter for CMD→items on the renamed host.
