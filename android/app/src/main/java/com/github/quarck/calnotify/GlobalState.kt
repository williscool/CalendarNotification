//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
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



// a mis mash of

// https://reactnative.dev/docs/native-modules-android?android-language=kotlin#register-the-module-android-specific
// https://reactnative.dev/docs/integration-with-android-fragment?android-language=kotlin
// https://github.com/itinance/react-native-fs/issues/1046#issue-1028007246


// because the docs are incomplete

// also learned this is really governed by
// android:name of teh application
// https://stackoverflow.com/questions/39344843/android-app-application-cannot-be-cast-to-com-facebook-react-reactapplication

package com.github.quarck.calnotify

import android.R
import android.app.Application
import android.content.Context
import com.facebook.react.BuildConfig
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.views.text.ReactFontManager
import com.facebook.soloader.SoLoader


// This storage is wiped every time app is restarted. Only keep variables
// that are instance-specific here
class GlobalState : Application(), ReactApplication {
    var lastNotificationRePost: Long = 0
    var lastTimerBroadcastReceived: Long = 0

    val reactNativeHost =
    object : DefaultReactNativeHost(this) {
      override fun getUseDeveloperSupport() = BuildConfig.DEBUG
      override fun getPackages(): List<ReactPackage>? {
        val packages: MutableList<ReactPackage> = PackageList(this).packages
        // Packages that cannot be autolinked yet can be added manually here, for example:
        packages.add(MyAppPackage())
        return packages
      }
    }

  override fun getReactNativeHost(): ReactNativeHost = reactNativeHost


}

val Context.globalState: GlobalState?
    get() {
        val appCtx = applicationContext
        if (appCtx is GlobalState)
            return appCtx

        return null
    }

