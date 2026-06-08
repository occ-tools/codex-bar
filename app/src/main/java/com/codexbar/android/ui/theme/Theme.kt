package com.codexbar.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AppBlue = Color(0xFF256DFF)
private val AppTeal = Color(0xFF00A7A7)
private val AppInk = Color(0xFF171A20)

private val LightColorScheme = lightColorScheme(
    primary = AppBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF082E68),
    secondary = Color(0xFF596375),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EAF4),
    onSecondaryContainer = Color(0xFF151B25),
    tertiary = AppTeal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9F4F1),
    onTertiaryContainer = Color(0xFF003D3C),
    background = Color(0xFFF5F7FB),
    onBackground = AppInk,
    surface = Color(0xFFFFFFFF),
    onSurface = AppInk,
    surfaceVariant = Color(0xFFE8EDF6),
    onSurfaceVariant = Color(0xFF5D6675),
    error = Color(0xFFD33B32),
    errorContainer = Color(0xFFFFE1DD),
    onErrorContainer = Color(0xFF5C0905),
    outline = Color(0xFFB9C2D0),
    outlineVariant = Color(0xFFDCE2EC)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9FBDFF),
    onPrimary = Color(0xFF003A87),
    primaryContainer = Color(0xFF144EBA),
    onPrimaryContainer = Color(0xFFE7EEFF),
    secondary = Color(0xFFC3CAD8),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3A4353),
    onSecondaryContainer = Color(0xFFE8EDF5),
    tertiary = Color(0xFF7DE0DC),
    onTertiary = Color(0xFF003736),
    tertiaryContainer = Color(0xFF00504F),
    onTertiaryContainer = Color(0xFFA5F4F0),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE5E8EF),
    surface = Color(0xFF1A1D24),
    onSurface = Color(0xFFE5E8EF),
    surfaceVariant = Color(0xFF343A46),
    onSurfaceVariant = Color(0xFFC2C8D3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8992A1),
    outlineVariant = Color(0xFF3F4754)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun CodexBarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
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
        shapes = AppShapes,
        content = content
    )
}
