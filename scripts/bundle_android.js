#!/usr/bin/env node

/**
 * Script to manually generate the React Native bundle for Android
 * This is useful for local testing without using Android Studio
 * 
 * Usage: node scripts/bundle_android.js
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// Paths
const ASSETS_DIR = path.join(__dirname, '../android/app/src/main/assets');
const BUNDLE_OUTPUT = path.join(ASSETS_DIR, 'index.android.bundle');
const ASSETS_DEST = path.join(__dirname, '../android/app/src/main/res');

// Create assets directory if it doesn't exist
if (!fs.existsSync(ASSETS_DIR)) {
  console.log(`Creating assets directory: ${ASSETS_DIR}`);
  fs.mkdirSync(ASSETS_DIR, { recursive: true });
}

// Build command
const command = `yarn react-native bundle --platform android --dev false --entry-file index.tsx --bundle-output ${BUNDLE_OUTPUT} --assets-dest ${ASSETS_DEST}`;

console.log('Generating React Native bundle for Android...');
console.log(command);

try {
  execSync(command, { stdio: 'inherit' });
  console.log('\nBundle generated successfully!');
  console.log(`Bundle location: ${BUNDLE_OUTPUT}`);
  console.log(`Assets location: ${ASSETS_DEST}`);
} catch (error) {
  console.error('Failed to generate bundle:', error.message);
  process.exit(1);
} 