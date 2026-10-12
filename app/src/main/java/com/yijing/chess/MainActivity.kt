package com.yijing.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yijing.chess.model.GameMode
import com.yijing.chess.model.GameType
import com.yijing.chess.ui.screens.ChessScreen
import com.yijing.chess.ui.screens.GoScreen
import com.yijing.chess.ui.screens.HomeScreen
import com.yijing.chess.ui.screens.XiangqiScreen
import com.yijing.chess.ui.theme.PrimaryGold
import com.yijing.chess.ui.theme.YiJingTheme

enum class MainTab(val title: String) {
    HOME("大厅"),
    XIANGQI("中国象棋"),
    GO("围棋"),
    CHESS("国际象棋")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YiJingTheme {
                var currentTab by remember { mutableStateOf(MainTab.HOME) }
                var xiangqiMode by remember { mutableStateOf(GameMode.PVE) }
                var goMode by remember { mutableStateOf(GameMode.PVE) }
                var chessMode by remember { mutableStateOf(GameMode.PVE) }

                var isGameActive by remember { mutableStateOf(false) }
                var onSaveAndProceed by remember { mutableStateOf<(() -> Unit)?>(null) }
                var pendingTab by remember { mutableStateOf<MainTab?>(null) }
                var showSaveDialog by remember { mutableStateOf(false) }

                fun requestTabSwitch(target: MainTab) {
                    if (target == currentTab) return
                    if (isGameActive && onSaveAndProceed != null) {
                        pendingTab = target
                        showSaveDialog = true
                    } else {
                        isGameActive = false
                        onSaveAndProceed = null
                        currentTab = target
                    }
                }

                BackHandler(enabled = currentTab != MainTab.HOME) {
                    requestTabSwitch(MainTab.HOME)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == MainTab.HOME,
                                onClick = { requestTabSwitch(MainTab.HOME) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "大厅") },
                                label = { Text("大厅", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryGold,
                                    selectedTextColor = PrimaryGold
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.XIANGQI,
                                onClick = { requestTabSwitch(MainTab.XIANGQI) },
                                icon = { Icon(Icons.Default.SportsEsports, contentDescription = "中国象棋") },
                                label = { Text("中国象棋", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryGold,
                                    selectedTextColor = PrimaryGold
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.GO,
                                onClick = { requestTabSwitch(MainTab.GO) },
                                icon = { Icon(Icons.Default.Adjust, contentDescription = "围棋") },
                                label = { Text("围棋", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryGold,
                                    selectedTextColor = PrimaryGold
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.CHESS,
                                onClick = { requestTabSwitch(MainTab.CHESS) },
                                icon = { Icon(Icons.Default.Extension, contentDescription = "国际象棋") },
                                label = { Text("国际象棋", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryGold,
                                    selectedTextColor = PrimaryGold
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        when (currentTab) {
                            MainTab.HOME -> {
                                HomeScreen(
                                    onNavigateToGame = { type, mode ->
                                        when (type) {
                                            GameType.XIANGQI -> {
                                                xiangqiMode = mode
                                                requestTabSwitch(MainTab.XIANGQI)
                                            }
                                            GameType.GO -> {
                                                goMode = mode
                                                requestTabSwitch(MainTab.GO)
                                            }
                                            GameType.CHESS -> {
                                                chessMode = mode
                                                requestTabSwitch(MainTab.CHESS)
                                            }
                                        }
                                    }
                                )
                            }
                            MainTab.XIANGQI -> {
                                XiangqiScreen(
                                    initialMode = xiangqiMode,
                                    onBack = { requestTabSwitch(MainTab.HOME) },
                                    onGameStateChanged = { active, saveFunc ->
                                        isGameActive = active
                                        onSaveAndProceed = saveFunc
                                    }
                                )
                            }
                            MainTab.GO -> {
                                GoScreen(
                                    initialMode = goMode,
                                    onBack = { requestTabSwitch(MainTab.HOME) },
                                    onGameStateChanged = { active, saveFunc ->
                                        isGameActive = active
                                        onSaveAndProceed = saveFunc
                                    }
                                )
                            }
                            MainTab.CHESS -> {
                                ChessScreen(
                                    initialMode = chessMode,
                                    onBack = { requestTabSwitch(MainTab.HOME) },
                                    onGameStateChanged = { active, saveFunc ->
                                        isGameActive = active
                                        onSaveAndProceed = saveFunc
                                    }
                                )
                            }
                        }
                    }
                }

                if (showSaveDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showSaveDialog = false
                            pendingTab = null
                        },
                        title = { Text("保存对局") },
                        text = { Text("当前对局尚未结束，是否保存当前棋局？下次进入可继续对弈。") },
                        confirmButton = {
                            TextButton(onClick = {
                                onSaveAndProceed?.invoke()
                                showSaveDialog = false
                                isGameActive = false
                                onSaveAndProceed = null
                                pendingTab?.let { currentTab = it }
                                pendingTab = null
                            }) {
                                Text("保存并切换")
                            }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    showSaveDialog = false
                                    isGameActive = false
                                    onSaveAndProceed = null
                                    pendingTab?.let { currentTab = it }
                                    pendingTab = null
                                }) {
                                    Text("不保存")
                                }
                                TextButton(onClick = {
                                    showSaveDialog = false
                                    pendingTab = null
                                }) {
                                    Text("取消")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
