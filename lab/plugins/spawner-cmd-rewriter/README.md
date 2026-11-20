# SpawnerCmdRewriter

Paper **1.16.4** plugin that rewrites **mob-spawner preview item** `CustomModelData` for ViaVersion clients so adventure-map props (e.g. Terra Swoop Force presentation boards) render with the correct resource-pack model on modern clients.

**Full end-to-end reference** (resource-pack converter + plugins, 26.x `equipment` migration, verification):  
[`../../docs/tsf-presentation-boards.md`](../../docs/tsf-presentation-boards.md)

For **Via on Velocity**, use [`../spawner-cmd-rewriter-velocity`](../spawner-cmd-rewriter-velocity/) instead of this jar.

## Why

Maps often fake props with a `minecraft:mob_spawner` whose `SpawnData` is an armor stand holding e.g. `white_stained_glass` + `tag.CustomModelData`. The 1.16 world keeps that legacy NBT. ViaVersion does **not** reliably upgrade nested spawner item CMD for 1.20.5+ clients, so those clients ignore the pack’s `items/*.json` predicates and show plain glass.

This plugin leaves **world NBT unchanged**. It only reshapes the copy in outgoing `BLOCK_ENTITY_DATA` / `CHUNK_DATA` packets per the player’s Via protocol:

| Client protocols | CMD / gear on the wire |
|---|---|
| 1.16.4 → 1.20.4 (`≤765`) | unchanged `tag.CustomModelData` |
| 1.20.5 → 1.21.3 (`766–768`) | `components."minecraft:custom_model_data": N` |
| 1.21.4 (`769`) | `components."minecraft:custom_model_data":{floats:[Nf]}` on `ArmorItems` |
| 1.21.5 → 26.3 (`≥770`) | same CMD floats, plus `ArmorItems`/`HandItems` → `equipment:{head:…}` |

Dual-write keeps legacy `tag.CustomModelData` alongside components when reshaping for Via mid-pipeline safety.

## Requirements

- Paper (or compatible) **1.16.4**
- **ViaVersion** on the **same Paper server** (not proxy-only — otherwise Paper never sees modern protocols)
- **packetevents** plugin (2.7+; listed in `plugin.yml` `depend` with ViaVersion)

Pinned compile APIs (see `pom.xml`): ViaVersion API `5.3.2`, PacketEvents `2.7.0`. Protocol thresholds are hardcoded ints so newer calendar releases (26.x) keep working as long as Via reports the correct protocol number.

## Install

```bash
cd plugins/spawner-cmd-rewriter
mvn -q package
cp target/spawner-cmd-rewriter.jar /path/to/server/plugins/
```

Enable `debug: true` in `plugins/SpawnerCmdRewriter/config.yml` to log rewrites.

## Tests

Pure unit tests (no live server):

```bash
mvn test
```

Fixtures include the TSF projector-style spawner SNBT under `src/test/resources/fixtures/`.

## Out of scope

- Living armor stands / entity equipment packets (only spawner block-entity preview NBT)
- Resource-pack conversion (use the fork at the repository root + [`docs/tsf-presentation-boards.md`](../../docs/tsf-presentation-boards.md))
- Proxy-only ViaVersion — use [`../spawner-cmd-rewriter-velocity`](../spawner-cmd-rewriter-velocity/)
