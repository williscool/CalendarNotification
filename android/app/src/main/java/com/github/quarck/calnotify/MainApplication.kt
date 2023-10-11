package com.github.quarck.calnotify

// NOTE WARNING! TURNS OUT THIS APP DONES'T USE THIS FILE AS ITS MAIN ENTRY!!!!!!!!!!!!!!
// SEE GlobalState.kt!!!

import android.app.Application
import com.facebook.react.BuildConfig
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactNativeHost

// a mis mash of

// https://reactnative.dev/docs/native-modules-android?android-language=kotlin#register-the-module-android-specific
// https://reactnative.dev/docs/integration-with-android-fragment?android-language=kotlin
// https://github.com/itinance/react-native-fs/issues/1046#issue-1028007246


// because the docs are incomplete

// also learned this is really governed by
// android:name of teh application
// https://stackoverflow.com/questions/39344843/android-app-application-cannot-be-cast-to-com-facebook-react-reactapplication


//class MyReactApplication : Application(), ReactApplication {
//  private val reactNativeHost =
//    object : DefaultReactNativeHost(this) {
//      override fun getUseDeveloperSupport() = BuildConfig.DEBUG
//      override fun getPackages(): List<ReactPackage> =
//        PackageList(this).packages.apply {
//          // Packages that cannot be autolinked yet can be added manually here, for example:
//          // packages.add(new MyReactNativePackage());
//          add(MyAppPackage())
//        }
//    }
//  override fun getReactNativeHost(): ReactNativeHost = reactNativeHost
//}
