package com.github.quarck.calnotify.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.app.ApplicationController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.Manifest
import android.net.Uri

@RunWith(AndroidJUnit4::class)
class CalendarBackupRestoreTest {
    private lateinit var context: Context
    private var testCalendarId1: Long = -1
    private var testCalendarId2: Long = -1

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create calendar 1 as the source calendar
        testCalendarId1 = createTestCalendar(
            displayName = "Test Calendar Source",
            accountName = "source@local",
            ownerAccount = "source@local"
        )
        
        // Create calendar 2 as the target calendar - better match for restore operations
        testCalendarId2 = createTestCalendar(
            displayName = "Test Calendar Target",
            accountName = "target@local",
            ownerAccount = "target@local"
        )
    }

    @After
    fun cleanup() {
        // Delete test events
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} IN (?, ?)",
            arrayOf(testCalendarId1.toString(), testCalendarId2.toString())
        )
        
        // Delete test calendars
        if (testCalendarId1 != -1L) {
            context.contentResolver.delete(
                CalendarContract.Calendars.CONTENT_URI,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(testCalendarId1.toString())
            )
        }
        
        if (testCalendarId2 != -1L) {
            context.contentResolver.delete(
                CalendarContract.Calendars.CONTENT_URI,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(testCalendarId2.toString())
            )
        }
    }

    @Test
    fun testGetCalendarBackupInfo() {
        val backupInfo = CalendarProvider.getCalendarBackupInfo(context, testCalendarId1)
        
        assertNotNull("Backup info should not be null", backupInfo)
        assertEquals("Calendar ID should match", testCalendarId1, backupInfo?.calendarId)
        assertEquals("Owner should match", "source@local", backupInfo?.ownerAccount)
        assertEquals("Account name should match", "source@local", backupInfo?.accountName)
        assertEquals("Account type should match", CalendarContract.ACCOUNT_TYPE_LOCAL, backupInfo?.accountType)
        assertEquals("Display name should match", "Test Calendar Source", backupInfo?.displayName)
    }

    @Test
    fun testFindMatchingCalendarId_ExactMatch() {
        val backupInfo = CalendarBackupInfo(
            calendarId = 999L, // Different ID
            ownerAccount = "source@local",
            accountName = "source@local",
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            displayName = "Test Calendar Source",
            name = "Test Calendar Source"
        )
        
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        assertEquals("Should find exact matching calendar", testCalendarId1, matchedId)
    }

    @Test
    fun testFindMatchingCalendarId_FallbackMatch() {
        val backupInfo = CalendarBackupInfo(
            calendarId = 999L,
            ownerAccount = "different@local",
            accountName = "source@local", // Match account name but different owner
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            displayName = "Different Name",
            name = "Different Name"
        )
        
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        assertEquals("Should find fallback matching calendar", testCalendarId1, matchedId)
    }

    @Test
    fun testFindMatchingCalendarId_NoMatch() {
        val backupInfo = CalendarBackupInfo(
            calendarId = 999L,
            ownerAccount = "Non Existent",
            accountName = "nonexistent@example.com",
            accountType = "com.nonexistent",
            displayName = "Non Existent",
            name = "Non Existent"
        )
        
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        assertEquals("Should not find any matching calendar", -1L, matchedId)
    }

    @Test
    fun testRestoreEvent_WithMatchingCalendar() {
        // First create a real calendar event
        val currentTime = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, testCalendarId1)
            put(CalendarContract.Events.TITLE, "Test Event")
            put(CalendarContract.Events.DESCRIPTION, "Test Description")
            put(CalendarContract.Events.DTSTART, currentTime + 3600000) // 1 hour from now
            put(CalendarContract.Events.DTEND, currentTime + 7200000)   // 2 hours from now
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.EVENT_LOCATION, "Test Location")
            put(CalendarContract.Events.HAS_ALARM, 1)
            // Add owner and account info to match calendar 2
            put(CalendarContract.Events.ORGANIZER, "target@local")
            put(CalendarContract.Events.CALENDAR_DISPLAY_NAME, "Test Calendar Target")
        }
        
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        assertNotNull("Failed to create test event", eventUri)
        val eventId = eventUri!!.lastPathSegment!!.toLong()
        
        // Create reminder for the event
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 15) // 15 minutes before
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        
        // Create backup info that matches calendar 2's properties
        val backupInfo = CalendarBackupInfo(
            calendarId = testCalendarId1,
            ownerAccount = "target@local",
            accountName = "target@local",
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            displayName = "Test Calendar Target",
            name = "Test Calendar Target"
        )
        
        // Now create our EventAlertRecord using the real event ID
        val originalEvent = EventAlertRecord(
            calendarId = testCalendarId1,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = 0,
            title = "Test Event",
            desc = "Test Description",
            startTime = currentTime + 3600000,
            endTime = currentTime + 7200000,
            instanceStartTime = currentTime + 3600000,
            instanceEndTime = currentTime + 7200000,
            location = "Test Location",
            lastStatusChangeTime = currentTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0L
        )
        
        // Verify that calendar 2 is found as a match for our backup info
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        assertEquals("Calendar matching should work", testCalendarId2, matchedId)
        
        // Restore the event
        ApplicationController.restoreEvent(context, originalEvent)
        
        // Verify the event was restored with the correct calendar ID
        val restoredEvent = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Restored event should exist", restoredEvent)
        assertEquals("Calendar ID should be updated", testCalendarId2, restoredEvent?.calendarId)
    }

    private fun createTestCalendar(
        displayName: String,
        accountName: String,
        ownerAccount: String
    ): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.NAME, displayName)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ownerAccount)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "UTC")
        }

        // Add account type to URI to allow local calendar creation
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
            
        val calUri = context.contentResolver.insert(uri, values)
        return calUri?.lastPathSegment?.toLong() ?: -1L
    }

    private fun createTestEvent(calendarId: Long): EventAlertRecord {
        val currentTime = System.currentTimeMillis()
        return EventAlertRecord(
            calendarId = calendarId,
            eventId = currentTime,
            isAllDay = false,
            isRepeating = false,
            alertTime = currentTime,
            notificationId = 0,
            title = "Test Event",
            desc = "Test Description",
            startTime = currentTime + 3600000, // 1 hour from now
            endTime = currentTime + 7200000,   // 2 hours from now
            instanceStartTime = currentTime + 3600000,
            instanceEndTime = currentTime + 7200000,
            location = "Test Location",
            lastStatusChangeTime = currentTime,
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0L
        )
    }
} 