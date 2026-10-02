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

import android.content.Context
import android.content.SharedPreferences
import com.github.quarck.calnotify.R
import expo.modules.mymodule.MyModule

/** How a background sync ended */
enum class SyncOutcome { COMPLETE, FAILED, PAUSED }

/**
 * What the last background sync did, kept so the Data Sync screen can show it after the process
 * has been restarted. Shares its SharedPreferences file with MyModule: the JS task reports its
 * outcome through MyModule, and the Data Sync screen reads the last result back through it.
 */
class BackgroundSyncState(private val context: Context) {

    private val prefs = context.getSharedPreferences(MyModule.SYNC_PREFS_NAME, Context.MODE_PRIVATE)

    val lastError: String?
        get() = prefs.getString(MyModule.PREF_SYNC_LAST_ERROR, null)

    /** Uploads done so far out of those queued when the task started; zero total means not known yet */
    val progressDone: Int
        get() = prefs.getInt(MyModule.PREF_SYNC_PROGRESS_DONE, 0)

    val progressTotal: Int
        get() = prefs.getInt(MyModule.PREF_SYNC_PROGRESS_TOTAL, 0)

    // SharedPreferences only holds listeners weakly, so the reference is kept here
    private var progressListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Calls [onChange] whenever the JS task reports progress, until [stopWatchingProgress] */
    fun watchProgress(onChange: () -> Unit) {
        progressListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MyModule.PREF_SYNC_PROGRESS_DONE || key == MyModule.PREF_SYNC_PROGRESS_TOTAL) onChange()
        }.also { prefs.registerOnSharedPreferenceChangeListener(it) }
    }

    fun stopWatchingProgress() {
        progressListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        progressListener = null
    }

    /** Clears what the previous run's task reported, so it can't be mistaken for this run's */
    fun clearReported() =
        prefs.edit()
            .remove(MyModule.PREF_SYNC_REPORTED_OK)
            .remove(MyModule.PREF_SYNC_REPORTED_ERROR)
            .remove(MyModule.PREF_SYNC_PROGRESS_DONE)
            .remove(MyModule.PREF_SYNC_PROGRESS_TOTAL)
            .remove(MyModule.PREF_SYNC_QUEUED)
            .apply()

    /**
     * Records how the sync ended. The JS task reports complete or failed before it finishes, so a
     * finish with nothing reported means the native timeout ended it with uploads still queued.
     */
    fun recordResult(now: Long): SyncOutcome {
        val outcome = when {
            !prefs.contains(MyModule.PREF_SYNC_REPORTED_OK) -> SyncOutcome.PAUSED
            prefs.getBoolean(MyModule.PREF_SYNC_REPORTED_OK, false) -> SyncOutcome.COMPLETE
            else -> SyncOutcome.FAILED
        }
        val error = when (outcome) {
            SyncOutcome.COMPLETE -> null
            SyncOutcome.FAILED -> prefs.getString(MyModule.PREF_SYNC_REPORTED_ERROR, null)
            SyncOutcome.PAUSED -> pausedDetail(prefs.getInt(MyModule.PREF_SYNC_QUEUED, 0))
        }
        prefs.edit()
            .putLong(MyModule.PREF_SYNC_LAST_COMPLETED_AT, now)
            .putBoolean(MyModule.PREF_SYNC_LAST_COMPLETED_OK, outcome == SyncOutcome.COMPLETE)
            .putString(MyModule.PREF_SYNC_LAST_ERROR, error)
            .remove(MyModule.PREF_SYNC_REPORTED_OK)
            .remove(MyModule.PREF_SYNC_REPORTED_ERROR)
            .apply()
        return outcome
    }

    /** The queue count is only known if the task reported progress before it timed out */
    private fun pausedDetail(queued: Int): String =
        if (queued > 0) context.resources.getQuantityString(R.plurals.sync_result_paused_detail_count, queued, queued)
        else context.getString(R.string.sync_result_paused_detail)
}
