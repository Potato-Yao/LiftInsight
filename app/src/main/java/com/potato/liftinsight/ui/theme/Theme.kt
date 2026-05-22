package com.potato.liftinsight.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ClockBlueDark,
    onPrimary = ClockBackgroundDark,
    primaryContainer = ClockBlueContainerDark,
    onPrimaryContainer = ClockBlueDark,
    secondary = ClockAccentDark,
    secondaryContainer = ClockAccentContainerDark,
    tertiary = ClockHighlightDark,
    background = ClockBackgroundDark,
    surface = ClockSurfaceDark,
    surfaceContainer = Color(0xFF181C24),
    surfaceContainerHigh = Color(0xFF202530),
    surfaceContainerHighest = Color(0xFF2A3040),
    onSurface = Color(0xFFE4E8F0),
    onSurfaceVariant = Color(0xFFC3C8D4),
    outline = Color(0xFF87909E)
)

private val LightColorScheme = lightColorScheme(
    primary = ClockBlueLight,
    onPrimary = Color.White,
    primaryContainer = ClockBlueContainerLight,
    onPrimaryContainer = Color(0xFF112A4E),
    secondary = ClockAccentLight,
    secondaryContainer = ClockAccentContainerLight,
    tertiary = ClockHighlightLight,
    background = ClockBackgroundLight,
    surface = ClockSurfaceLight,
    surfaceContainer = Color(0xFFF0F3F9),
    surfaceContainerHigh = Color(0xFFE8ECF4),
    surfaceContainerHighest = Color(0xFFDDE3EE),
    onSurface = Color(0xFF171C24),
    onSurfaceVariant = Color(0xFF5E6574),
    outline = Color(0xFF757C88)
)

@Composable
fun LiftInsightTheme(
    themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}