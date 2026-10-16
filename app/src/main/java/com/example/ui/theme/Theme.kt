package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.preferences.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D8FF),
    onPrimary = Color(0xFF003648),
    primaryContainer = Color(0xFF004D65),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3535),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB9C3EA),
    onTertiary = Color(0xFF232D4D),
    tertiaryContainer = Color(0xFF3A4465),
    onTertiaryContainer = Color(0xFFD9E2FF),
    background = Color(0xFF111415),
    surface = Color(0xFF111415),
    surfaceVariant = Color(0xFF222629),
    onSurface = Color(0xFFE1E3E4),
    onSurfaceVariant = Color(0xFFC2C7CC),
    outline = Color(0xFF8C9197)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006686),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCE9FF),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary = Color(0xFF4A6262),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F20),
    tertiary = Color(0xFF515C7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E2FF),
    onTertiaryContainer = Color(0xFF0D1937),
    background = Color(0xFFFAF9F6),
    surface = Color(0xFFFAF9F6),
    surfaceVariant = Color(0xFFE9EDF0),
    onSurface = Color(0xFF191C1E),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787D)
)

@Composable
fun VOZTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
