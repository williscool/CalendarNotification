package com.github.quarck.calnotify.calendar

import android.content.Context
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarProviderReminderRobolectricTest {
    private val LOG_TAG = "CalProviderReminderRobolectricTest"
    
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockContextProvider: MockContextProvider
    private lateinit var mockCalendarProvider: MockCalendarProvider
    private lateinit var mockComponents: MockApplicationComponents
    
    private val context: Context
        get() = mockContextProvider.fakeContext!!
    
    private var testCalendarId: Long = -1
    
    @Before
    fun setup() {
        ShadowLog.stream = System.out
        DevLog.info(LOG_TAG, "Setting up test environment")
        
        MockKAnnotations.init(this)
        unmockkAll()
        
        // Note: Permissions are granted automatically by MockContextProvider.setup()
        
        mockTimeProvider = MockTimeProvider()
        mockTimeProvider.setup()
        
        mockContextProvider = MockContextProvider(mockTimeProvider)
        mockContextProvider.setup()
        
        mockCalendarProvider = MockCalendarProvider(mockContextProvider, mockTimeProvider)
        mockCalendarProvider.setup()
        
        mockComponents = MockApplicationComponents(
            mockContextProvider,
            mockTimeProvider,
            mockCalendarProvider
        )
        mockComponents.setup()
        
        // Create a test calendar for all reminder tests
        testCalendarId = mockCalendarProvider.createTestCalendar(
            context,
            "Test Calendar",
            "test@example.com",
            "test@example.com"
        )
        assertTrue("Test calendar should be created", testCalendarId > 0)
    }
    
    @After
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up test environment")
        mockCalendarProvider.cleanup()
        mockContextProvider.cleanup()
        mockTimeProvider.cleanup()
        unmockkAll()
    }
    
    @Test
    fun testEventReminders() {
        DevLog.info(LOG_TAG, "Running testEventReminders")
        
        // Create event with default reminder (15 minutes)
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event with Reminder",
            reminderMinutes = 15,
            reminderMethod = CalendarContract.Reminders.METHOD_ALERT
        )
        
        // Debug: Log the actual reminders retrieved from the provider
        val reminders = CalendarProvider.getEventReminders(context, eventId)
        DevLog.info(LOG_TAG, "Retrieved ${reminders.size} reminders for event $eventId")
        reminders.forEachIndexed { index, reminder ->
            DevLog.info(LOG_TAG, "Reminder $index: minutes=${reminder.millisecondsBefore/60000}, method=${reminder.method}")
        }
        
        // Verify reminders
        assertEquals("Should have 1 reminder", 1, reminders.size)
        assertEquals("Reminder minutes should be 15", 15, (reminders[0].millisecondsBefore / 60000).toInt())
        assertEquals("Reminder method should be ALERT", CalendarContract.Reminders.METHOD_ALERT, reminders[0].method)
    }
    
    @Test
    fun testMultipleReminders() {
        DevLog.info(LOG_TAG, "Running testMultipleReminders")
        
        // Create event with initial reminder of 5 minutes
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event with Multiple Reminders",
            reminderMinutes = 5,
            reminderMethod = CalendarContract.Reminders.METHOD_ALERT
        )
        
        // Add additional reminders using MockCalendarProvider helper
        mockCalendarProvider.addReminderToEvent(eventId, 15, CalendarContract.Reminders.METHOD_ALERT)
        mockCalendarProvider.addReminderToEvent(eventId, 30, CalendarContract.Reminders.METHOD_ALERT)
        
        // Debug: Check what reminders are actually present
        val actualReminders = CalendarProvider.getEventReminders(context, eventId)
        DevLog.info(LOG_TAG, "Found ${actualReminders.size} reminders for event $eventId")
        actualReminders.forEachIndexed { index, reminder ->
            DevLog.info(LOG_TAG, "Reminder $index: minutes=${reminder.millisecondsBefore/60000}, method=${reminder.method}")
        }
        
        // The event will have 3 reminders (1 initial + 2 added)
        assertEquals("Should have 3 reminders", 3, actualReminders.size)
        
        // Verify reminder minutes (should be 5, 15, 30)
        val reminderMinutes = actualReminders.map { (it.millisecondsBefore / 60000).toInt() }.sorted()
        assertEquals("Reminder minutes should match", listOf(5, 15, 30), reminderMinutes)
        
        // Verify all methods are ALERT
        actualReminders.forEach { reminder ->
            assertEquals("Reminder method should be ALERT", CalendarContract.Reminders.METHOD_ALERT, reminder.method)
        }
    }
    
    @Test
    fun testGetNextReminderTime() {
        DevLog.info(LOG_TAG, "Running testGetNextReminderTime")
        
        // Create event starting in 2 hours
        val startTime = mockTimeProvider.testClock.currentTimeMillis() + 7200000 // 2 hours
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event for Next Reminder",
            startTime = startTime,
            reminderMinutes = 30 // 30 minute reminder
        )
        
        // Get next reminder time
        val nextReminder = CalendarProvider.getNextEventReminderTime(
            context,
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
        val startTime = mockTimeProvider.testClock.currentTimeMillis() + 86400000 // Tomorrow
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "All Day Event with Reminder",
            startTime = startTime,
            duration = 86400000,
            isAllDay = true,
            reminderMinutes = 60 // 1 hour reminder
        )
        
        // Verify reminder
        val reminders = CalendarProvider.getEventReminders(context, eventId)
        assertEquals("Should have 1 reminder", 1, reminders.size)
        assertEquals("Reminder minutes should be 60", 60, (reminders[0].millisecondsBefore / 60000).toInt())
        
        // Get next reminder time - should account for all-day event timing
        val nextReminder = CalendarProvider.getNextEventReminderTime(
            context,
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
        val startTime = mockTimeProvider.testClock.currentTimeMillis() + 3600000
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Recurring Event with Reminder",
            startTime = startTime,
            repeatingRule = "FREQ=DAILY;COUNT=5",
            reminderMinutes = 15
        )
        
        // Verify reminder for first instance
        val reminders = CalendarProvider.getEventReminders(context, eventId)
        assertEquals("Should have 1 reminder", 1, reminders.size)
        assertEquals("Reminder minutes should be 15", 15, (reminders[0].millisecondsBefore / 60000).toInt())
        
        // Get next reminder time for a future instance
        val futureInstanceStart = startTime + (24 * 3600000) // Next day
        val nextReminder = CalendarProvider.getNextEventReminderTime(
            context,
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
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event with Email Reminder",
            reminderMinutes = 30,
            reminderMethod = CalendarContract.Reminders.METHOD_EMAIL
        )
        
        // Verify reminder method
        val reminders = CalendarProvider.getEventReminders(context, eventId)
        assertEquals("Should have 1 reminder", 1, reminders.size)
        assertEquals("Reminder minutes should be 30", 30, (reminders[0].millisecondsBefore / 60000).toInt())
        assertEquals("Reminder method should be EMAIL", CalendarContract.Reminders.METHOD_EMAIL, reminders[0].method)
    }
    
    @Test
    fun testReminderTimeCalculation() {
        DevLog.info(LOG_TAG, "Running testReminderTimeCalculation")
        
        // Create event with specific timing
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = currentTime + 7200000 // 2 hours from now
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event for Reminder Calculation",
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
            context,
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
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId,
            title = "Event for Dismissal"
        )
        
        // Dismiss the reminder - should not throw
        CalendarProvider.dismissNativeEventAlert(
            context,
            eventId
        )
        
        // Note: We can't directly verify the dismissal state in the calendar provider
        // as it's handled by the system. The method call itself being successful
        // is considered a pass.
    }
}

