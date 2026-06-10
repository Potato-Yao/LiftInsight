package com.potato.liftinsight.training.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration21_22Test {
    private lateinit var context: Context
    private val dbName = "migration_21_22_test.db"

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
    fun migration21_22_createsMetahistoryPoseFrameTable() {
        createV21Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_21_22)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val columnCursor: Cursor = db.query("PRAGMA table_info(metahistory_pose_frame)")
        val columnNames = mutableListOf<String>()
        while (columnCursor.moveToNext()) {
            columnNames.add(columnCursor.getString(columnCursor.getColumnIndexOrThrow("name")))
        }
        columnCursor.close()

        assertTrue("metahistory_pose_frame should have id column", columnNames.contains("id"))
        assertTrue("metahistory_pose_frame should have metahistory_id column", columnNames.contains("metahistory_id"))
        assertTrue("metahistory_pose_frame should have timestamp_ms column", columnNames.contains("timestamp_ms"))
        assertTrue("metahistory_pose_frame should have landmarks_json column", columnNames.contains("landmarks_json"))

        database.close()
    }

    @Test
    fun migration21_22_createsCompositeIndex() {
        createV21Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_21_22)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val indexCursor: Cursor = db.query("PRAGMA index_list(metahistory_pose_frame)")
        val indexNames = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
        }
        indexCursor.close()

        assertTrue(
            "metahistory_pose_frame should have composite index",
            indexNames.any { it.contains("metahistory_id") && it.contains("timestamp_ms") }
        )

        database.close()
    }

    @Test
    fun migration21_22_foreignKeyToMetahistoryExists() {
        createV21Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_21_22)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val fkCursor: Cursor = db.query("PRAGMA foreign_key_list(metahistory_pose_frame)")
        val fkTableNames = mutableListOf<String>()
        while (fkCursor.moveToNext()) {
            fkTableNames.add(fkCursor.getString(fkCursor.getColumnIndexOrThrow("table")))
        }
        fkCursor.close()

        assertTrue(
            "metahistory_pose_frame should have foreign key to metahistory",
            fkTableNames.any { it == "metahistory" }
        )

        database.close()
    }

    @Test
    fun migration21_22_canInsertAndQueryAfterMigration() {
        createV21Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_21_22)
            .allowMainThreadQueries()
            .build()

        // Insert test data (requires a metahistory parent)
        val db = database.openHelper.writableDatabase

        // Insert a motion and get its id
        val motionValues = android.content.ContentValues().apply {
            put("name", "Squat")
            put("type", "BARBELL")
        }
        val motionId = db.insert("motion", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, motionValues)

        // Insert a metahistory and get its id
        val metaValues = android.content.ContentValues().apply {
            put("date", "2024-01-01")
            put("rep", 5)
            put("rpe", 8)
            put("weight", 100.0)
            put("motion_id", motionId)
        }
        val metaHistoryId = db.insert("metahistory", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, metaValues)

        // Insert pose frames into the migrated table
        val frameValues1 = android.content.ContentValues().apply {
            put("metahistory_id", metaHistoryId)
            put("timestamp_ms", 0)
            put("landmarks_json", "[{\"t\":0,\"x\":0.5,\"y\":0.3,\"v\":0.9}]")
        }
        db.insert("metahistory_pose_frame", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, frameValues1)

        val frameValues2 = android.content.ContentValues().apply {
            put("metahistory_id", metaHistoryId)
            put("timestamp_ms", 100)
            put("landmarks_json", "[{\"t\":0,\"x\":0.6,\"y\":0.4,\"v\":0.8}]")
        }
        db.insert("metahistory_pose_frame", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, frameValues2)

        // Query back
        val queryCursor: Cursor = db.query(
            "SELECT * FROM metahistory_pose_frame WHERE metahistory_id = ? ORDER BY timestamp_ms ASC",
            arrayOf<Any>(metaHistoryId)
        )
        val results = mutableListOf<Pair<Long, String>>()
        while (queryCursor.moveToNext()) {
            val ts = queryCursor.getLong(queryCursor.getColumnIndexOrThrow("timestamp_ms"))
            val json = queryCursor.getString(queryCursor.getColumnIndexOrThrow("landmarks_json"))
            results.add(Pair(ts, json))
        }
        queryCursor.close()

        assertEquals(2, results.size)
        assertEquals(0L, results[0].first)
        assertEquals("[{\"t\":0,\"x\":0.5,\"y\":0.3,\"v\":0.9}]", results[0].second)
        assertEquals(100L, results[1].first)
        assertEquals("[{\"t\":0,\"x\":0.6,\"y\":0.4,\"v\":0.8}]", results[1].second)

        database.close()
    }

    private fun createV21Database() {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        // Create all v19 tables
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

        db.execSQL("CREATE TABLE metahistory (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, rep INTEGER NOT NULL, rpe INTEGER NOT NULL, weight REAL NOT NULL, motion_id INTEGER NOT NULL, video_name TEXT, video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE', imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED', imported_reference_label TEXT NOT NULL DEFAULT '', imported_reference_pixel_distance REAL, imported_reference_distance_meters REAL, history_id INTEGER, FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT, FOREIGN KEY(history_id) REFERENCES history(id) ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_motion_id ON metahistory (motion_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_history_id ON metahistory (history_id)")

        db.execSQL("CREATE TABLE video_process_state (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, video_name TEXT NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL, processed_video_name TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_process_state_video_name ON video_process_state (video_name)")

        db.execSQL("CREATE TABLE metahistory_bin (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, rep INTEGER NOT NULL, rpe INTEGER NOT NULL, weight REAL NOT NULL, motion_id INTEGER NOT NULL, motion_name TEXT NOT NULL, video_name TEXT, video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE', imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED', imported_reference_label TEXT NOT NULL DEFAULT '', imported_reference_pixel_distance REAL, imported_reference_distance_meters REAL, history_id INTEGER)")

        db.execSQL("CREATE TABLE body_metric (id INTEGER NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id))")

        // v20 additions
        db.execSQL("CREATE TABLE IF NOT EXISTS metahistory_timeseries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, metric_name TEXT NOT NULL, value REAL NOT NULL, FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_timeseries_metahistory_id_metric_name_timestamp_ms ON metahistory_timeseries (metahistory_id, metric_name, timestamp_ms)")

        // v21 additions
        db.execSQL("ALTER TABLE metahistory ADD COLUMN pose_detection INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory ADD COLUMN angle_display INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory ADD COLUMN angle_plot INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory ADD COLUMN barbell_detection INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory ADD COLUMN power_calculation INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN pose_detection INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN angle_display INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN angle_plot INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN barbell_detection INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN power_calculation INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS metahistory_timeseries_bin (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, original_metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, metric_name TEXT NOT NULL, value REAL NOT NULL)")

        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'placeholder')")
        db.execSQL("PRAGMA user_version = 21")

        db.close()
    }
}
