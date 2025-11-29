package com.github.quarck.calnotify.testutils

import android.content.Context
import com.github.quarck.calnotify.eventsstorage.EventsStorageInterface
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.monitorstorage.MonitorStorageInterface

/**
 * Factory for getting storage instances in tests.
 * 
 * In Robolectric tests, use the mock implementations to avoid native SQLite dependencies.
 * This factory maintains singleton instances that can be shared across test components.
 * 
 * Usage:
 * ```
 * // In test setup:
 * TestStorageFactory.reset() // Clear any previous state
 * 
 * // Get storage instances (creates if needed):
 * val monitorStorage = TestStorageFactory.getMonitorStorage()
 * val eventsStorage = TestStorageFactory.getEventsStorage()
 * 
 * // In test teardown:
 * TestStorageFactory.reset()
 * ```
 */
object TestStorageFactory {
    private val LOG_TAG = "TestStorageFactory"
    
    private var _monitorStorage: MockMonitorStorage? = null
    private var _eventsStorage: MockEventsStorage? = null
    
    /**
     * Gets or creates the singleton MockMonitorStorage instance.
     */
    fun getMonitorStorage(): MockMonitorStorage {
        if (_monitorStorage == null) {
            DevLog.info(LOG_TAG, "Creating new MockMonitorStorage instance")
            _monitorStorage = MockMonitorStorage()
        }
        return _monitorStorage!!
    }
    
    /**
     * Gets or creates the singleton MockEventsStorage instance.
     */
    fun getEventsStorage(): MockEventsStorage {
        if (_eventsStorage == null) {
            DevLog.info(LOG_TAG, "Creating new MockEventsStorage instance")
            _eventsStorage = MockEventsStorage()
        }
        return _eventsStorage!!
    }
    
    /**
     * Gets the monitor storage as interface type (for use with existing code).
     */
    fun getMonitorStorageInterface(): MonitorStorageInterface = getMonitorStorage()
    
    /**
     * Gets the events storage as interface type (for use with existing code).
     */
    fun getEventsStorageInterface(): EventsStorageInterface = getEventsStorage()
    
    /**
     * Resets all storage instances and clears their contents.
     * Call this in test setup/teardown to ensure isolation.
     */
    fun reset() {
        DevLog.info(LOG_TAG, "Resetting all storage instances")
        _monitorStorage?.clear()
        _eventsStorage?.clear()
        _monitorStorage = null
        _eventsStorage = null
    }
    
    /**
     * Clears the contents of existing storage instances without destroying them.
     * Useful for cleaning up between tests while keeping the instances.
     */
    fun clearAll() {
        DevLog.info(LOG_TAG, "Clearing all storage contents")
        _monitorStorage?.clear()
        _eventsStorage?.clear()
    }
    
    /**
     * Returns true if mock storages have been initialized.
     */
    val isInitialized: Boolean
        get() = _monitorStorage != null || _eventsStorage != null
}

