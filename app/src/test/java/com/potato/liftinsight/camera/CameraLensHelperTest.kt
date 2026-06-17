package com.potato.liftinsight.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowCameraCharacteristics
import org.robolectric.shadows.ShadowCameraManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CameraLensHelperTest {

    // --- classifyWideCamera pure-function tests ---

    @Test
    fun `classifyWideCamera returns null when fewer than 2 cameras`() {
        val single = listOf(CameraFocalInfo("0", 5.0f))
        assertNull(CameraLensHelper.classifyWideCamera(single))
    }

    @Test
    fun `classifyWideCamera returns null when empty list`() {
        assertNull(CameraLensHelper.classifyWideCamera(emptyList()))
    }

    @Test
    fun `classifyWideCamera returns null when focal lengths are similar`() {
        val cameras = listOf(
            CameraFocalInfo("0", 4.5f),
            CameraFocalInfo("1", 5.0f)
        )
        assertNull(CameraLensHelper.classifyWideCamera(cameras))
    }

    @Test
    fun `classifyWideCamera returns wide camera when significant focal difference`() {
        val cameras = listOf(
            CameraFocalInfo("main", 5.0f),
            CameraFocalInfo("wide", 2.0f)
        )
        assertEquals("wide", CameraLensHelper.classifyWideCamera(cameras))
    }

    @Test
    fun `classifyWideCamera picks shortest focal length among 3+ back cameras`() {
        val cameras = listOf(
            CameraFocalInfo("main", 5.0f),
            CameraFocalInfo("tele", 8.0f),
            CameraFocalInfo("ultrawide", 1.5f)
        )
        assertEquals("ultrawide", CameraLensHelper.classifyWideCamera(cameras))
    }

    @Test
    fun `classifyWideCamera respects custom threshold`() {
        val cameras = listOf(
            CameraFocalInfo("a", 4.0f),
            CameraFocalInfo("b", 3.5f)
        )
        assertNull(CameraLensHelper.classifyWideCamera(cameras, threshold = 0.9f))
        assertEquals("b", CameraLensHelper.classifyWideCamera(cameras, threshold = 1.0f))
    }

    @Test
    fun `classifyWideCamera returns null when all focal lengths equal`() {
        val cameras = listOf(
            CameraFocalInfo("a", 5.0f),
            CameraFocalInfo("b", 5.0f)
        )
        assertNull(CameraLensHelper.classifyWideCamera(cameras))
    }

    // --- findWideCameraId tests ---

    @Test
    fun `findWideCameraId detects wide lens from CameraManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        val charsMain = createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK,
            floatArrayOf(5.0f)
        )
        val charsWide = createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK,
            floatArrayOf(2.0f)
        )
        shadow.addCamera("0", charsMain)
        shadow.addCamera("1", charsWide)

        val result = CameraLensHelper.findWideCameraId(cameraManager)
        assertEquals("1", result)
    }

    @Test
    fun `findWideCameraId returns null when only one back camera`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        shadow.addCamera("0", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK,
            floatArrayOf(5.0f)
        ))
        shadow.addCamera("1", createCharacteristics(
            CameraCharacteristics.LENS_FACING_FRONT,
            floatArrayOf(3.0f)
        ))

        val result = CameraLensHelper.findWideCameraId(cameraManager)
        assertNull(result)
    }

    @Test
    fun `findWideCameraId returns null with similar focal lengths`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        shadow.addCamera("0", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(4.5f)
        ))
        shadow.addCamera("1", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(5.0f)
        ))

        val result = CameraLensHelper.findWideCameraId(cameraManager)
        assertNull(result)
    }

    @Test
    fun `findWideCameraId skips cameras without focal length data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        // Camera 0 has focal length, camera 1 doesn't have it set
        val chars0 = createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(5.0f)
        )
        val chars1 = createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, null
        )
        shadow.addCamera("0", chars0)
        shadow.addCamera("1", chars1)

        val result = CameraLensHelper.findWideCameraId(cameraManager)
        assertNull(result) // only one valid back camera with focal data
    }

    // --- availableLenses tests ---

    @Test
    fun `availableLenses returns back and front from CameraManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        shadow.addCamera("0", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(4.0f)
        ))
        shadow.addCamera("1", createCharacteristics(
            CameraCharacteristics.LENS_FACING_FRONT, floatArrayOf(2.0f)
        ))

        val lenses = CameraLensHelper.availableLenses(context)
        assertEquals(setOf(CameraLens.BACK, CameraLens.FRONT), lenses)
    }

    @Test
    fun `availableLenses includes wide when back cameras have wide lens`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        shadow.addCamera("0", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(5.0f)
        ))
        shadow.addCamera("1", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(1.8f)
        ))
        shadow.addCamera("2", createCharacteristics(
            CameraCharacteristics.LENS_FACING_FRONT, floatArrayOf(2.0f)
        ))

        val lenses = CameraLensHelper.availableLenses(context)
        assertEquals(setOf(CameraLens.BACK, CameraLens.FRONT, CameraLens.WIDE), lenses)
    }

    @Test
    fun `availableLenses returns empty when no cameras`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // No cameras added to shadow — cameraManager.cameraIdList is empty
        val lenses = CameraLensHelper.availableLenses(context)
        assertTrue(lenses.isEmpty())
    }

    // --- hasWideCamera tests ---

    @Test
    fun `hasWideCamera returns true when wide back camera exists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        shadow.addCamera("0", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(5.0f)
        ))
        shadow.addCamera("1", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(1.2f)
        ))

        assertTrue(CameraLensHelper.hasWideCamera(context))
    }

    @Test
    fun `hasWideCamera returns false when only one back camera`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val shadow = Shadows.shadowOf(cameraManager)

        shadow.addCamera("0", createCharacteristics(
            CameraCharacteristics.LENS_FACING_BACK, floatArrayOf(4.0f)
        ))
        shadow.addCamera("1", createCharacteristics(
            CameraCharacteristics.LENS_FACING_FRONT, floatArrayOf(2.0f)
        ))

        val lenses = CameraLensHelper.availableLenses(context)
        assertTrue(CameraLens.WIDE !in lenses)
    }

    // --- createCameraSelector tests ---

    @Test
    fun `createCameraSelector for BACK returns default back camera`() {
        val fakeProvider = createFakeProvider()
        val selector = CameraLensHelper.createCameraSelector(CameraLens.BACK, fakeProvider)
        assertNotNull(selector)
        // DEFAULT_BACK_CAMERA is a static singleton
        assertEquals(CameraSelector.DEFAULT_BACK_CAMERA, selector)
    }

    @Test
    fun `createCameraSelector for FRONT returns default front camera`() {
        val fakeProvider = createFakeProvider()
        val selector = CameraLensHelper.createCameraSelector(CameraLens.FRONT, fakeProvider)
        assertNotNull(selector)
        assertEquals(CameraSelector.DEFAULT_FRONT_CAMERA, selector)
    }

    @Test
    fun `createCameraSelector for WIDE returns non-null selector`() {
        // Even without a real provider, WIDE falls back to DEFAULT_BACK_CAMERA
        val fakeProvider = createFakeProvider()
        val selector = CameraLensHelper.createCameraSelector(CameraLens.WIDE, fakeProvider)
        assertNotNull(selector)
    }

    // --- Constant test ---

    @Test
    fun `wideFocalRatioThreshold constant is 0_75`() {
        assertEquals(0.75f, CameraLensHelper.WIDE_FOCAL_RATIO_THRESHOLD)
    }

    // --- CameraFocalInfo data class test ---

    @Test
    fun `cameraFocalInfo stores cameraId and focalLength`() {
        val info = CameraFocalInfo("cam0", 4.2f)
        assertEquals("cam0", info.cameraId)
        assertEquals(4.2f, info.shortestFocalLength)
    }

    @Test
    fun `cameraFocalInfo equals works correctly`() {
        val a = CameraFocalInfo("0", 5.0f)
        val b = CameraFocalInfo("0", 5.0f)
        val c = CameraFocalInfo("1", 5.0f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    // --- CameraLens enum tests ---

    @Test
    fun `cameraLens has back front wide values`() {
        assertEquals(3, CameraLens.entries.size)
        assertTrue(CameraLens.BACK.ordinal < CameraLens.FRONT.ordinal)
    }

    @Test
    fun `cameraLens back refers to valid string resource`() {
        assertTrue(CameraLens.BACK.labelRes != 0)
        assertTrue(CameraLens.FRONT.labelRes != 0)
        assertTrue(CameraLens.WIDE.labelRes != 0)
    }

    // -------- helpers --------

    companion object {
        /**
         * Creates an instrumented [CameraCharacteristics] with the given
         * lens facing and optional focal lengths set via Robolectric shadow.
         */
        private fun createCharacteristics(
            lensFacing: Int,
            focalLengths: FloatArray?
        ): CameraCharacteristics {
            val chars = ShadowCameraCharacteristics.newCameraCharacteristics()
            val shadow = Shadows.shadowOf(chars)
            shadow.set(
                CameraCharacteristics.LENS_FACING,
                lensFacing
            )
            if (focalLengths != null) {
                shadow.set(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
                    focalLengths
                )
            }
            return chars
        }

        /**
         * Creates a minimal [ProcessCameraProvider] fake for selector tests.
         * Uses Robolectric's shadowed instance since CameraSelector constants
         * don't need a real provider.
         */
        private fun createFakeProvider(): ProcessCameraProvider {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val future = ProcessCameraProvider.getInstance(context)
            return future.get()
        }
    }
}
