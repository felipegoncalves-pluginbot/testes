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
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LLRule
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Grading
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.MistakeType
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
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Overlay
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.productionText
import com.felipe.compiladores.ui.components.symbolsText
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

@Composable
fun LL1TableGame(level: Level, spec: LevelSpec.LL1Table, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val an = remember(level.id) { Analysis(grammar) }
    val table = remember(level.id) { LL1Table(an) }
    val player = remember(level.id) { mutableStateMapOf<Pair<String, String>, Set<Int>>() }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var rowErrors by remember { mutableStateOf<Map<String, Int>?>(null) }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var solved by remember { mutableStateOf(false) }
    var explainChoice by remember { mutableStateOf<Int?>(null) }

    val truth: Map<Pair<String, String>, Set<Int>> = remember(level.id) {
        table.cells.mapValues { e -> e.value.map { it.production.id }.toSet() }
    }
    fun wrongCells() = (truth.keys + player.keys).filter { truth[it].orEmpty() != player[it].orEmpty() }

    val prompt = remember(level.id) {
        val entries = table.cells.flatMap { (k, v) -> v.map { k to it } }
        val pick = entries.firstOrNull { it.second.rule == LLRule.FOLLOW } ?: entries.firstOrNull()
        pick?.let { (k, e) ->
            val p = e.production
            ExplainPrompt(
                "Explique: por que (${p.id}) ${p.text} está em M[${k.first}, ${k.second}]?",
                listOf(
                    "Porque '${k.second}' ∈ FIRST(${p.rhsText})",
                    "Porque ${p.rhsText} pode virar ε e '${k.second}' ∈ FOLLOW(${k.first})",
                    "Porque é a primeira produção de ${k.first}",
                ),
                if (e.rule == LLRule.FIRST) setOf(0) else setOf(1),
                table.explainCell(k.first, k.second, p),
            )
        }
    }

    session.hintProvider = { n ->
        val wrong = wrongCells()
        when {
            wrong.isEmpty() && spec.askVerdict && verdict != table.isLL1 ->
                "A tabela está certa. Agora olhe: existe alguma célula com mais de uma produção?"
            wrong.isEmpty() -> "Tudo parece certo — aposte sua confiança e verifique!"
            n == 2 -> "Confira a linha de ${wrong.first().first}: há ${wrong.count { it.first == wrong.first().first }} célula(s) diferente(s)."
            else -> {
                val (nt, t) = wrong.first()
                val missing = truth[nt to t].orEmpty() - player[nt to t].orEmpty()
                val p = if (missing.isNotEmpty()) grammar.production(missing.first())
                else grammar.production((player[nt to t].orEmpty() - truth[nt to t].orEmpty()).first())
                table.explainCell(nt, t, p)
            }
        }
    }

    fun check() {
        val c = confidence ?: return
        val wrong = wrongCells()
        val verdictOk = !spec.askVerdict || verdict == table.isLL1
        val ok = wrong.isEmpty() && verdictOk
        session.check(c, ok)
        confidence = null
        rowErrors = grammar.nonterminals.associateWith { nt -> wrong.count { it.first == nt } }
        if (ok) {
            solved = true
            message = FeedbackKind.GOOD to (if (table.isLL1) "Tabela perfeita. Nenhum conflito: a gramática é LL(1)."
            else "Tabela perfeita e veredito certo. " + table.conflicts.joinToString(" ") { table.conflictDescription(it.first, it.second) })
        } else {
            val types = Grading.ll1Mistakes(table, player).distinct().toMutableList()
            if (!verdictOk) types += MistakeType.VERDICT_WRONG
            session.mistake(types)
            message = FeedbackKind.BAD to when {
                wrong.isEmpty() -> "A tabela está certa, mas o veredito não. Uma gramática é LL(1) se e só se nenhuma célula tem duas produções."
                else -> "${wrong.size} célula(s) diferente(s) do esperado (linhas marcadas). Pista: " +
                    types.take(2).joinToString("; ") { it.title.lowercase() } + "."
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GrammarCard(grammar, numbered = true)
            Panel(title = "FIRST e FOLLOW", accent = Tertiary, collapsible = true) { FirstFollowView(an) }
            Panel(Modifier.shake(session.mistakes), title = "Tabela M[A, a]", accent = NonterminalColor) {
                Text("Toque numa célula para escolher as produções (pelo número).", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Row {
                        HeaderCell(AnnotatedString("M"), width = 56.dp)
                        table.columns.forEach { HeaderCell(symbolsText(listOf(it), grammar)) }
                    }
                    grammar.nonterminals.forEach { nt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeaderCell(symbolsText(listOf(nt), grammar), width = 56.dp, height = 40.dp)
                            table.columns.forEach { t ->
                                val ids = player[nt to t].orEmpty().sorted()
                                GridCell(
                                    AnnotatedString(ids.joinToString(",")), selected = ids.isNotEmpty(),
                                    onClick = if (solved) null else ({ editing = nt to t }),
                                    status = if (ids.size > 1) CellStatus.CONFLICT else CellStatus.NEUTRAL,
                                    tag = "cell:$nt:$t",
                                )
                            }
                            val e = rowErrors?.get(nt)
                            if (e != null) Text(
                                if (e == 0) " ✓" else " ✗$e", color = if (e == 0) Success else Danger,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
            if (spec.askVerdict && !solved) {
                Panel(title = "Veredito", accent = Tertiary) {
                    Text("Esta gramática é LL(1)?", style = MaterialTheme.typography.bodyLarge, color = TextMain)
                    Spacer(Modifier.height(8.dp))
                    ChipsRow {
                        Chip("Sim, é LL(1)", verdict == true, { verdict = true }, color = Tertiary)
                        Chip("Não: há conflito", verdict == false, { verdict = false }, color = Tertiary)
                    }
                }
            }
            message?.let { (k, t) -> Feedback(k, t) }
            if (!solved) {
                CheckBar(confidence, { confidence = it }, ::check, enabled = !spec.askVerdict || verdict != null)
            } else if (prompt != null) {
                Panel(title = "Autoexplicação", accent = Tertiary) {
                    Text(prompt.question, style = MaterialTheme.typography.bodyLarge, color = TextMain)
                    Spacer(Modifier.height(8.dp))
                    ExplainOptions(prompt, explainChoice) { i ->
                        explainChoice = i
                        session.explanation(i in prompt.valid, MistakeType.TABLE_MISSED_FOLLOW)
                    }
                }
            }
            if (solved && (prompt == null || explainChoice != null)) {
                GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            }
            Spacer(Modifier.height(24.dp))
        }

        val cell = editing
        Overlay(cell != null, { editing = null }) {
            if (cell != null) {
                val (nt, t) = cell
                MonoText(symbolsText(listOf("M[", nt, ",", t, "]"), grammar), size = 18)
                Spacer(Modifier.height(4.dp))
                Text("Com $nt no topo e '$t' na entrada, qual produção usar?", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grammar.productionsOf(nt).forEach { p ->
                        val on = p.id in player[cell].orEmpty()
                        GameButton(
                            productionText(p, grammar, numbered = true),
                            {
                                val cur = player[cell].orEmpty()
                                player[cell] = if (on) cur - p.id else cur + p.id
                                rowErrors = null
                            },
                            Modifier.fillMaxWidth(), color = if (on) Success else TextDim,
                        )
                    }
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
