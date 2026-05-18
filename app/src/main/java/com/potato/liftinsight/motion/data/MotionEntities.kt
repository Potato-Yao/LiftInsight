package com.potato.liftinsight.motion.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "motions")
data class MotionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String? = null,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: String? = null,
    val notes: String? = null
)

@Entity(
    tableName = "frames",
    primaryKeys = ["motion_id", "frame_index"],
    foreignKeys = [
        ForeignKey(
            entity = MotionEntity::class,
            parentColumns = ["id"],
            childColumns = ["motion_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["motion_id"])]
)
data class FrameEntity(
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "frame_index")
    val frameIndex: Int,
    val time: Double,
    @ColumnInfo(name = "hip_angle")
    val hipAngle: Double? = null,
    @ColumnInfo(name = "knee_angle")
    val kneeAngle: Double? = null,
    @ColumnInfo(name = "ankle_angle")
    val ankleAngle: Double? = null,
    @ColumnInfo(name = "spine_angle")
    val spineAngle: Double? = null,
    @ColumnInfo(name = "elbow_angle")
    val elbowAngle: Double? = null,
    @ColumnInfo(name = "trunk_angle")
    val trunkAngle: Double? = null,
    @ColumnInfo(name = "barbell_x")
    val barbellX: Double? = null,
    @ColumnInfo(name = "barbell_y")
    val barbellY: Double? = null,
    @ColumnInfo(name = "barbell_z")
    val barbellZ: Double? = null
)

internal fun MotionEntity.toRecord(frames: List<FrameEntity>): MotionRecord {
    return MotionRecord(
        id = id,
        name = name,
        recordedAt = recordedAt,
        notes = notes,
        frames = frames.map { it.toRecord() }
    )
}

internal fun MotionRecord.toEntity(): MotionEntity {
    return MotionEntity(
        id = id,
        name = name,
        recordedAt = recordedAt,
        notes = notes
    )
}

internal fun FrameEntity.toRecord(): MotionFrameRecord {
    return MotionFrameRecord(
        frameIndex = frameIndex,
        time = time,
        hipAngle = hipAngle,
        kneeAngle = kneeAngle,
        ankleAngle = ankleAngle,
        spineAngle = spineAngle,
        elbowAngle = elbowAngle,
        trunkAngle = trunkAngle,
        barbellX = barbellX,
        barbellY = barbellY,
        barbellZ = barbellZ
    )
}

internal fun MotionFrameRecord.toEntity(motionId: Int): FrameEntity {
    return FrameEntity(
        motionId = motionId,
        frameIndex = frameIndex,
        time = time,
        hipAngle = hipAngle,
        kneeAngle = kneeAngle,
        ankleAngle = ankleAngle,
        spineAngle = spineAngle,
        elbowAngle = elbowAngle,
        trunkAngle = trunkAngle,
        barbellX = barbellX,
        barbellY = barbellY,
        barbellZ = barbellZ
    )
}

