#!/bin/bash

# Probe whether CalendarContract.Events.UID_2445 is populated on a device.
#
# Answers the question that gates Phase 0 of
# docs/dev_todo/portable_event_identity.md: does UID_2445 actually have values
# on real calendars? There is a long-standing unresolved Android issue claiming
# it is always null (https://issuetracker.google.com/issues/37053160), and the
# plan's exact-match path depends entirely on that column.
#
# This reads the Calendar Provider over adb with `content query`, so it needs
# no build, no install, and no instrumentation test. It is read-only.
#
# IMPORTANT: this must run against a LIVE device with real, synced calendars.
# It cannot run against an app backup -- UID_2445 lives in the Calendar
# Provider's database (com.android.providers.calendar), which is a different
# app that we do not back up. Our own backup contains only this app's
# databases and prefs. See the "Can this run offline?" section in the plan.
#
# Usage: ./probe_uid2445.sh [output_file]

set -euo pipefail

OUT="${1:-}"

if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb not found. Install Android SDK platform tools." >&2
    exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "Error: no device connected. This probe requires a live device with" >&2
    echo "real synced calendars -- it cannot read an app backup." >&2
    exit 1
fi

echo "== UID_2445 population probe =="
echo "device: $(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r') " \
     "(API $(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r'))"
echo

# Pull the raw rows once, then tally locally. `content query` prints one line
# per row shaped like: "Row: 0 _id=1, account_type=com.google, uid2445=NULL".
RAW_CALENDARS="$(adb shell content query \
    --uri content://com.android.calendar/calendars \
    --projection _id:account_type:account_name 2>/dev/null | tr -d '\r')"

if [ -z "$RAW_CALENDARS" ]; then
    echo "No calendars readable. Grant calendar permission to the shell or check the device." >&2
    exit 1
fi

echo "$RAW_CALENDARS" | sed 's/^/  /'
echo

RAW_EVENTS="$(adb shell content query \
    --uri content://com.android.calendar/events \
    --projection calendar_id:uid2445:_sync_id:deleted 2>/dev/null | tr -d '\r')"

# Tally in awk: join each event to its calendar's account_type, skip tombstones,
# and count how many carry each identifier.
{
    echo "$RAW_CALENDARS"
    echo "---SPLIT---"
    echo "$RAW_EVENTS"
} | awk '
function val(line, key,   parts, i, n, p) {
    # Rows look like: "Row: 0 _id=1, account_type=com.google, uid2445=NULL".
    # Split on ", " and compare keys EXACTLY -- a substring match would let
    # "_id" match inside "calendar_id", and values containing "@" or "." (UIDs,
    # emails) break naive regex trimming.
    sub(/^Row: [0-9]+ /, "", line)
    n = split(line, parts, ", ")
    for (i = 1; i <= n; i++) {
        p = index(parts[i], "=")
        if (p > 0 && substr(parts[i], 1, p - 1) == key)
            return substr(parts[i], p + 1)
    }
    return ""
}
/^---SPLIT---$/ { events = 1; next }
!events {
    id = val($0, "_id"); at = val($0, "account_type")
    if (id != "") acct[id] = (at == "" ? "(null)" : at)
    next
}
{
    del = val($0, "deleted")
    if (del == "1") next                      # skip tombstones
    cid = val($0, "calendar_id")
    a = (cid in acct) ? acct[cid] : "(unknown)"

    uid = val($0, "uid2445"); sid = val($0, "_sync_id")
    hasu = (uid != "" && uid != "NULL")
    hass = (sid != "" && sid != "NULL")

    total[a]++; grand_total++
    if (hasu) { u[a]++; grand_u++ }
    if (hass) { s[a]++; grand_s++ }
    if (!hasu && !hass) { n[a]++; grand_n++ }
}
END {
    if (grand_total == 0) {
        print "VERDICT: INCONCLUSIVE - no events found. Re-run on a device with real calendars."
        exit
    }
    printf "%-28s %7s %7s %6s %8s %9s\n", "accountType", "total", "uid", "pct", "syncId", "neither"
    for (a in total)
        printf "%-28s %7d %7d %5d%% %8d %9d\n", a, total[a], u[a]+0, (u[a]+0)*100/total[a], s[a]+0, n[a]+0
    printf "%s\n", "------------------------------------------------------------------------"
    printf "%-28s %7d %7d %5d%% %8d %9d\n", "OVERALL", grand_total, grand_u+0, (grand_u+0)*100/grand_total, grand_s+0, grand_n+0

    uidpct = (grand_u+0) * 100 / grand_total
    eitherpct = (grand_total - (grand_n+0)) * 100 / grand_total
    print ""
    if (uidpct >= 90)
        printf "VERDICT: VIABLE - UID_2445 populated for %d%% of events; exact path (Phases 0-5) stands\n", uidpct
    else if (eitherpct >= 90)
        printf "VERDICT: VIABLE WITH FALLBACK - UID_2445 only %d%%, but %d%% have UID or _SYNC_ID\n", uidpct, eitherpct
    else if (uidpct >= 50)
        printf "VERDICT: MIXED - UID_2445 %d%%, either %d%%; Phase 6 heuristic needed to cover the rest\n", uidpct, eitherpct
    else
        printf "VERDICT: NOT VIABLE - UID_2445 only %d%%; Phase 6 heuristic becomes primary, revisit the plan\n", uidpct
}' | if [ -n "$OUT" ]; then tee "$OUT"; else cat; fi

if [ -n "$OUT" ]; then
    echo
    echo "Saved to: $OUT"
fi
