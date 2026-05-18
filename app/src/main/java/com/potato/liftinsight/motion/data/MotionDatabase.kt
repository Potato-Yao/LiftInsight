package com.potato.liftinsight.motion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MotionEntity::class, FrameEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MotionDatabase : RoomDatabase() {
    abstract fun motionDao(): MotionDao

    companion object {
        @Volatile
        private var instance: MotionDatabase? = null

        fun from(context: Context): MotionDatabase {
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
                        MotionDatabase::class.java,
                        "motions.db"
                    ).build()

                    instance = createdInstance
                    createdInstance
                }
            }
        }
    }
}


