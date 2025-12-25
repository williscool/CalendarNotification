const { getDefaultConfig } = require('expo/metro-config');
const { withNativeWind } = require('nativewind/metro');

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 * https://docs.expo.dev/guides/customizing-metro/
 *
 * NOTE: NativeWind has Windows path issues. Use bundle_or_skip.js for Windows builds.
 * Pre-bundle on WSL: yarn bundle:android --dev=true
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
          // PowerSync requires eager initialization - don't inline its requires
          [require.resolve('@powersync/react-native')]: true,
        }
      }
    }
  })
};

config.resolver = {
  ...config.resolver,
  // Support ESM modules that use .mjs extension
  sourceExts: [...config.resolver.sourceExts, 'mjs'],
};

module.exports = withNativeWind(config, { input: './global.css' });
