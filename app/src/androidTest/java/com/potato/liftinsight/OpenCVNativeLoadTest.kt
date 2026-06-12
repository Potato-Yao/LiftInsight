package com.potato.liftinsight

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

@RunWith(AndroidJUnit4::class)
class OpenCVNativeLoadTest {

    @Test
    fun `native library loads successfully`() {
        val loaded = try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        assertTrue("OpenCV native library should load", loaded)
    }

    @Test
    fun `can create Mat after library load`() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        val mat = Mat.zeros(100, 100, CvType.CV_8UC3)
        assertNotNull(mat)
        assertTrue(mat.rows() == 100)
        assertTrue(mat.cols() == 100)
        mat.release()
    }

    @Test
    fun `opencv version is available`() {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        val version = Core.VERSION
        assertNotNull(version)
        assertTrue("Version should start with 4", version.startsWith("4"))
    }
}
