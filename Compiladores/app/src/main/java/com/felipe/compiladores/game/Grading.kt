package com.felipe.compiladores.game

import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.FirstReason
import com.felipe.compiladores.engine.FollowReason
import com.felipe.compiladores.engine.Item
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LLRule
import com.felipe.compiladores.engine.LRTable

/**
 * Classifica os erros do jogador em categorias ([MistakeType]).
 * É isso que alimenta o "Diário de erros": saber QUE TIPO de erro você comete é metacognição.
 */
object Grading {

    fun firstMistakes(an: Analysis, nt: String, player: Set<String>): List<MistakeType> {
        val truth = an.first.getValue(nt)
        val out = mutableListOf<MistakeType>()
        for (t in truth - player) {
            out += if (t == EPS) MistakeType.FIRST_EPS_MISSING else when (val r = an.firstReason[nt to t]) {
                is FirstReason.FromProduction -> when {
                    r.index > 0 -> MistakeType.FIRST_MISSED_NULLABLE
                    an.grammar.isNonterminal(r.production.rhs[0]) -> MistakeType.FIRST_MISSED_CHAIN
                    else -> MistakeType.FIRST_MISSED_DIRECT
                }
                else -> MistakeType.FIRST_MISSED_DIRECT
            }
        }
        for (t in player - truth) {
            out += when {
                t == EPS -> MistakeType.FIRST_EPS_EXTRA
                looksBeyond(an, nt, t) -> MistakeType.FIRST_BEYOND
                t in an.follow.getValue(nt) -> MistakeType.FIRST_CONFUSED_FOLLOW
                else -> MistakeType.FIRST_EXTRA
            }
        }
        return out
    }

    /** O terminal aparece no FIRST de um símbolo que vem DEPOIS de um símbolo não anulável. */
    private fun looksBeyond(an: Analysis, nt: String, t: String): Boolean =
        an.grammar.productionsOf(nt).any { p ->
            p.rhs.indices.any { j -> j > 0 && !an.isNullable(p.rhs.subList(0, j)) && t in an.firstOfSymbol(p.rhs[j]) }
        }

    fun followMistakes(an: Analysis, nt: String, player: Set<String>): List<MistakeType> {
        val truth = an.follow.getValue(nt)
        val out = mutableListOf<MistakeType>()
        for (t in truth - player) {
            out += when (an.followReason[nt to t]) {
                FollowReason.StartSymbol -> MistakeType.FOLLOW_MISSED_END
                is FollowReason.FirstOfRest -> MistakeType.FOLLOW_MISSED_FIRST
                is FollowReason.FollowOfLhs -> MistakeType.FOLLOW_MISSED_INHERIT
                null -> MistakeType.FOLLOW_MISSED_FIRST
            }
        }
        for (t in player - truth) out += if (t == EPS) MistakeType.FOLLOW_EPS else MistakeType.FOLLOW_EXTRA
        return out
    }

    /** [player]: (A, a) → ids das produções colocadas pelo jogador. */
    fun ll1Mistakes(table: LL1Table, player: Map<Pair<String, String>, Set<Int>>): List<MistakeType> {
        val out = mutableListOf<MistakeType>()
        val keys = table.cells.keys + player.keys
        for (k in keys) {
            val truth = table.entries(k.first, k.second)
            val mine = player[k].orEmpty()
            for (e in truth) if (e.production.id !in mine) {
                out += if (e.rule == LLRule.FIRST) MistakeType.TABLE_MISSED_FIRST else MistakeType.TABLE_MISSED_FOLLOW
            }
            val ids = truth.map { it.production.id }.toSet()
            for (id in mine) if (id !in ids) out += MistakeType.TABLE_EXTRA
        }
        return out
    }

    /** Texto de uma entrada da tabela LR: "s3", "r2", "acc" (ACTION) ou "5" (GOTO). */
    fun lrEntries(table: LRTable, state: Int, column: String): Set<String> =
        if (column in table.gotoColumns) setOfNotNull(table.goto[state to column]?.toString())
        else table.actions(state, column).map { it.toString() }.toSet()

    fun slrMistakes(table: LRTable, player: Map<Pair<Int, String>, Set<String>>): List<MistakeType> {
        val out = mutableListOf<MistakeType>()
        for (s in 0 until table.stateCount) for (c in table.actionColumns + table.gotoColumns) {
            val truth = lrEntries(table, s, c)
            val mine = player[s to c].orEmpty()
            for (e in truth - mine) out += if (e.startsWith("r")) MistakeType.SLR_REDUCE_COLUMNS else MistakeType.SLR_MISSED_SHIFT
            for (e in mine - truth) out += if (e.startsWith("r")) MistakeType.SLR_REDUCE_COLUMNS else MistakeType.SLR_EXTRA
        }
        return out
    }

    fun closureMistakes(expected: Set<Item>, player: Set<Item>, isGoto: Boolean): List<MistakeType> {
        val out = mutableListOf<MistakeType>()
        val missing = expected - player
        val extra = player - expected
        if (missing.any { it.dot > 0 } && isGoto) out += MistakeType.GOTO_WRONG
        if (missing.any { it.dot == 0 }) out += MistakeType.CLOSURE_MISSING
        if (extra.isNotEmpty()) out += if (isGoto && extra.any { it.dot > 0 }) MistakeType.GOTO_WRONG else MistakeType.CLOSURE_EXTRA
        return out
    }

    /** Número de células diferentes entre duas tabelas de conjuntos. */
    fun <K, V> diffCount(truth: Map<K, Set<V>>, player: Map<K, Set<V>>): Int =
        (truth.keys + player.keys).count { truth[it].orEmpty() != player[it].orEmpty() }
}
