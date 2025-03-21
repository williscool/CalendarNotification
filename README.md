# Calendar Notifications Plus React Native

## Prerequisites
For the original ReadMe, see [android/README.md](android/README.md)

## Building

```bash
yarn 
yarn start

```

## Running an emulator

```bash
cd C:\Users\<username>\appdata\local\android\sdk
.\emulator\emulator.exe -avd 7.6_Fold-in_with_outer_display_API_34q
```

## Setting up adb

https://gist.github.com/bergmannjg/461958db03c6ae41a66d264ae6504ade?permalink_comment_id=4149833#gistcomment-4149833

```bash
sudo ln -s /mnt/c/Users/<username>/AppData/Local/Android/Sdk/platform-tools/adb.exe /home/<username>/android/platform-tools/adb
```

## Wsl metro connection setup

```bash

# Get the WSL2 IP address and store it in a variable
WSL_VM_IP_ADDRESS=$(ifconfig eth0 | awk '/inet / {print $2}')

# Get the Windows HOST IP address and store it in a variable (in case you need it)
HOST_IP_ADDRESS=$(ipconfig.exe | awk '/Ethernet adapter vEthernet \(Default Switch\):/{i=1; next} i && /IPv4 Address/{print $NF; exit}' | sed 's/^[ \t]*//')

# Open the developer menu in the React Native activity once loaded
adb shell input keyevent 82

# Input the IP address in the developer menu
adb shell input text "${WSL_VM_IP_ADDRESS}:8081"


```


## Debugging

WARNING: BY DEFAULT IN THE CHROME DEBUGGER Expo Module Function does not work AT ALL! https://docs.expo.dev/modules/module-api/#function 

(WHY THIS ISNT IN BIG BOLD RED LETTERS AS A NOTE ON THE DOCUMENTATION FOR SYNC FUNCTIONS IS BEYOND ME!)

this is because either the methods are using  @ReactMethod(isBlockingSynchronousMethod = true) 

https://reactnative.dev/docs/native-modules-android#synchronous-methods

and or JSI, a JavaScript interface for native code, is being used which requires the JS VM to share memory with the app.

For the Google Chrome debugger, React Native runs inside the JS VM in Google Chrome, and communicates asynchronously with the mobile devices via WebSockets.

So it wont have access to the native modules.

Alternative setups:

Hermes on Chrome - https://reactnative.dev/docs/hermes?package-manager=yarn#debugging-js-on-hermes-using-google-chromes-devtools

VSCode - https://marketplace.visualstudio.com/items?itemName=msjsdiag.vscode-react-native

Flipper - https://fbflipper.com/


LOST A LOT OF TIME ON THIS! so wanted to document it here.

Sources:
https://github.com/williscool/CalendarNotification/issues/13#issuecomment-1760712053
https://reactnative.dev/docs/native-modules-android?android-language=kotlin#synchronous-methods

## Debugging Hermes on Chrome

WARNING: THIS DOES NOT WORK IN DEVELOPMENT MODE! YOU MUST BUILD YOU ANDROID APP IN RELEASE MODE!
(WHY THIS ISNT IN BIG BOLD RED LETTERS AS A NOTE ON THE DOCUMENTATION FOR DEBUGGING HERMES IS BEYOND ME!)

the instructions do say to to do a release build, but it doesnt say that it wont work in development mode!

if you want to try to get to work these gusy https://github.com/gusgard/react-native-devsettings

are working on it
https://github.com/jhen0409/react-native-debugger/issues/573#issuecomment-1533894331


Sources:
- google: react native hemes dev tools
- https://stackoverflow.com/questions/76604735/expo-v48-remote-debugging-w-hermes


## Building

### Debug

works like normal

### Release Local


https://instamobile.io/android-development/generate-react-native-release-build-android/

build the bundle ahead of time

```bash
yarn  react-native bundle --platform android --dev false --entry-file index.tsx --bundle-output android/app/src/main/assets/index.android.bundle  --assets-dest android/app/src/main/res/
```

Android studio instructions: https://stackoverflow.com/questions/18460774/how-to-set-up-gradle-and-android-studio-to-do-release-build


### Release CI
see .github/workflows/actions.yml

## Building cr-sqlite for Android

### Prerequisites

- Rust toolchain with nightly version
- Android NDK (version 26.1.10909125 recommended)
- cargo-ndk

### Important Warning ⚠️

The Android NDK version you use **must** match your target operating system. For example:
- If building on Linux, use the Linux NDK
- If building on Windows, use the Windows NDK
- If building on macOS, use the macOS NDK

Using an NDK from a different OS (e.g., using Windows NDK on Linux) will cause hard-to-debug build failures.

### Build Instructions

#### Build for x86_64 (Emulator)

1. Set up the environment:
```bash
# Set NDK path - adjust this to your NDK installation location
export ANDROID_NDK_HOME=/path/to/your/ndk/26.1.10909125

# Install Rust nightly and required components
brew install rustup
rustup toolchain install nightly
rustup target add x86_64-linux-android --toolchain nightly
rustup component add rust-src --toolchain nightly

# Install cargo-ndk
cargo install cargo-ndk
```

2. Clone and build cr-sqlite:
```bash
# Clone the repository
git clone https://github.com/vlcn-io/cr-sqlite.git
cd cr-sqlite

# Build the loadable extension
cd core
export ANDROID_NDK_HOME=/home/william/android/ndk/26.1.10909125
export ANDROID_TARGET=x86_64-linux-android
make loadable 
```

3. The compiled `.so` file will be in `core/dist/`. Copy it to your project:
```bash
# from the root of the project
mkdir -p android/app/src/main/jniLibs/x86_64

# from the root of your cr-sqlite checkout
cp core/dist/crsqlite.so android/app/src/main/jniLibs/x86_64/crsqlite.so
```

#### Build for arm64-v8a (Physical Devices like Pixel 9 Pro)

You can use the convenience script we've added to build for arm64-v8a:

```bash
# Set NDK path - adjust this to your NDK installation location
export ANDROID_NDK_HOME=/path/to/your/ndk/26.1.10909125

# Run the build script
yarn build:crsqlite:arm64
```

This will clone the cr-sqlite repository, build for arm64-v8a, and copy the resulting .so file to the correct location in your project.

### Troubleshooting

- If you see linker errors, double-check that your NDK matches your host OS
- Ensure the NDK version matches what's specified in your project's `build.gradle`
- For arm64-v8a builds, make sure you have the aarch64-linux-android target installed: `rustup target add aarch64-linux-android --toolchain nightly`
- If you see an error like `the -Z flag is only accepted on the nightly channel of Cargo`, ensure you're explicitly using the nightly toolchain:
  ```
  rustup run nightly make loadable
  ```
  or set the default to nightly temporarily:
  ```
  rustup default nightly
  make loadable
  rustup default stable  # Switch back when done
  ```
- **Homebrew Rust vs. Official Rust**: If you have Homebrew's Rust installed, it may conflict with rustup's version:
  - Check which Rust you're using: `which cargo` and `cargo --version`
  - If using Homebrew's Rust, uninstall it: `brew uninstall rust`
  - Install the official Rust toolchain from https://www.rust-lang.org/tools/install
  - Install nightly: `rustup toolchain install nightly`
  - Set nightly as default: `rustup default nightly`
  - Or use the `+nightly` flag with cargo commands and `rustup run nightly` with other commands
- **Missing submodule files**: If you see errors like `failed to read .../sqlite_nostd/Cargo.toml`, the Git submodules were not properly initialized:
  - Remove the tmp directory: `rm -rf tmp`
  - Run the build script again, which will re-clone the repository with proper submodule initialization
  - Or manually initialize submodules: `cd tmp/cr-sqlite && git submodule update --init --recursive`

## Dependencies Note

### Removal of react-native-safe-area-context despite the usage of react-native-screens for Data Sync UI

The `react-native-safe-area-context` package has been removed from the project's dependencies due to build issues. This package is typically used alongside `react-native-screens` for handling safe area insets and providing proper layout around notches, status bars, and home indicators.

#### Relationship with react-native-screens

`react-native-screens` does not directly depend on `react-native-safe-area-context` at the code level, but they are often used together in React Navigation implementations. React Navigation requires both packages as peer dependencies for proper functioning.

Key points about this relationship:

1. `react-native-screens` optimizes the native view hierarchy by using native platform components for screens.
2. `react-native-safe-area-context` provides inset values and components (like `SafeAreaView`) to handle device-specific UI features safely.
3. React Navigation uses both packages to create properly inset navigation components.

#### Mock Implementation Solution

Since removing `react-native-safe-area-context` causes dependency errors with React Navigation, we've implemented a mock version of the package. This mock provides dummy implementations of the required components and hooks without the native code that was causing build issues.

The mock is automatically created during the `yarn install` process via a postinstall script. You can manually run the setup with:

```bash
node scripts/setup_safe_area_mock.js
```

##### Mock Implementation Structure

For easier review and maintenance, the mock implementation templates are organized in separate files:

- `scripts/safe-area-mock-templates/` - Directory containing all template files
  - `package.json` - Package metadata
  - `module-index.js` - ES modules implementation
  - `commonjs-index.js` - CommonJS implementation
  - `typescript-index.d.ts` - TypeScript definition

The `setup_safe_area_mock.js` script combines these templates to create the mock package at runtime.

This approach allows us to:
1. Keep using React Navigation without errors
2. Avoid the build issues caused by the native implementation
3. Ensure consistent behavior in both development and CI environments
4. Easily review and maintain the mock implementation

Note: This is a temporary solution until we can update our SDK versions to support both packages without breaking the build. UI elements might not properly respect safe areas on notched devices or devices with system UI elements that intrude into the screen area.


