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

### Dependencies Setup

#### react-native-safe-area-context Issue and Mock Solution

We encountered a critical build issue with `react-native-safe-area-context` that required a custom solution. While upgrading the SDK would typically be the preferred solution, this wasn't immediately viable as SDK upgrades were breaking core notification functionality that would require extensive testing and modifications to fix.

##### Background

`react-native-safe-area-context` and `react-native-screens` are typically used together in React Navigation implementations:
1. `react-native-screens` optimizes the native view hierarchy using platform components
2. `react-native-safe-area-context` handles device-specific UI features (notches, status bars, etc.)
3. React Navigation expects both as peer dependencies

##### The Problem

We needed `react-native-screens` for the Data Sync UI, but `react-native-safe-area-context` was causing build failures. Simply removing it wasn't an option due to React Navigation's dependencies.

##### Our Solution: Custom Mock

We implemented a mock version of `react-native-safe-area-context` that:
- Provides dummy implementations of required components
- Allows React Navigation to function without errors
- Avoids the problematic native code entirely

The mock is automatically created during `yarn install` via a postinstall script:
```bash
node scripts/setup_safe_area_mock.js
```

##### Mock Implementation Structure

Located in `scripts/safe-area-mock-templates/`:
- `package.json` - Package metadata
- `module-index.js` - ES modules implementation
- `commonjs-index.js` - CommonJS implementation
- `typescript-index.d.ts` - TypeScript definitions

This approach allows us to:
1. Keep using React Navigation without errors
2. Avoid the build issues from the native implementation
3. Maintain consistent behavior in development and CI
4. Easily maintain the mock implementation

⚠️ **Note**: This is a temporary solution until we can safely upgrade SDK versions. UI elements may not properly respect safe areas on devices with notches or system UI elements.

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

https://gist.github.com/bergmannjg/461958db03c6ae41a66d264ae6504ade?permalink_comment_id=4149833#gistcomment-4149833

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


### If you even need to connect to your wsl running react server from your real phone on the same wifi

```bash

# get wsl hostname

$(wsl hostname -I)

# portforward host port to wsl vm

netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=8080 connectaddress=$(wsl hostname -I) connectport=8081

# open firewall
New-NetFirewallRule -DisplayName 'WSL Web Server' -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow

# get your host ip address
Get-NetIPAddress | Where-Object { $_.AddressFamily -eq 'IPv4' -and $_.PrefixOrigin -eq 'Dhcp' } | Select-Object -ExpandProperty IPAddress

x# get rid of all rules when done if you want
netsh interface portproxy reset

```


New-NetFirewallRule -DisplayName 'WSL Web Server' -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow

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