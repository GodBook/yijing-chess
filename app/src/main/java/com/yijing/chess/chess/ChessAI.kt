package com.yijing.chess.chess

import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChessAIDecision(
    val move: MoveRecord?,
    val score: Int
)

object ChessAI {

    private val PIECE_VALUES = mapOf(
        ChessPieceType.KING to 20000,
        ChessPieceType.QUEEN to 900,
        ChessPieceType.ROOK to 500,
        ChessPieceType.BISHOP to 330,
        ChessPieceType.KNIGHT to 320,
        ChessPieceType.PAWN to 100
    )

    suspend fun findBestMove(
        board: ChessBoard,
        side: PlayerSide,
        depth: Int = 3
    ): ChessAIDecision = withContext(Dispatchers.Default) {
        val legalMoves = ChessRule.getLegalMoves(board, side)
        if (legalMoves.isEmpty()) {
            return@withContext ChessAIDecision(null, if (ChessRule.isKingInCheck(board, side)) -19000 else 0)
        }

        val sortedMoves = legalMoves.sortedByDescending { move ->
            val captured = board[move.to]
            if (captured != null) (PIECE_VALUES[captured.type] ?: 0) * 10 else 0
        }

        var bestMove = sortedMoves.first()
        var bestScore = Int.MIN_VALUE + 100
        val alpha = Int.MIN_VALUE + 100
        val beta = Int.MAX_VALUE - 100

        for (move in sortedMoves) {
            val promoType = move.promotion?.let { runCatching { ChessPieceType.valueOf(it) }.getOrNull() }
            val cand = ChessRule.CandidateChessMove(move.from, move.to, promoType)
            val testBoard = board.copy()
            ChessRule.applyMove(testBoard, cand)

            val score = -alphaBeta(
                board = testBoard,
                currentSide = side.opposite(),
                depth = depth - 1,
                alpha = -beta,
                beta = -bestScore,
                maximizingSide = side
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        ChessAIDecision(bestMove, bestScore)
    }

    private fun alphaBeta(
        board: ChessBoard,
        currentSide: PlayerSide,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximizingSide: PlayerSide
    ): Int {
        if (depth <= 0) {
            return evaluate(board, maximizingSide)
        }

        val legalMoves = ChessRule.getLegalMoves(board, currentSide)
        if (legalMoves.isEmpty()) {
            return if (ChessRule.isKingInCheck(board, currentSide)) {
                if (currentSide == maximizingSide) -19000 - depth else 19000 + depth
            } else {
                0
            }
        }

        var curAlpha = alpha
        val sortedMoves = legalMoves.sortedByDescending { move ->
            val captured = board[move.to]
            if (captured != null) (PIECE_VALUES[captured.type] ?: 0) * 10 else 0
        }

        for (move in sortedMoves) {
            val promoType = move.promotion?.let { runCatching { ChessPieceType.valueOf(it) }.getOrNull() }
            val cand = ChessRule.CandidateChessMove(move.from, move.to, promoType)
            val testBoard = board.copy()
            ChessRule.applyMove(testBoard, cand)

            val score = -alphaBeta(
                board = testBoard,
                currentSide = currentSide.opposite(),
                depth = depth - 1,
                alpha = -beta,
                beta = -curAlpha,
                maximizingSide = maximizingSide
            )

            if (score >= beta) {
                return beta
            }
            if (score > curAlpha) {
                curAlpha = score
            }
        }

        return curAlpha
    }

    private fun evaluate(board: ChessBoard, mySide: PlayerSide): Int {
        var myMaterial = 0
        var oppMaterial = 0

        for (y in 0..7) {
            for (x in 0..7) {
                val piece = board[x, y] ?: continue
                var valScore = PIECE_VALUES[piece.type] ?: 0

                // 中心控制偏好 (d4, d5, e4, e5 附近加分)
                if (x in 3..4 && y in 3..4) {
                    valScore += 30
                } else if (x in 2..5 && y in 2..5) {
                    valScore += 15
                }

                // 兵向前推进一步奖励
                if (piece.type == ChessPieceType.PAWN) {
                    val rankDist = if (piece.side == PlayerSide.WHITE) 6 - y else y - 1
                    valScore += rankDist * 10
                }

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
