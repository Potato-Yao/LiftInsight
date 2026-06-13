package com.potato.liftinsight.settings.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class VideoCleanupStore private constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun getCleanupThresholdDays(): Int {
        return sharedPreferences.getInt(KEY_CLEANUP_THRESHOLD_DAYS, DEFAULT_CLEANUP_THRESHOLD_DAYS)
    }

    fun setCleanupThresholdDays(days: Int) {
        sharedPreferences.edit {
            putInt(KEY_CLEANUP_THRESHOLD_DAYS, days)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "liftinsight.settings"
        private const val KEY_CLEANUP_THRESHOLD_DAYS = "video_cleanup_threshold_days"
        const val DEFAULT_CLEANUP_THRESHOLD_DAYS = 30
        const val MIN_CLEANUP_THRESHOLD_DAYS = 1
        const val MAX_CLEANUP_THRESHOLD_DAYS = 365

        fun from(context: Context): VideoCleanupStore {
            return VideoCleanupStore(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
