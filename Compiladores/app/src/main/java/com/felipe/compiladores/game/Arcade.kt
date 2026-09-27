package com.felipe.compiladores.game

import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.GrammarClasses
import com.felipe.compiladores.engine.LRMove
import com.felipe.compiladores.engine.LRParser
import com.felipe.compiladores.engine.ParseStatus
import com.felipe.compiladores.engine.Production
import kotlin.random.Random

/** Os jogos do Treino Relâmpago: partidas curtas, cada uma treinando uma habilidade (como no Peak). */
enum class ArcadeGame(val id: String, val title: String, val emoji: String, val skill: String, val description: String) {
    FIRST_RAIN("chuva", "Chuva de FIRST", "🌧️", "Velocidade", "Toque só nos tokens que pertencem ao FIRST pedido antes que caiam."),
    HANDLE_HUNT("handle", "Caça ao Handle", "🎯", "Análise", "Qual pedaço da forma sentencial é o handle? Rápido!"),
    STACK_MEMORY("pilha", "Pilha na Memória", "🧠", "Memória", "Guarde as operações de cabeça e reconstrua a pilha."),
    WHO_AM_I("quem", "Quem sou eu?", "🕵️", "Conceitos", "Descubra o conceito com o mínimo de pistas."),
}

/** Gramáticas usadas nos jogos relâmpago (as mesmas das fases). */
object ArcadePool {
    val all: List<GrammarClasses> by lazy {
        Content.allLevels.map { it.grammar }.filter { it.isNotBlank() }.distinct().map { GrammarClasses(Grammar.parse(it)) }
    }
    val ll: List<GrammarClasses> by lazy { all.filter { it.isLL1 } }
    val slr: List<GrammarClasses> by lazy { all.filter { it.isSLR } }
}

// ======================================================================== Chuva de FIRST

data class Drop(val id: Int, val symbol: String, val x: Float, val y: Float, val speed: Float, val good: Boolean)

data class FirstRainState(
    val grammar: Grammar,
    val nt: String,
    val first: Set<String>,
    val level: Int,
    val drops: List<Drop> = emptyList(),
    val time: Float = 0f,
    val spawnIn: Float = 0.3f,
    val score: Int = 0,
    val combo: Int = 0,
    val lives: Int = 3,
    val caught: Int = 0,
    val nextId: Int = 0,
    val mistakes: Int = 0,
    val message: String? = null,
) {
    val duration: Float get() = 60f
    val over: Boolean get() = lives <= 0 || time >= duration
}

object FirstRain {
    private val targets: List<Triple<Grammar, String, Set<String>>> by lazy {
        ArcadePool.all.flatMap { c ->
            c.grammar.nonterminals.map { nt -> Triple(c.grammar, nt, c.analysis.first.getValue(nt) - EPS) }
        }.filter { (g, _, f) -> f.isNotEmpty() && g.terminals.size >= 3 && f.size < g.terminals.size }
    }

    fun target(level: Int) = 120 + 40 * level

    fun start(level: Int, random: Random): FirstRainState {
        val (g, nt, f) = targets.random(random)
        return FirstRainState(g, nt, f, level)
    }

    private fun newTarget(s: FirstRainState, random: Random): FirstRainState {
        val (g, nt, f) = targets.filter { it.second != s.nt || it.first != s.grammar }.random(random)
        return s.copy(grammar = g, nt = nt, first = f, drops = emptyList(), spawnIn = 0.6f, message = "Novo alvo: FIRST($nt)")
    }

    fun step(s: FirstRainState, dt: Float, random: Random): FirstRainState {
        if (s.over) return s
        var combo = s.combo
        val kept = mutableListOf<Drop>()
        for (d in s.drops) {
            val moved = d.copy(y = d.y + d.speed * dt)
            if (moved.y > 1f) { if (d.good) combo = 0 } else kept += moved
        }
        var spawnIn = s.spawnIn - dt
        var nextId = s.nextId
        if (spawnIn <= 0f) {
            val good = random.nextFloat() < 0.55f
            val bad = s.grammar.terminals - s.first
            val symbol = if (good || bad.isEmpty()) s.first.random(random) else bad.random(random)
            val speed = (0.16f + 0.025f * s.level) * (0.85f + random.nextFloat() * 0.3f)
            kept += Drop(nextId++, symbol, 0.1f + random.nextFloat() * 0.8f, -0.05f, speed, symbol in s.first)
            spawnIn = (1.15f - 0.06f * s.level).coerceAtLeast(0.45f)
        }
        return s.copy(drops = kept, time = s.time + dt, spawnIn = spawnIn, combo = combo, nextId = nextId)
    }

    fun tap(s: FirstRainState, id: Int, random: Random): FirstRainState {
        if (s.over) return s
        val d = s.drops.firstOrNull { it.id == id } ?: return s
        val rest = s.drops - d
        return if (d.good) {
            val caught = s.caught + 1
            val next = s.copy(drops = rest, score = s.score + 10 * (1 + s.combo / 4), combo = s.combo + 1, caught = caught, message = null)
            if (caught % 6 == 0) newTarget(next, random) else next
        } else {
            s.copy(
                drops = rest, lives = s.lives - 1, combo = 0, mistakes = s.mistakes + 1,
                message = "'${d.symbol}' ∉ FIRST(${s.nt}) = ${s.grammar.setText(s.first)}",
            )
        }
    }
}

// ======================================================================== Caça ao Handle

data class HandleQuestion(
    val grammar: Grammar,
    val form: List<String>,
    val handle: IntRange,
    val production: Production,
    val options: List<IntRange>,
)

object HandleHunt {
    const val DURATION = 60f
    fun target(level: Int) = 50 + 20 * level

    fun question(random: Random): HandleQuestion {
        repeat(50) {
            val c = ArcadePool.slr.random(random)
            val g = c.grammar
            val parser = LRParser(c.slrTable)
            val tokens = sampleString(g, random)
            val run = parser.run(tokens)
            if (run.last().status != ParseStatus.ACCEPTED) return@repeat
            val reductions = run.filter { st ->
                val m = parser.validMoves(st).first()
                m is LRMove.Reduce && !g.production(m.prod).isEpsilon
            }
            if (reductions.isEmpty()) return@repeat
            val st = reductions.random(random)
            val p = g.production((parser.validMoves(st).first() as LRMove.Reduce).prod)
            val form = st.sententialForm
            val end = st.stackSymbols.size
            val handle = (end - p.rhs.size) until end
            // Distrações: outros trechos que parecem lados direitos, mas não são o handle.
            val lookalikes = mutableListOf<IntRange>()
            for (q in g.productions) {
                if (q.rhs.isEmpty()) continue
                for (i in 0..form.size - q.rhs.size) {
                    val r = i until i + q.rhs.size
                    if (r != handle && form.subList(i, i + q.rhs.size) == q.rhs && r !in lookalikes) lookalikes += r
                }
            }
            val others = lookalikes.shuffled(random).take(2).toMutableList()
            var guard = 0
            while (others.size < 2 && guard++ < 20) {
                val i = random.nextInt(form.size)
                val r = i until (i + 1 + random.nextInt(2)).coerceAtMost(form.size)
                if (r != handle && r !in others) others += r
            }
            if (form.size < 2) return@repeat
            return HandleQuestion(g, form, handle, p, (others + listOf(handle)).shuffled(random))
        }
        error("não foi possível gerar pergunta")
    }
}

// ======================================================================== Pilha na Memória

sealed interface StackOp {
    data class Push(val symbol: String) : StackOp
    data object Pop : StackOp
    data class Expand(val production: Production) : StackOp
}

data class MemoryRound(val start: List<String>, val ops: List<StackOp>, val answer: List<String>, val palette: List<String>)

object StackMemory {
    fun opsFor(level: Int) = 2 + level

    fun describe(op: StackOp): String = when (op) {
        is StackOp.Push -> "empilha ${op.symbol}"
        StackOp.Pop -> "desempilha"
        is StackOp.Expand -> "troca ${op.production.lhs} por ${op.production.rhsText}"
    }

    fun apply(stack: List<String>, op: StackOp): List<String> = when (op) {
        is StackOp.Push -> stack + op.symbol
        StackOp.Pop -> stack.dropLast(1)
        is StackOp.Expand -> stack.dropLast(1) + op.production.rhs.asReversed()
    }

    fun round(length: Int, random: Random): MemoryRound {
        val g = ArcadePool.ll.random(random).grammar
        val start = listOf("$", g.start)
        var stack = start
        val ops = mutableListOf<StackOp>()
        while (ops.size < length) {
            val top = stack.lastOrNull()
            val op: StackOp = when {
                top != null && g.isNonterminal(top) && random.nextFloat() < 0.55f -> {
                    val options = g.productionsOf(top).filter { stack.size - 1 + it.rhs.size <= 7 }
                    if (options.isEmpty()) StackOp.Pop else StackOp.Expand(options.random(random))
                }
                stack.size > 1 && random.nextFloat() < 0.4f -> StackOp.Pop
                stack.size < 7 -> StackOp.Push(g.symbols.random(random))
                else -> StackOp.Pop
            }
            ops += op
            stack = apply(stack, op)
        }
        val palette = (stack.filter { it != "$" } + g.symbols).distinct().take(8)
        return MemoryRound(start, ops, stack, palette)
    }
}

// ======================================================================== Quem sou eu?

data class Concept(val name: String, val clues: List<String>)

object WhoAmI {
    val concepts = listOf(
        Concept("FIRST", listOf("Eu olho para o começo.", "Se o primeiro da fila some, eu espio o próximo.", "Posso conter ε.", "Cada lado direito também tem o seu.")),
        Concept("FOLLOW", listOf("Nunca carrego ε.", "O $ adora morar em mim.", "Você me acha procurando o não-terminal nos lados direitos.", "Sou quem vem DEPOIS.")),
        Concept("ε (cadeia vazia)", listOf("Tenho tamanho zero.", "Quando apareço, alguém some sem deixar rastro.", "Nunca entro num FOLLOW.", "Posso morar num FIRST.")),
        Concept("$ (fim da entrada)", listOf("Sou o último token de toda frase.", "Moro no fundo da pilha.", "Sempre sigo o símbolo inicial.", "Quando pilha e entrada chegam em mim, é aceite.")),
        Concept("Handle", listOf("Sou um lado direito no topo da pilha.", "Quando me encontram, fazem reduce.", "Se um shift me enterra, o parser se perde.", "Parsers bottom-up vivem me caçando.")),
        Concept("Shift", listOf("Pego o próximo token e ponho na pilha.", "Sou metade do nome de um parser bottom-up.", "Na tabela SLR apareço como sN.")),
        Concept("Reduce", listOf("Troco vários símbolos do topo por um só.", "Na tabela apareço como rN.", "Faço a árvore crescer para cima.")),
        Concept("Conflito", listOf("Duas ações disputam a mesma célula.", "Onde eu apareço, a gramática não é daquela classe.", "Posso ser shift/reduce ou reduce/reduce.")),
        Concept("Recursão à esquerda", listOf("Faço o parser top-down girar para sempre.", "Tenho a cara de A → A α.", "O LR não tem medo de mim.", "A cirurgia me troca por A → β A'.")),
        Concept("Fatoração à esquerda", listOf("Resolvo prefixos comuns.", "Crio um não-terminal novo para adiar a decisão.", "A → α β1 | α β2 é meu paciente.")),
        Concept("Item LR(0)", listOf("Sou uma produção com um ponto.", "Mostro o que já foi visto e o que ainda falta.", "Em grupo, viramos estados de um autômato.")),
        Concept("Fechamento (closure)", listOf("Se tem ponto antes de B, eu puxo todas as regras de B.", "Repito até nada novo aparecer.", "Transformo um núcleo num estado completo.")),
        Concept("goto", listOf("Ando com o ponto por cima de um símbolo.", "Levo de um estado a outro.", "Com não-terminais, formo a parte da tabela que tem meu nome.")),
        Concept("Gramática ambígua", listOf("Dou duas árvores para a mesma frase.", "Nenhum LR(k) me aceita.", "O dangling else é meu exemplo famoso.")),
        Concept("Derivação mais à esquerda", listOf("Troco sempre o primeiro não-terminal.", "O parser LL me segue passo a passo.", "Entrada lida + pilha LL = eu.")),
        Concept("Tabela LL(1)", listOf("Linha = não-terminal, coluna = token.", "Sou a cola do parser preditivo.", "Se uma célula minha tem duas regras, deu conflito.")),
        Concept("LALR(1)", listOf("Junto estados do LR(1) que têm o mesmo núcleo.", "O yacc e o bison me usam.", "Às vezes, ao fundir estados, crio um reduce/reduce.")),
        Concept("Não-terminal anulável", listOf("Posso derivar ε.", "Quando sou o primeiro da fila, o FIRST espia o próximo.", "Meu FIRST contém ε.")),
    )

    const val ROUNDS = 6
    fun points(cluesUsed: Int) = when (cluesUsed) { 1 -> 100; 2 -> 70; 3 -> 40; else -> 20 }
    fun target(level: Int) = 300 + 20 * level

    fun options(answer: Concept, random: Random, count: Int = 4): List<String> =
        (concepts.filter { it != answer }.shuffled(random).take(count - 1).map { it.name } + answer.name).shuffled(random)
}
