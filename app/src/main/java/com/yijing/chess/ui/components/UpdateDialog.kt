package com.yijing.chess.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yijing.chess.update.*

@Composable
fun UpdateDialog(status: UpdateStatus, onDownload: (UpdateInfo) -> Unit, onInstall: (String) -> Unit,
    onRetry: () -> Unit, onDismiss: () -> Unit, onCancelDownload: () -> Unit = {}) {
    if (status == UpdateStatus.Idle) return
    val title = when (status) {
        UpdateStatus.Checking -> "正在检查更新"
        is UpdateStatus.UpdateAvailable -> "发现新版本"
        is UpdateStatus.Downloading -> "系统正在下载更新"
        is UpdateStatus.Validating -> "正在校验更新包"
        is UpdateStatus.ReadyToInstall -> "安装包已准备好"
        is UpdateStatus.InstallPermission -> "开启安装授权后继续"
        is UpdateStatus.UpToDate -> "已是最新版本"
        is UpdateStatus.Information -> "更新提示"
        is UpdateStatus.Error -> when (status.stage) { UpdateStage.CHECK -> "检查未完成"; UpdateStage.DOWNLOAD -> "下载未完成"; UpdateStage.INSTALL -> "安装未完成" }
        else -> "软件更新"
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (status) {
                UpdateStatus.Checking -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在连接 GitHub 获取发布信息。") }
                is UpdateStatus.UpdateAvailable -> {
                    Text("v${status.info.versionName} · ${if (status.info.apkSize > 0) "约 %.1f MB".format(status.info.apkSize / 1048576.0) else "大小待确认"}")
                    Text(status.info.releaseNotes, style = MaterialTheme.typography.bodyMedium)
                }
                is UpdateStatus.Downloading -> {
                    if (status.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                    Text(if (status.totalBytes > 0) "%.1f MB / %.1f MB".format(status.downloadedBytes / 1048576.0, status.totalBytes / 1048576.0) else "已下载 %.1f MB".format(status.downloadedBytes / 1048576.0))
                    Text("可关闭提示继续下棋。系统下载通知与设置中的更新入口均可查看任务。", style = MaterialTheme.typography.bodySmall)
                    TextButton(onCancelDownload) { Text("取消下载") }
                }
                is UpdateStatus.Validating -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在检查下载完整性、版本和签名。") }
                is UpdateStatus.ReadyToInstall -> Text("v${status.info.versionName} 已校验，可使用系统安装器覆盖更新。棋局会继续保留。")
                is UpdateStatus.InstallPermission -> Text("需要允许弈境安装本次更新。授权后返回应用继续安装；也可以稍后再操作。")
                is UpdateStatus.UpToDate -> Text("当前版本 v${status.currentVersion}，暂无更高版本。")
                is UpdateStatus.Information -> Text(status.message)
                is UpdateStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }, confirmButton = {
        when (status) {
            is UpdateStatus.UpdateAvailable -> Button({ onDownload(status.info) }) { Text("下载更新") }
            is UpdateStatus.ReadyToInstall -> Button({ onInstall(status.apkPath) }) { Text("安装更新") }
            is UpdateStatus.InstallPermission -> Button({ onInstall(status.apkPath) }) { Text("前往授权") }
            is UpdateStatus.Error -> Button(onRetry) { Text("重试") }
            else -> TextButton(onDismiss) { Text(if (status is UpdateStatus.Downloading || status is UpdateStatus.Validating) "继续下棋" else "关闭") }
        }
    }, dismissButton = { if (status is UpdateStatus.UpdateAvailable || status is UpdateStatus.ReadyToInstall || status is UpdateStatus.InstallPermission) TextButton(onDismiss) { Text("稍后") } })
}
