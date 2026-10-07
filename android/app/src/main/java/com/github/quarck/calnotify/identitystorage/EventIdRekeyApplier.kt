//
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.identitystorage

import com.github.quarck.calnotify.calendar.EventAlertRecord

/**
 * Applies an [EventIdRekeyPlan] one event at a time, coordinating writes
 * across four separate SQLite files with manual rollback on partial failure.
 *
 * ### The four databases
 *
 * `eventsV9`'s primary key is `(id, instanceStartTime)`. Changing `id` is a
 * delete + re-insert. The same `id` lives as a foreign-key-in-spirit in three
 * other databases, all of which must move in lockstep:
 *
 *   1. `eventsV9` — the event row itself
 *   2. `eventIdentityV1` — the captured identity (uses a native `reKey`)
 *   3. `manualAlertsV1` — pending alerts scheduled for this event
 *   4. `dismissedEventsV2` — history of prior dismissals of this event
 *
 * Room databases are separate files, so a shared transaction is not possible.
 * The pattern follows `ApplicationController.unsnoozeToUpcoming`: numbered
 * writes, best-effort rollback of earlier steps if a later one fails.
 *
 * ### Ordering
 *
 * We update the sources of truth first and the derived data after. Concretely:
 *
 *   1. Delete + re-insert `eventsV9`.
 *      This is the write everything downstream keys off. If it fails, nothing
 *      else has moved and we bail cleanly.
 *
 *   2. `reKey` the identity row.
 *      A hand-written `UPDATE eventIdentityV1 SET eventId = ? WHERE eventId = ?`
 *      -- see [EventIdentityDao.reKey]. If it fails, we roll `eventsV9` back
 *      by deleting the new row and re-inserting the old one. `originalEventId`
 *      is deliberately not touched: it is the staleness anchor and must keep
 *      pointing at the old-device id.
 *
 *   3. Re-key `dismissedEventsV2` rows.
 *      Historical, so a failure here does not corrupt live behaviour -- but
 *      we still roll back so a re-run sees a consistent state.
 *
 *   4. Re-key `manualAlertsV1` rows.
 *      Deleting an alert without re-adding one would cost a notification.
 *      Doing this last (after everything above succeeded) means the alert
 *      remains keyed to the *old* id for the duration of the previous steps,
 *      which is fine -- the alert fires from the alarm scheduler, not from
 *      the event id.
 *
 * ### What if a rollback itself fails?
 *
 * Logged and reported. The caller learns via the [Result], stops the pass,
 * and lets the next rescan try again. Every write path here is idempotent:
 * a partial state on disk becomes visible to the *next* re-key attempt, which
 * either finishes it or notices the plan no longer applies.
 *
 * See docs/dev_todo/portable_event_identity.md, "Phase 3b" section.
 */
object EventIdRekeyApplier {

    /**
     * The outcome of applying **one** [EventIdRekeyPlan.EventIdChange].
     *
     * Reported per-event rather than per-batch so the caller can log the
     * shape of any partial failures and so tests can assert on a specific
     * failure path without also asserting on everything that succeeded first.
     */
    sealed class Result {
        object Success : Result()

        /** No change made; the event row was gone before we could act. */
        object EventRowMissing : Result()

        /**
         * `eventsV9` write failed; **nothing was touched**.
         * No rollback needed.
         */
        data class EventsWriteFailed(val cause: Throwable?) : Result()

        /**
         * A database *after* `eventsV9` failed. `eventsV9` was rolled back to
         * its old (id, istart) key. Other databases may or may not have
         * partial state — see [rolledBackCleanly].
         */
        data class LaterWriteFailed(
            val failedAt: Step,
            val rolledBackCleanly: Boolean,
            val cause: Throwable?
        ) : Result()

        enum class Step { IDENTITY, DISMISSED, MONITOR }
    }

    /**
     * The four storage operations, wired as function parameters so the applier
     * can be unit-tested without a database or a Context. Real callers supply
     * lambdas that open the appropriate storage; tests supply fakes that
     * record calls and can be told to fail.
     *
     * Deliberately narrow: each op does one thing per event, and each takes
     * the change record it is applying so it can log meaningfully.
     */
    interface Ops {
        /**
         * Read the current `eventsV9` row for the change's live key, or null
         * if it is gone.
         */
        fun readEvent(currentId: Long, instanceStartTime: Long): EventAlertRecord?

        /** Delete the row at `(currentId, instanceStartTime)`. */
        fun deleteEvent(currentId: Long, instanceStartTime: Long): Boolean

        /** Insert a row (used for both the new key and rollback of the old). */
        fun insertEvent(event: EventAlertRecord): Boolean

        /** Native identity re-key via `EventIdentityStorage.reKey`. */
        fun reKeyIdentity(oldId: Long, instanceStartTime: Long, newId: Long): Boolean

        /**
         * Any dismissed rows for this event id, deleted and re-inserted under
         * the new id. Returns true if all succeeded (or there were none);
         * false on any failure so the caller can roll back.
         */
        fun reKeyDismissed(oldId: Long, newId: Long): Boolean

        /** Same shape as [reKeyDismissed], for `manualAlertsV1`. */
        fun reKeyMonitorAlerts(
            oldId: Long, newId: Long, instanceStartTime: Long
        ): Boolean
    }

    /**
     * Apply one [EventIdRekeyPlan.EventIdChange].
     *
     * All the state lives in [ops] — no fields on this object — so applying
     * one change never leaks into applying the next.
     */
    fun applyOne(change: EventIdRekeyPlan.EventIdChange, ops: Ops): Result {
        val oldId = change.currentEventId
        val newId = change.newEventId
        val istart = change.identity.instanceStartTime

        // 1. Read the live row. We need it verbatim to re-insert under the new
        //    key, and its absence tells us the plan is stale.
        val oldRow = ops.readEvent(oldId, istart)
            ?: return Result.EventRowMissing

        // 2. eventsV9: delete + re-insert.
        //    Room's @Update can't change the PK, so this is the mechanism.
        val newRow = oldRow.copy(eventId = newId)

        val deleteOk = try { ops.deleteEvent(oldId, istart) }
            catch (ex: RuntimeException) { return Result.EventsWriteFailed(ex) }
        if (!deleteOk) return Result.EventsWriteFailed(null)

        val insertOk = try { ops.insertEvent(newRow) }
            catch (ex: RuntimeException) {
                // Delete succeeded but insert threw. Try to put the old row
                // back. If that also fails we are in a bad spot, but the row
                // itself is small and the next rescan will re-derive it from
                // the provider.
                tryReinsert(ops, oldRow)
                return Result.EventsWriteFailed(ex)
            }
        if (!insertOk) {
            tryReinsert(ops, oldRow)
            return Result.EventsWriteFailed(null)
        }

        // 3. Identity re-key.
        //    From here on, failures roll `eventsV9` back to its old key.
        val identityOk = try { ops.reKeyIdentity(oldId, istart, newId) }
            catch (ex: RuntimeException) {
                val restored = rollbackEvent(ops, oldRow, newId, istart)
                return Result.LaterWriteFailed(Result.Step.IDENTITY, restored, ex)
            }
        if (!identityOk) {
            val restored = rollbackEvent(ops, oldRow, newId, istart)
            return Result.LaterWriteFailed(Result.Step.IDENTITY, restored, null)
        }

        // 4. Dismissed events.
        val dismissedOk = try { ops.reKeyDismissed(oldId, newId) }
            catch (ex: RuntimeException) {
                val restored = rollbackAfterIdentity(ops, oldRow, newId, istart)
                return Result.LaterWriteFailed(Result.Step.DISMISSED, restored, ex)
            }
        if (!dismissedOk) {
            val restored = rollbackAfterIdentity(ops, oldRow, newId, istart)
            return Result.LaterWriteFailed(Result.Step.DISMISSED, restored, null)
        }

        // 5. Monitor alerts.
        val monitorOk = try { ops.reKeyMonitorAlerts(oldId, newId, istart) }
            catch (ex: RuntimeException) {
                val restored = rollbackAfterDismissed(ops, oldRow, newId, istart)
                return Result.LaterWriteFailed(Result.Step.MONITOR, restored, ex)
            }
        if (!monitorOk) {
            val restored = rollbackAfterDismissed(ops, oldRow, newId, istart)
            return Result.LaterWriteFailed(Result.Step.MONITOR, restored, null)
        }

        return Result.Success
    }

    // --- rollback helpers ---------------------------------------------------

    private fun tryReinsert(ops: Ops, oldRow: EventAlertRecord): Boolean =
        try { ops.insertEvent(oldRow) } catch (_: RuntimeException) { false }

    /** Rollback path when the identity step is the one that failed. */
    private fun rollbackEvent(
        ops: Ops, oldRow: EventAlertRecord, newId: Long, istart: Long
    ): Boolean {
        return try {
            val delOk = ops.deleteEvent(newId, istart)
            val insOk = if (delOk) ops.insertEvent(oldRow) else false
            delOk && insOk
        } catch (_: RuntimeException) { false }
    }

    private fun rollbackAfterIdentity(
        ops: Ops, oldRow: EventAlertRecord, newId: Long, istart: Long
    ): Boolean {
        // Reverse the identity move, then the events move.
        val idOk = try { ops.reKeyIdentity(newId, istart, oldRow.eventId) }
            catch (_: RuntimeException) { false }
        val evOk = rollbackEvent(ops, oldRow, newId, istart)
        return idOk && evOk
    }

    private fun rollbackAfterDismissed(
        ops: Ops, oldRow: EventAlertRecord, newId: Long, istart: Long
    ): Boolean {
        // Reverse dismissed, then identity, then events.
        val disOk = try { ops.reKeyDismissed(newId, oldRow.eventId) }
            catch (_: RuntimeException) { false }
        val idOk = try { ops.reKeyIdentity(newId, istart, oldRow.eventId) }
            catch (_: RuntimeException) { false }
        val evOk = rollbackEvent(ops, oldRow, newId, istart)
        return disOk && idOk && evOk
    }
}
