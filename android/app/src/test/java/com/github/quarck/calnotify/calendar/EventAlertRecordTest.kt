package com.github.quarck.calnotify.calendar

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EventAlertRecord data class and its extension functions.
 * 
 * These are pure JVM unit tests that don't need Robolectric or Android context.
 */
class EventAlertRecordTest {
    
    private lateinit var baseEvent: EventAlertRecord
    private val baseTime = 1635724800000L // 2021-11-01 00:00:00 UTC
    
    @Before
    fun setup() {
        baseEvent = createTestEvent(1)
    }
    
    @Test
    fun `test EventAlertRecord key generation`() {
        // Given
        val event = createTestEvent(123, instanceStartTime = baseTime + 3600000)
        
        // When
        val key = event.key
        
        // Then
        assertEquals("Key eventId should match", 123L, key.eventId)
        assertEquals("Key instanceStartTime should match", baseTime + 3600000, key.instanceStartTime)
    }
    
    @Test
    fun `test isMuted flag operations`() {
        // Given
        val event = createTestEvent(1)
        
        // Initially not muted
        assertFalse("Event should not be muted initially", event.isMuted)
        
        // When - set muted
        event.isMuted = true
        
        // Then
        assertTrue("Event should be muted", event.isMuted)
        assertTrue("Flags should contain IS_MUTED", event.flags.isFlagSet(EventAlertFlags.IS_MUTED))
        
        // When - unset muted
        event.isMuted = false
        
        // Then
        assertFalse("Event should not be muted", event.isMuted)
        assertFalse("Flags should not contain IS_MUTED", event.flags.isFlagSet(EventAlertFlags.IS_MUTED))
    }
    
    @Test
    fun `test isTask flag operations`() {
        // Given
        val event = createTestEvent(1)
        
        // Initially not a task
        assertFalse("Event should not be a task initially", event.isTask)
        
        // When - set task
        event.isTask = true
        
        // Then
        assertTrue("Event should be a task", event.isTask)
        assertTrue("Flags should contain IS_TASK", event.flags.isFlagSet(EventAlertFlags.IS_TASK))
        
        // When - unset task
        event.isTask = false
        
        // Then
        assertFalse("Event should not be a task", event.isTask)
    }
    
    @Test
    fun `test isAlarm flag operations`() {
        // Given
        val event = createTestEvent(1)
        
        // Initially not an alarm
        assertFalse("Event should not be an alarm initially", event.isAlarm)
        
        // When - set alarm
        event.isAlarm = true
        
        // Then
        assertTrue("Event should be an alarm", event.isAlarm)
        assertTrue("Flags should contain IS_ALARM", event.flags.isFlagSet(EventAlertFlags.IS_ALARM))
    }
    
    @Test
    fun `test isUnmutedAlarm property`() {
        // Given
        val event = createTestEvent(1)
        
        // Case 1: Not an alarm
        assertFalse("Should not be unmuted alarm when not an alarm", event.isUnmutedAlarm)
        
        // Case 2: Alarm but muted
        event.isAlarm = true
        event.isMuted = true
        assertFalse("Should not be unmuted alarm when muted", event.isUnmutedAlarm)
        
        // Case 3: Alarm and not muted
        event.isMuted = false
        assertTrue("Should be unmuted alarm", event.isUnmutedAlarm)
    }
    
    @Test
    fun `test multiple flags can be set simultaneously`() {
        // Given
        val event = createTestEvent(1)
        
        // When - set multiple flags
        event.isMuted = true
        event.isTask = true
        event.isAlarm = true
        
        // Then - all flags should be set
        assertTrue("IS_MUTED should be set", event.isMuted)
        assertTrue("IS_TASK should be set", event.isTask)
        assertTrue("IS_ALARM should be set", event.isAlarm)
        
        // When - unset one flag
        event.isTask = false
        
        // Then - other flags should remain
        assertTrue("IS_MUTED should still be set", event.isMuted)
        assertFalse("IS_TASK should not be set", event.isTask)
        assertTrue("IS_ALARM should still be set", event.isAlarm)
    }
    
    @Test
    fun `test displayedStartTime property`() {
        // Given
        val eventWithInstance = createTestEvent(1, instanceStartTime = baseTime + 3600000)
        val eventWithoutInstance = createTestEvent(1, instanceStartTime = 0L)
        
        // Then
        assertEquals("Should use instanceStartTime when set", baseTime + 3600000, eventWithInstance.displayedStartTime)
        assertEquals("Should use startTime when instanceStartTime is 0", eventWithoutInstance.startTime, eventWithoutInstance.displayedStartTime)
    }
    
    @Test
    fun `test displayedEndTime property`() {
        // Given
        val eventWithInstance = createTestEvent(1)
        val eventWithoutInstance = eventWithInstance.copy(instanceEndTime = 0L)
        
        // Then
        assertEquals("Should use instanceEndTime when set", eventWithInstance.instanceEndTime, eventWithInstance.displayedEndTime)
        assertEquals("Should use endTime when instanceEndTime is 0", eventWithoutInstance.endTime, eventWithoutInstance.displayedEndTime)
    }
    
    @Test
    fun `test isSnoozed property`() {
        // Given
        val notSnoozedEvent = createTestEvent(1, snoozedUntil = 0L)
        val snoozedEvent = createTestEvent(1, snoozedUntil = baseTime + 1800000)
        
        // Then
        assertFalse("Event should not be snoozed", notSnoozedEvent.isSnoozed)
        assertTrue("Event should not be snoozed flag", notSnoozedEvent.isNotSnoozed)
        assertTrue("Event should be snoozed", snoozedEvent.isSnoozed)
        assertFalse("Event should not have isNotSnoozed", snoozedEvent.isNotSnoozed)
    }
    
    @Test
    fun `test isSpecial property`() {
        // Given
        val normalEvent = createTestEvent(1)
        val specialEvent = createTestEvent(1, instanceStartTime = Long.MAX_VALUE)
        
        // Then
        assertFalse("Normal event should not be special", normalEvent.isSpecial)
        assertTrue("Normal event should be not special", normalEvent.isNotSpecial)
        assertTrue("Special event should be special", specialEvent.isSpecial)
        assertFalse("Special event should not be not special", specialEvent.isNotSpecial)
    }
    
    @Test
    fun `test specialId property`() {
        // Given
        val normalEvent = createTestEvent(123)
        val specialEvent = createTestEvent(456, instanceStartTime = Long.MAX_VALUE)
        
        // Then
        assertEquals("Normal event specialId should be -1", -1L, normalEvent.specialId)
        assertEquals("Special event specialId should be eventId", 456L, specialEvent.specialId)
    }
    
    @Test
    fun `test updateFrom EventAlertRecord`() {
        // Given
        val originalEvent = createTestEvent(1)
        val newEvent = originalEvent.copy(
            title = "Updated Title",
            desc = "Updated Description",
            location = "New Location",
            alertTime = baseTime + 600000
        )
        
        // When
        val wasUpdated = originalEvent.updateFrom(newEvent)
        
        // Then
        assertTrue("Should return true when updates made", wasUpdated)
        assertEquals("Title should be updated", "Updated Title", originalEvent.title)
        assertEquals("Description should be updated", "Updated Description", originalEvent.desc)
        assertEquals("Location should be updated", "New Location", originalEvent.location)
        assertEquals("Alert time should be updated", baseTime + 600000, originalEvent.alertTime)
    }
    
    @Test
    fun `test updateFrom EventAlertRecord no changes`() {
        // Given
        val originalEvent = createTestEvent(1)
        val sameEvent = originalEvent.copy() // Same values
        
        // When
        val wasUpdated = originalEvent.updateFrom(sameEvent)
        
        // Then
        assertFalse("Should return false when no updates made", wasUpdated)
    }
    
    @Test
    fun `test titleAsOneLine removes newlines`() {
        // Given
        val eventWithNewlines = createTestEvent(1).copy(title = "Line 1\nLine 2\r\nLine 3")
        
        // When
        val oneLineTitle = eventWithNewlines.titleAsOneLine
        
        // Then
        assertEquals("Title should be on one line", "Line 1 Line 2 Line 3", oneLineTitle)
        assertFalse("Should not contain newline", oneLineTitle.contains("\n"))
        assertFalse("Should not contain carriage return", oneLineTitle.contains("\r"))
    }
    
    @Test
    fun `test toPublicString format`() {
        // Given
        val event = createTestEvent(1)
        
        // When
        val publicString = event.toPublicString()
        
        // Then
        assertTrue("Should start with EventAlertRecord", publicString.startsWith("EventAlertRecord("))
        assertTrue("Should contain calendarId", publicString.contains(event.calendarId.toString()))
        assertTrue("Should contain eventId", publicString.contains(event.eventId.toString()))
    }
    
    @Test
    fun `test isActiveAlarm property`() {
        // Given
        val baseEvent = createTestEvent(1)
        
        // Case 1: Normal event (not alarm)
        assertFalse("Normal event should not be active alarm", baseEvent.isActiveAlarm)
        
        // Case 2: Alarm that is snoozed
        val snoozedAlarm = baseEvent.copy(snoozedUntil = baseTime + 3600000).apply { isAlarm = true }
        assertFalse("Snoozed alarm should not be active alarm", snoozedAlarm.isActiveAlarm)
        
        // Case 3: Special alarm
        val specialAlarm = baseEvent.copy(instanceStartTime = Long.MAX_VALUE).apply { isAlarm = true }
        assertFalse("Special alarm should not be active alarm", specialAlarm.isActiveAlarm)
        
        // Case 4: Active alarm (not snoozed, not special, is alarm)
        val activeAlarm = baseEvent.copy(snoozedUntil = 0L).apply { isAlarm = true }
        assertTrue("Should be active alarm", activeAlarm.isActiveAlarm)
    }
    
    @Test
    fun `test isFlagSet extension function`() {
        // Given
        val flags: Long = EventAlertFlags.IS_MUTED or EventAlertFlags.IS_ALARM
        
        // Then
        assertTrue("IS_MUTED should be set", flags.isFlagSet(EventAlertFlags.IS_MUTED))
        assertFalse("IS_TASK should not be set", flags.isFlagSet(EventAlertFlags.IS_TASK))
        assertTrue("IS_ALARM should be set", flags.isFlagSet(EventAlertFlags.IS_ALARM))
    }
    
    @Test
    fun `test setFlag extension function`() {
        // Given
        var flags: Long = 0
        
        // When - set a flag
        flags = flags.setFlag(EventAlertFlags.IS_MUTED, true)
        
        // Then
        assertTrue("Flag should be set", flags.isFlagSet(EventAlertFlags.IS_MUTED))
        
        // When - set another flag
        flags = flags.setFlag(EventAlertFlags.IS_TASK, true)
        
        // Then - both should be set
        assertTrue("IS_MUTED should still be set", flags.isFlagSet(EventAlertFlags.IS_MUTED))
        assertTrue("IS_TASK should be set", flags.isFlagSet(EventAlertFlags.IS_TASK))
        
        // When - unset first flag
        flags = flags.setFlag(EventAlertFlags.IS_MUTED, false)
        
        // Then
        assertFalse("IS_MUTED should not be set", flags.isFlagSet(EventAlertFlags.IS_MUTED))
        assertTrue("IS_TASK should still be set", flags.isFlagSet(EventAlertFlags.IS_TASK))
    }
    
    private fun createTestEvent(
        id: Long,
        instanceStartTime: Long? = null,
        snoozedUntil: Long = 0L
    ): EventAlertRecord {
        val startTime = instanceStartTime ?: (baseTime + 3600000)
        
        return EventAlertRecord(
            calendarId = 1L,
            eventId = id,
            isAllDay = false,
            isRepeating = false,
            alertTime = baseTime,
            notificationId = id.toInt(),
            title = "Test Event $id",
            desc = "Test Description",
            startTime = startTime,
            endTime = startTime + 3600000,
            instanceStartTime = startTime,
            instanceEndTime = startTime + 3600000,
            location = "",
            lastStatusChangeTime = baseTime,
            snoozedUntil = snoozedUntil,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0xffff0000.toInt(),
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = baseTime,
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }
}
