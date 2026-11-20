# SpawnerCmdRewriter (Velocity)

Proxy-side copy of [`../spawner-cmd-rewriter`](../spawner-cmd-rewriter) for networks where **ViaVersion runs on Velocity** in front of Paper 1.16 backends.

The Bukkit/Paper plugin is left unchanged. Use **this** jar on the proxy instead when Via is not on the backend.

**Full end-to-end reference** (pack converter + this plugin, symptoms, verification):  
[`../../docs/tsf-presentation-boards.md`](../../docs/tsf-presentation-boards.md)

## Server gate

Conversions run **only** while the player’s current Velocity server name starts with:

```text
terra_swoop_force
```

(configurable via `server-name-prefix` in `plugins/spawner-cmd-rewriter/config.properties`)

Examples that match: `terra_swoop_force`, `terra_swoop_force_1`, `Terra_Swoop_Force_EU`  
Examples that skip: `lobby`, `skyblock`, `survival`

Non-TSF backends never walk spawner NBT.

## Requirements

- Velocity 3.x
- ViaVersion (on the **proxy**)
- packetevents (Velocity build)

## Install

```bash
cd plugins/spawner-cmd-rewriter-velocity
mvn -q package
cp target/spawner-cmd-rewriter-velocity.jar /path/to/velocity/plugins/
```

## Logging

`config.properties` defaults to `log-conversions=true`. In the Velocity console/log you should see:

- Gate arming: `TSF gate ON for player=... server=terra_swoop_force... protocol=769 ...`
- Each rewrite: `converted spawner CMD for player=... server=... protocol=769 era=COMPONENTS_FLOATS via=CHUNK_DATA (1 spawner(s))`

`protocol` is the Via client protocol (e.g. 766 = 1.20.5 components-int, 769+ = floats). If you never see these lines on TSF, the gate name or packet hook isn’t firing.

Set `log-conversions=false` once verified.

## Tests

```bash
mvn test
```

Same CMD era fixtures as the Paper plugin, plus prefix-gate unit tests.

## Encoding eras

Same as the Paper plugin: legacy → int components (1.20.5+) → floats (1.21.4+), keyed by Via client protocol.

**1.21.5 / 26.x:** also migrates `ArmorItems`/`HandItems` → `equipment:{head:…}`. Those clients
ignore `ArmorItems` on armor stands (25w03a), so CMD-only rewrites on the old lists never show
presentation boards.
