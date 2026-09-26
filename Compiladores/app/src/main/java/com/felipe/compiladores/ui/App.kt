package com.felipe.compiladores.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.felipe.compiladores.game.Content
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.ui.components.SystemBack
import com.felipe.compiladores.ui.level.LevelHost
import com.felipe.compiladores.ui.screens.DiaryScreen
import com.felipe.compiladores.ui.screens.HomeScreen
import com.felipe.compiladores.ui.screens.ReviewScreen
import com.felipe.compiladores.ui.screens.SandboxModel
import com.felipe.compiladores.ui.screens.SandboxScreen
import com.felipe.compiladores.ui.screens.WorldScreen
import com.felipe.compiladores.ui.theme.Background

sealed interface Screen {
    data object Home : Screen
    data class WorldView(val worldId: String) : Screen
    data class Play(val level: Level, val sandbox: Boolean = false) : Screen
    data object Review : Screen
    data object Diary : Screen
    data object Sandbox : Screen
}

/** Raiz do jogo: navegação simples por pilha de telas. */
@Composable
fun App(game: GameState, start: List<Screen> = listOf(Screen.Home)) {
    val stack = remember { mutableStateListOf<Screen>().apply { addAll(start) } }
    val sandbox = remember { SandboxModel() }
    LaunchedEffect(Unit) { game.touchDay() }

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    SystemBack(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Background)) {
        when (val s = stack.last()) {
            Screen.Home -> HomeScreen(
                game,
                onWorld = { push(Screen.WorldView(it.id)) },
                onReview = { push(Screen.Review) },
                onDiary = { push(Screen.Diary) },
                onSandbox = { push(Screen.Sandbox) },
            )
            is Screen.WorldView -> WorldScreen(Content.world(s.worldId), game, ::pop) { push(Screen.Play(it)) }
            is Screen.Play -> key(s) {
                LevelHost(s.level, game, s.sandbox, onExit = ::pop, onNext = { next ->
                    stack[stack.lastIndex] = Screen.Play(next)
                    val below = stack.getOrNull(stack.lastIndex - 1)
                    val nextWorld = Content.worldOf(next.id).id
                    if (below is Screen.WorldView && below.worldId != nextWorld) {
                        stack[stack.lastIndex - 1] = Screen.WorldView(nextWorld)
                    }
                })
            }
            Screen.Review -> ReviewScreen(game, ::pop)
            Screen.Diary -> DiaryScreen(game, ::pop) { push(Screen.Play(it)) }
            Screen.Sandbox -> SandboxScreen(sandbox, ::pop) { push(Screen.Play(it, sandbox = true)) }
        }
    }
}
