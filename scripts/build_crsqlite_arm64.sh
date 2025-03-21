#!/bin/bash

# Exit on error
set -e

# Define variables
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_DIR="$PROJECT_ROOT/tmp"
CR_SQLITE_DIR="$TMP_DIR/cr-sqlite"

echo "=== Setting up environment ==="
# Check if NDK_HOME is set
if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "Please set ANDROID_NDK_HOME environment variable"
    exit 1
fi

echo "=== Checking for nightly cargo ==="
# Check if cargo is nightly version
if ! cargo --version | grep -q "nightly"; then
    echo "ERROR: Nightly version of cargo required!"
    echo ""
    echo "If you are using Rust from Homebrew:"
    echo "  brew uninstall rust"
    echo ""
    echo "And install from the official source:"
    echo "  https://www.rust-lang.org/tools/install"
    echo ""
    echo "Then run:"
    echo "  rustup toolchain install nightly"
    echo "  rustup default nightly"
    echo ""
    echo "Or you are likely gonna have a bad time."
    exit 1
fi

echo "=== Installing required tools ==="
rustup toolchain install nightly || true
rustup target add aarch64-linux-android --toolchain nightly || true
rustup component add rust-src --toolchain nightly || true
cargo +nightly install cargo-ndk || true

echo "=== Creating temp directory ==="
mkdir -p "$TMP_DIR"
cd "$TMP_DIR"

echo "=== Cloning cr-sqlite repository ==="
if [ ! -d "$CR_SQLITE_DIR" ]; then
    # Clone with HTTPS instead of SSH to avoid authentication issues
    git clone https://github.com/vlcn-io/cr-sqlite.git
    cd "$CR_SQLITE_DIR"
    # Initialize and update all submodules recursively
    git submodule update --init --recursive
else
    echo "cr-sqlite directory already exists, updating existing clone"
    cd "$CR_SQLITE_DIR"
    git pull
    # Make sure submodules are up to date
    git submodule update --init --recursive
fi

# Verify the SQLite embedded directory exists
if [ ! -d "$CR_SQLITE_DIR/core/rs/sqlite-rs-embedded/sqlite_nostd" ]; then
    echo "ERROR: SQLite embedded directory not found!"
    echo "Submodule initialization might have failed."
    echo "Try removing the tmp directory and running the script again:"
    echo "  rm -rf $TMP_DIR"
    exit 1
fi

cd "$CR_SQLITE_DIR/core"

echo "=== Building crsqlite for arm64-v8a ==="
export ANDROID_TARGET=aarch64-linux-android

# Use nightly explicitly for the make command
rustup run nightly make loadable

echo "=== Copying compiled .so file to project ==="
mkdir -p "$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a"
cp "$CR_SQLITE_DIR/core/dist/crsqlite.so" "$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a/"

echo "=== Build completed successfully ==="
echo "The crsqlite.so file has been placed in android/app/src/main/jniLibs/arm64-v8a/" 