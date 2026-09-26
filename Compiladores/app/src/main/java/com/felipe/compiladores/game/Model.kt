package com.felipe.compiladores.game

import com.felipe.compiladores.engine.DerivationMode

// ------------------------------------------------------------------ fases e mundos

data class World(
    val id: String,
    val number: Int,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val color: Long,
    val lesson: Lesson,
    val levels: List<Level>,
)

/** "Conceito em 1 minuto": uma mini-aula curta, com exemplo resolvido. */
data class Lesson(val title: String, val points: List<String>, val example: String? = null)

/**
 * Previsão antes de jogar (Prever → Observar → Explicar).
 * O jogador aposta, joga, e no fim compara a previsão com o que aconteceu.
 */
data class Prediction(val question: String, val options: List<String>, val correct: Int, val explanation: String)

data class Level(
    val id: String,
    val title: String,
    val briefing: String,
    val insight: String,
    val hint: String,
    val grammar: String,
    val spec: LevelSpec,
    val prediction: Prediction? = null,
)

sealed interface LevelSpec {
    /** Derivar [target] a partir do símbolo inicial. Em [ambiguity], achar duas árvores diferentes. */
    data class Derive(val target: String, val mode: DerivationMode, val ambiguity: Boolean = false) : LevelSpec

    data object FirstSets : LevelSpec

    data class FollowSets(val showFirst: Boolean = true) : LevelSpec

    data class LL1Table(val askVerdict: Boolean = false) : LevelSpec

    data class LL1Parse(val input: String, val showTable: Boolean = true, val askWhy: Boolean = false) : LevelSpec

    data class Surgery(
        val newSymbols: List<String>,
        val requireLL1: Boolean = true,
        val requireNoCommonPrefix: Boolean = false,
        val maxLen: Int = 6,
    ) : LevelSpec

    data class ShiftReduce(
        val input: String,
        val showStates: Boolean = false,
        val showTable: Boolean = false,
        val showFollow: Boolean = false,
    ) : LevelSpec

    data class Closure(val questions: List<ClosureQuestion>) : LevelSpec

    data object SlrTable : LevelSpec

    data object SlrConflict : LevelSpec

    data object Classify : LevelSpec
}

sealed interface ClosureQuestion {
    /** closure({ S' → • S }) */
    data object Initial : ClosureQuestion

    /** goto(I[state], symbol) */
    data class Goto(val state: Int, val symbol: String) : ClosureQuestion
}

// ------------------------------------------------------------------ metacognição

enum class Confidence(val label: String, val emoji: String, val win: Int, val lose: Int) {
    SURE("Certeza", "🎯", 3, -2),
    THINK("Acho que sim", "🤔", 2, -1),
    GUESS("Chute", "🎲", 1, 0),
}

enum class Feeling(val label: String, val emoji: String) {
    CONFUSED("Ainda confuso", "😵"),
    OK("Entendi", "🙂"),
    MASTERED("Domino isso", "😎"),
}

enum class Skill(val title: String, val emoji: String) {
    FIRST("FIRST", "🧪"),
    FOLLOW("FOLLOW", "🧭"),
    LL_TABLE("Tabela LL(1)", "🧩"),
    LL_STEP("Passo top-down", "🤖"),
    SR_STEP("Passo shift-reduce", "🏗️"),
    CLOSURE("Fechamento LR(0)", "🏭"),
    SLR_ACTION("Ação SLR", "📋"),
}

enum class MistakeType(val title: String, val tip: String) {
    FIRST_MISSED_NULLABLE("Esqueceu o vazamento do ε", "Se um símbolo pode virar ε, o FIRST do símbolo seguinte também entra."),
    FIRST_MISSED_CHAIN("Não herdou o FIRST de um não-terminal", "Em A → B…, tudo de FIRST(B) (menos ε) entra em FIRST(A)."),
    FIRST_MISSED_DIRECT("Esqueceu um terminal inicial", "Em A → a…, o terminal a entra direto em FIRST(A)."),
    FIRST_EPS_MISSING("Esqueceu o ε no FIRST", "Se alguma alternativa inteira pode sumir, ε ∈ FIRST(A)."),
    FIRST_EPS_EXTRA("ε indevido no FIRST", "ε só entra se TODOS os símbolos de alguma alternativa podem sumir."),
    FIRST_BEYOND("Olhou além de um símbolo que não some", "Pare no primeiro símbolo que não pode virar ε."),
    FIRST_CONFUSED_FOLLOW("Misturou FOLLOW no FIRST", "FIRST olha o começo do que A gera; FOLLOW olha o que vem depois de A."),
    FIRST_EXTRA("Terminal a mais no FIRST", "FIRST só contém os terminais que podem COMEÇAR as cadeias de A."),
    FOLLOW_MISSED_END("Esqueceu o $ do símbolo inicial", "$ ∈ FOLLOW(S) sempre, para o símbolo inicial S."),
    FOLLOW_EPS("Colocou ε no FOLLOW", "FOLLOW nunca contém ε."),
    FOLLOW_MISSED_FIRST("Não olhou o que vem depois", "Em X → α A β, FIRST(β) − {ε} entra em FOLLOW(A)."),
    FOLLOW_MISSED_INHERIT("Esqueceu a herança do FOLLOW", "Se A está no fim (ou o resto pode sumir), FOLLOW(X) entra em FOLLOW(A)."),
    FOLLOW_EXTRA("Terminal a mais no FOLLOW", "Procure A nos LADOS DIREITOS: só conta o que pode vir logo depois dele."),
    TABLE_MISSED_FIRST("Faltou entrada via FIRST", "A → α vai em M[A, a] para cada a ∈ FIRST(α)."),
    TABLE_MISSED_FOLLOW("Esqueceu a regra do ε na tabela", "Se α ⇒* ε, A → α vai nas colunas de FOLLOW(A), inclusive $."),
    TABLE_EXTRA("Entrada a mais na tabela", "A → α só entra nas colunas de FIRST(α) e, se α some, de FOLLOW(A)."),
    VERDICT_WRONG("Veredito LL(1) errado", "É LL(1) se e só se nenhuma célula tem duas produções."),
    LL_WRONG_PRODUCTION("Expandiu com a produção errada", "Consulte M[topo, próximo token] antes de expandir."),
    LL_MISSED_ERROR("Não percebeu um erro top-down", "Célula vazia, ou terminal no topo diferente do token = erro."),
    LL_FALSE_ERROR("Declarou erro cedo demais (top-down)", "Havia um movimento válido: confira a tabela."),
    LL_WHY_WRONG("Confundiu a regra FIRST/FOLLOW", "Uma produção só entra por FOLLOW quando o seu lado direito pode virar ε."),
    SR_REDUCE_EARLY("Reduziu cedo demais", "Só reduza se o topo é o handle e o token ∈ FOLLOW(A)."),
    SR_SHIFT_LATE("Empilhou quando devia reduzir", "Se o handle está completo e o token pode seguir A, reduza."),
    SR_WRONG_HANDLE("Reduziu pelo handle errado", "O handle é o lado direito que o autômato reconhece no topo da pilha."),
    SR_MISSED_ERROR("Não percebeu um erro bottom-up", "ACTION vazia = erro."),
    SR_FALSE_ERROR("Declarou erro cedo demais (bottom-up)", "Ainda havia ação válida para esse estado e token."),
    CLOSURE_MISSING("Fechamento incompleto", "Para cada • antes de um não-terminal B, adicione B → • γ e repita."),
    CLOSURE_EXTRA("Item a mais no conjunto", "Só entram itens com o ponto no início, de não-terminais logo após um •."),
    GOTO_WRONG("goto calculado errado", "Avance o ponto só nos itens com X depois do •; depois faça o fechamento."),
    SLR_REDUCE_COLUMNS("Reduce nas colunas erradas", "No SLR, reduce A → α só nas colunas de FOLLOW(A)."),
    SLR_MISSED_SHIFT("Faltou shift ou goto", "Cada transição do autômato vira shift (terminal) ou goto (não-terminal)."),
    SLR_EXTRA("Ação a mais na tabela", "Só transições e itens completos geram ações."),
    CONFLICT_MISSED("Não localizou o conflito", "Procure estados com item completo e uma transição por um terminal do FOLLOW."),
    DERIV_DEAD_END("Beco sem saída na derivação", "Antes de expandir, compare com o alvo: tamanho, prefixo e sufixo."),
    AMBIGUITY_SAME("Repetiu a mesma árvore", "Outra árvore exige outra escolha em algum ponto da derivação."),
    SURGERY_LEFT_REC("Ainda há recursão à esquerda", "A → A α | β vira A → β A' e A' → α A' | ε."),
    SURGERY_LANGUAGE("A linguagem mudou", "A transformação precisa gerar exatamente as mesmas cadeias."),
    SURGERY_NOT_LL1("A gramática ainda não é LL(1)", "Fatore prefixos comuns e elimine recursão à esquerda."),
    SURGERY_PREFIX("Ainda há prefixo comum", "A → α β1 | α β2 vira A → α A' e A' → β1 | β2."),
    CLASSIFY_WRONG("Classificação errada", "LR(0) ⊂ SLR ⊂ LALR ⊂ LR(1); LL(1) ⊂ LR(1)."),
}

// ------------------------------------------------------------------ perfil do jogador

data class SkillState(val box: Int = 1, val dueDay: Long = 0, val seen: Int = 0, val right: Int = 0)

data class Tally(val right: Int = 0, val total: Int = 0) {
    fun add(ok: Boolean) = Tally(right + if (ok) 1 else 0, total + 1)
    val rate: Float get() = if (total == 0) 0f else right.toFloat() / total
}

data class Profile(
    val stars: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    val calibration: Map<Confidence, Tally> = emptyMap(),
    val mistakes: Map<MistakeType, Int> = emptyMap(),
    val skills: Map<Skill, SkillState> = emptyMap(),
    val feelings: Map<String, Feeling> = emptyMap(),
    val predictions: Tally = Tally(),
    val explanations: Tally = Tally(),
    val lastDay: Long = -1,
    val streak: Int = 0,
    val seenLessons: Set<String> = emptySet(),
) {
    val totalStars: Int get() = stars.values.sum()
    fun starsOf(levelId: String) = stars[levelId] ?: 0
    fun isDone(levelId: String) = levelId in stars
}

/** Conversão do perfil para texto simples (chave=valor), para guardar em SharedPreferences. */
object ProfileCodec {
    fun encode(p: Profile): String = buildString {
        appendLine("xp=${p.xp}")
        appendLine("lastDay=${p.lastDay}")
        appendLine("streak=${p.streak}")
        appendLine("pred=${p.predictions.right}/${p.predictions.total}")
        appendLine("expl=${p.explanations.right}/${p.explanations.total}")
        p.stars.forEach { (k, v) -> appendLine("star.$k=$v") }
        p.calibration.forEach { (k, v) -> appendLine("cal.${k.name}=${v.right}/${v.total}") }
        p.mistakes.forEach { (k, v) -> appendLine("mis.${k.name}=$v") }
        p.skills.forEach { (k, v) -> appendLine("skill.${k.name}=${v.box},${v.dueDay},${v.seen},${v.right}") }
        p.feelings.forEach { (k, v) -> appendLine("feel.$k=${v.name}") }
        p.seenLessons.forEach { appendLine("lesson.$it=1") }
    }

    fun decode(text: String?): Profile {
        if (text.isNullOrBlank()) return Profile()
        var p = Profile()
        for (line in text.lines()) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq)
            val value = line.substring(eq + 1)
            runCatching {
                when {
                    key == "xp" -> p = p.copy(xp = value.toInt())
                    key == "lastDay" -> p = p.copy(lastDay = value.toLong())
                    key == "streak" -> p = p.copy(streak = value.toInt())
                    key == "pred" -> p = p.copy(predictions = tally(value))
                    key == "expl" -> p = p.copy(explanations = tally(value))
                    key.startsWith("star.") -> p = p.copy(stars = p.stars + (key.removePrefix("star.") to value.toInt()))
                    key.startsWith("cal.") -> p = p.copy(calibration = p.calibration + (Confidence.valueOf(key.removePrefix("cal.")) to tally(value)))
                    key.startsWith("mis.") -> p = p.copy(mistakes = p.mistakes + (MistakeType.valueOf(key.removePrefix("mis.")) to value.toInt()))
                    key.startsWith("skill.") -> {
                        val f = value.split(",")
                        val s = SkillState(f[0].toInt(), f[1].toLong(), f[2].toInt(), f[3].toInt())
                        p = p.copy(skills = p.skills + (Skill.valueOf(key.removePrefix("skill.")) to s))
                    }
                    key.startsWith("feel.") -> p = p.copy(feelings = p.feelings + (key.removePrefix("feel.") to Feeling.valueOf(value)))
                    key.startsWith("lesson.") -> p = p.copy(seenLessons = p.seenLessons + key.removePrefix("lesson."))
                }
            }
        }
        return p
    }

    private fun tally(v: String): Tally {
        val (a, b) = v.split("/")
        return Tally(a.toInt(), b.toInt())
    }
}

/** Resultado de uma fase, usado para calcular estrelas e XP. */
data class LevelOutcome(val mistakes: Int, val hints: Int) {
    val stars: Int
        get() = when {
            mistakes == 0 && hints == 0 -> 3
            mistakes + hints <= 2 -> 2
            else -> 1
        }
}
