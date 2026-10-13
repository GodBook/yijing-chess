package com.yijing.chess.ui.components

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.GameType
import kotlin.math.roundToInt

data class BoardPoint(val x: Float, val y: Float)

/** The same transform drives painting, hit testing, keyboard focus, and accessibility bounds. */
class BoardGeometry(val type: GameType, val width: Float, val height: Float, val size: Int = 19, val flipped: Boolean = false) {
    val columns = when (type) { GameType.CHESS -> 8; GameType.XIANGQI -> 9; GameType.GO -> size }
    val rows = if (type == GameType.XIANGQI) 10 else columns
    val stepX = when (type) { GameType.CHESS -> width / 8; GameType.XIANGQI -> width * 0.89f / 8; GameType.GO -> width * 0.9f / (size - 1) }
    val stepY = when (type) { GameType.CHESS -> height / 8; GameType.XIANGQI -> height * 0.9f / 9; GameType.GO -> height * 0.9f / (size - 1) }
    private val originX = when (type) { GameType.CHESS -> stepX / 2; GameType.XIANGQI -> width * 0.055f; GameType.GO -> width * 0.05f }
    private val originY = when (type) { GameType.CHESS -> stepY / 2; else -> height * 0.05f }

    fun center(coord: BoardCoordinate): BoardPoint {
        val x = if (flipped) columns - 1 - coord.x else coord.x
        val y = if (flipped) rows - 1 - coord.y else coord.y
        return BoardPoint(originX + x * stepX, originY + y * stepY)
    }

    fun hit(x: Float, y: Float): BoardCoordinate? {
        if (!x.isFinite() || !y.isFinite() || x < 0 || x >= width || y < 0 || y >= height) return null
        if (x < originX - stepX / 2 || x >= originX + (columns - 0.5f) * stepX ||
            y < originY - stepY / 2 || y >= originY + (rows - 0.5f) * stepY) return null
        val col = ((x - originX) / stepX).roundToInt()
        val row = ((y - originY) / stepY).roundToInt()
        if (col !in 0 until columns || row !in 0 until rows) return null
        return BoardCoordinate(if (flipped) columns - 1 - col else col, if (flipped) rows - 1 - row else row)
    }
}
