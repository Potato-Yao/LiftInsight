package com.potato.liftinsight

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.potato.liftinsight.home.HomeRoute
import com.potato.liftinsight.plan.data.TrainingPlanStore
import com.potato.liftinsight.ui.theme.LiftInsightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LiftInsightTheme {
                HomeRoute(
                    trainingPlanStore = TrainingPlanStore.from(applicationContext),
                    enableDebugPlanSeed = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                )
            }
        }
    }
}
