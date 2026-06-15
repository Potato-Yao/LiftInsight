package com.potato.liftinsight.training.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration31_32Test {
    private lateinit var context: Context
    private val dbName = "migration_31_32_test.db"

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
    fun migration31_32_addsActiveRangeColumns() {
        createV31Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_31_32)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        // Check metahistory has active_range columns
        val mhCursor: Cursor = db.query("PRAGMA table_info(metahistory)")
        val mhColumnNames = mutableListOf<String>()
        while (mhCursor.moveToNext()) {
            mhColumnNames.add(mhCursor.getString(mhCursor.getColumnIndexOrThrow("name")))
        }
        mhCursor.close()

        assertTrue("metahistory should have active_range_start_ms column", mhColumnNames.contains("active_range_start_ms"))
        assertTrue("metahistory should have active_range_end_ms column", mhColumnNames.contains("active_range_end_ms"))

        // Check metahistory_bin has active_range columns
        val mhbCursor: Cursor = db.query("PRAGMA table_info(metahistory_bin)")
        val mhbColumnNames = mutableListOf<String>()
        while (mhbCursor.moveToNext()) {
            mhbColumnNames.add(mhbCursor.getString(mhbCursor.getColumnIndexOrThrow("name")))
        }
        mhbCursor.close()

        assertTrue("metahistory_bin should have active_range_start_ms column", mhbColumnNames.contains("active_range_start_ms"))
        assertTrue("metahistory_bin should have active_range_end_ms column", mhbColumnNames.contains("active_range_end_ms"))

        database.close()
    }

    @Test
    fun migration31_32_preservesExistingData() {
        createV31Database()

        val v31Db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        v31Db.execSQL("INSERT INTO motion (name, type) VALUES ('Snatch', 'BARBELL')")
        v31Db.execSQL(
            """
            INSERT INTO metahistory (id, date, rep, rpe, weight, motion_id, marked, video_edited)
            VALUES (1, '2024-06-01', 5, 8, 80.0, 1, 0, 0)
            """
        )
        v31Db.execSQL(
            """
            INSERT INTO metahistory_bin (id, date, rep, rpe, weight, motion_id, motion_name, marked, video_edited)
            VALUES (1, '2024-05-15', 3, 7, 60.0, 1, 'Snatch', 0, 0)
            """
        )
        v31Db.close()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_31_32)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        // Verify metahistory data preserved and new columns default to null
        val mhCursor: Cursor = db.query("SELECT * FROM metahistory WHERE id = 1")
        assertTrue(mhCursor.moveToFirst())
        val startIndex = mhCursor.getColumnIndexOrThrow("active_range_start_ms")
        val endIndex = mhCursor.getColumnIndexOrThrow("active_range_end_ms")
        assertTrue("active_range_start_ms should be null after migration", mhCursor.isNull(startIndex))
        assertTrue("active_range_end_ms should be null after migration", mhCursor.isNull(endIndex))
        assertEquals("2024-06-01", mhCursor.getString(mhCursor.getColumnIndexOrThrow("date")))
        mhCursor.close()

        // Verify metahistory_bin data preserved
        val mhbCursor: Cursor = db.query("SELECT * FROM metahistory_bin WHERE id = 1")
        assertTrue(mhbCursor.moveToFirst())
        val binStartIndex = mhbCursor.getColumnIndexOrThrow("active_range_start_ms")
        val binEndIndex = mhbCursor.getColumnIndexOrThrow("active_range_end_ms")
        assertTrue("active_range_start_ms should be null in bin after migration", mhbCursor.isNull(binStartIndex))
        assertTrue("active_range_end_ms should be null in bin after migration", mhbCursor.isNull(binEndIndex))
        assertEquals("2024-05-15", mhbCursor.getString(mhbCursor.getColumnIndexOrThrow("date")))
        mhbCursor.close()

        database.close()
    }

    @Test
    fun migration31_32_canSetAndReadActiveRange() {
        createV31Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_31_32)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.writableDatabase

        db.execSQL("INSERT INTO motion (name, type) VALUES ('Squat', 'BARBELL')")
        db.execSQL(
            """
            INSERT INTO metahistory (id, date, rep, rpe, weight, motion_id)
            VALUES (1, '2024-06-01', 5, 8, 100.0, 1)
            """
        )

        // Set active range values
        db.execSQL("UPDATE metahistory SET active_range_start_ms = 1500, active_range_end_ms = 7500 WHERE id = 1")

        val cursor: Cursor = db.query("SELECT active_range_start_ms, active_range_end_ms FROM metahistory WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1500L, cursor.getLong(0))
        assertEquals(7500L, cursor.getLong(1))
        cursor.close()

        database.close()
    }

    @Test
    fun migration31_32_canSetActiveRangeToNull() {
        createV31Database()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_31_32)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.writableDatabase

        db.execSQL("INSERT INTO motion (name, type) VALUES ('Deadlift', 'BARBELL')")
        db.execSQL(
            """
            INSERT INTO metahistory (id, date, rep, rpe, weight, motion_id, active_range_start_ms, active_range_end_ms)
            VALUES (1, '2024-06-01', 5, 8, 150.0, 1, 1000, 5000)
            """
        )

        // Set back to null
        db.execSQL("UPDATE metahistory SET active_range_start_ms = NULL, active_range_end_ms = NULL WHERE id = 1")

        val cursor: Cursor = db.query("SELECT active_range_start_ms, active_range_end_ms FROM metahistory WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertTrue("start should be null", cursor.isNull(0))
        assertTrue("end should be null", cursor.isNull(1))
        cursor.close()

        database.close()
    }

    private fun createV31Database() {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

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

        // V31 metahistory includes video_edited but NOT active_range columns
        db.execSQL("CREATE TABLE metahistory (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, rep INTEGER NOT NULL, rpe INTEGER NOT NULL, weight REAL NOT NULL, motion_id INTEGER NOT NULL, video_name TEXT, video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE', imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED', imported_reference_label TEXT NOT NULL DEFAULT '', imported_reference_pixel_distance REAL, imported_reference_distance_meters REAL, pose_detection INTEGER NOT NULL DEFAULT 0, angle_display INTEGER NOT NULL DEFAULT 0, angle_plot INTEGER NOT NULL DEFAULT 0, barbell_detection INTEGER NOT NULL DEFAULT 0, power_calculation INTEGER NOT NULL DEFAULT 0, marked INTEGER NOT NULL DEFAULT 0, rdp_epsilon REAL NOT NULL DEFAULT 1.5, rdp_smooth_skeleton INTEGER NOT NULL DEFAULT 0, video_edited INTEGER NOT NULL DEFAULT 0, history_id INTEGER, FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT, FOREIGN KEY(history_id) REFERENCES history(id) ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_motion_id ON metahistory (motion_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_history_id ON metahistory (history_id)")

        db.execSQL("CREATE TABLE video_process_state (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, video_name TEXT NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL, processed_video_name TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_process_state_video_name ON video_process_state (video_name)")

        // V31 metahistory_bin includes video_edited but NOT active_range columns
        db.execSQL("CREATE TABLE metahistory_bin (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, rep INTEGER NOT NULL, rpe INTEGER NOT NULL, weight REAL NOT NULL, motion_id INTEGER NOT NULL, motion_name TEXT NOT NULL, video_name TEXT, video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE', imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED', imported_reference_label TEXT NOT NULL DEFAULT '', imported_reference_pixel_distance REAL, imported_reference_distance_meters REAL, pose_detection INTEGER NOT NULL DEFAULT 0, angle_display INTEGER NOT NULL DEFAULT 0, angle_plot INTEGER NOT NULL DEFAULT 0, barbell_detection INTEGER NOT NULL DEFAULT 0, power_calculation INTEGER NOT NULL DEFAULT 0, marked INTEGER NOT NULL DEFAULT 0, rdp_epsilon REAL NOT NULL DEFAULT 1.5, rdp_smooth_skeleton INTEGER NOT NULL DEFAULT 0, video_edited INTEGER NOT NULL DEFAULT 0, history_id INTEGER)")

        db.execSQL("CREATE TABLE body_metric (id INTEGER NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id))")

        db.execSQL("CREATE TABLE metahistory_timeseries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, metric_name TEXT NOT NULL, value REAL NOT NULL, FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_timeseries_metahistory_id_metric_name_timestamp_ms ON metahistory_timeseries (metahistory_id, metric_name, timestamp_ms)")

        db.execSQL("CREATE TABLE metahistory_timeseries_bin (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, original_metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, metric_name TEXT NOT NULL, value REAL NOT NULL)")

        db.execSQL("CREATE TABLE metahistory_pose_frame (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, landmarks_json TEXT NOT NULL, FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_pose_frame_metahistory_id_timestamp_ms ON metahistory_pose_frame (metahistory_id, timestamp_ms)")

        db.execSQL("CREATE TABLE metahistory_barbell_frame (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, metahistory_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, radius REAL NOT NULL, confidence REAL NOT NULL, x2 REAL, y2 REAL, is_manually_edited INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_barbell_frame_metahistory_id_timestamp_ms ON metahistory_barbell_frame (metahistory_id, timestamp_ms)")

        db.execSQL("CREATE TABLE video_export_state (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, video_name TEXT NOT NULL, rendered_items TEXT NOT NULL, state TEXT NOT NULL, progress INTEGER NOT NULL, exported_file_name TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_export_state_video_name ON video_export_state (video_name)")

        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'placeholder')")
        db.execSQL("PRAGMA user_version = 31")

        db.close()
    }
}
