package com.yijing.chess.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object NativeFiles {
    fun read(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(output.size() + count <= 2_000_000) { "棋谱超过 2 MB，请选择单局文件" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("无法读取所选文件")
        val prefix = String(bytes.take(512).toByteArray(), Charsets.ISO_8859_1)
        val declared = Regex("CA\\[([^]\\r\\n]+)]").find(prefix)?.groupValues?.get(1)?.uppercase() ?: "UTF-8"
        val encodings = mapOf("UTF-8" to "UTF-8", "UTF8" to "UTF-8", "GB2312" to "GB18030", "GBK" to "GB18030", "GB18030" to "GB18030", "ISO-8859-1" to "ISO-8859-1", "SHIFT_JIS" to "Shift_JIS")
        val charset = java.nio.charset.Charset.forName(requireNotNull(encodings[declared]) { "暂不支持棋谱编码 $declared，请转换为 UTF-8" })
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    fun write(context: Context, uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalArgumentException("无法写入所选位置")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("弈境棋谱", text))
    }

    fun paste(context: Context): String = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

    fun share(context: Context, name: String, text: String, isSgf: Boolean) {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, name.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isSgf) "application/x-go-sgf" else "application/x-chess-pgn"
            putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newUri(context.contentResolver, "弈境棋谱", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享棋谱"))
    }
}
