package com.yijing.chess.update

import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.yijing.chess.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** System downloads survive leaving the page and process death. Hiding UI never changes the task. */
class UpdateManager(context: Context, preferencesName: String = "yijing_updates", private val latestReleaseUrl: String = API_LATEST_RELEASE) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(DownloadManager::class.java)
    private val prefs = this.context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val gson = Gson()
    private var downloadId = prefs.getLong("download_id", 0)
    private var lastInfo: UpdateInfo? = runCatching { prefs.getString("info", null)?.let { gson.fromJson(it, UpdateInfo::class.java) } }.getOrNull()
    private var readyPath = prefs.getString("ready_path", null)
    private var visible = false
    private var current: UpdateStatus = UpdateStatus.Idle
    private var pollJob: Job? = null
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = _status.asStateFlow()
    private val _hasPending = MutableStateFlow(downloadId > 0 || readyPath != null)
    val hasPending = _hasPending.asStateFlow()

    companion object {
        const val API_LATEST_RELEASE = "https://api.github.com/repos/GodBook/yijing-chess/releases/latest"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId && downloadId > 0) scope.launch { refreshDownload() }
        }
    }

    init {
        ContextCompat.registerReceiver(this.context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
        if (readyPath != null && lastInfo != null) {
            if (runCatching { !UpdateVersion.newer(lastInfo!!.versionName, BuildConfig.VERSION_NAME) }.getOrDefault(false)) {
                readyPath = null; prefs.edit().remove("ready_path").remove("pending_install").apply(); _hasPending.value = downloadId > 0
            } else current = UpdateStatus.ReadyToInstall(lastInfo!!, readyPath!!)
        }
        if (downloadId > 0) startPolling()
    }

    private fun setStatus(value: UpdateStatus) { current = value; if (visible) _status.value = value }
    fun resetStatus() {
        visible = false; _status.value = UpdateStatus.Idle
        if (current is UpdateStatus.InstallPermission) prefs.edit().remove("pending_install").apply()
    }
    fun showStatus() {
        visible = true; _status.value = current
        if (downloadId > 0) scope.launch { refreshDownload() }
    }

    suspend fun checkForUpdates(isManualCheck: Boolean = false) = withContext(Dispatchers.IO) {
        visible = isManualCheck
        if (downloadId > 0) { refreshDownload(); return@withContext }
        if (readyPath != null && lastInfo != null) { setStatus(UpdateStatus.ReadyToInstall(lastInfo!!, readyPath!!)); return@withContext }
        setStatus(UpdateStatus.Checking)
        var connection: HttpURLConnection? = null
        try {
            val endpoint = Uri.parse(latestReleaseUrl)
            require(endpoint.scheme == "https" || (BuildConfig.DEBUG && endpoint.scheme == "http" && endpoint.host in listOf("127.0.0.1", "localhost"))) { "更新服务必须使用 HTTPS" }
            connection = (URL(latestReleaseUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("User-Agent", "YiJing/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = connection.responseCode
            if (code == 404) { setStatus(UpdateStatus.Information("暂未获取到可用 Release，请稍后检查。")); return@withContext }
            require(code == 200) { if (code == 403 || code == 429) "GitHub 暂时限制请求，请稍后重试" else "更新服务器返回 $code，请稍后重试" }
            val root = connection.inputStream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            val version = root.get("tag_name")?.asString.orEmpty().trimStart('v', 'V')
            UpdateVersion.parts(version)
            if (!UpdateVersion.newer(version, BuildConfig.VERSION_NAME)) { setStatus(UpdateStatus.UpToDate(BuildConfig.VERSION_NAME)); return@withContext }
            val asset = root.getAsJsonArray("assets")?.firstOrNull { it.asJsonObject.get("name")?.asString?.endsWith(".apk", true) == true }?.asJsonObject
            if (asset == null) { setStatus(UpdateStatus.Information("发现 v$version，但该 Release 尚未提供 Android 安装包。")); return@withContext }
            val digest = asset.get("digest")?.takeUnless { it.isJsonNull }?.asString?.removePrefix("sha256:")
            require(digest == null || digest.matches(Regex("[a-fA-F0-9]{64}"))) { "发布的安装包摘要格式无效" }
            val info = UpdateInfo(root.get("tag_name").asString, version,
                root.get("name")?.asString ?: "弈境 v$version", root.get("body")?.takeUnless { it.isJsonNull }?.asString ?: "优化与问题修复",
                asset.get("browser_download_url").asString, asset.get("size").asLong, digest)
            lastInfo = info
            setStatus(UpdateStatus.UpdateAvailable(info))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { setStatus(UpdateStatus.Error("检查更新失败：${error.message ?: "网络连接异常"}", UpdateStage.CHECK)) }
        finally { connection?.disconnect() }
    }

    suspend fun downloadApk(info: UpdateInfo) = withContext(Dispatchers.IO) { lock.withLock {
        visible = true
        if (downloadId > 0) { showStatus(); return@withLock }
        try {
            UpdateVersion.parts(info.versionName)
            val uri = Uri.parse(info.downloadUrl)
            require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http" && uri.host in listOf("127.0.0.1", "localhost"))) { "更新包必须通过 HTTPS 下载" }
            lastInfo = info; readyPath = null
            val request = DownloadManager.Request(uri).setTitle("弈境 v${info.versionName}")
                .setDescription("下载完成后可返回弈境安装更新")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "yijing-${info.versionName}-${System.currentTimeMillis()}.apk")
            downloadId = manager.enqueue(request)
            check(prefs.edit().putLong("download_id", downloadId).putString("info", gson.toJson(info)).remove("ready_path").commit()) { "下载任务状态保存失败" }
            _hasPending.value = true
            setStatus(UpdateStatus.Downloading(info, null, 0, info.apkSize))
            startPolling()
        } catch (error: Exception) { setStatus(UpdateStatus.Error("无法开始下载：${error.message}", UpdateStage.DOWNLOAD)) }
    } }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch { while (isActive && downloadId > 0) { refreshDownload(); delay(500) } }
    }

    private suspend fun refreshDownload() = withContext(Dispatchers.IO) { lock.withLock {
        val id = downloadId
        val info = lastInfo
        if (id <= 0 || info == null) return@withLock
        try {
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                require(cursor.moveToFirst()) { "系统下载任务已移除，可重新下载" }
                fun number(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                val status = number(DownloadManager.COLUMN_STATUS).toInt()
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        setStatus(UpdateStatus.Validating(info))
                        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
                        val partial = File(folder, "yijing-v${info.versionName}.partial.apk")
                        val target = File(folder, "yijing-v${info.versionName}.apk")
                        try {
                            val digest = MessageDigest.getInstance("SHA-256")
                            manager.openDownloadedFile(id).use { descriptor ->
                                java.io.FileInputStream(descriptor.fileDescriptor).use { input -> partial.outputStream().use { output ->
                                    val bytes = ByteArray(16_384)
                                    while (true) { currentCoroutineContext().ensureActive(); val read = input.read(bytes); if (read < 0) break; output.write(bytes, 0, read); digest.update(bytes, 0, read) }
                                } }
                            }
                            require(info.apkSize <= 0 || partial.length() == info.apkSize) { "下载长度不完整，请重新下载" }
                            val actual = digest.digest().joinToString("") { "%02x".format(it) }
                            require(info.sha256 == null || actual.equals(info.sha256, true)) { "安装包摘要不匹配，请重新下载" }
                            validateApk(partial, info)
                            check(partial.renameTo(target)) { "无法保存已校验的安装包" }
                            readyPath = target.absolutePath; downloadId = 0
                            check(prefs.edit().remove("download_id").putString("ready_path", readyPath).commit()) { "安装状态保存失败" }
                            manager.remove(id)
                            setStatus(UpdateStatus.ReadyToInstall(info, target.absolutePath))
                        } finally { if (partial.exists()) partial.delete() }
                    }
                    DownloadManager.STATUS_FAILED -> throw IllegalStateException("系统下载失败（${number(DownloadManager.COLUMN_REASON)}），可重试")
                    else -> {
                        val bytes = number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val total = number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).takeIf { it > 0 } ?: info.apkSize
                        setStatus(UpdateStatus.Downloading(info, if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null, bytes, total))
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            manager.remove(id); downloadId = 0; readyPath = null
            prefs.edit().remove("download_id").remove("ready_path").apply(); _hasPending.value = false
            setStatus(UpdateStatus.Error("下载或校验失败：${error.message}", UpdateStage.DOWNLOAD))
        }
    } }

    @Suppress("DEPRECATION")
    internal fun validateApk(file: File, info: UpdateInfo) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)) { "文件不是有效的 Android 安装包" }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(archive.packageName == context.packageName) { "安装包不属于弈境" }
        require(archive.versionName == info.versionName) { "安装包版本与发布信息不符" }
        require(archive.longVersionCode >= installed.longVersionCode) { "安装包版本早于已安装版本" }
        require((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "新版本暂不支持此 Android 系统" }
        val candidate = archive.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        val trusted = installed.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        require(candidate.isNotEmpty() && candidate == trusted) { "安装签名不兼容，无法覆盖更新" }
    }

    fun installApk(apkPath: String) {
        val info = lastInfo ?: return
        try {
            val file = File(apkPath)
            require(file.exists() && file.canonicalFile.parentFile == File(context.cacheDir, "updates").canonicalFile) { "安装包已失效，请重新下载" }
            validateApk(file, info)
            visible = true
            if (!context.packageManager.canRequestPackageInstalls()) {
                prefs.edit().putBoolean("pending_install", true).apply()
                setStatus(UpdateStatus.InstallPermission(info, apkPath))
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            prefs.edit().remove("pending_install").apply()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            setStatus(UpdateStatus.ReadyToInstall(info, apkPath))
        } catch (error: Exception) { setStatus(UpdateStatus.Error("安装未开始：${error.message}", if (File(apkPath).exists()) UpdateStage.INSTALL else UpdateStage.DOWNLOAD)) }
    }

    fun onResume() {
        if (prefs.getBoolean("pending_install", false) && readyPath != null) {
            if (context.packageManager.canRequestPackageInstalls()) installApk(readyPath!!)
            else if (lastInfo != null) { visible = true; setStatus(UpdateStatus.InstallPermission(lastInfo!!, readyPath!!)) }
        }
        if (downloadId > 0) scope.launch { refreshDownload() }
    }

    fun retry() {
        val error = current as? UpdateStatus.Error
        scope.launch { when (error?.stage) {
            UpdateStage.DOWNLOAD -> lastInfo?.let { downloadApk(it) } ?: checkForUpdates(true)
            UpdateStage.INSTALL -> readyPath?.let(::installApk) ?: checkForUpdates(true)
            else -> checkForUpdates(true)
        } }
    }

    fun cancelDownload() { scope.launch { lock.withLock {
        if (downloadId <= 0) return@withLock
        manager.remove(downloadId)
        downloadId = 0; pollJob?.cancel(); prefs.edit().remove("download_id").apply(); _hasPending.value = readyPath != null
        setStatus(lastInfo?.let { UpdateStatus.UpdateAvailable(it) } ?: UpdateStatus.Idle)
    } } }

    fun close() { context.unregisterReceiver(receiver); scope.cancel() }
}
