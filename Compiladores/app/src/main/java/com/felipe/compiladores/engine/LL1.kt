package com.felipe.compiladores.engine

/** Qual regra da construção colocou a produção na célula M[A, a]. */
enum class LLRule { FIRST, FOLLOW }

data class LLEntry(val production: Production, val rule: LLRule)

/** Tabela preditiva LL(1): M[A, a] = produções a usar com A no topo e a na entrada. */
class LL1Table(val analysis: Analysis) {
    val grammar: Grammar = analysis.grammar
    val columns: List<String> = grammar.terminals + END
    val cells: Map<Pair<String, String>, List<LLEntry>>

    init {
        val c = LinkedHashMap<Pair<String, String>, MutableList<LLEntry>>()
        for (p in grammar.productions) {
            val f = analysis.firstOf(p.rhs)
            for (t in f) {
                if (t == EPS) continue
                c.getOrPut(p.lhs to t) { mutableListOf() }.add(LLEntry(p, LLRule.FIRST))
            }
            if (EPS in f) {
                for (t in analysis.follow.getValue(p.lhs)) {
                    val list = c.getOrPut(p.lhs to t) { mutableListOf() }
                    if (list.none { it.production == p }) list.add(LLEntry(p, LLRule.FOLLOW))
                }
            }
        }
        cells = c
    }

    fun entries(nt: String, t: String): List<LLEntry> = cells[nt to t].orEmpty()
    fun productions(nt: String, t: String): List<Production> = entries(nt, t).map { it.production }

    val conflicts: List<Pair<String, String>> get() = cells.filterValues { it.size > 1 }.keys.toList()
    val isLL1: Boolean get() = conflicts.isEmpty()

    /** Explicação curta de por que a produção [p] está em M[nt, t] (ou não está). */
    fun explainCell(nt: String, t: String, p: Production): String {
        val entry = entries(nt, t).firstOrNull { it.production == p }
        val f = analysis.firstOf(p.rhs)
        return when (entry?.rule) {
            LLRule.FIRST -> "M[$nt, $t] contém ${p.text} porque '$t' ∈ FIRST(${p.rhsText}) = ${grammar.setText(f)}."
            LLRule.FOLLOW -> "M[$nt, $t] contém ${p.text} porque ${p.rhsText} pode virar ε e '$t' ∈ FOLLOW($nt) = ${analysis.followText(nt)}."
            null -> if (EPS in f) {
                "${p.text} só entra nas colunas de FIRST(${p.rhsText}) − ε = ${grammar.setText(f - EPS)} e de FOLLOW($nt) = ${analysis.followText(nt)}; '$t' não está em nenhum."
            } else {
                "${p.text} só entra nas colunas de FIRST(${p.rhsText}) = ${grammar.setText(f)}; '$t' não está lá."
            }
        }
    }

    fun conflictDescription(nt: String, t: String): String {
        val ps = productions(nt, t)
        return "Conflito em M[$nt, $t]: ${ps.joinToString(" e ") { "(${it.id}) ${it.text}" }} disputam a mesma célula."
    }
}

enum class ParseStatus { RUNNING, ACCEPTED, REJECTED }

sealed interface LLMove {
    data class Expand(val production: Production) : LLMove
    data object Match : LLMove
    data object Accept : LLMove
    data object Error : LLMove
}

/**
 * Configuração do parser preditivo. A pilha guarda ids de nós da árvore;
 * o topo é o último elemento e o fundo (-1) é o $.
 */
data class LLState(
    val stack: List<Int>,
    val tokens: List<String>,
    val pos: Int,
    val tree: ParseTree,
    val rootId: Int,
    val status: ParseStatus = ParseStatus.RUNNING,
    val steps: Int = 0,
) {
    val lookahead: String get() = tokens[pos]
    fun symbolOf(id: Int): String = if (id < 0) END else tree.symbol(id)
    val top: String get() = symbolOf(stack.last())

    /** Pilha lida do topo para o fundo (o topo à esquerda). */
    val stackTopFirst: List<String> get() = stack.asReversed().map { symbolOf(it) }

    /** Forma sentencial atual: entrada já casada + pilha (sem o $). */
    val sententialForm: List<String>
        get() = tokens.subList(0, pos) + stack.asReversed().filter { it >= 0 }.map { symbolOf(it) }
}

class LLParser(val table: LL1Table) {
    val grammar: Grammar get() = table.grammar

    fun initial(tokens: List<String>): LLState {
        val (tree, root) = ParseTree().add(grammar.start)
        return LLState(listOf(-1, root), tokens + END, 0, tree, root)
    }

    fun validMoves(s: LLState): List<LLMove> {
        val top = s.top
        val la = s.lookahead
        return when {
            top == END -> listOf(if (la == END) LLMove.Accept else LLMove.Error)
            !grammar.isNonterminal(top) -> listOf(if (top == la) LLMove.Match else LLMove.Error)
            else -> {
                val ps = table.productions(top, la)
                if (ps.isEmpty()) listOf(LLMove.Error) else ps.map { LLMove.Expand(it) }
            }
        }
    }

    fun isValid(s: LLState, move: LLMove) = move in validMoves(s)

    fun apply(s: LLState, move: LLMove): LLState = when (move) {
        is LLMove.Expand -> {
            val p = move.production
            val nodeId = s.stack.last()
            require(s.symbolOf(nodeId) == p.lhs) { "topo não é ${p.lhs}" }
            val (t1, kids) = s.tree.addAll(if (p.isEpsilon) listOf(EPS) else p.rhs)
            val t2 = t1.withChildren(nodeId, kids)
            val pushed = if (p.isEpsilon) emptyList() else kids.asReversed()
            s.copy(stack = s.stack.dropLast(1) + pushed, tree = t2, steps = s.steps + 1)
        }
        LLMove.Match -> s.copy(stack = s.stack.dropLast(1), pos = s.pos + 1, steps = s.steps + 1)
        LLMove.Accept -> s.copy(status = ParseStatus.ACCEPTED, steps = s.steps + 1)
        LLMove.Error -> s.copy(status = ParseStatus.REJECTED, steps = s.steps + 1)
    }

    /** Roda o parser automaticamente (primeira opção em caso de conflito). */
    fun run(tokens: List<String>, maxSteps: Int = 10_000): List<LLState> {
        val out = mutableListOf(initial(tokens))
        while (out.last().status == ParseStatus.RUNNING && out.size < maxSteps) {
            out += apply(out.last(), validMoves(out.last()).first())
        }
        return out
    }

    /** Explica o movimento correto na configuração [s]. */
    fun explain(s: LLState): String {
        val top = s.top
        val la = s.lookahead
        return when {
            top == END && la == END -> "Pilha e entrada chegaram ao $: a cadeia foi reconhecida. Aceitar!"
            top == END -> "A pilha só tem $, mas ainda sobra '$la' na entrada: erro."
            !grammar.isNonterminal(top) && top == la -> "O topo é o terminal '$top', igual ao próximo da entrada: casar (match) e avançar."
            !grammar.isNonterminal(top) -> "O topo é o terminal '$top', mas a entrada tem '$la': não há como casar. Erro!"
            else -> {
                val ps = table.productions(top, la)
                when (ps.size) {
                    0 -> "M[$top, $la] está vazia: nenhuma produção de $top pode começar com '$la' (nem sumir antes dele). Erro!"
                    1 -> table.explainCell(top, la, ps[0]) + " Então expanda ${ps[0].text}."
                    else -> table.conflictDescription(top, la)
                }
            }
        }
    }
}
