package com.felipe.compiladores.engine

enum class DerivationMode(val label: String) {
    LEFTMOST("mais à esquerda"),
    RIGHTMOST("mais à direita"),
    FREE("livre"),
}

data class DerivStep(val position: Int, val production: Production)

/** Forma sentencial (como ids de nós da árvore) e o histórico de passos. */
data class DerivationState(
    val form: List<Int>,
    val tree: ParseTree,
    val root: Int,
    val steps: List<DerivStep> = emptyList(),
) {
    val symbols: List<String> get() = form.map { tree.symbol(it) }
}

class Derivation(val grammar: Grammar, val target: List<String>) {
    private val minLen: Map<String, Int> = minimalLengths(grammar)

    fun initial(): DerivationState {
        val (t, root) = ParseTree().add(grammar.start)
        return DerivationState(listOf(root), t, root)
    }

    /** Posições de não-terminais que podem ser expandidas no modo dado. */
    fun allowedPositions(s: DerivationState, mode: DerivationMode): List<Int> {
        val nts = s.symbols.withIndex().filter { grammar.isNonterminal(it.value) }.map { it.index }
        return when (mode) {
            DerivationMode.LEFTMOST -> nts.take(1)
            DerivationMode.RIGHTMOST -> nts.takeLast(1)
            DerivationMode.FREE -> nts
        }
    }

    fun apply(s: DerivationState, position: Int, p: Production): DerivationState {
        val nodeId = s.form[position]
        require(s.tree.symbol(nodeId) == p.lhs)
        val (t1, kids) = s.tree.addAll(if (p.isEpsilon) listOf(EPS) else p.rhs)
        val t2 = t1.withChildren(nodeId, kids)
        val newForm = s.form.subList(0, position) + (if (p.isEpsilon) emptyList() else kids) +
            s.form.subList(position + 1, s.form.size)
        return s.copy(form = newForm, tree = t2, steps = s.steps + DerivStep(position, p))
    }

    fun isDone(s: DerivationState) = s.symbols == target

    /**
     * Se a forma sentencial não pode mais gerar o alvo, devolve uma explicação.
     * Usa verificações simples (com explicação clara) e, por fim, um reconhecedor Earley exato.
     */
    fun deadEndReason(s: DerivationState): String? {
        val sym = s.symbols
        if (sym == target) return null
        val terminals = sym.filter { !grammar.isNonterminal(it) }
        val targetText = target.joinToString(" ")
        if (terminals.size > target.size) {
            return "Já existem ${terminals.size} terminais na forma, mas o alvo \"$targetText\" só tem ${target.size}. Terminais nunca somem."
        }
        val least = sym.sumOf { minLen[it] ?: 1 }
        if (least > target.size) {
            return "Mesmo escolhendo as produções mais curtas, esta forma gera pelo menos $least símbolos; o alvo tem ${target.size}."
        }
        val prefix = sym.takeWhile { !grammar.isNonterminal(it) }
        if (prefix.size > target.size || target.subList(0, prefix.size) != prefix) {
            return "A forma começa com \"${prefix.joinToString(" ")}\", mas o alvo começa com \"${target.take(prefix.size).joinToString(" ")}\". Esse prefixo já está fixo."
        }
        val suffix = sym.asReversed().takeWhile { !grammar.isNonterminal(it) }.asReversed()
        if (suffix.size > target.size || target.subList(target.size - suffix.size, target.size) != suffix) {
            return "A forma termina com \"${suffix.joinToString(" ")}\", que não é o final do alvo."
        }
        if (sym.none { grammar.isNonterminal(it) }) return "Não há mais não-terminais e a cadeia não é o alvo."
        if (!Earley(grammar).derives(sym, target)) {
            return "Não existe sequência de passos que leve desta forma até \"$targetText\"."
        }
        return null
    }

    companion object {
        /** Menor número de terminais que cada não-terminal pode gerar (Int.MAX_VALUE se nenhum). */
        fun minimalLengths(g: Grammar): Map<String, Int> {
            val inf = Int.MAX_VALUE / 4
            val m = g.nonterminals.associateWith { inf }.toMutableMap()
            var changed = true
            while (changed) {
                changed = false
                for (p in g.productions) {
                    val v = p.rhs.sumOf { if (g.isNonterminal(it)) m.getValue(it) else 1 }.coerceAtMost(inf)
                    if (v < m.getValue(p.lhs)) { m[p.lhs] = v; changed = true }
                }
            }
            return m
        }
    }
}

/** Reconhecedor de Earley: decide se uma forma sentencial deriva uma cadeia de terminais. */
class Earley(private val grammar: Grammar) {
    private val nullable = Analysis(grammar).nullable

    private data class EItem(val prod: Int, val dot: Int, val origin: Int)

    fun derives(form: List<String>, target: List<String>): Boolean {
        // Produção artificial id = -1: START → form
        fun rhs(prod: Int) = if (prod == -1) form else grammar.production(prod).rhs
        fun lhs(prod: Int) = if (prod == -1) "<start>" else grammar.production(prod).lhs
        val n = target.size
        val chart = Array(n + 1) { mutableListOf<EItem>() }
        val seen = Array(n + 1) { HashSet<EItem>() }
        fun add(i: Int, it: EItem) { if (seen[i].add(it)) chart[i].add(it) }
        add(0, EItem(-1, 0, 0))
        for (i in 0..n) {
            var k = 0
            while (k < chart[i].size) {
                val it = chart[i][k]
                val r = rhs(it.prod)
                if (it.dot < r.size) {
                    val x = r[it.dot]
                    if (grammar.isNonterminal(x)) {
                        for (p in grammar.productionsOf(x)) add(i, EItem(p.id, 0, i))
                        if (x in nullable) add(i, it.copy(dot = it.dot + 1))
                    } else if (i < n && target[i] == x) {
                        add(i + 1, it.copy(dot = it.dot + 1))
                    }
                } else {
                    val a = lhs(it.prod)
                    // Iterar por índice: a lista pode crescer quando origin == i.
                    var j = 0
                    while (j < chart[it.origin].size) {
                        val w = chart[it.origin][j]
                        val wr = rhs(w.prod)
                        if (w.dot < wr.size && wr[w.dot] == a) add(i, w.copy(dot = w.dot + 1))
                        j++
                    }
                }
                k++
            }
        }
        return EItem(-1, form.size, 0) in seen[n]
    }
}
