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

/**
 * Parse supported architectures from build.gradle content
 * Looks for: supportedArchs = ['arm64-v8a', 'x86_64']
 * @param {string} content - The content of build.gradle
 * @returns {string[]|null} Array of architectures or null if not found
 */
function parseArchsFromGradle(content) {
  const match = content.match(/supportedArchs\s*=\s*\[([^\]]+)\]/);
  if (match) {
    const archsStr = match[1];
    const archs = archsStr.split(',').map(s => s.trim().replace(/['"]/g, ''));
    if (archs.length > 0) {
      return archs;
    }
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
  // Find buildTypes block and extract type names
  // Match lines like: "  debug {" or "  customDebugType {"
  const buildTypesMatch = content.match(/buildTypes\s*\{([\s\S]*?)\n\s{2}\}/);
  if (buildTypesMatch) {
    const block = buildTypesMatch[1];
    // Match build type names (word followed by { at start of line with indentation)
    const typeMatches = block.matchAll(/^\s{4}(\w+)\s*\{/gm);
    const types = [...typeMatches].map(m => m[1]);
    if (types.length > 0) {
      return types;
    }
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
      // Capitalize first letter of build type for variant name
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
    // Convert to lowercase first character (Arm64v8aDebug -> arm64v8aDebug)
    return match[1].charAt(0).toLowerCase() + match[1].slice(1);
  }
  return null;
}

const DEFAULT_ARCHS = ['arm64-v8a', 'x86_64'];
const DEFAULT_BUILD_TYPES = ['debug', 'release'];

/**
 * Read supported architectures from android/build.gradle
 */
function getSupportedArchs() {
  try {
    const buildGradlePath = path.join(__dirname, '../android/build.gradle');
    const content = fs.readFileSync(buildGradlePath, 'utf8');
    return parseArchsFromGradle(content) || DEFAULT_ARCHS;
  } catch (e) {
    return DEFAULT_ARCHS;
  }
}

/**
 * Read build types from android/app/build.gradle
 */
function getBuildTypes() {
  try {
    const appBuildGradlePath = path.join(__dirname, '../android/app/build.gradle');
    const content = fs.readFileSync(appBuildGradlePath, 'utf8');
    return parseBuildTypesFromGradle(content) || DEFAULT_BUILD_TYPES;
  } catch (e) {
    return DEFAULT_BUILD_TYPES;
  }
}

// Export functions for testing
module.exports = {
  parseArchsFromGradle,
  parseBuildTypesFromGradle,
  generateVariantNames,
  extractVariantFromPath,
  DEFAULT_ARCHS,
  DEFAULT_BUILD_TYPES,
};

// Only run main logic when executed directly (not when imported for testing)
if (require.main === module) {
  main();
}

function main() {
  // Args: ['bundle', '--platform', 'android', '--bundle-output', '/path/to/bundle', ...]
  const args = process.argv.slice(2);

console.log('[bundle_or_skip] Called with args:', args.slice(0, 6).join(' '), '...');

// Parse --bundle-output and --sourcemap-output from args
let gradleBundlePath = null;
let gradleSourceMapPath = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--bundle-output' && args[i + 1]) {
    gradleBundlePath = args[i + 1];
  }
  if (args[i] === '--sourcemap-output' && args[i + 1]) {
    gradleSourceMapPath = args[i + 1];
  }
}

// Our pre-built bundle location (from yarn bundle:android)
const preBuiltBundle = path.join(__dirname, '../android/app/src/main/assets/index.android.bundle');
const preBuiltSourceMap = preBuiltBundle + '.map';

console.log('[bundle_or_skip] Pre-built bundle:', preBuiltBundle);
console.log('[bundle_or_skip] Gradle bundle output:', gradleBundlePath || '(default)');
console.log('[bundle_or_skip] Gradle sourcemap output:', gradleSourceMapPath || '(default)');

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
    const emptySourceMap = '{"version":3,"sources":[],"names":[],"mappings":""}';
    if (fs.existsSync(preBuiltSourceMap)) {
      fs.copyFileSync(preBuiltSourceMap, gradleSourceMap);
    } else {
      fs.writeFileSync(gradleSourceMap, emptySourceMap);
    }
    
    // Also create the packager source map that compose-source-maps.js expects
    // Path: build/intermediates/sourcemaps/react/{variant}/index.android.bundle.packager.map
    // Extract variant from bundle output path (e.g., createBundleArm64v8aDebugJsAndAssets -> arm64v8aDebug)
    const bundlePathMatch = gradleBundlePath.match(/createBundle([A-Za-z0-9]+)JsAndAssets/);
    let detectedVariant = bundlePathMatch ? bundlePathMatch[1] : null;
    
    // Convert to lowercase first character (Arm64v8aDebug -> arm64v8aDebug)
    if (detectedVariant) {
      detectedVariant = detectedVariant.charAt(0).toLowerCase() + detectedVariant.slice(1);
      console.log('[bundle_or_skip] Detected variant:', detectedVariant);
    }
    
    const packagerSourceMapDir = path.join(__dirname, '../android/app/build/intermediates/sourcemaps/react');
    
    // Get all supported architectures and build types to create source maps for all variants
    // Read from android/build.gradle and android/app/build.gradle
    const supportedArchs = getSupportedArchs();
    const buildTypes = getBuildTypes();
    console.log('[bundle_or_skip] Supported archs:', supportedArchs.join(', '));
    console.log('[bundle_or_skip] Build types:', buildTypes.join(', '));
    
    // Generate all possible variant names
    const allVariants = new Set();
    if (detectedVariant) {
      allVariants.add(detectedVariant);
    }
    // Add arch+buildType combinations
    // Variant format: {arch}{BuildType} where arch has no separators
    // e.g., arm64-v8a + debug -> arm64v8aDebug, x86_64 + customDebugType -> x8664CustomDebugType
    for (const arch of supportedArchs) {
      // Convert arch to variant format: 'arm64-v8a' -> 'arm64v8a', 'x86_64' -> 'x8664'
      const archVariant = arch.replace(/-/g, '').replace(/_/g, '');
      for (const buildType of buildTypes) {
        // Capitalize first letter of build type for variant name
        const capitalizedBuildType = buildType.charAt(0).toUpperCase() + buildType.slice(1);
        allVariants.add(archVariant + capitalizedBuildType);
      }
    }
    
    console.log('[bundle_or_skip] Creating source maps for variants:', Array.from(allVariants).join(', '));
    
    for (const v of allVariants) {
      const variantDir = path.join(packagerSourceMapDir, v);
      if (!fs.existsSync(variantDir)) {
        fs.mkdirSync(variantDir, { recursive: true });
      }
      const packagerMapPath = path.join(variantDir, 'index.android.bundle.packager.map');
      if (fs.existsSync(preBuiltSourceMap)) {
        fs.copyFileSync(preBuiltSourceMap, packagerMapPath);
      } else {
        fs.writeFileSync(packagerMapPath, emptySourceMap);
      }
    }
    
    // Also use Gradle's --sourcemap-output if provided
    if (gradleSourceMapPath) {
      const smDir = path.dirname(gradleSourceMapPath);
      if (!fs.existsSync(smDir)) {
        fs.mkdirSync(smDir, { recursive: true });
      }
      if (fs.existsSync(preBuiltSourceMap)) {
        fs.copyFileSync(preBuiltSourceMap, gradleSourceMapPath);
      } else {
        fs.writeFileSync(gradleSourceMapPath, emptySourceMap);
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
}
