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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.chess.ChessPiece
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.ui.theme.BoardWoodDark
import com.yijing.chess.ui.theme.BoardWoodLight

@Composable
fun ChessBoardView(
    board: ChessBoard,
    selectedCoord: BoardCoordinate?,
    legalTargets: List<BoardCoordinate>,
    lastFrom: BoardCoordinate?,
    lastTo: BoardCoordinate?,
    isFlipped: Boolean = false,
    onSquareClick: (BoardCoordinate) -> Unit,
    modifier: Modifier = Modifier,
    options: BoardOptions = BoardOptions()
) {
    // 缓存 Paint 对象
    val coordinatePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val whitePiecePaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
    }
    val blackPiecePaint = remember {
        Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
    }
    val strokePaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF33271D))
            .padding(10.dp)
            .pointerInput(isFlipped) {
                detectTapGestures { offset ->
                    BoardGeometry(com.yijing.chess.model.GameType.CHESS, size.width.toFloat(), size.height.toFloat(), flipped = isFlipped)
                        .hit(offset.x, offset.y)?.let(onSquareClick)
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cellSize = size.width / 8f

            // 1. 绘制 8x8 黑白相间棋格
            for (y in 0..7) {
                for (x in 0..7) {
                    val rx = if (isFlipped) 7 - x else x
                    val ry = if (isFlipped) 7 - y else y
                    val isLight = (x + y) % 2 == 0
                    val squareColor = if (options.highContrast) { if (isLight) Color(0xFFFFF8E1) else Color(0xFF86664A) } else if (isLight) BoardWoodLight else BoardWoodDark

                    drawRect(
                        color = squareColor,
                        topLeft = Offset(rx * cellSize, ry * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                }
            }

            // 2. 绘制最后走步轨迹
            if (lastFrom != null) {
                val rx = if (isFlipped) 7 - lastFrom.x else lastFrom.x
                val ry = if (isFlipped) 7 - lastFrom.y else lastFrom.y
                drawRect(
                    color = Color(0x553F51B5),
                    topLeft = Offset(rx * cellSize, ry * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }
            if (lastTo != null) {
                val rx = if (isFlipped) 7 - lastTo.x else lastTo.x
                val ry = if (isFlipped) 7 - lastTo.y else lastTo.y
                drawRect(
                    color = Color(0x774CAF50),
                    topLeft = Offset(rx * cellSize, ry * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }

            // 3. 绘制选中格高亮
            if (selectedCoord != null) {
                val rx = if (isFlipped) 7 - selectedCoord.x else selectedCoord.x
                val ry = if (isFlipped) 7 - selectedCoord.y else selectedCoord.y
                drawRect(
                    color = Color(0x88FFD700),
                    topLeft = Offset(rx * cellSize, ry * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }

            // 4. 绘制棋子 (复用 Paint 对象)
            val textSize = cellSize * 0.82f
            whitePiecePaint.textSize = textSize
            blackPiecePaint.textSize = textSize
            strokePaint.textSize = textSize

            drawContext.canvas.nativeCanvas.apply {
                for (y in 0..7) {
                    for (x in 0..7) {
                        val piece = board[x, y] ?: continue
                        val rx = if (isFlipped) 7 - x else x
                        val ry = if (isFlipped) 7 - y else y
                        val cx = rx * cellSize + cellSize / 2f
                        val cy = ry * cellSize + cellSize / 2f

                        val isWhite = piece.side == PlayerSide.WHITE
                        val fillPaint = if (isWhite) whitePiecePaint else blackPiecePaint
                        strokePaint.color = if (isWhite) android.graphics.Color.DKGRAY else android.graphics.Color.WHITE

                        val textY = cy - (fillPaint.descent() + fillPaint.ascent()) / 2f
                        drawText(piece.symbol, cx, textY, strokePaint)
                        drawText(piece.symbol, cx, textY, fillPaint)
                    }
                }
            }

            // 5. 绘制合法落子点圆圈
            for (target in legalTargets) {
                val rx = if (isFlipped) 7 - target.x else target.x
                val ry = if (isFlipped) 7 - target.y else target.y
                val cx = rx * cellSize + cellSize / 2f
                val cy = ry * cellSize + cellSize / 2f
                val hasPiece = board[target] != null

                if (hasPiece) {
                    drawCircle(
                        color = Color(0xCCFF3D00),
                        radius = cellSize * 0.44f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                } else {
                    drawCircle(
                        color = Color(0x992E7D32),
                        radius = cellSize * 0.16f,
                        center = Offset(cx, cy)
                    )
                }
            }
            drawBoardOverlays(BoardGeometry(com.yijing.chess.model.GameType.CHESS, size.width, size.height, flipped = isFlipped), options, coordinatePaint)
        }
        BoardAccessibility(com.yijing.chess.model.GameType.CHESS, 8, isFlipped, selectedCoord, legalTargets,
            { coord -> board[coord]?.let { "${it.side.displayName}${when (it.type) { com.yijing.chess.chess.ChessPieceType.KING -> "王"; com.yijing.chess.chess.ChessPieceType.QUEEN -> "后"; com.yijing.chess.chess.ChessPieceType.ROOK -> "车"; com.yijing.chess.chess.ChessPieceType.BISHOP -> "象"; com.yijing.chess.chess.ChessPieceType.KNIGHT -> "马"; else -> "兵" }}" } ?: "空格" },
            onSquareClick, Modifier.matchParentSize())
    }
}
