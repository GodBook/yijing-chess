package com.yijing.chess.session

import com.yijing.chess.chess.*
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.*
import com.yijing.chess.xiangqi.*

object SessionRules {
    fun initial(type: GameType, size: Int = 19) = when (type) {
        GameType.CHESS -> PositionSnapshot.of(ChessBoard.initialBoard())
        GameType.XIANGQI -> PositionSnapshot.of(XiangqiBoard.initialBoard())
        GameType.GO -> PositionSnapshot.of(GoBoard(size))
    }

    fun empty(type: GameType, size: Int = 19) = when (type) {
        GameType.CHESS -> PositionSnapshot.of(ChessBoard(whiteKingSideCastle = false, whiteQueenSideCastle = false, blackKingSideCastle = false, blackQueenSideCastle = false))
        GameType.XIANGQI -> PositionSnapshot.of(XiangqiBoard())
        GameType.GO -> PositionSnapshot.of(GoBoard(size))
    }

    fun validateSnapshot(position: PositionSnapshot) {
        require(position.turn in listOf(position.gameType.firstSide(), position.gameType.opposite(position.gameType.firstSide()))) { "局面轮次无效" }
        when (position.gameType) {
            GameType.CHESS -> require(position.chess().turn == position.turn) { "FEN 与轮次不符" }
            GameType.XIANGQI -> require(position.xiangqi().turn == position.turn) { "象棋 FEN 与轮次不符" }
            GameType.GO -> position.go()
        }
    }

    fun legalMoves(position: PositionSnapshot, notation: Boolean = true): List<MoveRecord> = when (position.gameType) {
        GameType.CHESS -> ChessRule.getLegalMoves(position.chess(), position.turn, notation)
        GameType.XIANGQI -> XiangqiRule.getLegalMoves(position.xiangqi(), position.turn)
        GameType.GO -> {
            val board = position.go()
            buildList {
                for (y in 0 until board.size) for (x in 0 until board.size) {
                    val coord = BoardCoordinate(x, y)
                    if (board.isMoveLegal(coord, board.turn)) add(MoveRecord(coord, coord, board.turn.displayName,
                        "${board.turn.displayName} ${coordinateName(GameType.GO, coord, board.size)}"))
                }
            }
        }
    }

    fun after(position: PositionSnapshot, event: GameEvent): PositionSnapshot {
        require(event.side == position.turn) { "棋谱行棋方与局面轮次不符" }
        if (event.kind != EventKind.MOVE) {
            require(position.gameType == GameType.GO && event.move == null) { "该棋类不支持停着" }
            val board = position.go()
            if (event.kind == EventKind.PASS) board.pass() else board.consecutivePasses = 0
            return PositionSnapshot.of(board)
        }
        val move = requireNotNull(event.move) { "棋谱走法缺失" }
        return when (position.gameType) {
            GameType.CHESS -> {
                val board = position.chess()
                require(ChessRule.getLegalMoves(board, board.turn, false).any { it.from == move.from && it.to == move.to && it.promotion == move.promotion }) { "棋谱含非法国际象棋走法" }
                ChessRule.applyMove(board, ChessRule.CandidateChessMove(move.from, move.to, move.promotion?.let(ChessPieceType::valueOf)))
                PositionSnapshot.of(board)
            }
            GameType.XIANGQI -> {
                val board = position.xiangqi()
                require(XiangqiRule.getLegalMoves(board, board.turn).any { it.from == move.from && it.to == move.to }) { "棋谱含非法象棋走法" }
                board[move.to] = board[move.from]; board[move.from] = null; board.turn = board.turn.xiangqiOpposite
                PositionSnapshot.of(board)
            }
            GameType.GO -> {
                require(move.from == move.to) { "围棋走法坐标无效" }
                val board = position.go()
                require(board.playMove(move.to, board.turn) != null) { "棋谱含禁着点或无气落子" }
                PositionSnapshot.of(board)
            }
        }
    }

    fun playableErrors(position: PositionSnapshot): List<String> = buildList {
        when (position.gameType) {
            GameType.CHESS -> {
                val board = position.chess()
                for (side in listOf(PlayerSide.WHITE, PlayerSide.BLACK)) {
                    val pieces = board.grid.flatMap { it.toList() }.filterNotNull().filter { it.side == side }
                    if (pieces.count { it.type == ChessPieceType.KING } != 1) add("${side.displayName}必须恰好有一个王")
                    if (pieces.size > 16 || pieces.count { it.type == ChessPieceType.PAWN } > 8) add("${side.displayName}棋子数量超出规则范围")
                }
                if ((0..7).any { board[it, 0]?.type == ChessPieceType.PAWN || board[it, 7]?.type == ChessPieceType.PAWN }) add("兵不能留在第 1 或第 8 排，请先设置升变")
                if (isCheck(position, position.turn.opposite())) add("非行棋方的王正被将军，请检查轮次或局面")
                val rights = listOf(board.whiteKingSideCastle to (PlayerSide.WHITE to 7), board.whiteQueenSideCastle to (PlayerSide.WHITE to 0),
                    board.blackKingSideCastle to (PlayerSide.BLACK to 7), board.blackQueenSideCastle to (PlayerSide.BLACK to 0))
                if (rights.any { (enabled, detail) -> val (side, x) = detail; val y = if (side == PlayerSide.WHITE) 7 else 0
                    enabled && (board[4, y] != ChessPiece(side, ChessPieceType.KING) || board[x, y] != ChessPiece(side, ChessPieceType.ROOK)) }) add("易位权需要对应的王与车都在初始位置")
            }
            GameType.XIANGQI -> {
                val board = position.xiangqi()
                for (side in listOf(PlayerSide.RED, PlayerSide.BLACK)) {
                    val pieces = board.grid.flatMap { it.toList() }.filterNotNull().filter { it.side == side }
                    if (pieces.count { it.type == XiangqiPieceType.KING } != 1) add("${side.displayName}必须恰好有一个将帅")
                    val king = board.findKing(side)
                    if (king != null && (king.x !in 3..5 || king.y !in (if (side == PlayerSide.RED) 7..9 else 0..2))) add("${side.displayName}将帅必须在九宫内")
                    for (type in XiangqiPieceType.entries) {
                        val limit = if (type == XiangqiPieceType.PAWN) 5 else if (type == XiangqiPieceType.KING) 1 else 2
                        if (pieces.count { it.type == type } > limit) add("${side.displayName}的${XiangqiPiece(side, type).displayName}数量超限")
                    }
                }
                if (XiangqiRule.isFlyingGeneral(board)) add("将帅不能照面，请加入阻挡棋子")
                if (isCheck(position, position.turn.xiangqiOpposite)) add("非行棋方正在被将军，请检查轮次或局面")
            }
            GameType.GO -> {
                val board = position.go()
                if ((0 until board.size).any { y -> (0 until board.size).any { x -> board[x, y] != null && board.getGroupAndLiberties(BoardCoordinate(x, y)).second.isEmpty() } })
                    add("棋盘存在无气棋块，请先移除死子")
            }
        }
    }

    fun isCheck(position: PositionSnapshot, side: PlayerSide = position.turn) = when (position.gameType) {
        GameType.CHESS -> ChessRule.isKingInCheck(position.chess(), side)
        GameType.XIANGQI -> XiangqiRule.isKingInCheck(position.xiangqi(), side)
        GameType.GO -> false
    }

    fun outcome(session: GameSession): GameOutcome {
        val position = session.current.position
        if (position.gameType == GameType.GO) return if (position.passes >= 2) GameOutcome(GamePhase.SCORING, reason = "双方连续停着，请标记死子并确认结果") else GameOutcome()
        val legal = legalMoves(position, false)
        if (legal.isEmpty()) {
            val checked = isCheck(position)
            if (position.gameType == GameType.XIANGQI || checked) return GameOutcome(GamePhase.FINISHED, position.gameType.opposite(position.turn), if (checked) "将死" else "困毙")
            return GameOutcome(GamePhase.FINISHED, reason = "困毙和棋")
        }
        if (position.gameType == GameType.CHESS) {
            val board = position.chess()
            if (insufficientMaterial(board)) return GameOutcome(GamePhase.FINISHED, reason = "子力不足，无法将死")
            if (board.halfMoveClock >= 150) return GameOutcome(GamePhase.FINISHED, reason = "75 回合自动和棋")
            if (repetitionCount(session) >= 5) return GameOutcome(GamePhase.FINISHED, reason = "五次重复局面，自动和棋")
        }
        return GameOutcome(reason = if (isCheck(position)) "将军" else "")
    }

    fun drawClaim(session: GameSession): String? {
        if (session.gameType != GameType.CHESS || session.isDraft || session.isReview || session.outcome().phase != GamePhase.PLAYING) return null
        return when {
            repetitionCount(session) >= 3 -> "三次重复局面，申请和棋"
            session.current.position.chess().halfMoveClock >= 100 -> "50 回合未吃子或动兵，申请和棋"
            else -> null
        }
    }

    private fun repetitionCount(session: GameSession): Int {
        fun key(position: PositionSnapshot): String {
            val board = position.chess()
            if (board.enPassantTarget != null && ChessRule.getLegalMoves(board, board.turn, false).none { it.to == board.enPassantTarget && board[it.from]?.type == ChessPieceType.PAWN }) board.enPassantTarget = null
            return board.toFen().split(" ").take(4).joinToString(" ")
        }
        val current = key(session.current.position)
        return session.path().count { key(it.position) == current }
    }

    private fun insufficientMaterial(board: ChessBoard): Boolean {
        val minor = buildList { for (y in 0..7) for (x in 0..7) board[x, y]?.let { if (it.type != ChessPieceType.KING) add(it to BoardCoordinate(x, y)) } }
        if (minor.isEmpty()) return true
        if (minor.size == 1 && minor.first().first.type in listOf(ChessPieceType.BISHOP, ChessPieceType.KNIGHT)) return true
        return minor.all { it.first.type == ChessPieceType.BISHOP } && minor.map { (it.second.x + it.second.y) % 2 }.distinct().size == 1
    }
}

data class GoScore(val black: Double, val white: Double, val blackTerritory: Int, val whiteTerritory: Int, val neutral: Int) {
    val winner: PlayerSide? get() = when { black > white -> PlayerSide.BLACK; white > black -> PlayerSide.WHITE; else -> null }
    val margin: Double get() = kotlin.math.abs(black - white)
}

fun scoreGo(session: GameSession): GoScore {
    val board = session.current.position.go()
    var deadBlack = 0; var deadWhite = 0
    for (coord in session.markedDead.toSet()) {
        when (board[coord]) { PlayerSide.BLACK -> deadBlack++; PlayerSide.WHITE -> deadWhite++; else -> Unit }
        board[coord] = null
    }
    val seen = mutableSetOf<BoardCoordinate>()
    var blackTerritory = 0; var whiteTerritory = 0; var neutral = 0; var blackStones = 0; var whiteStones = 0
    for (y in 0 until board.size) for (x in 0 until board.size) {
        val coord = BoardCoordinate(x, y)
        when (board[coord]) { PlayerSide.BLACK -> { blackStones++; continue }; PlayerSide.WHITE -> { whiteStones++; continue }; else -> Unit }
        if (coord in seen) continue
        val region = mutableSetOf(coord); val queue = java.util.ArrayDeque<BoardCoordinate>().apply { add(coord) }; val borders = mutableSetOf<PlayerSide>()
        seen.add(coord)
        while (queue.isNotEmpty()) for (neighbor in board.getNeighbors(queue.removeFirst())) {
            val side = board[neighbor]
            if (side != null) borders.add(side) else if (seen.add(neighbor)) { region.add(neighbor); queue.add(neighbor) }
        }
        when (borders.singleOrNull()) { PlayerSide.BLACK -> blackTerritory += region.size; PlayerSide.WHITE -> whiteTerritory += region.size; else -> neutral += region.size }
    }
    val black = if (session.scoringRule == GoScoringRule.AREA) blackStones + blackTerritory else blackTerritory + board.blackPrisoners + deadWhite
    val white = if (session.scoringRule == GoScoringRule.AREA) whiteStones + whiteTerritory else whiteTerritory + board.whitePrisoners + deadBlack
    return GoScore(black.toDouble(), white + session.komi, blackTerritory, whiteTerritory, neutral)
}
