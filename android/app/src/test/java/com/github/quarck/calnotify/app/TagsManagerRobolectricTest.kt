package com.github.quarck.calnotify.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for TagsManager - event tag parsing logic
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [24])
class TagsManagerRobolectricTest {

    private lateinit var context: Context
    private lateinit var settings: Settings
    private lateinit var tagsManager: TagsManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settings = Settings(context)
        tagsManager = TagsManager()
    }

    private fun createTestEvent(
        title: String = "Test Event",
        desc: String = "Test Description"
    ): EventAlertRecord {
        return EventAlertRecord(
            calendarId = 1L,
            eventId = 1L,
            isAllDay = false,
            isRepeating = false,
            alertTime = System.currentTimeMillis(),
            notificationId = 0,
            title = title,
            desc = desc,
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 3600000,
            instanceStartTime = System.currentTimeMillis(),
            instanceEndTime = System.currentTimeMillis() + 3600000,
            location = "",
            lastStatusChangeTime = System.currentTimeMillis(),
            snoozedUntil = 0L,
            displayStatus = EventDisplayStatus.Hidden,
            color = 0,
            origin = EventOrigin.ProviderBroadcast,
            timeFirstSeen = System.currentTimeMillis(),
            eventStatus = EventStatus.Confirmed,
            attendanceStatus = AttendanceStatus.None,
            flags = 0
        )
    }

    // === #mute tag tests ===

    @Test
    fun testMuteTagInTitle() {
        val event = createTestEvent(title = "Meeting #mute")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is in title", event.isMuted)
    }

    @Test
    fun testMuteTagInDescription() {
        val event = createTestEvent(desc = "Silent meeting #mute")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is in description", event.isMuted)
    }

    @Test
    fun testMuteTagCaseInsensitive() {
        val event = createTestEvent(title = "Meeting #MUTE")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Mute tag should be case insensitive", event.isMuted)
    }

    @Test
    fun testMuteTagInMiddleOfTitle() {
        val event = createTestEvent(title = "Meeting #mute today")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is in middle of title", event.isMuted)
    }

    @Test
    fun testNoMuteTagWhenPartOfWord() {
        val event = createTestEvent(title = "Commuted to work")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be muted when 'mute' is part of another word", event.isMuted)
    }

    @Test
    fun testNoMuteTag() {
        val event = createTestEvent(title = "Regular meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be muted without #mute tag", event.isMuted)
    }

    // === #task tag tests ===

    @Test
    fun testTaskTagInTitle() {
        val event = createTestEvent(title = "Complete report #task")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task when #task is in title", event.isTask)
    }

    @Test
    fun testTaskTagInDescription() {
        val event = createTestEvent(desc = "This is a #task")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task when #task is in description", event.isTask)
    }

    @Test
    fun testTaskTagCaseInsensitive() {
        val event = createTestEvent(title = "Do something #TASK")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Task tag should be case insensitive", event.isTask)
    }

    @Test
    fun testNoTaskTag() {
        val event = createTestEvent(title = "Regular meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be task without #task tag", event.isTask)
    }

    // === #alarm tag tests ===

    @Test
    fun testAlarmTagInTitle() {
        val event = createTestEvent(title = "Wake up #alarm")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be alarm when #alarm is in title", event.isAlarm)
    }

    @Test
    fun testAlarmTagInDescription() {
        val event = createTestEvent(desc = "Important #alarm reminder")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be alarm when #alarm is in description", event.isAlarm)
    }

    @Test
    fun testNoAlarmTag() {
        val event = createTestEvent(title = "Regular meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be alarm without #alarm tag", event.isAlarm)
    }

    // === Multiple tags tests ===

    @Test
    fun testMultipleTags() {
        val event = createTestEvent(title = "Important #task #mute #alarm")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task", event.isTask)
        assertTrue("Event should be muted", event.isMuted)
        assertTrue("Event should be alarm", event.isAlarm)
    }

    @Test
    fun testTagsInTitleAndDescription() {
        val event = createTestEvent(title = "#task", desc = "#mute")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task from title", event.isTask)
        assertTrue("Event should be muted from description", event.isMuted)
    }

    // === Edge cases ===

    @Test
    fun testEmptyTitleAndDescription() {
        val event = createTestEvent(title = "", desc = "")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be muted", event.isMuted)
        assertFalse("Event should not be task", event.isTask)
        assertFalse("Event should not be alarm", event.isAlarm)
    }

    @Test
    fun testTagAtStartOfString() {
        val event = createTestEvent(title = "#mute Meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is at start", event.isMuted)
    }

    @Test
    fun testTagFollowedByPunctuation() {
        val event = createTestEvent(title = "#mute, Meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is followed by punctuation", event.isMuted)
    }
}

