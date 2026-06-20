package com.potato.liftinsight.settings.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.potato.liftinsight.settings.AppLanguageMode

class LanguageStore private constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun getLanguageMode(): AppLanguageMode {
        return AppLanguageMode.fromStorageValue(
            sharedPreferences.getString(KEY_LANGUAGE_MODE, null)
        )
    }

    fun setLanguageMode(languageMode: AppLanguageMode) {
        sharedPreferences.edit {
            putString(KEY_LANGUAGE_MODE, languageMode.storageValue)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "liftinsight.settings"
        private const val KEY_LANGUAGE_MODE = "language_mode"

        fun from(context: Context): LanguageStore {
            return LanguageStore(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
