package com.potato.liftinsight.training.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MotionEntity::class, PlanEntity::class, MetaPlanEntity::class],
    version = 2,
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
                    val createdInstance = Room.databaseBuilder(
                        context.applicationContext,
                        LiftInsightDatabase::class.java,
                        "lift_insight.db"
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




