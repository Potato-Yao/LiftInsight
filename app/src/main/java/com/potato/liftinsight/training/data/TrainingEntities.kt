package com.potato.liftinsight.training.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "motion",
    indices = [Index(value = ["name"], unique = true)]
)
data class MotionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)

@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "repeat_cycle")
    val repeatCycle: Int,
    @ColumnInfo(name = "last_applied_at")
    val lastAppliedAt: Long = 0L
)

@Entity(
    tableName = "plan_selection",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["current_plan_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["current_plan_id"])]
)
data class PlanSelectionEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "current_plan_id")
    val currentPlanId: Int? = null
)

@Entity(
    tableName = "metaplan",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MotionEntity::class,
            parentColumns = ["id"],
            childColumns = ["motion_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["motion_id"]),
        Index(value = ["plan_id", "order_index"], unique = true)
    ]
)
data class MetaPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)

data class MetaPlanRow(
    val id: Int,
    @ColumnInfo(name = "plan_id")
    val planId: Int,
    @ColumnInfo(name = "motion_id")
    val motionId: Int,
    @ColumnInfo(name = "motion_name")
    val motionName: String,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)

internal fun MotionEntity.toRecord(): MotionRecord {
    return MotionRecord(
        id = id,
        name = name
    )
}

internal fun MotionRecord.toEntity(): MotionEntity {
    return MotionEntity(
        id = id,
        name = name
    )
}

internal fun PlanEntity.toRecord(metaPlans: List<MetaPlanRow>): PlanRecord {
    return PlanRecord(
        id = id,
        name = name,
        repeatCycle = repeatCycle,
        lastAppliedAt = lastAppliedAt,
        metaPlans = metaPlans.map { metaPlan -> metaPlan.toRecord() }
    )
}

internal fun PlanRecord.toEntity(): PlanEntity {
    return PlanEntity(
        id = id,
        name = name,
        repeatCycle = repeatCycle,
        lastAppliedAt = lastAppliedAt
    )
}

internal fun MetaPlanRow.toRecord(): MetaPlanRecord {
    return MetaPlanRecord(
        id = id,
        motionId = motionId,
        motionName = motionName,
        sets = sets,
        reps = reps,
        intensity = intensity,
        weight = weight,
        orderIndex = orderIndex
    )
}


