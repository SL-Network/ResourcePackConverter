#!/usr/bin/env bash
# Fixtures expected to fail until concern/hop work lands. Exit 1 if any unexpectedly pass? 
# For now: run and report; exit 0 always so CI can collect baselines. Agents fixing a concern should move the fixture back to synthetic/ when green.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/harness/build.sh"
echo "[backlog] running known-gap fixtures (failures expected until fixed)"
FAIL=0
PASS=0
shopt -s nullglob
for pack in "$ROOT/fixtures/backlog"/*; do
  [[ -d "$pack" ]] || continue
  name="$(basename "$pack")"
  FROM="1.16.5"; TO="1.21.11"
  if [[ -f "$pack/fixture.json" ]]; then
    FROM="$(python3 -c "import json;print(json.load(open('$pack/fixture.json')).get('from','1.16.5'))")"
    TO="$(python3 -c "import json;print(json.load(open('$pack/fixture.json')).get('to','1.21.11'))")"
  fi
  echo "===== BACKLOG $name ($FROM → $TO) ====="
  SRC="$pack"; [[ -d "$pack/pack" ]] && SRC="$pack/pack"
  OUT="$ROOT/reports/work/backlog_${name}"
  CONVERTED="$("$ROOT/harness/convert.sh" --from "$FROM" --to "$TO" --pack "$SRC" --out "$OUT" | tail -1)"
  if python3 "$ROOT/harness/score.py" --pack "$CONVERTED" --to "$TO" --name "backlog_${name}"; then
    echo "[backlog] UNEXPECTED PASS: $name — move to fixtures/synthetic/"
    PASS=$((PASS+1))
  else
    FAIL=$((FAIL+1))
  fi
done
echo "[backlog] still failing=$FAIL  newly passing=$PASS"
