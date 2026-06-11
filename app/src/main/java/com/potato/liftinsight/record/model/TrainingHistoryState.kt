package com.potato.liftinsight.record.model

import com.potato.liftinsight.training.data.HistoryRecord
import com.potato.liftinsight.training.data.MetaHistoryRecord
import com.potato.liftinsight.video.VideoExportStatus
import com.potato.liftinsight.video.VideoProcessingStatus

enum class DisplayMode { META_HISTORY, HISTORY }

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
    val selectedBinRecord: MetaHistoryRecord? = null,
    val displayMode: DisplayMode = DisplayMode.META_HISTORY,
    val historyRecords: List<HistoryRecord> = emptyList(),
    val selectedHistorySession: HistoryRecord? = null,
    val sessionMetaHistoryRecords: List<MetaHistoryRecord> = emptyList(),
    val exportVideoStatus: VideoExportStatus? = null
)
