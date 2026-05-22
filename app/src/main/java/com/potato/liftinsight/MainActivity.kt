package com.potato.liftinsight

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.potato.liftinsight.home.HomeRoute
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.settings.data.ThemeStore
import com.potato.liftinsight.ui.theme.LiftInsightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeStore = ThemeStore.from(applicationContext)
        val trainingPlanStore = TrainingPlanStore.from(applicationContext)
        val enableDebugPlanSeed = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        setContent {
            var themeMode by remember(themeStore) {
                mutableStateOf(themeStore.getThemeMode())
            }

            LiftInsightTheme(themeMode = themeMode) {
                HomeRoute(
                    trainingPlanStore = trainingPlanStore,
                    enableDebugPlanSeed = enableDebugPlanSeed,
                    currentThemeMode = themeMode,
                    onThemeModeSelected = { selectedThemeMode ->
                        themeStore.setThemeMode(selectedThemeMode)
                        themeMode = selectedThemeMode
                    }
                )
            }
        }
    }
}
