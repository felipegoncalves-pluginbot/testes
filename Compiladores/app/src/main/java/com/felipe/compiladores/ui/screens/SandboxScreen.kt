package com.felipe.compiladores.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Earley
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.GrammarClasses
import com.felipe.compiladores.engine.LRKind
import com.felipe.compiladores.engine.LRTable
import com.felipe.compiladores.engine.Transform
import com.felipe.compiladores.engine.tokenizeInput
import com.felipe.compiladores.engine.DerivationMode
import com.felipe.compiladores.game.Grammars
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.SurgeryCheck
import com.felipe.compiladores.ui.components.AutomatonView
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.Chip
import com.felipe.compiladores.ui.components.ChipsRow
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.FirstFollowView
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.LLTableView
import com.felipe.compiladores.ui.components.LRTableView
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

/** Estado do laboratório, mantido fora da tela para sobreviver à navegação. */
class SandboxModel {
    var text by mutableStateOf(Grammars.EXPR_LR)
    var charMode by mutableStateOf(false)
    var input by mutableStateOf("id + id * id")
    var tab by mutableStateOf("Resumo")
}

private val TABS = listOf("Resumo", "FIRST/FOLLOW", "LL(1)", "LR(0)", "SLR", "LALR", "LR(1)", "Jogar")

@Composable
fun SandboxScreen(model: SandboxModel, onBack: () -> Unit, onPlay: (Level) -> Unit) {
    val parsed = remember(model.text, model.charMode) { Grammar.tryParse(model.text, model.charMode) }
    val grammar = parsed.grammar
    val classes = remember(grammar) { grammar?.let { runCatching { GrammarClasses(it) }.getOrNull() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Laboratório livre", "Sua gramática, suas tabelas, seu parser", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(title = "Gramática", accent = Primary) {
                Text(
                    "Uma regra por linha: A -> α | β. Símbolos separados por espaço. Use ε ou eps para vazio. Não-terminais = quem aparece à esquerda.",
                    style = MaterialTheme.typography.bodySmall, color = TextDim,
                )
                Spacer(Modifier.height(8.dp))
                CodeField(model.text, { model.text = it }, minHeight = 130)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(model.charMode, { model.charMode = it })
                    Text("  Cada caractere é um símbolo (ex.: S -> aSb)", style = MaterialTheme.typography.bodySmall, color = TextMain)
                }
                Spacer(Modifier.height(8.dp))
                Text("Exemplos", style = MaterialTheme.typography.labelMedium, color = TextDim)
                Spacer(Modifier.height(4.dp))
                ChipsRow {
                    Grammars.examples.forEach { (name, g) -> Chip(name, model.text == g, { model.text = g; model.charMode = false }) }
                }
            }
            parsed.errors.forEach { Feedback(FeedbackKind.BAD, it) }
            parsed.warnings.forEach { Feedback(FeedbackKind.WARN, it) }
            if (grammar != null && classes != null) {
                ChipsRow { TABS.forEach { t -> Chip(t, model.tab == t, { model.tab = t }, color = Tertiary) } }
                when (model.tab) {
                    "Resumo" -> Summary(grammar, classes)
                    "FIRST/FOLLOW" -> Panel(title = "FIRST e FOLLOW", accent = Tertiary) { FirstFollowView(classes.analysis) }
                    "LL(1)" -> {
                        GrammarCard(grammar, numbered = true)
                        Panel(title = "Tabela LL(1)", accent = Tertiary) { LLTableView(classes.ll1) }
                        classes.ll1.conflicts.forEach { (a, t) -> Feedback(FeedbackKind.WARN, classes.ll1.conflictDescription(a, t)) }
                    }
                    "LR(0)" -> Panel(title = "Autômato LR(0) (${classes.lr0Automaton.states.size} estados)", accent = Tertiary) {
                        AutomatonView(classes.lr0Automaton)
                    }
                    "SLR", "LALR", "LR(1)" -> {
                        val kind = when (model.tab) { "SLR" -> LRKind.SLR; "LALR" -> LRKind.LALR; else -> LRKind.LR1 }
                        val table = classes.table(kind)
                        GrammarCard(classes.lr0Automaton.grammar, title = "Gramática aumentada", numbered = true)
                        if (kind == LRKind.LALR) {
                            Panel(title = "Estados LALR (itens com lookahead)", accent = Tertiary, collapsible = true, initiallyOpen = false) {
                                AutomatonView(classes.lr0Automaton, lookaheads = LRTable.lalrLookaheads(classes.lr0Automaton, classes.lr1Automaton))
                            }
                        }
                        Panel(title = "Tabela ${kind.label} (${table.stateCount} estados)", accent = Tertiary) { LRTableView(table) }
                        table.conflicts.forEach { Feedback(FeedbackKind.WARN, table.conflictText(it)) }
                        if (!table.hasConflicts) Feedback(FeedbackKind.GOOD, "Sem conflitos: a gramática é ${kind.label}.")
                    }
                    "Jogar" -> PlayTab(model, grammar, classes, onPlay)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Summary(g: Grammar, c: GrammarClasses) {
    Panel(title = "Resumo", accent = Primary) {
        Line("Não-terminais", g.nonterminals.joinToString(" "))
        Line("Terminais", g.terminals.joinToString(" "))
        Line("Anuláveis", c.analysis.nullable.joinToString(" ").ifEmpty { "nenhum" })
        Line("Recursão à esquerda", Transform.leftRecursive(g).joinToString(" ").ifEmpty { "nenhuma" })
        Line("Prefixo comum", SurgeryCheck.commonPrefixes(g).joinToString(" ").ifEmpty { "nenhum" })
        val unreach = Transform.unreachable(g)
        val unprod = Transform.unproductive(g)
        if (unreach.isNotEmpty()) Line("Inalcançáveis", unreach.joinToString(" "))
        if (unprod.isNotEmpty()) Line("Improdutivos", unprod.joinToString(" "))
    }
    Panel(title = "Classes", accent = Tertiary) {
        ClassLine("LL(1)", c.isLL1, c.ll1.conflicts.firstOrNull()?.let { c.ll1.conflictDescription(it.first, it.second) })
        LRKind.entries.forEach { k ->
            val t = c.table(k)
            ClassLine(k.label, !t.hasConflicts, t.conflicts.firstOrNull()?.let { t.conflictText(it) })
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", color = TextDim, style = MaterialTheme.typography.labelLarge)
        Text(value, color = TextMain, fontFamily = Mono)
    }
}

@Composable
private fun ClassLine(label: String, ok: Boolean, why: String?) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(if (ok) "✓ " else "✗ ", color = if (ok) Success else Danger, fontWeight = FontWeight.Bold)
            Text(label, color = TextMain, style = MaterialTheme.typography.titleSmall)
        }
        if (!ok && why != null) Text(why, style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

@Composable
private fun PlayTab(model: SandboxModel, g: Grammar, c: GrammarClasses, onPlay: (Level) -> Unit) {
    val tokens = tokenizeInput(model.input, g)
    val inLanguage = remember(g, model.input) { runCatching { Earley(g).derives(listOf(g.start), tokens) }.getOrDefault(false) }
    Panel(title = "Cadeia de entrada", accent = Primary) {
        Text("Tokens separados por espaço (ou colados, se todos os terminais têm 1 caractere).", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Spacer(Modifier.height(8.dp))
        CodeField(model.input, { model.input = it }, minHeight = 48)
        Spacer(Modifier.height(8.dp))
        val unknown = tokens.filter { it !in g.terminals }
        when {
            unknown.isNotEmpty() -> Feedback(FeedbackKind.WARN, "Tokens que não são terminais da gramática: ${unknown.joinToString()}.")
            inLanguage -> Feedback(FeedbackKind.GOOD, "A cadeia pertence à linguagem.")
            else -> Feedback(FeedbackKind.INFO, "A cadeia NÃO pertence à linguagem — ótimo para treinar detecção de erro.")
        }
    }
    fun level(title: String, spec: LevelSpec) = Level(
        "sandbox", title, "Laboratório: \"${model.input}\"",
        "Você executou o algoritmo à mão numa gramática sua. Compare com a tabela para conferir cada decisão.",
        "Consulte a tabela na tela.", g.toText(), spec,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameButton(
            "🤖 Parser top-down LL(1)", { onPlay(level("Top-down livre", LevelSpec.LL1Parse(model.input, showTable = true))) },
            Modifier.fillMaxWidth(), enabled = c.isLL1 && tokens.all { it in g.terminals },
        )
        if (!c.isLL1) Text("Indisponível: a gramática não é LL(1).", style = MaterialTheme.typography.bodySmall, color = TextDim)
        GameButton(
            "🏗️ Parser shift-reduce SLR", { onPlay(level("Bottom-up livre", LevelSpec.ShiftReduce(model.input, showStates = true, showTable = true))) },
            Modifier.fillMaxWidth(), enabled = c.isSLR && tokens.all { it in g.terminals }, color = Tertiary,
        )
        if (!c.isSLR) Text("Indisponível: a gramática não é SLR(1).", style = MaterialTheme.typography.bodySmall, color = TextDim)
        GameButton(
            "🔧 Derivação mais à esquerda", { onPlay(level("Derivação livre", LevelSpec.Derive(model.input, DerivationMode.LEFTMOST))) },
            Modifier.fillMaxWidth(), enabled = inLanguage && tokens.isNotEmpty(), style = ButtonStyle.OUTLINED,
        )
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, minHeight: Int) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = minHeight.dp)
            .background(SurfaceHigh, RoundedCornerShape(12.dp))
            .border(1.dp, Outline, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        BasicTextField(
            value, onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = TextMain, lineHeight = 22.sp),
            cursorBrush = SolidColor(Primary),
        )
    }
}
