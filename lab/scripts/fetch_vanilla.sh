#!/usr/bin/env bash
# Download client jar for a version and extract assets/ into knowledge/vanilla/<ver>/
# Usage: ./scripts/fetch_vanilla.sh 1.16.5 [1.20.2 ...]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST_URL="https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <version> [version...]" >&2
  exit 2
fi

command -v jq >/dev/null || { echo "jq required" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl required" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -fsSL "$MANIFEST_URL" -o "$TMP/manifest.json"

for VER in "$@"; do
  OUT="$ROOT/knowledge/vanilla/$VER"
  echo "[fetch] $VER → $OUT"
  URL="$(jq -r --arg v "$VER" '.versions[] | select(.id==$v) | .url' "$TMP/manifest.json")"
  if [[ -z "$URL" || "$URL" == "null" ]]; then
    echo "[fetch] version not found in manifest: $VER" >&2
    exit 1
  fi
  curl -fsSL "$URL" -o "$TMP/$VER.json"
  CLIENT="$(jq -r '.downloads.client.url' "$TMP/$VER.json")"
  mkdir -p "$OUT"
  curl -fsSL "$CLIENT" -o "$TMP/client-$VER.jar"
  rm -rf "$OUT/assets"
  unzip -q -o "$TMP/client-$VER.jar" "assets/*" -d "$OUT"
  echo "[fetch] extracted $(find "$OUT/assets" -type f | wc -l) files"
done
