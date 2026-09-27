package com.felipe.compiladores.game

import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.DerivationMode
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.Transform
import com.felipe.compiladores.engine.tokenizeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NewModesTest {

    private fun designLevel(id: String) = Content.level(id).spec as LevelSpec.Design

    @Test
    fun factoryRejectsCommonMistakes() {
        val lista = designLevel("wf5")
        // Aceita vírgula sobrando: falha nos testes visíveis.
        val sobra = Grammar.parse("L -> id | id , L | id ,")
        assertFalse(DesignCheck.check(sobra, lista).ok)
        // Passa nos visíveis mas não é LL(1) na fase que exige LL(1).
        val ll = designLevel("wf7")
        val report = DesignCheck.check(Grammar.parse("L -> id | id , L\nL' -> id"), ll)
        assertTrue(report.tests.all { it.ok })
        assertFalse(report.ok)
        // Expressões ambíguas passam nos testes, mas não no controle de ambiguidade.
        val expr = designLevel("wf8")
        val amb = DesignCheck.check(Grammar.parse("E -> E + E | E * E | ( E ) | id"), expr)
        assertTrue(amb.tests.all { it.ok })
        assertFalse(amb.ok)
    }

    @Test
    fun factoryHiddenTestsCatchOverfitting() {
        // Decorou os exemplos de "um ou mais a" em vez de usar recursão.
        val spec = designLevel("wf1")
        val decorado = Grammar.parse("S -> a | a a | a a a a")
        val r = DesignCheck.check(decorado, spec)
        assertTrue(r.tests.all { it.ok })
        assertFalse(r.ok)
        assertTrue(r.extra.first().detail.contains("\"a a a\""))
    }

    @Test
    fun languageEnumerationIsFastForExpressions() {
        val g = Grammar.parse(Grammars.EXPR_LR)
        val start = System.currentTimeMillis()
        val lang = Transform.languageUpTo(g, 7)
        assertTrue(System.currentTimeMillis() - start < 3000)
        assertTrue(listOf("id", "+", "id", "*", "id") in lang)
        assertFalse(listOf("id", "+") in lang)
    }

    @Test
    fun robotDiagnosesWrongBranch() {
        val g = Grammar.parse(Grammars.ANBN)
        val table = LL1Table(Analysis(g))
        // Ramo ε sem o 'b': a frase "a b" falha dentro de S().
        val program = mapOf(1 to setOf("a"), 2 to setOf("$"))
        val run = RobotInterpreter.run(g, program, "a b")
        assertFalse(run.accepted)
        val diag = RobotInterpreter.diagnose(table, run)
        assertNotNull(diag)
        assertTrue(diag!!.contains("FOLLOW"))
        assertTrue(RobotInterpreter.run(g, RobotInterpreter.idealProgram(table), "a a b b").accepted)
    }

    @Test
    fun rightmostMagnetsFollowReductions() {
        val g = Grammar.parse(Grammars.EXPR_LR)
        val path = Magnets.derivation(g, tokenizeInput("id * id"), DerivationMode.RIGHTMOST)!!.map { it.symbols.joinToString(" ") }
        assertEquals(listOf("E", "T", "T * F", "T * id", "F * id", "id * id"), path)
    }

    @Test
    fun firstRainScoresOnlyFirstMembers() {
        val r = Random(7)
        var s = FirstRain.start(1, r)
        repeat(200) { s = FirstRain.step(s, 0.1f, r) }
        assertTrue(s.drops.isNotEmpty() || s.time > 0f)
        val good = s.drops.firstOrNull { it.good }
        val bad = s.drops.firstOrNull { !it.good }
        if (good != null) {
            val after = FirstRain.tap(s, good.id, r)
            assertTrue(after.score > s.score)
            assertTrue(good.symbol in s.first)
        }
        if (bad != null) {
            val after = FirstRain.tap(s, bad.id, r)
            assertEquals(s.lives - 1, after.lives)
            assertFalse(bad.symbol in s.first)
        }
        repeat(1000) { s = FirstRain.step(s, 0.1f, r) }
        assertTrue(s.over)
    }

    @Test
    fun handleQuestionsAreConsistent() {
        val r = Random(3)
        repeat(40) {
            val q = HandleHunt.question(r)
            assertEquals(q.production.rhs, q.form.subList(q.handle.first, q.handle.last + 1))
            assertTrue(q.handle in q.options)
            assertEquals(q.options.size, q.options.toSet().size)
        }
    }

    @Test
    fun stackMemoryAnswerMatchesOperations() {
        val r = Random(11)
        repeat(30) {
            val round = StackMemory.round(4, r)
            val replay = round.ops.fold(round.start) { st, op -> StackMemory.apply(st, op) }
            assertEquals(round.answer, replay)
            assertEquals(4, round.ops.size)
        }
    }

    @Test
    fun whoAmIOptionsIncludeTheAnswer() {
        val r = Random(5)
        for (c in WhoAmI.concepts) {
            val opts = WhoAmI.options(c, r)
            assertTrue(c.name in opts)
            assertEquals(4, opts.toSet().size)
            assertTrue(c.clues.size >= 3)
        }
    }

    @Test
    fun arcadeDifficultyAdapts() {
        val game = GameState(MemoryStore(), today = { 1 })
        game.recordArcade("chuva", 300, 1.2f)
        assertEquals(2, game.arcadeLevel("chuva"))
        game.recordArcade("chuva", 10, 0.1f)
        assertEquals(1, game.arcadeLevel("chuva"))
        assertEquals(300, game.arcadeBest("chuva"))
        game.updateSettings { it.copy(themeId = "sepia", reduceMotion = true, textScale = 1.15f) }
        val decoded = ProfileCodec.decode(ProfileCodec.encode(game.profile))
        assertEquals(game.profile, decoded)
        game.resetProgress()
        assertEquals("sepia", game.profile.settings.themeId)
    }

    @Test
    fun everyWorldHasALessonEndingInKeyPoints() {
        for (w in Content.worlds) {
            assertTrue(w.id, w.lesson.scenes.size >= 4)
            assertTrue(w.id, w.lesson.scenes.last() is Scene.KeyPoints)
            assertTrue(w.id, w.lesson.scenes.first() is Scene.Talk)
        }
        assertEquals((1..Content.worlds.size).toList(), Content.worlds.map { it.number })
    }
}
