package com.yijing.chess.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.yijing.chess.go.GoAI
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.GameMode
import com.yijing.chess.model.GameType
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.MoveTreeNode
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.parser.SgfParser
import com.yijing.chess.storage.GameSaveManager
import com.yijing.chess.storage.SavedGameState
import com.yijing.chess.ui.components.GameControlBar
import com.yijing.chess.ui.components.GoBoardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoScreen(
    initialMode: GameMode = GameMode.PVE,
    onBack: () -> Unit,
    onGameStateChanged: ((Boolean, (() -> Unit)?) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val saveManager = remember { GameSaveManager(context) }

    var boardSize by remember { mutableStateOf(19) }
    var mode by remember { mutableStateOf(initialMode) }
    var aiDifficulty by remember { mutableStateOf(2) } // 1: 初级, 2: 中级, 3: 大师
    var board by remember { mutableStateOf(GoBoard(size = 19)) }
    var lastMove by remember { mutableStateOf<BoardCoordinate?>(null) }
    var isAiThinking by remember { mutableStateOf(false) }
    var aiJob by remember { mutableStateOf<Job?>(null) }

    val boardHistory = remember { mutableStateListOf<GoBoard>() }
    val moveHistory = remember { mutableStateListOf<MoveRecord>() }
    val rootTreeNode = remember { mutableStateOf(MoveTreeNode(moveNumber = 0, move = null, fenBefore = "")) }
    var currentTreeNode by remember { mutableStateOf(rootTreeNode.value) }

    var editorStoneColor by remember { mutableStateOf(PlayerSide.BLACK) }
    var isEraserMode by remember { mutableStateOf(false) }
    var exportDialogContent by remember { mutableStateOf<String?>(null) }

    // 退出保存弹窗控制
    var showExitSaveDialog by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasSavedGameAvailable by remember { mutableStateOf(saveManager.hasSavedGame(GameType.GO)) }

    fun saveCurrentGame() {
        val state = SavedGameState(
            gameType = GameType.GO,
            mode = mode,
            difficulty = aiDifficulty,
            boardState = board.toStateString(),
            boardSize = boardSize,
            turn = board.turn,
            moveHistoryJson = saveManager.serializeMoveHistory(moveHistory.toList()),
            blackPrisoners = board.blackPrisoners,
            whitePrisoners = board.whitePrisoners
        )
        saveManager.saveGame(state)
        hasSavedGameAvailable = true
        Toast.makeText(context, "围棋对局已保存", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(moveHistory.size, mode) {
        val active = mode != GameMode.EDITOR && moveHistory.isNotEmpty()
        onGameStateChanged?.invoke(active) {
            saveCurrentGame()
        }
    }

    fun restoreSavedGame() {
        val saved = saveManager.getSavedGame(GameType.GO) ?: return
        aiJob?.cancel()
        isAiThinking = false
        boardSize = saved.boardSize
        board = GoBoard.fromStateString(
            size = saved.boardSize,
            state = saved.boardState,
            turn = saved.turn,
            blackPrisoners = saved.blackPrisoners,
            whitePrisoners = saved.whitePrisoners
        )
        mode = saved.mode
        aiDifficulty = saved.difficulty
        boardHistory.clear()
        moveHistory.clear()
        val historyList = saveManager.deserializeMoveHistory(saved.moveHistoryJson)
        moveHistory.addAll(historyList)
        lastMove = historyList.lastOrNull()?.to
        Toast.makeText(context, "已恢复上次围棋对局 (${historyList.size}步)", Toast.LENGTH_SHORT).show()
    }

    fun attemptExit(action: () -> Unit) {
        if (mode != GameMode.EDITOR && moveHistory.isNotEmpty()) {
            pendingExitAction = action
            showExitSaveDialog = true
        } else {
            action()
        }
    }

    BackHandler {
        attemptExit(onBack)
    }

    fun triggerAiMoveIfNeeded() {
        if (mode == GameMode.PVE && board.turn == PlayerSide.WHITE) {
            aiJob?.cancel()
            isAiThinking = true
            aiJob = coroutineScope.launch {
                try {
                    val bestMove = GoAI.findBestMove(board.copy(), PlayerSide.WHITE, difficulty = aiDifficulty)
                    if (bestMove != null) {
                        boardHistory.add(board.copy())
                        board.playMove(bestMove, PlayerSide.WHITE)
                        lastMove = bestMove
                        val record = MoveRecord(bestMove, bestMove, "白", "白[${bestMove.x},${bestMove.y}]")
                        moveHistory.add(record)
                        currentTreeNode = currentTreeNode.addVariation(record, "")
                        board = board.copy()
                    } else {
                        val ended = board.pass()
                        Toast.makeText(context, "AI 决定停着 (Pass)", Toast.LENGTH_SHORT).show()
                        board = board.copy()
                        if (ended) {
                            Toast.makeText(context, "双方连续停着，终局！", Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    isAiThinking = false
                }
            }
        }
    }

    fun handleIntersectionClick(coord: BoardCoordinate) {
        if (isAiThinking) return

        if (mode == GameMode.EDITOR) {
            if (isEraserMode) {
                board[coord] = null
            } else {
                board[coord] = editorStoneColor
            }
            board = board.copy()
            return
        }

        if (board.isMoveLegal(coord, board.turn)) {
            boardHistory.add(board.copy())
            val currentTurn = board.turn
            board.playMove(coord, currentTurn)
            lastMove = coord

            val record = MoveRecord(coord, coord, if (currentTurn == PlayerSide.BLACK) "黑" else "白", "${if (currentTurn == PlayerSide.BLACK) "黑" else "白"}[${coord.x},${coord.y}]")
            moveHistory.add(record)
            currentTreeNode = currentTreeNode.addVariation(record, "")

            board = board.copy()
            triggerAiMoveIfNeeded()
        } else {
            Toast.makeText(context, "禁着点或无气", Toast.LENGTH_SHORT).show()
        }
    }

    fun handlePass() {
        if (isAiThinking) return
        val ended = board.pass()
        board = board.copy()
        Toast.makeText(context, "您选择了停着 (Pass)", Toast.LENGTH_SHORT).show()
        if (ended) {
            Toast.makeText(context, "双方连续停着，终局！", Toast.LENGTH_LONG).show()
        } else {
            triggerAiMoveIfNeeded()
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
        lastMove = moveHistory.lastOrNull()?.to
        board = board.copy()
    }

    fun handleResetBoard(newSize: Int = boardSize) {
        aiJob?.cancel()
        isAiThinking = false
        boardSize = newSize
        board = GoBoard(size = newSize)
        boardHistory.clear()
        moveHistory.clear()
        lastMove = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("围棋", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "${boardSize}×${boardSize}路 · ${if (mode == GameMode.PVE) "人机对战 (${if (aiDifficulty == 1) "初级" else if (aiDifficulty == 2) "中级" else "大师"})" else if (mode == GameMode.PVP) "双人对弈" else "自由摆谱"}",
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
            // 棋盘路数与模式切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = boardSize == 19,
                        onClick = { handleResetBoard(19) },
                        label = { Text("19路", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = boardSize == 13,
                        onClick = { handleResetBoard(13) },
                        label = { Text("13路", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = boardSize == 9,
                        onClick = { handleResetBoard(9) },
                        label = { Text("9路", fontSize = 11.sp) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = mode == GameMode.PVE,
                        onClick = { mode = GameMode.PVE; triggerAiMoveIfNeeded() },
                        label = { Text("人机", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = mode == GameMode.PVP,
                        onClick = { mode = GameMode.PVP },
                        label = { Text("双人", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = mode == GameMode.EDITOR,
                        onClick = { mode = GameMode.EDITOR },
                        label = { Text("摆谱", fontSize = 11.sp) }
                    )
                }
            }

            // AI 难度切换栏 (人机模式下展示，非人机时占位保持布局稳定不跳动)
            if (mode == GameMode.PVE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI难度:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = aiDifficulty == 1,
                        onClick = { aiDifficulty = 1 },
                        label = { Text("初级", fontSize = 10.sp) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = aiDifficulty == 2,
                        onClick = { aiDifficulty = 2 },
                        label = { Text("中级", fontSize = 10.sp) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = aiDifficulty == 3,
                        onClick = { aiDifficulty = 3 },
                        label = { Text("大师", fontSize = 10.sp) }
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(34.dp))
            }

            // 提子与轮次信息条 (定高 40dp，彻底杜绝折行与抖动跳动)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
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
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (board.turn == PlayerSide.BLACK) Color.Black else Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (board.turn == PlayerSide.BLACK) "黑方走子" else "白方走子",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "提子: 黑${board.blackPrisoners} 白${board.whitePrisoners}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAiThinking) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        Text("思考中", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    if (mode != GameMode.EDITOR) {
                        OutlinedButton(
                            onClick = ::handlePass,
                            enabled = !isAiThinking,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("停着(Pass)", fontSize = 10.sp)
                        }
                    }
                }
            }

            // 围棋棋盘 (稳定无跳跃)
            GoBoardView(
                board = board,
                lastMove = lastMove,
                onIntersectionClick = ::handleIntersectionClick
            )

            // 摆谱工具条
            if (mode == GameMode.EDITOR) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isEraserMode && editorStoneColor == PlayerSide.BLACK,
                        onClick = { editorStoneColor = PlayerSide.BLACK; isEraserMode = false },
                        label = { Text("放置黑子") }
                    )
                    FilterChip(
                        selected = !isEraserMode && editorStoneColor == PlayerSide.WHITE,
                        onClick = { editorStoneColor = PlayerSide.WHITE; isEraserMode = false },
                        label = { Text("放置白子") }
                    )
                    FilterChip(
                        selected = isEraserMode,
                        onClick = { isEraserMode = true },
                        label = { Text("橡皮擦") }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 控制栏
            GameControlBar(
                canUndo = boardHistory.isNotEmpty() && !isAiThinking,
                isEditorMode = mode == GameMode.EDITOR,
                onUndo = ::handleUndo,
                onHint = {
                    coroutineScope.launch {
                        val best = GoAI.findBestMove(board.copy(), board.turn, difficulty = aiDifficulty)
                        if (best != null) {
                            Toast.makeText(context, "推荐落子：(${best.x + 1}, ${best.y + 1})", Toast.LENGTH_SHORT).show()
                            lastMove = best
                        }
                    }
                },
                onFlip = {},
                onToggleEditor = {
                    mode = if (mode == GameMode.EDITOR) GameMode.PVE else GameMode.EDITOR
                },
                onRestart = { handleResetBoard() },
                onExport = {
                    val sgf = SgfParser.exportToSgf(rootTreeNode.value, "弈境围棋对局", boardSize)
                    exportDialogContent = sgf
                }
            )
        }
    }

    // 棋谱导出弹窗
    exportDialogContent?.let { sgfText ->
        AlertDialog(
            onDismissRequest = { exportDialogContent = null },
            title = { Text("围棋 SGF 棋谱导出") },
            text = { Text(sgfText, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(sgfText))
                    Toast.makeText(context, "已复制 SGF 棋谱到剪贴板", Toast.LENGTH_SHORT).show()
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

    // 退出保存确认弹窗
    if (showExitSaveDialog) {
        AlertDialog(
            onDismissRequest = { showExitSaveDialog = false },
            title = { Text("保存围棋棋局") },
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
                        saveManager.clearSavedGame(GameType.GO)
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
