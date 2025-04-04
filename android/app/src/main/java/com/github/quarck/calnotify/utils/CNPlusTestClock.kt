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

/**
 * Interface extending functionality of java.time.Clock
 */
interface CNPlusClock {
    /**
     * Returns the current time in milliseconds
     */
    fun currentTimeMillis(): Long
    
    /**
     * Sleeps for the specified time in milliseconds
     */
    fun sleep(millis: Long)
    
    /**
     * Gets the underlying java.time.Clock
     */
    fun underlying(): Clock
}

/**
 * Default implementation of Clock that uses system time
 */
class CNPlusSystemClock : CNPlusClock {
    private val clock: Clock = Clock.systemUTC()
    
    override fun currentTimeMillis(): Long = clock.millis()
    
    override fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
    
    override fun underlying(): Clock = clock
}

/**
 * Test implementation of CNPlusClock that allows controlling the time
 */
class CNPlusTestClock(private var currentTimeMs: Long = 0L) : CNPlusClock {
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
        // Advance the clock by the sleep duration
        currentTimeMs += millis
        refreshClock()
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