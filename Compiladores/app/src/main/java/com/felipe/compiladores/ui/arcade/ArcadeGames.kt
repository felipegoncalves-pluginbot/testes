package com.felipe.compiladores.ui.arcade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.game.FirstRain
import com.felipe.compiladores.game.HandleHunt
import com.felipe.compiladores.game.HandleQuestion
import com.felipe.compiladores.game.MemoryRound
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.game.StackMemory
import com.felipe.compiladores.game.WhoAmI
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.ChipsRow
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ProgressBar
import com.felipe.compiladores.ui.components.SymbolBox
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.components.symbolColor
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.ThemeState
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
private fun Hud(score: Int, extra: String, timeFraction: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$score", style = MaterialTheme.typography.headlineSmall, color = TextMain, fontWeight = FontWeight.Black)
        Text(" pts", color = TextDim)
        Spacer(Modifier.weight(1f))
        Text(extra, color = Tertiary, style = MaterialTheme.typography.titleMedium)
    }
    if (timeFraction != null) ProgressBar(timeFraction, if (timeFraction < 0.2f) Danger else Primary)
}

// ======================================================================== Chuva de FIRST

@Composable
fun FirstRainGame(level: Int, onFinish: (Int, Float) -> Unit) {
    val random = remember { Random(System.nanoTime()) }
    var state by remember { mutableStateOf(FirstRain.start(level, random)) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (!state.over) {
            val now = withFrameNanos { it }
            state = FirstRain.step(state, ((now - last) / 1e9f).coerceIn(0f, 0.05f), random)
            last = now
        }
        onFinish(state.score, state.score / FirstRain.target(level).toFloat())
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hud(state.score, "♥".repeat(state.lives.coerceAtLeast(0)) + "  ×${1 + state.combo / 4}", 1f - state.time / state.duration)
        GrammarCard(state.grammar, collapsible = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pegue só o que está em ", color = TextMain)
            Text("FIRST(${state.nt})", color = NonterminalColor, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.appear(state.nt))
        }
        state.message?.let { Text(it, color = if (it.startsWith("Novo")) Primary else Danger, style = MaterialTheme.typography.bodySmall) }
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).shake(state.mistakes)
                .background(SurfaceHigh.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .border(1.dp, Outline, RoundedCornerShape(18.dp)),
        ) {
            val w = maxWidth
            val h = maxHeight
            state.drops.forEach { d ->
                Box(
                    Modifier.offset(w * d.x - 26.dp, h * d.y)
                        .size(52.dp)
                        .background(SurfaceHigh, CircleShape)
                        .border(2.dp, symbolColor(state.grammar, d.symbol), CircleShape)
                        .clickable { state = FirstRain.tap(state, d.id, random) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d.symbol, fontFamily = Mono, fontSize = 18.sp, color = symbolColor(state.grammar, d.symbol), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ======================================================================== Caça ao Handle

@Composable
fun HandleHuntGame(level: Int, onFinish: (Int, Float) -> Unit) {
    val random = remember { Random(System.nanoTime()) }
    var question by remember { mutableStateOf(HandleHunt.question(random)) }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableFloatStateOf(HandleHunt.DURATION) }
    var feedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var misses by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (timeLeft > 0f) {
            delay(250)
            timeLeft -= 0.25f
        }
        onFinish(score, score / HandleHunt.target(level).toFloat())
    }
    fun answer(r: IntRange) {
        val q = question
        if (r == q.handle) {
            score += 10 + 2 * streak
            streak++
            feedback = true to "Isso! ${q.production.text}"
        } else {
            streak = 0
            misses++
            timeLeft -= 3f
            feedback = false to "O handle era \"${q.form.subList(q.handle.first, q.handle.last + 1).joinToString(" ")}\" (${q.production.text}): é o que está no topo da pilha quando o parser reduz."
        }
        question = HandleHunt.question(random)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Hud(score, "sequência ${streak}🔥", (timeLeft / HandleHunt.DURATION).coerceIn(0f, 1f))
        GrammarCard(question.grammar, numbered = true, collapsible = false)
        Text("Nesta forma sentencial (mais à direita), qual trecho é o handle?", color = TextMain, style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.shake(misses), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            question.options.forEachIndexed { i, r -> HandleOption(question, r, i) { answer(r) } }
        }
        feedback?.let { (ok, text) -> Feedback(if (ok) FeedbackKind.GOOD else FeedbackKind.BAD, text) }
    }
}

@Composable
private fun HandleOption(q: HandleQuestion, range: IntRange, index: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("option:$index")
            .background(SurfaceHigh, RoundedCornerShape(12.dp))
            .border(1.dp, Outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        q.form.forEachIndexed { i, s ->
            val inside = i in range
            Box(
                Modifier.padding(horizontal = 1.dp)
                    .background(if (inside) Tertiary.copy(alpha = 0.3f) else SurfaceHigh, RoundedCornerShape(6.dp))
                    .border(if (inside) 2.dp else 0.dp, if (inside) Tertiary else SurfaceHigh, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(s, fontFamily = Mono, color = symbolColor(q.grammar, s), fontWeight = if (inside) FontWeight.Black else FontWeight.Normal)
            }
        }
    }
}

// ======================================================================== Pilha na Memória

@Composable
fun StackMemoryGame(level: Int, onFinish: (Int, Float) -> Unit) {
    val random = remember { Random(System.nanoTime()) }
    var roundNo by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableIntStateOf(0) }
    val length = StackMemory.opsFor(level) + roundNo / 2
    val round = remember(roundNo, lives) { StackMemory.round(length, random) }
    var showing by remember(roundNo, lives) { mutableStateOf(true) }
    var opIndex by remember(roundNo, lives) { mutableIntStateOf(-1) }
    val answer = remember(roundNo, lives) { mutableStateListOf<String>() }
    var verdict by remember(roundNo, lives) { mutableStateOf<Boolean?>(null) }
    val maxRounds = 5
    val target = StackMemory.opsFor(level) * 10 * 3

    LaunchedEffect(roundNo, lives) {
        if (ThemeState.reduceMotion) return@LaunchedEffect
        delay(900)
        for (i in round.ops.indices) {
            opIndex = i
            delay(1400)
        }
        showing = false
    }
    fun next(ok: Boolean) {
        if (ok) score += round.ops.size * 10
        if (!ok && lives - 1 <= 0 || roundNo + 1 >= maxRounds) {
            onFinish(score, score / target.toFloat())
            return
        }
        if (ok) roundNo++ else lives--
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Hud(score, "♥".repeat(lives) + "  rodada ${roundNo + 1}/$maxRounds", null)
        Text("Pilha inicial (base à esquerda):", style = MaterialTheme.typography.labelMedium, color = TextDim)
        Row { round.start.forEach { SymbolBox(it, null) } }
        if (showing) {
            if (ThemeState.reduceMotion) {
                Panel(title = "Memorize as operações (${round.ops.size})", accent = Tertiary) {
                    round.ops.forEachIndexed { i, op -> Text("${i + 1}. ${StackMemory.describe(op)}", fontFamily = Mono, color = TextMain) }
                }
                GameButton("Memorizei! Esconder", { showing = false }, Modifier.fillMaxWidth())
            } else {
                MascotSays("Guarde de cabeça! Vou fazer ${round.ops.size} operações na pilha…", Mood.THINK, mascotSize = 44.dp)
                val op = round.ops.getOrNull(opIndex)
                Box(
                    Modifier.fillMaxWidth().height(110.dp).background(SurfaceHigh, RoundedCornerShape(18.dp)).border(2.dp, Tertiary, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (op == null) "Preparar…" else "${opIndex + 1}. ${StackMemory.describe(op)}",
                        fontFamily = Mono, fontSize = 20.sp, color = Tertiary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.appear(opIndex),
                    )
                }
            }
        } else {
            Text("Como ficou a pilha? Toque na ordem, da base para o topo.", color = TextMain)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(SurfaceHigh, RoundedCornerShape(12.dp)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (answer.isEmpty()) Text("(vazia)", color = TextDim)
                answer.forEachIndexed { i, s -> Box(Modifier.appear("$i$s")) { SymbolBox(s, null, highlight = i == answer.lastIndex) } }
            }
            val v = verdict
            if (v == null) {
                ChipsRow {
                    (listOf("$") + round.palette).distinct().forEach { s ->
                        Box(
                            Modifier.testTag("sym:$s").heightIn(min = 42.dp).background(SurfaceHigh, RoundedCornerShape(10.dp))
                                .border(1.dp, symbolColor(null, s), RoundedCornerShape(10.dp))
                                .clickable { answer += s }.padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(s, fontFamily = Mono, color = symbolColor(null, s), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton("⌫", { if (answer.isNotEmpty()) answer.removeAt(answer.lastIndex) }, Modifier.weight(1f), style = ButtonStyle.OUTLINED)
                    GameButton("Confirmar", { verdict = answer.toList() == round.answer }, Modifier.weight(2f))
                }
            } else {
                Feedback(
                    if (v) FeedbackKind.GOOD else FeedbackKind.BAD,
                    if (v) "Memória de aço! +${round.ops.size * 10}" else "A pilha certa era: ${round.answer.joinToString(" ")}",
                )
                if (!v) MemoryReplay(round)
                GameButton("Continuar ▶", { next(v) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MemoryReplay(round: MemoryRound) {
    Panel(title = "Repassando", accent = Secondary) {
        var stack = round.start
        round.ops.forEachIndexed { i, op ->
            stack = StackMemory.apply(stack, op)
            Text("${i + 1}. ${StackMemory.describe(op)}  →  ${stack.joinToString(" ")}", fontFamily = Mono, fontSize = 13.sp, color = TextMain)
        }
    }
}

// ======================================================================== Quem sou eu?

@Composable
fun WhoAmIGame(level: Int, onFinish: (Int, Float) -> Unit) {
    val random = remember { Random(System.nanoTime()) }
    val order = remember { WhoAmI.concepts.shuffled(random).take(WhoAmI.ROUNDS) }
    var roundNo by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    val concept = order[roundNo]
    val options = remember(roundNo) { WhoAmI.options(concept, random, if (level >= 4) 5 else 4) }
    var clues by remember(roundNo) { mutableIntStateOf(1) }
    var picked by remember(roundNo) { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Hud(score, "rodada ${roundNo + 1}/${WhoAmI.ROUNDS}", null)
        MascotSays("Quem sou eu? Menos pistas = mais pontos (${WhoAmI.points(clues)} agora).", Mood.THINK, mascotSize = 44.dp)
        concept.clues.take(clues).forEachIndexed { i, c ->
            Row(
                Modifier.fillMaxWidth().appear("$roundNo-$i")
                    .background(SurfaceHigh, RoundedCornerShape(14.dp)).border(1.dp, Outline, RoundedCornerShape(14.dp)).padding(12.dp),
            ) {
                Text("${i + 1}. ", color = Tertiary, fontWeight = FontWeight.Bold)
                Text("\"$c\"", color = TextMain, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (picked == null && clues < concept.clues.size) {
            GameButton("Mais uma pista (−pontos)", { clues++ }, Modifier.fillMaxWidth(), style = ButtonStyle.OUTLINED, color = Tertiary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o ->
                val color = when {
                    picked == null -> Primary
                    o == concept.name -> Success
                    o == picked -> Danger
                    else -> TextDim
                }
                GameButton(o, {
                    if (picked == null) {
                        picked = o
                        if (o == concept.name) score += WhoAmI.points(clues)
                    }
                }, Modifier.fillMaxWidth().testTag("option:$i"), color = color, style = ButtonStyle.OUTLINED)
            }
        }
        if (picked != null) {
            Feedback(
                if (picked == concept.name) FeedbackKind.GOOD else FeedbackKind.BAD,
                if (picked == concept.name) "Acertou com $clues pista(s)!" else "Era ${concept.name}. Todas as pistas: " + concept.clues.joinToString(" "),
            )
            GameButton(if (roundNo < WhoAmI.ROUNDS - 1) "Próximo ▶" else "Ver resultado", {
                if (roundNo < WhoAmI.ROUNDS - 1) roundNo++ else onFinish(score, score / WhoAmI.target(level).toFloat())
            }, Modifier.fillMaxWidth())
        }
    }
}
