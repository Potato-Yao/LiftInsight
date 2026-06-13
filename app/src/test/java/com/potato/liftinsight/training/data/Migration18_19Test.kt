package com.potato.liftinsight.training.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
class Migration18_19Test {
    private lateinit var context: Context
    private val dbName = "migration_18_19_test.db"

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
    fun migration18_19_createsBodyMetricTable() {
        createV18DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val db = database.openHelper.readableDatabase

        val columnCursor: Cursor = db.query("PRAGMA table_info(body_metric)")
        val columnNames = mutableListOf<String>()
        while (columnCursor.moveToNext()) {
            columnNames.add(columnCursor.getString(columnCursor.getColumnIndexOrThrow("name")))
        }
        columnCursor.close()

        assertTrue(
            "body_metric table should have id column",
            columnNames.contains("id")
        )
        assertTrue(
            "body_metric table should have value column",
            columnNames.contains("value")
        )
        assertTrue(
            "body_metric table should have updated_at column",
            columnNames.contains("updated_at")
        )

        database.close()
    }

    @Test
    fun migration18_19_existingMotionsArePreserved() {
        createV18DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val motions = database.motionDao().getMotionEntities()

        assertEquals(2, motions.size)
        val names = motions.map { it.name }.toSet()
        assertTrue(names.contains("Squat"))
        assertTrue(names.contains("Bench Press"))

        database.close()
    }

    @Test
    fun migration18_19_bodyMetricDaoCanInsertAndRead() {
        createV18DatabaseWithData()

        val database = Room.databaseBuilder(context, LiftInsightDatabase::class.java, dbName)
            .addMigrations(LiftInsightDatabase.MIGRATION_18_19, LiftInsightDatabase.MIGRATION_19_20, LiftInsightDatabase.MIGRATION_20_21, LiftInsightDatabase.MIGRATION_21_22, LiftInsightDatabase.MIGRATION_22_23, LiftInsightDatabase.MIGRATION_23_24, LiftInsightDatabase.MIGRATION_24_25, LiftInsightDatabase.MIGRATION_25_26, LiftInsightDatabase.MIGRATION_26_27, LiftInsightDatabase.MIGRATION_27_28, LiftInsightDatabase.MIGRATION_28_29, LiftInsightDatabase.MIGRATION_29_30)
            .allowMainThreadQueries()
            .build()

        val dao = database.bodyMetricDao()

        dao.upsertAll(
            listOf(
                BodyMetricEntity(id = 1, value = "80", updatedAt = 1000L),
                BodyMetricEntity(id = 2, value = "180", updatedAt = 2000L)
            )
        )

        val results = dao.getAll()

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == 1 && it.value == "80" && it.updatedAt == 1000L })
        assertTrue(results.any { it.id == 2 && it.value == "180" && it.updatedAt == 2000L })

        database.close()
    }

    @Test
    fun bodyMetricDao_upsertReplacesExistingEntries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = LiftInsightDatabase.from(context)
        val dao = database.bodyMetricDao()

        withContext(Dispatchers.IO) {
            database.clearAllTables()
            dao.upsertAll(listOf(BodyMetricEntity(id = 1, value = "old", updatedAt = 100L)))
            dao.upsertAll(listOf(BodyMetricEntity(id = 1, value = "new", updatedAt = 200L)))
        }

        val results = withContext(Dispatchers.IO) {
            dao.getAll()
        }

        assertEquals(1, results.size)
        assertEquals("new", results[0].value)
        assertEquals(200L, results[0].updatedAt)
    }

    @Test
    fun bodyMetricDao_clearAllRemovesAllEntries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = LiftInsightDatabase.from(context)
        val dao = database.bodyMetricDao()

        withContext(Dispatchers.IO) {
            dao.upsertAll(
                listOf(
                    BodyMetricEntity(id = 1, value = "a", updatedAt = 1L),
                    BodyMetricEntity(id = 2, value = "b", updatedAt = 2L)
                )
            )
            dao.clearAll()
        }

        val results = withContext(Dispatchers.IO) {
            dao.getAll()
        }

        assertTrue(results.isEmpty())
    }

    private fun createV18DatabaseWithData() {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        db.execSQL(
            """
            CREATE TABLE motion (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'BARBELL'
            )
            """
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_motion_name ON motion (name)")

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
        db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_selection_current_plan_id ON plan_selection (current_plan_id)")

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
                active_history_id INTEGER,
                workout_intensity INTEGER,
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
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metaplan_plan_id ON metaplan (plan_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metaplan_motion_id ON metaplan (motion_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_metaplan_plan_id_day_index_order_index ON metaplan (plan_id, day_index, order_index)")

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
                history_id INTEGER,
                FOREIGN KEY(motion_id) REFERENCES motion(id) ON DELETE RESTRICT,
                FOREIGN KEY(history_id) REFERENCES history(id) ON DELETE SET NULL
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_motion_id ON metahistory (motion_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_history_id ON metahistory (history_id)")

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
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_process_state_video_name ON video_process_state (video_name)")

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
                imported_reference_distance_meters REAL,
                history_id INTEGER
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                plan_id INTEGER NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                intensity INTEGER NOT NULL DEFAULT 0,
                day_index INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(plan_id) REFERENCES plan(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_plan_id ON history (plan_id)")

        db.execSQL("INSERT INTO motion (name, type) VALUES ('Squat', 'BARBELL')")
        db.execSQL("INSERT INTO motion (name, type) VALUES ('Bench Press', 'BARBELL')")

        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'placeholder')")
        db.execSQL("PRAGMA user_version = 18")

        db.close()
    }
}
