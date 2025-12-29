import { open } from '@op-engineering/op-sqlite';
import { emitSyncLog } from '../logging/syncLog';


export const installCrsqliteOnTable = async (databaseName: string, tableName: string) => {
  const db = open({name: databaseName});
  try {
    await db.execute("SELECT load_extension('crsqlite_requery', 'sqlite3_crsqlite_init')");
    
    // Check if table exists before creating temp table
    const tableExists = await db.execute(`SELECT name FROM sqlite_master WHERE type='table' AND name='${tableName}'`);
    emitSyncLog('debug', 'crsqlite table check', { tableName, exists: tableExists?.rows?.length > 0 });

    if (tableExists?.rows?.length > 0) {
        // Unique index on 2 columns gets error below
        // statement execution error: Table has unique indices besides the primary key. This is not allowed for CRRs
        await db.execute(`DROP INDEX IF EXISTS ${tableName}Idx`);
        await db.execute(`CREATE TABLE IF NOT EXISTS ${tableName}_temp AS SELECT * FROM ${tableName}`);

        const result = await db.execute(`select id, Count(*) from ${tableName}_temp group by id`);
        emitSyncLog('debug', 'crsqlite temp table created', { tableName, numEvents: result?.rows?.length });

        // Get schema for table
        const tableSchema = await db.execute(`SELECT sql FROM sqlite_master WHERE type='table' AND name='${tableName}'`);

        const originalSchema = tableSchema?.rows?.[0]?.sql as string;

        emitSyncLog('debug', 'crsqlite original schema', { tableName, schema: originalSchema });

        const newSchemaStatementLines = originalSchema.split(',')

        const idIndex = newSchemaStatementLines.findIndex(i => i === " id INT" || i === " id INTEGER")
        
        newSchemaStatementLines[idIndex] = "id INT NOT NULL PRIMARY KEY"
        
        emitSyncLog('debug', 'crsqlite schema modification', { idIndex, numLines: newSchemaStatementLines.length });

        const newSchema = newSchemaStatementLines.join(',')
        .replace(", PRIMARY KEY (id, istart)", "")

        emitSyncLog('debug', 'crsqlite new schema', { tableName, schema: newSchema });
        
        // Drop table with old schema
        await db.execute(`DROP TABLE IF EXISTS ${tableName}`);

        // Create table with schema 
        await db.execute(newSchema);
        
        try {
          await db.execute(`INSERT OR REPLACE INTO ${tableName} SELECT * FROM ${tableName}_temp`);
        } catch (error) {
          emitSyncLog('error', 'crsqlite failed to insert/update data from temp table', { error });
        }
    } 
    
    // recreate index with no unique constraint
    await db.execute(`CREATE INDEX IF NOT EXISTS ${tableName}Idx ON ${tableName} (id, istart)`);
    
    // Enable CRDT behavior for the table
    await db.execute(`SELECT crsql_as_crr('${tableName}')`);
    
    // Verify CRR clock table was created
    const clockTable = await db.execute(`SELECT name FROM sqlite_master WHERE type='table' AND name='${tableName}__crsql_clock'`);
    emitSyncLog('info', 'crsqlite CRR enabled', { 
      tableName, 
      clockTableCreated: (clockTable?.rows?.length ?? 0) > 0 
    });
    
    await db.execute("SELECT crsql_finalize();");
  } catch (error) {
    emitSyncLog('error', 'crsqlite extension load failed', { error });
  }
}; 