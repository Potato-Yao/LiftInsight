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
import com.potato.liftinsight.camera.CameraCaptureMode
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.home.HomeRoute
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.record.controller.TrainingHistoryController
import com.potato.liftinsight.settings.data.CameraModeStore
import com.potato.liftinsight.settings.data.ThemeStore
import com.potato.liftinsight.settings.data.VideoCleanupStore
import com.potato.liftinsight.ui.theme.LiftInsightTheme
import com.potato.liftinsight.video.VideoProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("MainActivity", "onCreate: starting application")
        enableEdgeToEdge()

        val themeStore = ThemeStore.from(applicationContext)
        val videoCleanupStore = VideoCleanupStore.from(applicationContext)
        val cameraModeStore = CameraModeStore.from(applicationContext)
        val trainingPlanStore = TrainingPlanStore.from(applicationContext)
        val videoProcessor = VideoProcessor.from(applicationContext)
        val enableDebugPlanSeed = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        android.util.Log.d("MainActivity", "onCreate: stores and processor initialized")

        val cleanupThresholdDays = videoCleanupStore.getCleanupThresholdDays()
        val controller = TrainingHistoryController(
            trainingPlanStore = trainingPlanStore,
            videoProcessor = videoProcessor,
            logger = AndroidAppLogger
        )

        CoroutineScope(Dispatchers.IO).launch {
            controller.cleanupOldRawVideos(cleanupThresholdDays)
        }

        setContent {
            var themeMode by remember(themeStore) {
                mutableStateOf(themeStore.getThemeMode())
            }

            var cleanupThresholdDaysState by remember(videoCleanupStore) {
                mutableStateOf(videoCleanupStore.getCleanupThresholdDays())
            }

            var cameraCaptureMode by remember(cameraModeStore) {
                mutableStateOf(cameraModeStore.getCameraCaptureMode())
            }

            LiftInsightTheme(themeMode = themeMode) {
                HomeRoute(
                    trainingPlanStore = trainingPlanStore,
                    videoProcessor = videoProcessor,
                    enableDebugPlanSeed = enableDebugPlanSeed,
                    currentThemeMode = themeMode,
                    onThemeModeSelected = { selectedThemeMode ->
                        themeStore.setThemeMode(selectedThemeMode)
                        themeMode = selectedThemeMode
                    },
                    currentCleanupThresholdDays = cleanupThresholdDaysState,
                    onCleanupThresholdDaysChanged = { days ->
                        videoCleanupStore.setCleanupThresholdDays(days)
                        cleanupThresholdDaysState = days
                    },
                    currentCameraCaptureMode = cameraCaptureMode,
                    onCameraCaptureModeChanged = { mode ->
                        cameraModeStore.setCameraCaptureMode(mode)
                        cameraCaptureMode = mode
                    }
                )
            }
        }
        android.util.Log.d("MainActivity", "onCreate: setContent completed")
    }
}
