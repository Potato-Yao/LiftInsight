package com.potato.liftinsight.video

import android.content.Context
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import com.potato.liftinsight.training.data.LiftInsightDatabase
import com.potato.liftinsight.training.data.VideoExportStateEntity
import com.potato.liftinsight.training.data.VideoProcessState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class VideoExportStatus(
    val videoName: String,
    val state: VideoProcessState,
    val progress: Int,
    val exportedFileName: String?
)

interface VideoExporter {
    fun submitExport(
        videoName: String,
        metahistoryId: Int,
        motionName: String,
        date: String,
        options: ExportOverlayOptions
    )

    fun isExporting(videoName: String): Boolean
    fun getExportProgress(videoName: String): Int
    fun getExportStatus(videoName: String): VideoExportStatus?
    fun resetExportState(videoName: String)

    companion object {
        fun from(
            context: Context,
            logger: AppLogger = AndroidAppLogger
        ): VideoExporter {
            val appContext = context.applicationContext
            val database = LiftInsightDatabase.from(appContext)
            val videoFileManager = VideoFileManager(appContext)
            val videoProcessStore = VideoProcessStore.fromDatabase(database, logger)
            val worker = VideoExportRenderWorker(
                context = appContext,
                videoFileManager = videoFileManager,
                poseFrameDao = database.poseFrameDao(),
                timeseriesDao = database.timeseriesDao(),
                barbellFrameDao = database.barbellFrameDao(),
                logger = logger
            )
            return VideoExporterImpl(
                context = appContext,
                videoProcessStore = videoProcessStore,
                worker = worker,
                logger = logger
            )
        }
    }
}

object NoOpVideoExporter : VideoExporter {
    override fun submitExport(
        videoName: String,
        metahistoryId: Int,
        motionName: String,
        date: String,
        options: ExportOverlayOptions
    ) = Unit

    override fun isExporting(videoName: String): Boolean = false
    override fun getExportProgress(videoName: String): Int = 0
    override fun getExportStatus(videoName: String): VideoExportStatus? = null
    override fun resetExportState(videoName: String) = Unit
}

private class VideoExporterImpl(
    private val context: Context,
    private val videoProcessStore: VideoProcessStore,
    private val worker: VideoExportRenderWorker,
    private val logger: AppLogger
) : VideoExporter {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queuedExports = ConcurrentHashMap.newKeySet<String>()

    override fun submitExport(
        videoName: String,
        metahistoryId: Int,
        motionName: String,
        date: String,
        options: ExportOverlayOptions
    ) {
        val normalizedVideoName = videoName.trim()
        logger.info(TAG, "submitExport ENTERED: videoName=$normalizedVideoName, metahistoryId=$metahistoryId, motionName=$motionName, date=$date, options=$options")

        if (normalizedVideoName.isBlank()) {
            logger.warn(TAG, "submitExport EARLY RETURN: blank videoName")
            return
        }

        val added = queuedExports.add(normalizedVideoName)
        logger.info(TAG, "submitExport queuedExports.add result: $added (videoName=$normalizedVideoName)")

        if (!added) {
            logger.warn(TAG, "submitExport EARLY RETURN: duplicate export already queued for videoName=$normalizedVideoName")
            return
        }

        try {
            logger.info(TAG, "submitExport writing PROCESSING state to DB: videoName=$normalizedVideoName")
            videoProcessStore.upsertVideoExportState(
                VideoExportStateEntity(
                    videoName = normalizedVideoName,
                    renderedItems = options.renderedItemsCode,
                    state = VideoProcessState.PROCESSING.name,
                    progress = 0
                )
            )
            logger.info(TAG, "submitExport DB write SUCCESS: videoName=$normalizedVideoName")
        } catch (e: Exception) {
            logger.error(TAG, "submitExport DB write FAILED: videoName=$normalizedVideoName", e)
            queuedExports.remove(normalizedVideoName)
            return
        }

        try {
            logger.info(TAG, "submitExport starting foreground service")
            VideoProcessingForegroundService.start(context)
            logger.info(TAG, "submitExport foreground service started")
        } catch (e: Exception) {
            logger.error(TAG, "submitExport foreground service FAILED", e)
            // Continue anyway, not critical
        }

        logger.info(TAG, "submitExport launching coroutine on scope=$scope")
        scope.launch {
            logger.info(TAG, "submitExport COROUTINE STARTED: videoName=$normalizedVideoName")
            try {
                logger.info(TAG, "submitExport calling worker.renderExport: videoName=$normalizedVideoName")
                val outputFile = worker.renderExport(
                    videoName = normalizedVideoName,
                    metahistoryId = metahistoryId,
                    motionName = motionName,
                    date = date,
                    options = options,
                    progressCallback = { progress ->
                        logger.info(TAG, "submitExport progress callback: videoName=$normalizedVideoName, progress=$progress")
                        videoProcessStore.updateVideoExportProgress(
                            videoName = normalizedVideoName,
                            state = VideoProcessState.PROCESSING.name,
                            progress = progress
                        )
                    }
                )

                logger.info(TAG, "submitExport worker.renderExport RETURNED: outputFile=${outputFile?.absolutePath}, exists=${outputFile?.exists()}, size=${outputFile?.length()}")

                if (outputFile != null && outputFile.exists()) {
                    logger.info(TAG, "submitExport writing DONE state to DB")
                    videoProcessStore.upsertVideoExportState(
                        VideoExportStateEntity(
                            videoName = normalizedVideoName,
                            renderedItems = options.renderedItemsCode,
                            state = VideoProcessState.DONE.name,
                            progress = 100,
                            exportedFileName = outputFile.name
                        )
                    )

                    val displayName = outputFile.nameWithoutExtension
                    logger.info(TAG, "submitExport exporting to gallery: displayName=$displayName")
                    val galleryUri = VideoExportHelper.exportToGallery(context, outputFile, displayName)
                    logger.info(TAG, "submitExport gallery export result: uri=$galleryUri")

                    logger.info(TAG, "Export SUCCESS: videoName=$normalizedVideoName, outputFile=${outputFile.name}, size=${outputFile.length()}")
                } else {
                    logger.warn(TAG, "Export FAILED: worker returned null or file doesn't exist: videoName=$normalizedVideoName, outputFile=$outputFile")
                    videoProcessStore.upsertVideoExportState(
                        VideoExportStateEntity(
                            videoName = normalizedVideoName,
                            renderedItems = options.renderedItemsCode,
                            state = VideoProcessState.ERROR.name,
                            progress = 0
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error(TAG, "Export EXCEPTION in coroutine: videoName=$normalizedVideoName, error=${e.message}", e)
                try {
                    videoProcessStore.upsertVideoExportState(
                        VideoExportStateEntity(
                            videoName = normalizedVideoName,
                            renderedItems = options.renderedItemsCode,
                            state = VideoProcessState.ERROR.name,
                            progress = 0
                        )
                    )
                } catch (dbError: Exception) {
                    logger.error(TAG, "Export EXCEPTION: failed to write ERROR state to DB", dbError)
                }
            } finally {
                logger.info(TAG, "submitExport COROUTINE FINALLY: videoName=$normalizedVideoName, removing from queue")
                queuedExports.remove(normalizedVideoName)
                if (queuedExports.isEmpty()) {
                    logger.info(TAG, "submitExport stopping foreground service (queue empty)")
                    VideoProcessingForegroundService.stopWork(context)
                }
            }
        }
        logger.info(TAG, "submitExport EXITED (coroutine launched): videoName=$normalizedVideoName")
    }

    override fun isExporting(videoName: String): Boolean {
        val status = getExportStatus(videoName)
        return status?.state == VideoProcessState.PROCESSING
    }

    override fun getExportProgress(videoName: String): Int {
        return getExportStatus(videoName)?.progress ?: 0
    }

    override fun getExportStatus(videoName: String): VideoExportStatus? {
        val entity = videoProcessStore.getVideoExportState(videoName.trim()) ?: return null
        val state = try {
            VideoProcessState.valueOf(entity.state)
        } catch (_: IllegalArgumentException) {
            VideoProcessState.NOT_STARTED
        }
        return VideoExportStatus(
            videoName = entity.videoName,
            state = state,
            progress = entity.progress,
            exportedFileName = entity.exportedFileName
        )
    }

    override fun resetExportState(videoName: String) {
        videoProcessStore.deleteVideoExportState(videoName.trim())
    }

    companion object {
        private const val TAG = "VideoExporter"
    }
}
