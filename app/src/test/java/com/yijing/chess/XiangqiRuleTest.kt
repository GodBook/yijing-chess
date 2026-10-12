package com.yijing.chess

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.xiangqi.XiangqiBoard
import com.yijing.chess.xiangqi.XiangqiPiece
import com.yijing.chess.xiangqi.XiangqiPieceType
import com.yijing.chess.xiangqi.XiangqiRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiangqiRuleTest {

    @Test
    fun testInitialBoardSetup() {
        val board = XiangqiBoard.initialBoard()
        assertEquals(PlayerSide.RED, board.turn)
        assertEquals(XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KING), board[4, 9])
        assertEquals(XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.KING), board[4, 0])
        assertEquals(XiangqiPiece(PlayerSide.RED, XiangqiPieceType.CANNON), board[1, 7])
        assertEquals(XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.CANNON), board[1, 2])
    }

    @Test
    fun testFlyingGeneral() {
        val board = XiangqiBoard()
        board[4, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KING)
        board[4, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.KING)
        // 没有任何阻挡，属于飞将将帅照面
        assertTrue(XiangqiRule.isFlyingGeneral(board))

        // 放入阻挡子
        board[4, 5] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.PAWN)
        assertFalse(XiangqiRule.isFlyingGeneral(board))
    }

    @Test
    fun testKnightLegBlocking() {
        val board = XiangqiBoard()
        val horse = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KNIGHT)
        board[4, 4] = horse
        board[3, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KING)
        board[5, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.KING)

        // 此时 8 个方向均可走
        var legals = XiangqiRule.getLegalMoves(board, PlayerSide.RED)
        val horseTargets = legals.filter { it.from == BoardCoordinate(4, 4) }.map { it.to }
        assertEquals(8, horseTargets.size)

        // 别住上方马腿 (4, 3)
        board[4, 3] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.PAWN)
        legals = XiangqiRule.getLegalMoves(board, PlayerSide.RED)
        val newTargets = legals.filter { it.from == BoardCoordinate(4, 4) }.map { it.to }
        // 向上两格方向 (3, 2) 和 (5, 2) 应被阻挡，剩余 6 个
        assertEquals(6, newTargets.size)
        assertFalse(newTargets.contains(BoardCoordinate(3, 2)))
        assertFalse(newTargets.contains(BoardCoordinate(5, 2)))
    }

    @Test
    fun testCannonHopCapture() {
        val board = XiangqiBoard()
        board[1, 7] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.CANNON)
        board[1, 4] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.PAWN) // 炮架
        board[1, 2] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.CANNON) // 目标
        board[3, 9] = XiangqiPiece(PlayerSide.RED, XiangqiPieceType.KING)
        board[5, 0] = XiangqiPiece(PlayerSide.BLACK, XiangqiPieceType.KING)

        val legals = XiangqiRule.getLegalMoves(board, PlayerSide.RED)
        val canCapture = legals.any { it.from == BoardCoordinate(1, 7) && it.to == BoardCoordinate(1, 2) }
        assertTrue("炮应当能够隔炮架吃子", canCapture)
    }
}
