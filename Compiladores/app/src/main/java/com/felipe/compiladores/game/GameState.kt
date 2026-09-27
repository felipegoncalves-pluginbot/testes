package com.felipe.compiladores.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Onde o perfil é guardado (SharedPreferences no Android, memória nos testes). */
interface ProfileStore {
    fun load(): String?
    fun save(text: String)
}

class MemoryStore(private var text: String? = null) : ProfileStore {
    override fun load() = text
    override fun save(text: String) { this.text = text }
}

/** Estado global do jogo: o perfil do jogador e todas as regras de pontuação. */
class GameState(private val store: ProfileStore, private val today: () -> Long = { System.currentTimeMillis() / 86_400_000L }) {

    var profile by mutableStateOf(ProfileCodec.decode(store.load()))
        private set

    private fun update(f: (Profile) -> Profile) {
        profile = f(profile)
        store.save(ProfileCodec.encode(profile))
    }

    /** Atualiza a sequência de dias seguidos de estudo. */
    fun touchDay() {
        val d = today()
        val p = profile
        if (p.lastDay == d) return
        update { it.copy(lastDay = d, streak = if (it.lastDay == d - 1) it.streak + 1 else 1) }
    }

    /** Uma aposta de confiança seguida de verificação: alimenta a curva de calibração. */
    fun recordCheck(confidence: Confidence, correct: Boolean) = update {
        val t = it.calibration[confidence] ?: Tally()
        it.copy(
            calibration = it.calibration + (confidence to t.add(correct)),
            xp = (it.xp + if (correct) confidence.win else confidence.lose).coerceAtLeast(0),
        )
    }

    fun recordMistakes(types: Collection<MistakeType>) {
        if (types.isEmpty()) return
        update { p -> p.copy(mistakes = types.fold(p.mistakes) { m, t -> m + (t to (m[t] ?: 0) + 1) }) }
    }

    fun recordMistake(type: MistakeType) = recordMistakes(listOf(type))

    fun recordPrediction(correct: Boolean) = update {
        it.copy(predictions = it.predictions.add(correct), xp = it.xp + if (correct) 3 else 0)
    }

    fun recordExplanation(correct: Boolean) = update {
        it.copy(explanations = it.explanations.add(correct), xp = it.xp + if (correct) 2 else 0)
    }

    /** Conclui uma fase; devolve o XP ganho. */
    fun completeLevel(levelId: String, outcome: LevelOutcome): Int {
        val old = profile.starsOf(levelId)
        val gained = if (outcome.stars > old) 10 * (outcome.stars - old) + if (old == 0) 10 else 0 else 2
        update {
            it.copy(stars = it.stars + (levelId to maxOf(old, outcome.stars)), xp = it.xp + gained)
        }
        return gained
    }

    fun setFeeling(levelId: String, feeling: Feeling) {
        update { it.copy(feelings = it.feelings + (levelId to feeling)) }
        if (feeling == Feeling.CONFUSED) {
            skillOfWorld(Content.worldOf(levelId).id)?.let { s ->
                update { it.copy(skills = it.skills + (s to (it.skills[s] ?: SkillState()).copy(box = 1, dueDay = today()))) }
            }
        }
    }

    fun markLessonSeen(worldId: String) = update { it.copy(seenLessons = it.seenLessons + worldId) }

    fun resetProgress() = update { Profile(lastDay = it.lastDay, streak = it.streak, settings = it.settings) }

    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    // ------------------------------------------------------------ treino relâmpago

    fun arcadeLevel(gameId: String): Int = profile.arcadeLevel[gameId] ?: 1

    fun arcadeBest(gameId: String): Int = profile.arcade[gameId].orEmpty().maxOrNull() ?: 0

    /**
     * Registra uma partida. [performance] ≥ 1 significa que o jogador bateu a meta do nível:
     * a dificuldade sobe; abaixo de 0,4 ela desce (dificuldade adaptativa, como no Peak).
     */
    fun recordArcade(gameId: String, score: Int, performance: Float) = update {
        val history = (it.arcade[gameId].orEmpty() + score).takeLast(12)
        val level = it.arcadeLevel[gameId] ?: 1
        val next = when {
            performance >= 1f -> (level + 1).coerceAtMost(10)
            performance < 0.4f -> (level - 1).coerceAtLeast(1)
            else -> level
        }
        it.copy(
            arcade = it.arcade + (gameId to history),
            arcadeLevel = it.arcadeLevel + (gameId to next),
            xp = it.xp + score / 20,
        )
    }

    // ------------------------------------------------------------ revisão espaçada (Leitner)

    /** Habilidades liberadas: basta ter concluído alguma fase do mundo correspondente. */
    fun unlockedSkills(): List<Skill> = Skill.entries.filter { skill ->
        val world = worldOfSkill(skill)
        Content.world(world).levels.any { profile.isDone(it.id) }
    }

    fun dueSkills(): List<Skill> {
        val d = today()
        return unlockedSkills().filter { (profile.skills[it]?.dueDay ?: 0) <= d }
            .sortedBy { profile.skills[it]?.box ?: 1 }
    }

    /**
     * Repetição baseada em confiança: acertar com certeza sobe de caixa;
     * acertar no chute mantém; errar volta para a caixa 1.
     */
    fun reviewAnswered(skill: Skill, correct: Boolean, confidence: Confidence) {
        val s = profile.skills[skill] ?: SkillState()
        val box = when {
            !correct -> 1
            confidence == Confidence.GUESS -> s.box
            else -> (s.box + 1).coerceAtMost(5)
        }
        val due = today() + INTERVALS[box - 1]
        update {
            it.copy(skills = it.skills + (skill to SkillState(box, due, s.seen + 1, s.right + if (correct) 1 else 0)))
        }
        recordCheck(confidence, correct)
    }

    companion object {
        val INTERVALS = listOf(1L, 2L, 4L, 8L, 16L)

        fun worldOfSkill(skill: Skill): String = when (skill) {
            Skill.FIRST -> "w2"
            Skill.FOLLOW -> "w3"
            Skill.LL_TABLE -> "w4"
            Skill.LL_STEP -> "w5"
            Skill.SR_STEP -> "w7"
            Skill.CLOSURE -> "w8"
            Skill.SLR_ACTION -> "w8"
        }

        fun skillOfWorld(worldId: String): Skill? = when (worldId) {
            "w2" -> Skill.FIRST
            "w3" -> Skill.FOLLOW
            "w4" -> Skill.LL_TABLE
            "w5" -> Skill.LL_STEP
            "w7" -> Skill.SR_STEP
            "w8" -> Skill.CLOSURE
            else -> null
        }
    }
}
