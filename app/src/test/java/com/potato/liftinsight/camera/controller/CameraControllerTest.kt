package com.potato.liftinsight.camera.controller

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraControllerTest {
    private val controller = CameraController()

    @Test
    fun createVideoOutputFile_usesSharedProjectNamingPattern() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val file = controller.createVideoOutputFile(
            context = context,
            motionId = 17,
            setIndex = 3
        )

        assertTrue(file.parentFile?.exists() == true)
        assertTrue(file.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-17-3\\.mp4")))
    }

    @Test
    fun videoOutputDirectory_prefersMoviesExternalFilesDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val directory = controller.videoOutputDirectory(context)

        assertEquals(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath,
            directory.absolutePath
        )
    }

    @Test
    fun fileProviderAuthority_returnsPackageNameBasedAuthority() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val authority = controller.fileProviderAuthority(context)

        assertEquals("${context.packageName}.fileprovider", authority)
    }
}
