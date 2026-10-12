package com.yijing.chess.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.MoveTreeNode
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.parser.PgnParser
import com.yijing.chess.ui.components.ChessBoardView
import com.yijing.chess.ui.components.ChessPieceRack
import com.yijing.chess.ui.components.GameControlBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessScreen(
    initialMode: GameMode = GameMode.PVE,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(initialMode) }
    var isFlipped by remember { mutableStateOf(false) }
    var isAiThinking by remember { mutableStateOf(false) }

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
            isAiThinking = true
            coroutineScope.launch {
                val decision = ChessAI.findBestMove(board, PlayerSide.BLACK, depth = 3)
                isAiThinking = false
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
                val movingPiece = board[from]!!
                val isPawnPromotion = movingPiece.type == ChessPieceType.PAWN &&
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
        board = board.copy()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("国际象棋", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (mode == GameMode.PVE) "人机对决" else if (mode == GameMode.PVP) "双人对弈" else "自由排局",
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
            // 模式选择
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
                        label = { Text("人机") }
                    )
                    FilterChip(
                        selected = mode == GameMode.PVP,
                        onClick = { mode = GameMode.PVP },
                        label = { Text("双人") }
                    )
                    FilterChip(
                        selected = mode == GameMode.EDITOR,
                        onClick = { mode = GameMode.EDITOR },
                        label = { Text("摆谱") }
                    )
                }

                if (isAiThinking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI 思考中...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 状态条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (board.turn == PlayerSide.WHITE) "轮到白方走子 (White to move)" else "轮到黑方走子 (Black to move)",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                if (ChessRule.isKingInCheck(board, board.turn)) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("【将军!】", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // 棋盘视图
            ChessBoardView(
                board = board,
                selectedCoord = selectedCoord,
                legalTargets = legalTargets,
                lastFrom = lastFrom,
                lastTo = lastTo,
                isFlipped = isFlipped,
                onSquareClick = ::handleSquareClick
            )

            // 摆谱工具或历史
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
                    onResetBoard = {
                        board = ChessBoard.initialBoard()
                        selectedCoord = null
                        refreshLegalTargets()
                    },
                    onStartBattle = {
                        mode = GameMode.PVE
                        triggerAiMoveIfNeeded()
                        Toast.makeText(context, "从当前局面开始对战", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (moveHistory.isEmpty()) {
                        Text("暂无走法记录", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        moveHistory.forEachIndexed { index, m ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
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

            // 底部控制
            GameControlBar(
                canUndo = boardHistory.isNotEmpty() && !isAiThinking,
                isEditorMode = mode == GameMode.EDITOR,
                onUndo = ::handleUndo,
                onHint = {
                    coroutineScope.launch {
                        val decision = ChessAI.findBestMove(board, board.turn, depth = 3)
                        val m = decision.move
                        if (m != null) {
                            Toast.makeText(context, "推荐：${m.notation}", Toast.LENGTH_SHORT).show()
                            selectedCoord = m.from
                            refreshLegalTargets()
                        }
                    }
                },
                onFlip = { isFlipped = !isFlipped },
                onToggleEditor = {
                    mode = if (mode == GameMode.EDITOR) GameMode.PVE else GameMode.EDITOR
                },
                onRestart = {
                    board = ChessBoard.initialBoard()
                    boardHistory.clear()
                    moveHistory.clear()
                    selectedCoord = null
                    legalTargets = emptyList()
                    lastFrom = null
                    lastTo = null
                },
                onExport = {
                    val pgn = PgnParser.exportToPgn(rootTreeNode.value)
                    val fen = board.toFen()
                    exportDialogContent = "【FEN 串】\n$fen\n\n【PGN 棋谱】\n$pgn"
                }
            )
        }
    }

    // 兵升变选择弹窗
    pendingPromotionMove?.let { (from, to) ->
        AlertDialog(
            onDismissRequest = { pendingPromotionMove = null },
            title = { Text("兵升变 (Pawn Promotion)") },
            text = { Text("请选择升变的目标棋子：") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        executePlayerMove(from, to, ChessPieceType.QUEEN)
                        pendingPromotionMove = null
                    }) { Text("后 (Q)") }
                    TextButton(onClick = {
                        executePlayerMove(from, to, ChessPieceType.ROOK)
                        pendingPromotionMove = null
                    }) { Text("车 (R)") }
                    TextButton(onClick = {
                        executePlayerMove(from, to, ChessPieceType.BISHOP)
                        pendingPromotionMove = null
                    }) { Text("象 (B)") }
                    TextButton(onClick = {
                        executePlayerMove(from, to, ChessPieceType.KNIGHT)
                        pendingPromotionMove = null
                    }) { Text("马 (N)") }
                }
            }
        )
    }

    // 棋谱导出弹窗
    exportDialogContent?.let { content ->
        AlertDialog(
            onDismissRequest = { exportDialogContent = null },
            title = { Text("国象 PGN/FEN 导出") },
            text = { Text(content, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(content))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
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
}
