package com.github.quarck.calnotify.testutils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.VersionedPackage
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
    
    // Service is created once and reused
    lateinit var mockService: CalendarMonitorService
        private set
    
    // Track last timer broadcast time for tests (used by globalState)
    private var lastTimerBroadcastReceived: Long? = null
    
    // Track initialization state
    private var isInitialized = false
    
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
                mockService.handleIntentForTest(intent)
                ComponentName(realContext.packageName, CalendarMonitorService::class.java.name)
            }
            every { startActivity(any()) } just Runs
            every { getResources() } returns realContext.resources
            every { getTheme() } returns realContext.theme
        }


      // Mock the globalState extension property
      mockkStatic("com.github.quarck.calnotify.GlobalStateKt")
      every { any<Context>().globalState } answers {
        mockk {
          every { lastTimerBroadcastReceived } returns this@MockContextProvider.lastTimerBroadcastReceived!!
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
        DevLog.info(LOG_TAG, "Setting up mock service")
        
        mockService = spyk(CalendarMonitorService()) {
            every { applicationContext } returns fakeContext
            every { baseContext } returns fakeContext
            every { clock } returns timeProvider.testClock
            every { getDatabasePath(any()) } answers { fakeContext.getDatabasePath(firstArg()) }
            every { checkPermission(any(), any(), any()) } answers { 
                fakeContext.checkPermission(firstArg(), secondArg(), thirdArg()) 
            }
            every { checkCallingOrSelfPermission(any()) } answers { 
                fakeContext.checkCallingOrSelfPermission(firstArg()) 
            }
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
        
        // Mock handleIntentForTest to just log without executing actual intent handling logic
        every { mockService.handleIntentForTest(any()) } answers {
            val intent = firstArg<Intent>()
            DevLog.info(LOG_TAG, "Service intent: action=${intent.action}, extras=${intent.extras}")
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
} 
