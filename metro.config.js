const { getDefaultConfig } = require('expo/metro-config');

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 * 
 * https://docs.expo.dev/guides/customizing-metro/
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = getDefaultConfig(__dirname);

// PowerSync specific configuration
config.transformer = {
  ...config.transformer,
	  getTransformOptions: async () => ({
		transform: {
		  inlineRequires: {
			blockList: {
			  [require.resolve('@powersync/react-native')]: true,
			}
		  }
		}
	  })
  };

module.exports = config;
