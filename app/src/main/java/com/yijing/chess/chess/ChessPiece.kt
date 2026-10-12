package com.yijing.chess.chess

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide

enum class ChessPieceType {
    KING,
    QUEEN,
    ROOK,
    BISHOP,
    KNIGHT,
    PAWN
}

data class ChessPiece(
    val side: PlayerSide, // WHITE or BLACK
    val type: ChessPieceType
) {
    val symbol: String
        get() = when (side) {
            PlayerSide.WHITE -> when (type) {
                ChessPieceType.KING -> "♔"
                ChessPieceType.QUEEN -> "♕"
                ChessPieceType.ROOK -> "♖"
                ChessPieceType.BISHOP -> "♗"
                ChessPieceType.KNIGHT -> "♘"
                ChessPieceType.PAWN -> "♙"
            }
            else -> when (type) {
                ChessPieceType.KING -> "♚"
                ChessPieceType.QUEEN -> "♛"
                ChessPieceType.ROOK -> "♜"
                ChessPieceType.BISHOP -> "♝"
                ChessPieceType.KNIGHT -> "♞"
                ChessPieceType.PAWN -> "♟"
            }
        }

    val fenChar: Char
        get() {
            val c = when (type) {
                ChessPieceType.KING -> 'k'
                ChessPieceType.QUEEN -> 'q'
                ChessPieceType.ROOK -> 'r'
                ChessPieceType.BISHOP -> 'b'
                ChessPieceType.KNIGHT -> 'n'
                ChessPieceType.PAWN -> 'p'
            }
            return if (side == PlayerSide.WHITE) c.uppercaseChar() else c
        }

    companion object {
        fun fromFenChar(c: Char): ChessPiece? {
            val side = if (c.isUpperCase()) PlayerSide.WHITE else PlayerSide.BLACK
            val type = when (c.lowercaseChar()) {
                'k' -> ChessPieceType.KING
                'q' -> ChessPieceType.QUEEN
                'r' -> ChessPieceType.ROOK
                'b' -> ChessPieceType.BISHOP
                'n' -> ChessPieceType.KNIGHT
                'p' -> ChessPieceType.PAWN
                else -> return null
            }
            return ChessPiece(side, type)
        }
    }
}
