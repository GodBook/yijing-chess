package com.yijing.chess.xiangqi

import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class AIDecision(
    val move: MoveRecord?,
    val score: Int
)

object XiangqiAI {

    private val PIECE_VALUES = mapOf(
        XiangqiPieceType.KING to 10000,
        XiangqiPieceType.ROOK to 900,
        XiangqiPieceType.CANNON to 450,
        XiangqiPieceType.KNIGHT to 400,
        XiangqiPieceType.BISHOP to 200,
        XiangqiPieceType.ADVISOR to 200,
        XiangqiPieceType.PAWN to 100
    )

    /**
     * 兵/卒过河附加价值
     */
    private const val PAWN_ACROSS_RIVER_BONUS = 120

    suspend fun findBestMove(
        board: XiangqiBoard,
        side: PlayerSide,
        depth: Int = 3
    ): AIDecision = withContext(Dispatchers.Default) {
        val legalMoves = XiangqiRule.getLegalMoves(board, side)
        if (legalMoves.isEmpty()) {
            return@withContext AIDecision(null, if (XiangqiRule.isKingInCheck(board, side)) -9999 else 0)
        }

        // 排序走法：优先吃子，提高剪枝效率 (Move Ordering)
        val sortedMoves = legalMoves.sortedByDescending { move ->
            val captured = board[move.to]
            if (captured != null) (PIECE_VALUES[captured.type] ?: 0) * 10 else 0
        }

        var bestMove: MoveRecord = sortedMoves.first()
        var bestScore = Int.MIN_VALUE + 100
        val alpha = Int.MIN_VALUE + 100
        val beta = Int.MAX_VALUE - 100

        for (move in sortedMoves) {
            val movedPiece = board[move.from]!!
            val capturedPiece = board[move.to]

            board[move.to] = movedPiece
            board[move.from] = null
            board.turn = side.xiangqiOpposite

            val score = -alphaBeta(
                board = board,
                currentSide = side.xiangqiOpposite,
                depth = depth - 1,
                alpha = -beta,
                beta = -bestScore,
                maximizingSide = side
            )

            board[move.from] = movedPiece
            board[move.to] = capturedPiece
            board.turn = side

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        AIDecision(bestMove, bestScore)
    }

    private fun alphaBeta(
        board: XiangqiBoard,
        currentSide: PlayerSide,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximizingSide: PlayerSide
    ): Int {
        if (depth <= 0) {
            return evaluate(board, maximizingSide)
        }

        val legalMoves = XiangqiRule.getLegalMoves(board, currentSide)
        if (legalMoves.isEmpty()) {
            return if (XiangqiRule.isKingInCheck(board, currentSide)) {
                // 被将死
                if (currentSide == maximizingSide) -9000 - depth else 9000 + depth
            } else {
                0 // 困毙 / 和
            }
        }

        var curAlpha = alpha
        val sortedMoves = legalMoves.sortedByDescending { move ->
            val captured = board[move.to]
            if (captured != null) (PIECE_VALUES[captured.type] ?: 0) * 10 else 0
        }

        for (move in sortedMoves) {
            val movedPiece = board[move.from]!!
            val capturedPiece = board[move.to]

            board[move.to] = movedPiece
            board[move.from] = null
            board.turn = currentSide.xiangqiOpposite

            val score = -alphaBeta(
                board = board,
                currentSide = currentSide.xiangqiOpposite,
                depth = depth - 1,
                alpha = -beta,
                beta = -curAlpha,
                maximizingSide = maximizingSide
            )

            board[move.from] = movedPiece
            board[move.to] = capturedPiece
            board.turn = currentSide

            if (score >= beta) {
                return beta // Beta 剪枝
            }
            if (score > curAlpha) {
                curAlpha = score
            }
        }

        return curAlpha
    }

    /**
     * 静态评估函数
     */
    fun evaluate(board: XiangqiBoard, mySide: PlayerSide): Int {
        var myMaterial = 0
        var oppMaterial = 0

        for (y in 0..9) {
            for (x in 0..8) {
                val piece = board[x, y] ?: continue
                var valScore = PIECE_VALUES[piece.type] ?: 0

                // 兵卒过河奖励
                if (piece.type == XiangqiPieceType.PAWN) {
                    val crossed = if (piece.side == PlayerSide.RED) y <= 4 else y >= 5
                    if (crossed) valScore += PAWN_ACROSS_RIVER_BONUS
                }

                // 灵活性与中心控制微小加分
                val centerDist = kotlin.math.abs(x - 4)
                valScore += (4 - centerDist) * 3

                if (piece.side == mySide) {
                    myMaterial += valScore
                } else {
                    oppMaterial += valScore
                }
            }
        }

        return myMaterial - oppMaterial
    }
}
