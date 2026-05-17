package com.potato.liftinsight.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.ui.theme.LiftInsightMotion

data class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val contentDescription: String = label
)

@Composable
internal fun LiftInsightBottomBar(
    items: List<BottomBarItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedTabIndex == index
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.92f,
                animationSpec = tween(
                    durationMillis = LiftInsightMotion.MediumDuration,
                    easing = LiftInsightMotion.EnterEasing
                ),
                label = "bottomBarIconScale"
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                icon = {
                    AnimatedContent(
                        targetState = isSelected,
                        transitionSpec = {
                            (fadeIn(
                                animationSpec = tween(
                                    durationMillis = LiftInsightMotion.MediumDuration,
                                    easing = LiftInsightMotion.EnterEasing
                                )
                            ) + scaleIn(
                                initialScale = 0.9f,
                                animationSpec = tween(
                                    durationMillis = LiftInsightMotion.MediumDuration,
                                    easing = LiftInsightMotion.EnterEasing
                                )
                            )) togetherWith
                                (fadeOut(
                                    animationSpec = tween(
                                        durationMillis = LiftInsightMotion.ShortDuration,
                                        easing = LiftInsightMotion.ExitEasing
                                    )
                                ) + scaleOut(
                                    targetScale = 0.92f,
                                    animationSpec = tween(
                                        durationMillis = LiftInsightMotion.ShortDuration,
                                        easing = LiftInsightMotion.ExitEasing
                                    )
                                ))
                        },
                        label = "bottomBarIcon"
                    ) { selected ->
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.contentDescription,
                            modifier = Modifier.graphicsLayer(
                                scaleX = iconScale,
                                scaleY = iconScale
                            )
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

