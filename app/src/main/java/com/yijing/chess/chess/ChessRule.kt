package com.yijing.chess.chess

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide

object ChessRule {

    fun isKingInCheck(board: ChessBoard, side: PlayerSide): Boolean {
        val kingPos = board.findKing(side) ?: return true
        val opponent = side.opposite()

        for (y in 0..7) {
            for (x in 0..7) {
                val piece = board[x, y] ?: continue
                if (piece.side == opponent) {
                    val pseudo = getPseudoMoves(board, BoardCoordinate(x, y), piece, checkCastling = false)
                    if (pseudo.any { it.to == kingPos }) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun getLegalMoves(board: ChessBoard, side: PlayerSide): List<MoveRecord> {
        val legalMoves = mutableListOf<MoveRecord>()
        for (y in 0..7) {
            for (x in 0..7) {
                val piece = board[x, y] ?: continue
                if (piece.side == side) {
                    val from = BoardCoordinate(x, y)
                    val pseudo = getPseudoMoves(board, from, piece, checkCastling = true)
                    for (cand in pseudo) {
                        val testBoard = board.copy()
                        applyMove(testBoard, cand)
                        if (!isKingInCheck(testBoard, side)) {
                            val notation = generateAlgebraicNotation(board, testBoard, cand, piece)
                            legalMoves.add(
                                MoveRecord(
                                    from = cand.from,
                                    to = cand.to,
                                    pieceText = piece.symbol,
                                    notation = notation,
                                    capturedText = board[cand.to]?.symbol,
                                    promotion = cand.promotion?.name
                                )
                            )
                        }
                    }
                }
            }
        }
        return legalMoves
    }

    fun applyMove(board: ChessBoard, cand: CandidateChessMove) {
        val piece = board[cand.from] ?: return
        val from = cand.from
        val to = cand.to

        // 处理吃过路兵 (En Passant)
        if (piece.type == ChessPieceType.PAWN && to == board.enPassantTarget) {
            val capturedPawnY = if (piece.side == PlayerSide.WHITE) to.y + 1 else to.y - 1
            board[to.x, capturedPawnY] = null
        }

        // 处理王车易位 (Castling) 搬移车
        if (piece.type == ChessPieceType.KING && kotlin.math.abs(to.x - from.x) == 2) {
            if (to.x == 6) { // 王翼易位 (短易位)
                val rook = board[7, from.y]
                board[7, from.y] = null
                board[5, from.y] = rook
            } else if (to.x == 2) { // 后翼易位 (长易位)
                val rook = board[0, from.y]
                board[0, from.y] = null
                board[3, from.y] = rook
            }
        }

        // 更新过路兵目标格
        if (piece.type == ChessPieceType.PAWN && kotlin.math.abs(to.y - from.y) == 2) {
            board.enPassantTarget = BoardCoordinate(from.x, (from.y + to.y) / 2)
        } else {
            board.enPassantTarget = null
        }

        // 更新易位权限
        if (piece.type == ChessPieceType.KING) {
            if (piece.side == PlayerSide.WHITE) {
                board.whiteKingSideCastle = false
                board.whiteQueenSideCastle = false
            } else {
                board.blackKingSideCastle = false
                board.blackQueenSideCastle = false
            }
        } else if (piece.type == ChessPieceType.ROOK) {
            if (from == BoardCoordinate(0, 7)) board.whiteQueenSideCastle = false
            if (from == BoardCoordinate(7, 7)) board.whiteKingSideCastle = false
            if (from == BoardCoordinate(0, 0)) board.blackQueenSideCastle = false
            if (from == BoardCoordinate(7, 0)) board.blackKingSideCastle = false
        }

        // 放置棋子 (处理兵升变)
        board[from] = null
        if (cand.promotion != null) {
            board[to] = ChessPiece(piece.side, cand.promotion)
        } else {
            board[to] = piece
        }

        board.turn = board.turn.opposite()
    }

    private fun getPseudoMoves(
        board: ChessBoard,
        from: BoardCoordinate,
        piece: ChessPiece,
        checkCastling: Boolean
    ): List<CandidateChessMove> {
        val list = mutableListOf<CandidateChessMove>()
        val fx = from.x
        val fy = from.y
        val isWhite = piece.side == PlayerSide.WHITE

        when (piece.type) {
            ChessPieceType.PAWN -> {
                val dir = if (isWhite) -1 else 1
                val startRank = if (isWhite) 6 else 1
                val promoRank = if (isWhite) 0 else 7

                // 挺进 1 格
                val forward1 = BoardCoordinate(fx, fy + dir)
                if (board[forward1] == null) {
                    if (forward1.y == promoRank) {
                        for (promo in listOf(ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.BISHOP, ChessPieceType.KNIGHT)) {
                            list.add(CandidateChessMove(from, forward1, promo))
                        }
                    } else {
                        list.add(CandidateChessMove(from, forward1))
                        // 初始行挺进 2 格
                        if (fy == startRank) {
                            val forward2 = BoardCoordinate(fx, fy + 2 * dir)
                            if (board[forward2] == null) {
                                list.add(CandidateChessMove(from, forward2))
                            }
                        }
                    }
                }

                // 斜角吃子与吃过路兵
                for (dx in listOf(-1, 1)) {
                    val nx = fx + dx
                    val ny = fy + dir
                    if (nx in 0..7 && ny in 0..7) {
                        val target = board[nx, ny]
                        val isEnPassant = board.enPassantTarget != null && board.enPassantTarget == BoardCoordinate(nx, ny)
                        if ((target != null && target.side != piece.side) || isEnPassant) {
                            if (ny == promoRank) {
                                for (promo in listOf(ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.BISHOP, ChessPieceType.KNIGHT)) {
                                    list.add(CandidateChessMove(from, BoardCoordinate(nx, ny), promo))
                                }
                            } else {
                                list.add(CandidateChessMove(from, BoardCoordinate(nx, ny)))
                            }
                        }
                    }
                }
            }

            ChessPieceType.KNIGHT -> {
                val jumps = listOf(
                    Pair(1, 2), Pair(2, 1), Pair(2, -1), Pair(1, -2),
                    Pair(-1, -2), Pair(-2, -1), Pair(-2, 1), Pair(-1, 2)
                )
                for ((dx, dy) in jumps) {
                    val nx = fx + dx
                    val ny = fy + dy
                    if (nx in 0..7 && ny in 0..7) {
                        val target = board[nx, ny]
                        if (target == null || target.side != piece.side) {
                            list.add(CandidateChessMove(from, BoardCoordinate(nx, ny)))
                        }
                    }
                }
            }

            ChessPieceType.BISHOP -> addRayMoves(board, from, piece, listOf(Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)), list)
            ChessPieceType.ROOK -> addRayMoves(board, from, piece, listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1)), list)
            ChessPieceType.QUEEN -> addRayMoves(board, from, piece, listOf(
                Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1),
                Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1)
            ), list)

            ChessPieceType.KING -> {
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = fx + dx
                        val ny = fy + dy
                        if (nx in 0..7 && ny in 0..7) {
                            val target = board[nx, ny]
                            if (target == null || target.side != piece.side) {
                                list.add(CandidateChessMove(from, BoardCoordinate(nx, ny)))
                            }
                        }
                    }
                }

                // 易位检测
                if (checkCastling && !isKingInCheck(board, piece.side)) {
                    val rank = if (isWhite) 7 else 0
                    val canKingSide = if (isWhite) board.whiteKingSideCastle else board.blackKingSideCastle
                    val canQueenSide = if (isWhite) board.whiteQueenSideCastle else board.blackQueenSideCastle

                    // 短易位 O-O
                    if (canKingSide && board[5, rank] == null && board[6, rank] == null && board[7, rank]?.type == ChessPieceType.ROOK) {
                        // 途径格不得被攻击
                        val test1 = board.copy()
                        test1[4, rank] = null
                        test1[5, rank] = piece
                        if (!isKingInCheck(test1, piece.side)) {
                            list.add(CandidateChessMove(from, BoardCoordinate(6, rank)))
                        }
                    }

                    // 长易位 O-O-O
                    if (canQueenSide && board[1, rank] == null && board[2, rank] == null && board[3, rank] == null && board[0, rank]?.type == ChessPieceType.ROOK) {
                        val test1 = board.copy()
                        test1[4, rank] = null
                        test1[3, rank] = piece
                        if (!isKingInCheck(test1, piece.side)) {
                            list.add(CandidateChessMove(from, BoardCoordinate(2, rank)))
                        }
                    }
                }
            }
        }
        return list
    }

    private fun addRayMoves(
        board: ChessBoard,
        from: BoardCoordinate,
        piece: ChessPiece,
        rays: List<Pair<Int, Int>>,
        out: MutableList<CandidateChessMove>
    ) {
        for ((dx, dy) in rays) {
            var nx = from.x + dx
            var ny = from.y + dy
            while (nx in 0..7 && ny in 0..7) {
                val target = board[nx, ny]
                if (target == null) {
                    out.add(CandidateChessMove(from, BoardCoordinate(nx, ny)))
                } else {
                    if (target.side != piece.side) {
                        out.add(CandidateChessMove(from, BoardCoordinate(nx, ny)))
                    }
                    break
                }
                nx += dx
                ny += dy
            }
        }
    }

    private fun generateAlgebraicNotation(
        before: ChessBoard,
        after: ChessBoard,
        cand: CandidateChessMove,
        piece: ChessPiece
    ): String {
        if (piece.type == ChessPieceType.KING && kotlin.math.abs(cand.to.x - cand.from.x) == 2) {
            return if (cand.to.x == 6) "O-O" else "O-O-O"
        }

        val sb = StringBuilder()
        val toFile = ('a'.code + cand.to.x).toChar()
        val toRank = 8 - cand.to.y
        val isCapture = before[cand.to] != null || (piece.type == ChessPieceType.PAWN && cand.to == before.enPassantTarget)

        if (piece.type == ChessPieceType.PAWN) {
            if (isCapture) {
                sb.append(('a'.code + cand.from.x).toChar())
                sb.append('x')
            }
            sb.append(toFile).append(toRank)
            if (cand.promotion != null) {
                sb.append('=').append(cand.promotion.name.first())
            }
        } else {
            val sym = when (piece.type) {
                ChessPieceType.KNIGHT -> "N"
                ChessPieceType.BISHOP -> "B"
                ChessPieceType.ROOK -> "R"
                ChessPieceType.QUEEN -> "Q"
                ChessPieceType.KING -> "K"
                else -> ""
            }
            sb.append(sym)
            if (isCapture) sb.append('x')
            sb.append(toFile).append(toRank)
        }

        if (isKingInCheck(after, piece.side.opposite())) {
            sb.append('+')
        }
        return sb.toString()
    }

    data class CandidateChessMove(
        val from: BoardCoordinate,
        val to: BoardCoordinate,
        val promotion: ChessPieceType? = null
    )
}
