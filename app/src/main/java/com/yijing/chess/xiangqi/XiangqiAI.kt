package com.yijing.chess.xiangqi

import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class AIDecision(
    val move: MoveRecord?,
    val score: Int
)

object XiangqiAI {

    private val PIECE_VALUES = mapOf(
        XiangqiPieceType.KING to 10000,
        XiangqiPieceType.ROOK to 950,
        XiangqiPieceType.CANNON to 500,
        XiangqiPieceType.KNIGHT to 420,
        XiangqiPieceType.BISHOP to 220,
        XiangqiPieceType.ADVISOR to 220,
        XiangqiPieceType.PAWN to 100
    )

    // 马的位置价值表 (鼓励跃向中心控制楚河，避免被边线蹩腿)
    private val KNIGHT_PST = arrayOf(
        intArrayOf( 0, -4,  0,  4,  0,  4,  0, -4,  0),
        intArrayOf(-4,  4,  8, 12, 10, 12,  8,  4, -4),
        intArrayOf( 4,  8, 16, 20, 18, 20, 16,  8,  4),
        intArrayOf( 6, 12, 18, 24, 22, 24, 18, 12,  6),
        intArrayOf( 8, 14, 20, 26, 24, 26, 20, 14,  8),
        intArrayOf( 8, 14, 20, 26, 24, 26, 20, 14,  8),
        intArrayOf( 6, 12, 18, 24, 22, 24, 18, 12,  6),
        intArrayOf( 4,  8, 16, 20, 18, 20, 16,  8,  4),
        intArrayOf(-4,  4,  8, 12, 10, 12,  8,  4, -4),
        intArrayOf( 0, -4,  0,  4,  0,  4,  0, -4,  0)
    )

    /**
     * 寻找最佳走法 (绝不修改入参 board，确保界面绝不发生晃动或闪烁)
     */
    suspend fun findBestMove(
        board: XiangqiBoard,
        side: PlayerSide,
        difficulty: Int = 2
    ): AIDecision = withContext(Dispatchers.Default) {
        val searchBoard = board.copy()
        val legalMoves = XiangqiRule.getLegalMoves(searchBoard, side)
        if (legalMoves.isEmpty()) {
            return@withContext AIDecision(null, if (XiangqiRule.isKingInCheck(searchBoard, side)) -9999 else 0)
        }

        // 难度设置: 1=入门 (depth 2, 略带随机), 2=进阶 (depth 3, PST位置表), 3=大师 (depth 4, 静态吃子搜索)
        val searchDepth = when (difficulty) {
            1 -> 2
            2 -> 3
            else -> 4
        }
        val useQuiescence = difficulty >= 3

        // 走法排序：优先吃大子，提升 Alpha-Beta 剪枝效率
        val sortedMoves = legalMoves.sortedByDescending { move ->
            val captured = searchBoard[move.to]
            val victimVal = if (captured != null) (PIECE_VALUES[captured.type] ?: 0) * 10 else 0
            val moverVal = PIECE_VALUES[searchBoard[move.from]?.type] ?: 0
            victimVal - moverVal / 10
        }

        var bestMove: MoveRecord = sortedMoves.first()
        var bestScore = Int.MIN_VALUE + 200
        val alpha = Int.MIN_VALUE + 200
        val beta = Int.MAX_VALUE - 200

        val evaluatedCandidates = mutableListOf<Pair<MoveRecord, Int>>()

        for (move in sortedMoves) {
            currentCoroutineContext().ensureActive()

            val movedPiece = searchBoard[move.from]!!
            val capturedPiece = searchBoard[move.to]

            searchBoard[move.to] = movedPiece
            searchBoard[move.from] = null
            searchBoard.turn = side.xiangqiOpposite

            val score = -alphaBeta(
                board = searchBoard,
                currentSide = side.xiangqiOpposite,
                depth = searchDepth - 1,
                alpha = -beta,
                beta = -bestScore,
                maximizingSide = side,
                useQuiescence = useQuiescence
            )

            searchBoard[move.from] = movedPiece
            searchBoard[move.to] = capturedPiece
            searchBoard.turn = side

            evaluatedCandidates.add(Pair(move, score))

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        // 入门难度：在前 3 个候选步中带适度随机，增加对弈趣味
        if (difficulty == 1 && evaluatedCandidates.size > 1) {
            evaluatedCandidates.sortByDescending { it.second }
            val goodPicks = evaluatedCandidates.take(minOf(3, evaluatedCandidates.size))
            // 80% 选最高分，20% 选次优
            if (Random.nextFloat() < 0.25f && goodPicks.size > 1) {
                return@withContext AIDecision(goodPicks[1].first, goodPicks[1].second)
            }
        }

        AIDecision(bestMove, bestScore)
    }

    private suspend fun alphaBeta(
        board: XiangqiBoard,
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

        val legalMoves = XiangqiRule.getLegalMoves(board, currentSide)
        if (legalMoves.isEmpty()) {
            return if (XiangqiRule.isKingInCheck(board, currentSide)) {
                if (currentSide == maximizingSide) -9000 - depth else 9000 + depth
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
                maximizingSide = maximizingSide,
                useQuiescence = useQuiescence
            )

            board[move.from] = movedPiece
            board[move.to] = capturedPiece
            board.turn = currentSide

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
     * 彻底解决地平线效应 (防止 AI 为吃一个小兵而丢弃车马炮大子)
     */
    private fun quiescenceSearch(
        board: XiangqiBoard,
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

        val legalMoves = XiangqiRule.getLegalMoves(board, currentSide)
        // 仅搜索吃子走法
        val captureMoves = legalMoves.filter { board[it.to] != null }.sortedByDescending { move ->
            val captured = board[move.to]!!
            (PIECE_VALUES[captured.type] ?: 0) * 10 - (PIECE_VALUES[board[move.from]?.type] ?: 0)
        }

        for (move in captureMoves) {
            val movedPiece = board[move.from]!!
            val capturedPiece = board[move.to]

            board[move.to] = movedPiece
            board[move.from] = null
            board.turn = currentSide.xiangqiOpposite

            val score = -quiescenceSearch(
                board = board,
                currentSide = currentSide.xiangqiOpposite,
                alpha = -beta,
                beta = -curAlpha,
                maximizingSide = maximizingSide,
                qDepth = qDepth - 1
            )

            board[move.from] = movedPiece
            board[move.to] = capturedPiece
            board.turn = currentSide

            if (score >= beta) return beta
            if (score > curAlpha) curAlpha = score
        }

        return curAlpha
    }

    /**
     * 局面精细评估函数
     */
    fun evaluate(board: XiangqiBoard, mySide: PlayerSide): Int {
        var myMaterial = 0
        var oppMaterial = 0

        for (y in 0..9) {
            for (x in 0..8) {
                val piece = board[x, y] ?: continue
                var valScore = PIECE_VALUES[piece.type] ?: 0

                // 1. 兵卒过河与贴身逼近奖励
                if (piece.type == XiangqiPieceType.PAWN) {
                    val crossed = if (piece.side == PlayerSide.RED) y <= 4 else y >= 5
                    if (crossed) {
                        valScore += 120
                        // 深入敌阵九宫前沿大幅加分
                        val distToEnemyPalace = if (piece.side == PlayerSide.RED) y else 9 - y
                        valScore += (5 - distToEnemyPalace) * 20
                        // 中心控制加分
                        val centerDist = kotlin.math.abs(x - 4)
                        valScore += (4 - centerDist) * 15
                    }
                }

                // 2. 马的位置表 (越居中战力越强)
                if (piece.type == XiangqiPieceType.KNIGHT) {
                    val pstRow = if (piece.side == PlayerSide.RED) 9 - y else y
                    valScore += KNIGHT_PST[pstRow][x]
                }

                // 3. 车占通头与过河威慑
                if (piece.type == XiangqiPieceType.ROOK) {
                    val crossed = if (piece.side == PlayerSide.RED) y <= 4 else y >= 5
                    if (crossed) valScore += 35
                    // 巡河车/骑河车加分 (红方 y=5,4; 黑方 y=4,5)
                    if (y in 4..5) valScore += 25
                }

                // 4. 炮占中路或沉底
                if (piece.type == XiangqiPieceType.CANNON) {
                    if (x == 4) valScore += 40 // 当头炮中心威慑
                    val bottomRow = if (piece.side == PlayerSide.RED) 0 else 9
                    if (y == bottomRow) valScore += 30 // 沉底炮
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
