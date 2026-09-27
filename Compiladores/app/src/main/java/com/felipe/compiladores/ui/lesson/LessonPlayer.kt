package com.felipe.compiladores.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.game.Scene
import com.felipe.compiladores.game.World
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.Mascot
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.SystemBack
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.components.richText
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Surface
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.accentColor

/** Aula ilustrada: uma cena por vez (segmentação), com o mascote conduzindo. */
@Composable
fun LessonPlayer(world: World, onClose: () -> Unit, closeLabel: String = "Bora jogar! ▶") {
    val scenes = world.lesson.scenes
    var index by remember(world.id) { mutableIntStateOf(0) }
    SystemBack { if (index > 0) index-- else onClose() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("${world.emoji} ${world.lesson.title}", "Aula ilustrada · ${index + 1} de ${scenes.size}", onClose)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            scenes.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(5.dp)
                        .background(if (i <= index) accentColor(world.accent) else Outline, RoundedCornerShape(50)),
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            key(index) { SceneView(scenes[index]) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton("← Voltar", { if (index > 0) index-- else onClose() }, Modifier.weight(1f), style = ButtonStyle.OUTLINED)
            if (index < scenes.lastIndex) GameButton("Próximo →", { index++ }, Modifier.weight(1.4f))
            else GameButton(closeLabel, onClose, Modifier.weight(1.4f), color = Success)
        }
    }
}

@Composable
fun SceneView(scene: Scene) {
    when (scene) {
        is Scene.Talk -> {
            MascotSays(scene.text, scene.mood)
            scene.visual?.let { VisualCard(it, Modifier.appear("v")) }
        }
        is Scene.BrainPower -> BrainPower(scene)
        is Scene.WatchIt -> {
            Banner("⚠", "CUIDADO!", Danger)
            MascotSays(scene.text, Mood.SURPRISED)
            scene.visual?.let { VisualCard(it) }
        }
        is Scene.NoDumbQuestions -> {
            Banner("❓", "NÃO EXISTEM PERGUNTAS IDIOTAS", Secondary)
            scene.qa.forEachIndexed { i, (q, a) ->
                Column(
                    Modifier.fillMaxWidth().appear(i, delayMs = i * 150)
                        .background(Surface, RoundedCornerShape(16.dp)).border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(14.dp),
                ) {
                    Text(richText("*P:* $q"), style = MaterialTheme.typography.titleSmall, color = TextMain)
                    Spacer(Modifier.height(6.dp))
                    Text(richText("R: $a"), style = MaterialTheme.typography.bodyMedium, color = TextMain)
                }
            }
        }
        is Scene.Fireside -> Fireside(scene)
        is Scene.KeyPoints -> {
            Banner("📌", "PONTOS-CHAVE", Success)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(Mood.CELEBRATE, size = 56.dp)
                Spacer(Modifier.width(8.dp))
                Text("Anote isso — ou melhor: explique em voz alta para alguém (ou para mim!).", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            }
            scene.points.forEachIndexed { i, p ->
                Row(Modifier.appear(i, delayMs = i * 120), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 3.dp).size(18.dp).background(Success, CircleShape), contentAlignment = Alignment.Center) {
                        Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(richText(p), style = MaterialTheme.typography.bodyLarge, color = TextMain)
                }
            }
        }
    }
}

@Composable
private fun Banner(icon: String, title: String, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(title, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
    }
}

@Composable
private fun BrainPower(scene: Scene.BrainPower) {
    var revealed by remember(scene) { mutableStateOf(false) }
    Banner("🧠", "PODER DO CÉREBRO", Tertiary)
    MascotSays(scene.question, Mood.THINK)
    scene.visual?.let { VisualCard(it) }
    if (!revealed) {
        Text("Pare uns segundos e responda de cabeça (ou no papel) antes de revelar.", style = MaterialTheme.typography.bodySmall, color = TextDim)
        GameButton("Pensei! Mostrar resposta", { revealed = true }, Modifier.fillMaxWidth(), color = Tertiary)
    } else {
        Column(
            Modifier.fillMaxWidth().appear("answer")
                .background(Tertiary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .border(1.dp, Tertiary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(14.dp),
        ) {
            Text("Resposta", style = MaterialTheme.typography.labelMedium, color = Tertiary)
            Text(richText(scene.answer), style = MaterialTheme.typography.bodyLarge, color = TextMain)
        }
    }
}

@Composable
private fun Fireside(scene: Scene.Fireside) {
    var shown by remember(scene) { mutableIntStateOf(2) }
    Banner("🔥", "PAPO DE BASTIDOR: ${scene.left} × ${scene.right}", Tertiary)
    scene.lines.take(shown).forEachIndexed { i, (left, text) ->
        Row(
            Modifier.fillMaxWidth().appear(i),
            horizontalArrangement = if (left) Arrangement.Start else Arrangement.End,
        ) {
            val color = if (left) Primary else Secondary
            Column(
                Modifier.widthIn(max = 290.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Text(if (left) scene.left else scene.right, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                Text(richText(text), style = MaterialTheme.typography.bodyMedium, color = TextMain)
            }
        }
    }
    if (shown < scene.lines.size) {
        GameButton("Continuar conversa…", { shown++ }, Modifier.fillMaxWidth(), style = ButtonStyle.OUTLINED, color = Tertiary)
    }
}
