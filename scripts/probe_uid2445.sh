#!/bin/bash
#
# Probe whether CalendarContract.Events.UID_2445 is populated on a device.
#
# Answers the question that gates Phase 0 of
# docs/dev_todo/portable_event_identity.md: does UID_2445 actually have values
# on real calendars? There is a long-standing unresolved Android issue claiming
# it is always null (https://issuetracker.google.com/issues/37053160), and the
# plan's exact-match path depends entirely on that column.
#
# Read-only. Needs no build, install, or instrumentation run -- it reads the
# Calendar Provider over `adb shell content query`.
#
# REQUIRES A LIVE DEVICE with real, synced calendars. It cannot read an app
# backup: UID_2445 lives in the Calendar Provider's database
# (com.android.providers.calendar), a different app that we do not back up.
# See "Can this run against a backup instead of a live device?" in the plan.
#
# Usage:
#   ./scripts/probe_uid2445.sh [output_file]

set -euo pipefail

readonly OUTPUT_FILE="${1:-}"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TALLY_AWK="${SCRIPT_DIR}/lib/uid2445_tally.awk"

readonly CALENDARS_URI="content://com.android.calendar/calendars"
readonly EVENTS_URI="content://com.android.calendar/events"

die() {
    echo "Error: $*" >&2
    exit 1
}

# `content query` prints one "Row: N k=v, k=v" line per row. Strip CRs, which
# adb adds on some platforms and which would otherwise end up inside values.
query_provider() {
    local uri="$1" projection="$2"
    adb shell content query --uri "$uri" --projection "$projection" 2>/dev/null | tr -d '\r'
}

check_prerequisites() {
    command -v adb >/dev/null 2>&1 \
        || die "adb not found. Install Android SDK platform tools."

    adb get-state >/dev/null 2>&1 \
        || die "no device connected. This probe needs a live device with real synced calendars; it cannot read an app backup."

    [ -f "$TALLY_AWK" ] || die "missing $TALLY_AWK"
}

print_device_header() {
    local model api
    model="$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
    api="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"

    echo "== UID_2445 population probe =="
    echo "device: ${model} (API ${api})"
    echo
}

main() {
    check_prerequisites
    print_device_header

    local calendars events
    calendars="$(query_provider "$CALENDARS_URI" "_id:account_type:account_name")"
    [ -n "$calendars" ] \
        || die "no calendars readable. Grant calendar permission to the shell, or check the device."

    echo "calendars found:"
    echo "$calendars" | sed 's/^/  /'
    echo

    events="$(query_provider "$EVENTS_URI" "calendar_id:uid2445:_sync_id:deleted")"

    # Calendars first: the tally needs the id -> account_type map before it can
    # attribute events. Order matters, which is why they are passed separately
    # rather than concatenated.
    local report
    report="$(awk -f "$TALLY_AWK" <(echo "$calendars") <(echo "$events"))"

    echo "$report"

    if [ -n "$OUTPUT_FILE" ]; then
        { print_device_header; echo "$report"; } > "$OUTPUT_FILE"
        echo
        echo "Saved to: $OUTPUT_FILE"
    fi
}

main "$@"
