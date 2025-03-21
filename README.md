# Calendar Notifications Plus

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/refs/heads/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" width="200"/>](https://github.com/ImranR98/Obtainium)


Enhance your calendar experience with powerful notification controls! Calendar Notifications Plus is a feature-rich Android app that extends your calendar notifications with snooze functionality, persistence, and much more.

## 📱 What It Does

<p align="center">
  <img src="https://github.com/user-attachments/assets/fcadacbb-1309-4d92-92e4-721497f8968e" width="250" alt="Home Screen"/>&nbsp;&nbsp;&nbsp;
  <img src="https://github.com/user-attachments/assets/433f1ce4-99d9-4041-99f5-c7f06be0678e" width="250" alt="Snooze Screen"/>&nbsp;&nbsp;&nbsp;
  <img src="https://github.com/user-attachments/assets/96c1dfa9-b65b-46d3-a13d-a020041ae090" width="250" alt="Toast Notification"/>
</p>

This app replaces default calendar event notifications with an enhanced version that provides snooze functionality and notification persistence. It's designed to be transparent in its operation - calendar notifications behave as expected, with direct clicks opening event details in your default calendar application. The enhanced functionality is provided through additional actions in the notifications.

When snoozing events, you can snooze them a desired amount of time or quickly reschedule them with just one click. All your notification states persist through device reboots, ensuring you never miss an important event.

## ✨ Key Features

- **Smart Snooze**: Easily snooze calendar notifications to a time that works for you
- **Notification Persistence**: Never miss an event - notifications persist until you handle them
- **Quick Rescheduling**: Reschedule non-repeating events for the next day or week with just one click
- **Quiet Hours**: Set periods when notifications won't disturb you
- **Missed Event Reminders**: Optional reminders for notifications you might have missed (configurable intervals)
- **Bulk Actions**: "Snooze All" button to manage multiple notifications at once
- **Customization**: Custom LED colors and screen wake options for notifications
- **Data Backup**: Unidirectional sync to backup your notification data to the cloud (via Supabase/PowerSync)
- **Reboot Protection**: All your notifications are automatically restored after device restart

## 📱 Installation

You can install Calendar Notifications Plus in several ways:
1. Through [Obtainium](https://github.com/ImranR98/Obtainium)
2. Directly from our [GitHub Releases](https://github.com/williscool/CalendarNotification/releases)

## 🚀 Getting Started

The app seamlessly integrates with your existing calendar:
- Direct tap on notifications opens event details in your default calendar app
- Additional actions are available through notification buttons
- Works with your existing calendar events - no migration needed

## 📝 Permissions

The app requires the following permissions for core functionality:
- **Read Calendar**: To retrieve event details for notifications
- **Write Calendar**: To prevent duplicate notifications from the stock calendar
- **Start at Boot**: To restore your notifications after device restart

## 🛠️ For Developers

If you're interested in contributing or building the app yourself, check out our developer documentation:

- [Build Instructions](docs/BUILD.md)
- [Data Sync Guide](docs/DATA_SYNC_README.md)
- [Debugging Guide](docs/DEBUG.md)

## 💖 Contributing

We welcome contributions! Whether it's bug reports, feature requests, or code contributions, please feel free to get involved. Please report any bugs or feedback through the app's feedback page.

## 📜 History

This project is a maintained fork of the original [Calendar Notifications](https://github.com/quarck/CalendarNotification) app. When the original was archived in 2020, we took on the responsibility of maintaining and enhancing it to ensure continued compatibility with modern Android versions while adding new features like cloud data backup.
