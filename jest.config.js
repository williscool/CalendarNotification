module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src/lib', '<rootDir>/scripts'],
  testMatch: ['**/*.test.ts', '**/*.test.tsx', '**/*.ui.test.tsx'],
  moduleNameMapper: {
    '^@lib/(.*)$': '<rootDir>/src/lib/$1',
    '^@/components/ui$': '<rootDir>/src/lib/features/__tests__/__mocks__/gluestackUI.tsx',
    '^@/(.*)$': '<rootDir>/src/$1',
    '^react-native$': 'react-native-web',
    '^@expo/vector-icons$': '<rootDir>/src/lib/features/__tests__/__mocks__/vectorIcons.ts',
    '^@env$': '<rootDir>/src/lib/features/__tests__/__mocks__/env.ts',
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
    '<rootDir>/src/lib/powersync/testSetup.ts',
    '<rootDir>/src/lib/features/__tests__/setupComponentTests.ts',
  ],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
  clearMocks: true,
  collectCoverageFrom: [
    'src/lib/**/*.{ts,tsx}',
    '!src/lib/**/*.test.{ts,tsx}',
    '!src/lib/**/*.ui.test.{ts,tsx}',
    '!src/lib/**/testSetup.ts',
    '!src/lib/**/setupComponentTests.ts',
    '!src/lib/**/__tests__/**',
  ],
  coverageDirectory: 'coverage',
  coverageReporters: ['text', 'text-summary', 'lcov', 'html'],
};
