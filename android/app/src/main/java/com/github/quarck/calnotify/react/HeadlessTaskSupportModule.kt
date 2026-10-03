// Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
package com.github.quarck.calnotify.react

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.jstasks.HeadlessJsTaskContext

/**
 * Tells native when a headless JS task has finished, so its service can stop.
 *
 * React Native 0.81 ships this as HeadlessJsTaskSupportModule but only registers it for the old
 * architecture (CoreModulesPackage, not runtime/CoreReactPackage), and the class is internal.
 * Under the New Architecture JS finds no module, never reports the finish, and the task runs
 * until its timeout. JS looks the module up by this name, so providing it here restores that.
 */
class HeadlessTaskSupportModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "HeadlessJsTaskSupport"

    @ReactMethod
    fun notifyTaskFinished(taskId: Double) {
        val taskContext = HeadlessJsTaskContext.getInstance(reactApplicationContext)
        if (taskContext.isTaskRunning(taskId.toInt())) {
            taskContext.finishTask(taskId.toInt())
        }
    }

    /** Retries are not used: tasks are configured with the default no-retry policy. */
    @ReactMethod
    fun notifyTaskRetry(taskId: Double, promise: Promise) {
        promise.resolve(false)
    }
}
