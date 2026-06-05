package com.potato.liftinsight.record.model

import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.video.VideoProcessingStatus

data class TrainingHistoryState(
    val records: List<MetaHistoryRecord> = emptyList(),
    val selectedRecord: MetaHistoryRecord? = null,
    val selectedVideoStatus: VideoProcessingStatus? = null,
    val videoStatusRefreshKey: Int = 0,
    val isBatchMode: Boolean = false,
    val selectedRecordIds: Set<Int> = emptySet(),
    val expandedDateGroups: Set<String> = emptySet(),
    val isBinMode: Boolean = false,
    val binRecords: List<MetaHistoryRecord> = emptyList(),
    val selectedBinRecord: MetaHistoryRecord? = null
)
