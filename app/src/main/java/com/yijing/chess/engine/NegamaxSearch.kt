package com.yijing.chess.engine

import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface SearchRules<B> {
    fun moves(board: B, side: PlayerSide): List<MoveRecord>
    fun after(board: B, move: MoveRecord, side: PlayerSide): B
    fun evaluate(board: B, side: PlayerSide): Int
    fun inCheck(board: B, side: PlayerSide): Boolean
    fun priority(board: B, move: MoveRecord): Int
    fun opposite(side: PlayerSide): PlayerSide = side.opposite()
    val stalemateLoses: Boolean get() = false
}

data class SearchResult(val move: MoveRecord?, val score: Int, val depth: Int, val nodes: Long, val elapsedMillis: Long)

/** Every score is relative to the player to move; only completed iterations replace the fallback. */
object NegamaxSearch {
    private const val MATE = 100_000
    private class DeadlineReached : RuntimeException()

    suspend fun <B> find(board: B, side: PlayerSide, difficulty: Int, budgetMillis: Long, rules: SearchRules<B>): SearchResult {
        val started = System.nanoTime()
        val deadline = started + budgetMillis.coerceIn(25, 30_000) * 1_000_000
        val context = currentCoroutineContext()
        var nodes = 0L
        fun check() {
            context.ensureActive()
            nodes++
            if (System.nanoTime() >= deadline) throw DeadlineReached()
        }
        fun ordered(position: B, moves: List<MoveRecord>) = moves.sortedByDescending { rules.priority(position, it) }
        fun search(position: B, turn: PlayerSide, depth: Int, alpha: Int, beta: Int, ply: Int, quiet: Int): Int {
            check()
            val legal = rules.moves(position, turn)
            val checked = rules.inCheck(position, turn)
            if (legal.isEmpty()) return if (checked || rules.stalemateLoses) -MATE + ply else 0
            var a = alpha
            val candidates = if (depth <= 0) {
                val standPat = rules.evaluate(position, turn)
                if (quiet <= 0) return standPat
                if (!checked) {
                    if (standPat >= beta) return standPat
                    a = maxOf(a, standPat)
                }
                if (checked) legal else legal.filter { it.capturedText != null || it.promotion != null }
            } else legal
            for (move in ordered(position, candidates)) {
                check()
                val value = -search(rules.after(position, move, turn), rules.opposite(turn), depth - 1, -beta, -a, ply + 1, if (depth <= 0) quiet - 1 else quiet)
                if (value >= beta) return value
                a = maxOf(a, value)
            }
            return a
        }
        val roots = ordered(board, rules.moves(board, side))
        if (roots.isEmpty()) return SearchResult(null, if (rules.inCheck(board, side) || rules.stalemateLoses) -MATE else 0, 0, nodes, 0)
        var best = roots.first()
        var score = rules.evaluate(rules.after(board, best, side), side)
        var completedDepth = 0
        val maxDepth = when (difficulty) { 1 -> 2; 2 -> 3; else -> 4 }
        try {
            for (depth in 1..maxDepth) {
                var iterationBest = best
                var iterationScore = -MATE - 1
                for (move in listOf(best) + roots.filter { it != best }) {
                    check()
                    val value = -search(rules.after(board, move, side), rules.opposite(side), depth - 1, -MATE - 1, -iterationScore, 1, 3)
                    if (value > iterationScore) { iterationScore = value; iterationBest = move }
                }
                best = iterationBest; score = iterationScore; completedDepth = depth
                if (score >= MATE - 50) break
            }
        } catch (_: DeadlineReached) {
            context.ensureActive()
        }
        return SearchResult(best, score, completedDepth, nodes, (System.nanoTime() - started) / 1_000_000)
    }
}
