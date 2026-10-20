#!/usr/bin/env bash
# Preserve Gradle's exit status; expose compiler errors through GitHub's API
# annotations even when downloading the full Actions log is unavailable.
set -uo pipefail
log=$(mktemp)
trap 'rm -f "$log"' EXIT
./gradlew --no-daemon "$@" >"$log" 2>&1
result=$?
cat "$log"
if [ "$result" -ne 0 ]; then
  python3 - "$log" <<'PY'
import sys
from pathlib import Path
lines = Path(sys.argv[1]).read_text(errors='replace').splitlines()
errors = [s for s in lines if s.strip().startswith(('e: ', '> ', 'FAILURE:', 'Execution failed', 'Error:'))]
for line in (errors or lines[-25:])[-35:]:
    line = line[:1800].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error title=Android verification::{line}')
PY
fi
exit "$result"
