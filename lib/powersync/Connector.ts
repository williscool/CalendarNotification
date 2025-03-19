import 'react-native-url-polyfill/auto'

// lib/Connector.js
import { UpdateType, AbstractPowerSyncDatabase, PowerSyncBackendConnector, CrudEntry } from '@powersync/react-native';
import { SupabaseClient, createClient, PostgrestSingleResponse } from '@supabase/supabase-js';
import { Settings } from '../hooks/useStoredSettings';

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

interface SupabaseError {
  code: string;
}

export class Connector implements PowerSyncBackendConnector {
    client: SupabaseClient;
    private settings: Settings;

    constructor(settings: Settings) {
        this.settings = settings;
        // TODO setup session storage to support supabase auth
        // right now its not needed because will have people input
        // there own powersync token an supabase links in the app to start
        this.client = createClient(settings.supabaseUrl, settings.supabaseAnonKey);
    }

    async fetchCredentials() {
        return {
            endpoint: this.settings.powersyncUrl,
            token: this.settings.powersyncToken  // TODO: programattically generate token from user id (i.e. email or phone number) + random secret
        };
    }

    async uploadData(database: AbstractPowerSyncDatabase): Promise<void> {
        // based on https://github.com/powersync-ja/powersync-js/blob/main/demos/react-native-supabase-todolist/library/supabase/SupabaseConnector.ts
        // https://github.com/powersync-ja/powersync-js/blob/main/demos/react-native-supabase-todolist/library/powersync/system.ts
        const transaction = await database.getNextCrudTransaction();
    
        if (!transaction) {
          return;
        }
    
        let lastOp: CrudEntry | null = null;
        try {
          // Note: If transactional consistency is important, use database functions
          // or edge functions to process the entire transaction in a single call.
          for (const op of transaction.crud) {
            lastOp = op;
            const table = this.client.from(op.table);
            let result: PostgrestSingleResponse<null> | null = null;
            switch (op.op) {
              case UpdateType.PUT:
                // eslint-disable-next-line no-case-declarations
                const record = { ...op.opData, id: op.id };
                result = await table.upsert(record);
                break;
              case UpdateType.PATCH:
                result = await table.update(op.opData).eq('id', op.id);
                break;
              case UpdateType.DELETE:
                result = await table.delete().eq('id', op.id);
                break;
            }
    
            if (result?.error) {
              console.error(result.error);
              result.error.message = `Could not ${op.op} data to Supabase error: ${JSON.stringify(result)}`;
              throw result.error;
            }
          }
    
          await transaction.complete();
        } catch (ex: unknown) {
          console.debug(ex);
          const error = ex as SupabaseError;
          if (typeof error.code === 'string' && FATAL_RESPONSE_CODES.some((regex) => regex.test(error.code))) {
            /**
             * Instead of blocking the queue with these errors,
             * discard the (rest of the) transaction.
             *
             * Note that these errors typically indicate a bug in the application.
             * If protecting against data loss is important, save the failing records
             * elsewhere instead of discarding, and/or notify the user.
             */
            console.error('Data upload error - discarding:', lastOp, error);
            await transaction.complete();
          } else {
            // Error may be retryable - e.g. network error or temporary server error.
            // Throwing an error here causes this call to be retried after a delay.
            throw ex;
          }
        }
      }
} 