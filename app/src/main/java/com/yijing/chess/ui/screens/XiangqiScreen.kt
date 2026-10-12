package com.yijing.chess.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.GameMode
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.MoveTreeNode
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.parser.SgfParser
import com.yijing.chess.ui.components.GameControlBar
import com.yijing.chess.ui.components.XiangqiBoardView
import com.yijing.chess.ui.components.XiangqiPieceRack
import com.yijing.chess.ui.theme.PrimaryGold
import com.yijing.chess.ui.theme.XiangqiBlackColor
import com.yijing.chess.ui.theme.XiangqiRedColor
import com.yijing.chess.xiangqi.XiangqiAI
import com.yijing.chess.xiangqi.XiangqiBoard
import com.yijing.chess.xiangqi.XiangqiPiece
import com.yijing.chess.xiangqi.XiangqiRule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiangqiScreen(
    initialMode: GameMode = GameMode.PVE,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(initialMode) }
    var aiDifficulty by remember { mutableStateOf(2) } // 1: 入门, 2: 进阶, 3: 大师
    var isFlipped by remember { mutableStateOf(false) }
    var isAiThinking by remember { mutableStateOf(false) }

    var board by remember { mutableStateOf(XiangqiBoard.initialBoard()) }
    var selectedCoord by remember { mutableStateOf<BoardCoordinate?>(null) }
    var legalTargets by remember { mutableStateOf<List<BoardCoordinate>>(emptyList()) }
    var lastFrom by remember { mutableStateOf<BoardCoordinate?>(null) }
    var lastTo by remember { mutableStateOf<BoardCoordinate?>(null) }

    // 历史走步与撤销快照
    val moveHistory = remember { mutableStateListOf<MoveRecord>() }
    val boardHistory = remember { mutableStateListOf<XiangqiBoard>() }
    val rootTreeNode = remember { mutableStateOf(MoveTreeNode(moveNumber = 0, move = null, fenBefore = board.toFen())) }
    var currentTreeNode by remember { mutableStateOf(rootTreeNode.value) }

    // 摆谱编辑器状态
    var editorPiece by remember { mutableStateOf<XiangqiPiece?>(null) }
    var isEraserMode by remember { mutableStateOf(false) }

    // 提示或胜负弹窗
    var exportDialogContent by remember { mutableStateOf<String?>(null) }
    var checkmateWinner by remember { mutableStateOf<PlayerSide?>(null) }

    fun refreshLegalTargets() {
        val sel = selectedCoord
        if (sel == null) {
            legalTargets = emptyList()
        } else {
            val legalMoves = XiangqiRule.getLegalMoves(board, board.turn)
            legalTargets = legalMoves.filter { it.from == sel }.map { it.to }
        }
    }

    // AI 自动走子触发
    fun triggerAiMoveIfNeeded() {
        if (mode == GameMode.PVE && board.turn == PlayerSide.BLACK && checkmateWinner == null) {
            isAiThinking = true
            coroutineScope.launch {
                val decision = XiangqiAI.findBestMove(board, PlayerSide.BLACK, depth = aiDifficulty + 1)
                isAiThinking = false
                val bestMove = decision.move
                if (bestMove != null) {
                    boardHistory.add(board.copy())
                    val moved = board[bestMove.from]!!
                    board[bestMove.to] = moved
                    board[bestMove.from] = null
                    board.turn = PlayerSide.RED

                    lastFrom = bestMove.from
                    lastTo = bestMove.to
                    moveHistory.add(bestMove)
                    currentTreeNode = currentTreeNode.addVariation(bestMove, board.toFen())

                    // 检查红方是否被将死
                    val nextLegals = XiangqiRule.getLegalMoves(board, PlayerSide.RED)
                    if (nextLegals.isEmpty()) {
                        checkmateWinner = if (XiangqiRule.isKingInCheck(board, PlayerSide.RED)) PlayerSide.BLACK else null
                    }
                    board = board.copy()
                } else {
                    checkmateWinner = PlayerSide.RED
                }
            }
        }
    }

    fun handleSquareClick(coord: BoardCoordinate) {
        if (isAiThinking) return

        if (mode == GameMode.EDITOR) {
            // 摆谱模式：点击放置或移除
            if (isEraserMode) {
                board[coord] = null
            } else if (editorPiece != null) {
                board[coord] = editorPiece
            }
            board = board.copy()
            return
        }

        // 正常对弈模式
        val pieceAtCoord = board[coord]
        if (selectedCoord == null) {
            if (pieceAtCoord != null && pieceAtCoord.side == board.turn) {
                selectedCoord = coord
                refreshLegalTargets()
            }
        } else {
            val from = selectedCoord!!
            if (coord == from) {
                selectedCoord = null
                refreshLegalTargets()
            } else if (pieceAtCoord != null && pieceAtCoord.side == board.turn) {
                // 切换选中的己方棋子
                selectedCoord = coord
                refreshLegalTargets()
            } else if (legalTargets.contains(coord)) {
                // 执行合法走步
                val legalMoves = XiangqiRule.getLegalMoves(board, board.turn)
                val targetMove = legalMoves.find { it.from == from && it.to == coord }
                if (targetMove != null) {
                    boardHistory.add(board.copy())
                    val piece = board[from]!!
                    board[coord] = piece
                    board[from] = null
                    board.turn = board.turn.xiangqiOpposite

                    lastFrom = from
                    lastTo = coord
                    selectedCoord = null
                    refreshLegalTargets()

                    moveHistory.add(targetMove)
                    currentTreeNode = currentTreeNode.addVariation(targetMove, board.toFen())

                    // 检查下一方是否被将死
                    val nextLegals = XiangqiRule.getLegalMoves(board, board.turn)
                    if (nextLegals.isEmpty()) {
                        checkmateWinner = if (XiangqiRule.isKingInCheck(board, board.turn)) board.turn.xiangqiOpposite else null
                    }
                    board = board.copy()

                    triggerAiMoveIfNeeded()
                }
            } else {
                selectedCoord = null
                refreshLegalTargets()
            }
        }
    }

    fun handleUndo() {
        if (boardHistory.isEmpty() || isAiThinking) return
        if (mode == GameMode.PVE) {
            // 人机模式一次撤销 2 步 (己方+AI)
            if (boardHistory.size >= 2) {
                board = boardHistory.removeAt(boardHistory.lastIndex)
                board = boardHistory.removeAt(boardHistory.lastIndex)
                moveHistory.removeAt(moveHistory.lastIndex)
                moveHistory.removeAt(moveHistory.lastIndex)
            } else {
                board = boardHistory.removeAt(boardHistory.lastIndex)
                moveHistory.removeAt(moveHistory.lastIndex)
            }
        } else {
            board = boardHistory.removeAt(boardHistory.lastIndex)
            moveHistory.removeAt(moveHistory.lastIndex)
        }
        selectedCoord = null
        refreshLegalTargets()
        lastFrom = moveHistory.lastOrNull()?.from
        lastTo = moveHistory.lastOrNull()?.to
        checkmateWinner = null
    }

    fun handleHint() {
        if (isAiThinking) return
        coroutineScope.launch {
            val decision = XiangqiAI.findBestMove(board, board.turn, depth = 3)
            val move = decision.move
            if (move != null) {
                Toast.makeText(context, "推荐招法：${move.notation}", Toast.LENGTH_SHORT).show()
                selectedCoord = move.from
                refreshLegalTargets()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("中国象棋", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = when (mode) {
                                GameMode.PVE -> "人机对决 (${if (aiDifficulty == 1) "入门" else if (aiDifficulty == 2) "进阶" else "大师"})"
                                GameMode.PVP -> "双人同屏对弈"
                                GameMode.EDITOR -> "自由排局摆谱"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 模式与难度切换栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == GameMode.PVE,
                        onClick = { mode = GameMode.PVE; triggerAiMoveIfNeeded() },
                        label = { Text("人机", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = mode == GameMode.PVP,
                        onClick = { mode = GameMode.PVP },
                        label = { Text("双人", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = mode == GameMode.EDITOR,
                        onClick = { mode = GameMode.EDITOR },
                        label = { Text("摆谱", fontSize = 12.sp) }
                    )
                }

                if (mode == GameMode.PVE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = aiDifficulty == 1,
                            onClick = { aiDifficulty = 1 },
                            label = { Text("初级", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = aiDifficulty == 2,
                            onClick = { aiDifficulty = 2 },
                            label = { Text("中级", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = aiDifficulty == 3,
                            onClick = { aiDifficulty = 3 },
                            label = { Text("大师", fontSize = 11.sp) }
                        )
                    }
                }
            }

            // 状态指示条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (board.turn == PlayerSide.RED) XiangqiRedColor else XiangqiBlackColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (board.turn == PlayerSide.RED) "轮到红方走子" else "轮到黑方走子",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    if (XiangqiRule.isKingInCheck(board, board.turn)) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("【将军!】", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                if (isAiThinking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI 思考中...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 棋盘视图
            XiangqiBoardView(
                board = board,
                selectedCoord = selectedCoord,
                legalTargets = legalTargets,
                lastFrom = lastFrom,
                lastTo = lastTo,
                isFlipped = isFlipped,
                onSquareClick = ::handleSquareClick
            )

            // 摆谱托盘或走步历史
            if (mode == GameMode.EDITOR) {
                XiangqiPieceRack(
                    selectedPiece = editorPiece,
                    isEraserMode = isEraserMode,
                    onSelectPiece = { editorPiece = it; isEraserMode = false },
                    onToggleEraser = { isEraserMode = !isEraserMode },
                    onClearBoard = {
                        board = XiangqiBoard()
                        selectedCoord = null
                        refreshLegalTargets()
                    },
                    onResetBoard = {
                        board = XiangqiBoard.initialBoard()
                        selectedCoord = null
                        refreshLegalTargets()
                    },
                    onStartBattle = {
                        mode = GameMode.PVE
                        triggerAiMoveIfNeeded()
                        Toast.makeText(context, "已从当前排局开启人机对战", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // 走步历史列表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (moveHistory.isEmpty()) {
                        Text("暂无走步记录", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        moveHistory.forEachIndexed { index, m ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. ${m.notation}",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部控制栏
            GameControlBar(
                canUndo = boardHistory.isNotEmpty() && !isAiThinking,
                isEditorMode = mode == GameMode.EDITOR,
                onUndo = ::handleUndo,
                onHint = ::handleHint,
                onFlip = { isFlipped = !isFlipped },
                onToggleEditor = {
                    mode = if (mode == GameMode.EDITOR) GameMode.PVE else GameMode.EDITOR
                },
                onRestart = {
                    board = XiangqiBoard.initialBoard()
                    boardHistory.clear()
                    moveHistory.clear()
                    selectedCoord = null
                    legalTargets = emptyList()
                    lastFrom = null
                    lastTo = null
                    checkmateWinner = null
                },
                onExport = {
                    val fen = board.toFen()
                    val sgf = SgfParser.exportToSgf(rootTreeNode.value, "中国象棋对局", 9)
                    exportDialogContent = "【FEN 局面串】\n$fen\n\n【SGF 棋谱】\n$sgf"
                }
            )
        }
    }

    // 棋谱导出对话框
    exportDialogContent?.let { contentStr ->
        AlertDialog(
            onDismissRequest = { exportDialogContent = null },
            title = { Text("棋谱与局面导出") },
            text = {
                Text(contentStr, fontSize = 12.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(contentStr))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    exportDialogContent = null
                }) {
                    Text("复制全部")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportDialogContent = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 终局胜负对话框
    checkmateWinner?.let { winner ->
        AlertDialog(
            onDismissRequest = { checkmateWinner = null },
            title = { Text("对局结束") },
            text = {
                Text("恭喜 ${winner.displayName} 获胜！", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            confirmButton = {
                TextButton(onClick = { checkmateWinner = null }) {
                    Text("继续研讨")
                }
            }
        )
    }
}
