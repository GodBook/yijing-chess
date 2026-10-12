package com.yijing.chess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yijing.chess.chess.ChessPiece
import com.yijing.chess.chess.ChessPieceType
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.ui.theme.PrimaryGold
import com.yijing.chess.ui.theme.XiangqiBlackColor
import com.yijing.chess.ui.theme.XiangqiRedColor
import com.yijing.chess.xiangqi.XiangqiPiece
import com.yijing.chess.xiangqi.XiangqiPieceType

@Composable
fun XiangqiPieceRack(
    selectedPiece: XiangqiPiece?,
    isEraserMode: Boolean,
    onSelectPiece: (XiangqiPiece?) -> Unit,
    onToggleEraser: () -> Unit,
    onClearBoard: () -> Unit,
    onResetBoard: () -> Unit,
    onStartBattle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "摆谱托盘 (点击选择棋子放置到棋盘，或使用橡皮擦清除)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 红方棋子池
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XiangqiPieceType.values().forEach { type ->
                    val piece = XiangqiPiece(PlayerSide.RED, type)
                    val isSelected = !isEraserMode && selectedPiece == piece
                    PieceChip(
                        text = piece.displayName,
                        textColor = XiangqiRedColor,
                        isSelected = isSelected,
                        onClick = { onSelectPiece(piece) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 黑方棋子池
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XiangqiPieceType.values().forEach { type ->
                    val piece = XiangqiPiece(PlayerSide.BLACK, type)
                    val isSelected = !isEraserMode && selectedPiece == piece
                    PieceChip(
                        text = piece.displayName,
                        textColor = XiangqiBlackColor,
                        isSelected = isSelected,
                        onClick = { onSelectPiece(piece) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onToggleEraser,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isEraserMode) PrimaryGold.copy(alpha = 0.2f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "橡皮擦", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEraserMode) "擦除中" else "橡皮擦", fontSize = 12.sp)
                    }

                    OutlinedButton(onClick = onClearBoard) {
                        Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清空", fontSize = 12.sp)
                    }

                    OutlinedButton(onClick = onResetBoard) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置初始", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("初始", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onStartBattle,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "由此开始对战", tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("开局对战", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ChessPieceRack(
    selectedPiece: ChessPiece?,
    isEraserMode: Boolean,
    onSelectPiece: (ChessPiece?) -> Unit,
    onToggleEraser: () -> Unit,
    onClearBoard: () -> Unit,
    onResetBoard: () -> Unit,
    onStartBattle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "摆谱托盘 (点击选择棋子放置到棋盘)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 白方棋子
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChessPieceType.values().forEach { type ->
                    val piece = ChessPiece(PlayerSide.WHITE, type)
                    val isSelected = !isEraserMode && selectedPiece == piece
                    PieceChip(
                        text = piece.symbol,
                        textColor = Color.White,
                        isSelected = isSelected,
                        onClick = { onSelectPiece(piece) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 黑方棋子
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChessPieceType.values().forEach { type ->
                    val piece = ChessPiece(PlayerSide.BLACK, type)
                    val isSelected = !isEraserMode && selectedPiece == piece
                    PieceChip(
                        text = piece.symbol,
                        textColor = Color.Black,
                        isSelected = isSelected,
                        onClick = { onSelectPiece(piece) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onToggleEraser,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isEraserMode) PrimaryGold.copy(alpha = 0.2f) else Color.Transparent
                        )
                    ) {
                        Text(if (isEraserMode) "擦除中" else "清除格", fontSize = 12.sp)
                    }

                    OutlinedButton(onClick = onClearBoard) {
                        Text("清空", fontSize = 12.sp)
                    }

                    OutlinedButton(onClick = onResetBoard) {
                        Text("初始", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onStartBattle,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Text("开局对战", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PieceChip(
    text: String,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isSelected) PrimaryGold else Color(0xFFF7E6C4))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) Color.White else Color(0x66000000),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
