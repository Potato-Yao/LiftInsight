package com.potato.liftinsight.record.model

import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.video.VideoProcessingStatus

data class TrainingHistoryState(
    val records: List<MetaHistoryRecord> = emptyList(),
    val selectedRecord: MetaHistoryRecord? = null,
    val selectedVideoStatus: VideoProcessingStatus? = null,
    val videoStatusRefreshKey: Int = 0
)
