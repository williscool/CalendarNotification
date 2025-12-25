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

# Start Metro bundler (run from WSL)
yarn start
```

### NativeWind + Windows Build Workflow

NativeWind has a known issue with Windows ESM paths that prevents Metro from running on Windows. The workaround is to pre-bundle on WSL, then build on Windows.

**One-time (or when JS/CSS changes):**
```bash
# On WSL
yarn bundle:android --dev=true
```

**Then on Windows (as many times as needed):**
```powershell
cd X:\android  # or C:\dev\CN\android
.\gradlew assembleDebug   # Uses pre-built bundle
.\gradlew installDebug    # Install to device
```

The `bundle_or_skip.js` script automatically detects and copies the pre-built bundle, skipping Metro entirely on Windows. See [NativeWind #1667](https://github.com/nativewind/nativewind/issues/1667) for the upstream issue.

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

1. Build the bundle (from WSL):
```bash
# For release (production mode)
yarn bundle:android

# For debug (development mode with source maps)
yarn bundle:android --dev=true
```

2. Build the APK (can be done from Windows):
```powershell
cd X:\android
.\gradlew assembleRelease
```

3. Follow Android Studio instructions for signing configuration

### CI Release Build

Refer to `.github/workflows/actions.yml` for the complete CI build process.

## Building cr-sqlite for Android

See [CR_SQLITE_BUILD.md](CR_SQLITE_BUILD.md) for detailed instructions on building cr-sqlite for Android. 