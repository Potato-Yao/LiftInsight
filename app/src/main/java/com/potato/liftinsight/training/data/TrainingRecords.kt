package com.potato.liftinsight.training.data

data class MotionRecord(
    val id: Int,
    val name: String
)

data class CreateMotionRequest(
    val name: String? = null
)

data class MetaPlanRecord(
    val id: Int,
    val motionId: Int,
    val motionName: String,
    val dayIndex: Int = 1,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    val orderIndex: Int
)

data class CreateMetaPlanRequest(
    val motionId: Int,
    val dayIndex: Int = 1,
    val sets: Int,
    val reps: Int,
    val intensity: Double,
    val weight: Double,
    val orderIndex: Int
)

data class PlanRecord(
    val id: Int,
    val name: String,
    val cyclePeriod: Int,
    val currentIndex: Int = 1,
    val lastAppliedAt: Long = 0L,
    val metaPlans: List<MetaPlanRecord> = emptyList()
)

data class CreatePlanRequest(
    val name: String? = null,
    val cyclePeriod: Int,
    val currentIndex: Int = 1,
    val lastAppliedAt: Long = 0L,
    val metaPlans: List<CreateMetaPlanRequest> = emptyList()
)


