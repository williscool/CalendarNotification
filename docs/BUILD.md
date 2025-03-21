# Build Instructions

This document contains all the necessary information for building Calendar Notifications Plus from source.

## Prerequisites

- Java Development Kit (JDK) 21.0.6-tem or compatible version
- Android SDK
- Node.js and Yarn
- React Native development environment
- WSL2 (for Windows development)

## Setting Up the Development Environment

### Java Setup

Using SDKMAN (recommended):
```bash
# Install SDKMAN
brew tap sdkman/tap
brew install sdkman-cli

# Install and use Java
sdk install java 21.0.6-tem
sdk use java 21.0.6-tem
```

### Basic Build

```bash
# Install dependencies
yarn 

# Start Metro bundler
yarn start
```

### Running on Emulator

```bash
cd C:\Users\<username>\appdata\local\android\sdk
.\emulator\emulator.exe -avd 7.6_Fold-in_with_outer_display_API_34q
```

### ADB Setup for WSL

Follow these steps to set up ADB in WSL:

```bash
sudo ln -s /mnt/c/Users/<username>/AppData/Local/Android/Sdk/platform-tools/adb.exe /home/<username>/android/platform-tools/adb
```

### WSL Metro Connection Setup

```bash
# Get the WSL2 IP address
WSL_VM_IP_ADDRESS=$(ifconfig eth0 | awk '/inet / {print $2}')

# Get the Windows HOST IP address (if needed)
HOST_IP_ADDRESS=$(ipconfig.exe | awk '/Ethernet adapter vEthernet \(Default Switch\):/{i=1; next} i && /IPv4 Address/{print $NF; exit}' | sed 's/^[ \t]*//')

# Open the developer menu
adb shell input keyevent 82

# Input the IP address
adb shell input text "${WSL_VM_IP_ADDRESS}:8081"
```

## Building Release Versions

### Local Release Build

1. Build the bundle:
```bash
yarn react-native bundle --platform android --dev false --entry-file index.tsx --bundle-output android/app/src/main/assets/index.android.bundle  --assets-dest android/app/src/main/res/
```

2. Follow Android Studio instructions for release build configuration

### CI Release Build

Refer to `.github/workflows/actions.yml` for the complete CI build process.

## Building cr-sqlite for Android

See [CR_SQLITE_BUILD.md](CR_SQLITE_BUILD.md) for detailed instructions on building cr-sqlite for Android. 