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

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Helper class for testing AlarmManager interactions using Robolectric's ShadowAlarmManager.
 * 
 * ShadowAlarmManager provides better introspection capabilities than MockK mocks:
 * - Access to scheduled alarms
 * - Ability to verify alarm parameters
 * - Fire alarms programmatically
 * 
 * Usage:
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * class AlarmTest {
 *     private lateinit var alarmHelper: AlarmManagerTestHelper
 *     
 *     @Before
 *     fun setup() {
 *         alarmHelper = AlarmManagerTestHelper()
 *     }
 *     
 *     @Test
 *     fun testAlarmScheduling() {
 *         // Code that schedules an alarm...
 *         
 *         // Verify alarm was scheduled
 *         val nextAlarm = alarmHelper.getNextScheduledAlarm()
 *         assertNotNull(nextAlarm)
 *         assertEquals(expectedTime, nextAlarm?.triggerAtTime)
 *     }
 * }
 * ```
 * 
 * Note: For tests using MockContextProvider, the AlarmManager is already mocked.
 * This helper is for tests that want to use native Robolectric shadow behavior
 * without the additional MockK layer.
 */
class AlarmManagerTestHelper {
    
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val shadowAlarmManager: ShadowAlarmManager = shadowOf(alarmManager)
    
    /**
     * Gets the next scheduled alarm, if any.
     */
    fun getNextScheduledAlarm(): ShadowAlarmManager.ScheduledAlarm? {
        return shadowAlarmManager.nextScheduledAlarm
    }
    
    /**
     * Gets all scheduled alarms.
     */
    fun getAllScheduledAlarms(): List<ShadowAlarmManager.ScheduledAlarm> {
        return shadowAlarmManager.scheduledAlarms
    }
    
    /**
     * Checks if any alarm is scheduled.
     */
    fun hasScheduledAlarms(): Boolean {
        return shadowAlarmManager.scheduledAlarms.isNotEmpty()
    }
    
    /**
     * Gets the count of scheduled alarms.
     */
    fun getScheduledAlarmCount(): Int {
        return shadowAlarmManager.scheduledAlarms.size
    }
    
    /**
     * Gets an alarm scheduled for a specific time.
     */
    fun getAlarmAtTime(triggerTime: Long): ShadowAlarmManager.ScheduledAlarm? {
        return shadowAlarmManager.scheduledAlarms.find { it.triggerAtTime == triggerTime }
    }
    
    /**
     * Clears all scheduled alarms (useful in test setup/teardown).
     * Note: This uses reflection to clear internal state.
     */
    fun clearAllAlarms() {
        // Cancel all pending intents
        shadowAlarmManager.scheduledAlarms.forEach { alarm ->
            alarm.operation?.let { alarmManager.cancel(it) }
        }
    }
    
    /**
     * Verifies that an exact alarm was scheduled at the specified time.
     */
    fun assertExactAlarmScheduledAt(triggerTime: Long, message: String = "Expected exact alarm at $triggerTime") {
        val alarm = getAlarmAtTime(triggerTime)
        if (alarm == null) {
            val scheduled = getAllScheduledAlarms().map { it.triggerAtTime }
            throw AssertionError("$message\nScheduled alarms: $scheduled")
        }
    }
    
    /**
     * Verifies that no alarms are currently scheduled.
     */
    fun assertNoAlarmsScheduled(message: String = "Expected no alarms scheduled") {
        val alarms = getAllScheduledAlarms()
        if (alarms.isNotEmpty()) {
            val scheduled = alarms.map { it.triggerAtTime }
            throw AssertionError("$message\nFound scheduled alarms at: $scheduled")
        }
    }
    
    /**
     * Gets the underlying ShadowAlarmManager for advanced usage.
     */
    fun getShadow(): ShadowAlarmManager = shadowAlarmManager
    
    /**
     * Gets the AlarmManager instance.
     */
    fun getAlarmManager(): AlarmManager = alarmManager
}
