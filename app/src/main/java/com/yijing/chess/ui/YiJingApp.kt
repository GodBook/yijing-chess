package com.yijing.chess.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yijing.chess.*
import com.yijing.chess.chess.ChessPieceType
import com.yijing.chess.model.*
import com.yijing.chess.parser.NotationCodec
import com.yijing.chess.session.*
import com.yijing.chess.storage.ThemeChoice
import com.yijing.chess.ui.components.*
import com.yijing.chess.ui.screens.*
import com.yijing.chess.ui.theme.YiJingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SetupContext { NEW, CURRENT, DRAFT }
private data class SetupRequest(val value: GameSetup, val context: SetupContext)
private data class Confirmation(val title: String, val message: String, val action: () -> Unit)

@Composable
fun YiJingApp(model: GameViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as YiJingApplication
    val updateStatus by app.updates.status.collectAsStateWithLifecycle()
    val pendingUpdate by app.updates.hasPending.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var setup by remember { mutableStateOf<SetupRequest?>(null) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<GameSession?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    var exportSession by remember { mutableStateOf<GameSession?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var dismissedResult by remember { mutableStateOf<String?>(null) }
    var feedbackSession by remember { mutableStateOf<String?>(null) }
    var feedbackNode by remember { mutableStateOf<String?>(null) }
    val tone = remember(state.preferences.sound) { if (state.preferences.sound) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 30) }.getOrNull() else null }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    val dark = when (state.preferences.theme) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.DARK -> true; ThemeChoice.LIGHT -> false }
    val current = state.current
    val largeText = LocalDensity.current.fontScale >= 1.4f

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { NativeFiles.read(context, uri) } }.onSuccess { model.importText(it) }.onFailure { model.notify("文件读取失败：${it.message}") }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { NativeFiles.write(context, uri, text) } }.onSuccess { model.notify("棋谱文件已保存") }.onFailure { model.notify("文件保存失败：${it.message}") }
        }
    }
    fun chooseFile() = importFile.launch(arrayOf("*/*"))
    fun setupFor(session: GameSession) = GameSetup(session.gameType, session.mode, session.current.position.boardSize, session.humanSide,
        session.difficulty, session.thinkMillis, session.komi, session.scoringRule)
    fun newSetup(type: GameType) {
        val previous = state.sessions.firstOrNull { it.gameType == type && !it.isDraft && it.lessonId == null }
        setup = SetupRequest(previous?.let(::setupFor)?.copy(mode = GameMode.PVE) ?: GameSetup(type), SetupContext.NEW)
    }

    BackHandler(state.page != AppPage.HOME) { model.navigate(AppPage.HOME) }
    LaunchedEffect(state.message?.id) {
        state.message?.let { message ->
            val result = snackbar.showSnackbar(message.text, if (message.undoDelete) "撤销" else null, withDismissAction = true,
                duration = if (message.undoDelete) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) model.undoDelete()
            model.consumeMessage(message.id)
        }
    }
    LaunchedEffect(current?.id, current?.current?.id) {
        if (current != null && feedbackSession == current.id && feedbackNode != null && feedbackNode != current.current.id && current.current.event != null && !current.isReview) {
            if (state.preferences.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
        }
        feedbackSession = current?.id; feedbackNode = current?.current?.id
    }

    YiJingTheme(darkTheme = dark) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = state.page != AppPage.GAME, onClick = { model.navigate(AppPage.HOME) },
                    icon = { Icon(Icons.Default.Home, "大厅") }, label = { Text("大厅") }, modifier = Modifier.testTag("tab_HOME"))
                for (type in GameType.entries) NavigationBarItem(selected = state.page == AppPage.GAME && current?.gameType == type,
                    onClick = {
                        if (state.page != AppPage.GAME || current?.gameType != type) {
                            val saved = state.sessions.firstOrNull { it.gameType == type && !it.isDraft && it.lessonId == null }
                            if (saved != null) model.open(saved) else newSetup(type)
                        }
                    }, icon = { Icon(when (type) { GameType.XIANGQI -> Icons.Default.SportsEsports; GameType.GO -> Icons.Default.Adjust; GameType.CHESS -> Icons.Default.Extension }, type.displayName) },
                    label = { Text(if (type == GameType.XIANGQI) "象棋" else if (type == GameType.CHESS && largeText) "国象" else type.displayName) }, modifier = Modifier.testTag("tab_${type.name}"))
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                if (!state.loaded) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                else when (state.page) {
                    AppPage.HOME -> Box(Modifier.padding(top = padding.calculateTopPadding())) { NewHome(state, model, ::newSetup, ::chooseFile, { pasteOpen = true }, { sheet = "preferences" }) }
                    AppPage.LIBRARY -> LibraryScreen(state, model, { rename = it }, { target -> confirmation = Confirmation("删除棋局", "删除「${target.title}」？删除后可通过提示中的撤销恢复。") { model.delete(target) } }, ::chooseFile)
                    AppPage.LESSONS -> LessonsScreen(model) { sheet = "guide" }
                    AppPage.GAME -> GameScreen(state, model,
                        onSetup = { current?.let { setup = SetupRequest(setupFor(it), if (it.isDraft) SetupContext.DRAFT else SetupContext.CURRENT) } },
                        onEditor = { sheet = "editor" }, onExport = { exportSession = current }, onHistory = { sheet = "history" }, onHintDetails = { sheet = "hint" },
                        onRestart = { confirmation = Confirmation("重新开始", "当前棋局已保留在棋局库。开始一盘新的对局？", model::restart) },
                        onResign = { confirmation = Confirmation("确认认输", "本局将记录为认输，仍可保存棋谱和复盘。", model::resign) },
                        onDraw = { confirmation = Confirmation("确认和棋", "双方均同意结束本局并记录为和棋？", model::agreeDraw) }, onAnnotate = { sheet = "annotation" })
                }
            }
        }

        setup?.let { request -> SetupSheet(request.value, request.context == SetupContext.DRAFT, request.context == SetupContext.CURRENT, { setup = null }) { value ->
            setup = null
            when (request.context) {
                SetupContext.NEW -> model.newGame(value.type, value.mode, value.size, value.human, value.difficulty, value.budget, value.komi, value.rule)
                SetupContext.DRAFT -> model.startDraft(value.mode, value.human, value.difficulty, value.budget)
                SetupContext.CURRENT -> {
                    if (current?.gameType == GameType.GO && (value.size != current.current.position.boardSize || value.komi != current.komi || value.rule != current.scoringRule))
                        confirmation = Confirmation("开始新的围棋对局", "路数或结算规则已改变。当前棋局保留在棋局库，确认开始新局？") {
                            model.newGame(value.type, value.mode, value.size, value.human, value.difficulty, value.budget, value.komi, value.rule)
                        }
                    else model.setMode(value.mode, value.human, value.difficulty, value.budget)
                }
            }
        } }
        confirmation?.let { pending -> ConfirmAction(pending.title, pending.message, onDismiss = { confirmation = null }) { confirmation = null; pending.action() } }
        rename?.let { session -> TextEntryDialog("重命名棋局", session.title, { rename = null }, onConfirm = { model.renameSaved(session, it); rename = null }) }
        if (pasteOpen) TextEntryDialog("粘贴 PGN、SGF 或 FEN", "", { pasteOpen = false }, { text -> model.importText(text); pasteOpen = false }, multiline = true, confirmText = "预览导入")
        current?.let { session ->
            when (sheet) {
                "editor" -> EditorSheet(session, state.editorPiece, model, { sheet = null }, {
                    sheet = null; setup = SetupRequest(setupFor(session).copy(mode = GameMode.PVE), SetupContext.DRAFT)
                }) { initial -> sheet = null; confirmation = Confirmation(if (initial) "恢复初始布局" else "清空棋盘", "这会替换当前草稿中的棋子，确认继续？") { model.clearDraft(initial) } }
                "history" -> HistorySheet(session, model) { sheet = null }
                "annotation" -> TextEntryDialog("局面注释", session.current.comment, { sheet = null }, { model.annotate(it); sheet = null }, multiline = true)
                "hint" -> AppSheet("推荐走法", { sheet = null }) { Text(state.hintText.ifBlank { "点击提示后，可查看推荐走法与说明。" }); Text("推荐仅作参考，棋盘上的绿色箭头或圆圈表示目标。", style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (sheet == "preferences") PreferenceSheet(state.preferences, model::updatePreferences, { sheet = null }, {
            sheet = null; if (pendingUpdate) app.updates.showStatus() else app.applicationScope.launch { app.updates.checkForUpdates(isManualCheck = true) }
        }, pendingUpdate) { sheet = "guide" }
        if (sheet == "guide" || (state.loaded && !state.preferences.onboardingComplete)) GuideSheet {
            sheet = null; model.updatePreferences(state.preferences.copy(onboardingComplete = true))
        }
        state.pendingImport?.let { imported -> AlertDialog(onDismissRequest = model::cancelImport, title = { Text("导入预览") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(imported.title); Text("${imported.gameType.displayName} · ${imported.moveCount} 手 · ${if (imported.isDraft) "作为摆谱草稿打开" else "可继续对弈与复盘"}")
                Text("现有棋局继续保留在棋局库。", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(model::confirmImport, Modifier.testTag("confirm_import")) { Text("导入并打开") } }, dismissButton = { TextButton(model::cancelImport) { Text("取消") } }) }
        if (state.importing) AlertDialog(onDismissRequest = {}, title = { Text("正在解析棋谱") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
        exportSession?.let { session -> ExportSheet(session, { exportSession = null }, { name, text -> pendingExport = text; saveFile.launch(name) },
            { text -> NativeFiles.copy(context, text); model.notify("棋谱已复制") }, { name, text, sgf -> scope.launch {
                runCatching { withContext(Dispatchers.IO) { NativeFiles.share(context, name, text, sgf) } }.onFailure { model.notify("分享失败：${it.message}") }
            } }) }
        state.promotion?.let { AlertDialog(onDismissRequest = model::cancelPromotion, title = { Text("选择兵的升变") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { for ((type, label) in listOf(ChessPieceType.QUEEN to "后", ChessPieceType.ROOK to "车", ChessPieceType.BISHOP to "象", ChessPieceType.KNIGHT to "马"))
                OutlinedButton({ model.promote(type) }, Modifier.fillMaxWidth().testTag("promote_${type.name}")) { Text(label) }
            }
        }, confirmButton = {}, dismissButton = { TextButton(model::cancelPromotion) { Text("取消") } }) }
        if (current != null && state.page == AppPage.GAME && state.outcome.phase == GamePhase.FINISHED && !current.isReview && !state.lessonSolved) {
            val key = "${current.id}:${current.liveNodeId}:${state.outcome}"
            if (dismissedResult != key) AlertDialog(onDismissRequest = { dismissedResult = key }, title = { Text("对局结束") },
                text = { Text("${state.outcome.winner?.displayName?.plus("胜") ?: "和棋"}\n${state.outcome.reason}\n${state.saveStatus.ifBlank { "正在保存" }}，可继续复盘或导出。") },
                confirmButton = { TextButton({ dismissedResult = key; model.restart() }) { Text("再来一局") } },
                dismissButton = { TextButton({ dismissedResult = key }) { Text("查看棋盘") } })
        }
        UpdateDialog(updateStatus, { info -> app.applicationScope.launch { app.updates.downloadApk(info) } }, app.updates::installApk,
            app.updates::retry, app.updates::resetStatus, app.updates::cancelDownload)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportSheet(session: GameSession, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onCopy: (String) -> Unit, onShare: (String, String, Boolean) -> Unit) {
    var fen by remember { mutableStateOf(false) }
    var variations by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.revision, fen, variations) {
        text = null; error = null
        runCatching { withContext(Dispatchers.Default) { if (fen) session.current.position.state else NotationCodec.export(session, BuildConfig.VERSION_NAME, variations) } }
            .onSuccess { text = it }.onFailure { error = it.message }
    }
    val extension = if (fen) "fen" else if (session.gameType == GameType.GO) "sgf" else "pgn"
    val name = "yijing-${session.gameType.name.lowercase()}-${session.id.take(8)}.$extension"
    AppSheet("导出与分享", onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!fen, { fen = false }, label = { Text(if (session.gameType == GameType.GO) "SGF 棋谱" else "PGN 棋谱") })
            if (session.gameType != GameType.GO) FilterChip(fen, { fen = true }, label = { Text("当前局面 FEN") })
        }
        if (!fen) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(variations, { variations = it }); Text("包含其他历史分支", Modifier.weight(1f))
        }
        Text(if (fen) "导出当前查看的局面。" else if (session.gameType == GameType.XIANGQI) "中国象棋使用 ICCS 坐标记谱，导出当前实战主线及起始局面。" else "导出当前实战主线、起始局面与注释。", style = MaterialTheme.typography.bodySmall)
        if (text == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val content = text
        Button({ if (content != null) onSave(name, content) }, Modifier.fillMaxWidth().testTag("export_save"), enabled = content != null) { Text("保存到文件") }
        OutlinedButton({ if (content != null) onCopy(content) }, Modifier.fillMaxWidth().testTag("export_copy"), enabled = content != null) { Text("复制棋谱") }
        OutlinedButton({ if (content != null) onShare(name, content, session.gameType == GameType.GO) }, Modifier.fillMaxWidth(), enabled = content != null) { Text("分享棋谱文件") }
        TextButton({ preview = !preview }) { Text(if (preview) "收起预览" else "预览内容") }
        if (preview && content != null) SelectionContainer { Text(content.take(20_000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 240.dp)) }
    }
}
