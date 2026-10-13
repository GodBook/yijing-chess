package com.yijing.chess.chess

import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class ChessAIDecision(
    val move: MoveRecord?,
    val score: Int,
    val completedDepth: Int = 0,
    val searchedNodes: Long = 0,
    val elapsedMillis: Long = 0
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

    // 马的位置表 (Knight PST: 鼓励居中，惩罚边角 "A knight on the rim is dim")
    private val KNIGHT_PST = arrayOf(
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-30,  5, 20, 25, 25, 20,  5,-30),
        intArrayOf(-30,  5, 20, 25, 25, 20,  5,-30),
        intArrayOf(-30,  5, 15, 20, 20, 15,  5,-30),
        intArrayOf(-40,-20,  0,  5,  5,  0,-20,-40),
        intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50)
    )

    // 兵的位置表 (Pawn PST: 鼓励向前推进，占领中心)
    private val PAWN_PST = arrayOf(
        intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf( 50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf( 10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf(  5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf(  0,  0,  0, 20, 20,  0,  0,  0),
        intArrayOf(  5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf(  5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf(  0,  0,  0,  0,  0,  0,  0,  0)
    )

    // 王的位置表 (中局保护，鼓励王车易位后藏于角落)
    private val KING_PST = arrayOf(
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30),
        intArrayOf(-20,-30,-30,-40,-40,-30,-30,-20),
        intArrayOf(-10,-20,-20,-20,-20,-20,-20,-10),
        intArrayOf( 20, 20,  0,  0,  0,  0, 20, 20),
        intArrayOf( 20, 30, 10,  0,  0, 10, 30, 20)
    )

    suspend fun findBestMove(
        board: ChessBoard,
        side: PlayerSide,
        difficulty: Int = 2,
        budgetMillis: Long = 2500
    ): ChessAIDecision = withContext(Dispatchers.Default) {
        val result = com.yijing.chess.engine.NegamaxSearch.find(board.copy(), side, difficulty, budgetMillis,
            object : com.yijing.chess.engine.SearchRules<ChessBoard> {
                override fun moves(board: ChessBoard, side: PlayerSide) = ChessRule.getLegalMoves(board, side, includeNotation = false)
                override fun after(board: ChessBoard, move: MoveRecord, side: PlayerSide) = board.copy().also {
                    ChessRule.applyMove(it, ChessRule.CandidateChessMove(move.from, move.to, move.promotion?.let(ChessPieceType::valueOf)))
                }
                override fun evaluate(board: ChessBoard, side: PlayerSide) = ChessAI.evaluate(board, side)
                override fun inCheck(board: ChessBoard, side: PlayerSide) = ChessRule.isKingInCheck(board, side)
                override fun priority(board: ChessBoard, move: MoveRecord): Int =
                    (PIECE_VALUES[board[move.to]?.type] ?: if (move.capturedText != null) 100 else 0) * 10 -
                        (PIECE_VALUES[board[move.from]?.type] ?: 0) + if (move.promotion != null) 9000 else 0
            })
        val canonical = result.move?.let { best ->
            ChessRule.getLegalMoves(board, side).firstOrNull { it.from == best.from && it.to == best.to && it.promotion == best.promotion }
        }
        ChessAIDecision(canonical, result.score, result.depth, result.nodes, result.elapsedMillis)
    }

    fun evaluate(board: ChessBoard, mySide: PlayerSide): Int {
        var myMaterial = 0
        var oppMaterial = 0

        for (y in 0..7) {
            for (x in 0..7) {
                val piece = board[x, y] ?: continue
                var valScore = PIECE_VALUES[piece.type] ?: 0

                val isWhite = piece.side == PlayerSide.WHITE
                val pstRow = if (isWhite) y else 7 - y

                when (piece.type) {
                    ChessPieceType.KNIGHT -> {
                        valScore += KNIGHT_PST[pstRow][x]
                    }
                    ChessPieceType.PAWN -> {
                        valScore += PAWN_PST[pstRow][x]
                    }
                    ChessPieceType.KING -> {
                        valScore += KING_PST[pstRow][x]
                    }
                    ChessPieceType.ROOK -> {
                        // 车占通头或第7排 (白方 y=1, 黑方 y=6)
                        if ((isWhite && y == 1) || (!isWhite && y == 6)) {
                            valScore += 30
                        }
                    }
                    ChessPieceType.BISHOP -> {
                        // 象控制长斜线奖励
                        val distCenter = kotlin.math.abs(x - 3.5f) + kotlin.math.abs(y - 3.5f)
                        valScore += ((7 - distCenter) * 4).toInt()
                    }
                    else -> {}
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
