package com.felipe.compiladores.game

import com.felipe.compiladores.engine.Analysis
import com.felipe.compiladores.engine.DerivationMode
import com.felipe.compiladores.engine.EPS
import com.felipe.compiladores.engine.Earley
import com.felipe.compiladores.engine.END
import com.felipe.compiladores.engine.Grammar
import com.felipe.compiladores.engine.GrammarClasses
import com.felipe.compiladores.engine.LL1Table
import com.felipe.compiladores.engine.LLParser
import com.felipe.compiladores.engine.LRMove
import com.felipe.compiladores.engine.LRParser
import com.felipe.compiladores.engine.ParseStatus
import com.felipe.compiladores.engine.Transform
import com.felipe.compiladores.engine.tokenizeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garante que toda fase tem solução e que os textos batem com o motor. */
class ContentTest {

    @Test
    fun everyLevelIsConsistent() {
        val ids = Content.allLevels.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (level in Content.allLevels) {
            val g = Grammar.parse(level.grammar)
            val c = GrammarClasses(g)
            when (val s = level.spec) {
                is LevelSpec.Derive -> {
                    val target = tokenizeInput(s.target)
                    assertTrue(level.id, Earley(g).derives(listOf(g.start), target))
                    assertTrue(level.id, target.all { it in g.terminals })
                    if (s.ambiguity) assertEquals(level.id, DerivationMode.LEFTMOST, s.mode)
                }
                is LevelSpec.LL1Table -> if (!s.askVerdict) assertTrue(level.id, c.isLL1)
                is LevelSpec.LL1Parse -> {
                    assertTrue(level.id, c.isLL1)
                    val run = LLParser(c.ll1).run(tokenizeInput(s.input))
                    assertTrue(level.id, run.last().status != ParseStatus.RUNNING)
                    assertTrue(level.id, run.size < 80)
                }
                is LevelSpec.ShiftReduce -> {
                    assertTrue(level.id, c.isSLR)
                    val run = LRParser(c.slrTable).run(tokenizeInput(s.input))
                    assertTrue(level.id, run.last().status != ParseStatus.RUNNING)
                }
                is LevelSpec.Closure -> for (q in s.questions) if (q is ClosureQuestion.Goto) {
                    assertNotNull("${level.id} $q", c.lr0Automaton.transitions[q.state to q.symbol])
                }
                LevelSpec.SlrTable -> assertTrue(level.id, c.isSLR)
                LevelSpec.SlrConflict -> assertTrue(level.id, c.slrTable.hasConflicts)
                is LevelSpec.Surgery -> assertSurgerySolvable(level, g, s)
                else -> Unit
            }
        }
    }

    private fun assertSurgerySolvable(level: Level, g: Grammar, spec: LevelSpec.Surgery) {
        var fixed = g
        val names = spec.newSymbols.toMutableList()
        for (a in g.nonterminals) {
            if (a in Transform.directLeftRecursive(fixed)) fixed = Transform.eliminateDirectLeftRecursion(fixed, a, names.removeAt(0))
        }
        for (a in g.nonterminals) {
            if (a in SurgeryCheck.commonPrefixes(fixed)) fixed = Transform.leftFactor(fixed, a, names.removeAt(0))
        }
        val report = SurgeryCheck.check(g, fixed, spec)
        assertTrue("${level.id}: ${report.lines}", report.ok)
        // E a gramática original não passa (senão a fase seria trivial).
        assertFalse(level.id, SurgeryCheck.check(g, g, spec).ok)
    }

    @Test
    fun predictionsMatchTheEngine() {
        val first = Analysis(Grammar.parse(Content.level("w2l3").grammar)).first.getValue("S") - EPS
        assertEquals(3, first.size)
        assertFalse(LL1Table(Analysis(Grammar.parse(Content.level("w4l4").grammar))).isLL1)

        val w5l4 = Content.level("w5l4")
        val run = LLParser(LL1Table(Analysis(Grammar.parse(w5l4.grammar)))).run(tokenizeInput((w5l4.spec as LevelSpec.LL1Parse).input))
        assertEquals(ParseStatus.REJECTED, run.last().status)
        assertEquals("*", run.last().lookahead)

        val g7 = GrammarClasses(Grammar.parse(Content.level("w7l3").grammar))
        val p = LRParser(g7.slrTable)
        val s = p.run(tokenizeInput("id + id * id")).first { it.stackSymbols == listOf("E", "+", "T") && it.lookahead == "*" }
        assertEquals(listOf(LRMove.Shift), p.validMoves(s))
    }

    @Test
    fun hintsAboutFollowSetsAreTrue() {
        val an = Analysis(Grammar.parse(Grammars.LIST_LR0))
        assertEquals(setOf(")", ",", END), an.follow["S"])
        assertEquals(setOf(")", ","), an.follow["L"])
        val ifElse = Analysis(Grammar.parse(Grammars.IF_ELSE))
        assertEquals(setOf("e", END), ifElse.follow["S'"])
    }

    @Test
    fun classifierLevelsHaveTheExpectedAnswers() {
        fun classes(text: String) = GrammarClasses(Grammar.parse(text)).let {
            listOf(it.isLL1, it.isLR0, it.isSLR, it.isLALR, it.isLR1)
        }
        assertEquals(listOf(false, false, true, true, true), classes(Grammars.EXPR_LR))
        assertEquals(listOf(false, true, true, true, true), classes(Grammars.LIST_LR0))
        assertEquals(listOf(true, false, true, true, true), classes(Grammars.EXPR_LL))
        assertEquals(listOf(false, false, false, true, true), classes(Grammars.POINTERS))
        assertEquals(listOf(false, false, false, false, true), classes(Grammars.LR1_NOT_LALR))
        assertEquals(listOf(false, false, false, false, false), classes(Grammars.EXPR_AMB))
    }

    @Test
    fun reviewGeneratorProducesValidQuestions() {
        val gen = ReviewGenerator(Random(42))
        for (skill in Skill.entries) repeat(60) {
            when (val q = gen.generate(skill)) {
                is ChoiceQuestion -> assertTrue("$skill ${q.prompt} ${q.options}", q.answer in q.options.indices)
                is SetQuestion -> assertTrue(q.options.containsAll(q.answer))
                is ItemsQuestion -> assertTrue(q.answer.isNotEmpty())
            }
        }
    }

    @Test
    fun profileRoundTrip() {
        val state = GameState(MemoryStore(), today = { 100 })
        state.touchDay()
        state.recordCheck(Confidence.SURE, true)
        state.recordCheck(Confidence.GUESS, false)
        state.recordMistake(MistakeType.FOLLOW_EPS)
        state.completeLevel("w1l1", LevelOutcome(0, 0))
        state.setFeeling("w2l1", Feeling.CONFUSED)
        state.reviewAnswered(Skill.FIRST, true, Confidence.SURE)
        val decoded = ProfileCodec.decode(ProfileCodec.encode(state.profile))
        assertEquals(state.profile, decoded)
        assertEquals(3, decoded.starsOf("w1l1"))
        assertEquals(2, decoded.skills.getValue(Skill.FIRST).box)
    }
}
