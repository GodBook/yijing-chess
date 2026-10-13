package com.yijing.chess

import com.yijing.chess.model.*
import com.yijing.chess.ui.components.BoardGeometry
import com.yijing.chess.update.UpdateVersion
import org.junit.Assert.*
import org.junit.Test

class GeometryAndUpdateTest {
    @Test fun everyRenderedCenterHitsSameSquareIncludingFlip() {
        for (type in GameType.entries) for (flipped in listOf(false, true)) for (size in if (type == GameType.GO) listOf(9, 13, 19) else listOf(19)) {
            val geometry = BoardGeometry(type, 330f, if (type == GameType.XIANGQI) 377.6f else 330f, size, flipped)
            for (y in 0 until geometry.rows) for (x in 0 until geometry.columns) {
                val coord = BoardCoordinate(x, y); val point = geometry.center(coord)
                assertEquals("$type $flipped $coord", coord, geometry.hit(point.x, point.y))
            }
        }
    }

    @Test fun offBoardTapsAreRejectedRatherThanClamped() {
        for (type in GameType.entries) {
            val geometry = BoardGeometry(type, 330f, 330f)
            assertNull(geometry.hit(-1f, 10f)); assertNull(geometry.hit(330f, 10f)); assertNull(geometry.hit(10f, Float.NaN))
        }
        assertNull(BoardGeometry(GameType.GO, 330f, 330f).hit(0f, 0f))
    }

    @Test fun semanticVersionsCompareNumericallyAndRejectAmbiguity() {
        assertTrue(UpdateVersion.newer("v1.10.0", "1.9.9"))
        assertTrue(UpdateVersion.newer("2.0", "1.99.99"))
        assertFalse(UpdateVersion.newer("1.3.0", "1.3"))
        assertFalse(UpdateVersion.newer("1.2.99", "1.3.0"))
        for (version in listOf("1.3.0-beta", "latest", "1..3", "1.999999999999999999999")) assertTrue(runCatching { UpdateVersion.parts(version) }.isFailure)
    }
}
