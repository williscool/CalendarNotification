module.exports = function (api) {
	api.cache(true);
	return {
		presets: [
			['babel-preset-expo'],
			'nativewind/babel',
		],
		plugins: [
			[
				'module:react-native-dotenv',
				{
					moduleName: '@env',
					path: '.env',
					safe: false,
					allowUndefined: true,
				},
			],
			[
				'module-resolver',
				{
					root: ['./'],
					alias: {
						'@': './src',
						'@lib': './lib',
					},
					extensions: [
						'.ios.ts',
						'.android.ts',
						'.ts',
						'.ios.tsx',
						'.android.tsx',
						'.tsx',
						'.jsx',
						'.js',
						'.json',
					],
				},
			],
			// Reanimated plugin must be listed LAST
			'react-native-reanimated/plugin',
		],
	};
};
