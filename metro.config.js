const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');
const { getDefaultConfig: getExpoDefaultConfig } = require('expo/metro-config');

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 *
 * Expo Metro configuration
 * https://docs.expo.dev/versions/latest/sdk/sqlite/#usage
 * 
 * @type {import('metro-config').MetroConfig}
 */
const config = {};

const expoDefaultConfig = getExpoDefaultConfig(__dirname);

expoDefaultConfig.resolver.assetExts.push('db');

module.exports = mergeConfig(getDefaultConfig(__dirname), expoDefaultConfig, config);