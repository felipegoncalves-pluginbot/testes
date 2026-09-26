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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.Transform
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.SurgeryCheck
import com.felipe.compiladores.game.SurgeryReport
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.symbolColor
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.SpecialColor
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Surface
import com.felipe.compiladores.ui.theme.SurfaceHigh
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurgeryGame(level: Level, spec: LevelSpec.Surgery, session: LevelSession, onSolved: () -> Unit) {
    val original = remember(level.id) { Grammar.parse(level.grammar) }
    val ntOrder = remember(level.id) { original.nonterminals + spec.newSymbols.filter { it !in original.nonterminals } }
    val rules = remember(level.id) {
        mutableStateMapOf<String, List<List<String>>>().apply {
            ntOrder.forEach { nt -> put(nt, original.productionsOf(nt).map { it.rhs }) }
        }
    }
    var selected by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var report by remember { mutableStateOf<SurgeryReport?>(null) }
    var solved by remember { mutableStateOf(false) }
    val reference = remember(level.id) { referenceSurgery(original, spec) }

    fun candidate(): Grammar = Transform.build(
        ntOrder.flatMap { nt -> rules[nt].orEmpty().map { nt to it } }, original.start,
    )

    fun edit(nt: String, i: Int, f: (List<String>) -> List<String>) {
        val alts = rules[nt].orEmpty().toMutableList()
        if (i !in alts.indices) return
        alts[i] = f(alts[i])
        rules[nt] = alts
        report = null
    }

    session.hintProvider = { n ->
        if (n == 2) {
            val lr = Transform.directLeftRecursive(original).firstOrNull { a -> rules[a].orEmpty().any { it.firstOrNull() == a } }
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
            val diff = ntOrder.firstOrNull { nt ->
                reference.productionsOf(nt).map { it.rhs }.toSet() != cand.productionsOf(nt).map { it.rhs }.toSet()
            }
            diff?.let { nt -> "Uma solução para $nt: " + reference.productionsOf(nt).joinToString("  |  ") { it.text } }
                ?: "Sua gramática já coincide com a solução de referência. Verifique!"
        }
    }

    fun check() {
        val c = confidence ?: return
        val r = SurgeryCheck.check(original, candidate(), spec)
        report = r
        session.check(c, r.ok)
        confidence = null
        if (r.ok) solved = true
        else session.mistake(r.lines.mapNotNull { if (!it.ok) it.mistake else null }.distinct())
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
                ntOrder.forEach { nt ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            "$nt →", fontFamily = Mono, color = NonterminalColor, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.width(52.dp).padding(top = 10.dp),
                        )
                        FlowRow(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rules[nt].orEmpty().forEachIndexed { i, alt ->
                                val isSel = selected == (nt to i)
                                AlternativeBox(alt, original, isSel, enabled = !solved, tag = "alt:$nt:$i",
                                    onSelect = { selected = nt to i },
                                    onRemoveSymbol = { k -> edit(nt, i) { it.filterIndexed { j, _ -> j != k } } })
                            }
                            if (!solved) Box(
                                Modifier.testTag("addalt:$nt").heightIn(min = 40.dp).background(SurfaceHigh, RoundedCornerShape(10.dp))
                                    .border(1.dp, Outline, RoundedCornerShape(10.dp))
                                    .clickable {
                                        rules[nt] = rules[nt].orEmpty() + listOf(emptyList())
                                        selected = nt to (rules[nt].orEmpty().size - 1)
                                        report = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("+ alt", color = Primary, fontSize = 13.sp) }
                        }
                    }
                }
            }
            report?.let { r ->
                Panel(title = "Laudo do verificador", accent = if (r.ok) Success else Danger) {
                    r.lines.forEach { line ->
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
            if (solved) {
                Feedback(FeedbackKind.GOOD, "Cirurgia bem-sucedida! A linguagem é a mesma, mas agora a gramática serve para um parser top-down.")
                GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            } else {
                CheckBar(confidence, { confidence = it }, ::check, label = "Testar cirurgia")
                GameButton("⟲ Restaurar original", {
                    ntOrder.forEach { nt -> rules[nt] = original.productionsOf(nt).map { it.rhs } }
                    selected = null; report = null
                }, Modifier.fillMaxWidth(), style = ButtonStyle.OUTLINED)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!solved) {
            SymbolTray(
                symbols = ntOrder + original.terminals,
                grammar = original,
                selected = selected,
                onSymbol = { s -> selected?.let { (nt, i) -> edit(nt, i) { it + s } } },
                onEpsilon = { selected?.let { (nt, i) -> edit(nt, i) { emptyList() } } },
                onBackspace = { selected?.let { (nt, i) -> edit(nt, i) { it.dropLast(1) } } },
                onDelete = {
                    selected?.let { (nt, i) ->
                        rules[nt] = rules[nt].orEmpty().filterIndexed { j, _ -> j != i }
                        selected = null; report = null
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlternativeBox(
    alt: List<String>,
    grammar: Grammar,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemoveSymbol: (Int) -> Unit,
    tag: String,
) {
    Row(
        Modifier.testTag(tag).heightIn(min = 40.dp)
            .background(if (selected) Primary.copy(alpha = 0.15f) else SurfaceHigh, RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Primary else Outline, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (alt.isEmpty()) Text(EPS, color = SpecialColor, fontFamily = Mono, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 6.dp))
        alt.forEachIndexed { k, s ->
            val c = symbolColor(grammar.takeIf { s in it.nonterminals } ?: null, s)
            Box(
                Modifier.padding(2.dp).widthIn(min = 26.dp)
                    .then(if (selected && enabled) Modifier.clickable { onRemoveSymbol(k) } else Modifier)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s, fontFamily = Mono, fontSize = 16.sp, color = c, fontWeight = if (s.first().isUpperCase()) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolTray(
    symbols: List<String>,
    grammar: Grammar,
    selected: Pair<String, Int>?,
    onSymbol: (String) -> Unit,
    onEpsilon: () -> Unit,
    onBackspace: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Surface).border(1.dp, Outline).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            if (selected == null) "Bandeja — selecione uma alternativa primeiro" else "Inserir em ${selected.first} (alternativa ${selected.second + 1})",
            style = MaterialTheme.typography.labelMedium, color = if (selected == null) TextDim else Primary,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            symbols.forEach { s ->
                TrayKey(s, symbolColor(if (s.first().isUpperCase()) null else grammar, s), selected != null) { onSymbol(s) }
            }
            TrayKey(EPS, SpecialColor, selected != null, onEpsilon)
            TrayKey("⌫", TextDim, selected != null, onBackspace)
            TrayKey("✕ alt", Danger, selected != null, onDelete)
        }
    }
}

@Composable
private fun TrayKey(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.testTag("tray:$label").heightIn(min = 40.dp).widthIn(min = 40.dp)
            .background(if (enabled) SurfaceHigh else SurfaceHigh.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .border(1.dp, if (enabled) color.copy(alpha = 0.6f) else Outline, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Mono, fontSize = 16.sp, color = if (enabled) color else TextDim, fontWeight = FontWeight.Bold)
    }
}
