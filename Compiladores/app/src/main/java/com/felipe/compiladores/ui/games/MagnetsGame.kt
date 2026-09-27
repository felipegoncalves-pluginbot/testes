package com.felipe.compiladores.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.engine.DerivationMode
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.tokenizeInput
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.Magnets
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.components.symbolsText
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import kotlin.random.Random

/** Ímãs de derivação (Head First): ordene as formas e deixe as armadilhas de fora. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MagnetsGame(level: Level, spec: LevelSpec.Magnets, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val target = remember(level.id) { tokenizeInput(spec.target, grammar) }
    val puzzle = remember(level.id) { Magnets.build(grammar, target, spec.mode, spec.distractors, Random(level.id.hashCode())) }
    val pieces = remember(level.id) { puzzle.pieces(Random(level.id.hashCode() + 1)) }
    val placed = remember(level.id) { mutableStateListOf<Int>() }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var wrongAt by remember { mutableStateOf<Int?>(null) }
    var shakes by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }
    val which = if (spec.mode == DerivationMode.LEFTMOST) "mais à esquerda" else "mais à direita"

    session.hintProvider = { n ->
        val correct = puzzle.forms.drop(1)
        val okPrefix = placed.map { pieces[it] }.zip(correct).takeWhile { it.first == it.second }.size
        val last = if (okPrefix == 0) puzzle.forms.first() else correct[okPrefix - 1]
        if (okPrefix >= correct.size) "A sequência está completa. Verifique!"
        else if (n == 2) {
            val nts = last.indices.filter { grammar.isNonterminal(last[it]) }
            val pos = if (spec.mode == DerivationMode.LEFTMOST) nts.first() else nts.last()
            "Depois de \"${last.joinToString(" ")}\", troque o ${last[pos]} (o não-terminal $which, posição ${pos + 1})."
        } else "O ímã ${okPrefix + 1} é \"${correct[okPrefix].joinToString(" ")}\"."
    }

    fun check() {
        val c = confidence ?: return
        val seq = placed.map { pieces[it] }
        val err = Magnets.explain(grammar, puzzle, seq, spec.mode)
        session.check(c, err == null)
        confidence = null
        if (err == null) {
            solved = true
            message = null
        } else {
            shakes++
            session.mistake(MistakeType.MAGNET_ORDER)
            message = err
            val correct = puzzle.forms.drop(1)
            wrongAt = seq.indices.firstOrNull { it >= correct.size || seq[it] != correct[it] }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alvo  ", color = TextDim, style = MaterialTheme.typography.labelLarge)
            MonoText(symbolsText(target, grammar), size = 18)
            Spacer(Modifier.weight(1f))
            Tag("derivação $which", Tertiary)
        }
        GrammarCard(grammar)
        Panel(Modifier.shake(shakes), title = "Geladeira (a sua derivação)", accent = Primary) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MagnetTile(puzzle.forms.first(), grammar, fixed = true, wrong = false, tag = "placed:start") {}
                placed.forEachIndexed { i, idx ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⇒ ", color = TextDim)
                        MagnetTile(pieces[idx], grammar, fixed = solved, wrong = wrongAt == i, tag = "placed:$i") {
                            placed.removeAt(i); message = null; wrongAt = null
                        }
                    }
                }
                if (!solved && placed.size < puzzle.forms.size - 1) {
                    Text("⇒ …  (toque nos ímãs abaixo, em ordem)", color = TextDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!solved) {
            Panel(title = "Ímãs soltos (${pieces.size - placed.size})", accent = Tertiary) {
                Text("Cuidado: ${puzzle.distractors.size} deles são armadilhas e não entram.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pieces.indices.filter { it !in placed }.forEach { idx ->
                        val tilt = ((idx * 37) % 7 - 3).toFloat()
                        Box(Modifier.graphicsLayer { rotationZ = tilt }) {
                            MagnetTile(pieces[idx], grammar, fixed = false, wrong = false, tag = "magnet:$idx") {
                                placed.add(idx); message = null; wrongAt = null
                            }
                        }
                    }
                }
            }
        }
        message?.let { Feedback(FeedbackKind.BAD, it, title = "Hmm, algo não encaixa") }
        if (solved) {
            MascotSays(level.insight, Mood.CELEBRATE)
            GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
        } else {
            CheckBar(confidence, { confidence = it }, ::check, enabled = placed.isNotEmpty())
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MagnetTile(form: List<String>, grammar: Grammar, fixed: Boolean, wrong: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).appear(tag)
            .heightIn(min = 40.dp)
            .background(if (wrong) Danger.copy(alpha = 0.15f) else SurfaceHigh, RoundedCornerShape(10.dp))
            .border(if (wrong) 2.dp else 1.dp, if (wrong) Danger else if (fixed) Outline else Tertiary.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .then(if (fixed) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        MonoText(symbolsText(form, grammar), size = 15)
    }
}
