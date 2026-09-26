package com.felipe.compiladores.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipe.compiladores.engine.Item
import com.felipe.compiladores.game.ChoiceQuestion
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.ItemsQuestion
import com.felipe.compiladores.game.ReviewGenerator
import com.felipe.compiladores.game.ReviewQuestion
import com.felipe.compiladores.game.SetQuestion
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.Chip
import com.felipe.compiladores.ui.components.ChipsRow
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.ProgressBar
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.components.setAnnotated
import com.felipe.compiladores.ui.games.ItemsTask
import com.felipe.compiladores.ui.games.ItemsTaskView
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

@Composable
fun ReviewScreen(game: GameState, onBack: () -> Unit) {
    val generator = remember { ReviewGenerator() }
    val skills = remember { game.dueSkills().ifEmpty { game.unlockedSkills() } }
    val questions = remember { generator.session(skills, 6) }
    var index by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<Pair<Confidence, Boolean>>() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("Treino espaçado", "Prática intercalada · aposte antes de ver", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                questions.isEmpty() -> Feedback(FeedbackKind.INFO, "Complete algumas fases primeiro: cada mundo concluído libera um tipo de revisão.")
                index >= questions.size -> Summary(game, results, onBack)
                else -> {
                    Text("Desafio ${index + 1} de ${questions.size}", style = MaterialTheme.typography.labelLarge, color = Primary)
                    ProgressBar(index.toFloat() / questions.size)
                    key(index) {
                        QuestionCard(questions[index], onAnswered = { correct, conf ->
                            game.reviewAnswered(questions[index].skill, correct, conf)
                            results += conf to correct
                        }, onNext = { index++ })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuestionCard(q: ReviewQuestion, onAnswered: (Boolean, Confidence) -> Unit, onNext: () -> Unit) {
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var choice by remember { mutableStateOf<Int?>(null) }
    var items by remember { mutableStateOf(emptySet<Item>()) }
    val task = remember(q) { (q as? ItemsQuestion)?.let { ItemsTask(it.automaton, it.sourceState, it.symbol) } }

    Text("${q.skill.emoji} ${q.skill.title}", style = MaterialTheme.typography.labelLarge, color = Tertiary)
    GrammarCard(q.grammar, numbered = q is ChoiceQuestion)
    Text(q.prompt, style = MaterialTheme.typography.titleMedium, color = TextMain)

    when (q) {
        is SetQuestion -> ChipsRow {
            q.options.forEach { o ->
                Chip(o, o in picked, { if (result == null) picked = if (o in picked) picked - o else picked + o })
            }
        }
        is ChoiceQuestion -> {
            if (q.context.isNotEmpty()) Panel(title = "Situação", accent = Primary) {
                q.context.forEach { (k, v) ->
                    Text(k, style = MaterialTheme.typography.labelSmall, color = TextDim)
                    MonoText(v, size = 14)
                    Spacer(Modifier.height(4.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.forEachIndexed { i, o ->
                    val color = when {
                        result != null && i == q.answer -> Success
                        result != null && i == choice -> Danger
                        i == choice -> Primary
                        else -> TextDim
                    }
                    GameButton(o, { if (result == null) choice = i }, Modifier.fillMaxWidth().testTag("option:$i"), color = color, style = ButtonStyle.OUTLINED, mono = true)
                }
            }
        }
        is ItemsQuestion -> task?.let { t ->
            ItemsTaskView(t, items, { it2 -> if (result == null) items = if (it2 in items) items - it2 else items + it2 }, enabled = result == null)
        }
    }

    val r = result
    if (r == null) {
        val ready = when (q) {
            is ChoiceQuestion -> choice != null
            else -> true
        }
        CheckBar(confidence, { confidence = it }, {
            val c = confidence ?: return@CheckBar
            val ok = when (q) {
                is SetQuestion -> picked == q.answer
                is ChoiceQuestion -> choice == q.answer
                is ItemsQuestion -> items == q.answer
            }
            result = ok
            onAnswered(ok, c)
        }, enabled = ready)
    } else {
        val answerText = when (q) {
            is SetQuestion -> null
            is ChoiceQuestion -> "Resposta: ${q.options[q.answer]}."
            is ItemsQuestion -> "O conjunto correto tem ${q.answer.size} itens: " + q.answer.joinToString("   ") { q.automaton.itemText(it) }
        }
        Feedback(
            if (r) FeedbackKind.GOOD else FeedbackKind.BAD,
            listOfNotNull(answerText, q.explanation).joinToString("\n"),
            title = if (r) "Acertou${if (confidence == Confidence.GUESS) " (no chute — revise mesmo assim)" else "!"}" else "Não foi dessa vez",
        )
        if (q is SetQuestion) {
            Row {
                Text("Resposta: ", color = TextDim)
                MonoText(setAnnotated(q.answer, q.grammar), size = 14)
            }
        }
        GameButton("Próximo ▶", onNext, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Summary(game: GameState, results: List<Pair<Confidence, Boolean>>, onBack: () -> Unit) {
    val right = results.count { it.second }
    Text("Sessão concluída!", style = MaterialTheme.typography.headlineSmall, color = Success)
    Text("Você acertou $right de ${results.size}.", style = MaterialTheme.typography.titleMedium, color = TextMain)
    Panel(title = "Sua confiança nesta sessão", accent = Tertiary) {
        Confidence.entries.forEach { c ->
            val rs = results.filter { it.first == c }
            if (rs.isNotEmpty()) {
                Row {
                    Text("${c.emoji} ${c.label}", modifier = Modifier.width(140.dp), color = TextMain)
                    Text("${rs.count { it.second }}/${rs.size} certas", color = TextDim)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val sureWrong = results.count { it.first == Confidence.SURE && !it.second }
        val guessRight = results.count { it.first == Confidence.GUESS && it.second }
        Text(
            when {
                sureWrong > 0 -> "Atenção: $sureWrong erro(s) com \"Certeza\". Isso é ilusão de competência — revise a explicação desses itens."
                guessRight > 0 -> "Você acertou $guessRight no chute. Talvez você saiba mais do que pensa, mas confirme revisando."
                else -> "Boa calibração: sua confiança bateu com o resultado."
            },
            style = MaterialTheme.typography.bodySmall, color = TextMain,
        )
    }
    Panel(title = "Próximas revisões", accent = Primary) {
        game.unlockedSkills().forEach { s ->
            val st = game.profile.skills[s]
            Row {
                Text("${s.emoji} ${s.title}", modifier = Modifier.weight(1f), color = TextMain)
                Text(
                    if (st == null) "nova" else "caixa ${st.box}/5",
                    color = Tertiary, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Acertos com confiança sobem de caixa e voltam mais tarde; erros voltam amanhã.", style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
    GameButton("Voltar ao mapa", onBack, Modifier.fillMaxWidth())
}
