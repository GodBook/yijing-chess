package com.yijing.chess.go

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

object GoAI {

    /**
     * 为指定方选择最佳落子点 (若无好步则返回 null 表示停着 Pass)
     * @param difficulty 1: 初级入门, 2: 中级进阶, 3: 大师挑战
     */
    suspend fun findBestMove(
        board: GoBoard,
        side: PlayerSide,
        difficulty: Int = 2
    ): BoardCoordinate? = withContext(Dispatchers.Default) {
        val size = board.size
        val opponent = side.opposite()

        // 1. 战术检测：优先提吃处于“叫吃 (Atari)”状态的敌方棋子
        val captureCandidate = findAtariCaptureMove(board, side, opponent)
        if (captureCandidate != null) {
            // 大师与中级必吃，初级 85% 概率吃
            if (difficulty >= 2 || Random.nextFloat() < 0.85f) {
                return@withContext captureCandidate
            }
        }

        // 2. 战术检测：救援处于“叫吃”状态的己方棋子 (若能成功逃脱)
        val rescueCandidate = findRescueMove(board, side)
        if (rescueCandidate != null) {
            if (difficulty >= 2 || Random.nextFloat() < 0.8f) {
                return@withContext rescueCandidate
            }
        }

        // 3. 战术检测：双叫吃 (Double Atari) 制造者
        if (difficulty >= 2) {
            val doubleAtariMove = findDoubleAtariMove(board, side, opponent)
            if (doubleAtariMove != null) {
                return@withContext doubleAtariMove
            }
        }

        // 4. 开局定式/占角守角启发式候选 (星位、小目、天元)
        if (board.blackPrisoners + board.whitePrisoners < 6) {
            val cornerMoves = getCornerCandidates(size)
            val validCorners = cornerMoves.filter { board.isMoveLegal(it, side) && board[it] == null }
            if (validCorners.isNotEmpty()) {
                // 棋盘上如果角部大部分未占，优先占角
                val occupiedCorners = cornerMoves.count { board[it] != null }
                if (occupiedCorners < cornerMoves.size * 0.6f) {
                    return@withContext validCorners.random()
                }
            }
        }

        // 5. 遍历全盘合法落子点进行综合战术评估
        val candidates = mutableListOf<Pair<BoardCoordinate, Int>>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                currentCoroutineContext().ensureActive()
                val coord = BoardCoordinate(x, y)
                if (board.isMoveLegal(coord, side)) {
                    val score = evaluateGoMove(board, coord, side, difficulty)
                    if (score > -30) {
                        candidates.add(Pair(coord, score))
                    }
                }
            }
        }

        if (candidates.isEmpty()) return@withContext null

        candidates.sortByDescending { it.second }

        // 6. 大师难度：2-Ply 局部深度反击推演 (Minimax 1步对抗模拟)
        if (difficulty >= 3 && candidates.size > 1) {
            val topPicks = candidates.take(minOf(6, candidates.size))
            var bestTacticalMove = topPicks.first().first
            var bestTacticalScore = Int.MIN_VALUE

            for ((candMove, baseScore) in topPicks) {
                currentCoroutineContext().ensureActive()
                val testBoard = board.copy()
                testBoard.playMove(candMove, side)

                // 模拟对手在局部的最强反击
                var opponentMaxGain = 0
                val oppNeighbors = testBoard.getNeighbors(candMove)
                for (oppReply in oppNeighbors) {
                    if (testBoard.isMoveLegal(oppReply, opponent)) {
                        val oppScore = evaluateGoMove(testBoard, oppReply, opponent, difficulty = 2)
                        if (oppScore > opponentMaxGain) {
                            opponentMaxGain = oppScore
                        }
                    }
                }

                val netScore = baseScore - (opponentMaxGain * 0.6f).toInt()
                if (netScore > bestTacticalScore) {
                    bestTacticalScore = netScore
                    bestTacticalMove = candMove
                }
            }
            return@withContext bestTacticalMove
        }

        // 初级难度：前 4 名随机选择，增加活泼度与容错空间
        if (difficulty == 1) {
            val topFew = candidates.take(minOf(4, candidates.size))
            return@withContext topFew.random().first
        }

        // 中级难度：前 2 名中以 85% 选最优
        val topTwo = candidates.take(minOf(2, candidates.size))
        return@withContext if (Random.nextFloat() < 0.85f || topTwo.size == 1) topTwo.first().first else topTwo[1].first
    }

    private fun findAtariCaptureMove(
        board: GoBoard,
        side: PlayerSide,
        opponent: PlayerSide
    ): BoardCoordinate? {
        val checkedGroups = mutableSetOf<BoardCoordinate>()
        var bestCapture: BoardCoordinate? = null
        var maxVictimSize = 0

        for (y in 0 until board.size) {
            for (x in 0 until board.size) {
                val coord = BoardCoordinate(x, y)
                if (board[coord] == opponent && !checkedGroups.contains(coord)) {
                    val (group, liberties) = board.getGroupAndLiberties(coord)
                    checkedGroups.addAll(group)
                    if (liberties.size == 1) {
                        val targetLib = liberties.first()
                        if (board.isMoveLegal(targetLib, side) && group.size > maxVictimSize) {
                            maxVictimSize = group.size
                            bestCapture = targetLib
                        }
                    }
                }
            }
        }
        return bestCapture
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

    private fun findDoubleAtariMove(
        board: GoBoard,
        side: PlayerSide,
        opponent: PlayerSide
    ): BoardCoordinate? {
        for (y in 0 until board.size) {
            for (x in 0 until board.size) {
                val coord = BoardCoordinate(x, y)
                if (board[coord] == null && board.isMoveLegal(coord, side)) {
                    val testBoard = board.copy()
                    testBoard.playMove(coord, side)
                    var atariGroupCount = 0
                    val seenGroups = mutableSetOf<BoardCoordinate>()
                    for (neighbor in testBoard.getNeighbors(coord)) {
                        if (testBoard[neighbor] == opponent && !seenGroups.contains(neighbor)) {
                            val (grp, libs) = testBoard.getGroupAndLiberties(neighbor)
                            seenGroups.addAll(grp)
                            if (libs.size == 1) {
                                atariGroupCount++
                            }
                        }
                    }
                    if (atariGroupCount >= 2) {
                        return coord
                    }
                }
            }
        }
        return null
    }

    private fun evaluateGoMove(
        board: GoBoard,
        coord: BoardCoordinate,
        side: PlayerSide,
        difficulty: Int
    ): Int {
        val testBoard = board.copy()
        val captured = testBoard.playMove(coord, side) ?: return -100

        var score = 10 + captured.size * 60

        val (group, liberties) = testBoard.getGroupAndLiberties(coord)

        // 避免自寻死路 (落子后仅剩1气，无提子却被反叫吃)
        if (liberties.size <= 1 && captured.isEmpty()) {
            return -80
        }

        score += liberties.size * 10

        // 友军连接与切断价值 (长、扳、接、断)
        val opponent = side.opposite()
        var friendlyNeighbors = 0
        var opponentNeighbors = 0
        for (neighbor in board.getNeighbors(coord)) {
            when (board[neighbor]) {
                side -> friendlyNeighbors++
                opponent -> opponentNeighbors++
                else -> {}
            }
        }

        if (friendlyNeighbors >= 1) score += friendlyNeighbors * 12
        if (opponentNeighbors >= 1) score += opponentNeighbors * 8

        // 做眼识别：若周围有己方包围形成的眼位并提供保护
        if (friendlyNeighbors >= 3 && liberties.size >= 2) {
            score += 25
        }

        // 金角银边草肚皮位置权重
        val edgeDistX = minOf(coord.x, board.size - 1 - coord.x)
        val edgeDistY = minOf(coord.y, board.size - 1 - coord.y)
        if ((edgeDistX in 2..3) && (edgeDistY in 2..3)) {
            score += 30 // 三路/四路金角银边
        } else if (edgeDistX == 1 || edgeDistY == 1) {
            score += 15 // 二路渡过
        } else if (edgeDistX == 0 || edgeDistY == 0) {
            score -= 20 // 一路爬行减分 (除非提吃)
            if (captured.isNotEmpty()) score += 30
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
