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
        difficulty: Int = 2
    ): ChessAIDecision = withContext(Dispatchers.Default) {
        val searchBoard = board.copy()
        val legalMoves = ChessRule.getLegalMoves(searchBoard, side)
        if (legalMoves.isEmpty()) {
            return@withContext ChessAIDecision(null, if (ChessRule.isKingInCheck(searchBoard, side)) -19000 else 0)
        }

        val searchDepth = when (difficulty) {
            1 -> 2
            2 -> 3
            else -> 4
        }
        val useQuiescence = difficulty >= 3

        val sortedMoves = legalMoves.sortedByDescending { move ->
            val captured = searchBoard[move.to]
            val victimVal = if (captured != null) (PIECE_VALUES[captured.type] ?: 0) * 10 else 0
            val moverVal = PIECE_VALUES[searchBoard[move.from]?.type] ?: 0
            victimVal - moverVal / 10
        }

        var bestMove = sortedMoves.first()
        var bestScore = Int.MIN_VALUE + 200
        val alpha = Int.MIN_VALUE + 200
        val beta = Int.MAX_VALUE - 200

        val evaluatedCandidates = mutableListOf<Pair<MoveRecord, Int>>()

        for (move in sortedMoves) {
            currentCoroutineContext().ensureActive()

            val promoType = move.promotion?.let { runCatching { ChessPieceType.valueOf(it) }.getOrNull() }
            val cand = ChessRule.CandidateChessMove(move.from, move.to, promoType)
            val testBoard = searchBoard.copy()
            ChessRule.applyMove(testBoard, cand)

            val score = -alphaBeta(
                board = testBoard,
                currentSide = side.opposite(),
                depth = searchDepth - 1,
                alpha = -beta,
                beta = -bestScore,
                maximizingSide = side,
                useQuiescence = useQuiescence
            )

            evaluatedCandidates.add(Pair(move, score))

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        if (difficulty == 1 && evaluatedCandidates.size > 1) {
            evaluatedCandidates.sortByDescending { it.second }
            val goodPicks = evaluatedCandidates.take(minOf(3, evaluatedCandidates.size))
            if (Random.nextFloat() < 0.25f && goodPicks.size > 1) {
                return@withContext ChessAIDecision(goodPicks[1].first, goodPicks[1].second)
            }
        }

        ChessAIDecision(bestMove, bestScore)
    }

    private suspend fun alphaBeta(
        board: ChessBoard,
        currentSide: PlayerSide,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximizingSide: PlayerSide,
        useQuiescence: Boolean
    ): Int {
        if (depth <= 0) {
            return if (useQuiescence) {
                quiescenceSearch(board, currentSide, alpha, beta, maximizingSide, qDepth = 2)
            } else {
                evaluate(board, maximizingSide)
            }
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
            val victimVal = if (captured != null) (PIECE_VALUES[captured.type] ?: 0) * 10 else 0
            val moverVal = PIECE_VALUES[board[move.from]?.type] ?: 0
            victimVal - moverVal / 10
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
                maximizingSide = maximizingSide,
                useQuiescence = useQuiescence
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

    /**
     * 静态吃子延伸搜索 (Quiescence Search)
     */
    private fun quiescenceSearch(
        board: ChessBoard,
        currentSide: PlayerSide,
        alpha: Int,
        beta: Int,
        maximizingSide: PlayerSide,
        qDepth: Int
    ): Int {
        val standPat = evaluate(board, maximizingSide)
        if (qDepth <= 0) return standPat

        var curAlpha = alpha
        if (standPat >= beta) return beta
        if (standPat > curAlpha) curAlpha = standPat

        val legalMoves = ChessRule.getLegalMoves(board, currentSide)
        val captureMoves = legalMoves.filter { board[it.to] != null }.sortedByDescending { move ->
            val victim = board[move.to]!!
            (PIECE_VALUES[victim.type] ?: 0) * 10 - (PIECE_VALUES[board[move.from]?.type] ?: 0)
        }

        for (move in captureMoves) {
            val promoType = move.promotion?.let { runCatching { ChessPieceType.valueOf(it) }.getOrNull() }
            val cand = ChessRule.CandidateChessMove(move.from, move.to, promoType)
            val testBoard = board.copy()
            ChessRule.applyMove(testBoard, cand)

            val score = -quiescenceSearch(
                board = testBoard,
                currentSide = currentSide.opposite(),
                alpha = -beta,
                beta = -curAlpha,
                maximizingSide = maximizingSide,
                qDepth = qDepth - 1
            )

            if (score >= beta) return beta
            if (score > curAlpha) curAlpha = score
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

                val isWhite = piece.side == PlayerSide.WHITE
                val pstRow = if (isWhite) 7 - y else y

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
