//
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.sync

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.WritableMap
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.notification.NotificationChannels
import com.github.quarck.calnotify.ui.MyReactActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * React Native's native code doesn't load under Robolectric, so the three places the service
 * touches it are overridden here; everything else is the real service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = "AndroidManifest.xml", sdk = [34])
class SyncForegroundServiceRobolectricTest {

    class TestService : SyncForegroundService() {
        var tasksStarted = 0
        var refuseForeground = false

        override fun createTaskData(): WritableMap = JavaOnlyMap()

        override fun startHeadlessTask(intent: Intent?, flags: Int, startId: Int): Int {
            tasksStarted++
            return Service.START_REDELIVER_INTENT
        }

        override fun enterForeground() {
            if (refuseForeground) throw ForegroundServiceStartNotAllowedException("refused")
            super.enterForeground()
        }
    }

    private lateinit var context: Context
    private lateinit var service: TestService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = Robolectric.buildService(TestService::class.java).create().get()
    }

    @Test
    fun `task config names the JS task and allows running in the foreground`() {
        val config = service.getTaskConfig(null)

        assertEquals(SyncForegroundService.TASK_KEY, config.taskKey)
        assertEquals(SyncForegroundService.TASK_TIMEOUT_MS, config.timeout)
        assertTrue(config.isAllowedInForeground)
        assertFalse(config.data.getBoolean(SyncForegroundService.EXTRA_DEV_PAGE_FAKE_QUEUE))
    }

    @Test
    fun `task config passes the Dev page fake queue flag to the JS task`() {
        val intent = Intent().putExtra(SyncForegroundService.EXTRA_DEV_PAGE_FAKE_QUEUE, true)

        assertTrue(service.getTaskConfig(intent).data.getBoolean(SyncForegroundService.EXTRA_DEV_PAGE_FAKE_QUEUE))
    }

    @Test
    fun `notification is ongoing on the sync channel and opens Data Sync`() {
        val notification = SyncForegroundService.buildNotification(context)

        assertEquals(NotificationChannels.CHANNEL_ID_SYNC, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val tapIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MyReactActivity::class.java.name, tapIntent.component?.className)
    }

    @Test
    fun `start enters the foreground and starts one task`() {
        val result = service.onStartCommand(null, 0, 1)

        assertEquals(Service.START_REDELIVER_INTENT, result)
        assertEquals(Consts.NOTIFICATION_ID_SYNC, shadowOf(service).lastForegroundNotificationId)
        assertEquals(1, service.tasksStarted)
    }

    @Test
    fun `second start while running does not start a second task`() {
        service.onStartCommand(null, 0, 1)
        service.onStartCommand(null, 0, 2)

        assertEquals(1, service.tasksStarted)
    }

    @Test
    fun `refused foreground start stops the service without starting a task`() {
        service.refuseForeground = true

        val result = service.onStartCommand(null, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        assertEquals(0, service.tasksStarted)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
