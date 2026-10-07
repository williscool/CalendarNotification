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

import android.content.Context
import android.database.SQLException
import com.github.quarck.calnotify.logs.DevLog

/**
 * Read/write access to portable event identity.
 *
 * Capture is **best-effort**: a failure to record identity must never fail the
 * event write it accompanies. Identity is a recovery aid, so losing a row
 * costs a future restore some accuracy, while propagating the exception would
 * cost the user an actual notification. Write paths therefore swallow
 * [SQLException] and log; read paths return empty rather than throw.
 *
 * Only the resolver reads this during a restore — nothing on the app's hot
 * path touches it.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class EventIdentityStorage(
    private val dao: EventIdentityDao
) {
    constructor(context: Context) : this(
        EventIdentityDatabase.getInstance(context).eventIdentityDao()
    )

    /** Record identity for one event. Overwrites any existing row for the key. */
    fun put(identity: EventIdentityEntity): Boolean =
        runCatchingWrite("put(${identity.eventId}/${identity.instanceStartTime})") {
            dao.put(identity)
        }

    fun putAll(identities: List<EventIdentityEntity>): Boolean {
        if (identities.isEmpty())
            return true

        return runCatchingWrite("putAll(${identities.size})") { dao.putAll(identities) }
    }

    fun get(eventId: Long, instanceStartTime: Long): EventIdentityEntity? =
        runCatchingRead("get($eventId/$instanceStartTime)", null) {
            dao.getByKey(eventId, instanceStartTime)
        }

    fun getAll(): List<EventIdentityEntity> =
        runCatchingRead("getAll", emptyList()) { dao.getAll() }

    /**
     * Key plus sync id for every row, without reading the other columns.
     *
     * What the capture pass needs: which events already have identity, and what
     * sync id to compare against the provider. Avoids reading eleven unused
     * columns per row on a table sized to the stored-event count.
     */
    fun getAllSyncIds(): List<EventIdentitySyncId> =
        runCatchingRead("getAllSyncIds", emptyList()) { dao.getAllSyncIds() }

    /**
     * Keys of rows holding both halves of an identity: an event identifier and
     * a calendar to attach it to.
     *
     * Rows missing the calendar half are excluded so they keep being retried --
     * see [EventIdentityDao.getFullyCapturedKeys].
     */
    fun getFullyCapturedKeys(): List<EventIdentityKey> =
        runCatchingRead("getFullyCapturedKeys", emptyList()) { dao.getFullyCapturedKeys() }

    /** Rows still awaiting resolution, below the retry cap. */
    fun getUnresolved(maxAttempts: Int = DEFAULT_MAX_RESOLUTION_ATTEMPTS): List<EventIdentityEntity> =
        runCatchingRead("getUnresolved", emptyList()) { dao.getUnresolved(maxAttempts) }

    fun count(): Int = runCatchingRead("count", 0) { dao.count() }

    fun delete(eventId: Long, instanceStartTime: Long): Boolean =
        runCatchingWrite("delete($eventId/$instanceStartTime)") {
            dao.deleteByKey(eventId, instanceStartTime)
        }

    /**
     * Move an identity row onto a resolved event id.
     *
     * The caller is mid re-key: the `eventsV9` row has been deleted and
     * re-inserted under [newEventId], and this row has to follow or the next
     * retry pass will not find it.
     *
     * @return true if a row was moved.
     */
    fun reKey(oldEventId: Long, instanceStartTime: Long, newEventId: Long): Boolean {
        if (oldEventId == newEventId)
            return true    // already current; nothing to do

        return runCatchingRead("reKey($oldEventId->$newEventId)", false) {
            dao.reKey(oldEventId, instanceStartTime, newEventId) > 0
        }
    }

    /** Note that resolution was attempted, feeding the retry cap and backoff. */
    fun recordResolutionAttempt(
        eventId: Long,
        instanceStartTime: Long,
        attemptTime: Long
    ): Boolean =
        runCatchingWrite("recordResolutionAttempt($eventId/$instanceStartTime)") {
            dao.recordResolutionAttempt(eventId, instanceStartTime, attemptTime)
        }

    /**
     * Catch only SQLException, never the broad Exception -- a programming error
     * in a query should surface loudly rather than be silently logged.
     */
    private inline fun runCatchingWrite(what: String, body: () -> Unit): Boolean =
        try {
            body()
            true
        } catch (ex: SQLException) {
            DevLog.error(LOG_TAG, "$what failed: ${ex.message}")
            false
        }

    private inline fun <T> runCatchingRead(what: String, fallback: T, body: () -> T): T =
        try {
            body()
        } catch (ex: SQLException) {
            DevLog.error(LOG_TAG, "$what failed: ${ex.message}")
            fallback
        }

    companion object {
        private const val LOG_TAG = "EventIdentityStorage"

        /**
         * Stop retrying a row after this many failed resolution attempts.
         *
         * Events outside the provider's ~12 month sync window can never
         * resolve, so an uncapped retry would re-query the provider on every
         * launch forever.
         */
        const val DEFAULT_MAX_RESOLUTION_ATTEMPTS = 10
    }
}
