#!/usr/bin/env node

/**
 * Script to manually generate the React Native bundle for Android
 * This is useful for local testing without using Android Studio
 * 
 * Usage: node scripts/bundle_android.js [--dev=true]
 * Note: --dev=false is the default (production mode) and doesn't need to be specified
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// Parse command line arguments
const args = process.argv.slice(2);
const devArg = args.find(arg => arg.startsWith('--dev='));
const isDev = devArg ? devArg.split('=')[1] === 'true' : false;

// Paths
const ASSETS_DIR = path.join(__dirname, '../android/app/src/main/assets');
const BUNDLE_OUTPUT = path.join(ASSETS_DIR, 'index.android.bundle');
const SOURCEMAP_OUTPUT = BUNDLE_OUTPUT + '.map';
const ASSETS_DEST = path.join(__dirname, '../android/app/src/main/res');

// Create assets directory if it doesn't exist
if (!fs.existsSync(ASSETS_DIR)) {
  console.log(`Creating assets directory: ${ASSETS_DIR}`);
  fs.mkdirSync(ASSETS_DIR, { recursive: true });
}

// Build command (includes source map for debugging and to satisfy Gradle's compose-source-maps)
const command = `yarn react-native bundle --platform android --dev ${isDev} --entry-file index.tsx --bundle-output ${BUNDLE_OUTPUT} --sourcemap-output ${SOURCEMAP_OUTPUT} --assets-dest ${ASSETS_DEST}`;

console.log(`Generating React Native bundle for Android (dev mode: ${isDev})...`);

try {
  execSync(command, { stdio: 'inherit' });
  console.log('\nBundle generated successfully!');
  console.log(`Bundle location: ${BUNDLE_OUTPUT}`);
  console.log(`Assets location: ${ASSETS_DEST}`);
} catch (error) {
  console.error('Failed to generate bundle:', error.message);
  process.exit(1);
} 