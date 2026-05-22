package com.potato.liftinsight.ui.theme

import androidx.annotation.StringRes
import com.potato.liftinsight.R

enum class AppThemeMode(
    val storageValue: String,
    @param:StringRes val labelResId: Int
) {
    FollowSystem(
        storageValue = "system",
        labelResId = R.string.settings_theme_follow_system
    ),
    Light(
        storageValue = "light",
        labelResId = R.string.settings_theme_light
    ),
    Dark(
        storageValue = "dark",
        labelResId = R.string.settings_theme_dark
    );

    fun resolveDarkTheme(isSystemDarkTheme: Boolean): Boolean {
        return when (this) {
            FollowSystem -> isSystemDarkTheme
            Light -> false
            Dark -> true
        }
    }

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode {
            return entries.firstOrNull { themeMode -> themeMode.storageValue == value }
                ?: FollowSystem
        }
    }
}


