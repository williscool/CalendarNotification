/**
 * Clock interface for providing time functions
 * This abstraction allows for easier testing by providing a way to mock/control time
 * 
 * This is a wrapper around java.time.Clock to provide millisecond precision
 * and sleep functionality
 */
package com.github.quarck.calnotify.utils

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import io.mockk.every
import io.mockk.mockk
import com.github.quarck.calnotify.logs.DevLog

private const val LOG_TAG = "CNPlusTestClock"

/**
 * Test implementation of CNPlusClock that allows controlling the time
 * and supports timer-based sleep through a ScheduledExecutorService if provided
 */
class CNPlusTestClock(
    private var currentTimeMs: Long = 0L,
    private val mockTimer: ScheduledExecutorService? = null
) : CNPlusClockInterface {
    /**
     * The mutable clock implementation that can be replaced in tests
     */
    var fixedClock: Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        
        override fun withZone(zone: ZoneId?): Clock {
            return this
        }
        
        override fun instant(): Instant = Instant.ofEpochMilli(currentTimeMs)
    }
        private set
    
    /**
     * A list to track scheduled tasks and their execution times
     */
    val scheduledTasks = mutableListOf<Pair<Runnable, Long>>()

    init {
        // If we have a mock timer, configure it automatically
        mockTimer?.let { setupMockTimer(it) }
    }
    
    /**
     * Sets up the mock timer to work with this test clock
     */
    private fun setupMockTimer(timer: ScheduledExecutorService) {
        // Clear any existing tasks
        scheduledTasks.clear()
        
        // Check if this is a real timer or a mock - only mock if it's a MockK instance
        if (timer::class.java.name.contains("mockk")) {
            // Configure the mock timer's schedule method
            every { 
                timer.schedule(any<Runnable>(), any<Long>(), any<TimeUnit>()) 
            } answers { call ->
                val task = call.invocation.args[0] as Runnable
                val delay = call.invocation.args[1] as Long
                val unit = call.invocation.args[2] as TimeUnit
                val dueTime = currentTimeMs + unit.toMillis(delay)
                
                DevLog.info(LOG_TAG, "[mockTimer] Scheduling task to run at $dueTime (current: $currentTimeMs, delay: $delay ${unit.name})")
                scheduleTask(unit.toMillis(delay), task)
                
                // Return a mock ScheduledFuture
                mockk<java.util.concurrent.ScheduledFuture<*>>(relaxed = true)
            }
        } else {
            // For real timers, we'll use the direct scheduling method without mocking
            DevLog.info(LOG_TAG, "Using real timer - no mocking required")
        }
    }
    
    /**
     * Schedules a task to run after the specified delay
     * This method works for both real and mock timers
     */
    fun scheduleTask(delayMs: Long, task: Runnable) {
        val dueTime = currentTimeMs + delayMs
        DevLog.info(LOG_TAG, "Scheduling task to run at $dueTime (current: $currentTimeMs, delay: ${delayMs}ms)")
        scheduledTasks.add(Pair(task, dueTime))
        // Sort by due time to process in order
        scheduledTasks.sortBy { it.second }
    }
    
    /**
     * Create a new fixed clock with the current time value
     */
    fun refreshClock() {
        fixedClock = object : Clock() {
            override fun getZone(): ZoneId = ZoneId.systemDefault()
            
            override fun withZone(zone: ZoneId?): Clock {
                return this
            }
            
            override fun instant(): Instant = Instant.ofEpochMilli(currentTimeMs)
        }
    }
    
    override fun currentTimeMillis(): Long = currentTimeMs
    
    override fun sleep(millis: Long) {
        // Always advance the clock by the sleep duration
        currentTimeMs += millis
        refreshClock()
        
        if (mockTimer != null) {
            // If we have a mock timer, schedule a task to simulate the passage of time
            // but don't actually wait for real time to pass
            val latch = CountDownLatch(1)
            mockTimer.schedule({ latch.countDown() }, 0, TimeUnit.MILLISECONDS)
        }
    }
    
    override fun underlying(): Clock = fixedClock
    
    /**
     * Manually set the current time
     */
    fun setCurrentTime(timeMillis: Long) {
        currentTimeMs = timeMillis
        refreshClock()
    }
    
    /**
     * Advance the clock by the specified amount
     */
    fun advanceBy(millis: Long) {
        currentTimeMs += millis
        refreshClock()
    }
    
    /**
     * Advances the clock by the specified duration and processes any scheduled tasks.
     * This is used for testing to simulate the passage of time and execution of pending tasks.
     *
     * @param milliseconds The amount of time to advance
     * @param scheduledTasks A mutable list of pairs containing tasks and their scheduled execution times
     * @return The list of tasks that were executed
     */
    fun advanceAndExecuteTasks(milliseconds: Long, scheduledTasks: MutableList<Pair<Runnable, Long>>): List<Pair<Runnable, Long>> {
        val oldTime = currentTimeMillis()
        val newTime = oldTime + milliseconds
        setCurrentTime(newTime)
        
        // Process due tasks
        val tasksToRun = scheduledTasks.filter { it.second <= newTime }
        
        if (tasksToRun.isNotEmpty()) {
            tasksToRun.forEach { (task, _) ->
                try {
                    task.run()
                } catch (e: Exception) {
                    // Log exception during task execution
                    DevLog.error(LOG_TAG, "Exception running scheduled task: ${e.message}")
                }
            }
            // Remove executed tasks
            scheduledTasks.removeAll(tasksToRun)
        }
        
        return tasksToRun
    }
    
    /**
     * Advances the clock and processes any scheduled tasks tracked by this clock instance.
     * This is a convenience method that uses the internal scheduledTasks list.
     *
     * @param milliseconds The amount of time to advance
     * @return The list of tasks that were executed
     */
    fun advanceAndExecuteTasks(milliseconds: Long): List<Pair<Runnable, Long>> {
        return advanceAndExecuteTasks(milliseconds, scheduledTasks)
    }
    
    /**
     * Executes all pending scheduled tasks immediately without advancing the clock.
     * This is useful for cleaning up any remaining tasks at the end of a test.
     *
     * @return The list of tasks that were executed
     */
    fun executeAllPendingTasks(): List<Pair<Runnable, Long>> {
        val tasksCopy = scheduledTasks.toList()
        
        if (tasksCopy.isNotEmpty()) {
            DevLog.info(LOG_TAG, "Executing all ${tasksCopy.size} pending tasks immediately")
            
            // Execute all tasks
            tasksCopy.forEach { (task, time) ->
                try {
                    DevLog.info(LOG_TAG, "Executing task scheduled for time $time (current time: $currentTimeMs)")
                    task.run()
                } catch (e: Exception) {
                    DevLog.error(LOG_TAG, "Exception running scheduled task: ${e.message}")
                }
            }
            
            // Remove executed tasks
            scheduledTasks.removeAll(tasksCopy)
        } else {
            DevLog.info(LOG_TAG, "No pending tasks to execute")
        }
        
        return tasksCopy
    }
    
    /**
     * Get the current time (for backward compatibility with tests)
     */
    fun getCurrentTime(): Long = currentTimeMs
} 