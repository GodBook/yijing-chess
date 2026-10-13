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
    val score: Int,
    val completedDepth: Int = 0,
    val searchedNodes: Long = 0,
    val elapsedMillis: Long = 0
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
        difficulty: Int = 2,
        budgetMillis: Long = 2500
    ): AIDecision = withContext(Dispatchers.Default) {
        val result = com.yijing.chess.engine.NegamaxSearch.find(board.copy(), side, difficulty, budgetMillis,
            object : com.yijing.chess.engine.SearchRules<XiangqiBoard> {
                override fun moves(board: XiangqiBoard, side: PlayerSide) = XiangqiRule.getLegalMoves(board, side)
                override fun after(board: XiangqiBoard, move: MoveRecord, side: PlayerSide) = board.copy().also {
                    it[move.to] = it[move.from]; it[move.from] = null; it.turn = side.xiangqiOpposite
                }
                override fun evaluate(board: XiangqiBoard, side: PlayerSide) = XiangqiAI.evaluate(board, side)
                override fun inCheck(board: XiangqiBoard, side: PlayerSide) = XiangqiRule.isKingInCheck(board, side)
                override fun opposite(side: PlayerSide) = side.xiangqiOpposite
                override val stalemateLoses = true
                override fun priority(board: XiangqiBoard, move: MoveRecord): Int =
                    (PIECE_VALUES[board[move.to]?.type] ?: 0) * 10 - (PIECE_VALUES[board[move.from]?.type] ?: 0)
            })
        AIDecision(result.move, result.score, result.depth, result.nodes, result.elapsedMillis)
    }
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
