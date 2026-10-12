package com.yijing.chess.xiangqi

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide

/**
 * 中国象棋棋子类型
 */
enum class XiangqiPieceType {
    KING,       // 帅 / 将
    ADVISOR,    // 仕 / 士
    BISHOP,     // 相 / 象
    KNIGHT,     // 傌 / 馬
    ROOK,       // 俥 / 車
    CANNON,     // 砲 / 炮
    PAWN        // 兵 / 卒
}

/**
 * 象棋棋子
 */
data class XiangqiPiece(
    val side: PlayerSide,
    val type: XiangqiPieceType
) {
    val displayName: String
        get() = when (side) {
            PlayerSide.RED -> when (type) {
                XiangqiPieceType.KING -> "帅"
                XiangqiPieceType.ADVISOR -> "仕"
                XiangqiPieceType.BISHOP -> "相"
                XiangqiPieceType.KNIGHT -> "马"
                XiangqiPieceType.ROOK -> "车"
                XiangqiPieceType.CANNON -> "炮"
                XiangqiPieceType.PAWN -> "兵"
            }
            PlayerSide.BLACK -> when (type) {
                XiangqiPieceType.KING -> "将"
                XiangqiPieceType.ADVISOR -> "士"
                XiangqiPieceType.BISHOP -> "象"
                XiangqiPieceType.KNIGHT -> "马"
                XiangqiPieceType.ROOK -> "车"
                XiangqiPieceType.CANNON -> "炮"
                XiangqiPieceType.PAWN -> "卒"
            }
            else -> ""
        }

    val fenChar: Char
        get() = when (side) {
            PlayerSide.RED -> when (type) {
                XiangqiPieceType.KING -> 'K'
                XiangqiPieceType.ADVISOR -> 'A'
                XiangqiPieceType.BISHOP -> 'B'
                XiangqiPieceType.KNIGHT -> 'N'
                XiangqiPieceType.ROOK -> 'R'
                XiangqiPieceType.CANNON -> 'C'
                XiangqiPieceType.PAWN -> 'P'
            }
            PlayerSide.BLACK -> when (type) {
                XiangqiPieceType.KING -> 'k'
                XiangqiPieceType.ADVISOR -> 'a'
                XiangqiPieceType.BISHOP -> 'b'
                XiangqiPieceType.KNIGHT -> 'n'
                XiangqiPieceType.ROOK -> 'r'
                XiangqiPieceType.CANNON -> 'c'
                XiangqiPieceType.PAWN -> 'p'
            }
            else -> ' '
        }

    companion object {
        fun fromFenChar(c: Char): XiangqiPiece? {
            val side = if (c.isUpperCase()) PlayerSide.RED else PlayerSide.BLACK
            val type = when (c.lowercaseChar()) {
                'k' -> XiangqiPieceType.KING
                'a' -> XiangqiPieceType.ADVISOR
                'b' -> XiangqiPieceType.BISHOP
                'n' -> XiangqiPieceType.KNIGHT
                'r' -> XiangqiPieceType.ROOK
                'c' -> XiangqiPieceType.CANNON
                'p' -> XiangqiPieceType.PAWN
                else -> return null
            }
            return XiangqiPiece(side, type)
        }
    }
}
