#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/harness/build.sh"

FAIL=0
shopt -s nullglob
for pack in "$ROOT/fixtures/synthetic"/*; do
  [[ -d "$pack" ]] || continue
  name="$(basename "$pack")"
  # fixture may contain fixture.json with from/to
  FROM="1.16.5"
  TO="1.21.11"
  if [[ -f "$pack/fixture.json" ]]; then
    FROM="$(python3 -c "import json;print(json.load(open('$pack/fixture.json')).get('from','1.16.5'))")"
    TO="$(python3 -c "import json;print(json.load(open('$pack/fixture.json')).get('to','1.21.11'))")"
  fi
  echo "===== L0 $name ($FROM → $TO) ====="
  OUT="$ROOT/reports/work/l0_${name}"
  # convert expects pack root = directory with pack.mcmeta
  SRC="$pack"
  if [[ -d "$pack/pack" ]]; then SRC="$pack/pack"; fi
  CONVERTED="$("$ROOT/harness/convert.sh" --from "$FROM" --to "$TO" --pack "$SRC" --out "$OUT" | tail -1)"
  EXPECT_ARGS=()
  if [[ -f "$pack/fixture.json" ]]; then
    # optional expect_error ids
    while IFS= read -r id; do
      [[ -n "$id" ]] && EXPECT_ARGS+=(--expect-error "$id")
    done < <(python3 -c "import json;d=json.load(open('$pack/fixture.json'));
print('\n'.join(d.get('expect_error') or []))")
    # optional expect_paths (must exist on converted pack)
    while IFS= read -r rel; do
      [[ -n "$rel" ]] && EXPECT_ARGS+=(--expect-path "$rel")
    done < <(python3 -c "import json;d=json.load(open('$pack/fixture.json'));
print('\n'.join(d.get('expect_paths') or []))")
  fi
  if ! python3 "$ROOT/harness/score.py" --pack "$CONVERTED" --to "$TO" --name "l0_${name}" "${EXPECT_ARGS[@]+"${EXPECT_ARGS[@]}"}"; then
    FAIL=1
  fi
done

if [[ $FAIL -ne 0 ]]; then
  echo "[run_l0] FAILED — see reports/score_l0_*.json" >&2
  exit 1
fi
echo "[run_l0] OK"
