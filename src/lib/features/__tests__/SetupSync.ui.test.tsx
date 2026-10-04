/**
 * SetupSync UI rendering tests.
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 * 
 * Tests the 5 onboarding flow states:
 * | State           | isConfigured | syncEnabled | isConnected | Expected UI                                          |
 * |-----------------|--------------|-------------|-------------|------------------------------------------------------|
 * | No config       | `false`      | -           | -           | Setup guide + GitHub link + Settings button          |
 * | Sync disabled   | `true`       | `false`     | `false`     | Main screen + warning banner + disabled buttons      |
 * | Initializing    | `true`       | `true`      | `null`      | Main screen + "initializing" banner + disabled btns  |
 * | Not connected   | `true`       | `true`      | `false`     | Main screen + warning banner + disabled buttons      |
 * | Connected       | `true`       | `true`      | `true`      | Full UI with enabled buttons                         |
 */

// Mock native module before any imports
jest.mock('../../../../modules/my-module', () => ({
  getActiveEventsDbName: jest.fn(() => 'RoomEvents'),
  isUsingRoomStorage: jest.fn(() => true),
  hello: jest.fn(() => 'Hello world!'),
  sendRescheduleConfirmations: jest.fn(),
  addChangeListener: jest.fn(() => ({ remove: jest.fn() })),
  startBackgroundSync: jest.fn(() => Promise.resolve()),
  getLastBackgroundSyncResult: jest.fn(() => null),
  areNotificationsEnabled: jest.fn(() => true),
  PI: 100,
}));

// Mock op-sqlite
jest.mock('@op-engineering/op-sqlite', () => ({
  open: jest.fn(() => ({
    execute: jest.fn(() => Promise.resolve({ rows: [] })),
  })),
}));

// Mock cr-sqlite install
jest.mock('@lib/cr-sqlite/install', () => ({
  installCrsqliteOnTable: jest.fn(() => Promise.resolve()),
}));

// Mock orm
jest.mock('@lib/orm', () => ({
  psInsertDbTable: jest.fn(() => Promise.resolve()),
  psClearTable: jest.fn(() => Promise.resolve()),
  psResyncTable: jest.fn(() => Promise.resolve()),
  getPendingCrudCount: jest.fn(() => Promise.resolve(0)),
}));

import React from 'react';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { getColors } from '@lib/theme/colors';

// Test state - use object so mutations are visible to hoisted mocks
const testState = {
  settings: {
    syncEnabled: false,
    syncType: 'unidirectional' as const,
    supabaseUrl: '',
    supabaseAnonKey: '',
    powersyncUrl: '',
    powersyncSecret: '',
  },
  powerSyncStatus: {
    connected: null as boolean | null,
    hasSynced: false,
  },
};

// Mock useSettings - uses getter to access current testState
jest.mock('@lib/hooks/SettingsContext', () => ({
  useSettings: () => ({
    get settings() { return testState.settings; },
    updateSettings: jest.fn(),
  }),
}));

// Mock useTheme
jest.mock('@lib/theme/ThemeContext', () => ({
  useTheme: () => ({
    isDark: false,
    colors: getColors(false),
  }),
}));

// Mock PowerSyncContext - uses getter for dynamic status
const MockPowerSyncContext = React.createContext<any>(null);
jest.mock('@powersync/react', () => ({
  useQuery: jest.fn(() => ({ data: [] })),
  PowerSyncContext: MockPowerSyncContext,
}));

// Mock navigation
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({
    navigate: jest.fn(),
  }),
}));

// Mock syncLog
jest.mock('@lib/logging/syncLog', () => ({
  emitSyncLog: jest.fn(),
}));

// Import after mocks are set up
import { SetupSync } from '../SetupSync';
import { psResyncTable, getPendingCrudCount } from '@lib/orm';
import { startBackgroundSync, getLastBackgroundSyncResult, areNotificationsEnabled } from '../../../../modules/my-module';

// Helper to configure test scenarios
const configureSettings = (configured: boolean) => {
  if (configured) {
    testState.settings = {
      syncEnabled: true,
      syncType: 'unidirectional',
      supabaseUrl: 'https://example.supabase.co',
      supabaseAnonKey: 'anon-key-123',
      powersyncUrl: 'https://example.powersync.com',
      powersyncSecret: 'token123',
    };
  } else {
    testState.settings = {
      syncEnabled: false,
      syncType: 'unidirectional',
      supabaseUrl: '',
      supabaseAnonKey: '',
      powersyncUrl: '',
      powersyncSecret: '',
    };
  }
};

const configurePowerSyncStatus = (connected: boolean | null) => {
  testState.powerSyncStatus = {
    connected,
    hasSynced: connected === true,
  };
};

// Wrapper to provide PowerSync context with current status
const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <MockPowerSyncContext.Provider value={{ currentStatus: testState.powerSyncStatus }}>
    {children}
  </MockPowerSyncContext.Provider>
);

const renderWithContext = (ui: React.ReactElement) => render(ui, { wrapper: TestWrapper });

// Helper to render and advance timers so the status interval fires
const renderAndWaitForStatus = async (ui: React.ReactElement) => {
  const result = renderWithContext(ui);
  // Flush pending promises from useEffect async operations
  await act(async () => {
    await Promise.resolve();
  });
  // Advance timer to trigger the status interval (1000ms in SetupSync)
  await act(async () => {
    jest.advanceTimersByTime(1100);
  });
  // Flush any remaining state updates
  await act(async () => {
    await Promise.resolve();
  });
  return result;
};

// Changes still waiting to upload when the screen opens; any non-zero count will do
const LEFTOVER_QUEUED_OPS = 1138;
// How often SetupSync polls the PowerSync status and queue count
const STATUS_POLL_MS = 1000;

// Any fixed time will do: the banner tests only check the wording around it
const LAST_SYNC_COMPLETED_AT_MS = 1700000000000;

describe('SetupSync UI States', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    // Reset to defaults (clearAllMocks keeps implementations a test has overridden)
    (getPendingCrudCount as jest.Mock).mockImplementation(() => Promise.resolve(0));
    configureSettings(false);
    configurePowerSyncStatus(null);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('State: No config (isConfigured=false)', () => {
    beforeEach(() => {
      configureSettings(false);
    });

    it('renders setup guide message', () => {
      renderWithContext(<SetupSync />);
      expect(screen.getByText('PowerSync not configured')).toBeInTheDocument();
    });

    it('renders GitHub README link', () => {
      renderWithContext(<SetupSync />);
      expect(screen.getByText('GitHub README')).toBeInTheDocument();
    });

    it('renders Settings button', () => {
      renderWithContext(<SetupSync />);
      expect(screen.getByText('Go to Settings')).toBeInTheDocument();
    });

    it('does not render main sync UI', () => {
      renderWithContext(<SetupSync />);
      expect(screen.queryByText(/PowerSync Status:/)).not.toBeInTheDocument();
    });
  });

  describe('State: Sync disabled (isConfigured=true, syncEnabled=false)', () => {
    beforeEach(() => {
      // Configure with credentials but syncEnabled=false
      testState.settings = {
        syncEnabled: false,
        syncType: 'unidirectional',
        supabaseUrl: 'https://example.supabase.co',
        supabaseAnonKey: 'anon-key-123',
        powersyncUrl: 'https://example.powersync.com',
        powersyncSecret: 'token123',
      };
      configurePowerSyncStatus(null);
    });

    it('renders main sync screen', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByText(/PowerSync Status:/)).toBeInTheDocument();
    });

    it('renders warning banner (not initializing)', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByText(/PowerSync is not connected/)).toBeInTheDocument();
    });

    it('does not render initializing banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.queryByText(/PowerSync is initializing/)).not.toBeInTheDocument();
    });

    it('renders sync button as disabled', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      const button = screen.getByTestId('sync-button');
      expect(button).toHaveAttribute('aria-disabled', 'true');
    });
  });

  describe('State: Initializing (isConfigured=true, isConnected=null)', () => {
    beforeEach(() => {
      configureSettings(true);
      configurePowerSyncStatus(null);
    });

    it('renders main sync screen', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByText(/PowerSync Status:/)).toBeInTheDocument();
    });

    it('renders initializing banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByText(/PowerSync is initializing/)).toBeInTheDocument();
    });

    it('does not render warning banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.queryByText(/PowerSync is not connected/)).not.toBeInTheDocument();
    });

    it('renders sync button as disabled', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      const button = screen.getByTestId('sync-button');
      expect(button).toHaveAttribute('aria-disabled', 'true');
    });
  });

  describe('State: Not connected (isConfigured=true, isConnected=false)', () => {
    beforeEach(() => {
      configureSettings(true);
      configurePowerSyncStatus(false);
    });

    it('renders main sync screen', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByText(/PowerSync Status:/)).toBeInTheDocument();
    });

    it('renders warning banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByText(/PowerSync is not connected/)).toBeInTheDocument();
    });

    it('renders Settings link in warning banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      // The Settings link is inside the warning banner text
      expect(screen.getByText('Go to Settings')).toBeInTheDocument();
    });

    it('does not render initializing banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.queryByText(/PowerSync is initializing/)).not.toBeInTheDocument();
    });

    it('renders sync button as disabled', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      const button = screen.getByTestId('sync-button');
      expect(button).toHaveAttribute('aria-disabled', 'true');
    });
  });

  describe('State: Connected (isConfigured=true, isConnected=true)', () => {
    beforeEach(() => {
      configureSettings(true);
      configurePowerSyncStatus(true);
    });

    it('renders main sync screen', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByText(/PowerSync Status:/)).toBeInTheDocument();
    });

    it('does not render warning banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.queryByText(/PowerSync is not connected/)).not.toBeInTheDocument();
    });

    it('does not render initializing banner', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.queryByText(/PowerSync is initializing/)).not.toBeInTheDocument();
    });

    it('renders sync button as enabled', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      const button = screen.getByTestId('sync-button');
      expect(button).not.toHaveAttribute('aria-disabled', 'true');
    });

    it('renders danger zone button as enabled', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      const button = screen.getByTestId('danger-zone-button');
      expect(button).not.toHaveAttribute('aria-disabled', 'true');
    });

    it('shows no background sync banners when there is nothing to report', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.queryByTestId('last-background-sync-banner')).not.toBeInTheDocument();
      expect(screen.queryByTestId('notifications-off-banner')).not.toBeInTheDocument();
    });

    it('shows a background sync that completed while the screen was closed', async () => {
      (getLastBackgroundSyncResult as jest.Mock).mockReturnValueOnce({ completedAt: LAST_SYNC_COMPLETED_AT_MS, ok: true, error: null });
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByTestId('last-background-sync-banner')).toHaveTextContent(/Last background sync completed/);
    });

    it('shows why the last background sync did not finish', async () => {
      (getLastBackgroundSyncResult as jest.Mock).mockReturnValueOnce({ completedAt: LAST_SYNC_COMPLETED_AT_MS, ok: false, error: 'network down' });
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByTestId('last-background-sync-banner')).toHaveTextContent(/did not finish.*network down/);
    });

    it('notes when notifications are off', async () => {
      (areNotificationsEnabled as jest.Mock).mockReturnValueOnce(false);
      await renderAndWaitForStatus(<SetupSync />);
      expect(screen.getByTestId('notifications-off-banner')).toHaveTextContent(/Notifications are off/);
    });

    it('starts the background sync service when it finds changes already queued', async () => {
      // e.g. left over from a sync that was interrupted before this version
      (getPendingCrudCount as jest.Mock).mockResolvedValue(LEFTOVER_QUEUED_OPS);
      await renderAndWaitForStatus(<SetupSync />);

      expect(startBackgroundSync).toHaveBeenCalledTimes(1);
    });

    it('starts the background sync service only once while the queue stays non-empty', async () => {
      (getPendingCrudCount as jest.Mock).mockResolvedValue(LEFTOVER_QUEUED_OPS);
      await renderAndWaitForStatus(<SetupSync />);
      await act(async () => {
        jest.advanceTimersByTime(STATUS_POLL_MS * 3);
      });

      expect(startBackgroundSync).toHaveBeenCalledTimes(1);
    });

    it('does not start the background sync service when nothing is queued', async () => {
      await renderAndWaitForStatus(<SetupSync />);

      expect(startBackgroundSync).not.toHaveBeenCalled();
    });

    it('starts the background sync service after queueing a resync', async () => {
      await renderAndWaitForStatus(<SetupSync />);
      await act(async () => {
        fireEvent.click(screen.getByTestId('sync-button'));
      });

      expect(psResyncTable).toHaveBeenCalledTimes(1);
      expect(startBackgroundSync).toHaveBeenCalledTimes(1);
      expect((psResyncTable as jest.Mock).mock.invocationCallOrder[0])
        .toBeLessThan((startBackgroundSync as jest.Mock).mock.invocationCallOrder[0]);
    });

    it('does not start the background sync service when the resync fails', async () => {
      (psResyncTable as jest.Mock).mockRejectedValueOnce(new Error('resync failed'));
      await renderAndWaitForStatus(<SetupSync />);
      await act(async () => {
        fireEvent.click(screen.getByTestId('sync-button'));
      });

      expect(psResyncTable).toHaveBeenCalledTimes(1);
      expect(startBackgroundSync).not.toHaveBeenCalled();
    });
  });
});

