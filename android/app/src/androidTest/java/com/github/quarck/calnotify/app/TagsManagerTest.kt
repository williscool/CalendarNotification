package com.github.quarck.calnotify.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.AttendanceStatus
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.calendar.EventOrigin
import com.github.quarck.calnotify.calendar.EventStatus
import com.github.quarck.calnotify.logs.DevLog
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for TagsManager - event tag parsing logic
 */
@RunWith(AndroidJUnit4::class)
class TagsManagerTest {
    private val LOG_TAG = "TagsManagerTest"

    private lateinit var settings: Settings
    private lateinit var tagsManager: TagsManager

    @Before
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up TagsManagerTest")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        DevLog.info(LOG_TAG, "Running testMuteTagInTitle")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Meeting #mute")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is in title", event.isMuted)
    }

    @Test
    fun testMuteTagInDescription() {
        DevLog.info(LOG_TAG, "Running testMuteTagInDescription")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(desc = "Silent meeting #mute")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is in description", event.isMuted)
    }

    @Test
    fun testMuteTagCaseInsensitive() {
        DevLog.info(LOG_TAG, "Running testMuteTagCaseInsensitive")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Meeting #MUTE")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Mute tag should be case insensitive", event.isMuted)
    }

    @Test
    fun testNoMuteTagWhenPartOfWord() {
        DevLog.info(LOG_TAG, "Running testNoMuteTagWhenPartOfWord")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Commuted to work")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be muted when 'mute' is part of another word", event.isMuted)
    }

    @Test
    fun testNoMuteTag() {
        DevLog.info(LOG_TAG, "Running testNoMuteTag")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Regular meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be muted without #mute tag", event.isMuted)
    }

    // === #task tag tests ===

    @Test
    fun testTaskTagInTitle() {
        DevLog.info(LOG_TAG, "Running testTaskTagInTitle")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Complete report #task")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task when #task is in title", event.isTask)
    }

    @Test
    fun testTaskTagInDescription() {
        DevLog.info(LOG_TAG, "Running testTaskTagInDescription")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(desc = "This is a #task")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task when #task is in description", event.isTask)
    }

    @Test
    fun testNoTaskTag() {
        DevLog.info(LOG_TAG, "Running testNoTaskTag")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Regular meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be task without #task tag", event.isTask)
    }

    // === #alarm tag tests ===

    @Test
    fun testAlarmTagInTitle() {
        DevLog.info(LOG_TAG, "Running testAlarmTagInTitle")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Wake up #alarm")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be alarm when #alarm is in title", event.isAlarm)
    }

    @Test
    fun testNoAlarmTag() {
        DevLog.info(LOG_TAG, "Running testNoAlarmTag")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Regular meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be alarm without #alarm tag", event.isAlarm)
    }

    // === Multiple tags tests ===

    @Test
    fun testMultipleTags() {
        DevLog.info(LOG_TAG, "Running testMultipleTags")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "Important #task #mute #alarm")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task", event.isTask)
        assertTrue("Event should be muted", event.isMuted)
        assertTrue("Event should be alarm", event.isAlarm)
    }

    @Test
    fun testTagsInTitleAndDescription() {
        DevLog.info(LOG_TAG, "Running testTagsInTitleAndDescription")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "#task", desc = "#mute")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be task from title", event.isTask)
        assertTrue("Event should be muted from description", event.isMuted)
    }

    // === Edge cases ===

    @Test
    fun testEmptyTitleAndDescription() {
        DevLog.info(LOG_TAG, "Running testEmptyTitleAndDescription")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "", desc = "")
        tagsManager.parseEventTags(context, settings, event)
        assertFalse("Event should not be muted", event.isMuted)
        assertFalse("Event should not be task", event.isTask)
        assertFalse("Event should not be alarm", event.isAlarm)
    }

    @Test
    fun testTagAtStartOfString() {
        DevLog.info(LOG_TAG, "Running testTagAtStartOfString")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = createTestEvent(title = "#mute Meeting")
        tagsManager.parseEventTags(context, settings, event)
        assertTrue("Event should be muted when #mute is at start", event.isMuted)
    }
}

