package com.felipe.compiladores.engine

/** Símbolo usado para a cadeia vazia. */
const val EPS = "ε"

/** Marcador de fim de entrada. */
const val END = "$"

/**
 * Uma produção `lhs → rhs`. Um [rhs] vazio representa `lhs → ε`.
 * O [id] começa em 1 nas gramáticas do usuário; a produção aumentada `S' → S` recebe id 0.
 */
data class Production(val id: Int, val lhs: String, val rhs: List<String>) {
    val isEpsilon: Boolean get() = rhs.isEmpty()
    val rhsText: String get() = if (rhs.isEmpty()) EPS else rhs.joinToString(" ")
    val text: String get() = "$lhs → $rhsText"
    override fun toString() = text
}

class Grammar(productions: List<Production>, start: String? = null) {
    val productions: List<Production> = productions
    val start: String = start ?: productions.first().lhs

    /** Não-terminais na ordem em que aparecem como lado esquerdo. */
    val nonterminals: List<String> = productions.map { it.lhs }.distinct()

    private val ntSet = nonterminals.toSet()

    /** Terminais na ordem em que aparecem nos lados direitos. */
    val terminals: List<String> = productions.flatMap { it.rhs }.filter { it !in ntSet }.distinct()

    private val byLhs: Map<String, List<Production>> = productions.groupBy { it.lhs }

    fun isNonterminal(s: String) = s in ntSet
    fun isTerminal(s: String) = s !in ntSet && s != EPS && s != END

    fun productionsOf(nt: String): List<Production> = byLhs[nt].orEmpty()

    fun production(id: Int): Production = productions.first { it.id == id }

    /** Todos os símbolos: não-terminais seguidos de terminais. */
    val symbols: List<String> get() = nonterminals + terminals

    /** Gramática aumentada com `S' → S` como produção 0. */
    fun augmented(): Grammar {
        var name = "$start'"
        while (name in ntSet) name += "'"
        return Grammar(listOf(Production(0, name, listOf(start))) + productions, name)
    }

    /** Ordena um conjunto de símbolos terminais na ordem da gramática, com ε e $ no fim. */
    fun sortTerminals(set: Collection<String>): List<String> {
        val order = terminals + END + EPS
        return set.sortedBy { s -> order.indexOf(s).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    fun setText(set: Collection<String>): String =
        if (set.isEmpty()) "∅" else sortTerminals(set).joinToString(", ", "{ ", " }")

    /** Texto no formato aceito por [parse]. */
    fun toText(): String = nonterminals.joinToString("\n") { nt ->
        "$nt -> " + productionsOf(nt).joinToString(" | ") { it.rhsText }
    }

    /** Produções agrupadas por lado esquerdo, uma linha por não-terminal. */
    fun groupedLines(): List<Pair<String, List<Production>>> = nonterminals.map { it to productionsOf(it) }

    override fun toString() = toText()

    companion object {
        private val EPS_TOKENS = setOf("ε", "eps", "epsilon", "λ", "''", "\"\"")
        private val ARROWS = listOf("::=", "->", "→")

        /** Lê uma gramática; lança [GrammarParseException] com mensagem em português se houver erro. */
        fun parse(text: String, charMode: Boolean = false): Grammar {
            val result = tryParse(text, charMode)
            return result.grammar ?: throw GrammarParseException(result.errors.joinToString("\n"))
        }

        fun tryParse(text: String, charMode: Boolean = false): ParseResult {
            val errors = mutableListOf<String>()
            val rawRules = mutableListOf<Pair<String, String>>() // lhs, alternatives text
            var lastLhs: String? = null
            text.lines().forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachIndexed
                if (line.startsWith("|")) {
                    val lhs = lastLhs
                    if (lhs == null) errors += "Linha ${index + 1}: '|' sem um não-terminal antes."
                    else rawRules += lhs to line.substring(1)
                    return@forEachIndexed
                }
                val arrow = ARROWS.map { it to line.indexOf(it) }.filter { it.second >= 0 }.minByOrNull { it.second }
                if (arrow == null) {
                    errors += "Linha ${index + 1}: faltou a seta '->' em \"$line\"."
                    return@forEachIndexed
                }
                val lhs = line.substring(0, arrow.second).trim()
                if (lhs.isEmpty() || lhs.contains(' ')) {
                    errors += "Linha ${index + 1}: o lado esquerdo deve ser um único não-terminal."
                    return@forEachIndexed
                }
                lastLhs = lhs
                rawRules += lhs to line.substring(arrow.second + arrow.first.length)
            }
            if (rawRules.isEmpty() && errors.isEmpty()) errors += "A gramática está vazia."
            if (errors.isNotEmpty()) return ParseResult(null, errors, emptyList())

            val lhsNames = rawRules.map { it.first }.distinct()
            val productions = mutableListOf<Production>()
            for ((lhs, altsText) in rawRules) {
                for (alt in altsText.split("|")) {
                    val tokens = tokenize(alt, charMode, lhsNames).filter { it !in EPS_TOKENS }
                    productions += Production(productions.size + 1, lhs, tokens)
                }
            }
            val warnings = mutableListOf<String>()
            val used = productions.flatMap { it.rhs }.toSet()
            for (s in used) {
                if (s !in lhsNames && s.first().isUpperCase() && s.length <= 2) {
                    warnings += "'$s' não tem produções e será tratado como terminal."
                }
            }
            if (END in used) return ParseResult(null, listOf("'$' é reservado para o fim da entrada."), emptyList())
            // Evita produções duplicadas (mesma regra escrita duas vezes).
            val unique = productions.distinctBy { it.lhs to it.rhs }.mapIndexed { i, p -> p.copy(id = i + 1) }
            return ParseResult(Grammar(unique), emptyList(), warnings)
        }

        private fun tokenize(alt: String, charMode: Boolean, lhsNames: List<String>): List<String> {
            if (!charMode) return alt.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val names = lhsNames.sortedByDescending { it.length }
            val out = mutableListOf<String>()
            var i = 0
            val s = alt.trim()
            while (i < s.length) {
                if (s[i].isWhitespace()) { i++; continue }
                if (s.startsWith("eps", i)) { out += EPS; i += 3; continue }
                val name = names.firstOrNull { s.startsWith(it, i) }
                if (name != null) { out += name; i += name.length; continue }
                var tok = s[i].toString()
                i++
                while (i < s.length && s[i] == '\'') { tok += "'"; i++ }
                out += tok
            }
            return out
        }
    }
}

class ParseResult(val grammar: Grammar?, val errors: List<String>, val warnings: List<String>)

class GrammarParseException(message: String) : Exception(message)

/** Divide uma cadeia de entrada em tokens (separados por espaço). */
fun tokenizeInput(text: String, grammar: Grammar? = null): List<String> {
    val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (grammar == null || parts.size != 1) return parts
    // Permite escrever "aabb" quando todos os terminais têm um caractere.
    val single = parts[0]
    if (single in grammar.terminals) return parts
    return if (grammar.terminals.all { it.length == 1 } && single.all { it.toString() in grammar.terminals }) {
        single.map { it.toString() }
    } else parts
}
