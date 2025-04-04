/**
 * Clock interface for providing time functions
 * This abstraction allows for easier testing by providing a way to mock/control time
 * 
 * This is a wrapper around java.time.Clock to provide millisecond precision
 * and sleep functionality
 */
package com.github.quarck.calnotify.utils

import java.time.Clock

/**
 * Interface extending functionality of java.time.Clock
 */
interface CNPlusClockInterface {
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