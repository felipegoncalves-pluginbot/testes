package com.felipe.compiladores.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private val exprLL = Grammar.parse(
        """
        E -> T E'
        E' -> + T E' | ε
        T -> F T'
        T' -> * F T' | ε
        F -> ( E ) | id
        """.trimIndent()
    )

    private val exprLR = Grammar.parse(
        """
        E -> E + T | T
        T -> T * F | F
        F -> ( E ) | id
        """.trimIndent()
    )

    @Test
    fun parsesGrammarText() {
        assertEquals(listOf("E", "E'", "T", "T'", "F"), exprLL.nonterminals)
        assertEquals(listOf("+", "*", "(", ")", "id"), exprLL.terminals)
        assertEquals(8, exprLL.productions.size)
        assertTrue(exprLL.productionsOf("E'")[1].isEpsilon)

        val chars = Grammar.parse("S -> aSb | ε", charMode = true)
        assertEquals(listOf("a", "S", "b"), chars.productions[0].rhs)
        assertTrue(chars.productions[1].isEpsilon)

        val bad = Grammar.tryParse("S a b")
        assertNull(bad.grammar)
        assertTrue(bad.errors.first().contains("seta"))
    }

    @Test
    fun firstAndFollowOfClassicExpressionGrammar() {
        val a = Analysis(exprLL)
        assertEquals(setOf("(", "id"), a.first["E"])
        assertEquals(setOf("+", EPS), a.first["E'"])
        assertEquals(setOf("(", "id"), a.first["T"])
        assertEquals(setOf("*", EPS), a.first["T'"])
        assertEquals(setOf(")", END), a.follow["E"])
        assertEquals(setOf(")", END), a.follow["E'"])
        assertEquals(setOf("+", ")", END), a.follow["T"])
        assertEquals(setOf("+", ")", END), a.follow["T'"])
        assertEquals(setOf("*", "+", ")", END), a.follow["F"])
        assertEquals(setOf("E'", "T'"), a.nullable)
        // Toda entrada tem uma justificativa.
        for ((nt, set) in a.first) for (t in set) assertNotNull(a.firstReason[nt to t])
        for ((nt, set) in a.follow) for (t in set) assertNotNull(a.followReason[nt to t])
        assertTrue(a.explainFollow("T", "+").contains("FIRST"))
    }

    @Test
    fun nullablePropagationInFirst() {
        val g = Grammar.parse("S -> A B c\nA -> a | ε\nB -> b | ε")
        val a = Analysis(g)
        assertEquals(setOf("a", "b", "c"), a.first["S"])
        val r = a.firstReason["S" to "c"] as FirstReason.FromProduction
        assertEquals(2, r.index)
    }

    @Test
    fun ll1TableAndParsing() {
        val t = LL1Table(Analysis(exprLL))
        assertTrue(t.isLL1)
        assertEquals("E' → ε", t.productions("E'", ")").single().text)
        assertEquals(LLRule.FOLLOW, t.entries("E'", END).single().rule)
        assertTrue(t.productions("T", "+").isEmpty())

        val parser = LLParser(t)
        val ok = parser.run(tokenizeInput("id + id * id"))
        assertEquals(ParseStatus.ACCEPTED, ok.last().status)
        assertEquals(listOf("id", "+", "id", "*", "id"), ok.last().tree.frontier(ok.last().rootId).filter { it != EPS })

        val bad = parser.run(tokenizeInput("id + * id"))
        assertEquals(ParseStatus.REJECTED, bad.last().status)
    }

    @Test
    fun danglingElseIsNotLL1() {
        val g = Grammar.parse("S -> i E t S S' | a\nS' -> e S | ε\nE -> b")
        val t = LL1Table(Analysis(g))
        assertFalse(t.isLL1)
        assertEquals(listOf("S'" to "e"), t.conflicts)
    }

    @Test
    fun lr0AutomatonMatchesDragonBookNumbering() {
        val a = LR0Automaton(exprLR)
        assertEquals(12, a.states.size)
        val expected = mapOf(
            (0 to "E") to 1, (0 to "T") to 2, (0 to "F") to 3, (0 to "(") to 4, (0 to "id") to 5,
            (1 to "+") to 6, (2 to "*") to 7, (4 to "E") to 8, (6 to "T") to 9, (7 to "F") to 10, (8 to ")") to 11,
        )
        for ((k, v) in expected) assertEquals("goto$k", v, a.transitions[k])
        assertEquals(7, a.states[0].size)
    }

    @Test
    fun slrTableAndShiftReduceParsing() {
        val classes = GrammarClasses(exprLR)
        assertFalse(classes.isLL1)
        assertFalse(classes.isLR0)
        assertTrue(classes.isSLR)
        assertTrue(classes.isLALR)
        assertTrue(classes.isLR1)
        val slr = classes.slrTable
        assertEquals(listOf(LRAction.Shift(7)), slr.actions(2, "*"))
        assertEquals(listOf(LRAction.Reduce(2)), slr.actions(2, "+"))

        val p = LRParser(slr)
        val run = p.run(tokenizeInput("id + id * id"))
        assertEquals(ParseStatus.ACCEPTED, run.last().status)
        assertEquals(ParseStatus.REJECTED, p.run(tokenizeInput("id + + id")).last().status)

        // Explicações para jogadas erradas.
        val s = run.first { it.stackSymbols == listOf("E", "+", "T") && it.lookahead == "*" }
        assertEquals(listOf(LRMove.Shift), p.validMoves(s))
        assertTrue(p.explainWrong(s, LRMove.Reduce(1)).contains("FOLLOW"))
    }

    @Test
    fun pointerGrammarIsLalrButNotSlr() {
        val g = Grammar.parse("S -> L = R | R\nL -> * R | id\nR -> L")
        val c = GrammarClasses(g)
        assertFalse(c.isSLR)
        assertTrue(c.isLALR)
        val conflict = c.slrTable.conflicts.single()
        assertEquals("=", conflict.terminal)
        assertTrue(conflict.isShiftReduce)
    }

    @Test
    fun lr1ButNotLalr() {
        val g = Grammar.parse("S -> a A d | b B d | a B e | b A e\nA -> c\nB -> c")
        val c = GrammarClasses(g)
        assertTrue(c.isLR1)
        assertFalse(c.isLALR)
        assertFalse(c.lalrTable.conflicts.first().isShiftReduce)
    }

    @Test
    fun lr0Grammar() {
        val g = Grammar.parse("S -> ( L ) | x\nL -> S | L , S")
        val c = GrammarClasses(g)
        assertTrue(c.isLR0)
        assertFalse(c.isLL1)
    }

    @Test
    fun derivationDeadEnds() {
        val d = Derivation(exprLR, tokenizeInput("id * id"))
        var s = d.initial()
        s = d.apply(s, 0, exprLR.productionsOf("E")[0]) // E → E + T
        assertNotNull(d.deadEndReason(s))
        var ok = d.initial()
        ok = d.apply(ok, 0, exprLR.productionsOf("E")[1]) // E → T
        assertNull(d.deadEndReason(ok))
        assertTrue(Earley(exprLR).derives(listOf("T", "*", "F"), tokenizeInput("id * ( id )")))
        assertFalse(Earley(exprLR).derives(listOf("T", "+", "F"), tokenizeInput("id * id")))
    }

    @Test
    fun earleyHandlesEpsilon() {
        val g = Grammar.parse("S -> A B\nA -> a | ε\nB -> b | ε")
        val e = Earley(g)
        assertTrue(e.derives(listOf("S"), emptyList()))
        assertTrue(e.derives(listOf("S"), listOf("b")))
        assertFalse(e.derives(listOf("S"), listOf("b", "a")))
    }

    @Test
    fun leftRecursionElimination() {
        assertEquals(setOf("E", "T"), Transform.leftRecursive(exprLR))
        var g = Transform.eliminateDirectLeftRecursion(exprLR, "E")
        g = Transform.eliminateDirectLeftRecursion(g, "T")
        assertTrue(Transform.leftRecursive(g).isEmpty())
        assertTrue(LL1Table(Analysis(g)).isLL1)
        val (a, b) = Transform.compareLanguages(exprLR, g, 5)
        assertNull(a)
        assertNull(b)
        val (c, _) = Transform.compareLanguages(exprLR, Grammar.parse("E -> id"), 3)
        assertNotNull(c)
    }

    @Test
    fun indirectLeftRecursion() {
        val g = Grammar.parse("S -> A a | b\nA -> S c | ε")
        assertEquals(setOf("S", "A"), Transform.leftRecursive(g))
        assertEquals(setOf("a", "b", EPS), Analysis(g).first["A"])
    }

    @Test
    fun leftFactoring() {
        val g = Grammar.parse("S -> i E t S | i E t S e S | a\nE -> b")
        val f = Transform.leftFactor(g, "S")
        assertEquals("S → i E t S S'", f.productionsOf("S")[0].text)
        assertEquals(2, f.productionsOf("S'").size)
        assertNull(Transform.compareLanguages(g, f, 7).first)
    }

    @Test
    fun treeLayout() {
        val parser = LLParser(LL1Table(Analysis(exprLL)))
        val last = parser.run(tokenizeInput("id")).last()
        val layout = layoutForest(last.tree, listOf(last.rootId))
        assertEquals(0, layout.y[last.rootId])
        assertTrue(layout.width >= 3)
    }
}
