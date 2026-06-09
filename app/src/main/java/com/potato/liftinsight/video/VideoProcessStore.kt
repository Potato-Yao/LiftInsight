package com.potato.liftinsight.video

import android.content.Context
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.PlanDao
import com.potato.liftinsight.training.data.VideoProcessStateEntity

class VideoProcessStore private constructor(
    private val planDao: PlanDao,
    private val logger: AppLogger
) {
    fun getVideoProcessState(videoName: String): VideoProcessStateEntity? {
        return planDao.getVideoProcessState(videoName)
    }

    fun upsertVideoProcessState(state: VideoProcessStateEntity) {
        planDao.upsertVideoProcessState(state)
    }

    fun updateVideoProcessProgress(
        videoName: String,
        state: String,
        progress: Int
    ) {
        planDao.updateVideoProcessProgress(
            videoName = videoName,
            state = state,
            progress = progress
        )
    }

    companion object {
        fun from(context: Context): VideoProcessStore {
            return VideoProcessStore(
                planDao = LiftInsightDatabase.from(context).planDao(),
                logger = AndroidAppLogger
            )
        }

        internal fun fromDatabase(
            database: LiftInsightDatabase,
            logger: AppLogger = AndroidAppLogger
        ): VideoProcessStore {
            return VideoProcessStore(database.planDao(), logger)
        }
    }
}
