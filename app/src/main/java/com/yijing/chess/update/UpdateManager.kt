package com.yijing.chess.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.yijing.chess.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = _status.asStateFlow()

    companion object {
        const val REPO_OWNER = "GodBook"
        const val REPO_NAME = "yijing-chess"
        const val API_LATEST_RELEASE = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    }

    /**
     * 检查 GitHub 最新 Release
     */
    suspend fun checkForUpdates(isManualCheck: Boolean = false) = withContext(Dispatchers.IO) {
        _status.value = UpdateStatus.Checking
        try {
            val url = URL(API_LATEST_RELEASE)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "YiJing-Android-App/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (conn.responseCode == 404) {
                // 仓库尚未发布任何 Release
                _status.value = UpdateStatus.UpToDate(BuildConfig.VERSION_NAME)
                return@withContext
            }

            if (conn.responseCode != 200) {
                _status.value = UpdateStatus.Error("检查更新失败: HTTP ${conn.responseCode}")
                return@withContext
            }

            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(jsonText).asJsonObject

            val tagName = root.get("tag_name")?.asString ?: ""
            val title = root.get("name")?.asString ?: tagName
            val releaseNotes = root.get("body")?.asString ?: "优化与问题修复"
            val cleanRemoteVersion = tagName.trimStart('v', 'V')

            // 寻找 assets 中的 apk 文件
            var apkUrl: String? = null
            var apkSize: Long = 0L

            val assets = root.getAsJsonArray("assets")
            if (assets != null) {
                for (assetElem in assets) {
                    val asset = assetElem.asJsonObject
                    val name = asset.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.get("browser_download_url")?.asString
                        apkSize = asset.get("size")?.asLong ?: 0L
                        break
                    }
                }
            }

            if (apkUrl == null) {
                _status.value = if (isManualCheck) UpdateStatus.UpToDate(BuildConfig.VERSION_NAME) else UpdateStatus.Idle
                return@withContext
            }

            val currentVersion = BuildConfig.VERSION_NAME
            if (isVersionNewer(cleanRemoteVersion, currentVersion)) {
                val updateInfo = UpdateInfo(
                    tagName = tagName,
                    versionName = cleanRemoteVersion,
                    title = title,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkUrl,
                    apkSize = apkSize
                )
                _status.value = UpdateStatus.UpdateAvailable(updateInfo)
            } else {
                _status.value = UpdateStatus.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            _status.value = UpdateStatus.Error("网络连接异常: ${e.localizedMessage ?: "未知错误"}")
        }
    }

    /**
     * 下载 APK 文件
     */
    suspend fun downloadApk(info: UpdateInfo) = withContext(Dispatchers.IO) {
        val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(targetDir, "yijing-v${info.versionName}.apk")

        try {
            var downloadUrl = info.downloadUrl
            var conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", "YiJing-Android-App")
            }

            // 处理 302 重定向至 S3 / GitHub CDN
            var responseCode = conn.responseCode
            var redirectCount = 0
            while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER) && redirectCount < 5) {
                val newUrl = conn.getHeaderField("Location")
                if (newUrl != null) {
                    downloadUrl = newUrl
                    conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 12000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", "YiJing-Android-App")
                    }
                    responseCode = conn.responseCode
                    redirectCount++
                } else break
            }

            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.apkSize
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) else 0.5f
                        _status.value = UpdateStatus.Downloading(info, progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            _status.value = UpdateStatus.ReadyToInstall(info, targetFile.absolutePath)
        } catch (e: Exception) {
            targetFile.delete()
            _status.value = UpdateStatus.Error("下载失败: ${e.localizedMessage ?: "网络中断"}")
        }
    }

    /**
     * 调起系统安装器
     */
    fun installApk(apkPath: String) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            _status.value = UpdateStatus.Error("安装包文件不存在，请重新下载")
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _status.value = UpdateStatus.Error("无法启动安装程序: ${e.localizedMessage}")
        }
    }

    fun resetStatus() {
        _status.value = UpdateStatus.Idle
    }

    /**
     * 语义化版本比对
     */
    private fun isVersionNewer(remote: String, current: String): Boolean {
        val rParts = remote.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = if (i in rParts.indices) rParts[i] else 0
            val c = if (i in cParts.indices) cParts[i] else 0
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
