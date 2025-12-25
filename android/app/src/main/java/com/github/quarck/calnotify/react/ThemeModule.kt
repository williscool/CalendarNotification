// Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
package com.github.quarck.calnotify.react

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.github.quarck.calnotify.Settings

/**
 * Native module to expose the app's theme setting to React Native.
 * This allows React Native to respect the in-app theme choice (system/light/dark)
 * rather than just reading the system theme.
 */
class ThemeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "ThemeModule"

    /**
     * Get the current effective color scheme based on the app's theme setting.
     * Returns "light" or "dark".
     */
    @ReactMethod
    fun getColorScheme(promise: Promise) {
        try {
            val context = reactApplicationContext
            val settings = Settings(context)
            val themeMode = settings.themeMode
            
            val colorScheme = when (themeMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> "light"
                AppCompatDelegate.MODE_NIGHT_YES -> "dark"
                else -> {
                    // Follow system - check the actual current configuration
                    val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
                }
            }
            
            promise.resolve(colorScheme)
        } catch (e: Exception) {
            promise.reject("THEME_ERROR", e.message)
        }
    }

    /**
     * Get the raw theme mode setting.
     * Returns: -1 = follow system, 1 = light, 2 = dark
     */
    @ReactMethod
    fun getThemeMode(promise: Promise) {
        try {
            val settings = Settings(reactApplicationContext)
            promise.resolve(settings.themeMode)
        } catch (e: Exception) {
            promise.reject("THEME_ERROR", e.message)
        }
    }
}

