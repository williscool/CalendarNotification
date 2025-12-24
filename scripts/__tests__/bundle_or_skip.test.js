/**
 * Tests for bundle_or_skip.js utility functions
 * 
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */

const {
  parseArchsFromGradle,
  parseBuildTypesFromGradle,
  generateVariantNames,
  extractVariantFromPath,
  DEFAULT_ARCHS,
  DEFAULT_BUILD_TYPES,
} = require('../bundle_or_skip');

describe('bundle_or_skip', () => {
  describe('parseArchsFromGradle', () => {
    it('parses architectures from build.gradle content', () => {
      const content = `
buildscript {
    ext {
        supportedArchs = ['arm64-v8a', 'x86_64']
        kotlinVersion = "1.9.0"
    }
}
`;
      expect(parseArchsFromGradle(content)).toEqual(['arm64-v8a', 'x86_64']);
    });

    it('handles single architecture', () => {
      const content = `ext { supportedArchs = ['arm64-v8a'] }`;
      expect(parseArchsFromGradle(content)).toEqual(['arm64-v8a']);
    });

    it('handles double-quoted strings', () => {
      const content = `supportedArchs = ["arm64-v8a", "x86_64"]`;
      expect(parseArchsFromGradle(content)).toEqual(['arm64-v8a', 'x86_64']);
    });

    it('returns null when not found', () => {
      const content = `buildscript { ext { kotlinVersion = "1.9.0" } }`;
      expect(parseArchsFromGradle(content)).toBeNull();
    });

    it('returns null for empty content', () => {
      expect(parseArchsFromGradle('')).toBeNull();
    });
  });

  describe('parseBuildTypesFromGradle', () => {
    it('parses build types from app/build.gradle content', () => {
      const content = `
android {
  buildTypes {
    debug {
      testCoverageEnabled true
    }
    release {
      minifyEnabled false
    }
    customDebugType {
      initWith debug
    }
  }
}
`;
      expect(parseBuildTypesFromGradle(content)).toEqual(['debug', 'release', 'customDebugType']);
    });

    it('handles minimal build types block', () => {
      const content = `
  buildTypes {
    debug {
    }
    release {
    }
  }
`;
      expect(parseBuildTypesFromGradle(content)).toEqual(['debug', 'release']);
    });

    it('returns null when buildTypes block not found', () => {
      const content = `android { defaultConfig { minSdk 24 } }`;
      expect(parseBuildTypesFromGradle(content)).toBeNull();
    });

    it('returns null for empty content', () => {
      expect(parseBuildTypesFromGradle('')).toBeNull();
    });
  });

  describe('generateVariantNames', () => {
    it('generates variant names from archs and build types', () => {
      const archs = ['arm64-v8a', 'x86_64'];
      const buildTypes = ['debug', 'release'];
      
      expect(generateVariantNames(archs, buildTypes)).toEqual([
        'arm64v8aDebug',
        'arm64v8aRelease',
        'x8664Debug',
        'x8664Release',
      ]);
    });

    it('handles custom build types', () => {
      const archs = ['x86_64'];
      const buildTypes = ['debug', 'customDebugType'];
      
      expect(generateVariantNames(archs, buildTypes)).toEqual([
        'x8664Debug',
        'x8664CustomDebugType',
      ]);
    });

    it('handles single arch and build type', () => {
      expect(generateVariantNames(['arm64-v8a'], ['debug'])).toEqual(['arm64v8aDebug']);
    });

    it('returns empty array for empty inputs', () => {
      expect(generateVariantNames([], [])).toEqual([]);
      expect(generateVariantNames(['arm64-v8a'], [])).toEqual([]);
      expect(generateVariantNames([], ['debug'])).toEqual([]);
    });

    it('correctly handles armeabi-v7a architecture', () => {
      expect(generateVariantNames(['armeabi-v7a'], ['debug'])).toEqual(['armeabiv7aDebug']);
    });

    it('correctly handles x86 architecture', () => {
      expect(generateVariantNames(['x86'], ['release'])).toEqual(['x86Release']);
    });
  });

  describe('extractVariantFromPath', () => {
    it('extracts variant from bundle output path', () => {
      const path = 'android/app/build/generated/assets/createBundleArm64v8aDebugJsAndAssets/index.android.bundle';
      expect(extractVariantFromPath(path)).toBe('arm64v8aDebug');
    });

    it('handles x86_64 variant', () => {
      const path = 'build/generated/assets/createBundleX8664ReleaseJsAndAssets/index.android.bundle';
      expect(extractVariantFromPath(path)).toBe('x8664Release');
    });

    it('handles custom build type', () => {
      const path = 'createBundleX8664CustomDebugTypeJsAndAssets/index.android.bundle';
      expect(extractVariantFromPath(path)).toBe('x8664CustomDebugType');
    });

    it('returns null for non-matching paths', () => {
      expect(extractVariantFromPath('some/random/path')).toBeNull();
      expect(extractVariantFromPath('')).toBeNull();
    });

    it('handles Windows-style paths', () => {
      const path = 'android\\app\\build\\generated\\assets\\createBundleArm64v8aDebugJsAndAssets\\index.android.bundle';
      expect(extractVariantFromPath(path)).toBe('arm64v8aDebug');
    });
  });

  describe('default constants', () => {
    it('has correct default architectures', () => {
      expect(DEFAULT_ARCHS).toEqual(['arm64-v8a', 'x86_64']);
    });

    it('has correct default build types', () => {
      expect(DEFAULT_BUILD_TYPES).toEqual(['debug', 'release']);
    });
  });
});

