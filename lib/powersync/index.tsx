import { OPSqliteOpenFactory } from '@powersync/op-sqlite';
import { PowerSyncDatabase } from '@powersync/react-native';
import { Connector } from './Connector';
import { AppSchema } from './Schema';

const factory = new OPSqliteOpenFactory(
  {
    // Filename for the SQLite database — it's important to only instantiate one instance per file.
    // dbFilename: 'Events',
    dbFilename: 'powerSyncEvents.db',
  }
);


/**
 * Instantiate the local PowerSync database
 * https://github.com/powersync-ja/powersync-js/blob/%40powersync/react-native%401.18.0/packages/powersync-op-sqlite/README.md
 */
export const db = new PowerSyncDatabase({
  // The schema you defined in the previous step
  schema: AppSchema,
  database: factory,
});

export const setupPowerSync = async () => {
  // Uses the backend connector that will be created in the next section
  const connector = new Connector();
  db.connect(connector);

  try {
    await db.init();
  } catch (e) {
    console.log(e, typeof e);
  }

  // console.log(db)
};