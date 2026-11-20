# Milestone 1 status — TSF 1.16.5 → 1.21.11

**Declared structurally done** (2026-11-12). Structural L0 + TSF gates are green; residuals below are out of assert scope.

## Gate results

| Command | Exit | Notes |
|---------|------|-------|
| `./harness/build.sh` | 0 | jar + symlink OK |
| `./harness/run_l0.sh` | 0 | all synthetic fixtures ok (incl. custom_ns_atlas, overrides_to_items(+_chain), scute_host, items_atlas_split) |
| `./harness/run_backlog.sh` | 0 | `still failing=0` (gap fixtures promoted to `fixtures/synthetic/`) |
| `./harness/run_tsf.sh 1.21.11` | 0 | `score_tsf.json` + `assert_tsf.json` both `ok: true` |

## Spot-check (converted TSF)

- `assets/minecraft/items/` hosts present (yellow_dye, white_stained_glass, diamond_sword/hoe, light_blue_dye, turtle_scute, …)
- `atlases/items.json` emitted; `item/` directory sources peeled out of `blocks.json`
- `atlases/blocks.json` retains `npc` / `npc/barney` sources for `tsf:npc`
- `scute` → `turtle_scute` items + models/item host

## Residuals / out of scope

1. **Custom armor under `textures/models/armor/`** — 13 TSF PNGs (noxcrew, rainbow, community, white, …) remain after ArmorMover; vanilla-ish layers moved to `entity/equipment/`. Verify-on-fail / hop-equip follow-up; not an assert failure today.
1b. **Saddle overlays (pig/strider)** — **fixed**. `SaddleEquipmentConverter1_21_5` moves `entity/<mob>/<mob>_saddle.png` → `entity/equipment/<mob>_saddle/saddle.png` (TSF blank strider saddle). L0: `fixtures/synthetic/saddle_equipment`.
2. **Block models using `item/` textures** — **fixed (copy+rewrite)**. Dual-stitch onto both atlases is rejected in-game (sprites resolve to `items.png`). `ItemsAtlasConverter1_21_11` peels `item/` into `atlases/items.json`, then copies pack textures needed by `models/block/**` to `textures/block/item/...` and rewrites those refs to `block/item/...`. L0: `fixtures/synthetic/block_item_atlas_dual`. Related: `BlockStateConverter` no longer double-prefixes already-namespaced model ids at the 1.19.3 cliff. Remaining: item models that mix `item/` with non-items atlas roots (e.g. `models/armor`) — still out of assert scope.
3. **L3** — in-game screenshots / pixel+pose validation not required for milestone 1.
4. **26.x / Bedrock / Faithful L1** — explicitly out of scope.

## Delivered converters (hop agents)

- Custom-NS atlas sources (`AtlasConverter1_19_3`)
- scute → turtle_scute host rename (`NameConverter` + items wiring)
- CMD → `items/` schema harden (`ModelConverter`)
- 1.21.11 items atlas peel (`ItemsAtlasConverter1_21_11`)
