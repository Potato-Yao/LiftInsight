package com.potato.liftinsight.camera

import androidx.annotation.StringRes
import com.potato.liftinsight.R

enum class CameraCaptureMode(
    val storageValue: String,
    @param:StringRes val labelResId: Int
) {
    Native(
        storageValue = "native",
        labelResId = R.string.settings_camera_mode_native
    ),
    External(
        storageValue = "external",
        labelResId = R.string.settings_camera_mode_external
    );

    companion object {
        val Default: CameraCaptureMode = External

        fun fromStorageValue(value: String?): CameraCaptureMode {
            return entries.firstOrNull { mode -> mode.storageValue == value }
                ?: Default
        }
    }
}
