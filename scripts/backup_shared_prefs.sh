#!/bin/bash

# Backup Android shared preferences for a specific package
#
# This script backs up all shared preference files from an Android app's
# shared_prefs directory to the current directory.
#
# Usage: ./backup_shared_prefs.sh
#
# Requirements:
# - ADB must be installed and accessible in PATH
# - Device must be connected and authorized for debugging
# - App must be debuggable

set -euo pipefail

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

# Define the package name and shared_prefs directory
PACKAGE="com.github.quarck.calnotify"
SHARED_PREFS_DIR="/data/data/${PACKAGE}/shared_prefs"

# Create and ensure /tmp directory exists in project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="${PROJECT_ROOT}/tmp"
BACKUP_DIR="${TMP_DIR}/shared_prefs_backup_$(date +%Y%m%d_%H%M%S)"

echo "Starting backup of shared preferences..."
echo "Package: ${PACKAGE}"
echo "Source Directory: ${SHARED_PREFS_DIR}"

# Create backup directory
mkdir -p "${BACKUP_DIR}"
echo "Created backup directory: ${BACKUP_DIR}"

# List files in the shared_prefs directory
echo "Retrieving list of preference files..."
FILES=$(adb shell run-as "${PACKAGE}" ls "${SHARED_PREFS_DIR}") || {
    echo "Error: Failed to list files in ${SHARED_PREFS_DIR}"
    echo "Please ensure the app is debuggable and installed on the device."
    rm -r "${BACKUP_DIR}"
    exit 1
}

if [ -z "${FILES}" ]; then
    echo "Error: No preference files found."
    rm -r "${BACKUP_DIR}"
    exit 1
fi

echo "Found the following preference files:"
echo "${FILES}"

# Loop through each file and copy it to the backup directory
echo "Starting file backup..."
BACKUP_COUNT=0
while IFS= read -r FILE; do
    [ -z "${FILE}" ] && continue
    
    echo "Backing up ${FILE}..."
    REMOTE_FILE="${SHARED_PREFS_DIR}/${FILE}"
    
    if adb shell run-as "${PACKAGE}" cat "${REMOTE_FILE}" > "${BACKUP_DIR}/${FILE}"; then
        echo "✓ Successfully backed up ${FILE}"
        ((BACKUP_COUNT++))
    else
        echo "✗ Failed to backup ${FILE}"
    fi
done <<< "${FILES}"

echo
if [ ${BACKUP_COUNT} -gt 0 ]; then
    echo "Backup completed successfully!"
    echo "Total files backed up: ${BACKUP_COUNT}"
    echo "Backup location: ${BACKUP_DIR}/"
else
    echo "Error: No files were backed up successfully."
    rm -r "${BACKUP_DIR}"
    exit 1
fi
