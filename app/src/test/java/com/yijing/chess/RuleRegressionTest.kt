package com.yijing.chess

import com.yijing.chess.chess.*
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.*
import com.yijing.chess.xiangqi.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RuleRegressionTest {
    @Test fun fenPreservesAllSixFields() {
        val fen = "7k/8/8/8/8/8/8/K7 b - - 17 42"
        assertEquals(fen, ChessBoard.fromFen(fen).toFen())
    }

    @Test fun invalidFenIsRejected() {
        for (fen in listOf("8/8 w - - 0 1", "9/8/8/8/8/8/8/8 w - - 0 1", "8/8/8/8/8/8/8/8 w - z9 0 1"))
            assertTrue(runCatching { ChessBoard.fromFen(fen) }.isFailure)
    }

    @Test fun moveCountersAndCapturedRookRights() {
        val board = ChessBoard.fromFen("r6k/8/8/8/8/8/8/R6K w q - 12 20")
        ChessRule.applyMove(board, ChessRule.CandidateChessMove(BoardCoordinate(0, 7), BoardCoordinate(0, 0)))
        assertFalse(board.blackQueenSideCastle)
        assertEquals(0, board.halfMoveClock)
        ChessRule.applyMove(board, ChessRule.CandidateChessMove(BoardCoordinate(7, 0), BoardCoordinate(6, 0)))
        assertEquals(1, board.halfMoveClock)
        assertEquals(21, board.fullMoveNumber)
    }

    @Test fun knightPromotionUsesN() {
        val board = ChessBoard.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1")
        assertEquals("a8=N", ChessRule.getLegalMoves(board, board.turn).first { it.promotion == "KNIGHT" }.notation)
    }

    @Test fun sanDisambiguatesAndMarksMate() {
        val knights = ChessBoard.fromFen("7k/8/8/8/8/8/3N1N2/K7 w - - 0 1")
        assertEquals(setOf("Nde4", "Nfe4"), ChessRule.getLegalMoves(knights, knights.turn).filter { it.to == BoardCoordinate(4, 4) }.map { it.notation }.toSet())
        val mate = ChessBoard.fromFen("7k/8/5KQ1/8/8/8/8/8 w - - 0 1")
        assertEquals("Qg7#", ChessRule.getLegalMoves(mate, mate.turn).first { it.to == BoardCoordinate(6, 1) }.notation)
    }

    @Test fun castlingCannotCrossPawnAttack() {
        val board = ChessBoard.fromFen("k7/8/8/8/8/8/4p3/4K2R w K - 0 1")
        assertFalse(ChessRule.isKingInCheck(board, PlayerSide.WHITE))
        assertFalse(ChessRule.getLegalMoves(board, board.turn).any { it.from == BoardCoordinate(4, 7) && it.to == BoardCoordinate(6, 7) })
    }

    @Test fun enPassantCannotExposeOwnKing() {
        val board = ChessBoard.fromFen("k7/8/8/4KPpr/8/8/8/8 w - g6 0 1")
        assertFalse(ChessRule.getLegalMoves(board, board.turn).any { it.from == BoardCoordinate(5, 3) && it.to == BoardCoordinate(6, 2) })
    }

    @Test fun standardChessPerft() {
        fun count(board: ChessBoard, depth: Int): Long {
            if (depth == 0) return 1
            return ChessRule.getLegalMoves(board, board.turn, false).sumOf { move ->
                val next = board.copy()
                ChessRule.applyMove(next, ChessRule.CandidateChessMove(move.from, move.to, move.promotion?.let(ChessPieceType::valueOf)))
                count(next, depth - 1)
            }
        }
        assertEquals(400L, count(ChessBoard.initialBoard(), 2))
        assertEquals(8902L, count(ChessBoard.initialBoard(), 3))
    }

    @Test fun goRestoresPassesAndKo() {
        val board = GoBoard(9)
        board.pass()
        val restored = GoBoard.fromStateString(9, board.toStateString(), board.turn, consecutivePasses = board.consecutivePasses)
        assertTrue(restored.pass())
        val ko = GoBoard.fromStateString(9, ".".repeat(81), PlayerSide.BLACK, lastCapturedCoord = BoardCoordinate(4, 4))
        assertFalse(ko.isMoveLegal(BoardCoordinate(4, 4), ko.turn))
        assertFalse(ko.isMoveLegal(BoardCoordinate(-1, 0), ko.turn))
    }

    @Test fun chessAiTakesFreeRookAtAllDifficulties() = runBlocking {
        val board = ChessBoard.fromFen("7k/8/8/3r4/3Q4/8/8/K7 w - - 0 1")
        val before = board.toFen()
        for (difficulty in 1..3) {
            val decision = ChessAI.findBestMove(board, board.turn, difficulty, 3000)
            assertEquals("difficulty $difficulty", BoardCoordinate(3, 3), decision.move?.to)
            assertEquals(BoardCoordinate(3, 4), decision.move?.from)
            assertEquals(before, board.toFen())
        }
    }

    @Test fun xiangqiAiTakesFreeCannonAndPreservesBoard() = runBlocking {
        val board = XiangqiBoard.fromFen("4k4/9/c8/9/9/4P4/9/R8/9/4K4 w - - 0 1")
        val before = board.toFen()
        val decision = XiangqiAI.findBestMove(board, board.turn, 2, 2000)
        assertEquals(BoardCoordinate(0, 2), decision.move?.to)
        assertEquals(before, board.toFen())
    }

    @Test fun aiCancellationAndBudget() = runBlocking {
        val board = ChessBoard.initialBoard()
        val limited = ChessAI.findBestMove(board, board.turn, 3, 50)
        assertNotNull(limited.move)
        assertTrue(limited.elapsedMillis < 1000)
        val job = launch(Dispatchers.Default) { ChessAI.findBestMove(board, board.turn, 3, 30_000) }
        delay(25)
        withTimeout(1000) { job.cancelAndJoin() }
    }

    @Test fun snapbackIsNotMistakenForSingleStoneKo() {
        val board = GoBoard(9)
        for (coord in listOf(2 to 3, 2 to 4, 3 to 2, 4 to 3)) board[coord.first, coord.second] = PlayerSide.BLACK
        for (coord in listOf(3 to 3, 1 to 3, 2 to 2, 1 to 4, 2 to 5, 3 to 5, 4 to 4)) board[coord.first, coord.second] = PlayerSide.WHITE
        assertEquals(1, board.playMove(BoardCoordinate(3, 4), PlayerSide.BLACK)!!.size)
        assertNull(board.lastCapturedCoord)
        assertTrue(board.isMoveLegal(BoardCoordinate(3, 3), PlayerSide.WHITE))
        assertEquals(3, board.playMove(BoardCoordinate(3, 3), PlayerSide.WHITE)!!.size)
    }
}
