package com.felipe.compiladores

import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.felipe.compiladores.engine.*
import com.felipe.compiladores.game.*
import com.felipe.compiladores.ui.App
import com.felipe.compiladores.ui.Screen
import com.felipe.compiladores.ui.games.ItemsTask
import com.felipe.compiladores.ui.games.firstExplainPrompt
import com.felipe.compiladores.ui.theme.CompiladoresTheme
import com.felipe.compiladores.ui.theme.ThemeState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Joga fases inteiras pela interface, calculando cada jogada correta com o motor.
 * Rode com ./gradlew connectedAndroidTest (precisa de emulador ou aparelho).
 */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun SemanticsNodeInteraction.tap() { performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle() }
    private fun click(text: String, substring: Boolean = false) =
        rule.onAllNodes(hasText(text, substring = substring) and hasClickAction()).onFirst().tap()
    private fun clickTag(tag: String) = rule.onNodeWithTag(tag).tap()
    /** O diálogo é desenhado por último: clica na última ocorrência. */
    private fun clickInDialog(text: String) = rule.onAllNodes(hasText(text) and hasClickAction()).onLast().tap()
    private fun bet() { click("Certeza", substring = true); click("Verificar") }

    private fun calmGame() = GameState(MemoryStore()).apply { updateSettings { it.copy(reduceMotion = true) } }

    private fun start(levelId: String, game: GameState = calmGame()): GameState {
        val lvl = Content.level(levelId)
        rule.setContent { CompiladoresTheme { App(game, listOf(Screen.Home, Screen.WorldView(Content.worldOf(levelId).id), Screen.Play(lvl))) } }
        lvl.prediction?.let { click(it.options[it.correct]); click("Certeza", substring = true) }
        click("Começar ▶")
        return game
    }

    private fun finish(game: GameState, levelId: String, stars: Int = 3) {
        click("Concluir fase ✓")
        rule.onNodeWithText("Fase concluída!").assertExists()
        assertEquals(stars, game.profile.starsOf(levelId))
    }

    @Test fun derivation() {
        val game = start("w1l1")
        click("S → a S b"); click("S → a S b"); click("S → ε")
        finish(game, "w1l1")
    }

    @Test fun ambiguityHunter() {
        val game = start("w1l5")
        // Árvore 1: + no topo
        listOf("E → E + E", "E → id", "E → E * E", "E → id", "E → id").forEach { click(it) }
        rule.onNodeWithText("Primeira árvore", substring = true).assertExists()
        // Repetir a mesma árvore conta erro
        listOf("E → E + E", "E → id", "E → E * E", "E → id", "E → id").forEach { click(it) }
        rule.onNodeWithText("mesma árvore", substring = true).assertExists()
        // Árvore 2: * no topo
        listOf("E → E * E", "E → E + E", "E → id", "E → id", "E → id").forEach { click(it) }
        finish(game, "w1l5", stars = 2)
        assertEquals(1, game.profile.predictions.right)
    }

    @Test fun deadEndCountsMistake() {
        val game = start("w1l2")
        click("E → E + T")
        rule.onNodeWithText("Beco sem saída").assertExists()
        click("↶ Desfazer")
        listOf("E → T", "T → T * F", "T → F", "F → id", "F → id").forEach { click(it) }
        finish(game, "w1l2", stars = 2)
        assertEquals(1, game.profile.mistakes[MistakeType.DERIV_DEAD_END])
    }

    @Test fun firstSets() {
        val game = start("w2l3")
        val an = Analysis(Grammar.parse(Content.level("w2l3").grammar))
        an.first.forEach { (nt, set) -> set.forEach { clickTag("cell:$nt:$it") } }
        bet()
        val prompt = firstExplainPrompt(an)!!
        click(prompt.options[prompt.valid.first()])
        finish(game, "w2l3")
        assertEquals(1, game.profile.explanations.right)
    }

    @Test fun firstSetsWrongThenHint() {
        val game = start("w2l1")
        clickTag("cell:S:a")
        bet()
        rule.onNodeWithText("com erro", substring = true).assertExists()
        click("💡", substring = true)
        click("Revelar dica 1 (conceito)")
        click("Revelar dica 2 (onde olhar)")
        rule.onNodeWithText("Confira FIRST", substring = true).assertExists()
        click("Voltar ao desafio")
        clickTag("cell:S:b"); clickTag("cell:B:c"); clickTag("cell:B:d")
        bet()
        val prompt = firstExplainPrompt(Analysis(Grammar.parse(Content.level("w2l1").grammar)))!!
        click(prompt.options[prompt.valid.first()])
        finish(game, "w2l1", stars = 1)
        assertTrue(game.profile.mistakes.isNotEmpty())
    }

    @Test fun ll1Table() {
        val game = start("w4l2")
        val g = Grammar.parse(Content.level("w4l2").grammar)
        val t = LL1Table(Analysis(g))
        for ((k, entries) in t.cells) {
            clickTag("cell:${k.first}:${k.second}")
            entries.forEach { click("(${it.production.id}) ${it.production.text}") }
            click("OK")
        }
        bet()
        click("pode virar ε", substring = true)
        finish(game, "w4l2")
    }

    @Test fun ll1Parser() {
        val game = start("w5l2")
        val g = Grammar.parse(Content.level("w5l2").grammar)
        val table = LL1Table(Analysis(g))
        val parser = LLParser(table)
        val run = parser.run(tokenizeInput("id + id"))
        for (s in run.dropLast(1)) {
            when (val m = parser.validMoves(s).first()) {
                is LLMove.Expand -> {
                    click("(${m.production.id}) ${m.production.text}")
                    // pergunta "por quê?"
                    if (rule.onAllNodesWithText("Por quê?").fetchSemanticsNodes().isNotEmpty()) {
                        val rule0 = table.entries(s.top, s.lookahead).first { it.production == m.production }.rule
                        click(if (rule0 == LLRule.FIRST) "∈ FIRST(" else "pode virar ε", substring = true)
                        click("Continuar")
                    }
                }
                LLMove.Match -> click("Casar  ${s.top}")
                LLMove.Accept -> click("Aceitar ✓")
                LLMove.Error -> click("Erro de sintaxe!")
            }
        }
        rule.onNodeWithText("Cadeia aceita", substring = true).assertExists()
        finish(game, "w5l2")
    }

    @Test fun ll1ErrorDetection() {
        val game = start("w5l4")
        val g = Grammar.parse(Content.level("w5l4").grammar)
        val parser = LLParser(LL1Table(Analysis(g)))
        val run = parser.run(tokenizeInput("id + * id"))
        // um erro de propósito: declarar erro logo no início
        click("Erro de sintaxe!")
        for (s in run.dropLast(1)) {
            when (val m = parser.validMoves(s).first()) {
                is LLMove.Expand -> click("(${m.production.id}) ${m.production.text}")
                LLMove.Match -> click("Casar  ${s.top}")
                LLMove.Accept -> click("Aceitar ✓")
                LLMove.Error -> click("Erro de sintaxe!")
            }
        }
        rule.onNodeWithText("Erro detectado", substring = true).assertExists()
        finish(game, "w5l4", stars = 2)
        assertEquals(1, game.profile.mistakes[MistakeType.LL_FALSE_ERROR])
    }

    @Test fun shiftReduce() {
        val game = start("w7l3")
        val g = Grammar.parse(Content.level("w7l3").grammar)
        val parser = LRParser(LRTable.slr(LR0Automaton(g)))
        val run = parser.run(tokenizeInput("id + id * id"))
        for (s in run.dropLast(1)) {
            when (val m = parser.validMoves(s).first()) {
                LRMove.Shift -> click("Empilhar ⤵")
                is LRMove.Reduce -> { click("Reduzir…"); val p = g.production(m.prod); click("(${p.id}) ${p.text}") }
                LRMove.Accept -> click("Aceitar ✓")
                LRMove.Error -> click("Erro!")
            }
        }
        rule.onNodeWithText("Aceita!", substring = true).assertExists()
        finish(game, "w7l3")
    }

    @Test fun closure() {
        val game = start("w8l2")
        val g = Grammar.parse(Content.level("w8l2").grammar)
        val a = LR0Automaton(g)
        val qs = (Content.level("w8l2").spec as LevelSpec.Closure).questions
        qs.forEachIndexed { i, q ->
            val task = when (q) { ClosureQuestion.Initial -> ItemsTask(a, null, null); is ClosureQuestion.Goto -> ItemsTask(a, q.state, q.symbol) }
            task.expected.forEach { clickTag("item:${it.prod}:${it.dot}") }
            bet()
            rule.onNodeWithText("Correto!", substring = true).assertExists()
            if (i < qs.lastIndex) click("Próxima pergunta ▶")
        }
        finish(game, "w8l2")
    }

    @Test fun slrTable() {
        val game = start("w8l3")
        val g = Grammar.parse(Content.level("w8l3").grammar)
        val table = LRTable.slr(LR0Automaton(g))
        for (s in 0 until table.stateCount) for (c in table.actionColumns + table.gotoColumns) {
            val entries = Grading.lrEntries(table, s, c)
            if (entries.isEmpty()) continue
            clickTag("cell:$s:$c")
            entries.forEach { e ->
                when {
                    e == "acc" -> clickInDialog("acc (aceitar)")
                    e.startsWith("r") -> { val p = g.production(e.drop(1).toInt()); clickInDialog("(${p.id}) ${p.text}") }
                    else -> clickInDialog(e)
                }
            }
            click("OK")
        }
        bet()
        finish(game, "w8l3")
    }

    @Test fun slrConflict() {
        val game = start("w8l5")
        val table = LRTable.slr(LR0Automaton(Grammar.parse(Content.level("w8l5").grammar)))
        table.conflicts.forEach { clickTag("cell:${it.state}:${it.terminal}") }
        click("shift/reduce")
        bet()
        finish(game, "w8l5")
    }

    @Test fun classify() {
        val game = start("w9l4")
        click("LALR(1)"); click("LR(1)")
        bet()
        finish(game, "w9l4")
    }

    @Test fun surgery() {
        val game = start("w6l1")
        // A -> A a | b  ==>  A -> b A' ; A' -> a A' | ε
        clickTag("alt:A:0"); clickTag("tray:✕ alt")
        clickTag("alt:A:0"); clickTag("tray:A'")
        clickTag("addalt:A'"); clickTag("tray:a"); clickTag("tray:A'")
        clickTag("addalt:A'")
        click("Certeza", substring = true); click("Testar cirurgia")
        rule.onNodeWithText("Cirurgia bem-sucedida", substring = true).assertExists()
        finish(game, "w6l1")
    }

    @Test fun surgeryReportsCounterexample() {
        val game = start("w6l1")
        clickTag("alt:A:0"); clickTag("tray:✕ alt")   // sobra A -> b: linguagem menor
        click("Certeza", substring = true); click("Testar cirurgia")
        rule.onNodeWithText("A original gera", substring = true).assertExists()
        assertEquals(1, game.profile.mistakes[MistakeType.SURGERY_LANGUAGE])
    }

    @Test fun reviewSession() {
        val game = calmGame()
        listOf("w2l1", "w3l1", "w4l1", "w5l1", "w7l1", "w8l1").forEach { game.completeLevel(it, LevelOutcome(0, 0)) }
        rule.setContent { CompiladoresTheme { App(game, listOf(Screen.Home, Screen.Review)) } }
        repeat(6) {
            if (rule.onAllNodesWithTag("option:0").fetchSemanticsNodes().isNotEmpty()) clickTag("option:0")
            click("Acho que sim", substring = true)
            click("Verificar")
            click("Próximo ▶")
        }
        rule.onNodeWithText("Sessão concluída!").assertExists()
        assertEquals(6, game.profile.skills.values.sumOf { it.seen })
    }

    @Test fun sandboxToParser() {
        val game = calmGame()
        rule.setContent { CompiladoresTheme { App(game, listOf(Screen.Home, Screen.Sandbox)) } }
        click("Expressões LL")
        listOf("FIRST/FOLLOW", "LL(1)", "LR(0)", "SLR", "LALR", "LR(1)", "Resumo").forEach { click(it) }
        click("Jogar")
        click("🤖 Parser top-down LL(1)")
        val g = Grammar.parse(Grammars.EXPR_LL)
        val parser = LLParser(LL1Table(Analysis(g)))
        for (s in parser.run(tokenizeInput("id + id * id")).dropLast(1)) {
            when (val m = parser.validMoves(s).first()) {
                is LLMove.Expand -> click("(${m.production.id}) ${m.production.text}")
                LLMove.Match -> click("Casar  ${s.top}")
                LLMove.Accept -> click("Aceitar ✓")
                LLMove.Error -> click("Erro de sintaxe!")
            }
        }
        click("Concluir fase ✓")
        rule.onNodeWithText("Fase concluída!").assertExists()
        click("Voltar")
        rule.onNodeWithText("Laboratório livre").assertExists()
    }

    @Test fun nextLevelNavigation() {
        val game = start("w1l6")
        listOf("S → if S", "S → if S else S", "S → x", "S → x").forEach { click(it) }
        listOf("S → if S else S", "S → if S", "S → x", "S → x").forEach { click(it) }
        click("Concluir fase ✓")
        click("Próxima fase ▶")
        rule.onNodeWithText("Linha de a's").assertExists()
    }

    @Test fun factoryDesign() {
        val game = start("wf1")
        // S -> a S | a (S já começa com uma alternativa vazia selecionada)
        clickTag("tray:a"); clickTag("tray:S")
        clickTag("addalt:S"); clickTag("tray:a")
        click("Certeza", substring = true); click("▶ Ligar a esteira (rodar testes)")
        rule.onNodeWithText("igual ao recorde", substring = true).assertExists()
        finish(game, "wf1")
    }

    @Test fun factoryFailsThenPasses() {
        val game = start("wf3")
        clickTag("tray:a"); clickTag("tray:S"); clickTag("tray:b")
        click("Certeza", substring = true); click("▶ Ligar a esteira (rodar testes)")
        rule.onNodeWithText("Esteira de testes", substring = true).assertExists()
        clickTag("addalt:S")
        click("Certeza", substring = true); click("▶ Ligar a esteira (rodar testes)")
        finish(game, "wf3", stars = 2)
        assertEquals(1, game.profile.mistakes[MistakeType.DESIGN_TEST])
    }

    @Test fun robotProgramming() {
        val game = start("wr2")
        clickTag("branch:1"); clickInDialog("a"); click("OK")
        clickTag("branch:2"); clickInDialog("b"); clickInDialog("$"); click("OK")
        click("Certeza", substring = true); click("▶ Executar testes")
        rule.onNodeWithText("Todos os testes passaram", substring = true).assertExists()
        finish(game, "wr2")
    }

    @Test fun robotShowsDiagnosis() {
        val game = start("wr2")
        clickTag("branch:1"); clickInDialog("a"); click("OK")
        clickTag("branch:2"); clickInDialog("$"); click("OK")
        click("Certeza", substring = true); click("▶ Executar testes")
        rule.onNodeWithText("deveria entrar no ramo", substring = true).assertExists()
        assertEquals(1, game.profile.mistakes[MistakeType.ROBOT_BRANCH])
    }

    @Test fun magnets() {
        val game = start("w1m1")
        val level = Content.level("w1m1")
        val spec = level.spec as LevelSpec.Magnets
        val g = Grammar.parse(level.grammar)
        val puzzle = Magnets.build(g, tokenizeInput(spec.target), spec.mode, spec.distractors, kotlin.random.Random(level.id.hashCode()))
        val pieces = puzzle.pieces(kotlin.random.Random(level.id.hashCode() + 1))
        // Primeiro uma armadilha: deve ser recusada.
        clickTag("magnet:${pieces.indexOf(puzzle.distractors.first())}")
        bet()
        rule.onNodeWithText("é uma armadilha", substring = true).assertExists()
        clickTag("placed:0")
        puzzle.forms.drop(1).forEach { f -> clickTag("magnet:${pieces.indexOf(f)}") }
        bet()
        finish(game, "w1m1", stars = 2)
    }

    @Test fun lessonsPlayThroughEveryWorld() {
        var wid by androidx.compose.runtime.mutableStateOf(Content.worlds.first().id)
        ThemeState.settings = ThemeState.settings.copy(reduceMotion = true)
        rule.setContent {
            CompiladoresTheme {
                androidx.compose.runtime.key(wid) { com.felipe.compiladores.ui.lesson.LessonPlayer(Content.world(wid), onClose = {}) }
            }
        }
        for (w in Content.worlds) {
            wid = w.id
            rule.waitForIdle()
            w.lesson.scenes.forEachIndexed { i, scene ->
                if (scene is Scene.BrainPower) click("Pensei! Mostrar resposta")
                if (scene is Scene.Fireside) repeat(scene.lines.size - 2) { click("Continuar conversa…") }
                if (i < w.lesson.scenes.lastIndex) click("Próximo →")
            }
            rule.onNodeWithText("Bora jogar! ▶").assertExists()
        }
    }

    @Test fun settingsChangeTheme() {
        val game = calmGame()
        rule.setContent { CompiladoresTheme { App(game, listOf(Screen.Home, Screen.Settings)) } }
        clickTag("theme:sepia")
        click("Grande")
        rule.waitForIdle()
        assertEquals("sepia", game.profile.settings.themeId)
        assertEquals("sepia", ThemeState.settings.themeId)
        assertEquals(1.15f, game.profile.settings.textScale)
        clickTag("theme:everforest")
    }

    @Test fun homeContinueAndFirstVisitLesson() {
        val game = calmGame()
        rule.setContent { CompiladoresTheme { App(game) } }
        clickTag("world:wf")
        rule.onNodeWithText("Aula ilustrada", substring = true).assertExists()
        click("←")
        rule.onNodeWithText("Linha de a's").assertExists()
        click("←")
        clickTag("continue")
        rule.onNodeWithText("Começar ▶").assertExists()
    }

    @Test fun arcadeWhoAmIAndMemory() {
        val game = calmGame()
        rule.setContent { CompiladoresTheme { App(game, listOf(Screen.Home, Screen.Arcade)) } }
        clickTag("arcade:quem")
        repeat(WhoAmI.ROUNDS) { i ->
            clickTag("option:0")
            click(if (i < WhoAmI.ROUNDS - 1) "Próximo ▶" else "Ver resultado")
        }
        rule.onNodeWithText("pontos", substring = true).assertExists()
        assertEquals(1, game.profile.arcade["quem"]?.size)
        click("Sair")
        clickTag("arcade:pilha")
        repeat(3) {
            click("Memorizei! Esconder")
            click("Confirmar")
            click("Continuar ▶")
        }
        rule.onNodeWithText("pontos", substring = true).assertExists()
        assertEquals(1, game.profile.arcade["pilha"]?.size)
    }
}
