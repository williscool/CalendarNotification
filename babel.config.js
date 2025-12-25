module.exports = function (api) {
  api.cache(true);

  return {
    presets: [['babel-preset-expo'], 'nativewind/babel'],

    plugins: [
      [
        'module:react-native-dotenv',
        {
          envName: 'APP_ENV',
          moduleName: '@env',
          path: '.env',
          safe: false,
          allowUndefined: true,
          verbose: false,
        },
      ],
      [
        'module-resolver',
        {
          root: ['./'],

          alias: {
            '@': './',
            '@lib': './lib',
            'tailwind.config': './tailwind.config.js',
          },
        },
      ],
      'react-native-reanimated/plugin', // Reanimated plugin must be listed LAST (includes worklets)
    ],
  };
};
