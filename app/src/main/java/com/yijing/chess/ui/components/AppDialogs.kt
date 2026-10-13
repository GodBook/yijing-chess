package com.yijing.chess.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yijing.chess.GameViewModel
import com.yijing.chess.model.*
import com.yijing.chess.session.*
import com.yijing.chess.storage.*

data class GameSetup(val type: GameType, val mode: GameMode = GameMode.PVE, val size: Int = 19,
    val human: PlayerSide = type.firstSide(), val difficulty: Int = 2, val budget: Long = 2500,
    val komi: Double = 7.5, val rule: GoScoringRule = GoScoringRule.AREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupSheet(initial: GameSetup, fromDraft: Boolean, editingCurrent: Boolean, onDismiss: () -> Unit, onConfirm: (GameSetup) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    var komiText by remember(initial) { mutableStateOf(initial.komi.toString()) }
    val validKomi = komiText.toDoubleOrNull()?.takeIf { it.isFinite() && it in -100.0..100.0 }
    AppSheet(if (fromDraft) "从此局面开始" else if (editingCurrent) "对局设置" else "开始${initial.type.displayName}", onDismiss) {
        Text("对局模式", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (mode in listOf(GameMode.PVE, GameMode.PVP) + if (fromDraft) emptyList() else listOf(GameMode.EDITOR))
                FilterChip(selected = value.mode == mode, onClick = { value = value.copy(mode = mode) }, label = { Text(mode.displayName) }, modifier = Modifier.testTag("setup_mode_${mode.name}"))
        }
        if (value.mode == GameMode.PVE) {
            Text("我执", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (side in listOf(value.type.firstSide(), value.type.opposite(value.type.firstSide())))
                    FilterChip(value.human == side, { value = value.copy(human = side) }, label = { Text(side.displayName) })
            }
            Text("AI 难度", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((difficulty, label) in listOf(1 to "初级", 2 to "中级", 3 to "大师"))
                    FilterChip(value.difficulty == difficulty, { value = value.copy(difficulty = difficulty) }, label = { Text(label) })
            }
            Text("每步思考上限", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((budget, label) in listOf(1000L to "1 秒", 2500L to "2.5 秒", 5000L to "5 秒"))
                    FilterChip(value.budget == budget, { value = value.copy(budget = budget) }, label = { Text(label) })
            }
        }
        if (value.type == GameType.GO && !fromDraft) {
            Text("棋盘路数", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (size in listOf(9, 13, 19)) FilterChip(value.size == size, { value = value.copy(size = size) }, label = { Text("$size 路") }, modifier = Modifier.testTag("setup_size_$size"))
            }
            Text("结算规则", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rule in GoScoringRule.entries) FilterChip(value.rule == rule, { value = value.copy(rule = rule); komiText = if (rule == GoScoringRule.AREA) "7.5" else "6.5" }, label = { Text(rule.label) })
            }
            OutlinedTextField(komiText, { komiText = it }, label = { Text("贴目（白方）") }, singleLine = true,
                isError = validKomi == null, supportingText = { Text("支持 -100 至 100；结算时由双方确认死活与结果") }, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { onConfirm(value.copy(komi = validKomi ?: value.komi)) }, enabled = value.type != GameType.GO || validKomi != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("setup_confirm")) {
            Text(if (fromDraft) "从此局面开始" else if (editingCurrent) "应用设置" else if (value.mode == GameMode.EDITOR) "开始摆谱" else "开始对局")
        }
    }
}

@Composable
fun ConfirmAction(title: String, message: String, confirmText: String = "继续", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_action")) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun TextEntryDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, multiline: Boolean = false, confirmText: String = "保存") {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value, { value = it.take(if (multiline) 10_000 else 80) }, minLines = if (multiline) 4 else 1, maxLines = if (multiline) 8 else 1,
            modifier = Modifier.fillMaxWidth().testTag("text_entry")) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, modifier = Modifier.testTag("text_entry_confirm")) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun PreferenceSheet(value: AppPreferences, onChange: (AppPreferences) -> Unit, onDismiss: () -> Unit, onCheckUpdate: () -> Unit, hasPendingUpdate: Boolean = false, onGuide: () -> Unit) {
    AppSheet("设置与关于", onDismiss) {
        Text("界面主题", style = MaterialTheme.typography.titleSmall)
        Column { for (theme in ThemeChoice.entries) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(value.theme == theme, { onChange(value.copy(theme = theme)) }); TextButton({ onChange(value.copy(theme = theme)) }) { Text(theme.label) }
        } }
        PreferenceSwitch("高对比棋盘", "增强棋格和标记的辨识度", value.highContrast) { onChange(value.copy(highContrast = it)) }
        PreferenceSwitch("显示坐标", "用于提示、复盘和交流局面", value.coordinates) { onChange(value.copy(coordinates = it)) }
        PreferenceSwitch("19 路落点确认", "先预览落点，再确认落子，减少误触", value.confirmGo19) { onChange(value.copy(confirmGo19 = it)) }
        PreferenceSwitch("落子震动", "提供轻微的触觉反馈", value.haptics) { onChange(value.copy(haptics = it)) }
        PreferenceSwitch("落子音效", "使用媒体音量，可随时关闭", value.sound) { onChange(value.copy(sound = it)) }
        PreferenceSwitch("界面动效", "关闭后直接切换提示状态", value.animations) { onChange(value.copy(animations = it)) }
        HorizontalDivider()
        Text("弈境 v${com.yijing.chess.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        Text("三种棋类 · 本地对弈 · 无广告\n棋局保存在本机，可通过棋谱文件备份与分享。检查更新时会连接 GitHub；下载和安装由系统管理。", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onCheckUpdate, Modifier.fillMaxWidth()) { Text(if (hasPendingUpdate) "查看下载或安装状态" else "检查更新") }
        TextButton(onGuide, Modifier.fillMaxWidth()) { Text("查看操作说明") }
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(value, onChange)
    }
}

@Composable
fun GuideSheet(onDismiss: () -> Unit) {
    AppSheet("几步开始一盘棋", onDismiss) {
        TextButton(onDismiss) { Text("稍后再看") }
        Text("1  选择棋类与模式", style = MaterialTheme.typography.titleMedium)
        Text("人机对战可以选择执子方、难度和思考时间；双人模式在同一块屏幕上轮流操作。")
        Text("2  点选与落子", style = MaterialTheme.typography.titleMedium)
        Text("象棋先点自己的棋子，再点高亮目标。围棋点击交叉点，19 路默认先预览再确认；停着用于表示暂不落子。")
        Text("3  中断与复盘", style = MaterialTheme.typography.titleMedium)
        Text("有效操作后自动保存。大厅点击继续即可恢复；更多菜单可导出棋谱、打开走步列表和摆谱。查看历史时选择“返回实战”或“从此推演”。")
        Text("规则说明", style = MaterialTheme.typography.titleMedium)
        Text("中国象棋将死与困毙均判负，复杂长将长捉由双方协商；国际象棋支持将死、困毙、子力不足及重复／回合计数和棋。围棋采用禁自杀、单劫规则，双停后手动标记死子，数子或数目结果由双方确认，含双活等争议时可继续对弈。")
        Button(onDismiss, Modifier.fillMaxWidth().testTag("guide_done")) { Text("知道了，开始下棋") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorSheet(session: GameSession, selectedPiece: Char?, model: GameViewModel, onDismiss: () -> Unit, onStart: () -> Unit, onClear: (Boolean) -> Unit) {
    var rights by remember(session.id) { mutableStateOf(if (session.gameType == GameType.CHESS) session.current.position.state.split(" ")[2].replace("-", "") else "") }
    var ep by remember(session.id) { mutableStateOf(if (session.gameType == GameType.CHESS) session.current.position.state.split(" ")[3].replace("-", "") else "") }
    AppSheet("摆谱工具", onDismiss) {
        Text("先选择棋子，再关闭工具面板并点棋盘。修改自动存为独立草稿。", style = MaterialTheme.typography.bodyMedium)
        val codes = when (session.gameType) { GameType.CHESS -> "KQRBNPkqrbnp"; GameType.XIANGQI -> "KABNRCPkabnrcp"; GameType.GO -> "BW" }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (code in codes) {
                val label = when (session.gameType) {
                    GameType.CHESS -> com.yijing.chess.chess.ChessPiece.fromFenChar(code)!!.let { "${it.side.displayName} ${it.symbol}" }
                    GameType.XIANGQI -> com.yijing.chess.xiangqi.XiangqiPiece.fromFenChar(code)!!.let { "${it.side.displayName} ${it.displayName}" }
                    GameType.GO -> if (code == 'B') "黑子" else "白子"
                }
                FilterChip(selectedPiece == code, { model.selectEditorPiece(code); onDismiss() }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
            }
            FilterChip(selectedPiece == null, { model.selectEditorPiece(null); onDismiss() }, label = { Text("橡皮擦") }, modifier = Modifier.heightIn(min = 48.dp))
        }
        Text("轮到哪方", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (side in listOf(session.gameType.firstSide(), session.gameType.opposite(session.gameType.firstSide())))
                FilterChip(session.current.position.turn == side, { model.draftTurn(side) }, label = { Text(side.displayName) })
        }
        if (session.gameType == GameType.CHESS) {
            Text("易位权", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((code, title) in listOf('K' to "白短易位", 'Q' to "白长易位", 'k' to "黑短易位", 'q' to "黑长易位"))
                    FilterChip(code in rights, { rights = if (code in rights) rights.replace(code.toString(), "") else rights + code }, label = { Text(title) })
            }
            OutlinedTextField(ep, { ep = it.take(2) }, label = { Text("过路兵目标格，可留空") }, placeholder = { Text("例如 e3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton({ model.draftChessRights(rights, ep) }, Modifier.fillMaxWidth()) { Text("应用易位与过路兵设置") }
        }
        OutlinedButton({ onClear(false) }, Modifier.fillMaxWidth()) { Text("清空棋盘") }
        OutlinedButton({ onClear(true) }, Modifier.fillMaxWidth()) { Text("恢复初始布局") }
        val errors = remember(session.current.position) { SessionRules.playableErrors(session.current.position) }
        if (errors.isNotEmpty()) Text(errors.joinToString("\n"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Button(onStart, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = errors.isEmpty()) { Text("从此局面开始") }
    }
}
