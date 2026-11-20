#!/usr/bin/env bash
set -euo pipefail
LAB="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$LAB/.." && pwd)"
cd "$REPO"
# clean avoids stale shaded Console jar embedding outdated Forwards classes
echo "[build] mvn -q -DskipTests clean package"
mvn -q -DskipTests clean package
TARGET="$REPO/Applications/Console/target"
JAR=""
for cand in \
  "$TARGET"/ResourcePackConverter-Console-*.jar \
  "$TARGET"/Console-*-shaded.jar \
  "$TARGET"/Console-*.jar
do
  [[ -f "$cand" ]] || continue
  [[ "$(basename "$cand")" == original-* ]] && continue
  JAR="$cand"
  break
done
# glob may not expand — pick explicitly
if [[ -z "${JAR}" ]]; then
  JAR="$(find "$TARGET" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' | head -1 || true)"
fi
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "[build] ERROR: Console jar not found under Applications/Console/target/" >&2
  ls -la "$TARGET" 2>/dev/null || true
  exit 1
fi
mkdir -p "$LAB/harness"
ln -sfn "$JAR" "$LAB/harness/ResourcePackConverter.jar"
echo "[build] OK → $JAR"
echo "[build] symlink harness/ResourcePackConverter.jar"
