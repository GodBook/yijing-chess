package com.yijing.chess.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.ui.theme.BoardLineColor
import com.yijing.chess.ui.theme.BoardWoodBg
import com.yijing.chess.ui.theme.XiangqiBlackColor
import com.yijing.chess.ui.theme.XiangqiRedColor
import com.yijing.chess.xiangqi.XiangqiBoard
import com.yijing.chess.xiangqi.XiangqiPiece

@Composable
fun XiangqiBoardView(
    board: XiangqiBoard,
    selectedCoord: BoardCoordinate?,
    legalTargets: List<BoardCoordinate>,
    lastFrom: BoardCoordinate?,
    lastTo: BoardCoordinate?,
    isFlipped: Boolean = false,
    onSquareClick: (BoardCoordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 10.3f)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(BoardWoodBg)
            .padding(12.dp)
            .pointerInput(isFlipped, board) {
                detectTapGestures { offset ->
                    val cellW = size.width / 9f
                    val cellH = size.height / 10f
                    var col = (offset.x / cellW).toInt().coerceIn(0, 8)
                    var row = (offset.y / cellH).toInt().coerceIn(0, 9)
                    if (isFlipped) {
                        col = 8 - col
                        row = 9 - row
                    }
                    onSquareClick(BoardCoordinate(col, row))
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cellW = size.width / 8.8f
            val cellH = size.height / 9.8f
            val startX = size.width * 0.055f
            val startY = size.height * 0.05f

            fun getPos(x: Int, y: Int): Offset {
                val rx = if (isFlipped) 8 - x else x
                val ry = if (isFlipped) 9 - y else y
                return Offset(startX + rx * cellW, startY + ry * cellH)
            }

            // 1. 绘制横线
            for (y in 0..9) {
                val p1 = getPos(0, y)
                val p2 = getPos(8, y)
                drawLine(
                    color = BoardLineColor,
                    start = p1,
                    end = p2,
                    strokeWidth = 2.5f
                )
            }

            // 2. 绘制竖线 (楚河汉界隔开)
            for (x in 0..8) {
                // 上半场 (y=0..4)
                val top1 = getPos(x, 0)
                val top2 = getPos(x, 4)
                drawLine(color = BoardLineColor, start = top1, end = top2, strokeWidth = 2.5f)

                // 下半场 (y=5..9)
                val bot1 = getPos(x, 5)
                val bot2 = getPos(x, 9)
                drawLine(color = BoardLineColor, start = bot1, end = bot2, strokeWidth = 2.5f)

                // 左右两边边界竖线穿过楚河汉界
                if (x == 0 || x == 8) {
                    val river1 = getPos(x, 4)
                    val river2 = getPos(x, 5)
                    drawLine(color = BoardLineColor, start = river1, end = river2, strokeWidth = 2.5f)
                }
            }

            // 3. 绘制九宫格斜线
            // 黑方九宫 (y: 0..2, x: 3..5)
            drawLine(BoardLineColor, getPos(3, 0), getPos(5, 2), strokeWidth = 2f)
            drawLine(BoardLineColor, getPos(5, 0), getPos(3, 2), strokeWidth = 2f)
            // 红方九宫 (y: 7..9, x: 3..5)
            drawLine(BoardLineColor, getPos(3, 7), getPos(5, 9), strokeWidth = 2f)
            drawLine(BoardLineColor, getPos(5, 7), getPos(3, 9), strokeWidth = 2f)

            // 4. 绘制楚河汉界书法文字
            drawContext.canvas.nativeCanvas.apply {
                val textPaint = Paint().apply {
                    color = android.graphics.Color.rgb(74, 55, 40)
                    textSize = cellH * 0.42f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.SERIF
                }
                val riverY = (getPos(0, 4).y + getPos(0, 5).y) / 2f + (cellH * 0.15f)
                val text1 = if (isFlipped) "汉 界" else "楚 河"
                val text2 = if (isFlipped) "楚 河" else "汉 界"
                drawText(text1, startX + 2 * cellW, riverY, textPaint)
                drawText(text2, startX + 6 * cellW, riverY, textPaint)
            }

            // 5. 绘制上一步标记 (走子轨迹高亮)
            if (lastFrom != null) {
                val p = getPos(lastFrom.x, lastFrom.y)
                drawCircle(Color(0x553F51B5), radius = cellW * 0.42f, center = p)
            }
            if (lastTo != null) {
                val p = getPos(lastTo.x, lastTo.y)
                drawCircle(Color(0x884CAF50), radius = cellW * 0.45f, center = p, style = Stroke(width = 4f))
            }

            // 6. 绘制所有棋子
            val pieceRadius = cellW * 0.44f
            for (y in 0..9) {
                for (x in 0..8) {
                    val piece = board[x, y] ?: continue
                    val center = getPos(x, y)
                    drawXiangqiPiece(center, pieceRadius, piece)
                }
            }

            // 7. 绘制选中棋子的高亮光环
            if (selectedCoord != null) {
                val selCenter = getPos(selectedCoord.x, selectedCoord.y)
                drawCircle(
                    color = Color(0xFFFFD700),
                    radius = pieceRadius + 3.dp.toPx(),
                    center = selCenter,
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }

            // 8. 绘制合法目标落子点指示圆圈
            for (target in legalTargets) {
                val targetCenter = getPos(target.x, target.y)
                val hasPiece = board[target] != null
                if (hasPiece) {
                    drawCircle(
                        color = Color(0xCCFF3D00),
                        radius = pieceRadius + 1.dp.toPx(),
                        center = targetCenter,
                        style = Stroke(width = 3.dp.toPx())
                    )
                } else {
                    drawCircle(
                        color = Color(0xAA2E7D32),
                        radius = pieceRadius * 0.28f,
                        center = targetCenter
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawXiangqiPiece(
    center: Offset,
    radius: Float,
    piece: XiangqiPiece
) {
    // 阴影
    drawCircle(
        color = Color(0x33000000),
        radius = radius + 1f,
        center = center + Offset(1.5f, 2.5f)
    )

    // 棋子木质底座 (象牙黄/温暖椴木色)
    drawCircle(
        color = Color(0xFFF7E6C4),
        radius = radius,
        center = center
    )

    // 外圈实线边框
    val pieceColor = if (piece.side == PlayerSide.RED) XiangqiRedColor else XiangqiBlackColor
    drawCircle(
        color = pieceColor,
        radius = radius,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // 内圈细线雕刻纹
    drawCircle(
        color = pieceColor,
        radius = radius * 0.85f,
        center = center,
        style = Stroke(width = 1.2f)
    )

    // 绘制棋子传统中文字样
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            color = if (piece.side == PlayerSide.RED) {
                android.graphics.Color.rgb(184, 40, 40)
            } else {
                android.graphics.Color.rgb(30, 33, 36)
            }
            textSize = radius * 1.2f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
        val textY = center.y - (paint.descent() + paint.ascent()) / 2
        drawText(piece.displayName, center.x, textY, paint)
    }
}
