import { open } from '@op-engineering/op-sqlite';


export const installCrsqliteOnTable = async (databaseName: string, tableName: string) => {
  const db = open({name: databaseName});
  try {
    await db.execute("SELECT load_extension('crsqlite', 'sqlite3_crsqlite_init')");
  
    // Check if table exists before creating temp table
    const tableExists = await db.execute(`SELECT name FROM sqlite_master WHERE type='table' AND name='${tableName}'`);
    console.log('tableExists', tableExists?.rows);

    if (tableExists?.rows?.length > 0) {
        // Unique index on 2 columns gets error below
        // statement execution error: Table has unique indices besides the primary key. This is not allowed for CRRs
        await db.execute(`DROP INDEX IF EXISTS ${tableName}Idx`);
        await db.execute(`CREATE TABLE IF NOT EXISTS ${tableName}_temp AS SELECT * FROM ${tableName}`);

        const result = await db.execute(`select id, Count(*) from ${tableName}_temp group by id`);
        console.log("num events in temp table:", result?.rows?.length);

        // Get schema for table
        const tableSchema = await db.execute(`SELECT sql FROM sqlite_master WHERE type='table' AND name='${tableName}'`);

        const originalSchema = tableSchema?.rows?.[0]?.sql as string;

        console.log(`original ${tableName} schema:`, originalSchema);

        const newSchemaStatementLines = originalSchema.split(',')

        const idIndex = newSchemaStatementLines.findIndex(i => i === " id INT" || i === " id INTEGER")
        
        newSchemaStatementLines[idIndex] = "id INT NOT NULL PRIMARY KEY"
        
        console.log('idIndex', idIndex);

        console.log('newSchemaStatementLines', newSchemaStatementLines.length, newSchemaStatementLines);

        const newSchema = newSchemaStatementLines.join(',')
        .replace(", PRIMARY KEY (id, istart)", "")

        console.log('new Crsql compatible schema:', newSchema);
        
        // Drop table with old schema
        await db.execute(`DROP TABLE IF EXISTS ${tableName}`);

        // Create table with schema 
        await db.execute(newSchema);
        
        try {
          await db.execute(`INSERT OR REPLACE INTO ${tableName} SELECT * FROM ${tableName}_temp`);
        } catch (error) {
          console.error('Failed to insert/update data from temp table:', error);
        }
    } 
    
    // recreate index with no unique constraint
    await db.execute(`CREATE INDEX IF NOT EXISTS ${tableName}Idx ON ${tableName} (id, istart)`);
    
    // Enable CRDT behavior for the table
    await db.execute(`SELECT crsql_as_crr('${tableName}')`);
    await db.execute("SELECT crsql_finalize();")
  } catch (error) {
    console.error('Failed to load crsqlite extension:', error);
  }
}; 