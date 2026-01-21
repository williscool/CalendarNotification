package com.github.quarck.calnotify.testutils

import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.CNPlusClockInterface
import com.github.quarck.calnotify.utils.CNPlusUnitTestClock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Provides time-related mock functionality for tests
 * 
 * This class wraps CNPlusTestClock to provide a consistent
 * time management interface for tests.
 */
class MockTimeProvider(
    startTime: Long = TestTimeConstants.STANDARD_TEST_TIME
) {
    private val LOG_TAG = "MockTimeProvider"
    
    // Use a real timer instead of a mock to prevent recursion issues
    val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    val testClock: CNPlusUnitTestClock
    val currentTime = AtomicLong(startTime)
    
    // Track if we've been initialized to prevent double initialization
    private var isInitialized = false
    
    init {
        DevLog.info(LOG_TAG, "Initializing MockTimeProvider with startTime=$startTime")
        
        // Create the CNPlusTestClock with our real timer
        testClock = CNPlusUnitTestClock(startTime, timer)
    }
    
    /**
     * Sets up the mock time provider
     */
    fun setup() {
        if (isInitialized) {
            DevLog.info(LOG_TAG, "MockTimeProvider already initialized, skipping setup")
            return
        }
        
        DevLog.info(LOG_TAG, "Setting up MockTimeProvider")
        
        // The CNPlusTestClock already configures the timer in its init block
        // so we don't need to do additional setup
        
        // Initialize the current time
        currentTime.set(testClock.currentTimeMillis())
        
        isInitialized = true
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
        timer.shutdown()
        try {
            if (!timer.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                timer.shutdownNow()
            }
        } catch (e: InterruptedException) {
            timer.shutdownNow()
        }
        isInitialized = false
    }
} 
