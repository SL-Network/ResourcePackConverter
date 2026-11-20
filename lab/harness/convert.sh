#!/usr/bin/env bash
# Usage: ./harness/convert.sh --from 1.16.5 --to 1.21.4 --pack fixtures/.../my_pack [--out reports/work/...]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/harness/ResourcePackConverter.jar"
if [[ ! -e "$JAR" ]]; then
  echo "[convert] jar missing; running build..." >&2
  "$ROOT/harness/build.sh"
fi

FROM="" TO="" PACK="" OUT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --from) FROM="$2"; shift 2 ;;
    --to) TO="$2"; shift 2 ;;
    --pack) PACK="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$FROM" || -z "$TO" || -z "$PACK" ]]; then
  echo "Usage: $0 --from <ver> --to <ver> --pack <pack-dir-or-zip> [--out <workdir>]" >&2
  exit 2
fi

PACK="$(cd "$(dirname "$PACK")" && pwd)/$(basename "$PACK")"
NAME="$(basename "$PACK" .zip)"
OUT="${OUT:-$ROOT/reports/work/${NAME}_${FROM}_to_${TO}}"
rm -rf "$OUT"
mkdir -p "$OUT/input"

if [[ -f "$PACK" && "$PACK" == *.zip ]]; then
  mkdir -p "$OUT/input/$NAME"
  unzip -q "$PACK" -d "$OUT/input/$NAME"
  # flatten if zip contained a single root folder with pack.mcmeta deeper
  if [[ ! -f "$OUT/input/$NAME/pack.mcmeta" ]]; then
    meta="$(find "$OUT/input/$NAME" -name pack.mcmeta | head -1 || true)"
    if [[ -n "$meta" ]]; then
      src="$(dirname "$meta")"
      rm -rf "$OUT/input/_tmp" && mkdir -p "$OUT/input/_tmp"
      cp -a "$src"/. "$OUT/input/_tmp/"
      rm -rf "$OUT/input/$NAME"
      mv "$OUT/input/_tmp" "$OUT/input/$NAME"
    fi
  fi
elif [[ -d "$PACK" ]]; then
  mkdir -p "$OUT/input/$NAME"
  cp -a "$PACK"/. "$OUT/input/$NAME/"
else
  echo "[convert] pack not found: $PACK" >&2
  exit 1
fi

echo "[convert] $FROM → $TO  pack=$NAME  out=$OUT"
java -jar "$JAR" --input "$OUT/input" --from "$FROM" --to "$TO" --debug true | tee "$OUT/convert.log"
echo "$OUT" > "$OUT/WORKDIR"
# ResourcePackConverter writes <name>_converted next to the input copy
CONVERTED="$OUT/input/${NAME}_converted"
if [[ ! -f "$CONVERTED/pack.mcmeta" ]]; then
  # fallback: any *_converted with pack.mcmeta under input/
  CONVERTED="$(find "$OUT/input" -maxdepth 1 -type d -name '*_converted' | head -1 || true)"
fi
if [[ -z "${CONVERTED}" || ! -f "$CONVERTED/pack.mcmeta" ]]; then
  echo "[convert] ERROR: converted pack not found under $OUT/input" >&2
  exit 1
fi
echo "[convert] done: $CONVERTED"
echo "$CONVERTED"
