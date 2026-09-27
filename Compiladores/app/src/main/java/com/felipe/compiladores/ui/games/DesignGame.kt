package com.felipe.compiladores.ui.games

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.DesignCheck
import com.felipe.compiladores.game.DesignReport
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.game.Visual
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarWorkbench
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.SymbolTray
import com.felipe.compiladores.ui.components.WorkbenchState
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.lesson.VisualCard
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.level.SpecCard
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

/** Fábrica de Gramáticas: monte a gramática; a esteira roda os testes. */
@Composable
fun DesignGame(level: Level, spec: LevelSpec.Design, session: LevelSession, onSolved: () -> Unit) {
    val bench = remember(level.id) {
        WorkbenchState(spec.nonterminals, spec.nonterminals.associateWith { emptyList<List<String>>() }).apply {
            addAlternative(spec.nonterminals.first())
        }
    }
    val reference = remember(level.id) { Grammar.parse(spec.reference) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var report by remember { mutableStateOf<DesignReport?>(null) }
    var runs by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }

    session.hintProvider = { n ->
        val r = report ?: DesignCheck.check(bench.grammar(spec.nonterminals.first()), spec)
        if (n == 2) {
            val fail = r.tests.firstOrNull { !it.ok }
            val hidden = r.extra.firstOrNull { !it.ok }
            when {
                fail != null -> "O teste \"${fail.input.ifBlank { "ε" }}\" deveria ser ${if (fail.expected) "ACEITO" else "REJEITADO"}. Tente derivar essa frase à mão com a sua gramática."
                hidden != null -> hidden.detail
                else -> "Sua fábrica parece pronta. Rode os testes!"
            }
        } else {
            val nt = spec.nonterminals.firstOrNull { nt ->
                reference.productionsOf(nt).map { it.rhs }.toSet() != bench.alternatives(nt).toSet()
            }
            nt?.let { "Uma solução para $it: " + reference.productionsOf(it).joinToString("  |  ") { p -> p.text } }
                ?: "Sua gramática já é a de referência. Rode os testes!"
        }
    }

    fun check() {
        val c = confidence ?: return
        val r = DesignCheck.check(bench.grammar(spec.nonterminals.first()), spec)
        report = r
        runs++
        session.check(c, r.ok)
        confidence = null
        if (r.ok) solved = true
        else session.mistake(listOf(MistakeType.DESIGN_TEST) + r.extra.mapNotNull { if (!it.ok) it.mistake else null })
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpecCard(spec)
            Panel(title = "Sua fábrica", accent = NonterminalColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙ ${bench.productionCount} máquina(s) (produções)", style = MaterialTheme.typography.labelLarge, color = TextMain)
                    Spacer(Modifier.width(8.dp))
                    Text("recorde: ${spec.par}", style = MaterialTheme.typography.labelMedium, color = Tertiary)
                }
                Spacer(Modifier.height(8.dp))
                GrammarWorkbench(bench, enabled = !solved) { report = null }
            }
            val r = report
            if (r == null) {
                VisualCard(Visual.Conveyor)
            } else {
                TestBelt(r, runs, Modifier.shake(if (r.ok) 0 else runs))
                r.extra.takeIf { it.isNotEmpty() }?.let { ReportPanel("Controle de qualidade", it) }
                if (r.tests.all { it.ok } && r.extra.isEmpty()) {
                    Text("Todos os testes visíveis passaram!", color = Success, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (solved) {
                val record = bench.productionCount <= spec.par
                MascotSays(
                    if (record) "Fábrica aprovada com *${bench.productionCount}* máquinas — igual ao recorde! Enxuta e elegante."
                    else "Fábrica aprovada! Você usou *${bench.productionCount}* máquinas; dá para fazer com *${spec.par}*. Quer tentar otimizar depois?",
                    Mood.CELEBRATE,
                )
                GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            } else {
                CheckBar(confidence, { confidence = it }, ::check, label = "▶ Ligar a esteira (rodar testes)")
                GameButton("⟲ Começar do zero", { bench.reset(); bench.addAlternative(spec.nonterminals.first()); report = null }, Modifier.fillMaxWidth(), style = ButtonStyle.OUTLINED)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!solved) SymbolTray(bench, spec.nonterminals + spec.terminals) { report = null }
    }
}

/** Resultado dos testes como caixas saindo da esteira, uma de cada vez. */
@Composable
private fun TestBelt(report: DesignReport, runId: Int, modifier: Modifier = Modifier) {
    Panel(modifier, title = "Esteira de testes (${report.tests.count { it.ok }}/${report.tests.size})", accent = if (report.tests.all { it.ok }) Success else Danger) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.tests.forEachIndexed { i, t ->
                val color = if (t.ok) Success else Danger
                Row(
                    Modifier.fillMaxWidth().appear("$runId-$i", delayMs = i * 160)
                        .background(SurfaceHigh, RoundedCornerShape(10.dp)).border(1.dp, Outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).background(color, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(t.input.ifBlank { "ε" }, fontFamily = Mono, fontSize = 15.sp, color = TextMain, modifier = Modifier.weight(1f))
                    Text(
                        (if (t.expected) "deve aceitar" else "deve rejeitar") + " · " + (if (t.got) "aceitou" else "rejeitou"),
                        style = MaterialTheme.typography.labelMedium, color = if (t.ok) TextDim else Danger, fontWeight = if (t.ok) FontWeight.Normal else FontWeight.Bold,
                    )
                }
            }
        }
    }
}
