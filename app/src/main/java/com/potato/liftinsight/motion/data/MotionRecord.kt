package com.potato.liftinsight.motion.data

data class MotionFrameRecord(
    val frameIndex: Int,
    val time: Double,
    val hipAngle: Double? = null,
    val kneeAngle: Double? = null,
    val ankleAngle: Double? = null,
    val spineAngle: Double? = null,
    val elbowAngle: Double? = null,
    val trunkAngle: Double? = null,
    val barbellX: Double? = null,
    val barbellY: Double? = null,
    val barbellZ: Double? = null
)

data class MotionRecord(
    val id: Int,
    val name: String?,
    val recordedAt: String?,
    val notes: String?,
    val frames: List<MotionFrameRecord>
)

data class CreateMotionRequest(
    val name: String? = null,
    val recordedAt: String? = null,
    val notes: String? = null,
    val frames: List<MotionFrameRecord> = emptyList()
)

