package com.felipe.compiladores.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.felipe.compiladores.game.ArcadeGame
import com.felipe.compiladores.game.Content
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.ui.arcade.ArcadeHub
import com.felipe.compiladores.ui.arcade.ArcadeSession
import com.felipe.compiladores.ui.components.SystemBack
import com.felipe.compiladores.ui.lesson.LessonPlayer
import com.felipe.compiladores.ui.level.LevelHost
import com.felipe.compiladores.ui.screens.DiaryScreen
import com.felipe.compiladores.ui.screens.HomeScreen
import com.felipe.compiladores.ui.screens.ReviewScreen
import com.felipe.compiladores.ui.screens.SandboxModel
import com.felipe.compiladores.ui.screens.SandboxScreen
import com.felipe.compiladores.ui.screens.SettingsScreen
import com.felipe.compiladores.ui.screens.WorldScreen
import com.felipe.compiladores.ui.theme.Background
import com.felipe.compiladores.ui.theme.ThemeState

sealed interface Screen {
    data object Home : Screen
    data class WorldView(val worldId: String) : Screen
    data class Lesson(val worldId: String) : Screen
    data class Play(val level: Level, val sandbox: Boolean = false) : Screen
    data object Review : Screen
    data object Arcade : Screen
    data class ArcadeRun(val games: List<ArcadeGame>) : Screen
    data object Diary : Screen
    data object Sandbox : Screen
    data object Settings : Screen
}

/** Raiz do jogo: navegação simples por pilha de telas. */
@Composable
fun App(game: GameState, start: List<Screen> = listOf(Screen.Home)) {
    val stack = remember { mutableStateListOf<Screen>().apply { addAll(start) } }
    val sandbox = remember { SandboxModel() }
    LaunchedEffect(Unit) { game.touchDay() }
    // As cores são lidas de ThemeState: mantém em sincronia com as configurações salvas.
    val settings = game.profile.settings
    SideEffect { if (ThemeState.settings != settings) ThemeState.settings = settings }

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun openWorld(worldId: String) {
        push(Screen.WorldView(worldId))
        if (worldId !in game.profile.seenLessons) {
            game.markLessonSeen(worldId)
            push(Screen.Lesson(worldId))
        }
    }

    SystemBack(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Background)) {
        when (val s = stack.last()) {
            Screen.Home -> HomeScreen(
                game,
                onWorld = { openWorld(it.id) },
                onLevel = { level ->
                    push(Screen.WorldView(Content.worldOf(level.id).id))
                    push(Screen.Play(level))
                },
                onReview = { push(Screen.Review) },
                onArcade = { push(Screen.Arcade) },
                onDiary = { push(Screen.Diary) },
                onSandbox = { push(Screen.Sandbox) },
                onSettings = { push(Screen.Settings) },
            )
            is Screen.WorldView -> WorldScreen(
                Content.world(s.worldId), game, ::pop,
                onLesson = { push(Screen.Lesson(s.worldId)) },
            ) { push(Screen.Play(it)) }
            is Screen.Lesson -> LessonPlayer(Content.world(s.worldId), onClose = ::pop)
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
            Screen.Arcade -> ArcadeHub(game, ::pop) { push(Screen.ArcadeRun(it)) }
            is Screen.ArcadeRun -> key(s) { ArcadeSession(s.games, game, ::pop) }
            Screen.Diary -> DiaryScreen(game, ::pop) { push(Screen.Play(it)) }
            Screen.Sandbox -> SandboxScreen(sandbox, ::pop) { push(Screen.Play(it, sandbox = true)) }
            Screen.Settings -> SettingsScreen(game, ::pop)
        }
    }
}
