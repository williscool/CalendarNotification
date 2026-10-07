#!/bin/bash
#
# Tests for scripts/lib/uid2445_tally.awk.
#
# The awk program is the only part of the UID_2445 probe with real logic, and
# it cannot be exercised without a device unless it is tested against captured
# provider output -- which is what scripts/lib/testdata/ holds.
#
# These fixtures encode bugs that were actually hit while writing it:
#   - "_id" matching as a substring inside "calendar_id"
#   - values containing "@" and "." (every UID and email) breaking parsing
#   - "_sync_id=" being mistaken for a calendar row's "_id="
#   - tombstones (deleted=1) inflating the denominator
#
# Usage: ./scripts/lib/test_uid2445_tally.sh

set -uo pipefail

readonly DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly AWK_PROGRAM="${DIR}/uid2445_tally.awk"
readonly DATA="${DIR}/testdata"

failures=0

# run_tally <calendars_fixture> <events_fixture>
run_tally() {
    awk -f "$AWK_PROGRAM" "${DATA}/$1" "${DATA}/$2"
}

# expect_contains <description> <output> <substring>
expect_contains() {
    local description="$1" output="$2" needle="$3"

    if printf '%s' "$output" | grep -qF -- "$needle"; then
        echo "  PASS  ${description}"
    else
        echo "  FAIL  ${description}"
        echo "        expected to find: ${needle}"
        echo "        in output:"
        printf '%s\n' "$output" | sed 's/^/          /'
        failures=$((failures + 1))
    fi
}

echo "== uid2445_tally.awk =="

# --- mixed: one synced calendar, one local, one tombstone -------------------
out="$(run_tally calendars_mixed.txt events_mixed.txt)"

# google calendar has 4 rows but one is deleted=1, so the total must be 3.
expect_contains "tombstones excluded from totals" "$out" "com.google                       3"
expect_contains "local calendar reports 0% uid"   "$out" "LOCAL                            2       0     0%"
expect_contains "events with no identifier counted as neither" "$out" "OVERALL                          5       2    40%        3         2"
expect_contains "low coverage reports NOT VIABLE" "$out" "VERDICT: NOT VIABLE"

# --- all events carry a UID -------------------------------------------------
out="$(run_tally calendars_google.txt events_all_uid.txt)"
expect_contains "full coverage reports VIABLE" "$out" "VERDICT: VIABLE - UID_2445 populated for 100%"

# --- UID absent but _SYNC_ID present ---------------------------------------
out="$(run_tally calendars_google.txt events_sync_id_only.txt)"
expect_contains "sync_id-only coverage reports VIABLE WITH FALLBACK" "$out" "VERDICT: VIABLE WITH FALLBACK"

# --- no events at all -------------------------------------------------------
out="$(run_tally calendars_google.txt events_empty.txt)"
expect_contains "empty provider reports INCONCLUSIVE" "$out" "VERDICT: INCONCLUSIVE"

echo
if [ "$failures" -eq 0 ]; then
    echo "All tests passed."
else
    echo "${failures} test(s) failed."
fi
exit "$failures"
