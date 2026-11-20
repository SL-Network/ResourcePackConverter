#!/usr/bin/env python3
"""Score a converted pack. Emits reports/score_<name>.json and exit 1 on failure."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        return {"_error": str(e)}


def _needs_min_max_format(target_hint: str | None) -> bool:
    """1.21.9+ and calendar 26.x require min_format/max_format (pack_format alone → UI incompatible)."""
    if not target_hint:
        return False
    if _target_is_26_or_later(target_hint):
        return True
    return _target_at_least(target_hint, 1, 21, 9)


def check_pack_mcmeta(pack: Path, target_hint: str | None = None) -> list[dict]:
    findings = []
    meta = pack / "pack.mcmeta"
    if not meta.exists():
        findings.append({"level": "error", "id": "missing_pack_mcmeta", "msg": "pack.mcmeta missing"})
        return findings
    data = load_json(meta)
    if "_error" in data:
        findings.append({"level": "error", "id": "bad_pack_mcmeta", "msg": data["_error"]})
        return findings
    pack_obj = data.get("pack") or {}
    if "pack_format" not in pack_obj and "min_format" not in pack_obj:
        findings.append({"level": "error", "id": "no_pack_format", "msg": "no pack_format/min_format"})
    if _needs_min_max_format(target_hint):
        if "min_format" not in pack_obj or "max_format" not in pack_obj:
            findings.append(
                {
                    "level": "error",
                    "id": "missing_min_max_format",
                    "msg": "1.21.9+/26.x require min_format and max_format (pack_format alone shows incompatible in UI)",
                }
            )
        elif isinstance(pack_obj.get("min_format"), float) or isinstance(pack_obj.get("max_format"), float):
            # Gson doubles like 97.1 are not valid format tokens; game wants int or [major, minor].
            findings.append(
                {
                    "level": "error",
                    "id": "float_min_max_format",
                    "msg": "min_format/max_format must be int or [major, minor], not a float like 97.1",
                }
            )
    return findings


def _target_at_least(target_hint: str | None, major: int, minor: int, patch: int) -> bool:
    if not target_hint:
        return False
    parts = target_hint.split(".")
    try:
        if parts[0] == "26":
            return True
        t_major = int(parts[0])
        t_minor = int(parts[1]) if len(parts) > 1 else 0
        t_patch = int(parts[2]) if len(parts) > 2 else 0
        return (t_major, t_minor, t_patch) >= (major, minor, patch)
    except ValueError:
        return False


def _target_is_26_or_later(target_hint: str | None) -> bool:
    """Calendar versions 26.x (and later major) after the 1.21.x line."""
    if not target_hint:
        return False
    try:
        return int(str(target_hint).split(".")[0]) >= 26
    except ValueError:
        return False


def _target_at_least_26_3(target_hint: str | None) -> bool:
    """True for calendar 26.3+ (not 26.1/26.2)."""
    if not target_hint:
        return False
    parts = str(target_hint).split(".")
    try:
        major = int(parts[0])
        if major > 26:
            return True
        if major < 26:
            return False
        minor = int(parts[1]) if len(parts) > 1 else 0
        return minor >= 3
    except ValueError:
        return False


def _target_at_least_26_2(target_hint: str | None) -> bool:
    """True for calendar 26.2+ (not 26.1)."""
    if not target_hint:
        return False
    parts = str(target_hint).split(".")
    try:
        major = int(parts[0])
        minor = int(parts[1]) if len(parts) > 1 else 0
        if major < 26:
            return False
        return (major, minor) >= (26, 2)
    except ValueError:
        return False


def check_entity_layout_26_1(pack: Path, target_hint: str | None) -> list[dict]:
    """26.1+: sample of entity textures must leave pre-84.0 paths.

    Skips when the pack has none of the sampled old paths (no false fail).
    Full move table lives in EntityTextureLayoutConverter26_1.
    """
    findings: list[dict] = []
    if not _target_is_26_or_later(target_hint):
        return findings

    textures = pack / "assets" / "minecraft" / "textures"
    samples = [
        ("entity/bat.png", "entity/bat/bat.png"),
        ("entity/witch.png", "entity/witch/witch.png"),
        ("entity/armadillo.png", "entity/armadillo/armadillo.png"),
        ("entity/cat/tabby.png", "entity/cat/cat_tabby.png"),
        ("entity/rabbit/gold.png", "entity/rabbit/rabbit_gold.png"),
        ("entity/pig/cold_pig.png", "entity/pig/pig_cold.png"),
        ("entity/armorstand/wood.png", "entity/armorstand/armorstand.png"),
        ("entity/turtle/big_sea_turtle.png", "entity/turtle/turtle.png"),
        ("entity/projectiles/tipped_arrow.png", "entity/projectiles/arrow_tipped.png"),
        ("entity/projectiles/spectral_arrow.png", "entity/projectiles/arrow_spectral.png"),
    ]
    for old_rel, new_rel in samples:
        old = textures / Path(old_rel)
        new = textures / Path(new_rel)
        if old.is_file() and not new.is_file():
            findings.append(
                {
                    "level": "error",
                    "id": "entity_layout_26_1_not_moved",
                    "msg": f"26.1+ still has textures/{old_rel} without textures/{new_rel}",
                }
            )
        elif new.is_file():
            findings.append(
                {
                    "level": "info",
                    "id": "entity_layout_26_1_present",
                    "msg": f"textures/{new_rel} present",
                }
            )
    return findings


def check_beds_signs_pillars_26_2(pack: Path, target_hint: str | None) -> list[dict]:
    """26.2+: beds/signs atlases gone; pillar sides renamed; leftover entity beds/signs → review."""
    findings: list[dict] = []
    if not _target_at_least_26_2(target_hint):
        return findings

    atlases = pack / "assets" / "minecraft" / "atlases"
    for name in ("beds.json", "signs.json"):
        if (atlases / name).is_file():
            findings.append(
                {
                    "level": "error",
                    "id": "beds_signs_atlas_still_present_26_2",
                    "msg": f"26.2+ still has atlases/{name} (removed in pack_format 88.0)",
                }
            )

    textures = pack / "assets" / "minecraft" / "textures"
    for old_rel, new_rel in (
        ("block/purpur_pillar.png", "block/purpur_pillar_side.png"),
        ("block/quartz_pillar.png", "block/quartz_pillar_side.png"),
    ):
        old = textures / Path(old_rel)
        new = textures / Path(new_rel)
        if old.is_file() and not new.is_file():
            findings.append(
                {
                    "level": "error",
                    "id": "pillar_side_26_2_not_moved",
                    "msg": f"26.2+ still has textures/{old_rel} without textures/{new_rel}",
                }
            )
        elif new.is_file():
            findings.append(
                {
                    "level": "info",
                    "id": "pillar_side_26_2_present",
                    "msg": f"textures/{new_rel} present",
                }
            )

    bed_dir = textures / "entity" / "bed"
    if bed_dir.is_dir() and any(bed_dir.glob("*.png")):
        findings.append(
            {
                "level": "review",
                "id": "bed_entity_texture_needs_slicer",
                "msg": "textures/entity/bed/*.png remain; 26.2 needs block/<color>_bed_* slices (Mojang Slicer) — UVs not invented",
            }
        )
    elif any((textures / "block").glob("*_bed_*.png")):
        findings.append(
            {
                "level": "info",
                "id": "bed_entity_sliced_26_2",
                "msg": "entity bed textures sliced into block/*_bed_*",
            }
        )

    signs_dir = textures / "entity" / "signs"
    if signs_dir.is_dir() and any(signs_dir.rglob("*.png")):
        findings.append(
            {
                "level": "review",
                "id": "sign_entity_texture_needs_slicer",
                "msg": "textures/entity/signs/** remain; 26.2 needs block/<wood>_sign slices (Mojang Slicer) — UVs not invented",
            }
        )
    elif (textures / "block" / "oak_sign.png").is_file() or any(
        (textures / "block").glob("*_sign.png")
    ):
        findings.append(
            {
                "level": "info",
                "id": "sign_entity_sliced_26_2",
                "msg": "entity sign textures sliced into block/*_sign",
            }
        )

    review_path = pack / "conversion_review.json"
    if review_path.is_file():
        data = load_json(review_path)
        for item in data.get("findings") or []:
            if isinstance(item, dict) and item.get("id"):
                # Avoid duplicating the generic bed/sign review ids already emitted above.
                if item.get("id") in (
                    "bed_entity_texture_needs_slicer",
                    "sign_entity_texture_needs_slicer",
                ):
                    continue
                findings.append(item)

    return findings


def _looks_like_pre_vulkan_glsl(path: Path) -> bool:
    """Heuristic for OpenGL-era core shaders that break RenderPearl/SPIR-V."""
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return False
    if "gl_VertexID" in text:
        return True
    if "#version 150" in text or "#version 120" in text:
        return True
    # Bare uniforms / ins without layout(location) — common pre-26.1 pattern.
    if "layout(location" not in text and (
        "\nin vec" in text or text.startswith("in vec") or "\nuniform mat4" in text
    ):
        return True
    return False


def check_core_shaders_stripped_26_1(pack: Path, target_hint: str | None) -> list[dict]:
    """≥26.1: pre-Vulkan pack shaders/core|post must not remain (strip on ≤1.21.11→26.x)."""
    findings: list[dict] = []
    if not _target_is_26_or_later(target_hint):
        return findings

    shaders = pack / "assets" / "minecraft" / "shaders"
    pre_vulkan: list[str] = []
    for sub in ("core", "post"):
        d = shaders / sub
        if not d.is_dir():
            continue
        for p in d.rglob("*"):
            if not p.is_file():
                continue
            if p.suffix.lower() in (".vsh", ".fsh", ".glsl") and _looks_like_pre_vulkan_glsl(p):
                pre_vulkan.append(str(p.relative_to(pack)))

    review_ids: set[str] = set()
    review_path = pack / "conversion_review.json"
    if review_path.is_file():
        data = load_json(review_path)
        for item in data.get("findings") or []:
            if isinstance(item, dict) and item.get("id"):
                review_ids.add(str(item["id"]))

    if pre_vulkan:
        findings.append(
            {
                "level": "error",
                "id": "core_shaders_not_stripped_26_1",
                "msg": "≥26.1 pack still has OpenGL-era shaders/core|post GLSL — "
                "breaks RenderPearl/Vulkan; expect IncompatibleCoreShaderStripper26_1",
                "examples": pre_vulkan[:15],
                "count": len(pre_vulkan),
            }
        )
    elif "core_shaders_stripped_vulkan_26_1" in review_ids:
        findings.append(
            {
                "level": "info",
                "id": "core_shaders_stripped_vulkan_26_1",
                "msg": "pack core/post shaders stripped for ≥26.1 (Vulkan/SPIR-V)",
            }
        )
    return findings


def _parse_texture_id(raw: str, default_ns: str = "minecraft") -> tuple[str, str] | None:
    """Return (namespace, path) for a texture/palette id, or None if empty."""
    if not isinstance(raw, str):
        return None
    id_ = raw.replace("\\", "/").strip()
    if not id_:
        return None
    if ":" in id_:
        ns, path = id_.split(":", 1)
    else:
        ns, path = default_ns, id_
    if not path:
        return None
    return ns, path


def _is_trim_or_already_palette_path(path: str) -> bool:
    p = path.replace("\\", "/")
    return (
        p.startswith("palettes/")
        or p == "trim_base"
        or p.startswith("trim/")
        or p.startswith("trims/color_palettes/")
    )


def check_custom_palettes_26_3(pack: Path, target_hint: str | None) -> list[dict]:
    """26.3+: atlas paletted_permutations palette ids must live under textures/palettes/.

    Ignores vanilla trim ids already covered by check_trim_palettes_26_3.
    Error when textures/<id>.png still exists and textures/palettes/<id>.png does not.
    """
    findings: list[dict] = []
    if not _target_at_least_26_3(target_hint):
        return findings

    assets = pack / "assets"
    if not assets.is_dir():
        return findings

    seen: set[tuple[str, str]] = set()
    for atlas in assets.glob("*/atlases/*.json"):
        ns_default = atlas.parent.parent.name
        data = load_json(atlas)
        if not isinstance(data, dict):
            continue
        for src in data.get("sources") or []:
            if not isinstance(src, dict) or _atlas_type_name(src) != "paletted_permutations":
                continue
            ids: list[str] = []
            pk = src.get("palette_key")
            if isinstance(pk, str):
                ids.append(pk)
            perms = src.get("permutations")
            if isinstance(perms, dict):
                for v in perms.values():
                    if isinstance(v, str):
                        ids.append(v)
            for raw in ids:
                parsed = _parse_texture_id(raw, ns_default)
                if parsed is None:
                    continue
                ns, path = parsed
                if _is_trim_or_already_palette_path(path):
                    continue
                key = (ns, path)
                if key in seen:
                    continue
                seen.add(key)
                textures = assets / ns / "textures"
                old = textures / Path(path + ".png")
                new = textures / "palettes" / Path(path + ".png")
                if old.is_file() and not new.is_file():
                    findings.append(
                        {
                            "level": "error",
                            "id": "custom_palette_not_moved_26_3",
                            "msg": (
                                f"26.3+ atlas palette {ns}:{path} still at textures/{path}.png "
                                f"without textures/palettes/{path}.png"
                            ),
                        }
                    )
    return findings


def check_trim_palettes_26_3(pack: Path, target_hint: str | None) -> list[dict]:
    """26.3+: trim color palettes under textures/palettes/; armor_trims atlas gone."""
    findings: list[dict] = []
    if not _target_at_least_26_3(target_hint):
        return findings

    textures = pack / "assets" / "minecraft" / "textures"
    old_dir = textures / "trims" / "color_palettes"
    if old_dir.is_dir() and any(old_dir.glob("*.png")):
        findings.append(
            {
                "level": "error",
                "id": "trim_color_palettes_not_moved_26_3",
                "msg": "26.3+ still has textures/trims/color_palettes/*.png — expect textures/palettes/trim/ (+ trim_base.png)",
            }
        )

    atlas = pack / "assets" / "minecraft" / "atlases" / "armor_trims.json"
    if atlas.is_file():
        findings.append(
            {
                "level": "error",
                "id": "armor_trims_atlas_still_present_26_3",
                "msg": "26.3+ still has atlases/armor_trims.json (removed — entity trims on-demand)",
            }
        )

    # If pack shipped a migrated custom palette, require base_palette mcmeta.
    trim_dir = textures / "palettes" / "trim"
    if trim_dir.is_dir():
        for png in trim_dir.glob("*.png"):
            meta = Path(str(png) + ".mcmeta")
            if not meta.is_file():
                findings.append(
                    {
                        "level": "error",
                        "id": "trim_palette_mcmeta_missing_base_26_3",
                        "msg": f"26.3+ palette {png.relative_to(pack)} missing .mcmeta with palette.base_palette",
                    }
                )
                continue
            data = load_json(meta)
            base = ((data or {}).get("palette") or {}).get("base_palette")
            if not base:
                findings.append(
                    {
                        "level": "error",
                        "id": "trim_palette_mcmeta_missing_base_26_3",
                        "msg": f"{meta.relative_to(pack)} lacks palette.base_palette",
                    }
                )
            else:
                findings.append(
                    {
                        "level": "info",
                        "id": "trim_palette_base_present_26_3",
                        "msg": f"{meta.relative_to(pack)} base_palette={base}",
                    }
                )

    items_atlas = pack / "assets" / "minecraft" / "atlases" / "items.json"
    if items_atlas.is_file():
        data = load_json(items_atlas)
        raw = json.dumps(data)
        if "trims/color_palettes" in raw:
            findings.append(
                {
                    "level": "error",
                    "id": "items_atlas_legacy_trim_palette_refs_26_3",
                    "msg": "atlases/items.json still references trims/color_palettes — expect palettes ids (trim/…, trim_base)",
                }
            )

    # Leftover override_armor_assets in combined packs is an error for 26.3+.
    data_root = pack / "data"
    if data_root.is_dir():
        for mat in data_root.glob("*/trim_material/*.json"):
            doc = load_json(mat)
            if isinstance(doc, dict) and "override_armor_assets" in doc:
                findings.append(
                    {
                        "level": "error",
                        "id": "override_armor_assets_still_present_26_3",
                        "msg": f"{mat.relative_to(pack)} still has override_armor_assets — expect equipment trim_overrides",
                    }
                )
            if isinstance(doc, dict) and "asset_name" in doc and "palette_id" not in doc:
                findings.append(
                    {
                        "level": "error",
                        "id": "trim_material_asset_name_still_present_26_3",
                        "msg": f"{mat.relative_to(pack)} still has asset_name without palette_id",
                    }
                )

    return findings


def check_saddle_equipment_migrate(pack: Path, target_hint: str | None) -> list[dict]:
    """1.21.5+: pig/strider entity saddle overlays must live under entity/equipment."""
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 5):
        return findings

    textures = pack / "assets" / "minecraft" / "textures"
    moves = [
        (
            textures / "entity" / "pig" / "pig_saddle.png",
            textures / "entity" / "equipment" / "pig_saddle" / "saddle.png",
            "pig_saddle",
        ),
        (
            textures / "entity" / "strider" / "strider_saddle.png",
            textures / "entity" / "equipment" / "strider_saddle" / "saddle.png",
            "strider_saddle",
        ),
    ]
    for old, new, label in moves:
        if old.is_file() and not new.is_file():
            findings.append(
                {
                    "level": "error",
                    "id": f"saddle_overlay_not_moved_{label}",
                    "msg": f"1.21.5+ still has {old.relative_to(pack)} without equipment/{label}/saddle.png",
                }
            )
        elif new.is_file():
            findings.append(
                {
                    "level": "info",
                    "id": f"saddle_equipment_present_{label}",
                    "msg": f"equipment/{label}/saddle.png present",
                }
            )
    return findings


def check_custom_armor_equipment(pack: Path, target_hint: str | None) -> list[dict]:
    """1.21.2+: custom models/armor leftovers must also have an equipment copy.

    Packs with no models/armor overrides are skipped (no false fail). Layer-named
    files should already be moved; residual non-layer PNGs stay under models/armor
    for item models but must be copied into entity/equipment/humanoid*.
    """
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 2):
        return findings

    models_armor = pack / "assets" / "minecraft" / "textures" / "models" / "armor"
    if not models_armor.is_dir():
        return findings

    pngs = sorted(p for p in models_armor.glob("*.png") if p.is_file())
    if not pngs:
        return findings

    humanoid = pack / "assets" / "minecraft" / "textures" / "entity" / "equipment" / "humanoid"
    leggings = (
        pack / "assets" / "minecraft" / "textures" / "entity" / "equipment" / "humanoid_leggings"
    )

    missing: list[str] = []
    for png in pngs:
        stem = png.stem
        # Layer-pattern leftovers should have been moved — still require equipment peer.
        candidates = [
            humanoid / f"{stem}.png",
            leggings / f"{stem}.png",
        ]
        if stem.endswith("_layer_1"):
            material = stem[: -len("_layer_1")]
            candidates.append(humanoid / f"{material}.png")
        elif stem.endswith("_layer_2"):
            material = stem[: -len("_layer_2")]
            candidates.append(leggings / f"{material}.png")
        elif stem.endswith("_layer_1_overlay"):
            material = stem[: -len("_layer_1_overlay")]
            candidates.append(humanoid / f"{material}_overlay.png")
        elif stem.endswith("_layer_2_overlay"):
            material = stem[: -len("_layer_2_overlay")]
            candidates.append(leggings / f"{material}_overlay.png")
        elif stem == "turtle_layer_1":
            candidates.append(humanoid / "turtle_scute.png")

        if not any(c.is_file() for c in candidates):
            missing.append(stem)

    if missing:
        findings.append(
            {
                "level": "error",
                "id": "custom_armor_no_equipment_copy",
                "msg": (
                    "1.21.2+ models/armor leftovers lack entity/equipment/humanoid* copy: "
                    + ", ".join(missing[:12])
                ),
                "count": len(missing),
            }
        )
    else:
        findings.append(
            {
                "level": "info",
                "id": "custom_armor_equipment_ok",
                "msg": f"{len(pngs)} models/armor png(s) have equipment copies",
            }
        )

    # Non-vanilla residual materials need equipment/<id>.json for worn layers.
    vanilla_equip = {
        "leather", "chainmail", "iron", "gold", "diamond", "netherite", "copper",
        "turtle_scute", "armadillo_scute", "elytra", "saddle",
    }
    equip_dir = pack / "assets" / "minecraft" / "equipment"
    humanoid_dir = pack / "assets" / "minecraft" / "textures" / "entity" / "equipment" / "humanoid"
    if humanoid_dir.is_dir():
        missing_json: list[str] = []
        for png in sorted(humanoid_dir.glob("*.png")):
            stem = png.stem
            if stem.endswith("_overlay") or stem in vanilla_equip:
                continue
            if not (equip_dir / f"{stem}.json").is_file():
                missing_json.append(stem)
        if missing_json:
            findings.append(
                {
                    "level": "error",
                    "id": "custom_equipment_json_missing",
                    "msg": (
                        "1.21.2+ custom humanoid materials lack equipment/<id>.json: "
                        + ", ".join(missing_json[:12])
                    ),
                    "count": len(missing_json),
                }
            )
        elif any(
            p.stem for p in humanoid_dir.glob("*.png")
            if not p.stem.endswith("_overlay") and p.stem not in vanilla_equip
        ):
            findings.append(
                {
                    "level": "info",
                    "id": "custom_equipment_json_ok",
                    "msg": "custom humanoid materials have equipment/<id>.json",
                }
            )

        # Leather-style stacks (base + overlay PNG) need dyeable on the base layer.
        missing_dyeable: list[str] = []
        for png in sorted(humanoid_dir.glob("*.png")):
            stem = png.stem
            if stem.endswith("_overlay") or stem in vanilla_equip:
                continue
            overlay = humanoid_dir / f"{stem}_overlay.png"
            if not overlay.is_file():
                continue
            equip_path = equip_dir / f"{stem}.json"
            if not equip_path.is_file():
                continue
            data = load_json(equip_path)
            layers = data.get("layers") if isinstance(data, dict) else None
            if not isinstance(layers, dict):
                missing_dyeable.append(stem)
                continue
            humanoid_layers = layers.get("humanoid")
            if not isinstance(humanoid_layers, list) or not humanoid_layers:
                missing_dyeable.append(stem)
                continue
            base = humanoid_layers[0] if isinstance(humanoid_layers[0], dict) else {}
            if "dyeable" not in base:
                missing_dyeable.append(stem)
        if missing_dyeable:
            findings.append(
                {
                    "level": "error",
                    "id": "custom_equipment_missing_dyeable",
                    "msg": (
                        "custom equipment with overlay lacks dyeable on base layer: "
                        + ", ".join(missing_dyeable[:12])
                    ),
                    "count": len(missing_dyeable),
                }
            )
        elif any(
            (humanoid_dir / f"{p.stem}_overlay.png").is_file()
            for p in humanoid_dir.glob("*.png")
            if not p.stem.endswith("_overlay") and p.stem not in vanilla_equip
        ):
            findings.append(
                {
                    "level": "info",
                    "id": "custom_equipment_dyeable_ok",
                    "msg": "leather-style custom equipment has dyeable base layers",
                }
            )

    # Bad item-model refs: item/models/armor/... without textures/item/models/armor PNG
    item_models = pack / "assets" / "minecraft" / "models" / "item"
    if item_models.is_dir():
        bad_refs = 0
        for model_path in item_models.rglob("*.json"):
            data = load_json(model_path)
            textures = data.get("textures") if isinstance(data, dict) else None
            if not isinstance(textures, dict):
                continue
            for raw in textures.values():
                if not isinstance(raw, str):
                    continue
                path = raw.split(":", 1)[-1].lstrip("/")
                if not path.startswith("item/models/armor/"):
                    continue
                png = (
                    pack
                    / "assets"
                    / "minecraft"
                    / "textures"
                    / Path(path + ".png")
                )
                # Prefer rewritten models/armor; missing item/ path is an error.
                armor_rel = "models/armor/" + path[len("item/models/armor/") :]
                armor_png = pack / "assets" / "minecraft" / "textures" / Path(armor_rel + ".png")
                if not png.is_file() and armor_png.is_file():
                    bad_refs += 1
        if bad_refs:
            findings.append(
                {
                    "level": "error",
                    "id": "item_models_armor_bad_prefix",
                    "msg": (
                        f"{bad_refs} item model texture ref(s) use item/models/armor/ "
                        "while PNG lives under textures/models/armor/"
                    ),
                }
            )

    return findings


def check_scute_rename(pack: Path, target_hint: str | None) -> list[dict]:
    """1.20.5+ must not leave scute host as the modern id; expect turtle_scute."""
    findings = []
    if not _target_at_least(target_hint, 1, 20, 5):
        return findings
    models = pack / "assets" / "minecraft" / "models" / "item"
    items_dir = pack / "assets" / "minecraft" / "items"
    has_old = (models / "scute.json").exists() or (items_dir / "scute.json").exists()
    has_new = (models / "turtle_scute.json").exists() or (items_dir / "turtle_scute.json").exists()
    if has_old and not has_new:
        findings.append(
            {
                "level": "error",
                "id": "scute_not_renamed_to_turtle_scute",
                "msg": "1.20.5+ target still has scute host without turtle_scute model/items def",
            }
        )
    elif has_old and has_new:
        findings.append(
            {
                "level": "warn",
                "id": "scute_and_turtle_scute_both_present",
                "msg": "both scute and turtle_scute hosts present after 1.20.5+ convert",
            }
        )
    elif has_new:
        findings.append(
            {
                "level": "info",
                "id": "turtle_scute_host_present",
                "msg": "turtle_scute host present (scute rename OK)",
            }
        )
    return findings


def check_overrides_vs_items(pack: Path, target_hint: str | None) -> list[dict]:
    """If target looks like 1.21.4+, flag leftover overrides without items/ defs."""
    findings = []
    need_items = _target_at_least(target_hint, 1, 21, 4)

    models = pack / "assets" / "minecraft" / "models" / "item"
    items_dir = pack / "assets" / "minecraft" / "items"
    override_hosts = []
    if models.is_dir():
        for f in models.glob("*.json"):
            data = load_json(f)
            if isinstance(data, dict) and data.get("overrides"):
                override_hosts.append(f.name)

    if need_items and override_hosts:
        missing = []
        for host in override_hosts:
            stem = host[: -len(".json")]
            if not (items_dir / f"{stem}.json").exists():
                missing.append(stem)
        if missing:
            findings.append(
                {
                    "level": "error",
                    "id": "overrides_without_items_defs",
                    "msg": "1.21.4+ target but overrides remain without assets/minecraft/items/*.json",
                    "hosts": missing[:30],
                    "host_count": len(missing),
                }
            )
    elif override_hosts:
        findings.append(
            {
                "level": "info",
                "id": "has_overrides",
                "msg": "pack still uses model overrides (OK if target < 1.21.4)",
                "host_count": len(override_hosts),
            }
        )
    return findings


def _id_equals(value: str | None, *aliases: str) -> bool:
    if not isinstance(value, str):
        return False
    v = value if ":" in value else f"minecraft:{value}"
    return v in aliases


def _validate_item_model_node(node: dict, path: str, findings: list[dict], file_name: str) -> None:
    """Validate one items-model node against vanilla 1.21.4+ shape (no invented fields)."""
    typ = node.get("type")
    if not isinstance(typ, str):
        findings.append(
            {
                "level": "error",
                "id": "items_def_missing_type",
                "msg": f"{file_name}: {path} missing string type",
            }
        )
        return

    if _id_equals(typ, "minecraft:model"):
        model = node.get("model")
        if not isinstance(model, str) or not model.strip():
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_model_missing_model",
                    "msg": f"{file_name}: {path} minecraft:model requires non-empty model id",
                }
            )
        elif ":" not in model:
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_model_unnamespaced",
                    "msg": f"{file_name}: {path} model id should be namespaced (got {model!r})",
                }
            )
        return

    if _id_equals(typ, "minecraft:range_dispatch"):
        prop = node.get("property")
        if not isinstance(prop, str):
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_range_missing_property",
                    "msg": f"{file_name}: {path} range_dispatch missing property",
                }
            )
        elif not _id_equals(
            prop,
            "minecraft:custom_model_data",
            "minecraft:damage",
            "minecraft:count",
            "minecraft:cooldown",
            "minecraft:time",
            "minecraft:compass",
            "minecraft:crossbow/pull",
            "minecraft:use_duration",
            "minecraft:use_cycle",
            "minecraft:bundle/fullness",
        ):
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_range_unknown_property",
                    "msg": f"{file_name}: {path} unknown range_dispatch property {prop!r}",
                }
            )

        # CMD/damage migrations always emit fallback; other vanilla properties may omit it.
        fallback = node.get("fallback")
        needs_fallback = _id_equals(prop, "minecraft:custom_model_data", "minecraft:damage")
        if needs_fallback and not isinstance(fallback, dict):
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_range_missing_fallback",
                    "msg": f"{file_name}: {path} CMD/damage range_dispatch missing fallback model object",
                }
            )
        elif isinstance(fallback, dict):
            _validate_item_model_node(fallback, path + ".fallback", findings, file_name)

        entries = node.get("entries")
        if not isinstance(entries, list) or not entries:
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_range_missing_entries",
                    "msg": f"{file_name}: {path} range_dispatch needs a non-empty entries array",
                }
            )
            return

        prev_threshold = None
        for i, entry in enumerate(entries):
            epath = f"{path}.entries[{i}]"
            if not isinstance(entry, dict):
                findings.append(
                    {
                        "level": "error",
                        "id": "items_def_entry_not_object",
                        "msg": f"{file_name}: {epath} is not an object",
                    }
                )
                continue
            if "threshold" not in entry or not isinstance(entry["threshold"], (int, float)):
                findings.append(
                    {
                        "level": "error",
                        "id": "items_def_entry_missing_threshold",
                        "msg": f"{file_name}: {epath} missing numeric threshold",
                    }
                )
            else:
                th = float(entry["threshold"])
                if prev_threshold is not None and th < prev_threshold:
                    findings.append(
                        {
                            "level": "error",
                            "id": "items_def_entries_unsorted",
                            "msg": f"{file_name}: {epath} thresholds must be ascending for range_dispatch",
                        }
                    )
                prev_threshold = th
            child = entry.get("model")
            if not isinstance(child, dict):
                findings.append(
                    {
                        "level": "error",
                        "id": "items_def_entry_missing_model",
                        "msg": f"{file_name}: {epath} missing nested model object",
                    }
                )
            else:
                _validate_item_model_node(child, epath + ".model", findings, file_name)
        return

    # Other vanilla node types (condition/select/…) — accept type presence only.
    if not typ.startswith("minecraft:") and ":" not in typ:
        findings.append(
            {
                "level": "error",
                "id": "items_def_type_unnamespaced",
                "msg": f"{file_name}: {path} type should be namespaced (got {typ!r})",
            }
        )


def check_items_defs_schema(pack: Path, target_hint: str | None) -> list[dict]:
    """For 1.21.4+, validate assets/minecraft/items/*.json against vanilla range_dispatch/model shape."""
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 4):
        return findings
    items_dir = pack / "assets" / "minecraft" / "items"
    if not items_dir.is_dir():
        return findings

    checked = 0
    for path in sorted(items_dir.glob("*.json")):
        data = load_json(path)
        if not isinstance(data, dict) or "_error" in data:
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_invalid_json",
                    "msg": f"{path.name}: invalid JSON",
                }
            )
            continue
        root = data.get("model")
        if not isinstance(root, dict):
            findings.append(
                {
                    "level": "error",
                    "id": "items_def_missing_root_model",
                    "msg": f"{path.name}: missing root model object",
                }
            )
            continue
        before = len(findings)
        _validate_item_model_node(root, "model", findings, path.name)
        checked += 1
        # Cap error spam from huge theatrical hosts once schema is clearly wrong
        if len(findings) - before > 20:
            findings.append(
                {
                    "level": "warn",
                    "id": "items_def_schema_truncated",
                    "msg": f"stopped deep validation early after errors in {path.name}",
                }
            )
            break

    if checked:
        findings.append(
            {
                "level": "info",
                "id": "items_defs_schema_checked",
                "msg": f"validated {checked} assets/minecraft/items/*.json against 1.21.4+ shape",
            }
        )
    return findings


def check_expect_paths(pack: Path, expect_paths: list[str]) -> list[dict]:
    findings = []
    for rel in expect_paths:
        rel = rel.replace("\\", "/").lstrip("/")
        if not (pack / rel).exists():
            findings.append(
                {
                    "level": "error",
                    "id": "missing_expect_path",
                    "msg": f"expected path missing: {rel}",
                }
            )
    return findings


def check_model_uvs_in_range(pack: Path, target_hint: str | None) -> list[dict]:
    """For 26.1+: face UVs must stay in [0, 16] or NativeImage translucency bake throws OOB."""
    findings: list[dict] = []
    if not _target_at_least(target_hint, 26, 1, 0):
        return findings
    models = pack / "assets" / "minecraft" / "models"
    if not models.is_dir():
        return findings
    bad: list[str] = []
    files_ok = 0
    for path in models.rglob("*.json"):
        data = load_json(path)
        if not isinstance(data, dict) or "_error" in data:
            continue
        elements = data.get("elements")
        if not isinstance(elements, list) or not elements:
            continue
        file_bad = False
        for el in elements:
            if not isinstance(el, dict):
                continue
            faces = el.get("faces")
            if not isinstance(faces, dict):
                continue
            for face in faces.values():
                if not isinstance(face, dict):
                    continue
                uv = face.get("uv")
                if not isinstance(uv, list):
                    continue
                for v in uv:
                    if isinstance(v, (int, float)) and (v < 0 or v > 16 + 1e-9):
                        file_bad = True
                        bad.append(f"{path.relative_to(pack).as_posix()} has uv={uv}")
                        break
                if file_bad:
                    break
            if file_bad:
                break
        if file_bad:
            continue
        files_ok += 1

    for msg in bad[:10]:
        findings.append(
            {
                "level": "error",
                "id": "model_uv_out_of_range_26",
                "msg": msg,
            }
        )
    if files_ok and not bad:
        findings.append(
            {
                "level": "info",
                "id": "model_uvs_in_range_26",
                "msg": f"{files_ok} element model(s) have face UVs within [0, 16]",
            }
        )
    return findings


def check_items_host_models_valid(pack: Path, target_hint: str | None) -> list[dict]:
    """For 1.21.4+: models/item/<host>.json paired with items/<host>.json must bake (parent or elements).

    Catches textures+overrides-only hosts left as stubs after CMD→items/ migration
    (e.g. TSF white_stained_glass → purple/black fallback).
    """
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 4):
        return findings
    items_dir = pack / "assets" / "minecraft" / "items"
    models_item = pack / "assets" / "minecraft" / "models" / "item"
    if not items_dir.is_dir() or not models_item.is_dir():
        return findings

    checked = 0
    for items_path in sorted(items_dir.glob("*.json")):
        host = items_path.stem
        model_path = models_item / f"{host}.json"
        if not model_path.is_file():
            continue
        data = load_json(model_path)
        if not isinstance(data, dict) or "_error" in data:
            findings.append(
                {
                    "level": "error",
                    "id": "items_host_model_invalid_json",
                    "msg": f"models/item/{host}.json invalid JSON after items/ migration",
                }
            )
            continue
        has_parent = isinstance(data.get("parent"), str) and bool(data.get("parent"))
        elements = data.get("elements")
        has_elements = isinstance(elements, list) and len(elements) > 0
        if not has_parent and not has_elements:
            findings.append(
                {
                    "level": "error",
                    "id": "items_host_model_invalid_stub",
                    "msg": (
                        f"models/item/{host}.json has no parent/elements after CMD→items/ "
                        f"(fallback minecraft:item/{host} would purple/black)"
                    ),
                }
            )
            continue
        # Prefer restored vanilla block parent for former textures-only hosts
        parent = data.get("parent") if has_parent else None
        if isinstance(parent, str) and parent.replace("minecraft:", "") == f"block/{host}":
            findings.append(
                {
                    "level": "info",
                    "id": "items_host_model_block_parent",
                    "msg": f"models/item/{host}.json parent={parent}",
                }
            )
        checked += 1

    if checked:
        findings.append(
            {
                "level": "info",
                "id": "items_host_models_checked",
                "msg": f"validated {checked} models/item hosts paired with items/*.json",
            }
        )
    return findings



def _atlas_directory_sources(atlases: Path) -> set[str]:
    """Collect directory `source` paths from all atlas JSON files (blocks/items/…)."""
    sources: set[str] = set()
    if not atlases.is_dir():
        return sources
    for atlas in atlases.glob("*.json"):
        data = load_json(atlas)
        if not isinstance(data, dict):
            continue
        for entry in data.get("sources") or []:
            if not isinstance(entry, dict):
                continue
            typ = str(entry.get("type") or "")
            if typ in ("directory", "minecraft:directory") and isinstance(entry.get("source"), str):
                sources.add(entry["source"].replace("\\", "/").strip("/"))
            # single sources cover one texture; record as exact resource path (optional ns)
            if typ in ("single", "minecraft:single") and isinstance(entry.get("resource"), str):
                resource = entry["resource"].replace("\\", "/")
                if ":" in resource:
                    resource = resource.split(":", 1)[1]
                sources.add("__single__:" + resource)
    return sources


def _texture_covered(rel_posix: str, atlas_sources: set[str]) -> bool:
    """True if a directory source is a path-prefix of this textures/-relative png, or a single matches."""
    rel = rel_posix[:-4] if rel_posix.endswith(".png") else rel_posix
    if f"__single__:{rel}" in atlas_sources:
        return True
    parts = rel.split("/")
    # directory source "npc" covers "npc/face.png" and "npc/barney/x.png"
    for i in range(1, len(parts)):
        if "/".join(parts[:i]) in atlas_sources:
            return True
    return False


def _atlas_item_directory_sources(atlas_path: Path) -> list[str]:
    """Return directory source paths that belong on the items atlas (item / item/**)."""
    data = load_json(atlas_path)
    if not isinstance(data, dict):
        return []
    found: list[str] = []
    for entry in data.get("sources") or []:
        if not isinstance(entry, dict):
            continue
        typ = str(entry.get("type") or "")
        if typ not in ("directory", "minecraft:directory"):
            continue
        source = entry.get("source")
        if not isinstance(source, str):
            continue
        source = source.replace("\\", "/").strip("/")
        if source == "item" or source.startswith("item/"):
            found.append(source)
    return found


def _atlas_item_sources(atlas_path: Path) -> list[dict]:
    """Return item-related atlas source entries (directory/single under item/)."""
    data = load_json(atlas_path)
    if not isinstance(data, dict):
        return []
    found: list[dict] = []
    for entry in data.get("sources") or []:
        if not isinstance(entry, dict):
            continue
        typ = str(entry.get("type") or "")
        if typ in ("directory", "minecraft:directory"):
            source = entry.get("source")
            if isinstance(source, str):
                source = source.replace("\\", "/").strip("/")
                if source == "item" or source.startswith("item/"):
                    found.append(entry)
        elif typ in ("single", "minecraft:single"):
            resource = entry.get("resource")
            if isinstance(resource, str):
                resource = resource.replace("\\", "/")
                if ":" in resource:
                    resource = resource.split(":", 1)[1]
                resource = resource.lstrip("/")
                if resource == "item" or resource.startswith("item/"):
                    found.append(entry)
    return found


def _atlas_covers_item_texture(sources: list[dict], tex: str) -> bool:
    for entry in sources:
        typ = str(entry.get("type") or "")
        if typ in ("directory", "minecraft:directory"):
            source = str(entry.get("source") or "").replace("\\", "/").strip("/")
            if source == "item" and (tex == "item" or tex.startswith("item/")):
                return True
            if tex == source or tex.startswith(source + "/"):
                return True
        elif typ in ("single", "minecraft:single"):
            resource = str(entry.get("resource") or "").replace("\\", "/")
            if ":" in resource:
                resource = resource.split(":", 1)[1]
            if resource.lstrip("/") == tex:
                return True
    return False


def _block_model_item_textures(pack: Path) -> list[str]:
    """Texture paths under item/ referenced by any assets/*/models/block model."""
    needed: list[str] = []
    seen: set[str] = set()
    assets = pack / "assets"
    if not assets.is_dir():
        return needed
    for ns in assets.iterdir():
        if not ns.is_dir():
            continue
        block_models = ns / "models" / "block"
        if not block_models.is_dir():
            continue
        for model_path in block_models.rglob("*.json"):
            data = load_json(model_path)
            if not isinstance(data, dict):
                continue
            textures = data.get("textures")
            if not isinstance(textures, dict):
                continue
            for raw in textures.values():
                if not isinstance(raw, str):
                    continue
                path = raw.replace("\\", "/").strip()
                if not path or path.startswith("#"):
                    continue
                if ":" in path:
                    path = path.split(":", 1)[1]
                path = path.lstrip("/")
                if path == "item" or path.startswith("item/"):
                    if path not in seen:
                        seen.add(path)
                        needed.append(path)
    return needed


def check_double_namespace_blockstates(pack: Path) -> list[dict]:
    """Catch minecraft:minecraft:... model ids from naive namespace prefixing."""
    findings: list[dict] = []
    assets = pack / "assets"
    if not assets.is_dir():
        return findings
    examples: list[str] = []
    for ns in assets.iterdir():
        if not ns.is_dir():
            continue
        blockstates = ns / "blockstates"
        if not blockstates.is_dir():
            continue
        for path in blockstates.rglob("*.json"):
            text = path.read_text(encoding="utf-8", errors="replace")
            if "minecraft:minecraft:" in text:
                examples.append(str(path.relative_to(pack)))
    if examples:
        findings.append(
            {
                "level": "error",
                "id": "double_namespace_blockstate_model",
                "msg": "blockstate model id has doubled minecraft:minecraft: namespace",
                "examples": examples[:10],
                "count": len(examples),
            }
        )
    return findings


def check_items_atlas_split(pack: Path, target_hint: str | None) -> list[dict]:
    """1.21.11+: peel item/ into items.json; rewrite block models off item/ onto block/item/ copies."""
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 11):
        return findings

    atlases = pack / "assets" / "minecraft" / "atlases"
    blocks = atlases / "blocks.json"
    items = atlases / "items.json"

    blocks_item_dirs = _atlas_item_directory_sources(blocks) if blocks.is_file() else []
    items_item_dirs = _atlas_item_directory_sources(items) if items.is_file() else []
    blocks_item_sources = _atlas_item_sources(blocks) if blocks.is_file() else []
    items_item_sources = _atlas_item_sources(items) if items.is_file() else []

    # Peel must still happen: item/ sources must leave blocks.json for items.json.
    if blocks_item_dirs or blocks_item_sources:
        if not items_item_dirs and not items_item_sources:
            findings.append(
                {
                    "level": "error",
                    "id": "items_sources_still_in_blocks_atlas",
                    "msg": "1.21.11+ blocks.json still lists item/ sources but items.json was not peeled",
                    "examples": blocks_item_dirs[:10]
                    or [(e.get("source") or e.get("resource")) for e in blocks_item_sources[:10]],
                    "count": len(blocks_item_dirs) or len(blocks_item_sources),
                }
            )
        else:
            findings.append(
                {
                    "level": "error",
                    "id": "items_sources_still_in_blocks_atlas",
                    "msg": "1.21.11+ blocks.json must not keep item/ sources after peel "
                    "(dual-stitch rejected in-game; use block/item/ copy+rewrite)",
                    "examples": blocks_item_dirs[:10]
                    or [(e.get("source") or e.get("resource")) for e in blocks_item_sources[:10]],
                    "count": len(blocks_item_dirs) or len(blocks_item_sources),
                }
            )

    # If the pack declares item textures (any ns), require items.json after the split cliff.
    has_item_textures = False
    assets = pack / "assets"
    if assets.is_dir():
        for ns in assets.iterdir():
            if not ns.is_dir():
                continue
            item_tex = ns / "textures" / "item"
            if item_tex.is_dir() and any(item_tex.rglob("*.png")):
                has_item_textures = True
                break

    if has_item_textures and not items.is_file():
        findings.append(
            {
                "level": "error",
                "id": "missing_items_atlas",
                "msg": "1.21.11+ pack has textures/**/item but no assets/minecraft/atlases/items.json",
            }
        )
    elif items.is_file():
        if items_item_dirs or items_item_sources:
            findings.append(
                {
                    "level": "info",
                    "id": "items_atlas_present",
                    "msg": "atlases/items.json covers item/ sources",
                    "examples": items_item_dirs[:10] or [
                        e.get("resource") for e in items_item_sources[:10]
                    ],
                }
            )
        else:
            findings.append(
                {
                    "level": "warn",
                    "id": "items_atlas_missing_item_directory",
                    "msg": "atlases/items.json exists but has no item/ directory source",
                }
            )

    # models/armor residuals used by item hand models must also sit on the items atlas
    # (blocks atlas mipmaps muddy dyed sleeves; dual-stitch is intentional).
    models_armor = pack / "assets" / "minecraft" / "textures" / "models" / "armor"
    if models_armor.is_dir() and any(models_armor.glob("*.png")):
        items_has_models_armor = False
        if items.is_file():
            data = load_json(items)
            for src in data.get("sources") or []:
                if not isinstance(src, dict):
                    continue
                typ = str(src.get("type") or "")
                if ":" in typ:
                    typ = typ.split(":", 1)[-1]
                if typ != "directory":
                    continue
                directory = str(src.get("source") or "").replace("\\", "/").strip("/")
                if directory in ("models", "models/armor") or directory.startswith(
                    "models/armor/"
                ):
                    items_has_models_armor = True
                    break
        if not items_has_models_armor:
            findings.append(
                {
                    "level": "error",
                    "id": "models_armor_missing_from_items_atlas",
                    "msg": (
                        "1.21.11+ textures/models/armor present but atlases/items.json "
                        "has no models/armor (or models) directory source"
                    ),
                }
            )
        else:
            findings.append(
                {
                    "level": "info",
                    "id": "models_armor_dual_stitched_items",
                    "msg": "models/armor dual-stitched onto items atlas",
                }
            )

    # Block models must not still sample item/ texture ids (client rejects items-atlas sprites).
    leftover = _block_model_item_textures(pack)
    if leftover:
        findings.append(
            {
                "level": "error",
                "id": "block_models_still_reference_item_textures",
                "msg": "block models still reference item/ textures; expected rewrite to block/item/",
                "examples": leftover[:10],
                "count": len(leftover),
            }
        )
    else:
        rewritten_copies = _block_item_texture_copies(pack)
        if rewritten_copies:
            findings.append(
                {
                    "level": "info",
                    "id": "block_item_textures_rewritten",
                    "msg": "block models use block/item/ copies for former item/ textures",
                    "examples": rewritten_copies[:10],
                    "count": len(rewritten_copies),
                }
            )

    # Same-id dual-stitch of block/ onto items rejects terrain models in 1.21.11+.
    same_id = _same_id_block_singles_on_items_atlas(pack)
    if same_id:
        findings.append(
            {
                "level": "error",
                "id": "items_atlas_same_id_block_dual_stitch",
                "msg": (
                    "atlases/items.json has single sources for block/ textures without a "
                    "distinct sprite alias (same-id dual-stitch); expect resource=block/... "
                    "sprite=item/block/..."
                ),
                "examples": same_id[:15],
                "count": len(same_id),
            }
        )

    # Item-facing models: bare non-item refs must be remapped (or covered by mirrored paletted).
    leftover = _item_model_unremapped_non_item_textures(pack)
    if leftover:
        findings.append(
            {
                "level": "error",
                "id": "item_model_block_textures_not_remapped",
                "msg": (
                    "item-facing models still reference non-item/ textures; expected remap to "
                    "item/<path> with sprite-aliased singles (or mirrored paletted_permutations)"
                ),
                "examples": leftover[:15],
                "count": len(leftover),
            }
        )

    missing_aliases = _item_model_missing_sprite_aliases(pack)
    if missing_aliases:
        findings.append(
            {
                "level": "error",
                "id": "item_model_block_sprite_aliases_missing",
                "msg": (
                    "item-facing models reference item/<path> aliases that lack matching "
                    "single sources (resource=<path>, sprite=item/<path>) on items.json"
                ),
                "examples": missing_aliases[:15],
                "count": len(missing_aliases),
            }
        )
    elif not same_id and not leftover:
        aliases = _items_atlas_sprite_aliased_singles(pack)
        if aliases:
            findings.append(
                {
                    "level": "info",
                    "id": "item_model_block_textures_aliased_on_items_atlas",
                    "msg": (
                        "non-item textures used by item models aliased onto items atlas "
                        "as item/<path> sprites"
                    ),
                    "examples": aliases[:15],
                    "count": len(aliases),
                }
            )
    return findings


def _paletted_generated_sprites_on_atlas(atlas_path: Path) -> set[str]:
    """Generated sprite paths (no ns) from paletted_permutations on one atlas file."""
    out: set[str] = set()
    if not atlas_path.is_file():
        return out
    data = load_json(atlas_path)
    for src in data.get("sources") or []:
        if not isinstance(src, dict) or _atlas_type_name(src) != "paletted_permutations":
            continue
        textures = src.get("textures")
        permutations = src.get("permutations")
        if not isinstance(textures, list) or not isinstance(permutations, dict):
            continue
        for tex in textures:
            if not isinstance(tex, str):
                continue
            tpath = _texture_ref_path(tex)
            if not tpath:
                continue
            for key in permutations:
                out.add(f"{tpath}_{key}")
    return out


def check_duplicate_paletted_sprites(pack: Path, target_hint: str | None) -> list[dict]:
    """1.21.11+: same generated PPP sprite id on both blocks and items atlases warns in-game."""
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 11):
        return findings
    atlases = pack / "assets" / "minecraft" / "atlases"
    blocks = _paletted_generated_sprites_on_atlas(atlases / "blocks.json")
    items = _paletted_generated_sprites_on_atlas(atlases / "items.json")
    dupes = sorted(blocks & items)
    if dupes:
        findings.append(
            {
                "level": "error",
                "id": "paletted_sprites_on_both_atlases",
                "msg": (
                    "paletted_permutations generate the same sprite ids on both blocks and "
                    "items atlases (client Duplicate sprite warning; keep item-facing PPP "
                    "on items.json only when unused by block models)"
                ),
                "examples": dupes[:20],
                "count": len(dupes),
            }
        )
    elif items and not (blocks & items):
        findings.append(
            {
                "level": "info",
                "id": "paletted_sprites_items_only",
                "msg": "paletted_permutations sprites on items atlas are not duplicated on blocks",
                "examples": sorted(items)[:10],
                "count": len(items),
            }
        )
    return findings


def check_model_particle_fallback(pack: Path, target_hint: str | None) -> list[dict]:
    """1.21.11+: models with textures keys should declare particle (converter fills #key)."""
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 11):
        return findings
    missing: list[str] = []
    assets = pack / "assets"
    if not assets.is_dir():
        return findings
    for ns in assets.iterdir():
        if not ns.is_dir():
            continue
        models = ns / "models"
        if not models.is_dir():
            continue
        for model_path in models.rglob("*.json"):
            data = load_json(model_path)
            if not isinstance(data, dict):
                continue
            textures = data.get("textures")
            if not isinstance(textures, dict) or not textures:
                continue
            if "particle" in textures:
                continue
            # Skip if every value is #particle (unusable cycle) — converter also skips.
            usable = False
            for raw in textures.values():
                if isinstance(raw, str) and raw.strip() != "#particle":
                    usable = True
                    break
            if not usable:
                continue
            try:
                rel = f"{ns.name}/{model_path.relative_to(models).as_posix()}"
            except ValueError:
                rel = str(model_path)
            missing.append(rel)
    if missing:
        findings.append(
            {
                "level": "error",
                "id": "model_missing_particle",
                "msg": (
                    "models have textures but no particle slot (modern clients warn; "
                    "expect particle=#existingKey)"
                ),
                "examples": missing[:20],
                "count": len(missing),
            }
        )
    return findings


def _texture_ref_path(raw: str) -> str | None:
    path = raw.replace("\\", "/").strip()
    if not path or path.startswith("#"):
        return None
    if ":" in path:
        path = path.split(":", 1)[1]
    path = path.lstrip("/")
    return path or None


def _texture_resource_id(raw: str) -> str | None:
    """Return namespace:path for atlas single.resource, or None for # refs."""
    trimmed = raw.replace("\\", "/").strip()
    if not trimmed or trimmed.startswith("#"):
        return None
    if ":" in trimmed:
        ns, path = trimmed.split(":", 1)
    else:
        ns, path = "minecraft", trimmed
    path = path.lstrip("/")
    if not path:
        return None
    return f"{ns}:{path}"


def _items_atlas_coverage(pack: Path) -> tuple[set[str], list[tuple[str, str]]]:
    """Return (single resource ids + sprite ids, directory sources as (source_dir, prefix))."""
    singles: set[str] = set()
    directories: list[tuple[str, str]] = []
    items = pack / "assets" / "minecraft" / "atlases" / "items.json"
    if not items.is_file():
        return singles, directories
    data = load_json(items)
    for src in data.get("sources") or []:
        if not isinstance(src, dict):
            continue
        typ = str(src.get("type") or "")
        if ":" in typ:
            typ = typ.split(":", 1)[-1]
        if typ == "single":
            resource = src.get("resource")
            if isinstance(resource, str) and resource.strip():
                rid = _texture_resource_id(resource)
                if rid:
                    singles.add(rid)
            sprite = src.get("sprite")
            if isinstance(sprite, str) and sprite.strip():
                sid = _texture_resource_id(sprite)
                if sid:
                    singles.add(sid)
        elif typ == "directory":
            directory = str(src.get("source") or "").replace("\\", "/").strip("/")
            prefix = str(src.get("prefix") or (directory + "/")).replace("\\", "/")
            if directory:
                directories.append((directory, prefix))
    return singles, directories


def _items_atlas_covers_resource(rid: str, singles: set[str], directories: list[tuple[str, str]]) -> bool:
    if rid in singles:
        return True
    path = rid.split(":", 1)[1]
    # Vanilla items atlas always stitches textures/item/** (merged even without pack file).
    # Remapped aliases like item/block/... need an explicit single.sprite (tracked in singles).
    if path == "item" or path.startswith("item/"):
        if _looks_like_nonitem_alias(path):
            return rid in singles
        return True
    for directory, prefix in directories:
        if path == directory or path.startswith(directory + "/"):
            return True
        if prefix and (path == prefix.rstrip("/") or path.startswith(prefix)):
            return True
    return False


def _looks_like_nonitem_alias(item_path: str) -> bool:
    """True for remapped aliases item/<original> where original was not under item/."""
    if not item_path.startswith("item/") or item_path == "item":
        return False
    rest = item_path[len("item/") :]
    # Common remapped roots from adventure packs.
    return rest.startswith(
        (
            "block/",
            "skins/",
            "object/",
            "icon/",
            "itembox/",
            "pixel/",
            "entity/",
            "gui/",
            "environment/",
            "painting/",
            "particle/",
            "colormap/",
            "misc/",
            "font/",
            "trims/",
            "models/",
        )
    )


def _atlas_type_name(src: dict) -> str:
    typ = str(src.get("type") or "")
    if ":" in typ:
        typ = typ.split(":", 1)[-1]
    return typ


def _same_id_block_singles_on_items_atlas(pack: Path) -> list[str]:
    """Singles whose resource is block/ and sprite is missing or equals resource."""
    items = pack / "assets" / "minecraft" / "atlases" / "items.json"
    if not items.is_file():
        return []
    bad: list[str] = []
    data = load_json(items)
    for src in data.get("sources") or []:
        if not isinstance(src, dict) or _atlas_type_name(src) != "single":
            continue
        resource = src.get("resource")
        if not isinstance(resource, str) or not resource.strip():
            continue
        rid = _texture_resource_id(resource)
        if rid is None:
            continue
        path = rid.split(":", 1)[1]
        if not (path == "block" or path.startswith("block/")):
            continue
        sprite = src.get("sprite")
        if not isinstance(sprite, str) or not sprite.strip():
            bad.append(rid)
            continue
        sid = _texture_resource_id(sprite)
        if sid is None or sid == rid:
            bad.append(rid)
    return bad


def _items_atlas_sprite_aliased_singles(pack: Path) -> list[str]:
    """Singles with distinct sprite ids (resource → sprite), as 'resource => sprite'."""
    items = pack / "assets" / "minecraft" / "atlases" / "items.json"
    if not items.is_file():
        return []
    out: list[str] = []
    data = load_json(items)
    for src in data.get("sources") or []:
        if not isinstance(src, dict) or _atlas_type_name(src) != "single":
            continue
        resource = src.get("resource")
        sprite = src.get("sprite")
        if not isinstance(resource, str) or not isinstance(sprite, str):
            continue
        rid = _texture_resource_id(resource)
        sid = _texture_resource_id(sprite)
        if rid and sid and rid != sid:
            out.append(f"{rid} => {sid}")
    return out


def _items_atlas_sprite_alias_map(pack: Path) -> dict[str, str]:
    """Map sprite id → resource id for aliased singles on items.json."""
    items = pack / "assets" / "minecraft" / "atlases" / "items.json"
    if not items.is_file():
        return {}
    out: dict[str, str] = {}
    data = load_json(items)
    for src in data.get("sources") or []:
        if not isinstance(src, dict) or _atlas_type_name(src) != "single":
            continue
        resource = src.get("resource")
        sprite = src.get("sprite")
        if not isinstance(resource, str) or not isinstance(sprite, str):
            continue
        rid = _texture_resource_id(resource)
        sid = _texture_resource_id(sprite)
        if rid and sid:
            out[sid] = rid
    return out


def _items_atlas_generated_paletted_sprites(pack: Path) -> set[str]:
    """Sprite paths (no ns) generated by paletted_permutations on items or blocks atlas."""
    out: set[str] = set()
    atlases = pack / "assets" / "minecraft" / "atlases"
    for name in ("items.json", "blocks.json"):
        path = atlases / name
        if not path.is_file():
            continue
        data = load_json(path)
        for src in data.get("sources") or []:
            if not isinstance(src, dict) or _atlas_type_name(src) != "paletted_permutations":
                continue
            textures = src.get("textures")
            permutations = src.get("permutations")
            if not isinstance(textures, list) or not isinstance(permutations, dict):
                continue
            for tex in textures:
                if not isinstance(tex, str):
                    continue
                tpath = _texture_ref_path(tex)
                if not tpath:
                    continue
                for key in permutations:
                    out.add(f"{tpath}_{key}")
    return out


def _item_model_unremapped_non_item_textures(pack: Path) -> list[str]:
    """Non-item/ refs still on item-facing models, excluding paletted + models/armor."""
    generated = _items_atlas_generated_paletted_sprites(pack)
    # Prefer items-atlas paletted coverage: if only on blocks, still allow leftover pixel
    # only when items.json also mirrors the source (converter should mirror).
    items = pack / "assets" / "minecraft" / "atlases" / "items.json"
    items_generated: set[str] = set()
    if items.is_file():
        data = load_json(items)
        for src in data.get("sources") or []:
            if not isinstance(src, dict) or _atlas_type_name(src) != "paletted_permutations":
                continue
            textures = src.get("textures")
            permutations = src.get("permutations")
            if not isinstance(textures, list) or not isinstance(permutations, dict):
                continue
            for tex in textures:
                if not isinstance(tex, str):
                    continue
                tpath = _texture_ref_path(tex)
                if not tpath:
                    continue
                for key in permutations:
                    items_generated.add(f"{tpath}_{key}")

    leftover: set[str] = set()
    assets = pack / "assets"
    if not assets.is_dir():
        return []
    for ns in assets.iterdir():
        if not ns.is_dir():
            continue
        models = ns / "models"
        if not models.is_dir():
            continue
        for model_path in models.rglob("*.json"):
            try:
                rel = model_path.relative_to(models).as_posix()
            except ValueError:
                continue
            if rel.startswith("block/") or rel == "block":
                continue
            data = load_json(model_path)
            if not isinstance(data, dict):
                continue
            textures = data.get("textures")
            if not isinstance(textures, dict):
                continue
            for raw in textures.values():
                if not isinstance(raw, str):
                    continue
                rid = _texture_resource_id(raw)
                if rid is None:
                    continue
                path = rid.split(":", 1)[1]
                if path == "item" or path.startswith("item/"):
                    continue
                # Same-id directory dual-stitch for models/armor is intentional.
                if path == "models" or path.startswith("models/"):
                    continue
                if path in items_generated:
                    continue
                # Generated on blocks only but not mirrored → still an error.
                if path in generated and path not in items_generated:
                    leftover.add(rid)
                    continue
                leftover.add(rid)
    return sorted(leftover)


def _item_model_missing_sprite_aliases(pack: Path) -> list[str]:
    """Remapped item/<original> refs on item-facing models lacking matching singles."""
    alias_map = _items_atlas_sprite_alias_map(pack)
    missing: set[str] = set()
    assets = pack / "assets"
    if not assets.is_dir():
        return []
    for ns in assets.iterdir():
        if not ns.is_dir():
            continue
        models = ns / "models"
        if not models.is_dir():
            continue
        for model_path in models.rglob("*.json"):
            try:
                rel = model_path.relative_to(models).as_posix()
            except ValueError:
                continue
            if rel.startswith("block/") or rel == "block":
                continue
            data = load_json(model_path)
            if not isinstance(data, dict):
                continue
            textures = data.get("textures")
            if not isinstance(textures, dict):
                continue
            for raw in textures.values():
                if not isinstance(raw, str):
                    continue
                rid = _texture_resource_id(raw)
                if rid is None:
                    continue
                path = rid.split(":", 1)[1]
                if not _looks_like_nonitem_alias(path):
                    continue
                if rid not in alias_map:
                    missing.add(rid)
    return sorted(missing)


def _items_atlas_non_item_singles(pack: Path) -> list[str]:
    singles, _dirs = _items_atlas_coverage(pack)
    out = []
    for rid in sorted(singles):
        path = rid.split(":", 1)[1]
        if path == "item" or path.startswith("item/"):
            continue
        out.append(rid)
    return out


def _item_model_block_textures_missing_from_items_atlas(pack: Path) -> list[str]:
    """Legacy helper: bare non-item refs not covered (prefer _item_model_unremapped_*)."""
    return _item_model_unremapped_non_item_textures(pack)


def check_empty_armor_slot_remap(pack: Path, target_hint: str | None) -> list[dict]:
    """1.21.4+: empty_armor_slot_* item refs should be remapped when pack has block/transparent."""
    findings: list[dict] = []
    if not _target_at_least(target_hint, 1, 21, 4):
        return findings

    assets = pack / "assets"
    if not assets.is_dir():
        return findings

    slots = {
        "empty_armor_slot_helmet",
        "empty_armor_slot_chestplate",
        "empty_armor_slot_leggings",
        "empty_armor_slot_boots",
        "empty_armor_slot_shield",
    }
    leftovers: list[str] = []
    had_transparent = False
    for ns in assets.iterdir():
        if not ns.is_dir():
            continue
        if (ns / "textures" / "block" / "transparent.png").is_file():
            had_transparent = True
        models = ns / "models"
        if not models.is_dir():
            continue
        for model_path in models.rglob("*.json"):
            data = load_json(model_path)
            if not isinstance(data, dict):
                continue
            textures = data.get("textures")
            if not isinstance(textures, dict):
                continue
            for raw in textures.values():
                if not isinstance(raw, str):
                    continue
                path = _texture_ref_path(raw)
                if path is None or not path.startswith("item/"):
                    continue
                name = path[len("item/") :]
                if name in slots:
                    leftovers.append(
                        f"{model_path.relative_to(pack).as_posix()}:{path}"
                    )

    if leftovers and had_transparent:
        findings.append(
            {
                "level": "error",
                "id": "empty_armor_slot_refs_unmapped",
                "msg": (
                    "models still reference item/empty_armor_slot_* but pack has "
                    "block/transparent — expected remap"
                ),
                "examples": leftovers[:10],
                "count": len(leftovers),
            }
        )
    elif leftovers:
        findings.append(
            {
                "level": "warn",
                "id": "empty_armor_slot_refs_no_transparent",
                "msg": "models reference removed empty_armor_slot_* without pack block/transparent",
                "examples": leftovers[:10],
                "count": len(leftovers),
            }
        )
    elif had_transparent:
        findings.append(
            {
                "level": "info",
                "id": "empty_armor_slot_remap_ok",
                "msg": "no leftover item/empty_armor_slot_* refs (transparent available)",
            }
        )
    return findings


def _block_item_texture_copies(pack: Path) -> list[str]:
    """List textures/block/item/**.png copies produced for block-model rewrites."""
    found: list[str] = []
    assets = pack / "assets"
    if not assets.is_dir():
        return found
    for ns in assets.iterdir():
        if not ns.is_dir():
            continue
        root = ns / "textures" / "block" / "item"
        if not root.is_dir():
            continue
        for png in root.rglob("*.png"):
            rel = png.relative_to(ns / "textures").as_posix()
            # strip .png
            found.append(rel[:-4] if rel.endswith(".png") else rel)
    return found


def check_custom_ns_atlas(pack: Path) -> list[dict]:
    findings = []
    assets = pack / "assets"
    if not assets.is_dir():
        return findings
    custom_ns = [p.name for p in assets.iterdir() if p.is_dir() and p.name != "minecraft"]
    atlases = pack / "assets" / "minecraft" / "atlases"
    atlas_sources = _atlas_directory_sources(atlases)
    for ns in custom_ns:
        tex = assets / ns / "textures"
        if not tex.is_dir():
            continue
        # any png not under textures/block or textures/item
        risky = []
        uncovered = []
        for png in tex.rglob("*.png"):
            rel = png.relative_to(tex).as_posix()
            if rel.startswith("block/") or rel.startswith("item/"):
                continue
            ref = f"{ns}:{rel}"
            risky.append(ref)
            if not _texture_covered(rel, atlas_sources):
                uncovered.append(ref)
        if risky and not atlases.is_dir():
            findings.append(
                {
                    "level": "error",
                    "id": "custom_ns_textures_without_atlases",
                    "msg": f"namespace {ns} has non-block/item textures but no assets/minecraft/atlases/",
                    "examples": risky[:10],
                    "count": len(risky),
                }
            )
        elif uncovered:
            findings.append(
                {
                    "level": "error",
                    "id": "custom_ns_textures_without_atlases",
                    "msg": f"namespace {ns}: non-block/item textures lack atlas directory/single sources",
                    "examples": uncovered[:10],
                    "count": len(uncovered),
                }
            )
        elif risky:
            findings.append(
                {
                    "level": "info",
                    "id": "custom_ns_textures_atlas_ok",
                    "msg": f"namespace {ns}: atlas sources cover non-block/item textures",
                    "examples": risky[:10],
                    "count": len(risky),
                }
            )
    return findings


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", required=True, type=Path)
    ap.add_argument("--to", default=None, help="target version hint for scoring rules")
    ap.add_argument("--name", default=None)
    ap.add_argument("--expect-error", action="append", default=[], help="finding ids that are expected (do not fail)")
    ap.add_argument(
        "--expect-path",
        action="append",
        default=[],
        help="relative pack path that must exist after convert",
    )
    args = ap.parse_args()

    pack = args.pack.resolve()
    name = args.name or pack.name
    findings: list[dict] = []
    findings.extend(check_pack_mcmeta(pack, args.to))
    findings.extend(check_overrides_vs_items(pack, args.to))
    findings.extend(check_items_defs_schema(pack, args.to))
    findings.extend(check_items_host_models_valid(pack, args.to))
    findings.extend(check_model_uvs_in_range(pack, args.to))
    findings.extend(check_scute_rename(pack, args.to))
    findings.extend(check_saddle_equipment_migrate(pack, args.to))
    findings.extend(check_custom_armor_equipment(pack, args.to))
    findings.extend(check_entity_layout_26_1(pack, args.to))
    findings.extend(check_core_shaders_stripped_26_1(pack, args.to))
    findings.extend(check_beds_signs_pillars_26_2(pack, args.to))
    findings.extend(check_trim_palettes_26_3(pack, args.to))
    findings.extend(check_custom_palettes_26_3(pack, args.to))
    findings.extend(check_custom_ns_atlas(pack))
    findings.extend(check_items_atlas_split(pack, args.to))
    findings.extend(check_duplicate_paletted_sprites(pack, args.to))
    findings.extend(check_model_particle_fallback(pack, args.to))
    findings.extend(check_empty_armor_slot_remap(pack, args.to))
    findings.extend(check_double_namespace_blockstates(pack))
    findings.extend(check_expect_paths(pack, args.expect_path))

    errors = [f for f in findings if f.get("level") == "error" and f.get("id") not in args.expect_error]
    report = {
        "pack": str(pack),
        "name": name,
        "to": args.to,
        "ok": len(errors) == 0,
        "error_count": len(errors),
        "findings": findings,
    }
    out_dir = Path(__file__).resolve().parent.parent / "reports"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"score_{name}.json"
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    print(f"[score] wrote {out_path}", file=sys.stderr)
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
