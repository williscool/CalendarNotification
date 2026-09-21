#!/bin/bash
#
# Live progress for a running Gradle unit-test build.
#
# Gradle's Test task prints nothing per-test by default, so a long run looks
# identical to a hung one -- `--console=plain` shows "> Task :app:testX..."
# and then silence for minutes. This reads the JUnit XML files as they land
# instead, which works on a build that is ALREADY RUNNING (no config change,
# no restart).
#
# Usage: ./scripts/watch_test_progress.sh [results_dir] [interval_seconds]

set -uo pipefail

RESULTS="${1:-/mnt/c/dev/CN/android/app/build/test-results/testX8664DebugUnitTest}"
INTERVAL="${2:-15}"

[ -d "$RESULTS" ] || { echo "No results dir yet: $RESULTS" >&2; exit 1; }

while true; do
    python3 - "$RESULTS" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET
from datetime import datetime

results = sys.argv[1]
files = sorted(glob.glob(os.path.join(results, "*.xml")), key=os.path.getmtime)

classes = tests = failures = 0
latest = []
for path in files:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue            # still being written
    classes += 1
    tests += int(root.get("tests") or 0)
    failures += int(root.get("failures") or 0) + int(root.get("errors") or 0)
    latest.append((root.get("name", "?").split(".")[-1], root.get("tests"), root.get("time")))

stamp = datetime.now().strftime("%H:%M:%S")
flag = f"  FAILURES={failures}" if failures else ""
print(f"[{stamp}] classes={classes} tests={tests}{flag}")
for name, n, secs in latest[-2:]:
    print(f"           {name}: {n} tests, {secs}s")
PY
    sleep "$INTERVAL"
done
