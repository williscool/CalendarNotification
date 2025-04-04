/**
 * Default implementation of CNPlusClock that uses system time
 */
package com.github.quarck.calnotify.utils

import java.time.Clock

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