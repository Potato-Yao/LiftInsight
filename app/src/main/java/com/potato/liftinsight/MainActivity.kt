package com.potato.liftinsight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.potato.liftinsight.route.LiftInsightHomeRoute
import com.potato.liftinsight.ui.theme.LiftInsightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LiftInsightTheme {
                LiftInsightHomeRoute()
            }
        }
    }
}
