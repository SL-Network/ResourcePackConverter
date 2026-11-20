# Resource pack_format ↔ Java Edition releases

Source: [Minecraft Wiki – Pack format](https://minecraft.wiki/w/Pack_format) (resource pack format history).  
Snapshot-only format bumps between releases are omitted here; see the wiki for the full snapshot table.

**Important:** resource pack format ≠ data pack format (different number series on the same wiki page).

From 1.21.9 / 25w31a onward, formats can be `major.minor` (e.g. `69.0`, `75.0`) and `pack.mcmeta` may use `min_format` / `max_format` instead of only `pack_format`.

| pack_format | Minecraft releases |
|-------------|-------------------|
| 1 | 1.6.1 – 1.8.9 |
| 2 | 1.9 – 1.10.2 |
| 3 | 1.11 – 1.12.2 |
| 4 | 1.13 – 1.14.4 |
| 5 | 1.15 – 1.16.1 |
| 6 | 1.16.2 – 1.16.5 |
| 7 | 1.17 – 1.17.1 |
| 8 | 1.18 – 1.18.2 |
| 9 | 1.19 – 1.19.2 |
| 12 | 1.19.3 |
| 13 | 1.19.4 |
| 15 | 1.20 – 1.20.1 |
| 18 | 1.20.2 |
| 22 | 1.20.3 – 1.20.4 |
| 32 | 1.20.5 – 1.20.6 |
| 34 | 1.21 – 1.21.1 |
| 42 | 1.21.2 – 1.21.3 |
| 46 | 1.21.4 |
| 55 | 1.21.5 |
| 63 | 1.21.6 |
| 64 | 1.21.7 – 1.21.8 |
| 69.0 | 1.21.9 – 1.21.10 |
| 75.0 | 1.21.11 |
| 84.0 | 26.1 – 26.1.2 |
| 88.0 | 26.2 |
| 97.1 | 26.3 |

## Gaps in the integer sequence

Missing integers (10, 11, 14, 16, 17, …) are mostly snapshot-only formats that never became the sole format of a release. Full list: wiki resource pack format history.

## Project notes

- TSF v1.1.5 baseline uses `pack_format` **6** (1.16.2–1.16.5).
- Milestone 1 ceiling **1.21.11** → format **75.0**.
- Milestone 2 target **26.3** → format **97.1** (via **26.1** / **84.0**, **26.2** / **88.0**).
- Protocol ids (wiki / Mojang): **26.1–26.1.2** → 775, **26.2** → 776, **26.3** → 777 — mirrored in `library/src/main/resources/protocol.json`.
- Converter `protocol.json` should stay aligned with this table when adding targets.
