//
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//

package com.github.quarck.calnotify.ui

import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [28])
class PreActionActivityTest {

    private lateinit var activityController: ActivityController<PreActionActivity>
    private val testClock = CNPlusUnitTestClock(currentTimeMs = 1700000000000L)
    
    private val testEvent = EventAlertRecord(
        calendarId = 1L,
        eventId = 100L,
        isAllDay = false,
        isRepeating = false,
        alertTime = 1700000000000L,
        notificationId = 0,
        title = "Test Meeting",
        desc = "Meeting description with #mute tag",
        startTime = 1700003600000L,  // 1 hour after alert
        endTime = 1700007200000L,    // 2 hours after alert
        instanceStartTime = 1700003600000L,
        instanceEndTime = 1700007200000L,
        location = "Room 101",
        lastStatusChangeTime = 0L,
        snoozedUntil = 0L,
        color = 0xFF0000
    )

    @Before
    fun setup() {
        PreActionActivity.clockProvider = { testClock }
    }

    @After
    fun teardown() {
        PreActionActivity.resetProviders()
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
    }

    private fun launchActivity(event: EventAlertRecord = testEvent): PreActionActivity {
        val intent = PreActionActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            event
        )
        activityController = Robolectric.buildActivity(PreActionActivity::class.java, intent)
        return activityController.create().start().resume().get()
    }

    @Test
    fun `activity displays event title`() {
        val activity = launchActivity()
        
        val titleView = activity.findViewById<TextView>(R.id.pre_action_title)
        assertEquals("Test Meeting", titleView.text)
    }

    @Test
    fun `activity displays empty title placeholder when title is blank`() {
        val eventWithNoTitle = testEvent.copy(title = "")
        val activity = launchActivity(eventWithNoTitle)
        
        val titleView = activity.findViewById<TextView>(R.id.pre_action_title)
        assertEquals(activity.getString(R.string.empty_title), titleView.text)
    }

    @Test
    fun `activity displays snooze presets`() {
        val activity = launchActivity()
        
        val container = activity.findViewById<LinearLayout>(R.id.pre_action_presets_container)
        assertTrue("Should have snooze presets", container.childCount > 0)
    }

    @Test
    fun `mute button shows correct text for non-muted event`() {
        val activity = launchActivity()
        
        val muteButton = activity.findViewById<TextView>(R.id.pre_action_mute_toggle)
        assertEquals(activity.getString(R.string.pre_mute), muteButton.text)
    }

    @Test
    fun `mute button shows unmute text for pre-muted event`() {
        val mutedEvent = testEvent.copy(
            flags = com.github.quarck.calnotify.calendar.EventAlertFlags.IS_MUTED
        )
        val activity = launchActivity(mutedEvent)
        
        val muteButton = activity.findViewById<TextView>(R.id.pre_action_mute_toggle)
        assertEquals(activity.getString(R.string.pre_unmute), muteButton.text)
    }

    @Test
    fun `back button finishes activity`() {
        val activity = launchActivity()
        
        activity.findViewById<android.view.View>(R.id.btn_back).performClick()
        
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `activity finishes with invalid event data`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PreActionActivity::class.java)
        // Don't put any event data
        activityController = Robolectric.buildActivity(PreActionActivity::class.java, intent)
        val activity = activityController.create().get()
        
        assertTrue("Activity should finish with invalid data", activity.isFinishing)
    }

    @Test
    fun `createIntent includes all required event data`() {
        val intent = PreActionActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            testEvent
        )
        
        assertEquals(testEvent.eventId, intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1))
        assertEquals(testEvent.instanceStartTime, intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1))
        assertEquals(testEvent.instanceEndTime, intent.getLongExtra(Consts.INTENT_EVENT_INSTANCE_END_TIME_KEY, -1))
        assertEquals(testEvent.alertTime, intent.getLongExtra(Consts.INTENT_ALERT_TIME_KEY, -1))
        assertEquals(testEvent.title, intent.getStringExtra(Consts.INTENT_EVENT_TITLE_KEY))
        assertEquals(testEvent.startTime, intent.getLongExtra(Consts.INTENT_EVENT_START_TIME_KEY, -1))
        assertEquals(testEvent.endTime, intent.getLongExtra(Consts.INTENT_EVENT_END_TIME_KEY, -1))
        assertEquals(testEvent.isAllDay, intent.getBooleanExtra(Consts.INTENT_EVENT_ALL_DAY_KEY, true))
        assertEquals(testEvent.isRepeating, intent.getBooleanExtra(Consts.INTENT_EVENT_IS_REPEATING_KEY, true))
        assertEquals(testEvent.color, intent.getIntExtra(Consts.INTENT_EVENT_COLOR_KEY, -1))
        assertEquals(testEvent.desc, intent.getStringExtra(Consts.INTENT_EVENT_DESC_KEY))
    }

    @Test
    fun `dismiss button is present`() {
        val activity = launchActivity()
        
        val dismissButton = activity.findViewById<TextView>(R.id.pre_action_dismiss)
        assertNotNull(dismissButton)
        assertEquals(activity.getString(R.string.pre_dismiss), dismissButton.text)
    }
}
