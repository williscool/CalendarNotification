//
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//

package com.github.quarck.calnotify.calendar

import android.Manifest
import android.content.Context
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.logs.DevLog
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic probe for the portable event identity work.
 *
 * Answers one question before any of that plan gets built: **is
 * [CalendarContract.Events.UID_2445] actually populated on real calendars?**
 *
 * The exact-match path (Phases 0-5 of
 * `docs/dev_todo/portable_event_identity.md`) depends entirely on that column
 * having values, but there is a long-standing unresolved Android issue titled
 * "CalendarContract.Events.UID_2445 column is always null"
 * (https://issuetracker.google.com/issues/37053160). If the column turns out to
 * be sparse, the exact path is not viable and Phase 6's content heuristic
 * becomes the primary mechanism instead - a materially different plan.
 *
 * This is a **diagnostic, not an assertion**. It reads whatever calendars are on
 * the device and reports; it never fails on low coverage, because "this device
 * has no synced calendars" is a property of the device, not a bug. Read the
 * verdict in logcat:
 *
 * ```
 * adb logcat -s CNPlus:* | grep UID2445_PROBE
 * ```
 *
 * Run against a device with real (ideally Google-synced) calendars - an empty
 * emulator tells you nothing:
 *
 * ```
 * .\gradlew.bat :app:connectedX8664DebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.github.quarck.calnotify.calendar.Uid2445PopulationProbeTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class Uid2445PopulationProbeTest {

    private lateinit var context: Context

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    /** Per-account-type tally of how many events carry a usable identifier. */
    private data class Tally(
        var total: Int = 0,
        var uidNonNull: Int = 0,
        var syncIdNonNull: Int = 0,
        /** Events with neither identifier - unrecoverable by the exact path. */
        var neither: Int = 0
    )

    @Test
    fun probeUid2445Population() {
        val calendars = readCalendars()

        if (calendars.isEmpty()) {
            report(emptyMap(), 0)
            DevLog.warn(LOG_TAG, "$TAG no calendars on this device - probe is inconclusive")
            return
        }

        val byAccountType = mutableMapOf<String, Tally>()
        var scanned = 0

        for ((calendarId, accountType) in calendars) {
            val tally = byAccountType.getOrPut(accountType) { Tally() }
            scanned += tallyEventsForCalendar(calendarId, tally)
        }

        report(byAccountType, scanned)
    }

    /** @return calendar id -> account type for every calendar on the device. */
    private fun readCalendars(): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0) to (cursor.getString(1) ?: "(null)"))
            }
        }
        return result
    }

    /** Adds this calendar's events into [tally]. @return number of events scanned. */
    private fun tallyEventsForCalendar(calendarId: Long, tally: Tally): Int {
        val projection = arrayOf(
            CalendarContract.Events.UID_2445,
            CalendarContract.Events._SYNC_ID
        )
        // Skip tombstones - deleted rows would understate real coverage.
        val selection =
            "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "(${CalendarContract.Events.DELETED} IS NULL OR ${CalendarContract.Events.DELETED} = 0)"

        var scanned = 0
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(calendarId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val hasUid = !cursor.getString(0).isNullOrBlank()
                val hasSyncId = !cursor.getString(1).isNullOrBlank()

                tally.total++
                if (hasUid) tally.uidNonNull++
                if (hasSyncId) tally.syncIdNonNull++
                if (!hasUid && !hasSyncId) tally.neither++
                scanned++
            }
        }
        return scanned
    }

    private fun report(byAccountType: Map<String, Tally>, scanned: Int) {
        val overall = Tally()
        byAccountType.values.forEach {
            overall.total += it.total
            overall.uidNonNull += it.uidNonNull
            overall.syncIdNonNull += it.syncIdNonNull
            overall.neither += it.neither
        }

        DevLog.info(LOG_TAG, "$TAG ================ UID_2445 POPULATION ================")
        DevLog.info(LOG_TAG, "$TAG calendars=${byAccountType.size} accountTypes, events scanned=$scanned")
        DevLog.info(LOG_TAG, "$TAG ${"accountType".padEnd(28)} ${"total".padStart(6)} ${"uid".padStart(6)} ${"pct".padStart(5)} ${"syncId".padStart(7)} ${"neither".padStart(8)}")

        for ((accountType, t) in byAccountType.entries.sortedByDescending { it.value.total }) {
            DevLog.info(
                LOG_TAG,
                "$TAG ${accountType.take(28).padEnd(28)} ${t.total.toString().padStart(6)} " +
                    "${t.uidNonNull.toString().padStart(6)} ${pct(t.uidNonNull, t.total).padStart(5)} " +
                    "${t.syncIdNonNull.toString().padStart(7)} ${t.neither.toString().padStart(8)}"
            )
        }

        DevLog.info(LOG_TAG, "$TAG ${"-".repeat(66)}")
        DevLog.info(
            LOG_TAG,
            "$TAG ${"OVERALL".padEnd(28)} ${overall.total.toString().padStart(6)} " +
                "${overall.uidNonNull.toString().padStart(6)} ${pct(overall.uidNonNull, overall.total).padStart(5)} " +
                "${overall.syncIdNonNull.toString().padStart(7)} ${overall.neither.toString().padStart(8)}"
        )
        DevLog.info(LOG_TAG, "$TAG VERDICT: ${verdict(overall)}")
        DevLog.info(LOG_TAG, "$TAG ====================================================")
    }

    private fun pct(part: Int, whole: Int): String =
        if (whole == 0) "n/a" else "${part * 100 / whole}%"

    /**
     * Maps coverage onto the decision the plan actually needs to make.
     * Thresholds are judgement calls, deliberately stated so the reasoning is
     * visible rather than buried in a number.
     */
    private fun verdict(overall: Tally): String {
        if (overall.total == 0)
            return "INCONCLUSIVE - no events found; re-run on a device with real calendars"

        val uidPct = overall.uidNonNull * 100 / overall.total
        val eitherPct = (overall.total - overall.neither) * 100 / overall.total

        return when {
            uidPct >= 90 ->
                "VIABLE - UID_2445 populated for $uidPct% of events; exact path (Phases 0-5) stands"
            eitherPct >= 90 ->
                "VIABLE WITH FALLBACK - UID_2445 only $uidPct%, but $eitherPct% have UID or _SYNC_ID; " +
                    "exact path stands and the _SYNC_ID fallback carries real weight"
            uidPct >= 50 ->
                "MIXED - UID_2445 only $uidPct% and $eitherPct% have either; exact path works for " +
                    "some events, Phase 6 heuristic needed to cover the rest"
            else ->
                "NOT VIABLE - UID_2445 only $uidPct%; the exact path cannot carry this plan. " +
                    "Phase 6 heuristic becomes primary - revisit the plan before building Phases 0-5"
        }
    }

    companion object {
        private const val LOG_TAG = "Uid2445Probe"
        /** Greppable marker so the report is easy to pull out of logcat. */
        private const val TAG = "UID2445_PROBE"
    }
}
