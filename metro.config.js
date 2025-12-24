const path = require('path');
const { pathToFileURL } = require('url');
const { getDefaultConfig } = require('expo/metro-config');
const { withNativeWind } = require('nativewind/metro');

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 * https://docs.expo.dev/guides/customizing-metro/
 *
 * NOTE: NativeWind has known issues with Windows paths and ESM.
 * If you get ERR_UNSUPPORTED_ESM_URL_SCHEME errors on Windows,
 * build from WSL instead: cd to the WSL path and run ./gradlew assembleDebug
 * See: https://github.com/nativewind/nativewind/issues/1667
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = getDefaultConfig(__dirname);

config.transformer = {
  ...config.transformer,
  getTransformOptions: async () => ({
    transform: {
      experimentalImportSupport: false,
      inlineRequires: {
        blockList: {
          [require.resolve('@powersync/react-native')]: true,
        }
      }
    }
  })
};

config.resolver = {
  ...config.resolver,
  sourceExts: [...config.resolver.sourceExts, 'mjs'],
};

// Convert paths to file URLs for Windows ESM compatibility
// See: https://github.com/nativewind/nativewind/issues/1667
const cssPath = path.resolve(__dirname, 'global.css');
const tailwindConfigPath = path.resolve(__dirname, 'tailwind.config.js');

const nativeWindOptions = {
  input: process.platform === 'win32' ? pathToFileURL(cssPath).href : cssPath,
  configPath: process.platform === 'win32' ? pathToFileURL(tailwindConfigPath).href : tailwindConfigPath,
};

module.exports = withNativeWind(config, nativeWindOptions);
