package com.yijing.chess.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yijing.chess.chess.ChessAI
import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.chess.ChessPiece
import com.yijing.chess.chess.ChessPieceType
import com.yijing.chess.chess.ChessRule
import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.GameMode
import com.yijing.chess.model.GameType
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.MoveTreeNode
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.parser.PgnParser
import com.yijing.chess.storage.GameSaveManager
import com.yijing.chess.storage.SavedGameState
import com.yijing.chess.ui.components.ChessBoardView
import com.yijing.chess.ui.components.GameControlBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessScreen(
    initialMode: GameMode = GameMode.PVE,
    onBack: () -> Unit,
    onGameStateChanged: ((Boolean, (() -> Unit)?) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val saveManager = remember { GameSaveManager(context) }

    var mode by remember { mutableStateOf(initialMode) }
    var aiDifficulty by remember { mutableStateOf(2) } // 1: 初级, 2: 中级, 3: 大师
    var isFlipped by remember { mutableStateOf(false) }
    var isAiThinking by remember { mutableStateOf(false) }
    var aiJob by remember { mutableStateOf<Job?>(null) }

    var board by remember { mutableStateOf(ChessBoard.initialBoard()) }
    var selectedCoord by remember { mutableStateOf<BoardCoordinate?>(null) }
    var legalTargets by remember { mutableStateOf<List<BoardCoordinate>>(emptyList()) }
    var lastFrom by remember { mutableStateOf<BoardCoordinate?>(null) }
    var lastTo by remember { mutableStateOf<BoardCoordinate?>(null) }

    val moveHistory = remember { mutableStateListOf<MoveRecord>() }
    val boardHistory = remember { mutableStateListOf<ChessBoard>() }
    val rootTreeNode = remember { mutableStateOf(MoveTreeNode(moveNumber = 0, move = null, fenBefore = board.toFen())) }
    var currentTreeNode by remember { mutableStateOf(rootTreeNode.value) }

    var editorPiece by remember { mutableStateOf<ChessPiece?>(null) }
    var isEraserMode by remember { mutableStateOf(false) }
    var exportDialogContent by remember { mutableStateOf<String?>(null) }

    // 升变待选弹窗
    var pendingPromotionMove by remember { mutableStateOf<Pair<BoardCoordinate, BoardCoordinate>?>(null) }

    // 退出保存弹窗控制
    var showExitSaveDialog by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasSavedGameAvailable by remember { mutableStateOf(saveManager.hasSavedGame(GameType.CHESS)) }

    fun saveCurrentGame() {
        val state = SavedGameState(
            gameType = GameType.CHESS,
            mode = mode,
            difficulty = aiDifficulty,
            boardState = board.toFen(),
            turn = board.turn,
            moveHistoryJson = saveManager.serializeMoveHistory(moveHistory.toList())
        )
        saveManager.saveGame(state)
        hasSavedGameAvailable = true
        Toast.makeText(context, "国际象棋对局已保存", Toast.LENGTH_SHORT).show()
    }

    androidx.compose.runtime.LaunchedEffect(moveHistory.size, mode) {
        val active = mode != GameMode.EDITOR && moveHistory.isNotEmpty()
        onGameStateChanged?.invoke(active) {
            saveCurrentGame()
        }
    }

    fun restoreSavedGame() {
        val saved = saveManager.getSavedGame(GameType.CHESS) ?: return
        aiJob?.cancel()
        isAiThinking = false
        board = ChessBoard.fromFen(saved.boardState)
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
        Toast.makeText(context, "已恢复上次国际象棋对局 (${historyList.size}步)", Toast.LENGTH_SHORT).show()
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

    fun refreshLegalTargets() {
        val sel = selectedCoord
        if (sel == null) {
            legalTargets = emptyList()
        } else {
            val legals = ChessRule.getLegalMoves(board, board.turn)
            legalTargets = legals.filter { it.from == sel }.map { it.to }.distinct()
        }
    }

    fun triggerAiMoveIfNeeded() {
        if (mode == GameMode.PVE && board.turn == PlayerSide.BLACK) {
            aiJob?.cancel()
            isAiThinking = true
            aiJob = coroutineScope.launch {
                try {
                    val decision = ChessAI.findBestMove(board.copy(), PlayerSide.BLACK, difficulty = aiDifficulty)
                    val bestMove = decision.move
                    if (bestMove != null) {
                        boardHistory.add(board.copy())
                        val promo = bestMove.promotion?.let { runCatching { ChessPieceType.valueOf(it) }.getOrNull() }
                        val cand = ChessRule.CandidateChessMove(bestMove.from, bestMove.to, promo)
                        ChessRule.applyMove(board, cand)

                        lastFrom = bestMove.from
                        lastTo = bestMove.to
                        moveHistory.add(bestMove)
                        currentTreeNode = currentTreeNode.addVariation(bestMove, board.toFen())

                        board = board.copy()
                    } else {
                        Toast.makeText(context, "白方胜！黑方无合规走法", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isAiThinking = false
                }
            }
        }
    }

    fun executePlayerMove(from: BoardCoordinate, to: BoardCoordinate, promotionType: ChessPieceType? = null) {
        val legals = ChessRule.getLegalMoves(board, board.turn)
        val matched = legals.find {
            it.from == from && it.to == to && (promotionType == null || it.promotion == promotionType.name)
        } ?: return

        boardHistory.add(board.copy())
        val cand = ChessRule.CandidateChessMove(from, to, promotionType)
        ChessRule.applyMove(board, cand)

        lastFrom = from
        lastTo = to
        selectedCoord = null
        refreshLegalTargets()

        moveHistory.add(matched)
        currentTreeNode = currentTreeNode.addVariation(matched, board.toFen())

        board = board.copy()
        triggerAiMoveIfNeeded()
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

        val piece = board[coord]
        if (selectedCoord == null) {
            if (piece != null && piece.side == board.turn) {
                selectedCoord = coord
                refreshLegalTargets()
            }
        } else {
            val from = selectedCoord!!
            if (coord == from) {
                selectedCoord = null
                refreshLegalTargets()
            } else if (piece != null && piece.side == board.turn) {
                selectedCoord = coord
                refreshLegalTargets()
            } else if (legalTargets.contains(coord)) {
                val movingPiece = board[from]
                val isPawnPromotion = movingPiece?.type == ChessPieceType.PAWN &&
                        ((movingPiece.side == PlayerSide.WHITE && coord.y == 0) || (movingPiece.side == PlayerSide.BLACK && coord.y == 7))

                if (isPawnPromotion) {
                    pendingPromotionMove = Pair(from, coord)
                } else {
                    executePlayerMove(from, coord)
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("国际象棋", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = when (mode) {
                                GameMode.PVE -> "人机对战 (${if (aiDifficulty == 1) "初级" else if (aiDifficulty == 2) "中级" else "大师"})"
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
            // 模式选择与难度
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

            // 轮次与状态条 (定高 38dp，消除跳跃)
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
                    Surface(
                        shape = CircleShape,
                        color = if (board.turn == PlayerSide.WHITE) Color.White else Color.Black,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (board.turn == PlayerSide.WHITE) "轮到白方走子" else "轮到黑方走子",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (ChessRule.isKingInCheck(board, board.turn)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("【将王!】", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
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

            // 国际象棋棋盘
            ChessBoardView(
                board = board,
                selectedCoord = selectedCoord,
                legalTargets = legalTargets,
                lastFrom = lastFrom,
                lastTo = lastTo,
                isFlipped = isFlipped,
                onSquareClick = ::handleSquareClick
            )

            // 摆谱棋托
            if (mode == GameMode.EDITOR) {
                ChessPieceRack(
                    selectedPiece = editorPiece,
                    isEraserMode = isEraserMode,
                    onSelectPiece = { editorPiece = it; isEraserMode = false },
                    onToggleEraser = { isEraserMode = !isEraserMode },
                    onClearBoard = {
                        board = ChessBoard()
                        selectedCoord = null
                        refreshLegalTargets()
                    },
                    onResetInitial = {
                        board = ChessBoard.initialBoard()
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
                onHint = {
                    coroutineScope.launch {
                        val decision = ChessAI.findBestMove(board.copy(), board.turn, difficulty = aiDifficulty)
                        val move = decision.move
                        if (move != null) {
                            Toast.makeText(context, "推荐招法：${move.pieceText} 至 (${move.to.x + 1}, ${8 - move.to.y})", Toast.LENGTH_SHORT).show()
                            selectedCoord = move.from
                            refreshLegalTargets()
                        }
                    }
                },
                onFlip = { isFlipped = !isFlipped },
                onToggleEditor = {
                    mode = if (mode == GameMode.EDITOR) GameMode.PVE else GameMode.EDITOR
                    selectedCoord = null
                    refreshLegalTargets()
                },
                onRestart = {
                    aiJob?.cancel()
                    isAiThinking = false
                    board = ChessBoard.initialBoard()
                    boardHistory.clear()
                    moveHistory.clear()
                    selectedCoord = null
                    legalTargets = emptyList()
                    lastFrom = null
                    lastTo = null
                },
                onExport = {
                    val pgn = PgnParser.exportChessPgn(rootTreeNode.value, "弈境国际象棋对局")
                    exportDialogContent = pgn
                }
            )
        }
    }

    // 兵升变选择弹窗
    pendingPromotionMove?.let { (from, to) ->
        AlertDialog(
            onDismissRequest = { pendingPromotionMove = null },
            title = { Text("兵升变选择") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf(
                        ChessPieceType.QUEEN to "后 ♛",
                        ChessPieceType.ROOK to "车 ♜",
                        ChessPieceType.BISHOP to "象 ♝",
                        ChessPieceType.KNIGHT to "马 ♞"
                    ).forEach { (type, label) ->
                        OutlinedButton(onClick = {
                            pendingPromotionMove = null
                            executePlayerMove(from, to, type)
                        }) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingPromotionMove = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 棋谱导出弹窗
    exportDialogContent?.let { pgnText ->
        AlertDialog(
            onDismissRequest = { exportDialogContent = null },
            title = { Text("国际象棋 PGN 棋谱导出") },
            text = { Text(pgnText, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(pgnText))
                    Toast.makeText(context, "已复制 PGN 棋谱到剪贴板", Toast.LENGTH_SHORT).show()
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
            title = { Text("保存国际象棋棋局") },
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
                        saveManager.clearSavedGame(GameType.CHESS)
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
private fun ChessPieceRack(
    selectedPiece: ChessPiece?,
    isEraserMode: Boolean,
    onSelectPiece: (ChessPiece) -> Unit,
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
            Text("白方棋子：", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf(
                    ChessPieceType.KING, ChessPieceType.QUEEN, ChessPieceType.ROOK,
                    ChessPieceType.BISHOP, ChessPieceType.KNIGHT, ChessPieceType.PAWN
                ).forEach { type ->
                    val piece = ChessPiece(PlayerSide.WHITE, type)
                    ChessRackItem(
                        piece = piece,
                        isSelected = !isEraserMode && selectedPiece?.type == type && selectedPiece.side == PlayerSide.WHITE,
                        onClick = { onSelectPiece(piece) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text("黑方棋子：", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf(
                    ChessPieceType.KING, ChessPieceType.QUEEN, ChessPieceType.ROOK,
                    ChessPieceType.BISHOP, ChessPieceType.KNIGHT, ChessPieceType.PAWN
                ).forEach { type ->
                    val piece = ChessPiece(PlayerSide.BLACK, type)
                    ChessRackItem(
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
                    label = { Text("橡皮擦") }
                )
                OutlinedButton(
                    onClick = onClearBoard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空", fontSize = 12.sp)
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
private fun ChessRackItem(
    piece: ChessPiece,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = piece.symbol,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (piece.side == PlayerSide.WHITE) Color(0xFF6B4226) else Color.Black
            )
        }
    }
}
