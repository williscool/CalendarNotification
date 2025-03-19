import React, { useContext, useEffect, useState } from 'react';
import { StyleSheet, Text, View, Button, TouchableOpacity } from 'react-native';
import { hello, MyModuleView, setValueAsync, addChangeListener } from '../modules/my-module';
import { open } from '@op-engineering/op-sqlite';
import { useQuery } from '@powersync/react';
import { PowerSyncContext } from "@powersync/react";
import { installCrsqliteOnTable } from '@lib/cr-sqlite/install';
import { psInsertDbTable, psClearTable } from '@lib/orm';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from './index';
import { useSettings } from '@lib/hooks/SettingsContext';

type NavigationProp = NativeStackNavigationProp<RootStackParamList>;

export const SetupSync = () => {
  const navigation = useNavigation<NavigationProp>();
  const { settings } = useSettings();
  const debugDisplayKeys = ['id', 'ttl', 'loc'];
  const [showDangerZone, setShowDangerZone] = useState(false);

  const numEventsToDisplay = 3;

  const debugDisplayQuery = `select ${debugDisplayKeys.join(', ')} from eventsV9 limit ${numEventsToDisplay}`;

  const { data: psEvents } = useQuery<string>(debugDisplayQuery);
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
    })

    // Set up interval to check PowerSync status
    const statusInterval = setInterval(() => {
      setDbStatus(JSON.stringify(providerDb.currentStatus));
      setLastUpdate(new Date().toLocaleTimeString());
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

  if (!settings.supabaseUrl || !settings.supabaseAnonKey || !settings.powersyncUrl || !settings.powersyncToken) {
    return (
      <View style={styles.container}>
        <Text style={styles.hello}>PowerSync not configured</Text>
        <Text style={styles.subtext}>Please configure your sync settings to continue</Text>
        <Button 
          title="Go to Settings" 
          onPress={() => navigation.navigate('Settings')}
        />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.hello} selectable>PowerSync Status: {dbStatus}</Text>
      <Text style={styles.hello}>Last Updated: {lastUpdate}</Text>

      <Text style={styles.hello}> Sample Local SQLite Events eventsV9: {JSON.stringify(sqliteEvents)}</Text>
      
      <Text style={styles.hello}> Sample PowerSync Remote Events: {JSON.stringify(psEvents)}</Text>

      {settings.syncEnabled && settings.syncType === 'bidirectional' && (
        <Text style={styles.hello}>Events V9 Temp Table: {JSON.stringify(tempTableEvents)}</Text>
      )}

      <TouchableOpacity 
        style={styles.syncButton}
        onPress={handleSync}
      >
        {/* TODO: move to background job instead of buttton  */}
        <Text style={styles.syncButtonText}>Sync Events Local To PowerSync Now</Text>
      </TouchableOpacity>

      <TouchableOpacity 
        style={[styles.toggleButton, showDangerZone && styles.toggleButtonActive]}
        onPress={() => setShowDangerZone(!showDangerZone)}
      >
        <Text style={[styles.toggleButtonText, showDangerZone && styles.toggleButtonTextActive]}>
          {showDangerZone ? '🔒 Hide Danger Zone' : '⚠️ Show Danger Zone'}
        </Text>
      </TouchableOpacity>

      {showDangerZone && (
        <>
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

      {/* TODO: this native module can be used to communicate with the kolin code */}
      {/* I want to use it to get things like the mute status of a notification  */}
      {/* or whatever other useful things. so dont delete it so I remember to use it later  */}

      {/* <MyModuleView name="MyModuleView" />
      <Button
        title="Click me!"
        onPress={async () => {
          await setValueAsync('blarf');
        }}
      ></Button> */}

    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
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
}); 