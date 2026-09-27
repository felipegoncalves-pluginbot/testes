package com.felipe.compiladores.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.game.Content
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.game.World
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.Mascot
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.ProgressBar
import com.felipe.compiladores.ui.components.Stars
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.components.pulse
import com.felipe.compiladores.ui.theme.OnAccent
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Surface
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.accentColor

/** Próxima fase recomendada: a primeira liberada e ainda não concluída. */
fun nextRecommended(game: GameState): Level? =
    Content.allLevels.firstOrNull { !game.profile.isDone(it.id) && Content.isUnlocked(game.profile, it.id) }

@Composable
fun HomeScreen(
    game: GameState,
    onWorld: (World) -> Unit,
    onLevel: (Level) -> Unit,
    onReview: () -> Unit,
    onArcade: () -> Unit,
    onDiary: () -> Unit,
    onSandbox: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = game.profile
    val maxStars = Content.allLevels.size * 3
    val due = game.dueSkills()
    val unlocked = game.unlockedSkills()
    val next = nextRecommended(game)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Primary.copy(alpha = 0.22f), Secondary.copy(alpha = 0.14f))), RoundedCornerShape(22.dp))
                .border(1.dp, Outline, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PARSER QUEST", style = MaterialTheme.typography.headlineMedium, color = TextMain, letterSpacing = 2.sp)
                    Text("Você é o compilador.", style = MaterialTheme.typography.bodyMedium, color = TextDim)
                }
                Mascot(if (p.streak >= 3) Mood.CELEBRATE else Mood.HAPPY, size = 72.dp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("★", "${p.totalStars}/$maxStars", Tertiary)
                StatPill("XP", "${p.xp}", Primary)
                StatPill("🔥", "${p.streak}", Secondary)
            }
        }

        MascotSays(
            when {
                p.stars.isEmpty() -> "Oi! Eu sou o *Parsy*. Que tal começar pela *Oficina de Derivações*? Cada mundo abre com uma aula ilustrada curtinha."
                due.isNotEmpty() -> "Você tem *${due.size} tema(s)* para revisar hoje. Relembrar na hora certa é o que fixa!"
                next != null -> "Bora continuar? Próxima parada: *${next.title}*."
                else -> "Você completou tudo! Agora é buscar 3 estrelas e bater recordes no treino relâmpago."
            },
            Mood.HAPPY,
        )
        if (next != null) {
            GameButton("▶ Continuar: ${next.title}", { onLevel(next) }, Modifier.fillMaxWidth().pulse().testTag("continue"))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickCard("🧠", "Treino espaçado", if (unlocked.isEmpty()) "libera ao jogar" else if (due.isEmpty()) "em dia ✓" else "${due.size} para hoje", Tertiary, Modifier.weight(1f).testTag("review"), onReview)
            QuickCard("⚡", "Treino relâmpago", "4 minijogos", Secondary, Modifier.weight(1f).testTag("arcade"), onArcade)
        }

        Text("MAPA", style = MaterialTheme.typography.labelLarge, color = TextDim, letterSpacing = 2.sp)
        Content.worlds.forEachIndexed { i, w ->
            WorldCard(w, game, i, onClick = { onWorld(w) })
            if (i < Content.worlds.lastIndex) {
                Box(Modifier.padding(start = 34.dp).width(3.dp).height(12.dp).background(Outline))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton("🧪 Lab", onSandbox, Modifier.weight(1f), style = ButtonStyle.OUTLINED, color = Primary)
            GameButton("📓 Diário", onDiary, Modifier.weight(1f), style = ButtonStyle.OUTLINED, color = Secondary)
            GameButton("🎨 Tema", onSettings, Modifier.weight(1f).testTag("settings"), style = ButtonStyle.OUTLINED, color = Tertiary)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QuickCard(icon: String, title: String, subtitle: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(icon, fontSize = 24.sp)
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextMain)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun StatPill(icon: String, value: String, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(value, color = TextMain, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WorldCard(w: World, game: GameState, index: Int, onClick: () -> Unit) {
    val color = accentColor(w.accent)
    val stars = w.levels.sumOf { game.profile.starsOf(it.id) }
    val done = w.levels.count { game.profile.isDone(it.id) }
    Row(
        Modifier.fillMaxWidth()
            .appear(w.id, delayMs = (index * 40).coerceAtMost(400))
            .background(Surface, RoundedCornerShape(18.dp))
            .border(1.dp, if (done == w.levels.size) color else Outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("world:${w.id}")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(color.copy(alpha = 0.18f), CircleShape).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(w.emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${w.number}. ${w.title}", style = MaterialTheme.typography.titleMedium, color = TextMain)
            Text(w.subtitle, style = MaterialTheme.typography.bodySmall, color = TextDim)
            Spacer(Modifier.height(8.dp))
            ProgressBar(stars.toFloat() / (w.levels.size * 3), color)
            Spacer(Modifier.height(4.dp))
            Text("$done/${w.levels.size} fases · $stars★", style = MaterialTheme.typography.labelSmall, color = TextDim)
        }
    }
}

fun specLabel(spec: LevelSpec): String = when (spec) {
    is LevelSpec.Derive -> if (spec.ambiguity) "ambiguidade" else "derivação"
    LevelSpec.FirstSets -> "FIRST"
    is LevelSpec.FollowSets -> "FOLLOW"
    is LevelSpec.LL1Table -> "tabela LL(1)"
    is LevelSpec.LL1Parse -> "parser LL(1)"
    is LevelSpec.Surgery -> "cirurgia"
    is LevelSpec.ShiftReduce -> "shift-reduce"
    is LevelSpec.Closure -> "itens LR(0)"
    LevelSpec.SlrTable -> "tabela SLR"
    LevelSpec.SlrConflict -> "conflito SLR"
    LevelSpec.Classify -> "classificar"
    is LevelSpec.Design -> "fábrica"
    is LevelSpec.Robot -> "robô"
    is LevelSpec.Magnets -> "ímãs"
}

@Composable
fun WorldScreen(world: World, game: GameState, onBack: () -> Unit, onLesson: () -> Unit, onLevel: (Level) -> Unit) {
    val color = accentColor(world.accent)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("${world.emoji} ${world.title}", "Mundo ${world.number}", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val seen = world.id in game.profile.seenLessons
            Row(
                Modifier.fillMaxWidth()
                    .background(color.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                    .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .clickable { game.markLessonSeen(world.id); onLesson() }
                    .testTag("lesson")
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mascot(if (seen) Mood.HAPPY else Mood.SURPRISED, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (seen) "Rever a aula ilustrada" else "Comece pela aula ilustrada!", style = MaterialTheme.typography.titleSmall, color = color)
                    Text("${world.lesson.title} · ${world.lesson.scenes.size} cenas curtas", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
                Text("▶", color = color, fontSize = 20.sp, modifier = Modifier.pulse(!seen))
            }
            world.levels.forEachIndexed { i, level ->
                val unlocked = Content.isUnlocked(game.profile, level.id)
                val stars = game.profile.starsOf(level.id)
                Row(
                    Modifier.fillMaxWidth()
                        .appear(level.id, delayMs = i * 50)
                        .alpha(if (unlocked) 1f else 0.45f)
                        .background(Surface, RoundedCornerShape(16.dp))
                        .border(1.dp, if (game.profile.isDone(level.id)) color.copy(alpha = 0.7f) else Outline, RoundedCornerShape(16.dp))
                        .clickable(enabled = unlocked) { onLevel(level) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).background(if (unlocked) color else SurfaceHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (unlocked) "${i + 1}" else "🔒", color = OnAccent, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(level.title, style = MaterialTheme.typography.titleMedium, color = TextMain)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag(specLabel(level.spec), color)
                            if (level.prediction != null) Tag("previsão", Tertiary)
                            game.profile.feelings[level.id]?.let { Text(it.emoji, fontSize = 13.sp) }
                        }
                    }
                    if (game.profile.isDone(level.id)) Stars(stars, size = 16) else if (unlocked) Text("▶", color = color)
                }
            }
            if (world.levels.all { game.profile.isDone(it.id) }) {
                Text("Mundo completo! Refaça fases para buscar 3★ ou siga para o próximo.", color = Success, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
