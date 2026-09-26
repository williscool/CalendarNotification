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

package com.github.quarck.calnotify.testutils

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.broadcastreceivers.CalendarChangedBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.EventReminderBroadcastReceiver
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.RunListener

/**
 * Stops the OS delivering calendar-provider broadcasts to the app under test.
 *
 * Tests write to the emulator's real calendar provider, which answers with real
 * PROVIDER_CHANGED and EVENT_REMINDER broadcasts. Those start app code -- e.g.
 * CalendarMonitorService, after a 2s delay -- that can land after the test's
 * unmockkAll(), calling into a half-unmocked ApplicationController and crashing
 * the process with "MockKException: can't find stub". No test relies on real
 * delivery: they call onReceive / onProviderReminderBroadcast directly.
 *
 * Registered via the runner's `listener` argument (build.gradle and the
 * am instrument scripts). Debug builds share the release applicationId, so the
 * receivers are restored when the run finishes.
 */
class OsBroadcastBlockingListener : RunListener() {

    override fun testRunStarted(description: Description?) =
        setReceiversState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)

    override fun testRunFinished(result: Result?) =
        setReceiversState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

    private fun setReceiversState(state: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (receiver in listOf(CalendarChangedBroadcastReceiver::class.java, EventReminderBroadcastReceiver::class.java)) {
            // DONT_KILL_APP: the app process is the test process
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, receiver), state, PackageManager.DONT_KILL_APP)
        }
    }
}
