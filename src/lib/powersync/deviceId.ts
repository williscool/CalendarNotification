/**
 * Device ID helper for PowerSync authentication.
 * Generates and persists a unique UUID for this device installation.
 * 
 * Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
 */

import AsyncStorage from '@react-native-async-storage/async-storage';

const DEVICE_ID_KEY = '@powersync_device_id';

/**
 * Generates a UUID v4 without external dependencies.
 * Uses crypto.getRandomValues when available, falls back to Math.random.
 */
const generateUUID = (): string => {
  // Use crypto API if available (React Native has it via Hermes)
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  
  // Fallback UUID v4 generation
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

/**
 * Gets the existing device ID or creates a new one.
 * The ID is persisted in AsyncStorage and remains stable across app restarts.
 */
export const getOrCreateDeviceId = async (): Promise<string> => {
  try {
    const existingId = await AsyncStorage.getItem(DEVICE_ID_KEY);
    if (existingId) {
      return existingId;
    }
    
    const newId = generateUUID();
    await AsyncStorage.setItem(DEVICE_ID_KEY, newId);
    return newId;
  } catch (error) {
    // If storage fails, generate a new ID (won't persist but allows operation)
    console.warn('[deviceId] Failed to access AsyncStorage, using ephemeral ID:', error);
    return generateUUID();
  }
};

/**
 * Clears the stored device ID. Useful for testing or reset scenarios.
 */
export const clearDeviceId = async (): Promise<void> => {
  await AsyncStorage.removeItem(DEVICE_ID_KEY);
};

