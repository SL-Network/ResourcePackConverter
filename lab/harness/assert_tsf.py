#!/usr/bin/env python3
"""L2 asserts for Terra Swoop Force theatrical hosts after conversion."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


HOSTS = {
    "yellow_dye": "barney",
    "white_stained_glass": "presentation",
    "diamond_sword": "projector",  # also whiteboard/office props
    "diamond_hoe": "drill_screen",
    "light_blue_dye": "dropscreen",
    # 1.20.5+: scute host renamed to turtle_scute (Barney head bits)
    "turtle_scute": "scute_head",
}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def models_from_overrides(data: dict) -> list[str]:
    out = []
    for o in data.get("overrides") or []:
        m = o.get("model")
        if m:
            out.append(m.replace("minecraft:", ""))
    return out


def models_from_items_def(data: dict) -> list[str]:
    """Best-effort walk of 1.21.4+ item definition JSON for model refs."""
    found: list[str] = []

    def walk(obj):
        if isinstance(obj, dict):
            for k, v in obj.items():
                if k in ("model", "path") and isinstance(v, str):
                    found.append(v.replace("minecraft:", ""))
                else:
                    walk(v)
        elif isinstance(obj, list):
            for i in obj:
                walk(i)

    walk(data)
    return found


def _target_at_least(target_hint: str | None, major: int, minor: int, patch: int) -> bool:
    if not target_hint:
        return False
    parts = str(target_hint).split(".")
    try:
        if parts[0] == "26":
            return True
        t_major = int(parts[0])
        t_minor = int(parts[1]) if len(parts) > 1 else 0
        t_patch = int(parts[2]) if len(parts) > 2 else 0
        return (t_major, t_minor, t_patch) >= (major, minor, patch)
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


def _id_equals(value: str | None, *aliases: str) -> bool:
    if not isinstance(value, str):
        return False
    v = value if ":" in value else f"minecraft:{value}"
    return v in aliases


def validate_cmd_items_def(data: dict, host: str) -> list[dict]:
    """Require vanilla-shaped CMD range_dispatch for theatrical hosts (format 46+)."""
    findings: list[dict] = []
    root = data.get("model")
    if not isinstance(root, dict):
        findings.append(
            {
                "level": "error",
                "id": f"host_items_schema_{host}",
                "msg": f"items/{host}.json missing root model object",
            }
        )
        return findings
    if not _id_equals(root.get("type"), "minecraft:range_dispatch"):
        findings.append(
            {
                "level": "error",
                "id": f"host_items_schema_{host}",
                "msg": f"items/{host}.json expected minecraft:range_dispatch, got {root.get('type')!r}",
            }
        )
    if not _id_equals(root.get("property"), "minecraft:custom_model_data"):
        findings.append(
            {
                "level": "error",
                "id": f"host_items_schema_{host}",
                "msg": f"items/{host}.json expected property minecraft:custom_model_data, got {root.get('property')!r}",
            }
        )
    fallback = root.get("fallback")
    if not isinstance(fallback, dict) or not _id_equals(fallback.get("type"), "minecraft:model"):
        findings.append(
            {
                "level": "error",
                "id": f"host_items_schema_{host}",
                "msg": f"items/{host}.json missing minecraft:model fallback",
            }
        )
    elif fallback.get("model") != f"minecraft:item/{host}":
        findings.append(
            {
                "level": "error",
                "id": f"host_items_fallback_{host}",
                "msg": f"items/{host}.json fallback should be minecraft:item/{host}, got {fallback.get('model')!r}",
            }
        )
    entries = root.get("entries")
    if not isinstance(entries, list) or not entries:
        findings.append(
            {
                "level": "error",
                "id": f"host_items_schema_{host}",
                "msg": f"items/{host}.json range_dispatch entries missing",
            }
        )
    else:
        for i, entry in enumerate(entries[:5]):
            if not isinstance(entry, dict):
                continue
            child = entry.get("model")
            if not isinstance(child, dict) or not _id_equals(child.get("type"), "minecraft:model"):
                findings.append(
                    {
                        "level": "error",
                        "id": f"host_items_schema_{host}",
                        "msg": f"items/{host}.json entries[{i}] missing minecraft:model child",
                    }
                )
                break
            mid = child.get("model")
            if not isinstance(mid, str) or ":" not in mid:
                findings.append(
                    {
                        "level": "error",
                        "id": f"host_items_schema_{host}",
                        "msg": f"items/{host}.json entries[{i}] model id must be namespaced",
                    }
                )
                break
    return findings


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", required=True, type=Path)
    ap.add_argument("--to", default="1.21.4")
    args = ap.parse_args()
    pack = args.pack.resolve()

    findings = []
    models_item = pack / "assets" / "minecraft" / "models" / "item"
    items_dir = pack / "assets" / "minecraft" / "items"
    need_items = _target_at_least(args.to, 1, 21, 4)

    for host, needle in HOSTS.items():
        override_path = models_item / f"{host}.json"
        items_path = items_dir / f"{host}.json"
        refs: list[str] = []
        source = None
        if items_path.exists():
            data = load(items_path)
            refs = models_from_items_def(data)
            source = "items"
            if need_items:
                findings.extend(validate_cmd_items_def(data, host))
        elif override_path.exists():
            refs = models_from_overrides(load(override_path))
            source = "overrides"
        else:
            findings.append(
                {
                    "level": "error",
                    "id": f"missing_host_{host}",
                    "msg": f"missing both models/item/{host}.json and items/{host}.json",
                }
            )
            continue

        hit = [r for r in refs if needle in r or (host == "diamond_sword" and ("projector" in r or "whiteboard" in r))]
        if not hit:
            findings.append(
                {
                    "level": "error",
                    "id": f"host_missing_models_{host}",
                    "msg": f"{host} ({source}) has no model refs containing '{needle}'",
                    "ref_sample": refs[:5],
                }
            )
        else:
            findings.append(
                {
                    "level": "info",
                    "id": f"host_ok_{host}",
                    "msg": f"{host} via {source}: {len(hit)} matching refs",
                }
            )

        # 1.21.4+: host models/item/<host>.json must remain bakeable (parent or elements)
        # after overrides→items/ — textures-only stubs purple/black on fallback.
        if need_items and override_path.exists():
            host_model = load(override_path)
            has_parent = isinstance(host_model.get("parent"), str) and bool(host_model.get("parent"))
            elements = host_model.get("elements")
            has_elements = isinstance(elements, list) and len(elements) > 0
            if not has_parent and not has_elements:
                findings.append(
                    {
                        "level": "error",
                        "id": f"host_model_invalid_stub_{host}",
                        "msg": (
                            f"models/item/{host}.json has no parent/elements after items/ migration "
                            f"(CMD fallback would missing-model)"
                        ),
                    }
                )
            elif host == "white_stained_glass":
                parent = str(host_model.get("parent") or "").replace("minecraft:", "")
                if parent == "block/white_stained_glass":
                    findings.append(
                        {
                            "level": "info",
                            "id": "host_model_glass_block_parent",
                            "msg": "white_stained_glass host uses minecraft:block/white_stained_glass parent",
                        }
                    )

        # For 1.21.4+, theatrical hosts must resolve via items/<host>.json
        if need_items and source == "overrides":
            findings.append(
                {
                    "level": "error",
                    "id": f"host_still_overrides_{host}",
                    "msg": f"{host} still on model overrides; need items/{host}.json for {args.to}",
                }
            )
        elif need_items and not items_path.exists():
            findings.append(
                {
                    "level": "error",
                    "id": f"host_missing_items_{host}",
                    "msg": f"1.21.4+ target requires assets/minecraft/items/{host}.json",
                }
            )

    # Leftover pre-1.20.5 scute host on modern targets is a rename miss
    has_scute = (models_item / "scute.json").exists() or (items_dir / "scute.json").exists()
    has_turtle_scute = (models_item / "turtle_scute.json").exists() or (items_dir / "turtle_scute.json").exists()
    if args.to and has_scute and not has_turtle_scute:
        parts = str(args.to).split(".")
        modern = False
        try:
            if parts[0] == "26" or (
                parts[0] == "1" and int(parts[1]) > 20
            ) or (
                parts[0] == "1"
                and int(parts[1]) == 20
                and len(parts) > 2
                and int(parts[2]) >= 5
            ):
                modern = True
        except ValueError:
            modern = False
        if modern:
            findings.append(
                {
                    "level": "error",
                    "id": "scute_not_renamed_to_turtle_scute",
                    "msg": f"scute host still present without turtle_scute for target {args.to}",
                }
            )

    # 1.21.2+: custom models/armor residuals must have equipment copies
    if _target_at_least(args.to, 1, 21, 2):
        models_armor = pack / "assets" / "minecraft" / "textures" / "models" / "armor"
        if models_armor.is_dir():
            leftovers = sorted(p.stem for p in models_armor.glob("*.png") if p.is_file())
            if leftovers:
                humanoid = (
                    pack
                    / "assets"
                    / "minecraft"
                    / "textures"
                    / "entity"
                    / "equipment"
                    / "humanoid"
                )
                missing = [
                    stem
                    for stem in leftovers
                    if not (humanoid / f"{stem}.png").is_file()
                ]
                if missing:
                    findings.append(
                        {
                            "level": "error",
                            "id": "tsf_custom_armor_no_equipment_copy",
                            "msg": (
                                "TSF models/armor leftovers without equipment/humanoid copy: "
                                + ", ".join(missing[:12])
                            ),
                        }
                    )
                else:
                    findings.append(
                        {
                            "level": "info",
                            "id": "tsf_custom_armor_equipment_ok",
                            "msg": f"{len(leftovers)} TSF custom armor png(s) copied to equipment/humanoid",
                        }
                    )

                # Theatrical residual materials need equipment/<id>.json (≥1.21.2).
                vanilla_equip = {
                    "leather", "chainmail", "iron", "gold", "diamond", "netherite", "copper",
                    "turtle_scute", "armadillo_scute",
                }
                equip_dir = pack / "assets" / "minecraft" / "equipment"
                humanoid = (
                    pack
                    / "assets"
                    / "minecraft"
                    / "textures"
                    / "entity"
                    / "equipment"
                    / "humanoid"
                )
                missing_json = []
                if humanoid.is_dir():
                    for png in sorted(humanoid.glob("*.png")):
                        stem = png.stem
                        if stem.endswith("_overlay") or stem in vanilla_equip:
                            continue
                        if not (equip_dir / f"{stem}.json").is_file():
                            missing_json.append(stem)
                if missing_json:
                    findings.append(
                        {
                            "level": "error",
                            "id": "tsf_custom_equipment_json_missing",
                            "msg": (
                                "TSF custom materials lack equipment/<id>.json: "
                                + ", ".join(missing_json[:12])
                            ),
                        }
                    )
                else:
                    findings.append(
                        {
                            "level": "info",
                            "id": "tsf_custom_equipment_json_ok",
                            "msg": "TSF custom theatrical materials have equipment/<id>.json",
                        }
                    )

                # Leather-style theatrical materials (base + overlay) need dyeable.
                missing_dyeable = []
                for material in ("white", "noxcrew"):
                    overlay = humanoid / f"{material}_overlay.png"
                    equip_path = equip_dir / f"{material}.json"
                    if not overlay.is_file() or not equip_path.is_file():
                        continue
                    try:
                        data = load(equip_path)
                    except Exception:
                        missing_dyeable.append(material)
                        continue
                    layers = (data.get("layers") or {}).get("humanoid") or []
                    base = layers[0] if layers and isinstance(layers[0], dict) else {}
                    if "dyeable" not in base:
                        missing_dyeable.append(material)
                if missing_dyeable:
                    findings.append(
                        {
                            "level": "error",
                            "id": "tsf_equipment_missing_dyeable",
                            "msg": (
                                "TSF leather-style equipment missing dyeable: "
                                + ", ".join(missing_dyeable)
                            ),
                        }
                    )
                elif (humanoid / "white_overlay.png").is_file() or (
                    humanoid / "noxcrew_overlay.png"
                ).is_file():
                    findings.append(
                        {
                            "level": "info",
                            "id": "tsf_equipment_dyeable_ok",
                            "msg": "TSF white/noxcrew equipment has dyeable base layers",
                        }
                    )

                # Overlay faces on colored_arm_model must not keep tintindex.
                arm = pack / "assets" / "minecraft" / "models" / "item" / "colored_arm_model.json"
                if arm.is_file():
                    try:
                        arm_data = load(arm)
                    except Exception:
                        arm_data = {}
                    textures = arm_data.get("textures") or {}
                    overlay_keys = {
                        k
                        for k, v in textures.items()
                        if isinstance(v, str) and "_overlay" in v.replace("\\", "/")
                    }
                    bad_overlay_tints = 0
                    for el in arm_data.get("elements") or []:
                        faces = el.get("faces") if isinstance(el, dict) else None
                        if not isinstance(faces, dict):
                            continue
                        for face in faces.values():
                            if not isinstance(face, dict) or "tintindex" not in face:
                                continue
                            tex = face.get("texture")
                            if not isinstance(tex, str):
                                continue
                            key = tex[1:] if tex.startswith("#") else None
                            if key in overlay_keys or (
                                isinstance(tex, str) and "_overlay" in tex
                            ):
                                bad_overlay_tints += 1
                    if bad_overlay_tints:
                        findings.append(
                            {
                                "level": "error",
                                "id": "tsf_overlay_tintindex_present",
                                "msg": (
                                    f"colored_arm_model still has tintindex on "
                                    f"{bad_overlay_tints} overlay face(s)"
                                ),
                            }
                        )
                    else:
                        findings.append(
                            {
                                "level": "info",
                                "id": "tsf_overlay_tintindex_stripped",
                                "msg": "colored_arm_model overlay faces have no tintindex",
                            }
                        )

                leather_items = (
                    pack / "assets" / "minecraft" / "items" / "leather_chestplate.json"
                )
                if leather_items.is_file():
                    try:
                        li = load(leather_items)
                    except Exception:
                        li = {}
                    blob = str(li)
                    if "minecraft:dye" in blob or '"dye"' in blob:
                        findings.append(
                            {
                                "level": "info",
                                "id": "tsf_leather_items_dye_ok",
                                "msg": "items/leather_chestplate.json declares dye tint",
                            }
                        )
                    else:
                        findings.append(
                            {
                                "level": "error",
                                "id": "tsf_leather_items_dye_missing",
                                "msg": "items/leather_chestplate.json missing minecraft:dye tint",
                            }
                        )

                # Spot-check theatrical hand/head models no longer use item/models/armor/
                bad = []
                for name in (
                    "noxcrew_hand.json",
                    "community_hand.json",
                    "unicorn_hand.json",
                    "dark_hand.json",
                    "arm_model.json",
                ):
                    model = pack / "assets" / "minecraft" / "models" / "item" / name
                    if not model.is_file():
                        continue
                    try:
                        data = load(model)
                    except Exception:
                        continue
                    textures = data.get("textures") or {}
                    for raw in textures.values():
                        if isinstance(raw, str) and "item/models/armor/" in raw.replace("\\", "/"):
                            bad.append(name)
                            break
                if bad:
                    findings.append(
                        {
                            "level": "error",
                            "id": "tsf_item_models_armor_bad_prefix",
                            "msg": "TSF item models still reference item/models/armor/: " + ", ".join(bad),
                        }
                    )

    # 1.21.5+: TSF blank strider saddle must land on equipment path
    if _target_at_least(args.to, 1, 21, 5):
        old_strider = (
            pack / "assets" / "minecraft" / "textures" / "entity" / "strider" / "strider_saddle.png"
        )
        new_strider = (
            pack
            / "assets"
            / "minecraft"
            / "textures"
            / "entity"
            / "equipment"
            / "strider_saddle"
            / "saddle.png"
        )
        if old_strider.is_file() and not new_strider.is_file():
            findings.append(
                {
                    "level": "error",
                    "id": "tsf_strider_saddle_not_moved",
                    "msg": "TSF strider_saddle.png still at pre-1.21.5 path; expected entity/equipment/strider_saddle/saddle.png",
                }
            )
        elif new_strider.is_file():
            findings.append(
                {
                    "level": "info",
                    "id": "tsf_strider_saddle_equipment_ok",
                    "msg": "TSF strider saddle overlay at entity/equipment/strider_saddle/saddle.png",
                }
            )
        else:
            findings.append(
                {
                    "level": "warn",
                    "id": "tsf_strider_saddle_missing",
                    "msg": "no TSF strider saddle overlay at old or equipment path",
                }
            )

    # Barney textures under tsf:
    tsf_tex = pack / "assets" / "tsf" / "textures" / "npc" / "barney"
    if not tsf_tex.is_dir():
        findings.append({"level": "error", "id": "missing_tsf_barney_textures", "msg": str(tsf_tex)})
    else:
        findings.append({"level": "info", "id": "tsf_barney_textures_present", "msg": f"{len(list(tsf_tex.glob('*.png')))} pngs"})

    # Illegal RLs like minecraft:tsf:npc/… (ModelConverter minecraft: prefix on custom ns).
    bad_tsf_refs: list[str] = []
    assets_root = pack / "assets"
    if assets_root.is_dir():
        for model_path in assets_root.rglob("models/**/*.json"):
            try:
                data = load(model_path)
            except Exception:
                continue
            textures = data.get("textures") if isinstance(data, dict) else None
            if not isinstance(textures, dict):
                continue
            for key, raw in textures.items():
                if not isinstance(raw, str):
                    continue
                if "minecraft:tsf:" in raw or "item/tsf:" in raw.replace("\\", "/"):
                    try:
                        rel = model_path.relative_to(pack).as_posix()
                    except ValueError:
                        rel = str(model_path)
                    bad_tsf_refs.append(f"{rel}:{key}={raw}")
                elif ":" in raw:
                    path_part = raw.split(":", 1)[1]
                    if ":" in path_part:
                        try:
                            rel = model_path.relative_to(pack).as_posix()
                        except ValueError:
                            rel = str(model_path)
                        bad_tsf_refs.append(f"{rel}:{key}={raw}")
    if bad_tsf_refs:
        findings.append(
            {
                "level": "error",
                "id": "tsf_invalid_texture_resource_location",
                "msg": (
                    "TSF models have illegal texture resource locations "
                    "(e.g. minecraft:tsf:npc/…); custom namespaces must not get a minecraft: prefix"
                ),
                "examples": bad_tsf_refs[:20],
                "count": len(bad_tsf_refs),
            }
        )
    elif tsf_tex.is_dir():
        # Spot-check a known Barney suit model keeps a valid tsf: (or tsf:item/) ref.
        sample = (
            pack
            / "assets"
            / "minecraft"
            / "models"
            / "item"
            / "barney"
            / "brown_suit"
            / "head.json"
        )
        if sample.is_file():
            data = load(sample)
            textures = data.get("textures") if isinstance(data, dict) else None
            ok_tsf = False
            if isinstance(textures, dict):
                for raw in textures.values():
                    if isinstance(raw, str) and (
                        raw.startswith("tsf:npc/")
                        or raw.startswith("tsf:item/npc/")
                    ):
                        ok_tsf = True
                        break
            if ok_tsf:
                findings.append(
                    {
                        "level": "info",
                        "id": "tsf_barney_texture_ns_ok",
                        "msg": "barney brown_suit/head.json keeps valid tsf: texture namespace",
                    }
                )
            else:
                findings.append(
                    {
                        "level": "error",
                        "id": "tsf_barney_texture_ns_missing",
                        "msg": "barney brown_suit/head.json missing tsf:npc/ or tsf:item/npc/ texture refs",
                        "textures": textures,
                    }
                )

    atlases = pack / "assets" / "minecraft" / "atlases"
    if not atlases.is_dir():
        findings.append(
            {
                "level": "error",
                "id": "tsf_needs_atlas",
                "msg": "tsf:npc textures need atlas coverage on 1.19.3+; no atlases/ dir",
            }
        )
    else:
        # Directory sources are cross-namespace; "npc" covers assets/tsf/textures/npc/**
        dir_sources: set[str] = set()
        for atlas in atlases.glob("*.json"):
            try:
                data = load(atlas)
            except Exception:
                continue
            for entry in data.get("sources") or []:
                if not isinstance(entry, dict):
                    continue
                typ = str(entry.get("type") or "")
                if typ in ("directory", "minecraft:directory") and isinstance(entry.get("source"), str):
                    dir_sources.add(entry["source"].replace("\\", "/").strip("/"))
        covers_tsf_npc = any(
            src == "npc" or src.startswith("npc/") for src in dir_sources
        )
        if tsf_tex.is_dir() and not covers_tsf_npc:
            findings.append(
                {
                    "level": "error",
                    "id": "tsf_needs_atlas",
                    "msg": "tsf:npc textures present but no atlas directory source covers npc/",
                    "sources_sample": sorted(dir_sources)[:20],
                }
            )
        elif covers_tsf_npc:
            findings.append(
                {
                    "level": "info",
                    "id": "tsf_atlas_covers_npc",
                    "msg": "atlas directory sources include npc (covers tsf:npc)",
                }
            )

        # 1.21.11+ items/blocks atlas split: peel item/ into items.json; rewrite block
        # models that sampled item/ onto block/item/ copies (dual-stitch is rejected in-game).
        if _target_at_least(args.to, 1, 21, 11):
            blocks_atlas = atlases / "blocks.json"
            items_atlas = atlases / "items.json"

            def _item_dirs(path: Path) -> list[str]:
                out: list[str] = []
                if not path.is_file():
                    return out
                try:
                    data = load(path)
                except Exception:
                    return out
                for entry in data.get("sources") or []:
                    if not isinstance(entry, dict):
                        continue
                    typ = str(entry.get("type") or "")
                    if typ not in ("directory", "minecraft:directory"):
                        continue
                    src = entry.get("source")
                    if isinstance(src, str):
                        src = src.replace("\\", "/").strip("/")
                        if src == "item" or src.startswith("item/"):
                            out.append(src)
                return out

            def _item_cover_sources(path: Path) -> list[dict]:
                out: list[dict] = []
                if not path.is_file():
                    return out
                try:
                    data = load(path)
                except Exception:
                    return out
                for entry in data.get("sources") or []:
                    if not isinstance(entry, dict):
                        continue
                    typ = str(entry.get("type") or "")
                    if typ in ("directory", "minecraft:directory"):
                        src = entry.get("source")
                        if isinstance(src, str):
                            src = src.replace("\\", "/").strip("/")
                            if src == "item" or src.startswith("item/"):
                                out.append(entry)
                    elif typ in ("single", "minecraft:single"):
                        resource = entry.get("resource")
                        if isinstance(resource, str):
                            resource = resource.replace("\\", "/")
                            if ":" in resource:
                                resource = resource.split(":", 1)[1]
                            resource = resource.lstrip("/")
                            if resource == "item" or resource.startswith("item/"):
                                out.append(entry)
                return out

            item_on_blocks = _item_dirs(blocks_atlas)
            item_on_items = _item_dirs(items_atlas)
            blocks_item_sources = _item_cover_sources(blocks_atlas)

            if not items_atlas.is_file():
                findings.append(
                    {
                        "level": "error",
                        "id": "tsf_missing_items_atlas",
                        "msg": "1.21.11+ TSF missing assets/minecraft/atlases/items.json after atlas split",
                    }
                )
            elif item_on_blocks or blocks_item_sources:
                findings.append(
                    {
                        "level": "error",
                        "id": "tsf_items_sources_still_in_blocks_atlas",
                        "msg": "1.21.11+ TSF blocks.json must not keep item/ sources after peel "
                        "(use block/item/ copy+rewrite for crop-host props)",
                        "examples": item_on_blocks[:10]
                        or [
                            (e.get("source") or e.get("resource"))
                            for e in blocks_item_sources[:10]
                        ],
                    }
                )
            elif not item_on_items and not _item_cover_sources(items_atlas):
                findings.append(
                    {
                        "level": "error",
                        "id": "tsf_items_atlas_empty",
                        "msg": "1.21.11+ TSF items.json missing item/ sources after peel",
                    }
                )
            else:
                findings.append(
                    {
                        "level": "info",
                        "id": "tsf_items_atlas_split_ok",
                        "msg": "items.json present after 1.21.11 atlas peel; blocks.json has no item/ sources",
                    }
                )

            leftover_item_tex: list[str] = []
            seen_tex: set[str] = set()
            rewritten_block_item: list[str] = []
            assets_root = pack / "assets"
            if assets_root.is_dir():
                for ns_dir in assets_root.iterdir():
                    if not ns_dir.is_dir():
                        continue
                    block_models = ns_dir / "models" / "block"
                    if block_models.is_dir():
                        for model_path in block_models.rglob("*.json"):
                            try:
                                model = load(model_path)
                            except Exception:
                                continue
                            textures = model.get("textures") if isinstance(model, dict) else None
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
                                    if path not in seen_tex:
                                        seen_tex.add(path)
                                        leftover_item_tex.append(path)
                    copies = ns_dir / "textures" / "block" / "item"
                    if copies.is_dir():
                        for png in copies.rglob("*.png"):
                            rel = png.relative_to(ns_dir / "textures").as_posix()
                            rewritten_block_item.append(
                                rel[:-4] if rel.endswith(".png") else rel
                            )

            if leftover_item_tex:
                findings.append(
                    {
                        "level": "error",
                        "id": "tsf_block_models_still_reference_item_textures",
                        "msg": "TSF block models still reference item/ textures; expected block/item/ rewrite",
                        "examples": leftover_item_tex[:15],
                        "count": len(leftover_item_tex),
                    }
                )
            elif rewritten_block_item:
                findings.append(
                    {
                        "level": "info",
                        "id": "tsf_block_item_textures_rewritten",
                        "msg": "TSF block-referenced item/ textures copied+rewritten to block/item/",
                        "examples": rewritten_block_item[:15],
                        "count": len(rewritten_block_item),
                    }
                )

    # 26.2+: TSF oak entity sign must be sliced to block/oak_sign (Mojang Slicer v1.2.1).
    if _target_at_least_26_2(args.to):
        entity_oak = (
            pack / "assets" / "minecraft" / "textures" / "entity" / "signs" / "oak.png"
        )
        block_oak = pack / "assets" / "minecraft" / "textures" / "block" / "oak_sign.png"
        if entity_oak.is_file() and not block_oak.is_file():
            findings.append(
                {
                    "level": "error",
                    "id": "tsf_sign_entity_not_sliced_26_2",
                    "msg": "TSF entity/signs/oak.png remains without block/oak_sign.png after 26.2 slicer",
                }
            )
        elif block_oak.is_file():
            findings.append(
                {
                    "level": "info",
                    "id": "tsf_sign_entity_sliced_26_2",
                    "msg": "TSF oak sign sliced to textures/block/oak_sign.png",
                }
            )

    errors = [f for f in findings if f["level"] == "error"]
    report = {"pack": str(pack), "to": args.to, "ok": len(errors) == 0, "findings": findings}
    out = Path(__file__).resolve().parent.parent / "reports" / "assert_tsf.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    print(f"[assert_tsf] wrote {out}", file=sys.stderr)
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
