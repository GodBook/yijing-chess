package com.yijing.chess.storage

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.yijing.chess.model.GameType
import com.yijing.chess.session.GameSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ThemeChoice(val label: String) { SYSTEM("跟随系统"), DARK("深色"), LIGHT("浅色") }
data class AppPreferences(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val highContrast: Boolean = false,
    val sound: Boolean = false,
    val haptics: Boolean = true,
    val coordinates: Boolean = true,
    val confirmGo19: Boolean = true,
    val animations: Boolean = true,
    val onboardingComplete: Boolean = false
)
data class LibraryLoad(val sessions: List<GameSession>, val warnings: List<String>)

/** One atomic file per game avoids rewriting the whole library for every move. Legacy data is retained. */
class SessionRepository(context: Context, storageName: String = "games", preferencesName: String = "yijing_game_saves") {
    private val directory = File(context.filesDir, storageName).apply { mkdirs() }
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()
    private val revisions = mutableMapOf<String, Long>()

    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "棋局标识无效" }
        return AtomicFile(File(directory, "$id.json"))
    }

    private fun writeLocked(session: GameSession) {
        if ((revisions[session.id] ?: -1) > session.revision) return
        val atomic = file(session.id)
        val stream = atomic.startWrite()
        try {
            stream.write(gson.toJson(session).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            revisions[session.id] = session.revision
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    suspend fun load(): LibraryLoad = withContext(Dispatchers.IO) { mutex.withLock {
        val warnings = mutableListOf<String>()
        for (type in GameType.entries) {
            if (prefs.getBoolean("migrated_${type.name}", false)) continue
            val legacy = prefs.getString("save_${type.name}", null) ?: continue
            runCatching {
                val old = requireNotNull(gson.fromJson(legacy, SavedGameState::class.java))
                val session = LegacyMigrator.migrate(old)
                val migratedFile = file(session.id)
                if (migratedFile.baseFile.exists()) {
                    val existing = migratedFile.openRead().bufferedReader(Charsets.UTF_8).use { gson.fromJson(it, GameSession::class.java) }.validate()
                    require(existing.id == session.id && existing.gameType == type) { "已有迁移棋局信息不符" }
                } else writeLocked(session)
                check(prefs.edit().putBoolean("migrated_${type.name}", true).commit()) { "旧档迁移标记写入失败" }
            }.onFailure { warnings.add("${type.displayName}旧档暂时无法恢复，原始数据已保留：${it.message}") }
        }
        val sessions = directory.listFiles().orEmpty().filter { it.extension == "json" && it.name != "deleted.json" }.mapNotNull { source ->
            runCatching {
                val session = AtomicFile(source).openRead().bufferedReader(Charsets.UTF_8).use { gson.fromJson(it, GameSession::class.java) }.validate()
                require(source.nameWithoutExtension == session.id) { "棋局文件标识不符" }
                revisions[session.id] = session.revision
                session
            }.onFailure { warnings.add("一份棋局读取失败，文件已保留：${it.message}") }.getOrNull()
        }
        LibraryLoad(sessions.sortedByDescending { it.updatedAt }, warnings)
    } }

    suspend fun save(session: GameSession) = withContext(Dispatchers.IO) { mutex.withLock {
        writeLocked(session)
    } }

    suspend fun delete(session: GameSession) = withContext(Dispatchers.IO) { mutex.withLock {
        val deleted = AtomicFile(File(directory, "deleted.json"))
        val stream = deleted.startWrite()
        try { stream.write(gson.toJson(session).toByteArray(Charsets.UTF_8)); deleted.finishWrite(stream) }
        catch (error: Exception) { deleted.failWrite(stream); throw error }
        file(session.id).delete()
        revisions[session.id] = Long.MAX_VALUE
    } }

    suspend fun undoDelete(): GameSession? = withContext(Dispatchers.IO) { mutex.withLock {
        val deleted = AtomicFile(File(directory, "deleted.json"))
        if (!deleted.baseFile.exists()) return@withLock null
        val session = deleted.openRead().bufferedReader(Charsets.UTF_8).use { gson.fromJson(it, GameSession::class.java) }.validate().touch()
        revisions.remove(session.id)
        writeLocked(session)
        deleted.delete()
        session
    } }

    fun preferences(): AppPreferences = runCatching {
        prefs.getString("preferences_v2", null)?.let { gson.fromJson(it, AppPreferences::class.java) }
    }.getOrNull() ?: AppPreferences()

    suspend fun savePreferences(value: AppPreferences) = withContext(Dispatchers.IO) { mutex.withLock {
        check(prefs.edit().putString("preferences_v2", gson.toJson(value)).commit()) { "设置保存失败" }
    } }
}
