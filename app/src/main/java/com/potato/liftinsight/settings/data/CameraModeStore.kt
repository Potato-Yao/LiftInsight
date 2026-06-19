package com.potato.liftinsight.settings.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.potato.liftinsight.camera.CameraCaptureMode

class CameraModeStore private constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun getCameraCaptureMode(): CameraCaptureMode {
        return CameraCaptureMode.fromStorageValue(
            sharedPreferences.getString(KEY_CAMERA_MODE, null)
        )
    }

    fun setCameraCaptureMode(mode: CameraCaptureMode) {
        sharedPreferences.edit {
            putString(KEY_CAMERA_MODE, mode.storageValue)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "liftinsight.settings"
        private const val KEY_CAMERA_MODE = "camera_capture_mode"

        fun from(context: Context): CameraModeStore {
            return CameraModeStore(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
