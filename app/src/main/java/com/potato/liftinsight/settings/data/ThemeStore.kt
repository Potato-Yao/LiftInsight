package com.potato.liftinsight.settings.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.potato.liftinsight.ui.theme.AppThemeMode

class ThemeStore private constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun getThemeMode(): AppThemeMode {
        return AppThemeMode.fromStorageValue(
            sharedPreferences.getString(KEY_THEME_MODE, null)
        )
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        sharedPreferences.edit {
            putString(KEY_THEME_MODE, themeMode.storageValue)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "liftinsight.settings"
        private const val KEY_THEME_MODE = "theme_mode"

        fun from(context: Context): ThemeStore {
            return ThemeStore(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}


