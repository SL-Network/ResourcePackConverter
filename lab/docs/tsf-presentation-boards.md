# TSF presentation boards — end-to-end playbook

How Terra Swoop Force (and similar adventure maps) show “presentation boards,” what breaks on modern clients, and everything this lab needed to make them work again through **26.3**.

Use this when boards are purple/black, plain glass, or missing on Via clients while `/give` of the same CMD item looks fine.

---

## Architecture (two halves)

Presentation boards are **not** real entities in the world save sense for most props. TSF fakes them with:

| Layer | What it is |
|-------|------------|
| **World (Paper 1.16)** | `minecraft:mob_spawner` whose `SpawnData` is an armor stand wearing `white_stained_glass` with `tag:{CustomModelData:N}` on the head (`ArmorItems[3]`) |
| **Resource pack** | CMD overrides on `white_stained_glass` → `item/presentation/*` models (Blockbench, often `texture_size` 512) |
| **Modern client** | Needs (1) converted pack (`items/` + valid models) and (2) on-wire item NBT in the **client’s** item/equipment shape |

`/data get block …` always shows **legacy world NBT**. That does **not** mean the rewrite failed — the Velocity/Paper plugin never edits the world; it only mutates outgoing `CHUNK_DATA` / `BLOCK_ENTITY_DATA`.

```text
Paper 1.16 world NBT (unchanged)
        │
        ▼
Velocity + ViaVersion  ──►  client protocol bytes
        │
        ▼
SpawnerCmdRewriter (PE)  ──►  CMD components + equipment map
        │
        ▼
Client + converted resource pack  ──►  presentation model
```

Both halves are required. Fixing only the pack → give-test works, spawners don’t. Fixing only the plugin → spawners can select CMD but models may purple/black.

---

## Half A — Resource pack converter

Harness:

```bash
./harness/build.sh
./harness/run_l0.sh
./harness/run_tsf.sh 26.3
```

Then re-convert the live pack with the new jar and reload it on the client.

### A1. CMD → `assets/minecraft/items/`

- Hop: **1.21.4+** (`ModelConverter` override migration).
- Hosts like `white_stained_glass` become `items/white_stained_glass.json` (`range_dispatch` on `custom_model_data`).
- TSF: CMD **1 → presentation/a**, …, **10 → presentation/j**, etc.

Fixture / assert: `overrides_to_items*`, `assert_tsf` host_ok for `white_stained_glass`.

### A2. Valid host model after stripping overrides

Adventure hosts are often **textures + overrides only** (no `parent` / `elements`). After CMD migration the leftover `models/item/<host>.json` is an invalid bake target while `items/<host>.json` fallback still points at `minecraft:item/<host>` → **purple/black** even when CMD is correct.

**Fix:** `ensureValidHostModel` — if no parent/elements, set `parent` to `minecraft:block/<stem>` (e.g. `minecraft:block/white_stained_glass`).

Fixture: `fixtures/synthetic/overrides_host_fallback`.

### A3. Clamp face UVs to `[0, 16]` for ≥26.1

Blockbench often emits `16.03125`. With `texture_size: [512, 512]` that becomes pixel **513** on a 512² sprite. 26.x `NativeImage.computeTransparency` throws:

```text
Cannot compute translucency out of bounds: [0, 512, 0, 513] in 512x512 image
Unable to bake item model: 'minecraft:white_stained_glass'
```

**Fix:** `ModelConverter.clampElementFaceUvs` when `to ≥ 26.1`.

### A4. Recurse into nested model folders

`ModelConverter.findFiles` previously only remapped **one** directory level under `models/` (e.g. `item/*.json` but not `item/presentation/*.json`). UV clamp and other remaps never touched presentation bases (~2400 nested TSF models).

**Fix:** recursive `findFiles` over all `.json` under `assets/minecraft/models`.

Fixture: `fixtures/synthetic/model_uv_clamp_26` (model under `item/presentation/base.json`).

Score: `check_model_uvs_in_range` in `harness/score.py` for targets ≥26.1.

### A5. Related pack work (boards adjacent)

Not unique to presentation, but required for TSF→26.x overall:

- Custom-NS atlas (`tsf:`)
- Items atlas peel + `block/item/` copy-rewrite (1.21.11)
- Incompatible core/post shader strip (≥26.1 Vulkan/SPIR-V)
- Host renames (e.g. scute → turtle_scute)

---

## Half B — Spawner CMD rewriter (proxy or Paper)

### Why a plugin

ViaVersion **does not** reliably upgrade `CustomModelData` on items nested under mob-spawner `SpawnData` / `SpawnPotentials` (its block-entity CMD fix only covered top-level `item` tags, e.g. brushable blocks). Modern clients then never hit `items/*.json` predicates → plain glass on the stand.

### Which jar

| Via location | Use |
|--------------|-----|
| Via on **Velocity** (TSF setup) | `plugins/spawner-cmd-rewriter-velocity` |
| Via on **Paper** backend | `plugins/spawner-cmd-rewriter` |

Do **not** rely on the Paper jar if Via only runs on the proxy — Paper never sees modern protocol versions.

### B1. Wire formats by client protocol

| Protocol | Era | On-wire item CMD |
|----------|-----|------------------|
| ≤765 (through 1.20.4) | `LEGACY_TAG` | `tag:{CustomModelData:N}` (no rewrite) |
| 766–768 (1.20.5–1.21.3) | `COMPONENTS_INT` | `components."minecraft:custom_model_data": N` (+ dual-write legacy tag) |
| ≥769 (1.21.4+, 26.x) | `COMPONENTS_FLOATS` | `components."minecraft:custom_model_data":{floats:[Nf]}` (+ dual-write) |

Constants: `ClientItemEra` in both plugin trees.

### B2. ArmorItems → equipment (1.21.5 / 26.x) — the spawner-specific killer

From **25w03a / 1.21.5** (protocol **770**):

> `ArmorItems`, `HandItems`, and `body_armor_item` merged into `equipment`  
> (`head`, `chest`, `legs`, `feet`, `mainhand`, `offhand`, `body`, `saddle`).

Modern clients **ignore** `ArmorItems` on armor stands. Rewriting CMD onto `ArmorItems[3]` alone is not enough — the stand shows with an empty/default head.

**Fix:** for `protocol ≥ 770`, migrate lists → `equipment` and put CMD on `equipment.head` (etc.), then remove the legacy lists.

Example (SpawnData CMD 10 → presentation/j):

```text
equipment:{
  head:{
    id:"minecraft:white_stained_glass",
    count:1,
    components:{"minecraft:custom_model_data":{floats:[10.0f]}},
    tag:{CustomModelData:10}
  }
}
```

### B3. Packet path details

- Hook: PacketEvents `PacketSendEvent` on `CHUNK_DATA` + `BLOCK_ENTITY_DATA`
- Priority: `LOWEST` (among PE listeners; Via is a separate Netty codec)
- Must `markForReEncode(true)` after mutating wrappers
- PE `NBTCompound.getTags()` is unmodifiable — `removeTag` / `setTag`, never `Map.clear()`
- Velocity **gate**: only while current server name starts with `terra_swoop_force` (`config.properties` → `server-name-prefix`)

### B4. Install (Velocity)

```bash
cd plugins/spawner-cmd-rewriter-velocity
mvn -q test package
cp target/spawner-cmd-rewriter-velocity.jar /path/to/velocity/plugins/
```

Requirements: Velocity 3.x, ViaVersion on proxy, packetevents (Velocity).

`plugins/spawner-cmd-rewriter/config.properties` (data dir):

```properties
server-name-prefix=terra_swoop_force
log-conversions=true
```

Expect logs:

```text
[SpawnerCmdRewriter] TSF gate ON for player=... server=terra_swoop_force... protocol=777 ...
[SpawnerCmdRewriter] converted spawner CMD ... era=COMPONENTS_FLOATS via=CHUNK_DATA ... head={...equipment...floats:[10...}
```

If gate never ON → fix Velocity server name / prefix.  
If gate ON but no CHUNK rewrite → PE/Via parse issue; check `CHUNK_DATA hook failed` warnings.

Set `log-conversions=false` once verified.

### B5. Tests

```bash
cd plugins/spawner-cmd-rewriter-velocity && mvn test
# includes UserBoardSpawnerTest: real TSF-shaped SNBT → equipment.head CMD 10 @ protocol 777
```

Paper twin: `plugins/spawner-cmd-rewriter` (same converter logic).

---

## Symptom → cause cheat sheet

| What you see | Likely cause | Where to look |
|--------------|--------------|---------------|
| `/give` glass + CMD = purple/black | Invalid host model and/or UV OOB bake | A2, A3, A4; client log `Unable to bake item model` |
| `/give` works; spawner = plain glass / empty head | Wire NBT not upgraded / still ArmorItems-only on 26.x | B1, B2; Velocity rewrite logs |
| `/data get` still shows `tag.CustomModelData` | **Expected** (world NBT) | Do not use as rewrite proof |
| No rewrite logs on TSF | Gate prefix / plugin not loaded / Via not on proxy | B3, B4 |
| Bake error `[0,512,0,513] in 512x512` | UV `16.03125` on presentation models | A3, A4; re-convert pack |

---

## Example world SNBT (presentation board)

From a live TSF spawner (legacy world form):

```text
SpawnData: {
  id: "minecraft:armor_stand",
  ArmorItems: [{}, {}, {}, {
    id: "minecraft:white_stained_glass",
    Count: 1b,
    tag: {CustomModelData: 10}
  }],
  Pose: {Head: [-30.0f, 180.0f, 0.0f]}
}
SpawnPotentials: [{ Weight: 1, Entity: { … CustomModelData: 1 … } }]
```

Display uses **SpawnData** (here CMD **10** → `presentation/j`). Potentials may differ; rewrite both.

---

## Do not “fix” by remaking the map (unless you choose to)

One SNBT shape cannot be natively correct for both 1.16 and 26.x item/equipment formats. Options:

1. **Keep 1.16 world + convert pack + proxy rewriter** (this playbook) — preferred for Via multi-version.
2. Replace spawner fakes with real armor-stand entities (Via upgrades living gear more often) — map edit.
3. Remake props with no CMD / `item_display` — modern-only; breaks 1.16.

---

## Code map

| Concern | Location |
|---------|----------|
| Host fallback + UV clamp + recursive models | `conversions/Forwards/.../ModelConverter.java` |
| CMD → items/ | same + forwards items converters |
| L0 UV / host fixtures | `fixtures/synthetic/model_uv_clamp_26`, `overrides_host_fallback` |
| Score UV check | `harness/score.py` → `check_model_uvs_in_range` |
| CMD eras + equipment migration | `plugins/*/…/nbt/ClientItemEra.java`, `ItemCmdConverter.java` |
| Velocity PE hook + gate | `…/via/SpawnerBlockEntityRewriter.java`, `…/velocity/TsfServerGate.java` |
| Plugin install notes | `plugins/spawner-cmd-rewriter(-velocity)/README.md` |

---

## Verification checklist (26.3 client on TSF)

1. Converted pack loaded (re-convert after converter changes).
2. Velocity has latest `spawner-cmd-rewriter-velocity.jar`; Via + packetevents present.
3. Backend server name matches `server-name-prefix`.
4. Logs: gate ON + CHUNK/BE rewrite with `equipment` / `floats:[N]`.
5. In-game: board shows presentation art (not plain glass / purple-black).
6. Optional: `/give` same CMD still works (pack sanity).
7. Turn off `log-conversions` when done.
