package com.potato.liftinsight

import org.junit.Assert.assertNotNull
import org.junit.Test

class OpenCVImportTest {

    @Test
    fun `opencv Mat class is on classpath`() {
        val clazz = Class.forName("org.opencv.core.Mat", false, javaClass.classLoader)
        assertNotNull(clazz)
    }

    @Test
    fun `opencv Imgproc class is on classpath`() {
        val clazz = Class.forName("org.opencv.imgproc.Imgproc", false, javaClass.classLoader)
        assertNotNull(clazz)
    }

    @Test
    fun `opencv Core class is on classpath`() {
        val clazz = Class.forName("org.opencv.core.Core", false, javaClass.classLoader)
        assertNotNull(clazz)
    }

    @Test
    fun `opencv CvType class is on classpath`() {
        val clazz = Class.forName("org.opencv.core.CvType", false, javaClass.classLoader)
        assertNotNull(clazz)
    }

    @Test
    fun `opencv Imgcodecs class is on classpath`() {
        val clazz = Class.forName("org.opencv.imgcodecs.Imgcodecs", false, javaClass.classLoader)
        assertNotNull(clazz)
    }

    @Test
    fun `opencv VideoCapture class is on classpath`() {
        val clazz = Class.forName("org.opencv.videoio.VideoCapture", false, javaClass.classLoader)
        assertNotNull(clazz)
    }
}
