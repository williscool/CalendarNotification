// TODO: move to app settings
type SyncType = 'unidirectional' | 'bidirectional' | 'none';

export const ConfigObj = {
	supabase:{
		// url: process.env.EXPO_PUBLIC_SUPABASE_URL || '',
		url: 'https://bnaydawlivfeewhwtvry.supabase.co',
		// anonKey: process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY || '',
		anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJuYXlkYXdsaXZmZWV3aHd0dnJ5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDA4NjI5MDMsImV4cCI6MjA1NjQzODkwM30.M4Kl6odVaShvuA7cp1xVUljqUeqeDEFJeMyAtZwNQaE',
		bucket: process.env.EXPO_PUBLIC_SUPABASE_BUCKET || '',
	},
	powersync:{
		// url: process.env.EXPO_PUBLIC_POWERSYNC_URL || ''
		url: 'https://67c38073586c8d282a06c3c2.powersync.journeyapps.com',
		// token: process.env.EXPO_PUBLIC_POWERSYNC_TOKEN || ''
		token : 'eyJhbGciOiJSUzI1NiIsImtpZCI6InBvd2Vyc3luYy1kZXYtMzIyM2Q0ZTMifQ.eyJzdWIiOiIxIiwiaWF0IjoxNzQyMzMyMzcxLCJpc3MiOiJodHRwczovL3Bvd2Vyc3luYy1hcGkuam91cm5leWFwcHMuY29tIiwiYXVkIjoiaHR0cHM6Ly82N2MzODA3MzU4NmM4ZDI4MmEwNmMzYzIucG93ZXJzeW5jLmpvdXJuZXlhcHBzLmNvbSIsImV4cCI6MTc0MjM3NTU3MX0.nAQLGxW8scc8Ah16ow3L4ZgKwF7W-0_99eChceJfy2eFRAH8i5_eC7WHpfrRGU4xZ0aAfDqlfHr6PPL9UIjqAegsppXGwD_9B4sEb8hGGUrfFOlG7ivSDdFFAYOgea2snJcfqk5X4CfcnpcbCVBZsKWbUur-LAmZkis4vg4HRprrVAXk1iBxRWlNGZcrEZHdUX8ZdlvWVOE9p8diqLpzHHm1HLcMbXoX3CD_wVTqBO0yGJwpRldmpqiAHm1deZ10ksmXdvq8HElvh016fRWaxzF6yoC7HSH0EpYjAMPDnmlxUoydtSpWRqphRLYdbXEJ-HnViNHJi6CFUH593M7ekQ'
	},
	sync: {
		enabled: true,
		type: 'unidirectional' as SyncType,
	}
};