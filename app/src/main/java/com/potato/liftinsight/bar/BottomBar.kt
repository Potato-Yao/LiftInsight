package com.potato.liftinsight.bar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.potato.liftinsight.R

@Composable
internal fun LiftInsightBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val bottomItems = listOf(
        BottomBarItem(
            label = stringResource(R.string.nav_home),
            icon = { Icon(Icons.Filled.Home, contentDescription = null) }
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_plan),
            icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) }
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_body),
            icon = { Icon(Icons.Filled.FiberManualRecord, contentDescription = null) }
        ),
        BottomBarItem(
            label = stringResource(R.string.nav_settings),
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) }
        )
    )

    NavigationBar {
        bottomItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                icon = item.icon,
                label = { Text(item.label) }
            )
        }
    }
}

private data class BottomBarItem(
    val label: String,
    val icon: @Composable () -> Unit
)
