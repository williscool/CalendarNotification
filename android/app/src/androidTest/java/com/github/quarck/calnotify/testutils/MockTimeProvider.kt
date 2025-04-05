package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusTestClock
import io.mockk.mockk
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicLong

/**
 * Provides time-related mock functionality for tests
 * 
 * This class wraps CNPlusTestClock to provide a consistent
 * time management interface for tests.
 */
class MockTimeProvider(
    startTime: Long = System.currentTimeMillis()
) {
    private val LOG_TAG = "MockTimeProvider"
    
    // Core components
    val mockTimer: ScheduledExecutorService = mockk(relaxed = true)
    val testClock: CNPlusTestClock
    val currentTime = AtomicLong(startTime)
    
    init {
        DevLog.info(LOG_TAG, "Initializing MockTimeProvider with startTime=$startTime")
        testClock = CNPlusTestClock(startTime, mockTimer)
    }
    
    /**
     * Sets up the mock timer
     */
    fun setup() {
        DevLog.info(LOG_TAG, "Setting up MockTimeProvider")
        // Additional setup if needed, the CNPlusTestClock constructor
        // already configures the mockTimer in its init block
    }
    
    /**
     * Advances the test clock by the specified duration
     * and executes any scheduled tasks
     */
    fun advanceTime(milliseconds: Long) {
        val oldTime = testClock.currentTimeMillis()
        val executedTasks = testClock.advanceAndExecuteTasks(milliseconds)
        val newTime = testClock.currentTimeMillis()
        currentTime.set(newTime)
        
        DevLog.info(LOG_TAG, "Advanced time from $oldTime to $newTime (by $milliseconds ms)")
        
        if (executedTasks.isNotEmpty()) {
            DevLog.info(LOG_TAG, "Executed ${executedTasks.size} tasks due at or before $newTime")
            DevLog.info(LOG_TAG, "Remaining scheduled tasks: ${testClock.scheduledTasks.size}")
        } else {
            DevLog.info(LOG_TAG, "No tasks due at or before $newTime")
        }
    }
    
    /**
     * Sets the test clock to a specific time
     */
    fun setCurrentTime(timeMillis: Long) {
        DevLog.info(LOG_TAG, "Setting current time to $timeMillis")
        testClock.setCurrentTime(timeMillis)
        currentTime.set(timeMillis)
    }
    
    /**
     * Executes all pending scheduled tasks
     */
    fun executeAllPendingTasks() {
        DevLog.info(LOG_TAG, "Executing all pending tasks")
        testClock.executeAllPendingTasks()
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        DevLog.info(LOG_TAG, "Cleaning up MockTimeProvider")
        // Nothing to clean up, included for consistency
    }
} 