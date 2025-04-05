/**
 * Default implementation of CNPlusClock that uses system time
 */
package com.github.quarck.calnotify.utils

import java.time.Clock

/**
 * Default implementation of Clock that uses system time
 */
class CNPlusSystemClock : CNPlusClockInterface {
    private val clock: Clock = Clock.systemUTC()
    
    override fun currentTimeMillis(): Long = clock.millis()
    
    /**
     * Sleep for the given number of milliseconds
     * 
     * TODO: Implement a more robust sleep mechanism
     * for now we just want to preserve the behavior of the original code
     * @param millis the number of milliseconds to sleep
     */
    override fun sleep(millis: Long) {
        // try {
        Thread.sleep(millis)
        // } catch (e: InterruptedException) {
        //     Thread.currentThread().interrupt()
        // }
    }
    
    override fun underlying(): Clock = clock
} 