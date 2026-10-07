#!/bin/bash
#
# Capture a device's calendar state for offline analysis.
#
# Lets the portable-event-identity work continue without keeping a device
# attached: the snapshot holds everything the probes and simulations need.
#
# WHAT THIS CAN AND CANNOT CAPTURE
#
# The Calendar Provider's own database (com.android.providers.calendar) is NOT
# pullable -- `run-as` only works on your own debuggable package, and that is a
# different app. What we capture instead is its full *contents*, exported via
# `content query` as text. That is enough to re-run any read-only analysis
# offline; it is not a restorable database file.
#
# Our own app databases (Events/RoomEvents etc.) ARE pulled verbatim, because
# they belong to a debuggable package we control.
#
# PRIVACY: output contains real calendar data -- event titles, times, account
# names. It is written under ./tmp/, which is gitignored. Do not commit it.
#
# Usage: ./scripts/capture_calendar_snapshot.sh [output_dir]

set -euo pipefail

readonly APP_PACKAGE="com.github.quarck.calnotify"
readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly OUT_DIR="${1:-${PROJECT_ROOT}/tmp/calendar_snapshot_$(date +%Y%m%d_%H%M%S)}"

die() { echo "Error: $*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb not found."
adb get-state >/dev/null 2>&1 || die "no device connected."

mkdir -p "${OUT_DIR}/provider" "${OUT_DIR}/app_databases"

echo "== calendar snapshot =="
echo "destination: ${OUT_DIR}"
echo

# ----------------------------------------------------------- device info ---
{
    echo "captured_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    for prop in ro.product.model ro.build.version.sdk ro.build.version.release ro.build.fingerprint; do
        echo "${prop}=$(adb shell getprop "$prop" 2>/dev/null | tr -d '\r')"
    done
} > "${OUT_DIR}/device_info.txt"
echo "device_info.txt"

# ------------------------------------------------------- provider export ---
# Exported as text: the provider's own DB file is unreachable without root.
dump_provider() {
    local name="$1" uri="$2" projection="$3"
    adb shell "content query --uri '${uri}' --projection '${projection}' --where \"1=1\"" 2>/dev/null \
        | tr -d '\r' > "${OUT_DIR}/provider/${name}.txt"
    echo "provider/${name}.txt  ($(wc -l < "${OUT_DIR}/provider/${name}.txt") rows)"
}

dump_provider calendars \
    "content://com.android.calendar/calendars" \
    "_id:account_name:account_type:ownerAccount:name:calendar_displayName:calendar_color:sync_events:visible:isPrimary:calendar_timezone:calendar_access_level"

# Everything the identity and heuristic work needs, including deleted rows so
# tombstone handling can be exercised offline.
dump_provider events \
    "content://com.android.calendar/events" \
    "_id:calendar_id:_sync_id:uid2445:original_sync_id:original_id:title:eventLocation:dtstart:dtend:duration:allDay:rrule:rdate:eventStatus:deleted:lastSynced"

dump_provider reminders \
    "content://com.android.calendar/reminders" \
    "_id:event_id:minutes:method"

# Instances need an explicit time window in the URI. +/- 1 year, matching the
# provider's own typical sync horizon.
now_ms=$(( $(date +%s) * 1000 ))
year_ms=$(( 365 * 24 * 60 * 60 * 1000 ))
adb shell "content query --uri 'content://com.android.calendar/instances/when/$((now_ms - year_ms))/$((now_ms + year_ms))' --projection 'event_id:begin:end:startDay:endDay' --where \"1=1\"" 2>/dev/null \
    | tr -d '\r' > "${OUT_DIR}/provider/instances.txt"
echo "provider/instances.txt  ($(wc -l < "${OUT_DIR}/provider/instances.txt") rows)"

# --------------------------------------------------------- app databases ---
# exec-out, NOT shell: `adb shell` mangles binary streams and the database
# reads back as malformed. The -wal file matters just as much as the main file;
# without it recent writes are missing and integrity_check fails.
if adb shell "run-as ${APP_PACKAGE} true" >/dev/null 2>&1; then
    echo
    for db in $(adb shell "run-as ${APP_PACKAGE} ls databases" 2>/dev/null | tr -d '\r'); do
        case "$db" in
            *-journal|*-shm) continue ;;   # -shm is rebuildable; journals are noise
        esac
        adb exec-out run-as "${APP_PACKAGE}" cat "databases/${db}" \
            > "${OUT_DIR}/app_databases/${db}" 2>/dev/null || true
        echo "app_databases/${db}  ($(wc -c < "${OUT_DIR}/app_databases/${db}") bytes)"
    done

    adb exec-out run-as "${APP_PACKAGE}" tar c shared_prefs 2>/dev/null \
        | tar x -C "${OUT_DIR}" 2>/dev/null \
        && echo "shared_prefs/" || echo "shared_prefs/  (skipped)"
else
    echo
    echo "NOTE: ${APP_PACKAGE} not installed or not debuggable - skipping app databases." >&2
fi

# ------------------------------------------------------------- provenance ---
cat > "${OUT_DIR}/README.md" <<EOF
# Calendar snapshot

Captured $(date -u +%Y-%m-%dT%H:%M:%SZ) by \`scripts/capture_calendar_snapshot.sh\`
for the portable event identity work (docs/dev_todo/portable_event_identity.md).

**Contains real personal calendar data. Under ./tmp/, which is gitignored. Do not commit.**

## Layout

- \`device_info.txt\` - model, API level, build fingerprint
- \`provider/\` - Calendar Provider contents as \`content query\` text.
  Not a database file: the provider's DB belongs to another app and cannot be
  pulled without root. These exports are enough to re-run read-only analysis.
  \`events.txt\` deliberately includes \`deleted=1\` tombstones.
- \`app_databases/\` - this app's own SQLite files, pulled verbatim.
  Read with the \`-wal\` file present, or SQLite reports the DB as malformed.
- \`shared_prefs/\` - app preferences, including which storage is active.

## Re-running the UID probe offline

\`\`\`bash
awk -f scripts/lib/uid2445_tally.awk provider/calendars.txt provider/events.txt
\`\`\`
EOF
echo "README.md"

echo
echo "Snapshot complete: ${OUT_DIR}"
du -sh "${OUT_DIR}" | awk '{print "size: " $1}'
