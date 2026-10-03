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

package com.github.quarck.calnotify.calendar

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/**
 * Runs the real `getCalendarBackupInfo` cursor code against calendar rows with
 * null columns. Other tests stub this method out, which is how issue #298 shipped:
 * on GrapheneOS a calendar with a null column crashed identity capture on every
 * rescan, and so crashed the app on open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarBackupInfoNullColumnsRobolectricTest {

    private class FakeCalendarsProvider(private val row: Array<Any?>) : ContentProvider() {
        override fun onCreate() = true
        override fun query(
            uri: Uri, projection: Array<String>?, selection: String?,
            selectionArgs: Array<String>?, sortOrder: String?
        ): Cursor = MatrixCursor(projection).apply { addRow(row) }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
        override fun update(
            uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
        ) = 0
    }

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        shadowOf(context).grantPermissions(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }

    /** Row order follows getCalendarBackupInfo's projection. */
    private fun serveCalendarRow(
        accountName: String?, accountType: String?, ownerAccount: String?,
        displayName: String?, name: String?
    ) = ShadowContentResolver.registerProviderInternal(
        CalendarContract.AUTHORITY,
        FakeCalendarsProvider(arrayOf(CALENDAR_ID, accountName, accountType, ownerAccount, displayName, name))
    )

    @Test
    fun nullColumnsBecomeEmptyStringsInsteadOfCrashing() {
        serveCalendarRow(
            accountName = "local",
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            ownerAccount = null,
            displayName = "Personal",
            name = null
        )

        val info = CalendarProvider.getCalendarBackupInfo(context, CALENDAR_ID)

        assertEquals(
            CalendarBackupInfo(
                calendarId = CALENDAR_ID,
                accountName = "local",
                accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
                ownerAccount = "",
                displayName = "Personal",
                name = ""
            ),
            info
        )
    }

    @Test
    fun allNullColumnsStillReturnACalendar() {
        serveCalendarRow(null, null, null, null, null)

        val info = CalendarProvider.getCalendarBackupInfo(context, CALENDAR_ID)

        assertEquals(CalendarBackupInfo(CALENDAR_ID, "", "", "", "", ""), info)
    }

    companion object {
        private const val CALENDAR_ID = 7L
    }
}
