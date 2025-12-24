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
 * 
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// ============================================================================
// Constants
// ============================================================================

const LOG_PREFIX = '[bundle_or_skip]';
const BUNDLE_FILENAME = 'index.android.bundle';
const SOURCEMAP_EXTENSION = '.map';
const PACKAGER_SOURCEMAP_FILENAME = 'index.android.bundle.packager.map';
const EMPTY_SOURCEMAP = '{"version":3,"sources":[],"names":[],"mappings":""}';
const BUNDLE_AGE_WARNING_HOURS = 24;

const DEFAULT_ARCHS = ['arm64-v8a', 'x86_64'];
const DEFAULT_BUILD_TYPES = ['debug', 'release'];

// Relative paths from this script
const PATHS = {
  rootBuildGradle: '../android/build.gradle',
  appBuildGradle: '../android/app/build.gradle',
  preBuiltBundle: '../android/app/src/main/assets/' + BUNDLE_FILENAME,
  packagerSourceMapDir: '../android/app/build/intermediates/sourcemaps/react',
  reactNativeCli: '../node_modules/react-native/cli.js',
};

// ============================================================================
// Logging Helpers
// ============================================================================

const log = (msg) => console.log(`${LOG_PREFIX} ${msg}`);
const logSuccess = (msg) => console.log(`${LOG_PREFIX} ✓ ${msg}`);
const logWarning = (msg) => console.log(`${LOG_PREFIX} ⚠ ${msg}`);
const logError = (msg) => console.error(`${LOG_PREFIX} ✗ ${msg}`);

// ============================================================================
// Gradle Parsing Functions (exported for testing)
// ============================================================================

/**
 * Parse supported architectures from build.gradle content
 * Looks for: supportedArchs = ['arm64-v8a', 'x86_64']
 * @param {string} content - The content of build.gradle
 * @returns {string[]|null} Array of architectures or null if not found
 */
function parseArchsFromGradle(content) {
  const match = content.match(/supportedArchs\s*=\s*\[([^\]]+)\]/);
  if (match) {
    const archs = match[1].split(',').map(s => s.trim().replace(/['"]/g, ''));
    return archs.length > 0 ? archs : null;
  }
  return null;
}

/**
 * Parse build types from app/build.gradle content
 * Parses buildTypes { debug { ... } release { ... } customDebugType { ... } }
 * @param {string} content - The content of app/build.gradle
 * @returns {string[]|null} Array of build types or null if not found
 */
function parseBuildTypesFromGradle(content) {
  const buildTypesMatch = content.match(/buildTypes\s*\{([\s\S]*?)\n\s{2}\}/);
  if (buildTypesMatch) {
    const block = buildTypesMatch[1];
    const typeMatches = block.matchAll(/^\s{4}(\w+)\s*\{/gm);
    const types = [...typeMatches].map(m => m[1]);
    return types.length > 0 ? types : null;
  }
  return null;
}

/**
 * Generate all variant names from architectures and build types
 * @param {string[]} archs - Array of architecture names (e.g., ['arm64-v8a', 'x86_64'])
 * @param {string[]} buildTypes - Array of build type names (e.g., ['debug', 'release'])
 * @returns {string[]} Array of variant names (e.g., ['arm64v8aDebug', 'x8664Release'])
 */
function generateVariantNames(archs, buildTypes) {
  const variants = [];
  for (const arch of archs) {
    // Convert arch to variant format: 'arm64-v8a' -> 'arm64v8a', 'x86_64' -> 'x8664'
    const archVariant = arch.replace(/-/g, '').replace(/_/g, '');
    for (const buildType of buildTypes) {
      const capitalizedBuildType = buildType.charAt(0).toUpperCase() + buildType.slice(1);
      variants.push(archVariant + capitalizedBuildType);
    }
  }
  return variants;
}

/**
 * Extract variant name from Gradle bundle output path
 * @param {string} bundlePath - The bundle output path from Gradle
 * @returns {string|null} The variant name or null if not found
 */
function extractVariantFromPath(bundlePath) {
  const match = bundlePath.match(/createBundle([A-Za-z0-9]+)JsAndAssets/);
  if (match) {
    return match[1].charAt(0).toLowerCase() + match[1].slice(1);
  }
  return null;
}

// ============================================================================
// File System Helpers
// ============================================================================

function readGradleFile(relativePath) {
  try {
    return fs.readFileSync(path.join(__dirname, relativePath), 'utf8');
  } catch (e) {
    return null;
  }
}

function getSupportedArchs() {
  const content = readGradleFile(PATHS.rootBuildGradle);
  return (content && parseArchsFromGradle(content)) || DEFAULT_ARCHS;
}

function getBuildTypes() {
  const content = readGradleFile(PATHS.appBuildGradle);
  return (content && parseBuildTypesFromGradle(content)) || DEFAULT_BUILD_TYPES;
}

function ensureDir(filePath) {
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

function copyOrCreateSourceMap(source, dest) {
  ensureDir(dest);
  if (fs.existsSync(source)) {
    fs.copyFileSync(source, dest);
  } else {
    fs.writeFileSync(dest, EMPTY_SOURCEMAP);
  }
}

// ============================================================================
// Exports (for testing)
// ============================================================================

module.exports = {
  parseArchsFromGradle,
  parseBuildTypesFromGradle,
  generateVariantNames,
  extractVariantFromPath,
  DEFAULT_ARCHS,
  DEFAULT_BUILD_TYPES,
};

// ============================================================================
// Main Entry Point
// ============================================================================

if (require.main === module) {
  main();
}

function main() {
  const args = process.argv.slice(2);
  log(`Called with args: ${args.slice(0, 6).join(' ')} ...`);

  // Parse CLI arguments
  const gradleBundlePath = getArgValue(args, '--bundle-output');
  const gradleSourceMapPath = getArgValue(args, '--sourcemap-output');

  // Resolve paths
  const preBuiltBundle = path.join(__dirname, PATHS.preBuiltBundle);
  const preBuiltSourceMap = preBuiltBundle + SOURCEMAP_EXTENSION;

  log(`Pre-built bundle: ${preBuiltBundle}`);
  log(`Gradle bundle output: ${gradleBundlePath || '(default)'}`);
  log(`Gradle sourcemap output: ${gradleSourceMapPath || '(default)'}`);

  // Check if pre-built bundle exists
  if (!fs.existsSync(preBuiltBundle)) {
    return runMetroBundler(args);
  }

  // Bundle exists - use it
  const stats = fs.statSync(preBuiltBundle);
  const ageHours = (Date.now() - stats.mtimeMs) / (1000 * 60 * 60);
  const sizeKb = (stats.size / 1024).toFixed(0);

  logSuccess(`Pre-built bundle found (${sizeKb} KB, ${ageHours.toFixed(1)} hours old)`);

  if (ageHours > BUNDLE_AGE_WARNING_HOURS) {
    logWarning('Bundle is old, consider re-bundling: yarn bundle:android --dev=true');
  }

  // Copy bundle to Gradle location if needed
  if (gradleBundlePath && gradleBundlePath !== preBuiltBundle) {
    copyBundleAndSourceMaps(gradleBundlePath, gradleSourceMapPath, preBuiltBundle, preBuiltSourceMap);
  }

  log('Skipping Metro - using pre-built bundle');
  process.exit(0);
}

function getArgValue(args, flag) {
  const idx = args.indexOf(flag);
  return idx !== -1 && args[idx + 1] ? args[idx + 1] : null;
}

function copyBundleAndSourceMaps(gradleBundlePath, gradleSourceMapPath, preBuiltBundle, preBuiltSourceMap) {
  log('Copying bundle to Gradle location...');
  
  // Copy main bundle
  ensureDir(gradleBundlePath);
  fs.copyFileSync(preBuiltBundle, gradleBundlePath);

  // Copy source map next to bundle
  copyOrCreateSourceMap(preBuiltSourceMap, gradleBundlePath + SOURCEMAP_EXTENSION);

  // Create packager source maps for all variants
  const detectedVariant = extractVariantFromPath(gradleBundlePath);
  if (detectedVariant) {
    log(`Detected variant: ${detectedVariant}`);
  }

  const supportedArchs = getSupportedArchs();
  const buildTypes = getBuildTypes();
  log(`Supported archs: ${supportedArchs.join(', ')}`);
  log(`Build types: ${buildTypes.join(', ')}`);

  // Generate all variant names and add detected variant
  const allVariants = new Set(generateVariantNames(supportedArchs, buildTypes));
  if (detectedVariant) {
    allVariants.add(detectedVariant);
  }

  log(`Creating source maps for variants: ${Array.from(allVariants).join(', ')}`);

  const packagerSourceMapDir = path.join(__dirname, PATHS.packagerSourceMapDir);
  for (const variant of allVariants) {
    const packagerMapPath = path.join(packagerSourceMapDir, variant, PACKAGER_SOURCEMAP_FILENAME);
    copyOrCreateSourceMap(preBuiltSourceMap, packagerMapPath);
  }

  // Also use Gradle's --sourcemap-output if provided
  if (gradleSourceMapPath) {
    copyOrCreateSourceMap(preBuiltSourceMap, gradleSourceMapPath);
  }

  logSuccess('Bundle and source maps copied successfully');
}

function runMetroBundler(args) {
  logError(`No pre-built bundle found`);
  log('Attempting to run Metro bundler...');
  log('If this fails on Windows, pre-bundle on WSL: yarn bundle:android --dev=true');

  try {
    const cliPath = path.join(__dirname, PATHS.reactNativeCli);
    const cmd = `node "${cliPath}" ${args.join(' ')}`;
    execSync(cmd, { stdio: 'inherit', cwd: path.join(__dirname, '..') });
  } catch (error) {
    console.error('');
    logError('Bundling failed!');
    logError('On Windows with NativeWind, you must pre-bundle on WSL/Linux:');
    logError('  cd /path/to/project && yarn bundle:android --dev=true');
    logError('Then retry the Windows build.');
    process.exit(1);
  }
}
