package com.github.quarck.calnotify.testutils

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import com.github.quarck.calnotify.logs.DevLog
import io.mockk.*

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
        
        // Set up minimal mock components for Robolectric
        setupAlarmManager()
        
        // If a context was set from outside (for Robolectric), keep it
        if (fakeContext == null) {
            DevLog.warn(LOG_TAG, "No context was provided, creating an empty mock")
            fakeContext = mockk<Context>(relaxed = true)
        } else {
            DevLog.info(LOG_TAG, "Using externally provided context for Robolectric")
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
} 