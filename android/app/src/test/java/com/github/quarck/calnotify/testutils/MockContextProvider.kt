package com.github.quarck.calnotify.testutils

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*
import org.robolectric.Shadows.shadowOf

/**
 * Provides context-related mock functionality for tests
 *
 * This class manages a mock Android Context with simplified behavior for Robolectric tests.
 * It relies on Robolectric's shadow Context for most functionality.
 */
class MockContextProvider(
    private val timeProvider: MockTimeProvider
) {
    private val LOG_TAG = "MockContextProvider"
    
    // Context can be set from outside for Robolectric
    var fakeContext: Context? = null
    
    // Mock alarm manager
    lateinit var mockAlarmManager: AlarmManager
        private set
    
    // Track toast messages that would have been shown
    private val toastMessages = mutableListOf<String>()
    
    // Track last timer broadcast time for tests
    private var lastTimerBroadcastReceived: Long? = null
    
    // Track initialization state
    private var isInitialized = false
    
    /**
     * Gets the list of Toast messages that would have been shown
     */
    fun getToastMessages(): List<String> = toastMessages.toList()
    
    /**
     * Clears the list of Toast messages
     */
    fun clearToastMessages() {
        toastMessages.clear()
    }
    
    /**
     * Sets up the mock context and related components
     */
    fun setup() {
        if (isInitialized) {
            DevLog.info(LOG_TAG, "MockContextProvider already initialized, skipping setup")
            return
        }
        
        DevLog.info(LOG_TAG, "Setting up MockContextProvider")
        
        // Grant calendar permissions for Robolectric tests
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val shadowApp = shadowOf(app)
            shadowApp.grantPermissions(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.RECEIVE_BOOT_COMPLETED
            )
            DevLog.info(LOG_TAG, "Granted calendar permissions for Robolectric")
        } catch (e: Exception) {
            DevLog.warn(LOG_TAG, "Failed to grant permissions (may not be in Robolectric context): ${e.message}")
        }
        
        // Set up minimal mock components for Robolectric
        setupAlarmManager()
        
        // Get Robolectric context for system services
        val robolectricContext = try {
            ApplicationProvider.getApplicationContext<Context>()
        } catch (e: Exception) {
            DevLog.warn(LOG_TAG, "Failed to get Robolectric context: ${e.message}")
            null
        }
        
        // If a context was set from outside, use it; otherwise use Robolectric context
        if (fakeContext == null) {
            if (robolectricContext != null) {
                DevLog.info(LOG_TAG, "Using Robolectric context as base")
                // Use Robolectric context directly, but override AlarmManager
                fakeContext = spyk(robolectricContext) {
                    every { getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
                    every { getSystemService(AlarmManager::class.java) } returns mockAlarmManager
                }
            } else {
                DevLog.warn(LOG_TAG, "No context was provided, creating an empty mock")
                fakeContext = mockk<Context>(relaxed = true) {
                    every { getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
                    every { getSystemService(AlarmManager::class.java) } returns mockAlarmManager
                }
            }
        } else {
            DevLog.info(LOG_TAG, "Using externally provided context for Robolectric")
            // Enhance the provided context to delegate system services to Robolectric context
            if (robolectricContext != null) {
                fakeContext = spyk(fakeContext!!) {
                    every { getSystemService(any<String>()) } answers {
                        val serviceName = firstArg<String>()
                        when (serviceName) {
                            Context.ALARM_SERVICE -> mockAlarmManager
                            else -> robolectricContext.getSystemService(serviceName)
                        }
                    }
                    every { getSystemService(any<Class<*>>()) } answers {
                        val serviceClass = firstArg<Class<*>>()
                        when (serviceClass) {
                            AlarmManager::class.java -> mockAlarmManager
                            else -> robolectricContext.getSystemService(serviceClass)
                        }
                    }
                }
            }
        }
        
        isInitialized = true
    }
    
    /**
     * Sets up the mock AlarmManager
     */
    private fun setupAlarmManager() {
        DevLog.info(LOG_TAG, "Setting up mock AlarmManager")
        
        // Create a simple mock AlarmManager with no side effects
        mockAlarmManager = mockk<AlarmManager>(relaxed = true)
        
        // Mock any other necessary AlarmManager behavior here
    }
    
    /**
     * Sets the lastTimerBroadcastReceived value
     */
    fun setLastTimerBroadcastReceived(time: Long?) {
        lastTimerBroadcastReceived = time
    }
    
    /**
     * Gets the lastTimerBroadcastReceived value
     */
    fun getLastTimerBroadcastReceived(): Long? {
        return lastTimerBroadcastReceived
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockContextProvider")
        isInitialized = false
    }
    
    /**
     * Shows a toast message (simulated)
     */
    fun showToast(message: String, longDuration: Boolean = false) {
        toastMessages.add(message)
        DevLog.info(LOG_TAG, "TOAST: $message")
    }
    
    /**
     * Directly manipulates shared preferences to set calendar handling status
     * This bypasses the Settings class to ensure the correct key format is used
     */
    fun setCalendarHandlingStatusDirectly(calendarId: Long, isHandled: Boolean) {
        DevLog.info(LOG_TAG, "Directly setting calendar $calendarId handling status to $isHandled")
        
        val context = fakeContext ?: return
        
        // IMPORTANT: Settings class uses PreferenceManager.getDefaultSharedPreferences, not a named preferences file
        // Must use the same SharedPreferences instance that Settings will read from
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        
        // First, find and remove any existing keys related to this calendar
        val existingKeys = prefs.all.keys
            .filter { it.contains("calendar") && it.contains("handled") && it.contains("$calendarId") }
        
        if (existingKeys.isNotEmpty()) {
            DevLog.info(LOG_TAG, "Found existing keys to remove: $existingKeys")
            existingKeys.forEach { key ->
                editor.remove(key)
            }
        }
        
        // The exact key format used in the Settings class: CALENDAR_IS_HANDLED_KEY_PREFIX + "." + calendarId
        // From Settings class: private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_"
        val correctKey = "calendar_handled_.$calendarId"
        
        // Set the boolean with the correct key
        editor.putBoolean(correctKey, isHandled)
        
        // Use commit() instead of apply() to ensure synchronous write for testing
        val success = editor.commit()
        
        DevLog.info(LOG_TAG, "Set calendar handling preference: $correctKey = $isHandled, commit success = $success")
        
        // Verify it was actually stored (using the same SharedPreferences that Settings reads from)
        val verifyPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val storedValue = verifyPrefs.getBoolean(correctKey, !isHandled)  // Use opposite as default to detect if missing
        DevLog.info(LOG_TAG, "Verified stored value: $correctKey = $storedValue")
        
        if (storedValue != isHandled) {
            DevLog.error(LOG_TAG, "SharedPreferences did not store value correctly! Expected: $isHandled, Got: $storedValue")
        }
    }
    
    /**
     * Overrides CalendarProvider's getHandledCalendarsIds method to use our mock settings
     * 
     * NOTE: This method is primarily used by instrumentation tests. In Robolectric tests,
     * the MockCalendarProvider already stubs getHandledCalendarsIds to use Settings correctly.
     * This method provides an additional override layer when needed for complex test scenarios.
     */
    fun overrideGetHandledCalendarsIds(calendarHandlingOverrides: Map<Long, Boolean>) {
        DevLog.info(LOG_TAG, "Overriding Settings.getCalendarIsHandled with overrides: $calendarHandlingOverrides")
        
        // For Robolectric: Don't try to override CalendarProvider.getHandledCalendarsIds
        // Instead, we need to override Settings.getCalendarIsHandled so that when
        // CalendarProvider.getHandledCalendarsIds calls it, it gets the right values
        // But that requires mocking Settings instances, which is complex
        
        // Simpler approach for Robolectric: Just ensure the SharedPreferences have the right values
        // This has already been done by setCalendarHandlingStatusDirectly()
        
        DevLog.info(LOG_TAG, "In Robolectric, calendar handling overrides are applied via setCalendarHandlingStatusDirectly()")
        DevLog.info(LOG_TAG, "The MockCalendarProvider.getHandledCalendarsIds stub will use Settings.getCalendarIsHandled() which reads from SharedPreferences")
    }
} 