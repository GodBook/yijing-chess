package com.yijing.chess.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.ui.theme.BoardLineColor
import com.yijing.chess.ui.theme.BoardWoodBg

@Composable
fun GoBoardView(
    board: GoBoard,
    lastMove: BoardCoordinate?,
    onIntersectionClick: (BoardCoordinate) -> Unit,
    modifier: Modifier = Modifier,
    isFlipped: Boolean = false,
    options: BoardOptions = BoardOptions()
) {
    val size = board.size
    val coordinatePaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(BoardWoodBg)
            .padding(8.dp)
            .pointerInput(size, isFlipped) {
                detectTapGestures { offset ->
                    BoardGeometry(com.yijing.chess.model.GameType.GO, this.size.width.toFloat(), this.size.height.toFloat(), size, isFlipped)
                        .hit(offset.x, offset.y)?.let(onIntersectionClick)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val geometry = BoardGeometry(com.yijing.chess.model.GameType.GO, this.size.width, this.size.height, size, isFlipped)
            val cellSize = geometry.stepX

            fun getPos(x: Int, y: Int): Offset {
                return geometry.center(BoardCoordinate(x, y)).let { Offset(it.x, it.y) }
            }

            // 1. 绘制网格线
            for (i in 0 until size) {
                // 横线
                drawLine(
                    color = BoardLineColor,
                    start = getPos(0, i),
                    end = getPos(size - 1, i),
                    strokeWidth = if (i == 0 || i == size - 1) 2.4f else 1.5f
                )
                // 竖线
                drawLine(
                    color = BoardLineColor,
                    start = getPos(i, 0),
                    end = getPos(i, size - 1),
                    strokeWidth = if (i == 0 || i == size - 1) 2.4f else 1.5f
                )
            }

            // 2. 绘制星位 (Hoshi)
            val starPoints = when (size) {
                19 -> listOf(
                    BoardCoordinate(3, 3), BoardCoordinate(9, 3), BoardCoordinate(15, 3),
                    BoardCoordinate(3, 9), BoardCoordinate(9, 9), BoardCoordinate(15, 9),
                    BoardCoordinate(3, 15), BoardCoordinate(9, 15), BoardCoordinate(15, 15)
                )
                13 -> listOf(
                    BoardCoordinate(3, 3), BoardCoordinate(9, 3),
                    BoardCoordinate(3, 9), BoardCoordinate(9, 9),
                    BoardCoordinate(6, 6)
                )
                9 -> listOf(
                    BoardCoordinate(2, 2), BoardCoordinate(6, 2),
                    BoardCoordinate(2, 6), BoardCoordinate(6, 6),
                    BoardCoordinate(4, 4)
                )
                else -> emptyList()
            }

            for (sp in starPoints) {
                drawCircle(
                    color = BoardLineColor,
                    radius = if (size == 19) 3.5f else 4.5f,
                    center = getPos(sp.x, sp.y)
                )
            }

            // 3. 绘制棋子
            val stoneRadius = cellSize * 0.47f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val stone = board[x, y] ?: continue
                    drawGoStone(getPos(x, y), stoneRadius, stone)
                }
            }

            // 4. 绘制最后一手标记
            if (lastMove != null) {
                val stone = board[lastMove]
                if (stone != null) {
                    val markerColor = if (stone == PlayerSide.BLACK) Color.White else Color.Black
                    drawCircle(
                        color = markerColor,
                        radius = stoneRadius * 0.28f,
                        center = getPos(lastMove.x, lastMove.y),
                        style = Stroke(width = 2f)
                    )
                }
            }
            drawBoardOverlays(geometry, options, coordinatePaint)
        }
        BoardAccessibility(com.yijing.chess.model.GameType.GO, size, isFlipped, options.tentative, listOfNotNull(options.hintTo),
            { coord -> board[coord]?.displayName?.plus("棋子") ?: "空交叉点" }, onIntersectionClick, Modifier.matchParentSize())
    }
}

private fun DrawScope.drawGoStone(
    center: Offset,
    radius: Float,
    side: PlayerSide
) {
    // 阴影
    drawCircle(
        color = Color(0x33000000),
        radius = radius,
        center = center + Offset(1.5f, 2f)
    )

    if (side == PlayerSide.BLACK) {
        // 黑子：墨玉黑微高光
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF424242), Color(0xFF181818), Color(0xFF000000)),
                center = center - Offset(radius * 0.3f, radius * 0.3f),
                radius = radius * 1.3f
            ),
            radius = radius,
            center = center
        )
    } else {
        // 白子：云子微乳白珍珠光泽
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFEDE8DF), Color(0xFFCCC5B8)),
                center = center - Offset(radius * 0.3f, radius * 0.3f),
                radius = radius * 1.3f
            ),
            radius = radius,
            center = center
        )
        // 白子边缘微线提升立体感
        drawCircle(
            color = Color(0x33000000),
            radius = radius,
            center = center,
            style = Stroke(width = 0.8f)
        )
    }
}
