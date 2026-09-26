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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.game.Content
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.World
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ProgressBar
import com.felipe.compiladores.ui.components.Stars
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.level.LessonContent
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Surface
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

@Composable
fun HomeScreen(
    game: GameState,
    onWorld: (World) -> Unit,
    onReview: () -> Unit,
    onDiary: () -> Unit,
    onSandbox: () -> Unit,
) {
    val p = game.profile
    val maxStars = Content.allLevels.size * 3
    val due = game.dueSkills()
    val unlocked = game.unlockedSkills()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Primary.copy(alpha = 0.25f), Secondary.copy(alpha = 0.18f))), RoundedCornerShape(22.dp))
                .border(1.dp, Outline, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text("PARSER QUEST", style = MaterialTheme.typography.headlineMedium, color = TextMain, letterSpacing = 2.sp)
            Text("Análise sintática na prática: você é o compilador.", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("★", "${p.totalStars}/$maxStars", Tertiary)
                StatPill("XP", "${p.xp}", Primary)
                StatPill("🔥", "${p.streak} dia(s)", Secondary)
            }
        }

        Panel(title = "🧠 Treino espaçado", accent = Tertiary) {
            Text(
                when {
                    unlocked.isEmpty() -> "Complete fases para liberar revisões. Relembrar depois de um tempo é o que fixa o conteúdo."
                    due.isEmpty() -> "Nada pendente hoje. Volte amanhã — ou treine mesmo assim."
                    else -> "${due.size} tema(s) para revisar hoje: ${due.joinToString { it.title }}."
                },
                style = MaterialTheme.typography.bodyMedium, color = TextMain,
            )
            if (unlocked.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                GameButton(if (due.isEmpty()) "Treinar mesmo assim" else "Revisar agora (6 desafios)", onReview, Modifier.fillMaxWidth(), color = Tertiary)
            }
        }

        Text("MAPA", style = MaterialTheme.typography.labelLarge, color = TextDim, letterSpacing = 2.sp)
        Content.worlds.forEachIndexed { i, w ->
            WorldCard(w, game, onClick = { onWorld(w) })
            if (i < Content.worlds.lastIndex) {
                Box(Modifier.padding(start = 34.dp).width(3.dp).height(12.dp).background(Outline))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton("🧪 Laboratório", onSandbox, Modifier.weight(1f), style = ButtonStyle.OUTLINED, color = Primary)
            GameButton("📓 Diário", onDiary, Modifier.weight(1f), style = ButtonStyle.OUTLINED, color = Secondary)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatPill(icon: String, value: String, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(value, color = TextMain, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WorldCard(w: World, game: GameState, onClick: () -> Unit) {
    val color = Color(w.color)
    val stars = w.levels.sumOf { game.profile.starsOf(it.id) }
    val done = w.levels.count { game.profile.isDone(it.id) }
    Row(
        Modifier.fillMaxWidth()
            .background(Surface, RoundedCornerShape(18.dp))
            .border(1.dp, if (done == w.levels.size) color else Outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
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
}

@Composable
fun WorldScreen(world: World, game: GameState, onBack: () -> Unit, onLevel: (Level) -> Unit) {
    val color = Color(world.color)
    var lessonOpen by remember(world.id) { mutableStateOf(world.id !in game.profile.seenLessons) }
    LaunchedEffect(world.id) { game.markLessonSeen(world.id) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("${world.emoji} ${world.title}", "Mundo ${world.number}", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(color.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .clickable { lessonOpen = !lessonOpen }
                    .padding(14.dp),
            ) {
                if (lessonOpen) LessonContent(world)
                else Text("📖 ${world.lesson.title} (toque para abrir)", style = MaterialTheme.typography.titleSmall, color = color)
            }
            world.levels.forEachIndexed { i, level ->
                val unlocked = Content.isUnlocked(game.profile, level.id)
                val stars = game.profile.starsOf(level.id)
                Row(
                    Modifier.fillMaxWidth()
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
                        Text(if (unlocked) "${i + 1}" else "🔒", color = Color(0xFF0B1020), fontWeight = FontWeight.Black)
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
