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
        if (mockTimer != null) {
            // Use timer-based sleep with the provided executor
            val latch = CountDownLatch(1)
            val task = Runnable { latch.countDown() }
            mockTimer.schedule(task, millis, TimeUnit.MILLISECONDS)
            latch.await(millis, TimeUnit.MILLISECONDS)
        } else {
            // Advance the clock by the sleep duration
            currentTimeMs += millis
            refreshClock()
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
     * Get the current time (for backward compatibility with tests)
     */
    fun getCurrentTime(): Long = currentTimeMs
} 