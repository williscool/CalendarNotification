import { OPSqliteOpenFactory } from '@powersync/op-sqlite';
import { PowerSyncDatabase } from '@powersync/react-native';
import { Connector } from './Connector';
import { AppSchema } from './Schema';

const factory = new OPSqliteOpenFactory(
  {
    // Filename for the SQLite database — it's important to only instantiate one instance per file.
    dbFilename: 'Events',
    // Optional. Directory where the database file is located.'
    // dbLocation: '/databases'
  }
);


/**
 * Instantiate the local PowerSync database
 */
export const db = new PowerSyncDatabase({
  // The schema you defined in the previous step
  schema: AppSchema,
  database: factory,
  // database: {
  //   // Filename for the SQLite database — it's important to only instantiate one instance per file.
  //   dbFilename: 'Events',
  //   // Optional. Directory where the database file is located.'
  //   dbLocation: '/databases'
  // }
});

export const setupPowerSync = async () => {
  // Uses the backend connector that will be created in the next section
  const connector = new Connector();
  db.connect(connector);

  await db.init();
};