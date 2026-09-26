#!/bin/bash

# Wait for emulator to be ready
adb wait-for-device

# Set up logging
adb logcat -c
adb logcat -s "ReactNativeJS" "ReactNative" "AndroidRuntime" "System.err" &
adb logcat > emulator.log &

# adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done;'

# Wait for emulator to be fully responsive (15 minutes timeout)
retries=15  # 15 retries * 60 seconds = 900 seconds (15 minutes)
while [ $retries -gt 0 ]; do
  if adb shell getprop sys.boot_completed | grep -m 1 '1'; then
    break
  fi
  retries=$((retries-1))
  sleep 60
  echo "Waiting for emulator to be fully responsive... $retries retries remaining"
done

if [ $retries -eq 0 ]; then
  echo "Emulator failed to start after 15 minutes"
  exit 1
fi

# A freshly booted CI emulator is slow enough that the launcher or SystemUI can
# hit an ANR (Application Not Responding: its main thread stays blocked ~5s).
# Android then shows an "<app> isn't responding" dialog, which takes window
# focus from the app under test (Espresso: RootViewWithoutFocusException) until
# someone dismisses it. Hide future error dialogs -- the system kills the
# unresponsive app instead -- and close any that already went up during boot.
adb shell settings put global hide_error_dialogs 1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
