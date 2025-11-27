package com.github.quarck.calnotify.monitorstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.testutils.MockTimeProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSQLiteConnection

/**
 * Robolectric tests for MonitorStorage.
 *
 * These tests verify the actual SQLite storage operations for calendar monitoring alerts
 * using Robolectric's in-memory database support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class MonitorStorageRobolectricTest {
    private val LOG_TAG = "MonitorStorageRoboTest"

    private lateinit var context: Context
    private lateinit var storage: MonitorStorage
    private lateinit var mockTimeProvider: MockTimeProvider

    @Before
    fun setup() {
        // Enable Robolectric logging
        ShadowLog.stream = System.out

        // Configure Robolectric SQLite to use in-memory database
        ShadowSQLiteConnection.setUseInMemoryDatabase(true)

        DevLog.info(LOG_TAG, "Setting up MonitorStorageRobolectricTest")

        // Get Robolectric context
        context = ApplicationProvider.getApplicationContext()

        // Setup mock time provider
        mockTimeProvider = MockTimeProvider(1635724800000) // 2021-11-01 00:00:00 UTC
        mockTimeProvider.setup()

        // Create storage
        storage = MonitorStorage(context)
    }

    @After
    fun tearDown() {
        DevLog.info(LOG_TAG, "Cleaning up MonitorStorageRobolectricTest")
        storage.close()
        mockTimeProvider.cleanup()
    }

    @Test
    fun testAddAndRetrieveSingleAlert() {
        // Given
        val alert = createTestAlert(1)

        // When
        storage.addAlert(alert)
        val alerts = storage.alerts

        // Then
        assertEquals("Should have exactly one alert", 1, alerts.size)
        
        val storedAlert = alerts[0]
        assertEquals("Event ID should match", alert.eventId, storedAlert.eventId)
        assertEquals("Alert time should match", alert.alertTime, storedAlert.alertTime)
        assertEquals("Instance start time should match", alert.instanceStartTime, storedAlert.instanceStartTime)
    }

    @Test
    fun testAddMultipleAlerts() {
        // Given
        val alert1 = createTestAlert(1)
        val alert2 = createTestAlert(2)
        val alert3 = createTestAlert(3)
        val alertsToAdd = listOf(alert1, alert2, alert3)

        // When
        storage.addAlerts(alertsToAdd)
        val storedAlerts = storage.alerts

        // Then
        assertEquals("Should have exactly three alerts", 3, storedAlerts.size)
        
        val eventIds = storedAlerts.map { it.eventId }
        assertTrue("Should contain alert 1", eventIds.contains(1L))
        assertTrue("Should contain alert 2", eventIds.contains(2L))
        assertTrue("Should contain alert 3", eventIds.contains(3L))
    }

    @Test
    fun testGetAlertByEventIdAndTime() {
        // Given
        val alert = createTestAlert(1)
        storage.addAlert(alert)

        // When
        val retrievedAlert = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)

        // Then
        assertNotNull("Alert should be found", retrievedAlert)
        assertEquals("Event ID should match", alert.eventId, retrievedAlert?.eventId)
        assertEquals("Alert time should match", alert.alertTime, retrievedAlert?.alertTime)
    }

    @Test
    fun testGetInstanceAlerts() {
        // Given - create multiple alerts for the same event instance
        val baseTime = mockTimeProvider.testClock.currentTimeMillis()
        val alert1 = createTestAlert(1, alertTime = baseTime - 900000) // 15 min before
        val alert2 = createTestAlert(1, alertTime = baseTime - 300000) // 5 min before
        
        storage.addAlert(alert1)
        storage.addAlert(alert2)

        // When
        val instanceAlerts = storage.getInstanceAlerts(1, alert1.instanceStartTime)

        // Then
        assertEquals("Should have two alerts for the instance", 2, instanceAlerts.size)
        assertTrue("All alerts should have the same event ID", instanceAlerts.all { it.eventId == 1L })
    }

    @Test
    fun testDeleteAlert() {
        // Given
        val alert = createTestAlert(1)
        storage.addAlert(alert)
        
        // Verify alert was added
        assertEquals("Should have one alert before deletion", 1, storage.alerts.size)

        // When
        storage.deleteAlert(alert)
        val alerts = storage.alerts

        // Then
        assertEquals("Should have no alerts after deletion", 0, alerts.size)
    }

    @Test
    fun testDeleteAlerts() {
        // Given
        val alert1 = createTestAlert(1)
        val alert2 = createTestAlert(2)
        val alert3 = createTestAlert(3)
        val alertsToDelete = listOf(alert1, alert2)
        
        storage.addAlert(alert1)
        storage.addAlert(alert2)
        storage.addAlert(alert3)
        
        // Verify alerts were added
        assertEquals("Should have three alerts before deletion", 3, storage.alerts.size)

        // When
        storage.deleteAlerts(alertsToDelete)
        val remainingAlerts = storage.alerts

        // Then
        assertEquals("Should have one alert remaining", 1, remainingAlerts.size)
        assertEquals("Remaining alert should be alert 3", 3L, remainingAlerts[0].eventId)
    }

    @Test
    fun testDeleteAlertsMatching() {
        // Given
        val alert1 = createTestAlert(1, wasHandled = true)
        val alert2 = createTestAlert(2, wasHandled = false)
        val alert3 = createTestAlert(3, wasHandled = true)
        
        storage.addAlert(alert1)
        storage.addAlert(alert2)
        storage.addAlert(alert3)

        // When - delete all handled alerts
        storage.deleteAlertsMatching { it.wasHandled }
        val remainingAlerts = storage.alerts

        // Then
        assertEquals("Should have one alert remaining", 1, remainingAlerts.size)
        assertEquals("Remaining alert should be alert 2 (unhandled)", 2L, remainingAlerts[0].eventId)
    }

    @Test
    fun testUpdateAlert() {
        // Given
        val alert = createTestAlert(1, wasHandled = false)
        storage.addAlert(alert)

        // When - update the alert to mark it as handled
        val updatedAlert = alert.copy(wasHandled = true)
        storage.updateAlert(updatedAlert)
        val retrievedAlert = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)

        // Then
        assertNotNull("Updated alert should be found", retrievedAlert)
        assertTrue("Alert should be marked as handled", retrievedAlert?.wasHandled == true)
    }

    @Test
    fun testUpdateAlerts() {
        // Given
        val alert1 = createTestAlert(1, wasHandled = false)
        val alert2 = createTestAlert(2, wasHandled = false)
        storage.addAlert(alert1)
        storage.addAlert(alert2)

        // When - update both alerts
        val updatedAlerts = listOf(
            alert1.copy(wasHandled = true),
            alert2.copy(wasHandled = true)
        )
        storage.updateAlerts(updatedAlerts)
        val storedAlerts = storage.alerts

        // Then
        assertEquals("Should still have two alerts", 2, storedAlerts.size)
        assertTrue("All alerts should be marked as handled", storedAlerts.all { it.wasHandled })
    }

    @Test
    fun testGetNextAlert() {
        // Given
        val baseTime = mockTimeProvider.testClock.currentTimeMillis()
        val alert1 = createTestAlert(1, alertTime = baseTime + 300000) // 5 min from now
        val alert2 = createTestAlert(2, alertTime = baseTime + 600000) // 10 min from now
        val alert3 = createTestAlert(3, alertTime = baseTime + 900000) // 15 min from now
        
        storage.addAlert(alert1)
        storage.addAlert(alert2)
        storage.addAlert(alert3)

        // When
        val nextAlertTime = storage.getNextAlert(baseTime)

        // Then
        assertNotNull("Should find next alert", nextAlertTime)
        assertEquals("Next alert should be in 5 min", baseTime + 300000, nextAlertTime)
    }

    @Test
    fun testGetAlertsAt() {
        // Given
        val baseTime = mockTimeProvider.testClock.currentTimeMillis()
        val alertTime = baseTime + 300000
        
        val alert1 = createTestAlert(1, alertTime = alertTime)
        val alert2 = createTestAlert(2, alertTime = alertTime)
        val alert3 = createTestAlert(3, alertTime = baseTime + 600000) // Different time
        
        storage.addAlert(alert1)
        storage.addAlert(alert2)
        storage.addAlert(alert3)

        // When
        val alertsAtTime = storage.getAlertsAt(alertTime)

        // Then
        assertEquals("Should have two alerts at the specified time", 2, alertsAtTime.size)
        val eventIds = alertsAtTime.map { it.eventId }
        assertTrue("Should contain alert 1", eventIds.contains(1L))
        assertTrue("Should contain alert 2", eventIds.contains(2L))
    }

    @Test
    fun testGetAlertsForInstanceStartRange() {
        // Given
        val baseTime = mockTimeProvider.testClock.currentTimeMillis()
        
        val alert1 = createTestAlert(1, instanceStartTime = baseTime + 3600000) // 1 hour
        val alert2 = createTestAlert(2, instanceStartTime = baseTime + 7200000) // 2 hours
        val alert3 = createTestAlert(3, instanceStartTime = baseTime + 86400000) // 24 hours
        
        storage.addAlert(alert1)
        storage.addAlert(alert2)
        storage.addAlert(alert3)

        // When - get alerts for instances starting in the next 3 hours
        val scanFrom = baseTime
        val scanTo = baseTime + 10800000 // 3 hours
        val alertsInRange = storage.getAlertsForInstanceStartRange(scanFrom, scanTo)

        // Then
        assertEquals("Should have two alerts in range", 2, alertsInRange.size)
        val eventIds = alertsInRange.map { it.eventId }
        assertTrue("Should contain alert 1", eventIds.contains(1L))
        assertTrue("Should contain alert 2", eventIds.contains(2L))
        assertFalse("Should not contain alert 3", eventIds.contains(3L))
    }

    @Test
    fun testGetAlertsForAlertRange() {
        // Given
        val baseTime = mockTimeProvider.testClock.currentTimeMillis()
        
        val alert1 = createTestAlert(1, alertTime = baseTime + 300000) // 5 min
        val alert2 = createTestAlert(2, alertTime = baseTime + 600000) // 10 min
        val alert3 = createTestAlert(3, alertTime = baseTime + 3600000) // 60 min
        
        storage.addAlert(alert1)
        storage.addAlert(alert2)
        storage.addAlert(alert3)

        // When - get alerts in the next 30 minutes
        val scanFrom = baseTime
        val scanTo = baseTime + 1800000 // 30 min
        val alertsInRange = storage.getAlertsForAlertRange(scanFrom, scanTo)

        // Then
        assertEquals("Should have two alerts in range", 2, alertsInRange.size)
        val eventIds = alertsInRange.map { it.eventId }
        assertTrue("Should contain alert 1", eventIds.contains(1L))
        assertTrue("Should contain alert 2", eventIds.contains(2L))
        assertFalse("Should not contain alert 3", eventIds.contains(3L))
    }

    @Test
    fun testAlertDetailsPreserved() {
        // Given
        val alert = MonitorEventAlertEntry(
            eventId = 1,
            isAllDay = true,
            alertTime = mockTimeProvider.testClock.currentTimeMillis() - 900000,
            instanceStartTime = mockTimeProvider.testClock.currentTimeMillis(),
            instanceEndTime = mockTimeProvider.testClock.currentTimeMillis() + 3600000,
            alertCreatedByUs = true,
            wasHandled = true
        )

        // When
        storage.addAlert(alert)
        val retrievedAlert = storage.getAlert(alert.eventId, alert.alertTime, alert.instanceStartTime)

        // Then
        assertNotNull("Alert should be found", retrievedAlert)
        assertEquals("isAllDay should be preserved", true, retrievedAlert?.isAllDay)
        assertEquals("alertCreatedByUs should be preserved", true, retrievedAlert?.alertCreatedByUs)
        assertEquals("wasHandled should be preserved", true, retrievedAlert?.wasHandled)
    }

    @Test
    fun testGetNonExistentAlert() {
        // Given - no alerts added

        // When
        val retrievedAlert = storage.getAlert(999L, 0L, 0L)

        // Then
        assertNull("Should return null for non-existent alert", retrievedAlert)
    }

    @Test
    fun testEmptyAlertsList() {
        // Given - no alerts added

        // When
        val alerts = storage.alerts

        // Then
        assertTrue("Alerts list should be empty", alerts.isEmpty())
    }

    private fun createTestAlert(
        eventId: Long, 
        alertTime: Long? = null,
        instanceStartTime: Long? = null,
        wasHandled: Boolean = false
    ): MonitorEventAlertEntry {
        val currentTime = mockTimeProvider.testClock.currentTimeMillis()
        val startTime = instanceStartTime ?: (currentTime + 3600000) // 1 hour from now
        val alertTimeVal = alertTime ?: (startTime - 900000) // 15 min before instance
        
        return MonitorEventAlertEntry(
            eventId = eventId,
            isAllDay = false,
            alertTime = alertTimeVal,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 3600000, // 1 hour duration
            alertCreatedByUs = false,
            wasHandled = wasHandled
        )
    }
}
