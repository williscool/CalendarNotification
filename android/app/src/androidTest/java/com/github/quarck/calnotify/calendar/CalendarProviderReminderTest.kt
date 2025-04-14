package com.github.quarck.calnotify.calendar

import android.Manifest
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.CalendarProviderTestFixture
import io.mockk.every
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarProviderReminderTest {
    private val LOG_TAG = "CalProviderReminderTest"
    private lateinit var fixture: CalendarProviderTestFixture
    private var testCalendarId: Long = -1
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    
    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up test environment")
        fixture = CalendarProviderTestFixture()
        
        // Create a test calendar for all reminder tests
        testCalendarId = fixture.createCalendarWithSettings(
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        assertTrue("Test calendar should be created", testCalendarId > 0)
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        fixture.cleanup()
    }
    
    @Test
    fun testEventReminders() {
        DevLog.info(LOG_TAG, "Running testEventReminders")
        
        // Create event with default reminder (15 minutes)
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event with Reminder",
            reminderMinutes = 15,
            reminderMethod = CalendarContract.Reminders.METHOD_ALERT
        )
        
        // Debug: Log the actual reminders retrieved from the provider
        val context = fixture.contextProvider.fakeContext
        val reminders = CalendarProvider.getEventReminders(context, eventId)
        DevLog.info(LOG_TAG, "Retrieved ${reminders.size} reminders for event $eventId")
        reminders.forEachIndexed { index, reminder ->
            DevLog.info(LOG_TAG, "Reminder $index: minutes=${reminder.millisecondsBefore/60000}, method=${reminder.method}")
        }
        
        // Verify reminders - specify the exact method to check
        fixture.verifyReminders(
            eventId = eventId,
            expectedReminderCount = 1,
            expectedReminderMinutes = listOf(15),
            expectedMethods = listOf(CalendarContract.Reminders.METHOD_ALERT)
        )
    }
    
    @Test
    fun testMultipleReminders() {
        DevLog.info(LOG_TAG, "Running testMultipleReminders")
        
        // Create event with multiple reminders
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event with Multiple Reminders",
            reminderMinutes = 5,  // Initial reminder of 5 minutes
            reminderMethod = CalendarContract.Reminders.METHOD_ALERT
        )
        
        // Add additional reminders using the real implementation
        val reminderTimes = listOf(5, 15, 30) // 5 minutes, 15 minutes, 30 minutes before
        val context = fixture.contextProvider.fakeContext
        
        // Add each reminder using the real implementation
        reminderTimes.forEach { minutes ->
            val reminderValues = android.content.ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        }
        
        // Verify all reminders are present
        fixture.verifyReminders(
            eventId = eventId,
            expectedReminderCount = reminderTimes.size,
            expectedReminderMinutes = reminderTimes,
            expectedMethods = List(reminderTimes.size) { CalendarContract.Reminders.METHOD_ALERT }
        )
    }
    
    @Test
    fun testGetNextReminderTime() {
        DevLog.info(LOG_TAG, "Running testGetNextReminderTime")
        
        // Create event starting in 2 hours
        val startTime = fixture.timeProvider.testClock.currentTimeMillis() + 7200000 // 2 hours
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event for Next Reminder",
            startTime = startTime,
            reminderMinutes = 30 // 30 minute reminder
        )
        
        // Get next reminder time
        val nextReminder = CalendarProvider.getNextEventReminderTime(
            fixture.contextProvider.fakeContext,
            eventId,
            startTime
        )
        
        // Expected reminder time is 30 minutes before start
        val expectedReminderTime = startTime - (30 * 60000)
        assertEquals("Next reminder time should be correct",
            expectedReminderTime, nextReminder)
    }
    
    @Test
    fun testAllDayEventReminders() {
        DevLog.info(LOG_TAG, "Running testAllDayEventReminders")
        
        // Create all-day event
        val startTime = fixture.timeProvider.testClock.currentTimeMillis() + 86400000 // Tomorrow
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "All Day Event with Reminder",
            startTime = startTime,
            duration = 86400000,
            isAllDay = true,
            reminderMinutes = 60 // 1 hour reminder
        )
        
        // Verify reminder
        fixture.verifyReminders(
            eventId = eventId,
            expectedReminderCount = 1,
            expectedReminderMinutes = listOf(60)
        )
        
        // Get next reminder time - should account for all-day event timing
        val nextReminder = CalendarProvider.getNextEventReminderTime(
            fixture.contextProvider.fakeContext,
            eventId,
            startTime
        )
        
        // Expected reminder time is 1 hour before start, adjusted for all-day event
        val expectedReminderTime = startTime - (60 * 60000)
        assertEquals("Next reminder time for all-day event should be correct",
            expectedReminderTime, nextReminder)
    }
    
    @Test
    fun testRecurringEventReminders() {
        DevLog.info(LOG_TAG, "Running testRecurringEventReminders")
        
        // Create recurring event
        val startTime = fixture.timeProvider.testClock.currentTimeMillis() + 3600000
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Recurring Event with Reminder",
            startTime = startTime,
            repeatingRule = "FREQ=DAILY;COUNT=5",
            reminderMinutes = 15
        )
        
        // Verify reminder for first instance
        fixture.verifyReminders(
            eventId = eventId,
            expectedReminderCount = 1,
            expectedReminderMinutes = listOf(15)
        )
        
        // Get next reminder time for a future instance
        val futureInstanceStart = startTime + (24 * 3600000) // Next day
        val nextReminder = CalendarProvider.getNextEventReminderTime(
            fixture.contextProvider.fakeContext,
            eventId,
            futureInstanceStart
        )
        
        // Expected reminder time is 15 minutes before future instance
        val expectedReminderTime = futureInstanceStart - (15 * 60000)
        assertEquals("Next reminder time for recurring event should be correct",
            expectedReminderTime, nextReminder)
    }
    
    @Test
    fun testEmailReminder() {
        DevLog.info(LOG_TAG, "Running testEmailReminder")
        
        // Create event with email reminder
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event with Email Reminder",
            reminderMinutes = 30,
            reminderMethod = CalendarContract.Reminders.METHOD_EMAIL
        )
        
        // Verify reminder method
        fixture.verifyReminders(
            eventId = eventId,
            expectedReminderCount = 1,
            expectedReminderMinutes = listOf(30),
            expectedMethods = listOf(CalendarContract.Reminders.METHOD_EMAIL)
        )
    }
    
    @Test
    fun testReminderTimeCalculation() {
        DevLog.info(LOG_TAG, "Running testReminderTimeCalculation")
        
        // Create event with specific timing
        val currentTime = fixture.timeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 7200000 // 2 hours from now
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event for Reminder Calculation",
            startTime = startTime,
            reminderMinutes = 45 // 45 minute reminder
        )
        
        // Create an EventAlertRecord for the event
        val alertTime = startTime - (45 * 60000)
        val alertRecord = EventAlertRecord(
            calendarId = testCalendarId,
            eventId = eventId,
            isAllDay = false,
            isRepeating = false,
            alertTime = alertTime,
            notificationId = Consts.NOTIFICATION_ID_DYNAMIC_FROM,
            title = "Event for Reminder Calculation",
            desc = "Test Description",
            startTime = startTime,
            endTime = startTime + 3600000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 3600000,
            location = "",
            lastStatusChangeTime = currentTime,
            displayStatus = EventDisplayStatus.Hidden,
            color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = currentTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
        
        // Calculate next reminder time using event record
        val nextReminder = CalendarProvider.getNextEventReminderTime(
            fixture.contextProvider.fakeContext,
            alertRecord
        )
        
        // Expected reminder time is 45 minutes before start
        val expectedReminderTime = startTime - (45 * 60000)
        assertEquals("Calculated reminder time should be correct",
            expectedReminderTime, nextReminder)
    }
    
    @Test
    fun testDismissReminder() {
        DevLog.info(LOG_TAG, "Running testDismissReminder")
        
        // Create event
        val eventId = fixture.createEventWithSettings(
            testCalendarId,
            "Event for Dismissal"
        )
        
        // Dismiss the reminder
        CalendarProvider.dismissNativeEventAlert(
            fixture.contextProvider.fakeContext,
            eventId
        )
        
        // Note: We can't directly verify the dismissal state in the calendar provider
        // as it's handled by the system. The method call itself being successful
        // is considered a pass.
    }
} 
