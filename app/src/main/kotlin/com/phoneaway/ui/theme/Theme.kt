package com.phoneaway.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Dusk = Color(0xFF6C7CE0)
private val DuskLight = Color(0xFFAFB8F5)
private val Moss = Color(0xFF4CAF89)
private val Ember = Color(0xFFE0725C)
private val InkDark = Color(0xFF12131A)
private val InkSurface = Color(0xFF1C1E28)

private val DarkColors = darkColorScheme(
    primary = DuskLight,
    onPrimary = Color(0xFF1B2160),
    primaryContainer = Color(0xFF3B449C),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Moss,
    onSecondary = Color(0xFF00382A),
    tertiary = Ember,
    background = InkDark,
    onBackground = Color(0xFFE6E7EF),
    surface = InkSurface,
    onSurface = Color(0xFFE6E7EF),
    surfaceVariant = Color(0xFF2A2D3A),
    onSurfaceVariant = Color(0xFFB9BCCC),
    error = Color(0xFFFFB4A9),
)

private val LightColors = lightColorScheme(
    primary = Dusk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF1B2160),
    secondary = Color(0xFF2E7D62),
    onSecondary = Color.White,
    tertiary = Color(0xFFB4543F),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1A1B22),
    surface = Color.White,
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE3E4F0),
    onSurfaceVariant = Color(0xFF474A5A),
)

@Composable
fun PhoneAwayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
