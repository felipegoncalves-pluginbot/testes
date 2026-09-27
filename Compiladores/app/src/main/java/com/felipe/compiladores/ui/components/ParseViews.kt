package com.felipe.compiladores.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.Item
import com.felipe.compiladores.engine.ItemGrammar
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LR0Automaton
import com.felipe.compiladores.engine.LRTable
import com.felipe.compiladores.engine.ParseTree
import com.felipe.compiladores.engine.layoutForest
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.DotColor
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.OnAccent
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Secondary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain
import com.felipe.compiladores.ui.theme.Warning

// ------------------------------------------------------------------ pilha e entrada

/** Um símbolo numa "caixinha", como uma peça de pilha ou fita. */
@Composable
fun SymbolBox(symbol: String, grammar: Grammar?, highlight: Boolean = false, dim: Boolean = false, sub: String? = null) {
    val color = symbolColor(grammar, symbol)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.padding(2.dp)
                .heightIn(min = 36.dp)
                .widthIn(min = 34.dp)
                .background(if (highlight) color.copy(alpha = 0.22f) else SurfaceHigh, RoundedCornerShape(8.dp))
                .border(if (highlight) 2.dp else 1.dp, if (highlight) color else Outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol, fontFamily = Mono, fontSize = 15.sp,
                color = if (dim) TextDim.copy(alpha = 0.5f) else color,
                fontWeight = if (grammar?.isNonterminal(symbol) == true) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (sub != null) Text(sub, fontSize = 10.sp, color = TextDim, fontFamily = Mono)
    }
}

/**
 * Pilha desenhada na horizontal. [topAtLeft] = true no parser LL (a pilha lida do topo
 * forma o resto da derivação); false no LR (pilha + entrada = forma sentencial).
 */
@Composable
fun StackView(
    symbols: List<String>,
    grammar: Grammar?,
    topAtLeft: Boolean,
    states: List<Int>? = null,
    keys: List<Any>? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PILHA", style = MaterialTheme.typography.labelSmall, color = TextDim)
            Spacer(Modifier.weight(1f))
            Text(if (topAtLeft) "◀ topo" else "topo ▶", style = MaterialTheme.typography.labelSmall, color = Primary)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom) {
            if (!topAtLeft) {
                SymbolBox(END, grammar, sub = states?.getOrNull(0)?.toString())
                symbols.forEachIndexed { i, s ->
                    Box(Modifier.appear(keys?.getOrNull(i) ?: "$i-$s")) {
                        SymbolBox(s, grammar, highlight = i == symbols.lastIndex, sub = states?.getOrNull(i + 1)?.toString())
                    }
                }
            } else {
                symbols.forEachIndexed { i, s ->
                    Box(Modifier.appear(keys?.getOrNull(i) ?: "$i-$s")) { SymbolBox(s, grammar, highlight = i == 0) }
                }
            }
        }
    }
}

@Composable
fun InputTape(tokens: List<String>, pos: Int, grammar: Grammar?) {
    Column {
        Text("ENTRADA", style = MaterialTheme.typography.labelSmall, color = TextDim)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            tokens.forEachIndexed { i, t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SymbolBox(t, grammar, highlight = i == pos, dim = i < pos)
                    Text(if (i == pos) "▲" else " ", color = Primary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SententialForm(label: String, symbols: List<String>, grammar: Grammar) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextDim)
        Spacer(Modifier.width(8.dp))
        MonoText(symbolsText(symbols, grammar), size = 14)
    }
}

// ------------------------------------------------------------------ árvore de derivação

/** Desenha a floresta de derivação (nós como texto, arestas no Canvas). */
@Composable
fun ParseTreeView(
    tree: ParseTree,
    roots: List<Int>,
    grammar: Grammar,
    modifier: Modifier = Modifier,
    leavesAtBottom: Boolean = false,
    highlight: Set<Int> = emptySet(),
) {
    if (roots.isEmpty()) {
        Text("(a árvore aparece aqui)", color = TextDim, style = MaterialTheme.typography.bodySmall)
        return
    }
    val layout = layoutForest(tree, roots, leavesAtBottom)
    val colW = 46.dp
    val rowH = 50.dp
    val nodeW = 42.dp
    val nodeH = 28.dp
    val width = colW * layout.width
    val height = rowH * layout.height
    val density = LocalDensity.current
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Box(Modifier.size(width, height)) {
            Canvas(Modifier.size(width, height)) {
                with(density) {
                    for (n in tree.nodes.values) {
                        val px = layout.x[n.id] ?: continue
                        val py = layout.y[n.id] ?: continue
                        for (c in n.children) {
                            val cx = layout.x[c] ?: continue
                            val cy = layout.y[c] ?: continue
                            drawLine(
                                Outline.copy(alpha = 0.9f),
                                Offset((colW * px + colW / 2).toPx(), (rowH * py + nodeH).toPx()),
                                Offset((colW * cx + colW / 2).toPx(), (rowH * cy).toPx()),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }
            }
            for (n in tree.nodes.values) {
                val px = layout.x[n.id] ?: continue
                val py = layout.y[n.id] ?: continue
                val color = symbolColor(grammar, n.symbol)
                val hl = n.id in highlight
                Box(
                    Modifier
                        .offset(colW * px + (colW - nodeW) / 2, rowH * py)
                        .appear(n.id)
                        .size(nodeW, nodeH)
                        .background(if (hl) color.copy(alpha = 0.3f) else SurfaceHigh, RoundedCornerShape(8.dp))
                        .border(if (hl) 2.dp else 1.dp, if (hl) color else color.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(n.symbol, color = color, fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tabelas de referência

@Composable
fun FirstFollowView(analysis: Analysis, showFirst: Boolean = true, showFollow: Boolean = true) {
    val g = analysis.grammar
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (nt in g.nonterminals) {
            if (showFirst) MonoText(buildAnnotatedString {
                withStyle(SpanStyle(color = TextDim)) { append("FIRST(") }
                withStyle(SpanStyle(color = NonterminalColor, fontWeight = FontWeight.Bold)) { append(nt) }
                withStyle(SpanStyle(color = TextDim)) { append(") = ") }
                append(setAnnotated(analysis.first.getValue(nt), g))
            }, size = 13)
            if (showFollow) MonoText(buildAnnotatedString {
                withStyle(SpanStyle(color = TextDim)) { append("FOLLOW(") }
                withStyle(SpanStyle(color = NonterminalColor, fontWeight = FontWeight.Bold)) { append(nt) }
                withStyle(SpanStyle(color = TextDim)) { append(") = ") }
                append(setAnnotated(analysis.follow.getValue(nt), g))
            }, size = 13)
        }
    }
}

/** Tabela LL(1) somente leitura (números das produções). */
@Composable
fun LLTableView(table: LL1Table, highlight: Pair<String, String>? = null) {
    val g = table.grammar
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            HeaderCell(AnnotatedString("M"), width = 56.dp)
            table.columns.forEach { HeaderCell(symbolsText(listOf(it), g)) }
        }
        g.nonterminals.forEach { nt ->
            Row {
                HeaderCell(symbolsText(listOf(nt), g), width = 56.dp, height = 40.dp)
                table.columns.forEach { t ->
                    val ids = table.productions(nt, t).map { it.id }
                    GridCell(
                        AnnotatedString(ids.joinToString(",")), selected = highlight == (nt to t), onClick = null,
                        status = if (ids.size > 1) CellStatus.CONFLICT else CellStatus.NEUTRAL,
                    )
                }
            }
        }
    }
}

/** Tabela LR somente leitura. */
@Composable
fun LRTableView(table: LRTable, highlightState: Int? = null, highlightColumn: String? = null) {
    val g = table.items.original
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            HeaderCell(AnnotatedString("#"), width = 36.dp)
            table.actionColumns.forEach { HeaderCell(symbolsText(listOf(it), g), width = 48.dp) }
            Spacer(Modifier.width(6.dp))
            table.gotoColumns.forEach { HeaderCell(symbolsText(listOf(it), g), width = 40.dp) }
        }
        for (s in 0 until table.stateCount) {
            Row {
                HeaderCell(AnnotatedString("$s"), width = 36.dp, height = 36.dp)
                table.actionColumns.forEach { c ->
                    val acts = table.actions(s, c)
                    GridCell(
                        AnnotatedString(acts.joinToString("\n")), selected = s == highlightState && c == highlightColumn, onClick = null,
                        width = 48.dp, height = if (acts.size > 1) 44.dp else 36.dp,
                        status = if (acts.size > 1) CellStatus.CONFLICT else CellStatus.NEUTRAL,
                    )
                }
                Spacer(Modifier.width(6.dp))
                table.gotoColumns.forEach { c ->
                    GridCell(AnnotatedString(table.goto[s to c]?.toString() ?: ""), selected = false, onClick = null, width = 40.dp, height = 36.dp)
                }
            }
        }
    }
}

fun itemText(items: ItemGrammar, item: Item): AnnotatedString = buildAnnotatedString {
    val p = items.prod(item.prod)
    withStyle(SpanStyle(color = NonterminalColor, fontWeight = FontWeight.Bold)) { append(p.lhs) }
    withStyle(SpanStyle(color = TextDim)) { append(" → ") }
    val parts = p.rhs.toMutableList()
    parts.add(item.dot, "•")
    parts.forEachIndexed { i, s ->
        if (i > 0) append(" ")
        withStyle(SpanStyle(color = if (s == "•") DotColor else symbolColor(items.grammar, s), fontWeight = if (s == "•") FontWeight.Black else FontWeight.Normal)) { append(s) }
    }
}

/** Lista dos estados do autômato LR(0) com itens e transições. */
@Composable
fun AutomatonView(a: LR0Automaton, highlight: Int? = null, lookaheads: Map<Pair<Int, Item>, Set<String>>? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        a.states.forEachIndexed { i, items ->
            val hl = i == highlight
            Column(
                Modifier.fillMaxWidth()
                    .background(if (hl) Primary.copy(alpha = 0.12f) else SurfaceHigh, RoundedCornerShape(12.dp))
                    .border(1.dp, if (hl) Primary else Outline, RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) {
                Text("I$i", color = Tertiary, fontWeight = FontWeight.Bold, fontFamily = Mono)
                items.forEach { item ->
                    val la = lookaheads?.get(i to item)
                    MonoText(buildAnnotatedString {
                        append(itemText(a, item))
                        if (la != null) withStyle(SpanStyle(color = Secondary)) { append(",  " + a.grammar.sortTerminals(la).joinToString("/")) }
                    }, size = 13)
                }
                val tr = a.transitions.filterKeys { it.first == i }
                if (tr.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    MonoText(buildAnnotatedString {
                        tr.entries.forEachIndexed { k, e ->
                            if (k > 0) append("   ")
                            withStyle(SpanStyle(color = symbolColor(a.grammar, e.key.second))) { append(e.key.second) }
                            withStyle(SpanStyle(color = TextDim)) { append(" → I${e.value}") }
                        }
                    }, size = 12)
                }
            }
        }
    }
}

/**
 * Seletor de itens LR(0): cada produção vira uma linha, com "vagas" entre os símbolos.
 * Tocar numa vaga coloca/retira o ponto (•) naquela posição.
 */
@Composable
fun ItemPicker(
    items: ItemGrammar,
    selected: Set<Item>,
    onToggle: (Item) -> Unit,
    status: Map<Item, CellStatus> = emptyMap(),
    enabled: Boolean = true,
) {
    val g = items.grammar
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        g.productions.forEach { p ->
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.lhs, fontFamily = Mono, color = NonterminalColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(" → ", fontFamily = Mono, color = TextDim, fontSize = 15.sp)
                for (d in 0..p.rhs.size) {
                    val item = Item(p.id, d)
                    val on = item in selected
                    val st = status[item]
                    val border = when (st) {
                        CellStatus.OK -> Success
                        CellStatus.WRONG -> Danger
                        CellStatus.HINT -> Tertiary
                        else -> if (on) DotColor else Outline
                    }
                    Box(
                        Modifier.testTag("item:${p.id}:$d")
                            .padding(horizontal = 1.dp)
                            .size(26.dp, 34.dp)
                            .background(if (on) DotColor.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .border(1.dp, border, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { onToggle(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (on) "•" else "·", color = if (on) DotColor else TextDim, fontSize = if (on) 22.sp else 14.sp, fontWeight = FontWeight.Black)
                    }
                    if (d < p.rhs.size) {
                        val s = p.rhs[d]
                        Text(s, fontFamily = Mono, color = symbolColor(g, s), fontSize = 15.sp, modifier = Modifier.padding(horizontal = 2.dp))
                    }
                }
                if (p.rhs.isEmpty()) Text(" ($EPS)", color = TextDim, fontFamily = Mono, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun KeyValueLine(label: String, value: AnnotatedString) {
    Row {
        Text("$label  ", color = TextDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(110.dp))
        MonoText(value, size = 14)
    }
}

@Composable
fun NumberBadge(n: Int, color: Color = Warning) {
    Box(Modifier.size(22.dp).background(color, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Text("$n", color = OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@Composable
fun TextLine(text: String, color: Color = TextMain) {
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
}
