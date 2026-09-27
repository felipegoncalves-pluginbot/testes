package com.felipe.compiladores.game

import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.Derivation
import com.felipe.compiladores.engine.DerivationMode
import com.felipe.compiladores.engine.DerivationState
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.Earley
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.GrammarClasses
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LLRule
import com.felipe.compiladores.engine.Transform
import com.felipe.compiladores.engine.tokenizeInput
import kotlin.random.Random

// ======================================================================== Fábrica de Gramáticas

data class TestResult(val input: String, val expected: Boolean, val got: Boolean) {
    val ok: Boolean get() = expected == got
}

data class DesignReport(val tests: List<TestResult>, val extra: List<CheckLine>, val productions: Int) {
    val ok: Boolean get() = tests.all { it.ok } && extra.all { it.ok }
}

fun showString(tokens: List<String>) = if (tokens.isEmpty()) "ε (vazia)" else tokens.joinToString(" ")

object DesignCheck {

    fun accepts(g: Grammar?, input: String): Boolean {
        if (g == null || !g.isNonterminal(g.start)) return false
        return Earley(g).derives(listOf(g.start), tokenizeInput(input))
    }

    /** Roda os testes visíveis e, se passarem, os ocultos (comparação com a gramática de referência). */
    fun check(candidate: Grammar?, spec: LevelSpec.Design): DesignReport {
        val tests = spec.accept.map { TestResult(it, true, accepts(candidate, it)) } +
            spec.reject.map { TestResult(it, false, accepts(candidate, it)) }
        val extra = mutableListOf<CheckLine>()
        if (candidate != null && candidate.isNonterminal(candidate.start) && tests.all { it.ok }) {
            val reference = Grammar.parse(spec.reference)
            val (onlyRef, onlyMine) = Transform.compareLanguages(reference, candidate, spec.maxLen)
            extra += when {
                onlyMine != null -> CheckLine("Testes ocultos", false, "Sua fábrica aceita \"${showString(onlyMine)}\", que não pertence à linguagem.", MistakeType.DESIGN_TEST)
                onlyRef != null -> CheckLine("Testes ocultos", false, "Sua fábrica rejeita \"${showString(onlyRef)}\", que pertence à linguagem.", MistakeType.DESIGN_TEST)
                else -> CheckLine("Testes ocultos", true, "Todas as cadeias de até ${spec.maxLen} tokens batem com a especificação.", null)
            }
            if (spec.requireLL1) {
                val t = LL1Table(Analysis(candidate))
                extra += if (t.isLL1) CheckLine("É LL(1)", true, "Nenhum conflito na tabela.", null)
                else t.conflicts.first().let { CheckLine("É LL(1)", false, t.conflictDescription(it.first, it.second), MistakeType.SURGERY_NOT_LL1) }
            }
            if (spec.requireLR1) {
                val c = GrammarClasses(candidate)
                extra += if (c.isLR1) CheckLine("Sem ambiguidade (LR(1))", true, "A tabela LR(1) não tem conflitos.", null)
                else CheckLine(
                    "Sem ambiguidade (LR(1))", false,
                    "A tabela LR(1) tem conflito — sinal de ambiguidade. " + c.lr1Table.conflictText(c.lr1Table.conflicts.first()),
                    MistakeType.CLASSIFY_WRONG,
                )
            }
        }
        return DesignReport(tests, extra, candidate?.productions?.size ?: 0)
    }
}

// ======================================================================== Robô Descendente

/** Programa do robô: para cada produção (ramo), os tokens que fazem o robô entrar nele. */
typealias RobotProgram = Map<Int, Set<String>>

enum class FrameKind { CALL, BRANCH, MATCH, RETURN, ERROR, ACCEPT, REJECT }

/** Um "quadro" da execução, para animar: pilha de chamadas, posição na entrada e o que aconteceu. */
data class RobotFrame(val calls: List<String>, val pos: Int, val kind: FrameKind, val text: String, val prodId: Int? = null)

data class RobotRun(val tokens: List<String>, val frames: List<RobotFrame>, val accepted: Boolean) {
    val last: RobotFrame get() = frames.last()
}

object RobotInterpreter {

    /** Programa ideal: cada ramo usa exatamente as colunas da tabela LL(1) onde a produção aparece. */
    fun idealProgram(table: LL1Table): RobotProgram =
        table.grammar.productions.associate { p ->
            p.id to table.cells.filterValues { es -> es.any { it.production == p } }.keys.map { it.second }.toSet()
        }

    fun run(g: Grammar, program: RobotProgram, input: String, maxFrames: Int = 4000): RobotRun {
        val tokens = tokenizeInput(input, g) + END
        val frames = mutableListOf<RobotFrame>()
        var pos = 0
        val calls = mutableListOf<String>()
        var aborted = false

        fun emit(kind: FrameKind, text: String, prod: Int? = null) {
            if (frames.size >= maxFrames) { aborted = true; return }
            frames += RobotFrame(calls.toList(), pos, kind, text, prod)
        }

        fun call(a: String): Boolean {
            if (aborted) return false
            calls += "$a()"
            emit(FrameKind.CALL, "chama $a()")
            val la = tokens[pos]
            val p = g.productionsOf(a).firstOrNull { la in program[it.id].orEmpty() }
            if (p == null) {
                emit(FrameKind.ERROR, "erro() em $a(): nenhum ramo aceita '$la'")
                return false
            }
            emit(FrameKind.BRANCH, "'$la' → ramo ${p.text}", p.id)
            for (x in p.rhs) {
                if (g.isNonterminal(x)) {
                    if (!call(x)) return false
                } else if (tokens[pos] == x) {
                    pos++
                    emit(FrameKind.MATCH, "casa('$x')")
                } else {
                    emit(FrameKind.ERROR, "casa('$x') falhou: veio '${tokens[pos]}'")
                    return false
                }
            }
            calls.removeAt(calls.lastIndex)
            emit(FrameKind.RETURN, if (p.isEpsilon) "$a(): return  # ε" else "$a() terminou")
            return true
        }

        val ok = call(g.start)
        val accepted = ok && !aborted && tokens[pos] == END
        when {
            aborted -> frames += RobotFrame(calls.toList(), pos, FrameKind.REJECT, "O robô entrou em loop!")
            accepted -> emit(FrameKind.ACCEPT, "Aceita! A entrada acabou no $.")
            ok -> emit(FrameKind.REJECT, "Sobrou '${tokens[pos]}' na entrada.")
            else -> emit(FrameKind.REJECT, "Rejeitada.")
        }
        return RobotRun(tokens, frames, accepted)
    }

    /** Explica, em relação ao programa ideal, o que deu errado quando uma frase válida foi rejeitada. */
    fun diagnose(table: LL1Table, run: RobotRun): String? {
        val err = run.frames.lastOrNull { it.kind == FrameKind.ERROR } ?: return null
        val fn = err.calls.lastOrNull()?.removeSuffix("()") ?: return null
        val la = run.tokens[err.pos]
        val right = table.entries(fn, la).firstOrNull()
        return if (right != null) {
            val why = if (right.rule == LLRule.FIRST) "'$la' ∈ FIRST(${right.production.rhsText})"
            else "${right.production.rhsText} pode virar ε e '$la' ∈ FOLLOW($fn)"
            "Com '$la', $fn() deveria entrar no ramo ${right.production.text}: $why."
        } else {
            "Com '$la', nenhum ramo de $fn() serve: o erro está num ramo escolhido ANTES de chegar aqui. Confira quem chamou $fn()."
        }
    }
}

// ======================================================================== Ímãs (code magnets)

data class MagnetPuzzle(val forms: List<List<String>>, val distractors: List<List<String>>) {
    /** Peças embaralhadas (sem a forma inicial, que já vem colada). */
    fun pieces(random: Random): List<List<String>> = (forms.drop(1) + distractors).shuffled(random)
}

object Magnets {

    /** Derivação (à esquerda ou à direita) de [target] por busca com poda exata (Earley). */
    fun derivation(g: Grammar, target: List<String>, mode: DerivationMode): List<DerivationState>? {
        val d = Derivation(g, target)
        fun dfs(s: DerivationState, depth: Int): List<DerivationState>? {
            if (d.isDone(s)) return listOf(s)
            if (depth > 60) return null
            val pos = d.allowedPositions(s, mode).firstOrNull() ?: return null
            for (p in g.productionsOf(s.symbols[pos])) {
                val next = d.apply(s, pos, p)
                if (d.deadEndReason(next) != null && !d.isDone(next)) continue
                dfs(next, depth + 1)?.let { return listOf(s) + it }
            }
            return null
        }
        return dfs(d.initial(), 0)
    }

    /** Até [limit] derivações diferentes (útil para mostrar as duas árvores de uma gramática ambígua). */
    fun derivations(g: Grammar, target: List<String>, mode: DerivationMode, limit: Int): List<DerivationState> {
        val d = Derivation(g, target)
        val found = mutableListOf<DerivationState>()
        fun dfs(s: DerivationState, depth: Int) {
            if (found.size >= limit) return
            if (d.isDone(s)) { found += s; return }
            if (depth > 60) return
            val pos = d.allowedPositions(s, mode).firstOrNull() ?: return
            for (p in g.productionsOf(s.symbols[pos])) {
                val next = d.apply(s, pos, p)
                if (d.deadEndReason(next) == null || d.isDone(next)) dfs(next, depth + 1)
            }
        }
        dfs(d.initial(), 0)
        return found
    }

    fun build(g: Grammar, target: List<String>, mode: DerivationMode, distractors: Int, random: Random): MagnetPuzzle {
        val path = derivation(g, target, mode) ?: error("sem derivação")
        val forms = path.map { it.symbols }
        val d = Derivation(g, target)
        val candidates = LinkedHashSet<List<String>>()
        for (s in path.dropLast(1)) {
            val nts = s.symbols.indices.filter { g.isNonterminal(s.symbols[it]) }
            for (pos in nts) for (p in g.productionsOf(s.symbols[pos])) {
                val f = d.apply(s, pos, p).symbols
                if (f !in forms) candidates += f
            }
        }
        val picked = candidates.toList().shuffled(random).take(distractors)
        return MagnetPuzzle(forms, picked)
    }

    /** `to` sai de `from` em exatamente um passo no modo dado? */
    fun isStep(g: Grammar, from: List<String>, to: List<String>, mode: DerivationMode): Boolean {
        val nts = from.indices.filter { g.isNonterminal(from[it]) }
        val allowed = when (mode) {
            DerivationMode.LEFTMOST -> nts.take(1)
            DerivationMode.RIGHTMOST -> nts.takeLast(1)
            DerivationMode.FREE -> nts
        }
        return allowed.any { pos ->
            g.productionsOf(from[pos]).any { p -> from.subList(0, pos) + p.rhs + from.subList(pos + 1, from.size) == to }
        }
    }

    /** Explica o primeiro erro da sequência montada pelo jogador (null se estiver certa). */
    fun explain(g: Grammar, puzzle: MagnetPuzzle, placed: List<List<String>>, mode: DerivationMode): String? {
        val correct = puzzle.forms.drop(1)
        val which = if (mode == DerivationMode.LEFTMOST) "mais à esquerda" else "mais à direita"
        for (i in placed.indices) {
            val prev = if (i == 0) puzzle.forms.first() else placed[i - 1]
            val cur = placed[i]
            if (i < correct.size && cur == correct[i]) continue
            val prevText = prev.joinToString(" ")
            val curText = cur.joinToString(" ")
            return when {
                cur in puzzle.distractors && isStep(g, prev, cur, DerivationMode.FREE) ->
                    "\"$curText\" é uma armadilha: ela sai de \"$prevText\", mas trocando um não-terminal que NÃO é o $which."
                cur in puzzle.distractors -> "\"$curText\" é uma armadilha: nenhuma derivação $which de \"${showString(puzzle.forms.last())}\" passa por ela."
                !isStep(g, prev, cur, mode) -> "De \"$prevText\" não se chega em \"$curText\" trocando só o não-terminal $which uma vez."
                else -> "A peça ${i + 1} está fora de ordem."
            }
        }
        return if (placed.size < correct.size) "Faltam ${correct.size - placed.size} peça(s) para chegar à frase final." else null
    }
}
