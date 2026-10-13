package com.yijing.chess.ui.components

import android.graphics.Paint
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.yijing.chess.model.*
import com.yijing.chess.session.coordinateName
import kotlin.math.sqrt

data class BoardOptions(
    val highContrast: Boolean = false,
    val coordinates: Boolean = true,
    val hintFrom: BoardCoordinate? = null,
    val hintTo: BoardCoordinate? = null,
    val tentative: BoardCoordinate? = null,
    val dead: Set<BoardCoordinate> = emptySet()
)

@Composable
fun BoardAccessibility(type: GameType, size: Int, flipped: Boolean, selected: BoardCoordinate?, legal: List<BoardCoordinate>,
    label: (BoardCoordinate) -> String, onClick: (BoardCoordinate) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.semantics { collectionInfo = CollectionInfo(if (type == GameType.XIANGQI) 10 else if (type == GameType.CHESS) 8 else size,
        if (type == GameType.XIANGQI) 9 else if (type == GameType.CHESS) 8 else size) }) {
        val geometry = with(density) { BoardGeometry(type, maxWidth.toPx(), maxHeight.toPx(), size, flipped) }
        for (y in 0 until geometry.rows) for (x in 0 until geometry.columns) {
            val coord = BoardCoordinate(x, y); val point = geometry.center(coord)
            val left = (point.x - geometry.stepX / 2).coerceAtLeast(0f)
            val top = (point.y - geometry.stepY / 2).coerceAtLeast(0f)
            Box(Modifier.offset(with(density) { left.toDp() }, with(density) { top.toDp() })
                .size(with(density) { minOf(geometry.stepX, geometry.width - left).toDp() }, with(density) { minOf(geometry.stepY, geometry.height - top).toDp() })
                .testTag("point_${x}_$y")
                .semantics {
                    contentDescription = "${coordinateName(type, coord, size)}，${label(coord)}"
                    role = Role.Button
                    this.selected = coord == selected
                    stateDescription = when { coord == selected -> "已选中"; coord in legal -> "可以走到这里"; else -> "" }
                    collectionItemInfo = CollectionItemInfo(y, 1, x, 1)
                    onClick("选择此位置") { onClick(coord); true }
                }.focusable().onKeyEvent {
                    if (it.type == KeyEventType.KeyUp && it.key in listOf(Key.Enter, Key.DirectionCenter, Key.Spacebar)) { onClick(coord); true } else false
                })
        }
    }
}

fun DrawScope.drawBoardOverlays(geometry: BoardGeometry, options: BoardOptions, coordinatePaint: Paint) {
    fun point(coord: BoardCoordinate) = geometry.center(coord).let { Offset(it.x, it.y) }
    val radius = geometry.stepX * 0.37f
    val hintColor = Color(0xFF00695C)
    options.hintTo?.let { target ->
        val end = point(target)
        drawCircle(hintColor.copy(alpha = 0.28f), radius, end)
        drawCircle(hintColor, radius, end, style = Stroke(width = 3f))
        options.hintFrom?.takeIf { it != target }?.let { from ->
            val start = point(from); val delta = end - start; val length = sqrt(delta.x * delta.x + delta.y * delta.y)
            if (length > 0) {
                val direction = Offset(delta.x / length, delta.y / length)
                val tip = end - direction * (radius * 0.4f)
                drawLine(hintColor, start, tip, strokeWidth = geometry.stepX * 0.09f)
                val back = tip - direction * (radius * 0.6f); val normal = Offset(-direction.y, direction.x) * (radius * 0.35f)
                drawLine(hintColor, tip, back + normal, strokeWidth = geometry.stepX * 0.09f)
                drawLine(hintColor, tip, back - normal, strokeWidth = geometry.stepX * 0.09f)
            }
        }
    }
    options.tentative?.let { drawCircle(Color(0xAA00796B), geometry.stepX * 0.44f, point(it)); drawCircle(Color.White, geometry.stepX * 0.15f, point(it)) }
    for (coord in options.dead) {
        val center = point(coord); val offset = Offset(radius * 0.65f, radius * 0.65f)
        drawLine(Color(0xFFB71C1C), center - offset, center + offset, 4f)
        drawLine(Color(0xFFB71C1C), center + Offset(-offset.x, offset.y), center + Offset(offset.x, -offset.y), 4f)
    }
    if (options.coordinates) {
        coordinatePaint.textSize = minOf(geometry.stepX * 0.3f, 18f * density)
        coordinatePaint.color = android.graphics.Color.rgb(65, 48, 32)
        for (x in 0 until geometry.columns) {
            val coordinate = BoardCoordinate(x, geometry.rows - 1)
            val center = point(coordinate)
            val name = when (geometry.type) { GameType.CHESS -> ('a'.code + x).toChar().toString(); GameType.GO -> "ABCDEFGHJKLMNOPQRST"[x].toString(); GameType.XIANGQI -> (x + 1).toString() }
            val y = if (geometry.type == GameType.CHESS) center.y + geometry.stepY * 0.43f else if (geometry.flipped) coordinatePaint.textSize else geometry.height - 1f
            drawContext.canvas.nativeCanvas.drawText(name, center.x, y, coordinatePaint)
        }
        if (geometry.type != GameType.XIANGQI) for (y in 0 until geometry.rows) {
            val center = point(BoardCoordinate(0, y))
            val label = (geometry.rows - y).toString()
            val x = if (geometry.type == GameType.CHESS) center.x - geometry.stepX * 0.36f else if (geometry.flipped) geometry.width - coordinatePaint.textSize * 0.5f else coordinatePaint.textSize * 0.5f
            drawContext.canvas.nativeCanvas.drawText(label, x, center.y + coordinatePaint.textSize * 0.35f, coordinatePaint)
        }
    }
}
