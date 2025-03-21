module.exports = function (api) {
	api.cache(true);
	return {
		presets: [
			['babel-preset-expo'],
		],
		plugins: [
			[
				'module-resolver',
				{
					root: ['./'],
					alias: {
						'@': './src',
						'@lib': './lib', // Maps `@lib` to the `lib` directory
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
		],
	};
};