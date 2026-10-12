package com.yijing.chess.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.yijing.chess.model.GameType
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.MoveTreeNode
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.parser.PgnParser
import com.yijing.chess.storage.GameSaveManager
import com.yijing.chess.storage.SavedGameState
import com.yijing.chess.ui.components.GameControlBar
import com.yijing.chess.ui.components.XiangqiBoardView
import com.yijing.chess.ui.theme.XiangqiBlackColor
import com.yijing.chess.ui.theme.XiangqiRedColor
import com.yijing.chess.xiangqi.XiangqiAI
import com.yijing.chess.xiangqi.XiangqiBoard
import com.yijing.chess.xiangqi.XiangqiPiece
import com.yijing.chess.xiangqi.XiangqiPieceType
import com.yijing.chess.xiangqi.XiangqiRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiangqiScreen(
    initialMode: GameMode = GameMode.PVE,
    onBack: () -> Unit,
    onGameStateChanged: ((Boolean, (() -> Unit)?) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val saveManager = remember { GameSaveManager(context) }

    var mode by remember { mutableStateOf(initialMode) }
    var aiDifficulty by remember { mutableStateOf(2) } // 1: 入门, 2: 进阶, 3: 大师
    var isFlipped by remember { mutableStateOf(false) }
    var isAiThinking by remember { mutableStateOf(false) }
    var aiJob by remember { mutableStateOf<Job?>(null) }

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

    // 退出保存弹窗控制
    var showExitSaveDialog by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasSavedGameAvailable by remember { mutableStateOf(saveManager.hasSavedGame(GameType.XIANGQI)) }

    fun saveCurrentGame() {
        val state = SavedGameState(
            gameType = GameType.XIANGQI,
            mode = mode,
            difficulty = aiDifficulty,
            boardState = board.toFen(),
            turn = board.turn,
            moveHistoryJson = saveManager.serializeMoveHistory(moveHistory.toList())
        )
        saveManager.saveGame(state)
        hasSavedGameAvailable = true
        Toast.makeText(context, "象棋对局已保存", Toast.LENGTH_SHORT).show()
    }

    androidx.compose.runtime.LaunchedEffect(moveHistory.size, checkmateWinner, mode) {
        val active = mode != GameMode.EDITOR && moveHistory.isNotEmpty() && checkmateWinner == null
        onGameStateChanged?.invoke(active) {
            saveCurrentGame()
        }
    }

    fun restoreSavedGame() {
        val saved = saveManager.getSavedGame(GameType.XIANGQI) ?: return
        aiJob?.cancel()
        isAiThinking = false
        board = XiangqiBoard.fromFen(saved.boardState)
        mode = saved.mode
        aiDifficulty = saved.difficulty
        boardHistory.clear()
        moveHistory.clear()
        val historyList = saveManager.deserializeMoveHistory(saved.moveHistoryJson)
        moveHistory.addAll(historyList)
        lastFrom = historyList.lastOrNull()?.from
        lastTo = historyList.lastOrNull()?.to
        selectedCoord = null
        legalTargets = emptyList()
        checkmateWinner = null
        Toast.makeText(context, "已恢复上次象棋对局 (${historyList.size}步)", Toast.LENGTH_SHORT).show()
    }

    fun attemptExit(action: () -> Unit) {
        if (mode != GameMode.EDITOR && moveHistory.isNotEmpty() && checkmateWinner == null) {
            pendingExitAction = action
            showExitSaveDialog = true
        } else {
            action()
        }
    }

    BackHandler {
        attemptExit(onBack)
    }

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
            aiJob?.cancel()
            isAiThinking = true
            aiJob = coroutineScope.launch {
                try {
                    val decision = XiangqiAI.findBestMove(board.copy(), PlayerSide.BLACK, difficulty = aiDifficulty)
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
                } finally {
                    isAiThinking = false
                }
            }
        }
    }

    fun handleSquareClick(coord: BoardCoordinate) {
        if (isAiThinking) return

        if (mode == GameMode.EDITOR) {
            if (isEraserMode) {
                board[coord] = null
            } else if (editorPiece != null) {
                board[coord] = editorPiece
            }
            board = board.copy()
            return
        }

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
                selectedCoord = coord
                refreshLegalTargets()
            } else if (legalTargets.contains(coord)) {
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
        aiJob?.cancel()
        isAiThinking = false
        if (mode == GameMode.PVE) {
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
            val decision = XiangqiAI.findBestMove(board.copy(), board.turn, difficulty = aiDifficulty)
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
                                GameMode.PVE -> "人机对决 (${if (aiDifficulty == 1) "初级" else if (aiDifficulty == 2) "中级" else "大师"})"
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
                    IconButton(onClick = { attemptExit(onBack) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (hasSavedGameAvailable) {
                        IconButton(onClick = ::restoreSavedGame) {
                            Icon(Icons.Default.Restore, contentDescription = "恢复棋局", tint = MaterialTheme.colorScheme.primary)
                        }
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
                    .padding(vertical = 2.dp),
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

            // 状态指示条 (定高 38dp，布局绝不跳动)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
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
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (XiangqiRule.isKingInCheck(board, board.turn)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("【将军!】", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }

                if (isAiThinking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("思考中...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
            }

            // 棋盘视图 (完全消除中间突变闪烁)
            XiangqiBoardView(
                board = board,
                selectedCoord = selectedCoord,
                legalTargets = legalTargets,
                lastFrom = lastFrom,
                lastTo = lastTo,
                isFlipped = isFlipped,
                onSquareClick = ::handleSquareClick
            )

            // 摆谱托盘
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
                    onResetInitial = {
                        board = XiangqiBoard.initialBoard()
                        selectedCoord = null
                        refreshLegalTargets()
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 底部控制操作栏
            GameControlBar(
                canUndo = boardHistory.isNotEmpty() && !isAiThinking,
                isEditorMode = mode == GameMode.EDITOR,
                onUndo = ::handleUndo,
                onHint = ::handleHint,
                onFlip = { isFlipped = !isFlipped },
                onToggleEditor = {
                    mode = if (mode == GameMode.EDITOR) GameMode.PVE else GameMode.EDITOR
                    selectedCoord = null
                    refreshLegalTargets()
                },
                onRestart = {
                    aiJob?.cancel()
                    isAiThinking = false
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
                    val pgn = PgnParser.exportXiangqiPgn(rootTreeNode.value, "弈境中国象棋对局")
                    exportDialogContent = pgn
                }
            )
        }
    }

    // 棋谱导出弹窗
    exportDialogContent?.let { pgnText ->
        AlertDialog(
            onDismissRequest = { exportDialogContent = null },
            title = { Text("象棋 PGN 棋谱导出") },
            text = { Text(pgnText, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(pgnText))
                    Toast.makeText(context, "已复制棋谱到剪贴板", Toast.LENGTH_SHORT).show()
                    exportDialogContent = null
                }) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = { exportDialogContent = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 将死胜负提示弹窗
    checkmateWinner?.let { winner ->
        AlertDialog(
            onDismissRequest = { checkmateWinner = null },
            title = { Text("对局结束") },
            text = { Text(if (winner == PlayerSide.RED) "红方胜！黑方已被将死。" else "黑方胜！红方已被将死。") },
            confirmButton = {
                TextButton(onClick = {
                    saveManager.clearSavedGame(GameType.XIANGQI)
                    hasSavedGameAvailable = false
                    board = XiangqiBoard.initialBoard()
                    boardHistory.clear()
                    moveHistory.clear()
                    selectedCoord = null
                    legalTargets = emptyList()
                    lastFrom = null
                    lastTo = null
                    checkmateWinner = null
                }) {
                    Text("再来一局")
                }
            },
            dismissButton = {
                TextButton(onClick = { checkmateWinner = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 退出保存确认弹窗
    if (showExitSaveDialog) {
        AlertDialog(
            onDismissRequest = { showExitSaveDialog = false },
            title = { Text("保存象棋棋局") },
            text = { Text("当前对局尚未结束，是否保存当前棋局？下次可继续对弈。") },
            confirmButton = {
                TextButton(onClick = {
                    saveCurrentGame()
                    showExitSaveDialog = false
                    pendingExitAction?.invoke()
                }) {
                    Text("保存并退出")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitSaveDialog = false
                        saveManager.clearSavedGame(GameType.XIANGQI)
                        hasSavedGameAvailable = false
                        pendingExitAction?.invoke()
                    }) {
                        Text("不保存")
                    }
                    TextButton(onClick = { showExitSaveDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}

@Composable
private fun XiangqiPieceRack(
    selectedPiece: XiangqiPiece?,
    isEraserMode: Boolean,
    onSelectPiece: (XiangqiPiece) -> Unit,
    onToggleEraser: () -> Unit,
    onClearBoard: () -> Unit,
    onResetInitial: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("红方棋子：", fontSize = 12.sp, color = XiangqiRedColor, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf(
                    XiangqiPieceType.ROOK, XiangqiPieceType.KNIGHT, XiangqiPieceType.CANNON,
                    XiangqiPieceType.BISHOP, XiangqiPieceType.ADVISOR, XiangqiPieceType.KING, XiangqiPieceType.PAWN
                ).forEach { type ->
                    val piece = XiangqiPiece(PlayerSide.RED, type)
                    PieceRackItem(
                        piece = piece,
                        isSelected = !isEraserMode && selectedPiece?.type == type && selectedPiece.side == PlayerSide.RED,
                        onClick = { onSelectPiece(piece) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text("黑方棋子：", fontSize = 12.sp, color = XiangqiBlackColor, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf(
                    XiangqiPieceType.ROOK, XiangqiPieceType.KNIGHT, XiangqiPieceType.CANNON,
                    XiangqiPieceType.BISHOP, XiangqiPieceType.ADVISOR, XiangqiPieceType.KING, XiangqiPieceType.PAWN
                ).forEach { type ->
                    val piece = XiangqiPiece(PlayerSide.BLACK, type)
                    PieceRackItem(
                        piece = piece,
                        isSelected = !isEraserMode && selectedPiece?.type == type && selectedPiece.side == PlayerSide.BLACK,
                        onClick = { onSelectPiece(piece) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isEraserMode,
                    onClick = onToggleEraser,
                    label = { Text("橡皮擦 (移除)") }
                )
                OutlinedButton(
                    onClick = onClearBoard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空棋盘", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onResetInitial,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("初始布局", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PieceRackItem(
    piece: XiangqiPiece,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFFFD700) else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = piece.displayName,
            color = if (piece.side == PlayerSide.RED) XiangqiRedColor else XiangqiBlackColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}
