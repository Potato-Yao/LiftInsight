package com.potato.liftinsight.record.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisVideoStateTest {

    @Test
    fun `default state has all toggles off`() {
        val state = AnalysisVideoState()

        assertFalse(state.poseDetection)
        assertFalse(state.angleDisplay)
        assertFalse(state.anglePlot)
        assertFalse(state.barbellDetection)
        assertFalse(state.powerCalculation)
    }

    @Test
    fun `toggling poseDetection on enables barbellDetection toggle`() {
        val state = AnalysisVideoState()
        val toggled = state.togglePoseDetection()

        assertTrue(toggled.poseDetection)
        assertTrue(toggled.isBarbellDetectionEnabled)
    }

    @Test
    fun `toggling poseDetection off cascades to turn off barbellDetection and powerCalculation`() {
        val state = AnalysisVideoState(
            poseDetection = true,
            barbellDetection = true,
            powerCalculation = true
        )

        val toggled = state.togglePoseDetection()

        assertFalse(toggled.poseDetection)
        assertFalse(toggled.barbellDetection)
        assertFalse(toggled.powerCalculation)
    }

    @Test
    fun `barbellDetection cannot be turned on when poseDetection is off`() {
        val state = AnalysisVideoState(
            poseDetection = false
        )

        val toggled = state.toggleBarbellDetection()

        assertFalse(toggled.barbellDetection)
        assertFalse(toggled.isBarbellDetectionEnabled)
    }

    @Test
    fun `powerCalculation cannot be turned on when barbellDetection is off`() {
        val state = AnalysisVideoState(
            poseDetection = true,
            barbellDetection = false
        )

        val toggled = state.togglePowerCalculation()

        assertFalse(toggled.powerCalculation)
        assertFalse(toggled.isPowerCalculationEnabled)
    }

    @Test
    fun `toggling angleDisplay and anglePlot are independent`() {
        val state = AnalysisVideoState()

        val withAngleDisplay = state.toggleAngleDisplay()

        assertTrue(withAngleDisplay.angleDisplay)
        assertFalse(withAngleDisplay.anglePlot)

        val withAnglePlot = withAngleDisplay.toggleAnglePlot()

        assertTrue(withAnglePlot.angleDisplay)
        assertTrue(withAnglePlot.anglePlot)
    }

    @Test
    fun `turning on poseDetection then barbellDetection then powerCalculation works in sequence`() {
        var state = AnalysisVideoState()

        state = state.togglePoseDetection()
        assertTrue(state.poseDetection)
        assertTrue(state.isBarbellDetectionEnabled)
        assertFalse(state.barbellDetection)
        assertFalse(state.powerCalculation)

        state = state.toggleBarbellDetection()
        assertTrue(state.barbellDetection)
        assertTrue(state.isPowerCalculationEnabled)
        assertFalse(state.powerCalculation)

        state = state.togglePowerCalculation()
        assertTrue(state.powerCalculation)
    }
}
