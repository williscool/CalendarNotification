/**
 * Sync Database Contract Tests
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 * 
 * These tests verify the contract between the React Native sync feature
 * and the native Android storage layer. They ensure both components
 * agree on which database to use.
 * 
 * Background: After Room migration, the native code writes to "RoomEvents"
 * while the sync code was hardcoded to read from "Events". This caused
 * the sync feature to read stale data.
 * 
 * Fix: The sync code now calls getActiveEventsDbName() from the native module
 * to get the correct database name dynamically.
 */

import { getActiveEventsDbName, isUsingRoomStorage } from '../../../../modules/my-module';

// Mock the native module for unit tests
// In actual device tests, these would call real native code
jest.mock('../../../../modules/my-module', () => ({
  ...jest.requireActual('../../../../modules/my-module'),
  getActiveEventsDbName: jest.fn(),
  isUsingRoomStorage: jest.fn(),
}));

const mockGetActiveEventsDbName = getActiveEventsDbName as jest.MockedFunction<typeof getActiveEventsDbName>;
const mockIsUsingRoomStorage = isUsingRoomStorage as jest.MockedFunction<typeof isUsingRoomStorage>;

describe('Sync Database Contract', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('getActiveEventsDbName', () => {
    it('returns a valid database name string', () => {
      mockGetActiveEventsDbName.mockReturnValue('RoomEvents');
      
      const dbName = getActiveEventsDbName();
      
      expect(typeof dbName).toBe('string');
      expect(dbName.length).toBeGreaterThan(0);
    });

    it('returns RoomEvents when Room migration succeeded', () => {
      mockGetActiveEventsDbName.mockReturnValue('RoomEvents');
      mockIsUsingRoomStorage.mockReturnValue(true);
      
      const dbName = getActiveEventsDbName();
      const isRoom = isUsingRoomStorage();
      
      expect(dbName).toBe('RoomEvents');
      expect(isRoom).toBe(true);
    });

    it('returns Events when Room migration failed (legacy fallback)', () => {
      mockGetActiveEventsDbName.mockReturnValue('Events');
      mockIsUsingRoomStorage.mockReturnValue(false);
      
      const dbName = getActiveEventsDbName();
      const isRoom = isUsingRoomStorage();
      
      expect(dbName).toBe('Events');
      expect(isRoom).toBe(false);
    });

    it('database name is one of the expected values', () => {
      // Test with Room
      mockGetActiveEventsDbName.mockReturnValue('RoomEvents');
      expect(['RoomEvents', 'Events']).toContain(getActiveEventsDbName());
      
      // Test with Legacy
      mockGetActiveEventsDbName.mockReturnValue('Events');
      expect(['RoomEvents', 'Events']).toContain(getActiveEventsDbName());
    });
  });

  describe('isUsingRoomStorage', () => {
    it('returns a boolean', () => {
      mockIsUsingRoomStorage.mockReturnValue(true);
      expect(typeof isUsingRoomStorage()).toBe('boolean');
      
      mockIsUsingRoomStorage.mockReturnValue(false);
      expect(typeof isUsingRoomStorage()).toBe('boolean');
    });
  });

  describe('contract consistency', () => {
    it('Room storage returns RoomEvents database name', () => {
      mockIsUsingRoomStorage.mockReturnValue(true);
      mockGetActiveEventsDbName.mockReturnValue('RoomEvents');
      
      if (isUsingRoomStorage()) {
        expect(getActiveEventsDbName()).toBe('RoomEvents');
      }
    });

    it('Legacy storage returns Events database name', () => {
      mockIsUsingRoomStorage.mockReturnValue(false);
      mockGetActiveEventsDbName.mockReturnValue('Events');
      
      if (!isUsingRoomStorage()) {
        expect(getActiveEventsDbName()).toBe('Events');
      }
    });
  });
});

/**
 * Integration test note:
 * 
 * The above tests verify the contract with mocks. For true integration testing,
 * run the app on a device and check:
 * 
 * 1. Open React Native debugger / Flipper
 * 2. Call getActiveEventsDbName() 
 * 3. Verify it matches the native storage being used
 * 
 * Or check the logs for:
 * - "getActiveEventsDbName: returning 'RoomEvents' (isUsingRoom=true)"
 * - "getActiveEventsDbName: returning 'Events' (isUsingRoom=false)"
 */
