package com.github.quarck.calnotify

import android.util.Log
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import java.util.Map;
import java.util.HashMap;

class CalendarModule constructor(context: ReactApplicationContext?) :
  ReactContextBaseJavaModule(context) {
  override fun getName(): String {
    return "Calendar";
  }

  @ReactMethod
  fun createCalendarEvent(name: String, location: String) {
    Log.d("CalendarModule", "Create event called with name: $name and location: $location")
  }

}
