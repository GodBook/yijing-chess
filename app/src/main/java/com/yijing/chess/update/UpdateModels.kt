package com.yijing.chess.update

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateStatus()
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus()
    data class ReadyToInstall(val info: UpdateInfo, val apkPath: String) : UpdateStatus()
    data class UpToDate(val currentVersion: String) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}
