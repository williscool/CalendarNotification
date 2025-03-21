const {getDefaultConfig} = require('expo/metro-config');
const {mergeConfig} = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 * 
 * https://docs.expo.dev/guides/customizing-metro/
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = {
	transformer: {
	  getTransformOptions: async () => ({
		transform: {
		  inlineRequires: {
			blockList: {
			  [require.resolve('@powersync/react-native')]: true,
			}
		  }
		}
	  })
	}
  };

module.exports = mergeConfig(getDefaultConfig(__dirname), config);