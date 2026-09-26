package com.felipe.compiladores.engine

/** Por que um símbolo entrou em FIRST(A). */
sealed interface FirstReason {
    /** `A → X1 … Xk`, com X1…X(i-1) anuláveis, e o símbolo está em FIRST(Xi). */
    data class FromProduction(val production: Production, val index: Int) : FirstReason

    /** `A → X1 … Xk` com todos os Xi anuláveis (ou `A → ε`). */
    data class Nullable(val production: Production) : FirstReason
}

/** Por que um símbolo entrou em FOLLOW(B). */
sealed interface FollowReason {
    data object StartSymbol : FollowReason

    /** Em `A → α B β`, o símbolo está em FIRST(β). [index] é a posição de B. */
    data class FirstOfRest(val production: Production, val index: Int) : FollowReason

    /** Em `A → α B β` com β ⇒* ε, o símbolo está em FOLLOW(A). */
    data class FollowOfLhs(val production: Production, val index: Int) : FollowReason
}

/** Anuláveis, FIRST e FOLLOW de uma gramática, com a justificativa de cada elemento. */
class Analysis(val grammar: Grammar) {
    val first: Map<String, Set<String>>
    val follow: Map<String, Set<String>>
    val nullable: Set<String>
    val firstReason: Map<Pair<String, String>, FirstReason>
    val followReason: Map<Pair<String, String>, FollowReason>

    /** Quantas rodadas o algoritmo de ponto fixo precisou para FIRST (sem contar a de verificação). */
    val firstRounds: Int

    init {
        val f = grammar.nonterminals.associateWith { LinkedHashSet<String>() }
        val fr = LinkedHashMap<Pair<String, String>, FirstReason>()
        var rounds = 0
        var changed = true
        while (changed) {
            changed = false
            rounds++
            for (p in grammar.productions) {
                val a = p.lhs
                var allNullable = true
                for ((i, x) in p.rhs.withIndex()) {
                    val fx: Set<String> = if (grammar.isNonterminal(x)) f.getValue(x) else setOf(x)
                    for (t in fx.toList()) {
                        if (t == EPS) continue
                        if (f.getValue(a).add(t)) {
                            fr[a to t] = FirstReason.FromProduction(p, i)
                            changed = true
                        }
                    }
                    if (EPS !in fx) { allNullable = false; break }
                }
                if (allNullable && f.getValue(a).add(EPS)) {
                    fr[a to EPS] = FirstReason.Nullable(p)
                    changed = true
                }
            }
        }
        first = f
        firstReason = fr
        firstRounds = rounds - 1
        nullable = grammar.nonterminals.filter { EPS in f.getValue(it) }.toSet()

        val fo = grammar.nonterminals.associateWith { LinkedHashSet<String>() }
        val fol = LinkedHashMap<Pair<String, String>, FollowReason>()
        fo.getValue(grammar.start).add(END)
        fol[grammar.start to END] = FollowReason.StartSymbol
        changed = true
        while (changed) {
            changed = false
            for (p in grammar.productions) {
                for ((i, b) in p.rhs.withIndex()) {
                    if (!grammar.isNonterminal(b)) continue
                    val rest = firstOf(p.rhs.subList(i + 1, p.rhs.size))
                    for (t in rest) {
                        if (t == EPS) continue
                        if (fo.getValue(b).add(t)) {
                            fol[b to t] = FollowReason.FirstOfRest(p, i)
                            changed = true
                        }
                    }
                    if (EPS in rest) {
                        for (t in fo.getValue(p.lhs).toList()) {
                            if (fo.getValue(b).add(t)) {
                                fol[b to t] = FollowReason.FollowOfLhs(p, i)
                                changed = true
                            }
                        }
                    }
                }
            }
        }
        follow = fo
        followReason = fol
    }

    fun firstOfSymbol(x: String): Set<String> =
        if (grammar.isNonterminal(x)) first.getValue(x) else setOf(x)

    /** FIRST de uma sequência de símbolos; contém ε se a sequência inteira for anulável. */
    fun firstOf(seq: List<String>): Set<String> {
        val out = LinkedHashSet<String>()
        for (x in seq) {
            val fx = firstOfSymbol(x)
            out += fx - EPS
            if (EPS !in fx) return out
        }
        out += EPS
        return out
    }

    fun isNullable(seq: List<String>) = seq.all { it in nullable }

    fun firstText(nt: String) = grammar.setText(first.getValue(nt))
    fun followText(nt: String) = grammar.setText(follow.getValue(nt))

    // ---------------------------------------------------------------- explicações

    /** Explica por que [t] ∈ FIRST([nt]). */
    fun explainFirst(nt: String, t: String): String {
        return when (val r = firstReason[nt to t]) {
            null -> explainNotInFirst(nt, t)
            is FirstReason.Nullable ->
                if (r.production.isEpsilon) "ε ∈ FIRST($nt) porque existe a produção ${r.production.text}."
                else "ε ∈ FIRST($nt) porque em ${r.production.text} todos os símbolos podem virar ε."
            is FirstReason.FromProduction -> {
                val p = r.production
                val x = p.rhs[r.index]
                val before = p.rhs.subList(0, r.index)
                when {
                    before.isEmpty() && x == t -> "'$t' ∈ FIRST($nt) porque ${p.text} começa com '$t'."
                    before.isEmpty() -> "'$t' ∈ FIRST($nt) porque ${p.text} começa com $x, e '$t' ∈ FIRST($x)."
                    else -> {
                        val nul = before.joinToString(", ")
                        val tail = if (x == t) "chegamos em '$t'" else "'$t' ∈ FIRST($x)"
                        "'$t' ∈ FIRST($nt): em ${p.text}, $nul pode virar ε, então olhamos o próximo símbolo, e $tail."
                    }
                }
            }
        }
    }

    /** Explica por que [t] NÃO está em FIRST([nt]), listando a contribuição de cada alternativa. */
    fun explainNotInFirst(nt: String, t: String): String {
        val parts = grammar.productionsOf(nt).joinToString("; ") { p ->
            "FIRST(${p.rhsText}) = ${grammar.setText(firstOf(p.rhs))}"
        }
        return if (t == EPS) "ε ∉ FIRST($nt): nenhuma alternativa de $nt consegue sumir por completo. $parts."
        else "'$t' ∉ FIRST($nt): FIRST($nt) é a união das alternativas: $parts."
    }

    /** Explica por que [t] ∈ FOLLOW([nt]). */
    fun explainFollow(nt: String, t: String): String {
        return when (val r = followReason[nt to t]) {
            null -> explainNotInFollow(nt, t)
            FollowReason.StartSymbol -> "$ ∈ FOLLOW($nt) porque $nt é o símbolo inicial: depois dele vem o fim da entrada."
            is FollowReason.FirstOfRest -> {
                val p = r.production
                val rest = p.rhs.subList(r.index + 1, p.rhs.size)
                val direct = rest.first() == t
                if (direct) "'$t' ∈ FOLLOW($nt) porque em ${p.text}, '$t' aparece logo depois de $nt."
                else "'$t' ∈ FOLLOW($nt) porque em ${p.text}, depois de $nt vem ${rest.joinToString(" ")}, e '$t' ∈ FIRST(${rest.joinToString(" ")})."
            }
            is FollowReason.FollowOfLhs -> {
                val p = r.production
                val rest = p.rhs.subList(r.index + 1, p.rhs.size)
                val why = if (rest.isEmpty()) "$nt está no fim" else "o que vem depois de $nt (${rest.joinToString(" ")}) pode virar ε"
                "'$t' ∈ FOLLOW($nt) porque em ${p.text}, $why; então tudo que segue ${p.lhs} também segue $nt, e '$t' ∈ FOLLOW(${p.lhs})."
            }
        }
    }

    fun explainNotInFollow(nt: String, t: String): String {
        if (t == EPS) return "ε nunca pertence a FOLLOW: FOLLOW guarda terminais (e $) que podem aparecer depois de $nt."
        val occ = occurrences(nt)
        if (occ.isEmpty()) {
            return "'$t' ∉ FOLLOW($nt): $nt não aparece em nenhum lado direito" +
                if (nt == grammar.start) ", só o $ o segue." else "."
        }
        val parts = occ.joinToString("; ") { (p, i) ->
            val rest = p.rhs.subList(i + 1, p.rhs.size)
            if (rest.isEmpty()) "em ${p.text}, $nt está no fim → FOLLOW(${p.lhs})"
            else {
                val fr = firstOf(rest)
                val base = "em ${p.text}, depois vem ${grammar.setText(fr - EPS)}"
                if (EPS in fr) "$base e também FOLLOW(${p.lhs})" else base
            }
        }
        return "'$t' ∉ FOLLOW($nt). Veja onde $nt aparece: $parts."
    }

    /** Todas as ocorrências (produção, posição) de [nt] em lados direitos. */
    fun occurrences(nt: String): List<Pair<Production, Int>> =
        grammar.productions.flatMap { p -> p.rhs.withIndex().filter { it.value == nt }.map { p to it.index } }
}
