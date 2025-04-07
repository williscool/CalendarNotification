#!/bin/bash

# Default native build artifacts directory path
DEFAULT_BUILD_DIR="android/app/build/intermediates/cmake"

# Check if path argument is provided, otherwise use default
BUILD_DIR=${1:-$DEFAULT_BUILD_DIR}

# Validate input
if [ -z "$BUILD_DIR" ]; then
  echo "❌ Error: Build artifacts directory path cannot be empty"
  exit 1
fi

echo "🧪 Checking native build artifacts"
if [ -d "$BUILD_DIR" ]; then
  echo "✅ Native build directory exists at $BUILD_DIR"
  
  # Count all files in the directory
  FILE_COUNT=$(find "$BUILD_DIR" -type f | wc -l)
  echo "📊 Total files in build directory: $FILE_COUNT"
  
  # Count all .so files in the directory and subdirectories
  SO_COUNT=$(find "$BUILD_DIR" -name "*.so" | wc -l)
  echo "📁 Total .so files: $SO_COUNT"
  
  # Show directory tree
  echo "🌳 Directory tree:"
  tree "$BUILD_DIR"
else
  echo "❌ Native build directory does not exist at $BUILD_DIR"
  echo "⚠️ This may cause the build to rebuild native libraries multiple times"
fi 