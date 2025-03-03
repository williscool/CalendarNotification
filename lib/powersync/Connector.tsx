// lib/Connector.js
// import { UpdateType } from '@powersync/react-native';

/// Postgres Response codes that we cannot recover from by retrying.
const FATAL_RESPONSE_CODES = [
  // Class 22 — Data Exception
  // Examples include data type mismatch.
  new RegExp('^22...$'),
  // Class 23 — Integrity Constraint Violation.
  // Examples include NOT NULL, FOREIGN KEY and UNIQUE violations.
  new RegExp('^23...$'),
  // INSUFFICIENT PRIVILEGE - typically a row-level security violation
  new RegExp('^42501$')
];

export class Connector {
    async fetchCredentials() {
    // Implement fetchCredentials to obtain a JWT from your authentication service.
    // See https://docs.powersync.com/installation/authentication-setup
    // If you're using Supabase or Firebase, you can re-use the JWT from those clients, see
    // - https://docs.powersync.com/installation/authentication-setup/supabase-auth
    // - https://docs.powersync.com/installation/authentication-setup/firebase-auth
        return {
            endpoint: 'https://67c38073586c8d282a06c3c2.powersync.journeyapps.com',
            // Use a development token (see Authentication Setup https://docs.powersync.com/installation/authentication-setup/development-tokens) to get up and running quickly
			// TODO: programattically generate token from user id (i.e. email or phone number) + random secret
            token: 'eyJhbGciOiJSUzI1NiIsImtpZCI6InBvd2Vyc3luYy1kZXYtMzIyM2Q0ZTMifQ.eyJzdWIiOiIxIiwiaWF0IjoxNzQwODc1Njg5LCJpc3MiOiJodHRwczovL3Bvd2Vyc3luYy1hcGkuam91cm5leWFwcHMuY29tIiwiYXVkIjoiaHR0cHM6Ly82N2MzODA3MzU4NmM4ZDI4MmEwNmMzYzIucG93ZXJzeW5jLmpvdXJuZXlhcHBzLmNvbSIsImV4cCI6MTc0MDkxODg4OX0.GVGtHCtSeVQL1Nf72262dZ_cNcrAn-wzzff6ePnpWDElT_D7590-yv2nIAjloCCbzZ9BvXMle0xmmFoE7hGWZ8U2McUIPnz_jvhkzDjxxkMDmTc9y1WQ2OyU9rIEACjCbCYYpKHxQN_kEgsHnngK7kZHGX4farZ5DxP0w5fcNrOTz6ls4G8K3bz08p7lrAfzkQaicdqW-7d1AplYHyLNNgX9ACOfFZJand87kA4LTf9YHfvW5S4kWA4SycVswJsmJh2pW8bjL7dKCY74J-FoEnUG1oZCsv2XXQWaWHjUnYoXs3k6-6cKOP-HXXX94jZaFEltXhG6lCbdgmBRxxlKng'
        };
    }

    async uploadData(database) {
     // Implement uploadData to send local changes to your backend service.
     // You can omit this method if you only want to sync data from the database to the client

     // See example implementation here:https://docs.powersync.com/client-sdk-references/react-native-and-expo#3-integrate-with-your-backend
	}
}