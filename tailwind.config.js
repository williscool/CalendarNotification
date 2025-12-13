/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,jsx,ts,tsx}',
    './lib/**/*.{js,jsx,ts,tsx}',
  ],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        primary: '#007AFF',
        danger: '#FF3B30',
        success: '#28a745',
        warning: '#FF9500',
        'warning-bg': '#fff3cd',
        'warning-border': '#ffeeba',
        muted: '#666',
        'muted-light': '#999',
        border: '#ddd',
        'border-light': '#eee',
      },
    },
  },
  plugins: [],
};

