package com.yijing.chess.xiangqi

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide

object XiangqiRule {

    /**
     * 判断将帅照面 (飞将)
     */
    fun isFlyingGeneral(board: XiangqiBoard): Boolean {
        val redKing = board.findKing(PlayerSide.RED) ?: return false
        val blackKing = board.findKing(PlayerSide.BLACK) ?: return false
        if (redKing.x != blackKing.x) return false

        val x = redKing.x
        val minY = minOf(redKing.y, blackKing.y)
        val maxY = maxOf(redKing.y, blackKing.y)
        for (y in (minY + 1) until maxY) {
            if (board[x, y] != null) return false
        }
        return true
    }

    /**
     * 判断某一方帅/将是否处于被将军状态
     */
    fun isKingInCheck(board: XiangqiBoard, side: PlayerSide): Boolean {
        val kingPos = board.findKing(side) ?: return true // 无将则视同受击败
        val enemySide = side.xiangqiOpposite

        // 检查所有敌方棋子是否能攻击到 kingPos
        for (y in 0..9) {
            for (x in 0..8) {
                val piece = board[x, y] ?: continue
                if (piece.side == enemySide) {
                    val pseudoMoves = getPseudoMoves(board, BoardCoordinate(x, y), piece)
                    if (pseudoMoves.any { it.to == kingPos }) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 获取指定方所有合规走步 (已过滤送将、被将军和飞将)
     */
    fun getLegalMoves(board: XiangqiBoard, side: PlayerSide): List<MoveRecord> {
        val legalMoves = mutableListOf<MoveRecord>()
        for (y in 0..9) {
            for (x in 0..8) {
                val piece = board[x, y] ?: continue
                if (piece.side == side) {
                    val from = BoardCoordinate(x, y)
                    val pseudo = getPseudoMoves(board, from, piece)
                    for (cand in pseudo) {
                        // 试探走子
                        val captured = board[cand.to]
                        if (captured?.type == XiangqiPieceType.KING) continue
                        board[cand.to] = piece
                        board[from] = null

                        val valid = !isFlyingGeneral(board) && !isKingInCheck(board, side)

                        // 恢复局面
                        board[from] = piece
                        board[cand.to] = captured

                        if (valid) {
                            val notation = generateChineseNotation(board, cand.from, cand.to, piece)
                            legalMoves.add(
                                MoveRecord(
                                    from = cand.from,
                                    to = cand.to,
                                    pieceText = piece.displayName,
                                    notation = notation,
                                    capturedText = captured?.displayName
                                )
                            )
                        }
                    }
                }
            }
        }
        return legalMoves
    }

    /**
     * 计算单颗棋子的伪合法步 (不考虑是否自送将军)
     */
    private fun getPseudoMoves(
        board: XiangqiBoard,
        from: BoardCoordinate,
        piece: XiangqiPiece
    ): List<CandidateMove> {
        val moves = mutableListOf<CandidateMove>()
        val fx = from.x
        val fy = from.y

        when (piece.type) {
            XiangqiPieceType.KING -> {
                val palaceX = 3..5
                val palaceY = if (piece.side == PlayerSide.RED) 7..9 else 0..2
                val deltas = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
                for ((dx, dy) in deltas) {
                    val nx = fx + dx
                    val ny = fy + dy
                    if (nx in palaceX && ny in palaceY) {
                        val target = board[nx, ny]
                        if (target == null || target.side != piece.side) {
                            moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                        }
                    }
                }
            }
            XiangqiPieceType.ADVISOR -> {
                val palaceX = 3..5
                val palaceY = if (piece.side == PlayerSide.RED) 7..9 else 0..2
                val deltas = listOf(Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1))
                for ((dx, dy) in deltas) {
                    val nx = fx + dx
                    val ny = fy + dy
                    if (nx in palaceX && ny in palaceY) {
                        val target = board[nx, ny]
                        if (target == null || target.side != piece.side) {
                            moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                        }
                    }
                }
            }
            XiangqiPieceType.BISHOP -> {
                // 田字格，红不能过河 (y in 5..9)，黑不能过河 (y in 0..4)
                val riverY = if (piece.side == PlayerSide.RED) 5..9 else 0..4
                val jumps = listOf(
                    Triple(2, 2, Pair(1, 1)),
                    Triple(2, -2, Pair(1, -1)),
                    Triple(-2, 2, Pair(-1, 1)),
                    Triple(-2, -2, Pair(-1, -1))
                )
                for ((dx, dy, eye) in jumps) {
                    val nx = fx + dx
                    val ny = fy + dy
                    val ex = fx + eye.first
                    val ey = fy + eye.second
                    if (nx in 0..8 && ny in riverY) {
                        // 象眼不能被塞
                        if (board[ex, ey] == null) {
                            val target = board[nx, ny]
                            if (target == null || target.side != piece.side) {
                                moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                            }
                        }
                    }
                }
            }
            XiangqiPieceType.KNIGHT -> {
                // 日字格，别马腿
                val knightMoves = listOf(
                    Pair(Pair(1, 2), Pair(0, 1)),
                    Pair(Pair(-1, 2), Pair(0, 1)),
                    Pair(Pair(1, -2), Pair(0, -1)),
                    Pair(Pair(-1, -2), Pair(0, -1)),
                    Pair(Pair(2, 1), Pair(1, 0)),
                    Pair(Pair(2, -1), Pair(1, 0)),
                    Pair(Pair(-2, 1), Pair(-1, 0)),
                    Pair(Pair(-2, -1), Pair(-1, 0))
                )
                for ((dest, leg) in knightMoves) {
                    val nx = fx + dest.first
                    val ny = fy + dest.second
                    val lx = fx + leg.first
                    val ly = fy + leg.second
                    if (nx in 0..8 && ny in 0..9) {
                        // 马腿无棋子挡着
                        if (board[lx, ly] == null) {
                            val target = board[nx, ny]
                            if (target == null || target.side != piece.side) {
                                moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                            }
                        }
                    }
                }
            }
            XiangqiPieceType.ROOK -> {
                // 直线走，遇到第一个子停下 (若是敌方则可吃)
                val dirs = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
                for ((dx, dy) in dirs) {
                    var nx = fx + dx
                    var ny = fy + dy
                    while (nx in 0..8 && ny in 0..9) {
                        val target = board[nx, ny]
                        if (target == null) {
                            moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                        } else {
                            if (target.side != piece.side) {
                                moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                            }
                            break
                        }
                        nx += dx
                        ny += dy
                    }
                }
            }
            XiangqiPieceType.CANNON -> {
                // 走法同车；吃子必须隔一个棋子 (炮架)
                val dirs = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
                for ((dx, dy) in dirs) {
                    var nx = fx + dx
                    var ny = fy + dy
                    var jumped = false
                    while (nx in 0..8 && ny in 0..9) {
                        val target = board[nx, ny]
                        if (!jumped) {
                            if (target == null) {
                                moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                            } else {
                                jumped = true // 遇到炮架
                            }
                        } else {
                            if (target != null) {
                                if (target.side != piece.side) {
                                    moves.add(CandidateMove(from, BoardCoordinate(nx, ny)))
                                }
                                break
                            }
                        }
                        nx += dx
                        ny += dy
                    }
                }
            }
            XiangqiPieceType.PAWN -> {
                // 兵/卒：未过河只能向前一步；过河后可向前或左右一步，不可后退
                val isRed = piece.side == PlayerSide.RED
                val forwardDy = if (isRed) -1 else 1
                val crossedRiver = if (isRed) fy <= 4 else fy >= 5

                // 向前
                val fny = fy + forwardDy
                if (fny in 0..9) {
                    val target = board[fx, fny]
                    if (target == null || target.side != piece.side) {
                        moves.add(CandidateMove(from, BoardCoordinate(fx, fny)))
                    }
                }
                // 过河后左右横移
                if (crossedRiver) {
                    for (dx in listOf(-1, 1)) {
                        val nx = fx + dx
                        if (nx in 0..8) {
                            val target = board[nx, fy]
                            if (target == null || target.side != piece.side) {
                                moves.add(CandidateMove(from, BoardCoordinate(nx, fy)))
                            }
                        }
                    }
                }
            }
        }

        return moves
    }

    /**
     * 生成标准象棋中文记谱 (如: 炮二平五, 马８进７)
     */
    fun generateChineseNotation(
        board: XiangqiBoard,
        from: BoardCoordinate,
        to: BoardCoordinate,
        piece: XiangqiPiece
    ): String {
        val isRed = piece.side == PlayerSide.RED
        val digitsRed = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        val digitsBlack = listOf("", "１", "２", "３", "４", "５", "６", "７", "８", "９")

        val fromColStr = if (isRed) digitsRed[9 - from.x] else digitsBlack[from.x + 1]
        val pieceName = piece.displayName

        val dirStr: String
        val toStr: String

        if (from.y == to.y) {
            dirStr = "平"
            toStr = if (isRed) digitsRed[9 - to.x] else digitsBlack[to.x + 1]
        } else {
            val forward = if (isRed) to.y < from.y else to.y > from.y
            dirStr = if (forward) "进" else "退"

            toStr = when (piece.type) {
                XiangqiPieceType.KNIGHT, XiangqiPieceType.BISHOP, XiangqiPieceType.ADVISOR -> {
                    if (isRed) digitsRed[9 - to.x] else digitsBlack[to.x + 1]
                }
                else -> {
                    val step = kotlin.math.abs(to.y - from.y)
                    if (isRed) digitsRed[step] else digitsBlack[step]
                }
            }
        }

        return "$pieceName$fromColStr$dirStr$toStr"
    }

    private data class CandidateMove(val from: BoardCoordinate, val to: BoardCoordinate)
}
