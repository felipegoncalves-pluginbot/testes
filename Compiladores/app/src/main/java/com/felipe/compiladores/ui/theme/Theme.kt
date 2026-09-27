package com.felipe.compiladores.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Tema do jogo: a paleta e o tamanho do texto vêm das configurações em [ThemeState]. */
@Composable
fun CompiladoresTheme(content: @Composable () -> Unit) {
    val p = ThemeState.palette
    val scheme = if (p.isLight) {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onAccent, secondary = p.secondary, onSecondary = p.onAccent,
            tertiary = p.tertiary, onTertiary = p.onAccent, background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim,
            outline = p.outline, error = p.danger, onError = p.onAccent,
        )
    } else {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onAccent, secondary = p.secondary, onSecondary = p.onAccent,
            tertiary = p.tertiary, onTertiary = p.onAccent, background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim,
            outline = p.outline, error = p.danger, onError = p.onAccent,
        )
    }
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * ThemeState.settings.textScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
