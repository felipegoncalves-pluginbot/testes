package com.felipe.compiladores.ui.arcade

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.game.ArcadeGame
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.Confetti
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.Mascot
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.SystemBack
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Surface
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.accentColor

/** Hub do Treino Relâmpago: um cartão por jogo com nível, recorde e evolução. */
@Composable
fun ArcadeHub(game: GameState, onBack: () -> Unit, onPlay: (List<ArcadeGame>) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Treino relâmpago", "Partidas de 1 minuto · dificuldade adaptativa", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MascotSays("Treino curto e intenso, tipo academia do cérebro: *3 jogos por dia* deixam FIRST, handles e pilhas no automático.", Mood.HAPPY)
            GameButton("⚡ Treino do dia (3 jogos)", { onPlay(dailyWorkout(game)) }, Modifier.fillMaxWidth().testTag("daily"), color = Tertiary)
            ArcadeGame.entries.forEachIndexed { i, g -> ArcadeCard(g, game, i) { onPlay(listOf(g)) } }
        }
    }
}

/** Treino do dia: os 3 jogos com menos partidas recentes (ou os de nível mais baixo). */
fun dailyWorkout(game: GameState): List<ArcadeGame> =
    ArcadeGame.entries.sortedWith(compareBy({ game.profile.arcade[it.id].orEmpty().size }, { game.arcadeLevel(it.id) })).take(3)

@Composable
private fun ArcadeCard(g: ArcadeGame, game: GameState, index: Int, onClick: () -> Unit) {
    val color = accentColor(index + 1)
    val history = game.profile.arcade[g.id].orEmpty()
    Row(
        Modifier.fillMaxWidth().appear(g.id, delayMs = index * 80)
            .background(Surface, RoundedCornerShape(18.dp))
            .border(1.dp, Outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("arcade:${g.id}")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Text(g.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, color = TextMain)
                Spacer(Modifier.width(6.dp))
                Tag(g.skill, color)
            }
            Text(g.description, style = MaterialTheme.typography.bodySmall, color = TextDim)
            Spacer(Modifier.height(4.dp))
            Text(
                "Nível ${game.arcadeLevel(g.id)} · recorde ${game.arcadeBest(g.id)} · ${history.size} partida(s)",
                style = MaterialTheme.typography.labelSmall, color = TextDim,
            )
        }
        if (history.size >= 2) Sparkline(history, color)
    }
}

@Composable
fun Sparkline(values: List<Int>, color: Color, modifier: Modifier = Modifier.size(64.dp, 34.dp)) {
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * step, size.height - v / max * size.height * 0.9f)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
        val last = values.last()
        drawCircle(color, 5f, Offset((values.size - 1) * step, size.height - last / max * size.height * 0.9f))
    }
}

/** Joga uma lista de jogos em sequência e mostra o resultado de cada um. */
@Composable
fun ArcadeSession(games: List<ArcadeGame>, game: GameState, onExit: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    var bestBefore by remember { mutableIntStateOf(0) }
    var levelBefore by remember { mutableIntStateOf(1) }
    val current = games[index.coerceAtMost(games.lastIndex)]
    SystemBack { onExit() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            "${current.emoji} ${current.title}",
            if (games.size > 1) "Treino do dia · jogo ${index + 1} de ${games.size}" else "Nível ${game.arcadeLevel(current.id)}",
            onExit,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val r = result
            if (r == null) {
                key(index, attempt) {
                    val level = game.arcadeLevel(current.id)
                    val finish: (Int, Float) -> Unit = { score, perf ->
                        bestBefore = game.arcadeBest(current.id)
                        levelBefore = level
                        game.recordArcade(current.id, score, perf)
                        result = score to perf
                    }
                    when (current) {
                        ArcadeGame.FIRST_RAIN -> FirstRainGame(level, finish)
                        ArcadeGame.HANDLE_HUNT -> HandleHuntGame(level, finish)
                        ArcadeGame.STACK_MEMORY -> StackMemoryGame(level, finish)
                        ArcadeGame.WHO_AM_I -> WhoAmIGame(level, finish)
                    }
                }
            } else {
                val (score, perf) = r
                val record = score > bestBefore
                val newLevel = game.arcadeLevel(current.id)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Mascot(if (perf >= 1f) Mood.CELEBRATE else if (perf >= 0.5f) Mood.HAPPY else Mood.THINK, size = 96.dp)
                    Text("$score pontos", style = MaterialTheme.typography.headlineMedium, color = TextMain, modifier = Modifier.appear("score"))
                    if (record) Text("🏆 Novo recorde!", color = Tertiary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.appear("rec", 300))
                    Text(
                        when {
                            newLevel > levelBefore -> "Meta batida! Dificuldade sobe para o nível $newLevel."
                            newLevel < levelBefore -> "Vamos com calma: dificuldade volta para o nível $newLevel."
                            else -> "Meta do nível: ${(perf * 100).toInt().coerceAtMost(999)}% alcançada."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = TextDim,
                    )
                    val history = game.profile.arcade[current.id].orEmpty()
                    if (history.size >= 2) {
                        Text("Sua evolução", style = MaterialTheme.typography.labelMedium, color = TextDim)
                        Sparkline(history, Primary, Modifier.size(220.dp, 70.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (index < games.lastIndex) {
                        GameButton("Próximo jogo ▶", { index++; result = null }, Modifier.fillMaxWidth())
                    } else if (games.size > 1) {
                        Text("Treino do dia completo! 💪", style = MaterialTheme.typography.titleMedium, color = Success)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameButton("↻ De novo", { attempt++; result = null }, Modifier.weight(1f), style = ButtonStyle.OUTLINED)
                        GameButton("Sair", onExit, Modifier.weight(1f), style = ButtonStyle.OUTLINED)
                    }
                }
                if (record || perf >= 1f) Confetti(score)
            }
        }
    }
}
