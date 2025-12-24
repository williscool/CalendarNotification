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
import React from 'react';
import { render, screen, act } from '@testing-library/react';
import { getColors } from '@lib/theme/colors';

// Test state - use object so mutations are visible to hoisted mocks
const testState = {
  settings: {
    syncEnabled: false,
    syncType: 'unidirectional' as const,
    supabaseUrl: '',
    supabaseAnonKey: '',
    powersyncUrl: '',
    powersyncToken: '',
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

// Helper to configure test scenarios
const configureSettings = (configured: boolean) => {
  if (configured) {
    testState.settings = {
      syncEnabled: true,
      syncType: 'unidirectional',
      supabaseUrl: 'https://example.supabase.co',
      supabaseAnonKey: 'anon-key-123',
      powersyncUrl: 'https://example.powersync.com',
      powersyncToken: 'token123',
    };
  } else {
    testState.settings = {
      syncEnabled: false,
      syncType: 'unidirectional',
      supabaseUrl: '',
      supabaseAnonKey: '',
      powersyncUrl: '',
      powersyncToken: '',
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

describe('SetupSync UI States', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    // Reset to defaults
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
        powersyncToken: 'token123',
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
  });
});

