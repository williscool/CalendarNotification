// Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
package com.github.quarck.calnotify.ui

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MyReactActivity : ReactActivity() {
    /**
     * Returns the name of the main component registered from JavaScript.
     * This is used to schedule rendering of the component.
     */
    override fun getMainComponentName(): String = "CNPlusSync"

    /**
     * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
     * which allows you to enable New Architecture with a single boolean flag [fabricEnabled]
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    companion object {
        /**
         * True between onResume and onPause, i.e. while the React Native sync screens (Sync Info,
         * Sync Settings, Sync Debug) are in the foreground. SyncForegroundService reads it in the
         * same process when a sync finishes, and skips the "Sync complete" notification if so:
         * the screen already shows the result. Failed and paused results are still posted.
         */
        var isResumed = false
            internal set
    }
}
