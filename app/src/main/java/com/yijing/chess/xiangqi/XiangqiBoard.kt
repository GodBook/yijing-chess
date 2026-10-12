package com.yijing.chess.xiangqi

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide

/**
 * 中国象棋棋盘状态 (9 列 x 10 行)
 * x: 0..8 (左到右)
 * y: 0..9 (上到下，0~4黑方半场，5~9红方半场)
 */
class XiangqiBoard(
    val grid: Array<Array<XiangqiPiece?>> = Array(10) { arrayOfNulls<XiangqiPiece>(9) },
    var turn: PlayerSide = PlayerSide.RED
) {
    fun copy(): XiangqiBoard {
        val newGrid = Array(10) { y ->
            Array(9) { x -> grid[y][x] }
        }
        return XiangqiBoard(newGrid, turn)
    }

    operator fun get(coord: BoardCoordinate): XiangqiPiece? = get(coord.x, coord.y)

    operator fun get(x: Int, y: Int): XiangqiPiece? {
        if (x !in 0..8 || y !in 0..9) return null
        return grid[y][x]
    }

    operator fun set(coord: BoardCoordinate, piece: XiangqiPiece?) {
        set(coord.x, coord.y, piece)
    }

    operator fun set(x: Int, y: Int, piece: XiangqiPiece?) {
        if (x in 0..8 && y in 0..9) {
            grid[y][x] = piece
        }
    }

    fun findKing(side: PlayerSide): BoardCoordinate? {
        for (y in 0..9) {
            for (x in 0..8) {
                val piece = grid[y][x]
                if (piece != null && piece.side == side && piece.type == XiangqiPieceType.KING) {
                    return BoardCoordinate(x, y)
                }
            }
        }
        return null
    }

    /**
     * 生成标准 FEN 串
     */
    fun toFen(): String {
        val sb = StringBuilder()
        for (y in 0..9) {
            var emptyCount = 0
            for (x in 0..8) {
                val piece = grid[y][x]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece.fenChar)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (y < 9) sb.append('/')
        }
        sb.append(if (turn == PlayerSide.RED) " w - - 0 1" else " b - - 0 1")
        return sb.toString()
    }

    companion object {
        fun initialBoard(): XiangqiBoard {
            val board = XiangqiBoard()
            // 黑方 (y = 0..3)
            board[0, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.ROOK)
            board[1, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.KNIGHT)
            board[2, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.BISHOP)
            board[3, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.ADVISOR)
            board[4, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.KING)
            board[5, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.ADVISOR)
            board[6, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.BISHOP)
            board[7, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.KNIGHT)
            board[8, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.ROOK)

            board[1, 2] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.CANNON)
            board[7, 2] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.CANNON)

            for (x in 0..8 step 2) {
                board[x, 3] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.PAWN)
            }

            // 红方 (y = 6..9)
            for (x in 0..8 step 2) {
                board[x, 6] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.PAWN)
            }
            board[1, 7] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.CANNON)
            board[7, 7] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.CANNON)

            board[0, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.ROOK)
            board[1, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KNIGHT)
            board[2, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.BISHOP)
            board[3, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.ADVISOR)
            board[4, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KING)
            board[5, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.ADVISOR)
            board[6, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.BISHOP)
            board[7, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KNIGHT)
            board[8, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.ROOK)

            board.turn = PlayerSide.RED
            return board
        }

        fun fromFen(fen: String): XiangqiBoard {
            val board = XiangqiBoard()
            val parts = fen.trim().split(" ")
            val rows = parts[0].split("/")
            for (y in 0 until minOf(10, rows.size)) {
                var x = 0
                for (c in rows[y]) {
                    if (c.isDigit()) {
                        x += c.digitToInt()
                    } else {
                        val piece = XiangqiPiece.fromFenChar(c)
                        if (piece != null && x in 0..8) {
                            board[x, y] = piece
                        }
                        x++
                    }
                }
            }
            if (parts.size > 1) {
                board.turn = if (parts[1].lowercase() == "b") PlayerSide.BLACK else PlayerSide.RED
            }
            return board
        }
    }
}
