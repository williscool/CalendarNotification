package com.github.quarck.calnotify.testutils

import androidx.test.espresso.IdlingResource
import com.github.quarck.calnotify.utils.AsyncTaskCallback
import java.util.concurrent.atomic.AtomicInteger

/**
 * IdlingResource that tracks AsyncTask operations via callback.
 * 
 * Espresso will automatically wait for all tracked AsyncTasks to complete
 * before performing actions or assertions.
 */
class AsyncTaskIdlingResource(
    private val resourceName: String = "AsyncTaskIdlingResource"
) : IdlingResource, AsyncTaskCallback {
    
    private val counter = AtomicInteger(0)
    @Volatile private var callback: IdlingResource.ResourceCallback? = null
    
    override fun getName(): String = resourceName
    
    override fun isIdleNow(): Boolean {
        val idle = counter.get() == 0
        if (idle && callback != null) {
            callback?.onTransitionToIdle()
        }
        return idle
    }
    
    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
    }
    
    override fun onTaskStarted() {
        counter.incrementAndGet()
    }
    
    override fun onTaskCompleted() {
        val count = counter.decrementAndGet()
        if (count == 0 && callback != null) {
            callback?.onTransitionToIdle()
        }
    }
    
    /**
     * Get current count (for debugging).
     */
    fun getCount(): Int = counter.get()
    
    /**
     * Resets the counter to zero.
     * Call this when clearing IdlingResources between tests to prevent stale
     * counter values from causing subsequent tests to hang.
     */
    fun reset() {
        val previousCount = counter.getAndSet(0)
        if (previousCount > 0) {
            com.github.quarck.calnotify.logs.DevLog.info(
                "AsyncTaskIdlingResource",
                "Reset counter from $previousCount to 0 (clearing stale async task count)"
            )
        }
        // Notify callback that we're now idle (counter is 0)
        callback?.onTransitionToIdle()
    }
}

