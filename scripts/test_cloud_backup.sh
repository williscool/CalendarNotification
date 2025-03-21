#!/bin/bash

# Test Android cloud backup functionality
#
# This script tests the Android backup and restore functionality for a given package.
# It performs a backup, uninstalls the app, and then reinstalls it to test restore.
#
# Usage: ./test_cloud_backup.sh <package_name>
#
# Requirements:
# - ADB must be installed and accessible in PATH
# - Device must be connected and authorized for debugging
# - App must have backup enabled in manifest

set -euo pipefail

# Check if package name is provided
if [ $# -ne 1 ]; then
    echo "Error: Package name not provided"
    echo "Usage: $0 <package_name>"
    exit 1
fi

PACKAGE_NAME="$1"

# Create and ensure /tmp directory exists in project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="${PROJECT_ROOT}/tmp"
TEMP_APK_DIR="${TMP_DIR}/apk_backup_$(date +%Y%m%d_%H%M%S)"

# Check if adb is available
if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb command not found. Please install Android SDK platform tools."
    exit 1
fi

# Check if device is connected
if ! adb get-state >/dev/null 2>&1; then
    echo "Error: No Android device connected. Please connect a device and try again."
    exit 1
fi

# Create temporary directory for APKs
mkdir -p "${TEMP_APK_DIR}"

echo "Starting cloud backup test for package: ${PACKAGE_NAME}"

# Initialize and create a backup
echo "1. Configuring backup settings..."
adb shell bmgr enable true
if ! adb shell bmgr transport com.android.localtransport/.LocalTransport | grep -q "Selected transport"; then
    echo "Error: Failed to select local transport"
    rm -rf "${TEMP_APK_DIR}"
    exit 1
fi

echo "2. Setting backup encryption..."
adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true'

echo "3. Initiating backup..."
if ! adb shell bmgr backupnow "${PACKAGE_NAME}" | grep -F "Package ${PACKAGE_NAME} with result: Success"; then
    echo "Error: Backup failed"
    rm -rf "${TEMP_APK_DIR}"
    exit 1
fi

echo "4. Extracting current APK(s)..."
apk_path_list=$(adb shell pm path "${PACKAGE_NAME}")
if [ -z "${apk_path_list}" ]; then
    echo "Error: Could not find APK paths for ${PACKAGE_NAME}"
    rm -rf "${TEMP_APK_DIR}"
    exit 1
fi

# Save original IFS
ORIGINAL_IFS=$IFS
IFS=$'\n'
apk_number=0
for apk_line in $apk_path_list; do
    ((apk_number++))
    apk_path=${apk_line:8:1000}  # Remove "package:" prefix
    if ! adb pull "${apk_path}" "${TEMP_APK_DIR}/app_${apk_number}.apk"; then
        echo "Error: Failed to pull APK from ${apk_path}"
        IFS=$ORIGINAL_IFS
        rm -rf "${TEMP_APK_DIR}"
        exit 1
    fi
    echo "✓ Pulled APK ${apk_number}: ${apk_path}"
done
IFS=$ORIGINAL_IFS

echo "5. Uninstalling application..."
if ! adb shell pm uninstall --user 0 "${PACKAGE_NAME}"; then
    echo "Error: Failed to uninstall ${PACKAGE_NAME}"
    rm -rf "${TEMP_APK_DIR}"
    exit 1
fi

echo "6. Reinstalling application..."
apk_files=$(find "${TEMP_APK_DIR}" -name "*.apk" | tr '\n' ' ')
if ! adb install-multiple -t --user 0 ${apk_files}; then
    echo "Error: Failed to reinstall APKs"
    rm -rf "${TEMP_APK_DIR}"
    exit 1
fi

echo "7. Restoring backup transport..."
adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService

echo "8. Cleaning up temporary files..."
rm -rf "${TEMP_APK_DIR}"

echo
echo "✓ Cloud backup test completed successfully!"
echo "Please verify that your app data has been restored correctly."