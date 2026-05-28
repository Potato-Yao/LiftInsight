package com.potato.liftinsight.training.data

import android.content.pm.ApplicationInfo
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MotionEntity::class,
        PlanEntity::class,
        PlanSelectionEntity::class,
        WorkoutSessionEntity::class,
        WorkoutProgressEntity::class,
        MetaPlanEntity::class,
        MetaHistoryEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class LiftInsightDatabase : RoomDatabase() {
    abstract fun motionDao(): MotionDao
    abstract fun planDao(): PlanDao

    companion object {
        @Volatile
        private var instance: LiftInsightDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE motion ADD COLUMN default_sets INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE motion ADD COLUMN default_reps_per_set INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan ADD COLUMN last_applied_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_selection (`id` INTEGER NOT NULL, `current_plan_id` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`current_plan_id`) REFERENCES `plan`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_selection_current_plan_id ON plan_selection (`current_plan_id`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                db.execSQL("CREATE TABLE plan_new (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `cycle_period` INTEGER NOT NULL, `current_index` INTEGER NOT NULL, `last_applied_at` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO plan_new (`id`, `name`, `cycle_period`, `current_index`, `last_applied_at`) SELECT `id`, `name`, `repeat_cycle`, 0, `last_applied_at` FROM plan")
                db.execSQL("DROP TABLE plan")
                db.execSQL("ALTER TABLE plan_new RENAME TO plan")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE metaplan ADD COLUMN day_index INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX index_metaplan_plan_id_order_index")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_metaplan_plan_id_day_index_order_index ON metaplan (`plan_id`, `day_index`, `order_index`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS workout_session (`id` INTEGER NOT NULL, `is_workout_going` INTEGER NOT NULL, `is_paused` INTEGER NOT NULL, `started_at` INTEGER NOT NULL, `last_resumed_at` INTEGER NOT NULL, `elapsed_before_pause_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_selection ADD COLUMN current_day_epoch INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS workout_progress (`id` INTEGER NOT NULL, `plan_id` INTEGER NOT NULL, `plan_day_index` INTEGER NOT NULL, `next_set_index` INTEGER NOT NULL, `active_set_index` INTEGER, `total_set_count` INTEGER NOT NULL, `break_ends_at` INTEGER NOT NULL, `is_finished` INTEGER NOT NULL, `completed_elapsed_time_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS metahistory (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `rep` INTEGER NOT NULL, `rpe` INTEGER NOT NULL, `weight` REAL NOT NULL, `motion_id` INTEGER NOT NULL, `video_name` TEXT, FOREIGN KEY(`motion_id`) REFERENCES `motion`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metahistory_motion_id ON metahistory (`motion_id`)")
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
                            MIGRATION_9_10
                        )
                        .build()

                    instance = createdInstance
                    createdInstance
                }
            }
        }
    }
}




