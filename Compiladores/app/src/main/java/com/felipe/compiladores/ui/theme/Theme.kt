package com.felipe.compiladores.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GameColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF042F2E),
    secondary = Secondary,
    onSecondary = Color(0xFF3B0A24),
    tertiary = Tertiary,
    onTertiary = Color(0xFF3A2503),
    background = Background,
    onBackground = TextMain,
    surface = Surface,
    onSurface = TextMain,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextDim,
    outline = Outline,
    error = Danger,
    onError = Color(0xFF3B0A0A),
)

/** O jogo usa sempre o tema escuro "terminal neon", para ter a mesma cara em qualquer aparelho. */
@Composable
fun CompiladoresTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GameColors, typography = Typography, content = content)
}
