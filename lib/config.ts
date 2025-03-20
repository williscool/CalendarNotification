// TODO: move to app settings
type SyncType = 'unidirectional' | 'bidirectional' | 'none';

export const ConfigObj = {
	supabase:{
		url: process.env.EXPO_PUBLIC_SUPABASE_URL || '',
		anonKey: process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY || '',
		bucket: process.env.EXPO_PUBLIC_SUPABASE_BUCKET || '',
	},
	powersync:{
		url: process.env.EXPO_PUBLIC_POWERSYNC_URL || '',
		token: process.env.EXPO_PUBLIC_POWERSYNC_TOKEN || ''
	},
	sync: {
		enabled: process.env.EXPO_PUBLIC_SYNC_ENABLED !== 'false',
		type: (process.env.EXPO_PUBLIC_SYNC_TYPE || 'unidirectional') as SyncType,
	}
};