package com.felipe.compiladores.game

import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.Derivation
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.GrammarClasses
import com.felipe.compiladores.engine.Item
import com.felipe.compiladores.engine.LLMove
import com.felipe.compiladores.engine.LLParser
import com.felipe.compiladores.engine.LR0Automaton
import com.felipe.compiladores.engine.LRAction
import com.felipe.compiladores.engine.LRMove
import com.felipe.compiladores.engine.LRParser
import com.felipe.compiladores.engine.ParseStatus
import kotlin.random.Random

/** Uma pergunta de revisão, gerada a partir das gramáticas das fases (prática intercalada). */
sealed interface ReviewQuestion {
    val skill: Skill
    val grammar: Grammar
    val prompt: String
    val explanation: String
}

/** Marque todos os elementos de um conjunto (FIRST/FOLLOW). */
data class SetQuestion(
    override val skill: Skill,
    override val grammar: Grammar,
    override val prompt: String,
    val options: List<String>,
    val answer: Set<String>,
    override val explanation: String,
) : ReviewQuestion

/** Escolha uma alternativa. [context] são linhas "rótulo: valor" (pilha, entrada...). */
data class ChoiceQuestion(
    override val skill: Skill,
    override val grammar: Grammar,
    override val prompt: String,
    val context: List<Pair<String, String>>,
    val options: List<String>,
    val answer: Int,
    override val explanation: String,
) : ReviewQuestion

/** Selecione os itens de um conjunto LR(0). */
data class ItemsQuestion(
    override val skill: Skill,
    override val grammar: Grammar,
    override val prompt: String,
    val automaton: LR0Automaton,
    val sourceState: Int?,
    val symbol: String?,
    val answer: Set<Item>,
    override val explanation: String,
) : ReviewQuestion

class ReviewGenerator(private val random: Random = Random.Default) {

    private val pool: List<GrammarClasses> = Content.allLevels.map { it.grammar }.distinct()
        .map { GrammarClasses(Grammar.parse(it)) }

    private val llPool = pool.filter { it.isLL1 }
    private val slrPool = pool.filter { it.isSLR }

    fun session(skills: List<Skill>, size: Int = 6): List<ReviewQuestion> {
        if (skills.isEmpty()) return emptyList()
        // Intercala as habilidades: nunca duas iguais seguidas quando houver mais de uma.
        val out = mutableListOf<ReviewQuestion>()
        var last: Skill? = null
        repeat(size) {
            val choices = skills.filter { it != last }.ifEmpty { skills }
            val s = choices.random(random)
            out += generate(s)
            last = s
        }
        return out
    }

    fun generate(skill: Skill): ReviewQuestion = when (skill) {
        Skill.FIRST -> firstQuestion()
        Skill.FOLLOW -> followQuestion()
        Skill.LL_TABLE -> llTableQuestion()
        Skill.LL_STEP -> llStepQuestion()
        Skill.SR_STEP -> srStepQuestion()
        Skill.CLOSURE -> closureQuestion()
        Skill.SLR_ACTION -> slrActionQuestion()
    }

    private fun firstQuestion(): ReviewQuestion {
        val c = pool.random(random)
        val g = c.grammar
        val nt = g.nonterminals.random(random)
        val an = c.analysis
        val answer = an.first.getValue(nt)
        val expl = g.sortTerminals(answer).joinToString("\n") { an.explainFirst(nt, it) }
        return SetQuestion(Skill.FIRST, g, "Marque FIRST($nt).", g.terminals + EPS, answer, expl)
    }

    private fun followQuestion(): ReviewQuestion {
        val c = pool.random(random)
        val g = c.grammar
        val nt = g.nonterminals.random(random)
        val an = c.analysis
        val answer = an.follow.getValue(nt)
        val expl = g.sortTerminals(answer).joinToString("\n") { an.explainFollow(nt, it) }
        return SetQuestion(Skill.FOLLOW, g, "Marque FOLLOW($nt).", g.terminals + END, answer, expl)
    }

    private fun llTableQuestion(): ReviewQuestion {
        val c = llPool.random(random)
        val g = c.grammar
        val t = c.ll1
        val filled = t.cells.keys.toList()
        val (nt, col) = if (random.nextFloat() < 0.7f) filled.random(random)
        else g.nonterminals.random(random) to t.columns.random(random)
        val prods = g.productionsOf(nt)
        val options = prods.map { it.text } + "Célula vazia (erro)"
        val chosen = t.productions(nt, col).firstOrNull()
        val answer = if (chosen == null) options.lastIndex else prods.indexOf(chosen)
        val expl = if (chosen != null) t.explainCell(nt, col, chosen)
        else "M[$nt, $col] fica vazia. " + prods.joinToString(" ") { t.explainCell(nt, col, it) }
        return ChoiceQuestion(Skill.LL_TABLE, g, "Qual produção vai em M[$nt, $col]?", emptyList(), options, answer, expl)
    }

    private fun llStepQuestion(): ReviewQuestion {
        val c = llPool.random(random)
        val g = c.grammar
        val parser = LLParser(c.ll1)
        val tokens = maybeCorrupt(g, sampleString(g))
        val run = parser.run(tokens)
        val s = run.filter { it.status == ParseStatus.RUNNING }.random(random)
        val correct = parser.validMoves(s).first()
        val top = s.top
        val options = mutableListOf<String>()
        val moves = mutableListOf<LLMove>()
        when {
            top == END -> { options += "Aceitar"; moves += LLMove.Accept }
            !g.isNonterminal(top) -> { options += "Casar '$top'"; moves += LLMove.Match }
            else -> g.productionsOf(top).forEach { options += "Expandir ${it.text}"; moves += LLMove.Expand(it) }
        }
        options += "Erro!"; moves += LLMove.Error
        val ctx = listOf(
            "Pilha (topo à esquerda)" to s.stackTopFirst.joinToString(" "),
            "Entrada" to s.tokens.subList(s.pos, s.tokens.size).joinToString(" "),
        )
        return ChoiceQuestion(Skill.LL_STEP, g, "Qual o próximo movimento do parser LL(1)?", ctx, options, moves.indexOf(correct), parser.explain(s))
    }

    private fun srStepQuestion(): ReviewQuestion {
        val c = slrPool.random(random)
        val g = c.grammar
        val parser = LRParser(c.slrTable)
        val tokens = maybeCorrupt(g, sampleString(g))
        val run = parser.run(tokens)
        val s = run.filter { it.status == ParseStatus.RUNNING }.random(random)
        val correct = parser.validMoves(s).first()
        val syms = s.stackSymbols
        val moves = mutableListOf<LRMove>(LRMove.Shift)
        val candidates = g.productions.filter { p -> syms.size >= p.rhs.size && syms.takeLast(p.rhs.size) == p.rhs }
        candidates.forEach { moves += LRMove.Reduce(it.id) }
        if (correct is LRMove.Reduce && correct !in moves) moves += correct
        moves += LRMove.Accept
        moves += LRMove.Error
        val options = moves.map { m -> parser.moveText(m).replaceFirstChar { it.uppercase() } }
        val ctx = listOf(
            "Pilha" to ("$ " + syms.joinToString(" ")).trim(),
            "Entrada" to s.tokens.subList(s.pos, s.tokens.size).joinToString(" "),
        )
        return ChoiceQuestion(Skill.SR_STEP, g, "Qual o próximo movimento do parser SLR?", ctx, options, moves.indexOf(correct), parser.explainCorrect(s))
    }

    private fun closureQuestion(): ReviewQuestion {
        val c = slrPool.random(random)
        val a = c.lr0Automaton
        val edges = a.transitions.entries.toList()
        return if (random.nextFloat() < 0.25f) {
            ItemsQuestion(
                Skill.CLOSURE, c.grammar, "Monte closure({ ${a.itemText(Item(0, 0))} }).",
                a, null, null, a.states[0].toSet(),
                "O fechamento começa no núcleo e, para cada • antes de um não-terminal, puxa as produções dele com o ponto no início.",
            )
        } else {
            val e = edges.random(random)
            val (from, x) = e.key
            val to = e.value
            ItemsQuestion(
                Skill.CLOSURE, c.grammar, "Monte goto(I$from, $x).", a, from, x, a.states[to].toSet(),
                "Avance o ponto sobre $x nos itens de I$from que têm • antes de $x, e depois faça o fechamento. O resultado é o estado I$to.",
            )
        }
    }

    private fun slrActionQuestion(): ReviewQuestion {
        val c = slrPool.random(random)
        val a = c.lr0Automaton
        val table = c.slrTable
        val s = random.nextInt(a.states.size)
        val cols = table.actionColumns
        val interesting = cols.filter { table.actions(s, it).isNotEmpty() }
        val col = if (interesting.isNotEmpty() && random.nextFloat() < 0.75f) interesting.random(random) else cols.random(random)
        val truth = table.actions(s, col).firstOrNull()
        val correctText = truth?.toString() ?: "erro (vazia)"
        val candidates = linkedSetOf(correctText)
        a.transitions.filterKeys { it.first == s && !a.grammar.isNonterminal(it.second) }.values.forEach { candidates += "s$it" }
        a.states[s].filter { a.isComplete(it) && it.prod != 0 }.forEach { candidates += "r${it.prod}" }
        candidates += "erro (vazia)"
        candidates += "acc"
        var guard = 0
        while (candidates.size < 4 && guard++ < 20) {
            candidates += if (random.nextBoolean()) "s${random.nextInt(a.states.size)}" else "r${1 + random.nextInt(c.grammar.productions.size)}"
        }
        val options = candidates.take(5).shuffled(random)
        val an = Analysis(a.grammar)
        val expl = when (truth) {
            is LRAction.Shift -> "I$s tem transição com '$col' para I${truth.state}: ACTION = s${truth.state}."
            is LRAction.Reduce -> {
                val p = a.prod(truth.prod)
                "O item ${a.itemText(Item(p.id, p.rhs.size))} está em I$s e '$col' ∈ FOLLOW(${p.lhs}) = ${an.followText(p.lhs)}: ACTION = r${p.id}."
            }
            LRAction.Accept -> "I$s contém ${a.itemText(Item(0, 1))} e a entrada acabou: aceitar."
            null -> "Nenhum item de I$s tem • antes de '$col', e nenhum item completo tem '$col' no FOLLOW: célula vazia (erro)."
        }
        val ctx = listOf("Estado I$s" to a.stateText(s)) +
            a.grammar.nonterminals.drop(1).map { "FOLLOW($it)" to an.followText(it) }
        return ChoiceQuestion(Skill.SLR_ACTION, c.grammar, "Quanto vale ACTION[$s, $col] na tabela SLR?", ctx, options, options.indexOf(correctText), expl)
    }

    /** Às vezes estraga a cadeia, para treinar a detecção de erros. */
    private fun maybeCorrupt(g: Grammar, tokens: List<String>): List<String> {
        if (random.nextFloat() >= 0.25f || tokens.isEmpty()) return tokens
        val t = tokens.toMutableList()
        val i = random.nextInt(t.size)
        if (random.nextBoolean() && t.size > 1) t.removeAt(i) else t[i] = g.terminals.random(random)
        return t
    }

    /** Gera uma cadeia da linguagem com derivação aleatória (curta). */
    fun sampleString(g: Grammar, maxLen: Int = 8): List<String> {
        val minLen = Derivation.minimalLengths(g)
        fun cost(rhs: List<String>) = rhs.sumOf { if (g.isNonterminal(it)) minLen.getValue(it) else 1 }
        var best: List<String>? = null
        repeat(40) {
            val out = mutableListOf<String>()
            var ok = true
            fun expand(sym: String, depth: Int) {
                if (!ok) return
                if (depth > 40) { ok = false; return }
                if (!g.isNonterminal(sym)) { out += sym; return }
                val prods = g.productionsOf(sym).filter { cost(it.rhs) < Int.MAX_VALUE / 4 }
                val p = if (depth > 3) prods.minBy { cost(it.rhs) } else prods.random(random)
                p.rhs.forEach { expand(it, depth + 1) }
            }
            expand(g.start, 0)
            if (ok && out.size in 1..maxLen) return out
            if (ok && (best == null || out.size < best!!.size)) best = out
        }
        return best ?: emptyList()
    }
}
