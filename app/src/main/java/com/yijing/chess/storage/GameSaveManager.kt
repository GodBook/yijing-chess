package com.yijing.chess.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yijing.chess.model.GameMode
import com.yijing.chess.model.GameType
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.PlayerSide

/**
 * 棋局保存数据模型
 */
data class SavedGameState(
    val gameType: GameType,
    val mode: GameMode,
    val difficulty: Int = 2,
    val boardState: String,           // FEN 串或棋盘编码
    val boardSize: Int = 19,          // 围棋路数 (9, 13, 19)
    val turn: PlayerSide,
    val moveHistoryJson: String,      // 序列化的走步历史
    val blackPrisoners: Int = 0,      // 围棋提子
    val whitePrisoners: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 棋局持久化存储管理器
 */
class GameSaveManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("yijing_game_saves", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveGame(state: SavedGameState) {
        val json = gson.toJson(state)
        prefs.edit().putString("save_${state.gameType.name}", json).apply()
    }

    fun getSavedGame(gameType: GameType): SavedGameState? {
        val json = prefs.getString("save_${gameType.name}", null) ?: return null
        return runCatching {
            gson.fromJson(json, SavedGameState::class.java)
        }.getOrNull()
    }

    fun hasSavedGame(gameType: GameType): Boolean {
        return prefs.contains("save_${gameType.name}")
    }

    fun clearSavedGame(gameType: GameType) {
        prefs.edit().remove("save_${gameType.name}").apply()
    }

    fun serializeMoveHistory(history: List<MoveRecord>): String {
        return gson.toJson(history)
    }

    fun deserializeMoveHistory(json: String): List<MoveRecord> {
        return runCatching {
            val type = object : TypeToken<List<MoveRecord>>() {}.type
            gson.fromJson<List<MoveRecord>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
