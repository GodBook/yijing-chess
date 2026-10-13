package com.yijing.chess.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.yijing.chess.model.*
import com.yijing.chess.session.PositionSnapshot

@Composable
fun BoardThumbnail(position: PositionSnapshot, modifier: Modifier = Modifier) {
    val chess = remember(position) { if (position.gameType == GameType.CHESS) position.chess() else null }
    val xiangqi = remember(position) { if (position.gameType == GameType.XIANGQI) position.xiangqi() else null }
    val go = remember(position) { if (position.gameType == GameType.GO) position.go() else null }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    Canvas(modifier.aspectRatio(if (position.gameType == GameType.XIANGQI) 0.9f else 1f).background(Color(0xFFE8D3A7))
        .semantics { contentDescription = "${position.gameType.displayName}局面缩略图" }) {
        val geometry = BoardGeometry(position.gameType, size.width, size.height, position.boardSize)
        val count = geometry.columns
        if (chess != null) for (y in 0..7) for (x in 0..7) {
            drawRect(if ((x + y) % 2 == 0) Color(0xFFE8D3A7) else Color(0xFFB58863), Offset(x * geometry.stepX, y * geometry.stepY), Size(geometry.stepX, geometry.stepY))
            val piece = chess[x, y] ?: continue
            val point = geometry.center(BoardCoordinate(x, y))
            paint.textSize = geometry.stepX * 0.9f; paint.color = if (piece.side == PlayerSide.WHITE) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            drawContext.canvas.nativeCanvas.drawText(piece.symbol, point.x, point.y - (paint.ascent() + paint.descent()) / 2, paint)
        } else {
            fun point(x: Int, y: Int) = geometry.center(BoardCoordinate(x, y)).let { Offset(it.x, it.y) }
            for (x in 0 until count) drawLine(Color(0xFF80674B), point(x, 0), point(x, geometry.rows - 1), 0.8f)
            for (y in 0 until geometry.rows) drawLine(Color(0xFF80674B), point(0, y), point(count - 1, y), 0.8f)
            for (y in 0 until geometry.rows) for (x in 0 until count) {
                val side = xiangqi?.get(x, y)?.side ?: go?.get(x, y) ?: continue
                drawCircle(when (side) { PlayerSide.RED -> Color(0xFFB82828); PlayerSide.WHITE -> Color.White; else -> Color(0xFF24211C) }, geometry.stepX * 0.38f, point(x, y))
            }
        }
    }
}
