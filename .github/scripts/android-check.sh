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
errors = [s for s in lines if not s.strip().startswith('> Task') and
          (s.strip().startswith(('e: ', '> ', 'FAILURE:', 'Execution failed', 'Error:')) or ': Error:' in s)]
# One multiline annotation avoids GitHub's ten-errors-per-step annotation cap.
# XML retains all lint error IDs/locations even if Gradle truncates console output.
import xml.etree.ElementTree as ET
lint = []
for report in Path('app/build/reports').glob('lint-results-*.xml'):
    for issue in ET.parse(report).getroot().findall('issue'):
        if issue.get('severity') not in ('Error', 'Fatal'):
            continue
        loc = issue.find('location')
        lint.append(f"{loc.get('file', '') if loc is not None else ''}:{loc.get('line', '') if loc is not None else ''}: [{issue.get('id')}] {issue.get('message')}")
message = '\n'.join(lint or errors or lines[-25:])[:60000]
message = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f'::error title=Android verification::{message}')
PY
fi
exit "$result"
