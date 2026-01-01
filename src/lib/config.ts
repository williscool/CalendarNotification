// @ts-ignore - @env is provided by react-native-dotenv
import {
	EXPO_PUBLIC_SUPABASE_URL,
	EXPO_PUBLIC_SUPABASE_ANON_KEY,
	EXPO_PUBLIC_SUPABASE_BUCKET,
	EXPO_PUBLIC_POWERSYNC_URL,
	EXPO_PUBLIC_POWERSYNC_SECRET,
	EXPO_PUBLIC_SYNC_ENABLED,
	EXPO_PUBLIC_SYNC_TYPE,
} from '@env';

type SyncType = 'unidirectional' | 'bidirectional' | 'none';

// Debug: log env vars at module load time
if (__DEV__) {
	console.log('[ConfigObj] Loading env vars:', {
		SUPABASE_URL: EXPO_PUBLIC_SUPABASE_URL ? 'SET' : 'EMPTY',
		SUPABASE_ANON_KEY: EXPO_PUBLIC_SUPABASE_ANON_KEY ? 'SET' : 'EMPTY',
		POWERSYNC_URL: EXPO_PUBLIC_POWERSYNC_URL ? 'SET' : 'EMPTY',
		POWERSYNC_SECRET: EXPO_PUBLIC_POWERSYNC_SECRET ? 'SET' : 'EMPTY',
	});
}

export const ConfigObj = {
	supabase:{
		url: EXPO_PUBLIC_SUPABASE_URL || '',
		anonKey: EXPO_PUBLIC_SUPABASE_ANON_KEY || '',
		bucket: EXPO_PUBLIC_SUPABASE_BUCKET || '',
	},
	powersync:{
		url: EXPO_PUBLIC_POWERSYNC_URL || '',
		secret: EXPO_PUBLIC_POWERSYNC_SECRET || ''
	},
	sync: {
		enabled: EXPO_PUBLIC_SYNC_ENABLED !== 'false',
		type: (EXPO_PUBLIC_SYNC_TYPE || 'unidirectional') as SyncType,
	}
};