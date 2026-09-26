package com.felipe.compiladores.game

import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.Transform

/** Um item do relatório da cirurgia: passou ou não, com explicação. */
data class CheckLine(val label: String, val ok: Boolean, val detail: String, val mistake: MistakeType?)

data class SurgeryReport(val lines: List<CheckLine>) {
    val ok: Boolean get() = lines.all { it.ok }
}

object SurgeryCheck {

    /** Não-terminais com duas alternativas começando pelo mesmo símbolo. */
    fun commonPrefixes(g: Grammar): List<String> = g.nonterminals.filter { nt ->
        val firsts = g.productionsOf(nt).mapNotNull { it.rhs.firstOrNull() }
        firsts.size != firsts.toSet().size
    }

    fun check(original: Grammar, candidate: Grammar, spec: LevelSpec.Surgery): SurgeryReport {
        val lines = mutableListOf<CheckLine>()
        if (!candidate.isNonterminal(original.start)) {
            return SurgeryReport(listOf(CheckLine("Símbolo inicial", false, "${original.start} precisa ter pelo menos uma produção.", null)))
        }

        val unused = spec.newSymbols.filter { !candidate.isNonterminal(it) }
        val undefined = candidate.productions.flatMap { it.rhs }.filter { it in spec.newSymbols && !candidate.isNonterminal(it) }.distinct()
        if (undefined.isNotEmpty()) {
            lines += CheckLine(
                "Símbolos definidos", false,
                "Você usou ${undefined.joinToString()} mas não deu produções para ${if (undefined.size > 1) "eles" else "ele"}.",
                MistakeType.SURGERY_LANGUAGE,
            )
        }

        val lr = Transform.leftRecursive(candidate)
        lines += if (lr.isEmpty()) CheckLine("Sem recursão à esquerda", true, "Nenhum não-terminal deriva a si mesmo à esquerda.", null)
        else CheckLine(
            "Sem recursão à esquerda", false,
            "Ainda recursivo à esquerda: ${lr.joinToString()}. " + lr.first().let { a ->
                candidate.productionsOf(a).firstOrNull { it.rhs.firstOrNull() == a }?.let { "Veja ${it.text}." } ?: "A recursão é indireta."
            },
            MistakeType.SURGERY_LEFT_REC,
        )

        val (onlyOriginal, onlyMine) = Transform.compareLanguages(original, candidate, spec.maxLen)
        fun show(s: List<String>) = if (s.isEmpty()) "ε (cadeia vazia)" else "\"${s.joinToString(" ")}\""
        lines += when {
            onlyOriginal == null && onlyMine == null -> CheckLine(
                "Mesma linguagem", true,
                "As duas gramáticas geram exatamente as mesmas cadeias de até ${spec.maxLen} tokens.", null,
            )
            onlyMine != null -> CheckLine("Mesma linguagem", false, "Sua gramática gera ${show(onlyMine)}, mas a original não.", MistakeType.SURGERY_LANGUAGE)
            else -> CheckLine("Mesma linguagem", false, "A original gera ${show(onlyOriginal!!)}, mas a sua não.", MistakeType.SURGERY_LANGUAGE)
        }

        if (spec.requireNoCommonPrefix) {
            val cp = commonPrefixes(candidate)
            lines += if (cp.isEmpty()) CheckLine("Sem prefixo comum", true, "Nenhuma alternativa começa igual a outra do mesmo não-terminal.", null)
            else CheckLine("Sem prefixo comum", false, "Alternativas de ${cp.joinToString()} ainda começam com o mesmo símbolo.", MistakeType.SURGERY_PREFIX)
        }

        if (spec.requireLL1) {
            val t = LL1Table(Analysis(candidate))
            lines += if (t.isLL1) CheckLine("É LL(1)", true, "Nenhuma célula da tabela tem conflito.", null)
            else {
                val (a, b) = t.conflicts.first()
                CheckLine("É LL(1)", false, t.conflictDescription(a, b), MistakeType.SURGERY_NOT_LL1)
            }
        }
        if (unused.isNotEmpty() && lines.all { it.ok }) {
            // Resolver sem os símbolos sugeridos também vale — só avisamos.
            lines += CheckLine("Observação", true, "Você resolveu sem usar ${unused.joinToString()}. Criativo!", null)
        }
        return SurgeryReport(lines)
    }
}
