#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TSF="$ROOT/fixtures/golden/adventure/tsf_1.16.5"
if [[ ! -f "$TSF/pack.mcmeta" ]]; then
  echo "[run_tsf] missing $TSF/pack.mcmeta — link or copy the pack first" >&2
  exit 1
fi

"$ROOT/harness/build.sh"
TO="${1:-1.21.11}"
FROM="${FROM:-1.16.5}"
echo "===== L2 TSF ($FROM → $TO) ====="
OUT="$ROOT/reports/work/tsf_${FROM}_to_${TO}"
CONVERTED="$("$ROOT/harness/convert.sh" --from "$FROM" --to "$TO" --pack "$TSF" --out "$OUT" | tail -1)"
python3 "$ROOT/harness/score.py" --pack "$CONVERTED" --to "$TO" --name "tsf" || true
python3 "$ROOT/harness/assert_tsf.py" --pack "$CONVERTED" --to "$TO"
