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
}

