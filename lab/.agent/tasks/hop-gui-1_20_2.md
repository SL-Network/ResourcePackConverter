# Task: hop-gui-1_20_2

**Milestone target:** TSF **1.16.5 → 1.21.11** (GUI hop is intermediate; verify it does not break the chain).

## Goal

Confirm `icons.png` / `widgets.png` slice into gui sprites on **1.20.1 → 1.20.2** (pack_format 18). Patch `GuiSlicerConverter1_20_2` only on fail.

## Allowed paths

- `conversions/Forwards/.../v1_20/textures/GuiSlicerConverter1_20_2.java` (+ gui slice helpers)
- `fixtures/synthetic/gui_split_1_20_1/`
- Optional score enhancements for sprite path asserts (coordinate with concern-harness)

## Forbidden paths

- Inventing sprite UV layouts when ambiguous — emit review findings instead
- Touching ModelConverter / atlas converters
- 26.x work

## Commands

```bash
./harness/build.sh
./harness/convert.sh --from 1.20.1 --to 1.20.2 --pack fixtures/synthetic/gui_split_1_20_1/pack --out reports/work/hop_gui
./harness/run_l0.sh
# chain sanity
./harness/run_tsf.sh 1.21.11
```

## Done criteria

- Converted pack has expected gui sprite outputs (or documented review finding if input PNG is too synthetic for UV recovery)
- `./harness/run_l0.sh` green
- No TSF regression vs baseline on GUI-related paths

## Priority

**P3** — verify-first; implement only if L0/TSF shows breakage.
