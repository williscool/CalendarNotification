package com.github.quarck.calnotify.testutils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitorService
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
    
    private lateinit var mockService: CalendarMonitorService
    
    /**
     * Sets up the mock context and related components
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up MockContextProvider")
        setupMockContext()
        setupMockService()
    }
    
    /**
     * Creates and configures the mock Android Context
     */
    private fun setupMockContext() {
        DevLog.info(LOG_TAG, "Setting up mock context")
        
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Mock key extensions used with AlarmManager
        mockkStatic("com.github.quarck.calnotify.utils.SystemUtilsKt")
        
        // Create mock AlarmManager
        mockAlarmManager = mockk<AlarmManager>(relaxed = true) {
            every { setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) } just Runs
            every { setExact(any(), any(), any<PendingIntent>()) } just Runs
            every { setAlarmClock(any(), any<PendingIntent>()) } just Runs
            every { set(any(), any(), any<PendingIntent>()) } just Runs
            every {
                setInexactRepeating(
                    any(),
                    any(),
                    any(),
                    any<PendingIntent>()
                )
            } answers {
                val intervalType = firstArg<Int>()
                val triggerAtMillis = secondArg<Long>()
                val intervalMillis = thirdArg<Long>()
                DevLog.info(LOG_TAG, "Mock setInexactRepeating called: type=$intervalType, triggerAt=$triggerAtMillis, interval=$intervalMillis")
            }
            every { cancel(any<PendingIntent>()) } answers {
                DevLog.info(LOG_TAG, "Mock cancel called on AlarmManager")
            }
        }
        
        // Stub the extension function
        every {
            mockAlarmManager.cancelExactAndAlarm(any(), any(), any())
        } answers {
            val context = firstArg<Context>()
            val receiverClass1 = secondArg<Class<*>>()
            val receiverClass2 = thirdArg<Class<*>>()
            DevLog.info(LOG_TAG, "Mock cancelExactAndAlarm called: receivers=${receiverClass1.simpleName}, ${receiverClass2.simpleName}")
        }
        
        // Create mock Context
        fakeContext = mockk<Context>(relaxed = true) {
            every { packageName } returns realContext.packageName
            every { packageManager } returns realContext.packageManager
            every { applicationContext } returns realContext.applicationContext
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
            every { checkPermission(any(), any(), any()) } answers { realContext.checkPermission(firstArg(), secondArg(), thirdArg()) }
            every { checkCallingOrSelfPermission(any()) } answers { realContext.checkCallingOrSelfPermission(firstArg()) }
            every { createPackageContext(any(), any()) } answers { realContext.createPackageContext(firstArg(), secondArg()) }
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
                mockService.handleIntentForTest(intent)
                ComponentName(realContext.packageName, CalendarMonitorService::class.java.name)
            }
            every { startActivity(any()) } just Runs
            every { getResources() } returns realContext.resources
            every { getTheme() } returns realContext.theme
        }
    }
    
    /**
     * Creates a mock service instance
     */
    private fun setupMockService() {
        DevLog.info(LOG_TAG, "Setting up mock service")
        
        mockService = spyk(CalendarMonitorService()) {
            every { applicationContext } returns fakeContext
            every { baseContext } returns fakeContext
            every { clock } returns timeProvider.testClock
            every { getDatabasePath(any()) } answers { fakeContext.getDatabasePath(firstArg()) }
            every { checkPermission(any(), any(), any()) } answers { fakeContext.checkPermission(firstArg(), secondArg(), thirdArg()) }
            every { checkCallingOrSelfPermission(any()) } answers { fakeContext.checkCallingOrSelfPermission(firstArg()) }
            every { getPackageName() } returns fakeContext.packageName
            every { getContentResolver() } returns fakeContext.contentResolver
            every { getSystemService(Context.ALARM_SERVICE) } returns mockAlarmManager
            every { getSystemService(Context.POWER_SERVICE) } returns fakeContext.getSystemService(Context.POWER_SERVICE)
            every { getSystemService(any<String>()) } answers {
                when (firstArg<String>()) {
                    Context.ALARM_SERVICE -> mockAlarmManager
                    Context.POWER_SERVICE -> fakeContext.getSystemService(Context.POWER_SERVICE)
                    else -> fakeContext.getSystemService(firstArg())
                }
            }
            every { getSharedPreferences(any(), any()) } answers {
                val name = firstArg<String>()
                sharedPreferencesMap.getOrPut(name) { createPersistentSharedPreferences(name) }
            }
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
                    DevLog.info(LOG_TAG, "SharedPreferences[$name].putString($key, $value)")
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putBoolean(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Boolean>()
                    DevLog.info(LOG_TAG, "SharedPreferences[$name].putBoolean($key, $value)")
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putInt(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Int>()
                    DevLog.info(LOG_TAG, "SharedPreferences[$name].putInt($key, $value)")
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putLong(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Long>()
                    DevLog.info(LOG_TAG, "SharedPreferences[$name].putLong($key, $value)")
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { putFloat(any(), any()) } answers {
                    val key = firstArg<String>()
                    val value = secondArg<Float>()
                    DevLog.info(LOG_TAG, "SharedPreferences[$name].putFloat($key, $value)")
                    sharedPrefsMap[key] = value
                    this@mockk
                }
                every { remove(any()) } answers {
                    val key = firstArg<String>()
                    DevLog.info(LOG_TAG, "SharedPreferences[$name].remove($key)")
                    sharedPrefsMap.remove(key)
                    this@mockk
                }
                every { clear() } answers {
                    DevLog.info(LOG_TAG, "SharedPreferences[$name].clear()")
                    sharedPrefsMap.clear()
                    this@mockk
                }
                every { apply() } just Runs
                every { commit() } returns true
            }
            every { getString(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<String>()
                val value = sharedPrefsMap[key] as? String ?: defaultValue
                DevLog.info(LOG_TAG, "SharedPreferences[$name].getString($key) = $value")
                value
            }
            every { getBoolean(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Boolean>()
                val value = sharedPrefsMap[key] as? Boolean ?: defaultValue
                DevLog.info(LOG_TAG, "SharedPreferences[$name].getBoolean($key) = $value")
                value
            }
            every { getInt(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Int>()
                val value = sharedPrefsMap[key] as? Int ?: defaultValue
                DevLog.info(LOG_TAG, "SharedPreferences[$name].getInt($key) = $value")
                value
            }
            every { getLong(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Long>()
                val value = sharedPrefsMap[key] as? Long ?: defaultValue
                DevLog.info(LOG_TAG, "SharedPreferences[$name].getLong($key) = $value")
                value
            }
            every { getFloat(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultValue = secondArg<Float>()
                val value = sharedPrefsMap[key] as? Float ?: defaultValue
                DevLog.info(LOG_TAG, "SharedPreferences[$name].getFloat($key) = $value")
                value
            }
            every { contains(any()) } answers {
                val key = firstArg<String>()
                val value = sharedPrefsMap.containsKey(key)
                DevLog.info(LOG_TAG, "SharedPreferences[$name].contains($key) = $value")
                value
            }
            every { getAll() } returns sharedPrefsMap
        }
    }
    
    /**
     * Returns the mock service instance
     */
    fun getMockService(): CalendarMonitorService {
        return mockService
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockContextProvider")
        sharedPreferencesMap.clear()
        sharedPreferencesDataMap.clear()
    }
} 
