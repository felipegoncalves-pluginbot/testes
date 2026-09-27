package com.felipe.compiladores.ui.level

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import com.felipe.compiladores.game.Mood
import com.felipe.compiladores.ui.components.Confetti
import com.felipe.compiladores.ui.components.Mascot
import com.felipe.compiladores.ui.components.MascotSays
import com.felipe.compiladores.ui.components.Tag
import com.felipe.compiladores.ui.components.appear
import com.felipe.compiladores.ui.games.DesignGame
import com.felipe.compiladores.ui.games.MagnetsGame
import com.felipe.compiladores.ui.games.RobotGame
import com.felipe.compiladores.ui.lesson.LessonPlayer
import com.felipe.compiladores.ui.theme.Danger
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.game.Confidence
import com.felipe.compiladores.game.Content
import com.felipe.compiladores.game.Feeling
import com.felipe.compiladores.game.GameState
import com.felipe.compiladores.game.Level
import com.felipe.compiladores.game.LevelOutcome
import com.felipe.compiladores.game.LevelSpec
import com.felipe.compiladores.game.MistakeType
import com.felipe.compiladores.game.World
import com.felipe.compiladores.ui.components.ButtonStyle
import com.felipe.compiladores.ui.components.Chip
import com.felipe.compiladores.ui.components.ChipsRow
import com.felipe.compiladores.ui.components.ConfidencePicker
import com.felipe.compiladores.ui.components.Feedback
import com.felipe.compiladores.ui.components.FeedbackKind
import com.felipe.compiladores.ui.components.GameButton
import com.felipe.compiladores.ui.components.GrammarCard
import com.felipe.compiladores.ui.components.MonoText
import com.felipe.compiladores.ui.components.Overlay
import com.felipe.compiladores.ui.components.Panel
import com.felipe.compiladores.ui.components.Stars
import com.felipe.compiladores.ui.components.SystemBack
import com.felipe.compiladores.ui.components.TopBar
import com.felipe.compiladores.ui.games.ClassifyGame
import com.felipe.compiladores.ui.games.ClosureGame
import com.felipe.compiladores.ui.games.DeriveGame
import com.felipe.compiladores.ui.games.LL1ParseGame
import com.felipe.compiladores.ui.games.LL1TableGame
import com.felipe.compiladores.ui.games.SetsGame
import com.felipe.compiladores.ui.games.ShiftReduceGame
import com.felipe.compiladores.ui.games.SlrConflictGame
import com.felipe.compiladores.ui.games.SlrTableGame
import com.felipe.compiladores.ui.games.SurgeryGame
import com.felipe.compiladores.ui.theme.Outline
import com.felipe.compiladores.ui.theme.Primary
import com.felipe.compiladores.ui.theme.Success
import com.felipe.compiladores.ui.theme.SurfaceHigh
import com.felipe.compiladores.ui.theme.Tertiary
import com.felipe.compiladores.ui.theme.TextDim
import com.felipe.compiladores.ui.theme.TextMain

/** Contabiliza erros, dicas e apostas de uma tentativa de fase. */
class LevelSession(val level: Level, val game: GameState) {
    var mistakes by mutableIntStateOf(0)
        private set
    var hintsUsed by mutableIntStateOf(0)
        private set

    /** Dicas 2 (onde olhar) e 3 (revelação), calculadas pelo jogo a partir do estado atual. */
    var hintProvider: (Int) -> String? = { null }

    fun mistake(types: Collection<MistakeType>) {
        mistakes++
        game.recordMistakes(types)
    }

    fun mistake(type: MistakeType) = mistake(listOf(type))

    fun check(confidence: Confidence, correct: Boolean) = game.recordCheck(confidence, correct)

    /** Resposta a um "por quê?" (autoexplicação). Não tira estrelas, mas vai para o diário. */
    fun explanation(correct: Boolean, typeIfWrong: MistakeType? = null) {
        game.recordExplanation(correct)
        if (!correct && typeIfWrong != null) game.recordMistake(typeIfWrong)
    }

    fun useHint() { hintsUsed++ }
}

private enum class Phase { INTRO, PLAY, RESULT }

@Composable
fun LevelHost(
    level: Level,
    game: GameState,
    sandbox: Boolean,
    onExit: () -> Unit,
    onNext: (Level) -> Unit,
) {
    val world: World? = if (sandbox) null else Content.worldOf(level.id)
    var attempt by remember(level.id) { mutableIntStateOf(0) }
    var phase by remember(level.id) { mutableStateOf(if (sandbox) Phase.PLAY else Phase.INTRO) }
    var predictionChoice by remember(level.id) { mutableStateOf<Int?>(null) }
    var predictionConfidence by remember(level.id) { mutableStateOf<Confidence?>(null) }
    var showHints by remember { mutableStateOf(false) }
    var showLesson by remember { mutableStateOf(false) }
    val session = remember(level.id, attempt) { LevelSession(level, game) }
    val revealed = remember(level.id, attempt) { mutableStateListOf<String>() }
    var outcome by remember(level.id, attempt) { mutableStateOf<LevelOutcome?>(null) }
    var xpGained by remember(level.id, attempt) { mutableIntStateOf(0) }

    SystemBack { onExit() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                level.title,
                if (world != null) "Mundo ${world.number} · ${world.title}" else "Laboratório livre",
                onBack = onExit,
            ) {
                if (phase == Phase.PLAY) {
                    if (world != null) TopAction("📖") { showLesson = true }
                    TopAction("💡${if (session.hintsUsed > 0) session.hintsUsed else ""}") { showHints = true }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (phase) {
                    Phase.INTRO -> Intro(
                        level, world,
                        predictionChoice, { predictionChoice = it },
                        predictionConfidence, { predictionConfidence = it },
                        onLesson = { showLesson = true },
                        onStart = { phase = Phase.PLAY },
                    )
                    Phase.PLAY, Phase.RESULT -> key(attempt) {
                        GameFor(level, session) {
                            val o = LevelOutcome(session.mistakes, session.hintsUsed)
                            outcome = o
                            if (!sandbox) {
                                xpGained = game.completeLevel(level.id, o)
                                val p = level.prediction
                                val choice = predictionChoice
                                if (p != null && choice != null) {
                                    game.recordPrediction(choice == p.correct)
                                    predictionConfidence?.let { game.recordCheck(it, choice == p.correct) }
                                }
                            }
                            phase = Phase.RESULT
                        }
                    }
                }
                Overlay(showHints, { showHints = false }) {
                    HintLadder(level, session, revealed)
                    Spacer(Modifier.height(12.dp))
                    GameButton("Voltar ao desafio", { showHints = false }, Modifier.fillMaxWidth(), style = ButtonStyle.OUTLINED)
                }
                val o = outcome
                Overlay(phase == Phase.RESULT && o != null, null) {
                    if (o != null) ResultContent(
                        level, o, xpGained, sandbox, predictionChoice, game,
                        onRetry = { attempt++; phase = Phase.PLAY },
                        onNext = Content.nextLevel(level.id)?.takeIf { !sandbox }?.let { n -> { onNext(n) } },
                        onExit = onExit,
                    )
                }
                if (phase == Phase.RESULT && o != null && o.stars >= 2) Confetti(attempt)
            }
        }
        if (showLesson && world != null) {
            LessonPlayer(world, onClose = { showLesson = false }, closeLabel = "Voltar à fase ▶")
        }
    }
}

@Composable
private fun TopAction(label: String, onClick: () -> Unit) {
    GameButton(
        label, onClick, Modifier.padding(start = 6.dp), style = ButtonStyle.OUTLINED, color = Tertiary,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun GameFor(level: Level, session: LevelSession, onSolved: () -> Unit) {
    when (val s = level.spec) {
        is LevelSpec.Derive -> DeriveGame(level, s, session, onSolved)
        LevelSpec.FirstSets -> SetsGame(level, follow = false, showFirst = false, session = session, onSolved = onSolved)
        is LevelSpec.FollowSets -> SetsGame(level, follow = true, showFirst = s.showFirst, session = session, onSolved = onSolved)
        is LevelSpec.LL1Table -> LL1TableGame(level, s, session, onSolved)
        is LevelSpec.LL1Parse -> LL1ParseGame(level, s, session, onSolved)
        is LevelSpec.Surgery -> SurgeryGame(level, s, session, onSolved)
        is LevelSpec.ShiftReduce -> ShiftReduceGame(level, s, session, onSolved)
        is LevelSpec.Closure -> ClosureGame(level, s, session, onSolved)
        LevelSpec.SlrTable -> SlrTableGame(level, session, onSolved)
        LevelSpec.SlrConflict -> SlrConflictGame(level, session, onSolved)
        LevelSpec.Classify -> ClassifyGame(level, session, onSolved)
        is LevelSpec.Design -> DesignGame(level, s, session, onSolved)
        is LevelSpec.Robot -> RobotGame(level, s, session, onSolved)
        is LevelSpec.Magnets -> MagnetsGame(level, s, session, onSolved)
    }
}

@Composable
private fun Intro(
    level: Level,
    world: World?,
    predictionChoice: Int?,
    onPrediction: (Int) -> Unit,
    confidence: Confidence?,
    onConfidence: (Confidence) -> Unit,
    onLesson: () -> Unit,
    onStart: () -> Unit,
) {
    val grammar = remember(level.grammar) { level.grammar.takeIf { it.isNotBlank() }?.let { Grammar.parse(it) } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MascotSays(level.briefing, Mood.HAPPY)
        if (grammar != null) GrammarCard(grammar, collapsible = false)
        val spec = level.spec
        if (spec is LevelSpec.Design) SpecCard(spec)
        if (world != null) {
            GameButton("📖 Aula ilustrada: ${world.lesson.title}", onLesson, Modifier.fillMaxWidth(), style = ButtonStyle.OUTLINED, color = Tertiary)
        }
        val p = level.prediction
        if (p != null) {
            Panel(title = "Previsão (antes de jogar)", accent = Tertiary) {
                Text(p.question, style = MaterialTheme.typography.bodyLarge, color = TextMain)
                Spacer(Modifier.height(10.dp))
                ChipsRow {
                    p.options.forEachIndexed { i, o -> Chip(o, predictionChoice == i, { onPrediction(i) }, color = Tertiary) }
                }
                Spacer(Modifier.height(12.dp))
                ConfidencePicker(confidence, onConfidence)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Você vai descobrir se acertou no fim da fase. Prever antes de ver ativa o que você já sabe e mostra onde está a lacuna.",
                    style = MaterialTheme.typography.bodySmall, color = TextDim,
                )
            }
        }
        val ready = p == null || (predictionChoice != null && confidence != null)
        GameButton(if (ready) "Começar ▶" else "Faça sua previsão para começar", onStart, Modifier.fillMaxWidth(), enabled = ready)
    }
}

/** Especificação da Fábrica: exemplos que devem ser aceitos e rejeitados. */
@Composable
fun SpecCard(spec: LevelSpec.Design) {
    Panel(title = "Especificação da fábrica", accent = Tertiary) {
        Text("Deve ACEITAR", style = MaterialTheme.typography.labelMedium, color = Success)
        ChipsRow { spec.accept.forEach { Tag(if (it.isBlank()) "ε (vazia)" else it, Success) } }
        Spacer(Modifier.height(8.dp))
        Text("Deve REJEITAR", style = MaterialTheme.typography.labelMedium, color = Danger)
        ChipsRow { spec.reject.forEach { Tag(if (it.isBlank()) "ε (vazia)" else it, Danger) } }
        Spacer(Modifier.height(8.dp))
        Text(
            "Não-terminais: ${spec.nonterminals.joinToString(" ")} · Terminais: ${spec.terminals.joinToString(" ")}" +
                (if (spec.requireLL1) " · exige LL(1)" else "") + (if (spec.requireLR1) " · exige LR(1)" else ""),
            style = MaterialTheme.typography.bodySmall, color = TextDim,
        )
    }
}

@Composable
private fun HintLadder(level: Level, session: LevelSession, revealed: MutableList<String>) {
    Text("💡 Escada de dicas", style = MaterialTheme.typography.titleLarge, color = TextMain)
    Spacer(Modifier.height(4.dp))
    Text(
        "Antes de pedir: o que exatamente está travando você? Cada dica custa uma estrela.",
        style = MaterialTheme.typography.bodySmall, color = TextDim,
    )
    Spacer(Modifier.height(12.dp))
    val labels = listOf("1 · Conceito", "2 · Onde olhar", "3 · Revelação")
    revealed.forEachIndexed { i, text ->
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .background(Tertiary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .border(1.dp, Tertiary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(labels.getOrElse(i) { "3 · Revelação" }, style = MaterialTheme.typography.labelMedium, color = Tertiary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMain)
        }
    }
    Spacer(Modifier.height(8.dp))
    val next = revealed.size + 1
    val label = when {
        next == 1 -> "Revelar dica 1 (conceito)"
        next == 2 -> "Revelar dica 2 (onde olhar)"
        next == 3 -> "Revelar dica 3 (revelação)"
        else -> "Mais uma revelação"
    }
    GameButton(label, {
        val text = when (next) {
            1 -> level.hint
            else -> session.hintProvider(minOf(next, 3)) ?: level.hint
        }
        session.useHint()
        revealed += text
    }, Modifier.fillMaxWidth(), color = Tertiary)
}

@Composable
private fun ResultContent(
    level: Level,
    outcome: LevelOutcome,
    xp: Int,
    sandbox: Boolean,
    predictionChoice: Int?,
    game: GameState,
    onRetry: () -> Unit,
    onNext: (() -> Unit)?,
    onExit: () -> Unit,
) {
    val mood = when (outcome.stars) { 3 -> Mood.CELEBRATE; 2 -> Mood.HAPPY; else -> Mood.THINK }
    val xpShown = remember { Animatable(0f) }
    LaunchedEffect(xp) { xpShown.animateTo(xp.toFloat(), tween(900, delayMillis = 500)) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(mood, size = 84.dp)
        Text("Fase concluída!", style = MaterialTheme.typography.headlineSmall, color = Success)
        Spacer(Modifier.height(6.dp))
        Row {
            repeat(3) { i ->
                Text(
                    if (i < outcome.stars) "★" else "☆", fontSize = 40.sp,
                    color = if (i < outcome.stars) Tertiary else Outline,
                    modifier = Modifier.appear("star$i", delayMs = 250 + i * 250),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${outcome.mistakes} erro(s) · ${outcome.hints} dica(s)" + if (!sandbox) " · +${xpShown.value.toInt()} XP" else "",
            style = MaterialTheme.typography.labelLarge, color = TextDim,
        )
        Text(
            when (outcome.stars) {
                3 -> "Perfeito! Sem erros e sem dicas."
                2 -> "Muito bom! Quer tentar as 3 estrelas?"
                else -> "Concluído! Errar faz parte — refaça quando quiser."
            },
            style = MaterialTheme.typography.bodySmall, color = TextDim,
        )
    }
    Spacer(Modifier.height(14.dp))
    val p = level.prediction
    if (p != null && predictionChoice != null) {
        val ok = predictionChoice == p.correct
        Feedback(
            if (ok) FeedbackKind.GOOD else FeedbackKind.WARN,
            "Você previu \"${p.options[predictionChoice]}\". ${p.explanation}",
            title = if (ok) "Previsão certeira!" else "A previsão falhou — e isso é ótimo para aprender",
        )
        Spacer(Modifier.height(10.dp))
    }
    Text("💡 Ideia-chave", style = MaterialTheme.typography.titleSmall, color = Primary)
    Spacer(Modifier.height(4.dp))
    MascotSays(level.insight, Mood.HAPPY, mascotSize = 44.dp)
    if (!sandbox) {
        Spacer(Modifier.height(14.dp))
        Text("Como você se sente sobre essa ideia?", style = MaterialTheme.typography.labelMedium, color = TextDim)
        Spacer(Modifier.height(6.dp))
        val current = game.profile.feelings[level.id]
        ChipsRow {
            Feeling.entries.forEach { f -> Chip("${f.emoji} ${f.label}", current == f, { game.setFeeling(level.id, f) }, color = Tertiary) }
        }
        if (current == Feeling.CONFUSED) {
            Spacer(Modifier.height(6.dp))
            Text("Anotado: esse tema vai aparecer no treino espaçado de hoje.", style = MaterialTheme.typography.bodySmall, color = Tertiary)
        }
    }
    Spacer(Modifier.height(16.dp))
    if (onNext != null) {
        GameButton("Próxima fase ▶", onNext, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameButton("↻ Jogar de novo", onRetry, Modifier.weight(1f), style = ButtonStyle.OUTLINED)
        GameButton(if (sandbox) "Voltar" else "Mapa", onExit, Modifier.weight(1f), style = ButtonStyle.OUTLINED)
    }
}
