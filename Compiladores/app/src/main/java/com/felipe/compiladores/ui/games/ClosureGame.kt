package com.felipe.compiladores.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.Item
import com.felipe.compiladores.engine.LR0Automaton
import com.felipe.compiladores.game.ClosureQuestion
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Grading
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.ItemPicker
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ProgressBar
import com.felipe.compiladores.ui.components.itemText
import com.felipe.compiladores.ui.components.shake
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.DotColor
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim

/** Descrição de uma pergunta de itens: conjunto esperado, estado de origem e texto. */
class ItemsTask(val automaton: LR0Automaton, val source: Int?, val symbol: String?) {
    val expected: Set<Item> = if (source == null) automaton.states[0].toSet() else automaton.goto(automaton.states[source], symbol!!).toSet()
    val targetState: Int = if (source == null) 0 else automaton.transitions.getValue(source to symbol!!)
    val kernel: List<Item> = if (source == null) listOf(Item(0, 0)) else automaton.advance(automaton.states[source], symbol!!)
    val isGoto: Boolean get() = source != null

    fun title() = if (source == null) "closure({ ${automaton.itemText(Item(0, 0))} })" else "goto(I$source, $symbol)"
}

@Composable
fun ItemsTaskView(task: ItemsTask, selected: Set<Item>, onToggle: (Item) -> Unit, enabled: Boolean) {
    if (task.source != null) {
        Panel(title = "Estado I${task.source}", accent = Tertiary) {
            task.automaton.states[task.source].forEach { MonoText(itemText(task.automaton, it), size = 14) }
        }
    }
    Panel(title = "Monte ${task.title()}", accent = DotColor) {
        Text(
            if (task.source == null) "Marque todos os itens do fechamento: toque na vaga onde fica o ponto."
            else "Avance o ponto sobre ${task.symbol} e depois faça o fechamento. Marque todos os itens do novo estado.",
            style = MaterialTheme.typography.bodySmall, color = TextDim,
        )
        Spacer(Modifier.height(8.dp))
        ItemPicker(task.automaton, selected, onToggle, enabled = enabled)
        Spacer(Modifier.height(6.dp))
        Text("${selected.size} item(ns) marcado(s)", style = MaterialTheme.typography.labelMedium, color = TextDim)
    }
}

@Composable
fun ClosureGame(level: Level, spec: LevelSpec.Closure, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val automaton = remember(level.id) { LR0Automaton(grammar) }
    var qIndex by remember { mutableIntStateOf(0) }
    val q = spec.questions[qIndex]
    val task = remember(level.id, qIndex) {
        when (q) {
            ClosureQuestion.Initial -> ItemsTask(automaton, null, null)
            is ClosureQuestion.Goto -> ItemsTask(automaton, q.state, q.symbol)
        }
    }
    var selected by remember(qIndex) { mutableStateOf(emptySet<Item>()) }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var message by remember(qIndex) { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var answered by remember(qIndex) { mutableStateOf(false) }

    session.hintProvider = { n ->
        if (n == 2) "O núcleo é: " + task.kernel.joinToString("   ") { automaton.itemText(it) } +
            ". Agora procure • antes de não-terminais e puxe as produções deles."
        else {
            val missing = task.expected - selected
            val extra = selected - task.expected
            when {
                missing.isNotEmpty() -> "Falta o item ${automaton.itemText(missing.first())}."
                extra.isNotEmpty() -> "O item ${automaton.itemText(extra.first())} não pertence a esse conjunto."
                else -> "Está tudo certo — verifique!"
            }
        }
    }

    fun check() {
        val c = confidence ?: return
        val ok = selected == task.expected
        session.check(c, ok)
        confidence = null
        if (ok) {
            answered = true
            message = FeedbackKind.GOOD to "Correto! Esse conjunto é o estado I${task.targetState} do autômato."
        } else {
            session.mistake(Grading.closureMistakes(task.expected, selected, task.isGoto))
            val missing = (task.expected - selected).size
            val extra = (selected - task.expected).size
            message = FeedbackKind.BAD to buildString {
                if (missing > 0) append("Falta(m) $missing item(ns). ")
                if (extra > 0) append("Há $extra item(ns) a mais. ")
                append(if (missing > 0) "Você repetiu o fechamento para os novos itens também?" else "Só entram itens com o ponto no início de produções de não-terminais logo após um •.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pergunta ${qIndex + 1} de ${spec.questions.size}", style = MaterialTheme.typography.labelLarge, color = Primary)
        ProgressBar((qIndex + if (answered) 1 else 0).toFloat() / spec.questions.size)
        GrammarCard(automaton.grammar, title = "Gramática aumentada", numbered = true)
        Column(Modifier.shake(session.mistakes), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemsTaskView(task, selected, { item -> selected = if (item in selected) selected - item else selected + item; message = null }, enabled = !answered)
        }
        message?.let { (k, t) -> Feedback(k, t) }
        if (!answered) {
            CheckBar(confidence, { confidence = it }, ::check)
        } else if (qIndex < spec.questions.lastIndex) {
            GameButton("Próxima pergunta ▶", { qIndex++ }, Modifier.fillMaxWidth())
        } else {
            GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
        }
        Spacer(Modifier.height(24.dp))
    }
}
