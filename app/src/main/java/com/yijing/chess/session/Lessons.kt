package com.yijing.chess.session

import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.*
import com.yijing.chess.xiangqi.XiangqiBoard

data class Lesson(val id: String, val type: GameType, val title: String, val description: String,
    val explanation: String, val position: PositionSnapshot, val from: BoardCoordinate, val to: BoardCoordinate) {
    fun session() = GameSession.create(type, GameMode.PVP, position.boardSize, position).copy(lessonId = id, title = title)
}

object Lessons {
    val all: List<Lesson> by lazy {
        val go = GoBoard(9).apply {
            this[4, 4] = PlayerSide.WHITE
            this[4, 3] = PlayerSide.BLACK; this[4, 5] = PlayerSide.BLACK; this[3, 4] = PlayerSide.BLACK
        }
        listOf(
            Lesson("chess-mate-one", GameType.CHESS, "后与王的配合", "白方走，一步将死黑王。", "Qg7#：白王保护白后，后封住黑王的所有退路。",
                PositionSnapshot.of(ChessBoard.fromFen("7k/8/5KQ1/8/8/8/8/8 w - - 0 1")), BoardCoordinate(6, 2), BoardCoordinate(6, 1)),
            Lesson("xiangqi-rook-capture", GameType.XIANGQI, "车的直线进攻", "红方走，用车吃掉同一路上的黑炮。", "车可以沿直线走任意格数，中间没有棋子时可以直接吃掉黑炮。",
                PositionSnapshot.of(XiangqiBoard.fromFen("4k4/9/c8/9/9/4P4/9/R8/9/4K4 w - - 0 1")), BoardCoordinate(0, 7), BoardCoordinate(0, 2)),
            Lesson("go-last-liberty", GameType.GO, "找到最后一口气", "黑方走，提掉中央的白子。", "白子只剩右边一口气。落在这点后，白子无气，被提离棋盘。",
                PositionSnapshot.of(go), BoardCoordinate(5, 4), BoardCoordinate(5, 4))
        )
    }
}
