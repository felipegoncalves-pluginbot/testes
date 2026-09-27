package com.felipe.compiladores.ui.games

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.Transform
import com.felipe.compiladores.game.CheckLine
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.game.SurgeryCheck
import com.felipe.compiladores.game.SurgeryReport
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.GrammarWorkbench
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.SymbolTray
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.WorkbenchState
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim

/** Solução de referência: elimina recursão direta e fatora prefixos, usando os nomes sugeridos. */
fun referenceSurgery(g: Grammar, spec: LevelSpec.Surgery): Grammar {
    var fixed = g
    val names = spec.newSymbols.toMutableList()
    for (a in g.nonterminals) {
        if (a in Transform.directLeftRecursive(fixed) && names.isNotEmpty()) {
            fixed = Transform.eliminateDirectLeftRecursion(fixed, a, names.removeAt(0))
        }
    }
    for (a in g.nonterminals) {
        if (a in SurgeryCheck.commonPrefixes(fixed) && names.isNotEmpty()) fixed = Transform.leftFactor(fixed, a, names.removeAt(0))
    }
    return fixed
}

@Composable
fun SurgeryGame(level: Level, spec: LevelSpec.Surgery, session: LevelSession, onSolved: () -> Unit) {
    val original = remember(level.id) { Grammar.parse(level.grammar) }
    val bench = remember(level.id) {
        val order = original.nonterminals + spec.newSymbols.filter { it !in original.nonterminals }
        WorkbenchState(order, order.associateWith { nt -> original.productionsOf(nt).map { it.rhs } })
    }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var report by remember { mutableStateOf<SurgeryReport?>(null) }
    var solved by remember { mutableStateOf(false) }
    var shakes by remember { mutableIntStateOf(0) }
    val reference = remember(level.id) { referenceSurgery(original, spec) }

    fun candidate(): Grammar = bench.grammar(original.start) ?: Transform.build(emptyList(), original.start)

    session.hintProvider = { n ->
        if (n == 2) {
            val lr = Transform.directLeftRecursive(original).firstOrNull { a -> bench.alternatives(a).any { it.firstOrNull() == a } }
            val pre = SurgeryCheck.commonPrefixes(original).firstOrNull()
            when {
                lr != null -> {
                    val alphas = original.productionsOf(lr).filter { it.rhs.firstOrNull() == lr }.map { it.rhs.drop(1).joinToString(" ") }
                    val betas = original.productionsOf(lr).filter { it.rhs.firstOrNull() != lr }.map { it.rhsText }
                    "Em $lr: α = ${alphas.joinToString(" ou ")} (o que vem depois do $lr recursivo) e β = ${betas.joinToString(" ou ")}. Receita: $lr → β $lr' e $lr' → α $lr' | ε."
                }
                pre != null -> "Em $pre, as alternativas que começam iguais devem virar uma só: $pre → prefixo ${spec.newSymbols.firstOrNull() ?: "X"}, e o novo não-terminal escolhe o resto."
                else -> "Compare a sua gramática com os critérios do verificador: qual falhou?"
            }
        } else {
            val cand = candidate()
            val diff = bench.ntOrder.firstOrNull { nt ->
                reference.productionsOf(nt).map { it.rhs }.toSet() != cand.productionsOf(nt).map { it.rhs }.toSet()
            }
            diff?.let { nt -> "Uma solução para $nt: " + reference.productionsOf(nt).joinToString("  |  ") { it.text } }
                ?: "Sua gramática já coincide com a solução de referência. Verifique!"
        }
    }

    fun check() {
        val c = confidence ?: return
        val g = bench.grammar(original.start)
        val r = if (g == null) SurgeryReport(listOf(CheckLine("Símbolo inicial", false, "${original.start} precisa ter pelo menos uma produção.", null)))
        else SurgeryCheck.check(original, g, spec)
        report = r
        session.check(c, r.ok)
        confidence = null
        if (r.ok) solved = true
        else {
            shakes++
            session.mistake(r.lines.mapNotNull { if (!it.ok) it.mistake else null }.distinct())
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GrammarCard(original, title = "Paciente (gramática original)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("sem recursão à esquerda", Primary)
                Tag("mesma linguagem", Primary)
                if (spec.requireLL1) Tag("LL(1)", Tertiary)
                if (spec.requireNoCommonPrefix) Tag("sem prefixo comum", Tertiary)
            }
            Panel(title = "Mesa de cirurgia", accent = NonterminalColor) {
                Text(
                    "Toque numa alternativa para selecioná-la; use a bandeja abaixo para inserir símbolos. Toque num símbolo da alternativa selecionada para removê-lo.",
                    style = MaterialTheme.typography.bodySmall, color = TextDim,
                )
                Spacer(Modifier.height(10.dp))
                GrammarWorkbench(bench, enabled = !solved) { report = null }
            }
            report?.let { r -> ReportPanel("Laudo do verificador", r.lines, Modifier.shake(shakes)) }
            if (solved) {
                MascotSays("Cirurgia bem-sucedida! A linguagem é a mesma, mas agora a gramática serve para um parser top-down.", Mood.CELEBRATE)
                GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            } else {
                CheckBar(confidence, { confidence = it }, ::check, label = "Testar cirurgia")
                GameButton("⟲ Restaurar original", { bench.reset(); report = null }, Modifier.fillMaxWidth(), style = ButtonStyle.OUTLINED)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!solved) SymbolTray(bench, bench.ntOrder + original.terminals) { report = null }
    }
}

/** Lista de verificações ✓/✗ (laudo da cirurgia, testes ocultos da fábrica...). */
@Composable
fun ReportPanel(title: String, lines: List<CheckLine>, modifier: Modifier = Modifier) {
    val ok = lines.all { it.ok }
    Panel(modifier, title = title, accent = if (ok) Success else Danger) {
        lines.forEach { line ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(if (line.ok) "✓ " else "✗ ", color = if (line.ok) Success else Danger, fontWeight = FontWeight.Bold)
                Column {
                    Text(line.label, style = MaterialTheme.typography.titleSmall, color = if (line.ok) Success else Danger)
                    Text(line.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
