package com.yijing.chess.model

/**
 * 棋类类型
 */
enum class GameType(val displayName: String, val englishName: String) {
    XIANGQI("中国象棋", "Xiangqi"),
    GO("围棋", "Go / Weiqi"),
    CHESS("国际象棋", "Chess")
}

/**
 * 对局模式
 */
enum class GameMode(val displayName: String) {
    PVE("人机对战"),
    PVP("双人同屏"),
    EDITOR("自由摆谱"),
    REPLAY("打谱复盘")
}

/**
 * 棋子阵营/双方
 */
enum class PlayerSide(val displayName: String) {
    RED("红方"),
    BLACK("黑方"),
    WHITE("白方");

    fun opposite(forXiangqi: Boolean = false): PlayerSide = when (this) {
        RED -> BLACK
        WHITE -> BLACK
        BLACK -> if (forXiangqi) RED else WHITE
    }

    val xiangqiOpposite: PlayerSide
        get() = if (this == RED) BLACK else RED
}

/**
 * 对局状态
 */
sealed class GameStatus {
    object Playing : GameStatus()
    data class Check(val inCheckSide: PlayerSide) : GameStatus()
    data class Checkmate(val winner: PlayerSide) : GameStatus()
    data class Stalemate(val reason: String) : GameStatus()
    data class Win(val winner: PlayerSide, val reason: String) : GameStatus()
    data class Draw(val reason: String) : GameStatus()
}

/**
 * 统一棋盘坐标系 (0-indexed)
 * x: 水平列 (从左到右 0..columns-1)
 * y: 垂直行 (从上到下 0..rows-1)
 */
data class BoardCoordinate(val x: Int, val y: Int) {
    override fun toString(): String = "($x,$y)"
}
