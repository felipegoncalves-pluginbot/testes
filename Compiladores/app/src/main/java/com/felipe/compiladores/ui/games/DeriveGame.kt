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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Derivation
import com.felipe.compiladores.engine.DerivationMode
import com.felipe.compiladores.engine.DerivationState
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.Production
import com.felipe.compiladores.engine.tokenizeInput
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ParseTreeView
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.productionText
import com.felipe.compiladores.ui.components.symbolColor
import com.felipe.compiladores.ui.components.symbolsText
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeriveGame(level: Level, spec: LevelSpec.Derive, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val target = remember(level.id) { tokenizeInput(spec.target, grammar) }
    val engine = remember(level.id) { Derivation(grammar, target) }
    val history = remember(level.id) { mutableStateListOf(engine.initial()) }
    var selectedPos by remember { mutableStateOf<Int?>(null) }
    var deadEnd by remember { mutableStateOf<String?>(null) }
    val found = remember { mutableStateListOf<DerivationState>() }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var solved by remember { mutableStateOf(false) }

    val current = history.last()
    val allowed = engine.allowedPositions(current, spec.mode)
    val activePos = when {
        solved || deadEnd != null -> null
        spec.mode == DerivationMode.FREE -> selectedPos?.takeIf { it in allowed }
        else -> allowed.firstOrNull()
    }

    fun restart() {
        history.clear(); history += engine.initial(); deadEnd = null; selectedPos = null
    }

    fun reached(s: DerivationState) {
        if (!spec.ambiguity) {
            solved = true
            message = FeedbackKind.GOOD to "Você chegou em \"${spec.target}\" em ${s.steps.size} passos."
            return
        }
        val sig = s.tree.signature(s.root)
        when {
            found.isEmpty() -> {
                found += s
                message = FeedbackKind.INFO to "Primeira árvore encontrada! Agora derive a MESMA cadeia com uma árvore diferente."
                restart()
            }
            found.any { it.tree.signature(it.root) == sig } -> {
                session.mistake(MistakeType.AMBIGUITY_SAME)
                message = FeedbackKind.BAD to "Essa é a mesma árvore da primeira vez (mesmas escolhas). Tente mudar uma decisão."
                restart()
            }
            else -> {
                found += s
                solved = true
                message = FeedbackKind.GOOD to "Duas árvores diferentes para a mesma cadeia: a gramática é ambígua!"
            }
        }
    }

    fun choose(p: Production) {
        val pos = activePos ?: return
        val next = engine.apply(current, pos, p)
        history += next
        selectedPos = null
        message = null
        val reason = engine.deadEndReason(next)
        if (reason != null) {
            deadEnd = reason
            session.mistake(MistakeType.DERIV_DEAD_END)
        } else if (engine.isDone(next)) reached(next)
    }

    fun viable(s: DerivationState, pos: Int): List<Production> {
        val nt = s.symbols[pos]
        return grammar.productionsOf(nt).filter { engine.deadEndReason(engine.apply(s, pos, it)) == null }
    }

    session.hintProvider = { n ->
        val pos = if (spec.mode == DerivationMode.FREE) allowed.firstOrNull() else activePos
        when {
            deadEnd != null -> "Você está num beco sem saída. Desfaça o último passo (↶) e escolha outra produção."
            pos == null -> null
            else -> {
                val nt = current.symbols[pos]
                val ok = viable(current, pos)
                val firstTree = found.firstOrNull()
                val step = current.steps.size
                val preferred = if (firstTree != null && step < firstTree.steps.size) {
                    ok.firstOrNull { it != firstTree.steps[step].production } ?: ok.firstOrNull()
                } else ok.firstOrNull()
                if (n == 2) "Das ${grammar.productionsOf(nt).size} produções de $nt, ${ok.size} ainda permite(m) chegar ao alvo agora." +
                    if (firstTree != null) " Para uma árvore nova, alguma escolha precisa ser diferente da primeira." else ""
                else preferred?.let { "Expanda $nt usando ${it.text}." }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alvo  ", color = TextDim, style = MaterialTheme.typography.labelLarge)
            MonoText(symbolsText(target, grammar), size = 18)
            Spacer(Modifier.weight(1f))
            Tag("derivação ${spec.mode.label}", Tertiary)
        }
        if (spec.ambiguity) {
            Text("Árvores encontradas: ${found.size} / 2", color = Tertiary, style = MaterialTheme.typography.labelLarge)
        }
        GrammarCard(grammar)

        Panel(title = "Forma sentencial", accent = Primary) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                current.symbols.forEachIndexed { i, s ->
                    val isNt = grammar.isNonterminal(s)
                    val can = isNt && i in allowed && !solved && deadEnd == null
                    val active = i == activePos
                    val color = symbolColor(grammar, s)
                    Box(
                        Modifier.heightIn(min = 44.dp).widthIn(min = 40.dp)
                            .background(if (active) color.copy(alpha = 0.25f) else SurfaceHigh, RoundedCornerShape(10.dp))
                            .border(if (active) 2.dp else 1.dp, if (can) color else Outline, RoundedCornerShape(10.dp))
                            .then(if (can && spec.mode == DerivationMode.FREE) Modifier.clickable { selectedPos = i } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s, fontFamily = Mono, fontSize = 18.sp, color = if (isNt && !can) color.copy(alpha = 0.45f) else color,
                            fontWeight = if (isNt) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                if (current.symbols.isEmpty()) Text("ε", color = TextDim, fontFamily = Mono, fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            val instruction = when {
                solved -> "Pronto!"
                deadEnd != null -> "Beco sem saída — desfaça o passo."
                spec.mode == DerivationMode.FREE && activePos == null -> "Toque num não-terminal para expandi-lo."
                else -> "Escolha uma produção para ${current.symbols[activePos ?: 0]} (destacado)."
            }
            Text(instruction, color = TextDim, style = MaterialTheme.typography.bodySmall)
        }

        deadEnd?.let { Feedback(FeedbackKind.BAD, it, title = "Beco sem saída") }
        message?.let { (k, t) -> Feedback(k, t) }

        if (activePos != null) {
            val nt = current.symbols[activePos]
            Panel(title = "Trocar $nt por…", accent = Tertiary) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grammar.productionsOf(nt).forEach { p ->
                        GameButton(productionText(p, grammar), { choose(p) }, Modifier.fillMaxWidth(), color = Tertiary)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton("↶ Desfazer", {
                if (history.size > 1) history.removeAt(history.lastIndex)
                deadEnd = null; selectedPos = null
            }, Modifier.weight(1f), enabled = history.size > 1 && !solved, style = ButtonStyle.OUTLINED)
            GameButton("⟲ Recomeçar", { restart(); message = null }, Modifier.weight(1f), enabled = !solved, style = ButtonStyle.OUTLINED)
        }

        Panel(title = "Derivação (${current.steps.size} passos)", accent = Success, collapsible = true) {
            history.forEachIndexed { i, st ->
                MonoText(
                    buildAnnotatedString {
                        append(if (i == 0) "   " else "⇒ ")
                        append(symbolsText(st.symbols, grammar))
                    },
                    size = 14,
                )
            }
        }

        if (spec.ambiguity && found.isNotEmpty()) {
            found.forEachIndexed { i, s ->
                Panel(title = "Árvore ${i + 1}", accent = if (i == 0) Primary else Danger) {
                    ParseTreeView(s.tree, listOf(s.root), grammar)
                }
            }
        }
        if (!(spec.ambiguity && solved)) {
            Panel(title = "Árvore de derivação", accent = Primary, collapsible = true) {
                ParseTreeView(current.tree, listOf(current.root), grammar)
            }
        }
        if (solved) GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
        Spacer(Modifier.height(24.dp))
    }
}
