/**
 * Environment Variables Setup
 * 
 * This file sets up environment variables for the app.
 * In development, it will load variables from .env file.
 * In production, environment variables should be set through the deployment platform.
 * 
 * Note: Expo automatically loads environment variables from the .env file,
 * so we don't need to use dotenv explicitly.
 */

// Log environment variables loaded in development mode
if (__DEV__) {
  console.log('Environment variables loaded by Expo in development mode');
}

// Make sure environment is available
export const getEnv = () => {
  return {
    SUPABASE_URL: process.env.EXPO_PUBLIC_SUPABASE_URL,
    SUPABASE_ANON_KEY: process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY,
    SUPABASE_BUCKET: process.env.EXPO_PUBLIC_SUPABASE_BUCKET,
    POWERSYNC_URL: process.env.EXPO_PUBLIC_POWERSYNC_URL,
    POWERSYNC_SECRET: process.env.EXPO_PUBLIC_POWERSYNC_SECRET,
    SYNC_ENABLED: process.env.EXPO_PUBLIC_SYNC_ENABLED,
    SYNC_TYPE: process.env.EXPO_PUBLIC_SYNC_TYPE,
  };
}; 