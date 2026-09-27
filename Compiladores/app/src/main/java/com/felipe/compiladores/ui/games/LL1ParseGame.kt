package com.felipe.compiladores.ui.games

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LLEntry
import com.felipe.compiladores.engine.LLMove
import com.felipe.compiladores.engine.LLParser
import com.felipe.compiladores.engine.LLRule
import com.felipe.compiladores.engine.ParseStatus
import com.felipe.compiladores.engine.tokenizeInput
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.FirstFollowView
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.InputTape
import com.felipe.compiladores.ui.components.LLTableView
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Overlay
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ParseTreeView
import com.felipe.compiladores.ui.components.SententialForm
import com.felipe.compiladores.ui.components.StackView
import com.felipe.compiladores.ui.components.productionText
import com.felipe.compiladores.ui.components.symbolsText
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

private data class WhyAsk(val nt: String, val la: String, val entry: LLEntry)

@Composable
fun LL1ParseGame(level: Level, spec: LevelSpec.LL1Parse, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id, level.grammar) { Grammar.parse(level.grammar) }
    val an = remember(grammar) { Analysis(grammar) }
    val table = remember(grammar) { LL1Table(an) }
    val parser = remember(grammar) { LLParser(table) }
    val tokens = remember(grammar, spec.input) { tokenizeInput(spec.input, grammar) }
    var state by remember(grammar, spec.input) { mutableStateOf(parser.initial(tokens)) }
    val trace = remember(grammar, spec.input) { mutableStateListOf<String>() }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var why by remember { mutableStateOf<WhyAsk?>(null) }
    var whyChoice by remember { mutableStateOf<Int?>(null) }
    var askedFirstRule by remember { mutableStateOf(false) }
    val done = state.status != ParseStatus.RUNNING

    session.hintProvider = { n ->
        val top = state.top
        val la = state.lookahead
        when {
            done -> null
            n == 2 && grammar.isNonterminal(top) ->
                if (spec.showTable) "Olhe a célula M[$top, $la] da tabela."
                else "Em qual alternativa de $top o '$la' está no FIRST? Se em nenhuma: alguma alternativa pode sumir e '$la' ∈ FOLLOW($top)?"
            n == 2 && top == END -> "A pilha chegou ao fundo ($). E a entrada?"
            n == 2 -> "O topo é um terminal: compare '$top' com o próximo token '$la'."
            else -> parser.explain(state)
        }
    }

    fun moveLabel(m: LLMove): String = when (m) {
        is LLMove.Expand -> "expandir ${m.production.text}"
        LLMove.Match -> "casar '${state.top}'"
        LLMove.Accept -> "aceitar"
        LLMove.Error -> "erro"
    }

    fun play(move: LLMove) {
        val valid = parser.validMoves(state)
        if (move in valid) {
            if (spec.askWhy && move is LLMove.Expand) {
                val entry = table.entries(state.top, state.lookahead).first { it.production == move.production }
                if (entry.rule == LLRule.FOLLOW || !askedFirstRule) {
                    why = WhyAsk(state.top, state.lookahead, entry)
                    whyChoice = null
                    if (entry.rule == LLRule.FIRST) askedFirstRule = true
                }
            }
            trace += "${trace.size + 1}. ${moveLabel(move)}"
            val before = state
            state = parser.apply(state, move)
            message = when (state.status) {
                ParseStatus.ACCEPTED -> FeedbackKind.GOOD to "Cadeia aceita! A árvore está completa."
                ParseStatus.REJECTED -> FeedbackKind.GOOD to "Erro detectado no momento exato! ${parser.explain(before)}"
                else -> null
            }
        } else {
            val correct = valid.first()
            val top = state.top
            val la = state.lookahead
            val type = when {
                correct == LLMove.Error -> MistakeType.LL_MISSED_ERROR
                move == LLMove.Error -> MistakeType.LL_FALSE_ERROR
                else -> MistakeType.LL_WRONG_PRODUCTION
            }
            session.mistake(type)
            val text = when {
                correct == LLMove.Error -> parser.explain(state)
                move is LLMove.Expand -> table.explainCell(top, la, move.production)
                move == LLMove.Error && grammar.isNonterminal(top) -> "Ainda há saída: M[$top, $la] não está vazia."
                move == LLMove.Error -> "Ainda há saída: confira o topo e a entrada."
                move == LLMove.Accept -> "Só aceite quando a pilha e a entrada estiverem ambas no $."
                else -> "Esse movimento não se aplica aqui."
            }
            message = FeedbackKind.BAD to text
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GrammarCard(grammar, numbered = true)
            if (spec.showTable) {
                Panel(title = "Tabela LL(1)", accent = Tertiary, collapsible = true) { LLTableView(table) }
            } else {
                Panel(title = "FIRST e FOLLOW (sem tabela!)", accent = Tertiary, collapsible = true) { FirstFollowView(an) }
            }
            Panel(title = "Parser", accent = Primary) {
                StackView(state.stackTopFirst, grammar, topAtLeft = true, keys = state.stack.asReversed())
                Spacer(Modifier.height(10.dp))
                InputTape(state.tokens, state.pos, grammar)
                Spacer(Modifier.height(8.dp))
                SententialForm("Derivação à esquerda:", state.sententialForm, grammar)
            }
            message?.let { (k, t) -> Feedback(k, t) }
            if (!done) Box(Modifier.shake(session.mistakes)) { ActionPanel(state.top, grammar, ::play) }
            if (done) GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            Panel(title = "Árvore (cresce de cima para baixo)", accent = Primary, collapsible = true) {
                ParseTreeView(state.tree, listOf(state.rootId), grammar)
            }
            if (trace.isNotEmpty()) {
                Panel(title = "Movimentos (${trace.size})", accent = TextDim, collapsible = true, initiallyOpen = false) {
                    trace.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMain) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        val w = why
        Overlay(w != null, null) {
            if (w != null) {
                val p = w.entry.production
                Text("Por quê?", style = MaterialTheme.typography.titleLarge, color = Tertiary)
                Spacer(Modifier.height(6.dp))
                MonoText(buildAnnotatedString {
                    append("Você usou ")
                    append(productionText(p, grammar))
                    append(" com '${w.la}' na entrada.")
                }, size = 14)
                Spacer(Modifier.height(10.dp))
                val prompt = ExplainPrompt(
                    "", listOf(
                        "'${w.la}' ∈ FIRST(${p.rhsText})",
                        "${p.rhsText} pode virar ε e '${w.la}' ∈ FOLLOW(${w.nt})",
                    ),
                    if (w.entry.rule == LLRule.FIRST) setOf(0) else setOf(1),
                    table.explainCell(w.nt, w.la, p),
                )
                ExplainOptions(prompt, whyChoice) { i ->
                    whyChoice = i
                    session.explanation(i in prompt.valid, MistakeType.LL_WHY_WRONG)
                }
                if (whyChoice != null) {
                    Spacer(Modifier.height(10.dp))
                    GameButton("Continuar", { why = null }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(top: String, grammar: Grammar, play: (LLMove) -> Unit) {
    Panel(title = "Seu movimento", accent = Tertiary) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                top == END -> GameButton("Aceitar ✓", { play(LLMove.Accept) }, Modifier.fillMaxWidth(), color = Success)
                grammar.isNonterminal(top) -> {
                    Text("Expandir $top com:", style = MaterialTheme.typography.labelMedium, color = TextDim)
                    grammar.productionsOf(top).forEach { p ->
                        GameButton(productionText(p, grammar, numbered = true), { play(LLMove.Expand(p)) }, Modifier.fillMaxWidth(), color = Tertiary)
                    }
                }
                else -> GameButton(
                    buildAnnotatedString { append("Casar  "); append(symbolsText(listOf(top), grammar)) },
                    { play(LLMove.Match) }, Modifier.fillMaxWidth(), color = Primary,
                )
            }
            Row { GameButton("Erro de sintaxe!", { play(LLMove.Error) }, Modifier.fillMaxWidth(), color = Danger, style = ButtonStyle.OUTLINED) }
        }
    }
}
