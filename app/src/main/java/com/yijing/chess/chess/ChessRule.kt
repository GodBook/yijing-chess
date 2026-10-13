package com.yijing.chess.chess

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide
import kotlin.math.abs

object ChessRule {
    fun isKingInCheck(board: ChessBoard, side: PlayerSide): Boolean =
        board.findKing(side)?.let { isSquareAttacked(board, it, side.opposite()) } ?: true

    fun isSquareAttacked(board: ChessBoard, square: BoardCoordinate, by: PlayerSide): Boolean {
        for (y in 0..7) for (x in 0..7) {
            val piece = board[x, y] ?: continue
            if (piece.side != by) continue
            val dx = square.x - x; val dy = square.y - y
            when (piece.type) {
                ChessPieceType.PAWN -> if (abs(dx) == 1 && dy == (if (by == PlayerSide.WHITE) -1 else 1)) return true
                ChessPieceType.KNIGHT -> if (abs(dx) * abs(dy) == 2) return true
                ChessPieceType.KING -> if (maxOf(abs(dx), abs(dy)) == 1) return true
                else -> {
                    val diagonal = abs(dx) == abs(dy) && dx != 0
                    val straight = (dx == 0) != (dy == 0)
                    val ray = when (piece.type) {
                        ChessPieceType.BISHOP -> diagonal
                        ChessPieceType.ROOK -> straight
                        ChessPieceType.QUEEN -> diagonal || straight
                        else -> false
                    }
                    if (!ray) continue
                    val sx = dx.compareTo(0); val sy = dy.compareTo(0)
                    var nx = x + sx; var ny = y + sy
                    while (nx != square.x || ny != square.y) {
                        if (board[nx, ny] != null) break
                        nx += sx; ny += sy
                    }
                    if (nx == square.x && ny == square.y) return true
                }
            }
        }
        return false
    }

    fun getLegalMoves(board: ChessBoard, side: PlayerSide, includeNotation: Boolean = true): List<MoveRecord> {
        val candidates = legalCandidates(board, side)
        return candidates.map { cand ->
            val piece = board[cand.from]!!
            val enPassant = piece.type == ChessPieceType.PAWN && cand.to == board.enPassantTarget
            val after = board.copy().also { applyMove(it, cand) }
            MoveRecord(cand.from, cand.to, piece.symbol,
                if (includeNotation) notation(board, after, cand, piece, candidates) else "",
                if (enPassant) ChessPiece(side.opposite(), ChessPieceType.PAWN).symbol else board[cand.to]?.symbol,
                cand.promotion?.name)
        }
    }

    fun hasLegalMove(board: ChessBoard, side: PlayerSide): Boolean {
        for (y in 0..7) for (x in 0..7) {
            val piece = board[x, y] ?: continue
            if (piece.side != side) continue
            for (move in pseudoMoves(board, BoardCoordinate(x, y), piece)) {
                if (board[move.to]?.type == ChessPieceType.KING) continue
                if (!isKingInCheck(board.copy().also { applyMove(it, move) }, side)) return true
            }
        }
        return false
    }

    private fun legalCandidates(board: ChessBoard, side: PlayerSide): List<CandidateChessMove> = buildList {
        for (y in 0..7) for (x in 0..7) {
            val piece = board[x, y] ?: continue
            if (piece.side != side) continue
            for (move in pseudoMoves(board, BoardCoordinate(x, y), piece)) {
                if (board[move.to]?.type == ChessPieceType.KING) continue
                if (!isKingInCheck(board.copy().also { applyMove(it, move) }, side)) add(move)
            }
        }
    }

    fun applyMove(board: ChessBoard, cand: CandidateChessMove) {
        val piece = requireNotNull(board[cand.from]) { "起点没有棋子" }
        val from = cand.from; val to = cand.to; val captured = board[to]
        val enPassant = piece.type == ChessPieceType.PAWN && to == board.enPassantTarget && captured == null
        if (enPassant) board[to.x, if (piece.side == PlayerSide.WHITE) to.y + 1 else to.y - 1] = null
        if (piece.type == ChessPieceType.KING && abs(to.x - from.x) == 2) {
            val rookX = if (to.x == 6) 7 else 0
            board[if (to.x == 6) 5 else 3, from.y] = board[rookX, from.y]
            board[rookX, from.y] = null
        }
        if (piece.type == ChessPieceType.KING) {
            if (piece.side == PlayerSide.WHITE) {
                board.whiteKingSideCastle = false; board.whiteQueenSideCastle = false
            } else {
                board.blackKingSideCastle = false; board.blackQueenSideCastle = false
            }
        }
        fun removeRookRight(square: BoardCoordinate) {
            when (square) {
                BoardCoordinate(0, 7) -> board.whiteQueenSideCastle = false
                BoardCoordinate(7, 7) -> board.whiteKingSideCastle = false
                BoardCoordinate(0, 0) -> board.blackQueenSideCastle = false
                BoardCoordinate(7, 0) -> board.blackKingSideCastle = false
            }
        }
        if (piece.type == ChessPieceType.ROOK) removeRookRight(from)
        if (captured?.type == ChessPieceType.ROOK) removeRookRight(to)
        board.enPassantTarget = if (piece.type == ChessPieceType.PAWN && abs(to.y - from.y) == 2) BoardCoordinate(from.x, (from.y + to.y) / 2) else null
        board[from] = null
        board[to] = if (cand.promotion != null) ChessPiece(piece.side, cand.promotion) else piece
        board.halfMoveClock = if (piece.type == ChessPieceType.PAWN || captured != null || enPassant) 0 else board.halfMoveClock + 1
        if (piece.side == PlayerSide.BLACK) board.fullMoveNumber++
        board.turn = piece.side.opposite()
    }

    private fun pseudoMoves(board: ChessBoard, from: BoardCoordinate, piece: ChessPiece): List<CandidateChessMove> = buildList {
        fun addTarget(x: Int, y: Int) {
            if (x in 0..7 && y in 0..7 && board[x, y]?.side != piece.side) add(CandidateChessMove(from, BoardCoordinate(x, y)))
        }
        fun ray(dx: Int, dy: Int) {
            var x = from.x + dx; var y = from.y + dy
            while (x in 0..7 && y in 0..7) {
                val target = board[x, y]
                if (target?.side == piece.side) break
                add(CandidateChessMove(from, BoardCoordinate(x, y)))
                if (target != null) break
                x += dx; y += dy
            }
        }
        when (piece.type) {
            ChessPieceType.PAWN -> {
                val dir = if (piece.side == PlayerSide.WHITE) -1 else 1
                fun pawnTarget(x: Int, y: Int) {
                    if (y == 0 || y == 7) {
                        for (type in listOf(ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.BISHOP, ChessPieceType.KNIGHT)) add(CandidateChessMove(from, BoardCoordinate(x, y), type))
                    } else add(CandidateChessMove(from, BoardCoordinate(x, y)))
                }
                val y = from.y + dir
                if (y in 0..7 && board[from.x, y] == null) {
                    pawnTarget(from.x, y)
                    if (from.y == (if (piece.side == PlayerSide.WHITE) 6 else 1) && board[from.x, y + dir] == null) pawnTarget(from.x, y + dir)
                }
                for (dx in listOf(-1, 1)) {
                    val x = from.x + dx
                    if (x !in 0..7 || y !in 0..7) continue
                    val target = board[x, y]; val epPawn = board[x, from.y]
                    val ep = board.enPassantTarget == BoardCoordinate(x, y) && target == null && epPawn?.type == ChessPieceType.PAWN && epPawn.side != piece.side
                    if ((target != null && target.side != piece.side) || ep) pawnTarget(x, y)
                }
            }
            ChessPieceType.KNIGHT -> for ((dx, dy) in listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)) addTarget(from.x + dx, from.y + dy)
            ChessPieceType.BISHOP -> for ((dx, dy) in listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)) ray(dx, dy)
            ChessPieceType.ROOK -> for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) ray(dx, dy)
            ChessPieceType.QUEEN -> for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) ray(dx, dy)
            ChessPieceType.KING -> {
                for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) addTarget(from.x + dx, from.y + dy)
                val rank = if (piece.side == PlayerSide.WHITE) 7 else 0
                if (from != BoardCoordinate(4, rank) || isKingInCheck(board, piece.side)) return@buildList
                val kingRight = if (piece.side == PlayerSide.WHITE) board.whiteKingSideCastle else board.blackKingSideCastle
                val queenRight = if (piece.side == PlayerSide.WHITE) board.whiteQueenSideCastle else board.blackQueenSideCastle
                for ((rookX, allowed) in listOf(7 to kingRight, 0 to queenRight)) {
                    if (!allowed || board[rookX, rank] != ChessPiece(piece.side, ChessPieceType.ROOK)) continue
                    val empty = if (rookX == 7) 5..6 else 1..3
                    val crossed = if (rookX == 7) 5..6 else 2..3
                    if (empty.all { board[it, rank] == null } && crossed.none { isSquareAttacked(board, BoardCoordinate(it, rank), piece.side.opposite()) })
                        add(CandidateChessMove(from, BoardCoordinate(if (rookX == 7) 6 else 2, rank)))
                }
            }
        }
    }

    private fun notation(before: ChessBoard, after: ChessBoard, cand: CandidateChessMove, piece: ChessPiece, legal: List<CandidateChessMove>): String {
        val suffix = if (isKingInCheck(after, piece.side.opposite())) { if (hasLegalMove(after, piece.side.opposite())) "+" else "#" } else ""
        if (piece.type == ChessPieceType.KING && abs(cand.to.x - cand.from.x) == 2) return (if (cand.to.x == 6) "O-O" else "O-O-O") + suffix
        val capture = before[cand.to] != null || (piece.type == ChessPieceType.PAWN && cand.to == before.enPassantTarget)
        val file = ('a'.code + cand.to.x).toChar(); val rank = 8 - cand.to.y
        val symbol = when (piece.type) {
            ChessPieceType.KING -> "K"; ChessPieceType.QUEEN -> "Q"; ChessPieceType.ROOK -> "R"
            ChessPieceType.BISHOP -> "B"; ChessPieceType.KNIGHT -> "N"; ChessPieceType.PAWN -> ""
        }
        val peers = legal.filter { it.from != cand.from && it.to == cand.to && before[it.from]?.type == piece.type }
        val disambiguation = if (piece.type == ChessPieceType.PAWN) {
            if (capture) ('a'.code + cand.from.x).toChar().toString() else ""
        } else if (peers.isEmpty()) "" else if (peers.none { it.from.x == cand.from.x }) {
            ('a'.code + cand.from.x).toChar().toString()
        } else if (peers.none { it.from.y == cand.from.y }) (8 - cand.from.y).toString()
        else "${('a'.code + cand.from.x).toChar()}${8 - cand.from.y}"
        val promotion = cand.promotion?.let { "=" + if (it == ChessPieceType.KNIGHT) "N" else it.name.first().toString() } ?: ""
        return "$symbol$disambiguation${if (capture) "x" else ""}$file$rank$promotion$suffix"
    }

    data class CandidateChessMove(val from: BoardCoordinate, val to: BoardCoordinate, val promotion: ChessPieceType? = null)
}
