package com.potato.liftinsight.settings

import androidx.annotation.StringRes
import com.potato.liftinsight.R

enum class AppLanguageMode(
    val storageValue: String,
    val languageTag: String?,
    @param:StringRes val labelResId: Int
) {
    FollowSystem(
        storageValue = "system",
        languageTag = null,
        labelResId = R.string.settings_language_follow_system
    ),
    English(
        storageValue = "en",
        languageTag = "en",
        labelResId = R.string.settings_language_english
    ),
    Chinese(
        storageValue = "zh",
        languageTag = "zh",
        labelResId = R.string.settings_language_chinese
    );

    companion object {
        fun fromStorageValue(value: String?): AppLanguageMode {
            return entries.firstOrNull { it.storageValue == value } ?: FollowSystem
        }
    }
}
