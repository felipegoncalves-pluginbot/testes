package com.felipe.compiladores.engine

/** Item LR(0): produção (id na gramática aumentada) com o ponto na posição [dot]. */
data class Item(val prod: Int, val dot: Int)

/** Item LR(1): item LR(0) + símbolo de lookahead. */
data class LR1Item(val prod: Int, val dot: Int, val la: String) {
    val core: Item get() = Item(prod, dot)
}

/** Base comum: gramática aumentada e utilidades de itens. */
open class ItemGrammar(original: Grammar) {
    val original: Grammar = original
    val grammar: Grammar = original.augmented()
    private val prods: Map<Int, Production> = grammar.productions.associateBy { it.id }

    fun prod(id: Int): Production = prods.getValue(id)
    fun next(item: Item): String? = prod(item.prod).rhs.getOrNull(item.dot)
    fun isComplete(item: Item) = item.dot >= prod(item.prod).rhs.size

    fun itemText(item: Item): String {
        val p = prod(item.prod)
        val parts = p.rhs.toMutableList()
        parts.add(item.dot, "•")
        return "${p.lhs} → ${parts.joinToString(" ")}"
    }

    /** Todos os itens possíveis da gramática aumentada, na ordem das produções. */
    val allItems: List<Item> by lazy {
        grammar.productions.flatMap { p -> (0..p.rhs.size).map { Item(p.id, it) } }
    }
}

/** Autômato LR(0) (coleção canônica de conjuntos de itens). */
class LR0Automaton(original: Grammar) : ItemGrammar(original) {
    val states: List<List<Item>>
    val kernels: List<List<Item>>
    val transitions: Map<Pair<Int, String>, Int>

    init {
        val st = mutableListOf<List<Item>>()
        val ks = mutableListOf<List<Item>>()
        val tr = LinkedHashMap<Pair<Int, String>, Int>()
        val index = HashMap<Set<Item>, Int>()
        val k0 = listOf(Item(0, 0))
        ks += k0; st += closure(k0); index[k0.toSet()] = 0
        var i = 0
        while (i < st.size) {
            for (x in symbolsAfterDot(st[i])) {
                val k = advance(st[i], x)
                val key = k.toSet()
                val j = index.getOrPut(key) {
                    ks += k; st += closure(k); st.size - 1
                }
                tr[i to x] = j
            }
            i++
        }
        states = st; kernels = ks; transitions = tr
    }

    fun closure(kernel: List<Item>): List<Item> {
        val out = kernel.toMutableList()
        val seen = kernel.toHashSet()
        var i = 0
        while (i < out.size) {
            val x = next(out[i])
            if (x != null && grammar.isNonterminal(x)) {
                for (p in grammar.productionsOf(x)) {
                    val it = Item(p.id, 0)
                    if (seen.add(it)) out += it
                }
            }
            i++
        }
        return out
    }

    fun symbolsAfterDot(items: List<Item>): List<String> = items.mapNotNull { next(it) }.distinct()

    /** Núcleo de goto(I, X): itens com o ponto avançado sobre X. */
    fun advance(items: List<Item>, x: String): List<Item> =
        items.filter { next(it) == x }.map { it.copy(dot = it.dot + 1) }

    fun goto(items: List<Item>, x: String): List<Item> = closure(advance(items, x))

    /** Roda o AFD sobre uma sequência de símbolos; null se ela não for prefixo viável. */
    fun run(symbols: List<String>): Int? {
        var s = 0
        for (x in symbols) s = transitions[s to x] ?: return null
        return s
    }

    fun stateText(i: Int): String = states[i].joinToString("\n") { itemText(it) }
}

/** Autômato LR(1) canônico. */
class LR1Automaton(original: Grammar) : ItemGrammar(original) {
    val analysis = Analysis(grammar)
    val states: List<List<LR1Item>>
    val transitions: Map<Pair<Int, String>, Int>

    init {
        val st = mutableListOf<List<LR1Item>>()
        val tr = LinkedHashMap<Pair<Int, String>, Int>()
        val index = HashMap<Set<LR1Item>, Int>()
        val s0 = closure(listOf(LR1Item(0, 0, END)))
        st += s0; index[s0.toSet()] = 0
        var i = 0
        while (i < st.size) {
            val syms = st[i].mapNotNull { next(it.core) }.distinct()
            for (x in syms) {
                val k = st[i].filter { next(it.core) == x }.map { it.copy(dot = it.dot + 1) }
                val c = closure(k)
                val key = c.toSet()
                val j = index.getOrPut(key) { st += c; st.size - 1 }
                tr[i to x] = j
            }
            i++
        }
        states = st; transitions = tr
    }

    fun closure(kernel: List<LR1Item>): List<LR1Item> {
        val out = kernel.toMutableList()
        val seen = kernel.toHashSet()
        var i = 0
        while (i < out.size) {
            val item = out[i]
            val p = prod(item.prod)
            val x = p.rhs.getOrNull(item.dot)
            if (x != null && grammar.isNonterminal(x)) {
                val beta = p.rhs.subList(item.dot + 1, p.rhs.size) + item.la
                val las = analysis.firstOf(beta) - EPS
                for (q in grammar.productionsOf(x)) for (b in las) {
                    val ni = LR1Item(q.id, 0, b)
                    if (seen.add(ni)) out += ni
                }
            }
            i++
        }
        return out
    }

    fun itemText(item: LR1Item) = "[${itemText(item.core)}, ${item.la}]"
}

sealed interface LRAction {
    data class Shift(val state: Int) : LRAction { override fun toString() = "s$state" }
    data class Reduce(val prod: Int) : LRAction { override fun toString() = "r$prod" }
    data object Accept : LRAction { override fun toString() = "acc" }
}

enum class LRKind(val label: String) { LR0("LR(0)"), SLR("SLR(1)"), LALR("LALR(1)"), LR1("LR(1)") }

data class LRConflict(val state: Int, val terminal: String, val actions: List<LRAction>) {
    val isShiftReduce: Boolean get() = actions.any { it is LRAction.Shift }
    val typeText: String get() = if (isShiftReduce) "shift/reduce" else "reduce/reduce"
}

class LRTable(
    val kind: LRKind,
    val items: ItemGrammar,
    val stateCount: Int,
    val action: Map<Pair<Int, String>, List<LRAction>>,
    val goto: Map<Pair<Int, String>, Int>,
) {
    val grammar: Grammar get() = items.grammar
    val actionColumns: List<String> = items.original.terminals + END
    val gotoColumns: List<String> = items.original.nonterminals

    fun actions(state: Int, t: String): List<LRAction> = action[state to t].orEmpty()

    val conflicts: List<LRConflict>
        get() = action.filterValues { it.size > 1 }.map { (k, v) -> LRConflict(k.first, k.second, v) }

    val hasConflicts: Boolean get() = conflicts.isNotEmpty()

    fun conflictText(c: LRConflict): String {
        val parts = c.actions.joinToString(" e ") { a ->
            when (a) {
                is LRAction.Shift -> "empilhar (s${a.state})"
                is LRAction.Reduce -> "reduzir por (${a.prod}) ${items.prod(a.prod).text}"
                LRAction.Accept -> "aceitar"
            }
        }
        return "Conflito ${c.typeText} no estado ${c.state} com '${c.terminal}': $parts."
    }

    companion object {
        fun lr0(a: LR0Automaton): LRTable = fromLR0(a, LRKind.LR0) { _ -> a.original.terminals + END }

        fun slr(a: LR0Automaton): LRTable {
            val an = Analysis(a.grammar)
            return fromLR0(a, LRKind.SLR) { lhs -> an.follow.getValue(lhs).toList() }
        }

        private fun fromLR0(a: LR0Automaton, kind: LRKind, reduceOn: (String) -> List<String>): LRTable {
            val action = LinkedHashMap<Pair<Int, String>, MutableList<LRAction>>()
            val goto = LinkedHashMap<Pair<Int, String>, Int>()
            fun add(s: Int, t: String, act: LRAction) {
                val l = action.getOrPut(s to t) { mutableListOf() }
                if (act !in l) l += act
            }
            for ((i, items) in a.states.withIndex()) {
                for (x in a.symbolsAfterDot(items)) {
                    val j = a.transitions.getValue(i to x)
                    if (a.grammar.isNonterminal(x)) goto[i to x] = j else add(i, x, LRAction.Shift(j))
                }
                for (it in items) {
                    if (!a.isComplete(it)) continue
                    if (it.prod == 0) add(i, END, LRAction.Accept)
                    else for (t in reduceOn(a.prod(it.prod).lhs)) add(i, t, LRAction.Reduce(it.prod))
                }
            }
            return LRTable(kind, a, a.states.size, action, goto)
        }

        fun lr1(a: LR1Automaton): LRTable {
            val action = LinkedHashMap<Pair<Int, String>, MutableList<LRAction>>()
            val goto = LinkedHashMap<Pair<Int, String>, Int>()
            fun add(s: Int, t: String, act: LRAction) {
                val l = action.getOrPut(s to t) { mutableListOf() }
                if (act !in l) l += act
            }
            for ((k, j) in a.transitions) {
                if (a.grammar.isNonterminal(k.second)) goto[k] = j else add(k.first, k.second, LRAction.Shift(j))
            }
            for ((i, items) in a.states.withIndex()) for (it in items) {
                if (!a.isComplete(it.core)) continue
                if (it.prod == 0) add(i, END, LRAction.Accept) else add(i, it.la, LRAction.Reduce(it.prod))
            }
            return LRTable(LRKind.LR1, a, a.states.size, action, goto)
        }

        /** LALR(1) numerado como o autômato LR(0): lookaheads unidos dos estados LR(1) de mesmo núcleo. */
        fun lalr(a0: LR0Automaton, a1: LR1Automaton): LRTable {
            val coreIndex = a0.states.withIndex().associate { it.value.toSet() to it.index }
            val la = HashMap<Pair<Int, Item>, MutableSet<String>>()
            for (items in a1.states) {
                val s = coreIndex.getValue(items.map { it.core }.toSet())
                for (it in items) la.getOrPut(s to it.core) { linkedSetOf() }.add(it.la)
            }
            val action = LinkedHashMap<Pair<Int, String>, MutableList<LRAction>>()
            val goto = LinkedHashMap<Pair<Int, String>, Int>()
            fun add(s: Int, t: String, act: LRAction) {
                val l = action.getOrPut(s to t) { mutableListOf() }
                if (act !in l) l += act
            }
            for ((i, items) in a0.states.withIndex()) {
                for (x in a0.symbolsAfterDot(items)) {
                    val j = a0.transitions.getValue(i to x)
                    if (a0.grammar.isNonterminal(x)) goto[i to x] = j else add(i, x, LRAction.Shift(j))
                }
                for (it in items) {
                    if (!a0.isComplete(it)) continue
                    if (it.prod == 0) add(i, END, LRAction.Accept)
                    else for (t in la[i to it].orEmpty()) add(i, t, LRAction.Reduce(it.prod))
                }
            }
            return LRTable(LRKind.LALR, a0, a0.states.size, action, goto)
        }

        /** Lookaheads LALR de cada item de cada estado LR(0) (para exibição). */
        fun lalrLookaheads(a0: LR0Automaton, a1: LR1Automaton): Map<Pair<Int, Item>, Set<String>> {
            val coreIndex = a0.states.withIndex().associate { it.value.toSet() to it.index }
            val la = HashMap<Pair<Int, Item>, MutableSet<String>>()
            for (items in a1.states) {
                val s = coreIndex.getValue(items.map { it.core }.toSet())
                for (it in items) la.getOrPut(s to it.core) { linkedSetOf() }.add(it.la)
            }
            return la
        }
    }
}

/** Resumo das classes às quais a gramática pertence. */
class GrammarClasses(val grammar: Grammar) {
    val analysis = Analysis(grammar)
    val ll1 = LL1Table(analysis)
    val lr0Automaton = LR0Automaton(grammar)
    val lr1Automaton by lazy { LR1Automaton(grammar) }
    val lr0Table = LRTable.lr0(lr0Automaton)
    val slrTable = LRTable.slr(lr0Automaton)
    val lalrTable by lazy { LRTable.lalr(lr0Automaton, lr1Automaton) }
    val lr1Table by lazy { LRTable.lr1(lr1Automaton) }

    val isLL1 get() = ll1.isLL1
    val isLR0 get() = !lr0Table.hasConflicts
    val isSLR get() = !slrTable.hasConflicts
    val isLALR get() = !lalrTable.hasConflicts
    val isLR1 get() = !lr1Table.hasConflicts

    fun table(kind: LRKind): LRTable = when (kind) {
        LRKind.LR0 -> lr0Table
        LRKind.SLR -> slrTable
        LRKind.LALR -> lalrTable
        LRKind.LR1 -> lr1Table
    }

    fun belongs(kind: LRKind) = !table(kind).hasConflicts
}

// ------------------------------------------------------------------ parser shift-reduce

sealed interface LRMove {
    data object Shift : LRMove
    data class Reduce(val prod: Int) : LRMove
    data object Accept : LRMove
    data object Error : LRMove
}

/** Configuração do parser LR: pilha de estados e, em paralelo, os nós da árvore empilhados. */
data class LRState(
    val states: List<Int>,
    val nodes: List<Int>,
    val tokens: List<String>,
    val pos: Int,
    val tree: ParseTree,
    val status: ParseStatus = ParseStatus.RUNNING,
    val steps: Int = 0,
) {
    val lookahead: String get() = tokens[pos]
    val topState: Int get() = states.last()
    val stackSymbols: List<String> get() = nodes.map { tree.symbol(it) }

    /** Forma sentencial (mais à direita) atual: pilha + resto da entrada. */
    val sententialForm: List<String> get() = stackSymbols + tokens.subList(pos, tokens.size - 1)
}

class LRParser(val table: LRTable) {
    private val automaton = table.items as? LR0Automaton
    private val grammar get() = table.grammar
    private val follow by lazy { Analysis(grammar).follow }

    fun initial(tokens: List<String>) = LRState(listOf(0), emptyList(), tokens + END, 0, ParseTree())

    fun validMoves(s: LRState): List<LRMove> {
        val acts = table.actions(s.topState, s.lookahead)
        if (acts.isEmpty()) return listOf(LRMove.Error)
        return acts.map {
            when (it) {
                is LRAction.Shift -> LRMove.Shift
                is LRAction.Reduce -> LRMove.Reduce(it.prod)
                LRAction.Accept -> LRMove.Accept
            }
        }
    }

    fun isValid(s: LRState, m: LRMove) = m in validMoves(s)

    fun apply(s: LRState, m: LRMove): LRState = when (m) {
        LRMove.Shift -> {
            val target = table.actions(s.topState, s.lookahead).filterIsInstance<LRAction.Shift>().first().state
            val (t, id) = s.tree.add(s.lookahead)
            s.copy(states = s.states + target, nodes = s.nodes + id, pos = s.pos + 1, tree = t, steps = s.steps + 1)
        }
        is LRMove.Reduce -> {
            val p = grammar.production(m.prod)
            val n = p.rhs.size
            val kids = s.nodes.takeLast(n)
            var (t, parent) = s.tree.add(p.lhs)
            val children = if (p.isEpsilon) {
                val (t2, eps) = t.add(EPS); t = t2; listOf(eps)
            } else kids
            t = t.withChildren(parent, children)
            val states = s.states.dropLast(n)
            val g = table.goto[states.last() to p.lhs] ?: error("sem goto")
            s.copy(states = states + g, nodes = s.nodes.dropLast(n) + parent, tree = t, steps = s.steps + 1)
        }
        LRMove.Accept -> s.copy(status = ParseStatus.ACCEPTED, steps = s.steps + 1)
        LRMove.Error -> s.copy(status = ParseStatus.REJECTED, steps = s.steps + 1)
    }

    fun run(tokens: List<String>, maxSteps: Int = 10_000): List<LRState> {
        val out = mutableListOf(initial(tokens))
        while (out.last().status == ParseStatus.RUNNING && out.size < maxSteps) {
            out += apply(out.last(), validMoves(out.last()).first())
        }
        return out
    }

    fun moveText(m: LRMove): String = when (m) {
        LRMove.Shift -> "empilhar (shift)"
        is LRMove.Reduce -> "reduzir por ${grammar.production(m.prod).text}"
        LRMove.Accept -> "aceitar"
        LRMove.Error -> "declarar erro"
    }

    /** Explica qual é o movimento correto e por quê. */
    fun explainCorrect(s: LRState): String {
        val la = s.lookahead
        val st = s.topState
        return when (val m = validMoves(s).first()) {
            LRMove.Shift -> "Estado $st com '$la': ACTION = s${(table.actions(st, la).first { it is LRAction.Shift } as LRAction.Shift).state}. Ainda não há handle completo no topo — empilhe '$la'."
            is LRMove.Reduce -> {
                val p = grammar.production(m.prod)
                val item = table.items.itemText(Item(p.id, p.rhs.size))
                val why = if (table.kind == LRKind.SLR) "'$la' ∈ FOLLOW(${p.lhs})" else "'$la' é lookahead desse item"
                val handle = if (p.isEpsilon) "Um ${p.lhs} vazio precisa ser criado aqui" else "O topo da pilha termina com ${p.rhsText}, que é o handle"
                "$handle: o item $item está no estado $st e $why. Reduza por ${p.text}."
            }
            LRMove.Accept -> "A pilha tem só o símbolo inicial e a entrada acabou ($): aceitar!"
            LRMove.Error -> "ACTION[$st, '$la'] está vazia: nenhum item deste estado espera '$la'. A entrada tem um erro."
        }
    }

    /** Explica por que [chosen] está errado na configuração [s]. */
    fun explainWrong(s: LRState, chosen: LRMove): String {
        val la = s.lookahead
        val syms = s.stackSymbols
        val stackText = if (syms.isEmpty()) "vazia" else syms.joinToString(" ")
        return when (chosen) {
            is LRMove.Reduce -> {
                val p = grammar.production(chosen.prod)
                val suffixOk = syms.size >= p.rhs.size && syms.takeLast(p.rhs.size) == p.rhs
                when {
                    !suffixOk -> "A pilha ($stackText) não termina com ${p.rhsText}, então não dá para reduzir por ${p.text}."
                    la !in follow.getValue(p.lhs) ->
                        "Reduzir por ${p.text} poria ${p.lhs} antes de '$la', mas '$la' ∉ FOLLOW(${p.lhs}) = ${grammar.setText(follow.getValue(p.lhs))}. Nenhuma forma sentencial tem ${p.lhs} seguido de '$la'."
                    automaton != null && automaton.run(syms.dropLast(p.rhs.size) + p.lhs) == null ->
                        "Depois de reduzir, a pilha ficaria ${(syms.dropLast(p.rhs.size) + p.lhs).joinToString(" ")}, que não é prefixo viável: nenhum item do autômato aceita essa sequência. ${p.rhsText} não é o handle aqui."
                    else -> "${p.rhsText} não é o handle neste momento (o item ${p.lhs} → ${p.rhsText} • não está no estado ${s.topState})."
                }
            }
            LRMove.Shift -> when {
                la == END -> "A entrada acabou ($): não há o que empilhar."
                automaton != null && automaton.run(syms + la) == null ->
                    "Empilhar '$la' deixaria a pilha como ${(syms + la).joinToString(" ")}, que não é prefixo viável. " +
                        if (validMoves(s).first() is LRMove.Reduce) "O handle no topo ficaria enterrado para sempre." else "A entrada tem um erro aqui."
                else -> "O estado ${s.topState} não tem ação de shift para '$la'."
            }
            LRMove.Accept -> "Só aceitamos quando a pilha contém apenas o símbolo inicial e a entrada acabou."
            LRMove.Error -> "Ainda existe um movimento válido: ACTION[${s.topState}, '$la'] não está vazia."
        }
    }
}
