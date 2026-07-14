package com.heartguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.heartguard.utils.SettingsManager

private val HeartGuardLightColorScheme = lightColorScheme(
    background = BackgroundWarm,
    surface = SurfaceWarm,
    primary = PrimarySoftGreen,
    secondary = AccentOrange,
    tertiary = AccentBlue,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onTertiary = TextPrimary,
)

@Composable
fun HeartGuardTheme(
    fontSize: String = SettingsManager.DEFAULT_FONT_SIZE,
    typography: Typography = HeartGuardTypography,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * SettingsManager.fontScale(fontSize),
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = HeartGuardLightColorScheme,
            typography = typography,
            content = content,
        )
    }
}
