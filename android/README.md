# Calendar Notifications Plus
Android app extending calendar notifications with snooze button and notifications persistance

# Building the App
You can also build the app yourself from sources available here :)

# Android-Specific Setup

This document covers Android-specific setup and configuration for Calendar Notifications Plus.

## Android Studio Setup

1. Open the `android` directory in Android Studio
2. Ensure you have the correct SDK version installed
3. Configure your virtual device or connect a physical device

## Android SDK Requirements

- Minimum SDK Version: 21
- Target SDK Version: 34
- Build Tools Version: 34.0.0

## Device Configuration

### Physical Device Setup

1. Enable Developer Options:
   - Go to Settings > About Phone
   - Tap Build Number 7 times
   - Developer Options will appear in Settings

2. Enable USB Debugging:
   - Go to Settings > Developer Options
   - Enable USB Debugging
   - Connect your device via USB
   - Accept the debugging authorization prompt

### Virtual Device Setup

1. Open Android Studio
2. Go to Tools > Device Manager
3. Click "Create Device"
4. Select your preferred device definition
5. Download and select a system image
6. Complete the configuration and launch

## Troubleshooting

### Common Android Issues

1. **Gradle Build Failures**
   - Clean project: Build > Clean Project
   - Invalidate caches: File > Invalidate Caches
   - Delete `build` directories and rebuild

2. **Device Not Recognized**
   - Ensure USB debugging is enabled
   - Try different USB ports/cables
   - Update device drivers

3. **APK Installation Failed**
   - Uninstall existing app version
   - Clear build cache
   - Check signing configuration

For more detailed build and debugging information, see:
- [Build Instructions](../docs/BUILD.md)
- [Debugging Guide](../docs/DEBUG.md)

# Description
This app would replace default calendar event notifications, providing snooze functionality and notifications persistence. Reboot is handled, all notifications are restored after reboot. Focus of this app is to keep its operation as transparent as possible, calendar notifications would behave like expected: direct click on notification opens event details in default calendar application, new functionality is provided via actions available for notifications.
On the snooze activity you can also quickly re-schedule event for the next day or week in one click (this is not available for repeating events).

Additional functionality provided by this app: 
* Quiet hours
* Reminders for missed notifications (off by default, interval can be configured)
* "Snooze All" button in the app
* Custom LED colors / Wake screen on notification (if configured)

This app is currently in BETA. Please report any bugs found and any feedback you have via feedback page in the app.

Rationale for requested permissions: 
* Read Calendar - required to be able to retrieve event details to display notification
* Write Calendar - necessary to stop stock calendar from showing the same notification 
* Start at Boot - to restore notifications
