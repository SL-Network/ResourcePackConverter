# Agent instructions — pack converter

You are working in the ResourcePackConverter fork. The Java project is the repository root. This lab (harness, fixtures, knowledge, plugins) lives in `lab/`. Goal: reliably convert Java resource packs across versions, including adventure-map packs like Terra Swoop Force.

## Layout

| Path | Purpose |
|------|---------|
| `conversions/`, `library/`, `Applications/` | Java source. Edit migration code at the repository root. |
| `lab/knowledge/` | Generated/hand rules. **Do not invent renames from memory.** |
| `lab/knowledge/renames/` | Path maps for version hops (JSON). |
| `lab/knowledge/pack_formats.json` | Behavioral rules per pack_format. |
| `lab/knowledge/vanilla/` | Extracted client assets (large; often gitignored). |
| `lab/fixtures/synthetic/` | Tiny L0 packs — must stay green. |
| `lab/fixtures/backlog/` | Known gaps (expected fail). Move to `synthetic/` when fixed. |
| `lab/fixtures/golden/faithful/` | Multi-version Faithful builds (optional L1). |
| `lab/fixtures/golden/adventure/` | TSF and similar (L2). |
| `lab/harness/` | Build / convert / score — **always use these**. |
| `lab/reports/` | Machine-readable results for the latest run. |
| `lab/docs/tsf-presentation-boards.md` | TSF boards: pack + Via/spawner rewriter playbook (26.x). |
| `lab/plugins/spawner-cmd-rewriter*` | On-wire spawner CMD / `equipment` rewrite for Via clients. |

## Hard rules

1. **Do not invent vanilla path renames.** Add them only via jar/index diffs into `knowledge/renames/`, or from documented pack_format rules.
2. **Every code change** must be verified with the harness. From the repository root, prefer:
   - `./lab/harness/build.sh`
   - `./lab/harness/run_l0.sh`
   - For adventure work: `./lab/harness/run_tsf.sh`
3. On failure, **read `reports/*.json`**, fix the converter, do **not** hand-edit expected outputs to make tests pass (unless updating fixtures deliberately and documenting why).
4. Success is **not** “bump `pack_format` only.” Models, CMD→`items/`, atlases, GUI slices, and armor paths must actually work for the target version.
5. For adventure packs: migrate what is rule-based (CMD→item defs, atlas for custom NS, host item renames). If geometry/pose is ambiguous, **emit a review finding** in the report — do not guess UVs.
6. Prefer hop-scoped converters under `conversions/Forwards` / `Backwards` matching existing style. Shared library changes need care (affect all hops).

## Test layers

- **L0** (`run_l0.sh`): synthetic fixtures — fast, must stay green.
- **Backlog** (`run_backlog.sh`): known failures (CMD/atlas/etc.) — expected red until fixed.
- **L1** (optional): Faithful `convert(A→B)` vs official B — soft path/pixel score.
- **L2** (`run_tsf.sh`): Terra Swoop Force — assert Barney / presentation / projector host wiring + `tsf:` atlas needs.
- **L3** (later): in-game screenshots — not required for hop work.

## Subagent split (recommended)

- **Hop agents**: one Minecraft version edge (e.g. `1.20.1→1.20.2`). Own only that hop’s converter + L0 fixtures for that edge. See `.agent/hop-assignments.md`.
- **Concern agents**: CMD→`items/`, atlases, equipment/armor, GUI slicing — cross-cutting.
- **Integrator**: full chains + TSF; merge conflicts on shared library.

Do not claim a hop is done until its L0 fixtures pass and the integrator’s chain has been considered if you touched shared code.

## Converter CLI

Built jar (after `./lab/harness/build.sh`):

```bash
java -jar lab/harness/ResourcePackConverter.jar \
  --input <dir-containing-pack-folders> \
  --from <version> --to <version> --debug true
```

Use `./lab/harness/convert.sh` instead of invoking the jar ad hoc.
