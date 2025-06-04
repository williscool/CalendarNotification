import { createClient } from '@supabase/supabase-js';
import { PowerSyncDatabase } from '@powersync/react-native';
import { Connector } from './powersync/Connector';
import { Settings } from './hooks/SettingsContext';

export async function setupRemoteDatabaseConnections(
  settings: Settings,
  powerSyncDb: PowerSyncDatabase
) {
  // Initialize Supabase client
  const supabaseClient = createClient(settings.supabaseUrl, settings.supabaseAnonKey);
  
  // Initialize PowerSync connector with Supabase client
  const connector = new Connector(settings, supabaseClient);
  powerSyncDb.connect(connector);

  try {
    await powerSyncDb.init();
  } catch (e) {
    console.log('Error initializing PowerSync connection:', e);
  }

  return {
    supabaseClient
  };
} 