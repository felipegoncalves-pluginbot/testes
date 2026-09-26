package com.felipe.compiladores.engine

data class TreeNode(val id: Int, val symbol: String, val children: List<Int> = emptyList())

/** Árvore (ou floresta) de derivação imutável. */
data class ParseTree(val nodes: Map<Int, TreeNode> = emptyMap(), val nextId: Int = 0) {

    fun add(symbol: String): Pair<ParseTree, Int> {
        val id = nextId
        return ParseTree(nodes + (id to TreeNode(id, symbol)), nextId + 1) to id
    }

    fun addAll(symbols: List<String>): Pair<ParseTree, List<Int>> {
        var t = this
        val ids = symbols.map { s -> t.add(s).also { t = it.first }.second }
        return t to ids
    }

    fun withChildren(id: Int, children: List<Int>): ParseTree =
        copy(nodes = nodes + (id to nodes.getValue(id).copy(children = children)))

    fun symbol(id: Int): String = nodes.getValue(id).symbol

    /** Folhas da subárvore em ordem (a "fronteira"). */
    fun frontier(id: Int): List<String> {
        val n = nodes.getValue(id)
        return if (n.children.isEmpty()) listOf(n.symbol) else n.children.flatMap { frontier(it) }
    }

    /** Assinatura estrutural da subárvore, usada para comparar árvores. */
    fun signature(id: Int): String {
        val n = nodes.getValue(id)
        return if (n.children.isEmpty()) n.symbol else n.symbol + n.children.joinToString(" ", "(", ")") { signature(it) }
    }
}

/**
 * Posição de cada nó para desenhar a floresta: x em "colunas de folha" e y em níveis.
 * Com `leavesAtBottom` (útil no bottom-up) todas as folhas ficam na última linha.
 */
data class TreeLayout(val x: Map<Int, Float>, val y: Map<Int, Int>, val width: Int, val height: Int)

fun layoutForest(tree: ParseTree, roots: List<Int>, leavesAtBottom: Boolean = false): TreeLayout {
    val x = HashMap<Int, Float>()
    val depth = HashMap<Int, Int>()
    val heightOf = HashMap<Int, Int>()
    var nextLeaf = 0
    var maxDepth = 0
    fun visit(id: Int, d: Int): Int {
        depth[id] = d
        if (d > maxDepth) maxDepth = d
        val n = tree.nodes.getValue(id)
        val h = if (n.children.isEmpty()) {
            x[id] = nextLeaf.toFloat()
            nextLeaf++
            0
        } else {
            val hs = n.children.map { visit(it, d + 1) }
            x[id] = (x.getValue(n.children.first()) + x.getValue(n.children.last())) / 2f
            hs.max() + 1
        }
        heightOf[id] = h
        return h
    }
    val maxHeight = roots.maxOfOrNull { visit(it, 0) } ?: 0
    return if (leavesAtBottom) {
        TreeLayout(x, heightOf.mapValues { maxHeight - it.value }, maxOf(nextLeaf, 1), maxHeight + 1)
    } else {
        TreeLayout(x, depth, maxOf(nextLeaf, 1), maxDepth + 1)
    }
}
