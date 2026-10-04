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
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.WritableMap
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.notification.NotificationChannels
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.testutils.TestTimeConstants
import com.github.quarck.calnotify.ui.MyReactActivity
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import expo.modules.mymodule.MyModule
import org.junit.After
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

    private val prefs
        get() = context.getSharedPreferences(MyModule.SYNC_PREFS_NAME, Context.MODE_PRIVATE)

    private val resultNotification: Notification?
        get() = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .getNotification(Consts.NOTIFICATION_ID_SYNC_RESULT)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = Robolectric.buildService(TestService::class.java).create().get()
        service.clock = CNPlusUnitTestClock(TestTimeConstants.STANDARD_TEST_TIME)
    }

    @After
    fun tearDown() {
        MyReactActivity.isResumed = false
    }

    /** What MyModule.reportBackgroundSyncOutcome writes when the JS task reports before finishing */
    private fun reportOutcome(ok: Boolean, error: String? = null) {
        prefs.edit()
            .putBoolean(MyModule.PREF_SYNC_REPORTED_OK, ok)
            .putString(MyModule.PREF_SYNC_REPORTED_ERROR, error)
            .commit()
    }

    private fun assertRecorded(ok: Boolean, error: String?) {
        assertEquals(TestTimeConstants.STANDARD_TEST_TIME, prefs.getLong(MyModule.PREF_SYNC_LAST_COMPLETED_AT, 0))
        assertEquals(ok, prefs.getBoolean(MyModule.PREF_SYNC_LAST_COMPLETED_OK, !ok))
        assertEquals(error, prefs.getString(MyModule.PREF_SYNC_LAST_ERROR, null))
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

    // === Terminal states ===

    @Test
    fun `reported complete is recorded and announced with a dismissable notification`() {
        service.onStartCommand(null, 0, 1)
        reportOutcome(ok = true)

        service.onHeadlessJsTaskFinish(1)

        assertRecorded(ok = true, error = null)
        val notification = resultNotification!!
        assertEquals(context.getString(R.string.sync_result_complete), shadowOf(notification).contentTitle)
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(NotificationChannels.CHANNEL_ID_SYNC, notification.channelId)
        assertEquals(MyReactActivity::class.java.name, shadowOf(notification.contentIntent).savedIntent.component?.className)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `complete is recorded but not announced while the Data Sync screen is showing`() {
        MyReactActivity.isResumed = true
        service.onStartCommand(null, 0, 1)
        reportOutcome(ok = true)

        service.onHeadlessJsTaskFinish(1)

        assertRecorded(ok = true, error = null)
        assertNull(resultNotification)
    }

    @Test
    fun `reported failure is recorded and announced even while the Data Sync screen is showing`() {
        MyReactActivity.isResumed = true
        service.onStartCommand(null, 0, 1)
        reportOutcome(ok = false, error = "network down")

        service.onHeadlessJsTaskFinish(1)

        assertRecorded(ok = false, error = "network down")
        val notification = resultNotification!!
        assertEquals(context.getString(R.string.sync_result_failed), shadowOf(notification).contentTitle)
        assertEquals(NotificationChannels.CHANNEL_ID_SYNC_PROBLEMS, notification.channelId)
        assertEquals("network down", shadowOf(notification).contentText)
    }

    @Test
    fun `finish with nothing reported is a timeout and is recorded as paused`() {
        service.onStartCommand(null, 0, 1)

        service.onHeadlessJsTaskFinish(1)

        val pausedDetail = context.getString(R.string.sync_result_paused_detail)
        assertRecorded(ok = false, error = pausedDetail)
        val notification = resultNotification!!
        assertEquals(context.getString(R.string.sync_result_paused), shadowOf(notification).contentTitle)
        assertEquals(NotificationChannels.CHANNEL_ID_SYNC_PROBLEMS, notification.channelId)
        assertEquals(pausedDetail, shadowOf(notification).contentText)
    }

    @Test
    fun `starting a sync clears the previous run's report and result notification`() {
        service.onStartCommand(null, 0, 1)
        service.onHeadlessJsTaskFinish(1)
        assertNotNull(resultNotification)
        reportOutcome(ok = true)

        Robolectric.buildService(TestService::class.java).create().get().onStartCommand(null, 0, 1)

        assertNull(resultNotification)
        assertFalse(prefs.contains(MyModule.PREF_SYNC_REPORTED_OK))
    }

    // === Progress ===

    /** What MyModule.reportBackgroundSyncProgress writes as uploads go through */
    private fun reportProgress(done: Int, total: Int, queued: Int) {
        prefs.edit()
            .putInt(MyModule.PREF_SYNC_PROGRESS_DONE, done)
            .putInt(MyModule.PREF_SYNC_PROGRESS_TOTAL, total)
            .putInt(MyModule.PREF_SYNC_QUEUED, queued)
            .commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private val ongoingNotification: Notification
        get() = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .getNotification(Consts.NOTIFICATION_ID_SYNC) ?: shadowOf(service).lastForegroundNotification

    @Test
    fun `notification progress is indeterminate until the task reports a total`() {
        val notification = SyncForegroundService.buildNotification(context)

        assertTrue(shadowOf(notification).isIndeterminate)
        assertNull(shadowOf(notification).contentText)
    }

    @Test
    fun `notification shows determinate progress once a total is known`() {
        val notification = SyncForegroundService.buildNotification(context, done = 3, total = 10)

        assertFalse(shadowOf(notification).isIndeterminate)
        assertEquals(3, shadowOf(notification).progress)
        assertEquals(10, shadowOf(notification).max)
        assertEquals("3 of 10 uploaded", shadowOf(notification).contentText)
    }

    @Test
    fun `progress reported by the task updates the ongoing notification`() {
        service.onStartCommand(null, 0, 1)

        reportProgress(done = 3, total = 10, queued = 10)

        assertEquals(3, shadowOf(ongoingNotification).progress)
        assertEquals(10, shadowOf(ongoingNotification).max)
        assertTrue(ongoingNotification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `progress left over from the previous run is not shown when a sync starts`() {
        reportProgress(done = 9, total = 10, queued = 1)

        service.onStartCommand(null, 0, 1)

        assertTrue(shadowOf(shadowOf(service).lastForegroundNotification).isIndeterminate)
    }

    @Test
    fun `timeout records how many uploads were still queued`() {
        service.onStartCommand(null, 0, 1)
        reportProgress(done = 4, total = 10, queued = 6)

        service.onHeadlessJsTaskFinish(1)

        val detail = "Timed out with 6 uploads still queued. They resume the next time you sync."
        assertRecorded(ok = false, error = detail)
        assertEquals(detail, shadowOf(resultNotification!!).contentText)
    }

    @Test
    fun `progress reported after the task has finished does not bring the ongoing notification back`() {
        service.onStartCommand(null, 0, 1)
        service.onHeadlessJsTaskFinish(1)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()

        reportProgress(done = 5, total = 10, queued = 5)

        assertNull(shadowOf(manager).getNotification(Consts.NOTIFICATION_ID_SYNC))
    }
}
