package com.potato.liftinsight.record.model

data class AnalysisVideoState(
    val poseDetection: Boolean = false,
    val angleDisplay: Boolean = false,
    val anglePlot: Boolean = false,
    val barbellDetection: Boolean = false,
    val powerCalculation: Boolean = false
) {
    val isBarbellDetectionEnabled: Boolean
        get() = poseDetection

    val isPowerCalculationEnabled: Boolean
        get() = barbellDetection

    fun togglePoseDetection(): AnalysisVideoState {
        val newValue = !poseDetection

        return copy(
            poseDetection = newValue,
            barbellDetection = if (!newValue) false else barbellDetection,
            powerCalculation = if (!newValue) false else powerCalculation
        )
    }

    fun toggleAngleDisplay(): AnalysisVideoState {
        return copy(angleDisplay = !angleDisplay)
    }

    fun toggleAnglePlot(): AnalysisVideoState {
        return copy(anglePlot = !anglePlot)
    }

    fun toggleBarbellDetection(): AnalysisVideoState {
        if (!isBarbellDetectionEnabled) {
            return this
        }

        val newValue = !barbellDetection

        return copy(
            barbellDetection = newValue,
            powerCalculation = if (!newValue) false else powerCalculation
        )
    }

    fun togglePowerCalculation(): AnalysisVideoState {
        if (!isPowerCalculationEnabled) {
            return this
        }

        return copy(powerCalculation = !powerCalculation)
    }
}
