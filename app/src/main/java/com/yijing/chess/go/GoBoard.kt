package com.yijing.chess.go

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import java.util.ArrayDeque

/**
 * 围棋棋盘与规则计算引擎
 * 支持 19x19, 13x13, 9x9 规格
 */
class GoBoard(
    val size: Int = 19,
    val grid: Array<Array<PlayerSide?>> = Array(size) { arrayOfNulls<PlayerSide>(size) },
    var turn: PlayerSide = PlayerSide.BLACK,
    var lastCapturedCoord: BoardCoordinate? = null, // 单子打劫劫争点
    var consecutivePasses: Int = 0
) {
    var blackPrisoners: Int = 0 // 黑棋提子数 (提掉白子的数量)
    var whitePrisoners: Int = 0 // 白棋提子数 (提掉黑子的数量)

    fun copy(): GoBoard {
        val newGrid = Array(size) { y ->
            Array(size) { x -> grid[y][x] }
        }
        val copyBoard = GoBoard(size, newGrid, turn, lastCapturedCoord, consecutivePasses)
        copyBoard.blackPrisoners = blackPrisoners
        copyBoard.whitePrisoners = whitePrisoners
        return copyBoard
    }

    operator fun get(coord: BoardCoordinate): PlayerSide? = get(coord.x, coord.y)

    operator fun get(x: Int, y: Int): PlayerSide? {
        if (x !in 0 until size || y !in 0 until size) return null
        return grid[y][x]
    }

    operator fun set(coord: BoardCoordinate, side: PlayerSide?) {
        set(coord.x, coord.y, side)
    }

    operator fun set(x: Int, y: Int, side: PlayerSide?) {
        if (x in 0 until size && y in 0 until size) {
            grid[y][x] = side
        }
    }

    fun getNeighbors(coord: BoardCoordinate): List<BoardCoordinate> {
        val list = mutableListOf<BoardCoordinate>()
        val deltas = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
        for ((dx, dy) in deltas) {
            val nx = coord.x + dx
            val ny = coord.y + dy
            if (nx in 0 until size && ny in 0 until size) {
                list.add(BoardCoordinate(nx, ny))
            }
        }
        return list
    }

    /**
     * 计算一块连通棋子群的所有棋子坐标与总气数
     */
    fun getGroupAndLiberties(start: BoardCoordinate): Pair<Set<BoardCoordinate>, Set<BoardCoordinate>> {
        val side = get(start) ?: return Pair(emptySet(), emptySet())
        val group = mutableSetOf<BoardCoordinate>()
        val liberties = mutableSetOf<BoardCoordinate>()
        val queue = ArrayDeque<BoardCoordinate>()

        queue.add(start)
        group.add(start)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            for (neighbor in getNeighbors(curr)) {
                val neighborSide = get(neighbor)
                if (neighborSide == null) {
                    liberties.add(neighbor)
                } else if (neighborSide == side && !group.contains(neighbor)) {
                    group.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }

        return Pair(group, liberties)
    }

    /**
     * 判断在 (x,y) 处落子是否合法
     */
    fun isMoveLegal(coord: BoardCoordinate, side: PlayerSide): Boolean {
        if (get(coord) != null) return false // 已有子

        // 劫争禁着判定
        if (lastCapturedCoord != null && lastCapturedCoord == coord) {
            return false
        }

        // 试探落子
        set(coord, side)
        val opponent = side.opposite()
        val opponentNeighbors = getNeighbors(coord).filter { get(it) == opponent }

        var canCapture = false
        for (opp in opponentNeighbors) {
            val (_, oppLiberties) = getGroupAndLiberties(opp)
            if (oppLiberties.isEmpty()) {
                canCapture = true
                break
            }
        }

        val (_, myLiberties) = getGroupAndLiberties(coord)
        val hasOwnLiberties = myLiberties.isNotEmpty()

        // 恢复局面
        set(coord, null)

        // 若能提对方子，或自身有气，则合法；否则为自杀禁着点
        return canCapture || hasOwnLiberties
    }

    /**
     * 执行落子并处理提子与打劫逻辑
     * @return 提掉对方的棋子集合
     */
    fun playMove(coord: BoardCoordinate, side: PlayerSide): Set<BoardCoordinate>? {
        if (!isMoveLegal(coord, side)) return null

        set(coord, side)
        val opponent = side.opposite()
        val capturedStones = mutableSetOf<BoardCoordinate>()

        for (neighbor in getNeighbors(coord)) {
            if (get(neighbor) == opponent) {
                val (group, liberties) = getGroupAndLiberties(neighbor)
                if (liberties.isEmpty()) {
                    capturedStones.addAll(group)
                }
            }
        }

        // 移除死子
        for (stone in capturedStones) {
            set(stone, null)
        }

        // 统计提子
        if (side == PlayerSide.BLACK) {
            blackPrisoners += capturedStones.size
        } else {
            whitePrisoners += capturedStones.size
        }

        // 判断是否形成单子打劫局面
        if (capturedStones.size == 1) {
            val (_, myLiberties) = getGroupAndLiberties(coord)
            if (myLiberties.size == 1) {
                lastCapturedCoord = capturedStones.first()
            } else {
                lastCapturedCoord = null
            }
        } else {
            lastCapturedCoord = null
        }

        consecutivePasses = 0
        turn = opponent
        return capturedStones
    }

    fun pass(): Boolean {
        consecutivePasses++
        turn = turn.opposite()
        lastCapturedCoord = null
        return consecutivePasses >= 2 // 双方停着，进入终局
    }

    /**
     * 生成标准 SGF 坐标，如 (3,15) -> "dp"
     */
    fun toSgfCoord(coord: BoardCoordinate): String {
        val col = ('a'.code + coord.x).toChar()
        val row = ('a'.code + coord.y).toChar()
        return "$col$row"
    }

    /**
     * 导出为紧凑状态字符串 (用于对局保存)
     */
    fun toStateString(): String {
        val sb = StringBuilder(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                sb.append(when (grid[y][x]) {
                    PlayerSide.BLACK -> 'B'
                    PlayerSide.WHITE -> 'W'
                    else -> '.'
                })
            }
        }
        return sb.toString()
    }

    companion object {
        fun fromSgfCoord(sgf: String): BoardCoordinate? {
            if (sgf.length < 2) return null
            val x = sgf[0] - 'a'
            val y = sgf[1] - 'a'
            return BoardCoordinate(x, y)
        }

        fun fromStateString(
            size: Int,
            state: String,
            turn: PlayerSide,
            blackPrisoners: Int = 0,
            whitePrisoners: Int = 0
        ): GoBoard {
            val board = GoBoard(size = size, turn = turn)
            board.blackPrisoners = blackPrisoners
            board.whitePrisoners = whitePrisoners
            var idx = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (idx < state.length) {
                        board[x, y] = when (state[idx]) {
                            'B' -> PlayerSide.BLACK
                            'W' -> PlayerSide.WHITE
                            else -> null
                        }
                        idx++
                    }
                }
            }
            return board
        }
    }
}
