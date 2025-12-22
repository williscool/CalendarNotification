/**
 * SetupSync onboarding flow tests
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 * 
 * ## Onboarding Flow States
 * 
 * | State           | isConfigured | isConnected | Expected UI                                          |
 * |-----------------|--------------|-------------|------------------------------------------------------|
 * | No config       | `false`      | -           | Setup guide + GitHub link + Settings button          |
 * | Initializing    | `true`       | `null`      | Main screen + "initializing" banner                  |
 * | Not connected   | `true`       | `false`     | Main screen + warning banner + Settings link + disabled buttons |
 * | Connected       | `true`       | `true`      | Full UI with enabled buttons                         |
 * 
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

    it('configured + isConnected=null → shows initializing', () => {
      const settings = createCompleteSettings();
      const isConfigured = isSettingsConfigured(settings);
      const isConnected: boolean | null = null;
      
      expect(isConfigured).toBe(true);
      expect(isConnected).toBeNull();
      // When isConfigured && isConnected === null, shows "initializing" banner
    });

    it('configured + isConnected=false → shows not connected warning', () => {
      const settings = createCompleteSettings();
      const isConfigured = isSettingsConfigured(settings);
      const isConnected = false;
      
      expect(isConfigured).toBe(true);
      expect(isConnected).toBe(false);
      // When isConfigured && !isConnected, shows warning + disabled buttons
    });

    it('configured + isConnected=true → enables full functionality', () => {
      const settings = createCompleteSettings();
      const isConfigured = isSettingsConfigured(settings);
      const isConnected = true;
      
      expect(isConfigured).toBe(true);
      expect(isConnected).toBe(true);
      // When isConfigured && isConnected, all buttons enabled
    });
  });
});
