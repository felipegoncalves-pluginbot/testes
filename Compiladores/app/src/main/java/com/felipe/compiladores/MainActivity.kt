package com.felipe.compiladores

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.ProfileStore
import com.felipe.compiladores.ui.App
import com.felipe.compiladores.ui.components.LocalBackHandler
import com.felipe.compiladores.ui.theme.Background
import com.felipe.compiladores.ui.theme.CompiladoresTheme

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
        enableEdgeToEdge()
        val game = GameState(PrefsStore(applicationContext))
        setContent {
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
