import { open } from '@op-engineering/op-sqlite';
import { AbstractPowerSyncDatabase } from '@powersync/react-native';
import { emitSyncLog } from '../powersync/Connector';

/** SQLite primitive value types */
type SqliteValue = string | number | null | Uint8Array;

/** A row from a SQLite query result */
type SqliteRow = Record<string, SqliteValue>;

/**
 * Inserts data from a regular SQLite table into a PowerSync table
 * @param dbName The name of the regular SQLite database
 * @param tableName The name of the table to copy data from/to
 * @param psDb The PowerSync database instance
 */
export async function psInsertDbTable(
  dbName: string, 
  tableName: string,
  psDb: AbstractPowerSyncDatabase
): Promise<void> {
  const regDb = open({ name: dbName });

  try {
    // Get all data from the regular SQLite table
    const fullTableResult = await regDb.execute(`SELECT * FROM ${tableName}`);
    const rows: SqliteRow[] = fullTableResult?.rows || [];
    
    if (rows.length === 0) {
      emitSyncLog('info', `No data found in table ${tableName}`);
      return;
    }

    // Extract column names from the first row
    const columnNames = Object.keys(rows[0]);
    
    // Convert data to array format for batch insert
    // PowerSync requires 'id' to be text, so convert any non-string id to string
    const valuesArray: SqliteValue[][] = rows.map((row: SqliteRow) => 
      columnNames.map((key: string): SqliteValue => {
        const value = row[key];
        // Convert id to string if it's not already (PowerSync requires text id)
        if (key === 'id' && value !== null && typeof value !== 'string') {
          return String(value);
        }
        return value;
      })
    );

    // Construct the insert query
    const powerSyncInsertQuery = `INSERT OR REPLACE INTO ${tableName} (${columnNames.join(', ')}) VALUES (${columnNames.map(() => '?').join(', ')})`;

    // Execute the batch insert
    const result = await psDb.executeBatch(powerSyncInsertQuery, valuesArray);
    emitSyncLog('info', `Successfully inserted ${result.rowsAffected} rows into ${tableName}`);
  } catch (error) {
    emitSyncLog('error', `Failed to insert data from ${tableName}`, { error });
    throw error;
  }
} 

/**
 * Deletes all records from a PowerSync table
 * @param tableName The name of the PowerSync table to clear
 * @param psDb The PowerSync database instance
 * @returns The result of the delete operation
 */
export async function psClearTable(
  tableName: string,
  psDb: AbstractPowerSyncDatabase
) {
  try {
    const deleteResult = await psDb.execute(`DELETE FROM ${tableName}`);
    emitSyncLog('info', `Successfully cleared all records from PowerSync table ${tableName}`);
    return deleteResult;
  } catch (error) {
    emitSyncLog('error', `Failed to clear PowerSync table ${tableName}`, { error });
    throw error;
  }
}

