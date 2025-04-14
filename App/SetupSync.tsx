import React, { useContext, useEffect, useState } from 'react';
import { StyleSheet, Text, View, Button, TouchableOpacity, ScrollView, Linking } from 'react-native';
import { hello, MyModuleView, setValueAsync, addChangeListener } from '../modules/my-module';
import { open } from '@op-engineering/op-sqlite';
import { useQuery } from '@powersync/react';
import { PowerSyncContext } from "@powersync/react";
import { installCrsqliteOnTable } from '@lib/cr-sqlite/install';
import { psInsertDbTable, psClearTable } from '@lib/orm';
import { useNavigation } from '@react-navigation/native';
import { useSettings } from '@lib/hooks/SettingsContext';
import { GITHUB_README_URL } from '@lib/constants';

// Split out type imports for better readability
import type { RawRescheduleConfirmation } from '../modules/my-module';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from './index';

type NavigationProp = NativeStackNavigationProp<RootStackParamList>;

export const SetupSync = () => {
  const navigation = useNavigation<NavigationProp>();
  const { settings } = useSettings();
  const debugDisplayKeys = ['id', 'ttl', 'istart' ,'loc'];
  const [showDangerZone, setShowDangerZone] = useState(false);
  const [showDebugOutput, setShowDebugOutput] = useState(false);
  const [isConnected, setIsConnected] = useState<boolean | null>(null);

  const isConfigured = Boolean(
    settings.supabaseUrl &&
    settings.supabaseAnonKey &&
    settings.powersyncUrl &&
    settings.powersyncToken
  );

  const numEventsToDisplay = 3;

  const debugDisplayQuery = `select ${debugDisplayKeys.join(', ')} from eventsV9 limit ${numEventsToDisplay}`;

  const { data: psEvents } = useQuery<string>(debugDisplayQuery);
  const { data: rawConfirmations } = useQuery<RawRescheduleConfirmation>(`select event_id, calendar_id, original_instance_start_time, title, new_instance_start_time, is_in_future, created_at, updated_at from reschedule_confirmations`);

  const [sqliteEvents, setSqliteEvents] = useState<any[]>([]);
  const [tempTableEvents, setTempTableEvents] = useState<any[]>([]);
  const [dbStatus, setDbStatus] = useState<string>('');
  const [lastUpdate, setLastUpdate] = useState<string>('');
  const regDb = open({ name: 'Events' }); 
  const providerDb = useContext(PowerSyncContext);

  useEffect(() => {
    (async () => {
      if (settings.syncEnabled && settings.syncType === 'bidirectional') {
        await installCrsqliteOnTable('Events', 'eventsV9');
      }

      // Query events from SQLite
      const result = await regDb.execute(debugDisplayQuery);
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }

      // Query temp table events only in bidirectional mode
      if (settings.syncEnabled && settings.syncType === 'bidirectional') {
        const tempResult = await regDb.execute(debugDisplayQuery);
        if (tempResult?.rows) {
          setTempTableEvents(tempResult.rows || []);
        }
      }
    })();

    addChangeListener((value) => {
      console.log(hello());
      console.log('value changed', value);
    });

    // Set up interval to check PowerSync status
    const statusInterval = setInterval(() => {
      if (providerDb) {
        setDbStatus(JSON.stringify(providerDb.currentStatus));
        setLastUpdate(new Date().toLocaleTimeString());
        
        // Only update connection status if PowerSync has initialized
        // (hasSynced is defined and currentStatus object is populated)
        if (providerDb.currentStatus && providerDb.currentStatus.hasSynced !== undefined) {
          setIsConnected(providerDb.currentStatus.connected);
        }
      }
    }, 1000);

    // Cleanup interval on component unmount
    return () => clearInterval(statusInterval);
  }, [settings.syncEnabled, settings.syncType]);

  const handleSync = async () => {
    if (!providerDb || !settings.syncEnabled) return;
    
    try {
      await psInsertDbTable('Events', 'eventsV9', providerDb);
      // Refresh the events display after sync
      const result = await regDb.execute(debugDisplayQuery);
      if (result?.rows) {
        setSqliteEvents(result.rows || []);
      }
    } catch (error) {
      console.error('Failed to sync data:', error);
    }
  };

  if (!isConfigured) {
    return (
      <View style={styles.container}>
        <Text style={styles.hello}>PowerSync not configured</Text>
        <Text style={styles.subtext}>Please configure your sync settings to continue</Text>
        <Text style={styles.subtext}>
          For setup instructions, please visit our
        </Text>
        <Text   
          style={[styles.hello, styles.link]}
          onPress={() => Linking.openURL(GITHUB_README_URL)}
        >
          GitHub README
        </Text>
        <Text style={[styles.subtext, { marginBottom: 20 }]}>
          or
        </Text>
        <Button 
          title="Go to Settings" 
          onPress={() => navigation.navigate('Settings')}
        />
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.contentContainer} style={styles.scrollContainer}>
      <Text style={styles.hello} selectable>PowerSync Status: {dbStatus}</Text>
      <Text style={styles.hello}>Last Updated: {lastUpdate}</Text>

      {isConnected === false && (
        <View style={styles.connectionWarning}>
          <Text style={styles.warningText}>
            ⚠️ PowerSync is not connected. Sync features are disabled.
          </Text>
          <TouchableOpacity 
            style={styles.settingsLinkButton}
            onPress={() => navigation.navigate('Settings')}
          >
            <Text style={styles.settingsLinkText}>Go to Settings</Text>
          </TouchableOpacity>
        </View>
      )}

      {isConnected === null && (
        <View style={styles.initializingWarning}>
          <Text style={styles.initializingText}>
            ⏳ PowerSync is initializing... Please wait.
          </Text>
        </View>
      )}

      <TouchableOpacity 
        style={[styles.toggleButton, styles.debugToggleButton]}
        onPress={() => setShowDebugOutput(!showDebugOutput)}
      >
        <Text style={styles.toggleButtonText}>
          {showDebugOutput ? 'Hide Debug Data' : 'Show Debug Data'}
        </Text>
      </TouchableOpacity>

      {showDebugOutput && (
        <View style={styles.debugSection}>
          <Text style={styles.hello} selectable> Sample Local SQLite Events eventsV9: {JSON.stringify(sqliteEvents)}</Text>
          <Text style={styles.hello} selectable> Sample PowerSync Remote Events: {JSON.stringify(psEvents)}</Text>

          <Text style={styles.hello} selectable> Sample PowerSync Remote Events reschedule_confirmations: {JSON.stringify(rawConfirmations?.slice(0, numEventsToDisplay))}</Text>
          {settings.syncEnabled && settings.syncType === 'bidirectional' && (
            <Text style={styles.hello} selectable>Events V9 Temp Table: {JSON.stringify(tempTableEvents)}</Text>
          )}
        </View>
      )}

      <TouchableOpacity 
        style={[styles.syncButton, isConnected === false && styles.disabledButton]}
        onPress={isConnected === false ? undefined : handleSync}
        disabled={isConnected === false}
      >
        {/* TODO: move to background job instead of buttton  */}
        <Text style={styles.syncButtonText}>Sync Events Local To PowerSync Now</Text>
      </TouchableOpacity>

      <TouchableOpacity 
        style={[
          styles.toggleButton, 
          showDangerZone && styles.toggleButtonActive,
          isConnected === false && styles.disabledButton
        ]}
        onPress={isConnected === false ? undefined : () => setShowDangerZone(!showDangerZone)}
        disabled={isConnected === false}
      >
        <Text style={[
          styles.toggleButtonText, 
          showDangerZone && styles.toggleButtonTextActive,
          isConnected === false && styles.disabledButtonText
        ]}>
          {showDangerZone ? '🔒 Hide Danger Zone' : '⚠️ Show Danger Zone'}
        </Text>
      </TouchableOpacity>

      {showDangerZone && isConnected !== false && (
        <>
          <View style={styles.warningContainer}>
            <Text style={styles.warningText}>
              ⚠️ WARNING: This will dismiss potentially many events from your local device!{'\n'}
              You can restore them from the bin. 
            </Text>
          </View>

          <TouchableOpacity 
            style={[styles.syncButton, styles.yellowButton]}
            onPress={async () => {
              if (rawConfirmations) {
                await setValueAsync(rawConfirmations);
              }
            }}
          >
            <Text style={styles.yellowButtonText}>Send Reschedule Confirmations</Text>
          </TouchableOpacity>

          <View style={styles.warningContainer}>
            <Text style={styles.warningText}>
              ⚠️ WARNING: This will only delete events from the remote PowerSync database.{'\n'}
              Your local device events will remain unchanged.
            </Text>
          </View>

          <TouchableOpacity 
            style={[styles.syncButton, styles.deleteButton]}
            onPress={async () => {
              try {
                await psClearTable('eventsV9', providerDb);
              } catch (error) {
                console.error('Failed to clear PowerSync events:', error);
              }
            }}
          >
            <Text style={styles.syncButtonText}>Clear Remote PowerSync Events</Text>
          </TouchableOpacity>
        </>
      )}


      {/* this native module can be used to communicate with the kolin code */}
      {/* I want to use it to get things like the mute status of a notification  */}
      {/* or whatever other useful things. so dont delete it so I remember to use it later  */}

      
      {/* <MyModuleView name="MyModuleView" /> */}

    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
  },
  scrollContainer: {
    flex: 1,
  },
  contentContainer: {
    paddingVertical: 20,
    paddingHorizontal: 10,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  hello: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  settingsButton: {
    padding: 8,
  },
  settingsButtonText: {
    fontSize: 16,
    color: '#007AFF',
  },
  subtext: {
    textAlign: 'center',
    margin: 10,
  },
  syncButton: {
    backgroundColor: '#28a745',
    padding: 15,
    borderRadius: 8,
    marginTop: 20,
    marginHorizontal: 20,
  },
  syncButtonText: {
    color: '#fff',
    fontSize: 16,
    textAlign: 'center',
    fontWeight: '600',
  },
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  debugContainer: {
    backgroundColor: '#fff',
    padding: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#eee',
  },
  debugText: {
    fontSize: 16,
  },
  deleteButton: {
    backgroundColor: '#FF3B30',
  },
  yellowButton: {
    backgroundColor: '#FFD700',
  },
  yellowButtonText: {
    color: '#000000',
    fontSize: 16,
    textAlign: 'center',
    fontWeight: '600',
  },
  warningContainer: {
    backgroundColor: '#fff',
    padding: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#eee',
    marginTop: 20,
    marginHorizontal: 20,
  },
  warningText: {
    fontSize: 16,
    color: '#FF3B30',
  },
  toggleButton: {
    backgroundColor: '#007AFF',
    padding: 10,
    borderRadius: 8,
    marginTop: 20,
    marginHorizontal: 20,
  },
  toggleButtonActive: {
    backgroundColor: '#FF3B30',
  },
  toggleButtonText: {
    color: '#fff',
    fontSize: 16,
    textAlign: 'center',
    fontWeight: '600',
  },
  toggleButtonTextActive: {
    color: '#fff',
  },
  debugSection: {
    marginVertical: 10,
    padding: 10,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  debugToggleButton: {
    backgroundColor: '#6c757d',
    marginTop: 10,
  },
  disabledButton: {
    backgroundColor: '#cccccc',
    opacity: 0.7,
  },
  disabledButtonText: {
    color: '#888888',
  },
  connectionWarning: {
    backgroundColor: '#fff3cd',
    padding: 15,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ffeeba',
    marginVertical: 15,
    marginHorizontal: 20,
    alignItems: 'center',
  },
  settingsLinkButton: {
    marginTop: 10,
    padding: 8,
  },
  settingsLinkText: {
    color: '#007AFF',
    fontSize: 16,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
  initializingWarning: {
    backgroundColor: '#e2f3fc',
    padding: 15,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#90caf9',
    marginVertical: 15,
    marginHorizontal: 20,
    alignItems: 'center',
  },
  initializingText: {
    fontSize: 16,
    color: '#0277bd',
  },
  link: {
    color: '#007AFF',
    textDecorationLine: 'underline',
  },
  warningActions: {
    flexDirection: 'column',
    alignItems: 'center',
    gap: 8,
    marginTop: 10,
  },
}); 