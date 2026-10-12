package com.yijing.chess

import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoBoardTest {

    @Test
    fun testSingleStoneLiberties() {
        val board = GoBoard(size = 9)
        val center = BoardCoordinate(4, 4)
        board[center] = PlayerSide.BLACK
        val (group, liberties) = board.getGroupAndLiberties(center)
        assertEquals(1, group.size)
        assertEquals(4, liberties.size)

        val corner = BoardCoordinate(0, 0)
        board[corner] = PlayerSide.WHITE
        val (_, cornerLibs) = board.getGroupAndLiberties(corner)
        assertEquals(2, cornerLibs.size)
    }

    @Test
    fun testStoneCapture() {
        val board = GoBoard(size = 9)
        // 白子在 (4,4)
        val target = BoardCoordinate(4, 4)
        board[target] = PlayerSide.WHITE

        // 黑子包围 (4,4) 它的 4 个气位: (4,3), (4,5), (3,4), (5,4)
        board.playMove(BoardCoordinate(4, 3), PlayerSide.BLACK)
        board.playMove(BoardCoordinate(4, 5), PlayerSide.BLACK)
        board.playMove(BoardCoordinate(3, 4), PlayerSide.BLACK)
        // 最后一手落子 (5,4)
        val captured = board.playMove(BoardCoordinate(5, 4), PlayerSide.BLACK)
        assertNotNull(captured)
        assertTrue(captured!!.contains(target))
        assertNull("被提走的白子应当从盘面移除", board[target])
        assertEquals(1, board.blackPrisoners)
    }

    @Test
    fun testSuicideRuleProhibition() {
        val board = GoBoard(size = 9)
        // 黑子包围 (4,4) 周围四个点
        board[4, 3] = PlayerSide.BLACK
        board[4, 5] = PlayerSide.BLACK
        board[3, 4] = PlayerSide.BLACK
        board[5, 4] = PlayerSide.BLACK

        // 白子落入 (4,4) 无法提掉任何黑子，自身无气，属于禁着点
        assertFalse("无气且无法提子的落点属于禁着点", board.isMoveLegal(BoardCoordinate(4, 4), PlayerSide.WHITE))
    }
}
