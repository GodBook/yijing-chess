package com.yijing.chess.go

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

object GoAI {

    /**
     * 为指定方选择最佳落子点 (若无好步则返回 null 表示停着 Pass)
     */
    suspend fun findBestMove(
        board: GoBoard,
        side: PlayerSide
    ): BoardCoordinate? = withContext(Dispatchers.Default) {
        val size = board.size
        val opponent = side.opposite()

        // 1. 战术检测：优先提吃处于“叫吃 (Atari)”状态的敌方棋子
        val captureCandidate = findAtariCaptureMove(board, side, opponent)
        if (captureCandidate != null) return@withContext captureCandidate

        // 2. 战术检测：救援处于“叫吃”状态的己方棋子
        val rescueCandidate = findRescueMove(board, side)
        if (rescueCandidate != null) return@withContext rescueCandidate

        // 3. 开局定式/金角银边启发式候选 (星位与小目)
        val cornerMoves = getCornerCandidates(size)
        val validCorners = cornerMoves.filter { board.isMoveLegal(it, side) && board[it] == null }
        if (validCorners.isNotEmpty()) {
            return@withContext validCorners.random()
        }

        // 4. 评估全盘合法落子点
        val candidates = mutableListOf<Pair<BoardCoordinate, Int>>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val coord = BoardCoordinate(x, y)
                if (board.isMoveLegal(coord, side)) {
                    val score = evaluateGoMove(board, coord, side)
                    if (score > 0) {
                        candidates.add(Pair(coord, score))
                    }
                }
            }
        }

        if (candidates.isEmpty()) return@withContext null

        // 按得分降序取前 3 名，加微量随机增加棋局生动性
        candidates.sortByDescending { it.second }
        val topPicks = candidates.take(3)
        return@withContext topPicks.random().first
    }

    private fun findAtariCaptureMove(
        board: GoBoard,
        side: PlayerSide,
        opponent: PlayerSide
    ): BoardCoordinate? {
        val checkedGroups = mutableSetOf<BoardCoordinate>()
        for (y in 0 until board.size) {
            for (x in 0 until board.size) {
                val coord = BoardCoordinate(x, y)
                if (board[coord] == opponent && !checkedGroups.contains(coord)) {
                    val (group, liberties) = board.getGroupAndLiberties(coord)
                    checkedGroups.addAll(group)
                    if (liberties.size == 1) {
                        val targetLib = liberties.first()
                        if (board.isMoveLegal(targetLib, side)) {
                            return targetLib
                        }
                    }
                }
            }
        }
        return null
    }

    private fun findRescueMove(
        board: GoBoard,
        side: PlayerSide
    ): BoardCoordinate? {
        val checkedGroups = mutableSetOf<BoardCoordinate>()
        for (y in 0 until board.size) {
            for (x in 0 until board.size) {
                val coord = BoardCoordinate(x, y)
                if (board[coord] == side && !checkedGroups.contains(coord)) {
                    val (group, liberties) = board.getGroupAndLiberties(coord)
                    checkedGroups.addAll(group)
                    if (liberties.size == 1) {
                        val rescueSpot = liberties.first()
                        if (board.isMoveLegal(rescueSpot, side)) {
                            // 检查逃跑后是否真能增加气数 (避免自填气送吃)
                            val testBoard = board.copy()
                            testBoard.playMove(rescueSpot, side)
                            val (_, newLibs) = testBoard.getGroupAndLiberties(rescueSpot)
                            if (newLibs.size >= 2) {
                                return rescueSpot
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun evaluateGoMove(
        board: GoBoard,
        coord: BoardCoordinate,
        side: PlayerSide
    ): Int {
        val testBoard = board.copy()
        val captured = testBoard.playMove(coord, side) ?: return -100

        var score = 10 + captured.size * 50

        val (_, liberties) = testBoard.getGroupAndLiberties(coord)
        // 避免自寻死路 (落子后仅剩1气被反叫吃)
        if (liberties.size <= 1) {
            return -50
        }
        score += liberties.size * 8

        // 金角银边草肚皮位置权重 (距离边线 2~3 格最佳)
        val edgeDistX = minOf(coord.x, board.size - 1 - coord.x)
        val edgeDistY = minOf(coord.y, board.size - 1 - coord.y)
        if ((edgeDistX in 2..3) && (edgeDistY in 2..3)) {
            score += 25
        } else if (edgeDistX == 0 || edgeDistY == 0) {
            score -= 15 // 一路爬行减分
        }

        return score
    }

    private fun getCornerCandidates(size: Int): List<BoardCoordinate> {
        return when (size) {
            19 -> listOf(
                BoardCoordinate(3, 3), BoardCoordinate(15, 3),
                BoardCoordinate(3, 15), BoardCoordinate(15, 15),
                BoardCoordinate(3, 2), BoardCoordinate(2, 3),
                BoardCoordinate(15, 2), BoardCoordinate(16, 3),
                BoardCoordinate(9, 9) // 天元
            )
            13 -> listOf(
                BoardCoordinate(3, 3), BoardCoordinate(9, 3),
                BoardCoordinate(3, 9), BoardCoordinate(9, 9),
                BoardCoordinate(6, 6)
            )
            9 -> listOf(
                BoardCoordinate(2, 2), BoardCoordinate(6, 2),
                BoardCoordinate(2, 6), BoardCoordinate(6, 6),
                BoardCoordinate(4, 4)
            )
            else -> emptyList()
        }
    }
}
