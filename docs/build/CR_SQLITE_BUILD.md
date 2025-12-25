# Building cr-sqlite for Android

This guide details the process of building cr-sqlite for Android devices and emulators.

## Prerequisites

- Rust toolchain with nightly version
- Android NDK (version 26.1.10909125 recommended)
- cargo-ndk

## ⚠️ Important Warning

The Android NDK version you use **must** match your target operating system:
- Linux → Use Linux NDK
- Windows → Use Windows NDK
- macOS → Use macOS NDK

Using an NDK from a different OS will cause build failures that are difficult to debug.

## Build Instructions

### For x86_64 (Emulator)

1. Set up the environment:
```bash
# Set NDK path
export ANDROID_NDK_HOME=/path/to/your/ndk/26.1.10909125

# Install Rust nightly and components
brew install rustup
rustup toolchain install nightly
rustup target add x86_64-linux-android --toolchain nightly
rustup component add rust-src --toolchain nightly

# Install cargo-ndk
cargo install cargo-ndk
```

2. Build cr-sqlite:
```bash
# Clone repository
git clone https://github.com/vlcn-io/cr-sqlite.git
cd cr-sqlite

# Build loadable extension
cd core
export ANDROID_NDK_HOME=/home/william/android/ndk/26.1.10909125
export ANDROID_TARGET=x86_64-linux-android
make loadable 
```

3. Copy the .so file:
```bash
# from project root
mkdir -p android/app/src/main/jniLibs/x86_64

# from cr-sqlite root
cp core/dist/crsqlite.so android/app/src/main/jniLibs/x86_64/crsqlite.so
```

### For arm64-v8a (Physical Devices)

Use our convenience script:
```bash
# Set NDK path
export ANDROID_NDK_HOME=/path/to/your/ndk/26.1.10909125

# Run build script
yarn build:crsqlite:arm64
```

## Troubleshooting

### Common Issues

1. **Linker Errors**
   - Verify NDK matches host OS
   - Check NDK version in build.gradle

2. **Missing arm64-v8a Target**
   ```bash
   rustup target add aarch64-linux-android --toolchain nightly
   ```

3. **Nightly Channel Issues**
   ```bash
   # Use nightly explicitly
   rustup run nightly make loadable
   
   # Or temporarily set as default
   rustup default nightly
   make loadable
   rustup default stable  # Switch back after
   ```

4. **Homebrew Rust Conflicts**
   - Check Rust source: `which cargo`
   - Remove Homebrew Rust: `brew uninstall rust`
   - Install official Rust: https://www.rust-lang.org/tools/install
   - Set up nightly: `rustup toolchain install nightly`

5. **Missing Submodules**
   ```bash
   # Clean and retry
   rm -rf tmp
   
   # Or manually initialize
   cd tmp/cr-sqlite
   git submodule update --init --recursive
   ``` 