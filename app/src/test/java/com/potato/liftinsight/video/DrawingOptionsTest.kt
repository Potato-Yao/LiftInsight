package com.potato.liftinsight.video

import org.junit.Assert.assertFalse
import org.junit.Test

class DrawingOptionsTest {
    @Test
    fun defaultValues_areFalse() {
        val options = DrawingOptions()

        assertFalse(options.drawLandmarks)
        assertFalse(options.drawAngles)
    }
}
