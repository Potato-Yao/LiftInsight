package com.potato.liftinsight.video

import android.content.Context
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.MetahistoryTimeseriesEntity
import com.potato.liftinsight.training.data.PlanDao
import com.potato.liftinsight.training.data.PoseFrameDao
import com.potato.liftinsight.training.data.PoseFrameEntity
import com.potato.liftinsight.training.data.TimeseriesDao
import com.potato.liftinsight.training.data.VideoExportStateEntity
import com.potato.liftinsight.training.data.VideoProcessStateEntity

class VideoProcessStore private constructor(
    private val database: LiftInsightDatabase,
    private val planDao: PlanDao,
    private val timeseriesDao: TimeseriesDao,
    private val poseFrameDao: PoseFrameDao,
    private val logger: AppLogger
) {
    fun getVideoProcessState(videoName: String): VideoProcessStateEntity? {
        return planDao.getVideoProcessState(videoName)
    }

    fun deleteVideoProcessState(videoName: String) {
        planDao.deleteVideoProcessState(videoName)
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

    fun getMetaHistoryIdByVideoName(videoName: String): Int? {
        return planDao.getMetaHistoryIdByVideoName(videoName)
    }

    fun deleteTimeseries(metahistoryId: Int) {
        timeseriesDao.deleteByMetaHistoryId(metahistoryId)
    }

    fun insertTimeseries(entities: List<MetahistoryTimeseriesEntity>) {
        if (entities.isNotEmpty()) {
            timeseriesDao.insertAll(entities)
        }
    }

    fun replaceTimeseries(metahistoryId: Int, entities: List<MetahistoryTimeseriesEntity>) {
        database.runInTransaction {
            timeseriesDao.deleteByMetaHistoryId(metahistoryId)
            if (entities.isNotEmpty()) {
                timeseriesDao.insertAll(entities)
            }
        }
    }

    fun deletePoseFrames(metahistoryId: Int) {
        poseFrameDao.deleteByMetaHistoryId(metahistoryId)
    }

    fun insertPoseFrames(entities: List<PoseFrameEntity>) {
        if (entities.isNotEmpty()) {
            poseFrameDao.insertAll(entities)
        }
    }

    fun replacePoseFrames(metahistoryId: Int, entities: List<PoseFrameEntity>) {
        database.runInTransaction {
            poseFrameDao.deleteByMetaHistoryId(metahistoryId)
            if (entities.isNotEmpty()) {
                poseFrameDao.insertAll(entities)
            }
        }
    }

    fun getVideoExportState(videoName: String): VideoExportStateEntity? = planDao.getVideoExportState(videoName)

    fun upsertVideoExportState(state: VideoExportStateEntity) = planDao.upsertVideoExportState(state)

    fun deleteVideoExportState(videoName: String) = planDao.deleteVideoExportState(videoName)

    fun updateVideoExportProgress(videoName: String, state: String, progress: Int) =
        planDao.updateVideoExportProgress(videoName, state, progress)

    companion object {
        fun from(context: Context): VideoProcessStore {
            val database = LiftInsightDatabase.from(context)
            return VideoProcessStore(
                database = database,
                planDao = database.planDao(),
                timeseriesDao = database.timeseriesDao(),
                poseFrameDao = database.poseFrameDao(),
                logger = AndroidAppLogger
            )
        }

        internal fun fromDatabase(
            database: LiftInsightDatabase,
            logger: AppLogger = AndroidAppLogger
        ): VideoProcessStore {
            return VideoProcessStore(database, database.planDao(), database.timeseriesDao(), database.poseFrameDao(), logger)
        }
    }
}
