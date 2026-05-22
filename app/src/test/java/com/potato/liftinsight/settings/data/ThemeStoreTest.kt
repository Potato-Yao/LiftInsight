package com.potato.liftinsight.settings.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.ui.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeStoreTest {
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
    fun getThemeMode_returnsFollowSystemWhenNothingStored() {
        val store = ThemeStore.from(context)

        assertEquals(AppThemeMode.FollowSystem, store.getThemeMode())
    }

    @Test
    fun setThemeMode_persistsSelectedMode() {
        val store = ThemeStore.from(context)

        store.setThemeMode(AppThemeMode.Dark)

        val reloadedStore = ThemeStore.from(context)

        assertEquals(AppThemeMode.Dark, reloadedStore.getThemeMode())
    }

    @Test
    fun getThemeMode_fallsBackWhenStoredValueIsUnknown() {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(KEY_THEME_MODE, "unexpected")
            .commit()

        val store = ThemeStore.from(context)

        assertEquals(AppThemeMode.FollowSystem, store.getThemeMode())
    }

    companion object {
        private const val PREFERENCES_NAME = "liftinsight.settings"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

