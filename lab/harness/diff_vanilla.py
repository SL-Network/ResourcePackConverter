#!/usr/bin/env python3
"""Diff two extracted vanilla asset trees → knowledge/renames/<from>-<to>.json

Expects:
  knowledge/vanilla/<ver>/assets/...
produced by scripts/fetch_vanilla.sh
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


def index_pngs(root: Path) -> dict[str, str]:
    """path relative to assets/ -> sha256 of file"""
    assets = root / "assets"
    out = {}
    if not assets.is_dir():
        return out
    for p in assets.rglob("*"):
        if p.is_file():
            rel = p.relative_to(assets).as_posix()
            h = hashlib.sha256(p.read_bytes()).hexdigest()
            out[rel] = h
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-ver", required=True)
    ap.add_argument("--to-ver", required=True)
    args = ap.parse_args()
    root = Path(__file__).resolve().parent.parent
    a = root / "knowledge" / "vanilla" / args.from_ver
    b = root / "knowledge" / "vanilla" / args.to_ver
    if not a.is_dir() or not b.is_dir():
        print(f"Missing vanilla trees:\n  {a}\n  {b}\nRun scripts/fetch_vanilla.sh first.", file=sys.stderr)
        return 1

    ia, ib = index_pngs(a), index_pngs(b)
    by_hash_a: dict[str, list[str]] = {}
    for path, h in ia.items():
        by_hash_a.setdefault(h, []).append(path)
    by_hash_b: dict[str, list[str]] = {}
    for path, h in ib.items():
        by_hash_b.setdefault(h, []).append(path)

    moved = []
    for h, paths_a in by_hash_a.items():
        paths_b = by_hash_b.get(h) or []
        if not paths_b:
            continue
        for pa in paths_a:
            for pb in paths_b:
                if pa != pb:
                    moved.append({"from": pa, "to": pb, "sha256": h})

    only_a = sorted(set(ia) - set(ib))
    only_b = sorted(set(ib) - set(ia))
    same = sorted(set(ia) & set(ib))

    report = {
        "from": args.from_ver,
        "to": args.to_ver,
        "same_path_count": len(same),
        "removed_count": len(only_a),
        "added_count": len(only_b),
        "same_hash_different_path": moved[:5000],
        "removed_sample": only_a[:200],
        "added_sample": only_b[:200],
    }
    out = root / "knowledge" / "renames" / f"{args.from_ver}-{args.to_ver}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"Wrote {out} (moved={len(moved)} removed={len(only_a)} added={len(only_b)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
