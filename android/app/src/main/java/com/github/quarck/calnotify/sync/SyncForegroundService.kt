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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.notification.NotificationChannels
import com.github.quarck.calnotify.ui.MyReactActivity
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusSystemClock

/**
 * Keeps the data sync upload going after the Data Sync screen is left.
 *
 * React Native only fires JS timers in the background while a headless task is active, and the
 * upload depends on them. The foreground service keeps the process alive while that task runs.
 * See docs/dev_todo/background_data_sync.md.
 */
open class SyncForegroundService : HeadlessJsTaskService() {

    private var taskStarted = false

    internal var clock: CNPlusClockInterface = CNPlusSystemClock()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start has to enter the foreground, but only the first one starts a task
        if (!enterForegroundOrStop()) return START_NOT_STICKY
        if (taskStarted) return START_REDELIVER_INTENT
        taskStarted = true
        BackgroundSyncState(this).clearReportedOutcome()
        notificationManager.cancel(Consts.NOTIFICATION_ID_SYNC_RESULT)
        return startHeadlessTask(intent, flags, startId)
    }

    /**
     * Called when the JS task resolves and when the native timeout ends it. JS is not told about
     * a timeout, so the result is recorded and announced here rather than in the task.
     */
    override fun onHeadlessJsTaskFinish(taskId: Int) {
        val state = BackgroundSyncState(this)
        val outcome = state.recordResult(clock.currentTimeMillis())
        stopForeground(STOP_FOREGROUND_REMOVE)
        // The Data Sync screen already shows a completed sync while it is open
        if (outcome != SyncOutcome.COMPLETE || !MyReactActivity.isResumed) {
            notificationManager.notify(
                Consts.NOTIFICATION_ID_SYNC_RESULT,
                buildResultNotification(this, outcome, state.lastError)
            )
        }
        super.onHeadlessJsTaskFinish(taskId)
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * HeadlessJsTaskService calls this on every start to know which JS task to run. Both triggers
     * come through here: Full Resync's intent carries no extra, so the flag is false and the task
     * drains the real PowerSync queue; the Dev page button sets it to drain a fake queue instead.
     */
    public override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig {
        val data = createTaskData()
        data.putBoolean(EXTRA_DEV_PAGE_FAKE_QUEUE, intent?.getBooleanExtra(EXTRA_DEV_PAGE_FAKE_QUEUE, false) ?: false)
        return HeadlessJsTaskConfig(TASK_KEY, data, TASK_TIMEOUT_MS, true)
    }

    internal open fun createTaskData(): WritableMap = Arguments.createMap()

    internal open fun startHeadlessTask(intent: Intent?, flags: Int, startId: Int): Int =
        super.onStartCommand(intent, flags, startId)

    internal open fun enterForeground() {
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Consts.NOTIFICATION_ID_SYNC, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Consts.NOTIFICATION_ID_SYNC, notification)
        }
    }

    /**
     * A restart after a process kill comes from the background, where Android 12+ only allows a
     * foreground start for apps exempt from battery optimizations. Without the exemption, stop:
     * the upload queue is persisted and resumes on the next sync the user starts.
     */
    private fun enterForegroundOrStop(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterForeground()
            return true
        }
        return try {
            enterForeground()
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            DevLog.warn(LOG_TAG, "Foreground start refused, sync stays queued: ${e.message}")
            stopSelf()
            false
        }
    }

    companion object {
        private const val LOG_TAG = "SyncForegroundService"

        /** Must match the task registered in index.tsx */
        const val TASK_KEY = "CNPlusBackgroundSync"
        const val TASK_TIMEOUT_MS = 15 * Consts.MINUTE_IN_MILLISECONDS

        /** Dev page only: the task drains a fake queue instead of the real upload queue */
        const val EXTRA_DEV_PAGE_FAKE_QUEUE = "devPageFakeQueue"

        fun buildNotification(context: Context): Notification =
            notificationBuilder(context)
                .setContentTitle(context.getString(R.string.sync_notification_title))
                .setOngoing(true)
                .build()

        /** A completed sync stays on the silent sync channel; failed and paused ones go where they make a sound */
        fun buildResultNotification(context: Context, outcome: SyncOutcome, error: String?): Notification =
            notificationBuilder(
                context,
                if (outcome == SyncOutcome.COMPLETE) NotificationChannels.CHANNEL_ID_SYNC
                else NotificationChannels.CHANNEL_ID_SYNC_PROBLEMS
            )
                .setContentTitle(
                    context.getString(
                        when (outcome) {
                            SyncOutcome.COMPLETE -> R.string.sync_result_complete
                            SyncOutcome.FAILED -> R.string.sync_result_failed
                            SyncOutcome.PAUSED -> R.string.sync_result_paused
                        }
                    )
                )
                .setContentText(error)
                .setAutoCancel(true)
                .build()

        /** Calendar icon and a tap that opens Data Sync */
        private fun notificationBuilder(
            context: Context,
            channelId: String = NotificationChannels.CHANNEL_ID_SYNC
        ): NotificationCompat.Builder {
            val openDataSync = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MyReactActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.stat_notify_calendar)
                .setContentIntent(openDataSync)
        }
    }
}
