package com.potato.liftinsight.motion.model

import com.potato.liftinsight.training.data.MotionType

data class MotionRowState(
    val id: Int,
    val name: String,
    val type: MotionType
)

data class MotionSectionState(
    val label: String,
    val motions: List<MotionRowState>
)

enum class MotionEditorMessage {
    BlankName,
    DuplicateName,
    DeleteBlocked,
    SaveFailed
}

data class MotionEditorState(
    val motionId: Int? = null,
    val name: String = "",
    val type: MotionType = MotionType.BARBELL,
    val message: MotionEditorMessage? = null
) {
    val isNewMotion: Boolean
        get() = motionId == null
}

data class MotionState(
    val motions: List<MotionRowState> = emptyList(),
    val sections: List<MotionSectionState> = emptyList(),
    val editor: MotionEditorState? = null
) {
    val isEditing: Boolean
        get() = editor != null
}

