import { open } from '@op-engineering/op-sqlite';
import { AbstractPowerSyncDatabase } from '@powersync/react-native';

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
) {
  const regDb = open({ name: dbName });

  try {
    // Get all data from the regular SQLite table
    const fullTableResult = await regDb.execute(`SELECT * FROM ${tableName}`);
    const fullTableEvents = fullTableResult?.rows || [];
    
    if (fullTableEvents.length === 0) {
      console.log(`No data found in table ${tableName}`);
      return;
    }

    // Extract column names from the first row
    const fullTableEventsKeys = Object.keys(fullTableEvents[0]);
    
    // Convert data to array format for batch insert
    const fullTableEventsArray = fullTableEvents.map(event => 
      fullTableEventsKeys.map(key => event[key])
    );

    // Construct the insert query
    const powerSyncInsertQuery = `INSERT OR REPLACE INTO ${tableName} (${fullTableEventsKeys.join(', ')}) VALUES (${fullTableEventsKeys.map(() => '?').join(', ')})`;

    // Execute the batch insert
    const powerSyncInsertResult = await psDb.executeBatch(powerSyncInsertQuery, fullTableEventsArray);
    console.log(`Successfully inserted ${fullTableEventsArray.length} rows into ${tableName}`);
    
    return powerSyncInsertResult;
  } catch (error) {
    console.error(`Failed to insert data from ${tableName}:`, error);
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
    console.log(`Successfully cleared all records from PowerSync table ${tableName}`);
    return deleteResult;
  } catch (error) {
    console.error(`Failed to clear PowerSync table ${tableName}:`, error);
    throw error;
  }
}

