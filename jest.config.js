module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/lib', '<rootDir>/scripts'],
  testMatch: ['**/*.test.ts', '**/*.test.tsx', '**/*.ui.test.tsx'],
  moduleNameMapper: {
    '^@lib/(.*)$': '<rootDir>/lib/$1',
    '^react-native$': 'react-native-web',
    '^@expo/vector-icons$': '<rootDir>/lib/features/__tests__/__mocks__/vectorIcons.ts',
    '^@env$': '<rootDir>/lib/features/__tests__/__mocks__/env.ts',
  },
  transform: {
    '^.+\\.tsx?$': ['ts-jest', {
      tsconfig: {
        module: 'commonjs',
        moduleResolution: 'node',
        esModuleInterop: true,
        allowSyntheticDefaultImports: true,
        jsx: 'react',
      },
    }],
  },
  setupFilesAfterEnv: [
    '<rootDir>/lib/powersync/testSetup.ts',
    '<rootDir>/lib/features/__tests__/setupComponentTests.ts',
  ],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
  clearMocks: true,
  collectCoverageFrom: [
    'lib/**/*.{ts,tsx}',
    '!lib/**/*.test.{ts,tsx}',
    '!lib/**/*.ui.test.{ts,tsx}',
    '!lib/**/testSetup.ts',
    '!lib/**/setupComponentTests.ts',
    '!lib/**/__tests__/**',
  ],
  coverageDirectory: 'coverage',
  coverageReporters: ['text', 'text-summary', 'lcov', 'html'],
};
