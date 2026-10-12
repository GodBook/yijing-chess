package com.yijing.chess

import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.chess.ChessPiece
import com.yijing.chess.chess.ChessPieceType
import com.yijing.chess.chess.ChessRule
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRuleTest {

    @Test
    fun testInitialLegalMovesCount() {
        val board = ChessBoard.initialBoard()
        // 国际象棋白方开局有 20 种合法走步 (8个兵各1步或2步=16种 + 2个马各2种跳法=4种)
        val legals = ChessRule.getLegalMoves(board, PlayerSide.WHITE)
        assertEquals(20, legals.size)
    }

    @Test
    fun testCastlingRights() {
        val board = ChessBoard()
        // 白王在 (4,7)，白车在 (7,7)
        board[4, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.KING)
        board[7, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.ROOK)
        board[4, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.KING)

        val legals = ChessRule.getLegalMoves(board, PlayerSide.WHITE)
        val canCastle = legals.any { it.from == BoardCoordinate(4, 7) && it.to == BoardCoordinate(6, 7) }
        assertTrue("白方王翼易位应当合法", canCastle)
    }

    @Test
    fun testCheckDetection() {
        val board = ChessBoard()
        board[4, 7] = ChessPiece(PlayerSide.WHITE, ChessPieceType.KING)
        board[4, 0] = ChessPiece(PlayerSide.BLACK, ChessPieceType.KING)
        assertFalse(ChessRule.isKingInCheck(board, PlayerSide.WHITE))

        // 黑后与白王在同列无阻挡，白王被将军
        board[4, 2] = ChessPiece(PlayerSide.BLACK, ChessPieceType.QUEEN)
        assertTrue(ChessRule.isKingInCheck(board, PlayerSide.WHITE))
    }
}
