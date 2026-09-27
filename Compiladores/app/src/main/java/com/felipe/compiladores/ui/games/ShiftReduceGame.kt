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
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.LR0Automaton
import com.felipe.compiladores.engine.LRMove
import com.felipe.compiladores.engine.LRParser
import com.felipe.compiladores.engine.LRTable
import com.felipe.compiladores.engine.ParseStatus
import com.felipe.compiladores.engine.tokenizeInput
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.ui.components.AutomatonView
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.FirstFollowView
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.InputTape
import com.felipe.compiladores.ui.components.LRTableView
import com.felipe.compiladores.ui.components.Overlay
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ParseTreeView
import com.felipe.compiladores.ui.components.SententialForm
import com.felipe.compiladores.ui.components.StackView
import com.felipe.compiladores.ui.components.productionText
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

@Composable
fun ShiftReduceGame(level: Level, spec: LevelSpec.ShiftReduce, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id, level.grammar) { Grammar.parse(level.grammar) }
    val automaton = remember(grammar) { LR0Automaton(grammar) }
    val table = remember(grammar) { LRTable.slr(automaton) }
    val parser = remember(grammar) { LRParser(table) }
    val analysis = remember(grammar) { Analysis(grammar) }
    val tokens = remember(grammar, spec.input) { tokenizeInput(spec.input, grammar) }
    var state by remember(grammar, spec.input) { mutableStateOf(parser.initial(tokens)) }
    val trace = remember(grammar, spec.input) { mutableStateListOf<String>() }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var choosingReduce by remember { mutableStateOf(false) }
    val done = state.status != ParseStatus.RUNNING

    session.hintProvider = { n ->
        if (done) null
        else if (n == 2) when (val m = parser.validMoves(state).first()) {
            LRMove.Shift -> "Ainda não há um handle completo no topo da pilha. O que falta chegar?"
            is LRMove.Reduce -> "O topo da pilha já termina com um lado direito completo — e o próximo token '${state.lookahead}' pode vir depois do não-terminal dele." +
                if (m.prod > 0 && grammar.production(m.prod).isEpsilon) " (Dica extra: pode ser uma produção vazia.)" else ""
            LRMove.Accept -> "A pilha tem só o símbolo inicial e a entrada acabou."
            LRMove.Error -> "Olhe o próximo token '${state.lookahead}': ele pode aparecer aqui depois do que já está na pilha?"
        }
        else parser.explainCorrect(state)
    }

    fun play(move: LRMove) {
        choosingReduce = false
        val valid = parser.validMoves(state)
        if (move in valid) {
            val before = state
            trace += "${trace.size + 1}. ${parser.moveText(move)}"
            state = parser.apply(state, move)
            message = when (state.status) {
                ParseStatus.ACCEPTED -> FeedbackKind.GOOD to "Aceita! Leia as reduções de trás para frente: é a derivação mais à direita."
                ParseStatus.REJECTED -> FeedbackKind.GOOD to "Erro detectado no momento certo! ${parser.explainCorrect(before)}"
                else -> null
            }
        } else {
            val correct = valid.first()
            val type = when {
                correct == LRMove.Error -> MistakeType.SR_MISSED_ERROR
                move == LRMove.Error -> MistakeType.SR_FALSE_ERROR
                move is LRMove.Reduce && correct == LRMove.Shift -> MistakeType.SR_REDUCE_EARLY
                move == LRMove.Shift && correct is LRMove.Reduce -> MistakeType.SR_SHIFT_LATE
                move is LRMove.Reduce -> MistakeType.SR_WRONG_HANDLE
                else -> MistakeType.SR_MISSED_ERROR
            }
            session.mistake(type)
            message = FeedbackKind.BAD to parser.explainWrong(state, move)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GrammarCard(grammar, numbered = true)
            if (spec.showFollow) {
                Panel(title = "FOLLOW (quando reduzir?)", accent = Tertiary, collapsible = true) {
                    FirstFollowView(analysis, showFirst = false)
                }
            }
            if (spec.showTable) {
                Panel(title = "Tabela SLR", accent = Tertiary, collapsible = true) { LRTableView(table) }
                Panel(title = "Estados LR(0)", accent = Tertiary, collapsible = true, initiallyOpen = false) {
                    AutomatonView(automaton, highlight = state.topState)
                }
            }
            Panel(title = "Parser", accent = Primary) {
                StackView(state.stackSymbols, grammar, topAtLeft = false, states = if (spec.showStates) state.states else null, keys = state.nodes)
                Spacer(Modifier.height(10.dp))
                InputTape(state.tokens, state.pos, grammar)
                Spacer(Modifier.height(8.dp))
                SententialForm("Forma sentencial:", state.sententialForm, grammar)
            }
            message?.let { (k, t) -> Feedback(k, t) }
            if (!done) {
                Panel(Modifier.shake(session.mistakes), title = "Seu movimento", accent = Tertiary) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GameButton("Empilhar ⤵", { play(LRMove.Shift) }, Modifier.weight(1f), color = Primary)
                            GameButton("Reduzir…", { choosingReduce = true }, Modifier.weight(1f), color = Tertiary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GameButton("Aceitar ✓", { play(LRMove.Accept) }, Modifier.weight(1f), color = Success, style = ButtonStyle.OUTLINED)
                            GameButton("Erro!", { play(LRMove.Error) }, Modifier.weight(1f), color = Danger, style = ButtonStyle.OUTLINED)
                        }
                    }
                }
            } else {
                GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
            }
            Panel(title = "Floresta (cresce de baixo para cima)", accent = Primary, collapsible = true) {
                ParseTreeView(state.tree, state.nodes, grammar, leavesAtBottom = true)
            }
            if (trace.isNotEmpty()) {
                Panel(title = "Movimentos (${trace.size})", accent = TextDim, collapsible = true, initiallyOpen = false) {
                    trace.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMain) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Overlay(choosingReduce, { choosingReduce = false }) {
            Text("Reduzir por qual produção?", style = MaterialTheme.typography.titleLarge, color = Tertiary)
            Spacer(Modifier.height(4.dp))
            Text("O lado direito precisa estar no topo da pilha.", style = MaterialTheme.typography.bodySmall, color = TextDim)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grammar.productions.forEach { p ->
                    GameButton(productionText(p, grammar, numbered = true), { play(LRMove.Reduce(p.id)) }, Modifier.fillMaxWidth(), color = Tertiary)
                }
            }
        }
    }
}
