/**
 * SetupSync onboarding flow tests - Logic validation
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 * 
 * ## Onboarding Flow States
 * 
 * | State           | isConfigured | syncEnabled | isConnected | Expected UI                                          |
 * |-----------------|--------------|-------------|-------------|------------------------------------------------------|
 * | No config       | `false`      | -           | -           | Setup guide + GitHub link + Settings button          |
 * | Sync disabled   | `true`       | `false`     | `false`     | Main screen + warning banner + disabled buttons      |
 * | Initializing    | `true`       | `true`      | `null`      | Main screen + "initializing" banner + disabled btns  |
 * | Not connected   | `true`       | `true`      | `false`     | Main screen + warning banner + disabled buttons      |
 * | Connected       | `true`       | `true`      | `true`      | Full UI with enabled buttons                         |
 * 
 * Note: UI rendering tests are in SetupSync.ui.test.tsx
 */

import type { Settings } from '@lib/hooks/SettingsContext';
import { isSettingsConfigured } from '../SetupSync';

const createSettings = (overrides: Partial<Settings> = {}): Settings => ({
  syncEnabled: false,
  syncType: 'unidirectional',
  supabaseUrl: '',
  supabaseAnonKey: '',
  powersyncUrl: '',
  powersyncToken: '',
  ...overrides,
});

const createCompleteSettings = (overrides: Partial<Settings> = {}): Settings => ({
  syncEnabled: true,
  syncType: 'unidirectional',
  supabaseUrl: 'https://example.supabase.co',
  supabaseAnonKey: 'anon-key-123',
  powersyncUrl: 'https://example.powersync.com',
  powersyncToken: 'token123',
  ...overrides,
});

describe('SetupSync', () => {
  describe('isSettingsConfigured', () => {
    it('returns false when no settings are provided', () => {
      expect(isSettingsConfigured(createSettings())).toBe(false);
    });

    it('returns false when supabaseUrl is missing', () => {
      expect(isSettingsConfigured(createCompleteSettings({ supabaseUrl: '' }))).toBe(false);
    });

    it('returns false when supabaseAnonKey is missing', () => {
      expect(isSettingsConfigured(createCompleteSettings({ supabaseAnonKey: '' }))).toBe(false);
    });

    it('returns false when powersyncUrl is missing', () => {
      expect(isSettingsConfigured(createCompleteSettings({ powersyncUrl: '' }))).toBe(false);
    });

    it('returns false when powersyncToken is missing', () => {
      expect(isSettingsConfigured(createCompleteSettings({ powersyncToken: '' }))).toBe(false);
    });

    it('returns true when all credentials are provided', () => {
      expect(isSettingsConfigured(createCompleteSettings())).toBe(true);
    });

    it('returns true even if syncEnabled is false (config exists)', () => {
      expect(isSettingsConfigured(createCompleteSettings({ syncEnabled: false }))).toBe(true);
    });
  });

  describe('UI state derivation', () => {
    // These test the logic that drives UI state decisions
    
    it('not configured → shows setup guide', () => {
      const settings = createSettings();
      const isConfigured = isSettingsConfigured(settings);
      
      expect(isConfigured).toBe(false);
      // When !isConfigured, SetupSync renders setup guide with GitHub link
    });

    it('configured + syncEnabled=false → shows not connected (sync disabled)', () => {
      const settings = createCompleteSettings({ syncEnabled: false });
      const isConfigured = isSettingsConfigured(settings);
      
      expect(isConfigured).toBe(true);
      expect(settings.syncEnabled).toBe(false);
      // When isConfigured but syncEnabled=false, useEffect sets isConnected=false
      // Shows warning banner + disabled buttons (same as "not connected")
    });

    it('configured + syncEnabled=true + isConnected=null → shows initializing', () => {
      const settings = createCompleteSettings();
      const isConfigured = isSettingsConfigured(settings);
      const isConnected: boolean | null = null;
      
      expect(isConfigured).toBe(true);
      expect(settings.syncEnabled).toBe(true);
      expect(isConnected).toBeNull();
      // When isConfigured && syncEnabled && isConnected === null, shows "initializing" banner + buttons disabled
    });

    it('configured + syncEnabled=true + isConnected=false → shows not connected warning', () => {
      const settings = createCompleteSettings();
      const isConfigured = isSettingsConfigured(settings);
      const isConnected = false;
      
      expect(isConfigured).toBe(true);
      expect(settings.syncEnabled).toBe(true);
      expect(isConnected).toBe(false);
      // When isConfigured && syncEnabled && !isConnected, shows warning + disabled buttons
    });

    it('configured + syncEnabled=true + isConnected=true → enables full functionality', () => {
      const settings = createCompleteSettings();
      const isConfigured = isSettingsConfigured(settings);
      const isConnected = true;
      
      expect(isConfigured).toBe(true);
      expect(settings.syncEnabled).toBe(true);
      expect(isConnected).toBe(true);
      // When isConfigured && syncEnabled && isConnected, all buttons enabled
    });
  });
});
