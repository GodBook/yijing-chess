package com.yijing.chess.chess

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide

/**
 * 国际象棋 8x8 棋盘
 * x: 0..7 (对应 a..h)
 * y: 0..7 (0是第8行黑方底线，7是第1行白方底线)
 */
class ChessBoard(
    val grid: Array<Array<ChessPiece?>> = Array(8) { arrayOfNulls<ChessPiece>(8) },
    var turn: PlayerSide = PlayerSide.WHITE,
    var whiteKingSideCastle: Boolean = true,
    var whiteQueenSideCastle: Boolean = true,
    var blackKingSideCastle: Boolean = true,
    var blackQueenSideCastle: Boolean = true,
    var enPassantTarget: BoardCoordinate? = null,
    var halfMoveClock: Int = 0,
    var fullMoveNumber: Int = 1
) {
    fun copy(): ChessBoard {
        val newGrid = Array(8) { y ->
            Array(8) { x -> grid[y][x] }
        }
        return ChessBoard(
            grid = newGrid,
            turn = turn,
            whiteKingSideCastle = whiteKingSideCastle,
            whiteQueenSideCastle = whiteQueenSideCastle,
            blackKingSideCastle = blackKingSideCastle,
            blackQueenSideCastle = blackQueenSideCastle,
            enPassantTarget = enPassantTarget,
            halfMoveClock = halfMoveClock,
            fullMoveNumber = fullMoveNumber
        )
    }

    operator fun get(coord: BoardCoordinate): ChessPiece? = get(coord.x, coord.y)

    operator fun get(x: Int, y: Int): ChessPiece? {
        if (x !in 0..7 || y !in 0..7) return null
        return grid[y][x]
    }

    operator fun set(coord: BoardCoordinate, piece: ChessPiece?) {
        set(coord.x, coord.y, piece)
    }

    operator fun set(x: Int, y: Int, piece: ChessPiece?) {
        if (x in 0..7 && y in 0..7) {
            grid[y][x] = piece
        }
    }

    fun findKing(side: PlayerSide): BoardCoordinate? {
        for (y in 0..7) {
            for (x in 0..7) {
                val piece = grid[y][x]
                if (piece != null && piece.side == side && piece.type == ChessPieceType.KING) {
                    return BoardCoordinate(x, y)
                }
            }
        }
        return null
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (y in 0..7) {
            var empty = 0
            for (x in 0..7) {
                val piece = grid[y][x]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(piece.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (y < 7) sb.append('/')
        }

        sb.append(if (turn == PlayerSide.WHITE) " w " else " b ")

        val castle = StringBuilder()
        if (whiteKingSideCastle) castle.append('K')
        if (whiteQueenSideCastle) castle.append('Q')
        if (blackKingSideCastle) castle.append('k')
        if (blackQueenSideCastle) castle.append('q')
        sb.append(if (castle.isEmpty()) "-" else castle.toString())

        sb.append(' ')
        if (enPassantTarget != null) {
            val file = ('a'.code + enPassantTarget!!.x).toChar()
            val rank = 8 - enPassantTarget!!.y
            sb.append("$file$rank")
        } else {
            sb.append('-')
        }

        sb.append(" $halfMoveClock $fullMoveNumber")
        return sb.toString()
    }

    companion object {
        fun initialBoard(): ChessBoard {
            val board = ChessBoard()
            // 黑方
            board[0, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.ROOK)
            board[1, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.KNIGHT)
            board[2, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.BISHOP)
            board[3, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.QUEEN)
            board[4, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.KING)
            board[5, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.BISHOP)
            board[6, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.KNIGHT)
            board[7, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.ROOK)
            for (x in 0..7) {
                board[x, 1] = ChessPiece(PlayerSide.BLACK, ChessPieceType.PAWN)
            }

            // 白方
            for (x in 0..7) {
                board[x, 6] = ChessPiece(PlayerSide.WHITE, ChessPieceType.PAWN)
            }
            board[0, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.ROOK)
            board[1, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.KNIGHT)
            board[2, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.BISHOP)
            board[3, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.QUEEN)
            board[4, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.KING)
            board[5, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.BISHOP)
            board[6, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.KNIGHT)
            board[7, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.ROOK)

            return board
        }

        fun fromFen(fen: String): ChessBoard {
            val board = ChessBoard()
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size == 6) { "FEN 应包含局面、轮次、易位、过路兵和两个回合计数" }
            val ranks = parts[0].split("/")
            require(ranks.size == 8) { "国际象棋 FEN 应有 8 行" }
            for (y in 0 until minOf(8, ranks.size)) {
                var x = 0
                for (c in ranks[y]) {
                    if (c in '1'..'8') {
                        x += c.digitToInt()
                    } else {
                        val piece = requireNotNull(ChessPiece.fromFenChar(c)) { "无法识别 FEN 棋子：$c" }
                        require(x in 0..7) { "FEN 行宽超过 8 格" }
                        board[x, y] = piece
                        x++
                    }
                }
                require(x == 8) { "FEN 每行必须恰好 8 格" }
            }
            require(parts[1] in listOf("w", "b")) { "FEN 行棋方应为 w 或 b" }
            require(parts[2] == "-" || (parts[2].all { it in "KQkq" } && parts[2].toSet().size == parts[2].length)) { "FEN 易位权无效" }
            require(parts[3] == "-" || Regex("[a-h][36]").matches(parts[3])) { "FEN 过路兵格无效" }
            if (parts.size > 1) {
                board.turn = if (parts[1] == "b") PlayerSide.BLACK else PlayerSide.WHITE
            }
            if (parts.size > 2) {
                val castle = parts[2]
                board.whiteKingSideCastle = castle.contains('K')
                board.whiteQueenSideCastle = castle.contains('Q')
                board.blackKingSideCastle = castle.contains('k')
                board.blackQueenSideCastle = castle.contains('q')
            }
            if (parts.size > 3 && parts[3] != "-") {
                val ep = parts[3]
                if (ep.length >= 2) {
                    val ex = ep[0] - 'a'
                    val ey = 8 - ep[1].digitToInt()
                    board.enPassantTarget = BoardCoordinate(ex, ey)
                }
            }
            board.halfMoveClock = requireNotNull(parts[4].toIntOrNull()) { "FEN 半回合计数无效" }
            board.fullMoveNumber = requireNotNull(parts[5].toIntOrNull()) { "FEN 回合数无效" }
            require(board.halfMoveClock >= 0 && board.fullMoveNumber >= 1) { "FEN 回合计数超出范围" }
            return board
        }
    }
}
