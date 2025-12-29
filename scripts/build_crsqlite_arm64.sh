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

# Pin to specific nightly version - cr-sqlite uses concat_idents feature removed in Rust 1.90.0
RUST_NIGHTLY_VERSION="nightly-2023-10-05"

echo "=== Checking for rustup ==="
if ! command -v rustup &> /dev/null; then
    echo "ERROR: rustup is required but not installed!"
    echo ""
    echo "Install from: https://rustup.rs/"
    exit 1
fi

echo "=== Installing required tools ==="
echo "Using pinned Rust version: $RUST_NIGHTLY_VERSION"
echo "(cr-sqlite requires this specific version due to concat_idents feature removal in newer Rust)"
rustup toolchain install $RUST_NIGHTLY_VERSION || true
rustup target add aarch64-linux-android --toolchain $RUST_NIGHTLY_VERSION || true
rustup component add rust-src --toolchain $RUST_NIGHTLY_VERSION || true
cargo install cargo-ndk || true

# Verify the toolchain was installed
if ! rustup run $RUST_NIGHTLY_VERSION cargo --version &> /dev/null; then
    echo "ERROR: Failed to install $RUST_NIGHTLY_VERSION toolchain!"
    echo "Try manually running: rustup toolchain install $RUST_NIGHTLY_VERSION"
    exit 1
fi

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

# Use pinned nightly version for the make command
rustup run $RUST_NIGHTLY_VERSION make loadable

echo "=== Copying compiled .so file to project ==="
mkdir -p "$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a"
# Renamed to crsqlite_requery.so to avoid conflict with React Native's crsqlite.so
cp "$CR_SQLITE_DIR/core/dist/crsqlite.so" "$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a/crsqlite_requery.so"

echo "=== Build completed successfully ==="
echo "The crsqlite_requery.so file has been placed in android/app/src/main/jniLibs/arm64-v8a/" 