package com.yijing.chess.update

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long,
    val sha256: String? = null
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateStatus()
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float?,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus()
    data class ReadyToInstall(val info: UpdateInfo, val apkPath: String) : UpdateStatus()
    data class Validating(val info: UpdateInfo) : UpdateStatus()
    data class InstallPermission(val info: UpdateInfo, val apkPath: String) : UpdateStatus()
    data class Information(val message: String) : UpdateStatus()
    data class UpToDate(val currentVersion: String) : UpdateStatus()
    data class Error(val message: String, val stage: UpdateStage = UpdateStage.CHECK) : UpdateStatus()
}

enum class UpdateStage { CHECK, DOWNLOAD, INSTALL }

object UpdateVersion {
    fun parts(version: String): List<Int> {
        val clean = version.removePrefix("v").removePrefix("V")
        require(Regex("\\d+(?:\\.\\d+){1,3}").matches(clean)) { "无法识别版本号：$version" }
        return clean.split('.').map { requireNotNull(it.toIntOrNull()) { "版本号超出范围" } }
    }
    fun newer(remote: String, current: String): Boolean {
        val a = parts(remote); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}
