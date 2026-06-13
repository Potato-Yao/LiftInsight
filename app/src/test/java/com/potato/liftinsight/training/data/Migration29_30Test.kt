package com.potato.liftinsight.training.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration29_30Test {
    private lateinit var context: Context
    private val dbName = "migration_29_30_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration29_30_addsX2AndY2Columns() {
        createV29Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val columnCursor: Cursor = db.query("PRAGMA table_info(metahistory_barbell_frame)")
        val columnNames = mutableListOf<String>()
        while (columnCursor.moveToNext()) {
            columnNames.add(columnCursor.getString(columnCursor.getColumnIndexOrThrow("name")))
        }
        columnCursor.close()

        assertTrue("metahistory_barbell_frame should have x2 column", columnNames.contains("x2"))
        assertTrue("metahistory_barbell_frame should have y2 column", columnNames.contains("y2"))
        assertTrue("metahistory_barbell_frame should still have x column", columnNames.contains("x"))
        assertTrue("metahistory_barbell_frame should still have y column", columnNames.contains("y"))
        assertTrue("metahistory_barbell_frame should still have radius column", columnNames.contains("radius"))

        database.close()
    }

    @Test
    fun migration29_30_preservesExistingData() {
        createV29Database()

        // Insert test data before migration
        val v29Db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        // Need parent tables for foreign key
        v29Db.execSQL("INSERT INTO motion (name, type) VALUES ('Bench Press', 'BARBELL')")
        val motionId = 1L
        v29Db.execSQL("INSERT INTO metahistory (id, date, rep, rpe, weight, motion_id) VALUES (1, '2024-06-01', 5, 8, 80.0, $motionId)")

        val values = android.content.ContentValues().apply {
            put("metahistory_id", 1)
            put("timestamp_ms", 1000L)
            put("x", 0.5f)
            put("y", 0.3f)
            put("radius", 0.1f)
            put("confidence", 0.9f)
            put("is_manually_edited", 0)
        }
        v29Db.insert("metahistory_barbell_frame", null, values)
        v29Db.close()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val queryCursor: Cursor = db.query(
            "SELECT * FROM metahistory_barbell_frame WHERE metahistory_id = 1"
        )
        assertTrue(queryCursor.moveToFirst())

        val x = queryCursor.getFloat(queryCursor.getColumnIndexOrThrow("x"))
        val y = queryCursor.getFloat(queryCursor.getColumnIndexOrThrow("y"))
        val radius = queryCursor.getFloat(queryCursor.getColumnIndexOrThrow("radius"))
        val confidence = queryCursor.getFloat(queryCursor.getColumnIndexOrThrow("confidence"))
        val timestampMs = queryCursor.getLong(queryCursor.getColumnIndexOrThrow("timestamp_ms"))

        assertEquals(0.5f, x)
        assertEquals(0.3f, y)
        assertEquals(0.1f, radius)
        assertEquals(0.9f, confidence)
        assertEquals(1000L, timestampMs)

        // x2 and y2 should exist and be null for pre-migration data
        val x2Index = queryCursor.getColumnIndex("x2")
        val y2Index = queryCursor.getColumnIndex("y2")
        if (x2Index >= 0) {
            assertTrue("x2 should be null for pre-migration row", queryCursor.isNull(x2Index))
        }
        if (y2Index >= 0) {
            assertTrue("y2 should be null for pre-migration row", queryCursor.isNull(y2Index))
        }

        queryCursor.close()
        database.close()
    }

    @Test
    fun migration29_30_canInsertAndQueryWithX2Y2() {
        createV29Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.writableDatabase

        // Insert parent records
        db.execSQL("INSERT INTO motion (name, type) VALUES ('Squat', 'BARBELL')")
        db.execSQL("INSERT INTO metahistory (id, date, rep, rpe, weight, motion_id) VALUES (1, '2024-06-01', 5, 8, 100.0, 1)")

        // Insert barbell frame with line endpoints (x2, y2 non-null)
        val values1 = android.content.ContentValues().apply {
            put("metahistory_id", 1)
            put("timestamp_ms", 0L)
            put("x", 0.5f)
            put("y", 0.4f)
            put("radius", 0.15f)
            put("confidence", 0.9f)
            put("x2", 0.7f)
            put("y2", 0.42f)
        }
        db.insert("metahistory_barbell_frame", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, values1)

        // Insert barbell frame without line endpoints (circle detection — x2, y2 null)
        val values2 = android.content.ContentValues().apply {
            put("metahistory_id", 1)
            put("timestamp_ms", 100L)
            put("x", 0.48f)
            put("y", 0.42f)
            put("radius", 0.12f)
            put("confidence", 0.8f)
        }
        db.insert("metahistory_barbell_frame", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, values2)

        // Query back
        val queryCursor: Cursor = db.query(
            "SELECT * FROM metahistory_barbell_frame WHERE metahistory_id = 1 ORDER BY timestamp_ms ASC"
        )
        val results = mutableListOf<Triple<Long, Float?, Float?>>()
        while (queryCursor.moveToNext()) {
            val ts = queryCursor.getLong(queryCursor.getColumnIndexOrThrow("timestamp_ms"))
            val x2Idx = queryCursor.getColumnIndexOrThrow("x2")
            val y2Idx = queryCursor.getColumnIndexOrThrow("y2")
            val x2 = if (queryCursor.isNull(x2Idx)) null else queryCursor.getFloat(x2Idx)
            val y2 = if (queryCursor.isNull(y2Idx)) null else queryCursor.getFloat(y2Idx)
            results.add(Triple(ts, x2, y2))
        }
        queryCursor.close()

        assertEquals(2, results.size)

        // First row: line detection
        assertEquals(0L, results[0].first)
        assertEquals(0.7f, results[0].second)
        assertEquals(0.42f, results[0].third)

        // Second row: circle detection (null x2/y2)
        assertEquals(100L, results[1].first)
        assertNull(results[1].second)
        assertNull(results[1].third)

        database.close()
    }

    @Test
    fun migration29_30_foreignKeysStillWork() {
        createV29Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val fkCursor: Cursor = db.query("PRAGMA foreign_key_list(metahistory_barbell_frame)")
        val fkTableNames = mutableListOf<String>()
        while (fkCursor.moveToNext()) {
            fkTableNames.add(fkCursor.getString(fkCursor.getColumnIndexOrThrow("table")))
        }
        fkCursor.close()

        assertTrue(
            "metahistory_barbell_frame should have foreign key to metahistory",
            fkTableNames.any { it == "metahistory" }
        )

        database.close()
    }

    @Test
    fun migration29_30_indexStillExists() {
        createV29Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val indexCursor: Cursor = db.query("PRAGMA index_list(metahistory_barbell_frame)")
        val indexNames = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
        }
        indexCursor.close()

        assertTrue(
            "metahistory_barbell_frame should have composite index",
            indexNames.any { it.contains("metahistory_id") && it.contains("timestamp_ms") }
        )

        database.close()
    }

    private fun createV29Database() {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        // Create the tables needed up to v29
        db.execSQL("CREATE TABLE motion (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'BARBELL')")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_motion_name ON motion (name)")

        db.execSQL("CREATE TABLE plan (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, cycle_period INTEGER NOT NULL, current_index INTEGER NOT NULL, last_applied_at INTEGER NOT NULL)")

        db.execSQL("CREATE TABLE plan_selection (id INTEGER NOT NULL, current_plan_id INTEGER, current_day_epoch INTEGER, PRIMARY KEY(id), FOREIGN KEY(current_plan_id) REFERENCES plan(id) ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_selection_current_plan_id ON plan_selection (current_plan_id)")

        db.execSQL("CREATE TABLE workout_session (id INTEGER NOT NULL, is_workout_going INTEGER NOT NULL, is_paused INTEGER NOT NULL, started_at INTEGER NOT NULL, last_resumed_at INTEGER NOT NULL, elapsed_before_pause_ms INTEGER NOT NULL, PRIMARY KEY(id))")

        db.execSQL("CREATE TABLE workout_progress (id INTEGER NOT NULL, plan_id INTEGER NOT NULL, plan_day_index INTEGER NOT NULL, next_set_index INTEGER NOT NULL, active_set_index INTEGER, total_set_count INTEGER NOT NULL, break_ends_at INTEGER NOT NULL, is_finished INTEGER NOT NULL, completed_elapsed_time_ms INTEGER NOT NULL, active_history_id INTEGER, workout_intensity INTEGER, PRIMARY KEY(id))")

        db.execSQL("CREATE TABLE metaplan (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, plan_id INTEGER NOT NULL, motion_id INTEGER NOT NULL, day_index INTEGER NOT NULL, sets INTEGER NOT NULL, reps INTEGER NOT NULL, intensity REAL NOT NULL, weight REAL NOT NULL, order_index INTEGER NOT NULL, FOREIGN KEY(plan_id) REFERENCES plan(id) ON DELETE CASCADE, FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metaplan_plan_id ON metaplan (plan_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metaplan_motion_id ON metaplan (motion_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_metaplan_plan_id_day_index_order_index ON metaplan (plan_id, day_index, order_index)")

        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, plan_id INTEGER NOT NULL, start_time INTEGER NOT NULL, end_time INTEGER NOT NULL, intensity INTEGER NOT NULL DEFAULT 0, day_index INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(plan_id) REFERENCES plan(id) ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_plan_id ON history (plan_id)")

        db.execSQL("CREATE TABLE metahistory (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, rep INTEGER NOT NULL, rpe INTEGER NOT NULL, weight REAL NOT NULL, motion_id INTEGER NOT NULL, video_name TEXT, video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE', imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED', imported_reference_label TEXT NOT NULL DEFAULT '', imported_reference_pixel_distance REAL, imported_reference_distance_meters REAL, pose_detection INTEGER NOT NULL DEFAULT 0, angle_display INTEGER NOT NULL DEFAULT 0, angle_plot INTEGER NOT NULL DEFAULT 0, barbell_detection INTEGER NOT NULL DEFAULT 0, power_calculation INTEGER NOT NULL DEFAULT 0, marked INTEGER NOT NULL DEFAULT 0, rdp_epsilon REAL NOT NULL DEFAULT 1.5, rdp_smooth_skeleton INTEGER NOT NULL DEFAULT 0, history_id INTEGER, FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT, FOREIGN KEY(history_id) REFERENCES history(id) ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_motion_id ON metahistory (motion_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_history_id ON metahistory (history_id)")

        db.execSQL("CREATE TABLE video_process_state (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, video_name TEXT NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL, processed_video_name TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_process_state_video_name ON video_process_state (video_name)")

        db.execSQL("CREATE TABLE metahistory_bin (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, rep INTEGER NOT NULL, rpe INTEGER NOT NULL, weight REAL NOT NULL, motion_id INTEGER NOT NULL, motion_name TEXT NOT NULL, video_name TEXT, video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE', imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED', imported_reference_label TEXT NOT NULL DEFAULT '', imported_reference_pixel_distance REAL, imported_reference_distance_meters REAL, pose_detection INTEGER NOT NULL DEFAULT 0, angle_display INTEGER NOT NULL DEFAULT 0, angle_plot INTEGER NOT NULL DEFAULT 0, barbell_detection INTEGER NOT NULL DEFAULT 0, power_calculation INTEGER NOT NULL DEFAULT 0, marked INTEGER NOT NULL DEFAULT 0, rdp_epsilon REAL NOT NULL DEFAULT 1.5, rdp_smooth_skeleton INTEGER NOT NULL DEFAULT 0, history_id INTEGER)")

        db.execSQL("CREATE TABLE body_metric (id INTEGER NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id))")

        db.execSQL("CREATE TABLE metahistory_timeseries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, metric_name TEXT NOT NULL, value REAL NOT NULL, FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_timeseries_metahistory_id_metric_name_timestamp_ms ON metahistory_timeseries (metahistory_id, metric_name, timestamp_ms)")

        db.execSQL("CREATE TABLE metahistory_timeseries_bin (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, original_metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, metric_name TEXT NOT NULL, value REAL NOT NULL)")

        db.execSQL("CREATE TABLE metahistory_pose_frame (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, landmarks_json TEXT NOT NULL, FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_pose_frame_metahistory_id_timestamp_ms ON metahistory_pose_frame (metahistory_id, timestamp_ms)")

        db.execSQL("CREATE TABLE video_export_state (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, video_name TEXT NOT NULL, rendered_items TEXT NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL, exported_file_name TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_export_state_video_name ON video_export_state (video_name)")

        // v29: metahistory_barbell_frame (without x2, y2)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS metahistory_barbell_frame (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                metahistory_id INTEGER NOT NULL,
                timestamp_ms INTEGER NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                radius REAL NOT NULL,
                confidence REAL NOT NULL,
                is_manually_edited INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_barbell_frame_metahistory_id_timestamp_ms ON metahistory_barbell_frame (metahistory_id, timestamp_ms)")

        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'placeholder')")
        db.execSQL("PRAGMA user_version = 29")

        db.close()
    }
}
