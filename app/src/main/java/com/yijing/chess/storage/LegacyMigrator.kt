package com.yijing.chess.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yijing.chess.model.*
import com.yijing.chess.session.*

object LegacyMigrator {
    fun migrate(saved: SavedGameState): GameSession {
        val migrationId = "legacy-${saved.gameType.name.lowercase()}-${saved.timestamp}"
        val snapshot = when (saved.gameType) {
            GameType.CHESS -> PositionSnapshot.of(com.yijing.chess.chess.ChessBoard.fromFen(saved.boardState))
            GameType.XIANGQI -> PositionSnapshot.of(com.yijing.chess.xiangqi.XiangqiBoard.fromFen(saved.boardState))
            GameType.GO -> PositionSnapshot(GameType.GO, saved.boardState, saved.turn, saved.boardSize,
                blackPrisoners = saved.blackPrisoners, whitePrisoners = saved.whitePrisoners)
        }
        SessionRules.validateSnapshot(snapshot)
        val history: List<MoveRecord> = runCatching {
            Gson().fromJson<List<MoveRecord>>(saved.moveHistoryJson, object : TypeToken<List<MoveRecord>>() {}.type) ?: emptyList()
        }.getOrDefault(emptyList())
        var session = GameSession.create(saved.gameType, saved.mode, saved.boardSize).copy(
            difficulty = saved.difficulty.coerceIn(1, 3), createdAt = saved.timestamp, updatedAt = saved.timestamp)
        if (saved.gameType != GameType.GO && saved.mode != GameMode.EDITOR && history.isNotEmpty()) {
            val replayed = runCatching {
                for (move in history) session = session.play(move.from, move.to, move.promotion)
                val boardMatches = session.live.position.state.split(" ").take(4) == snapshot.state.split(" ").take(4)
                require(boardMatches && session.live.position.turn == saved.turn)
                session.copy(updatedAt = saved.timestamp, migrationNotice = "已升级旧棋局，走步历史已恢复")
            }.getOrNull()
            if (replayed != null) return replayed.copy(id = migrationId).validate()
        }
        val message = if (saved.gameType == GameType.GO) "旧档未记录停着和劫点，已保留盘面与提子数；从此局面继续，旧走步仅作参考"
            else "已保留旧局面；旧档缺少可验证的起始历史，从此局面继续"
        return GameSession.create(saved.gameType, saved.mode, saved.boardSize, snapshot).copy(
            difficulty = saved.difficulty.coerceIn(1, 3), legacyNotes = history.map { it.notation }, migrationNotice = message,
            createdAt = saved.timestamp, updatedAt = saved.timestamp, id = migrationId).validate()
    }
}
