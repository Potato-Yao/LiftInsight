package com.potato.liftinsight.training.data

import android.content.pm.ApplicationInfo
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.potato.liftinsight.common.logging.AndroidAppLogger

@Database(
    entities = [
        MotionEntity::class,
        PlanEntity::class,
        PlanSelectionEntity::class,
        WorkoutSessionEntity::class,
        WorkoutProgressEntity::class,
        MetaPlanEntity::class,
        MetaHistoryEntity::class,
        VideoProcessStateEntity::class,
        MetaHistoryBinEntity::class,
        HistoryEntity::class,
        BodyMetricEntity::class,
        MetahistoryTimeseriesEntity::class,
        MetahistoryTimeseriesBinEntity::class,
        PoseFrameEntity::class,
        VideoExportStateEntity::class
    ],
    version = 25,
    exportSchema = true
)
abstract class LiftInsightDatabase : RoomDatabase() {
    abstract fun motionDao(): MotionDao
    abstract fun planDao(): PlanDao
    abstract fun historyDao(): HistoryDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun timeseriesDao(): TimeseriesDao
    abstract fun poseFrameDao(): PoseFrameDao

    companion object {
        private const val TAG = "LiftInsightDatabase"

        @Volatile
        private var instance: LiftInsightDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 2 -> 3")
                db.execSQL("ALTER TABLE motion ADD COLUMN default_sets INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE motion ADD COLUMN default_reps_per_set INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan ADD COLUMN last_applied_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_selection (`id` INTEGER NOT NULL, `current_plan_id` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`current_plan_id`) REFERENCES `plan`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_selection_current_plan_id ON plan_selection (`current_plan_id`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 3 -> 4")
                db.execSQL("CREATE TABLE motion_new (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("INSERT INTO motion_new (`id`, `name`) SELECT `id`, `name` FROM motion")
                db.execSQL("DROP TABLE motion")
                db.execSQL("ALTER TABLE motion_new RENAME TO motion")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_motion_name ON motion (`name`)")
                db.execSQL("ALTER TABLE metaplan ADD COLUMN intensity REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 4 -> 5")
                db.execSQL("CREATE TABLE plan_new (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `cycle_period` INTEGER NOT NULL, `current_index` INTEGER NOT NULL, `last_applied_at` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO plan_new (`id`, `name`, `cycle_period`, `current_index`, `last_applied_at`) SELECT `id`, `name`, `repeat_cycle`, 0, `last_applied_at` FROM plan")
                db.execSQL("DROP TABLE plan")
                db.execSQL("ALTER TABLE plan_new RENAME TO plan")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 5 -> 6")
                db.execSQL("ALTER TABLE metaplan ADD COLUMN day_index INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX index_metaplan_plan_id_order_index")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_metaplan_plan_id_day_index_order_index ON metaplan (`plan_id`, `day_index`, `order_index`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 6 -> 7")
                db.execSQL("CREATE TABLE IF NOT EXISTS workout_session (`id` INTEGER NOT NULL, `is_workout_going` INTEGER NOT NULL, `is_paused` INTEGER NOT NULL, `started_at` INTEGER NOT NULL, `last_resumed_at` INTEGER NOT NULL, `elapsed_before_pause_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 7 -> 8")
                db.execSQL("ALTER TABLE plan_selection ADD COLUMN current_day_epoch INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 8 -> 9")
                db.execSQL("CREATE TABLE IF NOT EXISTS workout_progress (`id` INTEGER NOT NULL, `plan_id` INTEGER NOT NULL, `plan_day_index` INTEGER NOT NULL, `next_set_index` INTEGER NOT NULL, `active_set_index` INTEGER, `total_set_count` INTEGER NOT NULL, `break_ends_at` INTEGER NOT NULL, `is_finished` INTEGER NOT NULL, `completed_elapsed_time_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 9 -> 10")
                db.execSQL("CREATE TABLE IF NOT EXISTS metahistory (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `rep` INTEGER NOT NULL, `rpe` INTEGER NOT NULL, `weight` REAL NOT NULL, `motion_id` INTEGER NOT NULL, `video_name` TEXT, FOREIGN KEY(`motion_id`) REFERENCES `motion`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_motion_id ON metahistory (`motion_id`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 10 -> 11")
                db.execSQL("CREATE TABLE IF NOT EXISTS video_process_state (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `video_name` TEXT NOT NULL, `state` TEXT NOT NULL, `progress` INTEGER NOT NULL, `processed_video_name` TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_process_state_video_name ON video_process_state (`video_name`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 11 -> 12")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN video_source TEXT NOT NULL DEFAULT 'CAMERA_CAPTURE'")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN imported_video_analysis_mode TEXT NOT NULL DEFAULT 'ESTIMATED'")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN imported_reference_label TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN imported_reference_pixel_distance REAL")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN imported_reference_distance_meters REAL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 12 -> 13")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS metahistory_bin (
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
            }
        }

        val MIGRATION_13_12 = object : Migration(13, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 13 -> 12")
                db.execSQL("CREATE TABLE workout_progress_new (`id` INTEGER NOT NULL, `plan_id` INTEGER NOT NULL, `plan_day_index` INTEGER NOT NULL, `next_set_index` INTEGER NOT NULL, `active_set_index` INTEGER, `total_set_count` INTEGER NOT NULL, `break_ends_at` INTEGER NOT NULL, `is_finished` INTEGER NOT NULL, `completed_elapsed_time_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO workout_progress_new (`id`, `plan_id`, `plan_day_index`, `next_set_index`, `active_set_index`, `total_set_count`, `break_ends_at`, `is_finished`, `completed_elapsed_time_ms`) SELECT `id`, `plan_id`, `plan_day_index`, `next_set_index`, `active_set_index`, `total_set_count`, `break_ends_at`, `is_finished`, `completed_elapsed_time_ms` FROM workout_progress")
                db.execSQL("DROP TABLE workout_progress")
                db.execSQL("ALTER TABLE workout_progress_new RENAME TO workout_progress")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 13 -> 14")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        plan_id INTEGER NOT NULL,
                        start_time INTEGER NOT NULL,
                        end_time INTEGER NOT NULL,
                        intensity INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(plan_id) REFERENCES plan(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_plan_id ON history (`plan_id`)")

                db.execSQL(
                    """
                    CREATE TABLE metahistory_new (
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
                        FOREIGN KEY(motion_id) REFERENCES motion(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(history_id) REFERENCES history(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO metahistory_new (
                        id, date, rep, rpe, weight, motion_id, video_name,
                        video_source, imported_video_analysis_mode,
                        imported_reference_label, imported_reference_pixel_distance,
                        imported_reference_distance_meters
                    )
                    SELECT
                        id, date, rep, rpe, weight, motion_id, video_name,
                        video_source, imported_video_analysis_mode,
                        imported_reference_label, imported_reference_pixel_distance,
                        imported_reference_distance_meters
                    FROM metahistory
                    """
                )
                db.execSQL("DROP TABLE metahistory")
                db.execSQL("ALTER TABLE metahistory_new RENAME TO metahistory")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_motion_id ON metahistory (`motion_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_history_id ON metahistory (`history_id`)")

                db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN history_id INTEGER")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 14 -> 15")
                db.execSQL("ALTER TABLE history ADD COLUMN day_index INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 15 -> 16")
                db.execSQL("ALTER TABLE workout_progress ADD COLUMN active_history_id INTEGER")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 16 -> 17")
                db.execSQL("ALTER TABLE workout_progress ADD COLUMN workout_intensity INTEGER")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 17 -> 18")
                db.execSQL("ALTER TABLE motion ADD COLUMN type TEXT NOT NULL DEFAULT 'BARBELL'")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 18 -> 19")
                db.execSQL("CREATE TABLE IF NOT EXISTS body_metric (id INTEGER NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 19 -> 20")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS metahistory_timeseries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        metahistory_id INTEGER NOT NULL,
                        timestamp_ms INTEGER NOT NULL,
                        metric_name TEXT NOT NULL,
                        value REAL NOT NULL,
                        FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_timeseries_metahistory_id_metric_name_timestamp_ms ON metahistory_timeseries (`metahistory_id`, `metric_name`, `timestamp_ms`)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 20 -> 21")
                // Add detection columns to metahistory
                db.execSQL("ALTER TABLE metahistory ADD COLUMN pose_detection INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN angle_display INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN angle_plot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN barbell_detection INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN power_calculation INTEGER NOT NULL DEFAULT 0")
                // Add detection columns to metahistory_bin
                db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN pose_detection INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN angle_display INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN angle_plot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN barbell_detection INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN power_calculation INTEGER NOT NULL DEFAULT 0")
                // Create metahistory_timeseries_bin table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS metahistory_timeseries_bin (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        original_metahistory_id INTEGER NOT NULL,
                        timestamp_ms INTEGER NOT NULL,
                        metric_name TEXT NOT NULL,
                        value REAL NOT NULL
                    )
                    """
                )
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 21 -> 22")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS metahistory_pose_frame (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        metahistory_id INTEGER NOT NULL,
                        timestamp_ms INTEGER NOT NULL,
                        landmarks_json TEXT NOT NULL,
                        FOREIGN KEY(metahistory_id) REFERENCES metahistory(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_pose_frame_metahistory_id_timestamp_ms ON metahistory_pose_frame (metahistory_id, timestamp_ms)")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 22 -> 23")
                db.execSQL("ALTER TABLE metahistory ADD COLUMN marked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE metahistory_bin ADD COLUMN marked INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 23 -> 24")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `video_export_state` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `video_name` TEXT NOT NULL,
                        `rendered_items` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL,
                        `exported_file_name` TEXT
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_export_state_video_name` ON `video_export_state` (`video_name`)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AndroidAppLogger.info(TAG, "Running migration 24 -> 25")
                // Drop old non-unique index and recreate as unique
                db.execSQL("DROP INDEX IF EXISTS `index_video_export_state_video_name`")
                // Deduplicate: keep only the latest row per video_name
                db.execSQL("""
                    DELETE FROM video_export_state 
                    WHERE id NOT IN (
                        SELECT MAX(id) FROM video_export_state GROUP BY video_name
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_video_export_state_video_name` ON `video_export_state` (`video_name`)")
            }
        }

        fun from(context: Context): LiftInsightDatabase {
            val existingInstance = instance
            if (existingInstance != null) {
                return existingInstance
            }

            return synchronized(this) {
                val currentInstance = instance
                if (currentInstance != null) {
                    currentInstance
                } else {
                    val isDebuggable =
                        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    val databaseName = if (isDebuggable) {
                        "lift_insight.dev.db"
                    } else {
                        "lift_insight.db"
                    }

                    AndroidAppLogger.info(TAG, "Creating database instance: databaseName=$databaseName")

                    val createdInstance = Room.databaseBuilder(
                        context.applicationContext,
                        LiftInsightDatabase::class.java,
                        databaseName
                    )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_12,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25
                    )
                        .build()

                    instance = createdInstance
                    AndroidAppLogger.debug(TAG, "Database instance created successfully")
                    createdInstance
                }
            }
        }
    }
}



