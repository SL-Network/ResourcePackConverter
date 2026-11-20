# Minecraft resource pack converter lab

Environment for developing and regression-testing a Java resource pack converter (fork of [agentdid127/ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter)), with fixtures for vanilla migrations and adventure-map packs (e.g. Terra Swoop Force).

## Quick start

```bash
cd ~/SL-ResourcePackConverter
./lab/harness/build.sh          # Maven build → Console jar + symlink
./lab/harness/run_l0.sh         # synthetic fixtures (must stay green)
./lab/harness/run_backlog.sh    # known gaps (expected fail)
./lab/harness/run_tsf.sh        # TSF adventure asserts (1.16.5 → 1.21.4 default)
```

Reports land in `lab/reports/`. Agent rules: `lab/AGENTS.md`. Hop split: `lab/.agent/hop-assignments.md`.

## Tree

```text
conversions/        # Java migration code (repository root)
library/
Applications/
lab/
  harness/          # build, convert, score, run_*
  fixtures/
    synthetic/      # L0 — green
    backlog/        # known gaps
    golden/adventure/ # tsf_1.16.5 → your TSF resources
    golden/faithful/  # optional multi-version oracles
  knowledge/
    pack_formats.json
    renames/        # from diff_vanilla.py
    vanilla/        # from fetch_vanilla.sh (gitignored)
  reports/
  plugins/
    spawner-cmd-rewriter/           # Paper 1.16.4 + Via on backend
    spawner-cmd-rewriter-velocity/  # Velocity + Via on proxy; TSF server-name gate
```

## Related plugins

- [`plugins/spawner-cmd-rewriter`](plugins/spawner-cmd-rewriter/): Paper 1.16.4 plugin (ViaVersion + packetevents) that rewrites mob-spawner preview `CustomModelData` for 1.20.5–26.3 clients. Use when Via is on the backend.
- [`plugins/spawner-cmd-rewriter-velocity`](plugins/spawner-cmd-rewriter-velocity/): Velocity copy for proxy-side Via; only rewrites while the player’s server name starts with `terra_swoop_force`.

**Playbook (pack + proxy):** [`docs/tsf-presentation-boards.md`](docs/tsf-presentation-boards.md) — everything needed for TSF presentation boards on Via/26.x (CMD→`items/`, host fallback, UV clamp, nested models, ArmorItems→`equipment`).

## Optional next steps

1. Download Faithful builds into `fixtures/golden/faithful/` ([archive](https://faithfulpack.net/archive)).
2. `./lab/scripts/fetch_vanilla.sh 1.20.1 1.20.2` then `python3 lab/harness/diff_vanilla.py --from-ver 1.20.1 --to-ver 1.20.2`.
3. Add PCRT public packs under `fixtures/synthetic/` for extra L0 coverage.
4. Spin hop subagents per `.agent/hop-assignments.md`; integrator owns `run_tsf.sh`.
