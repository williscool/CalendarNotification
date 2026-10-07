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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule the whole restore feature turns on.
 *
 * [checkEventIdentity] decides, for one event, whether its stored provider id
 * still points at the event it was stored for. Plain JUnit -- the function
 * takes two strings and touches no Android API, which is the point of keeping
 * it separate from the walk that calls it.
 *
 * See docs/dev_todo/portable_event_identity.md.
 */
class EventIdentityCheckTest {

    @Test
    fun matchingSyncIdIsCurrent() {
        assertEquals(
            EventIdentityVerdict.CURRENT,
            checkEventIdentity(storedSyncId = "abc123", providerSyncId = "abc123")
        )
    }

    @Test
    fun differentSyncIdIsStale() {
        // The restored-device case: the new phone assigned this row's id to
        // some other event.
        assertEquals(
            EventIdentityVerdict.STALE,
            checkEventIdentity(storedSyncId = "abc123", providerSyncId = "zzz999")
        )
    }

    @Test
    fun providerReturningNothingIsStale() {
        // Distinct branch from the mismatch above: the id resolves to no event
        // at all, which is what a restore onto a phone that has not synced this
        // calendar yet looks like.
        assertEquals(
            EventIdentityVerdict.STALE,
            checkEventIdentity(storedSyncId = "abc123", providerSyncId = null)
        )
    }

    @Test
    fun noStoredSyncIdIsUnknownNotStale() {
        // The distinction that killed two earlier designs: "nothing captured"
        // is not evidence of a restore. Calling it STALE would hand every
        // never-synced event to the resolver, which has nothing to resolve it
        // against.
        assertEquals(
            EventIdentityVerdict.UNKNOWN,
            checkEventIdentity(storedSyncId = null, providerSyncId = "abc123")
        )
    }

    @Test
    fun noStoredSyncIdIsUnknownEvenWhenProviderHasNothingEither() {
        assertEquals(
            EventIdentityVerdict.UNKNOWN,
            checkEventIdentity(storedSyncId = null, providerSyncId = null)
        )
    }

    @Test
    fun blankStoredSyncIdIsTreatedAsAbsent() {
        // Provider columns come back as "" as readily as null, and an empty
        // string must not be compared as if it were an identifier -- two
        // unrelated events with blank sync ids would otherwise read as CURRENT.
        assertEquals(
            EventIdentityVerdict.UNKNOWN,
            checkEventIdentity(storedSyncId = "", providerSyncId = "")
        )
        assertEquals(
            EventIdentityVerdict.UNKNOWN,
            checkEventIdentity(storedSyncId = "   ", providerSyncId = "abc123")
        )
    }

    @Test
    fun blankProviderSyncIdIsStaleWhenSomethingWasStored() {
        assertEquals(
            EventIdentityVerdict.STALE,
            checkEventIdentity(storedSyncId = "abc123", providerSyncId = "")
        )
    }

    @Test
    fun comparisonIsExactNotFuzzy() {
        // Sync ids are opaque server strings; near-misses are different events,
        // and case differences are not equivalences.
        assertEquals(
            EventIdentityVerdict.STALE,
            checkEventIdentity(storedSyncId = "abc123", providerSyncId = "abc1234")
        )
        assertEquals(
            EventIdentityVerdict.STALE,
            checkEventIdentity(storedSyncId = "abc123", providerSyncId = "ABC123")
        )
    }
}
