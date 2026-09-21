# Tally UID_2445 / _SYNC_ID coverage from Calendar Provider rows.
#
# Used by scripts/probe_uid2445.sh. Kept as a separate file so it can be read
# and tested on its own:
#
#   awk -f scripts/lib/uid2445_tally.awk calendars.txt events.txt
#
# Input is two `adb shell content query` outputs, as two separate files,
# calendars FIRST and events SECOND. Order matters: the account_type map has to
# be built before events can be attributed to it.
#
# A row looks like:
#   Row: 0 _id=1, account_type=com.google, account_name=will@example.com
#   Row: 0 calendar_id=1, uid2445=abc@google.com, _sync_id=s1, deleted=0

# ---------------------------------------------------------------- parsing ---

# Return the value of `key` in a "Row: N k=v, k=v" line, or "" if absent.
#
# Keys are compared EXACTLY after splitting on ", ". Two bugs this avoids:
#   - a substring match lets "_id" match inside "calendar_id"
#   - regex trimming breaks on values containing "@" or "." -- i.e. every
#     UID and every email address
function field(line, key,    parts, count, i, eq) {
    sub(/^Row: [0-9]+ /, "", line)
    count = split(line, parts, ", ")

    for (i = 1; i <= count; i++) {
        eq = index(parts[i], "=")
        if (eq > 0 && substr(parts[i], 1, eq - 1) == key)
            return substr(parts[i], eq + 1)
    }
    return ""
}

# The provider prints absent values as the literal string "NULL".
function has_value(v) {
    return (v != "" && v != "NULL")
}

# ---------------------------------------------------------------- reading ---

# Which file we are in: 1 = calendars, 2 = events. Using FNR/NR rather than
# matching on field names, because "_sync_id=" would otherwise be caught by any
# pattern looking for "_id=".
FNR == 1 { file_index++ }

# Calendar row: remember which account type this calendar belongs to.
file_index == 1 {
    id = field($0, "_id")
    if (id != "") {
        type = field($0, "account_type")
        account_type_of[id] = (type == "" ? "(null)" : type)
    }
    next
}

# Event row: count it against its calendar's account type.
{
    if (field($0, "deleted") == "1")
        next                      # tombstone: would understate real coverage

    calendar_id = field($0, "calendar_id")
    account = (calendar_id in account_type_of) \
        ? account_type_of[calendar_id] : "(unknown calendar)"

    got_uid = has_value(field($0, "uid2445"))
    got_sync_id = has_value(field($0, "_sync_id"))

    events[account]++
    total_events++

    if (got_uid) {
        with_uid[account]++
        total_with_uid++
    }
    if (got_sync_id) {
        with_sync_id[account]++
        total_with_sync_id++
    }
    if (!got_uid && !got_sync_id) {
        with_neither[account]++
        total_with_neither++
    }
}

# --------------------------------------------------------------- reporting ---

function percent(part, whole) {
    return (whole == 0) ? 0 : (part * 100 / whole)
}

function print_row(label, total, uid, sync_id, neither) {
    printf "%-26s %7d %7d %5d%% %8d %9d\n",
        label, total, uid, percent(uid, total), sync_id, neither
}

# Map coverage onto the decision the plan actually has to make. Thresholds are
# judgement calls, spelled out here rather than left implicit.
function verdict(uid_pct, either_pct) {
    if (uid_pct >= 90)
        return sprintf("VIABLE - UID_2445 populated for %d%% of events; " \
            "exact path (Phases 0-5) stands", uid_pct)

    if (either_pct >= 90)
        return sprintf("VIABLE WITH FALLBACK - UID_2445 only %d%%, but %d%% " \
            "have UID or _SYNC_ID; the _SYNC_ID fallback carries real weight",
            uid_pct, either_pct)

    if (uid_pct >= 50)
        return sprintf("MIXED - UID_2445 %d%%, either %d%%; exact path works " \
            "for some events, Phase 6 heuristic needed for the rest",
            uid_pct, either_pct)

    return sprintf("NOT VIABLE - UID_2445 only %d%%; the exact path cannot " \
        "carry this plan. Phase 6 heuristic becomes primary - revisit the " \
        "plan before building Phases 0-5", uid_pct)
}

END {
    if (total_events == 0) {
        print "VERDICT: INCONCLUSIVE - no events found."
        print "Re-run on a device with real, synced calendars."
        exit
    }

    printf "%-26s %7s %7s %6s %8s %9s\n",
        "accountType", "total", "uid", "pct", "syncId", "neither"

    for (account in events)
        print_row(account, events[account], with_uid[account] + 0,
            with_sync_id[account] + 0, with_neither[account] + 0)

    print "----------------------------------------------------------------------"
    print_row("OVERALL", total_events, total_with_uid + 0,
        total_with_sync_id + 0, total_with_neither + 0)

    print ""
    print "VERDICT: " verdict(percent(total_with_uid, total_events),
        percent(total_events - total_with_neither, total_events))
}
