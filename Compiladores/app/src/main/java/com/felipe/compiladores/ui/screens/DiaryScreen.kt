package com.felipe.compiladores.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Content
import com.felipe.compiladores.game.Feeling
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ProgressBar
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

/** Interpretação da curva de calibração: é aqui que a metacognição fica visível. */
fun calibrationAdvice(game: GameState): String {
    val cal = game.profile.calibration
    val sure = cal[Confidence.SURE]
    val guess = cal[Confidence.GUESS]
    val total = cal.values.sumOf { it.total }
    return when {
        total < 5 -> "Aposte sua confiança em mais algumas verificações para ver sua curva de calibração."
        sure != null && sure.total >= 4 && sure.rate < 0.7f ->
            "Quando você diz \"Certeza\", acerta só ${(sure.rate * 100).toInt()}%. Sinal de ilusão de competência: antes de apostar alto, tente explicar a regra em voz alta."
        guess != null && guess.total >= 4 && guess.rate > 0.6f ->
            "Você acerta ${(guess.rate * 100).toInt()}% dos chutes: sabe mais do que pensa! Confie um pouco mais — e confirme revisando."
        else -> "Boa calibração: sua confiança acompanha seus acertos. É exatamente isso que um bom estudante faz antes da prova."
    }
}

@Composable
fun DiaryScreen(game: GameState, onBack: () -> Unit, onLevel: (Level) -> Unit) {
    val p = game.profile
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Diário de bordo", "Como você aprende (metacognição)", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(title = "Resumo", accent = Primary) {
                val done = Content.allLevels.count { p.isDone(it.id) }
                Text("$done de ${Content.allLevels.size} fases · ${p.totalStars}★ · ${p.xp} XP · 🔥 ${p.streak} dia(s)", color = TextMain)
                Spacer(Modifier.height(6.dp))
                ProgressBar(done.toFloat() / Content.allLevels.size)
            }

            Panel(title = "🎯 Calibração da confiança", accent = Tertiary) {
                Text("Quanto você acerta em cada nível de confiança:", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(8.dp))
                Confidence.entries.forEach { c ->
                    val t = p.calibration[c]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.emoji} ${c.label}", modifier = Modifier.width(140.dp), color = TextMain, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.weight(1f)) { ProgressBar(t?.rate ?: 0f, if ((t?.rate ?: 0f) >= 0.7f) Success else Tertiary) }
                        Text(
                            if (t == null) "  –" else "  ${(t.rate * 100).toInt()}% (${t.right}/${t.total})",
                            color = TextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(92.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(calibrationAdvice(game), style = MaterialTheme.typography.bodyMedium, color = TextMain)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Previsões certas: ${p.predictions.right}/${p.predictions.total} · Autoexplicações certas: ${p.explanations.right}/${p.explanations.total}",
                    style = MaterialTheme.typography.labelMedium, color = TextDim,
                )
            }

            Panel(title = "🐞 Seus erros mais comuns", accent = Danger) {
                val top = p.mistakes.entries.sortedByDescending { it.value }.take(6)
                if (top.isEmpty()) Text("Nenhum erro registrado ainda. Eles aparecem aqui, por tipo, para você saber ONDE focar.", color = TextDim)
                top.forEach { (type, n) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Danger, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(type.title, style = MaterialTheme.typography.titleSmall, color = TextMain, modifier = Modifier.weight(1f))
                            Text("${n}x", color = Danger, fontWeight = FontWeight.Bold)
                        }
                        Text("Estratégia: ${type.tip}", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }

            Panel(title = "🧠 Memória (treino espaçado)", accent = Primary) {
                val unlocked = game.unlockedSkills()
                if (unlocked.isEmpty()) Text("Nenhuma habilidade liberada ainda.", color = TextDim)
                unlocked.forEach { s ->
                    val st = p.skills[s]
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.emoji} ${s.title}", modifier = Modifier.weight(1f), color = TextMain)
                        repeat(5) { i ->
                            Box(
                                Modifier.padding(horizontal = 2.dp).size(12.dp)
                                    .background(if (st != null && i < st.box) Primary else Outline, CircleShape),
                            )
                        }
                    }
                }
            }

            val confused = Content.allLevels.filter { p.feelings[it.id] == Feeling.CONFUSED }
            Panel(title = "😵 Temas que você marcou como confusos", accent = Secondary) {
                if (confused.isEmpty()) Text("Nenhum. Ao terminar uma fase, diga como se sente — é honestidade consigo mesmo que guia a revisão.", color = TextDim)
                confused.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onLevel(l) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(l.title, color = TextMain, modifier = Modifier.weight(1f))
                        Text("jogar de novo ▶", color = Secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (!confirmReset) {
                GameButton("Apagar progresso", { confirmReset = true }, Modifier.fillMaxWidth(), style = ButtonStyle.GHOST, color = TextDim)
            } else {
                Text("Tem certeza? Estrelas, XP e estatísticas serão apagados.", color = Danger)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton("Cancelar", { confirmReset = false }, Modifier.weight(1f), style = ButtonStyle.OUTLINED)
                    GameButton("Apagar", { game.resetProgress(); confirmReset = false }, Modifier.weight(1f), color = Danger)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
