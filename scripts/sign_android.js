#!/usr/bin/env node

/**
 * Script to sign Android APKs using a provided keystore
 * 
 * @description This script takes a keystore and its credentials to sign Android APK files.
 *              It processes all APK files in the debug output directory and creates signed versions.
 * 
 * @usage node scripts/sign_android.js --keystore path/to/keystore.jks --alias your_alias --storePassword your_store_password --keyPassword your_key_password
 * 
 * @param {string} --keystore - Path to the keystore file (.jks)
 * @param {string} --alias - Keystore alias name
 * @param {string} --storePassword - Password for the keystore
 * @param {string} --keyPassword - Password for the key
 */

const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

// Constants
const SIGNATURE_ALGORITHM = 'SHA1withRSA';
const DIGEST_ALGORITHM = 'SHA1';

// Parse command line arguments
const args = process.argv.slice(2);
const options = {};
for (let i = 0; i < args.length; i += 2) {
  const key = args[i].replace('--', '');
  const value = args[i + 1];
  if (key && value) {
    options[key] = value;
  }
}

// Required parameters
const requiredParams = ['keystore', 'alias', 'storePassword', 'keyPassword'];
const missingParams = requiredParams.filter(param => !options[param]);

if (missingParams.length > 0) {
  console.error('Error: Missing required parameters:', missingParams.join(', '));
  console.error('\nUsage:');
  console.error('node scripts/sign_android.js --keystore path/to/keystore.jks --alias your_alias --storePassword your_store_password --keyPassword your_key_password');
  process.exit(1);
}

// Verify keystore exists and is accessible
try {
  const keystoreStats = fs.statSync(options.keystore);
  if (!keystoreStats.isFile()) {
    console.error(`Error: Keystore path is not a file: ${options.keystore}`);
    process.exit(1);
  }
} catch (error) {
  console.error(`Error: Cannot access keystore file: ${options.keystore}`);
  console.error(error.message);
  process.exit(1);
}

// Paths
const APK_DIR = path.join(__dirname, '../android/app/build/outputs/apk/debug');
const SIGNED_APK_DIR = path.join(__dirname, '../android/app/build/outputs/apk/debug/signed');

// Create signed directory if it doesn't exist
try {
  fs.mkdirSync(SIGNED_APK_DIR, { recursive: true });
} catch (error) {
  console.error('Error: Failed to create output directory:', error.message);
  process.exit(1);
}

// Find all APKs in the debug directory
const apks = fs.readdirSync(APK_DIR).filter(file => file.endsWith('.apk'));

if (apks.length === 0) {
  console.error('Error: No APKs found in:', APK_DIR);
  console.error('Please run yarn android:build:debug first');
  process.exit(1);
}

console.log('Found APKs:', apks);

// Sign each APK
apks.forEach(apk => {
  const inputPath = path.join(APK_DIR, apk);
  const outputPath = path.join(SIGNED_APK_DIR, `signed-${apk}`);
  
  console.log(`\nSigning ${apk}...`);
  
  const command = [
    'jarsigner',
    '-verbose',
    `-sigalg ${SIGNATURE_ALGORITHM}`,
    `-digestalg ${DIGEST_ALGORITHM}`,
    `-keystore "${options.keystore}"`,
    `-storepass "${options.storePassword}"`,
    `-keypass "${options.keyPassword}"`,
    `-signedjar "${outputPath}"`,
    `"${inputPath}"`,
    `"${options.alias}"`
  ].join(' ');
  
  try {
    execSync(command, { stdio: 'inherit' });
    console.log(`Successfully signed: ${outputPath}`);
  } catch (error) {
    console.error(`Error: Failed to sign ${apk}:`, error.message);
    process.exit(1);
  }
});

console.log('\nAll APKs signed successfully!');
console.log('Signed APKs location:', SIGNED_APK_DIR); 