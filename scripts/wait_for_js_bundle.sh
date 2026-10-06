#!/bin/bash
set -euo pipefail

# Blocks until the background JS bundle started by common-setup
# (js_bundle_in_background) has finished, prints its log, and fails if the
# bundler failed. The bundler holds $RUNNER_TEMP/js-bundle.lock for its whole
# run, so taking the lock here waits for it without polling.

lock="$RUNNER_TEMP/js-bundle.lock"
status_file="$RUNNER_TEMP/js-bundle.status"

flock "$lock" true

echo "=== JS bundle log ==="
cat "$RUNNER_TEMP/js-bundle.log"

if [ ! -f "$status_file" ]; then
  echo "::error::JS bundle finished without recording an exit status"
  exit 1
fi
status=$(cat "$status_file")
if [ "$status" != "0" ]; then
  echo "::error::JS bundle failed (exit $status)"
  exit "$status"
fi
