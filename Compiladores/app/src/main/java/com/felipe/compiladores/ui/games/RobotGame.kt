package com.felipe.compiladores.ui.games

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LLRule
import com.felipe.compiladores.engine.Production
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.FrameKind
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.game.RobotInterpreter
import com.felipe.compiladores.game.RobotRun
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.Chip
import com.felipe.compiladores.ui.components.ChipsRow
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.FirstFollowView
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.Mascot
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.Overlay
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.SpecialColor
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.TerminalColor
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.ThemeState
import kotlinx.coroutines.delay

/** Robô Descendente: programe cada função escolhendo os tokens de cada ramo e rode os testes. */
@Composable
fun RobotGame(level: Level, spec: LevelSpec.Robot, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val table = remember(level.id) { LL1Table(Analysis(grammar)) }
    val ideal = remember(level.id) { RobotInterpreter.idealProgram(table) }
    val program = remember(level.id) { mutableStateMapOf<Int, Set<String>>() }
    var editing by remember { mutableStateOf<Production?>(null) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var results by remember { mutableStateOf<List<Pair<Pair<String, Boolean>, RobotRun>>?>(null) }
    var runs by remember { mutableIntStateOf(0) }
    var watching by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }
    val tokens = grammar.terminals + END

    session.hintProvider = { n ->
        val wrong = grammar.productions.firstOrNull { program[it.id].orEmpty() != ideal[it.id].orEmpty() }
        when {
            wrong == null -> "Seu programa já é o ideal. Rode os testes!"
            n == 2 -> "Confira o ramo ${wrong.text} dentro de ${wrong.lhs}()."
            else -> {
                val set = ideal.getValue(wrong.id)
                val rule = table.cells.entries.firstOrNull { (_, es) -> es.any { it.production == wrong } }?.value?.first { it.production == wrong }?.rule
                "O ramo ${wrong.text} deve ser escolhido com { ${grammar.sortTerminals(set).joinToString(", ")} }" +
                    if (rule == LLRule.FOLLOW) " — é um ramo ε, então usa FOLLOW(${wrong.lhs})." else " — o FIRST de ${wrong.rhsText}."
            }
        }
    }

    fun check() {
        val c = confidence ?: return
        val rs = spec.tests.map { t -> t to RobotInterpreter.run(grammar, program, t.first) }
        results = rs
        runs++
        val ok = rs.all { (t, r) -> r.accepted == t.second }
        session.check(c, ok)
        confidence = null
        watching = rs.indexOfFirst { (t, r) -> r.accepted != t.second }.coerceAtLeast(0)
        if (ok) solved = true else session.mistake(MistakeType.ROBOT_BRANCH)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GrammarCard(grammar, initiallyOpen = false)
            Panel(title = "FIRST e FOLLOW", accent = Tertiary, collapsible = true, initiallyOpen = false) { FirstFollowView(table.analysis) }
            Panel(title = "Programa do robô", accent = NonterminalColor) {
                Text("Toque em { … } para escolher os tokens que ativam cada ramo.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(8.dp))
                grammar.nonterminals.forEach { nt ->
                    FunctionBlock(grammar, nt, program, enabled = !solved) { editing = it }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Panel(title = "Testes", accent = Tertiary) {
                spec.tests.forEachIndexed { i, (input, expected) ->
                    val run = results?.getOrNull(i)?.second
                    val ok = run?.accepted == expected
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .then(if (run != null) Modifier.clickable { watching = i } else Modifier)
                            .background(if (i == watching && run != null) Primary.copy(alpha = 0.10f) else SurfaceHigh, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).background(if (run == null) Outline else if (ok) Success else Danger, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(input.ifBlank { "ε" }, fontFamily = Mono, color = TextMain, modifier = Modifier.weight(1f))
                        Text(if (expected) "aceitar" else "rejeitar", style = MaterialTheme.typography.labelMedium, color = if (expected) Success else Danger)
                    }
                }
            }
            val rs = results
            if (rs != null) {
                val (test, run) = rs[watching.coerceIn(0, rs.lastIndex)]
                RobotStage(grammar, test.first, run, runs, Modifier.shake(if (rs.all { (t, r) -> r.accepted == t.second }) 0 else runs))
                if (run.accepted != test.second) {
                    val diag = if (test.second) RobotInterpreter.diagnose(table, run) else null
                    Feedback(
                        FeedbackKind.BAD,
                        (if (test.second) "O robô rejeitou uma frase válida. " else "O robô aceitou uma frase inválida. ") + (diag ?: ""),
                        title = "Teste \"${test.first.ifBlank { "ε" }}\" falhou",
                    )
                }
            }
            if (solved) {
                MascotSays("Todos os testes passaram! Você acabou de escrever um *parser descendente recursivo*. As condições dos seus ifs são a tabela LL(1).", Mood.CELEBRATE)
                GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            } else {
                CheckBar(confidence, { confidence = it }, ::check, label = "▶ Executar testes")
            }
            Spacer(Modifier.height(24.dp))
        }

        val p = editing
        Overlay(p != null, { editing = null }) {
            if (p != null) {
                Text("Ramo de ${p.lhs}()", style = MaterialTheme.typography.titleLarge, color = TextMain)
                Text(p.text, fontFamily = Mono, color = NonterminalColor)
                Spacer(Modifier.height(10.dp))
                Text("Entrar neste ramo quando o token for:", style = MaterialTheme.typography.labelMedium, color = TextDim)
                Spacer(Modifier.height(6.dp))
                ChipsRow {
                    tokens.forEach { t ->
                        val on = t in program[p.id].orEmpty()
                        Chip(t, on, {
                            program[p.id] = if (on) program[p.id].orEmpty() - t else program[p.id].orEmpty() + t
                            results = null
                        }, color = Tertiary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                GameButton("OK", { editing = null }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FunctionBlock(grammar: Grammar, nt: String, program: Map<Int, Set<String>>, enabled: Boolean, onEdit: (Production) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SurfaceHigh, RoundedCornerShape(12.dp)).border(1.dp, Outline, RoundedCornerShape(12.dp)).padding(10.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Secondary)) { append("def ") }
                withStyle(SpanStyle(color = NonterminalColor, fontWeight = FontWeight.Bold)) { append("$nt()") }
                append(":")
            },
            fontFamily = Mono, fontSize = 14.sp,
        )
        grammar.productionsOf(nt).forEachIndexed { i, p ->
            val set = program[p.id].orEmpty()
            Row(Modifier.padding(start = 14.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0) "if " else "elif ", color = Secondary, fontFamily = Mono, fontSize = 14.sp)
                Text("token in ", color = TextDim, fontFamily = Mono, fontSize = 14.sp)
                Box(
                    Modifier.testTag("branch:${p.id}")
                        .heightIn(min = 32.dp)
                        .background(Tertiary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, if (set.isEmpty()) Tertiary else Tertiary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { onEdit(p) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (set.isEmpty()) "{ ? }" else "{ " + grammar.sortTerminals(set).joinToString(", ") + " }",
                        color = TerminalColor, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Text(":", color = TextDim, fontFamily = Mono)
            }
            Text(
                buildAnnotatedString {
                    if (p.isEpsilon) {
                        withStyle(SpanStyle(color = Secondary)) { append("return") }
                        withStyle(SpanStyle(color = TextDim)) { append("   # ε") }
                    } else p.rhs.forEachIndexed { k, s ->
                        if (k > 0) append("; ")
                        if (grammar.isNonterminal(s)) withStyle(SpanStyle(color = NonterminalColor)) { append("$s()") }
                        else {
                            append("casa(")
                            withStyle(SpanStyle(color = TerminalColor)) { append("'$s'") }
                            append(")")
                        }
                    }
                },
                fontFamily = Mono, fontSize = 13.sp, color = TextMain, modifier = Modifier.padding(start = 34.dp, top = 2.dp),
            )
        }
        Row(Modifier.padding(start = 14.dp, top = 4.dp)) {
            Text("else", color = Secondary, fontFamily = Mono, fontSize = 14.sp)
            Text(": erro()", color = Danger, fontFamily = Mono, fontSize = 14.sp)
        }
    }
}

/** O robô andando pela fita, com a pilha de chamadas ao lado. */
@Composable
private fun RobotStage(grammar: Grammar, input: String, run: RobotRun, runId: Int, modifier: Modifier = Modifier) {
    var speed by remember { mutableIntStateOf(1) }
    var frame by remember(runId, input) { mutableIntStateOf(if (ThemeState.reduceMotion) run.frames.lastIndex else 0) }
    LaunchedEffect(runId, input, speed) {
        if (ThemeState.reduceMotion || speed == 0) { frame = run.frames.lastIndex; return@LaunchedEffect }
        while (frame < run.frames.lastIndex) {
            delay(520L / speed)
            frame++
        }
    }
    val f = run.frames[frame.coerceIn(0, run.frames.lastIndex)]
    val cell = 48.dp
    val robotX by animateDpAsState(cell * f.pos, tween(250))
    val mood = when (f.kind) {
        FrameKind.ACCEPT -> Mood.CELEBRATE
        FrameKind.ERROR, FrameKind.REJECT -> Mood.SAD
        FrameKind.BRANCH -> Mood.THINK
        else -> Mood.HAPPY
    }
    Panel(modifier, title = "Robô em ação: \"${input.ifBlank { "ε" }}\"", accent = Primary) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "1×", 3 to "3×", 0 to "⏭").forEach { (v, l) -> Chip(l, speed == v, { speed = v }) }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.height(46.dp)) {
                Mascot(mood, Modifier.offset(robotX + 4.dp, 0.dp), size = 42.dp)
            }
            Row {
                run.tokens.forEachIndexed { i, t ->
                    Box(
                        Modifier.padding(end = 4.dp).size(cell - 4.dp, 36.dp)
                            .background(if (i == f.pos) Primary.copy(alpha = 0.2f) else SurfaceHigh, RoundedCornerShape(8.dp))
                            .border(if (i == f.pos) 2.dp else 1.dp, if (i == f.pos) Primary else Outline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t, fontFamily = Mono, color = if (i < f.pos) TextDim else if (t == END) SpecialColor else TerminalColor)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.width(110.dp)) {
                Text("chamadas", style = MaterialTheme.typography.labelSmall, color = TextDim)
                f.calls.asReversed().forEachIndexed { i, c ->
                    Box(
                        Modifier.padding(top = 3.dp).fillMaxWidth().appear(c + f.calls.size)
                            .background(if (i == 0) NonterminalColor.copy(alpha = 0.18f) else SurfaceHigh, RoundedCornerShape(6.dp))
                            .border(1.dp, if (i == 0) NonterminalColor else Outline, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text(c, fontFamily = Mono, fontSize = 12.sp, color = NonterminalColor) }
                }
                if (f.calls.isEmpty()) Text("—", color = TextDim)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("passo ${frame + 1}/${run.frames.size}", style = MaterialTheme.typography.labelSmall, color = TextDim)
                Text(
                    f.text, style = MaterialTheme.typography.bodyMedium,
                    color = when (f.kind) { FrameKind.ERROR, FrameKind.REJECT -> Danger; FrameKind.ACCEPT -> Success; else -> TextMain },
                    modifier = Modifier.appear(frame),
                )
            }
        }
        if (frame < run.frames.lastIndex && speed != 0 && !ThemeState.reduceMotion) {
            Spacer(Modifier.height(6.dp))
            GameButton("Pular para o fim", { frame = run.frames.lastIndex }, Modifier.fillMaxWidth(), style = ButtonStyle.GHOST)
        }
    }
}
