package com.yijing.chess.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yijing.chess.*
import com.yijing.chess.model.*
import com.yijing.chess.session.*
import com.yijing.chess.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(state: AppState, model: GameViewModel, onSetup: () -> Unit, onEditor: () -> Unit, onExport: () -> Unit,
    onHistory: () -> Unit, onHintDetails: () -> Unit, onRestart: () -> Unit, onResign: () -> Unit, onDraw: () -> Unit, onAnnotate: () -> Unit) {
    val session = state.current ?: return
    var menu by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.4f
    Scaffold(topBar = { TopAppBar(title = { Column {
        Text(session.gameType.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!largeText) Text(if (session.isDraft) "独立摆谱草稿" else if (session.lessonId != null) "精选练习" else session.mode.displayName,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, navigationIcon = { IconButton({ model.navigate(AppPage.HOME) }, Modifier.testTag("game_back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大厅") } },
        actions = {
            IconButton(onSetup, Modifier.testTag("game_setup")) { Icon(Icons.Default.Tune, "对局设置") }
            Box {
                IconButton({ menu = true }, Modifier.testTag("game_more")) { Icon(Icons.Default.MoreVert, "更多操作") }
                DropdownMenu(menu, { menu = false }) {
                    fun action(block: () -> Unit) { menu = false; block() }
                    DropdownMenuItem({ Text("翻转棋盘") }, { action(model::flip) })
                    DropdownMenuItem({ Text("导出与分享棋谱") }, { action(onExport) })
                    DropdownMenuItem({ Text("走步与分支复盘") }, { action(onHistory) })
                    DropdownMenuItem({ Text("添加局面注释") }, { action(onAnnotate) })
                    if (!session.isDraft) DropdownMenuItem({ Text("保存为摆谱草稿") }, { action(model::startEditor) })
                    DropdownMenuItem({ Text("重新开始") }, { action(onRestart) })
                    if (!session.isDraft && !session.isReview && state.outcome.phase == GamePhase.PLAYING) {
                        if (SessionRules.drawClaim(session) != null) DropdownMenuItem({ Text("申请和棋") }, { action(model::claimDraw) })
                        DropdownMenuItem({ Text("双方同意和棋") }, { action(onDraw) })
                        DropdownMenuItem({ Text("认输") }, { action(onResign) })
                    }
                }
            }
        }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).testTag("game_screen")) {
            val wide = maxWidth > maxHeight * 1.15f && maxWidth >= 560.dp
            val statusMaxHeight = maxHeight * 0.42f
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BoardHost(state, model, Modifier.weight(1.45f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameStatusPanel(state, model, onEditor, onHistory, onHintDetails)
                        Text(sessionSummary(session), style = MaterialTheme.typography.bodySmall)
                        Text("最近走步", style = MaterialTheme.typography.titleSmall)
                        session.path(session.liveNodeId).takeLast(8).filter { it.event != null }.forEach { node ->
                            TextButton({ model.review(node.id) }) { Text(node.event!!.label) }
                        }
                    }
                    GameActions(state, model, onHistory, { menu = true }, onEditor)
                }
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(max = statusMaxHeight).verticalScroll(rememberScrollState())) {
                    GameStatusPanel(state, model, onEditor, onHistory, onHintDetails)
                }
                BoardHost(state, model, Modifier.weight(1f).fillMaxWidth())
                GameActions(state, model, onHistory, { menu = true }, onEditor)
            }
        }
    }
}

@Composable
private fun BoardHost(state: AppState, model: GameViewModel, modifier: Modifier) {
    val session = state.current ?: return
    val position = session.current.position
    val options = BoardOptions(state.preferences.highContrast, state.preferences.coordinates, state.hint?.from, state.hint?.to, state.pendingGo, session.markedDead.toSet())
    val lastMove = session.path().lastOrNull { it.event?.kind == EventKind.MOVE }?.event?.move
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val ratio = if (session.gameType == GameType.XIANGQI) 9f / 10.3f else 1f
        val width = minOf(maxWidth, maxHeight * ratio)
        Box(Modifier.width(width).testTag("board")) {
            when (session.gameType) {
                GameType.CHESS -> ChessBoardView(remember(position) { position.chess() }, state.selected, state.legalTargets, lastMove?.from, lastMove?.to,
                    state.flipped, model::tap, options = options)
                GameType.XIANGQI -> XiangqiBoardView(remember(position) { position.xiangqi() }, state.selected, state.legalTargets, lastMove?.from, lastMove?.to,
                    state.flipped, model::tap, options = options)
                GameType.GO -> GoBoardView(remember(position) { position.go() }, lastMove?.to, model::tap, isFlipped = state.flipped, options = options)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameStatusPanel(state: AppState, model: GameViewModel, onEditor: () -> Unit, onHistory: () -> Unit, onHintDetails: () -> Unit) {
    val session = state.current ?: return
    val lesson = Lessons.all.firstOrNull { it.id == session.lessonId }
    val status = when {
        session.isDraft -> "${session.current.position.turn.displayName}先走 · ${if (state.editorPiece == null) "橡皮擦" else "放置棋子"}"
        session.isReview -> "正在复盘 · ${session.path().count { it.event != null }} 手位置"
        state.lessonSolved -> "练习完成"
        state.outcome.phase == GamePhase.FINISHED -> "${state.outcome.winner?.displayName?.plus("胜") ?: "和棋"} · ${state.outcome.reason}"
        state.outcome.phase == GamePhase.SCORING -> "结算中 · 点击棋块标记／取消死子"
        state.thinking -> "AI 正在思考 · 最多 ${session.thinkMillis / 1000.0} 秒"
        state.hintThinking -> "正在寻找推荐走法"
        else -> "轮到${session.current.position.turn.displayName}${if (SessionRules.isCheck(session.current.position)) " · 将军" else ""} · ${session.moveCount} 手"
    }
    Column(Modifier.fillMaxWidth().then(if (state.preferences.animations) Modifier.animateContentSize() else Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(status, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("game_status"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(model::persistCurrent, contentPadding = PaddingValues(0.dp)) { Text(state.saveStatus.ifBlank { "自动保存已开启" }, style = MaterialTheme.typography.labelMedium) }
            when {
                state.thinking || state.hintThinking -> TextButton(model::stopThinking, Modifier.testTag("stop_thinking")) { Text("停止思考") }
                session.mode == GameMode.PVE && !session.isReview && session.current.position.turn != session.humanSide && state.outcome.phase == GamePhase.PLAYING -> TextButton(model::continueThinking) { Text("让 AI 继续") }
                session.isDraft -> TextButton(onEditor, Modifier.testTag("editor_tools")) { Text("摆谱工具") }
                session.isReview -> TextButton(model::returnToLive, Modifier.testTag("return_live")) { Text("返回实战") }
                state.hint != null -> TextButton(onHintDetails) { Text("查看推荐说明") }
            }
        }
        if (lesson != null && !state.lessonSolved) Text(lesson.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (state.lessonSolved) Text(state.hintText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (state.pendingGo != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("落在 ${coordinateName(GameType.GO, state.pendingGo, session.current.position.boardSize)}？", Modifier.weight(1f))
            TextButton(model::cancelGo) { Text("取消") }
            Button(model::confirmGo, Modifier.testTag("confirm_go")) { Text("确认落子") }
        }
        if (session.isReview) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(model::branchHere, Modifier.testTag("branch_here")) { Text("从此推演") }
            TextButton(onHistory) { Text("选择分支") }
        }
        if (session.gameType == GameType.GO && !session.isDraft && !session.isReview && state.outcome.phase == GamePhase.PLAYING) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("提子  黑 ${session.current.position.blackPrisoners} · 白 ${session.current.position.whitePrisoners}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(model::pass, Modifier.heightIn(min = 48.dp).testTag("pass"),
                    enabled = !state.thinking && (session.mode != GameMode.PVE || session.current.position.turn == session.humanSide)) { Text("停着") }
            }
        }
        if (state.outcome.phase == GamePhase.SCORING && !session.isReview) {
            val score = remember(session.current.position, session.markedDead, session.komi, session.scoringRule) { scoreGo(session) }
            Text("黑 ${score.black} · 白 ${score.white}（含贴目 ${session.komi}）", style = MaterialTheme.typography.titleMedium)
            Text("${session.scoringRule.label}估算；双活等争议请双方确认或继续对弈。", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(model::confirmScore, Modifier.testTag("confirm_score")) { Text("确认结果") }
                OutlinedButton(model::resumeGo, Modifier.testTag("resume_go")) { Text("继续对弈") }
            }
        }
    }
}

@Composable
private fun GameActions(state: AppState, model: GameViewModel, onHistory: () -> Unit, onMore: () -> Unit, onEditor: () -> Unit) {
    val session = state.current ?: return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = (maxWidth - 18.dp) / 4 < (32 * LocalDensity.current.fontScale).dp
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(model::undo, Modifier.weight(1f).heightIn(min = 48.dp).testTag("undo"), enabled = session.hasMoves && !session.isDraft && !session.isReview,
            contentPadding = PaddingValues(horizontal = 4.dp)) { if (compact) Icon(Icons.AutoMirrored.Filled.Undo, "悔棋") else Text("悔棋") }
        OutlinedButton(if (session.isDraft) onEditor else model::hint, Modifier.weight(1f).heightIn(min = 48.dp).testTag("hint"),
            enabled = session.isDraft || (!state.thinking && !state.hintThinking && state.outcome.phase == GamePhase.PLAYING), contentPadding = PaddingValues(horizontal = 4.dp)) {
            if (compact) Icon(if (session.isDraft) Icons.Default.Build else Icons.Default.Lightbulb, if (session.isDraft) "工具" else "提示") else Text(if (session.isDraft) "工具" else "提示")
        }
        OutlinedButton(onHistory, Modifier.weight(1f).heightIn(min = 48.dp).testTag("history"), contentPadding = PaddingValues(horizontal = 4.dp)) {
            if (compact) Icon(Icons.Default.History, "复盘") else Text("复盘")
        }
        OutlinedButton(onMore, Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            if (compact) Icon(Icons.Default.MoreHoriz, "更多") else Text("更多")
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(session: GameSession, model: GameViewModel, onDismiss: () -> Unit) {
    val main = session.path(session.liveNodeId)
    val others = session.nodes.filter { node -> main.none { it.id == node.id } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("走步与分支", style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ model.previous(); onDismiss() }) { Text("上一步") }
                TextButton({ model.next(); onDismiss() }) { Text("下一步") }
                TextButton({ model.returnToLive(); onDismiss() }) { Text("返回实战") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(main, key = { it.id }) { node ->
                    ListItem(headlineContent = { Text(if (node.event == null) "起始局面" else "${session.path(node.id).size - 1} · ${node.event.label}") },
                        supportingContent = if (node.comment.isNotBlank()) ({ Text(node.comment) }) else null,
                        trailingContent = if (node.id == session.cursorId) ({ Icon(Icons.Default.Check, "当前查看位置") }) else null,
                        modifier = Modifier.testTag("history_${node.id}").clickable { model.review(node.id); onDismiss() })
                }
                if (others.isNotEmpty()) item { Text("其他分支", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium) }
                items(others, key = { it.id }) { node ->
                    TextButton({ model.review(node.id); onDismiss() }, Modifier.fillMaxWidth().testTag("variation_${node.id}")) { Text("${session.path(node.id).size - 1} 手 · ${node.event?.label} ${node.comment}") }
                }
                if (session.legacyNotes.isNotEmpty()) item { Text("旧档走步参考（无法验证完整历史）\n${session.legacyNotes.joinToString(" ")}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
