package com.potato.liftinsight.training.data

import android.content.pm.ApplicationInfo
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MotionEntity::class, PlanEntity::class, PlanSelectionEntity::class, MetaPlanEntity::class],
    version = 4,
    exportSchema = true
)
abstract class LiftInsightDatabase : RoomDatabase() {
    abstract fun motionDao(): MotionDao
    abstract fun planDao(): PlanDao

    companion object {
        @Volatile
        private var instance: LiftInsightDatabase? = null

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
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()

                    instance = createdInstance
                    createdInstance
                }
            }
        }
    }
}




