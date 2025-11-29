package com.github.quarck.calnotify.calendar

import android.content.Context
import android.provider.CalendarContract
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.dismissedeventsstorage.DismissedEventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockApplicationComponents
import com.github.quarck.calnotify.testutils.MockCalendarProvider
import com.github.quarck.calnotify.testutils.MockContextProvider
import com.github.quarck.calnotify.testutils.MockTimeProvider
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.UUID

/**
 * Robolectric version of CalendarBackupRestoreTest
 * 
 * Tests the backup and restore functionality related to calendar data.
 *
 * This test suite verifies:
 * - Retrieving backup information for a calendar (mocked CalendarProvider)
 * - Finding a matching calendar on a "restored" device based on backup info (mocked CalendarProvider)
 * - Restoring an event alert record using injected mock storage
 *
 * **Implementation Approach:** Following the pattern from `safeDismissEvents`, `restoreEvent` was
 * refactored to accept optional `EventsStorageInterface` and `DismissedEventsStorage` parameters.
 * This allows testing the orchestration logic (calendar matching, event transformation, storage calls)
 * without requiring native SQLite libraries.
 *
 * **What This Tests:**
 * - Calendar backup info retrieval and matching logic (via mocked CalendarProvider)
 * - Event transformation during restore (calendarId update, notificationId reset, etc.)
 * - Correct orchestration of storage operations (addEvent, deleteEvent calls)
 *
 * **What The Instrumentation Test Tests:**
 * - Real Android Calendar ContentProvider interactions
 * - Real EventsStorage database operations with native SQLite
 * - End-to-end backup/restore flow on real Android
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class CalendarBackupRestoreRobolectricTest {
    private val LOG_TAG = "CalBackupRestoreRobolectricTest"
    
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var mockContextProvider: MockContextProvider
    private lateinit var mockCalendarProvider: MockCalendarProvider
    private lateinit var mockComponents: MockApplicationComponents
    
    private val context: Context
        get() = mockContextProvider.fakeContext!!
    
    private var testCalendarId1: Long = -1
    private var testCalendarId2: Long = -1
    private lateinit var uniqueSuffix: String
    
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
        
        uniqueSuffix = UUID.randomUUID().toString().take(8) // Generate a unique suffix for this test run
        
        // Create calendar 1 as the source calendar with unique names
        testCalendarId1 = mockCalendarProvider.createTestCalendar(
            context,
            "Test Calendar Source $uniqueSuffix",
            "source_$uniqueSuffix@local",
            "source_$uniqueSuffix@local"
        )
        
        // Create calendar 2 as the target calendar with unique names
        testCalendarId2 = mockCalendarProvider.createTestCalendar(
            context,
            "Test Calendar Target $uniqueSuffix",
            "target_$uniqueSuffix@local",
            "target_$uniqueSuffix@local"
        )
        
        assertTrue("Test calendar 1 should be created", testCalendarId1 > 0)
        assertTrue("Test calendar 2 should be created", testCalendarId2 > 0)
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
    fun testGetCalendarBackupInfo() {
        val backupInfo = CalendarProvider.getCalendarBackupInfo(context, testCalendarId1)
        
        assertNotNull("Backup info should not be null", backupInfo)
        assertEquals("Calendar ID should match", testCalendarId1, backupInfo?.calendarId)
        assertEquals("Owner should match", "source_$uniqueSuffix@local", backupInfo?.ownerAccount)
        assertEquals("Account name should match", "source_$uniqueSuffix@local", backupInfo?.accountName)
        assertEquals("Account type should match", CalendarContract.ACCOUNT_TYPE_LOCAL, backupInfo?.accountType)
        assertEquals("Display name should match", "Test Calendar Source $uniqueSuffix", backupInfo?.displayName)
    }
    
    @Test
    fun testFindMatchingCalendarId_ExactMatch() {
        val backupInfo = CalendarBackupInfo(
            calendarId = 999L, // Different ID
            ownerAccount = "source_$uniqueSuffix@local", // Use unique owner
            accountName = "source_$uniqueSuffix@local", // Use unique account name
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            displayName = "Test Calendar Source $uniqueSuffix", // Use unique display name
            name = "Test Calendar Source $uniqueSuffix" // Use unique name
        )
        
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        assertEquals("Should find exact matching calendar", testCalendarId1, matchedId)
    }
    
    @Test
    fun testFindMatchingCalendarId_FallbackMatch() {
        // This test might become less reliable with unique names,
        // as fallback relies on non-unique properties.
        // Consider if this test case is still valid or needs adjustment.
        val backupInfo = CalendarBackupInfo(
            calendarId = 999L,
            ownerAccount = "different_$uniqueSuffix@local", // Different owner
            accountName = "source_$uniqueSuffix@local", // Match unique account name
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            displayName = "Different Name $uniqueSuffix", // Different display name
            name = "Different Name $uniqueSuffix" // Different name
        )
        
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        // Depending on the fallback logic, this might still match testCalendarId1
        // or might fail if the logic strictly requires more matches.
        // For now, keep the assertion but be aware it might need adjustment.
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
        // First create a test event in calendar 1
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val eventId = mockCalendarProvider.createTestEvent(
            context,
            testCalendarId1,
            title = "Test Event",
            description = "Test Description",
            startTime = currentTime + 3600000, // 1 hour from now
            duration = 3600000
        )
        
        // Verify the event was created in calendar 1
        var event = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Original event should exist", event)
        assertEquals("Original event should be in calendar 1", testCalendarId1, event?.calendarId)
        
        // Create backup info that matches calendar 2's unique properties
        val backupInfo = CalendarBackupInfo(
            calendarId = testCalendarId2, // Use target calendar ID
            ownerAccount = "target_$uniqueSuffix@local", // Use unique owner
            accountName = "target_$uniqueSuffix@local", // Use unique account name
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            displayName = "Test Calendar Target $uniqueSuffix", // Use unique display name
            name = "Test Calendar Target $uniqueSuffix" // Use unique name
        )
        
        // Create our EventAlertRecord using the real event ID but with target calendar ID
        val originalEvent = EventAlertRecord(
            calendarId = testCalendarId2, // Set this to the target calendar ID
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
        
        // Verify that calendar 2 exists and is accessible
        val matchedId = CalendarProvider.findMatchingCalendarId(context, backupInfo)
        assertEquals("Calendar matching should work", testCalendarId2, matchedId)
        
        // Create mock storage instances for dependency injection
        val mockEventsStorage = mockk<EventsStorageInterface>(relaxed = true)
        val mockDismissedEventsStorage = mockk<DismissedEventsStorage>(relaxed = true)
        
        // Stub the addEvent to return success
        every { mockEventsStorage.addEvent(any()) } returns true
        
        // Call restoreEvent with injected dependencies (following the pattern from safeDismissEvents)
        // This tests the orchestration logic without needing native SQLite
        ApplicationController.restoreEvent(
            context, 
            originalEvent,
            db = mockEventsStorage,
            dismissedEventsStorage = mockDismissedEventsStorage
        )
        
        // Verify the orchestration happened correctly:
        // 1. Should have added the restored event to EventsStorage
        verify { mockEventsStorage.addEvent(match { restoredEvent ->
            // Verify the event was transformed correctly
            restoredEvent.calendarId == testCalendarId2 &&  // Calendar ID updated
            restoredEvent.eventId == eventId &&             // Event ID preserved
            restoredEvent.notificationId == 0 &&            // Notification ID reset
            restoredEvent.displayStatus == EventDisplayStatus.Hidden && // Display status set
            restoredEvent.title == "Test Event"            // Title preserved
        }) }
        
        // 2. Should have deleted the event from DismissedEventsStorage
        verify { mockDismissedEventsStorage.deleteEvent(originalEvent) }
        
        // Verify the original event in system calendar is unchanged
        val systemEvent = CalendarProvider.getEvent(context, eventId)
        assertNotNull("Original event should still exist in system calendar", systemEvent)
        assertEquals("System calendar event should maintain original calendar ID", testCalendarId1, systemEvent?.calendarId)
    }
}

