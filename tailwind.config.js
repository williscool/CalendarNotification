/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './index.tsx',
    './app/**/*.{js,jsx,ts,tsx}',
    './lib/**/*.{js,jsx,ts,tsx}',
    './node_modules/@gluestack-ui/nativewind-utils/**/*.{js,jsx,ts,tsx}',
  ],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        // Custom colors can be added here to match your theme
        primary: {
          DEFAULT: '#007AFF',
          dark: '#0A84FF',
        },
      },
    },
  },
  plugins: [],
};

