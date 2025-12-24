#!/usr/bin/env node
/**
 * Bundle wrapper script that copies pre-built bundle instead of running Metro.
 * This works around NativeWind's Windows ESM path issues.
 * 
 * Called by Gradle as: node bundle_or_skip.js bundle --platform android --bundle-output ...
 * 
 * If a pre-built bundle exists in assets/, copies it to the Gradle output location.
 * If no pre-built bundle exists, runs Metro (will fail on Windows with NativeWind).
 * 
 * Pre-bundle on WSL/Linux: yarn bundle:android --dev=true
 * Then Windows builds will use the copied bundle.
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Args: ['bundle', '--platform', 'android', '--bundle-output', '/path/to/bundle', ...]
const args = process.argv.slice(2);

console.log('[bundle_or_skip] Called with args:', args.slice(0, 6).join(' '), '...');

// Parse --bundle-output from args (where Gradle expects the bundle)
let gradleBundlePath = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--bundle-output' && args[i + 1]) {
    gradleBundlePath = args[i + 1];
    break;
  }
}

// Our pre-built bundle location (from yarn bundle:android)
const preBuiltBundle = path.join(__dirname, '../android/app/src/main/assets/index.android.bundle');
const preBuiltSourceMap = preBuiltBundle + '.map';

console.log('[bundle_or_skip] Pre-built bundle:', preBuiltBundle);
console.log('[bundle_or_skip] Gradle expects:', gradleBundlePath || '(default)');

// Check if pre-built bundle exists
if (fs.existsSync(preBuiltBundle)) {
  const stats = fs.statSync(preBuiltBundle);
  const ageMs = Date.now() - stats.mtimeMs;
  const ageHours = ageMs / (1000 * 60 * 60);
  const sizeKb = (stats.size / 1024).toFixed(0);
  
  console.log(`[bundle_or_skip] ✓ Pre-built bundle found (${sizeKb} KB, ${ageHours.toFixed(1)} hours old)`);
  
  if (ageHours > 24) {
    console.log('[bundle_or_skip] ⚠ Bundle is old, consider re-bundling: yarn bundle:android --dev=true');
  }
  
  // If Gradle wants it somewhere else, copy it there
  if (gradleBundlePath && gradleBundlePath !== preBuiltBundle) {
    // Ensure directory exists
    const dir = path.dirname(gradleBundlePath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    
    // Copy bundle
    console.log('[bundle_or_skip] Copying bundle to Gradle location...');
    fs.copyFileSync(preBuiltBundle, gradleBundlePath);
    
    // Copy or create source map next to bundle
    const gradleSourceMap = gradleBundlePath + '.map';
    if (fs.existsSync(preBuiltSourceMap)) {
      fs.copyFileSync(preBuiltSourceMap, gradleSourceMap);
    } else {
      fs.writeFileSync(gradleSourceMap, '{"version":3,"sources":[],"names":[],"mappings":""}');
    }
    
    // Also create the packager source map that compose-source-maps.js expects
    // Path: build/intermediates/sourcemaps/react/{variant}/index.android.bundle.packager.map
    const packagerSourceMapDir = path.join(__dirname, '../android/app/build/intermediates/sourcemaps/react');
    const variants = ['x8664Debug', 'arm64V8aDebug', 'armeabiV7aDebug', 'x86Debug', 'debug'];
    for (const variant of variants) {
      const variantDir = path.join(packagerSourceMapDir, variant);
      if (!fs.existsSync(variantDir)) {
        fs.mkdirSync(variantDir, { recursive: true });
      }
      const packagerMapPath = path.join(variantDir, 'index.android.bundle.packager.map');
      if (fs.existsSync(preBuiltSourceMap)) {
        fs.copyFileSync(preBuiltSourceMap, packagerMapPath);
      } else {
        fs.writeFileSync(packagerMapPath, '{"version":3,"sources":[],"names":[],"mappings":""}');
      }
    }
    
    console.log('[bundle_or_skip] ✓ Bundle and source maps copied successfully');
  }
  
  console.log('[bundle_or_skip] Skipping Metro - using pre-built bundle');
  process.exit(0);
}

// No pre-built bundle - try to run Metro (will fail on Windows with NativeWind)
console.log('[bundle_or_skip] ✗ No pre-built bundle found at:', preBuiltBundle);
console.log('[bundle_or_skip] Attempting to run Metro bundler...');
console.log('[bundle_or_skip] If this fails on Windows, pre-bundle on WSL: yarn bundle:android --dev=true');

try {
  // Run the actual React Native CLI
  const cliPath = path.join(__dirname, '../node_modules/react-native/cli.js');
  const cmd = `node "${cliPath}" ${args.join(' ')}`;
  execSync(cmd, { stdio: 'inherit', cwd: path.join(__dirname, '..') });
} catch (error) {
  console.error('\n[bundle_or_skip] ✗ Bundling failed!');
  console.error('[bundle_or_skip] On Windows with NativeWind, you must pre-bundle on WSL/Linux:');
  console.error('[bundle_or_skip]   cd /path/to/project && yarn bundle:android --dev=true');
  console.error('[bundle_or_skip] Then retry the Windows build.');
  process.exit(1);
}

