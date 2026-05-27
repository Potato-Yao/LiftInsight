package com.potato.liftinsight.settings

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.potato.liftinsight.BuildConfig
import com.potato.liftinsight.R
import com.potato.liftinsight.common.MetricCard
import com.potato.liftinsight.settings.route.SettingsRoute
import com.potato.liftinsight.ui.theme.AppThemeMode
import com.potato.liftinsight.ui.theme.LiftInsightMotion

@Composable
internal fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var route by rememberSaveable { mutableStateOf(SettingsRoute.Overview) }

    BackHandler(enabled = route != SettingsRoute.Overview) {
        route = SettingsRoute.Overview
    }

    when (route) {
        SettingsRoute.Overview -> SettingsOverviewScreen(
            currentThemeMode = currentThemeMode,
            onOpenThemeSettings = {
                route = SettingsRoute.Theme
            },
            modifier = modifier
        )

        SettingsRoute.Theme -> ThemeSettingsScreen(
            currentThemeMode = currentThemeMode,
            onThemeModeSelected = onThemeModeSelected,
            onBack = {
                route = SettingsRoute.Overview
            },
            modifier = modifier
        )
    }
}

@Composable
private fun SettingsOverviewScreen(
    currentThemeMode: AppThemeMode,
    onOpenThemeSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    var showAboutPanel by remember { mutableStateOf(false) }

    if (showAboutPanel) {
        AboutPanel(
            onDismiss = { showAboutPanel = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 12.dp,
            end = 24.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "settingsTitle") {
            AnimatedVisibility(
                visible = showContent,
                enter = settingsSectionEnter(delayMillis = 0),
                exit = ExitTransition.None
            ) {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item(key = "settingsTheme") {
            AnimatedVisibility(
                visible = showContent,
                enter = settingsSectionEnter(delayMillis = 50),
                exit = ExitTransition.None
            ) {
                MetricCard(
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(
                        R.string.settings_theme_summary,
                        stringResource(currentThemeMode.labelResId)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenThemeSettings,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        SettingsTrailingArrow()
                    }
                ) {}
            }
        }

        item(key = "settingsAbout") {
            AnimatedVisibility(
                visible = showContent,
                enter = settingsSectionEnter(delayMillis = 100),
                exit = ExitTransition.None
            ) {
                MetricCard(
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showAboutPanel = true },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        SettingsTrailingArrow()
                    }
                ) {}
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThemeSettingsScreen(
    currentThemeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings_theme))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 12.dp,
                end = 24.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "themeDescription") {
                AnimatedVisibility(
                    visible = showContent,
                    enter = settingsSectionEnter(delayMillis = 0),
                    exit = ExitTransition.None
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.settings_theme_page_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = stringResource(R.string.settings_theme_page_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = AppThemeMode.FollowSystem.storageValue) {
                AnimatedVisibility(
                    visible = showContent,
                    enter = settingsSectionEnter(delayMillis = 50),
                    exit = ExitTransition.None
                ) {
                    ThemeModeCard(
                        themeMode = AppThemeMode.FollowSystem,
                        subtitle = stringResource(R.string.settings_theme_follow_system_subtitle),
                        icon = Icons.Rounded.BrightnessAuto,
                        isSelected = currentThemeMode == AppThemeMode.FollowSystem,
                        onClick = {
                            onThemeModeSelected(AppThemeMode.FollowSystem)
                        }
                    )
                }
            }

            item(key = AppThemeMode.Light.storageValue) {
                AnimatedVisibility(
                    visible = showContent,
                    enter = settingsSectionEnter(delayMillis = 100),
                    exit = ExitTransition.None
                ) {
                    ThemeModeCard(
                        themeMode = AppThemeMode.Light,
                        subtitle = stringResource(R.string.settings_theme_light_subtitle),
                        icon = Icons.Rounded.LightMode,
                        isSelected = currentThemeMode == AppThemeMode.Light,
                        onClick = {
                            onThemeModeSelected(AppThemeMode.Light)
                        }
                    )
                }
            }

            item(key = AppThemeMode.Dark.storageValue) {
                AnimatedVisibility(
                    visible = showContent,
                    enter = settingsSectionEnter(delayMillis = 150),
                    exit = ExitTransition.None
                ) {
                    ThemeModeCard(
                        themeMode = AppThemeMode.Dark,
                        subtitle = stringResource(R.string.settings_theme_dark_subtitle),
                        icon = Icons.Rounded.DarkMode,
                        isSelected = currentThemeMode == AppThemeMode.Dark,
                        onClick = {
                            onThemeModeSelected(AppThemeMode.Dark)
                        }
                    )
                }
            }
        }
    }
}

private fun settingsSectionEnter(delayMillis: Int): EnterTransition {
    return fadeIn(
        animationSpec = tween(
            durationMillis = LiftInsightMotion.MediumDuration,
            delayMillis = delayMillis,
            easing = LiftInsightMotion.EnterEasing
        )
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = LiftInsightMotion.LongDuration,
            delayMillis = delayMillis,
            easing = LiftInsightMotion.EnterEasing
        ),
        initialOffsetY = { fullHeight -> fullHeight / 12 }
    )
}

@Composable
private fun ThemeModeCard(
    themeMode: AppThemeMode,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MetricCard(
        title = stringResource(themeMode.labelResId),
        subtitle = subtitle,
        modifier = modifier.fillMaxWidth(),
        highlighted = isSelected,
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        }
    ) {}
}

@Composable
private fun SettingsTrailingArrow() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutPanel(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.about_panel_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.about_version_label),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isDebuggable) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.about_build_time_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = BuildConfig.BUILD_TIME,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

