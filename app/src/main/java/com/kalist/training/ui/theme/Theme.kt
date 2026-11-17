package com.kalist.training.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.kalist.training.data.settings.Accent
import com.kalist.training.data.settings.ThemeMode

val isDynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun KalistTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.ORANGE,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (accent == Accent.DYNAMIC && isDynamicColorSupported) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentColorScheme(Color(accent.argb), dark)
    }
    MaterialTheme(colorScheme = colorScheme, typography = KalistTypography, content = content)
}

/** Builds a Material 3 scheme from a single accent color (a light-weight stand-in for a seeded palette). */
fun accentColorScheme(accent: Color, dark: Boolean): ColorScheme {
    val white = Color.White
    val black = Color.Black
    return if (!dark) {
        val neutral = lerp(Color(0xFFFFFBFA), accent, 0.03f)
        lightColorScheme(
            primary = accent,
            onPrimary = white,
            primaryContainer = lerp(accent, white, 0.78f),
            onPrimaryContainer = lerp(accent, black, 0.65f),
            secondary = lerp(accent, Color(0xFF5F5A58), 0.6f),
            onSecondary = white,
            secondaryContainer = lerp(accent, white, 0.86f),
            onSecondaryContainer = lerp(accent, black, 0.7f),
            tertiary = lerp(accent, Color(0xFF3B6E8F), 0.55f),
            onTertiary = white,
            tertiaryContainer = lerp(Color(0xFF3B6E8F), white, 0.82f),
            onTertiaryContainer = lerp(Color(0xFF3B6E8F), black, 0.6f),
            background = neutral,
            onBackground = Color(0xFF201A19),
            surface = neutral,
            onSurface = Color(0xFF201A19),
            surfaceVariant = lerp(accent, Color(0xFFF2E6E3), 0.9f),
            onSurfaceVariant = Color(0xFF524440),
            surfaceContainerLowest = white,
            surfaceContainerLow = lerp(neutral, accent, 0.03f),
            surfaceContainer = lerp(neutral, accent, 0.05f),
            surfaceContainerHigh = lerp(neutral, accent, 0.075f),
            surfaceContainerHighest = lerp(neutral, accent, 0.1f),
            outline = Color(0xFF85736F),
            outlineVariant = Color(0xFFD8C2BD),
        )
    } else {
        val neutral = lerp(Color(0xFF151212), accent, 0.04f)
        val primary = lerp(accent, white, 0.35f)
        darkColorScheme(
            primary = primary,
            onPrimary = lerp(accent, black, 0.7f),
            primaryContainer = lerp(accent, black, 0.45f),
            onPrimaryContainer = lerp(accent, white, 0.8f),
            secondary = lerp(primary, Color(0xFFC9BEBB), 0.6f),
            onSecondary = lerp(accent, black, 0.75f),
            secondaryContainer = lerp(accent, Color(0xFF3A3331), 0.75f),
            onSecondaryContainer = lerp(accent, white, 0.85f),
            tertiary = Color(0xFF9CCBEB),
            onTertiary = Color(0xFF00344F),
            tertiaryContainer = Color(0xFF1E4C68),
            onTertiaryContainer = Color(0xFFCDE5FF),
            background = neutral,
            onBackground = Color(0xFFEDE0DD),
            surface = neutral,
            onSurface = Color(0xFFEDE0DD),
            surfaceVariant = lerp(accent, Color(0xFF3B3331), 0.88f),
            onSurfaceVariant = Color(0xFFD8C2BD),
            surfaceContainerLowest = lerp(neutral, black, 0.4f),
            surfaceContainerLow = lerp(neutral, white, 0.03f),
            surfaceContainer = lerp(neutral, white, 0.06f),
            surfaceContainerHigh = lerp(neutral, white, 0.09f),
            surfaceContainerHighest = lerp(neutral, white, 0.13f),
            outline = Color(0xFFA08C88),
            outlineVariant = Color(0xFF524440),
        )
    }
}
