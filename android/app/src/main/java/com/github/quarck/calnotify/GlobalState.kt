//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA

package com.github.quarck.calnotify

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.soloader.SoLoader
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.github.quarck.calnotify.notification.NotificationChannels

// This storage is wiped every time app is restarted. Only keep variables
// that are instance-specific here
class GlobalState : Application(), ReactApplication {
    var lastNotificationRePost: Long = 0
    var lastTimerBroadcastReceived: Long = 0

    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> =
                PackageList(this@GlobalState).packages

            override fun getJSMainModuleName(): String = "index"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
        }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        // Skip React Native initialization in Robolectric tests
        if (!isRunningInRobolectric()) {
            // Initialize SoLoader with merged SO mapping for RN 0.76+
            SoLoader.init(this, OpenSourceMergedSoMapping)
            // Load New Architecture if enabled
            if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
                load()
            }
        }
        // Apply saved theme preference (defaults to follow system if not set)
        val settings = Settings(this)
        AppCompatDelegate.setDefaultNightMode(settings.themeMode)
        // Create notification channels (required for Android 8+, no-op on older versions)
        NotificationChannels.createChannels(this)
    }

    /**
     * Detects if we're running in a Robolectric test environment.
     * SoLoader and React Native native code don't work in Robolectric.
     */
    private fun isRunningInRobolectric(): Boolean {
        return try {
            Class.forName("org.robolectric.Robolectric")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}

val Context.globalState: GlobalState?
    get() {
        val appCtx = applicationContext
        if (appCtx is GlobalState)
            return appCtx

        return null
    }

