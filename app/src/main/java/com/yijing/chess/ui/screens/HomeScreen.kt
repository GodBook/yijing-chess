package com.yijing.chess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yijing.chess.model.GameMode
import com.yijing.chess.model.GameType
import com.yijing.chess.ui.theme.BoardWoodBg
import com.yijing.chess.ui.theme.PrimaryGold
import com.yijing.chess.ui.theme.XiangqiRedColor

import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yijing.chess.ui.components.UpdateDialog
import com.yijing.chess.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToGame: (GameType, GameMode) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val updateStatus by updateManager.status.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 标题徽标与文字
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(PrimaryGold)
            ) {
                Text(
                    text = "弈",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "弈 境",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "方寸乾坤 · 棋乐无穷",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        updateManager.checkForUpdates(isManualCheck = true)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = "检查更新", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("检查更新", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 中国象棋卡片
            GameCard(
                title = "中国象棋",
                subtitle = "Xiangqi",
                desc = "楚河汉界，万马奔腾。内置飞将、别马腿、象眼判定与多级启发式 AI 引擎。",
                badge = "经典国粹",
                badgeColor = XiangqiRedColor,
                onModeSelected = { mode -> onNavigateToGame(GameType.XIANGQI, mode) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 围棋卡片
            GameCard(
                title = "围 棋",
                subtitle = "Go / Weiqi",
                desc = "千古无同局。支持 19路大盘、13路中盘及 9路快棋，支持叫吃战术与 SGF 棋谱导出。",
                badge = "黑白胜负",
                badgeColor = Color(0xFF2C3E50),
                onModeSelected = { mode -> onNavigateToGame(GameType.GO, mode) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 国际象棋卡片
            GameCard(
                title = "国际象棋",
                subtitle = "Chess",
                desc = "王车纵横。支持王车易位、兵升变、吃过路兵，内置 Alpha-Beta 战术评估与 PGN 导出。",
                badge = "世界棋战",
                badgeColor = Color(0xFF4A3525),
                onModeSelected = { mode -> onNavigateToGame(GameType.CHESS, mode) }
            )

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "版本 v1.0.0 · 支持 Android 11+ · 纯净无广告",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        UpdateDialog(
            status = updateStatus,
            onDownload = { info ->
                coroutineScope.launch {
                    updateManager.downloadApk(info)
                }
            },
            onInstall = { path ->
                updateManager.installApk(path)
            },
            onRetry = {
                coroutineScope.launch {
                    updateManager.checkForUpdates(isManualCheck = true)
                }
            },
            onDismiss = {
                updateManager.resetStatus()
            }
        )
    }
}

@Composable
private fun GameCard(
    title: String,
    subtitle: String,
    desc: String,
    badge: String,
    badgeColor: Color,
    onModeSelected: (GameMode) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = desc,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 模式快捷入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onModeSelected(GameMode.PVE) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = "人机", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("人机对战", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onModeSelected(GameMode.PVP) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Groups, contentDescription = "双人", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("双人同屏", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onModeSelected(GameMode.EDITOR) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Build, contentDescription = "摆谱", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("自由摆谱", fontSize = 12.sp)
                }
            }
        }
    }
}
