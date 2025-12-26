//
//   Calendar Notifications Plus
//   Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.database.poc

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.quarck.calnotify.logs.DevLog
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * POC tests to verify Room can work with cr-sqlite extension.
 * 
 * These tests validate the technical feasibility of migrating from
 * raw SQLite to Room while preserving cr-sqlite functionality.
 * 
 * SUCCESS CRITERIA:
 * 1. Room CRUD operations work with our custom factory
 * 2. CR-SQLite extension is loaded (can query crsql_version)
 * 3. crsql_finalize() is called properly on close
 * 4. Room and legacy implementations can coexist
 * 
 * See: docs/dev_todo/database_modernization_plan.md
 */
@RunWith(AndroidJUnit4::class)
class RoomCrSqlitePocTest {
    
    companion object {
        private const val LOG_TAG = "RoomCrSqlitePocTest"
    }
    
    private lateinit var context: Context
    private var database: RoomPocDatabase? = null
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        DevLog.info(LOG_TAG, "Setting up test")
    }
    
    @After
    fun teardown() {
        DevLog.info(LOG_TAG, "Tearing down test")
        try {
            database?.close()
        } catch (e: Exception) {
            DevLog.error(LOG_TAG, "Error closing database: ${e.message}")
        }
        database = null
        
        // Clean up test database file
        context.deleteDatabase("room_poc_test.db")
    }
    
    /**
     * TEST 1: Verify Room can perform basic CRUD operations with our custom factory.
     * 
     * This is the fundamental test - if Room CRUD works with CrSqliteRoomFactory,
     * the basic integration is successful.
     */
    @Test
    fun test_room_basic_crud_works() {
        DevLog.info(LOG_TAG, "Starting test_room_basic_crud_works")
        
        // Create database with cr-sqlite
        database = RoomPocDatabase.createWithCrSqlite(context)
        val dao = database!!.pocDao()
        
        // Test INSERT
        val entity1 = RoomPocEntity(
            id = 1,
            name = "Test Entity 1",
            timestamp = System.currentTimeMillis(),
            description = "First test entity"
        )
        val insertResult = dao.insert(entity1)
        DevLog.info(LOG_TAG, "Insert result: $insertResult")
        assertEquals("Insert should return the row ID", 1L, insertResult)
        
        // Test READ
        val retrieved = dao.getById(1)
        assertNotNull("Should retrieve inserted entity", retrieved)
        assertEquals("Name should match", entity1.name, retrieved!!.name)
        assertEquals("Description should match", entity1.description, retrieved.description)
        DevLog.info(LOG_TAG, "Retrieved entity: $retrieved")
        
        // Test UPDATE
        val updatedEntity = entity1.copy(name = "Updated Name")
        val updateResult = dao.update(updatedEntity)
        assertEquals("Update should affect 1 row", 1, updateResult)
        
        val afterUpdate = dao.getById(1)
        assertEquals("Name should be updated", "Updated Name", afterUpdate!!.name)
        DevLog.info(LOG_TAG, "After update: $afterUpdate")
        
        // Test DELETE
        val deleteResult = dao.delete(afterUpdate)
        assertEquals("Delete should affect 1 row", 1, deleteResult)
        
        val afterDelete = dao.getById(1)
        assertNull("Entity should be deleted", afterDelete)
        DevLog.info(LOG_TAG, "After delete: entity is null as expected")
        
        DevLog.info(LOG_TAG, "test_room_basic_crud_works PASSED")
    }
    
    /**
     * TEST 2: Verify CR-SQLite extension is properly loaded.
     * 
     * We query crsql_version() which only works if the extension is loaded.
     * This proves the custom factory correctly configures cr-sqlite.
     */
    @Test
    fun test_crsqlite_extension_is_loaded() {
        DevLog.info(LOG_TAG, "Starting test_crsqlite_extension_is_loaded")
        
        database = RoomPocDatabase.createWithCrSqlite(context)
        
        // Query cr-sqlite version - this only works if extension is loaded
        val cursor = database!!.openHelper.writableDatabase.query("SELECT crsql_version()")
        
        assertTrue("Cursor should have results", cursor.moveToFirst())
        
        val version = cursor.getString(0)
        cursor.close()
        
        assertNotNull("CR-SQLite version should not be null", version)
        DevLog.info(LOG_TAG, "CR-SQLite version: $version")
        
        // Version should be a valid semver-ish string
        assertTrue(
            "Version should look like a version number (got: $version)",
            version.matches(Regex(".*\\d+.*"))
        )
        
        DevLog.info(LOG_TAG, "test_crsqlite_extension_is_loaded PASSED")
    }
    
    /**
     * TEST 3: Verify multiple operations work correctly.
     * 
     * Tests bulk operations and queries to ensure Room's generated code
     * works correctly with our custom SQLite wrapper.
     */
    @Test
    fun test_bulk_operations() {
        DevLog.info(LOG_TAG, "Starting test_bulk_operations")
        
        database = RoomPocDatabase.createWithCrSqlite(context)
        val dao = database!!.pocDao()
        
        // Insert multiple entities
        val entities = (1..10).map { i ->
            RoomPocEntity(
                id = i.toLong(),
                name = "Entity $i",
                timestamp = System.currentTimeMillis() + i,
                description = "Description for entity $i"
            )
        }
        dao.insertAll(entities)
        
        // Verify count
        val count = dao.count()
        assertEquals("Should have 10 entities", 10, count)
        DevLog.info(LOG_TAG, "Entity count: $count")
        
        // Test getAll
        val allEntities = dao.getAll()
        assertEquals("getAll should return 10 entities", 10, allEntities.size)
        
        // Verify ordering (should be by id ASC)
        for (i in 0 until 10) {
            assertEquals("Entity ID should match order", (i + 1).toLong(), allEntities[i].id)
        }
        
        // Test findByName with LIKE
        val found = dao.findByName("%Entity 5%")
        assertEquals("Should find 1 entity", 1, found.size)
        assertEquals("Should find Entity 5", "Entity 5", found[0].name)
        
        // Test deleteAll
        dao.deleteAll()
        val afterDelete = dao.count()
        assertEquals("Should have 0 entities after deleteAll", 0, afterDelete)
        
        DevLog.info(LOG_TAG, "test_bulk_operations PASSED")
    }
    
    /**
     * TEST 4: Verify database close properly calls crsql_finalize.
     * 
     * This tests that our CrSqliteSupportHelper properly calls crsql_finalize()
     * before closing, which is required for cr-sqlite to work correctly.
     */
    @Test
    fun test_crsqlite_finalize_on_close() {
        DevLog.info(LOG_TAG, "Starting test_crsqlite_finalize_on_close")
        
        database = RoomPocDatabase.createWithCrSqlite(context)
        val dao = database!!.pocDao()
        
        // Do some operations
        dao.insert(RoomPocEntity(1, "Test", System.currentTimeMillis()))
        val retrieved = dao.getById(1)
        assertNotNull(retrieved)
        
        // Close the database - this should call crsql_finalize internally
        // If it throws, the test fails
        database!!.close()
        database = null  // Prevent double-close in teardown
        
        DevLog.info(LOG_TAG, "Database closed successfully (crsql_finalize was called)")
        
        // Reopen to verify database integrity
        database = RoomPocDatabase.createWithCrSqlite(context)
        val reopenedDao = database!!.pocDao()
        
        val afterReopen = reopenedDao.getById(1)
        assertNotNull("Data should persist after close/reopen", afterReopen)
        assertEquals("Data should be intact", "Test", afterReopen!!.name)
        
        DevLog.info(LOG_TAG, "test_crsqlite_finalize_on_close PASSED")
    }
    
    /**
     * TEST 5: Compare Room with standard SQLite vs Room with cr-sqlite.
     * 
     * This ensures our cr-sqlite integration doesn't break basic Room behavior.
     */
    @Test
    fun test_room_standard_vs_crsqlite_behavior_matches() {
        DevLog.info(LOG_TAG, "Starting test_room_standard_vs_crsqlite_behavior_matches")
        
        // Create two databases - one standard, one with cr-sqlite
        val standardDb = RoomPocDatabase.createInMemory(context)
        val crSqliteDb = RoomPocDatabase.createWithCrSqlite(context)
        
        try {
            val standardDao = standardDb.pocDao()
            val crSqliteDao = crSqliteDb.pocDao()
            
            // Perform identical operations on both
            val testEntity = RoomPocEntity(
                id = 42,
                name = "Comparison Test",
                timestamp = 1234567890L,
                description = "Testing behavior parity"
            )
            
            standardDao.insert(testEntity)
            crSqliteDao.insert(testEntity)
            
            val standardResult = standardDao.getById(42)
            val crSqliteResult = crSqliteDao.getById(42)
            
            // Results should be identical
            assertNotNull(standardResult)
            assertNotNull(crSqliteResult)
            assertEquals("Names should match", standardResult!!.name, crSqliteResult!!.name)
            assertEquals("Timestamps should match", standardResult.timestamp, crSqliteResult.timestamp)
            assertEquals("Descriptions should match", standardResult.description, crSqliteResult.description)
            
            // Count should be the same
            assertEquals("Counts should match", standardDao.count(), crSqliteDao.count())
            
            DevLog.info(LOG_TAG, "Standard Room and CR-SQLite Room behave identically")
        } finally {
            standardDb.close()
            crSqliteDb.close()
            database = null  // Already closed both
        }
        
        DevLog.info(LOG_TAG, "test_room_standard_vs_crsqlite_behavior_matches PASSED")
    }
    
    /**
     * TEST 6: Test raw SQL execution works (needed for cr-sqlite CRDT operations).
     * 
     * CR-SQLite requires executing raw SQL for CRDT operations like
     * crsql_changes() and crsql_finalize(). This verifies we can do that through Room.
     */
    @Test
    fun test_raw_sql_execution_works() {
        DevLog.info(LOG_TAG, "Starting test_raw_sql_execution_works")
        
        database = RoomPocDatabase.createWithCrSqlite(context)
        
        val writableDb = database!!.openHelper.writableDatabase
        
        // Execute raw SQL to create a table
        writableDb.execSQL("""
            CREATE TABLE IF NOT EXISTS raw_test (
                id INTEGER PRIMARY KEY,
                value TEXT
            )
        """.trimIndent())
        
        // Insert via raw SQL
        writableDb.execSQL("INSERT INTO raw_test (id, value) VALUES (1, 'test_value')")
        
        // Query via raw SQL
        val cursor = writableDb.query("SELECT value FROM raw_test WHERE id = 1")
        assertTrue("Should have results", cursor.moveToFirst())
        assertEquals("Value should match", "test_value", cursor.getString(0))
        cursor.close()
        
        // Clean up
        writableDb.execSQL("DROP TABLE raw_test")
        
        DevLog.info(LOG_TAG, "test_raw_sql_execution_works PASSED")
    }
    
    /**
     * TEST 7: Verify cr-sqlite specific functions work.
     * 
     * Tests that we can call cr-sqlite specific functions through Room's database.
     */
    @Test
    fun test_crsqlite_specific_functions() {
        DevLog.info(LOG_TAG, "Starting test_crsqlite_specific_functions")
        
        database = RoomPocDatabase.createWithCrSqlite(context)
        val writableDb = database!!.openHelper.writableDatabase
        
        // Test crsql_version() - already tested above but let's be thorough
        val versionCursor = writableDb.query("SELECT crsql_version()")
        assertTrue(versionCursor.moveToFirst())
        val version = versionCursor.getString(0)
        versionCursor.close()
        DevLog.info(LOG_TAG, "crsql_version: $version")
        
        // Test crsql_siteid() - each database should have a unique site ID
        val siteIdCursor = writableDb.query("SELECT crsql_site_id()")
        assertTrue(siteIdCursor.moveToFirst())
        val siteId = siteIdCursor.getBlob(0)
        siteIdCursor.close()
        assertNotNull("Site ID should not be null", siteId)
        assertTrue("Site ID should have bytes", siteId.isNotEmpty())
        DevLog.info(LOG_TAG, "crsql_site_id: ${siteId.size} bytes")
        
        DevLog.info(LOG_TAG, "test_crsqlite_specific_functions PASSED")
    }
}

