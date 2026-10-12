package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VozDarkColorScheme = darkColorScheme(
    primary = VozDarkPrimary,
    onPrimary = VozDarkOnPrimary,
    primaryContainer = VozDarkPrimaryContainer,
    onPrimaryContainer = VozDarkOnPrimaryContainer,
    secondary = VozDarkSecondary,
    onSecondary = VozDarkOnSecondary,
    secondaryContainer = VozDarkSecondaryContainer,
    onSecondaryContainer = VozDarkOnSecondaryContainer,
    tertiary = VozDarkTertiary,
    onTertiary = VozDarkOnTertiary,
    background = VozDarkBackground,
    onBackground = VozDarkTextPrimary,
    surface = VozDarkSurface,
    onSurface = VozDarkTextPrimary,
    surfaceVariant = VozDarkSurfaceVariant,
    onSurfaceVariant = VozDarkTextSecondary,
    surfaceContainer = VozDarkSurfaceContainer,
    outline = VozDarkOutline,
    error = VozDarkError,
    errorContainer = VozDarkErrorContainer
)

private val VozLightColorScheme = lightColorScheme(
    primary = VozLightPrimary,
    onPrimary = VozLightOnPrimary,
    primaryContainer = VozLightPrimaryContainer,
    onPrimaryContainer = VozLightOnPrimaryContainer,
    secondary = VozLightSecondary,
    onSecondary = VozLightOnSecondary,
    secondaryContainer = VozLightSecondaryContainer,
    onSecondaryContainer = VozLightOnSecondaryContainer,
    tertiary = VozLightTertiary,
    onTertiary = VozLightOnTertiary,
    background = VozLightBackground,
    onBackground = VozLightTextPrimary,
    surface = VozLightSurface,
    onSurface = VozLightTextPrimary,
    surfaceVariant = VozLightSurfaceVariant,
    onSurfaceVariant = VozLightTextSecondary,
    surfaceContainer = VozLightSurfaceContainer,
    outline = VozLightOutline,
    error = VozLightError,
    errorContainer = VozLightErrorContainer
)

val VozShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun VozTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDarkTheme) VozDarkColorScheme else VozLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VozShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    VozTheme(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        content = content
    )
}
