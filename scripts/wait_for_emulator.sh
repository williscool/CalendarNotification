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