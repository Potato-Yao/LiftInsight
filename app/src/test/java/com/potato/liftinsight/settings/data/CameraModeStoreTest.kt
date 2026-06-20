package com.potato.liftinsight.settings.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.camera.CameraCaptureMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraModeStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun getCameraCaptureMode_returnsExternalWhenNothingStored() {
        val store = CameraModeStore.from(context)
        assertEquals(CameraCaptureMode.Default, store.getCameraCaptureMode())
    }

    @Test
    fun setCameraCaptureMode_persistsSelectedMode() {
        val store = CameraModeStore.from(context)
        store.setCameraCaptureMode(CameraCaptureMode.External)

        val reloadedStore = CameraModeStore.from(context)
        assertEquals(CameraCaptureMode.External, reloadedStore.getCameraCaptureMode())
    }

    @Test
    fun getCameraCaptureMode_fallsBackToExternalWhenStoredValueIsUnknown() {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString("camera_capture_mode", "unexpected")
            .commit()

        val store = CameraModeStore.from(context)
        assertEquals(CameraCaptureMode.Default, store.getCameraCaptureMode())
    }

    @Test
    fun cameraCaptureMode_defaultIsExternal() {
        assertEquals(CameraCaptureMode.External, CameraCaptureMode.Default)
        assertEquals(CameraCaptureMode.Default, CameraCaptureMode.fromStorageValue(null))
        assertEquals(CameraCaptureMode.Default, CameraCaptureMode.fromStorageValue("unexpected"))
    }

    @Test
    fun cameraCaptureMode_fromStorageValue_returnsExternal() {
        assertEquals(CameraCaptureMode.External, CameraCaptureMode.fromStorageValue("external"))
    }

    @Test
    fun cameraCaptureMode_storageValues_areConsistent() {
        assertEquals("native", CameraCaptureMode.Native.storageValue)
        assertEquals("external", CameraCaptureMode.External.storageValue)
    }

    companion object {
        private const val PREFERENCES_NAME = "liftinsight.settings"
    }
}
