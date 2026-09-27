package com.felipe.compiladores.ui.lesson

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.DerivationMode
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LLParser
import com.felipe.compiladores.engine.LR0Automaton
import com.felipe.compiladores.engine.LRParser
import com.felipe.compiladores.engine.LRTable
import com.felipe.compiladores.engine.tokenizeInput
import com.felipe.compiladores.game.Magnets
import com.felipe.compiladores.game.Visual
import com.felipe.compiladores.ui.components.InputTape
import com.felipe.compiladores.ui.components.LLTableView
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.ParseTreeView
import com.felipe.compiladores.ui.components.StackView
import com.felipe.compiladores.ui.components.SymbolBox
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.components.itemText
import com.felipe.compiladores.ui.components.productionText
import com.felipe.compiladores.ui.components.rememberLoopStep
import com.felipe.compiladores.ui.components.symbolColor
import com.felipe.compiladores.ui.components.symbolsText
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.DotColor
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.SpecialColor
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Surface
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.TerminalColor
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.ThemeState

/** Moldura das ilustrações. */
@Composable
fun VisualCard(visual: Visual, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .background(Surface, RoundedCornerShape(18.dp))
            .border(1.dp, Outline, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        VisualView(visual)
    }
}

@Composable
fun VisualView(visual: Visual) {
    when (visual) {
        is Visual.Derivation -> DerivationVisual(visual)
        is Visual.FirstQueue -> FirstQueueVisual(visual)
        is Visual.FollowSpot -> FollowSpotVisual(visual)
        is Visual.StackOps -> StackOpsVisual(visual)
        is Visual.LLRun -> LLRunVisual(visual)
        is Visual.SRRun -> SRRunVisual(visual)
        is Visual.TableLookup -> TableLookupVisual(visual)
        Visual.LeftRecursion -> LeftRecursionVisual()
        is Visual.DotWalk -> DotWalkVisual(visual)
        is Visual.Closure -> ClosureVisual(visual)
        Visual.Hierarchy -> HierarchyVisual()
        is Visual.TwoTrees -> TwoTreesVisual(visual)
        is Visual.Code -> CodeVisual(visual)
        Visual.Conveyor -> ConveyorVisual()
        Visual.Legend -> LegendVisual()
    }
}

@Composable
private fun Caption(text: String, color: Color = TextDim) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

// ------------------------------------------------------------------ derivação passo a passo

@Composable
private fun DerivationVisual(v: Visual.Derivation) {
    val grammar = remember(v) { Grammar.parse(v.grammar) }
    val forms = remember(v) { v.forms.map { f -> f.split(" ").filter { it.isNotBlank() && it != EPS } } }
    val step = rememberLoopStep(forms.size, 1100)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        forms.take(step + 1).forEachIndexed { i, form ->
            val next = forms.getOrNull(i + 1)
            val changed = if (next != null && i < step) changedPosition(grammar, form, next) else null
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(i)) {
                Text(if (i == 0) "  " else "⇒ ", color = TextDim, fontFamily = Mono)
                if (form.isEmpty()) Text("ε", color = SpecialColor, fontFamily = Mono)
                form.forEachIndexed { k, s ->
                    SymbolBox(s, grammar, highlight = k == changed && grammar.isNonterminal(s))
                }
            }
        }
    }
}

/** Posição do não-terminal que foi trocado para ir de [form] a [next]. */
private fun changedPosition(g: Grammar, form: List<String>, next: List<String>): Int? {
    val tail = form.size - 1
    return form.indices.firstOrNull { pos ->
        val after = tail - pos
        g.isNonterminal(form[pos]) && next.size >= pos + after &&
            next.subList(0, pos) == form.subList(0, pos) &&
            next.subList(next.size - after, next.size) == form.subList(pos + 1, form.size)
    }
}

// ------------------------------------------------------------------ fila do FIRST

@Composable
private fun FirstQueueVisual(v: Visual.FirstQueue) {
    val steps = v.vanish.size + 2
    val step = rememberLoopStep(steps, 1200)
    val vanished = v.vanish.sorted().take(step.coerceAtMost(v.vanish.size)).toSet()
    val winner = v.rhs.indices.firstOrNull { it !in v.vanish }
    val showWinner = step >= v.vanish.size + 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${v.lhs} → ", color = NonterminalColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            v.rhs.forEachIndexed { i, s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (i in vanished) "ε" else " ", color = SpecialColor, fontSize = 12.sp)
                    Box(Modifier.alpha(if (i in vanished) 0.25f else 1f)) {
                        SymbolBox(s, null, highlight = showWinner && i == winner)
                    }
                }
            }
        }
        Text(
            when {
                step == 0 -> "Quem é o primeiro da fila?"
                !showWinner -> "${v.rhs[v.vanish.sorted()[step - 1]]} pode sumir… o próximo avança!"
                winner != null -> "FIRST(${v.lhs}) recebe o que ${v.rhs[winner]} traz na frente."
                else -> "Todos somem: ε ∈ FIRST(${v.lhs})."
            },
            color = if (showWinner) Primary else TextMain, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.appear(step),
        )
    }
}

// ------------------------------------------------------------------ holofote do FOLLOW

@Composable
private fun FollowSpotVisual(v: Visual.FollowSpot) {
    val step = rememberLoopStep(3, 1300)
    val atEnd = v.index == v.rhs.lastIndex
    val glow = if (ThemeState.reduceMotion) 0.35f
    else rememberInfiniteTransition().animateFloat(0.2f, 0.6f, infiniteRepeatable(tween(900), RepeatMode.Reverse)).value
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.then(
                    if (atEnd && step >= 1) Modifier.background(Tertiary.copy(alpha = glow), RoundedCornerShape(8.dp)) else Modifier,
                ),
            ) { Text(" ${v.lhs} ", color = NonterminalColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            Text(" → ", color = TextDim, fontFamily = Mono, fontSize = 18.sp)
            v.rhs.forEachIndexed { i, s ->
                val spot = i == v.index
                val neighbor = i == v.index + 1 && step >= 1
                Box(
                    Modifier.then(
                        if (spot) Modifier.background(Primary.copy(alpha = glow), RoundedCornerShape(10.dp)) else Modifier,
                    ),
                ) { SymbolBox(s, null, highlight = neighbor) }
            }
        }
        val target = v.rhs[v.index]
        Text(
            when {
                step == 0 -> "Holofote em $target: quem pode vir logo depois dele?"
                !atEnd -> "O vizinho da direita! FIRST(${v.rhs[v.index + 1]}) entra em FOLLOW($target)."
                else -> "$target está no fim: quem segue ${v.lhs} também segue $target. FOLLOW(${v.lhs}) ⊆ FOLLOW($target)."
            },
            color = if (step == 0) TextMain else Primary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.appear(step),
        )
    }
}

// ------------------------------------------------------------------ pilha

@Composable
private fun StackOpsVisual(v: Visual.StackOps) {
    val step = rememberLoopStep(v.ops.size + 1, 900)
    val stack = remember(v, step) {
        val st = mutableListOf<String>()
        v.ops.take(step).forEach { op -> if (op == "-") { if (st.isNotEmpty()) st.removeAt(st.lastIndex) } else st += op.drop(1) }
        st.toList()
    }
    val last = v.ops.getOrNull(step - 1)
    Row(verticalAlignment = Alignment.Bottom) {
        Column(
            Modifier.width(76.dp).border(2.dp, Outline, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            stack.asReversed().forEachIndexed { i, s ->
                Box(Modifier.appear("$i-$s-${stack.size}")) { SymbolBox(s, null, highlight = i == 0) }
            }
            if (stack.isEmpty()) Text("vazia", color = TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("topo ↑", color = Primary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    last == null -> "pilha pronta"
                    last == "-" -> "desempilha!"
                    else -> "empilha ${last.drop(1)}"
                },
                color = TextMain, style = MaterialTheme.typography.titleMedium, modifier = Modifier.appear(step),
            )
        }
    }
}

// ------------------------------------------------------------------ parsers rodando

@Composable
private fun LLRunVisual(v: Visual.LLRun) {
    val grammar = remember(v) { Grammar.parse(v.grammar) }
    val parser = remember(v) { LLParser(LL1Table(Analysis(grammar))) }
    val run = remember(v) { parser.run(tokenizeInput(v.input, grammar)) }
    val step = rememberLoopStep(run.size, 1100)
    val s = run[step]
    val prev = run.getOrNull(step - 1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StackView(s.stackTopFirst, grammar, topAtLeft = true, keys = s.stack.asReversed())
        InputTape(s.tokens, s.pos, grammar)
        Caption(
            if (prev == null) "Início: pilha = ${grammar.start} $" else "Passo $step: " + parser.explain(prev).substringBefore(". ").take(90),
            Primary,
        )
    }
}

@Composable
private fun SRRunVisual(v: Visual.SRRun) {
    val grammar = remember(v) { Grammar.parse(v.grammar) }
    val parser = remember(v) { LRParser(LRTable.slr(LR0Automaton(grammar))) }
    val run = remember(v) { parser.run(tokenizeInput(v.input, grammar)) }
    val step = rememberLoopStep(run.size, 1100)
    val s = run[step]
    val prev = run.getOrNull(step - 1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StackView(s.stackSymbols, grammar, topAtLeft = false, keys = s.nodes)
        InputTape(s.tokens, s.pos, grammar)
        Caption(if (prev == null) "Início: pilha vazia" else parser.moveText(parser.validMoves(prev).first()).replaceFirstChar { it.uppercase() }, Primary)
        ParseTreeView(s.tree, s.nodes, grammar, leavesAtBottom = true)
    }
}

@Composable
private fun TableLookupVisual(v: Visual.TableLookup) {
    val grammar = remember(v) { Grammar.parse(v.grammar) }
    val table = remember(v) { LL1Table(Analysis(grammar)) }
    val cells = remember(v) { table.cells.keys.toList().take(6) }
    val step = rememberLoopStep(cells.size, 1300, holdLast = 0)
    val cell = cells[step]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LLTableView(table, highlight = cell)
        MonoText(
            buildAnnotatedString {
                append("M[")
                append(symbolsText(listOf(cell.first), grammar))
                append(", ")
                append(symbolsText(listOf(cell.second), grammar))
                append("] = ")
                append(productionText(table.productions(cell.first, cell.second).first(), grammar))
            },
            size = 14, modifier = Modifier.appear(step),
        )
    }
}

// ------------------------------------------------------------------ recursão à esquerda

@Composable
private fun LeftRecursionVisual() {
    val step = rememberLoopStep(6, 700, holdLast = 1)
    val spin = if (ThemeState.reduceMotion) 0f
    else rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing))).value
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            SymbolBox("E", null, highlight = true)
            repeat(step) { k ->
                Box(Modifier.appear(k)) { Row { SymbolBox("+", null); SymbolBox("T", null) } }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⟳", color = Danger, fontSize = 26.sp, modifier = Modifier.graphicsLayer { rotationZ = spin })
            Spacer(Modifier.width(8.dp))
            Text("E → E + T → E + T + T → … e nenhum token foi lido!", color = Danger, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ------------------------------------------------------------------ itens LR

@Composable
private fun DotWalkVisual(v: Visual.DotWalk) {
    val step = rememberLoopStep(v.rhs.size + 1, 1100)
    val seen = v.rhs.take(step)
    val rest = v.rhs.drop(step)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${v.lhs} → ", color = NonterminalColor, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            seen.forEach { SymbolBox(it, null) }
            Text(" • ", color = DotColor, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.appear(step))
            rest.forEach { Box(Modifier.alpha(0.55f)) { SymbolBox(it, null) } }
        }
        Text(
            "já vi: ${seen.joinToString(" ").ifEmpty { "nada" }}   ·   espero: ${rest.joinToString(" ").ifEmpty { "nada — hora de reduzir!" }}",
            color = if (rest.isEmpty()) Success else TextMain, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ClosureVisual(v: Visual.Closure) {
    val automaton = remember(v) { LR0Automaton(Grammar.parse(v.grammar)) }
    val items = automaton.states[0]
    val step = rememberLoopStep(items.size, 1100)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Caption("closure({ ${automaton.itemText(items[0])} })", Tertiary)
        items.take(step + 1).forEachIndexed { i, it ->
            Box(Modifier.appear(i)) { MonoText(itemText(automaton, it), size = 15) }
        }
        val last = items[step]
        val next = automaton.next(last)
        Caption(
            if (next != null && automaton.grammar.isNonterminal(next)) "• antes de $next → puxa as regras de $next" else "nada novo para puxar",
            Primary,
        )
    }
}

// ------------------------------------------------------------------ hierarquia

@Composable
private fun HierarchyVisual() {
    val step = rememberLoopStep(5, 900)
    val layers = listOf("LR(1)" to Primary, "LALR(1)" to Tertiary, "SLR(1)" to Secondary, "LR(0)" to Success)
    val total = 230.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(total)) {
        val w = maxWidth
        layers.forEachIndexed { i, (name, color) ->
            if (i <= step) {
                // Cada caixa desce 28dp e entra 12dp: o rótulo fica na faixa livre do topo.
                val dx = (12 * i).dp
                val dy = (28 * i).dp
                Box(
                    Modifier.offset(dx, dy).size(w - dx * 2, total - dy - (8 * i).dp)
                        .appear(i)
                        .background(color.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                        .border(2.dp, color, RoundedCornerShape(16.dp)),
                ) {
                    Text(name, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp, top = 5.dp))
                }
            }
        }
        if (step >= 4) {
            // LL(1): dentro do LR(1), cruzando as outras caixas.
            Box(Modifier.offset(w * 0.55f, 36.dp).size(w * 0.4f, 170.dp).appear("ll")) {
                Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(
                        NonterminalColor, Offset.Zero, size, CornerRadius(24f),
                        style = Stroke(5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                }
                Text(
                    "LL(1)", color = NonterminalColor, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 6.dp)
                        .background(Surface, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ ambiguidade

@Composable
private fun TwoTreesVisual(v: Visual.TwoTrees) {
    val grammar = remember(v) { Grammar.parse(v.grammar) }
    val trees = remember(v) { Magnets.derivations(grammar, tokenizeInput(v.input, grammar), DerivationMode.LEFTMOST, 2) }
    val step = rememberLoopStep(2, 1600, holdLast = 1)
    // Uma árvore embaixo da outra: cada ParseTreeView já rola na horizontal sozinha.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        trees.forEachIndexed { i, t ->
            if (i <= step) Column(Modifier.appear(i)) {
                Caption("Árvore ${i + 1}", if (i == 0) Primary else Danger)
                ParseTreeView(t.tree, listOf(t.root), grammar)
            }
        }
        Caption("Mesma frase, duas árvores = dois significados.", TextMain)
    }
}

// ------------------------------------------------------------------ código

@Composable
private fun CodeVisual(v: Visual.Code) {
    val step = rememberLoopStep(v.lines.size, 700)
    val keywords = setOf("def", "if", "elif", "else:", "return", "else")
    Column(Modifier.fillMaxWidth().background(SurfaceHigh, RoundedCornerShape(12.dp)).padding(10.dp)) {
        v.lines.forEachIndexed { i, line ->
            val active = i == step
            Row(
                Modifier.fillMaxWidth().background(if (active) Primary.copy(alpha = 0.14f) else SurfaceHigh, RoundedCornerShape(6.dp)),
            ) {
                Text(
                    buildAnnotatedString {
                        val comment = line.indexOf('#')
                        val code = if (comment >= 0) line.substring(0, comment) else line
                        code.split(" ").forEachIndexed { k, tok ->
                            if (k > 0) append(" ")
                            val color = when {
                                tok in keywords -> Secondary
                                tok.startsWith("{") || tok.endsWith("}") || tok.endsWith("}:") || tok.endsWith(",") -> TerminalColor
                                tok.endsWith("():") || tok.endsWith("()") || tok.contains("()") -> NonterminalColor
                                else -> TextMain
                            }
                            pushStyle(SpanStyle(color = color))
                            append(tok)
                            pop()
                        }
                        if (comment >= 0) {
                            pushStyle(SpanStyle(color = TextDim))
                            append(line.substring(comment))
                            pop()
                        }
                    },
                    fontFamily = Mono, fontSize = 13.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ esteira da fábrica

@Composable
private fun ConveyorVisual() {
    val phase = if (ThemeState.reduceMotion) 0.7f
    else rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing))).value
    val boxes = listOf("a a" to true, "b" to false, "a" to true, "a b" to false)
    BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp)) {
        val w = maxWidth
        // esteira
        Canvas(Modifier.offset(0.dp, 78.dp).size(w, 14.dp)) {
            drawRoundRect(Outline, Offset.Zero, size, CornerRadius(20f))
            val gap = 22f
            var x = -(phase * gap * 8) % gap
            while (x < size.width) {
                drawLine(SurfaceHigh, Offset(x, 2f), Offset(x + 8f, size.height - 2f), strokeWidth = 3f)
                x += gap
            }
        }
        // máquina
        Box(
            Modifier.offset(w / 2 - 44.dp, 8.dp).size(88.dp, 70.dp)
                .background(SurfaceHigh, RoundedCornerShape(12.dp)).border(2.dp, Primary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("⚙ sua\ngramática", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        // caixas passando
        boxes.forEachIndexed { i, (label, ok) ->
            val p = ((phase + i / boxes.size.toFloat()) % 1f)
            val x = w * p - 30.dp
            val done = p > 0.62f
            val color = if (!done) TextDim else if (ok) Success else Danger
            if (p !in 0.4f..0.6f) {
                Box(
                    Modifier.offset(x, 44.dp).size(60.dp, 32.dp)
                        .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).border(2.dp, color, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(if (done) (if (ok) "$label ✓" else "$label ✗") else label, color = TextMain, fontFamily = Mono, fontSize = 12.sp) }
            }
        }
    }
}

// ------------------------------------------------------------------ legenda das cores

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendVisual() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("S", "não-terminal", "ainda vai ser trocado"),
            Triple("a", "terminal", "token de verdade"),
            Triple("ε", "vazio", "a cadeia sem nada"),
            Triple("$", "fim", "o fim da frase"),
        ).forEachIndexed { i, (sym, name, desc) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(i, delayMs = i * 120)) {
                SymbolBox(sym, null, highlight = true)
                Column(Modifier.padding(start = 4.dp)) {
                    Text(name, color = symbolColor(null, sym), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(desc, color = TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}
