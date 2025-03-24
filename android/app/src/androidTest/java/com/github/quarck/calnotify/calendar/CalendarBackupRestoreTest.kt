package com.github.quarck.calnotify.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.quarck.calnotify.app.ApplicationController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarBackupRestoreTest {
    private lateinit var context: Context
    private var testCalendarId1: Long = -1
    private var testCalendarId2: Long = -1

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create test calendars
        testCalendarId1 = createTestCalendar(
            "test1@example.com",
            "Test Calendar 1",
            "com.example",
            "Test Owner"
        )
        
        testCalendarId2 = createTestCalendar(
            "test2@example.com",
            "Test Calendar 2",
            "com.example",
            "Test Owner 2"
        )
    }

    @After
    fun cleanup() {
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
        assertEquals("Owner should match", "Test Owner", backupInfo?.ownerAccount)
        assertEquals("Account name should match", "test1@example.com", backupInfo?.accountName)
        assertEquals("Account type should match", "com.example", backupInfo?.accountType)
        assertEquals("Display name should match", "Test Calendar 1", backupInfo?.displayName)
    }

    @Test
    fun testFindMatchingCalendarId_ExactMatch() {
        val backupInfo = CalendarBackupInfo(
            calendarId = 999L, // Different ID
            ownerAccount = "Test Owner",
            accountName = "test1@example.com",
            accountType = "com.example",
            displayName = "Test Calendar 1",
            name = "Test Calendar 1"
        )
        
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        assertEquals("Should find exact matching calendar", testCalendarId1, matchedId)
    }

    @Test
    fun testFindMatchingCalendarId_FallbackMatch() {
        val backupInfo = CalendarBackupInfo(
            calendarId = 999L,
            ownerAccount = "Different Owner",
            accountName = "test1@example.com",
            accountType = "com.example",
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
        // Create a test event with testCalendarId1
        val originalEvent = createTestEvent(testCalendarId1)
        
        // Create a backup info that should match testCalendarId2
        val backupInfo = CalendarBackupInfo(
            calendarId = testCalendarId1,
            ownerAccount = "Test Owner 2",
            accountName = "test2@example.com",
            accountType = "com.example",
            displayName = "Test Calendar 2",
            name = "Test Calendar 2"
        )
        
        // Restore the event
        ApplicationController.restoreEvent(context, originalEvent)
        
        // Verify the event was restored with the correct calendar ID
        val restoredEvent = CalendarProvider.getEvent(context, originalEvent.eventId)
        assertNotNull("Restored event should exist", restoredEvent)
        assertEquals("Calendar ID should be updated", testCalendarId2, restoredEvent?.calendarId)
    }

    private fun createTestCalendar(accountName: String, displayName: String, accountType: String, owner: String): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
            put(CalendarContract.Calendars.NAME, displayName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, owner)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        
        val uri = context.contentResolver.insert(CalendarContract.Calendars.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLong() ?: -1L
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