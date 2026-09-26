package com.felipe.compiladores.engine

object Transform {

    /**
     * Não-terminais recursivos à esquerda (diretos ou indiretos): A ⇒+ A α.
     * Considera símbolos anuláveis antes do não-terminal.
     */
    fun leftRecursive(g: Grammar): Set<String> {
        val nullable = Analysis(g).nullable
        val edges = g.nonterminals.associateWith { HashSet<String>() }
        for (p in g.productions) {
            for (x in p.rhs) {
                if (g.isNonterminal(x)) edges.getValue(p.lhs).add(x)
                if (x !in nullable) break
            }
        }
        return g.nonterminals.filter { a ->
            val seen = HashSet<String>()
            val stack = ArrayDeque(edges.getValue(a))
            var found = false
            while (stack.isNotEmpty() && !found) {
                val b = stack.removeLast()
                if (b == a) found = true
                else if (seen.add(b)) stack.addAll(edges.getValue(b))
            }
            found
        }.toSet()
    }

    fun directLeftRecursive(g: Grammar): Set<String> =
        g.productions.filter { it.rhs.firstOrNull() == it.lhs }.map { it.lhs }.toSet()

    /** A → A α | β  ⇒  A → β A',  A' → α A' | ε */
    fun eliminateDirectLeftRecursion(g: Grammar, a: String, newName: String = freshName(g, a)): Grammar {
        val ps = g.productionsOf(a)
        val alphas = ps.filter { it.rhs.firstOrNull() == a }.map { it.rhs.drop(1) }
        val betas = ps.filter { it.rhs.firstOrNull() != a }.map { it.rhs }
        if (alphas.isEmpty()) return g
        val rules = mutableListOf<Pair<String, List<String>>>()
        for (nt in g.nonterminals) {
            if (nt == a) {
                betas.forEach { rules += a to (it + newName) }
                alphas.forEach { rules += newName to (it + newName) }
                rules += newName to emptyList()
            } else g.productionsOf(nt).forEach { rules += nt to it.rhs }
        }
        return build(rules, g.start)
    }

    /** Fatora o maior prefixo comum de duas ou mais alternativas de [a]. */
    fun leftFactor(g: Grammar, a: String, newName: String = freshName(g, a)): Grammar {
        val ps = g.productionsOf(a)
        var best: List<String> = emptyList()
        for (i in ps.indices) for (j in i + 1 until ps.size) {
            val pre = ps[i].rhs.zip(ps[j].rhs).takeWhile { it.first == it.second }.map { it.first }
            if (pre.size > best.size) best = pre
        }
        if (best.isEmpty()) return g
        val rules = mutableListOf<Pair<String, List<String>>>()
        for (nt in g.nonterminals) {
            if (nt == a) {
                var placed = false
                for (p in ps) {
                    if (p.rhs.size >= best.size && p.rhs.subList(0, best.size) == best) {
                        if (!placed) { rules += a to (best + newName); placed = true }
                    } else rules += a to p.rhs
                }
                ps.filter { it.rhs.size >= best.size && it.rhs.subList(0, best.size) == best }
                    .forEach { rules += newName to it.rhs.drop(best.size) }
            } else g.productionsOf(nt).forEach { rules += nt to it.rhs }
        }
        return build(rules, g.start)
    }

    fun freshName(g: Grammar, base: String): String {
        var n = "$base'"
        while (g.isNonterminal(n) || n in g.terminals) n += "'"
        return n
    }

    fun build(rules: List<Pair<String, List<String>>>, start: String): Grammar {
        val unique = rules.distinct()
        return Grammar(unique.mapIndexed { i, (l, r) -> Production(i + 1, l, r) }, start)
    }

    /**
     * Todas as cadeias com até [maxLen] terminais geradas pela gramática.
     * Ponto fixo sobre conjuntos finitos, então sempre termina (mesmo com recursão à esquerda).
     */
    fun languageUpTo(g: Grammar, maxLen: Int, limit: Int = 20_000): Set<List<String>> {
        val lang = g.nonterminals.associateWith { HashSet<List<String>>() }
        var changed = true
        while (changed) {
            changed = false
            for (p in g.productions) {
                var acc: Set<List<String>> = setOf(emptyList())
                for (x in p.rhs) {
                    val xs: Set<List<String>> = if (g.isNonterminal(x)) lang.getValue(x) else setOf(listOf(x))
                    val next = HashSet<List<String>>()
                    for (u in acc) for (v in xs) if (u.size + v.size <= maxLen) next += (u + v)
                    acc = next
                    if (acc.isEmpty() || acc.size > limit) break
                }
                if (lang.getValue(p.lhs).addAll(acc)) changed = true
            }
        }
        return lang.getValue(g.start)
    }

    /** Compara linguagens até [maxLen]: (cadeia só em g1, cadeia só em g2), menores primeiro. */
    fun compareLanguages(g1: Grammar, g2: Grammar, maxLen: Int): Pair<List<String>?, List<String>?> {
        val l1 = languageUpTo(g1, maxLen)
        val l2 = languageUpTo(g2, maxLen)
        val only1 = (l1 - l2).minWithOrNull(compareBy<List<String>> { it.size }.thenBy { it.joinToString(" ") })
        val only2 = (l2 - l1).minWithOrNull(compareBy<List<String>> { it.size }.thenBy { it.joinToString(" ") })
        return only1 to only2
    }

    /** Não-terminais que não geram nenhuma cadeia de terminais. */
    fun unproductive(g: Grammar): Set<String> {
        val m = Derivation.minimalLengths(g)
        return m.filterValues { it >= Int.MAX_VALUE / 4 }.keys
    }

    /** Não-terminais inalcançáveis a partir do símbolo inicial. */
    fun unreachable(g: Grammar): Set<String> {
        val seen = hashSetOf(g.start)
        val stack = ArrayDeque(listOf(g.start))
        while (stack.isNotEmpty()) {
            val a = stack.removeLast()
            for (p in g.productionsOf(a)) for (x in p.rhs) if (g.isNonterminal(x) && seen.add(x)) stack.addLast(x)
        }
        return g.nonterminals.toSet() - seen
    }
}
