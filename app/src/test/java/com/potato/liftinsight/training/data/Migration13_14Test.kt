package com.potato.liftinsight.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration13_14Test {
    private lateinit var context: Context
    private val dbName = "migration_13_14_test.db"

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
    fun migration13_14_preservesExistingMetahistoryData() {
        createV13DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_13_14, LiftInsightDatabase.MIGRATION_14_15, LiftInsightDatabase.MIGRATION_15_16, LiftInsightDatabase.MIGRATION_16_17, LiftInsightDatabase.MIGRATION_17_18, LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val metaHistoryRows = database.planDao().getMetaHistoryWithMotions()

        assertEquals(2, metaHistoryRows.size)

        val first = metaHistoryRows.first { it.id == 1L.toInt() }
        assertEquals("2024-01-01", first.date)
        assertEquals(5, first.rep)
        assertEquals(8, first.rpe)
        assertEquals(80.0, first.weight, 0.01)
        assertEquals("Snatch", first.motionName)
        assertEquals("video1.mp4", first.videoName)
        assertEquals(null, first.historyId)

        val second = metaHistoryRows.first { it.id == 2L.toInt() }
        assertEquals("2024-01-02", second.date)
        assertEquals(3, second.rep)
        assertEquals(9, second.rpe)
        assertEquals(100.0, second.weight, 0.01)
        assertEquals("Clean", second.motionName)
        assertEquals(null, second.historyId)

        database.close()
    }

    @Test
    fun migration13_14_createsHistoryTableWithForeignKey() {
        createV13DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_13_14, LiftInsightDatabase.MIGRATION_14_15, LiftInsightDatabase.MIGRATION_15_16, LiftInsightDatabase.MIGRATION_16_17, LiftInsightDatabase.MIGRATION_17_18, LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val planId = 1L

        val historyId = database.historyDao().insertHistory(
            HistoryEntity(
                planId = planId.toInt(),
                startTime = 1000L,
                endTime = 2000L,
                intensity = 7
            )
        )

        assertTrue(historyId > 0)

        val historyRow = database.historyDao().getHistoryRowById(historyId.toInt())
        assertNotNull(historyRow)
        assertEquals(planId.toInt(), historyRow!!.planId)
        assertEquals("Test Plan", historyRow.planName)
        assertEquals(1000L, historyRow.startTime)
        assertEquals(2000L, historyRow.endTime)
        assertEquals(7, historyRow.intensity)

        val fkCursor = db.query("PRAGMA foreign_key_list(history)")
        var foundPlanFk = false
        while (fkCursor.moveToNext()) {
            val refTable = fkCursor.getString(fkCursor.getColumnIndexOrThrow("table"))
            if (refTable == "plan") {
                foundPlanFk = true
            }
        }
        fkCursor.close()
        assertTrue("history table should have FK to plan", foundPlanFk)

        database.close()
    }

    @Test
    fun migration13_14_createsMetahistoryForeignKeyToHistory() {
        createV13DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_13_14, LiftInsightDatabase.MIGRATION_14_15, LiftInsightDatabase.MIGRATION_15_16, LiftInsightDatabase.MIGRATION_16_17, LiftInsightDatabase.MIGRATION_17_18, LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val fkCursor = db.query("PRAGMA foreign_key_list(metahistory)")
        var foundHistoryFk = false
        var foundMotionFk = false
        while (fkCursor.moveToNext()) {
            val refTable = fkCursor.getString(fkCursor.getColumnIndexOrThrow("table"))
            val onDelete = fkCursor.getString(fkCursor.getColumnIndexOrThrow("on_delete"))
            if (refTable == "history" && onDelete == "SET NULL") {
                foundHistoryFk = true
            }
            if (refTable == "motion" && onDelete == "RESTRICT") {
                foundMotionFk = true
            }
        }
        fkCursor.close()
        assertTrue("metahistory should have FK to history with ON DELETE SET NULL", foundHistoryFk)
        assertTrue("metahistory should have FK to motion with ON DELETE RESTRICT", foundMotionFk)

        database.close()
    }

    @Test
    fun migration13_14_createsMetahistoryIndexes() {
        createV13DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_13_14, LiftInsightDatabase.MIGRATION_14_15, LiftInsightDatabase.MIGRATION_15_16, LiftInsightDatabase.MIGRATION_16_17, LiftInsightDatabase.MIGRATION_17_18, LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val indexCursor = db.query("PRAGMA index_list(metahistory)")
        val indexNames = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
        }
        indexCursor.close()

        assertTrue(
            "metahistory should have index_metahistory_motion_id",
            indexNames.contains("index_metahistory_motion_id")
        )
        assertTrue(
            "metahistory should have index_metahistory_history_id",
            indexNames.contains("index_metahistory_history_id")
        )

        database.close()
    }

    @Test
    fun migration13_14_addsHistoryIdToMetahistoryBin() {
        createV13DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_13_14, LiftInsightDatabase.MIGRATION_14_15, LiftInsightDatabase.MIGRATION_15_16, LiftInsightDatabase.MIGRATION_16_17, LiftInsightDatabase.MIGRATION_17_18, LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val columnCursor = db.query("PRAGMA table_info(metahistory_bin)")
        val columnNames = mutableListOf<String>()
        while (columnCursor.moveToNext()) {
            columnNames.add(columnCursor.getString(columnCursor.getColumnIndexOrThrow("name")))
        }
        columnCursor.close()

        assertTrue(
            "metahistory_bin should have history_id column",
            columnNames.contains("history_id")
        )

        database.close()
    }

    @Test
    fun migration13_14_allowsNullHistoryIdOnMetahistory() {
        createV13DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_13_14, LiftInsightDatabase.MIGRATION_14_15, LiftInsightDatabase.MIGRATION_15_16, LiftInsightDatabase.MIGRATION_16_17, LiftInsightDatabase.MIGRATION_17_18, LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val planId = database.historyDao().insertHistory(
            HistoryEntity(planId = 1, startTime = 100L, endTime = 200L, intensity = 5)
        )

        database.planDao().insertMetaHistory(
            MetaHistoryEntity(
                date = "2024-06-01",
                rep = 3,
                rpe = 7,
                weight = 90.0,
                motionId = 1,
                historyId = planId.toInt()
            )
        )

        val rows = database.planDao().getMetaHistoryWithMotions()
        val attached = rows.first { it.date == "2024-06-01" }
        assertEquals(planId.toInt(), attached.historyId)

        database.close()
    }

    private fun createV13DatabaseWithData() {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        db.execSQL(
            """
            CREATE TABLE motion (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL
            )
            """
        )
        db.execSQL("CREATE UNIQUE INDEX index_motion_name ON motion (name)")

        db.execSQL(
            """
            CREATE TABLE plan (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                cycle_period INTEGER NOT NULL,
                current_index INTEGER NOT NULL,
                last_applied_at INTEGER NOT NULL
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE plan_selection (
                id INTEGER NOT NULL,
                current_plan_id INTEGER,
                current_day_epoch INTEGER,
                PRIMARY KEY(id),
                FOREIGN KEY(current_plan_id) REFERENCES plan(id) ON DELETE SET NULL
            )
            """
        )
        db.execSQL("CREATE INDEX index_plan_selection_current_plan_id ON plan_selection (current_plan_id)")

        db.execSQL(
            """
            CREATE TABLE workout_session (
                id INTEGER NOT NULL,
                is_workout_going INTEGER NOT NULL,
                is_paused INTEGER NOT NULL,
                started_at INTEGER NOT NULL,
                last_resumed_at INTEGER NOT NULL,
                elapsed_before_pause_ms INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE workout_progress (
                id INTEGER NOT NULL,
                plan_id INTEGER NOT NULL,
                plan_day_index INTEGER NOT NULL,
                next_set_index INTEGER NOT NULL,
                active_set_index INTEGER,
                total_set_count INTEGER NOT NULL,
                break_ends_at INTEGER NOT NULL,
                is_finished INTEGER NOT NULL,
                completed_elapsed_time_ms INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE metaplan (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                plan_id INTEGER NOT NULL,
                motion_id INTEGER NOT NULL,
                day_index INTEGER NOT NULL,
                sets INTEGER NOT NULL,
                reps INTEGER NOT NULL,
                intensity REAL NOT NULL,
                weight REAL NOT NULL,
                order_index INTEGER NOT NULL,
                FOREIGN KEY(plan_id) REFERENCES plan(id) ON DELETE CASCADE,
                FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT
            )
            """
        )
        db.execSQL("CREATE INDEX index_metaplan_plan_id ON metaplan (plan_id)")
        db.execSQL("CREATE INDEX index_metaplan_motion_id ON metaplan (motion_id)")
        db.execSQL("CREATE UNIQUE INDEX index_metaplan_plan_id_day_index_order_index ON metaplan (plan_id, day_index, order_index)")

        db.execSQL(
            """
            CREATE TABLE metahistory (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                rep INTEGER NOT NULL,
                rpe INTEGER NOT NULL,
                weight REAL NOT NULL,
                motion_id INTEGER NOT NULL,
                video_name TEXT,
                video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE',
                imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED',
                imported_reference_label TEXT NOT NULL DEFAULT '',
                imported_reference_pixel_distance REAL,
                imported_reference_distance_meters REAL,
                FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT
            )
            """
        )
        db.execSQL("CREATE INDEX index_metahistory_motion_id ON metahistory (motion_id)")

        db.execSQL(
            """
            CREATE TABLE video_process_state (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                video_name TEXT NOT NULL,
                state TEXT NOT NULL,
                progress INTEGER NOT NULL,
                processed_video_name TEXT
            )
            """
        )
        db.execSQL("CREATE UNIQUE INDEX index_video_process_state_video_name ON video_process_state (video_name)")

        db.execSQL(
            """
            CREATE TABLE metahistory_bin (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                rep INTEGER NOT NULL,
                rpe INTEGER NOT NULL,
                weight REAL NOT NULL,
                motion_id INTEGER NOT NULL,
                motion_name TEXT NOT NULL,
                video_name TEXT,
                video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE',
                imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED',
                imported_reference_label TEXT NOT NULL DEFAULT '',
                imported_reference_pixel_distance REAL,
                imported_reference_distance_meters REAL
            )
            """
        )

        db.execSQL("INSERT INTO motion (name) VALUES ('Snatch')")
        db.execSQL("INSERT INTO motion (name) VALUES ('Clean')")
        db.execSQL("INSERT INTO plan (name, cycle_period, current_index, last_applied_at) VALUES ('Test Plan', 7, 1, 0)")
        db.execSQL("INSERT INTO metahistory (date, rep, rpe, weight, motion_id, video_name) VALUES ('2024-01-01', 5, 8, 80.0, 1, 'video1.mp4')")
        db.execSQL("INSERT INTO metahistory (date, rep, rpe, weight, motion_id) VALUES ('2024-01-02', 3, 9, 100.0, 2)")
        db.execSQL("INSERT INTO metahistory_bin (date, rep, rpe, weight, motion_id, motion_name) VALUES ('2024-01-03', 4, 7, 70.0, 1, 'Snatch')")

        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'dca1e93dff1116e9d51592ad8a12faae')")
        db.execSQL("PRAGMA user_version = 13")

        db.close()
    }
}
