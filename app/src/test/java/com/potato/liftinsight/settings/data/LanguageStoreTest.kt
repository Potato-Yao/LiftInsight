package com.potato.liftinsight.settings.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.settings.AppLanguageMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageStoreTest {
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
    fun getLanguageMode_returnsFollowSystemWhenNothingStored() {
        val store = LanguageStore.from(context)

        assertEquals(AppLanguageMode.FollowSystem, store.getLanguageMode())
    }

    @Test
    fun setLanguageMode_persistsSelectedMode() {
        val store = LanguageStore.from(context)

        store.setLanguageMode(AppLanguageMode.Chinese)

        val reloadedStore = LanguageStore.from(context)

        assertEquals(AppLanguageMode.Chinese, reloadedStore.getLanguageMode())
    }

    @Test
    fun getLanguageMode_fallsBackWhenStoredValueIsUnknown() {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(KEY_LANGUAGE_MODE, "unexpected")
            .commit()

        val store = LanguageStore.from(context)

        assertEquals(AppLanguageMode.FollowSystem, store.getLanguageMode())
    }

    @Test
    fun appLanguageMode_storageValuesAreConsistent() {
        assertEquals("system", AppLanguageMode.FollowSystem.storageValue)
        assertEquals("en", AppLanguageMode.English.storageValue)
        assertEquals("zh", AppLanguageMode.Chinese.storageValue)
    }

    @Test
    fun appLanguageMode_languageTagsAreConsistent() {
        assertEquals(null, AppLanguageMode.FollowSystem.languageTag)
        assertEquals("en", AppLanguageMode.English.languageTag)
        assertEquals("zh", AppLanguageMode.Chinese.languageTag)
    }

    companion object {
        private const val PREFERENCES_NAME = "liftinsight.settings"
        private const val KEY_LANGUAGE_MODE = "language_mode"
    }
}
