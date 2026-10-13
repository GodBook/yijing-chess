package com.yijing.chess.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yijing.chess.*
import com.yijing.chess.model.*
import com.yijing.chess.session.*
import com.yijing.chess.ui.components.BoardThumbnail
import java.text.DateFormat
import java.util.Date

fun sessionSummary(session: GameSession): String = buildList {
    add(session.gameType.displayName)
    add(session.mode.displayName)
    if (session.mode == GameMode.PVE) add("${session.humanSide.displayName} · ${listOf("初级", "中级", "大师")[session.difficulty - 1]}")
    add("${session.moveCount} 手")
}.joinToString(" · ")

fun dateLabel(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

@Composable
fun NewHome(state: AppState, model: GameViewModel, onNew: (GameType) -> Unit, onImport: () -> Unit, onPaste: () -> Unit, onSettings: () -> Unit) {
    val recent = remember(state.sessions) { state.sessions.filter { !it.isDraft && it.lessonId == null && it.outcome().phase != GamePhase.FINISHED }.distinctBy { it.gameType }.take(3) }
    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("弈境", style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text("方寸之间，安心下一盘棋", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onSettings, Modifier.testTag("app_settings")) { Icon(Icons.Default.Settings, "设置与关于") }
            }
        }
        if (recent.isNotEmpty()) {
            item { Text("继续对局", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(recent, key = { "recent_${it.id}" }) { session ->
                Card(onClick = { model.open(session) }, modifier = Modifier.fillMaxWidth().testTag("continue_${session.gameType.name}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BoardThumbnail(session.live.position, Modifier.width(58.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium)
                            Text(sessionSummary(session), style = MaterialTheme.typography.bodySmall)
                            Text(dateLabel(session.updatedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.PlayArrow, "继续${session.gameType.displayName}")
                    }
                }
            }
        }
        item { Text("开始一盘新棋", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        items(GameType.entries.toList()) { type ->
            OutlinedCard(onClick = { onNew(type) }, modifier = Modifier.fillMaxWidth().testTag("new_${type.name}")) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(when (type) { GameType.XIANGQI -> "帥"; GameType.GO -> "●"; GameType.CHESS -> "♞" }, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(type.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(if (type == GameType.GO) "9／13／19 路 · 人机、双人、摆谱" else "人机对战 · 双人同屏 · 残局摆谱", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, "开始${type.displayName}")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ model.navigate(AppPage.LIBRARY) }, Modifier.fillMaxWidth().testTag("open_library")) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("棋局库 · ${state.sessions.size} 盘") }
                OutlinedButton({ model.navigate(AppPage.LESSONS) }, Modifier.fillMaxWidth().testTag("open_lessons")) { Icon(Icons.Default.School, null); Spacer(Modifier.width(8.dp)); Text("精选练习与操作说明") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onImport, Modifier.weight(1f).testTag("import_file")) { Text("导入棋谱文件") }
                    TextButton(onPaste, Modifier.weight(1f).testTag("import_text")) { Text("粘贴棋谱／FEN") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(state: AppState, model: GameViewModel, onRename: (GameSession) -> Unit, onDelete: (GameSession) -> Unit, onImport: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf("ALL") }
    var phase by rememberSaveable { mutableStateOf("全部") }
    var days by rememberSaveable { mutableStateOf(0) }
    val cutoff = remember(days) { if (days == 0) Long.MIN_VALUE else java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.DAY_OF_MONTH, -(days - 1))
    }.timeInMillis }
    val sessions = remember(state.sessions, query, typeName, phase, cutoff) { state.sessions.filter {
        it.updatedAt >= cutoff &&
        (typeName == "ALL" || it.gameType.name == typeName) && it.title.contains(query, true) && when (phase) {
            "草稿" -> it.isDraft
            "已结束" -> !it.isDraft && it.outcome().phase == GamePhase.FINISHED
            "进行中" -> !it.isDraft && it.outcome().phase != GamePhase.FINISHED
            else -> true
        }
    } }
    Scaffold(topBar = { TopAppBar(title = { Text("棋局库") }, navigationIcon = { IconButton({ model.navigate(AppPage.HOME) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大厅") } },
        actions = { IconButton(onImport) { Icon(Icons.Default.FileOpen, "导入棋谱") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("library"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(query, { query = it }, label = { Text("搜索棋局名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("library_search")) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(typeName == "ALL", { typeName = "ALL" }, label = { Text("全部棋类") })
                for (type in GameType.entries) FilterChip(typeName == type.name, { typeName = type.name }, label = { Text(type.displayName) })
            } }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (label in listOf("全部", "进行中", "已结束", "草稿")) FilterChip(phase == label, { phase = label }, label = { Text(label) }) } }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for ((count, label) in listOf(0 to "全部时间", 1 to "今天", 7 to "7 天", 30 to "30 天")) FilterChip(days == count, { days = count }, label = { Text(label) }) } }
            if (sessions.isEmpty()) item { Text(if (state.sessions.isEmpty()) "还没有棋局。开始对弈或摆谱后会自动保存到这里。" else "没有符合条件的棋局。", modifier = Modifier.padding(vertical = 24.dp)) }
            items(sessions, key = { it.id }) { session ->
                Column(Modifier.fillMaxWidth().clickable { model.open(session) }.padding(vertical = 8.dp).testTag("saved_${session.id}")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BoardThumbnail(session.live.position, Modifier.width(56.dp).padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium)
                            Text(sessionSummary(session), style = MaterialTheme.typography.bodySmall)
                            Text("${dateLabel(session.updatedAt)} · ${if (session.isDraft) "摆谱草稿" else if (session.outcome().phase == GamePhase.FINISHED) "已结束" else "进行中"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ onRename(session) }) { Icon(Icons.Default.Edit, "重命名${session.title}") }
                        IconButton({ onDelete(session) }) { Icon(Icons.Default.DeleteOutline, "删除${session.title}") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonsScreen(model: GameViewModel, onGuide: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("练习与学习") }, navigationIcon = { IconButton({ model.navigate(AppPage.HOME) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大厅") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("从一个清楚的小目标开始", style = MaterialTheme.typography.titleLarge); Text("三道入门练习，帮助熟悉走子、配合与提子。") }
            items(Lessons.all) { lesson ->
                OutlinedCard(onClick = { model.openLesson(lesson) }, modifier = Modifier.fillMaxWidth().testTag("lesson_${lesson.id}")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lesson.type.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(lesson.title, style = MaterialTheme.typography.titleLarge)
                        Text(lesson.description)
                    }
                }
            }
            item { OutlinedButton(onGuide, Modifier.fillMaxWidth()) { Text("操作与规则说明") } }
        }
    }
}
