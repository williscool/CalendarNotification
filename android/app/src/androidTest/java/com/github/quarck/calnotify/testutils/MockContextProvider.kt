package com.github.quarck.calnotify.testutils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.VersionedPackage
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorService
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import io.mockk.*

/**
 * Provides context-related mock functionality for tests
 *
 * This class creates and manages a mock Android Context and related
 * components like SharedPreferences and system services.
 */
class MockContextProvider(
    private val timeProvider: MockTimeProvider
) {
    private val LOG_TAG = "MockContextProvider"
    
    // Core components
    lateinit var fakeContext: Context
        private set
        
    lateinit var mockAlarmManager: AlarmManager
        private set
        
    private val sharedPreferencesMap = mutableMapOf<String, SharedPreferences>()
    private val sharedPreferencesDataMap = mutableMapOf<String, MutableMap<String, Any>>()
    
    // Track Toast messages that would have been shown
    private val toastMessages = mutableListOf<String>()
    
    // Service is created once and reused
    lateinit var mockService: CalendarMonitorService
        private set
    
    // Track last timer broadcast time for tests (used by globalState)
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
        
        // Get real context for delegating operations
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Set up dependencies in the correct order
        setupPendingIntent()
        setupAlarmManager()
        setupContext(realContext)
        setupMockService()
        
        isInitialized = true
    }
    
    /**
     * Mocks PendingIntent.getBroadcast for AlarmManager functionality
     */
    private fun setupPendingIntent() {
        DevLog.info(LOG_TAG, "Setting up PendingIntent mocks")
        
        mockkStatic(PendingIntent::class)
        every { 
            PendingIntent.getBroadcast(any(), any(), any(), any()) 
        } returns mockk(relaxed = true)
    }
    
    /**
     * Sets up the mock AlarmManager
     */
    private fun setupAlarmManager() {
        DevLog.info(LOG_TAG, "Setting up mock AlarmManager")
        
        // Mock static extension function used with AlarmManager
        mockkStatic("com.github.quarck.calnotify.utils.SystemUtilsKt")
        
        // Create a simple mock AlarmManager with no side effects
        mockAlarmManager = mockk<AlarmManager>(relaxed = true) {
            every { setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) } just Runs
            every { setExact(any(), any(), any<PendingIntent>()) } just Runs
            every { setAlarmClock(any<AlarmManager.AlarmClockInfo>(), any<PendingIntent>()) } just Runs
            every { set(any(), any(), any<PendingIntent>()) } just Runs
            every { setInexactRepeating(any(), any(), any(), any<PendingIntent>()) } just Runs
            every { cancel(any<PendingIntent>()) } just Runs
        }
        
        // Mock the extension function with direct behavior
        every {
            mockAlarmManager.cancelExactAndAlarm(any(), any(), any())
        } just Runs
    }
    
    /**
     * Creates a mock Android Context
     */
    private fun setupContext(realContext: Context) {
        DevLog.info(LOG_TAG, "Setting up mock context")

        // Mock Toast static methods
        mockkStatic(android.widget.Toast::class)
        every { 
            android.widget.Toast.makeText(any(), any<String>(), any()) 
        } answers {
            val message = secondArg<String>()
            toastMessages.add(message)
            DevLog.info(LOG_TAG, "Mock Toast would have shown: $message")
            mockk<android.widget.Toast>(relaxed = true) {
                every { show() } just Runs
            }
        }
        
        // Create mock package manager with enhanced functionality
        val mockPackageManager = mockk<android.content.pm.PackageManager> {
            every { resolveActivity(any(), any<Int>()) } answers {
                val intent = firstArg<Intent>()
                val flags = secondArg<Int>()
                realContext.packageManager.resolveActivity(intent, flags)
            }
            every { queryIntentActivities(any(), any<Int>()) } answers {
                val intent = firstArg<Intent>()
                val flags = secondArg<Int>()
                realContext.packageManager.queryIntentActivities(intent, flags)
            }
            every { getApplicationInfo(any<String>(), any<Int>()) } answers {
                val packageName = firstArg<String>()
                val flags = secondArg<Int>()
                realContext.packageManager.getApplicationInfo(packageName, flags)
            }
            @Suppress("DEPRECATION")
            every { getApplicationInfo(any<String>(), any<android.content.pm.PackageManager.ApplicationInfoFlags>()) } answers {
                val packageName = firstArg<String>()
                val flags = secondArg<android.content.pm.PackageManager.ApplicationInfoFlags>()
                realContext.packageManager.getApplicationInfo(packageName, flags)
            }
            every { getActivityInfo(any<ComponentName>(), any<Int>()) } answers {
                val component = firstArg<ComponentName>()
                val flags = secondArg<Int>()
                realContext.packageManager.getActivityInfo(component, flags)
            }
            @Suppress("DEPRECATION")
            every { getActivityInfo(any<ComponentName>(), any<android.content.pm.PackageManager.ComponentInfoFlags>()) } answers {
                val component = firstArg<ComponentName>()
                val flags = secondArg<android.content.pm.PackageManager.ComponentInfoFlags>()
                realContext.packageManager.getActivityInfo(component, flags)
            }
            every { getPackageInfo(any<String>(), any<Int>()) } answers {
                val packageName = firstArg<String>()
                val flags = secondArg<Int>()
                realContext.packageManager.getPackageInfo(packageName, flags)
            }
            @Suppress("DEPRECATION")
            every { getPackageInfo(any<String>(), any<android.content.pm.PackageManager.PackageInfoFlags>()) } answers {
                val packageName = firstArg<String>()
                val flags = secondArg<android.content.pm.PackageManager.PackageInfoFlags>()
                realContext.packageManager.getPackageInfo(packageName, flags)
            }
            every { getPackageInfo(any<VersionedPackage>(), any<Int>()) } answers {
                val versionedPackage = firstArg<VersionedPackage>()
                val flags = secondArg<Int>()
                realContext.packageManager.getPackageInfo(versionedPackage, flags)
            }
            @Suppress("DEPRECATION")
            every { getPackageInfo(any<VersionedPackage>(), any<android.content.pm.PackageManager.PackageInfoFlags>()) } answers {
                val versionedPackage = firstArg<VersionedPackage>()
                val flags = secondArg<android.content.pm.PackageManager.PackageInfoFlags>()
                realContext.packageManager.getPackageInfo(versionedPackage, flags)
            }
        }
        
        // Create a proper mock of Resources with non-null Configuration
        val mockConfiguration = Configuration().apply {
            setToDefaults()
        }
        
        // Create a robust Resources mock that always returns a valid Configuration
        val mockResources = mockk<Resources>(relaxed = true) {
            every { getConfiguration() } returns mockConfiguration
            every { configuration } returns mockConfiguration
            every { displayMetrics } returns realContext.resources.displayMetrics
        }
        
        // Create a mock context with minimal implementation that delegates when possible
        fakeContext = mockk<Context>(relaxed = true) {
            every { packageName } returns realContext.packageName
            every { packageManager } returns mockPackageManager
            every { applicationContext } returns this@mockk
            every { contentResolver } returns realContext.contentResolver
            every { getDatabasePath(any()) } answers { realContext.getDatabasePath(firstArg()) }
            every { getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
            every { getSystemService(Context.POWER_SERVICE) } returns realContext.getSystemService(Context.POWER_SERVICE)
            every { getSystemService(any<String>()) } answers {
                when (firstArg<String>()) {
                    Context.ALARM_SERVICE -> mockAlarmManager
                    Context.POWER_SERVICE -> realContext.getSystemService(Context.POWER_SERVICE)
                    else -> realContext.getSystemService(firstArg())
                }
            }
            every { checkPermission(any(), any(), any()) } answers { 
                realContext.checkPermission(firstArg(), secondArg(), thirdArg()) 
            }
            every { checkCallingOrSelfPermission(any()) } answers { 
                realContext.checkCallingOrSelfPermission(firstArg()) 
            }
            every { createPackageContext(any(), any()) } answers { 
                realContext.createPackageContext(firstArg(), secondArg()) 
            }
            every { getSharedPreferences(any(), any()) } answers {
                val name = firstArg<String>()
                sharedPreferencesMap.getOrPut(name) { createPersistentSharedPreferences(name) }
            }
            every { getApplicationInfo() } returns realContext.applicationInfo
            every { getFilesDir() } returns realContext.filesDir
            every { getCacheDir() } returns realContext.cacheDir
            every { getDir(any(), any()) } answers { realContext.getDir(firstArg(), secondArg()) }
            every { startService(any()) } answers {
                val intent = firstArg<Intent>()
                DevLog.info(LOG_TAG, "Mock context startService with intent: action=${intent.action}, extras=${intent.extras}")
                
                // Explicitly don't call mockService.handleIntentForTest here to prevent any possible recursion
                ComponentName(realContext.packageName, CalendarMonitorService::class.java.name)
            }
            every { startActivity(any()) } just Runs
            
            // Replace delegation to real resources with our own mock
            every { getResources() } returns mockResources
            every { resources } returns mockResources
            
            every { getTheme() } returns realContext.theme
        }

        // Mock the globalState extension property with safer implementation
        mockkStatic("com.github.quarck.calnotify.GlobalStateKt")
        every { any<Context>().globalState } answers {
            mockk {
                // Never return null from lastTimerBroadcastReceived to prevent NPEs
                every { lastTimerBroadcastReceived } returns (this@MockContextProvider.lastTimerBroadcastReceived ?: System.currentTimeMillis())
                
                // Mock all other potential globalState properties to prevent missing property errors
                every { lastNotificationRePost } returns System.currentTimeMillis() 
                
                every { lastTimerBroadcastReceived = any() } answers {
                    this@MockContextProvider.lastTimerBroadcastReceived = firstArg()
                }
            }
        }
    }
    
    /**
     * Creates a mock service instance
     */
    private fun setupMockService() {
        DevLog.info(LOG_TAG, "Setting up mock service with MAXIMUM isolation")
        
        mockService = mockk<CalendarMonitorService>(relaxed = true) {
            // Don't use spyk which might call real implementations
            every { applicationContext } returns fakeContext
            every { baseContext } returns fakeContext
            every { clock } returns timeProvider.testClock
            
//            // Mock all methods to prevent any real code execution
//            every { onHandleIntent(any()) } just Runs
        }
        
        // Mock handleIntentForTest to just log without executing actual intent handling logic
        every { mockService.handleIntentForTest(any()) } answers {
            val intent = firstArg<Intent>()
            DevLog.info(LOG_TAG, "Mock service received intent: action=${intent.action}, extras=${intent.extras}")
            // Explicitly don't call original to avoid recursion
        }
    }
    
    /**
     * Creates a mock SharedPreferences implementation that maintains state
     */
    private fun createPersistentSharedPreferences(name: String): SharedPreferences {
        val sharedPrefsMap = sharedPreferencesDataMap.getOrPut(name) { mutableMapOf() }
        return mockk<SharedPreferences>(relaxed = true) {
            every { edit() } returns mockk<SharedPreferences.Editor>(relaxed = true) {
                every { putString(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<String>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putBoolean(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Boolean>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putInt(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Int>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putLong(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Long>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putFloat(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Float>()
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { remove(any()) } answers {
                    val key = firstArg<String>()
                    sharedPrefsMap.remove(key)
                    this@mockk
                }
                every { clear() } answers {
                    sharedPrefsMap.clear()
                    this@mockk
                }
                every { apply() } just Runs
                every { commit() } returns true
            }
            every { getString(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<String>()
                sharedPrefsMap[key] as? String ?: defaultValue
            }
            every { getBoolean(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Boolean>()
                // Log the actual keys being accessed for debugging
                if (key.contains("calendar") && key.contains("handled")) {
                    DevLog.info(LOG_TAG, "SharedPreferences ACCESSED key: '$key' with default: $defaultValue")
                }
                sharedPrefsMap[key] as? Boolean ?: defaultValue
            }
            every { getInt(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Int>()
                sharedPrefsMap[key] as? Int ?: defaultValue
            }
            every { getLong(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Long>()
                sharedPrefsMap[key] as? Long ?: defaultValue
            }
            every { getFloat(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Float>()
                sharedPrefsMap[key] as? Float ?: defaultValue
            }
            every { contains(any()) } answers {
                val key = firstArg<String>()
                sharedPrefsMap.containsKey(key)
            }
            every { getAll() } returns sharedPrefsMap
        }
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
        sharedPreferencesMap.clear()
        sharedPreferencesDataMap.clear()
        lastTimerBroadcastReceived = null
        isInitialized = false
    }
    
    /**
     * Directly manipulates shared preferences to set calendar handling status
     * This bypasses the Settings class to ensure the correct key format is used
     */
    fun setCalendarHandlingStatusDirectly(calendarId: Long, isHandled: Boolean) {
        DevLog.info(LOG_TAG, "Directly setting calendar $calendarId handling status to $isHandled")
        
        // Get the preferences editor
        val prefs = fakeContext.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
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
        
        // Apply the changes
        editor.apply()
        
        // Validate the setting took effect
        val settings = com.github.quarck.calnotify.Settings(fakeContext)
        val actualHandled = settings.getCalendarIsHandled(calendarId)
        
        DevLog.info(LOG_TAG, "After setting key: $correctKey = $isHandled")
        DevLog.info(LOG_TAG, "Settings.getCalendarIsHandled returns: $actualHandled (should be $isHandled)")
        
        // Check if the setting didn't take effect as expected
        if (actualHandled != isHandled) {
            DevLog.warn(LOG_TAG, "Failed to set calendar handling status directly. Trying via Settings class.")
            
            // Try to use the setCalendarIsHandled method directly via reflection
            try {
                val settingsClass = com.github.quarck.calnotify.Settings::class.java
                val method = settingsClass.getDeclaredMethod("setCalendarIsHandled", Long::class.java, Boolean::class.java)
                method.invoke(settings, calendarId, isHandled)
                
                val updatedHandled = settings.getCalendarIsHandled(calendarId)
                DevLog.info(LOG_TAG, "After using reflection: Settings.getCalendarIsHandled returns: $updatedHandled")
            } catch (e: Exception) {
                DevLog.error(LOG_TAG, "Failed to use reflection on Settings: ${e.message}")
            }
        }
        
        // Double check the preference value was stored correctly
        val updatedPrefs = fakeContext.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        val storedValue = updatedPrefs.getBoolean(correctKey, !isHandled)
        DevLog.info(LOG_TAG, "Preference key '$correctKey' = $storedValue")
    }
    
    /**
     * Creates and returns a specialized mock of the Settings class
     * This allows direct overriding of calendar handling checks
     */
    fun createMockSettingsWithOverrides(calendarHandlingOverrides: Map<Long, Boolean>): com.github.quarck.calnotify.Settings {
        DevLog.info(LOG_TAG, "Creating mock Settings with calendar handling overrides: $calendarHandlingOverrides")
        
        // Create a partial spy on a real Settings object
        val realSettings = com.github.quarck.calnotify.Settings(fakeContext)
        val mockSettings = spyk(realSettings)
        
        // Override getCalendarIsHandled to use our overrides map
        every { mockSettings.getCalendarIsHandled(any()) } answers {
            val calendarId = firstArg<Long>()
            
            // If we have a specific override for this calendar ID, use that
            if (calendarHandlingOverrides.containsKey(calendarId)) {
                val overrideValue = calendarHandlingOverrides[calendarId] ?: true
                DevLog.info(LOG_TAG, "MOCK Settings.getCalendarIsHandled($calendarId) = $overrideValue (OVERRIDDEN)")
                overrideValue
            } else {
                // Otherwise delegate to the real implementation
                val originalResult = realSettings.getCalendarIsHandled(calendarId)
                DevLog.info(LOG_TAG, "MOCK Settings.getCalendarIsHandled($calendarId) = $originalResult (DEFAULT)")
                originalResult
            }
        }
        
        return mockSettings
    }
    
    /**
     * Overrides CalendarProvider's getHandledCalendarsIds method to use our mock settings
     */
    fun overrideGetHandledCalendarsIds(calendarHandlingOverrides: Map<Long, Boolean>) {
        DevLog.info(LOG_TAG, "Overriding CalendarProvider.getHandledCalendarsIds with direct implementation")
        
        mockkObject(com.github.quarck.calnotify.calendar.CalendarProvider)
        
        // Directly mock the getHandledCalendarsIds method to use our overrides
        every { 
            com.github.quarck.calnotify.calendar.CalendarProvider.getHandledCalendarsIds(any(), any()) 
        } answers {
            val context = firstArg<Context>()
            
            // Get all calendars
            val allCalendars = com.github.quarck.calnotify.calendar.CalendarProvider.getCalendars(context)
            
            // Filter calendars based directly on our overrides map
            val handledIds = allCalendars
                .filter { calendar ->
                    // If we have an explicit override for this calendar, use that
                    if (calendarHandlingOverrides.containsKey(calendar.calendarId)) {
                        val isHandled = calendarHandlingOverrides[calendar.calendarId] ?: true
                        DevLog.info(LOG_TAG, "Calendar ${calendar.calendarId} handling override: $isHandled")
                        isHandled
                    } else {
                        // Otherwise use the real settings
                        val settings = secondArg<com.github.quarck.calnotify.Settings>()
                        val isHandled = settings.getCalendarIsHandled(calendar.calendarId)
                        DevLog.info(LOG_TAG, "Calendar ${calendar.calendarId} default handling: $isHandled")
                        isHandled
                    }
                }
                .map { it.calendarId }
                .toSet()
            
            DevLog.info(LOG_TAG, "getHandledCalendarsIds returning: $handledIds from all: ${allCalendars.map { it.calendarId }}")
            handledIds
        }
    }
}
