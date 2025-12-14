import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, Linking, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { setupPowerSync } from '@lib/powersync';
import { useSettings } from '@lib/hooks/SettingsContext';
import { GITHUB_README_URL } from '@lib/constants';
import { SetupSync } from '@lib/features/SetupSync';

const InitialSetupScreen = () => {
  const navigation = useNavigation<any>();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Sync is not enabled</Text>
      <Text style={styles.subtitle}>Please enable sync in settings to continue</Text>
      <TouchableOpacity
        style={styles.button}
        onPress={() => navigation.navigate('Settings')}
      >
        <Text style={styles.buttonText}>Go to Settings</Text>
      </TouchableOpacity>
      <Text style={styles.subtitle}>or view our</Text>
      <TouchableOpacity onPress={() => Linking.openURL(GITHUB_README_URL)}>
        <Text style={styles.link}>Setup Guide</Text>
      </TouchableOpacity>
    </View>
  );
};

export default function HomeScreen() {
  const { settings } = useSettings();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    const init = async () => {
      if (settings.syncEnabled) {
        await setupPowerSync(settings);
      }
      setIsReady(true);
    };
    init();
  }, [settings.syncEnabled, settings]);

  if (!isReady) {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>Initializing...</Text>
      </View>
    );
  }

  return settings.syncEnabled ? <SetupSync /> : <InitialSetupScreen />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 20,
    textAlign: 'center',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    color: '#666',
    marginBottom: 20,
  },
  button: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
    marginBottom: 20,
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  link: {
    fontSize: 18,
    color: '#007AFF',
    textDecorationLine: 'underline',
  },
});
