package com.yijing.chess.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yijing.chess.BuildConfig
import com.yijing.chess.ui.theme.PrimaryGold
import com.yijing.chess.update.UpdateInfo
import com.yijing.chess.update.UpdateStatus

@Composable
fun UpdateDialog(
    status: UpdateStatus,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (status == UpdateStatus.Idle) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = "软件更新",
                tint = PrimaryGold,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = when (status) {
                    is UpdateStatus.Checking -> "正在检查更新..."
                    is UpdateStatus.UpdateAvailable -> "发现新版本"
                    is UpdateStatus.Downloading -> "正在下载更新包"
                    is UpdateStatus.ReadyToInstall -> "下载完成"
                    is UpdateStatus.UpToDate -> "已是最新版本"
                    is UpdateStatus.Error -> "检查更新提示"
                    else -> "软件更新"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (status) {
                    is UpdateStatus.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.size(12.dp))
                            Text("正在连接 GitHub 获取最新版本信息...", fontSize = 13.sp)
                        }
                    }

                    is UpdateStatus.UpToDate -> {
                        Text(
                            text = "当前版本 v${status.currentVersion} 已经是最新版本，无需更新。",
                            fontSize = 14.sp
                        )
                    }

                    is UpdateStatus.UpdateAvailable -> {
                        val info = status.info
                        Text(
                            text = "新版本：v${info.versionName}  (当前版本: v${BuildConfig.VERSION_NAME})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        if (info.apkSize > 0) {
                            val sizeMb = String.format("%.1f", info.apkSize / (1024f * 1024f))
                            Text("安装包大小：约 $sizeMb MB", fontSize = 12.sp, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("更新日志：", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = info.releaseNotes,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    is UpdateStatus.Downloading -> {
                        Text("新版本 v${status.info.versionName} 下载中，请稍候...", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { status.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val downMb = String.format("%.1f", status.downloadedBytes / (1024f * 1024f))
                        val totalMb = String.format("%.1f", status.totalBytes / (1024f * 1024f))
                        Text(
                            text = "$downMb MB / $totalMb MB (${(status.progress * 100).toInt()}%)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Text("安装包已准备就绪，点击下方按钮即可调起系统安装更新。", fontSize = 14.sp)
                    }

                    is UpdateStatus.Error -> {
                        Text(
                            text = status.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }

                    else -> {}
                }
            }
        },
        confirmButton = {
            when (status) {
                is UpdateStatus.UpdateAvailable -> {
                    Button(
                        onClick = { onDownload(status.info) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                    ) {
                        Text("立即下载更新", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateStatus.ReadyToInstall -> {
                    Button(
                        onClick = { onInstall(status.apkPath) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                    ) {
                        Text("立即安装", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateStatus.Error -> {
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("确定")
                    }
                }
            }
        },
        dismissButton = {
            if (status is UpdateStatus.UpdateAvailable || status is UpdateStatus.ReadyToInstall || status is UpdateStatus.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text(if (status is UpdateStatus.Downloading) "后台下载" else "稍后再说")
                }
            }
        }
    )
}
