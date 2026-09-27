package com.felipe.compiladores.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.SpecialColor
import com.felipe.compiladores.ui.theme.Surface
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.TextDim

/** Gramática em edição: alternativas de cada não-terminal e a alternativa selecionada. */
class WorkbenchState(val ntOrder: List<String>, private val initial: Map<String, List<List<String>>>) {
    val rules = mutableStateMapOf<String, List<List<String>>>().apply { putAll(initial) }
    var selected by mutableStateOf<Pair<String, Int>?>(null)

    fun alternatives(nt: String) = rules[nt].orEmpty()

    fun edit(nt: String, i: Int, f: (List<String>) -> List<String>) {
        val alts = alternatives(nt).toMutableList()
        if (i !in alts.indices) return
        alts[i] = f(alts[i])
        rules[nt] = alts
    }

    fun editSelected(f: (List<String>) -> List<String>) { selected?.let { (nt, i) -> edit(nt, i, f) } }

    fun addAlternative(nt: String) {
        rules[nt] = alternatives(nt) + listOf(emptyList())
        selected = nt to alternatives(nt).lastIndex
    }

    fun deleteSelected() {
        val (nt, i) = selected ?: return
        rules[nt] = alternatives(nt).filterIndexed { j, _ -> j != i }
        selected = null
    }

    fun reset() {
        rules.clear()
        rules.putAll(initial)
        selected = null
    }

    val productionCount: Int get() = ntOrder.sumOf { alternatives(it).size }

    /** A gramática montada (null se ainda não há nenhuma produção). */
    fun grammar(start: String): Grammar? {
        val rs = ntOrder.flatMap { nt -> alternatives(nt).map { nt to it } }
        return if (rs.isEmpty()) null else Transform.build(rs, start)
    }
}

/** Linhas "A → alt | alt | + alt" editáveis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrammarWorkbench(state: WorkbenchState, enabled: Boolean, onChange: () -> Unit) {
    state.ntOrder.forEach { nt ->
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
                state.alternatives(nt).forEachIndexed { i, alt ->
                    AlternativeBox(
                        alt, state.selected == (nt to i), enabled, "alt:$nt:$i",
                        onSelect = { state.selected = nt to i },
                        onRemoveSymbol = { k -> state.edit(nt, i) { it.filterIndexed { j, _ -> j != k } }; onChange() },
                    )
                }
                if (enabled) Box(
                    Modifier.testTag("addalt:$nt").heightIn(min = 40.dp).background(SurfaceHigh, RoundedCornerShape(10.dp))
                        .border(1.dp, Outline, RoundedCornerShape(10.dp))
                        .clickable { state.addAlternative(nt); onChange() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("+ alt", color = Primary, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun AlternativeBox(
    alt: List<String>,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onSelect: () -> Unit,
    onRemoveSymbol: (Int) -> Unit,
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
            Box(
                Modifier.padding(2.dp).widthIn(min = 26.dp).appear("$k-$s")
                    .then(if (selected && enabled) Modifier.clickable { onRemoveSymbol(k) } else Modifier)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s, fontFamily = Mono, fontSize = 16.sp, color = symbolColor(null, s), fontWeight = if (s.first().isUpperCase()) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Bandeja de símbolos fixa no rodapé. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolTray(state: WorkbenchState, symbols: List<String>, onChange: () -> Unit) {
    val selected = state.selected
    Column(
        Modifier.fillMaxWidth().background(Surface).border(1.dp, Outline).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            if (selected == null) "Bandeja — selecione uma alternativa primeiro" else "Inserir em ${selected.first} (alternativa ${selected.second + 1})",
            style = MaterialTheme.typography.labelMedium, color = if (selected == null) TextDim else Primary,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val on = selected != null
            symbols.forEach { s -> TrayKey(s, symbolColor(null, s), on) { state.editSelected { it + s }; onChange() } }
            TrayKey(EPS, SpecialColor, on) { state.editSelected { emptyList() }; onChange() }
            TrayKey("⌫", TextDim, on) { state.editSelected { it.dropLast(1) }; onChange() }
            TrayKey("✕ alt", Danger, on) { state.deleteSelected(); onChange() }
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
