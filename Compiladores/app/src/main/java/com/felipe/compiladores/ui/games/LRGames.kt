package com.felipe.compiladores.ui.games

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.GrammarClasses
import com.felipe.compiladores.engine.Item
import com.felipe.compiladores.engine.LR0Automaton
import com.felipe.compiladores.engine.LRKind
import com.felipe.compiladores.engine.LRTable
import com.felipe.compiladores.engine.Transform
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Grading
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.ui.components.AutomatonView
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.CellStatus
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.Chip
import com.felipe.compiladores.ui.components.ChipsRow
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.FirstFollowView
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.GridCell
import com.felipe.compiladores.ui.components.HeaderCell
import com.felipe.compiladores.ui.components.LRTableView
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Overlay
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.itemText
import com.felipe.compiladores.ui.components.productionText
import com.felipe.compiladores.ui.components.symbolsText
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.Warning

/** Explica o conteúdo correto de uma célula da tabela SLR. */
fun explainSlrCell(a: LR0Automaton, table: LRTable, an: Analysis, s: Int, col: String): String {
    val truth = Grading.lrEntries(table, s, col)
    if (col in table.gotoColumns) {
        return truth.firstOrNull()?.let { "goto(I$s, $col) = I$it: a célula GOTO[$s, $col] vale $it." }
            ?: "Nenhum item de I$s tem • antes de $col: GOTO[$s, $col] fica vazia."
    }
    if (truth.isEmpty()) return "ACTION[$s, '$col'] fica vazia: nenhum item de I$s tem • antes de '$col' e nenhum item completo tem '$col' no FOLLOW."
    return truth.joinToString(" ") { e ->
        when {
            e == "acc" -> "I$s contém ${a.itemText(Item(0, 1))}: com $ na entrada, aceitar (acc)."
            e.startsWith("s") -> "I$s tem transição com '$col' para I${e.drop(1)}: ${e}."
            else -> {
                val p = a.prod(e.drop(1).toInt())
                "I$s tem o item completo ${a.itemText(Item(p.id, p.rhs.size))} e '$col' ∈ FOLLOW(${p.lhs}) = ${an.followText(p.lhs)}: $e."
            }
        }
    }
}

@Composable
fun SlrTableGame(level: Level, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val automaton = remember(level.id) { LR0Automaton(grammar) }
    val table = remember(level.id) { LRTable.slr(automaton) }
    val an = remember(level.id) { Analysis(automaton.grammar) }
    val player = remember(level.id) { mutableStateMapOf<Pair<Int, String>, Set<String>>() }
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var rowErrors by remember { mutableStateOf<Map<Int, Int>?>(null) }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var solved by remember { mutableStateOf(false) }
    val columns = table.actionColumns + table.gotoColumns

    fun wrongCells(): List<Pair<Int, String>> = (0 until table.stateCount).flatMap { s ->
        columns.filter { c -> Grading.lrEntries(table, s, c) != player[s to c].orEmpty() }.map { s to it }
    }

    session.hintProvider = { n ->
        val wrong = wrongCells()
        when {
            wrong.isEmpty() -> "Tudo parece certo — aposte sua confiança e verifique!"
            n == 2 -> "Confira a linha do estado ${wrong.first().first}: ${wrong.count { it.first == wrong.first().first }} célula(s) diferente(s)."
            else -> explainSlrCell(automaton, table, an, wrong.first().first, wrong.first().second)
        }
    }

    fun check() {
        val c = confidence ?: return
        val wrong = wrongCells()
        val ok = wrong.isEmpty()
        session.check(c, ok)
        confidence = null
        rowErrors = (0 until table.stateCount).associateWith { s -> wrong.count { it.first == s } }
        if (ok) {
            solved = true
            message = FeedbackKind.GOOD to "Tabela SLR completa e sem conflitos!"
        } else {
            val types = Grading.slrMistakes(table, player).distinct()
            session.mistake(types)
            message = FeedbackKind.BAD to "${wrong.size} célula(s) diferente(s) (linhas marcadas). Pista: " +
                types.take(2).joinToString("; ") { it.title.lowercase() } + "."
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GrammarCard(automaton.grammar, title = "Gramática aumentada", numbered = true)
            Panel(title = "Autômato LR(0)", accent = Tertiary, collapsible = true) { AutomatonView(automaton) }
            Panel(title = "FOLLOW", accent = Tertiary, collapsible = true) {
                FirstFollowView(Analysis(grammar), showFirst = false)
            }
            Panel(title = "ACTION | GOTO", accent = NonterminalColor) {
                Text("sN = empilhar e ir para N · rN = reduzir pela produção N · acc = aceitar", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Row {
                        HeaderCell(AnnotatedString("#"), width = 36.dp)
                        table.actionColumns.forEach { HeaderCell(symbolsText(listOf(it), grammar), width = 50.dp) }
                        Spacer(Modifier.width(6.dp))
                        table.gotoColumns.forEach { HeaderCell(symbolsText(listOf(it), grammar), width = 44.dp) }
                    }
                    for (s in 0 until table.stateCount) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeaderCell(AnnotatedString("$s"), width = 36.dp, height = 40.dp)
                            columns.forEachIndexed { i, col ->
                                if (i == table.actionColumns.size) Spacer(Modifier.width(6.dp))
                                val entries = player[s to col].orEmpty().sorted()
                                GridCell(
                                    AnnotatedString(entries.joinToString("\n")), selected = entries.isNotEmpty(),
                                    onClick = if (solved) null else ({ editing = s to col }),
                                    width = if (col in table.gotoColumns) 44.dp else 50.dp,
                                    height = if (entries.size > 1) 48.dp else 40.dp,
                                    status = if (entries.size > 1) CellStatus.CONFLICT else CellStatus.NEUTRAL,
                                    tag = "cell:$s:$col",
                                )
                            }
                            val e = rowErrors?.get(s)
                            if (e != null) Text(
                                if (e == 0) " ✓" else " ✗$e", color = if (e == 0) Success else Danger,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
            message?.let { (k, t) -> Feedback(k, t) }
            if (!solved) CheckBar(confidence, { confidence = it }, ::check)
            else GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            Spacer(Modifier.height(24.dp))
        }

        val cell = editing
        Overlay(cell != null, { editing = null }) {
            if (cell != null) {
                val (s, col) = cell
                val isGoto = col in table.gotoColumns
                val cur = player[cell].orEmpty()
                fun toggle(e: String) {
                    player[cell] = if (e in cur) cur - e else cur + e
                    rowErrors = null
                }
                Text(if (isGoto) "GOTO[$s, $col]" else "ACTION[$s, '$col']", style = MaterialTheme.typography.titleLarge, color = TextMain)
                Spacer(Modifier.height(8.dp))
                Panel(title = "I$s", accent = Tertiary) {
                    automaton.states[s].forEach { MonoText(itemText(automaton, it), size = 13) }
                }
                Spacer(Modifier.height(10.dp))
                if (isGoto) {
                    Text("Ir para o estado:", style = MaterialTheme.typography.labelMedium, color = TextDim)
                    Spacer(Modifier.height(6.dp))
                    ChipsRow { (0 until table.stateCount).forEach { n -> Chip("$n", "$n" in cur, { toggle("$n") }) } }
                } else {
                    Text("Empilhar e ir para:", style = MaterialTheme.typography.labelMedium, color = TextDim)
                    Spacer(Modifier.height(6.dp))
                    ChipsRow { (0 until table.stateCount).forEach { n -> Chip("s$n", "s$n" in cur, { toggle("s$n") }) } }
                    Spacer(Modifier.height(10.dp))
                    Text("Reduzir por:", style = MaterialTheme.typography.labelMedium, color = TextDim)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        grammar.productions.forEach { p ->
                            GameButton(productionText(p, grammar, numbered = true), { toggle("r${p.id}") }, Modifier.fillMaxWidth(),
                                color = if ("r${p.id}" in cur) Success else TextDim)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Chip("acc (aceitar)", "acc" in cur, { toggle("acc") }, color = Success)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton("Vazia", { player.remove(cell); rowErrors = null; editing = null }, Modifier.weight(1f), style = ButtonStyle.OUTLINED, color = Danger)
                    GameButton("OK", { editing = null }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SlrConflictGame(level: Level, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val automaton = remember(level.id) { LR0Automaton(grammar) }
    val table = remember(level.id) { LRTable.slr(automaton) }
    val conflicts = remember(level.id) { table.conflicts }
    val truthCells = remember(level.id) { conflicts.map { it.state to it.terminal }.toSet() }
    val truthShiftReduce = conflicts.any { it.isShiftReduce }
    var marks by remember { mutableStateOf(emptySet<Pair<Int, String>>()) }
    var type by remember { mutableStateOf<Boolean?>(null) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var solved by remember { mutableStateOf(false) }

    session.hintProvider = { n ->
        val c = conflicts.first()
        if (n == 2) "O conflito está no estado ${c.state}. Qual item completo desse estado e qual transição disputam a mesma coluna?"
        else table.conflictText(c)
    }

    fun check() {
        val c = confidence ?: return
        val ok = marks == truthCells && type == truthShiftReduce
        session.check(c, ok)
        confidence = null
        if (ok) {
            solved = true
            message = FeedbackKind.GOOD to conflicts.joinToString(" ") { table.conflictText(it) }
        } else {
            session.mistake(MistakeType.CONFLICT_MISSED)
            message = FeedbackKind.BAD to when {
                marks != truthCells -> "As células marcadas não são exatamente as de conflito (${marks.size} marcada(s)). Procure um estado com item completo A → α • e uma transição por um terminal de FOLLOW(A)."
                else -> "As células estão certas, mas o tipo do conflito não. Quais ações disputam a célula?"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GrammarCard(automaton.grammar, title = "Gramática aumentada", numbered = true)
        Panel(title = "Autômato LR(0)", accent = Tertiary, collapsible = true) { AutomatonView(automaton) }
        Panel(title = "FOLLOW", accent = Tertiary, collapsible = true) { FirstFollowView(Analysis(grammar), showFirst = false) }
        Panel(title = "Marque as células ACTION com conflito", accent = Warning) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row {
                    HeaderCell(AnnotatedString("#"), width = 36.dp)
                    table.actionColumns.forEach { HeaderCell(symbolsText(listOf(it), grammar)) }
                }
                for (s in 0 until table.stateCount) {
                    Row {
                        HeaderCell(AnnotatedString("$s"), width = 36.dp, height = 40.dp)
                        table.actionColumns.forEach { col ->
                            val on = (s to col) in marks
                            GridCell(
                                AnnotatedString(if (on) "⚡" else ""), selected = on,
                                onClick = if (solved) null else ({ marks = if (on) marks - (s to col) else marks + (s to col) }),
                                status = if (on) CellStatus.CONFLICT else CellStatus.NEUTRAL,
                                tag = "cell:$s:$col",
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Tipo do conflito:", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Spacer(Modifier.height(6.dp))
            ChipsRow {
                Chip("shift/reduce", type == true, { type = true }, color = Warning)
                Chip("reduce/reduce", type == false, { type = false }, color = Warning)
            }
        }
        message?.let { (k, t) -> Feedback(k, t) }
        if (!solved) CheckBar(confidence, { confidence = it }, ::check, enabled = type != null && marks.isNotEmpty())
        else {
            Panel(title = "Tabela SLR completa", accent = Primary, collapsible = true) {
                LRTableView(table)
            }
            GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ClassifyGame(level: Level, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val classes = remember(level.id) { GrammarClasses(grammar) }
    val labels = listOf("LL(1)") + LRKind.entries.map { it.label }
    val truth = remember(level.id) {
        buildSet {
            if (classes.isLL1) add("LL(1)")
            LRKind.entries.forEach { if (classes.belongs(it)) add(it.label) }
        }
    }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var solved by remember { mutableStateOf(false) }

    fun reason(label: String): String {
        if (label == "LL(1)") {
            val lr = Transform.leftRecursive(grammar)
            return when {
                classes.isLL1 -> "LL(1): nenhuma célula da tabela tem conflito."
                lr.isNotEmpty() -> "Não é LL(1): há recursão à esquerda em ${lr.joinToString()}. " +
                    (classes.ll1.conflicts.firstOrNull()?.let { classes.ll1.conflictDescription(it.first, it.second) } ?: "")
                else -> "Não é LL(1): " + (classes.ll1.conflicts.firstOrNull()?.let { classes.ll1.conflictDescription(it.first, it.second) } ?: "")
            }
        }
        val kind = LRKind.entries.first { it.label == label }
        val t = classes.table(kind)
        return if (!t.hasConflicts) "${kind.label}: tabela sem conflitos (${t.stateCount} estados)."
        else "Não é ${kind.label}: " + t.conflictText(t.conflicts.first())
    }

    session.hintProvider = { n ->
        if (n == 2) reason("LL(1)")
        else {
            val smallest = LRKind.entries.firstOrNull { classes.belongs(it) }
            smallest?.let { "A menor classe LR que aceita a gramática é ${it.label}. Lembre: LR(0) ⊂ SLR ⊂ LALR ⊂ LR(1)." }
                ?: "Nenhuma tabela LR, nem a LR(1), fica sem conflitos. O que isso diz sobre a gramática?"
        }
    }

    fun check() {
        val c = confidence ?: return
        val ok = picked == truth
        session.check(c, ok)
        confidence = null
        if (ok) {
            solved = true
            message = FeedbackKind.GOOD to if (truth.isEmpty()) "Nenhuma classe: a gramática é ambígua!" else "Classificação perfeita!"
        } else {
            session.mistake(MistakeType.CLASSIFY_WRONG)
            val wrong = labels.count { (it in picked) != (it in truth) }
            message = FeedbackKind.BAD to "$wrong classificação(ões) errada(s). Dica de consistência: se é LR(0), também é SLR, LALR e LR(1)."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GrammarCard(grammar, numbered = true, collapsible = false)
        Panel(title = "A quais classes ela pertence?", accent = Tertiary) {
            Text("Marque todas. Se nenhuma, deixe tudo desmarcado.", style = MaterialTheme.typography.bodySmall, color = TextDim)
            Spacer(Modifier.height(8.dp))
            ChipsRow {
                labels.forEach { l ->
                    Chip(l, l in picked, { if (!solved) picked = if (l in picked) picked - l else picked + l }, color = Tertiary)
                }
            }
        }
        message?.let { (k, t) -> Feedback(k, t) }
        if (!solved) CheckBar(confidence, { confidence = it }, ::check)
        else {
            Panel(title = "Por quê", accent = Success) {
                labels.forEach { l ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(if (l in truth) "✓ " else "✗ ", color = if (l in truth) Success else Danger, fontWeight = FontWeight.Bold)
                        Text(reason(l), style = MaterialTheme.typography.bodySmall, color = TextMain)
                    }
                }
            }
            GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
        }
        Spacer(Modifier.height(24.dp))
    }
}
