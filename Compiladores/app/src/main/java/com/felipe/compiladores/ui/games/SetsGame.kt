package com.felipe.compiladores.ui.games

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Grading
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.CheckBar
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.FirstFollowView
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.GridCell
import com.felipe.compiladores.ui.components.HeaderCell
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.symbolsText
import com.felipe.compiladores.ui.level.LevelSession
import com.felipe.compiladores.ui.theme.Danger
import com.felipe.compiladores.ui.theme.Mono
import com.felipe.compiladores.ui.theme.NonterminalColor
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

/** Pergunta de autoexplicação: "por que x ∈ FIRST(A)?", com as categorias de regra como opções. */
data class ExplainPrompt(val question: String, val options: List<String>, val valid: Set<Int>, val explanation: String)

fun firstExplainPrompt(an: Analysis): ExplainPrompt? {
    val g = an.grammar
    val cells = g.nonterminals.flatMap { nt -> (an.first.getValue(nt) - EPS).map { nt to it } }
    if (cells.isEmpty()) return null
    fun categories(nt: String, t: String): Set<Int> {
        val out = HashSet<Int>()
        for (p in g.productionsOf(nt)) for ((i, x) in p.rhs.withIndex()) {
            if (!an.isNullable(p.rhs.subList(0, i))) break
            if (t in an.firstOfSymbol(x)) out += if (i > 0) 2 else if (g.isNonterminal(x)) 1 else 0
        }
        return out
    }
    val (nt, t) = cells.maxByOrNull { (nt, t) -> categories(nt, t).maxOrNull() ?: 0 }!!
    return ExplainPrompt(
        "Explique antes de ver: por que '$t' ∈ FIRST($nt)?",
        listOf(
            "Uma alternativa de $nt começa direto com '$t'",
            "Vem do FIRST do não-terminal que inicia uma alternativa",
            "Os símbolos antes dele podem virar ε (vazamento)",
            "Porque '$t' pode vir depois de $nt (FOLLOW)",
        ),
        categories(nt, t),
        an.explainFirst(nt, t),
    )
}

fun followExplainPrompt(an: Analysis): ExplainPrompt? {
    val g = an.grammar
    val cells = g.nonterminals.flatMap { nt -> an.follow.getValue(nt).map { nt to it } }
    if (cells.isEmpty()) return null
    fun categories(nt: String, t: String): Set<Int> {
        val out = HashSet<Int>()
        if (nt == g.start && t == END) out += 0
        for ((p, i) in an.occurrences(nt)) {
            val rest = p.rhs.subList(i + 1, p.rhs.size)
            val fr = an.firstOf(rest)
            if (t in fr - EPS) out += 1
            if (EPS in fr && t in an.follow.getValue(p.lhs)) out += 2
        }
        return out
    }
    // Prefere células de herança (as mais esquecidas).
    val (nt, t) = cells.maxByOrNull { (nt, t) -> categories(nt, t).let { if (2 in it) 3 else it.maxOrNull() ?: 0 } }!!
    return ExplainPrompt(
        "Explique antes de ver: por que '$t' ∈ FOLLOW($nt)?",
        listOf(
            "$nt é o símbolo inicial (o fim da entrada vem depois)",
            "'$t' aparece logo depois de $nt em algum lado direito",
            "$nt fica no fim de uma produção X → … (ou o resto some) e herda FOLLOW(X)",
            "Porque '$t' ∈ FIRST($nt)",
        ),
        categories(nt, t),
        an.explainFollow(nt, t),
    )
}

@Composable
fun SetsGame(level: Level, follow: Boolean, showFirst: Boolean, session: LevelSession, onSolved: () -> Unit) {
    val grammar = remember(level.id) { Grammar.parse(level.grammar) }
    val an = remember(level.id) { Analysis(grammar) }
    val columns = grammar.terminals + (if (follow) END else EPS)
    val truth: Map<String, Set<String>> = if (follow) an.follow else an.first
    val name = if (follow) "FOLLOW" else "FIRST"
    val player = remember(level.id) { mutableStateMapOf<String, Set<String>>() }
    var confidence by remember { mutableStateOf<Confidence?>(null) }
    var rowErrors by remember { mutableStateOf<Map<String, Int>?>(null) }
    var message by remember { mutableStateOf<Pair<FeedbackKind, String>?>(null) }
    var solved by remember { mutableStateOf(false) }
    val prompt = remember(level.id) { if (follow) followExplainPrompt(an) else firstExplainPrompt(an) }
    var explainChoice by remember { mutableStateOf<Int?>(null) }

    fun mine(nt: String) = player[nt].orEmpty()
    fun wrongCount(nt: String) = (truth.getValue(nt) - mine(nt)).size + (mine(nt) - truth.getValue(nt)).size

    session.hintProvider = { n ->
        val wrong = grammar.nonterminals.firstOrNull { wrongCount(it) > 0 }
        if (wrong == null) "Tudo parece certo — aposte sua confiança e verifique!"
        else if (n == 2) "Confira $name($wrong): há ${wrongCount(wrong)} diferença(s) nessa linha."
        else {
            val missing = truth.getValue(wrong) - mine(wrong)
            if (missing.isNotEmpty()) {
                val t = grammar.sortTerminals(missing).first()
                if (follow) an.explainFollow(wrong, t) else an.explainFirst(wrong, t)
            } else {
                val t = (mine(wrong) - truth.getValue(wrong)).first()
                if (follow) an.explainNotInFollow(wrong, t) else an.explainNotInFirst(wrong, t)
            }
        }
    }

    fun check() {
        val c = confidence ?: return
        val errs = grammar.nonterminals.associateWith { wrongCount(it) }
        rowErrors = errs
        val ok = errs.values.all { it == 0 }
        session.check(c, ok)
        confidence = null
        if (ok) {
            solved = true
            message = FeedbackKind.GOOD to "Todos os conjuntos $name estão corretos!"
        } else {
            val types = grammar.nonterminals.flatMap { nt ->
                if (follow) Grading.followMistakes(an, nt, mine(nt)) else Grading.firstMistakes(an, nt, mine(nt))
            }.distinct()
            session.mistake(types)
            val bad = errs.count { it.value > 0 }
            val kinds = types.take(2).joinToString("; ") { it.title.lowercase() }
            message = FeedbackKind.BAD to "$bad conjunto(s) com erro (marcados em vermelho). Pista sobre o tipo de erro: $kinds."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GrammarCard(grammar)
        if (follow && showFirst) {
            Panel(title = "FIRST (referência)", accent = Tertiary, collapsible = true) {
                FirstFollowView(an, showFirst = true, showFollow = false)
            }
        }
        Panel(title = "Monte os conjuntos $name", accent = NonterminalColor) {
            Text(
                if (follow) "Toque nas células para marcar. $ = fim da entrada." else "Toque nas células para marcar. ε = pode sumir.",
                style = MaterialTheme.typography.bodySmall, color = TextDim,
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row {
                    Spacer(Modifier.width(96.dp))
                    columns.forEach { HeaderCell(symbolsText(listOf(it), grammar)) }
                }
                grammar.nonterminals.forEach { nt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(96.dp)) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = TextDim)) { append("$name(") }
                                    withStyle(SpanStyle(color = NonterminalColor, fontWeight = FontWeight.Bold)) { append(nt) }
                                    withStyle(SpanStyle(color = TextDim)) { append(")") }
                                },
                                fontFamily = Mono, fontSize = 13.sp,
                            )
                        }
                        columns.forEach { t ->
                            val on = t in mine(nt)
                            GridCell(
                                if (on) symbolsText(listOf(t), grammar) else AnnotatedString(""),
                                selected = on,
                                onClick = if (solved) null else ({
                                    player[nt] = if (on) mine(nt) - t else mine(nt) + t
                                    rowErrors = null
                                }),
                                tag = "cell:$nt:$t",
                            )
                        }
                        val e = rowErrors?.get(nt)
                        if (e != null) {
                            Text(
                                if (e == 0) " ✓" else " ✗$e", color = if (e == 0) Success else Danger,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        message?.let { (k, t) -> Feedback(k, t) }

        if (!solved) {
            CheckBar(confidence, { confidence = it }, ::check)
        } else if (prompt != null) {
            Panel(title = "Autoexplicação", accent = Tertiary) {
                Text(prompt.question, style = MaterialTheme.typography.bodyLarge, color = TextMain)
                Spacer(Modifier.height(8.dp))
                ExplainOptions(prompt, explainChoice) { i ->
                    explainChoice = i
                    session.explanation(i in prompt.valid, if (follow) MistakeType.FOLLOW_MISSED_INHERIT else MistakeType.FIRST_MISSED_NULLABLE)
                }
            }
        }
        if (solved && (prompt == null || explainChoice != null)) {
            Panel(title = "Por que cada elemento está lá", accent = Success, collapsible = true, initiallyOpen = false) {
                grammar.nonterminals.forEach { nt ->
                    grammar.sortTerminals(truth.getValue(nt)).forEach { t ->
                        Text(
                            "• " + if (follow) an.explainFollow(nt, t) else an.explainFirst(nt, t),
                            style = MaterialTheme.typography.bodySmall, color = TextMain,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            GameButton("Concluir fase ✓", onSolved, Modifier.fillMaxWidth(), color = Success)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ExplainOptions(prompt: ExplainPrompt, choice: Int?, onChoose: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        prompt.options.forEachIndexed { i, o ->
            val color = when {
                choice == null -> Tertiary
                i in prompt.valid -> Success
                i == choice -> Danger
                else -> TextDim
            }
            GameButton(o, { if (choice == null) onChoose(i) }, Modifier.fillMaxWidth(), color = color, style = ButtonStyle.OUTLINED)
        }
    }
    if (choice != null) {
        Spacer(Modifier.height(8.dp))
        Feedback(
            if (choice in prompt.valid) FeedbackKind.GOOD else FeedbackKind.WARN,
            prompt.explanation,
            title = if (choice in prompt.valid) "Isso mesmo!" else "Quase — a regra certa é outra",
        )
    }
}
