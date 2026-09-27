package com.felipe.compiladores

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.ProfileStore
import com.felipe.compiladores.ui.App
import com.felipe.compiladores.ui.components.LocalBackHandler
import com.felipe.compiladores.ui.theme.Background
import com.felipe.compiladores.ui.theme.CompiladoresTheme
import com.felipe.compiladores.ui.theme.ThemeState

/** Guarda o perfil do jogador em SharedPreferences. */
private class PrefsStore(context: Context) : ProfileStore {
    private val prefs = context.getSharedPreferences("parser_quest", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString("profile", null)
    override fun save(text: String) {
        prefs.edit().putString("profile", text).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = GameState(PrefsStore(applicationContext))
        // Aplica o tema salvo antes do primeiro quadro, para não piscar outra cor.
        ThemeState.settings = game.profile.settings
        setContent {
            val isLight = ThemeState.palette.isLight
            DisposableEffect(isLight) {
                val style = if (isLight) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            CompiladoresTheme {
                CompositionLocalProvider(LocalBackHandler provides { enabled, onBack -> BackHandler(enabled, onBack) }) {
                    Box(Modifier.fillMaxSize().background(Background).safeDrawingPadding()) {
                        App(game)
                    }
                }
            }
        }
    }
}
